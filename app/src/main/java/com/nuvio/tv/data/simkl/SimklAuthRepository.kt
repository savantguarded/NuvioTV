package com.nuvio.tv.data.simkl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimklAuthRepository(
    private val apiClient: SimklApiClient,
    private val configuration: SimklApiConfiguration,
    private val storage: SimklAuthStorage,
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) {
    @Inject
    constructor(
        apiClient: SimklApiClient,
        configuration: SimklApiConfiguration,
        storage: SimklAuthStorage
    ) : this(apiClient, configuration, storage, System::currentTimeMillis)

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    val state: StateFlow<SimklAuthState> = storage.state

    fun hasRequiredCredentials(): Boolean = configuration.clientId.isNotBlank()

    suspend fun startPinAuth(): SimklPinSession = mutex.withLock {
        val authScope = storage.currentScope()
        if (!hasRequiredCredentials()) throw SimklAuthException(SimklAuthError.MISSING_CLIENT_ID)
        // [fork] Simkl AUTH V2 device flow first; V1 client IDs are refused there and fall back to the PIN login.
        if (com.nuvio.tv.NuvioCFeatures.SIMKL_V2) startDeviceAuthV2(authScope)?.let { return@withLock it }
        val response = executeAuthRequest(SimklApiRequest(SimklHttpMethod.GET, "/oauth/pin", requiresAuthentication = false))
        val payload = decodePinResponse(response.body)
        val userCode = payload.userCode?.trim()?.takeIf(String::isNotBlank)
        val verificationUri = (payload.verificationUri ?: payload.verificationUrl)
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val expiresIn = payload.expiresIn?.takeIf { it > 0L }
        val interval = payload.interval?.coerceAtLeast(1)
        if (payload.result != "OK" || userCode == null || verificationUri == null || expiresIn == null || interval == null) {
            throw SimklAuthException(SimklAuthError.INVALID_PIN_RESPONSE)
        }
        val session = SimklPinSession(
            userCode = userCode,
            verificationUri = verificationUri,
            expiresAtEpochMs = nowEpochMs() + expiresIn * 1_000L,
            intervalSeconds = interval
        )
        if (!storage.savePinSession(session, authScope)) {
            throw SimklAuthException(SimklAuthError.PIN_INVALIDATED)
        }
        session
    }

    suspend fun pollPin(): SimklPinPollResult = mutex.withLock {
        val authScope = storage.currentScope()
        val session = storage.state.value.pinSession ?: return@withLock SimklPinPollResult.Invalidated
        if (nowEpochMs() >= session.expiresAtEpochMs) {
            storage.clearPinSession(SimklAuthError.PIN_EXPIRED, authScope)
            return@withLock SimklPinPollResult.Expired
        }
        session.deviceCode?.takeIf(String::isNotBlank)?.let { deviceCode ->
            return@withLock pollDeviceAuthV2(session, deviceCode, authScope)
        }
        if (!PIN_PATTERN.matches(session.userCode)) {
            storage.clearPinSession(SimklAuthError.PIN_INVALIDATED, authScope)
            return@withLock SimklPinPollResult.Invalidated
        }
        val response = executeAuthRequest(
            SimklApiRequest(
                method = SimklHttpMethod.GET,
                path = "/oauth/pin/${session.userCode}",
                requiresAuthentication = false
            )
        )
        val payload = decodePinResponse(response.body)
        if (!payload.deviceCode.isNullOrBlank()) {
            storage.clearPinSession(SimklAuthError.PIN_INVALIDATED, authScope)
            return@withLock SimklPinPollResult.Invalidated
        }
        val token = payload.accessToken?.trim()?.takeIf(String::isNotBlank)
        if (payload.result == "OK" && token != null) {
            if (!storage.completePinAuthorization(token, authScope)) {
                return@withLock SimklPinPollResult.Invalidated
            }
            try {
                refreshUserSettings(scope = authScope)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Unit
            }
            return@withLock SimklPinPollResult.Authorized
        }
        if (payload.result == "KO") return@withLock SimklPinPollResult.Pending
        throw SimklAuthException(SimklAuthError.INVALID_PIN_RESPONSE)
    }

    suspend fun refreshUserSettings(
        activityWatermark: String? = null,
        scope: SimklAuthScope = storage.currentScope()
    ): String? {
        if (!storage.isCurrent(scope) || !storage.state.value.isAuthenticated) return null
        val response = apiClient.execute(
            request = SimklApiRequest(SimklHttpMethod.POST, "/users/settings"),
            expectedAuthScope = scope
        )
        val settings = runCatching { json.decodeFromString<SimklUserSettingsResponse>(response.body) }.getOrNull()
            ?: return null
        val username = settings.user?.name?.trim()?.takeIf(String::isNotBlank)
        val accountType = settings.account?.type?.trim()?.takeIf(String::isNotBlank)
        val saved = storage.saveIdentity(
            username = username,
            accountId = settings.account?.id,
            accountType = accountType,
            settingsActivityWatermark = activityWatermark,
            scope = scope
        )
        return username.takeIf { saved }
    }

    /**
     * The plan the account is on, read from `/users/settings` when the stored plan is unknown or does
     * not prove Pro or VIP.
     *
     * Used to check the rewatch setting at the moment the user enables it. A read that fails gives back
     * the stored plan instead of throwing, so a network hiccup cannot break the settings screen: the
     * caller only needs to know whether rewatches are selectable at all.
     */
    suspend fun ensurePlanLoaded(): String? {
        val authScope = storage.currentScope()
        val current = storage.state.value
        if (!storage.isCurrent(authScope) || !current.isAuthenticated) return current.accountType
        if (isSimklRewatchPlanEligible(current.accountType)) return current.accountType
        try {
            refreshUserSettings(scope = authScope)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Unit
        }
        return storage.state.value.accountType
    }

    /**
     * Corrects the cached plan when the account says rewatches are not part of it.
     *
     * Simkl answers a stop with `rewatch_status: "pro_required"` when the account can no longer record
     * rewatches, and its guide names that answer as the moment to correct the cached plan. Without the
     * correction every rewatch decision would keep being made against a plan the account no longer has,
     * so the flag would keep going out and each rewatch would be dropped.
     *
     * Only the plan is written. The user's own setting and the sessions already on their account are
     * left alone, so an account that resubscribes finds everything as it was: a plan that does not
     * prove Pro or VIP is read again, which is where the upgrade is picked up.
     *
     * Answers whether the plan was corrected, so a caller can tell a real correction from an account
     * that was on a plan without rewatches already.
     *
     * The scope is read here rather than passed in, so the method stays a plain call for the caller
     * that only knows the answer it got.
     */
    suspend fun markPlanAsFree(): Boolean {
        val authScope = storage.currentScope()
        if (!storage.isCurrent(authScope) || !storage.state.value.isAuthenticated) return false
        val current = storage.state.value
        if (!isSimklRewatchPlanEligible(current.accountType)) return false
        return storage.saveIdentity(
            username = current.username,
            accountId = current.accountId,
            accountType = SIMKL_FREE_PLAN,
            scope = authScope
        )
    }

    suspend fun synchronizeUserSettings(activityWatermark: String?) {
        val authScope = storage.currentScope()
        if (!storage.state.value.isAuthenticated) return
        try {
            when (simklSettingsRefreshAction(storage.state.value, activityWatermark)) {
                SimklSettingsRefreshAction.NONE -> Unit
                SimklSettingsRefreshAction.RECORD_WATERMARK -> {
                    storage.recordSettingsActivityWatermark(requireNotNull(activityWatermark), authScope)
                }
                SimklSettingsRefreshAction.FETCH -> refreshUserSettings(activityWatermark, authScope)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Unit
        }
    }

    fun cancelPinAuth() = storage.clearPinSession()

    fun disconnect() = storage.clearAuth()

    private suspend fun executeAuthRequest(request: SimklApiRequest): SimklApiResponse = try {
        apiClient.execute(request)
    } catch (error: CancellationException) {
        throw error
    } catch (error: SimklAuthException) {
        throw error
    } catch (error: Throwable) {
        throw SimklAuthException(SimklAuthError.NETWORK, error)
    }

    // [fork] ---- Simkl AUTH V2 device flow (Nuvio C) ----

    private suspend fun startDeviceAuthV2(authScope: SimklAuthScope): SimklPinSession? {
        val response = try {
            apiClient.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = "/oauth2/device",
                    body = simklFormBody(
                        "client_id" to configuration.clientId,
                        "scope" to SIMKL_V2_SCOPE
                    ),
                    requiresAuthentication = false,
                    formEncoded = true
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SimklApiException) {
            // No HTTP status means the network failed; any HTTP error means this is not a V2 app.
            if (error.status == null) throw SimklAuthException(SimklAuthError.NETWORK, error)
            return null
        } catch (error: Throwable) {
            throw SimklAuthException(SimklAuthError.NETWORK, error)
        }
        val payload = runCatching { json.decodeFromString<SimklDeviceCodeResponse>(response.body) }.getOrNull()
            ?: return null
        val deviceCode = payload.deviceCode?.trim()?.takeIf(String::isNotBlank) ?: return null
        val userCode = payload.userCode?.trim()?.takeIf(String::isNotBlank) ?: return null
        val verificationUri = payload.verificationUri?.trim()?.takeIf(String::isNotBlank) ?: return null
        val session = SimklPinSession(
            userCode = userCode,
            verificationUri = verificationUri,
            expiresAtEpochMs = nowEpochMs() + (payload.expiresIn?.takeIf { it > 0L } ?: 900L) * 1_000L,
            intervalSeconds = payload.interval?.coerceAtLeast(1) ?: 5,
            deviceCode = deviceCode,
            // QR opens the approval page with the code already filled in.
            qrUri = payload.verificationUriComplete?.trim()?.takeIf(String::isNotBlank)
        )
        if (!storage.savePinSession(session, authScope)) {
            throw SimklAuthException(SimklAuthError.PIN_INVALIDATED)
        }
        return session
    }

    private suspend fun pollDeviceAuthV2(
        session: SimklPinSession,
        deviceCode: String,
        authScope: SimklAuthScope
    ): SimklPinPollResult {
        val response = try {
            apiClient.execute(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = "/oauth2/token",
                    body = simklFormBody(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id" to configuration.clientId,
                        "device_code" to deviceCode
                    ),
                    requiresAuthentication = false,
                    retryPolicy = SimklRetryPolicy.NEVER,
                    formEncoded = true
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: SimklApiException) {
            val status = error.status ?: throw SimklAuthException(SimklAuthError.NETWORK, error)
            return when {
                error.errorCode == "authorization_pending" -> SimklPinPollResult.Pending
                error.errorCode == "slow_down" -> {
                    storage.savePinSession(session.copy(intervalSeconds = session.intervalSeconds + 5), authScope)
                    SimklPinPollResult.Pending
                }
                error.errorCode == "expired_token" -> {
                    storage.clearPinSession(SimklAuthError.PIN_EXPIRED, authScope)
                    SimklPinPollResult.Expired
                }
                status == 429 || status >= 500 -> SimklPinPollResult.Pending
                else -> {
                    storage.clearPinSession(SimklAuthError.PIN_INVALIDATED, authScope)
                    SimklPinPollResult.Invalidated
                }
            }
        } catch (error: Throwable) {
            throw SimklAuthException(SimklAuthError.NETWORK, error)
        }
        val payload = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }.getOrNull()
        val access = payload?.accessToken?.trim()?.takeIf(String::isNotBlank)
        val refresh = payload?.refreshToken?.trim()?.takeIf(String::isNotBlank)
        if (access == null || refresh == null) {
            throw SimklAuthException(SimklAuthError.INVALID_PIN_RESPONSE)
        }
        val tokens = SimklTokenSet(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = nowEpochMs() + (payload.expiresIn ?: 7L * 24L * 60L * 60L) * 1_000L
        )
        if (!storage.completeDeviceAuthorization(tokens, authScope)) {
            return SimklPinPollResult.Invalidated
        }
        try {
            refreshUserSettings(scope = authScope)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Unit
        }
        return SimklPinPollResult.Authorized
    }

    private fun decodePinResponse(body: String): SimklPinResponse =
        runCatching { json.decodeFromString<SimklPinResponse>(body) }
            .getOrElse { throw SimklAuthException(SimklAuthError.INVALID_PIN_RESPONSE, it) }

    private companion object {
        val PIN_PATTERN = Regex("[A-Za-z0-9]{4,12}")
        const val SIMKL_V2_SCOPE = "media:read media:write" // [fork] read + write for sync/scrobble
    }
}

package com.nuvio.tv.data.simkl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * [fork] Simkl AUTH V2 token renewal (Nuvio C).
 *
 * AUTH V2 access tokens last about 7 days; the refresh token lasts 180 days and its window
 * resets every time it is used. [SimklApiClient] calls this shortly before expiry and once
 * after a 401, so the login stays alive without the user doing anything.
 *
 * Talks to the HTTP engine directly (not through [SimklApiClient]) because it runs while the
 * client's request lock is held. V1 logins have no refresh token, so this is never called for them.
 */
class SimklTokenRefresher(
    private val engine: SimklHttpEngine,
    private val configuration: SimklApiConfiguration,
    private val storage: SimklAuthStorage,
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun refresh(current: SimklAuthorization): SimklRefreshOutcome = mutex.withLock {
        // Another caller may already have renewed it while we waited.
        storage.authorization()?.let { latest ->
            if (latest.scope == current.scope && latest.accessToken != current.accessToken &&
                latest.refreshToken != null
            ) {
                return@withLock SimklRefreshOutcome.Refreshed(latest)
            }
        }
        val refreshToken = current.refreshToken?.takeIf(String::isNotBlank)
            ?: return@withLock SimklRefreshOutcome.Rejected
        val response = try {
            engine.execute(
                method = SimklHttpMethod.POST.name,
                url = buildSimklApiUrl(configuration, "/oauth2/token"),
                headers = simklRequestHeaders(configuration = configuration, formEncoded = true),
                body = simklFormBody(
                    "grant_type" to "refresh_token",
                    "client_id" to configuration.clientId,
                    "refresh_token" to refreshToken
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return@withLock SimklRefreshOutcome.Unavailable
        }
        when {
            response.status in 200..299 -> {
                val payload = runCatching { json.decodeFromString<SimklTokenResponse>(response.body) }.getOrNull()
                val access = payload?.accessToken?.trim()?.takeIf(String::isNotBlank)
                    ?: return@withLock SimklRefreshOutcome.Unavailable
                val tokens = SimklTokenSet(
                    accessToken = access,
                    // Simkl returns the same refresh token with its window extended; keep ours if omitted.
                    refreshToken = payload.refreshToken?.trim()?.takeIf(String::isNotBlank) ?: refreshToken,
                    expiresAtEpochMs = nowEpochMs() + (payload.expiresIn ?: DEFAULT_EXPIRES_IN_SECONDS) * 1_000L
                )
                if (!storage.updateTokens(tokens, current.scope, refreshToken)) {
                    return@withLock SimklRefreshOutcome.Unavailable
                }
                storage.authorization()
                    ?.let { SimklRefreshOutcome.Refreshed(it) }
                    ?: SimklRefreshOutcome.Unavailable
            }
            // invalid_grant / invalid_client: the refresh token is dead, a new login is needed.
            response.status == 400 || response.status == 401 -> SimklRefreshOutcome.Rejected
            else -> SimklRefreshOutcome.Unavailable
        }
    }

    private companion object {
        const val DEFAULT_EXPIRES_IN_SECONDS = 7L * 24L * 60L * 60L
    }
}

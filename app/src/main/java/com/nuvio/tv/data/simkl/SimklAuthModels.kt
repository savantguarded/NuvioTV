package com.nuvio.tv.data.simkl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SimklConnectionMode {
    DISCONNECTED,
    AWAITING_APPROVAL,
    CONNECTED
}

enum class SimklAuthError {
    MISSING_CLIENT_ID,
    INVALID_PIN_RESPONSE,
    PIN_EXPIRED,
    PIN_INVALIDATED,
    AUTHORIZATION_REVOKED,
    NETWORK
}

@Serializable
data class SimklPinSession(
    val userCode: String,
    val verificationUri: String,
    val expiresAtEpochMs: Long,
    val intervalSeconds: Int,
    // [fork] Simkl AUTH V2 device flow: set only for V2 sessions (V1 PIN sessions leave them null).
    val deviceCode: String? = null,
    val qrUri: String? = null
)

data class SimklAuthState(
    val isAuthenticated: Boolean = false,
    val username: String? = null,
    val accountId: Long? = null,
    val hasFetchedUserSettings: Boolean = false,
    val settingsActivityWatermark: String? = null,
    val pinSession: SimklPinSession? = null,
    val error: SimklAuthError? = null
) {
    val mode: SimklConnectionMode
        get() = when {
            isAuthenticated -> SimklConnectionMode.CONNECTED
            pinSession != null -> SimklConnectionMode.AWAITING_APPROVAL
            else -> SimklConnectionMode.DISCONNECTED
        }
}

sealed interface SimklPinPollResult {
    data object Pending : SimklPinPollResult
    data object Authorized : SimklPinPollResult
    data object Expired : SimklPinPollResult
    data object Invalidated : SimklPinPollResult
}

class SimklAuthException(val error: SimklAuthError, cause: Throwable? = null) : Exception(error.name, cause)

@Serializable
internal data class SimklStoredAuthMetadata(
    val username: String? = null,
    val accountId: Long? = null,
    val hasFetchedUserSettings: Boolean = false,
    val settingsActivityWatermark: String? = null,
    val pinSession: SimklPinSession? = null,
    val accessTokenExpiresAtEpochMs: Long? = null // [fork] AUTH V2 token expiry
)

internal enum class SimklSettingsRefreshAction {
    NONE,
    RECORD_WATERMARK,
    FETCH
}

internal fun simklSettingsRefreshAction(
    state: SimklAuthState,
    activityWatermark: String?
): SimklSettingsRefreshAction = when {
    activityWatermark.isNullOrBlank() -> SimklSettingsRefreshAction.NONE
    activityWatermark == state.settingsActivityWatermark -> SimklSettingsRefreshAction.NONE
    state.settingsActivityWatermark == null && state.hasFetchedUserSettings -> {
        SimklSettingsRefreshAction.RECORD_WATERMARK
    }
    else -> SimklSettingsRefreshAction.FETCH
}

@Serializable
internal data class SimklPinResponse(
    val result: String? = null,
    val message: String? = null,
    @SerialName("device_code") val deviceCode: String? = null,
    @SerialName("user_code") val userCode: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_url") val verificationUrl: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val interval: Int? = null,
    @SerialName("access_token") val accessToken: String? = null
)

@Serializable
internal data class SimklUserSettingsResponse(
    val user: SimklUser? = null,
    val account: SimklAccount? = null
)

@Serializable
internal data class SimklUser(val name: String? = null)

@Serializable
internal data class SimklAccount(val id: Long? = null)

// [fork] Simkl AUTH V2 (device flow + refresh tokens) response models.
@Serializable
internal data class SimklDeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String? = null,
    @SerialName("user_code") val userCode: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val interval: Int? = null
)

@Serializable
internal data class SimklTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null
)

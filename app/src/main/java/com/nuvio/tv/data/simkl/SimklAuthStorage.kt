package com.nuvio.tv.data.simkl

import kotlinx.coroutines.flow.StateFlow

data class SimklAuthScope(
    val profileId: Int,
    val generation: Long
)

data class SimklAuthorization(
    val scope: SimklAuthScope,
    val accessToken: String,
    // [fork] AUTH V2 only; V1 tokens never expire and have no refresh token.
    val refreshToken: String? = null,
    val expiresAtEpochMs: Long? = null
)

// [fork] AUTH V2 token set (access token lasts ~7 days, refresh token ~180 days and rotates on use).
data class SimklTokenSet(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long
)

interface SimklAuthStorage {
    val state: StateFlow<SimklAuthState>

    fun currentScope(): SimklAuthScope
    fun isCurrent(scope: SimklAuthScope): Boolean
    fun authorization(): SimklAuthorization?
    fun accessToken(): String? = authorization()?.accessToken
    fun savePinSession(session: SimklPinSession, scope: SimklAuthScope = currentScope()): Boolean
    fun clearPinSession(
        error: SimklAuthError? = null,
        scope: SimklAuthScope = currentScope()
    ): Boolean
    fun completePinAuthorization(token: String, scope: SimklAuthScope): Boolean
    // [fork] AUTH V2: save a fresh login, and swap in refreshed tokens (only if the refresh token still matches).
    fun completeDeviceAuthorization(tokens: SimklTokenSet, scope: SimklAuthScope): Boolean =
        completePinAuthorization(tokens.accessToken, scope)
    fun updateTokens(tokens: SimklTokenSet, scope: SimklAuthScope, expectedRefreshToken: String): Boolean = false
    fun saveIdentity(
        username: String?,
        accountId: Long?,
        settingsActivityWatermark: String? = null,
        scope: SimklAuthScope = currentScope()
    ): Boolean
    fun recordSettingsActivityWatermark(
        watermark: String,
        scope: SimklAuthScope = currentScope()
    ): Boolean
    fun clearAuth(
        error: SimklAuthError? = null,
        scope: SimklAuthScope = currentScope(),
        expectedAccessToken: String? = null
    ): Boolean
    fun removeProfile(profileId: Int)
    fun clearAllProfiles()
}

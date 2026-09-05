package com.nuvio.tv.data.local

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.domain.model.ServerCapabilities
import com.nuvio.tv.domain.model.ServerConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerConfigurationStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun loadActive(): ServerConfiguration {
        if (!BuildConfig.FEATURE_CUSTOM_SERVER_CONNECTIONS_ENABLED) return officialConfiguration()
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val explicitOfficial = preferences.getBoolean(KEY_EXPLICIT_OFFICIAL, false)
        if (!preferences.getBoolean(KEY_CUSTOM_ENABLED, false)) {
            return if (explicitOfficial) officialConfiguration() else primeDefaultConfiguration()
        }
        val backendUrl = preferences.getString(KEY_BACKEND_URL, null)?.trim().orEmpty()
        val publishableKey = preferences.getString(KEY_PUBLISHABLE_KEY, null)?.trim().orEmpty()
        val emailPasswordAuth = preferences.getBoolean(KEY_EMAIL_PASSWORD_AUTH, false)
        val tvLogin = preferences.getBoolean(KEY_TV_LOGIN, false)
        if (backendUrl.isBlank() || publishableKey.isBlank() || (!emailPasswordAuth && !tvLogin)) {
            return if (explicitOfficial) officialConfiguration() else primeDefaultConfiguration()
        }
        return ServerConfiguration(
            backendUrl = backendUrl,
            publishableKey = publishableKey,
            capabilities = ServerCapabilities(
                emailPasswordAuth = emailPasswordAuth,
                tvLogin = tvLogin
            ),
            isCustom = true,
            discoveryUrl = preferences.getString(KEY_DISCOVERY_URL, null),
            tvLoginWebBaseUrl = "$backendUrl/tv-login",
            deviceLoginWebBaseUrl = "$backendUrl/link",
            avatarPublicBaseUrl = "$backendUrl/storage/v1/object/public/avatars"
        )
    }

    fun saveCustom(configuration: ServerConfiguration): Boolean {
        if (!BuildConfig.FEATURE_CUSTOM_SERVER_CONNECTIONS_ENABLED) return false
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CUSTOM_ENABLED, true)
            .putBoolean(KEY_EXPLICIT_OFFICIAL, false)
            .putString(KEY_BACKEND_URL, configuration.backendUrl)
            .putString(KEY_PUBLISHABLE_KEY, configuration.publishableKey)
            .putBoolean(KEY_EMAIL_PASSWORD_AUTH, configuration.capabilities.emailPasswordAuth)
            .putBoolean(KEY_TV_LOGIN, configuration.capabilities.tvLogin)
            .putString(KEY_DISCOVERY_URL, configuration.discoveryUrl)
            .commit()
    }

    // Sticks even after this fork's own prime default would otherwise apply again on the
    // next loadActive() call, so a user who deliberately opts back into the official
    // server (Settings -> "use official server") doesn't get silently switched back.
    fun useOfficial(): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .putBoolean(KEY_EXPLICIT_OFFICIAL, true)
            .commit()

    private fun officialConfiguration() = ServerConfiguration(
        backendUrl = BuildConfig.SUPABASE_URL.trimEnd('/'),
        publishableKey = BuildConfig.SUPABASE_ANON_KEY,
        capabilities = ServerCapabilities(
            emailPasswordAuth = false,
            tvLogin = true
        ),
        isCustom = false,
        fallbackBackendUrl = BuildConfig.SUPABASE_FALLBACK_URL.trim().trimEnd('/').takeIf { it.isNotBlank() },
        tvLoginWebBaseUrl = BuildConfig.TV_LOGIN_WEB_BASE_URL,
        deviceLoginWebBaseUrl = BuildConfig.DEVICE_LOGIN_WEB_BASE_URL,
        avatarPublicBaseUrl = BuildConfig.AVATAR_PUBLIC_BASE_URL.trimEnd('/').takeIf { it.isNotBlank() }
    )

    // Same backend as officialConfiguration() (this fork bakes in its own authorized
    // Supabase credentials as the build's default, see NuvioSyncDefaults in
    // app/build.gradle.kts), but reported as a custom connection so
    // AccountViewModel.usesEmailPasswordLogin (isCustom && capabilities.emailPasswordAuth)
    // unlocks email/password sign-in automatically — no manual "Custom Server Connection"
    // setup step needed on a fresh install.
    private fun primeDefaultConfiguration() = officialConfiguration().copy(
        capabilities = ServerCapabilities(
            emailPasswordAuth = true,
            tvLogin = true
        ),
        isCustom = true
    )

    private companion object {
        const val PREFERENCES_NAME = "server_configuration"
        const val KEY_CUSTOM_ENABLED = "custom_enabled"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_PUBLISHABLE_KEY = "publishable_key"
        const val KEY_EMAIL_PASSWORD_AUTH = "email_password_auth"
        const val KEY_TV_LOGIN = "tv_login"
        const val KEY_DISCOVERY_URL = "discovery_url"
        const val KEY_EXPLICIT_OFFICIAL = "explicit_official"
    }
}

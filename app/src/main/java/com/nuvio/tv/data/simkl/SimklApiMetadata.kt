package com.nuvio.tv.data.simkl

import com.nuvio.tv.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl

data class SimklApiConfiguration(
    val clientId: String,
    val appName: String,
    val appVersion: String,
    val baseUrl: String = "https://api.simkl.com"
)

fun buildSimklApiUrl(
    configuration: SimklApiConfiguration,
    path: String,
    query: Map<String, String> = emptyMap()
): String {
    val normalizedPath = path.trim().let { value -> if (value.startsWith('/')) value else "/$value" }
    val builder = configuration.baseUrl.toHttpUrl().newBuilder().encodedPath(normalizedPath)
    // [fork] AUTH V2 OAuth endpoints take client_id in the form body, not the query string.
    if (normalizedPath.startsWith("/oauth2/")) {
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build().toString()
    }
    query.filterKeys { it !in SIMKL_REQUIRED_QUERY_KEYS }.forEach { (key, value) ->
        builder.addQueryParameter(key, value)
    }
    builder.addQueryParameter("client_id", configuration.clientId)
    builder.addQueryParameter("app-name", configuration.appName)
    builder.addQueryParameter("app-version", configuration.appVersion)
    return builder.build().toString()
}

fun simklRequestHeaders(
    configuration: SimklApiConfiguration,
    accessToken: String? = null,
    contentTypeJson: Boolean = false,
    formEncoded: Boolean = false // [fork] AUTH V2 OAuth endpoints
): Map<String, String> = buildMap {
    put("User-Agent", "$SIMKL_USER_AGENT_APP_NAME/${configuration.appVersion}")
    put("Accept", "application/json")
    accessToken?.trim()?.takeIf(String::isNotBlank)?.let { token ->
        put("Authorization", "Bearer $token")
    }
    if (formEncoded) {
        put("Content-Type", SIMKL_FORM_CONTENT_TYPE)
    } else if (contentTypeJson) {
        put("Content-Type", "application/json")
    }
}

fun defaultSimklApiConfiguration(): SimklApiConfiguration = SimklApiConfiguration(
    clientId = BuildConfig.SIMKL_CLIENT_ID,
    appName = BuildConfig.SIMKL_APP_NAME.ifBlank { "nuvio" },
    appVersion = BuildConfig.VERSION_NAME.ifBlank { "dev" }
)

private val SIMKL_REQUIRED_QUERY_KEYS = setOf("client_id", "app-name", "app-version")
private const val SIMKL_USER_AGENT_APP_NAME = "NuvioTV"

internal const val SIMKL_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"

// [fork] URL-encoded form body for the AUTH V2 OAuth endpoints.
internal fun simklFormBody(vararg fields: Pair<String, String>): String =
    fields.joinToString("&") { (key, value) ->
        "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
    }

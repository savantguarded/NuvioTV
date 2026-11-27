package com.nuvio.tv.core.nuvioc.health

// [fork] Nuvio C round 67 `ADDON_HEALTH`, security (board 5d): every health call goes through this client and only
// this one. No interceptors (the app's shared clients add logging, headers and Sentry breadcrumbs), no cookie jar,
// no cache, no automatic redirects (one same-host https redirect is followed by hand), https only, 6 s per call,
// at most 2 MB read. Status sources get a bare GET; the TorBox key is only ever sent to api.torbox.app.

import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One answer: [code] null = no answer (timeout, refused, too big, not https). [reason] is short and never an address. */
data class HealthResponse(val code: Int?, val body: String?, val latencyMs: Int?, val reason: String?)

object NuvioCHealthHttp {
    private const val USER_AGENT = "NuvioC-Health/1"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .cache(null)
            .retryOnConnectionFailure(false)
            .callTimeout(NuvioCHealthRules.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(NuvioCHealthRules.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(NuvioCHealthRules.TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * A bare GET (fixed User-Agent and Accept only). [bearer] is attached only when the host is exactly
     * api.torbox.app; for any other host it is dropped. Blocking: call from Dispatchers.IO.
     */
    fun get(address: String, bearer: String? = null, readBody: Boolean = true): HealthResponse {
        var url = address
        var hops = 0
        val started = System.nanoTime()
        while (true) {
            val parsed = url.toHttpUrlOrNull() ?: return HealthResponse(null, null, null, "Not a valid address")
            if (parsed.scheme != "https") return HealthResponse(null, null, null, "Not https")
            val builder = Request.Builder().url(parsed).get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
            if (bearer != null && parsed.host == NuvioCHealthRules.TORBOX_API_HOST) builder.header("Authorization", "Bearer $bearer")
            try {
                client.newCall(builder.build()).execute().use { resp ->
                    if (resp.isRedirect) {
                        val to = resp.header("Location")?.let { parsed.resolve(it)?.toString() }
                        if (!NuvioCHealthRules.redirectAllowed(url, to, hops)) {
                            return HealthResponse(null, null, ms(started), "Redirected elsewhere")
                        }
                        hops++
                        url = to!!
                        return@use
                    }
                    val code = resp.code
                    val body = if (readBody && code in 200..299) readCapped(resp.body?.byteStream()) else null
                    if (readBody && code in 200..299 && body == null) return HealthResponse(null, null, ms(started), "Too large")
                    return HealthResponse(code, body, ms(started), reasonFor(code))
                }
            } catch (e: IOException) {
                val timedOut = e is java.io.InterruptedIOException || e.message?.contains("timeout", true) == true
                return HealthResponse(null, null, ms(started), if (timedOut) "Timed out" else "No answer")
            } catch (e: Exception) {
                return HealthResponse(null, null, ms(started), "No answer")
            }
        }
    }

    private fun ms(startedNs: Long) = ((System.nanoTime() - startedNs) / 1_000_000L).toInt().coerceIn(0, 60_000)

    /** Short reason for a status code. Pure, for tests. */
    fun reasonFor(code: Int): String? = when (code) {
        in 200..299 -> null
        401 -> "Blocked (401)"
        403 -> "Blocked (403)"
        404 -> "Not found (404)"
        429 -> "Rate-limited (429)"
        in 500..599 -> "Server error ($code)"
        else -> "Answered $code"
    }

    /** Reads at most 2 MB; more = null (the stream is closed by the caller's use{}). */
    internal fun readCapped(stream: java.io.InputStream?, max: Long = NuvioCHealthRules.MAX_BYTES): String? {
        stream ?: return null
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

package com.nuvio.tv

// [fork] Nuvio C image cache (from ysosrs123's fork, disk part only). Official keeps 200 MB of
// posters, backdrops and logos on disk; here it is 10% of the free space, at least 256 MB and at
// most 1 GB (worked out at each app start, so it shrinks as the TV fills up). Images whose server
// sends no caching rules (rating posters and the like) are kept for a week instead of being
// downloaded again every time a row comes back into view. Memory use is unchanged.
// Switch: NuvioCFeatures.IMAGE_CACHE.

import coil3.disk.DiskCache
import okhttp3.Interceptor

internal object NuvioCImageCache {
    private const val MB = 1024L * 1024L
    private const val WEEK_SECONDS = 7 * 24 * 60 * 60

    fun size(builder: DiskCache.Builder) {
        builder
            .maxSizePercent(0.10)
            .minimumMaxSizeBytes(256L * MB)
            .maximumMaxSizeBytes(1024L * MB)
    }

    /** Responses with no usable Cache-Control are treated as fresh for a week. */
    val weekDefaultInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) return@Interceptor response
        val cc = response.header("Cache-Control")?.lowercase()
        if (cc == null || "no-store" in cc || "no-cache" in cc || "max-age=0" in cc) {
            response.newBuilder()
                .header("Cache-Control", "public, max-age=$WEEK_SECONDS")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        } else {
            response
        }
    }
}

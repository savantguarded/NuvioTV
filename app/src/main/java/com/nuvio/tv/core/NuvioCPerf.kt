package com.nuvio.tv.core

import android.os.SystemClock
import android.util.Log

// [fork] Nuvio C (2026-10-17): timing lines for "opening a details page takes a beat", so the delay can be
// split into navigation / first frame / meta arriving on the TV: `logcat -d -s NuvioCPerf`.
// Log only, no behaviour change.
object NuvioCPerf {
    private const val TAG = "NuvioCPerf"

    @Volatile
    private var detailOpenAtMs = 0L

    fun markDetailOpen() {
        detailOpenAtMs = SystemClock.uptimeMillis()
        mark("details open")
    }

    /**
     * [fork] round 64b trace_labels: a zero-length named slice on the calling thread, so a perfetto trace of the
     * release build shows where a screen opened / the OSD toggled / a key was handled (names are obfuscated there).
     */
    fun mark(label: String) {
        if (!com.nuvio.tv.NuvioCFeatures.TRACE_LABELS) return
        android.os.Trace.beginSection("NuvioC " + label.take(100))
        android.os.Trace.endSection()
    }

    /** Round 64b: [block] as a named slice in a trace. */
    inline fun <T> section(label: String, block: () -> T): T {
        if (!com.nuvio.tv.NuvioCFeatures.TRACE_LABELS) return block()
        android.os.Trace.beginSection("NuvioC " + label.take(100))
        try { return block() } finally { android.os.Trace.endSection() }
    }

    fun detailStage(stage: String, last: Boolean = false) {
        val start = detailOpenAtMs
        if (start == 0L) return
        Log.i(TAG, "details $stage +${SystemClock.uptimeMillis() - start} ms")
        mark("details $stage")
        if (last) detailOpenAtMs = 0L
    }
}

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
    }

    fun detailStage(stage: String, last: Boolean = false) {
        val start = detailOpenAtMs
        if (start == 0L) return
        Log.i(TAG, "details $stage +${SystemClock.uptimeMillis() - start} ms")
        if (last) detailOpenAtMs = 0L
    }
}

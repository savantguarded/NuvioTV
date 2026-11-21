package com.nuvio.tv.ui.screens.detail

// [fork] round 57c `DETAIL_FOCUS_LOG`: info lines (kept in release) that trace the details page focus restore, so a
// focus bug can be read over adb (`adb logcat -s NuvioCFocus`). Switch off once the restore is settled.
internal object NuvioCFocusLog {
    fun i(msg: String) { if (com.nuvio.tv.NuvioCFeatures.DETAIL_FOCUS_LOG) android.util.Log.i("NuvioCFocus", msg) }
}

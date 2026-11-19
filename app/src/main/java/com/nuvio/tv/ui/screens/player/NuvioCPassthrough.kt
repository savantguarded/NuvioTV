package com.nuvio.tv.ui.screens.player

import android.content.Context
import androidx.media3.common.C
import androidx.compose.runtime.mutableStateOf
import com.nuvio.tv.NuvioCFeatures

/**
 * [fork] round 51 passthrough_formats: which audio formats are sent to the soundbar as they are.
 * Auto (default) = the app's normal behaviour, untouched. Choose = each format has a switch; a format switched off
 * is taken out of what the TV tells the player it can pass through, so the player decodes it to PCM instead.
 * Kept on this TV only (SharedPreferences), read when a player starts; it never touches the video path.
 */
object NuvioCPassthrough {
    private const val PREFS = "nuvio_c_audio"
    enum class Format(val key: String, val encodings: IntArray) {
        DD("pt_dd", intArrayOf(C.ENCODING_AC3)),
        DDP("pt_ddp", intArrayOf(C.ENCODING_E_AC3, C.ENCODING_E_AC3_JOC)),
        TRUEHD("pt_truehd", intArrayOf(C.ENCODING_DOLBY_TRUEHD)),
        DTS("pt_dts", intArrayOf(C.ENCODING_DTS)),
        DTSHD("pt_dtshd", intArrayOf(C.ENCODING_DTS_HD, C.ENCODING_DTS_UHD_P2))
    }

    val choose = mutableStateOf(false)
    private val on = mutableMapOf<Format, androidx.compose.runtime.MutableState<Boolean>>()
    @Volatile private var loaded = false

    fun state(f: Format) = on.getOrPut(f) { mutableStateOf(f != Format.DTS && f != Format.DTSHD) }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        choose.value = p.getBoolean("pt_choose", false)
        Format.entries.forEach { state(it).value = p.getBoolean(it.key, it != Format.DTS && it != Format.DTSHD) }
    }

    fun setChoose(context: Context, value: Boolean) {
        load(context)
        choose.value = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("pt_choose", value).apply()
    }

    fun set(context: Context, f: Format, value: Boolean) {
        load(context)
        state(f).value = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(f.key, value).apply()
    }

    /** Whether the player must drop some formats from the TV's list. */
    fun restricts(context: Context): Boolean {
        if (!NuvioCFeatures.PASSTHROUGH_FORMATS) return false
        load(context)
        return choose.value && Format.entries.any { !state(it).value }
    }

    /** Encodings that stay allowed out of [supported]. */
    fun filter(context: Context, supported: List<Int>): List<Int> {
        if (!restricts(context)) return supported
        val off = Format.entries.filter { !state(it).value }.flatMap { it.encodings.toList() }.toSet()
        return supported.filter { it !in off }
    }
}

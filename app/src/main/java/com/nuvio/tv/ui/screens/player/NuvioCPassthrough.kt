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

    // [fork] round 53 passthrough_per_profile: every value is kept per profile on this TV (never synced). The active
    // profile comes from ProfileManager; a profile switch reloads that profile's values the next time they are read.
    @Volatile var profileIdProvider: () -> Int = { 1 }
    @Volatile private var loadedFor: Int? = null
    private const val PROFILE_KEYS = "pt_choose|pt_dd|pt_ddp|pt_truehd|pt_dts|pt_dtshd|pt_lift|pt_surround"

    /** The stored key for [key] in the active profile (or the TV-wide key with the switch off). */
    internal fun key(key: String): String =
        if (NuvioCFeatures.PASSTHROUGH_PER_PROFILE) "p${profileIdProvider()}_$key" else key

    /**
     * One-time copy: a profile with no saved values of its own starts from what this TV had before round 53, so
     * nothing changes for anyone on the first launch; from then on each profile keeps its own.
     */
    internal fun migrate(p: android.content.SharedPreferences, profile: Int) {
        if (!NuvioCFeatures.PASSTHROUGH_PER_PROFILE || p.getBoolean("p${profile}_migrated", false)) return
        val e = p.edit()
        PROFILE_KEYS.split('|').forEach { k ->
            when (val v = p.all[k]) {
                is Boolean -> if (!p.contains("p${profile}_$k")) e.putBoolean("p${profile}_$k", v)
                is String -> if (!p.contains("p${profile}_$k")) e.putString("p${profile}_$k", v)
            }
        }
        e.putBoolean("p${profile}_migrated", true).apply()
    }

    fun state(f: Format) = on.getOrPut(f) { mutableStateOf(f != Format.DTS && f != Format.DTSHD) }

    fun load(context: Context) {
        val profile = profileIdProvider()
        if (loaded && (!NuvioCFeatures.PASSTHROUGH_PER_PROFILE || loadedFor == profile)) return
        loaded = true
        loadedFor = profile
        extrasLoaded = false
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        migrate(p, profile)
        choose.value = p.getBoolean(key("pt_choose"), false)
        Format.entries.forEach { state(it).value = p.getBoolean(key(it.key), it != Format.DTS && it != Format.DTSHD) }
        ensureExtras(context) // [fork] round 52
    }

    fun setChoose(context: Context, value: Boolean) {
        load(context)
        choose.value = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key("pt_choose"), value).apply()
    }

    fun set(context: Context, f: Format, value: Boolean) {
        load(context)
        state(f).value = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key(f.key), value).apply()
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

    // ---- [fork] round 52 decoded_dialogue_lift / decoded_surround -------------------------------------------------
    /** How much dialogue is raised on a switched-off format decoded on the TV (Off / Light / Medium / Strong). */
    val lift = mutableStateOf(com.nuvio.tv.data.local.NightModeLevel.MEDIUM)
    /** Ask the TV for 6 / 8 channel PCM when decoding, instead of the channel count it reports (often 2). */
    val surround = mutableStateOf(true)

    private fun loadExtras(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lift.value = com.nuvio.tv.data.local.NightModeLevel.fromKey(p.getString(key("pt_lift"), com.nuvio.tv.data.local.NightModeLevel.MEDIUM.key))
        surround.value = p.getBoolean(key("pt_surround"), true)
    }

    fun setLift(context: Context, level: com.nuvio.tv.data.local.NightModeLevel) {
        load(context)
        lift.value = level
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key("pt_lift"), level.key).apply()
    }

    fun setSurround(context: Context, value: Boolean) {
        load(context)
        surround.value = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key("pt_surround"), value).apply()
    }

    @Volatile private var extrasLoaded = false
    private fun ensureExtras(context: Context) {
        if (NuvioCFeatures.PASSTHROUGH_PER_PROFILE && loadedFor != profileIdProvider()) { load(context); return }
        if (extrasLoaded) return
        extrasLoaded = true
        loadExtras(context)
    }

    /** True when the gain processor must stay in the chain so the lift can follow track changes. */
    fun liftArmed(context: Context): Boolean {
        if (!NuvioCFeatures.DECODED_DIALOGUE_LIFT || !restricts(context)) return false
        ensureExtras(context)
        return lift.value != com.nuvio.tv.data.local.NightModeLevel.OFF
    }

    /** True when decoding should open a multichannel output. */
    fun wantsSurround(context: Context): Boolean {
        if (!NuvioCFeatures.DECODED_SURROUND || !restricts(context)) return false
        ensureExtras(context)
        return surround.value
    }

    /** The format a track's MIME type belongs to, or null for PCM / AAC and the rest (never passed through). */
    fun formatOf(mime: String?): Format? {
        val m = mime?.lowercase() ?: return null
        return when {
            m == "audio/ac3" -> Format.DD
            m == "audio/eac3" || m == "audio/eac3-joc" -> Format.DDP
            m == "audio/true-hd" -> Format.TRUEHD
            m == "audio/vnd.dts" -> Format.DTS
            m.startsWith("audio/vnd.dts.hd") || m.startsWith("audio/vnd.dts.uhd") -> Format.DTSHD
            else -> null
        }
    }

    /** The lift for the playing track: the chosen level when its format is switched off, else Off. */
    fun liftFor(context: Context, mime: String?): com.nuvio.tv.data.local.NightModeLevel {
        if (!liftArmed(context)) return com.nuvio.tv.data.local.NightModeLevel.OFF
        val f = formatOf(mime) ?: return com.nuvio.tv.data.local.NightModeLevel.OFF
        return if (state(f).value) com.nuvio.tv.data.local.NightModeLevel.OFF else lift.value
    }

    /** The lift on the playing track (Off when it isn't a decoded switched-off format); read by Stream info. */
    val playingLift = mutableStateOf(com.nuvio.tv.data.local.NightModeLevel.OFF)
}

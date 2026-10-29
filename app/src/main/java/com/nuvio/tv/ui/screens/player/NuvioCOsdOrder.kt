package com.nuvio.tv.ui.screens.player

import android.content.Context
import androidx.annotation.StringRes
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// [fork] Nuvio C round 31 (switch `osd_button_order`): the order of the player's buttons and which ones sit
// in the main row or behind More, set in Settings > Playback > Player buttons. Play / Pause stays first and
// the More arrow last; Watch Party and Report keep their own rules. The default list is exactly the fork's
// current layout, so nothing moves until you change it. Kept on this TV (fork prefs).

internal enum class NuvioCOsdButton(val key: String, @StringRes val label: Int, val defaultInMore: Boolean) {
    START_OVER("start_over", R.string.nuvio_c_start_over, false),
    NEXT("next", R.string.next_episode_label, false),
    SUBTITLES("subtitles", R.string.cd_subtitles, false),
    AUDIO("audio", R.string.cd_audio_tracks, false),
    CHAPTERS("chapters", R.string.player_chapters, false),
    CAST("cast", R.string.nuvio_c_osd_cast, false),
    SOURCES("sources", R.string.cd_sources, false),
    EPISODES("episodes", R.string.cd_episodes, false),
    SPEED("speed", R.string.cd_playback_speed, true),
    ASPECT("aspect", R.string.cd_aspect_ratio, true),
    ENGINE("engine", R.string.cd_switch_player_engine, true),
    EXTERNAL("external", R.string.cd_open_external_player, true),
    INFO("info", R.string.cd_stream_info, true);

    companion object {
        fun byKey(key: String): NuvioCOsdButton? = entries.firstOrNull { it.key == key }
    }
}

internal data class NuvioCOsdSlot(val button: NuvioCOsdButton, val inMore: Boolean)

internal object NuvioCOsdOrder {
    private const val PREFS = "nuvio_c_osd"
    private const val KEY = "button_order"
    val defaults: List<NuvioCOsdSlot> = NuvioCOsdButton.entries.map { NuvioCOsdSlot(it, it.defaultInMore) }
    private val _slots = MutableStateFlow(defaults)
    val slots: StateFlow<List<NuvioCOsdSlot>> = _slots.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context): List<NuvioCOsdSlot> {
        if (!NuvioCFeatures.OSD_BUTTON_ORDER) return defaults
        if (!loaded) {
            loaded = true
            val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            _slots.value = parse(raw)
        }
        return _slots.value
    }

    fun save(context: Context, slots: List<NuvioCOsdSlot>) {
        _slots.value = slots
        loaded = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(slots)).apply()
    }

    fun reset(context: Context) = save(context, defaults)

    internal fun encode(slots: List<NuvioCOsdSlot>): String =
        slots.joinToString(",") { "${it.button.key}:${if (it.inMore) "x" else "m"}" }

    /** Saved order; buttons added in a later version join at their default place, unknown keys are dropped. */
    internal fun parse(raw: String?): List<NuvioCOsdSlot> {
        if (raw.isNullOrBlank()) return defaults
        val read = raw.split(',').mapNotNull { part ->
            val button = NuvioCOsdButton.byKey(part.substringBefore(':')) ?: return@mapNotNull null
            NuvioCOsdSlot(button, part.substringAfter(':', "m") == "x")
        }.distinctBy { it.button }
        val missing = defaults.filter { d -> read.none { it.button == d.button } }
        return read + missing
    }

    fun moved(slots: List<NuvioCOsdSlot>, index: Int, delta: Int): List<NuvioCOsdSlot> {
        val target = index + delta
        if (index !in slots.indices || target !in slots.indices) return slots
        return slots.toMutableList().apply { add(target, removeAt(index)) }
    }

    fun toggled(slots: List<NuvioCOsdSlot>, index: Int): List<NuvioCOsdSlot> =
        slots.mapIndexed { i, s -> if (i == index) s.copy(inMore = !s.inMore) else s }
}

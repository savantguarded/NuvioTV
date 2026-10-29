package com.nuvio.tv.ui.screens.player

import android.content.Context
import android.util.Log
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.data.local.NightModeLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.pow

// [fork] Nuvio C round 31 (switch `night_mode`): ysosrs123's night mode. Off / Light / Medium / Strong lift
// quiet moments while loud ones stay as they were mixed (PcmDynamics, in GainAudioProcessor for ExoPlayer and
// an acompressor + alimiter chain for mpv).
//
// Passthrough: night mode can only change sound the app itself produces. When a level is chosen while the
// soundbar is getting the bitstream (Atmos, TrueHD, DD+...), the audio sink is told to decode to PCM with the
// same live switch official uses for Bluetooth and non-1x speeds (PlaybackSpeedAwareAudioSink), so the
// player keeps its place and only the sound path changes. Off hands the bitstream back the same way. No
// decoder code is touched: the app's existing decoders do the decoding.
//
// One device-wide choice (kept in the fork's own prefs), shown in the player's Audio panel and in
// Settings > Playback > Audio.

object NuvioCNight {
    private const val PREFS = "nuvio_c_audio"
    private const val KEY = "night_mode"
    private const val KEY_ENABLED = "night_enabled" // [fork] round 32 night_mode_v2
    private const val KEY_PERSIST = "night_persist" // mirror of the player's "Persist between sessions"
    private val _level = MutableStateFlow(NightModeLevel.OFF)
    val level: StateFlow<NightModeLevel> = _level.asStateFlow()
    private val _enabled = MutableStateFlow(true)
    /** [fork] round 32: the Settings switch. Off = no night mode control in the player, audio path untouched. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    @Volatile private var loaded = false
    @Volatile private var persist = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _enabled.value = !NuvioCFeatures.NIGHT_MODE_V2 || p.getBoolean(KEY_ENABLED, true)
        persist = p.getBoolean(KEY_PERSIST, false)
        val saved = NightModeLevel.fromKey(p.getString(KEY, null))
        _level.value = when {
            !_enabled.value -> NightModeLevel.OFF
            NuvioCFeatures.NIGHT_MODE_V2 && !persist -> NightModeLevel.OFF
            else -> saved
        }
    }

    /** True when the player should offer and apply night mode at all. */
    fun active(context: Context): Boolean {
        if (!NuvioCFeatures.NIGHT_MODE) return false
        ensureLoaded(context)
        return _enabled.value
    }

    fun load(context: Context): NightModeLevel {
        if (!NuvioCFeatures.NIGHT_MODE) return NightModeLevel.OFF
        ensureLoaded(context)
        return if (_enabled.value) _level.value else NightModeLevel.OFF
    }

    /**
     * [fork] round 32: a new player session (a new player screen) starts with the saved level when
     * "Persist between sessions" is on, else Off. Episodes and engine switches inside the session keep it.
     */
    fun beginSession(context: Context) {
        if (!NuvioCFeatures.NIGHT_MODE || !NuvioCFeatures.NIGHT_MODE_V2) return
        ensureLoaded(context)
        _level.value = when {
            !_enabled.value -> NightModeLevel.OFF
            persist -> NightModeLevel.fromKey(prefs(context).getString(KEY, null))
            else -> NightModeLevel.OFF
        }
    }

    fun save(context: Context, level: NightModeLevel) {
        ensureLoaded(context)
        _level.value = level
        if (!NuvioCFeatures.NIGHT_MODE_V2 || persist) prefs(context).edit().putString(KEY, level.key).apply()
    }

    /** Keeps the night level saved together with the player's "Persist between sessions" setting. */
    fun setPersist(context: Context, enabled: Boolean) {
        if (!NuvioCFeatures.NIGHT_MODE_V2) return
        ensureLoaded(context)
        if (persist == enabled) return
        persist = enabled
        val editor = prefs(context).edit().putBoolean(KEY_PERSIST, enabled)
        if (enabled) editor.putString(KEY, _level.value.key)
        editor.apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        _enabled.value = enabled
        if (!enabled) _level.value = NightModeLevel.OFF
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun next(level: NightModeLevel, step: Int): NightModeLevel {
        val all = NightModeLevel.entries
        return all[(all.indexOf(level) + step).coerceIn(0, all.lastIndex)]
    }
}

/** mpv: the compressor (same presets as PcmDynamics) and the limiter, or "" when night mode is off. */
internal fun nuvioCMpvNightChain(level: NightModeLevel): String {
    val preset = PcmDynamics.presetFor(level) ?: return ""
    fun linear(db: Float) = String.format(Locale.ROOT, "%.4f", 10f.pow(db / 20f))
    fun number(v: Float) = String.format(Locale.ROOT, "%.4f", v)
    return "lavfi=[acompressor=threshold=${linear(preset.thresholdDb)}:ratio=${number(preset.ratio)}" +
        ":attack=10:release=350:knee=2:makeup=${linear(preset.makeupDb)}:detection=peak:link=maximum," +
        "alimiter=limit=${number(PcmDynamics.LIMITER_CEILING)}:attack=1:release=100:level=0]"
}

/** Applies [level] to the playing title (and saves it as the choice for the next ones). */
internal fun PlayerRuntimeController.nuvioCApplyNightMode(level: NightModeLevel, save: Boolean = true) {
    if (!NuvioCFeatures.NIGHT_MODE) return
    if (NuvioCFeatures.NIGHT_MODE_V2 && !NuvioCNight.active(context)) return // [fork] round 32: switched off in Settings
    if (save) NuvioCNight.save(context, level)
    val on = level != NightModeLevel.OFF
    if (isUsingMpvEngine()) {
        mpvView?.nuvioCApplyNightMode(level)
        return
    }
    val wasActive = gainAudioProcessor.isGainEnabled() || gainAudioProcessor.isNightModeEnabled()
    gainAudioProcessor.setNightMode(level)
    val nowActive = gainAudioProcessor.isGainEnabled() || gainAudioProcessor.isNightModeEnabled()
    val pathChanged = playbackSpeedAwareAudioSink?.nuvioCSetNightForcePcm(on) == true
    if (pathChanged || wasActive != nowActive) {
        Log.d("NuvioCNight", "night=$level pcmPathChanged=$pathChanged processor=$nowActive")
        val wasPlaying = hasActivePlayIntent() && !userPausedManually
        playbackSpeedAwareAudioSink?.notifyAudioProcessingRequirementChanged()
        _exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().build()
            if (!wasPlaying) {
                player.playWhenReady = false
                player.pause()
            }
        }
    }
}

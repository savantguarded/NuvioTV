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
    private val _level = MutableStateFlow(NightModeLevel.OFF)
    val level: StateFlow<NightModeLevel> = _level.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context): NightModeLevel {
        if (!NuvioCFeatures.NIGHT_MODE) return NightModeLevel.OFF
        if (!loaded) {
            loaded = true
            _level.value = NightModeLevel.fromKey(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            )
        }
        return _level.value
    }

    fun save(context: Context, level: NightModeLevel) {
        _level.value = level
        loaded = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, level.key).apply()
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

package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 66 SYNC_AUDIO_NOW: "Sync to audio now" in the Modern subtitles panel. Runs official beta.4's
// audio-based sync (AudioSyncFallback) for the add-on subtitle on screen, this episode only, without waiting for
// automatic sync to give up first. Automatic sync is left exactly as official ships it.

import com.nuvio.tv.ui.screens.player.audiosync.AudioSamplingMode
import com.nuvio.tv.ui.screens.player.audiosync.AudioSyncFallback
import com.nuvio.tv.ui.screens.player.audiosync.AudioSyncSettings

/** Why the button can or can't run now. */
internal enum class NuvioCAudioSyncNow { STARTED, NO_ADDON_SUBTITLE, SETTING_OFF, MPV_SAMPLING_OFF, UNAVAILABLE }

/** Pure, for tests: what the button does with these facts. */
internal fun nuvioCAudioSyncNowCheck(hasAddonSubtitle: Boolean, settingOn: Boolean, mpv: Boolean, samplingOff: Boolean): NuvioCAudioSyncNow = when {
    !hasAddonSubtitle -> NuvioCAudioSyncNow.NO_ADDON_SUBTITLE
    !settingOn -> NuvioCAudioSyncNow.SETTING_OFF
    mpv && samplingOff -> NuvioCAudioSyncNow.MPV_SAMPLING_OFF
    else -> NuvioCAudioSyncNow.STARTED
}

internal fun PlayerRuntimeController.nuvioCSyncToAudioNow(): NuvioCAudioSyncNow {
    val subtitle = _uiState.value.selectedAddonSubtitle
    val check = nuvioCAudioSyncNowCheck(
        hasAddonSubtitle = subtitle != null,
        settingOn = AudioSyncSettings.fallbackEnabled.value,
        mpv = isUsingMpvEngine(),
        samplingOff = AudioSyncSettings.samplingMode.value == AudioSamplingMode.OFF
    )
    if (check != NuvioCAudioSyncNow.STARTED || subtitle == null) return check
    val fallback = AudioSyncFallback.of(this) ?: return NuvioCAudioSyncNow.UNAVAILABLE
    fallback.arm()
    return if (fallback.takeOver(subtitle.url)) NuvioCAudioSyncNow.STARTED else NuvioCAudioSyncNow.UNAVAILABLE
}

/** The panel reaches the running player through this (set by PlayerScreen while it is shown). */
internal object NuvioCAudioSyncHook {
    @Volatile var run: (() -> NuvioCAudioSyncNow)? = null
}

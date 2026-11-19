package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

internal class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var gainDb: Int = AUDIO_AMPLIFICATION_MIN_DB

    @Volatile
    private var gainScale: Float = 1f

    fun setGainDb(db: Int) {
        val clampedDb = db.coerceIn(AUDIO_AMPLIFICATION_MIN_DB, AUDIO_AMPLIFICATION_MAX_DB)
        gainDb = clampedDb
        gainScale = gainToLinearScale(clampedDb)
        nuvioCDynamics.setGainDb(clampedDb.toFloat()) // [fork] night_mode
    }

    fun isGainEnabled(): Boolean {
        return gainDb != AUDIO_AMPLIFICATION_MIN_DB
    }

    // [fork] Nuvio C round 31 night_mode: while a night mode level is on, the samples go through
    // ysosrs123's PcmDynamics (gain, compressor that only lifts, limiter). Off = the official path below,
    // byte for byte.
    private val nuvioCDynamics = PcmDynamics()
    @Volatile private var nuvioCNight: com.nuvio.tv.data.local.NightModeLevel = com.nuvio.tv.data.local.NightModeLevel.OFF
    private var nuvioCFrame = FloatArray(0)

    fun setNightMode(level: com.nuvio.tv.data.local.NightModeLevel) {
        nuvioCNight = level
        nuvioCDynamics.setNightMode(nuvioCEffectiveLevel())
    }

    fun isNightModeEnabled(): Boolean = nuvioCNight != com.nuvio.tv.data.local.NightModeLevel.OFF

    // [fork] Nuvio C round 52 decoded_dialogue_lift: a format switched off in "Formats sent to the soundbar" is decoded
    // on the TV and its dialogue raised with the same dynamics. Night mode and this lift are one level, never stacked:
    // while night mode (manual or scheduled) is on, its level is used; otherwise the lift, and only while the playing
    // track is a switched-off format. [nuvioCLiftArmed] keeps this processor in the chain from the start (gain 0 dB
    // passes samples through unchanged), so the lift can switch on and off with a track change without rebuilding.
    @Volatile private var nuvioCLift: com.nuvio.tv.data.local.NightModeLevel = com.nuvio.tv.data.local.NightModeLevel.OFF
    @Volatile private var nuvioCLiftArmed = false

    fun nuvioCArmLift(armed: Boolean) { nuvioCLiftArmed = armed }

    fun nuvioCSetDecodedLift(level: com.nuvio.tv.data.local.NightModeLevel) {
        nuvioCLift = level
        nuvioCDynamics.setNightMode(nuvioCEffectiveLevel())
    }

    fun nuvioCEffectiveLevel(): com.nuvio.tv.data.local.NightModeLevel =
        if (nuvioCNight != com.nuvio.tv.data.local.NightModeLevel.OFF) nuvioCNight else nuvioCLift

    private fun nuvioCDynamicsOn(): Boolean = nuvioCEffectiveLevel() != com.nuvio.tv.data.local.NightModeLevel.OFF

    override fun isActive(): Boolean {
        return super.isActive() && (isGainEnabled() || isNightModeEnabled() || nuvioCLiftArmed)
    }

    // [fork] round 53 decoded_audio_steady: the lift's state survives a flush with the same format (seek, resume),
    // so the level doesn't sink and climb back after each one; a 25 ms fade-in after a flush avoids a click.
    private var nuvioCConfiguredRate = 0
    private var nuvioCConfiguredChannels = 0
    private var nuvioCFadeFrames = 0
    private var nuvioCFadePos = 0

    override fun onFlush() {
        val format = inputAudioFormat
        if (format.channelCount > 0 && format.sampleRate > 0) {
            if (nuvioCFrame.size != format.channelCount) nuvioCFrame = FloatArray(format.channelCount)
            nuvioCDynamics.setGainDb(gainDb.toFloat())
            val steady = com.nuvio.tv.NuvioCFeatures.DECODED_AUDIO_STEADY
            if (!steady || format.sampleRate != nuvioCConfiguredRate || format.channelCount != nuvioCConfiguredChannels) {
                nuvioCDynamics.configure(format.sampleRate, format.channelCount)
                nuvioCConfiguredRate = format.sampleRate
                nuvioCConfiguredChannels = format.channelCount
            }
            if (steady) {
                nuvioCFadeFrames = (format.sampleRate * 0.025f).toInt()
                nuvioCFadePos = 0
            }
        }
    }

    override fun onReset() {
        nuvioCFrame = FloatArray(0)
        nuvioCConfiguredRate = 0
        nuvioCConfiguredChannels = 0
    }

    /** [fork] round 53: the fade-in factor for the next frame (1 once the fade is done). */
    private fun nuvioCFade(): Float {
        if (nuvioCFadePos >= nuvioCFadeFrames) return 1f
        nuvioCFadePos++
        return nuvioCFadePos.toFloat() / nuvioCFadeFrames
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val inputSize = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(inputSize)
        if (nuvioCDynamicsOn() && nuvioCFrame.isNotEmpty()) { // [fork] night_mode; round 52: or the decoded-format lift
            nuvioCProcess(inputBuffer, outputBuffer)
            return
        }
        val scale = gainScale

        if (scale == 1f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, outputBuffer, scale)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, outputBuffer, scale)
            else -> outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }

    private fun processPcm16(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, scale: Float) {
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.short.toInt()
            val amplified = (sample * scale)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            outputBuffer.putShort(amplified.toShort())
        }

        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
    }

    private fun processPcmFloat(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, scale: Float) {
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        while (inputBuffer.remaining() >= 4) {
            val sample = inputBuffer.float
            val amplified = (sample * scale).coerceIn(-1f, 1f)
            outputBuffer.putFloat(amplified)
        }

        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }
    }

    private fun gainToLinearScale(db: Int): Float {
        if (db == 0) return 1f
        return 10.0.pow(db / 20.0).toFloat()
    }

    // [fork] night_mode: ysosrs123's frame loop, so all channels share one detector and gain.
    private fun nuvioCProcess(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())
        nuvioCDynamics.prepareBuffer()
        val samples = nuvioCFrame
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val frameBytes = samples.size * 2
                while (inputBuffer.remaining() >= frameBytes) {
                    for (c in samples.indices) samples[c] = inputBuffer.short / 32768f
                    nuvioCDynamics.processFrame(samples)
                    val fade = nuvioCFade()
                    if (fade < 1f) for (c in samples.indices) samples[c] *= fade
                    for (c in samples.indices) {
                        outputBuffer.putShort((samples[c] * 32768f).roundToInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                    }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val frameBytes = samples.size * 4
                while (inputBuffer.remaining() >= frameBytes) {
                    for (c in samples.indices) samples[c] = inputBuffer.float
                    nuvioCDynamics.processFrame(samples)
                    val fade = nuvioCFade()
                    if (fade < 1f) for (c in samples.indices) samples[c] *= fade
                    for (c in samples.indices) outputBuffer.putFloat(samples[c])
                }
            }
        }
        if (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }
}

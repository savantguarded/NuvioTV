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
        nuvioCDynamics.setNightMode(level)
    }

    fun isNightModeEnabled(): Boolean = nuvioCNight != com.nuvio.tv.data.local.NightModeLevel.OFF

    override fun isActive(): Boolean {
        return super.isActive() && (isGainEnabled() || isNightModeEnabled())
    }

    override fun onFlush() {
        val format = inputAudioFormat
        if (format.channelCount > 0 && format.sampleRate > 0) {
            if (nuvioCFrame.size != format.channelCount) nuvioCFrame = FloatArray(format.channelCount)
            nuvioCDynamics.setGainDb(gainDb.toFloat())
            nuvioCDynamics.configure(format.sampleRate, format.channelCount)
        }
    }

    override fun onReset() {
        nuvioCFrame = FloatArray(0)
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
        if (isNightModeEnabled() && nuvioCFrame.isNotEmpty()) { // [fork] night_mode
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
                    for (c in samples.indices) outputBuffer.putFloat(samples[c])
                }
            }
        }
        if (inputBuffer.hasRemaining()) outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }
}

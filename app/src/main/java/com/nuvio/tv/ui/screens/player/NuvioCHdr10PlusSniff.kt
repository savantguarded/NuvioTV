package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C (2026-10-11 round 4): HDR10+ read from the video stream itself.
// HDR10+ is per-frame metadata inside the HEVC / AV1 stream (an ITU-T T.35 message from Samsung:
// country B5, provider 003C, oriented code 0001, application 4). Containers don't flag it and the
// TV's decoder doesn't report it to apps (the round 3 decoder check never fired on the TCL), so
// HDR10+ files whose name doesn't say so showed plain HDR10.
// This looks at the first video samples ExoPlayer reads (about 200 frames, at most 8 MB) for that
// message and then steps aside: after that the samples go straight through, nothing is copied.
// The bytes themselves are never changed. Progressive files only (MKV / MP4 from debrid and add-ons);
// HLS / DASH and mpv are untouched. Used by nuvioCVisualTag. Switch: NuvioCFeatures.HDR_DECODED.

import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import com.nuvio.tv.NuvioCFeatures
import java.io.EOFException
import kotlin.math.min

internal object NuvioCHdr10PlusSniff {
    private const val MAX_SAMPLES = 200
    private const val MAX_BYTES = 8L * 1024 * 1024

    /** T.35 header of an HDR10+ message (application identifier 4 included). */
    private val SIGNATURE = byteArrayOf(0xB5.toByte(), 0x00, 0x3C, 0x00, 0x01, 0x04)

    private class Result(val width: Int, val height: Int, val found: Boolean)

    @Volatile private var result: Result? = null

    /** True when the current video track's stream carries HDR10+ (matched by picture size). */
    fun found(videoFormat: Format?): Boolean {
        if (!NuvioCFeatures.HDR_DECODED) return false
        val r = result ?: return false
        if (!r.found) return false
        if (videoFormat == null) return true
        return (r.width <= 0 || videoFormat.width <= 0 || r.width == videoFormat.width) &&
            (r.height <= 0 || videoFormat.height <= 0 || r.height == videoFormat.height)
    }

    internal fun reset(width: Int, height: Int) { result = Result(width, height, false) }
    internal fun mark(width: Int, height: Int) { result = Result(width, height, true) }

    /** Byte matcher kept across reads (the message can straddle two reads). Unit tested. */
    internal class Matcher {
        private var state = 0
        var matched = false
            private set

        fun feed(data: ByteArray, offset: Int, length: Int) {
            if (matched) return
            for (i in offset until offset + length) {
                val b = data[i]
                state = when {
                    b == SIGNATURE[state] -> state + 1
                    b == SIGNATURE[0] -> 1
                    else -> 0
                }
                if (state == SIGNATURE.size) { matched = true; return }
            }
        }
    }

    @UnstableApi
    fun wrap(factory: ExtractorsFactory): ExtractorsFactory {
        if (!NuvioCFeatures.HDR_DECODED) return factory
        return SniffingFactory(factory)
    }

    /** Forwards everything (file-type order, subtitle parser, transcoding) to the real factory. */
    @UnstableApi
    private class SniffingFactory(private val delegate: ExtractorsFactory) : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> =
            delegate.createExtractors().map { SniffingExtractor(it) as Extractor }.toTypedArray()

        override fun createExtractors(uri: android.net.Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
            delegate.createExtractors(uri, responseHeaders).map { SniffingExtractor(it) as Extractor }.toTypedArray()

        @Suppress("DEPRECATION")
        override fun experimentalSetTextTrackTranscodingEnabled(enabled: Boolean): ExtractorsFactory {
            delegate.experimentalSetTextTrackTranscodingEnabled(enabled)
            return this
        }

        override fun setSubtitleParserFactory(subtitleParserFactory: androidx.media3.extractor.text.SubtitleParser.Factory): ExtractorsFactory {
            delegate.setSubtitleParserFactory(subtitleParserFactory)
            return this
        }

        override fun experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies: Int): ExtractorsFactory {
            delegate.experimentalSetCodecsToParseWithinGopSampleDependencies(codecsToParseWithinGopSampleDependencies)
            return this
        }
    }

    @UnstableApi
    private class SniffingExtractor(private val delegate: Extractor) : Extractor {
        override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)
        override fun init(output: ExtractorOutput) = delegate.init(SniffingOutput(output))
        override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int = delegate.read(input, seekPosition)
        override fun seek(position: Long, timeUs: Long) = delegate.seek(position, timeUs)
        override fun release() = delegate.release()
        override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation
        override fun getSniffFailureDetails() = delegate.sniffFailureDetails
    }

    @UnstableApi
    private class SniffingOutput(private val delegate: ExtractorOutput) : ExtractorOutput {
        private var videoClaimed = false
        override fun track(id: Int, type: Int): TrackOutput {
            val track = delegate.track(id, type)
            if (type != C.TRACK_TYPE_VIDEO || videoClaimed) return track
            videoClaimed = true
            return SniffingTrack(track)
        }
        override fun endTracks() = delegate.endTracks()
        override fun seekMap(seekMap: SeekMap) = delegate.seekMap(seekMap)
    }

    @UnstableApi
    private class SniffingTrack(private val delegate: TrackOutput) : TrackOutput {
        private val matcher = Matcher()
        private var samples = 0
        private var bytes = 0L
        private var width = Format.NO_VALUE
        private var height = Format.NO_VALUE
        private var active = true
        private var buffer = ByteArray(64 * 1024)

        override fun format(format: Format) {
            width = format.width
            height = format.height
            if (active) reset(width, height)
            delegate.format(format)
        }

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
            if (!active) return delegate.sampleData(input, length, allowEndOfInput, sampleDataPart)
            val want = min(length, buffer.size)
            val read = input.read(buffer, 0, want)
            if (read == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return C.RESULT_END_OF_INPUT
                throw EOFException()
            }
            scan(buffer, 0, read)
            delegate.sampleData(ParsableByteArray(buffer.copyOf(read)), read, sampleDataPart)
            return read
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            if (active && length > 0) scan(data.data, data.position, min(length, data.bytesLeft()))
            delegate.sampleData(data, length, sampleDataPart)
        }

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            if (active && ++samples >= MAX_SAMPLES) stop()
            delegate.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        }

        private fun scan(data: ByteArray, offset: Int, length: Int) {
            if (length <= 0) return
            matcher.feed(data, offset, length)
            bytes += length
            if (matcher.matched) {
                mark(width, height)
                android.util.Log.i("NuvioCOsdBadges", "HDR10+ metadata found in the video stream")
                stop()
            } else if (bytes >= MAX_BYTES) stop()
        }

        private fun stop() {
            active = false
            buffer = ByteArray(0)
        }
    }
}

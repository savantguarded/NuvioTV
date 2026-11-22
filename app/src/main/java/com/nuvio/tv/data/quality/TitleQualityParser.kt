package com.nuvio.tv.data.quality

// [fork] Nuvio C round 60 `detail_quality_line`: picks ONE real release from a Torrentio answer and returns its
// tags (resolution, dynamic range, audio, source). Tags are never mixed across releases. Pure Kotlin, no Android,
// so it runs in JVM tests. Rules (spec "Nuvio details page quality badges", 2026-11-22):
//  - drop CAM / telesync / TS / TC, releases with no resolution, and zero-seeder releases;
//  - movies: drop a release that names a year but none within a year of the film's (loose ID matching guard);
//  - 4K only when two different 4K releases each have 5+ seeders, otherwise every 4K release is dropped;
//  - only releases with 5+ seeders compete, unless none has 5, then all that are left do;
//  - rank by resolution, dynamic range, audio, source, seeders; 8K counts as 4K.

/** One stream of a Torrentio answer: only the three strings the parser reads. */
data class QualityStreamText(val name: String?, val title: String?, val filename: String?)

enum class QualityResolution(val label: String) { SD("480P"), HD("720P"), FHD("1080P"), UHD("4K") }
enum class QualityRange(val label: String) { HDR10("HDR10"), HDR10_PLUS("HDR10+"), DOLBY_VISION("DOLBY VISION") }
enum class QualityAudio(val label: String) {
    DD("DOLBY DIGITAL"), DDP("DOLBY DIGITAL PLUS"), DTS_HD("DTS-HD"), TRUEHD("TRUEHD"), DTS_X("DTS:X"), ATMOS("ATMOS")
}
enum class QualitySource(val label: String) { DVD("DVD"), HDTV("HDTV"), WEB_DL("WEB-DL"), BLURAY("BLURAY"), REMUX("REMUX") }

/** The tags of one release. [labels] is the badge order: resolution, dynamic range, audio, source. */
data class TitleQuality(
    val resolution: QualityResolution,
    val range: QualityRange? = null,
    val audio: QualityAudio? = null,
    val source: QualitySource? = null
) {
    val labels: List<String> get() = listOfNotNull(resolution.label, range?.label, audio?.label, source?.label)
}

/** A parsed release (internal, exposed for tests). */
data class ParsedRelease(
    val quality: TitleQuality?,
    val seeders: Int,
    val cam: Boolean,
    val years: List<Int>
)

object TitleQualityParser {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    /** The name, title and filename of every stream in a Torrentio answer (no other fields are read). */
    fun streamsOf(body: String): List<QualityStreamText> {
        val root = json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return emptyList()
        fun kotlinx.serialization.json.JsonObject.str(k: String) =
            (this[k] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content
        return (root["streams"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            QualityStreamText(o.str("name"), o.str("title"), (o["behaviorHints"] as? kotlinx.serialization.json.JsonObject)?.str("filename"))
        }
    }

    private val B = "(?<![a-z0-9])"
    private val E = "(?![a-z0-9])"
    private val SEEDERS = Regex("👤\\s*(\\d+)")
    private val YEAR = Regex("(?<!\\d)(19\\d\\d|20\\d\\d)(?!\\d)")
    private val CAM = Regex("${B}(cam|camrip|hdcam|hd-?ts|telesync|ts|tsrip|tc|hdtc|telecine|scr|screener)$E")
    private val RES_4K = Regex("${B}(2160p|4k|uhd|4320p|8k)$E")
    private val RES_1080 = Regex("${B}1080[pi]$E")
    private val RES_720 = Regex("${B}720p$E")
    private val RES_SD = Regex("${B}(576p|480p|360p|dvdrip|dvd|sd|sdtv|dvd5|dvd9|xvid)$E")
    private val DV = Regex("${B}(dv|dovi|dolby[ ._-]?vision)$E")
    private val HDR10P = Regex("hdr10(\\+|plus|p(?![a-z]))")
    private val HDR = Regex("${B}(hdr10|hdr|hlg)$E")
    private val DTSX = Regex("${B}dts[ ._:-]?x$E")
    private val TRUEHD = Regex("true[ ._-]?hd")
    private val DTSHD = Regex("${B}dts[ ._-]?hd")
    private val DDP = Regex("${B}(ddp|dd\\+|e-?ac-?3|eac3)")
    private val DD = Regex("${B}(dd|ac-?3)(?=[ ._\\-]?\\d|$E)")
    private val REMUX = Regex("remux")
    private val BLURAY = Regex("${B}(blu-?ray|bdrip|brrip|bd25|bd50|bd)$E")
    private val WEB = Regex("${B}(web-?dl|web-?rip|web|amzn|nf|dsnp|hmax|atvp)$E")
    private val HDTV = Regex("${B}(hdtv|pdtv)$E")
    private val DVD = Regex("${B}(dvd|dvdrip|dvd5|dvd9)$E")

    const val CORROBORATION_RELEASES = 2
    const val CORROBORATION_SEEDERS = 5
    const val HEALTHY_SEEDERS = 5

    fun parse(stream: QualityStreamText): ParsedRelease {
        val raw = listOfNotNull(stream.name, stream.title, stream.filename).joinToString(" ").lowercase()
        val seeders = SEEDERS.find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val t = SEEDERS.replace(raw, " ")
        val res = when {
            RES_4K.containsMatchIn(t) -> QualityResolution.UHD
            RES_1080.containsMatchIn(t) -> QualityResolution.FHD
            RES_720.containsMatchIn(t) -> QualityResolution.HD
            RES_SD.containsMatchIn(t) -> QualityResolution.SD
            else -> null
        }
        val range = when {
            DV.containsMatchIn(t) -> QualityRange.DOLBY_VISION
            HDR10P.containsMatchIn(t) -> QualityRange.HDR10_PLUS
            HDR.containsMatchIn(t) -> QualityRange.HDR10
            else -> null
        }
        val audio = when {
            "atmos" in t -> QualityAudio.ATMOS
            DTSX.containsMatchIn(t) -> QualityAudio.DTS_X
            TRUEHD.containsMatchIn(t) -> QualityAudio.TRUEHD
            DTSHD.containsMatchIn(t) -> QualityAudio.DTS_HD
            DDP.containsMatchIn(t) -> QualityAudio.DDP
            DD.containsMatchIn(t) || "dolby digital" in t -> QualityAudio.DD
            else -> null
        }
        val source = when {
            REMUX.containsMatchIn(t) -> QualitySource.REMUX
            BLURAY.containsMatchIn(t) -> QualitySource.BLURAY
            WEB.containsMatchIn(t) -> QualitySource.WEB_DL
            HDTV.containsMatchIn(t) -> QualitySource.HDTV
            DVD.containsMatchIn(t) -> QualitySource.DVD
            else -> null
        }
        return ParsedRelease(
            quality = res?.let { TitleQuality(it, range, audio, source) },
            seeders = seeders,
            cam = CAM.containsMatchIn(t),
            years = YEAR.findAll(t).mapNotNull { it.value.toIntOrNull() }.toList()
        )
    }

    /** The best single release, or null when nothing usable is left. [year] is the film's year (movies only). */
    fun best(streams: List<QualityStreamText>, year: Int? = null): TitleQuality? {
        var left = streams.asSequence().map(::parse)
            .filter { !it.cam && it.quality != null && it.seeders > 0 }
            .filter { r -> year == null || r.years.isEmpty() || r.years.any { kotlin.math.abs(it - year) <= 1 } }
            .toList()
        val healthy4k = left.count { it.quality!!.resolution == QualityResolution.UHD && it.seeders >= CORROBORATION_SEEDERS }
        if (healthy4k < CORROBORATION_RELEASES) left = left.filter { it.quality!!.resolution != QualityResolution.UHD }
        val pool = left.filter { it.seeders >= HEALTHY_SEEDERS }.ifEmpty { left }
        return pool.maxWithOrNull(
            compareBy<ParsedRelease>({ it.quality!!.resolution.ordinal }, { it.quality!!.range?.ordinal ?: -1 },
                { it.quality!!.audio?.ordinal ?: -1 }, { it.quality!!.source?.ordinal ?: -1 }, { it.seeders })
        )?.quality
    }

    /** QualiCache tokens (one trusted release) to the same slots. Resolution capped at 4K. */
    fun fromTokens(tokens: List<String>): TitleQuality? {
        val t = tokens.map { it.uppercase().trim() }.toSet()
        val res = when {
            "4K" in t || "8K" in t || "2160P" in t -> QualityResolution.UHD
            "1080P" in t -> QualityResolution.FHD
            "720P" in t -> QualityResolution.HD
            "480P" in t || "576P" in t || "SD" in t -> QualityResolution.SD
            else -> return null
        }
        val range = when {
            "DV" in t || "DOVI" in t -> QualityRange.DOLBY_VISION
            "HDR10+" in t || "HDR10PLUS" in t -> QualityRange.HDR10_PLUS
            "HDR10" in t || "HDR" in t -> QualityRange.HDR10
            else -> null
        }
        val audio = when {
            "ATMOS" in t -> QualityAudio.ATMOS
            "DTSX" in t || "DTS:X" in t -> QualityAudio.DTS_X
            "TRUEHD" in t -> QualityAudio.TRUEHD
            "DTSHD" in t || "DTS-HD" in t || "DTSHDMA" in t -> QualityAudio.DTS_HD
            "DDP" in t || "EAC3" in t -> QualityAudio.DDP
            "DD" in t || "AC3" in t -> QualityAudio.DD
            else -> null
        }
        val source = when {
            "REMUX" in t -> QualitySource.REMUX
            "BLURAY" in t -> QualitySource.BLURAY
            "WEBDL" in t || "WEB-DL" in t || "WEB" in t -> QualitySource.WEB_DL
            "HDTV" in t -> QualitySource.HDTV
            "DVD" in t -> QualitySource.DVD
            else -> null
        }
        return TitleQuality(res, range, audio, source)
    }
}

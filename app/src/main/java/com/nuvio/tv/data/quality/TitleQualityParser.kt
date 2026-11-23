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
    // round 64: DTS (core) and FLAC added; enum order is the ranking order (low to high). FLAC ranks lowest so the
    // round 60 picks don't change (it shows only when no other sound format is named).
    FLAC("FLAC"), DD("DOLBY DIGITAL"), DTS("DTS"), DDP("DOLBY DIGITAL PLUS"), DTS_HD("DTS-HD"), TRUEHD("TRUEHD"), DTS_X("DTS:X"), ATMOS("ATMOS")
}

/**
 * [fork] round 64 quality_editions: editions a title has somewhere in its releases. Enum order is the display
 * priority (Extended and Director's Cut first); at most two are shown.
 */
enum class QualityEdition(val label: String) {
    EXTENDED("EXTENDED"), DIRECTORS_CUT("DIRECTOR'S CUT"), SPECIAL_EDITION("SPECIAL EDITION"), IMAX("IMAX"),
    FINAL_CUT("FINAL CUT"), ULTIMATE_CUT("ULTIMATE CUT"), REDUX("REDUX"), CRITERION("CRITERION"),
    OPEN_MATTE("OPEN MATTE"), BLACK_AND_WHITE("B&W")
}
enum class QualitySource(val label: String) { DVD("DVD"), HDTV("HDTV"), WEB_DL("WEB-DL"), BLURAY("BLURAY"), REMUX("REMUX") }

/** The tags of one release. [labels] is the badge order: resolution, dynamic range, audio, source. */
data class TitleQuality(
    val resolution: QualityResolution,
    val range: QualityRange? = null,
    val audio: QualityAudio? = null,
    val source: QualitySource? = null,
    /** Round 64: editions seen in ANY release of the title (not only this one), priority order. Not in [labels]. */
    val editions: List<QualityEdition> = emptyList()
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
    private val DTS = Regex("${B}dts(?=[ ._\\-]?\\d|$E)")
    private val FLAC = Regex("${B}flac")
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
            FLAC.containsMatchIn(t) -> QualityAudio.FLAC
            DDP.containsMatchIn(t) -> QualityAudio.DDP
            DTS.containsMatchIn(t) -> QualityAudio.DTS
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

    /** Torrentio's own resolution tag from the first word of its name line (its list is sorted by it, then seeders). */
    fun torrentioTier(name: String?): Int {
        val tag = name?.substringAfterLast('\n')?.trim()?.lowercase()?.substringBefore(' ') ?: return 0
        return when (tag) {
            "4k", "2160p", "8k", "4320p" -> 5
            "1440p" -> 4
            "1080p" -> 3
            "720p" -> 2
            "576p", "480p", "360p" -> 1
            else -> 0
        }
    }

    /** What [bestEarly] picked and how many streams it had to read. */
    data class EarlyPick(val quality: TitleQuality?, val read: Int)

    /**
     * Round 60d: the same pick as [best], reading as little as possible. Torrentio sorts by its resolution tag, then
     * seeders, so once a healthy (5+ seeders) release is held at resolution R and the list has moved to a lower tag,
     * nothing later can beat it: stop. A 4K tier is read to its end first (two healthy 4K releases needed).
     */
    fun bestEarly(streams: Sequence<QualityStreamText>, year: Int? = null): EarlyPick {
        val iter = streams.iterator()
        var read = 0
        val kept = ArrayList<ParsedRelease>()
        var pending: QualityStreamText? = null
        fun next(): QualityStreamText? = pending?.also { pending = null } ?: if (iter.hasNext()) iter.next().also { read++ } else null
        while (true) {
            val first = next() ?: break
            val tier = torrentioTier(first.name)
            val group = arrayListOf(first)
            while (true) {
                val n = next() ?: break
                if (torrentioTier(n.name) != tier) { pending = n; break }
                group += n
            }
            group.asSequence().map(::parse)
                .filter { !it.cam && it.quality != null && it.seeders > 0 }
                .filter { r -> year == null || r.years.isEmpty() || r.years.any { kotlin.math.abs(it - year) <= 1 } }
                .forEach { kept += it }
            // stop once a healthy release is held and the next tag can't reach its resolution
            val pick = rank(kept)
            val nextTier = pending?.let { torrentioTier(it.name) }
            if (pick != null && pick.seeders >= HEALTHY_SEEDERS && nextTier != null && nextTier != 0 &&
                nextTier < tierOf(pick.quality!!.resolution)) break
            if (nextTier == null) break
        }
        return EarlyPick(rank(kept)?.quality, read)
    }

    private fun tierOf(r: QualityResolution) = when (r) {
        QualityResolution.UHD -> 5; QualityResolution.FHD -> 3; QualityResolution.HD -> 2; QualityResolution.SD -> 1
    }

    /** The ranking of [best] over already-filtered releases. */
    private fun rank(left0: List<ParsedRelease>): ParsedRelease? {
        var left = left0
        val healthy4k = left.count { it.quality!!.resolution == QualityResolution.UHD && it.seeders >= CORROBORATION_SEEDERS }
        if (healthy4k < CORROBORATION_RELEASES) left = left.filter { it.quality!!.resolution != QualityResolution.UHD }
        val pool = left.filter { it.seeders >= HEALTHY_SEEDERS }.ifEmpty { left }
        return pool.maxWithOrNull(
            compareBy<ParsedRelease>({ it.quality!!.resolution.ordinal }, { it.quality!!.range?.ordinal ?: -1 },
                { it.quality!!.audio?.ordinal ?: -1 }, { it.quality!!.source?.ordinal ?: -1 }, { it.seeders })
        )
    }

    /**
     * Round 60d: reads a Torrentio answer one stream at a time from [reader] (name, title, filename only; everything
     * else skipped), never building the whole JSON. Stop consuming and close the reader to stop the download.
     */
    fun streamsFrom(reader: java.io.Reader): Sequence<QualityStreamText> = sequence {
        val r = JsonPull(reader)
        if (!r.seekKey("streams") || !r.expect('[')) return@sequence
        while (true) {
            val c = r.peekNonSpace() ?: return@sequence
            if (c == ']') return@sequence
            if (c == ',') { r.skip1(); continue }
            if (c != '{') return@sequence
            yield(r.readStream() ?: return@sequence)
        }
    }

    // ---- round 64 quality_editions ----
    // matched on the words form (see [words]): letters and digits, every separator one space
    private val EDITION_RULES: List<Pair<QualityEdition, Regex>> = listOf(
        QualityEdition.EXTENDED to Regex(" extended "),
        QualityEdition.DIRECTORS_CUT to Regex(" directors? (s )?cut "),
        QualityEdition.SPECIAL_EDITION to Regex(" special edition "),
        QualityEdition.IMAX to Regex(" imax "),
        QualityEdition.FINAL_CUT to Regex(" final cut "),
        QualityEdition.ULTIMATE_CUT to Regex(" ultimate (cut|edition) "),
        QualityEdition.REDUX to Regex(" redux "),
        QualityEdition.CRITERION to Regex(" criterion "),
        QualityEdition.OPEN_MATTE to Regex(" open matte "),
        QualityEdition.BLACK_AND_WHITE to Regex(" (black and white|b w) ")
    )

    /** Lowercase words of a title, separators collapsed: "Director's Cut: X" -> "director s cut x". */
    fun words(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /**
     * Round 64: the editions one release names. The film's own [title] is taken out first (so "The Final Cut" or
     * "Redux" in a title never counts); CAM / zero-seeder releases and, for a film, other-year releases don't count.
     */
    fun editionsOf(stream: QualityStreamText, title: String? = null, year: Int? = null): Set<QualityEdition> {
        val raw = listOfNotNull(stream.name, stream.title, stream.filename).joinToString(" ").lowercase()
        val seeders = SEEDERS.find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (seeders <= 0) return emptySet()
        var t = SEEDERS.replace(raw, " ")
        if (CAM.containsMatchIn(t)) return emptySet()
        if (year != null) {
            val ys = YEAR.findAll(t).mapNotNull { it.value.toIntOrNull() }.toList()
            if (ys.isNotEmpty() && ys.none { kotlin.math.abs(it - year) <= 1 }) return emptySet()
        }
        // Words form, padded. Only lines that name the film count (a pack's name like "Y Tu Mama Tambien Criterion"
        // above a file of this film doesn't), and the film's own title is taken out of them (so "The Final Cut" or a
        // title with "Redux" in it never counts). Spaces are doubled so each match keeps its neighbours' boundaries.
        val tw = title?.let(::words)?.takeIf { it.length >= 2 }?.let { " " + it.replace(" ", "  ") + " " }
        t = t.split('\n').asSequence()
            .map { " " + words(it).replace(" ", "  ") + " " }
            .filter { tw == null || it.contains(tw) }
            .joinToString(" ") { if (tw == null) it else it.replace(tw, " ") }
        if (t.isBlank()) return emptySet()
        t = t.replace(Regex(" +"), " ")
        return EDITION_RULES.filter { (_, re) -> re.containsMatchIn(t) }.mapTo(LinkedHashSet()) { it.first }
    }

    /** Round 64: editions across all [streams], in priority order, at most [max]. */
    fun editions(streams: Iterable<QualityStreamText>, title: String? = null, year: Int? = null, max: Int = 2): List<QualityEdition> {
        val seen = HashSet<QualityEdition>()
        streams.forEach { seen += editionsOf(it, title, year) }
        return QualityEdition.entries.filter { it in seen }.take(max)
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
            "FLAC" in t -> QualityAudio.FLAC
            "DDP" in t || "EAC3" in t -> QualityAudio.DDP
            "DTS" in t -> QualityAudio.DTS
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

/** A tiny forward-only JSON reader for Torrentio answers (strings, nesting, escapes). */
private class JsonPull(private val r: java.io.Reader) {
    private var peeked = -2
    private fun read(): Int = if (peeked != -2) peeked.also { peeked = -2 } else r.read()
    fun skip1() { read() }
    fun peekNonSpace(): Char? {
        while (true) {
            val c = read()
            if (c < 0) return null
            if (!c.toChar().isWhitespace()) { peeked = c; return c.toChar() }
        }
    }
    fun expect(ch: Char): Boolean = peekNonSpace() == ch && run { read(); true }

    /** Moves past `"key":` at any depth. */
    fun seekKey(key: String): Boolean {
        while (true) {
            val c = read()
            if (c < 0) return false
            if (c.toChar() == '"') {
                val s = readStringBody()
                if (s == key && peekNonSpace() == ':') { read(); return true }
            }
        }
    }

    /** Reads one stream object; keeps name, title and behaviorHints.filename. */
    fun readStream(): QualityStreamText? {
        if (!expect('{')) return null
        var name: String? = null; var title: String? = null; var filename: String? = null
        while (true) {
            when (peekNonSpace() ?: return null) {
                '}' -> { read(); return QualityStreamText(name, title, filename) }
                ',' -> read()
                '"' -> {
                    read(); val key = readStringBody()
                    if (!expect(':')) return null
                    when (key) {
                        "name" -> name = readValueString()
                        "title" -> title = readValueString()
                        "behaviorHints" -> filename = readHintsFilename()
                        else -> skipValue()
                    }
                }
                else -> return null
            }
        }
    }

    private fun readValueString(): String? = if (peekNonSpace() == '"') { read(); readStringBody() } else { skipValue(); null }

    private fun readHintsFilename(): String? {
        if (peekNonSpace() != '{') { skipValue(); return null }
        read()
        var filename: String? = null
        while (true) {
            when (peekNonSpace() ?: return filename) {
                '}' -> { read(); return filename }
                ',' -> read()
                '"' -> {
                    read(); val key = readStringBody()
                    if (!expect(':')) return filename
                    if (key == "filename") filename = readValueString() else skipValue()
                }
                else -> return filename
            }
        }
    }

    private fun skipValue() {
        when (peekNonSpace() ?: return) {
            '"' -> { read(); skipStringBody() }
            '{', '[' -> {
                var depth = 0
                while (true) {
                    val c = read(); if (c < 0) return
                    when (c.toChar()) {
                        '"' -> skipStringBody()
                        '{', '[' -> depth++
                        '}', ']' -> { depth--; if (depth == 0) return }
                    }
                }
            }
            else -> while (true) { // number, true, false, null
                val c = peekNonSpace() ?: return
                if (c == ',' || c == '}' || c == ']') return
                read()
            }
        }
    }

    private fun skipStringBody() {
        while (true) {
            val c = read(); if (c < 0) return
            if (c.toChar() == '\\') read() else if (c.toChar() == '"') return
        }
    }

    private fun readStringBody(): String {
        val sb = StringBuilder()
        while (true) {
            val c = read(); if (c < 0) return sb.toString()
            when (val ch = c.toChar()) {
                '"' -> return sb.toString()
                '\\' -> when (val e = read().toChar()) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                    'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                    'u' -> { val hex = CharArray(4) { read().toChar() }; sb.append(String(hex).toIntOrNull(16)?.toChar() ?: '?') }
                    else -> sb.append(e)
                }
                else -> sb.append(ch)
            }
        }
    }
}

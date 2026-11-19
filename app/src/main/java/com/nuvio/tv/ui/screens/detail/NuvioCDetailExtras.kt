package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 43 (switches `detail_info_slots`, `detail_awards_bodies`, `detail_guide`).
//
// The Information block as fixed slots: every label always in the same place for films (8) and shows (10), a dim
// dash where a value is missing, so the eye always finds the same thing in the same spot. Anything the top of the
// page already shows (rating, runtime, genres, seasons, director / creator) stays out.
//
// Round 43b layout, one full-width column: Information, then Parental guidance (rating chip and five fixed cells
// with a 3-step bar and level, no notes).
// Round 47: inside Information, after Themes, a Keywords line (the rest of TMDB's keywords, wrapping) and an Awards
// line: major wins only (Oscars, Emmys, Golden Globes, BAFTA, main festival prizes) from Wikidata by IMDb id (the
// title and people "for" it), 7 days on disk. No nominations, no OMDb. A dash means "no major win found".
//
// The whole block is one quiet focus stop (detail_info_stop) below the last row; late data never moves anything above it.

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.repository.NuvioCGuide
import com.nuvio.tv.data.repository.NuvioCGuideLine
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One awards line: counts ("1 win · 4 nominations") and the categories (wins first). */
@Immutable
data class NuvioCAwardLine(val wins: Int, val nominations: Int, val categories: List<String>)

/** The per-body lines Wikidata gave, keyed by body; null = not looked up / unreachable. */
@Immutable
data class NuvioCAwards(val bodies: Map<NuvioCAwardBody, NuvioCAwardLine>)

enum class NuvioCAwardBody { OSCARS, GLOBES, BAFTA, EMMYS, FESTIVALS }

internal object NuvioCAwardBodies {
    /** Which body an award belongs to, and the category text to show, or null for awards outside the five. */
    fun classify(label: String): Pair<NuvioCAwardBody, String>? {
        val l = label.trim()
        fun after(prefix: String) = l.substring(prefix.length).trim().replaceFirstChar { it.uppercase() }
        return when {
            l.startsWith("Academy Award for ") -> NuvioCAwardBody.OSCARS to after("Academy Award for ").replace("Best Writing, ", "Best ")
            l.startsWith("Golden Globe Award for ") -> NuvioCAwardBody.GLOBES to after("Golden Globe Award for ")
            l.startsWith("BAFTA Award for ") -> NuvioCAwardBody.BAFTA to after("BAFTA Award for ")
            l.startsWith("British Academy Film Award for ") -> NuvioCAwardBody.BAFTA to after("British Academy Film Award for ")
            l.startsWith("British Academy Television Award for ") -> NuvioCAwardBody.BAFTA to after("British Academy Television Award for ")
            l.startsWith("BAFTA TV Award for ") -> NuvioCAwardBody.BAFTA to after("BAFTA TV Award for ")
            l.startsWith("Primetime Emmy Award for ") -> NuvioCAwardBody.EMMYS to after("Primetime Emmy Award for ")
            FESTIVAL.containsMatchIn(l) -> NuvioCAwardBody.FESTIVALS to l
            else -> null
        }
    }

    // [fork] round 47: the main competition prizes only (no sidebars, critics' or audience awards).
    private val FESTIVAL = Regex(
        "^(Palme d'Or|Palme d’Or|Grand Prix \\(Cannes Film Festival\\)|Cannes Best Director Award|Golden Lion|Silver Lion|Volpi Cup.*|Golden Bear|Silver Bear.*)$",
        RegexOption.IGNORE_CASE
    )

    /** [fork] round 47: rows of (kind, award label, who) into per-body wins. Only "win" rows count; one category counts
     *  once however many people shared it (a VFX team, a cast). */
    fun group(rows: List<Triple<String, String, String>>): NuvioCAwards {
        val winCats = LinkedHashMap<NuvioCAwardBody, LinkedHashSet<String>>()
        rows.forEach { (kind, label, _) ->
            if (kind != "win") return@forEach
            val (body, cat) = classify(label) ?: return@forEach
            winCats.getOrPut(body) { LinkedHashSet() }.add(cat)
        }
        val out = winCats.mapValues { (_, w) -> NuvioCAwardLine(w.size, 0, w.toList()) }
        return NuvioCAwards(out)
    }

    fun countText(wins: Int, nominations: Int): String {
        val parts = buildList {
            if (wins > 0) add("$wins ${if (wins == 1) "win" else "wins"}")
            if (nominations > 0) add("$nominations ${if (nominations == 1) "nomination" else "nominations"}")
        }
        return parts.joinToString(" · ")
    }

    /**
     * [fork] round 47 detail_awards_wins: the major wins only, one body after another: "Academy Awards: Best Visual
     * Effects · BAFTA: Best Film Not in the English Language". Festivals show the prize itself ("Palme d'Or").
     * Null when Wikidata lists no major win.
     */
    fun winsText(awards: NuvioCAwards?, names: Map<NuvioCAwardBody, String>): String? {
        val order = listOf(NuvioCAwardBody.OSCARS, NuvioCAwardBody.EMMYS, NuvioCAwardBody.GLOBES, NuvioCAwardBody.BAFTA, NuvioCAwardBody.FESTIVALS)
        val parts = order.mapNotNull { body ->
            val line = awards?.bodies?.get(body)?.takeIf { it.wins > 0 } ?: return@mapNotNull null
            val cats = line.categories.take(line.wins.coerceAtLeast(1)).filter { it.isNotBlank() }
            if (cats.isEmpty()) return@mapNotNull null
            if (body == NuvioCAwardBody.FESTIVALS) cats.joinToString(", ")
            else "${names[body]}: ${cats.joinToString(", ")}"
        }
        return parts.joinToString(" · ").takeIf { it.isNotBlank() }
    }
}

/** Wikidata lookup by IMDb id, 7 days on disk (30 entries in memory). */
internal object NuvioCWikidataAwards {
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val FILE = "nuvio_c_award_wins.json" // round 47: wins only (old nuvio_c_awards.json is deleted)
    private const val OLD_FILE = "nuvio_c_awards.json"
    private const val MAX = 400
    private val memory = LinkedHashMap<String, NuvioCAwards>()

    suspend fun awards(context: Context, imdbId: String): NuvioCAwards? = withContext(Dispatchers.IO) {
        if (!imdbId.matches(Regex("tt\\d+"))) return@withContext null
        synchronized(memory) { memory[imdbId] }?.let { return@withContext it }
        val file = File(context.applicationContext.filesDir, FILE)
        runCatching { File(context.applicationContext.filesDir, OLD_FILE).takeIf { it.exists() }?.delete() }
        val store = runCatching { if (file.exists()) JSONObject(file.readText()) else JSONObject() }.getOrDefault(JSONObject())
        val now = System.currentTimeMillis()
        store.optJSONObject(imdbId)?.takeIf { now - it.optLong("at") < TTL_MS }?.let { kept ->
            val rows = kept.optJSONArray("rows")?.let(::rowsOf).orEmpty()
            return@withContext NuvioCAwardBodies.group(rows).also { remember(imdbId, it) }
        }
        val rows = query(imdbId) ?: return@withContext null // unreachable: ask again next visit
        runCatching {
            store.put(imdbId, JSONObject().put("at", now).put("rows", JSONArray().apply {
                rows.forEach { put(JSONArray().put(it.first).put(it.second).put(it.third)) }
            }))
            if (store.length() > MAX) {
                val oldest = store.keys().asSequence().toList().sortedBy { store.optJSONObject(it)?.optLong("at") ?: 0L }
                oldest.take(store.length() - MAX).forEach { store.remove(it) }
            }
            file.writeText(store.toString())
        }
        NuvioCAwardBodies.group(rows).also { remember(imdbId, it) }
    }

    private fun remember(id: String, a: NuvioCAwards) = synchronized(memory) {
        memory.remove(id); memory[id] = a
        while (memory.size > 30) memory.remove(memory.keys.first())
    }

    private fun rowsOf(a: JSONArray): List<Triple<String, String, String>> = (0 until a.length()).mapNotNull { i ->
        a.optJSONArray(i)?.let { Triple(it.optString(0), it.optString(1), it.optString(2)) }
    }

    private fun query(imdbId: String): List<Triple<String, String, String>>? = runCatching {
        val sparql = """
            SELECT DISTINCT ?kind ?awardLabel ?who WHERE {
              ?film wdt:P345 "$imdbId" .
              {
                { ?film p:P166 ?st . ?st ps:P166 ?award . BIND(?film AS ?who) }
                UNION { ?st pq:P1686 ?film . ?who p:P166 ?st . ?st ps:P166 ?award . }
              }
              FILTER NOT EXISTS { ?st wikibase:rank wikibase:DeprecatedRank }
              BIND("win" AS ?kind)
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            }
        """.trimIndent()
        val url = URL("https://query.wikidata.org/sparql?format=json&query=" + URLEncoder.encode(sparql, "UTF-8"))
        val c = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/sparql-results+json")
            setRequestProperty("User-Agent", "NuvioC/1.0 (personal Android TV app)")
        }
        try {
            if (c.responseCode !in 200..299) return@runCatching null
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val bindings = JSONObject(body).getJSONObject("results").getJSONArray("bindings")
            (0 until bindings.length()).mapNotNull { i ->
                val b = bindings.getJSONObject(i)
                val kind = b.optJSONObject("kind")?.optString("value") ?: return@mapNotNull null
                val label = b.optJSONObject("awardLabel")?.optString("value") ?: return@mapNotNull null
                val who = b.optJSONObject("who")?.optString("value").orEmpty().substringAfterLast('/')
                Triple(kind, label, who)
            }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}

// ---- the slots -------------------------------------------------------------------------------------------

private const val DASH = "–"

/** The fixed Information labels, in reading order (two per line). */
internal val NUVIO_C_FILM_SLOTS = listOf(
    R.string.nuvio_c_info_theatrical, R.string.nuvio_c_info_digital,
    R.string.nuvio_c_info_country, R.string.nuvio_c_info_language,
    R.string.nuvio_c_info_budget, R.string.nuvio_c_info_box_office,
    R.string.nuvio_c_info_based_on, R.string.nuvio_c_info_original_title
)
internal val NUVIO_C_SHOW_SLOTS = listOf(
    R.string.nuvio_c_info_status, R.string.nuvio_c_info_airs,
    R.string.nuvio_c_info_first_aired, R.string.nuvio_c_info_last_aired,
    R.string.nuvio_c_info_episodes, R.string.nuvio_c_info_episode_length,
    R.string.nuvio_c_info_country, R.string.nuvio_c_info_language,
    R.string.nuvio_c_info_based_on, R.string.nuvio_c_info_original_title
)

/**
 * [fork] round 43b: one column, full width. Information (two slots per line, so long values keep their room), then
 * Parental guidance (five fixed cells: name, 3-step bar, level), then Awards (fixed cells: body, counts). Same
 * heading style, hairline and spacing rules for all three.
 */
@Composable
internal fun NuvioCInfoSlots(info: NuvioCDetailInfo, margin: Dp) {
    val hairline = NuvioTheme.colors.TextPrimary.copy(alpha = 0.08f)
    Column(Modifier.fillMaxWidth().padding(horizontal = margin)) {
        Heading(stringResource(R.string.nuvio_c_info_title))
        val full = com.nuvio.tv.NuvioCFeatures.INFO_FULL_ROWS // [fork] round 44: Based on (and Themes) get the full width
        val slots = (if (info.show) NUVIO_C_SHOW_SLOTS else NUVIO_C_FILM_SLOTS).let { if (full) it - R.string.nuvio_c_info_based_on else it }
        val byLabel = info.rows.associateBy { it.label }
        val hideEmpty = com.nuvio.tv.NuvioCFeatures.DETAIL_INFO_HIDE_EMPTY // [fork] round 52: empty slots are left out, not dashed
        val shownSlots = if (hideEmpty) slots.filter { l -> !byLabel[l]?.value.isNullOrBlank() } else slots
        shownSlots.chunked(2).forEach { pair ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { label ->
                    val value = byLabel[label]?.value?.let { v ->
                        if (v.startsWith("\u0000")) v.drop(1).toIntOrNull()?.let { stringResource(it) } else v
                    }?.takeIf { it.isNotBlank() }
                    SlotLine(stringResource(label), value, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f)) // a lone slot keeps half the width
            }
        }
        if (full && (!hideEmpty || !byLabel[R.string.nuvio_c_info_based_on]?.value.isNullOrBlank())) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            SlotLine(stringResource(R.string.nuvio_c_info_based_on), byLabel[R.string.nuvio_c_info_based_on]?.value?.takeIf { it.isNotBlank() }, Modifier.fillMaxWidth(), maxLines = 2)
        }
        if (com.nuvio.tv.NuvioCFeatures.DETAIL_THEMES && (!hideEmpty || info.themes.isNotEmpty())) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            NuvioCThemesLine(stringResource(R.string.nuvio_c_info_themes), info.themes)
        }
        if (com.nuvio.tv.NuvioCFeatures.DETAIL_KEYWORDS && (!hideEmpty || info.keywords.isNotEmpty())) { // [fork] round 47: the rest of the keywords, wrapping
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            NuvioCThemesLine(stringResource(R.string.nuvio_c_info_keywords), info.keywords) // [fork] 47b: one line and "+N", like Themes
        }
        val nuvioCAwardNames = mapOf(
                NuvioCAwardBody.OSCARS to stringResource(R.string.nuvio_c_award_oscars),
                NuvioCAwardBody.EMMYS to stringResource(R.string.nuvio_c_award_emmys),
                NuvioCAwardBody.GLOBES to stringResource(R.string.nuvio_c_award_globes),
                NuvioCAwardBody.BAFTA to stringResource(R.string.nuvio_c_award_bafta)
            )
        val nuvioCWins = NuvioCAwardBodies.winsText(info.awards, nuvioCAwardNames)
        if (com.nuvio.tv.NuvioCFeatures.DETAIL_AWARDS_WINS && (!hideEmpty || !nuvioCWins.isNullOrBlank())) { // [fork] round 47: major wins only, full width, wraps
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            val names = mapOf(
                NuvioCAwardBody.OSCARS to stringResource(R.string.nuvio_c_award_oscars),
                NuvioCAwardBody.EMMYS to stringResource(R.string.nuvio_c_award_emmys),
                NuvioCAwardBody.GLOBES to stringResource(R.string.nuvio_c_award_globes),
                NuvioCAwardBody.BAFTA to stringResource(R.string.nuvio_c_award_bafta)
            )
            SlotLine(stringResource(R.string.nuvio_c_info_awards), NuvioCAwardBodies.winsText(info.awards, names), Modifier.fillMaxWidth(), maxLines = Int.MAX_VALUE)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))

        // [fork] round 52 detail_info_hide_empty: Parental guidance only once it has at least one level (never while
        // loading, never as a row of dashes). It is the last band, so arriving late moves nothing below it.
        val nuvioCGuideHas = info.guide?.let { g -> listOf(g.nudity, g.violence, g.profanity, g.alcohol, g.frightening).any { it?.level != null } } == true
        if (com.nuvio.tv.NuvioCFeatures.DETAIL_GUIDE && (!hideEmpty || nuvioCGuideHas)) {
            Spacer(Modifier.height(BAND_GAP))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Heading(stringResource(R.string.nuvio_c_guide_title))
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .padding(bottom = HEADING_GAP)
                        .border(1.dp, NuvioTheme.colors.TextPrimary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(info.rated ?: "NR", style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextPrimary, maxLines = 1)
                }
            }
            val g = info.guide
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    R.string.nuvio_c_guide_nudity to g?.nudity,
                    R.string.nuvio_c_guide_violence to g?.violence,
                    R.string.nuvio_c_guide_profanity to g?.profanity,
                    R.string.nuvio_c_guide_alcohol to g?.alcohol,
                    R.string.nuvio_c_guide_frightening to g?.frightening
                ).forEach { (label, line) -> GuideCell(stringResource(label), line?.level, Modifier.weight(1f)) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
        }

    }
}

private val BAND_GAP = 28.dp
private val HEADING_GAP = 10.dp
private val CELL_GAP = 24.dp

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = NuvioTheme.colors.TextPrimary, maxLines = 1, modifier = Modifier.padding(bottom = HEADING_GAP))
}

@Composable
private fun dashColor() = NuvioTheme.extendedColors.textTertiary.copy(alpha = 0.6f)

@Composable
private fun SlotLine(label: String, value: String?, modifier: Modifier, maxLines: Int = 1) {
    val style = slotStyle()
    Row(modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = style,
            color = NuvioTheme.extendedColors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(136.dp)
        )
        Text(
            value ?: DASH,
            style = style,
            color = if (value != null) NuvioTheme.colors.TextPrimary else dashColor(),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GuideCell(label: String, level: String?, modifier: Modifier) {
    val steps = when (level) { "mild" -> 1; "moderate" -> 2; "severe" -> 3; else -> 0 }
    val levelText = when (level) {
        "none" -> stringResource(R.string.nuvio_c_guide_level_none)
        "mild" -> stringResource(R.string.nuvio_c_guide_level_mild)
        "moderate" -> stringResource(R.string.nuvio_c_guide_level_moderate)
        "severe" -> stringResource(R.string.nuvio_c_guide_level_severe)
        else -> null
    }
    Column(modifier.padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.extendedColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier.width(16.dp).height(5.dp).background(
                            if (i < steps) NuvioTheme.colors.TextPrimary.copy(alpha = 0.9f) else NuvioTheme.colors.TextPrimary.copy(alpha = 0.14f),
                            RoundedCornerShape(3.dp)
                        )
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                levelText ?: DASH,
                style = MaterialTheme.typography.bodyMedium,
                color = if (levelText != null) NuvioTheme.colors.TextPrimary else dashColor(),
                maxLines = 1
            )
        }
    }
}

/** [fork] round 44 info_full_rows: Information labels and values at 13 sp (were 14). */
@Composable
private fun slotStyle(): androidx.compose.ui.text.TextStyle =
    if (com.nuvio.tv.NuvioCFeatures.INFO_FULL_ROWS) MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp)
    else MaterialTheme.typography.bodyMedium

/**
 * [fork] round 44 detail_themes: label, then quiet outline chips on one line, as many whole chips as fit and "+N" for
 * the rest. Not focusable (the Information block is one focus stop). A dash while TMDB has none.
 */
@Composable
private fun NuvioCThemesLine(label: String, themes: List<String>) {
    val style = slotStyle()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style, color = NuvioTheme.extendedColors.textTertiary, maxLines = 1, modifier = Modifier.width(136.dp))
        if (themes.isEmpty()) {
            Text(DASH, style = style, color = dashColor())
            return@Row
        }
        val chipStyle = MaterialTheme.typography.labelMedium
        val chipColor = NuvioTheme.colors.TextPrimary.copy(alpha = 0.85f)
        val border = NuvioTheme.colors.TextPrimary.copy(alpha = 0.22f)
        val more = NuvioTheme.extendedColors.textTertiary
        androidx.compose.ui.layout.SubcomposeLayout(Modifier.weight(1f)) { constraints ->
            val gap = 8.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val chips = themes.mapIndexed { i, t ->
                subcompose("c$i") {
                    Box(
                        Modifier
                            .border(1.dp, border, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) { Text(t, style = chipStyle, color = chipColor, maxLines = 1) }
                }.first().measure(loose)
            }
            val plusWidth = subcompose("plusProbe") { Text("+${themes.size}", style = chipStyle, color = more) }.first().measure(loose).width
            var used = 0
            var count = 0
            for ((i, p) in chips.withIndex()) {
                val next = (if (count == 0) 0 else used + gap) + p.width
                val rest = chips.size - i - 1
                val room = if (rest > 0) next + gap + plusWidth else next
                if (room > constraints.maxWidth) break
                used = next
                count++
            }
            val left = chips.size - count
            val plus = if (left > 0) subcompose("plus") { Text("+$left", style = chipStyle, color = more) }.first().measure(loose) else null
            val height = (chips.take(count).maxOfOrNull { it.height } ?: 0).coerceAtLeast(plus?.height ?: 0)
            layout(constraints.maxWidth, height) {
                var x = 0
                chips.take(count).forEach { p -> p.placeRelative(x, (height - p.height) / 2); x += p.width + gap }
                plus?.placeRelative(x, (height - plus.height) / 2)
            }
        }
    }
}

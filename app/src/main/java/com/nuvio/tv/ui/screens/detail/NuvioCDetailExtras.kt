package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 43 (switches `detail_info_slots`, `detail_awards_bodies`, `detail_guide`).
//
// The Information block as fixed slots: every label always in the same place for films (8) and shows (10), a dim
// dash where a value is missing, so the eye always finds the same thing in the same spot. Anything the top of the
// page already shows (rating, runtime, genres, seasons, director / creator) stays out.
//
// Round 43b layout, one full-width column: Information, then Parental guidance (rating chip and five fixed cells
// with a 3-step bar and level, no notes), then Awards (fixed cells: films Academy Awards, Golden Globes, BAFTA,
// Festivals, Overall; shows Primetime Emmys, Golden Globes, BAFTA, Overall; counts only). Per-body counts come from
// Wikidata by IMDb id (title + people "for" it), 7 days on disk; Overall is OMDb's / the meta add-on's total. A dash
// means "none found", not "none".
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

    private val FESTIVAL = Regex(
        "Palme d'Or|Palme d’Or|Cannes|Golden Lion|Silver Lion|Venice|Volpi Cup|Golden Bear|Silver Bear|Berlin",
        RegexOption.IGNORE_CASE
    )

    /** Rows of (kind "win"/"nom", award label, who) into per-body lines. A win counts as a nomination too. */
    fun group(rows: List<Triple<String, String, String>>): NuvioCAwards {
        val noms = HashMap<NuvioCAwardBody, MutableSet<Pair<String, String>>>()
        val wins = HashMap<NuvioCAwardBody, MutableSet<Pair<String, String>>>()
        val winCats = HashMap<NuvioCAwardBody, LinkedHashSet<String>>()
        val nomCats = HashMap<NuvioCAwardBody, LinkedHashSet<String>>()
        rows.forEach { (kind, label, who) ->
            val (body, cat) = classify(label) ?: return@forEach
            noms.getOrPut(body) { HashSet() }.add(label to who)
            if (kind == "win") {
                wins.getOrPut(body) { HashSet() }.add(label to who)
                winCats.getOrPut(body) { LinkedHashSet() }.add(cat)
            } else {
                nomCats.getOrPut(body) { LinkedHashSet() }.add(cat)
            }
        }
        val out = noms.keys.associateWith { body ->
            val w = winCats[body].orEmpty()
            val n = nomCats[body].orEmpty().filter { it !in w }
            NuvioCAwardLine(wins[body]?.size ?: 0, noms[body]?.size ?: 0, w.toList() + n)
        }
        return NuvioCAwards(out)
    }

    /** "152 wins & 220 nominations" / "4 wins" / "3 nominations" out of OMDb's or the add-on's text, else null. */
    fun overall(text: String?): String? {
        val t = text ?: return null
        val w = Regex("""(\d+)\s+wins?""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toIntOrNull()
        val n = Regex("""(\d+)\s+nominations?""", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toIntOrNull()
        return countText(w ?: 0, n ?: 0).takeIf { w != null || n != null }
    }

    fun countText(wins: Int, nominations: Int): String {
        val parts = buildList {
            if (wins > 0) add("$wins ${if (wins == 1) "win" else "wins"}")
            if (nominations > 0) add("$nominations ${if (nominations == 1) "nomination" else "nominations"}")
        }
        return parts.joinToString(" · ")
    }
}

/** Wikidata lookup by IMDb id, 7 days on disk (30 entries in memory). */
internal object NuvioCWikidataAwards {
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val FILE = "nuvio_c_awards.json"
    private const val MAX = 400
    private val memory = LinkedHashMap<String, NuvioCAwards>()

    suspend fun awards(context: Context, imdbId: String): NuvioCAwards? = withContext(Dispatchers.IO) {
        if (!imdbId.matches(Regex("tt\\d+"))) return@withContext null
        synchronized(memory) { memory[imdbId] }?.let { return@withContext it }
        val file = File(context.applicationContext.filesDir, FILE)
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
                { ?film p:P166 ?st . ?st ps:P166 ?award . BIND("win" AS ?kind) BIND(?film AS ?who) }
                UNION { ?film p:P1411 ?st . ?st ps:P1411 ?award . BIND("nom" AS ?kind) BIND(?film AS ?who) }
                UNION { ?st pq:P1686 ?film . ?who p:P166 ?st . ?st ps:P166 ?award . BIND("win" AS ?kind) }
                UNION { ?st pq:P1686 ?film . ?who p:P1411 ?st . ?st ps:P1411 ?award . BIND("nom" AS ?kind) }
              }
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
        slots.chunked(2).forEach { pair ->
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
        if (full) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            SlotLine(stringResource(R.string.nuvio_c_info_based_on), byLabel[R.string.nuvio_c_info_based_on]?.value?.takeIf { it.isNotBlank() }, Modifier.fillMaxWidth(), maxLines = 2)
        }
        if (com.nuvio.tv.NuvioCFeatures.DETAIL_THEMES) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            NuvioCThemesLine(stringResource(R.string.nuvio_c_info_themes), info.themes)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))

        if (com.nuvio.tv.NuvioCFeatures.DETAIL_GUIDE) {
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

        if (com.nuvio.tv.NuvioCFeatures.DETAIL_AWARDS_BODIES) {
            Spacer(Modifier.height(BAND_GAP))
            Heading(stringResource(R.string.nuvio_c_info_awards))
            val bodies = if (info.show) {
                listOf(NuvioCAwardBody.EMMYS to R.string.nuvio_c_award_emmys, NuvioCAwardBody.GLOBES to R.string.nuvio_c_award_globes, NuvioCAwardBody.BAFTA to R.string.nuvio_c_award_bafta)
            } else {
                listOf(NuvioCAwardBody.OSCARS to R.string.nuvio_c_award_oscars, NuvioCAwardBody.GLOBES to R.string.nuvio_c_award_globes, NuvioCAwardBody.BAFTA to R.string.nuvio_c_award_bafta, NuvioCAwardBody.FESTIVALS to R.string.nuvio_c_award_festivals)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP), modifier = Modifier.fillMaxWidth()) {
                bodies.forEach { (body, name) ->
                    val line = info.awards?.bodies?.get(body)
                    AwardCell(stringResource(name), line?.let { NuvioCAwardBodies.countText(it.wins, it.nominations) }, Modifier.weight(1f))
                }
                AwardCell(stringResource(R.string.nuvio_c_award_overall), NuvioCAwardBodies.overall(info.awardsText), Modifier.weight(1f))
                // Shows have one body fewer: keep the cells the same width as a film's.
                if (info.show) Spacer(Modifier.weight(1f))
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
private fun AwardCell(body: String, counts: String?, modifier: Modifier) {
    Column(modifier.padding(vertical = 10.dp)) {
        Text(body, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.extendedColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            counts?.takeIf { it.isNotBlank() } ?: DASH,
            style = MaterialTheme.typography.bodyMedium,
            color = if (!counts.isNullOrBlank()) NuvioTheme.colors.TextPrimary else dashColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

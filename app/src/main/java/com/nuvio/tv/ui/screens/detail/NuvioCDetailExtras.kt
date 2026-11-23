package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 43 (switch `detail_info_slots`; Awards since round 47, `detail_awards_wins`).
//
// The Information block as fixed slots: every label always in the same place for films (8) and shows (10), a dim
// dash where a value is missing, so the eye always finds the same thing in the same spot. Anything the top of the
// page already shows (rating, runtime, genres, seasons, director / creator) stays out.
//
// Round 43b layout, one full-width column. Round 47: an Awards line: major wins only (Oscars, Emmys, Golden Globes, BAFTA, main festival prizes) from Wikidata by IMDb id (the
// title and people "for" it), 7 days on disk. No nominations, no OMDb. A dash means "no major win found".
//
// 2026-11-20: Parental guidance, Themes, Keywords and Original title removed (owner).
//
// The whole block is one quiet focus stop (detail_info_stop) below the last row; late data never moves anything above it.

import android.content.Context
import androidx.compose.foundation.background
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
        return parts.joinToString(if (com.nuvio.tv.NuvioCFeatures.AWARDS_BY_LINE) "\n" else " · ").takeIf { it.isNotBlank() } // [fork] round 63: one award body per line
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
    R.string.nuvio_c_info_based_on
)
internal val NUVIO_C_SHOW_SLOTS = listOf(
    R.string.nuvio_c_info_status, R.string.nuvio_c_info_airs,
    R.string.nuvio_c_info_first_aired, R.string.nuvio_c_info_last_aired,
    R.string.nuvio_c_info_episodes, R.string.nuvio_c_info_episode_length,
    R.string.nuvio_c_info_country, R.string.nuvio_c_info_language,
    R.string.nuvio_c_info_based_on
)

/**
 * [fork] round 43b: one column, full width. Information (two slots per line, so long values keep their room), then
 * Based on and Awards on full-width lines.
 */
@Composable
internal fun NuvioCInfoSlots(info: NuvioCDetailInfo, margin: Dp) {
    val hairline = NuvioTheme.colors.TextPrimary.copy(alpha = 0.08f)
    Column(Modifier.fillMaxWidth().padding(horizontal = margin)) {
        Heading(stringResource(R.string.nuvio_c_info_title))
        val full = com.nuvio.tv.NuvioCFeatures.INFO_FULL_ROWS // [fork] round 44: Based on gets the full width
        val slots = (if (info.show) NUVIO_C_SHOW_SLOTS else NUVIO_C_FILM_SLOTS).let { if (full) it - R.string.nuvio_c_info_based_on else it }
        val byLabel = info.rows.associateBy { it.label }
        // [fork] round 52: empty slots were left out; round 53 detail_info_slots: every slot back in its fixed place with a dash
        val hideEmpty = com.nuvio.tv.NuvioCFeatures.DETAIL_INFO_HIDE_EMPTY && !com.nuvio.tv.NuvioCFeatures.DETAIL_INFO_DASHES
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
        val nuvioCAwardNames = mapOf(
                NuvioCAwardBody.OSCARS to stringResource(R.string.nuvio_c_award_oscars),
                NuvioCAwardBody.EMMYS to stringResource(R.string.nuvio_c_award_emmys),
                NuvioCAwardBody.GLOBES to stringResource(R.string.nuvio_c_award_globes),
                NuvioCAwardBody.BAFTA to stringResource(R.string.nuvio_c_award_bafta)
            )
        val nuvioCWins = NuvioCAwardBodies.winsText(info.awards, nuvioCAwardNames)
        if (com.nuvio.tv.NuvioCFeatures.DETAIL_AWARDS_WINS && (!hideEmpty || !nuvioCWins.isNullOrBlank())) { // [fork] round 47: major wins only, full width, wraps
            Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            SlotLine(stringResource(R.string.nuvio_c_info_awards), nuvioCWins, Modifier.fillMaxWidth(), maxLines = Int.MAX_VALUE)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
    }
}

private val HEADING_GAP = 10.dp

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

/** [fork] round 44 info_full_rows: Information labels and values at 13 sp (were 14). */
@Composable
private fun slotStyle(): androidx.compose.ui.text.TextStyle =
    if (com.nuvio.tv.NuvioCFeatures.INFO_FULL_ROWS) MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp)
    else MaterialTheme.typography.bodyMedium

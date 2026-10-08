package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C: one row per details section. Official shows Cast / Ratings / More like this /
// Trailers / Collection behind one tab row; Nuvio C draws each as its own row, in the order and
// with the on/off set in Settings › Layout › Details › Details sections.
//
// Built to merge easily: official's own section code is reused untouched. MetaDetailsScreen only
// turns its tab row off (hasVisiblePeopleTabs / shouldSplitCollection false), runs its existing
// section item once per row instead of once, and lets focus move row to row naturally. All the
// `[fork]` lines there read this state. Switch: NuvioCFeatures.SECTION_ROWS (off = official tabs).

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.rememberNuvioCSectionSettings
import com.nuvio.tv.ui.theme.NuvioTheme

/** One list item of the sections area: a section's own row, or a heading above it. */
@Immutable
internal data class NuvioCRowEntry<T>(val tab: T?, val header: Boolean = false, val label: String = "")

@Immutable
internal class NuvioCSectionRows(
    val on: Boolean,
    private val order: List<String>,
    private val hidden: Set<String>
) {
    /**
     * The section items to draw. Rows off: a single entry with no tab, which is exactly official's
     * one tabbed item. Rows on: one entry per visible section in the saved order, each preceded by
     * a heading entry when official's section has no title of its own ([selfTitled]).
     */
    fun <T : Enum<T>> entries(available: List<Pair<T, String>>, selfTitled: Set<T>): List<NuvioCRowEntry<T>> {
        if (!on) return listOf(NuvioCRowEntry(null))
        val byName = available.associateBy { it.first.name }
        return order.filter { it !in hidden }.mapNotNull { byName[it] }.flatMap { (tab, label) ->
            if (tab in selfTitled) listOf(NuvioCRowEntry(tab))
            else listOf(NuvioCRowEntry(tab, header = true, label = label), NuvioCRowEntry(tab))
        }
    }

    /** Lazy-list key: official's key when rows are off, so nothing changes there. */
    fun key(base: String, entry: NuvioCRowEntry<*>): String = when {
        entry.tab == null -> base
        entry.header -> "$base:hdr:${(entry.tab as Enum<*>).name}"
        else -> "$base:${(entry.tab as Enum<*>).name}"
    }

    /** Official counts the sections area as one or two list items; rows make it [count]. */
    fun commentsIndex(officialIndex: Int, officialSectionItems: Int, count: Int): Int =
        if (!on) officialIndex else officialIndex - officialSectionItems + count
}

@Composable
internal fun rememberNuvioCSectionRows(): NuvioCSectionRows {
    val s = rememberNuvioCSectionSettings()
    return remember(s) { NuvioCSectionRows(s.rows, s.order, s.hidden) }
}

/** Heading for a section that has no title of its own; same look as official's Collection title. */
@Composable
internal fun NuvioCRowHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = NuvioTheme.colors.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = NuvioTheme.spacing.xxxl,
                end = NuvioTheme.spacing.xxxl,
                top = 20.dp
            )
    )
}


package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.core.di.NuvioCFeaturedInfo
import com.nuvio.tv.domain.model.MetaPreview
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// [fork] Nuvio C featured_badges (2026-10-17, Charles picked A + B from the round 12 mockups).
// A: "#3 in Top 20" eyebrow above the logo when the title came from a ranked hero catalog
//    (name says top / trending / popular / hot / most watched), ranks 1-20; red TOP 10 tile for 1-10.
// B: one white NEW / NEW SEASON chip at the start of the meta line, aired in the last 60 days.
// Everything comes from catalogs and the TMDB answer the carousel already loads; drawn once per slide.
internal object NuvioCFeaturedBadgeRules {
    const val NEW_DAYS = 60L
    const val MAX_RANK = 20
    const val TOP_TILE_RANK = 10
    private val ranked = Regex("""(?i)\b(top|trending|popular|hot|most[ -]watched)\b""")

    enum class Status { NEW, NEW_SEASON }

    /** Rank to show, or null when the catalog isn't a ranked list or the rank is past [MAX_RANK]. */
    fun rank(entry: NuvioCHeroSource.Entry?): Int? {
        entry ?: return null
        if (entry.rank !in 1..MAX_RANK) return null
        return if (ranked.containsMatchIn(entry.catalogName)) entry.rank else null
    }

    fun status(item: MetaPreview, info: NuvioCFeaturedInfo?, today: LocalDate = LocalDate.now()): Status? {
        val isSeries = item.apiType.equals("series", ignoreCase = true) || item.apiType.equals("tv", ignoreCase = true)
        if (isSeries) {
            val season = info?.latestSeason
            val aired = parse(info?.latestSeasonAirDate)
            if (season != null && aired != null && recent(aired, today)) {
                return if (season > 1) Status.NEW_SEASON else Status.NEW
            }
            return null
        }
        val released = parse(info?.releaseDate) ?: parse(item.released)
        return if (released != null && recent(released, today)) Status.NEW else null
    }

    private fun recent(date: LocalDate, today: LocalDate): Boolean {
        val days = ChronoUnit.DAYS.between(date, today)
        return days in 0..NEW_DAYS
    }

    internal fun parse(raw: String?): LocalDate? {
        val text = raw?.trim()?.take(10) ?: return null
        if (text.length < 10) return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }
}

/** A: rank eyebrow ("TOP 10" tile + "#3 IN TOP 20"). */
@Composable
internal fun NuvioCFeaturedRankLine(item: MetaPreview) {
    if (!NuvioCFeatures.FEATURED_BADGES) return
    val entry = NuvioCHeroSource.of(item.id)
    val rank = NuvioCFeaturedBadgeRules.rank(entry) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rank <= NuvioCFeaturedBadgeRules.TOP_TILE_RANK) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color(0xFFE50914), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TOP", color = Color.White, fontSize = 6.5.sp, lineHeight = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
                    Text("10", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Text(
            text = stringResource(R.string.nuvio_c_featured_rank, rank, entry!!.catalogName).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** B: white NEW / NEW SEASON chip. */
@Composable
internal fun NuvioCFeaturedStatusChip(status: NuvioCFeaturedBadgeRules.Status) {
    val label = stringResource(
        if (status == NuvioCFeaturedBadgeRules.Status.NEW_SEASON) R.string.nuvio_c_featured_new_season else R.string.nuvio_c_featured_new
    )
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = Color(0xFF111111),
        maxLines = 1,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

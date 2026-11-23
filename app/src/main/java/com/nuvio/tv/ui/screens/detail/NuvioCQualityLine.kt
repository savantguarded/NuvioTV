package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 60 `detail_quality_line`: one line of outlined badges under the hero's facts line, e.g.
// 4K | DOLBY VISION | ATMOS | WEB-DL, describing ONE real release (never tags mixed across releases).
// Look (round 60d): age rating, a hairline, then soft-fill badges, in the synopsis colour and opacity, as the last line
// under the synopsis; NOT YET RELEASED / ON DIGITAL dim at 65%; nothing drawn for the quality part while checking.
// No jump: the hero is bottom-anchored, so the line holds a fixed height from the first frame in every state
// where a lookup is possible, and is absent only when there's no usable ID (known before the first frame).
// Its result lives in this leaf's own view model, never in MetaDetailsUiState, so only this line recomposes.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.quality.CachedKind
import com.nuvio.tv.data.quality.CachedQuality
import com.nuvio.tv.data.quality.QualityEpisode
import com.nuvio.tv.data.quality.QualityLineState
import com.nuvio.tv.data.quality.QualityLookup
import com.nuvio.tv.data.quality.QualityPlan
import com.nuvio.tv.data.quality.QualityTitle
import com.nuvio.tv.data.quality.TitleQualityPlanner
import com.nuvio.tv.data.quality.TitleQualityRepository
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

internal fun Meta.nuvioCQualityTitle(): QualityTitle = QualityTitle(
    id = id,
    imdbId = imdbId,
    isSeries = type == ContentType.SERIES || type == ContentType.TV,
    released = released,
    episodes = videos.map { QualityEpisode(it.season, it.episode, it.released) }
)

@HiltViewModel
class NuvioCQualityViewModel @Inject constructor(
    private val repo: TitleQualityRepository,
    private val tmdbService: TmdbService
) : ViewModel() {
    /** (metaId, state): the leaf ignores a state that belongs to another title. */
    private val _state = MutableStateFlow<Pair<String, QualityLineState>?>(null)
    val state: StateFlow<Pair<String, QualityLineState>?> = _state.asStateFlow()
    private var bound: String? = null
    private var job: Job? = null

    /**
     * What the first frame shows: O(1), no planning, no disk (round 60c). Absent when there is no usable ID; a result
     * already seen this run; otherwise Checking, which draws nothing (the space is held).
     */
    fun initial(t: QualityTitle): QualityLineState {
        if (TitleQualityPlanner.imdbOf(t) == null && TitleQualityPlanner.tmdbOf(t) == null) return QualityLineState.Absent
        return repo.cache.peekMeta(t.id)?.toState() ?: QualityLineState.Checking
    }

    /**
     * Round 60d: a title is bound once. The page often gets its episode list a moment after it opens; that is not a
     * new title, so the line is never cleared (that blank-then-fade was the flicker). A series waits for its
     * episode list before asking anything.
     */
    fun bind(t: QualityTitle) {
        val waitingForEpisodes = t.isSeries && t.episodes.isEmpty()
        val key = t.id + if (waitingForEpisodes) "|wait" else "|ready"
        if (bound == key) return
        val sameTitle = bound?.substringBefore('|') == t.id
        bound = key
        if (!sameTitle) _state.value = t.id to initial(t)
        if (waitingForEpisodes) return
        val now = _state.value?.takeIf { it.first == t.id }?.second
        if (sameTitle && now != null && now != QualityLineState.Checking) return // already showing a result
        job?.cancel()
        job = viewModelScope.launch { run(t) }
    }

    private suspend fun run(t: QualityTitle) {
        val today = LocalDate.now()
        // planning walks the episode list: off the main thread
        var plan = withContext(Dispatchers.Default) { TitleQualityPlanner.plan(t, today) }
        if (plan == QualityPlan.NotYetReleased) { publish(t, QualityLineState.Dim(QualityLineState.NOT_YET_RELEASED)); return }
        val cachedHit = (plan as? QualityPlan.Lookup)?.let { l -> withContext(Dispatchers.IO) { repo.cache.get(l.cacheKey) } }
        if (cachedHit != null) { withContext(Dispatchers.IO) { repo.cache.link(t.id, (plan as QualityPlan.Lookup).cacheKey) }; publish(t, cachedHit.toState()); return }
        // a layer like the ratings: nothing goes out until the page has opened and settled
        delay(SETTLE_MS)
        val needs = plan as? QualityPlan.NeedsImdb
        if (needs != null) {
            val imdb = withTimeoutOrNull(5_000) {
                runCatching { tmdbService.tmdbToImdb(needs.tmdbId, if (t.isSeries) "series" else "movie") }.getOrNull()
            }
            plan = if (imdb.isNullOrBlank()) { publish(t, QualityLineState.Dim(QualityLineState.UNAVAILABLE)); return }
            else withContext(Dispatchers.Default) { TitleQualityPlanner.plan(t, today, imdbOverride = imdb) }
            if (plan == QualityPlan.NotYetReleased) { publish(t, QualityLineState.Dim(QualityLineState.NOT_YET_RELEASED)); return }
        }
        val lookup = plan as? QualityPlan.Lookup ?: return
        withContext(Dispatchers.IO) { repo.cache.get(lookup.cacheKey) }?.let { withContext(Dispatchers.IO) { repo.cache.link(t.id, lookup.cacheKey) }; publish(t, it.toState()); return }
        val (result, nextRefresh) = repo.lookup(lookup.targets, lookup.year)
        val now = System.currentTimeMillis()
        when (result) {
            is QualityLookup.Found -> {
                val c = CachedQuality(CachedKind.FOUND, result.quality, now, TitleQualityPlanner.expiry(now, lookup.releaseDate, today, nextRefresh))
                withContext(Dispatchers.IO) { repo.cache.put(lookup.cacheKey, c, t.id) }
                publish(t, QualityLineState.Found(result.quality.labels))
            }
            else -> {
                val info = NuvioCDetailInfoStore.current.value?.takeIf { it.metaId == t.id } ?: NuvioCDetailInfoStore.cached(t.id)
                val label = TitleQualityPlanner.emptyLabel(result, t.isSeries, info?.theatricalDate ?: t.released, info?.homeReleaseDate, today)
                if (label == QualityLineState.NOT_YET_ON_DIGITAL) {
                    val c = CachedQuality(CachedKind.NOT_ON_DIGITAL, null, now, now + TitleQualityPlanner.NOT_ON_DIGITAL_MS)
                    withContext(Dispatchers.IO) { repo.cache.put(lookup.cacheKey, c, t.id) }
                }
                publish(t, QualityLineState.Dim(label))
            }
        }
    }

    private fun publish(t: QualityTitle, s: QualityLineState) { _state.value = t.id to s }

    private companion object { const val SETTLE_MS = 1_200L } // same settle as the Information block

    private fun CachedQuality.toState(): QualityLineState = when (kind) {
        CachedKind.FOUND -> quality?.let { QualityLineState.Found(it.labels) } ?: QualityLineState.Checking
        CachedKind.NOT_ON_DIGITAL -> QualityLineState.Dim(QualityLineState.NOT_YET_ON_DIGITAL)
    }
}

private const val QL_DIM_ALPHA = 0.65f // round 60d: readable from the sofa
// round 60d: soft-fill pills (white at 12% once the line takes the synopsis opacity), synopsis-colour text, a touch
// smaller than 60b; the age rating leads, a hairline separates it from the quality badges
private val QL_HEIGHT = 15.dp
private const val QL_TEXT_SP = 10f
private const val QL_FILL_ALPHA = 0.17f // x the synopsis' 0.72 = 12%
private val QL_RADIUS = 2.5.dp
private val QL_PAD = 4.dp
private val QL_GAP = 4.dp
private val QL_SEP_SPACE = 7.dp
private val QL_SEP_HEIGHT = 9.dp
private const val QL_SEP_ALPHA = 0.4f
// round 61: the whole line 17 dp (pills and logos), 11 sp pill text; a dot between year • duration and the badges
private val QL_HEIGHT_61 = 17.dp
private const val QL_TEXT_SP_61 = 11f
private const val QL_DOT_ALPHA = 0.62f
private val QL_DOT_SPACE = 6.dp
private val QL_LOGO_62 = 12.dp
// round 63
private val QL_GAP_63 = 6.dp
private val QL_LEAD_DOT_63 = 7.dp
private val QL_BOX_LOGO_63 = 10.dp
private const val QL_BOX_BORDER_63 = 0.45f // round 62: logos sit inside the 15 dp row, closer to the Apple TV scale

/**
 * [fork] round 62 quality_line_v62: resolution, source (REMUX reads BLURAY), TrueHD, visual, Atmos. One sound label per
 * release as before; only TrueHD and Atmos are shown (DTS, DD, DD+ are dropped). Dim labels pass through.
 */
internal fun nuvioCLabels62(labels: List<String>): List<String> {
    if (labels.isEmpty()) return labels
    val ranges = setOf("DOLBY VISION", "HDR10", "HDR10+")
    val sources = setOf("DVD", "HDTV", "WEB-DL", "BLURAY", "REMUX")
    val res = labels.first()
    val rest = labels.drop(1)
    val source = rest.firstOrNull { it in sources }?.let { if (it == "REMUX") "BLURAY" else it }
    val range = rest.firstOrNull { it in ranges }
    val sound = rest.firstOrNull { it !in sources && it !in ranges }
    return listOfNotNull(res, source, sound?.takeIf { it == "TRUEHD" }, range, sound?.takeIf { it == "ATMOS" })
}

/**
 * The line under the synopsis: age rating, a hairline, then the quality badges of one release. Nothing when the title
 * has neither a rating nor a usable ID. Drawn in the synopsis colour; the caller gives it the synopsis opacity. While
 * checking, the space is held and only the rating is drawn; a result that arrives later fades in beside it, so nothing
 * already drawn moves.
 */
@Composable
internal fun NuvioCQualityLine(meta: Meta, modifier: Modifier = Modifier, lead: String? = null) {
    if (!NuvioCFeatures.DETAIL_QUALITY_LINE) return
    val vm: NuvioCQualityViewModel = hiltViewModel()
    val title = remember(meta.id, meta.imdbId, meta.released, meta.videos.size) { meta.nuvioCQualityTitle() }
    // round 61 hero_lines_v61: the age rating is back on the facts line; year • duration lead this line instead
    val v61 = NuvioCFeatures.HERO_LINES_V61
    val rating = if (NuvioCFeatures.QUALITY_LINE_RATING && !v61) meta.ageRating?.trim()?.takeIf { it.isNotBlank() } else null
    val leadText = if (v61) lead?.takeIf { it.isNotBlank() } else null
    val first = remember(meta.id) { vm.initial(title) }
    if (first == QualityLineState.Absent && rating == null && leadText == null) return
    LaunchedEffect(title) { if (first != QualityLineState.Absent) vm.bind(title) }
    val held by vm.state.collectAsState()
    val state = held?.takeIf { it.first == meta.id }?.second ?: first

    val color = com.nuvio.tv.ui.theme.NuvioTheme.colors.TextPrimary
    val big = NuvioCFeatures.QUALITY_LOGOS
    val v62 = NuvioCFeatures.QUALITY_LINE_V62
    val lineHeight = if (big && !v62) QL_HEIGHT_61 else QL_HEIGHT // round 62: back to 15 dp
    val textSp = if (big && !v62) QL_TEXT_SP_61 else QL_TEXT_SP
    val logoHeight = if (v62) QL_LOGO_62 else lineHeight
    val textStyle = androidx.tv.material3.MaterialTheme.typography.labelMedium.copy(
        fontSize = textSp.sp, lineHeight = (textSp * 1.2f).sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp, color = color
    )
    val quality: List<String>? = when (state) {
        is QualityLineState.Found -> if (NuvioCFeatures.QUALITY_LINE_V62) nuvioCLabels62(state.labels) else state.labels
        is QualityLineState.Dim -> if (state.label == QualityLineState.UNAVAILABLE) null else listOf(state.label)
        else -> null
    }
    val dim = state is QualityLineState.Dim
    // round 60d flicker fix: a result that arrives after the first frame starts at 0 in that same frame, then fades in
    val fade = remember(meta.id, quality) { Animatable(if (quality == null || state == first) 1f else 0f) }
    LaunchedEffect(fade) { if (fade.value < 1f) fade.animateTo(1f, tween(220)) }

    Row(modifier.height(lineHeight), verticalAlignment = Alignment.CenterVertically) {
        if (leadText != null) {
            if (NuvioCFeatures.QUALITY_LINE_V63) { // round 63: more room around the dot between year and runtime
                leadText.split(" \u2022 ").forEachIndexed { i, part ->
                    if (i > 0) Text(text = "\u2022", style = leadStyle(color).copy(color = color.copy(alpha = QL_DOT_ALPHA)), modifier = Modifier.padding(horizontal = QL_LEAD_DOT_63))
                    Text(text = part, style = leadStyle(color), maxLines = 1, softWrap = false)
                }
            } else Text(text = leadText, style = leadStyle(color), maxLines = 1, softWrap = false)
        }
        if (rating != null) Pill(rating, textStyle, color, lineHeight)
        if (quality != null) {
            Row(Modifier.graphicsLayer { alpha = fade.value * if (dim) QL_DIM_ALPHA else 1f }, verticalAlignment = Alignment.CenterVertically) {
                if (leadText != null && !v62) {
                    Text(text = "•", style = leadStyle(color).copy(color = color.copy(alpha = QL_DOT_ALPHA)), modifier = Modifier.padding(horizontal = QL_DOT_SPACE))
                } else if (rating != null || leadText != null) { // round 62: hairline between year • duration and the badges
                    Spacer(Modifier.width(QL_SEP_SPACE))
                    Box(Modifier.width(1.dp).height(QL_SEP_HEIGHT).background(color.copy(alpha = QL_SEP_ALPHA)))
                    Spacer(Modifier.width(QL_SEP_SPACE))
                }
                BadgeRow { quality.forEach { label -> if (big) Badge61(label, textStyle, color, logoHeight) else Pill(label, textStyle, color, lineHeight) } }
            }
        }
    }
}

@Composable
private fun leadStyle(color: Color): TextStyle = androidx.tv.material3.MaterialTheme.typography.labelMedium.copy(
    fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium, color = color
)

/** [fork] round 61 quality_logos: Dolby Vision / Dolby Atmos / DTS (any DTS) as a white logo exactly the line's height; other labels stay pills. */
@Composable
private fun Badge61(label: String, style: TextStyle, color: Color, height: androidx.compose.ui.unit.Dp) {
    val res = when {
        label == "DOLBY VISION" -> com.nuvio.tv.R.drawable.nuvio_c_logo_dolby_vision
        label == "ATMOS" -> com.nuvio.tv.R.drawable.nuvio_c_logo_dolby_atmos
        label.startsWith("DTS") -> com.nuvio.tv.R.drawable.nuvio_c_logo_dts
        else -> null
    }
    if (res == null) { Pill(label, style, color, if (NuvioCFeatures.QUALITY_LINE_V62) QL_HEIGHT else height); return }
    val painter = androidx.compose.ui.res.painterResource(res)
    val size = painter.intrinsicSize
    val ratio = if (size.height > 0f) size.width / size.height else 2.5f
    if (NuvioCFeatures.QUALITY_LINE_V63) { // round 63: the logo in an outlined box the size of the other badges
        Box(
            Modifier.height(QL_HEIGHT).border(1.dp, color.copy(alpha = QL_BOX_BORDER_63), RoundedCornerShape(QL_RADIUS)).padding(horizontal = QL_PAD),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = label,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
                modifier = Modifier.height(QL_BOX_LOGO_63).width(QL_BOX_LOGO_63 * ratio)
            )
        }
        return
    }
    androidx.compose.foundation.Image(
        painter = painter,
        contentDescription = label,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
        modifier = Modifier.height(height).width(height * ratio)
    )
}

@Composable
private fun Pill(label: String, style: TextStyle, color: Color, height: androidx.compose.ui.unit.Dp = QL_HEIGHT) {
    Box(
        Modifier.height(height).background(color.copy(alpha = QL_FILL_ALPHA), RoundedCornerShape(QL_RADIUS)).padding(horizontal = QL_PAD),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = style, maxLines = 1, softWrap = false)
    }
}

/**
 * Badges in a row; if they don't fit the width, source goes first, then audio, then dynamic range. The first
 * badge (resolution) always stays. Labels come in slot order, so the drop order is last-to-first.
 */
@Composable
private fun BadgeRow(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val gap = (if (NuvioCFeatures.QUALITY_LINE_V63) QL_GAP_63 else QL_GAP).roundToPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        var count = placeables.size
        fun width(n: Int) = placeables.take(n).sumOf { it.width } + gap * (n - 1).coerceAtLeast(0)
        while (count > 1 && constraints.hasBoundedWidth && width(count) > constraints.maxWidth) count--
        val h = placeables.take(count).maxOfOrNull { it.height } ?: 0
        layout(width(count).coerceAtMost(constraints.maxWidth), h) {
            var x = 0
            placeables.take(count).forEach { p -> p.place(x, (h - p.height) / 2); x += p.width + gap }
        }
    }
}

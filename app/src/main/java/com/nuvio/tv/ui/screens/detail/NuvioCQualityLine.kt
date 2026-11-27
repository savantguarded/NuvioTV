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
import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.coroutines.flow.first
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
    episodes = videos.map { QualityEpisode(it.season, it.episode, it.released) },
    name = name
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
        if (NuvioCFeatures.QUALITY_DIGITAL_GATE && !t.isSeries) {
            // [fork] round 67: the gate decides first when the dates are already known; a recent film whose dates
            // aren't known yet shows nothing (space held) rather than a cached result the gate may take away
            val today = LocalDate.now()
            val info = NuvioCDetailInfoStore.cached(t.id)
            if (info != null && com.nuvio.tv.data.quality.NuvioCDigitalGate.gated(false, info.theatricalDate ?: t.released, info.anyHomeReleaseDate, today))
                return QualityLineState.Dim(QualityLineState.NOT_YET_ON_DIGITAL)
            if (info == null && com.nuvio.tv.data.quality.NuvioCDigitalGate.mayBeGated(false, t.released, today)) return QualityLineState.Checking
        }
        return (if (NuvioCFeatures.QUALITY_REFRESH_V66) repo.cache.peekMetaStale(t.id) else repo.cache.peekMeta(t.id))?.toState() ?: QualityLineState.Checking
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
        // [fork] round 67 QUALITY_DIGITAL_GATE: before any cache or lookup, a recent film waits (within the settle, plus
        // the Information block's own fetch) for its release dates; no digital / disc date anywhere = no lookup at all
        var waited = 0L
        if (NuvioCFeatures.QUALITY_DIGITAL_GATE && com.nuvio.tv.data.quality.NuvioCDigitalGate.mayBeGated(t.isSeries, t.released, today)) {
            val started = android.os.SystemClock.elapsedRealtime()
            val info = NuvioCDetailInfoStore.cached(t.id)?.takeIf { it.hasDates() } ?: withTimeoutOrNull(SETTLE_MS + GATE_EXTRA_MS) {
                NuvioCDetailInfoStore.current.first { it != null && it.metaId == t.id && it.hasDates() }
            }
            waited = android.os.SystemClock.elapsedRealtime() - started
            if (info != null && com.nuvio.tv.data.quality.NuvioCDigitalGate.gated(t.isSeries, info.theatricalDate ?: t.released, info.anyHomeReleaseDate, today)) {
                publish(t, QualityLineState.Dim(QualityLineState.NOT_YET_ON_DIGITAL)); return
            }
        }
        val cachedHit = (plan as? QualityPlan.Lookup)?.let { l -> withContext(Dispatchers.IO) { repo.cache.get(l.cacheKey) } }
        if (cachedHit != null) { withContext(Dispatchers.IO) { repo.cache.link(t.id, (plan as QualityPlan.Lookup).cacheKey) }; publish(t, cachedHit.toState()); return }
        // [fork] round 66 quality_refresh_v66: an expired result stays on screen while the new lookup runs
        val staleFound = if (NuvioCFeatures.QUALITY_REFRESH_V66) (plan as? QualityPlan.Lookup)?.let { l -> repo.cache.peekStale(l.cacheKey) }
            ?.takeIf { it.kind == CachedKind.FOUND }?.also { publish(t, it.toState()) } else null
        // a layer like the ratings: nothing goes out until the page has opened and settled
        delay((SETTLE_MS - waited).coerceAtLeast(0L)) // [fork] round 67: the gate's wait counts toward the settle
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
        val (result, nextRefresh) = repo.lookup(lookup.targets, lookup.year, movieTitle = t.name)
        val now = System.currentTimeMillis()
        when (result) {
            is QualityLookup.Found -> {
                val c = CachedQuality(CachedKind.FOUND, result.quality, now, TitleQualityPlanner.expiry(now, lookup.releaseDate, today, nextRefresh, result.quality))
                withContext(Dispatchers.IO) { repo.cache.put(lookup.cacheKey, c, t.id) }
                publish(t, QualityLineState.Found(result.quality.labels))
                // round 64: editions arrive a moment later, once the rest of the list was read (films only)
                val eds = result.editions?.let { d -> withTimeoutOrNull(EDITIONS_WAIT_MS) { d.await() } }.orEmpty()
                if (eds.isNotEmpty()) {
                    val q = result.quality.copy(editions = eds)
                    withContext(Dispatchers.IO) { repo.cache.put(lookup.cacheKey, c.copy(quality = q), t.id) }
                    publish(t, q.toFound())
                }
            }
            else -> {
                if (staleFound != null) return // [fork] round 66: a failed refresh keeps the line it had
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

    private companion object {
        const val SETTLE_MS = 1_200L // same settle as the Information block
        /** [fork] round 67: the Information block fetches its dates after the same settle; give that fetch time too. */
        const val GATE_EXTRA_MS = 3_000L
        const val EDITIONS_WAIT_MS = 20_000L
    }

    private fun CachedQuality.toState(): QualityLineState = when (kind) {
        CachedKind.FOUND -> quality?.toFound() ?: QualityLineState.Checking
        CachedKind.NOT_ON_DIGITAL -> QualityLineState.Dim(QualityLineState.NOT_YET_ON_DIGITAL)
    }
}

/** [fork] round 67: the Information answer has its release dates (a placeholder published before the fetch has not). */
private fun NuvioCDetailInfo.hasDates(): Boolean = loaded || theatricalDate != null || anyHomeReleaseDate != null

private fun com.nuvio.tv.data.quality.TitleQuality.toFound() = QualityLineState.Found(
    labels, if (NuvioCFeatures.QUALITY_EDITIONS) editions.map { it.label } else emptyList()
)

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
 * [fork] round 64 quality_line_v64: resolution, source (REMUX reads BLURAY), one visual tag, one sound tag, with Dolby
 * Vision and Atmos always last (e.g. 4K BLURAY TRUEHD [DV], 1080P BLURAY HDR10 [DTS], 4K BLURAY [DV] [ATMOS]). Every
 * sound format is shown again (DD, DD+, DTS, DTS-HD, DTS:X as logos; TrueHD and FLAC as pills). Dim labels pass through.
 */
internal fun nuvioCLabels64(labels: List<String>): List<String> {
    if (labels.isEmpty()) return labels
    val ranges = setOf("DOLBY VISION", "HDR10", "HDR10+")
    val sources = setOf("DVD", "HDTV", "WEB-DL", "BLURAY", "REMUX")
    val res = labels.first()
    val rest = labels.drop(1)
    val source = rest.firstOrNull { it in sources }?.let { if (it == "REMUX") "BLURAY" else it }
    val range = rest.firstOrNull { it in ranges }
    val sound = rest.firstOrNull { it !in sources && it !in ranges }
    return listOfNotNull(
        res, source,
        range?.takeIf { it != "DOLBY VISION" }, sound?.takeIf { it != "ATMOS" },
        range?.takeIf { it == "DOLBY VISION" }, sound?.takeIf { it == "ATMOS" }
    )
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
        is QualityLineState.Found -> when {
            NuvioCFeatures.QUALITY_LINE_V64 -> nuvioCLabels64(state.labels)
            NuvioCFeatures.QUALITY_LINE_V62 -> nuvioCLabels62(state.labels)
            else -> state.labels
        }
        is QualityLineState.Dim -> if (state.label == QualityLineState.UNAVAILABLE) null else listOf(state.label)
        else -> null
    }
    val dim = state is QualityLineState.Dim
    val editions = (state as? QualityLineState.Found)?.editions.orEmpty().take(2)
    // round 64: editions fade in on their own when they arrive after the badges
    val edFade = remember(meta.id, editions) { Animatable(if (editions.isEmpty() || state == first) 1f else 0f) }
    LaunchedEffect(edFade) { if (edFade.value < 1f) edFade.animateTo(1f, tween(220)) }
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
                if (NuvioCFeatures.QUALITY_LINE_V64 && !dim) {
                    // round 64: one line; when space runs out the second edition goes, then the first (with its
                    // hairline), then the visual pill, then the sound pill. Resolution and source never go.
                    val ranks = quality.mapIndexed { i, l -> if (i < 2 || l == "DOLBY VISION" || l == "ATMOS") 0 else if (l in setOf("HDR10", "HDR10+")) 2 else 1 }
                    // hairline: 0 here, it leaves with the last edition; first edition 4, second 5
                    val edRanks = if (editions.isEmpty()) emptyList() else listOf(0) + listOf(4, 5).take(editions.size)
                    DropRow(ranks + edRanks) {
                        quality.forEach { label -> Badge61(label, textStyle, color, logoHeight) }
                        if (editions.isNotEmpty()) {
                            Box(Modifier.graphicsLayer { alpha = edFade.value }.padding(horizontal = 1.dp).width(1.dp).height(QL_SEP_HEIGHT).background(color.copy(alpha = QL_SEP_ALPHA)))
                            editions.forEach { e -> Box(Modifier.graphicsLayer { alpha = edFade.value }) { Pill(e, textStyle, color, QL_HEIGHT) } }
                        }
                    }
                } else BadgeRow { quality.forEach { label -> if (big) Badge61(label, textStyle, color, logoHeight) else Pill(label, textStyle, color, lineHeight) } }
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
        NuvioCFeatures.QUALITY_LINE_V64 && label == "DOLBY DIGITAL" -> com.nuvio.tv.R.drawable.nuvio_c_logo_dolby_digital
        NuvioCFeatures.QUALITY_LINE_V64 && label == "DOLBY DIGITAL PLUS" -> com.nuvio.tv.R.drawable.nuvio_c_logo_dolby_digital_plus
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
            // round 64: DTS:X and DTS-HD keep the DTS logo with a short suffix in the same box; round 64b: the suffix
            // sits on the logo's bottom edge (its baseline on the logo's base), not centred
            val suffix = if (NuvioCFeatures.QUALITY_LINE_V64) nuvioCDtsSuffix(label) else null
            Row {
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = label,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
                    modifier = Modifier.height(QL_BOX_LOGO_63).width(QL_BOX_LOGO_63 * ratio).alignBy { it.measuredHeight }
                )
                if (suffix != null) Text(
                    text = suffix, style = style.copy(fontSize = (style.fontSize.value * 0.9f).sp), maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(start = 2.dp).alignByBaseline()
                )
            }
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

/** Round 64: the text after the DTS logo: ":X" for DTS:X, "HD" for DTS-HD, nothing for plain DTS. */
internal fun nuvioCDtsSuffix(label: String): String? = when (label) {
    "DTS:X" -> ":X"
    "DTS-HD" -> "HD"
    else -> null
}

/**
 * Round 64: badges in one row; [ranks] (one per child) say what goes when the row is too wide: the highest rank
 * first, 0 never. Children that stay keep their order.
 */
@Composable
private fun DropRow(ranks: List<Int>, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val gap = QL_GAP_63.roundToPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        val keep = BooleanArray(placeables.size) { true }
        fun width(): Int { val kept = placeables.indices.filter { keep[it] }; return kept.sumOf { placeables[it].width } + gap * (kept.size - 1).coerceAtLeast(0) }
        val order = placeables.indices.filter { (ranks.getOrNull(it) ?: 0) > 0 }.sortedWith(compareByDescending<Int> { ranks.getOrNull(it) ?: 0 }.thenByDescending { it })
        val isEdition = { i: Int -> (ranks.getOrNull(i) ?: 0) >= 4 }
        val hairline = ranks.indices.firstOrNull { i -> ranks[i] == 0 && i + 1 < ranks.size && isEdition(i + 1) }
        for (i in order) {
            if (!constraints.hasBoundedWidth || width() <= constraints.maxWidth) break
            keep[i] = false
            // the hairline goes with the last edition
            if (hairline != null && placeables.indices.none { keep[it] && isEdition(it) }) keep[hairline] = false
        }
        val kept = placeables.indices.filter { keep[it] }
        val h = kept.maxOfOrNull { placeables[it].height } ?: 0
        layout(width().coerceAtMost(constraints.maxWidth), h) {
            var x = 0
            kept.forEach { i -> val p = placeables[i]; p.place(x, (h - p.height) / 2); x += p.width + gap }
        }
    }
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

/**
 * [fork] round 66b featured_v66b: the details quality line drawn from known values (the Featured carousel): the
 * same lead (year • length), hairline and badges (Dolby / DTS logos in outlined boxes, other labels as pills),
 * at most [max] badges in the details order. Nothing is looked up here.
 */
@Composable
internal fun NuvioCQualityLineStatic(lead: String?, labels: List<String>, modifier: Modifier = Modifier, max: Int = 4, rating: String? = null, filled: Boolean = false, compact: Boolean = false) {
    val color = com.nuvio.tv.ui.theme.NuvioTheme.colors.TextPrimary
    val textStyle = androidx.tv.material3.MaterialTheme.typography.labelMedium.copy(
        fontSize = QL_TEXT_SP.sp, lineHeight = (QL_TEXT_SP * 1.2f).sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp, color = color
    )
    val quality = remember(labels) {
        (if (NuvioCFeatures.QUALITY_LINE_V64) nuvioCLabels64(labels) else if (NuvioCFeatures.QUALITY_LINE_V62) nuvioCLabels62(labels) else labels).take(max)
    }
    val leadText = lead?.takeIf { it.isNotBlank() }
    val ratingText = rating?.trim()?.takeIf { it.isNotBlank() }
    if (leadText == null && quality.isEmpty() && ratingText == null) return
    Row(modifier.height(QL_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        if (leadText != null) {
            leadText.split(" • ").forEachIndexed { i, part ->
                if (i > 0) Text(text = "•", style = leadStyle(color).copy(color = color.copy(alpha = QL_DOT_ALPHA)), modifier = Modifier.padding(horizontal = QL_LEAD_DOT_63))
                Text(text = part, style = leadStyle(color), maxLines = 1, softWrap = false)
            }
        }
        if (quality.isNotEmpty() || ratingText != null) {
            if (leadText != null) {
                Spacer(Modifier.width(QL_SEP_SPACE))
                Box(Modifier.width(1.dp).height(if (compact) QL_SEP_HEIGHT_66D else QL_SEP_HEIGHT).background(color.copy(alpha = QL_SEP_ALPHA)))
                Spacer(Modifier.width(QL_SEP_SPACE))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) QL_GAP_66D else QL_GAP_63), verticalAlignment = Alignment.CenterVertically) {
                if (ratingText != null) Pill(ratingText, textStyle, color, QL_HEIGHT)
                if (compact) quality.forEach { label -> CompactBadge66d(label, textStyle, color) }
                else quality.forEach { label ->
                    // [fork] round 66c: on the carousel, word badges are solid light tiles like the Modern player's size badge
                    if (filled && !nuvioCIsLogoLabel(label)) FilledTile66c(label, textStyle)
                    else Badge61(label, textStyle, color, if (NuvioCFeatures.QUALITY_LINE_V62) QL_LOGO_62 else QL_HEIGHT)
                }
            }
        }
    }
}

/** [fork] round 66c: labels drawn as a logo by [Badge61]. */
internal fun nuvioCIsLogoLabel(label: String): Boolean =
    label == "DOLBY VISION" || label == "ATMOS" || label.startsWith("DTS") ||
        (NuvioCFeatures.QUALITY_LINE_V64 && (label == "DOLBY DIGITAL" || label == "DOLBY DIGITAL PLUS"))

/** [fork] round 66c: a solid light tile with dark text, the Modern player's file-size badge look, at the line's height. */
@Composable
private fun FilledTile66c(label: String, style: TextStyle) {
    Box(
        Modifier.height(QL_HEIGHT).background(Color(0xFFDCDCDC), RoundedCornerShape(QL_RADIUS)).padding(horizontal = QL_PAD),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = style.copy(color = Color(0xFF151515), fontWeight = FontWeight.Bold), maxLines = 1, softWrap = false)
    }
}

// [fork] round 66d: the carousel's smaller, quieter badge line
private val QL_HEIGHT_66D = 11.dp
private const val QL_TEXT_SP_66D = 7.5f
private val QL_LOGO_66D = 7.5.dp
private val QL_GAP_66D = 4.dp
private val QL_PAD_66D = 3.5.dp
private val QL_SEP_HEIGHT_66D = 8.dp
private const val QL_TEXT_ALPHA_66D = 0.8f

/**
 * [fork] round 66d: one carousel badge, 11 dp: words as an outlined box with text at 80%; Dolby / DTS logos (DTS:X and
 * DTS-HD keep their suffix) in the same outlined box.
 */
@Composable
private fun CompactBadge66d(label: String, style: TextStyle, color: Color) {
    val text = color.copy(alpha = QL_TEXT_ALPHA_66D)
    val small = style.copy(fontSize = QL_TEXT_SP_66D.sp, lineHeight = (QL_TEXT_SP_66D * 1.15f).sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp, color = text)
    val res = when {
        label == "DOLBY VISION" -> com.nuvio.tv.R.drawable.nuvio_c_logo_dolby_vision
        label == "ATMOS" -> com.nuvio.tv.R.drawable.nuvio_c_logo_dolby_atmos
        label.startsWith("DTS") -> com.nuvio.tv.R.drawable.nuvio_c_logo_dts
        NuvioCFeatures.QUALITY_LINE_V64 && label == "DOLBY DIGITAL" -> com.nuvio.tv.R.drawable.nuvio_c_logo_dolby_digital
        NuvioCFeatures.QUALITY_LINE_V64 && label == "DOLBY DIGITAL PLUS" -> com.nuvio.tv.R.drawable.nuvio_c_logo_dolby_digital_plus
        else -> null
    }
    // [fork] round 66e: resolutions are filled light tiles with dark text (like the Modern player's size badge)
    if (NuvioCFeatures.FEATURED_V66E && res == null && nuvioCIsResolutionLabel(label)) {
        Box(
            Modifier.height(QL_HEIGHT_66D).background(Color(0xFFDCDCDC), RoundedCornerShape(2.dp)).padding(horizontal = QL_PAD_66D),
            contentAlignment = Alignment.Center
        ) {
            Text(text = label, style = small.copy(color = Color(0xFF151515), fontWeight = FontWeight.ExtraBold), maxLines = 1, softWrap = false)
        }
        return
    }
    Box(
        Modifier.height(QL_HEIGHT_66D).border(1.dp, color.copy(alpha = QL_BOX_BORDER_63), RoundedCornerShape(2.dp)).padding(horizontal = QL_PAD_66D),
        contentAlignment = Alignment.Center
    ) {
        if (res == null) {
            Text(text = label, style = small, maxLines = 1, softWrap = false)
        } else {
            val painter = androidx.compose.ui.res.painterResource(res)
            val size = painter.intrinsicSize
            val ratio = if (size.height > 0f) size.width / size.height else 2.5f
            val suffix = if (NuvioCFeatures.QUALITY_LINE_V64) nuvioCDtsSuffix(label) else null
            Row {
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = label,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(text),
                    modifier = Modifier.height(QL_LOGO_66D).width(QL_LOGO_66D * ratio).alignBy { it.measuredHeight }
                )
                if (suffix != null) Text(
                    text = suffix, style = small.copy(fontSize = (QL_TEXT_SP_66D * 0.9f).sp), maxLines = 1, softWrap = false,
                    modifier = Modifier.padding(start = 1.5.dp).alignByBaseline()
                )
            }
        }
    }
}

/** [fork] round 66e: resolution labels (filled on the carousel). */
internal fun nuvioCIsResolutionLabel(label: String): Boolean =
    com.nuvio.tv.data.quality.QualityResolution.entries.any { it.label == label }

/** [fork] round 66d: the carousel badge height, for tests. */
internal val NUVIO_C_CAROUSEL_BADGE_HEIGHT_66D get() = QL_HEIGHT_66D

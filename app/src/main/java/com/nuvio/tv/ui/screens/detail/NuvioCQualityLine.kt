package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 60 `detail_quality_line`: one line of outlined badges under the hero's facts line, e.g.
// 4K | DOLBY VISION | ATMOS | WEB-DL, describing ONE real release (never tags mixed across releases).
// Look: the owner's design (v3, generic labels): grey outline #B9B9C2, light grey text #C9C9D1, dim pills at 45%,
// dashed dark ghosts while checking. Sizes follow the facts text (badge text 0.75 of it).
// No jump: the hero is bottom-anchored, so the line holds a fixed height from the first frame in every state
// where a lookup is possible, and is absent only when there's no usable ID (known before the first frame).
// Its result lives in this leaf's own view model, never in MetaDetailsUiState, so only this line recomposes.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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

    init { viewModelScope.launch(Dispatchers.IO) { repo.cache.warm() } }

    /** What the first frame shows, without waiting for anything. */
    fun initial(t: QualityTitle): QualityLineState {
        val today = LocalDate.now()
        return when (val p = TitleQualityPlanner.plan(t, today)) {
            QualityPlan.NoLine -> QualityLineState.Absent
            QualityPlan.NotYetReleased -> QualityLineState.Dim(QualityLineState.NOT_YET_RELEASED)
            is QualityPlan.NeedsImdb -> QualityLineState.Checking
            is QualityPlan.Lookup -> repo.cache.peek(p.cacheKey)?.toState() ?: QualityLineState.Checking
        }
    }

    fun bind(t: QualityTitle) {
        val key = t.id + "|" + t.episodes.size
        if (bound == key) return
        bound = key
        job?.cancel()
        _state.value = t.id to initial(t)
        job = viewModelScope.launch { run(t) }
    }

    private suspend fun run(t: QualityTitle) {
        val today = LocalDate.now()
        var plan = TitleQualityPlanner.plan(t, today)
        val needs = plan as? QualityPlan.NeedsImdb
        if (needs != null) {
            val imdb = withTimeoutOrNull(5_000) {
                runCatching { tmdbService.tmdbToImdb(needs.tmdbId, if (t.isSeries) "series" else "movie") }.getOrNull()
            }
            plan = if (imdb.isNullOrBlank()) { publish(t, QualityLineState.Dim(QualityLineState.UNAVAILABLE)); return }
            else TitleQualityPlanner.plan(t, today, imdbOverride = imdb)
        }
        val lookup = plan as? QualityPlan.Lookup ?: return
        withContext(Dispatchers.IO) { repo.cache.get(lookup.cacheKey) }?.let { publish(t, it.toState()); return }
        val (result, nextRefresh) = repo.lookup(lookup.targets, lookup.year) { /* pending: ghosts stay for the re-poll */ }
        val now = System.currentTimeMillis()
        when (result) {
            is QualityLookup.Found -> {
                repo.cache.put(lookup.cacheKey, CachedQuality(CachedKind.FOUND, result.quality, now,
                    TitleQualityPlanner.expiry(now, lookup.releaseDate, today, nextRefresh)))
                publish(t, QualityLineState.Found(result.quality.labels))
            }
            else -> {
                val info = NuvioCDetailInfoStore.current.value?.takeIf { it.metaId == t.id } ?: NuvioCDetailInfoStore.cached(t.id)
                val label = TitleQualityPlanner.emptyLabel(result, t.isSeries, info?.theatricalDate ?: t.released, info?.homeReleaseDate, today)
                if (label == QualityLineState.NOT_YET_ON_DIGITAL) {
                    repo.cache.put(lookup.cacheKey, CachedQuality(CachedKind.NOT_ON_DIGITAL, null, now, now + TitleQualityPlanner.NOT_ON_DIGITAL_MS))
                }
                publish(t, QualityLineState.Dim(label))
            }
        }
    }

    private fun publish(t: QualityTitle, s: QualityLineState) { _state.value = t.id to s }

    private fun CachedQuality.toState(): QualityLineState = when (kind) {
        CachedKind.FOUND -> quality?.let { QualityLineState.Found(it.labels) } ?: QualityLineState.Checking
        CachedKind.NOT_ON_DIGITAL -> QualityLineState.Dim(QualityLineState.NOT_YET_ON_DIGITAL)
    }
}

private val QL_BORDER = Color(0xFFB9B9C2)
private val QL_TEXT = Color(0xFFC9C9D1)
private val QL_GHOST = Color(0xFF3A3A46)
private const val QL_DIM_ALPHA = 0.45f
private val QL_GHOST_LABELS = listOf("4K", "DOLBY VISION", "ATMOS")

/** The badge line, or nothing when the title has no usable ID. [factsStyle] is the facts line's text style. */
@Composable
internal fun NuvioCQualityLine(meta: Meta, factsStyle: TextStyle, modifier: Modifier = Modifier) {
    if (!NuvioCFeatures.DETAIL_QUALITY_LINE) return
    val vm: NuvioCQualityViewModel = hiltViewModel()
    val title = remember(meta.id, meta.imdbId, meta.released, meta.videos.size) { meta.nuvioCQualityTitle() }
    val first = remember(title) { vm.initial(title) }
    if (first == QualityLineState.Absent) return
    LaunchedEffect(title) { vm.bind(title) }
    val held by vm.state.collectAsState()
    val state = held?.takeIf { it.first == meta.id }?.second ?: first

    // sizes from the facts text: badge text 0.75 of it; border, padding, gap and height as multiples of that
    val factsSp = if (factsStyle.fontSize.isSpecified) factsStyle.fontSize.value else 12f
    val badgeSp = factsSp * 0.75f
    val density = LocalDensity.current
    val unit = with(density) { badgeSp.sp.toDp() }
    val lineHeight = unit * 2.2f
    val textStyle = factsStyle.copy(
        fontSize = badgeSp.sp, lineHeight = (badgeSp * 1.2f).sp, fontWeight = FontWeight.Normal,
        letterSpacing = 0.02.em, color = QL_TEXT
    )

    // fade a result in (draw phase only, never the height)
    val fade = remember(meta.id) { Animatable(1f) }
    LaunchedEffect(state) {
        // a result that arrives after the first frame fades in; a cached one is simply there
        if (state != first && (state is QualityLineState.Found || state is QualityLineState.Dim)) { fade.snapTo(0f); fade.animateTo(1f, tween(220)) }
    }
    Box(modifier.height(lineHeight).graphicsLayer { alpha = fade.value }, contentAlignment = Alignment.CenterStart) {
        when (state) {
            QualityLineState.Absent -> Unit
            QualityLineState.Checking -> BadgeRow(unit) { QL_GHOST_LABELS.forEach { Ghost(it, textStyle, unit) } }
            is QualityLineState.Dim -> Box(Modifier.graphicsLayer { alpha = QL_DIM_ALPHA }) { Badge(state.label, textStyle, unit) }
            is QualityLineState.Found -> BadgeRow(unit) { state.labels.forEach { Badge(it, textStyle, unit) } }
        }
    }
}

@Composable
private fun Badge(label: String, style: TextStyle, unit: androidx.compose.ui.unit.Dp) {
    Text(
        text = label, style = style, maxLines = 1, softWrap = false,
        modifier = Modifier
            .border(unit * 0.12f, QL_BORDER, RoundedCornerShape(unit * 0.24f))
            .padding(horizontal = unit * 0.47f, vertical = unit * 0.12f)
    )
}

@Composable
private fun Ghost(label: String, style: TextStyle, unit: androidx.compose.ui.unit.Dp) {
    Text(
        text = label, style = style.copy(color = Color.Transparent), maxLines = 1, softWrap = false,
        modifier = Modifier
            .drawBehind {
                val w = (unit * 0.12f).toPx()
                drawRoundRect(
                    color = QL_GHOST, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius((unit * 0.24f).toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                )
            }
            .padding(horizontal = unit * 0.47f, vertical = unit * 0.12f)
    )
}

/**
 * Badges in a row; if they don't fit the width, source goes first, then audio, then dynamic range. The first
 * badge (resolution) always stays. Labels come in slot order, so the drop order is last-to-first.
 */
@Composable
private fun BadgeRow(unit: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val gap = (unit * 0.47f).roundToPx()
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

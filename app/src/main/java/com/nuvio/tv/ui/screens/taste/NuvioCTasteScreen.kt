package com.nuvio.tv.ui.screens.taste

// [fork] Nuvio C round 39 `taste_page`: the profile's Taste page (sidebar, under Calendar).
//
// Layout, top to bottom: header (profile dot, "Your taste", time range), the one-line summary, four headline numbers,
// Top genres beside How you rate (or What you pick), When you watch beside Decades, Your people, then the shelves:
// Spin my taste, Your gaps, Unfinished, Rewatch. Sections appear only once there's enough to be true (thresholds in
// NuvioCTasteStats); an empty profile gets a short invitation instead. Charts are drawn once (no animation), the only
// pictures are people's photos, the shelves' posters and a dim backdrop collage fetched small.
// Everything that looks tappable is: genres and decades start Roulette with that choice, people open their page,
// posters open the title.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat
import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteRepository
import com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem
import com.nuvio.tv.data.nuvioc.NuvioCTasteState
import com.nuvio.tv.data.nuvioc.NuvioCTasteStats
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.shuffle.NuvioCRoulettePreset
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

private val EDGE = 48.dp

@HiltViewModel
class NuvioCTasteViewModel @Inject constructor(
    private val repository: NuvioCTasteRepository,
    private val profiles: ProfileManager
) : ViewModel() {
    val state: StateFlow<NuvioCTasteState> = repository.state
    private val _gaps = MutableStateFlow<List<NuvioCTasteShelfItem>>(emptyList())
    val gaps: StateFlow<List<NuvioCTasteShelfItem>> = _gaps.asStateFlow()
    private var gapsFor = -1L

    val accent: Color
        get() = runCatching { Color(android.graphics.Color.parseColor(profiles.activeProfile?.avatarColorHex ?: "")) }
            .getOrDefault(Color(0xFF6FA8FF))
    val profileName: String get() = profiles.activeProfile?.name.orEmpty()

    fun open(force: Boolean = false) {
        viewModelScope.launch { repository.open(force) }
    }

    /** One discover call per build, only once the page has its numbers. */
    fun loadGaps(stats: NuvioCTasteStats, builtAt: Long) {
        if (builtAt == gapsFor || stats.genres.isEmpty()) return
        gapsFor = builtAt
        viewModelScope.launch {
            _gaps.value = runCatching { repository.gaps(stats, repository.watchedKeys()) }.getOrDefault(emptyList())
        }
    }
}

@Composable
fun NuvioCTasteScreen(
    onOpenTitle: (id: String, type: String) -> Unit,
    onOpenPerson: (id: Int, name: String, crew: Boolean) -> Unit,
    onOpenRoulette: () -> Unit,
    viewModel: NuvioCTasteViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val gaps by viewModel.gaps.collectAsState()
    var range by rememberSaveable { mutableStateOf(NuvioCTasteRange.ALL) }
    LaunchedEffect(Unit) { viewModel.open() }
    val stats = state.all[range] ?: state.all[NuvioCTasteRange.ALL]
    val all = state.all[NuvioCTasteRange.ALL]
    LaunchedEffect(state.builtAt) { all?.let { viewModel.loadGaps(it, state.builtAt) } }
    val accent = remember { viewModel.accent }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(stats == null) { firstFocus.requestFocusAfterFrames() }

    fun spin(genre: String?, year: Int?) {
        NuvioCRoulettePreset.set(
            NuvioCRoulettePreset.Preset(
                mood = NuvioCRoulettePreset.moodFor(genre),
                decade = NuvioCRoulettePreset.decadeFor(year)
            )
        )
        onOpenRoulette()
    }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        TasteBackdrop(all?.backdrops.orEmpty())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item(key = "header") {
                TasteHeader(
                    name = viewModel.profileName,
                    accent = accent,
                    range = range,
                    showRanges = all?.showNumbers == true,
                    onRange = { range = it },
                    onRefresh = { viewModel.open(force = true) },
                    firstFocus = firstFocus
                )
            }
            val s = stats
            item(key = "summary") {
                Column(Modifier.padding(horizontal = EDGE)) {
                    Text(
                        text = when {
                            s == null && state.pending > 0 -> "Getting your history ready (${state.pending} titles to look up the first time)…"
                            s == null -> "Getting your history ready…"
                            else -> NuvioCTasteCopy.summary(s)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (s?.localOnly == true) {
                        Text(NuvioCTasteCopy.localLine(), style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary)
                    }
                }
            }
            if (s == null) return@LazyColumn
            if (s.empty) {
                item(key = "empty") { TasteEmpty(accent, onStart = { spin(null, null) }) }
                return@LazyColumn
            }
            if (s.showNumbers) item(key = "metrics") { TasteMetrics(s) }
            item(key = "genres_ratings") {
                Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1.1f)) {
                        if (s.genres.isNotEmpty()) TasteGenres(s, accent, compact = !s.showGenres) { spin(it, null) }
                    }
                    Column(Modifier.weight(1f)) {
                        if (s.showRatings) TasteRatings(s, accent) else TastePick(s, accent)
                    }
                }
            }
            if (s.showHabits || s.decades.size >= 2) item(key = "habits_decades") {
                Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1.1f)) { if (s.showHabits) TasteHabits(s, accent) }
                    Column(Modifier.weight(1f)) { if (s.decades.size >= 2 && s.showNumbers) TasteDecades(s, accent) { spin(s.genres.firstOrNull()?.name, it) } }
                }
            }
            if (s.showPeople) item(key = "people") { TastePeople(s, onOpenPerson) }
            item(key = "spin") {
                Column(Modifier.padding(horizontal = EDGE)) {
                    SectionTitle("Do something with it")
                    TasteButton("Spin my taste", accent, primary = true) { spin(s.genres.firstOrNull()?.name, s.topDecade) }
                }
            }
            if (gaps.isNotEmpty() && range == NuvioCTasteRange.ALL) item(key = "gaps") {
                TasteShelf("Your gaps", "Well-loved ${s.genres.firstOrNull()?.name?.lowercase() ?: ""} you haven't seen yet.", gaps, onOpenTitle)
            }
            if (s.unfinished.isNotEmpty() && range == NuvioCTasteRange.ALL) item(key = "unfinished") {
                TasteShelf("Unfinished", "Shows you started and stepped away from.", s.unfinished, onOpenTitle)
            }
            if (s.rewatch.isNotEmpty() && range == NuvioCTasteRange.ALL) item(key = "rewatch") {
                TasteShelf(
                    "Rewatch",
                    if (s.anyRatings) "Films you rated 9 or higher and haven't seen in three years." else "Films you finished that the crowd rates highly, not seen in three years.",
                    s.rewatch,
                    onOpenTitle
                )
            }
            NuvioCTasteCopy.growingLine(s)?.let { line ->
                item(key = "growing") {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary, modifier = Modifier.padding(horizontal = EDGE))
                }
            }
        }
    }
}

// ---- parts -----------------------------------------------------------------------------------------------

@Composable
private fun TasteBackdrop(urls: List<String>) {
    if (urls.isEmpty()) return
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().height(420.dp).graphicsLayer { alpha = 0.22f }) {
        urls.take(3).forEach { url ->
            val request = remember(url) { ImageRequest.Builder(context).data(url).size(320, 180).crossfade(300).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
    Box(
        Modifier.fillMaxWidth().height(420.dp).background(
            Brush.verticalGradient(listOf(NuvioTheme.colors.Background.copy(alpha = 0.35f), NuvioTheme.colors.Background))
        )
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteHeader(
    name: String,
    accent: Color,
    range: NuvioCTasteRange,
    showRanges: Boolean,
    onRange: (NuvioCTasteRange) -> Unit,
    onRefresh: () -> Unit,
    firstFocus: FocusRequester
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(14.dp))
        Text("Your taste", style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (showRanges) {
            NuvioCTasteRange.entries.forEachIndexed { i, r ->
                TastePill(NuvioCTasteCopy.rangeLabel(r), selected = r == range, accent = accent,
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) { onRange(r) }
                Spacer(Modifier.width(8.dp))
            }
        }
        Surface(
            onClick = onRefresh,
            modifier = (if (!showRanges) Modifier.focusRequester(firstFocus) else Modifier).size(36.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = NuvioTheme.colors.TextSecondary,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black
            )
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePill(label: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = if (selected) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.10f)), shape = RoundedCornerShape(50))
        )
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String, line: String? = null) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
    if (line != null) {
        Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TasteMetrics(s: NuvioCTasteStats) {
    val nf = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val tiles = buildList {
        add("Hours watched" to nf.format(s.hours))
        add((if (s.shows > 0 && s.movies > 0) "Films and shows" else if (s.shows > 0) "Shows" else "Films") to nf.format(s.titles))
        if (s.rated >= 5) add("Titles rated" to nf.format(s.rated))
        else if (s.episodes > 0) add("Episodes" to nf.format(s.episodes))
        when {
            s.finishedPercent != null -> add("Shows finished" to "${s.finishedPercent}%")
            s.longestStreak >= 3 -> add("Longest streak" to "${s.longestStreak} days")
            s.episodes > 0 && s.rated >= 5 -> add("Episodes" to nf.format(s.episodes))
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { (label, value) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
                Text(value, style = MaterialTheme.typography.headlineSmall, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteGenres(s: NuvioCTasteStats, accent: Color, compact: Boolean, onGenre: (String) -> Unit) {
    SectionTitle("Top genres")
    if (compact) {
        Text(s.genres.take(3).joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextPrimary)
        return
    }
    val max = s.genres.maxOf { it.percent }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        s.genres.take(5).forEachIndexed { i, g ->
            Surface(
                onClick = { onGenre(g.name) },
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = NuvioTheme.colors.TextSecondary,
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    focusedContentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
                    val fraction = g.percent.toFloat() / max
                    val color = if (i < 2) accent else Color.White.copy(alpha = 0.32f)
                    Canvas(Modifier.weight(1f).height(8.dp)) {
                        drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
                    }
                    Text("${g.percent}%", style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(start = 10.dp).width(36.dp))
                }
            }
        }
    }
}

@Composable
private fun TasteRatings(s: NuvioCTasteStats, accent: Color) {
    SectionTitle("How you rate", NuvioCTasteCopy.ratingLine(s))
    val counts = s.ratingCounts
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val muted = Color.White.copy(alpha = 0.20f)
    Canvas(Modifier.fillMaxWidth().height(86.dp)) {
        val gap = 5.dp.toPx()
        val w = (size.width - gap * (counts.size - 1)) / counts.size
        counts.forEachIndexed { i, c ->
            val h = (size.height * c / max).coerceAtLeast(2.dp.toPx())
            drawRoundRect(if (i >= 6) accent else muted, topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h), cornerRadius = CornerRadius(3.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("½ star", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary)
        Spacer(Modifier.weight(1f))
        Text("5 stars", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary)
    }
}

@Composable
private fun TastePick(s: NuvioCTasteStats, accent: Color) {
    val avg = s.crowdAverage ?: return
    SectionTitle("What you pick")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(String.format(Locale.US, "%.1f", avg), style = MaterialTheme.typography.displaySmall, color = accent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("average crowd rating", style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
    }
    NuvioCTasteCopy.pickLine(s)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary) }
}

@Composable
private fun TasteHabits(s: NuvioCTasteStats, accent: Color) {
    SectionTitle("When you watch", NuvioCTasteCopy.habitLine(s) ?: NuvioCTasteCopy.finishLine(s))
    val heat = s.heat
    val max = (heat.maxOrNull() ?: 0).coerceAtLeast(1)
    val empty = Color.White.copy(alpha = 0.07f)
    Canvas(Modifier.fillMaxWidth().height(84.dp)) {
        val cols = 26
        val gap = 3.dp.toPx()
        val cell = minOf((size.width - gap * (cols - 1)) / cols, (size.height - gap * 6) / 7)
        heat.forEachIndexed { i, c ->
            val col = i / 7
            val row = i % 7
            val color = if (c == 0) empty else accent.copy(alpha = 0.35f + 0.65f * c / max)
            drawRoundRect(color, topLeft = Offset(col * (cell + gap), row * (cell + gap)), size = Size(cell, cell), cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
    if (s.habitLine() != null) NuvioCTasteCopy.finishLine(s)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun NuvioCTasteStats.habitLine(): String? = NuvioCTasteCopy.habitLine(this)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteDecades(s: NuvioCTasteStats, accent: Color, onDecade: (Int) -> Unit) {
    SectionTitle("Decades")
    val max = s.decades.maxOf { it.second }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        s.decades.takeLast(8).forEach { (decade, count) ->
            Surface(
                onClick = { onDecade(decade) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = NuvioTheme.colors.TextTertiary,
                    focusedContainerColor = Color.White.copy(alpha = 0.10f),
                    focusedContentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    val color = if (decade == s.topDecade) accent else Color.White.copy(alpha = 0.32f)
                    Canvas(Modifier.fillMaxWidth().weight(1f)) {
                        val h = (size.height * count / max).coerceAtLeast(2.dp.toPx())
                        drawRoundRect(color, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = CornerRadius(3.dp.toPx()))
                    }
                    Text(NuvioCTasteCopy.decade(decade), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePeople(s: NuvioCTasteStats, onOpenPerson: (Int, String, Boolean) -> Unit) {
    Column {
        Column(Modifier.padding(horizontal = EDGE)) { SectionTitle("Your people", NuvioCTasteCopy.peopleLine(s)) }
        LazyRow(contentPadding = PaddingValues(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(s.people, key = { it.person.id }) { p -> TastePersonCard(p, onOpenPerson) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePersonCard(p: NuvioCTastePersonStat, onOpenPerson: (Int, String, Boolean) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = { onOpenPerson(p.person.id, p.person.name, p.director) },
            modifier = Modifier.size(80.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = Color.White.copy(alpha = 0.08f)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape))
        ) {
            val photo = p.person.photo
            if (photo != null) {
                val request = remember(photo) { ImageRequest.Builder(context).data(photo).size(160, 160).crossfade(200).build() }
                AsyncImage(model = request, contentDescription = p.person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(p.person.name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextSecondary)
                }
            }
        }
        Text(p.person.name, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(if (p.director) "Director · ${p.count}" else "${p.count} titles", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteButton(label: String, accent: Color, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(com.nuvio.tv.ui.components.NuvioCIcons.Dice, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteShelf(title: String, line: String, items: List<NuvioCTasteShelfItem>, onOpenTitle: (String, String) -> Unit) {
    val context = LocalContext.current
    Column {
        Column(Modifier.padding(horizontal = EDGE)) { SectionTitle(title, line) }
        LazyRow(contentPadding = PaddingValues(horizontal = EDGE, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { item ->
                Column(Modifier.width(120.dp)) {
                    Card(
                        onClick = { onOpenTitle(item.id, item.type) },
                        modifier = Modifier.width(120.dp).height(180.dp),
                        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                        scale = CardDefaults.scale(focusedScale = 1.06f),
                        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(12.dp))),
                        colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f))
                    ) {
                        if (item.poster != null) {
                            val request = remember(item.poster) { ImageRequest.Builder(context).data(item.poster).size(240, 360).crossfade(200).build() }
                            AsyncImage(model = request, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                                Text(item.name, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 4)
                            }
                        }
                    }
                    Text(item.name, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TasteEmpty(accent: Color, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = EDGE, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Default.Insights, contentDescription = null, tint = NuvioTheme.colors.TextTertiary, modifier = Modifier.size(40.dp))
        Text(NuvioCTasteCopy.empty(), style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextSecondary)
        TasteButton("Start with Roulette", accent, primary = true, onClick = onStart)
    }
}

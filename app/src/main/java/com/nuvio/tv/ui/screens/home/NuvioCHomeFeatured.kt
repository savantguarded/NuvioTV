package com.nuvio.tv.ui.screens.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.LocalContentFocusRequester
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.NuvioCHeroAutoAdvanceEffect
import com.nuvio.tv.ui.components.NuvioCHeroSpec
import com.nuvio.tv.ui.components.NuvioCLogoSize
import com.nuvio.tv.ui.components.nuvioCHomeMargin
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.formatHeroRuntime
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import com.nuvio.tv.ui.util.nuvioCLogoContrast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// [fork] Nuvio C home_featured (2026-10-15): fourth home layout "Featured".
// A full-screen carousel of your hero catalogs (Settings > Layout > Hero catalogs) sits on top of
// Modern home. Play / Details buttons; Left / Right move between them and page past the ends
// (Right from Details = next title, Left from Play = previous; on the first title Left reaches the
// sidebar as usual). Down slides the carousel away and focuses the rows; Up on the first row, or
// Back when Modern has nothing of its own to do, brings it back on the same title.
// Stored as Modern + the `nuvio_c_home_featured` flag, so every Modern behaviour stays as it is.
// While the carousel covers the screen Modern is composed but not drawn, and it can't take focus.

/** True inside the Featured layout: the Modern hero synopsis uses the details-page text style. */
val LocalNuvioCHomeDetailText = staticCompositionLocalOf { false }

internal object NuvioCFeaturedRules {
    private val YEAR = Regex("""\b(19|20)\d{2}\b""")

    fun contentIdOf(item: ContinueWatchingItem): String = when (item) {
        is ContinueWatchingItem.InProgress -> item.progress.contentId
        is ContinueWatchingItem.NextUp -> item.info.contentId
    }

    fun continueItemFor(ids: Collection<String?>, items: List<ContinueWatchingItem>): ContinueWatchingItem? {
        val wanted = ids.filterNotNull().filter { it.isNotBlank() }.toSet()
        if (wanted.isEmpty()) return null
        return items.firstOrNull { contentIdOf(it) in wanted }
    }

    fun isMovie(apiType: String): Boolean = apiType.equals("movie", ignoreCase = true)

    /** Play button text: kind 0 = Play, 1 = Resume; season / episode when known. */
    data class Label(val resume: Boolean, val season: Int?, val episode: Int?)

    fun labelFor(cw: ContinueWatchingItem?): Label = when (cw) {
        null -> Label(false, null, null)
        is ContinueWatchingItem.InProgress -> Label(true, cw.progress.season, cw.progress.episode)
        is ContinueWatchingItem.NextUp -> Label(false, cw.info.season, cw.info.episode)
    }

    fun year(releaseInfo: String?): String? = releaseInfo?.let { YEAR.find(it)?.value }

    fun nextIndex(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1) % count
    fun previousIndex(index: Int, count: Int): Int = if (count <= 0) 0 else (index - 1 + count) % count
}

@HiltViewModel
class NuvioCHomeFeaturedViewModel @Inject constructor(
    private val layoutStore: LayoutPreferenceDataStore
) : ViewModel() {

    /** null until the store has answered, so Home never flashes plain Modern first. */
    val featured: StateFlow<Boolean?> = layoutStore.nuvioCHomeFeatured
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setFeatured(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) layoutStore.setLayout(HomeLayout.MODERN)
            layoutStore.setNuvioCHomeFeatured(enabled)
        }
    }
}

/**
 * Home's Modern branch goes through here. Off (switch, or Modern chosen in settings) = [content]
 * exactly as before.
 */
@Composable
internal fun NuvioCFeaturedGate(
    uiState: HomeUiState,
    modernPresentation: ModernHomePresentationState,
    scrollToTopTrigger: Int,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onPlayFromDetails: ((String, String, String?) -> Unit)?,
    content: @Composable () -> Unit
) {
    if (!NuvioCFeatures.HOME_FEATURED) {
        content()
        return
    }
    val viewModel: NuvioCHomeFeaturedViewModel = hiltViewModel()
    val featured by viewModel.featured.collectAsStateWithLifecycle()
    when (featured) {
        null -> Box(Modifier.fillMaxSize())
        false -> content()
        true -> NuvioCFeaturedHome(
            uiState = uiState,
            firstRow = modernPresentation.rows.list.firstOrNull(),
            scrollToTopTrigger = scrollToTopTrigger,
            viewModel = viewModel,
            onNavigateToDetail = onNavigateToDetail,
            onContinueWatchingClick = onContinueWatchingClick,
            onPlayFromDetails = onPlayFromDetails,
            content = content
        )
    }
}

private const val SLIDE_MS = 450
private val PEEK_TITLE = 22.dp
private val PEEK_POSTERS = 24.dp

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun NuvioCFeaturedHome(
    uiState: HomeUiState,
    firstRow: HeroCarouselRow?,
    scrollToTopTrigger: Int,
    viewModel: NuvioCHomeFeaturedViewModel,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onPlayFromDetails: ((String, String, String?) -> Unit)?,
    content: @Composable () -> Unit
) {
    val items = uiState.heroItems
    val available = uiState.heroSectionEnabled && items.isNotEmpty()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val outerRequester = LocalContentFocusRequester.current
    val rowsRequester = remember { FocusRequester() }
    val playRequester = remember { FocusRequester() }
    val detailsRequester = remember { FocusRequester() }

    var shown by rememberSaveable { mutableStateOf(true) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsHadFocus by rememberSaveable { mutableStateOf(false) }
    var rowsFocused by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    val carouselOn = shown && available
    val progress = remember { Animatable(if (carouselOn) 0f else 1f) } // 0 = carousel, 1 = rows

    // Same title kept when the list is rebuilt (enrichment, catalogs arriving, refresh).
    LaunchedEffect(items) {
        index = com.nuvio.tv.ui.components.NuvioCHeroAutoAdvance.indexAfterListChange(items, activeId, index)
    }
    LaunchedEffect(index, items) { activeId = items.getOrNull(index)?.id }

    fun focusCarousel() {
        scope.launch {
            repeat(2) { withFrameNanos { } }
            runCatching { (if (detailsHadFocus) detailsRequester else playRequester).requestFocus() }
        }
    }

    fun goToRows() {
        if (!shown) return
        shown = false
        scope.launch {
            repeat(2) { withFrameNanos { } }
            runCatching { rowsRequester.requestFocus() }
        }
    }

    fun goToCarousel() {
        if (shown || !available) return
        shown = true
        focusCarousel()
    }

    // Hero items arriving after you already moved into the rows never pop the carousel over you.
    LaunchedEffect(available) {
        if (available && rowsFocused) shown = false
    }
    LaunchedEffect(carouselOn) {
        progress.animateTo(if (carouselOn) 0f else 1f, tween(SLIDE_MS, easing = FastOutSlowInEasing))
    }
    // Opening / coming back to Home with the carousel up: focus its button (Modern can't take it).
    LaunchedEffect(carouselOn) {
        if (carouselOn) focusCarousel()
    }
    // Sidebar "Home" again (scroll to top) = back to the carousel.
    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(scrollToTopTrigger) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger != lastScrollTrigger) {
            lastScrollTrigger = scrollToTopTrigger
            goToCarousel()
        }
    }
    // Back from the rows (when Modern itself has nothing to undo) returns to the carousel. Declared
    // before Modern's own handlers, so theirs still win while they are enabled.
    BackHandler(enabled = available && !shown && rowsFocused) { goToCarousel() }

    val activeItem = items.getOrNull(index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val cwItems = uiState.continueWatchingItems
    // Play: a Continue Watching title resumes exactly as its CW card would; anything else opens the
    // details page with official "play on load" (as post-play "Play" does), so its own logic picks
    // the resume point / next unwatched episode / first episode and the sources.
    val play: () -> Unit = play@{
        val item = activeItem ?: return@play
        val cw = NuvioCFeaturedRules.continueItemFor(listOf(item.id, item.imdbId), cwItems)
        when {
            cw != null -> onContinueWatchingClick(cw)
            onPlayFromDetails != null -> onPlayFromDetails(item.id, item.apiType, item.backdropUrl)
            else -> onNavigateToDetail(item.id, item.apiType, "")
        }
    }

    val atRest by remember { derivedStateOf { progress.value == 0f } }
    val carouselDrawn by remember { derivedStateOf { progress.value < 1f } }
    NuvioCHeroAutoAdvanceEffect(
        count = if (carouselOn && atRest) items.size else 0,
        activeIndex = index,
        interaction = interaction
    ) { index = NuvioCFeaturedRules.nextIndex(index, items.size) }

    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(outerRequester)
            .focusProperties {
                enter = { if (carouselOn) (if (detailsHadFocus) detailsRequester else playRequester) else FocusRequester.Default }
            }
            .focusGroup()
    ) {
        // Modern home, as always. Not drawn and not focusable while the carousel covers it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (1f - progress.value) * screenHeightPx * 0.35f }
                .drawWithContent { if (progress.value > 0f) drawContent() }
                .onFocusChanged { rowsFocused = it.hasFocus }
                .focusProperties { onEnter = { if (carouselOn) cancelFocusChange() } }
                .focusGroup()
                .onKeyEvent { event ->
                    // Up with nothing above (first row) = back to the carousel.
                    if (available && !shown && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                        if (!focusManager.moveFocus(FocusDirection.Up)) goToCarousel()
                        true
                    } else {
                        false
                    }
                }
        ) {
            CompositionLocalProvider(
                LocalContentFocusRequester provides rowsRequester,
                LocalNuvioCHomeDetailText provides true
            ) {
                content()
            }
        }

        if (available && (carouselOn || carouselDrawn)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = -progress.value * screenHeightPx }
                    .background(NuvioTheme.colors.Background)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        interaction++
                        if (event.key == Key.DirectionDown) {
                            goToRows()
                            true
                        } else if (event.key == Key.DirectionUp) {
                            true
                        } else {
                            false
                        }
                    }
            ) {
                NuvioCFeaturedSlide(
                    items = items,
                    index = index.coerceIn(0, items.size - 1),
                    cwItems = cwItems,
                    focusable = carouselOn,
                    playRequester = playRequester,
                    detailsRequester = detailsRequester,
                    peekRow = firstRow,
                    landscapePeek = uiState.modernLandscapePostersEnabled,
                    posterWidthDp = uiState.posterCardWidthDp,
                    onPlay = play,
                    onDetails = { activeItem?.let { onNavigateToDetail(it.id, it.apiType, "") } },
                    onButtonFocused = { details -> detailsHadFocus = details },
                    onNext = {
                        index = NuvioCFeaturedRules.nextIndex(index, items.size)
                        detailsHadFocus = false
                        runCatching { playRequester.requestFocus() }
                    },
                    onPrevious = {
                        index = NuvioCFeaturedRules.previousIndex(index, items.size)
                        detailsHadFocus = true
                        runCatching { detailsRequester.requestFocus() }
                    }
                )
            }
        }
    }
}

private fun backdropRequest(context: Context, url: String?, widthPx: Int, heightPx: Int): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(widthPx, heightPx)
        .crossfade(false)
        .build()

private fun logoRequest(context: Context, url: String?): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(680, 220)
        .crossfade(false)
        .nuvioCLogoContrast()
        .build()

@Composable
private fun NuvioCFeaturedSlide(
    items: List<MetaPreview>,
    index: Int,
    cwItems: List<ContinueWatchingItem>,
    focusable: Boolean,
    playRequester: FocusRequester,
    detailsRequester: FocusRequester,
    peekRow: HeroCarouselRow?,
    landscapePeek: Boolean,
    posterWidthDp: Int,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onButtonFocused: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val widthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    val heightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }.coerceAtLeast(1)
    val item = items[index]
    val bg = NuvioTheme.colors.Background
    val margin = nuvioCHomeMargin
    val peekHeight = if (peekRow != null) PEEK_TITLE + PEEK_POSTERS + 6.dp else 0.dp

    // Next title's pictures fetched ahead, so a slide change shows them at once.
    LaunchedEffect(index, items) {
        if (items.size < 2) return@LaunchedEffect
        val next = items[NuvioCFeaturedRules.nextIndex(index, items.size)]
        runCatching {
            next.backdropUrl?.let { context.imageLoader.enqueue(backdropRequest(context, it, widthPx, heightPx)) }
            next.logo?.let { context.imageLoader.enqueue(logoRequest(context, it)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = index, animationSpec = tween(400), label = "nuvioCFeaturedBackdrop") { i ->
            val slide = items.getOrNull(i) ?: return@Crossfade
            val model = remember(slide.backdropUrl, widthPx, heightPx) { backdropRequest(context, slide.backdropUrl, widthPx, heightPx) }
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Scrims: left for the text, bottom into the page colour under the peek row. Plain draws, no layer.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val left = Brush.horizontalGradient(
                        0f to bg.copy(alpha = 0.92f),
                        0.30f to bg.copy(alpha = 0.72f),
                        0.55f to bg.copy(alpha = 0.25f),
                        0.75f to Color.Transparent,
                        endX = size.width
                    )
                    val bottom = Brush.verticalGradient(
                        0.50f to Color.Transparent,
                        0.78f to bg.copy(alpha = 0.70f),
                        0.92f to bg,
                        1f to bg,
                        endY = size.height
                    )
                    onDrawBehind {
                        drawRect(left)
                        drawRect(bottom)
                    }
                }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = margin, bottom = peekHeight + 22.dp)
                .fillMaxWidth(0.46f),
            verticalArrangement = Arrangement.Bottom
        ) {
            Crossfade(targetState = index, animationSpec = tween(300), label = "nuvioCFeaturedText") { i ->
                items.getOrNull(i)?.let { NuvioCFeaturedText(it) }
            }
            Spacer(Modifier.height(18.dp))
            val cw = remember(item.id, item.imdbId, cwItems) {
                NuvioCFeaturedRules.continueItemFor(listOf(item.id, item.imdbId), cwItems)
            }
            val label = NuvioCFeaturedRules.labelFor(cw)
            val playText = when {
                label.season != null && label.episode != null -> stringResource(
                    if (label.resume) R.string.nuvio_c_featured_resume_episode else R.string.nuvio_c_featured_play_episode,
                    label.season, label.episode
                )
                label.resume -> stringResource(R.string.nuvio_c_featured_resume)
                else -> stringResource(R.string.nuvio_c_featured_play)
            }
            val latestNext by rememberUpdatedState(onNext)
            val latestPrevious by rememberUpdatedState(onPrevious)
            var playFocused by remember { mutableStateOf(false) }
            var detailsFocused by remember { mutableStateOf(false) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .focusProperties { canFocus = focusable }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || items.size < 2) return@onPreviewKeyEvent false
                        when {
                            event.key == Key.DirectionRight && detailsFocused -> { latestNext(); true }
                            event.key == Key.DirectionLeft && playFocused && index > 0 -> { latestPrevious(); true }
                            else -> false
                        }
                    }
            ) {
                com.nuvio.tv.ui.screens.detail.PlayButton(
                    text = playText,
                    enabled = focusable,
                    onClick = onPlay,
                    focusRequester = playRequester,
                    modifier = Modifier.onFocusChanged {
                        playFocused = it.isFocused
                        if (it.isFocused) onButtonFocused(false)
                    }
                )
                NuvioCFeaturedDetailsButton(
                    enabled = focusable,
                    onClick = onDetails,
                    modifier = Modifier
                        .focusRequester(detailsRequester)
                        .onFocusChanged {
                            detailsFocused = it.isFocused
                            if (it.isFocused) onButtonFocused(true)
                        }
                )
            }
            if (items.size > 1) {
                Spacer(Modifier.height(16.dp))
                NuvioCFeaturedDots(count = items.size, active = index)
            }
        }

        if (peekRow != null) {
            NuvioCFeaturedPeek(
                row = peekRow,
                landscape = landscapePeek || peekRow.key == "continue_watching",
                posterWidthDp = posterWidthDp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = margin)
            )
        }
    }
}

@Composable
private fun NuvioCFeaturedText(item: MetaPreview) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.Bottom) {
        var aspect by remember(item.logo) { mutableStateOf(NuvioCLogoSize.known(item.logo)) }
        var logoFailed by remember(item.logo) { mutableStateOf(false) }
        if (!item.logo.isNullOrBlank() && !logoFailed) {
            val box = NuvioCLogoSize.HOME.sizeFor(aspect)
            val model = remember(item.logo) { logoRequest(context, item.logo) }
            AsyncImage(
                model = model,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onState = { state ->
                    when (state) {
                        is AsyncImagePainter.State.Success -> NuvioCLogoSize.aspectOf(state)?.let { aspect = it }
                        is AsyncImagePainter.State.Error -> logoFailed = true
                        else -> Unit
                    }
                },
                modifier = Modifier.width(box.width).height(box.height)
            )
        } else {
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineLarge,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val meta = remember(item.id, item.apiType, item.genres, item.releaseInfo, item.runtime, item.imdbRating) {
            listOfNotNull(
                localizedContentType(context, item.apiType).takeIf { it.isNotBlank() },
                item.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let { localizedGenreLabel(context, it) },
                NuvioCFeaturedRules.year(item.releaseInfo),
                formatHeroRuntime(item.runtime),
                item.imdbRating?.takeIf { it > 0f }?.let { String.format("%.1f", it) }
            ).joinToString("  ·  ")
        }
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = NuvioTheme.colors.TextPrimary.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val synopsis = item.description?.let { NuvioCHeroSpec.homeSynopsis(it) }
        if (!synopsis.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = synopsis,
                style = NuvioCHeroSpec.detailSynopsisStyle(MaterialTheme.typography.bodyMedium),
                color = NuvioTheme.colors.TextPrimary.copy(alpha = NuvioCHeroSpec.DETAIL_SYNOPSIS_ALPHA),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }
    }
}

@Composable
private fun NuvioCFeaturedDetailsButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(NuvioTheme.spacing.xxl)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.focusProperties { up = FocusRequester.Cancel },
        colors = ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.Secondary,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContentColor = NuvioTheme.colors.OnSecondary,
            disabledContainerColor = NuvioTheme.colors.BackgroundCard,
            disabledContentColor = NuvioTheme.colors.TextPrimary
        ),
        shape = ButtonDefaults.shape(shape = shape),
        border = ButtonDefaults.border(
            focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = shape)
        ),
        contentPadding = PaddingValues(horizontal = NuvioTheme.spacing.xl, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
            Icon(imageVector = Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.nuvio_c_featured_details), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NuvioCFeaturedDots(count: Int, active: Int) {
    val ring = NuvioTheme.focusRing.brush()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count.coerceAtMost(20)) { i ->
            val on = i == active
            Box(
                Modifier
                    .size(width = if (on) 22.dp else 8.dp, height = if (on) 5.dp else 4.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .then(if (on) Modifier.background(ring) else Modifier.background(Color.White.copy(alpha = 0.3f)))
            )
        }
    }
}

@Composable
private fun NuvioCFeaturedPeek(row: HeroCarouselRow, landscape: Boolean, posterWidthDp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cardWidth = if (landscape) 176.dp else posterWidthDp.dp
    val cardHeightFull = if (landscape) cardWidth * 0.5625f else cardWidth * 1.5f
    Column(modifier) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            color = NuvioTheme.colors.TextPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(PEEK_TITLE)
        )
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(PEEK_POSTERS)) {
            val count = ((maxWidth / (cardWidth + 12.dp)).toInt() + 1).coerceAtMost(row.items.size).coerceAtLeast(0)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.items.list.take(count).forEach { card ->
                    val w = with(density) { cardWidth.roundToPx() }
                    val h = with(density) { cardHeightFull.roundToPx() }
                    val model = remember(card.imageUrl, w, h) {
                        ImageRequest.Builder(context).data(card.imageUrl).size(w, h).crossfade(false).build()
                    }
                    Box(
                        Modifier
                            .width(cardWidth)
                            .height(PEEK_POSTERS)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(NuvioTheme.colors.BackgroundCard)
                    ) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.width(cardWidth).height(cardHeightFull)
                        )
                    }
                }
            }
        }
    }
}

/** Settings > Layout > Home: the fourth card. */
@Composable
internal fun NuvioCFeaturedLayoutCard(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val radius = com.nuvio.tv.ui.screens.settings.SettingsSecondaryCardRadius
    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.Background,
            focusedContainerColor = NuvioTheme.colors.Background
        ),
        border = CardDefaults.border(
            border = if (isSelected) Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.hairline),
                shape = RoundedCornerShape(radius)
            ) else Border.None,
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(radius)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(radius)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val accent = NuvioTheme.colors.Primary
            val page = NuvioTheme.colors.Background
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(NuvioTheme.radii.sm))
                    .background(page)
            ) {
                val w = size.width
                val h = size.height
                val pad = w * 0.05f
                drawRoundRect(accent.copy(alpha = 0.38f), Offset(0f, 0f), Size(w, h * 0.84f))
                drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(pad, h * 0.30f), Size(w * 0.30f, h * 0.09f), CornerRadius(h * 0.02f))
                drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(pad, h * 0.44f), Size(w * 0.40f, h * 0.04f), CornerRadius(h * 0.02f))
                drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(pad, h * 0.51f), Size(w * 0.34f, h * 0.04f), CornerRadius(h * 0.02f))
                drawRoundRect(Color.White, Offset(pad, h * 0.61f), Size(w * 0.13f, h * 0.08f), CornerRadius(h * 0.04f))
                drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(pad + w * 0.15f, h * 0.61f), Size(w * 0.13f, h * 0.08f), CornerRadius(h * 0.04f))
                val cardW = w * 0.17f
                for (i in 0 until 6) {
                    drawRoundRect(
                        accent.copy(alpha = if (i == 0) 0.6f else 0.3f),
                        Offset(pad + i * (cardW + w * 0.03f), h * 0.90f),
                        Size(cardW, h * 0.10f),
                        CornerRadius(h * 0.03f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.cd_selected),
                        tint = NuvioTheme.colors.FocusRing,
                        modifier = Modifier.size(NuvioTheme.spacing.lg).padding(end = 6.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.nuvio_c_layout_featured),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected || focused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary
                )
            }
        }
    }
}

/** Settings: whether Featured is on, and a setter. Off-switch builds always answer false. */
@Composable
internal fun rememberNuvioCFeaturedSetting(): Pair<Boolean, (Boolean) -> Unit> {
    if (!NuvioCFeatures.HOME_FEATURED) return false to { _ -> }
    val viewModel: NuvioCHomeFeaturedViewModel = hiltViewModel()
    val featured by viewModel.featured.collectAsStateWithLifecycle()
    return (featured == true) to viewModel::setFeatured
}

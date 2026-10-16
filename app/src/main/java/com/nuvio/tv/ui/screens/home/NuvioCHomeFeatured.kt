package com.nuvio.tv.ui.screens.home

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
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
import com.nuvio.tv.core.di.NuvioCFeaturedTagline
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.repository.MDBListRepository
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.MDBListRatingsRow
import com.nuvio.tv.ui.components.NuvioCHeroAutoAdvanceEffect
import com.nuvio.tv.ui.components.NuvioCHeroSpec
import com.nuvio.tv.ui.components.NuvioCLogoSize
import com.nuvio.tv.ui.components.PlayManualOverrideDialog
import com.nuvio.tv.ui.components.nuvioCHomeMargin
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.formatHeroRuntime
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import com.nuvio.tv.ui.util.nuvioCLogoContrast
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// [fork] Nuvio C home_featured (2026-10-15): fourth home layout "Featured".
// A full-screen carousel of your hero catalogs (Settings > Layout > Hero catalogs) sits on top of
// Modern home. Play / Details / next-title arrow; Left / Right also page past the ends (Right from the
// arrow = next title, Left from Play = previous; both wrap round the ends with featured_wrap, so the
// sidebar is reached with Back; without it, Left on the first title reaches the sidebar).
// The real Modern rows show in the strip under the carousel (pushed down, drawn only below the
// carousel), so Down is one draw-only move of things already on screen: the carousel slides up, the
// rows slide into their usual place, then focus lands on them. Up on the first row, Back when Modern
// has nothing of its own to undo, or sidebar Home bring the carousel back on the same title.
// Stored as Modern + the `nuvio_c_home_featured` flag, so every Modern behaviour stays as it is.

/** True inside the Featured layout: Modern hero fades through between titles (synopsis style same as Collections). */
val LocalNuvioCHomeDetailText = staticCompositionLocalOf { false }

/**
 * [fork] home_featured: alpha for Modern's hero picture while it sits under the carousel. 0 with the
 * carousel up (the peek strip then shows the page colour the carousel fades into, not the top edge
 * of Modern's own backdrop), rising to 1 as the rows slide into place. Read at draw time.
 */
val LocalNuvioCFeaturedHeroAlpha = staticCompositionLocalOf<(() -> Float)?> { null }

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

    /** Play button text: resume or play; season / episode when known. */
    data class Label(val resume: Boolean, val season: Int?, val episode: Int?)

    fun labelFor(cw: ContinueWatchingItem?): Label = when (cw) {
        null -> Label(false, null, null)
        is ContinueWatchingItem.InProgress -> Label(true, cw.progress.season, cw.progress.episode)
        is ContinueWatchingItem.NextUp -> Label(false, cw.info.season, cw.info.episode)
    }

    fun year(releaseInfo: String?): String? = releaseInfo?.let { YEAR.find(it)?.value }

    fun nextIndex(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1) % count
    fun previousIndex(index: Int, count: Int): Int = if (count <= 0) 0 else (index - 1 + count) % count

    /** Modern's rows viewport share of the screen (same numbers as ModernHomeContent). */
    fun rowsViewportFraction(landscapePosters: Boolean): Float = if (landscapePosters) 0.49f else 0.52f
}

@HiltViewModel
class NuvioCHomeFeaturedViewModel @Inject constructor(
    private val layoutStore: LayoutPreferenceDataStore,
    private val mdbListRepository: MDBListRepository,
    private val taglineSources: Set<@JvmSuppressWildcards NuvioCFeaturedTagline>
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

    internal val taglineSource: NuvioCFeaturedTagline? = taglineSources.firstOrNull()

    private val _ratings = MutableStateFlow<Map<String, MDBListRatings>>(emptyMap())
    val ratings: StateFlow<Map<String, MDBListRatings>> = _ratings.asStateFlow()
    private val _taglines = MutableStateFlow<Map<String, String>>(emptyMap())
    val taglines: StateFlow<Map<String, String>> = _taglines.asStateFlow()
    private val asked = HashSet<String>()

    /** Ratings (MDBList, when shown on the hero) and tagline for a title, once per title per visit. */
    fun prefetch(item: MetaPreview, wantRatings: Boolean) {
        if (!asked.add(item.id)) return
        if (wantRatings) {
            viewModelScope.launch {
                val result = runCatching {
                    mdbListRepository.getRatingsForMeta(item.toMdbMeta(), fallbackItemId = item.id, fallbackItemType = item.apiType)
                }.getOrNull()
                val ratings = result?.ratings?.takeIf { !it.isEmpty() } ?: return@launch
                _ratings.update { it + (item.id to ratings) }
            }
        }
        val source = taglineSource ?: return
        viewModelScope.launch {
            val tagline = runCatching { source.taglineFor(item) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@launch
            _taglines.update { it + (item.id to tagline) }
        }
    }

    private fun MetaPreview.toMdbMeta(): Meta = Meta(
        id = id,
        type = type,
        name = name,
        poster = poster,
        posterShape = posterShape,
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating,
        genres = genres,
        runtime = runtime,
        director = director,
        cast = emptyList(),
        videos = emptyList(),
        country = country,
        awards = null,
        language = language,
        links = links
    )
}

/** Callbacks Home already has, passed through to the carousel. */
internal class NuvioCFeaturedActions(
    val onNavigateToDetail: (String, String, String) -> Unit,
    val onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    val onContinueWatchingStartFromBeginning: (ContinueWatchingItem) -> Unit,
    val onContinueWatchingPlayManually: (ContinueWatchingItem) -> Unit,
    /** itemId, type, backdrop, manual source selection: details page with official "play on load". */
    val onPlayFromDetails: ((String, String, String?, Boolean) -> Unit)?,
    val autoplayEnabled: Boolean
)

/**
 * Home's Modern branch goes through here. Off (switch, or Modern chosen in settings) = [content]
 * exactly as before.
 */
@Composable
internal fun NuvioCFeaturedGate(
    uiState: HomeUiState,
    scrollToTopTrigger: Int,
    actions: NuvioCFeaturedActions,
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
        true -> NuvioCFeaturedHome(uiState, scrollToTopTrigger, viewModel, actions, content)
    }
}

private val SLIDE_MS = if (com.nuvio.tv.NuvioCFeatures.FEATURED_FAST_FOCUS) 260 else 380
private val PEEK = 76.dp

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun NuvioCFeaturedHome(
    uiState: HomeUiState,
    scrollToTopTrigger: Int,
    viewModel: NuvioCHomeFeaturedViewModel,
    actions: NuvioCFeaturedActions,
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
    val nextRequester = remember { FocusRequester() }

    var shown by rememberSaveable { mutableStateOf(true) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var activeId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastButton by rememberSaveable { mutableIntStateOf(0) } // 0 Play, 1 Details, 2 next arrow
    var rowsFocused by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    val carouselOn = shown && available
    val progress = remember { Animatable(if (carouselOn) 0f else 1f) } // 0 = carousel, 1 = rows

    fun buttonRequester(i: Int) = when (i) { 1 -> detailsRequester; 2 -> nextRequester; else -> playRequester }

    // Same title kept when the list is rebuilt (enrichment, catalogs arriving, refresh).
    LaunchedEffect(items) {
        index = com.nuvio.tv.ui.components.NuvioCHeroAutoAdvance.indexAfterListChange(items, activeId, index)
    }
    LaunchedEffect(index, items) { activeId = items.getOrNull(index)?.id }

    fun goToRows() {
        if (!shown || sliding) return
        scope.launch {
            sliding = true
            shown = false
            if (com.nuvio.tv.NuvioCFeatures.FEATURED_FAST_FOCUS) {
                // Focus goes to the rows at once (the slide only moves pictures), so the first card is
                // focused on the first frame and Right / OK work while the rows are still rising.
                withFrameNanos { }
                runCatching { rowsRequester.requestFocus() }
                progress.animateTo(1f, tween(SLIDE_MS, easing = FastOutSlowInEasing))
                sliding = false
            } else {
                progress.animateTo(1f, tween(SLIDE_MS, easing = FastOutSlowInEasing))
                sliding = false
                withFrameNanos { }
                runCatching { rowsRequester.requestFocus() }
            }
        }
    }

    fun goToCarousel() {
        if (shown || !available || sliding) return
        scope.launch {
            sliding = true
            shown = true
            withFrameNanos { }
            runCatching { buttonRequester(lastButton).requestFocus() }
            progress.animateTo(0f, tween(SLIDE_MS, easing = FastOutSlowInEasing))
            sliding = false
        }
    }

    // Hero items arriving after you already moved into the rows never pop the carousel over you.
    LaunchedEffect(available) {
        if (available && rowsFocused) {
            shown = false
            progress.snapTo(1f)
        }
    }
    // Opening / coming back to Home with the carousel up: focus its last button (Modern can't take it).
    LaunchedEffect(carouselOn) {
        if (carouselOn && !sliding) {
            if (progress.value != 0f) progress.snapTo(0f)
            repeat(2) { withFrameNanos { } }
            runCatching { buttonRequester(lastButton).requestFocus() }
        } else if (!carouselOn && !sliding && progress.value != 1f) {
            progress.snapTo(1f)
        }
    }
    // Sidebar "Home" again (scroll to top) = back to the carousel.
    var lastScrollTrigger by rememberSaveable { mutableIntStateOf(scrollToTopTrigger) }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger != lastScrollTrigger) {
            lastScrollTrigger = scrollToTopTrigger
            goToCarousel()
        }
    }
    // Back from the rows keeps official behaviour (sidebar); the carousel comes back through Up on the
    // first row or the sidebar's Home.

    val activeItem = items.getOrNull(index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    val cwItems = uiState.continueWatchingItems
    val activeCw = remember(activeItem?.id, activeItem?.imdbId, cwItems) {
        activeItem?.let { NuvioCFeaturedRules.continueItemFor(listOf(it.id, it.imdbId), cwItems) }
    }
    // Play: a Continue Watching title resumes exactly as its CW card would; anything else opens the
    // details page with official "play on load" (as post-play "Play" does), so its own logic picks
    // the resume point / next unwatched episode / first episode and the sources.
    fun play(manual: Boolean) {
        val item = activeItem ?: return
        val cw = activeCw
        when {
            cw != null && manual -> actions.onContinueWatchingPlayManually(cw)
            cw != null -> actions.onContinueWatchingClick(cw)
            actions.onPlayFromDetails != null -> actions.onPlayFromDetails.invoke(item.id, item.apiType, item.backdropUrl, manual)
            else -> actions.onNavigateToDetail(item.id, item.apiType, "")
        }
    }
    var showPlayOptions by remember { mutableStateOf(false) }
    val canResumeFromStart = activeCw is ContinueWatchingItem.InProgress
    val playLongPress: (() -> Unit)? = if (actions.autoplayEnabled || canResumeFromStart) {
        { showPlayOptions = true }
    } else null

    // Ratings and tagline for this title and the next, so a slide change finds them ready.
    val wantRatings = uiState.mdbListShowOnHero
    LaunchedEffect(index, items, wantRatings) {
        if (items.isEmpty()) return@LaunchedEffect
        items.getOrNull(index)?.let { viewModel.prefetch(it, wantRatings) }
        if (items.size > 1) viewModel.prefetch(items[NuvioCFeaturedRules.nextIndex(index, items.size)], wantRatings)
    }
    val ratings by viewModel.ratings.collectAsStateWithLifecycle()
    val taglines by viewModel.taglines.collectAsStateWithLifecycle()

    val atRest by remember { derivedStateOf { progress.value == 0f } }
    NuvioCHeroAutoAdvanceEffect(
        count = if (carouselOn && atRest && !showPlayOptions) items.size else 0,
        activeIndex = index,
        interaction = interaction
    ) { index = NuvioCFeaturedRules.nextIndex(index, items.size) }

    val heroAlpha = remember(progress) { { progress.value } }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenH = configuration.screenHeightDp.dp
    val screenHeightPx = with(density) { screenH.toPx() }
    val peekPx = with(density) { PEEK.toPx() }
    val carouselHeight = screenH - PEEK
    val carouselHeightPx = screenHeightPx - peekPx
    // How far Modern sits lower while the carousel is up: its first row then starts PEEK above the bottom.
    val rowsDropPx = (screenHeightPx * NuvioCFeaturedRules.rowsViewportFraction(uiState.modernLandscapePostersEnabled) - peekPx)
        .coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(outerRequester)
            .focusProperties {
                enter = { if (carouselOn) buttonRequester(lastButton) else FocusRequester.Default }
            }
            .focusGroup()
    ) {
        // Modern home, as always. While the carousel is up it sits lower and is drawn only below the
        // carousel (the peek strip), and it can't take focus.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (1f - progress.value) * rowsDropPx }
                .drawWithContent {
                    val p = progress.value
                    if (p >= 1f) {
                        drawContent()
                    } else {
                        // Carousel bottom edge, in Modern's own (dropped) coordinates.
                        val top = (1f - p) * (carouselHeightPx - rowsDropPx)
                        clipRect(top = top) { this@drawWithContent.drawContent() }
                    }
                }
                .onFocusChanged { rowsFocused = it.hasFocus }
                .focusProperties {
                    onEnter = {
                        val blocked = if (com.nuvio.tv.NuvioCFeatures.FEATURED_FAST_FOCUS) shown else (shown || sliding)
                        if (blocked) cancelFocusChange()
                    }
                }
                .focusGroup()
                .onKeyEvent { event ->
                    // Up with nothing above (first row) = back to the carousel.
                    if (available && !shown && !sliding && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                        if (!focusManager.moveFocus(FocusDirection.Up)) goToCarousel()
                        true
                    } else {
                        false
                    }
                }
        ) {
            CompositionLocalProvider(
                LocalContentFocusRequester provides rowsRequester,
                LocalNuvioCHomeDetailText provides true,
                LocalNuvioCFeaturedHeroAlpha provides (if (available) heroAlpha else null)
            ) {
                content()
            }
        }

        if (available) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(carouselHeight)
                    .graphicsLayer { translationY = -progress.value * carouselHeightPx }
                    .drawWithContent { if (progress.value < 1f) drawContent() }
                    .background(NuvioTheme.colors.Background)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (sliding) return@onPreviewKeyEvent true // keys during the slide are dropped
                        interaction++
                        when (event.key) {
                            Key.DirectionDown -> { goToRows(); true }
                            Key.DirectionUp -> true
                            else -> false
                        }
                    }
            ) {
                NuvioCFeaturedSlide(
                    items = items,
                    index = index.coerceIn(0, items.size - 1),
                    cw = activeCw,
                    ratings = if (wantRatings) ratings else emptyMap(),
                    ratingOrder = uiState.mdbListRatingOrder,
                    taglines = taglines,
                    tagline = viewModel.taglineSource,
                    focusable = shown || sliding,
                    playRequester = playRequester,
                    detailsRequester = detailsRequester,
                    nextRequester = nextRequester,
                    onPlay = { play(manual = false) },
                    onPlayLongPress = playLongPress,
                    onDetails = { activeItem?.let { actions.onNavigateToDetail(it.id, it.apiType, "") } },
                    onButtonFocused = { lastButton = it },
                    onNext = { focusFirst ->
                        index = NuvioCFeaturedRules.nextIndex(index, items.size)
                        if (focusFirst) {
                            lastButton = 0
                            runCatching { playRequester.requestFocus() }
                        }
                    },
                    onPrevious = {
                        index = NuvioCFeaturedRules.previousIndex(index, items.size)
                        lastButton = 2
                        runCatching { nextRequester.requestFocus() }
                    }
                )
            }
        }
    }

    if (showPlayOptions && activeItem != null) {
        PlayManualOverrideDialog(
            title = activeItem.name,
            subtitle = null,
            onDismiss = { showPlayOptions = false },
            showPlayManually = actions.autoplayEnabled,
            onPlayManually = {
                showPlayOptions = false
                play(manual = true)
            },
            showStartFromBeginning = canResumeFromStart,
            onStartFromBeginning = {
                showPlayOptions = false
                activeCw?.let(actions.onContinueWatchingStartFromBeginning)
            }
        )
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
    cw: ContinueWatchingItem?,
    ratings: Map<String, MDBListRatings>,
    ratingOrder: List<String>,
    taglines: Map<String, String>,
    tagline: NuvioCFeaturedTagline?,
    focusable: Boolean,
    playRequester: FocusRequester,
    detailsRequester: FocusRequester,
    nextRequester: FocusRequester,
    onPlay: () -> Unit,
    onPlayLongPress: (() -> Unit)?,
    onDetails: () -> Unit,
    onButtonFocused: (Int) -> Unit,
    onNext: (focusFirst: Boolean) -> Unit,
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
        // Backdrop: the full-screen picture, cut where the peek strip starts (the box ends there).
        Crossfade(targetState = index, animationSpec = tween(420), label = "nuvioCFeaturedBackdrop") { i ->
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
        // Scrims: left for the text, bottom into the page colour where the rows start. Plain draws, no layer.
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
                        0.55f to Color.Transparent,
                        0.86f to bg.copy(alpha = 0.75f),
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
                .padding(start = margin, bottom = 18.dp)
                .fillMaxWidth(0.46f),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Text block bottom-anchored: the buttons never move. Titles swap with a short fade-through,
            // so a taller or shorter block never visibly jumps.
            NuvioCFadeThrough(target = item, key = { it.id }, contentAlignment = Alignment.BottomStart) { slide ->
                NuvioCFeaturedText(slide, ratings[slide.id], ratingOrder, taglines[slide.id], tagline)
            }
            Spacer(Modifier.height(18.dp))
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
            var focusedButton by remember { mutableIntStateOf(-1) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .focusProperties { canFocus = focusable }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || items.size < 2) return@onPreviewKeyEvent false
                        when {
                            event.key == Key.DirectionRight && focusedButton == 2 -> { latestNext(true); true }
                            event.key == Key.DirectionLeft && focusedButton == 0 && (com.nuvio.tv.NuvioCFeatures.FEATURED_WRAP || index > 0) -> { latestPrevious(); true } // [fork] featured_wrap: first title wraps to the last
                            else -> false
                        }
                    }
            ) {
                com.nuvio.tv.ui.screens.detail.PlayButton(
                    text = playText,
                    enabled = focusable,
                    onClick = onPlay,
                    onLongPress = onPlayLongPress,
                    focusRequester = playRequester,
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) { focusedButton = 0; onButtonFocused(0) } else if (focusedButton == 0) focusedButton = -1
                    }
                )
                NuvioCFeaturedDetailsButton(
                    enabled = focusable,
                    onClick = onDetails,
                    modifier = Modifier
                        .focusRequester(detailsRequester)
                        .onFocusChanged {
                            if (it.isFocused) { focusedButton = 1; onButtonFocused(1) } else if (focusedButton == 1) focusedButton = -1
                        }
                )
                if (items.size > 1) {
                    NuvioCFeaturedNextButton(
                        enabled = focusable,
                        onClick = { latestNext(false) },
                        modifier = Modifier
                            .focusRequester(nextRequester)
                            .onFocusChanged {
                                if (it.isFocused) { focusedButton = 2; onButtonFocused(2) } else if (focusedButton == 2) focusedButton = -1
                            }
                    )
                }
            }
            if (items.size > 1) {
                Spacer(Modifier.height(16.dp))
                NuvioCFeaturedDots(count = items.size, active = index)
            }
        }
    }
}

/**
 * Lightweight title change: the old content fades out ([outMs]), the new one is swapped in while
 * invisible and fades in ([inMs]). Only one copy is ever composed and the fade is ModulateAlpha
 * (no offscreen buffer), so a block of a different height never slides or jumps on screen. A new
 * version of the same title (enrichment, ratings landing) is shown straight away.
 */
@Composable
internal fun <T> NuvioCFadeThrough(
    target: T,
    key: (T) -> Any?,
    modifier: Modifier = Modifier,
    outMs: Int = 110,
    inMs: Int = 200,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit
) {
    var shown by remember { mutableStateOf(target) }
    val alpha = remember { Animatable(1f) }
    val targetKey = key(target)
    SideEffect { if (key(shown) == targetKey && shown != target) shown = target }
    LaunchedEffect(targetKey) {
        if (key(shown) == targetKey) return@LaunchedEffect
        if (outMs > 0) alpha.animateTo(0f, tween(outMs)) else alpha.snapTo(0f)
        shown = target
        withFrameNanos { }
        alpha.animateTo(1f, tween(inMs))
    }
    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
        contentAlignment = contentAlignment
    ) {
        content(shown)
    }
}

@Composable
private fun NuvioCFeaturedText(
    item: MetaPreview,
    ratings: MDBListRatings?,
    ratingOrder: List<String>,
    taglineText: String?,
    tagline: NuvioCFeaturedTagline?
) {
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
        if (tagline != null && !taglineText.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            tagline.Draw(taglineText, 0.92f, Modifier)
        }
        val meta = remember(item.id, item.apiType, item.genres, item.releaseInfo, item.runtime) {
            listOfNotNull(
                localizedContentType(context, item.apiType).takeIf { it.isNotBlank() },
                item.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let { localizedGenreLabel(context, it) },
                NuvioCFeaturedRules.year(item.releaseInfo),
                formatHeroRuntime(item.runtime)
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
        if (ratings != null) {
            Spacer(Modifier.height(8.dp))
            MDBListRatingsRow(ratings = ratings, order = ratingOrder)
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

/** Round "next title" button, in the details page's round-button style (Apple TV's arrow). */
@Composable
private fun NuvioCFeaturedNextButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(NuvioTheme.spacing.xxxl)
            .focusProperties { up = FocusRequester.Cancel },
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = NuvioTheme.colors.Secondary,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContentColor = NuvioTheme.colors.OnSecondary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = NuvioTheme.colors.TextPrimary
        ),
        // Apple TV arrow: a bare chevron until focused, then the filled circle with the focus ring.
        border = IconButtonDefaults.border(
            border = Border.None,
            disabledBorder = Border.None,
            focusedDisabledBorder = Border.None,
            focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = CircleShape)
        ),
        shape = IconButtonDefaults.shape(shape = CircleShape)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.nuvio_c_featured_next),
            modifier = Modifier.size(24.dp)
        )
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

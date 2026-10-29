package com.nuvio.tv.ui.screens.shuffle

// [fork] Nuvio C round 28 `shuffle_page`: the Shuffle page in the sidebar.
// Mood screen (three tiles + mood chips) -> poster reel -> result (backdrop, logo, synopsis, Open / Shuffle again).
// Only graphicsLayer values animate (alpha, scale, translation), so nothing is laid out again while it moves.

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
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
import com.nuvio.tv.R
import com.nuvio.tv.data.nuvioc.NuvioCShuffleKind
import com.nuvio.tv.data.nuvioc.NuvioCShuffleMood
import com.nuvio.tv.ui.components.NuvioCPosterReel
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme

private val EDGE = 48.dp

@Composable
fun NuvioCShuffleScreen(
    onOpenTitle: (id: String, type: String) -> Unit,
    viewModel: NuvioCShuffleViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()
    LaunchedEffect(Unit) { viewModel.onScreenShown() }
    BackHandler(enabled = ui.phase != NuvioCShufflePhase.MOOD) { viewModel.backToMood() }
    var sourcesOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        if (ui.phase == NuvioCShufflePhase.MOOD) {
            ShuffleMood(
                ui = ui,
                onKind = viewModel::chooseKind,
                onMood = { viewModel.setMood(it) },
                onUnwatched = { viewModel.toggleUnwatched() },
                onSources = { sourcesOpen = true }
            )
        } else {
            ShuffleStage(
                ui = ui,
                onLanded = viewModel::onLanded,
                onCycleKind = viewModel::cycleKind,
                onCycleMood = viewModel::cycleMood,
                onUnwatched = { viewModel.toggleUnwatched(reroll = true) },
                onAgain = viewModel::roll,
                onOpen = {
                    val item = ui.pick?.item ?: return@ShuffleStage
                    viewModel.onOpenTitle()
                    onOpenTitle(item.id, item.type)
                }
            )
        }
    }
    if (sourcesOpen) {
        com.nuvio.tv.ui.screens.settings.NuvioCShuffleSourcesDialog(onDismiss = {
            sourcesOpen = false
            viewModel.refreshSourcesCount()
        })
    }
}

// ---- mood screen ---------------------------------------------------------------------------------------

@Composable
private fun ShuffleMood(
    ui: NuvioCShuffleUi,
    onKind: (NuvioCShuffleKind) -> Unit,
    onMood: (NuvioCShuffleMood) -> Unit,
    onUnwatched: () -> Unit,
    onSources: () -> Unit
) {
    val tileRequesters = remember { NuvioCShuffleKind.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { tileRequesters.getValue(NuvioCShuffleKind.MOVIES).requestFocusAfterFrames() } // [fork] roulette_page: always lands on Movies
    Column(Modifier.fillMaxSize().padding(start = EDGE, end = EDGE, top = 28.dp, bottom = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.nuvio_c_ic_dice), contentDescription = null, tint = NuvioTheme.colors.TextPrimary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.nuvio_c_shuffle_title),
                style = MaterialTheme.typography.headlineMedium,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1
            )
            Spacer(Modifier.weight(1f))
            // [fork] roulette_page: Unwatched and Blend sit top right
            ShuffleChip(label = stringResource(R.string.nuvio_c_shuffle_unwatched), selected = ui.unwatched, dot = ui.unwatched, onClick = onUnwatched)
            Spacer(Modifier.width(8.dp))
            ShuffleChip(label = stringResource(R.string.nuvio_c_shuffle_blend), selected = false, onClick = onSources)
        }
        Text(
            text = if (ui.nothingLeft) stringResource(R.string.nuvio_c_shuffle_nothing_left) else stringResource(R.string.nuvio_c_shuffle_title_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioTheme.colors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            com.nuvio.tv.data.nuvioc.nuvioCShuffleKinds.forEach { kind -> // [fork] roulette_anime
                ShuffleTile(
                    kind = kind,
                    posters = ui.fans[kind].orEmpty(),
                    requester = tileRequesters.getValue(kind),
                    onClick = { onKind(kind) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.nuvio_c_shuffle_mood),
                style = MaterialTheme.typography.labelLarge,
                color = NuvioTheme.colors.TextSecondary,
                modifier = Modifier.padding(end = 12.dp)
            )
            val nuvioCMoods = com.nuvio.tv.data.nuvioc.nuvioCShuffleMoods // [fork] roulette_v2
            var nuvioCFocusedMood by remember { mutableStateOf(-1) } // [fork] roulette_hold_right
            LazyRow(
                modifier = Modifier.weight(1f).nuvioCHoldRightStop { nuvioCFocusedMood == nuvioCMoods.lastIndex }, // [fork] roulette_hold_right
                horizontalArrangement = Arrangement.spacedBy(if (com.nuvio.tv.NuvioCFeatures.ROULETTE_V2) 6.dp else 8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                itemsIndexed(nuvioCMoods, key = { _, it -> it.key }) { index, mood ->
                    ShuffleChip(
                        label = moodLabel(mood), selected = ui.mood == mood, onClick = { onMood(mood) },
                        modifier = Modifier.onFocusChanged { if (it.isFocused) nuvioCFocusedMood = index }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShuffleTile(
    kind: NuvioCShuffleKind,
    posters: List<String>,
    requester: FocusRequester,
    onClick: () -> Unit,
    modifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val spread by animateFloatAsState(if (focused) 1f else 0f, tween(320), label = "fan")
    val shape = RoundedCornerShape(24.dp)
    Card(
        onClick = onClick,
        modifier = modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused },
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.04f),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.11f)
        ),
        border = CardDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), shape = shape),
            focusedBorder = Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), shape = shape)
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.05f))))
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                // Poster size from the space actually there, so all three posters always fit inside the tile.
                val h = minOf(maxHeight * 0.94f, maxWidth * 0.43f * 1.5f)
                val w = h * (2f / 3f)
                Fan(posters = posters, width = w, height = h, spread = { spread })
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = kindLabel(kind),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 1
            )
            Text(
                text = kindHint(kind),
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Fan(posters: List<String>, width: Dp, height: Dp, spread: () -> Float) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val wPx = with(density) { width.roundToPx() }
    val hPx = with(density) { height.roundToPx() }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(width * 2.2f, height), contentAlignment = Alignment.BottomCenter) {
        // Side posters first (behind), centre last.
        listOf(0 to -1f, 2 to 1f, 1 to 0f).forEach { (index, side) ->
            val url = posters.getOrNull(index)
            val request = remember(url, wPx, hPx) {
                url?.let { ImageRequest.Builder(context).data(it).size(wPx, hPx).crossfade(180).build() }
            }
            Box(
                Modifier
                    .size(width, height)
                    .graphicsLayer {
                        val s = spread()
                        val shift = width.toPx() * (0.46f + 0.10f * s)
                        translationX = side * shift
                        translationY = if (side == 0f) -(4.dp.toPx() + 4.dp.toPx() * s) else 0f
                        rotationZ = side * (7f + 2f * s)
                        val scale = if (side == 0f) 1f + 0.03f * s else 0.88f + 0.02f * s
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        shadowElevation = 12.dp.toPx()
                        this.shape = shape
                        clip = true
                    }
                    .background(NuvioTheme.colors.BackgroundCard)
            ) {
                if (request != null) {
                    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShuffleChip(label: String, selected: Boolean, onClick: () -> Unit, dot: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(if (com.nuvio.tv.NuvioCFeatures.ROULETTE_V2) 32.dp else 36.dp), // [fork] roulette_v2: smaller chips
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (selected) 0.18f else 0.07f),
            contentColor = if (selected) Color.White else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = if (com.nuvio.tv.NuvioCFeatures.ROULETTE_V2) 12.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF5FD39A)))
                Spacer(Modifier.width(7.dp))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ---- reel + result -------------------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShuffleStage(
    ui: NuvioCShuffleUi,
    onLanded: (Int) -> Unit,
    onCycleKind: () -> Unit,
    onCycleMood: () -> Unit,
    onUnwatched: () -> Unit,
    onAgain: () -> Unit,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val reelFocus = remember { FocusRequester() }
    val openFocus = remember { FocusRequester() }
    val landed = ui.landed && ui.pick != null
    val reveal by animateFloatAsState(if (landed) 1f else 0f, tween(if (landed) 520 else 200), label = "reveal")
    val bodyIn by animateFloatAsState(if (landed) 1f else 0f, tween(if (landed) 420 else 150, delayMillis = if (landed) 140 else 0), label = "body")

    // Something always holds focus: the reel while it spins (so keys never wander to the sidebar), then Open.
    LaunchedEffect(landed) {
        if (landed) openFocus.requestFocusAfterFrames() else reelFocus.requestFocusAfterFrames()
    }

    Box(Modifier.fillMaxSize()) {
        val background = ui.pick?.item?.background
        if (background != null) {
            val request = remember(background) { shuffleBackdropRequest(context, background) }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = reveal }
            )
        }
        val bg = NuvioTheme.colors.Background
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.background(
                Brush.horizontalGradient(0f to bg.copy(alpha = 0.94f), 0.34f to bg.copy(alpha = 0.72f), 0.66f to Color.Transparent)
            )
        )
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to bg.copy(alpha = 0.85f))
            )
        )

        // The reel, centred; fades away once the pick has landed.
        val spin = ui.spin
        if (spin != null && (!landed || reveal < 1f)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - reveal }
                    .focusRequester(reelFocus)
                    .focusable()
            ) {
                NuvioCPosterReel(
                    spin = spin,
                    cardWidth = 150.dp,
                    cardHeight = 225.dp,
                    edgeColor = NuvioTheme.colors.Background,
                    modifier = Modifier.fillMaxWidth(),
                    edgeFade = 160.dp,
                    onLanded = onLanded
                )
            }
        }

        // While the pick is being found (first use, before the pool exists, this can take a few seconds) a quiet line
        // under the still frame says so; it only appears if the wait passes 0.7 s.
        val waiting = !landed && (ui.spin?.token ?: 0) < 0
        var showWaiting by remember { mutableStateOf(false) }
        LaunchedEffect(waiting) {
            showWaiting = false
            if (waiting) {
                kotlinx.coroutines.delay(700)
                showWaiting = true
            }
        }
        val waitingAlpha by animateFloatAsState(if (showWaiting && waiting) 1f else 0f, tween(250), label = "waiting")
        if (waitingAlpha > 0f) {
            Text(
                text = stringResource(R.string.nuvio_c_shuffle_finding),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary,
                modifier = Modifier.align(Alignment.Center).padding(top = 290.dp).graphicsLayer { alpha = waitingAlpha }
            )
        }

        // Choices along the top; changing one rolls again.
        Row(
            Modifier.align(Alignment.TopStart).padding(start = EDGE, top = 28.dp, end = EDGE),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(painterResource(R.drawable.nuvio_c_ic_dice), contentDescription = null, tint = NuvioTheme.colors.TextPrimary, modifier = Modifier.size(26.dp))
            Text(
                text = stringResource(R.string.nuvio_c_roulette), // [fork] roulette_page: the section is Roulette, the action stays Shuffle
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier.padding(end = 8.dp)
            )
            ShuffleChip(label = kindLabel(ui.kind), selected = true, onClick = onCycleKind)
            ShuffleChip(label = moodLabel(ui.mood), selected = true, onClick = onCycleMood)
            ShuffleChip(label = stringResource(R.string.nuvio_c_shuffle_unwatched), selected = ui.unwatched, dot = ui.unwatched, onClick = onUnwatched)
        }

        val pick = ui.pick
        if (pick != null && (landed || bodyIn > 0f)) {
            val item = pick.item
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = EDGE, top = 104.dp)
                    .width(460.dp)
                    .graphicsLayer {
                        alpha = bodyIn
                        translationY = (1f - bodyIn) * 14.dp.toPx()
                        // No offscreen buffer: one would clip the focused button's grow and border while it fades in.
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!item.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = item.logo,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.height(88.dp).widthIn(max = 380.dp)
                    )
                } else {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val meta = listOfNotNull(
                    item.year,
                    com.nuvio.tv.ui.util.nuvioCRuntime(pick.runtime)?.takeIf { it.isNotBlank() }, // [fork] runtime_format
                    item.genres.take(2).takeIf { it.isNotEmpty() }?.joinToString(" · ") { g -> g.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) } }
                ).joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                item.description?.let {
                    // [fork] same size as the details page description, up to six lines
                    val base = MaterialTheme.typography.bodyMedium
                    val style = if (com.nuvio.tv.ui.components.NuvioCHeroSpec.enabled) com.nuvio.tv.ui.components.NuvioCHeroSpec.detailSynopsisStyle(base) else base
                    Text(it, style = style, color = Color.White.copy(alpha = 0.66f), maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                nuvioCBecauseText(item.because)?.let { reason -> // [fork] roulette_v2: why this pick
                    Text(reason, style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = stringResource(R.string.nuvio_c_shuffle_from, item.from),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (landed) onOpen() },
                        modifier = Modifier.focusRequester(openFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nuvio_c_random_open))
                    }
                    Button(
                        onClick = { if (landed) onAgain() },
                        colors = ButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Icon(painterResource(R.drawable.nuvio_c_ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nuvio_c_shuffle_again))
                    }
                }
            }
        }
    }
}

@Composable
private fun kindLabel(kind: NuvioCShuffleKind): String = stringResource(
    when (kind) {
        NuvioCShuffleKind.MOVIES -> R.string.nuvio_c_shuffle_movies
        NuvioCShuffleKind.SHOWS -> R.string.nuvio_c_shuffle_shows
        NuvioCShuffleKind.EITHER -> R.string.nuvio_c_shuffle_either
        NuvioCShuffleKind.ANIME -> R.string.nuvio_c_shuffle_anime // [fork] roulette_anime
    }
)

@Composable
private fun kindHint(kind: NuvioCShuffleKind): String = stringResource(
    when (kind) {
        NuvioCShuffleKind.MOVIES -> movieHintForNow() // [fork] roulette_page: wording follows the time of day
        NuvioCShuffleKind.SHOWS -> R.string.nuvio_c_shuffle_shows_hint
        NuvioCShuffleKind.EITHER -> R.string.nuvio_c_shuffle_either_hint
        NuvioCShuffleKind.ANIME -> R.string.nuvio_c_shuffle_anime_hint // [fork] roulette_anime
    }
)

@Composable
private fun moodLabel(mood: NuvioCShuffleMood): String = stringResource(
    when (mood) {
        NuvioCShuffleMood.ANY -> R.string.nuvio_c_shuffle_mood_any
        NuvioCShuffleMood.FEEL_GOOD -> R.string.nuvio_c_shuffle_mood_feel_good
        NuvioCShuffleMood.GRIPPING -> R.string.nuvio_c_shuffle_mood_gripping
        NuvioCShuffleMood.FUNNY -> R.string.nuvio_c_shuffle_mood_funny
        NuvioCShuffleMood.DARK -> R.string.nuvio_c_shuffle_mood_dark
        NuvioCShuffleMood.MIND_BENDING -> R.string.nuvio_c_shuffle_mood_mind_bending
        NuvioCShuffleMood.ROMANTIC -> R.string.nuvio_c_shuffle_mood_romantic
        NuvioCShuffleMood.ANIMATED -> R.string.nuvio_c_shuffle_mood_animated
        NuvioCShuffleMood.TRUE_STORY -> R.string.nuvio_c_shuffle_mood_true_story
        NuvioCShuffleMood.WESTERN -> R.string.nuvio_c_shuffle_mood_western
        NuvioCShuffleMood.DOCUMENTARY -> R.string.nuvio_c_shuffle_mood_documentary
        NuvioCShuffleMood.ADVENTURE -> R.string.nuvio_c_shuffle_mood_adventure // [fork] roulette_v2
        NuvioCShuffleMood.FAMILY -> R.string.nuvio_c_shuffle_mood_family
        NuvioCShuffleMood.MUSIC -> R.string.nuvio_c_shuffle_mood_music
    }
)

/** Five time bands for the Movies tile: morning 5-11, afternoon 12-16, evening 17-20, night 21-23, late 0-4. */
@Composable
private fun movieHintForNow(): Int {
    val hour = remember { java.time.LocalTime.now().hour }
    return when (hour) {
        in 5..11 -> R.string.nuvio_c_shuffle_movies_hint_morning
        in 12..16 -> R.string.nuvio_c_shuffle_movies_hint_afternoon
        in 17..20 -> R.string.nuvio_c_shuffle_movies_hint_evening
        in 21..23 -> R.string.nuvio_c_shuffle_movies_hint
        else -> R.string.nuvio_c_shuffle_movies_hint_late
    }
}

/** [fork] round 31 roulette_v2: "Because you rated Arrival ★★★★½" / "Because you watched Arrival". */
@Composable
private fun nuvioCBecauseText(because: String?): String? {
    if (!com.nuvio.tv.NuvioCFeatures.ROULETTE_V2 || because.isNullOrBlank()) return null
    val parts = because.split('|')
    return when (parts.firstOrNull()) {
        "rated" -> {
            val title = parts.getOrNull(1) ?: return null
            val score = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 10) ?: return null
            val stars = "★".repeat(score / 2) + if (score % 2 == 1) "½" else ""
            stringResource(R.string.nuvio_c_shuffle_because_rated, title, stars)
        }
        "watched" -> parts.getOrNull(1)?.let { stringResource(R.string.nuvio_c_shuffle_because_watched, it) }
        else -> null
    }
}

/** [fork] round 31 roulette_hold_right: a held Right on the last mood chip stops there; a fresh press moves on. */
private fun Modifier.nuvioCHoldRightStop(atLast: () -> Boolean): Modifier =
    if (!com.nuvio.tv.NuvioCFeatures.ROULETTE_HOLD_RIGHT) this else this.onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && native.action == android.view.KeyEvent.ACTION_DOWN &&
            native.repeatCount > 0 && atLast()
    }

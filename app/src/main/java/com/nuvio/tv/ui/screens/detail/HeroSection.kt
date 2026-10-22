package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.widthIn // [fork] hero spec
import androidx.compose.animation.core.VectorConverter // [fork] logo weight
import kotlinx.coroutines.launch // [fork] logo weight

// Nuvio C imports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop

import com.nuvio.tv.ui.theme.NuvioMotion

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import android.util.Log
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.ui.components.ImdbRatingSourceLabel
import com.nuvio.tv.ui.components.MDBListRatingsRow
import com.nuvio.tv.ui.components.SynopsisDescription
import com.nuvio.tv.ui.theme.NuvioTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.Painter
import coil3.request.ImageRequest
import com.nuvio.tv.ui.util.nuvioCLogoContrast
import coil3.request.crossfade
import com.nuvio.tv.ui.util.localizedGenreLabel
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.rememberRawSvgPainter
import java.util.Locale

private const val MAX_VISIBLE_HERO_GENRES = 6

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HeroContentSection(
    meta: Meta,
    nextEpisode: Video?,
    nextToWatch: NextToWatch?,
    onPlayClick: () -> Unit,
    isPlayEnabled: Boolean = true,
    onPlayLongPress: (() -> Unit)? = null,
    isInLibrary: Boolean,
    onToggleLibrary: () -> Unit,
    onLibraryLongPress: () -> Unit,
    isMovieWatched: Boolean,
    isMovieWatchedPending: Boolean,
    onToggleMovieWatched: () -> Unit,
    trailerAvailable: Boolean = false,
    onTrailerClick: () -> Unit = {},
    showRandomEpisodeButton: Boolean = false,
    episodeShuffle: com.nuvio.tv.domain.model.EpisodeShuffleSettings = com.nuvio.tv.domain.model.EpisodeShuffleSettings(),
    shuffleActionPending: Boolean = false,
    onRandomEpisodeClick: () -> Unit = {},
    randomEpisodeFocusRequester: FocusRequester? = null,
    hideLogoDuringTrailer: Boolean = false,
    mdbListRatings: MDBListRatings? = null,
    mdbListRatingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    nuvioCRatingsPending: Boolean = false, // [fork] MDBList still loading: keep its line's space
    hideMetaInfoImdb: Boolean = false,
    tmdbRating: Float? = null,
    showFullReleaseDate: Boolean = true,
    isTrailerPlaying: Boolean = false,
    nuvioCBackgroundTrailer: Boolean = false, // [fork] muted background trailer behind the page: dim the text
    nuvioCFadeLogo: Boolean = false, // [fork] fade the logo with the trailer instead of removing it
    playButtonFocusRequester: FocusRequester? = null,
    restorePlayFocusToken: Int = 0,
    nuvioCPlayTokenSeen: androidx.compose.runtime.MutableIntState? = null, // [fork] focus stability
    onHeroActionFocused: () -> Unit = {},
    onPlayFocusRestored: () -> Unit = {},
    onShowFullDescription: () -> Unit = {},
    nuvioCTagline: String? = null, // [fork] TMDB tagline between logo and Play (switch `detail_tagline`)
    onTruncationChanged: (Boolean) -> Unit = {},
    nuvioCRateAvailable: Boolean = false, // [fork] Rate button (Trakt / MDBList)
    nuvioCRated: Boolean = false, // [fork]
    onNuvioCRate: () -> Unit = {}, // [fork]
    nuvioCRateFocusRequester: FocusRequester? = null // [fork]
) {
    val context = LocalContext.current
    // [fork] Nuvio C dimming over a background trailer (Apple TV look), full brightness when the synopsis is focused
    var nuvioCSynopsisFocused by remember(meta.id) { mutableStateOf(false) }
    val nuvioCTextAlpha = animateFloatAsState( // [fork] State, read at draw time
        targetValue = if (nuvioCBackgroundTrailer && !nuvioCSynopsisFocused) NUVIO_C_DETAIL_TEXT_ALPHA else 1f,
        animationSpec = tween(300),
        label = "nuvioCTextAlpha"
    )
    val nuvioCDescriptionAlpha = animateFloatAsState( // [fork] State, read at draw time
        targetValue = if (!nuvioCSynopsisFocused && com.nuvio.tv.ui.components.NuvioCHeroSpec.enabled) com.nuvio.tv.ui.components.NuvioCHeroSpec.DETAIL_SYNOPSIS_ALPHA // [fork] hero spec: Apple TV grey, always
            else if (nuvioCBackgroundTrailer && !nuvioCSynopsisFocused) NUVIO_C_DESCRIPTION_ALPHA else 1f,
        animationSpec = tween(300),
        label = "nuvioCDescriptionAlpha"
    )
    // [fork] logo stays composed and fades with the trailer, so it never pops, reloads or crops mid-move
    val nuvioCLogoAlpha = animateFloatAsState( // [fork] State, read at draw time
        targetValue = if (isTrailerPlaying && hideLogoDuringTrailer) 0f else 1f,
        animationSpec = tween(NuvioMotion.tokens.durations.overlay),
        label = "nuvioCLogoAlpha"
    )
    // [fork] fade for the actions block, drawn by nuvioCFade (official dropped its heroActionsAlpha in beta.5)
    val nuvioCActionsAlpha = animateFloatAsState( // [fork] State, read at draw time
        targetValue = if (isTrailerPlaying) 0f else 1f,
        animationSpec = tween(NuvioMotion.tokens.durations.overlay),
        label = "nuvioCActionsAlpha"
    )
    val isSeriesApi = remember(meta.apiType) {
        meta.apiType.equals("series", ignoreCase = true) || meta.apiType.equals("tv", ignoreCase = true)
    }
    val logoModel = remember(context, meta.logo) {
        meta.logo?.let { logo ->
            ImageRequest.Builder(context)
                .data(logo)
                .crossfade(true)
                .nuvioCLogoContrast() // [fork] dark logos
                .build()
        }
    }
    var logoLoadFailed by remember(meta.logo) { mutableStateOf(false) }
    val shouldShowLogo =
        !meta.logo.isNullOrBlank() &&
            !logoLoadFailed &&
            !(isTrailerPlaying && hideLogoDuringTrailer && !nuvioCFadeLogo) // [fork]
    val libraryAddPainter = rememberRawSvgPainter(rawIconRes = com.nuvio.tv.R.raw.library_add_plus)
    val trailerPainter = rememberRawSvgPainter(rawIconRes = com.nuvio.tv.R.raw.trailer_play_button)
    val strCreator = stringResource(R.string.hero_creator)
    val strDirector = stringResource(R.string.hero_director)
    val strWriter = stringResource(R.string.hero_writer)
    val creditLine = remember(meta.director, meta.writer, isSeriesApi) {
        val directorLine = meta.director.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val writerLine = meta.writer.takeIf { it.isNotEmpty() }?.joinToString(", ")
        when {
            !directorLine.isNullOrBlank() -> {
                if (isSeriesApi) strCreator.format(directorLine) else strDirector.format(directorLine)
            }
            !writerLine.isNullOrBlank() -> strWriter.format(writerLine)
            else -> null
        }
    }

    // Animate logo properties for trailer mode
    val logoHeight by animateDpAsState(
        targetValue = if (isTrailerPlaying) 60.dp else if (com.nuvio.tv.ui.components.NuvioCHeroSpec.enabled) com.nuvio.tv.ui.components.NuvioCHeroSpec.logoHeight + NuvioTheme.spacing.lg else 100.dp, // [fork] hero spec: 90 dp logo + its 16 dp gap
        animationSpec = tween(600),
        label = "logoHeight"
    )
    val logoBottomPadding by animateDpAsState(
        targetValue = if (isTrailerPlaying) NuvioTheme.spacing.xl else NuvioTheme.spacing.lg,
        animationSpec = tween(600),
        label = "logoPadding"
    )
    val logoMaxWidth by animateFloatAsState(
        targetValue = if (isTrailerPlaying) 0.25f else 0.4f,
        animationSpec = tween(600),
        label = "logoWidth"
    )
    // During a trailer the hero fills the screen so the logo sits at the bottom
    // on screens taller than the hero (lower-density TV boxes).
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight by animateDpAsState(
        targetValue = if (isTrailerPlaying) maxOf(540.dp, screenHeight) else 540.dp,
        animationSpec = tween(600),
        label = "heroHeight"
    )
    // [fork] logo weight: same visual weight for every logo (Apple TV style), official sizes over a trailer.
    // Box size snaps when the logo loads (aspect known) and animates only when the trailer starts / stops.
    var nuvioCLogoAspect by remember(meta.logo) { mutableStateOf(com.nuvio.tv.ui.components.NuvioCLogoSize.known(meta.logo)) }
    val nuvioCLogoNormal = com.nuvio.tv.ui.components.NuvioCLogoSize.DETAIL.sizeFor(nuvioCLogoAspect)
    val nuvioCTrailerLogoWidth = (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - com.nuvio.tv.ui.components.nuvioCDetailMargin * 2) * 0.25f
    val nuvioCLogoTargetW = if (isTrailerPlaying) nuvioCTrailerLogoWidth else nuvioCLogoNormal.width
    val nuvioCLogoTargetH = if (isTrailerPlaying) 60.dp else nuvioCLogoNormal.height + NuvioTheme.spacing.lg
    val nuvioCLogoW = remember { androidx.compose.animation.core.Animatable(nuvioCLogoTargetW, androidx.compose.ui.unit.Dp.VectorConverter) }
    val nuvioCLogoH = remember { androidx.compose.animation.core.Animatable(nuvioCLogoTargetH, androidx.compose.ui.unit.Dp.VectorConverter) }
    val nuvioCLastTrailer = remember { mutableStateOf(isTrailerPlaying) }
    LaunchedEffect(nuvioCLogoTargetW, nuvioCLogoTargetH, isTrailerPlaying) {
        if (nuvioCLastTrailer.value != isTrailerPlaying) {
            nuvioCLastTrailer.value = isTrailerPlaying
            kotlinx.coroutines.coroutineScope {
                launch { nuvioCLogoW.animateTo(nuvioCLogoTargetW, tween(600)) }
                launch { nuvioCLogoH.animateTo(nuvioCLogoTargetH, tween(600)) }
            }
        } else {
            nuvioCLogoW.snapTo(nuvioCLogoTargetW)
            nuvioCLogoH.snapTo(nuvioCLogoTargetH)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight),
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then( // [fork] same size animation without clipping the logo or the focused buttons
                    if (nuvioCFadeLogo) Modifier.nuvioCAnimateHeightNoClip(tween(600), animateKey = isTrailerPlaying)
                    else Modifier.animateContentSize(animationSpec = tween(600))
                )
                .padding(start = com.nuvio.tv.ui.components.nuvioCDetailMargin, end = com.nuvio.tv.ui.components.nuvioCDetailMargin, bottom = NuvioTheme.spacing.lg), // [fork] page margin
            verticalArrangement = Arrangement.Bottom
        ) {
            // Logo/Title — always visible during trailer, animates size
            if (shouldShowLogo) {
                AsyncImage(
                    model = logoModel,
                    contentDescription = meta.name,
                    onError = { logoLoadFailed = true },
                    onSuccess = { nuvioCLogoAspect = com.nuvio.tv.ui.components.NuvioCLogoSize.aspectOf(it) }, // [fork] logo weight
                    modifier = Modifier
                        .nuvioCFade { if (nuvioCFadeLogo) nuvioCLogoAlpha.value else 1f } // [fork] no offscreen layer, nothing clipped
                        .then(
                            if (com.nuvio.tv.ui.components.NuvioCLogoSize.enabled) Modifier.width(nuvioCLogoW.value).height(nuvioCLogoH.value) // [fork] logo weight
                            else Modifier
                                .height(logoHeight)
                                .fillMaxWidth(logoMaxWidth)
                                .then(if (com.nuvio.tv.ui.components.NuvioCHeroSpec.enabled) Modifier.widthIn(max = com.nuvio.tv.ui.components.NuvioCHeroSpec.logoMaxWidth) else Modifier) // [fork] hero spec
                        )
                        .padding(bottom = logoBottomPadding),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                // Text title hides entirely during trailer
                AnimatedVisibility(
                    visible = !isTrailerPlaying,
                    enter = fadeIn(tween(NuvioMotion.tokens.durations.overlay)),
                    exit = fadeOut(tween(NuvioMotion.tokens.durations.overlay))
                ) {
                    Text(
                        text = meta.name,
                        style = MaterialTheme.typography.displayMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
                    )
                }
            }

            // Everything below the logo fades out during trailer
            AnimatedVisibility(
                visible = isTrailerPlaying && !hideLogoDuringTrailer,
                enter = fadeIn(tween(600)),
                exit = fadeOut(tween(300))
            ) {
                Text(
                    text = stringResource(R.string.hero_press_back_trailer),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(bottom = NuvioTheme.spacing.sm)
                )
            }

            // Everything below the logo fades out during trailer
            AnimatedVisibility(
                visible = !isTrailerPlaying,
                // [fork] fade on a non-clipping layer, so the focused Play button's ring is never cut
                enter = if (nuvioCFadeLogo) androidx.compose.animation.EnterTransition.None else fadeIn(tween(NuvioMotion.tokens.durations.overlay)),
                exit = fadeOut(tween(NuvioMotion.tokens.durations.overlay)),
                modifier = if (nuvioCFadeLogo) Modifier.nuvioCFade { nuvioCActionsAlpha.value } else Modifier // [fork]
            ) {
                Column {
                    // [fork] tagline: between the logo and Play, details text style, one line (two if long),
                    // hidden with the buttons while a trailer plays; absent = no gap at all.
                    if (com.nuvio.tv.NuvioCFeatures.DETAIL_TAGLINE && !nuvioCTagline.isNullOrBlank()) {
                        NuvioCTagline( // [fork] style A: italic, hanging quotes, synopsis width
                            tagline = nuvioCTagline,
                            widthFraction = if (com.nuvio.tv.ui.components.NuvioCHeroSpec.enabled) com.nuvio.tv.ui.components.NuvioCHeroSpec.DETAIL_TEXT_FRACTION else 0.6f,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayButton(
                            text = if (isPlayEnabled) nextToWatch?.displayText else stringResource(R.string.playback_unavailable),
                            enabled = isPlayEnabled,
                            onClick = onPlayClick,
                            onLongPress = onPlayLongPress,
                            focusRequester = playButtonFocusRequester,
                            restoreFocusToken = restorePlayFocusToken,
                            nuvioCTokenSeen = nuvioCPlayTokenSeen,
                            onFocusRestored = {
                                onHeroActionFocused()
                                onPlayFocusRestored()
                            }
                        )

                        if (showRandomEpisodeButton && com.nuvio.tv.NuvioCFeatures.SHUFFLE_BUTTON_SIZE) { // [fork] round like the others, next to Play
                            ActionIconButton(
                                icon = if (episodeShuffle.enabled) Icons.Default.Stop else null,
                                painter = if (episodeShuffle.enabled) null else androidx.compose.ui.res.painterResource(R.drawable.nuvio_c_ic_shuffle),
                                contentDescription = stringResource(if (episodeShuffle.enabled) R.string.shuffle_stop else R.string.random_episode_title),
                                onClick = { if (!shuffleActionPending) onRandomEpisodeClick() },
                                selected = episodeShuffle.enabled,
                                selectedContainerColor = Color.White,
                                selectedContentColor = Color.Black,
                                onFocused = onHeroActionFocused,
                                focusRequester = randomEpisodeFocusRequester
                            )
                        }

                        ActionIconButton(
                            icon = if (isInLibrary) Icons.Default.Check else null,
                            painter = if (!isInLibrary) {
                                libraryAddPainter
                            } else {
                                null
                            },
                            contentDescription = if (isInLibrary) stringResource(R.string.hero_remove_from_library) else stringResource(R.string.hero_add_to_library),
                            // [fork] detail_plus_picker: click opens the library list, hold does the one-step add/remove
                            onClick = if (com.nuvio.tv.NuvioCFeatures.DETAIL_PLUS_PICKER) onLibraryLongPress else onToggleLibrary,
                            onLongPress = if (com.nuvio.tv.NuvioCFeatures.DETAIL_PLUS_PICKER) onToggleLibrary else onLibraryLongPress,
                            onFocused = onHeroActionFocused
                        )

                        if (meta.apiType == "movie") {
                            ActionIconButton(
                                icon = if (isMovieWatched) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                },
                                contentDescription = if (isMovieWatched) {
                                    stringResource(R.string.hero_mark_unwatched)
                                } else {
                                    stringResource(R.string.hero_mark_watched)
                                },
                                onClick = onToggleMovieWatched,
                                enabled = !isMovieWatchedPending,
                                selected = isMovieWatched,
                                selectedContainerColor = Color.White,
                                selectedContentColor = Color.Black,
                                onFocused = onHeroActionFocused
                            )
                        }

                        if (nuvioCRateAvailable) { // [fork] Rate: white when you've rated it, like Watched
                            ActionIconButton(
                                icon = if (nuvioCRated) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.nuvio_c_rate),
                                onClick = onNuvioCRate,
                                selected = nuvioCRated,
                                selectedContainerColor = Color.White,
                                selectedContentColor = Color.Black,
                                onFocused = onHeroActionFocused,
                                focusRequester = nuvioCRateFocusRequester
                            )
                        }

                        if (trailerAvailable) {
                            ActionIconButtonPainter(
                                painter = trailerPainter,
                                contentDescription = stringResource(R.string.hero_play_trailer),
                                onClick = onTrailerClick,
                                onFocused = onHeroActionFocused
                            )
                        }

                        if (showRandomEpisodeButton && !com.nuvio.tv.NuvioCFeatures.SHUFFLE_BUTTON_SIZE) {
                            ShuffleButton(
                                active = episodeShuffle.enabled,
                                pending = shuffleActionPending,
                                onClick = onRandomEpisodeClick,
                                focusRequester = randomEpisodeFocusRequester,
                                onFocused = onHeroActionFocused
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.lg))

                    // Director/Writer line above description
                    if (!creditLine.isNullOrBlank()) {
                        Text(
                            text = creditLine,
                            style = MaterialTheme.typography.labelLarge.copy(
                                textDirection = creditLine.contentTextDirection()
                            ),
                            color = NuvioTheme.extendedColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.nuvioCFade { nuvioCTextAlpha.value }.fillMaxWidth(0.6f) // [fork]
                        )
                        Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
                    }

                    if (mdbListRatings?.isEmpty() == false) {
                        MDBListRatingsRow(ratings = mdbListRatings, order = mdbListRatingOrder, modifier = Modifier.nuvioCFade { nuvioCTextAlpha.value }) // [fork]
                        Spacer(modifier = Modifier.height(14.dp))
                    } else if (com.nuvio.tv.NuvioCFeatures.HERO_RATINGS_SPACE && rememberNuvioCRatingsHold(nuvioCRatingsPending, meta.id)) { // [fork] same height, invisible, until MDBList answers
                        MDBListRatingsRow(ratings = NUVIO_C_RATINGS_PLACEHOLDER, modifier = Modifier.nuvioCInvisible())
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    meta.description?.nuvioCCleanSynopsis()?.let { description -> // [fork] no blank lines at the end
                        SynopsisDescription(
                            description = description,
                            onShowFullDescription = onShowFullDescription,
                            upFocusRequester = playButtonFocusRequester,
                            onFocused = onHeroActionFocused,
                            onTruncationChanged = onTruncationChanged,
                            nuvioCTextStyle = if (com.nuvio.tv.ui.components.NuvioCHeroSpec.enabled) com.nuvio.tv.ui.components.NuvioCHeroSpec.detailSynopsisStyle(MaterialTheme.typography.bodyMedium) else null, // [fork] hero spec
                            modifier = Modifier
                                .nuvioCFade { nuvioCTextAlpha.value * nuvioCDescriptionAlpha.value } // [fork]
                                .onFocusChanged { nuvioCSynopsisFocused = it.hasFocus } // [fork]
                                .fillMaxWidth(if (com.nuvio.tv.ui.components.NuvioCHeroSpec.enabled) com.nuvio.tv.ui.components.NuvioCHeroSpec.DETAIL_TEXT_FRACTION else 0.6f) // [fork] hero spec
                                .padding(bottom = NuvioTheme.spacing.md)
                        )
                    }

                    Box(modifier = Modifier.nuvioCFade { nuvioCTextAlpha.value }) { MetaInfoRow( // [fork] dimmed with the text
                        meta = meta,
                        hideImdbRating = hideMetaInfoImdb,
                        showFullReleaseDate = showFullReleaseDate,
                        tmdbRating = tmdbRating
                    ) }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun PlayButton(
    text: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    restoreFocusToken: Int = 0,
    nuvioCTokenSeen: androidx.compose.runtime.MutableIntState? = null,
    onFocusRestored: () -> Unit = {}
) {
    var longPressTriggered by remember { mutableStateOf(false) }
    val longPressKeyTracker = rememberLongPressKeyTracker()

    LaunchedEffect(restoreFocusToken) {
        // [fork] focus stability: only a new request moves focus to Play. Official re-ran this every
        // time the hero came back into the list (scrolling up from seasons / cast, back from a cast
        // page) and when the token dropped as a restore was cleared, pulling focus to Play.
        if (com.nuvio.tv.NuvioCFeatures.DETAIL_FOCUS_STABLE && nuvioCTokenSeen != null) {
            val seen = nuvioCTokenSeen.intValue
            nuvioCTokenSeen.intValue = restoreFocusToken
            if (restoreFocusToken <= seen) return@LaunchedEffect
        }
        if (restoreFocusToken > 0 && focusRequester != null) {
            focusRequester.requestFocusAfterFrames()
        }
    }
    val playPainter = rememberRawSvgPainter(rawIconRes = com.nuvio.tv.R.raw.ic_player_play)
    val nuvioCPill = com.nuvio.tv.ui.components.rememberNuvioCPillScale() // [fork] pill_focus_growth

    Button(
        enabled = enabled,
        onClick = {
            if (longPressTriggered) {
                longPressTriggered = false
            } else {
                onClick()
            }
        },
        modifier = modifier
            .then(nuvioCPill?.modifier ?: Modifier) // [fork] pill_focus_growth
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                if (it.isFocused) {
                    onFocusRestored()
                }
            }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0 && isSelectKey(native.keyCode)) {
                    longPressTriggered = false
                }
                if (enabled && onLongPress != null && native.action == AndroidKeyEvent.ACTION_DOWN) {
                    if (native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                        longPressTriggered = true
                        if (native.repeatCount == 0) onLongPress()
                        return@onPreviewKeyEvent true
                    }
                }
                if (enabled && onLongPress != null &&
                    longPressKeyTracker.handle(native, ::isSelectKey) {
                        longPressTriggered = true
                        onLongPress()
                    }
                ) {
                    if (native.action == AndroidKeyEvent.ACTION_UP) {
                        longPressTriggered = false
                    }
                    return@onPreviewKeyEvent true
                }

                if (native.action == AndroidKeyEvent.ACTION_UP && longPressTriggered) {
                    if (isSelectOrMenuKey(native.keyCode)) {
                        longPressTriggered = false
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusProperties { up = FocusRequester.Cancel },
        colors = ButtonDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.White,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
            contentColor = androidx.compose.ui.graphics.Color.Black,
            focusedContentColor = androidx.compose.ui.graphics.Color.Black
        ),
        shape = ButtonDefaults.shape(
            shape = RoundedCornerShape(NuvioTheme.spacing.xxl)
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(NuvioTheme.spacing.xxl)
            )
        ),
        scale = nuvioCPill?.scale ?: ButtonDefaults.scale(), // [fork] pill_focus_growth
        contentPadding = PaddingValues(horizontal = NuvioTheme.spacing.xl, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
            modifier = Modifier.animateContentSize(
                animationSpec = tween(
                    durationMillis = NuvioMotion.tokens.durations.fast,
                    easing = NuvioMotion.tokens.easings.standard
                )
            )
        ) {
            Icon(
                painter = playPainter,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            AnimatedVisibility(
                visible = text != null,
                enter = fadeIn(animationSpec = tween(NuvioMotion.tokens.durations.fast)),
                exit = fadeOut(animationSpec = tween(NuvioMotion.tokens.durations.quick))
            ) {
                Text(
                    text = text ?: "",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ActionIconButtonPainter(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(NuvioTheme.spacing.xxxl)
            .onFocusChanged { state ->
                if (state.isFocused) onFocused()
            }
            .focusProperties { up = FocusRequester.Cancel },
        colors = IconButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.Secondary,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContentColor = NuvioTheme.colors.OnSecondary
        ),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = CircleShape
            )
        ),
        shape = IconButtonDefaults.shape(
            shape = CircleShape
        )
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painter: Painter? = null,
    contentDescription: String,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    selectedContainerColor: Color = Color(0xFF7CFF9B),
    selectedContentColor: Color = Color.Black,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    var longPressTriggered by remember { mutableStateOf(false) }
    val longPressKeyTracker = rememberLongPressKeyTracker()

    IconButton(
        onClick = {
            if (longPressTriggered) {
                longPressTriggered = false
            } else {
                onClick()
            }
        },
        enabled = enabled,
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(NuvioTheme.spacing.xxxl)
            .onFocusChanged { state ->
                if (state.isFocused) onFocused()
            }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0 && isSelectKey(native.keyCode)) {
                    longPressTriggered = false
                }
                if (onLongPress != null && native.action == AndroidKeyEvent.ACTION_DOWN) {
                    if (native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                        longPressTriggered = true
                        if (native.repeatCount == 0) onLongPress()
                        return@onPreviewKeyEvent true
                    }
                }
                if (onLongPress != null &&
                    longPressKeyTracker.handle(native, ::isSelectKey) {
                        longPressTriggered = true
                        onLongPress()
                    }
                ) {
                    if (native.action == AndroidKeyEvent.ACTION_UP) {
                        longPressTriggered = false
                    }
                    return@onPreviewKeyEvent true
                }

                if (native.action == AndroidKeyEvent.ACTION_UP && longPressTriggered) {
                    if (isSelectOrMenuKey(native.keyCode)) {
                        longPressTriggered = false
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .focusProperties { up = FocusRequester.Cancel },
        colors = IconButtonDefaults.colors(
            containerColor = if (selected) selectedContainerColor else NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.Secondary,
            contentColor = if (selected) selectedContentColor else NuvioTheme.colors.TextPrimary,
            focusedContentColor = NuvioTheme.colors.OnSecondary
        ),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = CircleShape
            )
        ),
        shape = IconButtonDefaults.shape(
            shape = CircleShape
        )
    ) {
        when {
            painter != null -> Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(22.dp)
            )
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(NuvioTheme.spacing.xl)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetaInfoRow(
    meta: Meta,
    hideImdbRating: Boolean,
    showFullReleaseDate: Boolean = true,
    tmdbRating: Float? = null
) {
    val context = LocalContext.current
    val genresText = remember(meta.genres) {
        meta.genres
            .take(MAX_VISIBLE_HERO_GENRES)
            .joinToString(" • ") { localizedGenreLabel(context, it) }
    }
    val runtimeText = remember(meta.runtime) { meta.runtime?.let { formatRuntime(it) } }
    val yearText = remember(meta.releaseInfo, meta.released, meta.type, showFullReleaseDate) {
        if (showFullReleaseDate && meta.type == ContentType.MOVIE) {
            meta.released
                ?.let { runCatching { java.time.OffsetDateTime.parse(it).toLocalDate() }.getOrNull() }
                ?.let { val locale = java.util.Locale.getDefault(); java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMMMy"), locale).format(java.util.Date(it.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())) }
                ?: formatYearRange(meta.releaseInfo)
        } else {
            formatYearRange(meta.releaseInfo)
        }
    }
    val imdbRating = if (hideImdbRating) null else meta.imdbRating
    val reserveImdbRatingHeight = meta.imdbRating != null
    val shouldShowImdbRating = imdbRating != null
    val shouldShowTmdbRating = tmdbRating != null
    val tmdbModel = remember(context) {
        ImageRequest.Builder(context)
            .data(com.nuvio.tv.R.raw.mdblist_tmdb)
            .build()
    }
    val ageRatingBadge = remember(meta.ageRating) {
        meta.ageRating?.trim()?.takeIf { it.isNotBlank() }
    }
    val isSeries = meta.type == ContentType.SERIES || meta.type == ContentType.TV
    val strStatusEnded = stringResource(if (isSeries) R.string.series_status_ended else R.string.movie_status_ended)
    val strStatusContinuing = stringResource(if (isSeries) R.string.series_status_continuing else R.string.movie_status_continuing)
    val strStatusCurrent = stringResource(if (isSeries) R.string.series_status_current else R.string.movie_status_current)
    val strStatusCancelled = stringResource(if (isSeries) R.string.series_status_cancelled else R.string.movie_status_cancelled)
    val strStatusReleased = stringResource(if (isSeries) R.string.series_status_released else R.string.movie_status_released)
    val strStatusPlanned = stringResource(if (isSeries) R.string.series_status_planned else R.string.movie_status_planned)
    val strStatusRumored = stringResource(if (isSeries) R.string.series_status_rumored else R.string.movie_status_rumored)
    val strStatusInProduction = stringResource(if (isSeries) R.string.series_status_in_production else R.string.movie_status_in_production)
    val strStatusPostProduction = stringResource(if (isSeries) R.string.series_status_post_production else R.string.movie_status_post_production)
    val statusBadge = remember(meta.status, isSeries) {
        when (meta.status?.trim()?.lowercase()) {
            "ended" -> strStatusEnded.uppercase()
            "continuing", "returning series" -> strStatusContinuing.uppercase()
            "current" -> strStatusCurrent.uppercase()
            "cancelled", "canceled" -> strStatusCancelled.uppercase()
            "released" -> strStatusReleased.uppercase()
            "planned" -> strStatusPlanned.uppercase()
            "rumored" -> strStatusRumored.uppercase()
            "in production" -> strStatusInProduction.uppercase()
            "post production" -> strStatusPostProduction.uppercase()
            else -> meta.status?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
        }
    }
    Log.d("HeroBadge", "name=${meta.name} ageRating=${meta.ageRating} status=${meta.status} ageRatingBadge=$ageRatingBadge statusBadge=$statusBadge")
    val secondaryItems = remember(runtimeText, meta.country, meta.language) {
        buildList<String> {
            runtimeText?.takeIf { it.isNotBlank() }?.let { add(it) }
            meta.country?.trim()?.takeIf { it.isNotBlank() }?.let { add(normalizeCountryLabel(it)) }
            meta.language?.trim()?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
        // Primary row: Genres, Release, Ratings
        Row(
            modifier = if (reserveImdbRatingHeight) Modifier.height(30.dp) else Modifier,
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (meta.genres.isNotEmpty()) {
                Text(
                    text = genresText,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge,
                    color = NuvioTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (yearText != null || shouldShowImdbRating || shouldShowTmdbRating) {
                    MetaInfoDivider()
                }
            }

            yearText?.let { year ->
                Text(
                    text = year,
                    style = MaterialTheme.typography.labelLarge,
                    color = NuvioTheme.extendedColors.textSecondary
                )
                if (shouldShowImdbRating || shouldShowTmdbRating) {
                    MetaInfoDivider()
                }
            }

            imdbRating?.let { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
                ) {
                    ImdbRatingSourceLabel(
                        logoModifier = Modifier.size(30.dp),
                        textStyle = MaterialTheme.typography.labelLarge,
                        textColor = NuvioTheme.extendedColors.textSecondary
                    )
                    val ratingText = remember(rating) { String.format("%.1f", rating) }
                    Text(
                        text = ratingText,
                        style = MaterialTheme.typography.labelLarge,
                        color = NuvioTheme.extendedColors.textSecondary
                    )
                }
            }

            tmdbRating?.let { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
                ) {
                    AsyncImage(
                        model = tmdbModel,
                        contentDescription = null,
                        modifier = Modifier.size(NuvioTheme.spacing.xl),
                        contentScale = ContentScale.Fit
                    )
                    val ratingText = remember(rating) { (rating * 10).toInt().toString() }
                    Text(
                        text = ratingText,
                        style = MaterialTheme.typography.labelLarge,
                        color = NuvioTheme.extendedColors.textSecondary
                    )
                }
            }
        }

        // Secondary row: Runtime, Age Rating, Status, Country, Language
        if (ageRatingBadge != null || statusBadge != null || secondaryItems.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ageRatingBadge != null && statusBadge != null) {
                    CombinedMetaBadge(
                        leftText = ageRatingBadge,
                        leftColor = NuvioTheme.colors.TextSecondary,
                        rightText = statusBadge,
                        rightColor = NuvioTheme.colors.TextPrimary
                    )
                } else {
                    ageRatingBadge?.let { badge ->
                        HeroMetaBadge(text = badge)
                    }
                    statusBadge?.let { badge ->
                        HeroMetaBadge(
                            text = badge,
                            contentColor = NuvioTheme.colors.TextPrimary
                        )
                    }
                }
                if ((ageRatingBadge != null || statusBadge != null) && secondaryItems.isNotEmpty()) {
                    MetaInfoDivider()
                }
                secondaryItems.forEachIndexed { index, value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioTheme.colors.TextPrimary
                    )
                    if (index < secondaryItems.lastIndex) {
                        MetaInfoDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetaBadge(
    text: String,
    contentColor: Color = NuvioTheme.colors.TextSecondary
) {
    Box(
        modifier = Modifier
            .border(
                border = BorderStroke(NuvioTheme.spacing.hairline, contentColor.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = NuvioTheme.spacing.sm, vertical = NuvioTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun CombinedMetaBadge(
    leftText: String,
    leftColor: Color = NuvioTheme.colors.TextSecondary,
    rightText: String,
    rightColor: Color = NuvioTheme.colors.TextPrimary
) {
    val dividerColor = leftColor.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .border(
                border = BorderStroke(NuvioTheme.spacing.hairline, dividerColor),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = NuvioTheme.spacing.sm, vertical = NuvioTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        Text(
            text = leftText,
            style = MaterialTheme.typography.labelMedium,
            color = leftColor,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(NuvioTheme.spacing.hairline)
                .height(NuvioTheme.spacing.md)
                .background(dividerColor)
        )
        Text(
            text = rightText,
            style = MaterialTheme.typography.labelMedium,
            color = rightColor,
            maxLines = 1
        )
    }
}

private fun normalizeCountryLabel(raw: String): String {
    val displayLocale = Locale.getDefault()
    return raw
        .split(",")
        .joinToString(", ") { part ->
            val code = part.trim()
            if (code.matches(Regex("[A-Za-z]{2}"))) {
                Locale("", code).getDisplayCountry(displayLocale).takeIf { it.isNotBlank() } ?: code
            } else {
                code
            }
        }
}

private fun isSelectKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}

private fun isSelectOrMenuKey(keyCode: Int): Boolean {
    return isSelectKey(keyCode) || keyCode == AndroidKeyEvent.KEYCODE_MENU
}


private fun formatYearRange(releaseInfo: String?): String? {
    if (releaseInfo.isNullOrBlank()) return null
    return releaseInfo.trim()
}

/**
 * Null when the runtime is zero or unparseable-as-positive.
 *
 * TMDB answers with runtime 0, not null, for a title whose length it does not know yet, so
 * every branch below could reach the end with a total of zero and render a literal "0m" in the
 * metadata row. The caller already drops a null.
 */
private fun formatRuntime(runtime: String): String? {
    val trimmed = runtime.trim()
    // Already in "Xh Ym" or "Xh" format
    if (trimmed.contains('h') || trimmed.contains('m')) {
        val hours = Regex("(\\d+)\\s*h").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val mins = Regex("(\\d+)\\s*m").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + mins
        if (total > 0) return if (total >= 60) {
            val h = total / 60; val m = total % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        } else "${total}m"
    }
    // "H:MM" or "HH:MM" format
    if (trimmed.contains(':')) {
        val parts = trimmed.split(':')
        val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val mins = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + mins
        if (total > 0) return if (total >= 60) {
            val m = total % 60
            if (m > 0) "${hours}h ${m}m" else "${hours}h"
        } else "${total}m"
    }
    // Plain number (minutes)
    val minutes = trimmed.filter { it.isDigit() }.toIntOrNull() ?: return runtime
    if (minutes <= 0) return null
    return if (minutes >= 60) {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
    } else {
        "${minutes}m"
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetaInfoDivider() {
    Text(
        text = "•",
        style = MaterialTheme.typography.labelLarge,
        color = NuvioTheme.extendedColors.textTertiary
    )
}

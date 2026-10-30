package com.nuvio.tv.ui.screens.home

// [fork] Nuvio C round 33 (switches `featured_logo_yield`, `featured_polish`).
//
// featured_logo_yield: the Featured hero's text block used to stack logo, tagline, badges, ratings and synopsis in
// order, so a tall or square logo (Helluva Boss, about 140 x 140 dp) took the room and the synopsis was cut to
// one line. Now a custom layout measures the badges / ratings first, keeps room for four synopsis lines, gives
// the logo what's left (never below 64 dp tall; Fit + BottomStart, so it just scales down), then hands the
// synopsis everything still free (up to six lines). Wide logos, which never needed the room, look the same.
// The tagline is one line on the Featured hero.
//
// featured_polish P3: on a slide change the parts come in one after the other (logo, then the badges and
// ratings, then the synopsis), each fading up from 6 dp lower. Driven by the existing fade-through's alpha,
// read only in graphicsLayer blocks (ModulateAlpha, no offscreen buffers), so nothing recomposes or re-lays out.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.nuvio.tv.core.di.NuvioCFeaturedTagline
import com.nuvio.tv.domain.model.MDBListRatings
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.MDBListRatingsRow
import com.nuvio.tv.ui.components.NuvioCHeroSpec
import com.nuvio.tv.ui.components.NuvioCLogoSize
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.formatHeroRuntime
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import kotlin.math.max

/** Entrance progress of the block (0 = just swapped in, 1 = settled); null = no staggering. Read in draw only. */
internal val LocalNuvioCFeaturedEntrance = compositionLocalOf<(() -> Float)?> { null }

/** P3: part [step] (0, 1, 2) of the block fades / rises in a little after the one before. */
@Composable
private fun Modifier.staggered(step: Int): Modifier {
    val entrance = LocalNuvioCFeaturedEntrance.current ?: return this
    val rise = with(LocalDensity.current) { 6.dp.toPx() }
    return this.graphicsLayer {
        val p = entrance()
        val start = step * 0.18f
        val t = ((p - start) / (1f - 0.36f)).coerceIn(0f, 1f)
        val eased = 1f - (1f - t) * (1f - t) // ease-out
        alpha = eased
        translationY = (1f - eased) * rise
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }
}

/** Pure sizing rule (unit tested): px for the logo given the free height and the parts below it. */
internal object NuvioCFeaturedYieldRules {
    /** Height the logo slot may take: what's left after [midPx] and [synMinPx], never below [logoMinPx]. */
    fun logoMax(totalPx: Int, rankPx: Int, midPx: Int, synMinPx: Int, gapsPx: Int, logoMinPx: Int): Int =
        max(logoMinPx, totalPx - rankPx - midPx - synMinPx - gapsPx)

    /** Height left for the synopsis once everything above is placed. */
    fun synMax(totalPx: Int, topPx: Int, midPx: Int, gapsPx: Int): Int = max(0, totalPx - topPx - midPx - gapsPx)
}

private const val SYN_MIN_LINES = 4
private val LOGO_MIN = 64.dp
private val GAP_SYN = 8.dp

@Composable
internal fun NuvioCFeaturedTextYield(
    item: MetaPreview,
    ratings: MDBListRatings?,
    ratingOrder: List<String>,
    taglineText: String?,
    tagline: NuvioCFeaturedTagline?,
    info: com.nuvio.tv.core.di.NuvioCFeaturedInfo?,
    ratingsPending: Boolean,
    synopsisLinesMax: Int
) {
    val context = LocalContext.current
    val synopsis = item.description?.let { NuvioCHeroSpec.homeSynopsis(it) }?.takeIf { it.isNotBlank() }
    val synopsisStyle = NuvioCHeroSpec.detailSynopsisStyle(MaterialTheme.typography.bodyMedium)
    val density = LocalDensity.current
    val linePx = with(density) { synopsisStyle.lineHeight.toPx() }.toInt().coerceAtLeast(1)
    val hasRank = com.nuvio.tv.NuvioCFeatures.FEATURED_BADGES && NuvioCFeaturedBadgeRules.rank(NuvioCHeroSource.of(item.id)) != null
    // the top slot holds the rank line too (about 24 dp + 10 dp gap), so its floor includes it
    val logoMinPx = with(density) { (LOGO_MIN + if (hasRank) 34.dp else 0.dp).roundToPx() }
    val gapSynPx = if (synopsis != null) with(density) { GAP_SYN.roundToPx() } else 0

    Layout(
        contents = listOf(
            // 0: rank line + logo (or the name)
            {
                Column(Modifier.staggered(0)) {
                    if (hasRank) {
                        NuvioCFeaturedRankLine(item)
                        Spacer(Modifier.height(10.dp))
                    }
                    var aspect by remember(item.logo) { mutableStateOf(NuvioCLogoSize.known(item.logo)) }
                    var logoFailed by remember(item.logo) { mutableStateOf(false) }
                    if (!item.logo.isNullOrBlank() && !logoFailed) {
                        val box = NuvioCLogoSize.HOME.sizeFor(aspect)
                        val model = remember(item.logo) { nuvioCFeaturedLogoRequest(context, item.logo) }
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
                            // the logo's own size is its upper bound; the layout may give it less (Fit scales it down)
                            modifier = Modifier.width(box.width).layoutHeight(box.height)
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
                }
            },
            // 1: tagline, badges + meta, ratings
            {
                Column(Modifier.staggered(1)) {
                    if (tagline != null && !taglineText.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        CompositionLocalProvider(com.nuvio.tv.ui.screens.detail.LocalNuvioCTaglineLines provides 1) {
                            tagline.Draw(taglineText, 0.92f, Modifier)
                        }
                    }
                    val meta = remember(item.id, item.apiType, item.genres, item.releaseInfo, item.runtime) {
                        listOfNotNull(
                            localizedContentType(context, item.apiType).takeIf { it.isNotBlank() },
                            item.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.let { localizedGenreLabel(context, it) },
                            NuvioCFeaturedRules.year(item.releaseInfo),
                            formatHeroRuntime(item.runtime)
                        ).joinToString("  ·  ")
                    }
                    val status = if (com.nuvio.tv.NuvioCFeatures.FEATURED_BADGES) NuvioCFeaturedBadgeRules.status(item, info) else null
                    if (meta.isNotBlank() || status != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (status != null) NuvioCFeaturedStatusChip(status)
                            if (meta.isNotBlank()) Text(
                                text = meta,
                                style = MaterialTheme.typography.labelMedium,
                                color = NuvioTheme.colors.TextPrimary.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (ratings != null) {
                        Spacer(Modifier.height(8.dp))
                        MDBListRatingsRow(
                            ratings = ratings,
                            order = ratingOrder,
                            maxItems = if (com.nuvio.tv.NuvioCFeatures.FEATURED_TOP3_RATINGS) 3 else Int.MAX_VALUE
                        )
                    } else if (com.nuvio.tv.ui.screens.detail.rememberNuvioCRatingsHold(ratingsPending, item.id)) {
                        Spacer(Modifier.height(8.dp))
                        MDBListRatingsRow(
                            ratings = com.nuvio.tv.ui.screens.detail.NUVIO_C_RATINGS_PLACEHOLDER,
                            modifier = Modifier.alpha(0f)
                        )
                    }
                }
            },
            // 2: synopsis (ellipsizes at whatever height it's given)
            {
                if (synopsis != null) {
                    Text(
                        text = synopsis,
                        style = synopsisStyle,
                        color = NuvioTheme.colors.TextPrimary.copy(alpha = NuvioCHeroSpec.DETAIL_SYNOPSIS_ALPHA),
                        maxLines = synopsisLinesMax,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.staggered(2)
                    )
                }
            }
        )
    ) { (topM, midM, synM), constraints ->
        val maxW = (constraints.maxWidth * 0.92f).toInt().coerceAtLeast(0)
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val bounded = constraints.hasBoundedHeight
        val total = if (bounded) constraints.maxHeight else Int.MAX_VALUE
        val mid = midM.firstOrNull()?.measure(loose)
        val midH = mid?.height ?: 0
        val synMin = if (synM.isEmpty()) 0 else linePx * SYN_MIN_LINES
        val top = topM.firstOrNull()?.let { m ->
            val cap = if (bounded) NuvioCFeaturedYieldRules.logoMax(total, 0, midH, synMin, gapSynPx, logoMinPx) else Constraints.Infinity
            m.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = cap))
        }
        val topH = top?.height ?: 0
        val synCap = if (bounded) NuvioCFeaturedYieldRules.synMax(total, topH, midH, gapSynPx) else linePx * synopsisLinesMax
        val syn = synM.firstOrNull()?.measure(Constraints(maxWidth = maxW, maxHeight = synCap))
        val synH = syn?.height ?: 0
        val g = if (syn != null && synH > 0) gapSynPx else 0
        val width = listOfNotNull(top?.width, mid?.width, syn?.width).maxOrNull() ?: 0
        val height = (topH + midH + g + synH).let { if (bounded) it.coerceAtMost(constraints.maxHeight) else it }
        layout(width.coerceAtMost(constraints.maxWidth), height) {
            var y = 0
            top?.place(0, y); y += topH
            mid?.place(0, y); y += midH + g
            syn?.place(0, y)
        }
    }
}

/** A preferred height that still accepts a smaller max height from the parent (the image then fits). */
private fun Modifier.layoutHeight(h: androidx.compose.ui.unit.Dp): Modifier =
    this.then(Modifier.layout { measurable, constraints ->
        val want = h.roundToPx()
        val use = want.coerceIn(constraints.minHeight, constraints.maxHeight)
        val p = measurable.measure(constraints.copy(minHeight = use, maxHeight = use))
        layout(p.width, use) { p.place(0, 0) }
    })

/** P3 entrance: wraps the fade-through so its fade-in drives the per-part stagger instead of the whole block. */
@Composable
internal fun NuvioCFeaturedStaggerScope(entrance: () -> Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNuvioCFeaturedEntrance provides entrance) { content() }
}

// ---- featured_polish P1: the current page dot is a short pill that fills until the next slide ----------

/** When the current slide's timer started; the fill restarts with every key press like the timer does. */
internal class NuvioCFeaturedTimer(private val clock: com.nuvio.tv.ui.components.NuvioCIdleClock) {
    @Volatile var startMs: Long = 0L
    var running by mutableStateOf(false)

    fun fraction(now: Long = android.os.SystemClock.uptimeMillis()): Float {
        if (!running) return 0f
        val from = max(startMs, clock.lastMs)
        return ((now - from).toFloat() / com.nuvio.tv.ui.components.NuvioCHeroAutoAdvance.INTERVAL_MS).coerceIn(0f, 1f)
    }
}

/** The active dot as a 22 x 6 dp pill; its fill is redrawn 10 times a second (draw only, no recomposition). */
@Composable
internal fun NuvioCFeaturedTimedPill(timer: NuvioCFeaturedTimer, active: Int) {
    val frac = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(timer.running, active) {
        frac.floatValue = timer.fraction()
        while (timer.running) {
            kotlinx.coroutines.delay(100)
            frac.floatValue = timer.fraction()
        }
        frac.floatValue = 0f
    }
    androidx.compose.foundation.layout.Box(
        Modifier
            .width(22.dp)
            .height(6.dp)
            .androidxDrawPill { frac.floatValue }
    )
}

private fun Modifier.androidxDrawPill(fraction: () -> Float): Modifier = this.then(
    Modifier.drawBehind {
        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        drawRoundRect(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.30f), cornerRadius = r)
        val w = size.width * fraction()
        if (w > 0f) {
            drawRoundRect(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f),
                size = androidx.compose.ui.geometry.Size(max(w, size.height), size.height),
                cornerRadius = r
            )
        }
    }
)

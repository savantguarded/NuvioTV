@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import com.nuvio.tv.ui.components.nuvioCFocusRoom // [fork] focus room
import com.nuvio.tv.ui.components.nuvioCSwatchRing // [fork] focus ring shape

import com.nuvio.tv.ui.theme.NuvioTheme

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.nuvio.tv.ui.util.keepLtr
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.tv.material3.Card
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.local.SUBTITLE_LANGUAGE_FORCED
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncedChip

private const val SubtitleOffLanguageKey = "__off__"
private const val SubtitleUnknownLanguageKey = "__unknown__"
private const val SubtitleFocusTag = "SubtitleFocus"

private const val RailFadeDurationMs = 120

@Composable
internal fun SubtitleSelectionOverlay(
    visible: Boolean,
    internalTracks: List<TrackInfo>,
    selectedInternalIndex: Int,
    addonSubtitles: List<Subtitle>,
    selectedAddonSubtitle: Subtitle?,
    subtitleStyle: SubtitleStyleSettings,
    isEditingHdrStyle: Boolean = false,
    subtitleDelayMs: Int,
    installedSubtitleAddonOrder: List<String>,
    isLoadingAddons: Boolean,
    useLibass: Boolean = false,
    isUsingMpv: Boolean = false,
    onInternalTrackSelected: (Int) -> Unit,
    onAddonSubtitleSelected: (Subtitle) -> Unit,
    onDisableSubtitles: () -> Unit,
    onEvent: (PlayerEvent) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val noneLabel = stringResource(R.string.subtitle_none)
    val unknownLabel = stringResource(R.string.subtitle_language_unknown)
    val builtInLabel = stringResource(R.string.subtitle_built_in)
    val forcedLabel = stringResource(R.string.sub_forced_lang)
    var persistedStyleFocusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val sessionPreferredLanguage = remember(visible) { subtitleStyle.preferredLanguage }
    val sessionSecondaryPreferredLanguage = remember(visible) { subtitleStyle.secondaryPreferredLanguage }
    val sessionShowOnlyPreferredLanguages = remember(visible) { subtitleStyle.showOnlyPreferredLanguages }
    val sessionSelectedInternalIndex = remember(visible) { selectedInternalIndex }
    val sessionInternalTracks = remember(visible) { internalTracks.map(TrackInfo::copy) }
    val sessionAddonSubtitles = remember(visible, addonSubtitles) { addonSubtitles.map(Subtitle::copy) }
    val sessionSelectedAddonSubtitle = remember(visible) { selectedAddonSubtitle?.copy() }
    val sessionInstalledSubtitleAddonOrder = remember(visible) { installedSubtitleAddonOrder.toList() }
    val sessionIsLoadingAddons = isLoadingAddons
    val sessionSelectedSubtitleLanguageKey = remember(visible) {
        selectedSubtitleLanguageKey(
            internalTracks = sessionInternalTracks,
            selectedInternalIndex = sessionSelectedInternalIndex,
            selectedAddonSubtitle = sessionSelectedAddonSubtitle
        )
    }
    val languageItems = remember(visible, sessionAddonSubtitles) {
        buildSubtitleLanguageRailItems(
            internalTracks = sessionInternalTracks,
            addonSubtitles = sessionAddonSubtitles,
            preferredLanguage = sessionPreferredLanguage,
            secondaryPreferredLanguage = sessionSecondaryPreferredLanguage,
            showOnlyPreferredLanguages = sessionShowOnlyPreferredLanguages,
            currentLanguageKey = sessionSelectedSubtitleLanguageKey,
            noneLabel = noneLabel,
            unknownLabel = unknownLabel
        )
    }
    val sessionInitialLanguageKey = remember(visible, languageItems, sessionSelectedSubtitleLanguageKey) {
        sessionSelectedSubtitleLanguageKey.takeIf { key -> languageItems.any { it.key == key } }
            ?: languageItems.firstOrNull { it.key != SubtitleOffLanguageKey }?.key
            ?: SubtitleOffLanguageKey
    }
    val sessionInitialSelectedOptionId = remember(
        visible,
        sessionInitialLanguageKey,
        sessionSelectedSubtitleLanguageKey
    ) {
        val optionId = selectedSubtitleOptionId(
            internalTracks = sessionInternalTracks,
            selectedInternalIndex = sessionSelectedInternalIndex,
            selectedAddonSubtitle = sessionSelectedAddonSubtitle
        )
        optionId.takeIf { sessionInitialLanguageKey == sessionSelectedSubtitleLanguageKey }
    }
    fun buildSessionOptions(languageKey: String, activeSelectedOptionId: String?): List<SubtitleOptionRailItem> {
        return buildSubtitleOptionRailItems(
            selectedLanguageKey = languageKey,
            internalTracks = sessionInternalTracks,
            addonSubtitles = sessionAddonSubtitles,
            installedAddonOrder = sessionInstalledSubtitleAddonOrder,
            selectedOptionId = activeSelectedOptionId,
            builtInLabel = builtInLabel,
            forcedLabel = forcedLabel
        )
    }

    val sessionInitialSubtitleOptions = remember(
        visible,
        sessionInitialLanguageKey,
        sessionInitialSelectedOptionId
    ) {
        buildSessionOptions(sessionInitialLanguageKey, sessionInitialSelectedOptionId)
    }
    val sessionInitialOptionTargetId = remember(visible) {
        sessionInitialSelectedOptionId
            ?.takeIf { id -> sessionInitialSubtitleOptions.any { it.id == id } }
            ?: sessionInitialSubtitleOptions.firstOrNull()?.id
    }
    var selectedLanguageKey by remember(visible) {
        mutableStateOf(sessionInitialLanguageKey)
    }
    var selectedOptionId by remember(visible) {
        mutableStateOf(sessionInitialSelectedOptionId)
    }
    val subtitleOptions = remember(
        selectedLanguageKey,
        selectedOptionId,
        sessionInternalTracks,
        sessionAddonSubtitles,
        sessionInstalledSubtitleAddonOrder
    ) {
        buildSessionOptions(selectedLanguageKey, selectedOptionId)
    }
    var lastFocusedLanguageKey by remember(visible) {
        mutableStateOf(sessionInitialLanguageKey.takeIf { key -> languageItems.any { it.key == key } })
    }
    var optionFocusMemory by remember(visible) {
        mutableStateOf<Map<String, String>>(
            sessionInitialOptionTargetId
                ?.let { mapOf(sessionInitialLanguageKey to it) }
                ?: emptyMap()
        )
    }
    var optionEntryLanguageKey by remember(visible) { mutableStateOf(sessionInitialLanguageKey) }
    var styleEntryOptionId by remember(visible) { mutableStateOf(sessionInitialOptionTargetId) }
    var activeRail by remember(visible) { mutableStateOf<OverlayFocusRail?>(null) }
    var activeOptionFocusId by remember(visible) { mutableStateOf<String?>(sessionInitialOptionTargetId) }
    var activeStyleFocusKey by remember(visible) { mutableStateOf<String?>(null) }
    var lastStyleFocusKey by remember(visible) { mutableStateOf(persistedStyleFocusKey) }
    var revealStyleRail by remember(visible) {
        mutableStateOf(sessionInitialSelectedOptionId != null)
    }
    var languageFocusToken by remember(visible) { mutableStateOf(0) }
    var optionFocusToken by remember(visible) { mutableStateOf(0) }
    var styleFocusToken by remember(visible) { mutableStateOf(0) }
    var pendingLanguageFocusKey by remember(visible) { mutableStateOf<String?>(null) }
    var pendingOptionFocusId by remember(visible) { mutableStateOf<String?>(null) }
    var pendingOptionFocusLanguageKey by remember(visible) { mutableStateOf<String?>(null) }
    var pendingStyleFocusKey by remember(visible) { mutableStateOf<String?>(null) }
    val overlaySessionKey = remember(visible) { Any() }
    val languageInitialVisibleIndex = remember(visible, languageItems, sessionInitialLanguageKey) {
        preferredVisibleStartIndex(languageItems.indexOfFirst { it.key == sessionInitialLanguageKey })
    }
    val optionInitialVisibleIndex = remember(visible, sessionInitialSubtitleOptions, sessionInitialSelectedOptionId) {
        preferredVisibleStartIndex(sessionInitialSubtitleOptions.indexOfFirst { it.id == sessionInitialSelectedOptionId })
    }
    val languageListState = remember(overlaySessionKey) {
        LazyListState(firstVisibleItemIndex = languageInitialVisibleIndex)
    }
    val optionListState = remember(overlaySessionKey) {
        LazyListState(firstVisibleItemIndex = optionInitialVisibleIndex)
    }
    val styleListState = remember(overlaySessionKey) {
        LazyListState(firstVisibleItemIndex = 0)
    }
    val languageItemRequesters = rememberFocusRequesterMap(languageItems.map { it.key })
    val optionItemRequesters = rememberFocusRequesterMap(subtitleOptions.map { it.id })
    val styleRequesters = rememberStyleFocusRequesters()
    val optionRailVisible = selectedLanguageKey != SubtitleOffLanguageKey
    val styleRailVisible = optionRailVisible && (revealStyleRail || selectedOptionId != null)
    val languageTargetKey: String? = remember(languageItems, lastFocusedLanguageKey, selectedLanguageKey) {
        lastFocusedLanguageKey?.takeIf { key -> languageItems.any { it.key == key } }
            ?: selectedLanguageKey.takeIf { key -> languageItems.any { it.key == key } }
            ?: languageItems.firstOrNull()?.key
    }
    val optionTargetId: String? = remember(subtitleOptions, optionFocusMemory, selectedLanguageKey, selectedOptionId) {
        selectedOptionId?.takeIf { id -> subtitleOptions.any { it.id == id } }
            ?: optionFocusMemory[selectedLanguageKey]?.takeIf { id -> subtitleOptions.any { it.id == id } }
            ?: subtitleOptions.firstOrNull()?.id
    }
    val styleTargetKey = remember(lastStyleFocusKey) {
        lastStyleFocusKey ?: StyleFocusKey.DelaySet
    }
    val isStyleDisabledByLibass = remember(
        useLibass,
        isUsingMpv,
        subtitleOptions,
        selectedOptionId,
        sessionInternalTracks,
        sessionSelectedInternalIndex,
        sessionSelectedAddonSubtitle
    ) {
        if (!useLibass && !isUsingMpv) return@remember false
        val selectedOption = subtitleOptions.firstOrNull { it.id == selectedOptionId }
        val isAss = when (selectedOption?.kind) {
            SubtitleOptionKind.INTERNAL -> {
                val track = selectedOption.internalTrackIndex?.let { sessionInternalTracks.getOrNull(it) }
                val codec = track?.codec?.lowercase(java.util.Locale.US).orEmpty()
                codec.contains("ass") || codec.contains("ssa") || track?.name?.contains("ASS", ignoreCase = true) == true
            }
            SubtitleOptionKind.ADDON -> {
                val url = selectedOption.addonSubtitle?.url?.lowercase(java.util.Locale.US).orEmpty()
                url.contains(".ass") || url.contains(".ssa")
            }
            null -> {
                val currentInternalTrack = sessionInternalTracks.getOrNull(sessionSelectedInternalIndex)
                val internalCodec = currentInternalTrack?.codec?.lowercase(java.util.Locale.US).orEmpty()
                val addonUrl = sessionSelectedAddonSubtitle?.url?.lowercase(java.util.Locale.US).orEmpty()
                internalCodec.contains("ass") || internalCodec.contains("ssa") ||
                    currentInternalTrack?.name?.contains("ASS", ignoreCase = true) == true ||
                    addonUrl.contains(".ass") || addonUrl.contains(".ssa")
            }
        }
        isAss && (isUsingMpv || useLibass)
    }

    fun requestLanguageFocus(targetKey: String?) {
        val resolvedKey = targetKey
            ?.takeIf { key -> languageItems.any { it.key == key } }
            ?: languageItems.firstOrNull()?.key
            ?: return
        pendingLanguageFocusKey = resolvedKey
        languageFocusToken += 1
    }

    fun requestOptionFocus(
        targetId: String?,
        languageKey: String = selectedLanguageKey,
        reason: String
    ) {
        val resolvedId = targetId ?: subtitleOptions.firstOrNull()?.id
            ?: return
        if (pendingOptionFocusId == resolvedId && pendingOptionFocusLanguageKey == languageKey) {
            Log.d(
                SubtitleFocusTag,
                "option_restore_skip reason=duplicate_pending source=$reason language=$languageKey id=$resolvedId"
            )
            return
        }
        if (
            activeRail == OverlayFocusRail.OPTION &&
            languageKey == selectedLanguageKey &&
            activeOptionFocusId == resolvedId
        ) {
            Log.d(
                SubtitleFocusTag,
                "option_restore_skip reason=already_focused source=$reason language=$languageKey id=$resolvedId"
            )
            return
        }
        pendingOptionFocusId = resolvedId
        pendingOptionFocusLanguageKey = languageKey
        Log.d(
            SubtitleFocusTag,
            "option_restore_schedule source=$reason language=$languageKey id=$resolvedId"
        )
        optionFocusToken += 1
    }

    fun requestStyleFocus(targetKey: String?, reason: String) {
        if (isStyleDisabledByLibass) return
        val requestedKey = targetKey ?: StyleFocusKey.DelaySet
        val resolvedKey = when {
            requestedKey.startsWith("${StyleFocusKey.OutlineColorPrefix}:") && !subtitleStyle.outlineEnabled -> {
                StyleFocusKey.OutlineToggle
            }
            requestedKey.isHdrBrightnessFocusKey() && !isEditingHdrStyle -> StyleFocusKey.OpacityDecrease
            else -> requestedKey
        }.takeIf { key -> styleRequesters.containsKey(key) } ?: StyleFocusKey.DelaySet
        if (
            pendingStyleFocusKey == resolvedKey &&
            activeRail == OverlayFocusRail.STYLE &&
            activeStyleFocusKey == resolvedKey
        ) {
            Log.d(
                SubtitleFocusTag,
                "style_focus_skip reason=already_focused source=$reason key=$resolvedKey"
            )
            return
        }
        pendingStyleFocusKey = resolvedKey
        Log.d(
            SubtitleFocusTag,
            "style_focus_schedule source=$reason key=$resolvedKey"
        )
        styleFocusToken += 1
    }

    fun moveFocusToLanguageRail() {
        requestLanguageFocus(optionEntryLanguageKey)
    }

    fun moveFocusToOptionRail() {
        optionEntryLanguageKey = lastFocusedLanguageKey ?: selectedLanguageKey
        requestOptionFocus(
            targetId = optionTargetId,
            languageKey = selectedLanguageKey,
            reason = "language_to_option"
        )
    }

    fun moveFocusBackToOptionRail() {
        val targetId = styleEntryOptionId?.takeIf { id -> subtitleOptions.any { it.id == id } }
            ?: optionTargetId
        requestOptionFocus(
            targetId = targetId,
            languageKey = selectedLanguageKey,
            reason = "style_to_option"
        )
    }

    fun moveFocusToStyleRail() {
        if (isStyleDisabledByLibass) return
        styleEntryOptionId = optionFocusMemory[selectedLanguageKey]?.takeIf { id ->
            subtitleOptions.any { it.id == id }
        } ?: selectedOptionId?.takeIf { id ->
            subtitleOptions.any { it.id == id }
        } ?: subtitleOptions.firstOrNull()?.id
        revealStyleRail = true
        requestStyleFocus(targetKey = styleTargetKey, reason = "option_to_style")
    }

    PlayerOverlayScaffold(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        captureKeys = false,
        contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 36.dp, bottom = 76.dp)
    ) {
        LaunchedEffect(visible) {
            if (!visible) return@LaunchedEffect
            if (sessionInitialLanguageKey != SubtitleOffLanguageKey && sessionInitialSelectedOptionId != null) {
                Log.d(
                    SubtitleFocusTag,
                    "overlay_open focus=option selectedLanguage=$sessionInitialLanguageKey selectedOption=$sessionInitialSelectedOptionId showStyle=true"
                )
                requestOptionFocus(
                    targetId = sessionInitialSelectedOptionId,
                    languageKey = sessionInitialLanguageKey,
                    reason = "overlay_open"
                )
            } else {
                Log.d(
                    SubtitleFocusTag,
                    "overlay_open focus=language selectedLanguage=$sessionInitialLanguageKey showOption=${sessionInitialLanguageKey != SubtitleOffLanguageKey} showStyle=${sessionInitialSelectedOptionId != null}"
                )
                requestLanguageFocus(sessionInitialLanguageKey)
            }
        }

        LaunchedEffect(visible, sessionInitialLanguageKey, languageItems) {
            if (!visible) return@LaunchedEffect
            val targetIndex = languageItems.indexOfFirst { it.key == sessionInitialLanguageKey }
            if (targetIndex >= 0) {
                languageListState.scrollItemIntoView(targetIndex)
            }
        }

        LaunchedEffect(visible, selectedOptionId) {
            if (visible && selectedOptionId != null) {
                revealStyleRail = true
            }
        }

        LaunchedEffect(visible, styleRailVisible, styleFocusToken, isStyleDisabledByLibass) {
            if (!visible || !styleRailVisible || styleFocusToken <= 0 || isStyleDisabledByLibass) return@LaunchedEffect
            val targetKey = pendingStyleFocusKey ?: return@LaunchedEffect
            val requester = styleRequesters[targetKey] ?: run {
                pendingStyleFocusKey = null
                return@LaunchedEffect
            }
            val targetIndex = styleListIndexForFocusKey(targetKey, hasHdrBrightnessItem = isEditingHdrStyle)
            repeat(8) { attempt ->
                styleListState.scrollItemIntoView(targetIndex)
                Log.d(
                    SubtitleFocusTag,
                    "style_focus_request attempt=$attempt key=$targetKey activeRail=$activeRail activeStyleKey=$activeStyleFocusKey"
                )
                requester.requestFocusAfterFrames(frames = if (attempt == 0) 2 else 1)
                if (activeRail == OverlayFocusRail.STYLE && activeStyleFocusKey == targetKey) {
                    Log.d(
                        SubtitleFocusTag,
                        "style_focus_complete attempt=$attempt key=$targetKey"
                    )
                    pendingStyleFocusKey = null
                    return@LaunchedEffect
                }
            }
            Log.d(
                SubtitleFocusTag,
                "style_focus_timeout key=$targetKey activeStyleKey=$activeStyleFocusKey"
            )
            pendingStyleFocusKey = null
        }

        if (com.nuvio.tv.NuvioCFeatures.TRACK_PANELS_V2 && nuvioCModernPanels()) { // [fork] round 54: new panels in Modern only
            // [fork] round 52 track_panels_v2: one card on the left with Tracks / Style / Language tabs
            val styleDelayFocused = remember { mutableStateOf(false) } // [fork] round 53: Up from Delay -> Style tab
            NuvioCSubtitleTabsPanel(
                modifier = Modifier.align(Alignment.BottomStart),
                visible = visible,
                languageItems = languageItems,
                initialLanguageKey = sessionInitialLanguageKey,
                initialSelectedOptionId = sessionInitialSelectedOptionId,
                buildOptions = { key, selected -> buildSessionOptions(key, selected) },
                onInternalTrackSelected = onInternalTrackSelected,
                onAddonSubtitleSelected = onAddonSubtitleSelected,
                onDisableSubtitles = onDisableSubtitles,
                styleContent = { width ->
                    SubtitleStyleRail(
                        subtitleStyle = subtitleStyle,
                        isEditingHdrStyle = isEditingHdrStyle,
                        subtitleDelayMs = subtitleDelayMs,
                        listState = styleListState,
                        onMoveLeft = {},
                        focusRequesters = styleRequesters,
                        onStyleFocused = { key ->
                            activeRail = OverlayFocusRail.STYLE
                            activeStyleFocusKey = key
                            lastStyleFocusKey = key
                            persistedStyleFocusKey = key
                            styleDelayFocused.value = key == StyleFocusKey.DelaySet
                        },
                        onEvent = onEvent,
                        isStyleDisabledByLibass = isStyleDisabledByLibass,
                        nuvioCWidth = width,
                        nuvioCTitle = false
                    )
                },
                styleEntry = requireNotNull(styleRequesters[StyleFocusKey.DelaySet]),
                styleEntryFocused = styleDelayFocused
            )
        } else
        Column(verticalArrangement = Arrangement.Bottom) {
            Text(
                text = stringResource(R.string.subtitle_dialog_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = NuvioTheme.spacing.md)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SubtitleLanguageRail(
                    items = languageItems,
                    selectedLanguageKey = selectedLanguageKey,
                    listState = languageListState,
                    itemFocusRequesters = languageItemRequesters,
                    focusTargetKey = pendingLanguageFocusKey,
                    focusToken = languageFocusToken,
                    onFocusRequestConsumed = {
                        pendingLanguageFocusKey = null
                    },
                    onMoveRight = if (optionRailVisible && subtitleOptions.isNotEmpty()) ::moveFocusToOptionRail else null,
                    onLanguageSelected = { languageKey ->
                        Log.d(
                            SubtitleFocusTag,
                            "language_select key=$languageKey previous=$selectedLanguageKey"
                        )
                        selectedLanguageKey = languageKey
                        lastFocusedLanguageKey = languageKey
                        optionEntryLanguageKey = languageKey
                        activeRail = OverlayFocusRail.LANGUAGE
                        if (languageKey == SubtitleOffLanguageKey) {
                            selectedOptionId = null
                            revealStyleRail = false
                            onDisableSubtitles()
                        } else {
                            val nextOptions = buildSessionOptions(languageKey, selectedOptionId)
                            val nextSelectedId = selectedOptionId?.takeIf { id ->
                                nextOptions.any { it.id == id }
                            }
                            selectedOptionId = nextSelectedId
                            val nextTargetId = nextSelectedId
                                ?: optionFocusMemory[languageKey]?.takeIf { id ->
                                    nextOptions.any { it.id == id }
                                }
                                ?: nextOptions.firstOrNull()?.id
                            if (nextTargetId != null) {
                                optionFocusMemory = optionFocusMemory + (languageKey to nextTargetId)
                            }
                            revealStyleRail = nextSelectedId != null
                            requestOptionFocus(
                                targetId = nextTargetId,
                                languageKey = languageKey,
                                reason = "language_select"
                            )
                        }
                    },
                    onLanguageFocused = { key ->
                        lastFocusedLanguageKey = key
                        optionEntryLanguageKey = key
                        activeRail = OverlayFocusRail.LANGUAGE
                        activeStyleFocusKey = null
                    }
                )

                RailFadeIn(visible = optionRailVisible) {
                    SubtitleOptionsRail(
                        selectedLanguageKey = selectedLanguageKey,
                        options = subtitleOptions,
                        isLoadingAddons = sessionIsLoadingAddons,
                        listState = optionListState,
                        itemFocusRequesters = optionItemRequesters,
                        focusTargetId = pendingOptionFocusId,
                        focusLanguageKey = pendingOptionFocusLanguageKey,
                        focusToken = optionFocusToken,
                        onFocusRequestConsumed = {
                            pendingOptionFocusId = null
                            pendingOptionFocusLanguageKey = null
                        },
                        onOptionFocused = {
                            optionFocusMemory = optionFocusMemory + (selectedLanguageKey to it)
                            styleEntryOptionId = it
                            activeOptionFocusId = it
                            activeRail = OverlayFocusRail.OPTION
                            activeStyleFocusKey = null
                        },
                        onMoveLeft = ::moveFocusToLanguageRail,
                        onMoveRight = ::moveFocusToStyleRail,
                        onInternalTrackSelected = { optionId, trackIndex ->
                            selectedOptionId = optionId
                            optionFocusMemory = optionFocusMemory + (selectedLanguageKey to optionId)
                            styleEntryOptionId = optionId
                            activeOptionFocusId = optionId
                            activeRail = OverlayFocusRail.OPTION
                            onInternalTrackSelected(trackIndex)
                            revealStyleRail = true
                        },
                        onAddonSubtitleSelected = { optionId, subtitle ->
                            selectedOptionId = optionId
                            optionFocusMemory = optionFocusMemory + (selectedLanguageKey to optionId)
                            styleEntryOptionId = optionId
                            activeOptionFocusId = optionId
                            activeRail = OverlayFocusRail.OPTION
                            onAddonSubtitleSelected(subtitle)
                            revealStyleRail = true
                        },
                        onOptionLongPressed = { optionId ->
                            activeOptionFocusId = optionId
                            activeRail = OverlayFocusRail.OPTION
                            if (optionId == selectedOptionId) {
                                selectedOptionId = null
                                revealStyleRail = false
                                onDisableSubtitles()
                            }
                        }
                    )
                }

                RailFadeIn(visible = styleRailVisible) {
                    SubtitleStyleRail(
                        subtitleStyle = subtitleStyle,
                        isEditingHdrStyle = isEditingHdrStyle,
                        subtitleDelayMs = subtitleDelayMs,
                        listState = styleListState,
                        onMoveLeft = ::moveFocusBackToOptionRail,
                        focusRequesters = styleRequesters,
                        onStyleFocused = {
                            activeRail = OverlayFocusRail.STYLE
                            activeStyleFocusKey = it
                            pendingStyleFocusKey = null
                            lastStyleFocusKey = it
                            persistedStyleFocusKey = it
                        },
                        onEvent = onEvent,
                        isStyleDisabledByLibass = isStyleDisabledByLibass
                    )
                }
            }
        }
    }
}

@Composable
private fun RailFadeIn(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    if (!visible) return

    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.snapTo(0f)
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = RailFadeDurationMs,
                easing = FastOutLinearInEasing
            )
        )
    }

    Box(modifier = Modifier.graphicsLayer(alpha = alpha.value)) {
        content()
    }
}

@Composable
private fun SubtitleLanguageRail(
    items: List<SubtitleLanguageRailItem>,
    selectedLanguageKey: String,
    listState: LazyListState,
    itemFocusRequesters: Map<String, FocusRequester>,
    focusTargetKey: String?,
    focusToken: Int,
    onFocusRequestConsumed: () -> Unit,
    onMoveRight: (() -> Unit)?,
    onLanguageSelected: (String) -> Unit,
    onLanguageFocused: (String) -> Unit
) {
    LaunchedEffect(focusToken) {
        if (focusToken <= 0) return@LaunchedEffect
        val targetKey = focusTargetKey ?: return@LaunchedEffect
        val targetIndex = items.indexOfFirst { it.key == targetKey }
            .takeIf { it >= 0 }
            ?: run {
                onFocusRequestConsumed()
                return@LaunchedEffect
            }
        Log.d(
            SubtitleFocusTag,
            "language_restore_request key=$targetKey index=$targetIndex selected=$selectedLanguageKey firstVisible=${listState.firstVisibleItemIndex}"
        )
        listState.scrollItemIntoView(targetIndex)
        itemFocusRequesters[targetKey]?.requestFocusAfterFrames()
        Log.d(
            SubtitleFocusTag,
            "language_restore_complete key=$targetKey index=$targetIndex firstVisible=${listState.firstVisibleItemIndex}"
        )
        onFocusRequestConsumed()
    }

    RailColumn(width = 200.dp, title = stringResource(R.string.subtitle_tab_languages)) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs),
            contentPadding = PaddingValues(top = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.sm),
            modifier = Modifier
                .heightIn(max = 720.dp)
        ) {
            items(items = items, key = { item -> item.key }) { item ->
                SubtitleLanguageCard(
                    item = item,
                    isSelected = item.key == selectedLanguageKey,
                    onClick = { onLanguageSelected(item.key) },
                    focusRequester = itemFocusRequesters[item.key],
                    onMoveRight = onMoveRight,
                    onFocused = { onLanguageFocused(item.key) }
                )
            }
        }
    }
}

@Composable
private fun SubtitleOptionsRail(
    selectedLanguageKey: String,
    options: List<SubtitleOptionRailItem>,
    isLoadingAddons: Boolean,
    listState: LazyListState,
    itemFocusRequesters: Map<String, FocusRequester>,
    focusTargetId: String?,
    focusLanguageKey: String?,
    focusToken: Int,
    onFocusRequestConsumed: () -> Unit,
    onOptionFocused: (String) -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onInternalTrackSelected: (String, Int) -> Unit,
    onAddonSubtitleSelected: (String, Subtitle) -> Unit,
    onOptionLongPressed: (String) -> Unit
) {
    LaunchedEffect(focusToken) {
        if (focusToken <= 0) return@LaunchedEffect
        val requestLanguageKey = focusLanguageKey
            ?: run {
                onFocusRequestConsumed()
                return@LaunchedEffect
            }
        if (requestLanguageKey != selectedLanguageKey) {
            Log.d(
                SubtitleFocusTag,
                "option_restore_drop reason=language_mismatch requestLanguage=$requestLanguageKey selectedLanguage=$selectedLanguageKey target=$focusTargetId"
            )
            onFocusRequestConsumed()
            return@LaunchedEffect
        }
        val targetId = focusTargetId
            ?.takeIf { id -> options.any { it.id == id } }
            ?: run {
                onFocusRequestConsumed()
                return@LaunchedEffect
            }
        val targetIndex = options.indexOfFirst { it.id == targetId }
            .takeIf { it >= 0 }
            ?: run {
                onFocusRequestConsumed()
                return@LaunchedEffect
            }
        Log.d(
            SubtitleFocusTag,
            "option_restore_request language=$selectedLanguageKey id=$targetId index=$targetIndex firstVisible=${listState.firstVisibleItemIndex}"
        )
        listState.scrollItemIntoView(targetIndex)
        itemFocusRequesters[targetId]?.requestFocusAfterFrames()
        Log.d(
            SubtitleFocusTag,
            "option_restore_complete language=$selectedLanguageKey id=$targetId index=$targetIndex firstVisible=${listState.firstVisibleItemIndex}"
        )
        onFocusRequestConsumed()
    }

    RailColumn(width = 300.dp, title = stringResource(R.string.subtitle_dialog_title)) {
        when {
            selectedLanguageKey == SubtitleOffLanguageKey -> {
                OverlayEmptyCard(text = stringResource(R.string.subtitle_none))
            }

            options.isEmpty() && isLoadingAddons -> {
                OverlayLoadingCard(text = stringResource(R.string.subtitle_loading_addon))
            }

            options.isEmpty() -> {
                OverlayEmptyCard(text = stringResource(R.string.subtitle_no_addon))
            }

            else -> {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs),
                    contentPadding = PaddingValues(top = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.sm),
                    modifier = Modifier
                        .heightIn(max = 720.dp)
                ) {
                    items(items = options, key = { option -> option.id }) { option ->
                        SubtitleOptionCard(
                            item = option,
                            focusRequester = itemFocusRequesters[option.id],
                            onMoveLeft = onMoveLeft,
                            onMoveRight = onMoveRight,
                            onFocused = { onOptionFocused(option.id) },
                            onClick = {
                                when (option.kind) {
                                    SubtitleOptionKind.INTERNAL -> {
                                        option.internalTrackIndex?.let { trackIndex ->
                                            onInternalTrackSelected(option.id, trackIndex)
                                        }
                                    }

                                    SubtitleOptionKind.ADDON -> {
                                        option.addonSubtitle?.let { subtitle ->
                                            onAddonSubtitleSelected(option.id, subtitle)
                                        }
                                    }
                                }
                            },
                            onLongClick = { onOptionLongPressed(option.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleStyleRail(
    subtitleStyle: SubtitleStyleSettings,
    isEditingHdrStyle: Boolean,
    subtitleDelayMs: Int,
    listState: LazyListState,
    onMoveLeft: () -> Unit,
    focusRequesters: Map<String, FocusRequester>,
    onStyleFocused: (String) -> Unit,
    onEvent: (PlayerEvent) -> Unit,
    isStyleDisabledByLibass: Boolean = false,
    nuvioCWidth: androidx.compose.ui.unit.Dp = 280.dp, // [fork] round 52 track_panels_v2: full card width
    nuvioCTitle: Boolean = true // [fork] round 52: the tab already names it
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moveLeftKey = if (isRtl) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT
    val dispatchStyleEvent: (PlayerEvent) -> Unit = { event ->
        if (!isStyleDisabledByLibass) {
            onEvent(event)
        }
    }
    val styleCardModifier = if (isStyleDisabledByLibass) Modifier.alpha(0.35f) else Modifier
    val styleRailModifier = if (isStyleDisabledByLibass) Modifier.focusProperties { canFocus = false } else Modifier
    RailColumn(
        width = nuvioCWidth,
        title = if (nuvioCTitle) stringResource(R.string.subtitle_style_title) else null,
        modifier = styleRailModifier
    ) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = com.nuvio.tv.ui.components.nuvioCFocusRoomPadding(horizontal = com.nuvio.tv.ui.components.NUVIO_C_FOCUS_ROOM, top = com.nuvio.tv.ui.components.NUVIO_C_FOCUS_ROOM, officialBottom = NuvioTheme.spacing.sm), // [fork] focus room
            modifier = Modifier
                .nuvioCFocusRoom(horizontal = com.nuvio.tv.ui.components.NUVIO_C_FOCUS_ROOM, top = com.nuvio.tv.ui.components.NUVIO_C_FOCUS_ROOM) // [fork] focus room
                .heightIn(max = 720.dp)
        ) {
            item {
                Card(
                    onClick = { dispatchStyleEvent(PlayerEvent.OnShowSubtitleDelayOverlay) },
                    colors = overlayCardColors(selected = false),
                    shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(styleCardModifier)
                        .focusRequester(requireNotNull(focusRequesters[StyleFocusKey.DelaySet]))
                        .onPreviewKeyEvent { event ->
                            when (event.nativeKeyEvent.keyCode) {
                                moveLeftKey -> {
                                    when (event.nativeKeyEvent.action) {
                                        android.view.KeyEvent.ACTION_DOWN -> {
                                            onMoveLeft()
                                            true
                                        }

                                        android.view.KeyEvent.ACTION_UP -> true
                                        else -> false
                                    }
                                }

                                else -> false
                            }
                        }
                        .onFocusChanged { if (it.isFocused) onStyleFocused(StyleFocusKey.DelaySet) },
                    scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = NuvioTheme.spacing.md, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.subtitle_tab_delay),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Text(
                            text = formatSubtitleDelay(subtitleDelayMs).keepLtr(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            item {
                SubtitleFontStyleCard(
                    subtitleStyle = subtitleStyle,
                    focusRequesters = focusRequesters,
                    onMoveLeft = onMoveLeft,
                    onFocused = onStyleFocused,
                    onEvent = dispatchStyleEvent,
                    modifier = styleCardModifier
                )
            }
            item {
                OverlaySectionCard(
                    title = stringResource(R.string.subtitle_style_font_size),
                    modifier = styleCardModifier
                ) {
                    StepperRow(
                        value = "${subtitleStyle.size}%",
                        onDecrease = { dispatchStyleEvent(PlayerEvent.OnSetSubtitleSize(subtitleStyle.size - 10)) },
                        onIncrease = { dispatchStyleEvent(PlayerEvent.OnSetSubtitleSize(subtitleStyle.size + 10)) },
                        onMoveLeft = onMoveLeft,
                        decrementFocusRequester = focusRequesters[StyleFocusKey.FontSizeDecrease],
                        incrementFocusRequester = focusRequesters[StyleFocusKey.FontSizeIncrease],
                        decrementFocusKey = StyleFocusKey.FontSizeDecrease,
                        incrementFocusKey = StyleFocusKey.FontSizeIncrease,
                        onFocusChanged = onStyleFocused
                    )
                }
            }
            item {
                OverlaySectionCard(
                    title = stringResource(R.string.subtitle_style_bold),
                    modifier = styleCardModifier
                ) {
                    ToggleChip(
                        label = if (subtitleStyle.bold) stringResource(R.string.subtitle_style_on) else stringResource(R.string.subtitle_style_off),
                        isEnabled = subtitleStyle.bold,
                        onMoveLeft = onMoveLeft,
                        focusRequester = focusRequesters[StyleFocusKey.Bold],
                        focusKey = StyleFocusKey.Bold,
                        onFocused = onStyleFocused,
                        onClick = { dispatchStyleEvent(PlayerEvent.OnSetSubtitleBold(!subtitleStyle.bold)) }
                    )
                }
            }
            item {
                OverlaySectionCard(
                    title = if (isEditingHdrStyle) {
                        stringResource(R.string.subtitle_style_text_color_hdr)
                    } else {
                        stringResource(R.string.subtitle_style_text_color)
                    },
                    modifier = styleCardModifier
                ) {
                    ColorChipRow(
                        colors = SubtitleTextColorPresets,
                        selectedColor = subtitleStyle.textColor,
                        onMoveLeft = onMoveLeft,
                        focusRequesters = focusRequesters,
                        focusKeyPrefix = StyleFocusKey.TextColorPrefix,
                        onFocused = onStyleFocused,
                        onColorSelected = { color -> dispatchStyleEvent(PlayerEvent.OnSetSubtitleTextColor(color)) }
                    )
                }
            }
            item {
                OverlaySectionCard(
                    title = if (isEditingHdrStyle) {
                        stringResource(R.string.subtitle_style_text_opacity_hdr)
                    } else {
                        stringResource(R.string.subtitle_style_text_opacity)
                    },
                    modifier = styleCardModifier
                ) {
                    val currentColor = Color(subtitleStyle.textColor)
                    val currentAlphaPercent = (currentColor.alpha * 100f).roundToInt().coerceIn(0, 100)
                    StepperRow(
                        value = "$currentAlphaPercent%",
                        onDecrease = {
                            val newAlpha = (currentAlphaPercent - 10).coerceAtLeast(0) / 100f
                            dispatchStyleEvent(PlayerEvent.OnSetSubtitleTextColor(currentColor.copy(alpha = newAlpha).toArgb()))
                        },
                        onIncrease = {
                            val newAlpha = (currentAlphaPercent + 10).coerceAtMost(100) / 100f
                            dispatchStyleEvent(PlayerEvent.OnSetSubtitleTextColor(currentColor.copy(alpha = newAlpha).toArgb()))
                        },
                        onMoveLeft = onMoveLeft,
                        decrementFocusRequester = focusRequesters[StyleFocusKey.OpacityDecrease],
                        incrementFocusRequester = focusRequesters[StyleFocusKey.OpacityIncrease],
                        decrementFocusKey = StyleFocusKey.OpacityDecrease,
                        incrementFocusKey = StyleFocusKey.OpacityIncrease,
                        onFocusChanged = onStyleFocused
                    )
                }
            }
            if (isEditingHdrStyle) {
                item {
                    OverlaySectionCard(
                        title = stringResource(R.string.subtitle_style_brightness_hdr),
                        modifier = styleCardModifier
                    ) {
                        StepperRow(
                            value = "${subtitleStyle.hdrBrightness}%",
                            onDecrease = {
                                dispatchStyleEvent(PlayerEvent.OnSetSubtitleHdrBrightness(subtitleStyle.hdrBrightness - 10))
                            },
                            onIncrease = {
                                dispatchStyleEvent(PlayerEvent.OnSetSubtitleHdrBrightness(subtitleStyle.hdrBrightness + 10))
                            },
                            onMoveLeft = onMoveLeft,
                            decrementFocusRequester = focusRequesters[StyleFocusKey.HdrBrightnessDecrease],
                            incrementFocusRequester = focusRequesters[StyleFocusKey.HdrBrightnessIncrease],
                            decrementFocusKey = StyleFocusKey.HdrBrightnessDecrease,
                            incrementFocusKey = StyleFocusKey.HdrBrightnessIncrease,
                            onFocusChanged = onStyleFocused
                        )
                    }
                }
            }
            item {
                SubtitleBackgroundStyleCard(
                    subtitleStyle = subtitleStyle,
                    focusRequesters = focusRequesters,
                    onMoveLeft = onMoveLeft,
                    onFocused = onStyleFocused,
                    onEvent = dispatchStyleEvent,
                    modifier = styleCardModifier
                )
            }
            item {
                OverlaySectionCard(
                    title = stringResource(R.string.subtitle_style_outline),
                    modifier = styleCardModifier
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                        ToggleChip(
                            label = if (subtitleStyle.outlineEnabled) stringResource(R.string.subtitle_style_on) else stringResource(R.string.subtitle_style_off),
                            isEnabled = subtitleStyle.outlineEnabled,
                            onMoveLeft = onMoveLeft,
                            focusRequester = focusRequesters[StyleFocusKey.OutlineToggle],
                            focusKey = StyleFocusKey.OutlineToggle,
                            onFocused = onStyleFocused,
                            onClick = { dispatchStyleEvent(PlayerEvent.OnSetSubtitleOutlineEnabled(!subtitleStyle.outlineEnabled)) }
                        )
                        ColorChipRow(
                            colors = SubtitleOutlineColorPresets,
                            selectedColor = subtitleStyle.outlineColor,
                            enabled = subtitleStyle.outlineEnabled,
                            onMoveLeft = onMoveLeft,
                            focusRequesters = focusRequesters,
                            focusKeyPrefix = StyleFocusKey.OutlineColorPrefix,
                            onFocused = onStyleFocused,
                            onColorSelected = { color ->
                                if (!subtitleStyle.outlineEnabled) {
                                    dispatchStyleEvent(PlayerEvent.OnSetSubtitleOutlineEnabled(true))
                                }
                                dispatchStyleEvent(PlayerEvent.OnSetSubtitleOutlineColor(color))
                            }
                        )
                        if (com.nuvio.tv.NuvioCFeatures.SUBTITLE_OUTLINE_WIDTH_ROW && subtitleStyle.outlineEnabled) {
                            // [fork] round 46: Outline width came back (lost when round 44 took official's Style tab)
                            if (NuvioCPanelLook.v3) RailStepperLabel(stringResource(R.string.nuvio_c_sub_outline_width)) // [fork] round 53
                            StepperRow(
                                value = subtitleStyle.outlineWidth.toString(),
                                onDecrease = { dispatchStyleEvent(PlayerEvent.OnSetSubtitleOutlineWidth((subtitleStyle.outlineWidth - 1).coerceIn(1, 5))) },
                                onIncrease = { dispatchStyleEvent(PlayerEvent.OnSetSubtitleOutlineWidth((subtitleStyle.outlineWidth + 1).coerceIn(1, 5))) },
                                onMoveLeft = onMoveLeft,
                                decrementFocusRequester = focusRequesters[StyleFocusKey.OutlineWidthDecrease],
                                incrementFocusRequester = focusRequesters[StyleFocusKey.OutlineWidthIncrease],
                                decrementFocusKey = StyleFocusKey.OutlineWidthDecrease,
                                incrementFocusKey = StyleFocusKey.OutlineWidthIncrease,
                                onFocusChanged = onStyleFocused
                            )
                        }
                    }
                }
            }
            if (!com.nuvio.tv.NuvioCFeatures.SUBTITLE_CLASSIC_RENDERER) item { // [fork] round 44: shadow needs official's new renderer
                SubtitleShadowStyleCard(
                    subtitleStyle = subtitleStyle,
                    focusRequesters = focusRequesters,
                    onMoveLeft = onMoveLeft,
                    onFocused = onStyleFocused,
                    onEvent = dispatchStyleEvent,
                    modifier = styleCardModifier
                )
            }
            item {
                OverlaySectionCard(
                    title = stringResource(R.string.subtitle_style_bottom_offset),
                    modifier = styleCardModifier
                ) {
                    StepperRow(
                        value = subtitleStyle.verticalOffset.toString(),
                        onDecrease = { dispatchStyleEvent(PlayerEvent.OnSetSubtitleVerticalOffset(subtitleStyle.verticalOffset - 5)) },
                        onIncrease = { dispatchStyleEvent(PlayerEvent.OnSetSubtitleVerticalOffset(subtitleStyle.verticalOffset + 5)) },
                        onMoveLeft = onMoveLeft,
                        decrementFocusRequester = focusRequesters[StyleFocusKey.OffsetDecrease],
                        incrementFocusRequester = focusRequesters[StyleFocusKey.OffsetIncrease],
                        decrementFocusKey = StyleFocusKey.OffsetDecrease,
                        incrementFocusKey = StyleFocusKey.OffsetIncrease,
                        onFocusChanged = onStyleFocused
                    )
                }
            }
            item {
                Card(
                    onClick = { dispatchStyleEvent(PlayerEvent.OnResetSubtitleDefaults) },
                    colors = overlayCardColors(selected = false),
                    shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
                    modifier = Modifier
                        .then(styleCardModifier)
                        .focusRequester(requireNotNull(focusRequesters[StyleFocusKey.Reset]))
                        .onPreviewKeyEvent { event ->
                            when (event.nativeKeyEvent.keyCode) {
                                moveLeftKey -> {
                                    when (event.nativeKeyEvent.action) {
                                        android.view.KeyEvent.ACTION_DOWN -> {
                                            onMoveLeft()
                                            true
                                        }

                                        android.view.KeyEvent.ACTION_UP -> true
                                        else -> false
                                    }
                                }

                                else -> false
                            }
                        }
                        .onFocusChanged { if (it.isFocused) onStyleFocused(StyleFocusKey.Reset) },
                    scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_reset_defaults),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RailColumn(
    width: androidx.compose.ui.unit.Dp,
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.width(width),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
    ) {
        if (title != null) Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = NuvioTheme.colors.TextTertiary
        )
        content()
    }
}

@Composable
private fun SubtitleLanguageCard(
    item: SubtitleLanguageRailItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    onMoveRight: (() -> Unit)?,
    onFocused: () -> Unit
) {
    val textColor = if (isSelected) NuvioTheme.colors.OnSecondary else Color.White
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moveToOptionsKey = if (isRtl) android.view.KeyEvent.KEYCODE_DPAD_LEFT else android.view.KeyEvent.KEYCODE_DPAD_RIGHT

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = isSelected
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    moveToOptionsKey -> {
                        val moveRight = onMoveRight ?: return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.action) {
                            android.view.KeyEvent.ACTION_DOWN -> {
                                moveRight()
                                true
                            }

                            android.view.KeyEvent.ACTION_UP -> true
                            else -> false
                        }
                    }

                    else -> false
                }
            }
            .onFocusChanged {
                if (it.isFocused) {
                    Log.d(
                        SubtitleFocusTag,
                        "language_focused key=${item.key} label=${item.label}"
                    )
                    onFocused()
                }
            },
        colors = overlayCardColors(selected = isSelected),
        shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        border = overlayCardBorder(),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (item.count > 0) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
                CountBadge(count = item.count, selected = isSelected)
            }
        }
    }
}

@Composable
private fun SubtitleOptionCard(
    item: SubtitleOptionRailItem,
    focusRequester: FocusRequester?,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val titleColor = if (item.isSelected) NuvioTheme.colors.OnSecondary else Color.White
    val metaColor = if (item.isSelected) {
        NuvioTheme.colors.OnSecondary.copy(alpha = 0.72f)
    } else {
        NuvioTheme.colors.TextTertiary
    }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moveLeftKey = if (isRtl) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT
    val moveRightKey = if (isRtl) android.view.KeyEvent.KEYCODE_DPAD_LEFT else android.view.KeyEvent.KEYCODE_DPAD_RIGHT
    var nuvioCFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) } // [fork] round 40 subtitle_meta_marquee

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                this.selected = item.isSelected
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    moveLeftKey -> {
                        when (event.nativeKeyEvent.action) {
                            android.view.KeyEvent.ACTION_DOWN -> {
                                onMoveLeft()
                                true
                            }

                            android.view.KeyEvent.ACTION_UP -> true
                            else -> false
                        }
                    }

                    moveRightKey -> {
                        when (event.nativeKeyEvent.action) {
                            android.view.KeyEvent.ACTION_DOWN -> {
                                onMoveRight()
                                true
                            }

                            android.view.KeyEvent.ACTION_UP -> true
                            else -> false
                        }
                    }

                    else -> false
                }
            }
            .onFocusChanged {
                nuvioCFocused = it.isFocused // [fork] round 40 subtitle_meta_marquee
                if (it.isFocused) {
                    Log.d(
                        SubtitleFocusTag,
                        "option_focused id=${item.id} title=${item.title} selected=${item.isSelected}"
                    )
                    onFocused()
                }
            },
        colors = overlayCardColors(selected = item.isSelected),
        shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        border = overlayCardBorder(),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NuvioTheme.spacing.md, vertical = if (com.nuvio.tv.NuvioCFeatures.SUBTITLE_PANEL_CLEAN) 10.dp else 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (com.nuvio.tv.NuvioCFeatures.SUBTITLE_PANEL_CLEAN) {
                // [fork] round 39 subtitle_panel_clean: one line (language, then the source chip), an optional
                // release name under it on one line, nothing else. Rows are about half as tall as before.
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = titleColor,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        SourceChip(label = item.sourceLabel, selected = item.isSelected)
                        item.addonSubtitle?.let { AutoSyncedChip(it.url, item.isSelected) } // AutoSync hook
                    }
                    if (!item.meta.isNullOrBlank()) {
                        // [fork] round 40 subtitle_meta_marquee: one line with an ellipsis, scrolling only on the focused row.
                        if (com.nuvio.tv.NuvioCFeatures.SUBTITLE_META_MARQUEE) com.nuvio.tv.ui.components.FocusMarqueeText(
                            text = item.meta.replace('\n', ' ').trim(),
                            focused = nuvioCFocused,
                            style = MaterialTheme.typography.bodySmall,
                            color = metaColor
                        ) else Text(
                            text = item.meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = metaColor,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            } else
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row( // AutoSync hook: room for the "Auto synced" chip beside the source
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SourceChip(label = item.sourceLabel, selected = item.isSelected)
                    item.addonSubtitle?.let { AutoSyncedChip(it.url, item.isSelected) } // AutoSync hook
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor
                )
                if (!item.meta.isNullOrBlank()) {
                    Text(
                        text = item.meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = metaColor
                    )
                }
            }
            if (item.isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NuvioTheme.colors.OnSecondary
                )
            }
        }
    }
}

@Composable
private fun CountBadge(
    count: Int,
    selected: Boolean
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) {
                    Color.White.copy(alpha = 0.18f)
                } else {
                    NuvioTheme.colors.Secondary.copy(alpha = 0.85f)
                },
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = NuvioTheme.spacing.sm, vertical = 3.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.OnSecondary
        )
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean = false) {
    Box(
        modifier = Modifier
            .background(
                if (selected) {
                    NuvioTheme.colors.OnSecondary.copy(alpha = 0.14f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                },
                RoundedCornerShape(999.dp)
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = NuvioTheme.spacing.hairline,
                        color = NuvioTheme.colors.OnSecondary.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(999.dp)
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = NuvioTheme.spacing.sm, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                NuvioTheme.colors.OnSecondary.copy(alpha = 0.9f)
            } else {
                Color.White.copy(alpha = 0.78f)
            }
        )
    }
}

@Composable
private fun OverlayLoadingCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LoadingIndicator(modifier = Modifier.size(NuvioTheme.spacing.xl))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextTertiary
            )
        }
    }
}

@Composable
private fun OverlayEmptyCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.colors.TextTertiary
        )
    }
}

@Composable
internal fun OverlaySectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        content()
    }
}

@Composable
internal fun StepperRow(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    valueWidth: Dp = 84.dp,
    valueFontFamily: FontFamily? = null,
    onMoveLeft: (() -> Unit)? = null,
    decrementFocusRequester: FocusRequester? = null,
    incrementFocusRequester: FocusRequester? = null,
    decrementFocusKey: String? = null,
    incrementFocusKey: String? = null,
    onFocusChanged: ((String) -> Unit)? = null
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            icon = Icons.Default.Remove,
            contentDescription = stringResource(R.string.cd_decrease),
            onClick = onDecrease,
            onMoveLeft = onMoveLeft,
            focusRequester = decrementFocusRequester,
            focusKey = decrementFocusKey,
            onFocused = onFocusChanged
        )
        Box(
            modifier = Modifier
                .width(valueWidth)
                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(NuvioTheme.radii.md))
                .padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontFamily = valueFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StepperButton(
            icon = Icons.Default.Add,
            contentDescription = stringResource(R.string.cd_increase),
            onClick = onIncrease,
            focusRequester = incrementFocusRequester,
            focusKey = incrementFocusKey,
            onFocused = onFocusChanged
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onMoveLeft: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    focusKey: String? = null,
    onFocused: ((String) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moveLeftKey = if (isRtl) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    moveLeftKey -> {
                        val moveLeft = onMoveLeft ?: return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.action) {
                            android.view.KeyEvent.ACTION_DOWN -> {
                                moveLeft()
                                true
                            }

                            android.view.KeyEvent.ACTION_UP -> true
                            else -> false
                        }
                    }

                    else -> false
                }
            }
            .then(
                if (isFocused) {
                    Modifier.border(
                        NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                        RoundedCornerShape(NuvioTheme.radii.md)
                    )
                } else {
                    Modifier
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && focusKey != null) {
                    onFocused?.invoke(focusKey)
                }
            },
        colors = IconButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContentColor = Color.White
        ),
        shape = IconButtonDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.md)),
        scale = IconButtonDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

@Composable
internal fun ToggleChip(
    label: String,
    isEnabled: Boolean,
    onMoveLeft: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    focusKey: String? = null,
    onFocused: ((String) -> Unit)? = null,
    onClick: () -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moveLeftKey = if (isRtl) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT
    val switchSemantics = Modifier.semantics {
        role = Role.Switch
        toggleableState = if (isEnabled) ToggleableState.On else ToggleableState.Off
    }
    Card(
        onClick = onClick,
        modifier = if (focusRequester != null) {
            Modifier
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    when (event.nativeKeyEvent.keyCode) {
                        moveLeftKey -> {
                            val moveLeft = onMoveLeft ?: return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.action) {
                                android.view.KeyEvent.ACTION_DOWN -> {
                                    moveLeft()
                                    true
                                }

                                android.view.KeyEvent.ACTION_UP -> true
                                else -> false
                            }
                        }

                        else -> false
                    }
                }
                .onFocusChanged {
                    if (it.isFocused && focusKey != null) onFocused?.invoke(focusKey)
                }
                .then(switchSemantics)
        } else {
            Modifier
                .onPreviewKeyEvent { event ->
                    when (event.nativeKeyEvent.keyCode) {
                        moveLeftKey -> {
                            val moveLeft = onMoveLeft ?: return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.action) {
                                android.view.KeyEvent.ACTION_DOWN -> {
                                    moveLeft()
                                    true
                                }

                                android.view.KeyEvent.ACTION_UP -> true
                                else -> false
                            }
                        }

                        else -> false
                    }
                }
                .onFocusChanged {
                    if (it.isFocused && focusKey != null) onFocused?.invoke(focusKey)
                }
                .then(switchSemantics)
        },
        colors = overlayCardColors(selected = isEnabled),
        shape = CardDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isEnabled) NuvioTheme.colors.OnSecondary else Color.White,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.md, vertical = NuvioTheme.spacing.sm)
        )
    }
}

@Composable
internal fun ColorChipRow(
    colors: List<Color>,
    selectedColor: Int,
    enabled: Boolean = true,
    onMoveLeft: (() -> Unit)? = null,
    focusRequesters: Map<String, FocusRequester>,
    focusKeyPrefix: String,
    onFocused: ((String) -> Unit)? = null,
    onColorSelected: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { color ->
            val focusKey = "$focusKeyPrefix:${color.toArgb()}"
            ColorChip(
                color = if (enabled) color else color.copy(alpha = 0.35f),
                isSelected = color.toArgb() == selectedColor,
                enabled = enabled,
                onMoveLeft = if (color == colors.firstOrNull()) onMoveLeft else null,
                focusRequester = focusRequesters[focusKey],
                focusKey = focusKey,
                onFocused = onFocused,
                onClick = { onColorSelected(color.toArgb()) }
            )
        }
    }
}

@Composable
private fun ColorChip(
    color: Color,
    isSelected: Boolean,
    enabled: Boolean,
    onMoveLeft: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    focusKey: String? = null,
    onFocused: ((String) -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val moveLeftKey = if (isRtl) android.view.KeyEvent.KEYCODE_DPAD_RIGHT else android.view.KeyEvent.KEYCODE_DPAD_LEFT

    val swatchLabel = stringResource(R.string.cd_color_swatch, overlaySwatchColorLabel(color))
    Card(
        onClick = { if (enabled) onClick() },
        colors = CardDefaults.colors(
            containerColor = color,
            focusedContainerColor = color
        ),
        modifier = Modifier
            .size(NuvioTheme.spacing.xxl)
            .clearAndSetSemantics {
                contentDescription = swatchLabel
                role = Role.RadioButton
                this.selected = isSelected
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    moveLeftKey -> {
                        val moveLeft = onMoveLeft ?: return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.action) {
                            android.view.KeyEvent.ACTION_DOWN -> {
                                moveLeft()
                                true
                            }

                            android.view.KeyEvent.ACTION_UP -> true
                            else -> false
                        }
                    }

                    else -> false
                }
            }
            .then(
                if (com.nuvio.tv.ui.components.nuvioCFocusRingShapeOn) Modifier.nuvioCSwatchRing(isFocused, isSelected, NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), NuvioTheme.spacing.xxs) else // [fork] focus ring shape
                when {
                    isSelected -> Modifier.border(NuvioTheme.spacing.xxs, Color.White, CircleShape)
                    isFocused -> Modifier.border(NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), CircleShape)
                    else -> Modifier
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && focusKey != null) {
                    onFocused?.invoke(focusKey)
                }
            },
        shape = CardDefaults.shape(CircleShape)
        ,
        border = if (com.nuvio.tv.ui.components.nuvioCFocusRingShapeOn) com.nuvio.tv.ui.components.nuvioCNoCardBorder() else CardDefaults.border(), // [fork] focus ring shape
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {}
}

@Composable
private fun overlayCardColors(selected: Boolean) = CardDefaults.colors(
    containerColor = if (selected) NuvioTheme.colors.Secondary else Color.Transparent,
    focusedContainerColor = if (selected) NuvioTheme.colors.Secondary else Color.Transparent
)

@Composable
private fun overlayCardBorder() = CardDefaults.border(
    border = Border(
        border = BorderStroke(NuvioTheme.spacing.xxs, Color.Transparent),
        shape = RoundedCornerShape(NuvioTheme.radii.md)
    ),
    focusedBorder = Border(
        border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
        shape = RoundedCornerShape(NuvioTheme.radii.md)
    )
)

// [fork] Nuvio C subtitle font stepper focus keys
private const val NUVIO_C_FONT_PREV = "nuvio_c_font_prev"
private const val NUVIO_C_FONT_NEXT = "nuvio_c_font_next"
private const val NUVIO_C_BG_PREV = "nuvio_c_bg_prev" // [fork] round 29
private const val NUVIO_C_BG_NEXT = "nuvio_c_bg_next"
/** Accessible name for a colour swatch: its own RGB value, as that is the only colour
 *  description available on this screen (there are no per-colour name resources). */
private fun overlaySwatchColorLabel(color: Color): String {
    val rgb = color.toArgb() and 0xFFFFFF
    return "#" + rgb.toString(16).padStart(6, '0').uppercase()
}

internal object StyleFocusKey {
    const val FontSizeDecrease = "font_size_decrease"
    const val FontSizeIncrease = "font_size_increase"
    const val Bold = "bold"
    const val OutlineToggle = "outline_toggle"
    const val OffsetDecrease = "offset_decrease"
    const val OffsetIncrease = "offset_increase"
    const val DelaySet = "delay_set"
    const val Reset = "reset"
    const val TextColorPrefix = "text_color"
    const val OpacityDecrease = "opacity_decrease"
    const val OpacityIncrease = "opacity_increase"
    const val HdrBrightnessDecrease = "hdr_brightness_decrease"
    const val HdrBrightnessIncrease = "hdr_brightness_increase"
    const val OutlineColorPrefix = "outline_color"
    const val FontDecrease = "font_decrease"
    const val FontIncrease = "font_increase"
    const val BackgroundColorPrefix = "background_color"
    const val BackgroundOpacityDecrease = "background_opacity_decrease"
    const val BackgroundOpacityIncrease = "background_opacity_increase"
    const val RoundnessDecrease = "roundness_decrease"
    const val RoundnessIncrease = "roundness_increase"
    const val ShadowToggle = "shadow_toggle"
    const val ShadowOpacityDecrease = "shadow_opacity_decrease"
    const val ShadowOpacityIncrease = "shadow_opacity_increase"
    const val ShadowSizeDecrease = "shadow_size_decrease"
    const val ShadowSizeIncrease = "shadow_size_increase"
    const val OutlineWidthDecrease = "outline_width_decrease" // [fork] round 46
    const val OutlineWidthIncrease = "outline_width_increase" // [fork] round 46
}

private enum class OverlayFocusRail {
    LANGUAGE,
    OPTION,
    STYLE
}

private fun String.isHdrBrightnessFocusKey(): Boolean =
    this == StyleFocusKey.HdrBrightnessDecrease || this == StyleFocusKey.HdrBrightnessIncrease

/**
 * [fork] Order: delay, font size, font, bold, colour, text opacity, background opacity, (HDR brightness),
 * outline, bottom offset, reset. The HDR brightness item sits right after background opacity.
 */
private fun styleListIndexForFocusKey(focusKey: String, hasHdrBrightnessItem: Boolean): Int {
    if (focusKey.isHdrBrightnessFocusKey()) return 6
    val raw = styleListIndexForFocusKey(focusKey)
    // [fork] round 44 subtitle_classic_renderer: no Shadow card, so the rows after it move up one
    val index = if (com.nuvio.tv.NuvioCFeatures.SUBTITLE_CLASSIC_RENDERER && raw >= 9) raw - 1 else raw
    return if (hasHdrBrightnessItem && index >= 6) index + 1 else index
}

private fun styleListIndexForFocusKey(focusKey: String): Int {
    return when {
        focusKey == StyleFocusKey.DelaySet -> 0
        focusKey == StyleFocusKey.FontDecrease || focusKey == StyleFocusKey.FontIncrease -> 1
        focusKey == StyleFocusKey.FontSizeDecrease || focusKey == StyleFocusKey.FontSizeIncrease -> 2
        focusKey == StyleFocusKey.Bold -> 3
        focusKey.startsWith("${StyleFocusKey.TextColorPrefix}:") -> 4
        focusKey == StyleFocusKey.OpacityDecrease || focusKey == StyleFocusKey.OpacityIncrease -> 5
        focusKey.startsWith("${StyleFocusKey.BackgroundColorPrefix}:") ||
            focusKey == StyleFocusKey.BackgroundOpacityDecrease || focusKey == StyleFocusKey.BackgroundOpacityIncrease ||
            focusKey == StyleFocusKey.RoundnessDecrease || focusKey == StyleFocusKey.RoundnessIncrease -> 6
        focusKey == StyleFocusKey.OutlineToggle || focusKey.startsWith("${StyleFocusKey.OutlineColorPrefix}:") ||
            focusKey == StyleFocusKey.OutlineWidthDecrease || focusKey == StyleFocusKey.OutlineWidthIncrease -> 7
        focusKey == StyleFocusKey.ShadowToggle ||
            focusKey == StyleFocusKey.ShadowOpacityDecrease || focusKey == StyleFocusKey.ShadowOpacityIncrease ||
            focusKey == StyleFocusKey.ShadowSizeDecrease || focusKey == StyleFocusKey.ShadowSizeIncrease -> 8
        focusKey == StyleFocusKey.OffsetDecrease || focusKey == StyleFocusKey.OffsetIncrease -> 9
        focusKey == StyleFocusKey.Reset -> 10
        else -> 0
    }
}

@Composable
private fun rememberFocusRequesterMap(keys: List<String>): Map<String, FocusRequester> {
    return remember(keys) { keys.associateWith { FocusRequester() } }
}

@Composable
private fun rememberStyleFocusRequesters(): Map<String, FocusRequester> {
    return remember {
        listOf(
            StyleFocusKey.FontSizeDecrease,
            StyleFocusKey.FontSizeIncrease,
            StyleFocusKey.Bold,
            StyleFocusKey.OpacityDecrease,
            StyleFocusKey.OpacityIncrease,
            StyleFocusKey.HdrBrightnessDecrease,
            StyleFocusKey.HdrBrightnessIncrease,
            StyleFocusKey.OutlineToggle,
            StyleFocusKey.OffsetDecrease,
            StyleFocusKey.OffsetIncrease,
            StyleFocusKey.DelaySet,
            StyleFocusKey.Reset,
            StyleFocusKey.FontDecrease,
            StyleFocusKey.FontIncrease,
            StyleFocusKey.BackgroundOpacityDecrease,
            StyleFocusKey.BackgroundOpacityIncrease,
            StyleFocusKey.RoundnessDecrease,
            StyleFocusKey.RoundnessIncrease,
            StyleFocusKey.ShadowToggle,
            StyleFocusKey.ShadowOpacityDecrease,
            StyleFocusKey.ShadowOpacityIncrease,
            StyleFocusKey.ShadowSizeDecrease,
            StyleFocusKey.ShadowSizeIncrease,
            StyleFocusKey.OutlineWidthDecrease,
            StyleFocusKey.OutlineWidthIncrease
        ).associateWith { FocusRequester() } +
            SubtitleTextColorPresets.associate { color ->
                "${StyleFocusKey.TextColorPrefix}:${color.toArgb()}" to FocusRequester()
            } +
            SubtitleOutlineColorPresets.associate { color ->
                "${StyleFocusKey.OutlineColorPrefix}:${color.toArgb()}" to FocusRequester()
            } +
            SubtitleBackgroundColorPresets.associate { color ->
                "${StyleFocusKey.BackgroundColorPrefix}:${color.toArgb()}" to FocusRequester()
            }
    }
}

private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollItemIntoView(
    targetIndex: Int,
    contextItemsBefore: Int = 1
) {
    if (layoutInfo.visibleItemsInfo.any { it.index == targetIndex }) return
    scrollToItem((targetIndex - contextItemsBefore).coerceAtLeast(0))
}

private fun preferredVisibleStartIndex(targetIndex: Int): Int {
    if (targetIndex < 0) return 0
    return (targetIndex - 1).coerceAtLeast(0)
}

private data class SubtitleLanguageRailItem(
    val key: String,
    val label: String,
    val count: Int
)

private enum class SubtitleOptionKind {
    INTERNAL,
    ADDON
}

private data class SubtitleOptionRailItem(
    val id: String,
    val kind: SubtitleOptionKind,
    val title: String,
    val sourceLabel: String,
    val meta: String?,
    val isSelected: Boolean,
    val internalTrackIndex: Int? = null,
    val addonSubtitle: Subtitle? = null
)

private fun buildSubtitleLanguageRailItems(
    internalTracks: List<TrackInfo>,
    addonSubtitles: List<Subtitle>,
    preferredLanguage: String,
    secondaryPreferredLanguage: String?,
    showOnlyPreferredLanguages: Boolean,
    currentLanguageKey: String,
    noneLabel: String,
    unknownLabel: String
): List<SubtitleLanguageRailItem> {
    val counts = linkedMapOf<String, Int>()
    internalTracks.forEach { track ->
        val key = normalizeOverlayLanguageKeyForTrack(track)
        counts[key] = (counts[key] ?: 0) + 1
    }
    addonSubtitles.forEach { subtitle ->
        val key = normalizeOverlayLanguageKey(subtitle.lang)
        counts[key] = (counts[key] ?: 0) + 1
    }

    val preferredOrder = preferredOverlayLanguageOrder(
        preferredLanguage = preferredLanguage,
        secondaryPreferredLanguage = secondaryPreferredLanguage
    )

    val languageEntries = if (showOnlyPreferredLanguages) {
        val preferredKeys = preferredOrder.toSet()
        counts.entries.filter { entry ->
            entry.key in preferredKeys || entry.key == currentLanguageKey
        }
    } else {
        counts.entries
    }

    val sortedItems = languageEntries
        .sortedWith(
            compareBy<Map.Entry<String, Int>>(
                { entry ->
                    val preferredIndex = preferredOrder.indexOf(entry.key)
                    if (preferredIndex >= 0) preferredIndex else Int.MAX_VALUE
                },
                { entry -> subtitleLanguageSortLabel(entry.key) }
            )
        )
        .map { (key, count) ->
            SubtitleLanguageRailItem(
                key = key,
                label = subtitleLanguageLabel(key, unknownLabel),
                count = count
            )
        }

    return listOf(
        SubtitleLanguageRailItem(
            key = SubtitleOffLanguageKey,
            label = noneLabel,
            count = 0
        )
    ) + sortedItems
}

private fun preferredOverlayLanguageOrder(
    preferredLanguage: String,
    secondaryPreferredLanguage: String?
): List<String> {
    fun toOverlayLanguageKey(language: String?): String? {
        if (language.isNullOrBlank()) return null
        val normalized = PlayerSubtitleUtils.normalizeLanguageCode(language)
        if (normalized == "none" || normalized == SUBTITLE_LANGUAGE_FORCED) return null
        return normalizeOverlayLanguageKey(language)
            .takeUnless { it == SubtitleUnknownLanguageKey }
    }

    return listOfNotNull(
        toOverlayLanguageKey(preferredLanguage),
        toOverlayLanguageKey(secondaryPreferredLanguage)
    ).distinct()
}

private fun buildSubtitleOptionRailItems(
    selectedLanguageKey: String,
    internalTracks: List<TrackInfo>,
    addonSubtitles: List<Subtitle>,
    installedAddonOrder: List<String>,
    selectedOptionId: String?,
    builtInLabel: String,
    forcedLabel: String
): List<SubtitleOptionRailItem> {
    if (selectedLanguageKey == SubtitleOffLanguageKey) return emptyList()

    val addonOrderMap = installedAddonOrder.withIndex().associate { (index, name) -> name to index }
    fun toAddonItem(subtitle: Subtitle): SubtitleOptionRailItem {
        val optionId = addonSubtitleOptionId(subtitle)
        return SubtitleOptionRailItem(
            id = optionId,
            kind = SubtitleOptionKind.ADDON,
            title = if (subtitle.isStreamProvided) {
                streamProvidedSubtitleTitle(subtitle)
            } else {
                Subtitle.languageCodeToName(PlayerSubtitleUtils.normalizeLanguageCode(subtitle.lang))
            },
            sourceLabel = if (subtitle.isStreamProvided) builtInLabel else subtitle.addonName,
            meta = subtitle.id.takeIf { it.isNotBlank() && it != subtitle.lang && it != subtitle.url },
            isSelected = optionId == selectedOptionId,
            addonSubtitle = subtitle
        )
    }

    val matchingAddonSubtitles = addonSubtitles
        .filter { normalizeOverlayLanguageKey(it.lang) == selectedLanguageKey }
        .distinctBy { addonSubtitleOptionId(it) }

    val streamProvidedItems = matchingAddonSubtitles
        .filter { it.isStreamProvided }
        .map(::toAddonItem)

    val internalItems = internalTracks
        .filter { normalizeOverlayLanguageKeyForTrack(it) == selectedLanguageKey }
        .map { track ->
            // [fork] round 34 track_names: "English · SDH" with the file's own name on the meta line
            val nuvioCTitle = NuvioCTrackNames.subtitleTitle(track.name, normalizeOverlayLanguageKeyForTrack(track), track.isForced)
            SubtitleOptionRailItem(
                id = "internal:${track.index}",
                kind = SubtitleOptionKind.INTERNAL,
                title = nuvioCTitle ?: track.name,
                sourceLabel = builtInLabel,
                meta = (if (com.nuvio.tv.NuvioCFeatures.TRACK_LABEL_LANGUAGE && nuvioCTitle != null)
                    // [fork] round 35: "English" with only its description underneath (no codec)
                    NuvioCTrackNames.subtitleDescription(track.name, nuvioCTitle, track.isForced)
                else listOfNotNull(
                    NuvioCTrackNames.original(track.name, nuvioCTitle),
                    track.codec,
                    if (track.isForced && nuvioCTitle == null) forcedLabel else null
                ).joinToString(" • ").ifBlank { null }),
                isSelected = "internal:${track.index}" == selectedOptionId,
                internalTrackIndex = track.index
            )
        }

    val addonFetchedItems = matchingAddonSubtitles
        .filter { !it.isStreamProvided }
        .withIndex()
        .sortedWith(
            compareBy(
                { (_, subtitle) -> addonOrderMap[subtitle.addonName] ?: Int.MAX_VALUE },
                { (index, _) -> index }
            )
        )
        .map { (_, subtitle) -> toAddonItem(subtitle) }
        .let { if (com.nuvio.tv.NuvioCFeatures.SUBTITLE_PANEL_CLEAN) NuvioCSubtitleRows.tidyAddonItems(it, { i -> i.addonSubtitle }, { i, t, src, meta -> i.copy(title = t, sourceLabel = src, meta = meta) }) else it }

    return internalItems + streamProvidedItems + addonFetchedItems
}

private fun selectedSubtitleLanguageKey(
    internalTracks: List<TrackInfo>,
    selectedInternalIndex: Int,
    selectedAddonSubtitle: Subtitle?
): String {
    val selectedAddonKey = selectedAddonSubtitle?.let { normalizeOverlayLanguageKey(it.lang) }
    if (selectedAddonKey != null) return selectedAddonKey

    val selectedInternalKey = internalTracks
        .firstOrNull { it.index == selectedInternalIndex }
        ?.let { normalizeOverlayLanguageKeyForTrack(it) }
        ?: internalTracks.firstOrNull { it.isSelected }
            ?.let { normalizeOverlayLanguageKeyForTrack(it) }
    if (selectedInternalKey != null) return selectedInternalKey

    return SubtitleOffLanguageKey
}

private fun selectedSubtitleOptionId(
    internalTracks: List<TrackInfo>,
    selectedInternalIndex: Int,
    selectedAddonSubtitle: Subtitle?
): String? {
    selectedAddonSubtitle?.let { subtitle ->
        return addonSubtitleOptionId(subtitle)
    }

    internalTracks
        .firstOrNull { it.index == selectedInternalIndex }
        ?.let { track ->
            return "internal:${track.index}"
        }

    internalTracks
        .firstOrNull { it.isSelected }
        ?.let { track ->
            return "internal:${track.index}"
        }

    return null
}

private fun addonSubtitleOptionId(subtitle: Subtitle): String {
    return "addon:${subtitle.addonName}:${subtitle.id}:${subtitle.url}"
}

private fun streamProvidedSubtitleTitle(subtitle: Subtitle): String {
    val name = subtitle.addonName.trim()
    if (name.isNotBlank() && !name.equals("Plugin", ignoreCase = true)) {
        return name
    }
    return subtitle.lang
}

private fun normalizeOverlayLanguageKey(language: String?): String {
    if (language.isNullOrBlank()) return SubtitleUnknownLanguageKey
    val normalized = PlayerSubtitleUtils.normalizeLanguageCode(language)
    return when (normalized) {
        "pt-br", "es-419", "sr-me" -> normalized
        else -> normalized
            .substringBefore('-')
            .substringBefore('_')
            .ifBlank { SubtitleUnknownLanguageKey }
    }
}

/**
 * Variant-aware language key for embedded tracks. Inspects name/label/trackId
 * to detect regional accents (e.g. Brazilian Portuguese, Latin American Spanish)
 * even when the language field is generic ("por", "spa").
 */
private fun normalizeOverlayLanguageKeyForTrack(track: TrackInfo): String {
    val variant = PlayerSubtitleUtils.detectTrackLanguageVariant(
        language = track.language,
        name = track.name,
        trackId = track.trackId
    )
    return when (variant) {
        "pt-br", "es-419", "sr-me" -> variant
        else -> variant
            .substringBefore('-')
            .substringBefore('_')
            .ifBlank { SubtitleUnknownLanguageKey }
    }
}

private fun subtitleLanguageLabel(key: String, unknownLabel: String): String {
    return when (key) {
        SubtitleOffLanguageKey -> Subtitle.languageCodeToName("none")
        SubtitleUnknownLanguageKey -> unknownLabel
        else -> Subtitle.languageCodeToName(key)
    }
}

private fun subtitleLanguageSortLabel(key: String): String = when (key) {
    SubtitleUnknownLanguageKey -> "\uFFFF"
    SubtitleOffLanguageKey -> Subtitle.languageCodeToName("none").lowercase()
    else -> Subtitle.languageCodeToName(key).lowercase()
}

private fun formatSubtitleDelay(delayMs: Int): String {
    return when {
        delayMs > 0 -> "+${delayMs}ms"
        delayMs < 0 -> "${delayMs}ms"
        else -> "0ms"
    }
}


// ---- [fork] Nuvio C round 52 track_panels_v2: the Subtitles card --------------------------------------------------

/**
 * One card on the left, under the Subtitles pill: title and "English · 7" on top, then Tracks / Style / Language tabs.
 * Tracks lists Off, then the chosen language's tracks grouped by source (Built in, then each add-on), every row two
 * lines (name; details), a check on the active one, long lines scroll on focus. Style is the same list as before
 * (delay first, then the Settings > Subtitles order). Language picks which language Tracks shows. Left / Right on the
 * tabs switch at once; Down enters the list; focus opens on the active track.
 */
@Composable
private fun NuvioCSubtitleTabsPanel(
    modifier: Modifier,
    visible: Boolean,
    languageItems: List<SubtitleLanguageRailItem>,
    initialLanguageKey: String,
    initialSelectedOptionId: String?,
    buildOptions: (String, String?) -> List<SubtitleOptionRailItem>,
    onInternalTrackSelected: (Int) -> Unit,
    onAddonSubtitleSelected: (Subtitle) -> Unit,
    onDisableSubtitles: () -> Unit,
    styleContent: @Composable (androidx.compose.ui.unit.Dp) -> Unit,
    styleEntry: FocusRequester,
    styleEntryFocused: androidx.compose.runtime.State<Boolean> = androidx.compose.runtime.mutableStateOf(false)
) {
    val firstLanguage = remember(languageItems) { languageItems.firstOrNull { it.key != SubtitleOffLanguageKey }?.key }
    var tab by remember(visible) { mutableStateOf(0) }
    var languageKey by remember(visible) {
        mutableStateOf(initialLanguageKey.takeIf { it != SubtitleOffLanguageKey } ?: firstLanguage ?: SubtitleOffLanguageKey)
    }
    var selectedId by remember(visible) { mutableStateOf(initialSelectedOptionId) }
    var off by remember(visible) { mutableStateOf(initialSelectedOptionId == null && initialLanguageKey == SubtitleOffLanguageKey) }
    val options = remember(languageKey, selectedId) { buildOptions(languageKey, selectedId) }
    val tabRequesters = remember { List(3) { FocusRequester() } }
    val trackEntry = remember { FocusRequester() }
    val languageEntry = remember { FocusRequester() }
    val tracksState = rememberLazyListState()
    val languageState = rememberLazyListState()
    val cardWidth = NuvioCPanelLook.width
    val labelOf = languageItems.associate { it.key to it.label }
    // [fork] round 53 track_panels_v3: Tracks lists every language (no Language tab); each row is language, then details
    val allOptions = remember(languageItems, selectedId) {
        if (!NuvioCPanelLook.v3) emptyList()
        else languageItems.filter { it.key != SubtitleOffLanguageKey }.flatMap { lang -> buildOptions(lang.key, selectedId).map { lang.label to it } }
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rowFocus = remember(visible) { HashMap<Int, FocusRequester>() } // [fork] round 53c: one per Tracks row
    fun rowReq(i: Int) = rowFocus.getOrPut(i) { FocusRequester() }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        tab = 0
        delay(120)
        if (com.nuvio.tv.NuvioCFeatures.SUB_PANEL_ENTRY_FIX && NuvioCPanelLook.v3) {
            // [fork] round 54 sub_panel_entry_fix: the active track can sit far down the list (an add-on subtitle
            // was row 40-something of 47); it wasn't composed, the request failed and the panel opened with no focus
            // at all, so nothing could be changed. Scroll it into view first, retry, then fall back to the top row.
            val idx = if (off) 0 else (allOptions.indexOfFirst { it.second.id == selectedId } + 1).coerceAtLeast(0)
            runCatching { tracksState.scrollToItem((idx - 1).coerceAtLeast(0)) }
            repeat(6) {
                if (trackEntry.requestFocusAfterFrames(frames = 1)) return@LaunchedEffect
            }
            runCatching { tracksState.scrollToItem(0) }
            repeat(4) {
                if (rowReq(0).requestFocusAfterFrames(frames = 1)) return@LaunchedEffect
            }
            runCatching { tabRequesters[0].requestFocus() }
        } else trackEntry.requestFocusAfterFrames(frames = 2)
    }

    Column(
        modifier = modifier.nuvioCPanelCard(),
        verticalArrangement = Arrangement.spacedBy(NuvioCPanelLook.gap)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.subtitle_dialog_title),
                style = if (NuvioCPanelLook.v3) MaterialTheme.typography.titleMedium.copy(shadow = NuvioCPanelLook.textShadow) else MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.weight(1f))
            val summary = if (off) stringResource(R.string.subtitle_none)
                else listOfNotNull(labelOf[languageKey], options.size.takeIf { it > 0 }?.toString()).joinToString(" · ")
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.nuvioCTabRow { tabRequesters[tab] }) {
            listOfNotNull(
                stringResource(R.string.nuvio_c_panel_tracks),
                stringResource(R.string.subtitle_style_title),
                if (NuvioCPanelLook.v3) null else stringResource(R.string.nuvio_c_panel_language)
            ).forEachIndexed { i, label ->
                NuvioCPanelTab(
                    label = label,
                    selected = tab == i,
                    focusRequester = tabRequesters[i],
                    down = when (if (NuvioCPanelLook.v3 && com.nuvio.tv.NuvioCFeatures.PANEL_TABS_ON_SELECT) tab else i) { 0 -> trackEntry; 1 -> styleEntry; else -> languageEntry },
                    onFocused = { tab = i },
                    onDown = if (NuvioCPanelLook.v3 && (if (com.nuvio.tv.NuvioCFeatures.PANEL_TABS_ON_SELECT) tab == 0 else i == 0)) ({
                        // scroll the active row into view before focusing it, so Down never loses focus
                        val idx = if (off) 0 else (allOptions.indexOfFirst { it.second.id == selectedId } + 1).coerceAtLeast(0)
                        scope.launch { tracksState.scrollToItem(idx); trackEntry.requestFocusAfterFrames(1) }
                        true
                    }) else null
                )
            }
        }
        when {
            NuvioCPanelLook.v3 && tab == 0 -> LazyColumn(
                state = tracksState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = NuvioCPanelLook.listMax)
            ) {
                item(key = "off") {
                    NuvioCPanelRow(
                        title = stringResource(R.string.subtitle_none),
                        detail = stringResource(R.string.nuvio_c_panel_no_subtitles),
                        selected = off,
                        focusRequester = if (off || allOptions.none { it.second.id == selectedId }) trackEntry else null,
                        onClick = { off = true; selectedId = null; onDisableSubtitles() },
                        modifier = Modifier.focusRequester(rowReq(0)).nuvioCRowUp(0, rowFocus, tracksState, tabRequesters[0], scope),
                        pill = stringResource(R.string.nuvio_c_panel_pill_off)
                    )
                }
                items(allOptions.size, key = { allOptions[it].second.id }) { i ->
                    val (language, item) = allOptions[i]
                    // three lines on every track: source pill, language, description
                    val detail = listOfNotNull(
                        item.title.takeIf { it.isNotBlank() && !it.equals(language, true) },
                        item.meta?.takeIf { it.isNotBlank() }
                    ).distinct().joinToString(" · ").ifBlank { stringResource(R.string.nuvio_c_panel_subtitle_track) }
                    NuvioCPanelRow(
                        modifier = Modifier.focusRequester(rowReq(i + 1)).nuvioCRowUp(i + 1, rowFocus, tracksState, tabRequesters[0], scope),
                        pill = item.sourceLabel.ifBlank { null },
                        title = language,
                        detail = detail,
                        selected = !off && item.id == selectedId,
                        focusRequester = if (!off && item.id == selectedId) trackEntry else null,
                        onClick = {
                            off = false
                            selectedId = item.id
                            when (item.kind) {
                                SubtitleOptionKind.INTERNAL -> item.internalTrackIndex?.let(onInternalTrackSelected)
                                SubtitleOptionKind.ADDON -> item.addonSubtitle?.let(onAddonSubtitleSelected)
                            }
                        }
                    )
                }
            }
            NuvioCPanelLook.v3 && tab == 1 -> Box(
                Modifier.onPreviewKeyEvent { e ->
                    // Up from the first Style row (Delay) goes back to the Style tab
                    if (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && styleEntryFocused.value) {
                        if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) runCatching { tabRequesters[1].requestFocus() }
                        true
                    } else false
                }
            ) { styleContent(cardWidth - NuvioCPanelLook.padH * 2) }
            tab == 0 -> LazyColumn(
                state = tracksState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp)
            ) {
                item(key = "off") {
                    NuvioCPanelRow(
                        title = stringResource(R.string.subtitle_none),
                        detail = stringResource(R.string.nuvio_c_panel_no_subtitles),
                        selected = off,
                        focusRequester = if (off || options.none { it.id == selectedId }) trackEntry else null,
                        onClick = { off = true; selectedId = null; onDisableSubtitles() }
                    )
                }
                var lastSource: String? = null
                options.forEach { item ->
                    if (item.sourceLabel != lastSource) {
                        val header = item.sourceLabel
                        lastSource = header
                        item(key = "h:$header") {
                            Text(
                                header.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                    }
                    item(key = item.id) {
                        val active = !off && item.id == selectedId
                        NuvioCPanelRow(
                            title = item.title,
                            detail = item.meta,
                            selected = active,
                            focusRequester = if (!off && item.id == selectedId) trackEntry else null,
                            onClick = {
                                off = false
                                selectedId = item.id
                                when (item.kind) {
                                    SubtitleOptionKind.INTERNAL -> item.internalTrackIndex?.let(onInternalTrackSelected)
                                    SubtitleOptionKind.ADDON -> item.addonSubtitle?.let(onAddonSubtitleSelected)
                                }
                            }
                        )
                    }
                }
            }
            tab == 1 -> styleContent(cardWidth - 44.dp)
            else -> LazyColumn(
                state = languageState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp)
            ) {
                val items = languageItems.filter { it.key != SubtitleOffLanguageKey }
                items(items.size, key = { items[it].key }) { i ->
                    val item = items[i]
                    NuvioCPanelRow(
                        title = item.label,
                        detail = pluralStringResource(R.plurals.nuvio_c_panel_track_count, item.count, item.count),
                        selected = item.key == languageKey,
                        focusRequester = if (item.key == languageKey || (i == 0 && items.none { it.key == languageKey })) languageEntry else null,
                        onClick = {
                            languageKey = item.key
                            tab = 0
                        }
                    )
                }
            }
        }
    }
    // a language chosen in the Language tab opens Tracks on that language's list
    LaunchedEffect(languageKey) { tracksState.scrollToItem(0) }
}

@Composable
internal fun NuvioCPanelTab(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    down: FocusRequester?,
    onDown: (() -> Boolean)? = null,
    onFocused: () -> Unit
) {
    // [fork] round 54b: the key handler and focus properties are always in the chain and read the current values; adding
    // or removing them when the active tab changed (tabs on Select) rebuilt the pressed tab and dropped its focus
    // [fork] round 54c: with tabs on Select, where Down goes changes while this tab is focused. Changing the focused
    // node's focusProperties (down = ...) dropped focus on the TV (Style / Sound tab), so Down is handled here in the
    // key handler instead and no focus property ever changes; after Select, focus is put back on this tab.
    val currentOnDown by androidx.compose.runtime.rememberUpdatedState(onDown)
    val currentDown by androidx.compose.runtime.rememberUpdatedState(down)
    val onSelectOnly = NuvioCPanelLook.v3 && com.nuvio.tv.NuvioCFeatures.PANEL_TABS_ON_SELECT
    val tabScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.tv.material3.Surface(
        onClick = {
            onFocused()
            if (onSelectOnly) tabScope.launch { focusRequester.requestFocusAfterFrames(frames = 1) }
        },
        modifier = Modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { e ->
                if (e.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_DOWN) return@onPreviewKeyEvent false
                val od = currentOnDown
                if (od != null) return@onPreviewKeyEvent if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) od() else true
                if (!onSelectOnly) return@onPreviewKeyEvent false
                val target = currentDown ?: return@onPreviewKeyEvent false
                if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    tabScope.launch { target.requestFocusAfterFrames(frames = 1) }
                }
                true
            }
            .then(if (onSelectOnly) Modifier else Modifier.focusProperties { down?.let { this.down = it } })
            .onFocusChanged { if (it.isFocused && !(NuvioCPanelLook.v3 && com.nuvio.tv.NuvioCFeatures.PANEL_TABS_ON_SELECT)) onFocused() }, // [fork] round 54: Modern tabs switch on Select only
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (selected) (if (NuvioCPanelLook.accent) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.92f)) else Color.White.copy(alpha = 0.08f),
            contentColor = if (selected) (if (NuvioCPanelLook.accent) NuvioTheme.colors.OnSecondary else Color.Black) else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = NuvioTheme.focusRing.border(2.dp), shape = RoundedCornerShape(50))
        )
    ) {
        Text(
            label,
            style = if (NuvioCPanelLook.v3) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
            modifier = if (NuvioCPanelLook.v3) Modifier.padding(horizontal = 14.dp, vertical = 6.dp) else Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

/** Two-line row: name, then details; the focused row scrolls what doesn't fit; a check marks the active one. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun NuvioCPanelRow(
    title: String,
    detail: String?,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    pill: String? = null // [fork] round 53b: a source pill (Built-in / add-on) as the first of three lines
) {
    var focused by remember { mutableStateOf(false) }
    val v3 = NuvioCPanelLook.v3
    // [fork] round 54b: always one requester in the chain (the shared "active row" one, or this row's own); moving the
    // shared one between rows used to add / remove a modifier and rebuild the row that was just pressed, losing focus
    val ownRequester = remember { FocusRequester() }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester ?: ownRequester)
            .onFocusChanged { focused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = if (v3) 0.14f else 0.10f) else Color.Transparent,
            focusedContainerColor = if (v3) (if (NuvioCPanelLook.accent) NuvioTheme.colors.Secondary else Color.White) else Color.White.copy(alpha = 0.16f)
        ),
        shape = CardDefaults.shape(RoundedCornerShape(if (v3) 12.dp else 14.dp)),
        border = CardDefaults.border(
            focusedBorder = Border(border = NuvioTheme.focusRing.border(2.dp), shape = RoundedCornerShape(14.dp))
        ),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (v3) 12.dp else 16.dp, vertical = if (v3) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val onBar = if (NuvioCPanelLook.accent) NuvioTheme.colors.OnSecondary else Color.Black // [fork] round 54: accent bar
            val fg = if (v3 && focused) onBar else Color.White
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (v3) 1.dp else 2.dp)) {
                if (v3 && pill != null) Box(
                    Modifier.padding(bottom = 2.dp)
                        .background(if (focused) onBar.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                ) {
                    Text(pill, style = MaterialTheme.typography.labelSmall, color = if (focused) onBar.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f), maxLines = 1)
                }
                Text(
                    title,
                    style = if (v3) MaterialTheme.typography.titleSmall.copy(shadow = if (focused) null else NuvioCPanelLook.textShadow) else MaterialTheme.typography.titleMedium,
                    color = fg,
                    maxLines = 1,
                    overflow = if (focused) androidx.compose.ui.text.style.TextOverflow.Clip else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier
                )
                if (!detail.isNullOrBlank()) Text(
                    detail,
                    style = if (v3) (if (com.nuvio.tv.NuvioCFeatures.PANEL_DETAIL_SMALLER) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium).copy(shadow = if (focused) null else NuvioCPanelLook.textShadow) else MaterialTheme.typography.bodySmall,
                    color = if (v3 && focused) onBar.copy(alpha = 0.75f) else Color.White.copy(alpha = if (v3) 0.75f else 0.6f),
                    maxLines = 1,
                    overflow = if (focused) androidx.compose.ui.text.style.TextOverflow.Clip else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = if (focused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier
                )
            }
            trailing?.invoke()
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.Check, contentDescription = null, tint = if (v3 && focused) onBar else NuvioTheme.colors.Secondary)
            }
        }
    }
}


// ---- [fork] Nuvio C round 53 track_panels_v3: slimmer, see-through panels -------------------------------------------

/** [fork] round 54: the new track panels belong to the Modern player style; the default style keeps the round 51 panels. */
internal fun nuvioCModernPanels(): Boolean =
    !com.nuvio.tv.NuvioCFeatures.PANELS_MODERN_ONLY || NuvioCOsdStyle.style.value == NuvioCOsdStyle.CLEAN

internal object NuvioCPanelLook {
    val v3: Boolean get() = com.nuvio.tv.NuvioCFeatures.TRACK_PANELS_V3 && com.nuvio.tv.NuvioCFeatures.TRACK_PANELS_V2 && nuvioCModernPanels()
    /** [fork] round 54: the focus bar and active tab take the theme accent, as the official panels do. */
    val accent: Boolean get() = v3 && com.nuvio.tv.NuvioCFeatures.PANEL_ACCENT_BAR
    val width: Dp get() = if (v3) (if (com.nuvio.tv.NuvioCFeatures.PANEL_NARROWER) 298.dp else 372.dp) else 560.dp
    val maxHeight: Dp get() = if (v3) 430.dp else 640.dp
    val listMax: Dp get() = if (v3) 318.dp else 470.dp
    val background: Color get() = if (v3) Color(0x8C0E0F13) else Color(0xF2141519)
    val radius: Dp get() = if (v3) 18.dp else 24.dp
    val padH: Dp get() = if (v3) 14.dp else 22.dp
    val padV: Dp get() = if (v3) 14.dp else 20.dp
    val gap: Dp get() = if (v3) 8.dp else 14.dp
    val textShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(0f, 1.5f), 4f)
}

/** The panel's card: a thin border and a lighter fill in v3 so the video shows through. */
internal fun Modifier.nuvioCPanelCard(): Modifier = this
    .width(NuvioCPanelLook.width)
    .heightIn(max = NuvioCPanelLook.maxHeight)
    .background(NuvioCPanelLook.background, RoundedCornerShape(NuvioCPanelLook.radius))
    .then(if (NuvioCPanelLook.v3) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(NuvioCPanelLook.radius)) else Modifier)
    .padding(horizontal = NuvioCPanelLook.padH, vertical = NuvioCPanelLook.padV)

/**
 * [fork] round 53: Up from the first row of a panel list goes to the active tab (never the tab that happens to sit
 * above it, which would switch tabs); Down from a tab scrolls the list to its entry row first, so focus always lands.
 */
internal fun Modifier.nuvioCUpToTab(isFirst: Boolean, tab: FocusRequester): Modifier =
    if (!NuvioCPanelLook.v3 || !isFirst) this else this.onPreviewKeyEvent { e ->
        if (e.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
            if (e.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) runCatching { tab.requestFocus() }
            true
        } else false
    }


/**
 * [fork] round 53c: Up inside a panel list moves one row at a time and scrolls the row above into view first, so a held
 * Up never skips out of the list early (and lands on a tab) or stalls on a row that isn't laid out yet. Row 0 goes to
 * the active tab.
 */
internal fun Modifier.nuvioCRowUp(
    index: Int,
    rows: Map<Int, FocusRequester>,
    listState: LazyListState,
    tab: FocusRequester,
    scope: kotlinx.coroutines.CoroutineScope
): Modifier = if (!NuvioCPanelLook.v3) this else this.onPreviewKeyEvent { e ->
    if (e.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_UP) return@onPreviewKeyEvent false
    if (e.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
    if (index <= 0) { runCatching { tab.requestFocus() }; return@onPreviewKeyEvent true }
    val target = rows[index - 1]
    scope.launch {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == index - 1 } ||
            (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index - 1 }?.offset ?: 0) < 0) {
            listState.scrollToItem((index - 1).coerceAtLeast(0))
        }
        target?.requestFocusAfterFrames(1)
    }
    true
}

/** [fork] round 53c: focus coming into a panel's tab row from the list always lands on the active tab. */
@Suppress("DEPRECATION")
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun Modifier.nuvioCTabRow(active: () -> FocusRequester): Modifier =
    if (!NuvioCPanelLook.v3) this else this.focusProperties { enter = { active() } }.focusGroup()

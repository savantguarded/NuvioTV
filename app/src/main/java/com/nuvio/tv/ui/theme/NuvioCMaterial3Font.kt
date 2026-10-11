package com.nuvio.tv.ui.theme

// [fork] Nuvio C (2026-10-11 round 4): the app font reaches Material 3 text too.
// Official only sets the TV Material theme, so a few screens drawn with Material 3 `Text` (the
// player's Episodes and Sources panels, some dialogs) stayed on the system font whatever was picked
// in Settings > Appearance > Font. This adds a Material 3 theme whose type scale carries the chosen
// font and nothing else: colours, shapes, default text size and focus/click indication stay exactly
// what those screens had before. Switch: NuvioCFeatures.FONT_EVERYWHERE.

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

@Composable
internal fun NuvioCMaterial3Font(fontFamily: FontFamily, content: @Composable () -> Unit) {
    val outerIndication = LocalIndication.current
    val outerSelection = LocalTextSelectionColors.current
    val outerTextStyle = LocalTextStyle.current
    val typography = remember(fontFamily) { nuvioCMaterial3Typography(fontFamily) }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = typography
    ) {
        CompositionLocalProvider(
            LocalIndication provides outerIndication,
            LocalTextSelectionColors provides outerSelection,
            LocalTextStyle provides outerTextStyle.merge(TextStyle(fontFamily = fontFamily)),
            content = content
        )
    }
}

private fun nuvioCMaterial3Typography(f: FontFamily): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = f),
        displayMedium = t.displayMedium.copy(fontFamily = f),
        displaySmall = t.displaySmall.copy(fontFamily = f),
        headlineLarge = t.headlineLarge.copy(fontFamily = f),
        headlineMedium = t.headlineMedium.copy(fontFamily = f),
        headlineSmall = t.headlineSmall.copy(fontFamily = f),
        titleLarge = t.titleLarge.copy(fontFamily = f),
        titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f),
        bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f),
        bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f),
        labelMedium = t.labelMedium.copy(fontFamily = f),
        labelSmall = t.labelSmall.copy(fontFamily = f)
    )
}

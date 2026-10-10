package com.nuvio.tv.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Typography
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.AppFont

val DMSansFamily = FontFamily(
    Font(R.font.dm_sans_variable, FontWeight.Normal),
    Font(R.font.dm_sans_variable, FontWeight.Medium),
    Font(R.font.dm_sans_variable, FontWeight.SemiBold),
    Font(R.font.dm_sans_variable, FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal),
    Font(R.font.inter_variable, FontWeight.Medium),
    Font(R.font.inter_variable, FontWeight.SemiBold),
    Font(R.font.inter_variable, FontWeight.Bold)
)

val OpenSansFamily = FontFamily(
    Font(R.font.opensans_variable, FontWeight.Normal),
    Font(R.font.opensans_variable, FontWeight.Medium),
    Font(R.font.opensans_variable, FontWeight.SemiBold),
    Font(R.font.opensans_variable, FontWeight.Bold)
)

fun getFontFamily(appFont: AppFont): FontFamily = when (appFont) {
    AppFont.INTER -> InterFamily
    AppFont.DM_SANS -> DMSansFamily
    AppFont.OPEN_SANS -> OpenSansFamily
    // [fork] Nuvio C extra fonts
    AppFont.GOOGLE_SANS_FLEX -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCGoogleSansFlexFamily else InterFamily
    AppFont.MANROPE -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCManropeFamily else InterFamily
    AppFont.FIGTREE -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCFigtreeFamily else InterFamily
    AppFont.PLUS_JAKARTA_SANS -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCPlusJakartaSansFamily else InterFamily
    AppFont.OUTFIT -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCOutfitFamily else InterFamily
    AppFont.ATKINSON_HYPERLEGIBLE_NEXT -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCAtkinsonHyperlegibleNextFamily else InterFamily
    AppFont.GEIST -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCGeistFamily else InterFamily
    AppFont.ONEST -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCOnestFamily else InterFamily
    AppFont.INSTRUMENT_SANS -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCInstrumentSansFamily else InterFamily
    AppFont.ALBERT_SANS -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCAlbertSansFamily else InterFamily
    AppFont.HANKEN_GROTESK -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCHankenGroteskFamily else InterFamily
    AppFont.RETHINK_SANS -> if (com.nuvio.tv.NuvioCFeatures.EXTRA_FONTS) NuvioCRethinkSansFamily else InterFamily
}

// [fork] Nuvio C extra fonts: one variable font file each (OFL, from Google Fonts), weights from its wght axis
private fun nuvioCVariableFamily(res: Int) = FontFamily(
    Font(res, FontWeight.Normal),
    Font(res, FontWeight.Medium),
    Font(res, FontWeight.SemiBold),
    Font(res, FontWeight.Bold)
)
private val NuvioCGoogleSansFlexFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_google_sans_flex) }
private val NuvioCManropeFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_manrope) }
private val NuvioCFigtreeFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_figtree) }
private val NuvioCPlusJakartaSansFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_plus_jakarta_sans) }
private val NuvioCOutfitFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_outfit) }
private val NuvioCAtkinsonHyperlegibleNextFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_atkinson_hyperlegible_next) }
private val NuvioCGeistFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_geist) }
private val NuvioCOnestFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_onest) }
private val NuvioCInstrumentSansFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_instrument_sans) }
private val NuvioCAlbertSansFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_albert_sans) }
private val NuvioCHankenGroteskFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_hanken_grotesk) }
private val NuvioCRethinkSansFamily by lazy { nuvioCVariableFamily(R.font.nuvio_c_rethink_sans) }


@Immutable
data class NuvioTextStyleTokens(
    val display: TextStyle,
    val displayCompact: TextStyle,
    val headline: TextStyle,
    val sectionTitle: TextStyle,
    val cardTitle: TextStyle,
    val body: TextStyle,
    val bodyCompact: TextStyle,
    val metadata: TextStyle,
    val badge: TextStyle,
    val button: TextStyle,
    val tab: TextStyle,
    val nav: TextStyle,
    val playerControl: TextStyle
)

@OptIn(ExperimentalTvMaterial3Api::class)
fun buildNuvioTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

@OptIn(ExperimentalTvMaterial3Api::class)
val NuvioTypography = buildNuvioTypography(InterFamily)

@OptIn(ExperimentalTvMaterial3Api::class)
fun buildNuvioTextStyles(typography: Typography): NuvioTextStyleTokens = NuvioTextStyleTokens(
    display = typography.displayLarge,
    displayCompact = typography.displayMedium,
    headline = typography.headlineLarge,
    sectionTitle = typography.headlineMedium,
    cardTitle = typography.titleMedium,
    body = typography.bodyLarge,
    bodyCompact = typography.bodyMedium,
    metadata = typography.labelMedium,
    badge = typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    ),
    button = typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    tab = typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    nav = typography.titleMedium,
    playerControl = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
)

@OptIn(ExperimentalTvMaterial3Api::class)
val NuvioTextStyles = buildNuvioTextStyles(NuvioTypography)

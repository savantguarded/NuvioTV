package com.nuvio.tv.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.theme.NuvioTheme

// [fork] Nuvio C page margin (switch `page_margin_40`): 40 dp side margin (Apple TV uses about 40)
// instead of official's 52 dp on Home / collection folders and 48 dp on the details page (hero,
// tabs, every row). Rows still run to the right edge, so this only gives the content more room on
// the left. Switch off = official margins.

val NUVIO_C_PAGE_MARGIN: Dp = 40.dp

/** Home / collection folders: rows, row titles and the hero text (official 52 dp). */
val nuvioCHomeMargin: Dp get() = if (NuvioCFeatures.PAGE_MARGIN_40) NUVIO_C_PAGE_MARGIN else 52.dp

/** Details page side margin (official `spacing.xxxl`, 48 dp). */
val nuvioCDetailMargin: Dp
    get() = if (NuvioCFeatures.PAGE_MARGIN_40) NUVIO_C_PAGE_MARGIN else NuvioTheme.spacing.xxxl

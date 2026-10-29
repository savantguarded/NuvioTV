package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

// [fork] Nuvio C round 32 (switch `imdb_logo_everywhere`): the details page's IMDb rating (logo + "8.4"),
// for the player loading screen, the player's About tab and the Episodes panel.

/** "8.4" from "8.4", "8.40", "8.4/10"; the text as given when it isn't a number. */
internal fun nuvioCImdbValue(raw: String): String {
    val n = raw.trim().substringBefore('/').trim().toDoubleOrNull() ?: return raw.trim()
    return String.format(java.util.Locale.US, "%.1f", n)
}

@Composable
fun NuvioCImdbRating(
    rating: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = NuvioTheme.spacing.xl,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
    textColor: Color = NuvioTheme.extendedColors.textSecondary
) {
    val context = LocalContext.current
    val model = remember(context) { ImageRequest.Builder(context).data(R.raw.imdb_logo_2016).build() }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = model, contentDescription = null, modifier = Modifier.size(iconSize), contentScale = ContentScale.Fit)
        Text(text = nuvioCImdbValue(rating), style = textStyle, color = textColor, maxLines = 1)
    }
}

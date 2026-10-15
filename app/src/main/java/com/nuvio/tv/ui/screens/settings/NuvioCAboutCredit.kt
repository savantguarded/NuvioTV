package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// [fork] Nuvio C attribution in Settings > About (2026-10-15). Always on, like the rest of the
// branding. Sits under official's version line; official credits, privacy policy and licences
// stay as they are (GPL-3.0: modified version, upstream notices kept). Not focusable, so focus
// order on the page is unchanged.
private const val NUVIO_C_MAINTAINER = "Charles"
private const val NUVIO_C_GITHUB = "github.com/savantguarded"

@Composable
internal fun NuvioCAboutCredit(modifier: Modifier = Modifier) {
    val qr by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching { QrCodeGenerator.generate("https://$NUVIO_C_GITHUB", size = 192, margin = 2).asImageBitmap() }.getOrNull()
        }
    }
    val build = BuildConfig.NUVIO_C_BUILD
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        qr?.let {
            Image(bitmap = it, contentDescription = NUVIO_C_GITHUB, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column {
            Text(
                text = if (build > 0) "Nuvio C · build $build" else "Nuvio C",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = "Maintained by $NUVIO_C_MAINTAINER · $NUVIO_C_GITHUB",
                style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.colors.TextSecondary
            )
            Text(
                text = "Modified build of Nuvio (GPL-3.0). Original credits below.",
                style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.colors.TextTertiary
            )
        }
    }
}

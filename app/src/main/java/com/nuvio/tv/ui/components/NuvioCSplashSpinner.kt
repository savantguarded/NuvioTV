package com.nuvio.tv.ui.components

import android.graphics.drawable.AnimatedVectorDrawable
import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.theme.accentBrush

// [fork] Nuvio C splash spinner (switch `splash_spinner_rt`, round 7, 2026-10-13).
// Charles: the loading circle at app start "stutters right before the home screen shows". Cause:
// official's LoadingIndicator is a Compose animation, so every frame of it is drawn on the main
// thread, and the home screen's first build (rows, hero, images) runs on that same thread under
// the splash; while it works, the circle stalls. This draws the same arc as an
// AnimatedVectorDrawable, whose rotation and trim animators run on the render thread, so the circle
// keeps turning through that work and through the fade-out. The theme's accent (solid or
// gradient) is painted over the white arc (SrcIn in its own layer), so it looks as before.

@Composable
fun NuvioCSplashSpinner(modifier: Modifier = Modifier) {
    val brush = NuvioTheme.palette.accentBrush()
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                setImageResource(R.drawable.nuvio_c_splash_spinner)
                scaleType = ImageView.ScaleType.FIT_CENTER
                (drawable as? AnimatedVectorDrawable)?.start()
            }
        },
        onRelease = { view -> (view.drawable as? AnimatedVectorDrawable)?.stop() },
        modifier = modifier
            .size(NuvioTheme.spacing.xxxl)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = brush, blendMode = BlendMode.SrcIn)
            }
    )
}

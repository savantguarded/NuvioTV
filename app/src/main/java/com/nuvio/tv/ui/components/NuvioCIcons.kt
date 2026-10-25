package com.nuvio.tv.ui.components

// [fork] Nuvio C round 29 `roulette_page`: the line icons as ImageVectors, for places that take a vector
// (sidebar entry, layout settings). Same paths as res/drawable nuvio_c_ic_dice.xml / nuvio_c_ic_shuffle.xml.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun line(name: String, pathData: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
        .build()

internal object NuvioCIcons {
    val Dice: ImageVector by lazy {
        line("NuvioCDice", "M3,5a2,2 0,0 1,2 -2h14a2,2 0,0 1,2 2v14a2,2 0,0 1,-2 2h-14a2,2 0,0 1,-2 -2zM8.5,8.5v0.01M15.5,8.5v0.01M8.5,15.5v0.01M15.5,15.5v0.01M12,12v0.01")
    }
    val Shuffle: ImageVector by lazy {
        line("NuvioCShuffle", "M18,4l3,3l-3,3M18,20l3,-3l-3,-3M3,7h3a5,5 0,0 1,5 5a5,5 0,0 0,5 5h5M21,7h-5a4.978,4.978 0,0 0,-3 1m-4,8a4.984,4.984 0,0 1,-3 1h-3")
    }
}

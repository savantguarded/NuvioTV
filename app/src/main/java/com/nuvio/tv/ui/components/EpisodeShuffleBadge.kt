package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.nuvio.tv.R

@Composable
internal fun EpisodeShuffleBadge(modifier: Modifier = Modifier) {
    // [fork] cw_shuffle_icon: Nuvio C line shuffle icon (same as the details Shuffle button)
    if (com.nuvio.tv.NuvioCFeatures.CW_SHUFFLE_ICON) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(R.drawable.nuvio_c_ic_shuffle),
            contentDescription = stringResource(R.string.shuffle_badge),
            tint = Color.White,
            modifier = modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.8f), CircleShape)
                .padding(6.dp).size(18.dp)
        )
        return
    }
    Icon(
        imageVector = com.nuvio.tv.ui.components.NuvioCIcons.Shuffle,
        contentDescription = stringResource(R.string.shuffle_badge),
        tint = Color.White,
        modifier = modifier.padding(8.dp).background(Color.Black.copy(alpha = 0.8f), CircleShape)
            .padding(6.dp).size(18.dp)
    )
}

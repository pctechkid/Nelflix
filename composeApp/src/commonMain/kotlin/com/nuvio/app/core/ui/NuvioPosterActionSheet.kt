package com.nuvio.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.episodes_cd_watched
import org.jetbrains.compose.resources.stringResource

@Composable
fun NuvioWatchedBadge(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(NetflixRed),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = stringResource(Res.string.episodes_cd_watched),
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
    }
}

private val NetflixRed = Color(0xFFE50914)

@Composable
fun NuvioAnimatedWatchedBadge(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        NuvioWatchedBadge()
    }
}

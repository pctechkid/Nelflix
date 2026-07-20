package com.nuvio.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import nuvio.composeapp.generated.resources.Res

@Composable
fun NuvioLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        val composition by rememberLottieComposition {
            LottieCompositionSpec.JsonString(
                Res.readBytes("files/nuvio_loading_indicator.json").decodeToString(),
            )
        }
        val progress by animateLottieCompositionAsState(
            composition = composition,
            iterations = Compottie.IterateForever,
        )

        Image(
            painter = rememberLottiePainter(
                composition = composition,
                progress = { progress },
            ),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = false
                },
        )
    }
}

@Composable
fun NuvioBlockingLoadingOverlay(
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Black.copy(alpha = 0.58f),
    color: Color = Color.White,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrimColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change -> change.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        NuvioLoadingIndicator(
            color = color,
            size = size,
        )
    }
}

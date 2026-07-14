package com.nuvio.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val InitialBlackFrameMillis = 1_250L
private const val InitialNFadeMillis = 120
private const val WordmarkRevealMillis = 1_450
private const val CompletedWordmarkHoldMillis = 350L
private const val WordmarkFadeMillis = 120
private const val FinalBlackFrameMillis = 750L
private const val BackgroundFadeMillis = 120

private const val WordmarkScale = 0.96f
private const val ArtworkPixelSize = 432f
private const val WordmarkLeftPixel = 70f
private const val WordmarkWidthPixels = 292f
private const val InitialNWidthPixels = 41f

private val NetflixRedColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0.53f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

@Composable
internal fun NelflixStartupSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revealProgress = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val backgroundAlpha = remember { Animatable(1f) }
    val latestOnFinished = rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        delay(InitialBlackFrameMillis)
        wordmarkAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = InitialNFadeMillis,
                easing = LinearEasing,
            ),
        )
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = WordmarkRevealMillis,
                easing = LinearEasing,
            ),
        )
        delay(CompletedWordmarkHoldMillis)
        wordmarkAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = WordmarkFadeMillis,
                easing = LinearEasing,
            ),
        )
        delay(FinalBlackFrameMillis)
        backgroundAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = BackgroundFadeMillis,
                easing = LinearEasing,
            ),
        )
        latestOnFinished.value()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = backgroundAlpha.value }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val artworkSize = minOf(maxWidth * 1.025f, maxHeight, 560.dp) * WordmarkScale
        val wordmarkWidth = artworkSize * (WordmarkWidthPixels / ArtworkPixelSize)
        val initialNWidth = artworkSize * (InitialNWidthPixels / ArtworkPixelSize)
        val revealWidth = initialNWidth + ((wordmarkWidth - initialNWidth) * revealProgress.value)
        val wordmarkLeftInset = artworkSize * (WordmarkLeftPixel / ArtworkPixelSize)
        val wordmarkPainter = painterResource(R.drawable.nelflix_splash_logo)

        Box(
            modifier = Modifier
                .requiredWidth(revealWidth)
                .requiredHeight(artworkSize)
                .offset(y = (-5).dp)
                .graphicsLayer { alpha = wordmarkAlpha.value }
                .clipToBounds(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val artworkSizePx = artworkSize.toPx()
                translate(left = -wordmarkLeftInset.toPx()) {
                    with(wordmarkPainter) {
                        draw(
                            size = Size(artworkSizePx, artworkSizePx),
                            colorFilter = NetflixRedColorFilter,
                        )
                    }
                }
            }
        }
    }
}

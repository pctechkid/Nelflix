package com.nuvio.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val INITIAL_BLACK_DELAY_MS = 100L
private const val LOGO_REVEAL_TOTAL_MS = 1_800
private const val FINAL_SETTLE_MS = 200
private const val FINAL_HOLD_MS = 200L
private const val MAX_TOTAL_SPLASH_LOGO_MS = 2_200L

// 1f keeps the first clipped N visually near center, then settles the full wordmark to center.
private const val CENTER_START_OFFSET_STRENGTH = 1f
private const val APP_LAUNCH_WORDMARK_WIDTH_FRACTION = 0.48f
private const val AppLogoWordmarkWidthPixels = 309f
private const val AppLogoWordmarkHeightPixels = 83f
private val AppLaunchWordmarkMaxWidth = 44.dp * (AppLogoWordmarkWidthPixels / AppLogoWordmarkHeightPixels)

private const val ArtworkPixelSize = 432f
private const val WordmarkLeftPixel = 70f
private const val WordmarkWidthPixels = 292f
private const val WordmarkTopPixel = 177f
private const val WordmarkBottomPixel = 254f
private const val InitialNWidthPixels = 41f

private enum class LetterRevealDirection {
    Horizontal,
    Vertical,
    Diagonal,
}

private data class LetterRevealSpec(
    val leftPx: Float,
    val rightPx: Float,
    val startMs: Int,
    val endMs: Int,
    val direction: LetterRevealDirection,
)

private val NelflixLetterRevealSpecs = listOf(
    LetterRevealSpec(70f, 110f, 0, 260, LetterRevealDirection.Vertical),
    LetterRevealSpec(121f, 155f, 220, 620, LetterRevealDirection.Horizontal),
    LetterRevealSpec(163f, 202f, 560, 940, LetterRevealDirection.Vertical),
    LetterRevealSpec(209f, 244f, 820, 1_190, LetterRevealDirection.Horizontal),
    LetterRevealSpec(251f, 285f, 1_100, 1_450, LetterRevealDirection.Vertical),
    LetterRevealSpec(295f, 308f, 1_330, 1_620, LetterRevealDirection.Vertical),
    LetterRevealSpec(317f, 361f, 1_520, LOGO_REVEAL_TOTAL_MS, LetterRevealDirection.Diagonal),
)

private val NelflixRedColorFilter = ColorFilter.colorMatrix(
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
    val latestOnFinished = rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        delay(INITIAL_BLACK_DELAY_MS)
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = LOGO_REVEAL_TOTAL_MS,
                easing = LinearEasing,
            ),
        )
        delay((MAX_TOTAL_SPLASH_LOGO_MS - LOGO_REVEAL_TOTAL_MS).coerceAtLeast(FINAL_SETTLE_MS + FINAL_HOLD_MS))
        latestOnFinished.value()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val wordmarkWidth = minOf(
            maxWidth * APP_LAUNCH_WORDMARK_WIDTH_FRACTION,
            AppLaunchWordmarkMaxWidth,
        )
        val artworkSize = wordmarkWidth * (ArtworkPixelSize / WordmarkWidthPixels)
        val initialNWidth = artworkSize * (InitialNWidthPixels / ArtworkPixelSize)
        val wordmarkLeftInset = artworkSize * (WordmarkLeftPixel / ArtworkPixelSize)
        val timelineMs = revealProgress.value * LOGO_REVEAL_TOTAL_MS
        val revealEase = FastOutSlowInEasing.transform(revealProgress.value)
        val revealWidth = initialNWidth + ((wordmarkWidth - initialNWidth) * revealEase)
        val horizontalOffset = ((wordmarkWidth - revealWidth) / 2f) * CENTER_START_OFFSET_STRENGTH
        val wordmarkPainter = painterResource(R.drawable.nelflix_splash_logo)

        Canvas(
            modifier = Modifier
                .requiredWidth(wordmarkWidth)
                .requiredHeight(artworkSize)
                .graphicsLayer {
                    translationX = horizontalOffset.toPx()
                    // Matches AppLaunchOverlay: logo center sits above screen center because the spinner is below it.
                    translationY = (-32).dp.toPx()
                }
                .clipToBounds(),
        ) {
            val artworkSizePx = artworkSize.toPx()
            val pixelScale = artworkSizePx / ArtworkPixelSize
            val visibleRight = revealWidth.toPx()
            val top = WordmarkTopPixel * pixelScale
            val bottom = WordmarkBottomPixel * pixelScale

            // Each letter uses the real logo asset clipped by its own mask so the final frame is identical.
            NelflixLetterRevealSpecs.forEach { spec ->
                val letterProgress = spec.revealProgress(timelineMs)
                if (letterProgress <= 0f) return@forEach

                val left = ((spec.leftPx - WordmarkLeftPixel) * pixelScale).coerceAtLeast(0f)
                val right = ((spec.rightPx + 1f - WordmarkLeftPixel) * pixelScale).coerceAtMost(visibleRight)
                if (right <= left) return@forEach

                val letterWidth = right - left
                val letterHeight = bottom - top
                val clip = when (spec.direction) {
                    LetterRevealDirection.Horizontal -> LetterClip(
                        left = left,
                        top = top,
                        right = left + (letterWidth * letterProgress),
                        bottom = bottom,
                    )
                    LetterRevealDirection.Vertical -> LetterClip(
                        left = left,
                        top = top,
                        right = right,
                        bottom = top + (letterHeight * letterProgress),
                    )
                    LetterRevealDirection.Diagonal -> {
                        val sweep = letterWidth + letterHeight
                        val edge = sweep * letterProgress
                        LetterClip(
                            left = left,
                            top = top,
                            right = (left + edge).coerceAtMost(right),
                            bottom = (top + edge).coerceAtMost(bottom),
                        )
                    }
                }
                if (clip.right <= clip.left || clip.bottom <= clip.top) return@forEach

                clipRect(
                    left = clip.left,
                    top = clip.top,
                    right = clip.right,
                    bottom = clip.bottom,
                ) {
                    translate(left = -wordmarkLeftInset.toPx()) {
                        with(wordmarkPainter) {
                            draw(
                                size = Size(artworkSizePx, artworkSizePx),
                                colorFilter = NelflixRedColorFilter,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class LetterClip(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private fun LetterRevealSpec.revealProgress(timelineMs: Float): Float {
    if (timelineMs <= startMs) return 0f
    if (timelineMs >= endMs) return 1f
    val raw = (timelineMs - startMs) / (endMs - startMs).toFloat()
    return FastOutSlowInEasing.transform(raw.coerceIn(0f, 1f))
}

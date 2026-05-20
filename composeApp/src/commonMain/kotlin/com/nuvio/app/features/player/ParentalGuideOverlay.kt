package com.nuvio.app.features.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val ParentalGuideBarHeight = 46.dp

@Composable
internal fun ParentalGuideOverlay(
    ratingCode: String?,
    genresLine: String?,
    warnings: List<ParentalWarning>,
    isVisible: Boolean,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 32.dp, top = 24.dp),
) {
    val normalizedRating = ratingCode?.trim()?.takeIf { it.isNotBlank() }
    val normalizedGenres = genresLine?.trim()?.takeIf { it.isNotBlank() }
    if (warnings.isEmpty() && normalizedRating == null && normalizedGenres == null) return

    val headline = normalizedRating?.let(::formatMaturityRating) ?: inferMaturityRating(warnings)
    val themes = normalizedGenres ?: warnings.joinToString(", ") { it.label }
    val containerAlpha = remember { Animatable(0f) }
    val lineHeightFraction = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    var animating by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible && !animating) {
            animating = true
            containerAlpha.animateTo(1f, tween(220))
            lineHeightFraction.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
            textAlpha.animateTo(1f, tween(220))

            delay(5000)

            textAlpha.animateTo(0f, tween(150))
            lineHeightFraction.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            containerAlpha.animateTo(0f, tween(200))

            animating = false
            onAnimationComplete()
        } else if (!isVisible && animating) {
            textAlpha.snapTo(0f)
            lineHeightFraction.snapTo(0f)
            containerAlpha.snapTo(0f)
            animating = false
            onAnimationComplete()
        }
    }

    if (containerAlpha.value <= 0f) return

    Row(
        modifier = modifier
            .alpha(containerAlpha.value)
            .padding(contentPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(ParentalGuideBarHeight * lineHeightFraction.value)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFFE50914)),
        )

        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = headline,
                modifier = Modifier.alpha(textAlpha.value),
                fontSize = 17.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            if (themes.isNotBlank()) {
                Text(
                    text = themes,
                    modifier = Modifier
                        .alpha(textAlpha.value)
                        .padding(top = 1.dp),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun inferMaturityRating(warnings: List<ParentalWarning>): String =
    if (warnings.any { it.severity.equals("severe", ignoreCase = true) }) "TV-MA" else "PG-13"

private fun formatMaturityRating(value: String): String {
    val trimmed = value.trim()
    return if (trimmed.startsWith("Rated", ignoreCase = true)) trimmed else "Rated $trimmed"
}

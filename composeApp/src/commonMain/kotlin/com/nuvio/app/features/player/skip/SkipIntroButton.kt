package com.nuvio.app.features.player.skip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_skip
import nuvio.composeapp.generated.resources.player_skip_intro
import nuvio.composeapp.generated.resources.player_skip_outro
import nuvio.composeapp.generated.resources.player_skip_recap
import org.jetbrains.compose.resources.stringResource

@Composable
fun SkipIntroButton(
    interval: SkipInterval?,
    dismissed: Boolean,
    controlsVisible: Boolean,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastType by remember { mutableStateOf(interval?.type) }
    if (interval != null) lastType = interval.type
    val shouldShow = interval != null && (!dismissed || controlsVisible)

    var autoHidden by remember { mutableStateOf(false) }
    var manuallyDismissed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(interval?.startTime, interval?.type) {
        autoHidden = false
        manuallyDismissed = false
        progress.snapTo(0f)
    }

    LaunchedEffect(dismissed) {
        if (!dismissed) {
            if (!manuallyDismissed) {
                autoHidden = false
                progress.snapTo(0f)
            }
        } else {
            manuallyDismissed = true
        }
    }

    LaunchedEffect(shouldShow, autoHidden, controlsVisible) {
        if (shouldShow && !autoHidden && !controlsVisible) {
            progress.animateTo(
                1f,
                animationSpec = tween(
                    durationMillis = ((1f - progress.value) * 10000).toInt().coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
            autoHidden = true
        }
    }

    val isVisible = shouldShow && (!autoHidden || controlsVisible)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.8f),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.8f),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(5.dp)
        Box(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .clip(shape)
                .background(Color.Transparent)
                .border(width = 1.25.dp, color = Color.White, shape = shape)
                .clickable { onSkip() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = skipLabel(lastType).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun skipLabel(type: String?): String =
    when (type?.lowercase()) {
        "intro", "op", "mixed-op" -> stringResource(Res.string.player_skip_intro)
        "outro", "ed", "mixed-ed", "credits" -> stringResource(Res.string.player_skip_outro)
        "recap" -> stringResource(Res.string.player_skip_recap)
        else -> stringResource(Res.string.player_skip)
    }

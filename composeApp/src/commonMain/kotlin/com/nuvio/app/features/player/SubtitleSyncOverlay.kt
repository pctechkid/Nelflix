package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SUBTITLE_SYNC_STEP_MS = 500L
private const val SUBTITLE_SYNC_REPEAT_DELAY_MS = 320L
private const val SUBTITLE_SYNC_REPEAT_INTERVAL_MS = 120L

@Composable
internal fun SubtitleSyncOverlay(
    visible: Boolean,
    delayMs: Long,
    onDelayChanged: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(140)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Subtitle sync",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                RoundIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close subtitle sync",
                    onClick = onClose,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = "Decrease subtitle delay",
                    onClick = { onDelayChanged(delayMs - SUBTITLE_SYNC_STEP_MS) },
                    onLongPressStep = { onDelayChanged(delayMs - SUBTITLE_SYNC_STEP_MS) },
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.32f), RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Delay",
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 11.sp,
                            lineHeight = 12.sp,
                        )
                        Text(
                            text = delayMs.formatSubtitleDelay(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = "ms",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 14.sp,
                    )
                }

                RoundIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Increase subtitle delay",
                    onClick = { onDelayChanged(delayMs + SUBTITLE_SYNC_STEP_MS) },
                    onLongPressStep = { onDelayChanged(delayMs + SUBTITLE_SYNC_STEP_MS) },
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Reset",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(40.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onDelayChanged(0L) }
                    .padding(vertical = 9.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    onLongPressStep: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var repeatJob by remember { androidx.compose.runtime.mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            repeatJob?.cancel()
            repeatJob = null
        }
    }

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = Color.White,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .pointerInput(onClick, onLongPressStep) {
                if (onLongPressStep == null) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onClick()
                        waitForUpOrCancellation()
                    }
                } else {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onClick()
                        val job = scope.launch {
                            delay(SUBTITLE_SYNC_REPEAT_DELAY_MS)
                            while (isActive) {
                                onLongPressStep()
                                delay(SUBTITLE_SYNC_REPEAT_INTERVAL_MS)
                            }
                        }
                        repeatJob = job
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            job.cancel()
                            if (repeatJob === job) {
                                repeatJob = null
                            }
                        }
                    }
                }
            }
            .padding(5.dp),
    )
}

private fun Long.formatSubtitleDelay(): String =
    when {
        this > 0L -> "+$this"
        else -> toString()
    }

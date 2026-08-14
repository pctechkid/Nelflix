package com.nuvio.app.features.player.skip

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_cancel
import nuvio.composeapp.generated.resources.player_next_episode_countdown
import nuvio.composeapp.generated.resources.player_next_episode_title_format
import nuvio.composeapp.generated.resources.player_next_episode_play_now
import nuvio.composeapp.generated.resources.player_next_episode_preparing
import nuvio.composeapp.generated.resources.player_next_episode_ready
import nuvio.composeapp.generated.resources.player_next_episode_thumbnail
import nuvio.composeapp.generated.resources.player_next_episode_up_next
import org.jetbrains.compose.resources.stringResource

@Composable
fun NextEpisodeCard(
    nextEpisode: NextEpisodeInfo?,
    visible: Boolean,
    isAutoPlaySearching: Boolean,
    autoPlayCountdownSec: Int?,
    countdownProgress: Float,
    canPlayNow: Boolean,
    onPlayNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (nextEpisode == null) return

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(360), initialOffsetY = { it / 2 }) +
            fadeIn(animationSpec = tween(260)),
        exit = slideOutVertically(animationSpec = tween(240), targetOffsetY = { it / 2 }) +
            fadeOut(animationSpec = tween(180)),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(13.dp)
        val animatedCountdownProgress by animateFloatAsState(
            targetValue = if (autoPlayCountdownSec != null) {
                countdownProgress.coerceIn(0f, 1f)
            } else {
                0f
            },
            animationSpec = tween(
                durationMillis = if (countdownProgress >= 0.999f) 0 else 900,
                easing = LinearEasing,
            ),
            label = "nextEpisodeCountdown",
        )
        Column(
            modifier = Modifier
                .widthIn(min = 292.dp, max = 312.dp)
                .shadow(12.dp, shape)
                .clip(shape)
                .background(Color(0xF70A0A0B)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF242424)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!nextEpisode.thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = nextEpisode.thumbnail,
                            contentDescription = stringResource(Res.string.player_next_episode_thumbnail),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.28f),
                                ),
                            ),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.player_next_episode_up_next),
                        color = Color(0xFFE50914),
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                    )
                    Text(
                        text = stringResource(
                            Res.string.player_next_episode_title_format,
                            nextEpisode.season,
                            nextEpisode.episode,
                            nextEpisode.title,
                        ),
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val status = when {
                        isAutoPlaySearching -> stringResource(Res.string.player_next_episode_preparing)
                        autoPlayCountdownSec != null -> stringResource(
                            Res.string.player_next_episode_countdown,
                            autoPlayCountdownSec,
                        )
                        else -> stringResource(Res.string.player_next_episode_ready)
                    }
                    Text(
                        text = status,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NextEpisodeActionButton(
                            label = stringResource(Res.string.player_next_episode_play_now),
                            enabled = canPlayNow,
                            onClick = onPlayNext,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(Res.string.action_cancel).uppercase(),
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 10.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.12f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedCountdownProgress)
                        .height(3.dp)
                        .background(
                            Color(0xFFE50914),
                        ),
                )
            }
        }
    }
}

@Composable
private fun NextEpisodeActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = modifier
            .height(30.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(Color(0xFFE50914))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = label.uppercase(),
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

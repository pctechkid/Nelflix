package com.nuvio.app.features.player

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.AppIconResource
import com.nuvio.app.core.ui.appIconPainter
import com.nuvio.app.core.ui.nuvioTypeScale
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayerControlsShell(
    title: String,
    logo: String?,
    streamTitle: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    isLocked: Boolean,
    showPlaybackControls: Boolean = true,
    playbackControlsVisible: Boolean = showPlaybackControls,
    showHeaderMetadata: Boolean = true,
    showStartupTitleIntro: Boolean = false,
    startupTitleIntroVisible: Boolean = showStartupTitleIntro,
    metadataInfoOnly: Boolean = false,
    onLockToggle: () -> Unit,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onResizeModeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSilenceSkipClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onChaptersClick: (() -> Unit)? = null,
    onSourcesClick: (() -> Unit)? = null,
    onWatchTogetherClick: (() -> Unit)? = null,
    onWatchTogetherInfoClick: (() -> Unit)? = null,
    onSubmitIntroClick: (() -> Unit)? = null,
    maturityRatingCode: String? = null,
    maturityGenresLine: String? = null,
    parentalWarnings: List<ParentalWarning> = emptyList(),
    showParentalGuide: Boolean = false,
    onParentalGuideAnimationComplete: () -> Unit = {},
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    horizontalSafePadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val controlsBackdropAlpha by animateFloatAsState(
            targetValue = if (showPlaybackControls && !metadataInfoOnly) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (showPlaybackControls) 220 else 420,
                easing = FastOutSlowInEasing,
            ),
            label = "playerControlsBackdropAlpha",
        )
        if (controlsBackdropAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = controlsBackdropAlpha },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.24f),
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.58f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.64f),
                                ),
                            ),
                        ),
                )
            }
        }

        val introBackdropAlpha by animateFloatAsState(
            targetValue = if ((showParentalGuide || showStartupTitleIntro) && !showPlaybackControls && !metadataInfoOnly) 1f else 0f,
            animationSpec = tween(durationMillis = if (showParentalGuide || showStartupTitleIntro) 180 else 1000, easing = FastOutSlowInEasing),
            label = "playerIntroBackdropAlpha",
        )
        if (introBackdropAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = introBackdropAlpha }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.66f),
                                Color.Black.copy(alpha = 0.48f),
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.04f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalSafePadding),
        ) {
            PlayerHeader(
                title = title,
                logo = logo,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                episodeTitle = episodeTitle,
                metrics = metrics,
                isLocked = isLocked,
                actionsVisible = playbackControlsVisible,
                showHeaderMetadata = showHeaderMetadata,
                showStartupTitleIntro = showStartupTitleIntro,
                startupTitleIntroVisible = startupTitleIntroVisible,
                metadataInfoOnly = metadataInfoOnly,
                onWatchTogetherClick = onWatchTogetherClick,
                onWatchTogetherInfoClick = onWatchTogetherInfoClick,
                onSubmitIntroClick = onSubmitIntroClick,
                maturityRatingCode = maturityRatingCode,
                maturityGenresLine = maturityGenresLine,
                parentalWarnings = parentalWarnings,
                showParentalGuide = showParentalGuide,
                onParentalGuideAnimationComplete = onParentalGuideAnimationComplete,
                onLockToggle = onLockToggle,
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Top))
                    .padding(
                        start = metrics.horizontalPadding,
                        end = metrics.horizontalPadding,
                        top = metrics.verticalPadding / 4,
                    ),
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = playbackControlsVisible,
                enter = slideInVertically(playerControlsEnterAnimationSpec()) { it },
                exit = slideOutVertically(playerControlsExitAnimationSpec()) { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = metrics.horizontalPadding)
                    .padding(bottom = metrics.sliderBottomOffset / 2),
            ) {
                ProgressControls(
                    playbackSnapshot = playbackSnapshot,
                    displayedPositionMs = displayedPositionMs,
                    metrics = metrics,
                    resizeMode = resizeMode,
                    onTogglePlayback = onTogglePlayback,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onScrubChange = onScrubChange,
                    onScrubFinished = onScrubFinished,
                    onResizeModeClick = onResizeModeClick,
                    onSpeedClick = onSpeedClick,
                    onSilenceSkipClick = onSilenceSkipClick,
                    onSubtitleClick = onSubtitleClick,
                    onAudioClick = onAudioClick,
                    onChaptersClick = onChaptersClick,
                    onSourcesClick = onSourcesClick,
                    onInfoClick = onWatchTogetherInfoClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PlayerHeader(
    title: String,
    logo: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
    episodeTitle: String?,
    metrics: PlayerLayoutMetrics,
    isLocked: Boolean,
    actionsVisible: Boolean,
    showHeaderMetadata: Boolean,
    showStartupTitleIntro: Boolean,
    startupTitleIntroVisible: Boolean,
    metadataInfoOnly: Boolean,
    onWatchTogetherClick: (() -> Unit)?,
    onWatchTogetherInfoClick: (() -> Unit)?,
    onSubmitIntroClick: (() -> Unit)?,
    maturityRatingCode: String?,
    maturityGenresLine: String?,
    parentalWarnings: List<ParentalWarning>,
    showParentalGuide: Boolean,
    onParentalGuideAnimationComplete: () -> Unit,
    onLockToggle: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalHeaderVisible = showHeaderMetadata &&
        !showParentalGuide &&
        actionsVisible &&
        !metadataInfoOnly
    Column(modifier = modifier) {
        val episodeLine = if (seasonNumber != null && episodeNumber != null && !episodeTitle.isNullOrBlank()) {
            "S${seasonNumber}E${episodeNumber}: $episodeTitle"
        } else {
            null
        }
        var logoLoadFailed by remember(logo) { mutableStateOf(false) }
        LaunchedEffect(logo) {
            logoLoadFailed = false
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .align(Alignment.TopStart),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = normalHeaderVisible,
                    enter = slideInVertically(playerControlsEnterAnimationSpec()) { -it },
                    exit = slideOutVertically(playerControlsExitAnimationSpec()) { -it },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!logo.isNullOrBlank() && !logoLoadFailed) {
                            AsyncImage(
                                model = logo,
                                contentDescription = title,
                                modifier = Modifier
                                    .height(if (seasonNumber != null && episodeNumber != null) 64.dp else 76.dp)
                                    .fillMaxWidth(),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart,
                                onError = { logoLoadFailed = true },
                            )
                        } else {
                            NetflixHeaderTitle(
                                title = title,
                                episodeLine = episodeLine,
                                fontSize = metrics.episodeInfoSize * 1.08f,
                                episodeFontSize = metrics.episodeInfoSize,
                                animateContent = false,
                            )
                        }
                        if (episodeLine != null && !logo.isNullOrBlank() && !logoLoadFailed) {
                            PlayerHeaderEpisodeLine(
                                text = episodeLine,
                                fontSize = metrics.episodeInfoSize,
                            )
                        }
                    }
                }
                ParentalGuideOverlay(
                    ratingCode = maturityRatingCode,
                    genresLine = maturityGenresLine,
                    warnings = parentalWarnings,
                    isVisible = showParentalGuide,
                    onAnimationComplete = onParentalGuideAnimationComplete,
                    contentPadding = PaddingValues(0.dp),
                    ratingFontSize = metrics.episodeInfoSize * 1.08f,
                    genresFontSize = metrics.episodeInfoSize,
                )
                if (showStartupTitleIntro && !metadataInfoOnly) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        NetflixHeaderTitle(
                            title = title,
                            episodeLine = episodeLine,
                            fontSize = metrics.episodeInfoSize * 1.08f,
                            episodeFontSize = metrics.episodeInfoSize,
                            visible = startupTitleIntroVisible,
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = actionsVisible || metadataInfoOnly,
                enter = slideInVertically(playerControlsEnterAnimationSpec()) { -it },
                exit = slideOutVertically(playerControlsExitAnimationSpec()) { -it },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!metadataInfoOnly && onWatchTogetherClick != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.Outlined.Groups,
                            contentDescription = "Watch Together",
                            buttonSize = metrics.headerIconSize + 18.dp,
                            iconSize = metrics.headerIconSize + 4.dp,
                            onClick = onWatchTogetherClick,
                        )
                    }
                    if (!metadataInfoOnly && onSubmitIntroClick != null) {
                        PlayerHeaderIconButton(
                            icon = Icons.Outlined.Flag,
                            contentDescription = "Submit Intro",
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onSubmitIntroClick,
                        )
                    }
                    if (!metadataInfoOnly) {
                        PlayerHeaderIconButton(
                            icon = if (isLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                            contentDescription = if (isLocked) {
                                stringResource(Res.string.compose_player_unlock_controls)
                            } else {
                                stringResource(Res.string.compose_player_lock_controls)
                            },
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onLockToggle,
                        )
                        PlayerHeaderIconButton(
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.compose_player_close),
                            buttonSize = metrics.headerIconSize + 16.dp,
                            iconSize = metrics.headerIconSize,
                            onClick = onBack,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHeaderEpisodeLine(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.nuvioTypeScale.bodyMd.copy(
            fontSize = fontSize,
            lineHeight = fontSize * 1.3f,
        ),
        color = Color.White.copy(alpha = 0.9f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun NetflixHeaderTitle(
    title: String,
    episodeLine: String?,
    fontSize: androidx.compose.ui.unit.TextUnit,
    episodeFontSize: androidx.compose.ui.unit.TextUnit,
    visible: Boolean = true,
    animateContent: Boolean = true,
) {
    var accentVisible by remember(title, episodeLine) { mutableStateOf(false) }
    val accentScaleY by animateFloatAsState(
        targetValue = if (!animateContent || (accentVisible && visible)) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (!animateContent) 0 else if (visible) 760 else 1700,
            easing = FastOutSlowInEasing,
        ),
        label = "playerTitleAccentScale",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (!animateContent || (accentVisible && visible)) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (!animateContent) 0 else if (visible) 720 else 1500,
            delayMillis = if (animateContent && visible) 110 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "playerTitleTextAlpha",
    )
    val textOffset by animateFloatAsState(
        targetValue = if (!animateContent || (accentVisible && visible)) 0f else -18f,
        animationSpec = tween(
            durationMillis = if (!animateContent) 0 else if (visible) 720 else 1500,
            delayMillis = if (animateContent && visible) 110 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "playerTitleTextOffset",
    )
    val episodeInsideAccent = !episodeLine.isNullOrBlank()
    LaunchedEffect(title, episodeLine) {
        accentVisible = true
    }

    Column(
        modifier = Modifier
            .graphicsLayer {
                alpha = textAlpha
                translationX = textOffset
            },
        verticalArrangement = Arrangement.spacedBy(if (episodeInsideAccent) 1.dp else 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = accentScaleY
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .clip(RoundedCornerShape(1.dp))
                    .background(NetflixProgressRed),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (episodeInsideAccent) 0.dp else 2.dp),
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.05f,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp, top = 4.dp, bottom = if (episodeInsideAccent) 0.dp else 4.dp)
                        .fillMaxWidth(),
                )
                if (!episodeLine.isNullOrBlank() && episodeInsideAccent) {
                    Text(
                        text = episodeLine,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = episodeFontSize,
                            lineHeight = episodeFontSize * 1.25f,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 8.dp, bottom = 3.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun CenterControls(
    snapshot: PlayerPlaybackSnapshot,
    metrics: PlayerLayoutMetrics,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.centerGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideControlButton(
            icon = Icons.Rounded.Replay10,
            contentDescription = stringResource(Res.string.compose_player_seek_back_10),
            metrics = metrics,
            onClick = onSeekBack,
        )
        PlayPauseControlButton(
            isPlaying = snapshot.isPlaying,
            isBuffering = snapshot.isLoading,
            metrics = metrics,
            onClick = onTogglePlayback,
        )
        SideControlButton(
            icon = Icons.Rounded.Forward10,
            contentDescription = stringResource(Res.string.compose_player_seek_forward_10),
            metrics = metrics,
            onClick = onSeekForward,
        )
    }
}

@Composable
private fun SideControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    metrics: PlayerLayoutMetrics,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(metrics.sideButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(metrics.playIconSize),
        )
    }
}

@Composable
private fun PlayPauseControlButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    metrics: PlayerLayoutMetrics,
    onClick: () -> Unit,
) {
    val playPausePainter = appIconPainter(
        if (isPlaying) AppIconResource.PlayerPause else AppIconResource.PlayerPlay,
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(metrics.playButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(metrics.playIconSize),
            )
        } else {
            Icon(
                painter = playPausePainter,
                contentDescription = if (isPlaying) {
                    stringResource(Res.string.compose_action_pause)
                } else {
                    stringResource(Res.string.detail_btn_play)
                },
                tint = Color.White,
                modifier = Modifier.size(metrics.playIconSize),
            )
        }
    }
}

@Composable
private fun ProgressControls(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    resizeMode: PlayerResizeMode,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    onResizeModeClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onSilenceSkipClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onAudioClick: () -> Unit,
    onChaptersClick: (() -> Unit)? = null,
    onSourcesClick: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val durationMs = playbackSnapshot.durationMs.coerceAtLeast(1L)
    val bufferedFraction = (playbackSnapshot.bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val playedFraction = (displayedPositionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val durationText = formatPlaybackTime(durationMs)
    val timeLabelWidth = if (durationText.length > 5) 64.dp else 44.dp
    val actionButtonSize = metrics.headerIconSize + 20.dp
    val actionIconSize = metrics.headerIconSize + 4.dp

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackTimeLabel(
                text = formatPlaybackTime(displayedPositionMs),
                fontSize = metrics.timeSize,
                modifier = Modifier.width(timeLabelWidth),
                textAlign = TextAlign.Start,
            )
            NetflixSeekBar(
                modifier = Modifier.weight(1f),
                durationMs = durationMs,
                playedFraction = playedFraction,
                bufferedFraction = bufferedFraction,
                touchHeight = metrics.sliderTouchHeight,
                onScrubChange = onScrubChange,
                onScrubFinished = onScrubFinished,
            )
            PlaybackTimeLabel(
                text = durationText,
                fontSize = metrics.timeSize,
                modifier = Modifier.width(timeLabelWidth),
                textAlign = TextAlign.End,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    PlayerActionBarButton(
                        label = if (playbackSnapshot.isPlaying) {
                            stringResource(Res.string.compose_action_pause)
                        } else {
                            stringResource(Res.string.detail_btn_play)
                        },
                        icon = if (playbackSnapshot.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        onClick = onTogglePlayback,
                    )
                    PlayerActionBarButton(
                        label = stringResource(Res.string.compose_player_seek_back_10),
                        icon = Icons.Outlined.Replay10,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        onClick = onSeekBack,
                    )
                    PlayerActionBarButton(
                        label = stringResource(Res.string.compose_player_seek_forward_10),
                        icon = Icons.Outlined.Forward10,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        onClick = onSeekForward,
                    )
                    PlayerActionBarButton(
                        label = "Skip",
                        icon = Icons.Outlined.SkipNext,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        onClick = onSilenceSkipClick,
                    )
                    PlayerActionBarButton(
                        label = formatPlaybackSpeedLabel(playbackSnapshot.playbackSpeed),
                        icon = Icons.Outlined.Speed,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        onClick = onSpeedClick,
                    )
            }
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    if (onInfoClick != null) {
                        PlayerActionBarButton(
                            label = "Playback info",
                            icon = Icons.Outlined.Info,
                            buttonSize = actionButtonSize,
                            iconSize = actionIconSize,
                            onClick = onInfoClick,
                        )
                    }
                    PlayerActionBarButton(
                        label = stringResource(Res.string.compose_player_audio),
                        icon = Icons.Outlined.VolumeUp,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        onClick = onAudioClick,
                    )
                    if (onChaptersClick != null) {
                        PlayerActionBarButton(
                            label = "Chapters",
                            icon = Icons.Outlined.FilterNone,
                            buttonSize = actionButtonSize,
                            iconSize = actionIconSize,
                            onClick = onChaptersClick,
                        )
                    }
                    if (onSourcesClick != null) {
                        PlayerActionBarButton(
                            label = stringResource(Res.string.compose_player_sources),
                            icon = Icons.Rounded.SwapHoriz,
                            buttonSize = actionButtonSize,
                            iconSize = actionIconSize,
                            onClick = onSourcesClick,
                        )
                    }
                    PlayerActionBarButton(
                        label = stringResource(Res.string.compose_player_subs),
                        icon = Icons.Outlined.Subtitles,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        onClick = onSubtitleClick,
                    )
                    PlayerActionBarButton(
                        label = stringResource(resizeMode.labelRes),
                        icon = Icons.Rounded.AspectRatio,
                        buttonSize = actionButtonSize,
                        iconSize = actionIconSize,
                        onClick = onResizeModeClick,
                    )
            }
        }
    }
}

private fun <T> playerControlsEnterAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 320,
    easing = LinearOutSlowInEasing,
)

private fun <T> playerControlsExitAnimationSpec(): FiniteAnimationSpec<T> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing,
)

@Composable
private fun NetflixSeekBar(
    modifier: Modifier = Modifier,
    durationMs: Long,
    playedFraction: Float,
    bufferedFraction: Float,
    touchHeight: androidx.compose.ui.unit.Dp,
    onScrubChange: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
) {
    val density = LocalDensity.current
    var activeScrubFraction by remember(durationMs) { mutableStateOf<Float?>(null) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = maxWidth
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val markerWidth = 4.dp
        val markerHeight = 23.dp
        val segmentGap = 8.dp
        val trackHeight = 14.dp
        val protectedHalfFraction = with(density) {
            ((markerWidth.toPx() / 2f) + segmentGap.toPx()) / widthPx
        }
        val effectivePlayedFraction = activeScrubFraction ?: playedFraction.coerceIn(0f, 1f)
        val playedSegmentFraction = (effectivePlayedFraction - protectedHalfFraction).coerceIn(0f, 1f)
        val bufferedTrackFraction = bufferedFraction.coerceAtLeast(effectivePlayedFraction)
        val bufferedStartFraction = (effectivePlayedFraction + protectedHalfFraction).coerceIn(0f, 1f)
        val bufferedVisibleFraction = (bufferedTrackFraction - bufferedStartFraction).coerceAtLeast(0f)
        val remainingVisibleFraction = (1f - bufferedStartFraction).coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(touchHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .width(trackWidth * playedSegmentFraction)
                    .height(trackHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = 999.dp,
                            bottomStart = 999.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp,
                        ),
                    )
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * bufferedStartFraction)
                    .width(trackWidth * remainingVisibleFraction)
                    .height(trackHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp,
                            bottomStart = 0.dp,
                            topEnd = 999.dp,
                            bottomEnd = 999.dp,
                        ),
                    )
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            Box(
                modifier = Modifier
                    .offset(x = trackWidth * bufferedStartFraction)
                    .width(trackWidth * bufferedVisibleFraction)
                    .height(trackHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp,
                            bottomStart = 0.dp,
                            topEnd = 999.dp,
                            bottomEnd = 999.dp,
                        ),
                    )
                    .background(Color.White.copy(alpha = 0.5f)),
            )
            Box(
                modifier = Modifier
                    .width(trackWidth * playedSegmentFraction)
                    .height(trackHeight)
                    .clip(
                        RoundedCornerShape(
                            topStart = 999.dp,
                            bottomStart = 999.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp,
                        ),
                    )
                    .background(NetflixProgressRed),
            )
            Box(
                modifier = Modifier
                    .offset(x = (trackWidth * effectivePlayedFraction) - (markerWidth / 2))
                    .width(markerWidth)
                    .height(markerHeight),
            ) {
                Box(
                    modifier = Modifier
                        .width(markerWidth)
                        .height(markerHeight)
                        .clip(RoundedCornerShape(999.dp))
                        .background(NetflixProgressRed),
                )
            }
            Slider(
                value = effectivePlayedFraction,
                onValueChange = { fraction ->
                    val clampedFraction = fraction.coerceIn(0f, 1f)
                    activeScrubFraction = clampedFraction
                    onScrubChange(
                        (durationMs * clampedFraction).toLong().coerceIn(0L, durationMs),
                    )
                },
                onValueChangeFinished = {
                    val finalFraction = activeScrubFraction ?: effectivePlayedFraction
                    onScrubFinished(
                        (durationMs * finalFraction).toLong().coerceIn(0L, durationMs),
                    )
                    activeScrubFraction = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(touchHeight),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                ),
            )
        }
    }
}

private val NetflixProgressRed = Color(0xFFE50914)

@Composable
internal fun LockedPlayerOverlay(
    playbackSnapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    metrics: PlayerLayoutMetrics,
    horizontalSafePadding: androidx.compose.ui.unit.Dp,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = playbackSnapshot.durationMs.coerceAtLeast(1L)
    val sliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = NetflixProgressRed,
        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
        disabledThumbColor = Color.White,
        disabledActiveTrackColor = NetflixProgressRed,
        disabledInactiveTrackColor = Color.White.copy(alpha = 0.22f),
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable(onClick = onUnlock),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = stringResource(Res.string.compose_player_unlock_controls),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.compose_player_tap_to_unlock),
                style = MaterialTheme.nuvioTypeScale.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.92f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = horizontalSafePadding + metrics.horizontalPadding)
                .padding(bottom = metrics.sliderBottomOffset),
        ) {
            Slider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.sliderTouchHeight)
                    .graphicsLayer(scaleY = metrics.sliderScaleY),
                value = displayedPositionMs.coerceIn(0L, durationMs).toFloat(),
                onValueChange = {},
                onValueChangeFinished = {},
                valueRange = 0f..durationMs.toFloat(),
                enabled = false,
                colors = sliderColors,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimePill(text = formatPlaybackTime(displayedPositionMs), fontSize = metrics.timeSize)
                TimePill(text = formatPlaybackTime(durationMs), fontSize = metrics.timeSize)
            }
        }
    }
}

@Composable
private fun TimePill(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.nuvioTypeScale.labelSm.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.25f,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun PlaybackTimeLabel(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.nuvioTypeScale.labelSm.copy(
            fontSize = fontSize,
            lineHeight = fontSize * 1.2f,
            fontWeight = FontWeight.Medium,
        ),
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
    )
}

@Composable
private fun PlayerActionBarButton(
    label: String,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    icon: ImageVector? = null,
    painter: Painter? = null,
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            painter != null -> Icon(
                painter = painter,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

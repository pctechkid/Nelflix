package com.nuvio.app.features.details.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.player.EnterImmersivePlayerMode
import com.nuvio.app.features.player.LockPlayerToLandscape
import com.nuvio.app.features.player.PlatformPlayerSurface
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.detail_tab_trailer
import nuvio.composeapp.generated.resources.trailer_close
import nuvio.composeapp.generated.resources.trailer_enter_fullscreen
import nuvio.composeapp.generated.resources.trailer_exit_fullscreen
import nuvio.composeapp.generated.resources.trailer_unable_to_play
import org.jetbrains.compose.resources.stringResource

@Composable
fun TrailerPlayerPopup(
    visible: Boolean,
    trailerTitle: String,
    trailerType: String,
    contentTitle: String,
    playbackSource: TrailerPlaybackSource?,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    if (!visible) return

    val headerType = trailerType.trim().ifBlank { stringResource(Res.string.detail_tab_trailer) }
    val headerSubtitle = buildList {
        if (trailerTitle.isNotBlank() && !trailerTitle.equals(headerType, ignoreCase = true)) {
            add(trailerTitle)
        }
        if (contentTitle.isNotBlank()) {
            add(contentTitle)
        }
    }.joinToString(separator = " • ")

    var playerError by remember(playbackSource?.videoUrl, playbackSource?.audioUrl) {
        mutableStateOf<String?>(null)
    }
    var fullscreen by remember(playbackSource?.videoUrl, playbackSource?.audioUrl) {
        mutableStateOf(false)
    }

    val activeError = errorMessage ?: playerError
    if (fullscreen) {
        LockPlayerToLandscape()
        EnterImmersivePlayerMode(keepScreenAwake = true)
    }

    PlatformBackHandler(enabled = true) {
        if (fullscreen) {
            fullscreen = false
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (fullscreen) {
                fullscreen = false
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        TrailerPlayerDialogContent(
            fullscreen = fullscreen,
            headerType = headerType,
            headerSubtitle = headerSubtitle,
            playbackSource = playbackSource,
            isLoading = isLoading,
            activeError = activeError,
            onRetry = onRetry,
            onPlayerError = { playerError = it },
            onToggleFullscreen = { fullscreen = !fullscreen },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun TrailerPlayerDialogContent(
    fullscreen: Boolean,
    headerType: String,
    headerSubtitle: String,
    playbackSource: TrailerPlaybackSource?,
    isLoading: Boolean,
    activeError: String?,
    onRetry: (() -> Unit)?,
    onPlayerError: (String?) -> Unit,
    onToggleFullscreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (fullscreen) {
                    Color.Black
                } else {
                    MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)
                },
            ),
    ) {
        val layout = remember(maxWidth, maxHeight, fullscreen) {
            trailerPopupLayout(maxWidth = maxWidth, maxHeight = maxHeight, fullscreen = fullscreen)
        }
        val playerContent = remember(playbackSource?.videoUrl, playbackSource?.audioUrl) {
            movableContentOf<TrailerPlayerFrameArgs> { args ->
                TrailerPlayerFrame(
                    modifier = args.modifier,
                    shape = args.shape,
                    clipPlayer = args.clipPlayer,
                    isLoading = args.isLoading,
                    activeError = args.activeError,
                    onRetry = args.onRetry,
                    playbackSource = args.playbackSource,
                    onPlayerError = args.onPlayerError,
                )
            }
        }

        when (layout) {
            TrailerPopupLayout.Fullscreen -> FullscreenTrailerLayout(
                headerType = headerType,
                headerSubtitle = headerSubtitle,
                playerContent = playerContent,
                playbackSource = playbackSource,
                isLoading = isLoading,
                activeError = activeError,
                onRetry = onRetry,
                onPlayerError = onPlayerError,
                onToggleFullscreen = onToggleFullscreen,
                onDismiss = onDismiss,
            )

            TrailerPopupLayout.CompactLandscape -> CompactLandscapeTrailerLayout(
                headerType = headerType,
                headerSubtitle = headerSubtitle,
                playerContent = playerContent,
                playbackSource = playbackSource,
                isLoading = isLoading,
                activeError = activeError,
                onRetry = onRetry,
                onPlayerError = onPlayerError,
                onToggleFullscreen = onToggleFullscreen,
                onDismiss = onDismiss,
            )

            TrailerPopupLayout.CompactPortrait -> CompactPortraitTrailerLayout(
                maxHeight = maxHeight,
                headerType = headerType,
                headerSubtitle = headerSubtitle,
                playerContent = playerContent,
                playbackSource = playbackSource,
                isLoading = isLoading,
                activeError = activeError,
                onRetry = onRetry,
                onPlayerError = onPlayerError,
                onToggleFullscreen = onToggleFullscreen,
                onDismiss = onDismiss,
            )

            TrailerPopupLayout.TheaterPanel -> TheaterTrailerLayout(
                maxHeight = maxHeight,
                headerType = headerType,
                headerSubtitle = headerSubtitle,
                playerContent = playerContent,
                playbackSource = playbackSource,
                isLoading = isLoading,
                activeError = activeError,
                onRetry = onRetry,
                onPlayerError = onPlayerError,
                onToggleFullscreen = onToggleFullscreen,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun FullscreenTrailerLayout(
    headerType: String,
    headerSubtitle: String,
    playerContent: @Composable (TrailerPlayerFrameArgs) -> Unit,
    playbackSource: TrailerPlaybackSource?,
    isLoading: Boolean,
    activeError: String?,
    onRetry: (() -> Unit)?,
    onPlayerError: (String?) -> Unit,
    onToggleFullscreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        playerContent(
            TrailerPlayerFrameArgs(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                clipPlayer = false,
                isLoading = isLoading,
                activeError = activeError,
                onRetry = onRetry,
                playbackSource = playbackSource,
                onPlayerError = onPlayerError,
            ),
        )

        TrailerChromeScrim(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(132.dp),
        )
        TrailerHeaderChrome(
            headerType = headerType,
            headerSubtitle = headerSubtitle,
            fullscreen = true,
            translucent = true,
            onToggleFullscreen = onToggleFullscreen,
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun CompactLandscapeTrailerLayout(
    headerType: String,
    headerSubtitle: String,
    playerContent: @Composable (TrailerPlayerFrameArgs) -> Unit,
    playbackSource: TrailerPlaybackSource?,
    isLoading: Boolean,
    activeError: String?,
    onRetry: (() -> Unit)?,
    onPlayerError: (String?) -> Unit,
    onToggleFullscreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        playerContent(
            TrailerPlayerFrameArgs(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(8.dp),
                clipPlayer = false,
                isLoading = isLoading,
                activeError = activeError,
                onRetry = onRetry,
                playbackSource = playbackSource,
                onPlayerError = onPlayerError,
            ),
        )

        TrailerChromeScrim(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(104.dp),
        )
        TrailerHeaderChrome(
            headerType = headerType,
            headerSubtitle = headerSubtitle,
            fullscreen = false,
            translucent = true,
            onToggleFullscreen = onToggleFullscreen,
            onDismiss = onDismiss,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BoxScope.CompactPortraitTrailerLayout(
    maxHeight: Dp,
    headerType: String,
    headerSubtitle: String,
    playerContent: @Composable (TrailerPlayerFrameArgs) -> Unit,
    playbackSource: TrailerPlaybackSource?,
    isLoading: Boolean,
    activeError: String?,
    onRetry: (() -> Unit)?,
    onPlayerError: (String?) -> Unit,
    onToggleFullscreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val panelShape = RoundedCornerShape(22.dp)

    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 16.dp, vertical = navigationBottom + 24.dp)
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .heightIn(max = maxHeight * 0.78f),
        shape = panelShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrailerHeaderChrome(
                headerType = headerType,
                headerSubtitle = headerSubtitle,
                fullscreen = false,
                translucent = false,
                onToggleFullscreen = onToggleFullscreen,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            playerContent(
                TrailerPlayerFrameArgs(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(16.dp),
                    clipPlayer = true,
                    isLoading = isLoading,
                    activeError = activeError,
                    onRetry = onRetry,
                    playbackSource = playbackSource,
                    onPlayerError = onPlayerError,
                ),
            )
        }
    }
}

@Composable
private fun BoxScope.TheaterTrailerLayout(
    maxHeight: Dp,
    headerType: String,
    headerSubtitle: String,
    playerContent: @Composable (TrailerPlayerFrameArgs) -> Unit,
    playbackSource: TrailerPlaybackSource?,
    isLoading: Boolean,
    activeError: String?,
    onRetry: (() -> Unit)?,
    onPlayerError: (String?) -> Unit,
    onToggleFullscreen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val horizontalSafePadding = maxOf(
        safePadding.calculateLeftPadding(layoutDirection),
        safePadding.calculateRightPadding(layoutDirection),
    )
    val verticalSafePadding = safePadding.calculateTopPadding() + safePadding.calculateBottomPadding()

    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = horizontalSafePadding + 22.dp, vertical = 20.dp)
            .fillMaxWidth()
            .widthIn(max = 1040.dp)
            .heightIn(max = maxHeight - verticalSafePadding - 40.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrailerHeaderChrome(
                headerType = headerType,
                headerSubtitle = headerSubtitle,
                fullscreen = false,
                translucent = false,
                onToggleFullscreen = onToggleFullscreen,
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
            playerContent(
                TrailerPlayerFrameArgs(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(16.dp),
                    clipPlayer = true,
                    isLoading = isLoading,
                    activeError = activeError,
                    onRetry = onRetry,
                    playbackSource = playbackSource,
                    onPlayerError = onPlayerError,
                ),
            )
        }
    }
}

@Composable
private fun TrailerHeaderChrome(
    headerType: String,
    headerSubtitle: String,
    fullscreen: Boolean,
    translucent: Boolean,
    onToggleFullscreen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = headerType,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (headerSubtitle.isNotBlank()) {
                Text(
                    text = headerSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        TrailerIconButton(
            icon = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            contentDescription = stringResource(
                if (fullscreen) {
                    Res.string.trailer_exit_fullscreen
                } else {
                    Res.string.trailer_enter_fullscreen
                },
            ),
            translucent = translucent,
            onClick = onToggleFullscreen,
        )
        TrailerIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = stringResource(Res.string.trailer_close),
            translucent = translucent,
            onClick = onDismiss,
        )
    }
}

@Composable
private fun TrailerIconButton(
    icon: ImageVector,
    contentDescription: String,
    translucent: Boolean,
    onClick: () -> Unit,
) {
    val background = if (translucent) {
        Color.Black.copy(alpha = 0.46f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun TrailerPlayerFrame(
    modifier: Modifier,
    shape: Shape,
    clipPlayer: Boolean,
    isLoading: Boolean,
    activeError: String?,
    onRetry: (() -> Unit)?,
    playbackSource: TrailerPlaybackSource?,
    onPlayerError: (String?) -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (clipPlayer) Modifier.clip(shape) else Modifier)
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> TrailerLoadingState()

            activeError != null -> TrailerErrorState(
                errorMessage = activeError,
                onRetry = onRetry,
            )

            playbackSource != null -> {
                PlatformPlayerSurface(
                    sourceUrl = playbackSource.videoUrl,
                    sourceAudioUrl = playbackSource.audioUrl,
                    useYoutubeChunkedPlayback = true,
                    modifier = Modifier.fillMaxSize(),
                    playWhenReady = true,
                    resizeMode = PlayerResizeMode.Fit,
                    useNativeController = true,
                    onControllerReady = {},
                    onSnapshot = {},
                    onError = onPlayerError,
                )
            }
        }
    }
}

@Composable
private fun TrailerLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                        Color.Black,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
    }
}

@Composable
private fun TrailerErrorState(
    errorMessage: String,
    onRetry: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.trailer_unable_to_play),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.action_retry))
            }
        }
    }
}

@Composable
private fun TrailerChromeScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.78f),
                    Color.Black.copy(alpha = 0.42f),
                    Color.Transparent,
                ),
            ),
        ),
    )
}

private enum class TrailerPopupLayout {
    CompactPortrait,
    CompactLandscape,
    TheaterPanel,
    Fullscreen,
}

private data class TrailerPlayerFrameArgs(
    val modifier: Modifier,
    val shape: Shape,
    val clipPlayer: Boolean,
    val isLoading: Boolean,
    val activeError: String?,
    val onRetry: (() -> Unit)?,
    val playbackSource: TrailerPlaybackSource?,
    val onPlayerError: (String?) -> Unit,
)

private fun trailerPopupLayout(
    maxWidth: Dp,
    maxHeight: Dp,
    fullscreen: Boolean,
): TrailerPopupLayout =
    when {
        fullscreen -> TrailerPopupLayout.Fullscreen
        maxHeight < 480.dp -> TrailerPopupLayout.CompactLandscape
        maxWidth < 600.dp -> TrailerPopupLayout.CompactPortrait
        else -> TrailerPopupLayout.TheaterPanel
    }

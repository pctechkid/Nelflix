package com.nuvio.app.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PosterZoomAnchor(
    val boundsInRoot: Rect,
    val imageUrl: String?,
    val cornerRadius: Dp,
)

object PosterZoomAnchorHolder {
    private var pending: PosterZoomAnchor? = null

    fun stash(anchor: PosterZoomAnchor) {
        pending = anchor
    }

    fun consume(): PosterZoomAnchor? = pending.also { pending = null }
}

class PosterZoomOverlayAction(
    val icon: ImageVector,
    val label: String,
    val isDestructive: Boolean = false,
    val onSelected: () -> Unit,
)

private enum class PosterZoomPhase {
    Open,
    Closing,
}

private const val PosterZoomVisibilityThreshold = 0.0005f
private val PosterZoomExpandSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 340f,
    visibilityThreshold = PosterZoomVisibilityThreshold,
)
private val PosterZoomCollapseSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 460f,
    visibilityThreshold = PosterZoomVisibilityThreshold,
)
private val PosterZoomMenuSpring = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = 420f,
    visibilityThreshold = Spring.DefaultDisplacementThreshold,
)

@Composable
fun NuvioPosterZoomActionOverlay(
    imageUrl: String?,
    title: String,
    subtitle: String?,
    isWatched: Boolean = false,
    anchor: PosterZoomAnchor?,
    actions: List<PosterZoomOverlayAction>,
    hazeState: HazeState,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val zoom = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    val menu = remember { Animatable(0f) }
    val frozenActions = remember { actions }
    var phase by remember { mutableStateOf(PosterZoomPhase.Open) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var slotBounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(durationMillis = 260)) }
        launch { zoom.animateTo(1f, PosterZoomExpandSpring) }
        launch {
            delay(60)
            menu.animateTo(1f, PosterZoomMenuSpring)
        }
    }

    fun close() {
        if (phase != PosterZoomPhase.Open) return
        phase = PosterZoomPhase.Closing
        scope.launch {
            coroutineScope {
                launch { menu.animateTo(0f, tween(durationMillis = 140)) }
                launch {
                    delay(80)
                    scrim.animateTo(0f, tween(durationMillis = 260))
                }
                launch { zoom.animateTo(0f, PosterZoomCollapseSpring) }
            }
            onDismissed()
        }
    }

    fun select(action: PosterZoomOverlayAction) {
        if (phase != PosterZoomPhase.Open) return
        action.onSelected()
        close()
    }

    PlatformBackHandler(enabled = true) {
        close()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates -> rootOrigin = coordinates.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures { close() }
            },
    ) {
        val anchorAspect = anchor
            ?.boundsInRoot
            ?.takeIf { it.height > 0f }
            ?.let { it.width / it.height }
            ?: 0.675f
        val aspect = anchorAspect.coerceIn(0.35f, 2.4f)
        val maxPosterWidth = if (aspect >= 1f) maxWidth * 0.8f else maxWidth * 0.6f
        val posterHeight = min(maxPosterWidth / aspect, maxHeight * 0.44f)
        val posterWidth = posterHeight * aspect
        val menuAvailableWidth = if (maxWidth > 80.dp) maxWidth - 80.dp else maxWidth
        val menuWidth = min(252.dp, menuAvailableWidth)
        val columnWidth = max(posterWidth, menuWidth)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeEffect(state = hazeState) {
                    blurRadius = 36.dp
                    inputScale = HazeInputScale.Auto
                    noiseFactor = 0f
                    blurredEdgeTreatment = BlurredEdgeTreatment.Rectangle
                    clipToAreasBounds = false
                    expandLayerBounds = true
                    alpha = scrim.value.coerceIn(0f, 1f)
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * scrim.value.coerceIn(0f, 1f))),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(columnWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = posterWidth, height = posterHeight)
                    .onGloballyPositioned { coordinates -> slotBounds = coordinates.boundsInRoot() },
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .width(columnWidth)
                    .graphicsLayer {
                        val progress = menu.value.coerceIn(0f, 1f)
                        alpha = progress
                        translationY = (1f - progress) * 10.dp.toPx()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .width(menuWidth)
                    .graphicsLayer {
                        val progress = menu.value.coerceIn(0f, 1f)
                        alpha = progress
                        val scale = 0.62f + 0.38f * progress
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
                    .graphicsLayer {
                        shape = RoundedCornerShape(18.dp)
                        clip = true
                    }
                    .background(ContextMenuSurface),
            ) {
                frozenActions.forEachIndexed { index, action ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.07f)),
                        )
                    }
                    PosterZoomMenuRow(
                        action = action,
                        enabled = phase == PosterZoomPhase.Open,
                        onSelected = { select(action) },
                        modifier = Modifier.graphicsLayer {
                            val stagger = index * 0.07f
                            val progress = ((menu.value.coerceIn(0f, 1f) - stagger) / (1f - stagger))
                                .coerceIn(0f, 1f)
                            alpha = progress
                            translationY = (1f - progress) * 8.dp.toPx()
                        },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(width = posterWidth, height = posterHeight)
                .graphicsLayer {
                    val slot = slotBounds
                    if (slot == null || slot.width <= 0f) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val progress = zoom.value
                    val clamped = progress.coerceIn(0f, 1f)
                    val start = posterStartBounds(anchor = anchor, slot = slot)
                    val scale = posterScale(anchor = anchor, slot = slot, progress = progress)
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = lerp(start.left, slot.left, progress) - rootOrigin.x
                    translationY = lerp(start.top, slot.top, progress) - rootOrigin.y
                    alpha = if (anchor == null) clamped else (clamped / 0.08f).coerceIn(0f, 1f)
                    shape = RoundedCornerShape(posterCornerRadiusPx(anchor, clamped, scale))
                    clip = false
                    shadowElevation = 24.dp.toPx() * clamped
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val clamped = zoom.value.coerceIn(0f, 1f)
                        val slot = slotBounds
                        val scale = if (slot != null && slot.width > 0f) {
                            posterScale(anchor = anchor, slot = slot, progress = zoom.value)
                        } else {
                            1f
                        }
                        shape = RoundedCornerShape(posterCornerRadiusPx(anchor, clamped, scale))
                        clip = true
                    }
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = title,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PosterZoomWatchedOverlay(
                    isWatched = isWatched,
                    modifier = Modifier.graphicsLayer {
                        val slot = slotBounds
                        if (slot != null && slot.width > 0f) {
                            val scale = posterScale(anchor = anchor, slot = slot, progress = zoom.value)
                                .coerceAtLeast(0.001f)
                            scaleX = 1f / scale
                            scaleY = 1f / scale
                            transformOrigin = TransformOrigin(1f, 0f)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun PosterZoomMenuRow(
    action: PosterZoomOverlayAction,
    enabled: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (action.isDestructive) ContextMenuDanger else Color.White

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelected)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun BoxScope.PosterZoomWatchedOverlay(
    isWatched: Boolean,
    modifier: Modifier = Modifier,
) {
    NuvioAnimatedWatchedBadge(
        isVisible = isWatched,
        modifier = modifier
            .align(Alignment.TopEnd)
            .padding(8.dp),
    )
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun posterStartBounds(anchor: PosterZoomAnchor?, slot: Rect): Rect =
    anchor?.boundsInRoot ?: slot.scaleAboutCenter(0.85f)

private fun posterScale(anchor: PosterZoomAnchor?, slot: Rect, progress: Float): Float =
    lerp(posterStartBounds(anchor = anchor, slot = slot).width / slot.width, 1f, progress)

private fun Rect.scaleAboutCenter(factor: Float): Rect {
    val newWidth = width * factor
    val newHeight = height * factor
    return Rect(
        offset = Offset(center.x - newWidth / 2f, center.y - newHeight / 2f),
        size = Size(newWidth, newHeight),
    )
}

private fun Density.posterCornerRadiusPx(
    anchor: PosterZoomAnchor?,
    clampedProgress: Float,
    scale: Float,
): Float {
    val finalRadius = PosterZoomFinalCornerRadius.toPx()
    val startRadius = anchor?.cornerRadius?.toPx() ?: finalRadius
    val apparent = lerp(startRadius, finalRadius, clampedProgress)
    return if (scale > 0f) apparent / scale else apparent
}

private val PosterZoomFinalCornerRadius = 18.dp
private val ContextMenuSurface = Color(0xFF1A1A1A)
private val ContextMenuDanger = Color(0xFFE66A8B)

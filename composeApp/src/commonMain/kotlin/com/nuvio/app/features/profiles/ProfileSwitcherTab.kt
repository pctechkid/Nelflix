package com.nuvio.app.features.profiles

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.isIos
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileSwitcherTab(
    selected: Boolean,
    onClick: () -> Unit,
    onProfileSelected: (NuvioProfile) -> Unit,
    onAddProfileRequested: () -> Unit,
    triggerContent: (@Composable (selected: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val activeProfile = profileState.activeProfile
    val profiles = profileState.profiles
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AvatarRepository.fetchAvatars()
        AvatarRepository.refreshAvatars()
    }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var showPopup by remember { mutableStateOf(false) }
    var popupVisible by remember { mutableStateOf(false) }
    var dragTargetProfileIndex by remember { mutableStateOf<Int?>(null) }
    var triggerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pinDialogProfile by remember { mutableStateOf<NuvioProfile?>(null) }
    var isVerifyingPin by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val pinVerifyFailedMessage = stringResource(Res.string.pin_incorrect)
    val profileBubbleBounds = remember(profiles.map { it.profileIndex }) {
        mutableStateMapOf<Int, Rect>()
    }

    fun performProfileHoldHaptic() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun performProfileHoverHaptic() {
        if (isIos) {
            ProfileHoverHapticFeedback.perform()
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun updateDragTarget(localPosition: Offset) {
        val trigger = triggerCoordinates ?: return
        val screenPosition = trigger.localToScreen(localPosition)
        val nextTargetProfileIndex = profileBubbleBounds.entries
            .firstOrNull { (_, bounds) -> bounds.contains(screenPosition) }
            ?.key
        if (nextTargetProfileIndex != null && nextTargetProfileIndex != dragTargetProfileIndex) {
            performProfileHoverHaptic()
        }
        dragTargetProfileIndex = nextTargetProfileIndex
    }

    fun chooseProfile(profile: NuvioProfile) {
        if (profile.pinEnabled && profile.profileIndex != activeProfile?.profileIndex) {
            pinError = null
            pinDialogProfile = profile
        } else {
            onProfileSelected(profile)
            showPopup = false
        }
    }

    fun chooseDragTarget() {
        val profile = profiles.firstOrNull { it.profileIndex == dragTargetProfileIndex }
        dragTargetProfileIndex = null
        if (profile != null) {
            chooseProfile(profile)
        }
    }

    val popupAlpha = remember { Animatable(0f) }
    val popupScale = remember { Animatable(0.5f) }
    val popupTranslateY = remember { Animatable(40f) }

    LaunchedEffect(showPopup) {
        if (showPopup) {
            popupVisible = true
            launch { popupAlpha.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
            launch {
                popupScale.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
            launch {
                popupTranslateY.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                )
            }
        } else {
            ProfileHoverHapticFeedback.release()
            launch { popupAlpha.animateTo(0f, tween(180, easing = FastOutSlowInEasing)) }
            launch { popupScale.animateTo(0.85f, tween(200, easing = FastOutSlowInEasing)) }
            launch {
                popupTranslateY.animateTo(30f, tween(200, easing = FastOutSlowInEasing))
                popupVisible = false
                dragTargetProfileIndex = null
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { triggerCoordinates = it }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .pointerInput(profiles) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        if (profiles.isNotEmpty()) {
                            performProfileHoldHaptic()
                            ProfileHoverHapticFeedback.prepare()
                            showPopup = true
                            updateDragTarget(startOffset)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateDragTarget(change.position)
                    },
                    onDragEnd = {
                        ProfileHoverHapticFeedback.release()
                        chooseDragTarget()
                    },
                    onDragCancel = {
                        ProfileHoverHapticFeedback.release()
                        dragTargetProfileIndex = null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (triggerContent != null) {
            triggerContent(selected)
        } else {
            ActiveProfileMiniAvatar(
                profile = activeProfile,
                avatars = avatars,
                selected = selected,
                size = 28,
            )
        }

        if (popupVisible && profiles.isNotEmpty()) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, with(density) { -64.dp.roundToPx() }),
                properties = PopupProperties(focusable = true),
                onDismissRequest = { showPopup = false },
            ) {
                Box(
                    modifier = Modifier
                        .imePadding()
                        .graphicsLayer {
                            alpha = popupAlpha.value
                            scaleX = popupScale.value
                            scaleY = popupScale.value
                            translationY = popupTranslateY.value
                        }
                        .shadow(16.dp, RoundedCornerShape(28.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(28.dp),
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        profiles.forEachIndexed { index, profile ->
                            PopupProfileBubble(
                                profile = profile,
                                avatars = avatars,
                                isActive = profile.profileIndex == activeProfile?.profileIndex,
                                isSelected = dragTargetProfileIndex == profile.profileIndex,
                                delayMs = index * 50,
                                onBoundsChanged = { bounds ->
                                    profileBubbleBounds[profile.profileIndex] = bounds
                                },
                                onClick = {
                                    chooseProfile(profile)
                                },
                            )
                        }

                        if (profiles.size < 4) {
                            PopupAddProfileBubble(
                                delayMs = profiles.size * 50,
                                onClick = {
                                    showPopup = false
                                    onAddProfileRequested()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pinDialogProfile?.let { profile ->
        PinEntryDialog(
            title = stringResource(Res.string.pin_enter_for, profile.name),
            message = stringResource(Res.string.profile_security_pin_enabled),
            confirmText = stringResource(Res.string.pin_enter),
            isBusy = isVerifyingPin,
            errorMessage = pinError,
            onConfirm = { pin ->
                scope.launch {
                    isVerifyingPin = true
                    pinError = null
                    val result = ProfileRepository.verifyPin(profile.profileIndex, pin)
                    isVerifyingPin = false
                    if (result.unlocked) {
                        pinDialogProfile = null
                        showPopup = false
                        onProfileSelected(profile)
                    } else {
                        pinError = result.message.ifBlank { pinVerifyFailedMessage }
                    }
                }
            },
            onDismiss = {
                pinDialogProfile = null
                pinError = null
            },
        )
    }
}

@Composable
private fun PopupAddProfileBubble(
    delayMs: Int,
    onClick: () -> Unit,
) {
    val itemAlpha = remember { Animatable(0f) }
    val itemScale = remember { Animatable(0.4f) }

    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        launch { itemAlpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
        launch {
            itemScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                alpha = itemAlpha.value
                scaleX = itemScale.value
                scaleY = itemScale.value
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(Res.string.compose_profile_add_profile),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(Res.string.compose_profile_add_profile),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(56.dp),
        )
    }
}

@Composable
private fun PopupProfileBubble(
    profile: NuvioProfile,
    avatars: List<AvatarCatalogItem>,
    isActive: Boolean,
    isSelected: Boolean,
    delayMs: Int,
    onBoundsChanged: (Rect) -> Unit,
    onClick: () -> Unit,
) {
    val avatarColor = remember(profile.avatarColorHex) { parseHexColor(profile.avatarColorHex) }
    val avatarItem = remember(profile.avatarId, avatars) {
        profile.avatarId?.let { id -> avatars.find { it.id == id } }
    }
    val avatarImageUrl = remember(profile.avatarUrl, avatarItem) {
        profileAvatarImageUrl(profile, avatarItem)
    }

    val itemAlpha = remember { Animatable(0f) }
    val itemScale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        launch { itemAlpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
        launch {
            itemScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }

    val pressScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "pressScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsOnScreen())
            }
            .graphicsLayer {
                alpha = itemAlpha.value
                scaleX = itemScale.value * pressScale
                scaleY = itemScale.value * pressScale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (avatarImageUrl != null) {
                            avatarItem?.bgColor?.let { parseHexColor(it) } ?: avatarColor
                        } else {
                            avatarColor.copy(alpha = 0.15f)
                        },
                    )
                    .then(
                        when {
                            isSelected -> Modifier.border(
                                2.5.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape,
                            )
                            isActive -> Modifier.border(
                                2.dp,
                                avatarColor.copy(alpha = 0.6f),
                                CircleShape,
                            )
                            avatarImageUrl == null -> Modifier.border(
                                1.5.dp,
                                avatarColor.copy(alpha = 0.3f),
                                CircleShape,
                            )
                            else -> Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarImageUrl != null) {
                    AsyncImage(
                        model = avatarImageUrl,
                        contentDescription = profile.name,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else if (profile.name.isNotBlank()) {
                    Text(
                        text = profile.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        color = avatarColor,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = avatarColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            if (profile.pinEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = profile.name.ifBlank {
                stringResource(Res.string.profile_label_number, profile.profileIndex)
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isActive || isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(56.dp),
        )
    }
}

private fun LayoutCoordinates.boundsOnScreen(): Rect {
    val topLeft = localToScreen(Offset.Zero)
    val bottomRight = localToScreen(Offset(size.width.toFloat(), size.height.toFloat()))
    return Rect(
        left = min(topLeft.x, bottomRight.x),
        top = min(topLeft.y, bottomRight.y),
        right = max(topLeft.x, bottomRight.x),
        bottom = max(topLeft.y, bottomRight.y),
    )
}

@Composable
fun ActiveProfileMiniAvatar(
    profile: NuvioProfile?,
    avatars: List<AvatarCatalogItem>,
    selected: Boolean,
    size: Int = 24,
) {
    if (profile == null) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = stringResource(Res.string.compose_nav_profile),
            modifier = Modifier.size(size.dp),
        )
        return
    }

    val avatarColor = remember(profile.avatarColorHex) { parseHexColor(profile.avatarColorHex) }
    val avatarItem = remember(profile.avatarId, avatars) {
        profile.avatarId?.let { id -> avatars.find { it.id == id } }
    }
    val avatarImageUrl = remember(profile.avatarUrl, avatarItem) {
        profileAvatarImageUrl(profile, avatarItem)
    }

    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        avatarColor.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (avatarImageUrl != null) {
                    avatarItem?.bgColor?.let { parseHexColor(it) } ?: avatarColor
                } else {
                    avatarColor.copy(alpha = 0.15f)
                },
            )
            .border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarImageUrl != null) {
            AsyncImage(
                model = avatarImageUrl,
                contentDescription = profile.name,
                modifier = Modifier.size(size.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else if (profile.name.isNotBlank()) {
            Text(
                text = profile.name.take(1).uppercase(),
                fontSize = (size * 0.45f).sp,
                color = avatarColor,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = avatarColor,
                modifier = Modifier.size((size * 0.6f).dp),
            )
        }
    }
}

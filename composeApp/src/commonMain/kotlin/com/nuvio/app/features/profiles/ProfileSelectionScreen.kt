package com.nuvio.app.features.profiles

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.auth.AuthState
import com.nuvio.app.core.ui.NuvioStatusModal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

private const val ProfileSpotlightItemLimit = 16
private const val ProfileSpotlightCycleMs = 4_800L
private const val ProfileSpotlightCrossfadeMs = 900
private const val ProfileSpotlightZoomMs = 5_400

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectionScreen(
    onProfileSelected: (NuvioProfile) -> Unit,
    onEditProfile: (NuvioProfile) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val authState by AuthRepository.state.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isEditMode by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var isSigningOut by remember { mutableStateOf(false) }
    var pinDialogProfile by remember { mutableStateOf<NuvioProfile?>(null) }
    var isVerifyingPin by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val pinVerifyFailedMessage = stringResource(Res.string.pin_incorrect)
    val profileSpotlightState by ProfileSpotlightRepository.state.collectAsStateWithLifecycle()
    val profileSpotlightItems = remember(profileSpotlightState.items) {
        profileSpotlightState.items.take(ProfileSpotlightItemLimit)
    }
    val profileSpotlightKey = remember(profileSpotlightItems) {
        profileSpotlightItems.joinToString(separator = "|") { item -> "${item.type}:${item.id}" }
    }
    var spotlightIndex by remember(profileSpotlightKey) {
        mutableStateOf(
            if (profileSpotlightItems.size > 1) {
                Random.nextInt(profileSpotlightItems.size)
            } else {
                0
            },
        )
    }
    val activeSpotlight = profileSpotlightItems.getOrNull(spotlightIndex)

    LaunchedEffect(Unit) {
        AvatarRepository.fetchAvatars()
        AvatarRepository.refreshAvatars()
        ProfileSpotlightRepository.load()
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            ProfileRepository.pullProfiles()
        }
    }

    LaunchedEffect(profileSpotlightKey) {
        if (profileSpotlightItems.size <= 1) return@LaunchedEffect
        while (true) {
            delay(ProfileSpotlightCycleMs)
            spotlightIndex = (spotlightIndex + 1) % profileSpotlightItems.size
        }
    }

    fun selectProfile(profile: NuvioProfile) {
        if (profile.pinEnabled) {
            pinError = null
            pinDialogProfile = profile
        } else {
            ProfileRepository.selectProfile(profile.profileIndex)
            onProfileSelected(profile)
        }
    }

    ProfileSelectionHeroLayout(
        profiles = profileState.profiles,
        spotlight = activeSpotlight,
        isEditMode = isEditMode,
        isSigningOut = isSigningOut,
        onProfileClick = { profile ->
            if (isEditMode) {
                onEditProfile(profile)
            } else {
                selectProfile(profile)
            }
        },
        onAddProfile = onAddProfile,
        onToggleEditMode = { isEditMode = !isEditMode },
        onSignOutClick = { showSignOutConfirm = true },
        modifier = modifier,
    )

    NuvioStatusModal(
        title = stringResource(Res.string.settings_account_sign_out_confirm_title),
        message = stringResource(Res.string.settings_account_sign_out_confirm_message),
        isVisible = showSignOutConfirm,
        isBusy = isSigningOut,
        confirmText = stringResource(Res.string.settings_account_sign_out),
        dismissText = stringResource(Res.string.action_cancel),
        confirmContainerColor = Color(0xFFE50914),
        confirmContentColor = Color.White,
        onConfirm = {
            scope.launch {
                isSigningOut = true
                try {
                    AuthRepository.signOut()
                } finally {
                    isSigningOut = false
                    showSignOutConfirm = false
                }
            }
        },
        onDismiss = { if (!isSigningOut) showSignOutConfirm = false },
    )


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
                        ProfileRepository.selectProfile(profile.profileIndex)
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
private fun ProfileSelectionHeroLayout(
    profiles: List<NuvioProfile>,
    spotlight: ProfileSpotlightItem?,
    isEditMode: Boolean,
    isSigningOut: Boolean,
    onProfileClick: (NuvioProfile) -> Unit,
    onAddProfile: () -> Unit,
    onToggleEditMode: () -> Unit,
    onSignOutClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val isLandscapeLayout = maxWidth > maxHeight
        val isTabletLayout = maxWidth >= 768.dp && maxHeight >= 600.dp
        val isWideTabletLayout = isTabletLayout && isLandscapeLayout
        val isPhoneLandscapeLayout = !isTabletLayout && isLandscapeLayout
        val horizontalPadding = when {
            isWideTabletLayout -> 80.dp
            isPhoneLandscapeLayout -> 42.dp
            isTabletLayout -> 54.dp
            else -> 24.dp
        }
        val avatarSize = when {
            isWideTabletLayout -> 88.dp
            isPhoneLandscapeLayout -> 68.dp
            maxWidth >= 840.dp -> 96.dp
            maxWidth >= 600.dp -> 86.dp
            else -> 74.dp
        }
        val profileCardWidth = avatarSize + when {
            isWideTabletLayout -> 20.dp
            isPhoneLandscapeLayout -> 14.dp
            isTabletLayout -> 28.dp
            else -> 18.dp
        }
        val profileSpacing = when {
            isWideTabletLayout -> 20.dp
            isPhoneLandscapeLayout -> 28.dp
            isTabletLayout -> 24.dp
            else -> 16.dp
        }
        val chooserBottomPadding = navigationBarBottom + when {
            isWideTabletLayout -> 16.dp
            isPhoneLandscapeLayout -> 8.dp
            isTabletLayout -> 28.dp
            else -> 10.dp
        }
        val shelfHeight = when {
            isWideTabletLayout -> (maxHeight * 0.36f).coerceIn(248.dp, 336.dp)
            isPhoneLandscapeLayout -> (maxHeight * 0.56f).coerceIn(178.dp, 236.dp)
            else -> {
                (maxHeight * if (isTabletLayout) 0.31f else 0.26f)
                    .coerceIn(196.dp, if (isTabletLayout) 318.dp else 250.dp)
            }
        }
        val shelfCurveDepth = when {
            isWideTabletLayout -> (maxHeight * 0.064f).coerceIn(52.dp, 82.dp)
            isPhoneLandscapeLayout -> (maxHeight * 0.070f).coerceIn(26.dp, 42.dp)
            else -> {
                (maxHeight * if (isTabletLayout) 0.023f else 0.025f)
                    .coerceIn(18.dp, if (isTabletLayout) 30.dp else 26.dp)
            }
        }
        val shelfColor = remember(spotlight?.type, spotlight?.id, spotlight?.banner) {
            spotlight.profileShelfColor()
        }
        val heroInfoBottomPadding = shelfHeight + when {
            isWideTabletLayout -> 48.dp
            isPhoneLandscapeLayout -> 28.dp
            isTabletLayout -> 58.dp
            else -> 42.dp
        }

        Crossfade(
            targetState = spotlight,
            animationSpec = tween(ProfileSpotlightCrossfadeMs, easing = FastOutSlowInEasing),
            label = "profile_spotlight_background",
            modifier = Modifier.fillMaxSize(),
        ) { item ->
            ProfileSpotlightBackground(
                item = item,
                isTabletLayout = isTabletLayout,
                modifier = Modifier.fillMaxSize(),
            )
        }

        ProfileSpotlightOverlays(
            shelfHeight = shelfHeight,
            curveDepth = shelfCurveDepth,
            shelfColor = shelfColor,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = statusBarTop + if (isTabletLayout) 22.dp else 18.dp,
                    end = if (isTabletLayout) 36.dp else 22.dp,
                )
                .size(if (isTabletLayout) 44.dp else 40.dp)
                .clip(CircleShape)
                .background(Color(0xFFE50914).copy(alpha = if (isSigningOut) 0.42f else 0.94f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clickable(enabled = !isSigningOut) { onSignOutClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Logout,
                contentDescription = stringResource(Res.string.settings_account_sign_out),
                tint = Color.White.copy(alpha = if (isSigningOut) 0.42f else 0.86f),
                modifier = Modifier.size(if (isTabletLayout) 21.dp else 19.dp),
            )
        }

        Crossfade(
            targetState = spotlight,
            animationSpec = tween(ProfileSpotlightCrossfadeMs, easing = FastOutSlowInEasing),
            label = "profile_spotlight_info",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = heroInfoBottomPadding)
                .padding(horizontal = horizontalPadding),
        ) { item ->
            ProfileSpotlightInfo(
                item = item,
                isTabletLayout = isTabletLayout,
                isWideTabletLayout = isWideTabletLayout,
                isPhoneLandscapeLayout = isPhoneLandscapeLayout,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = chooserBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.profile_choose_your_profile),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = when {
                        isWideTabletLayout -> 13.sp
                        isPhoneLandscapeLayout -> 11.5.sp
                        isTabletLayout -> 15.sp
                        else -> 12.sp
                    },
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.58f),
                        offset = Offset(0f, 2f),
                        blurRadius = 7f,
                    ),
                ),
                color = Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(
                modifier = Modifier.height(
                    when {
                        isWideTabletLayout -> 14.dp
                        isPhoneLandscapeLayout -> 10.dp
                        isTabletLayout -> 18.dp
                        else -> 12.dp
                    },
                ),
            )

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(profileSpacing, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top,
                ) {
                    profiles.forEachIndexed { index, profile ->
                        ProfileAvatarCard(
                            profile = profile,
                            isEditMode = isEditMode,
                            animDelay = index * 70,
                            avatarSize = avatarSize,
                            cardWidth = profileCardWidth,
                            labelColor = Color.White,
                            labelFontSize = when {
                                isWideTabletLayout -> 14.sp
                                isPhoneLandscapeLayout -> 12.5.sp
                                isTabletLayout -> 15.sp
                                else -> 14.sp
                            },
                            onClick = { onProfileClick(profile) },
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.height(
                    when {
                        isWideTabletLayout -> 20.dp
                        isPhoneLandscapeLayout -> 14.dp
                        isTabletLayout -> 26.dp
                        else -> 22.dp
                    },
                ),
            )

            ProfileSelectionActions(
                canAddProfile = profiles.size < 4,
                isEditMode = isEditMode,
                isSigningOut = isSigningOut,
                onAddProfile = onAddProfile,
                onToggleEditMode = onToggleEditMode,
                isTabletLayout = isTabletLayout,
            )
        }
    }
}

@Composable
private fun ProfileSpotlightBackground(
    item: ProfileSpotlightItem?,
    isTabletLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val imageUrl = item?.banner
    val zoom = remember(item?.type, item?.id, imageUrl) { Animatable(1f) }

    LaunchedEffect(item?.type, item?.id, imageUrl) {
        zoom.snapTo(1f)
        zoom.animateTo(
            targetValue = if (isTabletLayout) 1.05f else 1.08f,
            animationSpec = tween(ProfileSpotlightZoomMs, easing = LinearEasing),
        )
    }

    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14151D),
                        Color(0xFF070A12),
                        Color.Black,
                    ),
                ),
            ),
        )
        return
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = item?.name,
        modifier = modifier.graphicsLayer {
            scaleX = zoom.value
            scaleY = zoom.value
        },
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
    )
}

@Composable
private fun ProfileSpotlightOverlays(
    shelfHeight: androidx.compose.ui.unit.Dp,
    curveDepth: androidx.compose.ui.unit.Dp,
    shelfColor: Color,
    modifier: Modifier = Modifier,
) {
    val shelfTopColor = shelfColor.shelfShade(1.18f)
    val shelfMidColor = shelfColor.shelfShade(0.70f)
    val shelfBottomColor = shelfColor.shelfShade(0.30f)

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.52f),
                            0.10f to Color.Black.copy(alpha = 0.34f),
                            0.28f to Color.Black.copy(alpha = 0.10f),
                            0.42f to Color.Black.copy(alpha = 0.08f),
                            0.56f to Color.Black.copy(alpha = 0.34f),
                            0.68f to Color(0xFF020711).copy(alpha = 0.64f),
                            0.82f to Color(0xFF01050C).copy(alpha = 0.86f),
                            1f to Color.Black.copy(alpha = 0.98f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.54f),
                            0.10f to Color.Black.copy(alpha = 0.28f),
                            0.24f to Color.Black.copy(alpha = 0.08f),
                            0.50f to Color.Transparent,
                            0.76f to Color.Black.copy(alpha = 0.08f),
                            0.90f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Black.copy(alpha = 0.54f),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(shelfHeight)
                .clip(ProfileSelectionShelfShape(curveDepth = curveDepth))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to shelfTopColor,
                            0.24f to shelfColor,
                            0.68f to shelfMidColor,
                            1f to shelfBottomColor,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(shelfHeight)
                .clip(ProfileSelectionShelfShape(curveDepth = curveDepth))
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.88f),
                            0.04f to Color.Black.copy(alpha = 0.72f),
                            0.12f to Color.Black.copy(alpha = 0.36f),
                            0.24f to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(shelfHeight + 190.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.24f to Color.Black.copy(alpha = 0.12f),
                            0.42f to Color.Black.copy(alpha = 0.34f),
                            0.58f to shelfColor.copy(alpha = 0.40f),
                            0.78f to shelfMidColor.copy(alpha = 0.42f),
                            1f to Color.Black.copy(alpha = 0.56f),
                        ),
                    ),
                ),
        )
    }
}

private class ProfileSelectionShelfShape(
    private val curveDepth: androidx.compose.ui.unit.Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val depth = with(density) { curveDepth.toPx() }.coerceAtMost(size.height * 0.20f)
        val path = Path().apply {
            moveTo(0f, depth)
            quadraticTo(size.width * 0.50f, 0f, size.width, depth)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

private fun ProfileSpotlightItem?.profileShelfColor(): Color {
    val genres = this?.genres.orEmpty().map { genre -> genre.lowercase() }
    val base = when {
        genres.any { it.contains("horror") || it.contains("mystery") || it.contains("thriller") } -> Color(0xFF081118)
        genres.any { it.contains("science") || it.contains("sci-fi") || it.contains("fantasy") } -> Color(0xFF061725)
        genres.any { it.contains("romance") || it.contains("comedy") || it.contains("family") } -> Color(0xFF171220)
        genres.any { it.contains("action") || it.contains("adventure") || it.contains("war") } -> Color(0xFF11170F)
        genres.any { it.contains("crime") || it.contains("drama") } -> Color(0xFF0B121C)
        else -> Color(0xFF07101D)
    }
    val seed = ((this?.id.orEmpty() + this?.name.orEmpty()).hashCode() ushr 8) and 0x0F
    val nudge = (seed - 7) / 255f
    return Color(
        red = (base.red + nudge * 0.55f).coerceIn(0.02f, 0.18f),
        green = (base.green + nudge * 0.38f).coerceIn(0.02f, 0.18f),
        blue = (base.blue + nudge * 0.70f).coerceIn(0.03f, 0.24f),
        alpha = 1f,
    )
}

private fun Color.shelfShade(multiplier: Float): Color = Color(
    red = (red * multiplier).coerceIn(0f, 1f),
    green = (green * multiplier).coerceIn(0f, 1f),
    blue = (blue * multiplier).coerceIn(0f, 1f),
    alpha = alpha,
)

@Composable
private fun ProfileSpotlightInfo(
    item: ProfileSpotlightItem?,
    isTabletLayout: Boolean,
    isWideTabletLayout: Boolean,
    isPhoneLandscapeLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    var logoFailed by remember(item?.logo) { mutableStateOf(false) }
    val showLogo = !item?.logo.isNullOrBlank() && !logoFailed
    val genreText = remember(item?.genres) {
        item?.genres
            .orEmpty()
            .map { genre -> genre.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .take(3)
            .joinToString(" \u2022 ")
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (item != null) {
            ProfileNelflixWordmark(
                isTabletLayout = isTabletLayout,
                isWideTabletLayout = isWideTabletLayout,
                isPhoneLandscapeLayout = isPhoneLandscapeLayout,
            )

            Spacer(modifier = Modifier.height(if (isTabletLayout || isPhoneLandscapeLayout) 1.dp else 2.dp))

            if (showLogo) {
                AsyncImage(
                    model = item.logo,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth(
                            when {
                                isWideTabletLayout -> 0.40f
                                isPhoneLandscapeLayout -> 0.36f
                                isTabletLayout -> 0.56f
                                else -> 0.62f
                            },
                        )
                        .widthIn(
                            max = when {
                                isWideTabletLayout -> 500.dp
                                isPhoneLandscapeLayout -> 360.dp
                                isTabletLayout -> 560.dp
                                else -> 480.dp
                            },
                        )
                        .aspectRatio(2.6f),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    onError = { logoFailed = true },
                )
            }

            if (!showLogo) {
                Text(
                    text = item.name,
                    modifier = Modifier.widthIn(
                        max = when {
                            isWideTabletLayout -> 500.dp
                            isPhoneLandscapeLayout -> 360.dp
                            isTabletLayout -> 560.dp
                            else -> 360.dp
                        },
                    ),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = when {
                            isWideTabletLayout -> 34.sp
                            isPhoneLandscapeLayout -> 26.sp
                            isTabletLayout -> 40.sp
                            else -> 31.sp
                        },
                        letterSpacing = 0.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.72f),
                            offset = Offset(0f, 3f),
                            blurRadius = 12f,
                        ),
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (genreText.isNotBlank()) {
                Spacer(modifier = Modifier.height(if (isWideTabletLayout || isPhoneLandscapeLayout) 3.dp else 6.dp))
                Text(
                    text = genreText,
                    modifier = Modifier.widthIn(
                        max = when {
                            isWideTabletLayout -> 460.dp
                            isPhoneLandscapeLayout -> 340.dp
                            isTabletLayout -> 560.dp
                            else -> 340.dp
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = when {
                            isWideTabletLayout -> 12.5.sp
                            isPhoneLandscapeLayout -> 11.5.sp
                            isTabletLayout -> 14.sp
                            else -> 12.5.sp
                        },
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.76f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f,
                        ),
                    ),
                    color = Color.White.copy(alpha = 0.86f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProfileNelflixWordmark(
    isTabletLayout: Boolean,
    isWideTabletLayout: Boolean,
    isPhoneLandscapeLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val wordmarkModifier = modifier
        .fillMaxWidth(
            when {
                isWideTabletLayout -> 0.085f
                isPhoneLandscapeLayout -> 0.075f
                isTabletLayout -> 0.12f
                else -> 0.22f
            },
        )
        .widthIn(
            max = when {
                isWideTabletLayout -> 104.dp
                isPhoneLandscapeLayout -> 76.dp
                isTabletLayout -> 130.dp
                else -> 96.dp
            },
        )
        .height(
            when {
                isWideTabletLayout -> 18.dp
                isPhoneLandscapeLayout -> 15.dp
                isTabletLayout -> 22.dp
                else -> 18.dp
            },
        )

    Image(
        painter = painterResource(Res.drawable.app_logo_wordmark),
        contentDescription = stringResource(Res.string.app_brand_name),
        modifier = wordmarkModifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ProfileSelectionActions(
    canAddProfile: Boolean,
    isEditMode: Boolean,
    isSigningOut: Boolean,
    onAddProfile: () -> Unit,
    onToggleEditMode: () -> Unit,
    isTabletLayout: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = if (isTabletLayout) 460.dp else 330.dp),
    ) {
        if (canAddProfile) {
            ProfileRoundAction(
                icon = Icons.Rounded.Add,
                label = stringResource(Res.string.compose_profile_add_profile),
                onClick = onAddProfile,
                enabled = !isSigningOut,
                isTabletLayout = isTabletLayout,
            )

            Box(
                modifier = Modifier
                    .padding(horizontal = if (isTabletLayout) 28.dp else 22.dp)
                    .width(1.dp)
                    .height(if (isTabletLayout) 62.dp else 54.dp)
                    .background(Color.White.copy(alpha = 0.34f)),
            )
        }
        ProfileRoundAction(
            icon = Icons.Rounded.Edit,
            label = if (isEditMode) {
                stringResource(Res.string.action_done)
            } else {
                stringResource(Res.string.profile_manage_profiles)
            },
            onClick = onToggleEditMode,
            enabled = !isSigningOut,
            selected = isEditMode,
            isTabletLayout = isTabletLayout,
        )
    }
}

@Composable
private fun ProfileRoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    selected: Boolean = false,
    isTabletLayout: Boolean,
) {
    val containerColor = if (selected) {
        Color(0xFFE50914).copy(alpha = 0.80f)
    } else {
        Color.White.copy(alpha = 0.03f)
    }
    val contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.42f)
    val buttonSize = if (isTabletLayout) 54.dp else 50.dp
    val iconSize = if (isTabletLayout) 24.dp else 23.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(if (isTabletLayout) 124.dp else 104.dp),
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(containerColor)
                .border(1.2.dp, Color.White.copy(alpha = if (selected) 0.24f else 0.38f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = if (isTabletLayout) 14.sp else 12.5.sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.52f),
                    offset = Offset(0f, 1f),
                    blurRadius = 6f,
                ),
            ),
            color = contentColor.copy(alpha = if (selected) 0.98f else 0.76f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileAvatarCard(
    profile: NuvioProfile,
    isEditMode: Boolean,
    animDelay: Int,
    avatarSize: androidx.compose.ui.unit.Dp = 100.dp,
    cardWidth: androidx.compose.ui.unit.Dp = 150.dp,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    labelFontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    onClick: () -> Unit,
) {
    val avatarColor = remember(profile.avatarColorHex) {
        parseHexColor(profile.avatarColorHex)
    }
    val avatars by AvatarRepository.avatars.collectAsStateWithLifecycle()
    val avatarItem = remember(profile.avatarId, avatars) {
        profile.avatarId?.let { id -> avatars.find { it.id == id } }
    }
    val avatarImageUrl = remember(profile.avatarUrl, avatarItem) {
        profileAvatarImageUrl(profile, avatarItem)
    }

    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(0.85f) }
    val animOffset = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        delay(animDelay.toLong() + 150)
        launch { animAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        launch { animScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { animOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) 0.95f else 1f
    val badgeSize = if (avatarSize < 90.dp) 28.dp else 34.dp
    val badgeIconSize = if (avatarSize < 90.dp) 14.dp else 16.dp
    val initialFontSize = if (avatarSize < 90.dp) 31.sp else 38.sp
    val fallbackIconSize = if (avatarSize < 90.dp) 38.dp else 46.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(cardWidth)
            .graphicsLayer {
                alpha = animAlpha.value
                scaleX = animScale.value * pressScale
                scaleY = animScale.value * pressScale
                translationY = animOffset.value
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier.size(avatarSize),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .shadow(
                        elevation = if (avatarSize < 90.dp) 12.dp else 16.dp,
                        shape = CircleShape,
                        clip = false,
                    )
                    .clip(CircleShape)
                    .background(
                        if (avatarItem != null) {
                            avatarItem.bgColor?.let { parseHexColor(it) } ?: avatarColor
                        } else {
                            avatarColor.copy(alpha = 0.15f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarImageUrl != null) {
                    AsyncImage(
                        model = avatarImageUrl,
                        contentDescription = avatarItem?.displayName ?: profile.name,
                        modifier = Modifier.size(avatarSize).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else if (profile.name.isNotBlank()) {
                    Text(
                        text = profile.name.take(1).uppercase(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = initialFontSize),
                        color = avatarColor,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = avatarColor,
                        modifier = Modifier.size(fallbackIconSize),
                    )
                }
            }

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(badgeIconSize),
                    )
                }
            }

            if (!isEditMode && profile.pinEnabled) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(badgeIconSize),
                    )
                }
            }

        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = profile.name.ifBlank {
                stringResource(Res.string.profile_label_number, profile.profileIndex)
            },
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = labelFontSize,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.48f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 6f,
                ),
            ),
            color = labelColor.copy(alpha = 0.94f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddProfileCard(
    animDelay: Int,
    onClick: () -> Unit,
) {
    val animAlpha = remember { Animatable(0f) }
    val animScale = remember { Animatable(0.85f) }
    val animOffset = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        delay(animDelay.toLong() + 150)
        launch { animAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        launch { animScale.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { animOffset.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale = if (isPressed) 0.95f else 1f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer {
                alpha = animAlpha.value
                scaleX = animScale.value * pressScale
                scaleY = animScale.value * pressScale
                translationY = animOffset.value
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier.size(110.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(Res.string.compose_profile_add_profile),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

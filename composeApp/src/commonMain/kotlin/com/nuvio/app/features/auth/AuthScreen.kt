package com.nuvio.app.features.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.features.profiles.ProfileSpotlightItem
import com.nuvio.app.features.profiles.ProfileSpotlightRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_brand_name
import nuvio.composeapp.generated.resources.app_logo_wordmark
import nuvio.composeapp.generated.resources.compose_auth_already_have_account
import nuvio.composeapp.generated.resources.compose_auth_dont_have_account
import nuvio.composeapp.generated.resources.compose_auth_email
import nuvio.composeapp.generated.resources.compose_auth_password
import nuvio.composeapp.generated.resources.compose_auth_sign_in
import nuvio.composeapp.generated.resources.compose_auth_sign_up
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

private enum class AuthMode {
    SignIn,
    CreateAccount,
}

private val AuthNetflixRed = Color(0xFFE50914)
private val AuthPanelBlack = Color(0xFF02050A)
private val AuthPanelBlueBlack = Color(0xFF07101A)
private val AuthFieldFill = Color(0xFF17191D)

private const val AuthSpotlightItemLimit = 16
private const val AuthSpotlightCycleMs = 4_800L
private const val AuthSpotlightCrossfadeMs = 900
private const val AuthSpotlightZoomMs = 5_400

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
) {
    val authError by AuthRepository.error.collectAsStateWithLifecycle()
    val spotlightState by ProfileSpotlightRepository.state.collectAsStateWithLifecycle()
    val spotlightItems = remember(spotlightState.items) {
        spotlightState.items.take(AuthSpotlightItemLimit)
    }
    val spotlightKey = remember(spotlightItems) {
        spotlightItems.joinToString(separator = "|") { item -> "${item.type}:${item.id}" }
    }
    var spotlightIndex by remember(spotlightKey) {
        mutableStateOf(
            if (spotlightItems.size > 1) {
                Random.nextInt(spotlightItems.size)
            } else {
                0
            },
        )
    }
    val spotlight = spotlightItems.getOrNull(spotlightIndex.coerceIn(0, (spotlightItems.size - 1).coerceAtLeast(0)))

    val scope = rememberCoroutineScope()
    var authMode by rememberSaveable { mutableStateOf(AuthMode.SignIn) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var authMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    val formFocused = emailFocused || passwordFocused
    val focusManager = LocalFocusManager.current
    var keyboardWasVisible by remember { mutableStateOf(false) }

    fun submitAuth() {
        if (email.isBlank() || password.length < 6 || isLoading) return
        isLoading = true
        scope.launch {
            val result = if (authMode == AuthMode.CreateAccount) {
                AuthRepository.signUpWithEmail(email.trim(), password)
            } else {
                AuthRepository.signInWithEmail(email.trim(), password)
            }
            if (authMode == AuthMode.CreateAccount && result.isSuccess) {
                authMessage = "Account created. Please check your email to verify your account."
                authMode = AuthMode.SignIn
                password = ""
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        ProfileSpotlightRepository.load()
    }

    LaunchedEffect(spotlightKey) {
        if (spotlightItems.size <= 1) return@LaunchedEffect
        while (true) {
            delay(AuthSpotlightCycleMs)
            spotlightIndex = (spotlightIndex + 1) % spotlightItems.size
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val isLandscape = maxWidth > maxHeight
        val isTablet = maxWidth >= 600.dp
        val isWideTablet = maxWidth >= 900.dp && isLandscape
        val compactLandscape = isLandscape && maxHeight < 520.dp
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val keyboardVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
        val panelExpanded = formFocused && keyboardVisible
        LaunchedEffect(keyboardVisible) {
            if (keyboardVisible) {
                keyboardWasVisible = true
            } else if (keyboardWasVisible) {
                focusManager.clearFocus(force = true)
                keyboardWasVisible = false
            }
        }
        val horizontalPadding = when {
            isWideTablet -> 64.dp
            isTablet -> 40.dp
            else -> 24.dp
        }
        val curveDepth = when {
            compactLandscape -> 18.dp
            isWideTablet -> 48.dp
            isLandscape -> 32.dp
            isTablet -> 34.dp
            else -> 26.dp
        }
        val basePanelHeight = when {
            compactLandscape -> (maxHeight * 0.64f).coerceIn(238.dp, 326.dp)
            isWideTablet -> (maxHeight * 0.47f).coerceIn(350.dp, 430.dp)
            isLandscape -> (maxHeight * 0.56f).coerceIn(260.dp, 368.dp)
            isTablet -> (maxHeight * 0.44f).coerceIn(382.dp, 520.dp)
            else -> (maxHeight * 0.42f).coerceIn(352.dp, 470.dp)
        }
        val focusedPanelHeight = maxHeight + curveDepth
        val panelHeight by animateDpAsState(
            targetValue = if (panelExpanded) focusedPanelHeight else basePanelHeight,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "auth_panel_keyboard_height",
        )
        val formMaxWidth = when {
            isWideTablet -> 500.dp
            isTablet -> 410.dp
            else -> 340.dp
        }
        val heroTopPadding = when {
            compactLandscape -> statusBarTop + 20.dp
            isWideTablet -> statusBarTop + (maxHeight * 0.11f)
            isLandscape -> statusBarTop + (maxHeight * 0.10f)
            isTablet -> statusBarTop + (maxHeight * 0.15f)
            else -> statusBarTop + (maxHeight * 0.15f)
        }

        Crossfade(
            targetState = spotlight,
            animationSpec = tween(AuthSpotlightCrossfadeMs, easing = FastOutSlowInEasing),
            label = "auth_spotlight_background",
            modifier = Modifier.fillMaxSize(),
        ) { item ->
            AuthSpotlightBackground(
                item = item,
                isTablet = isTablet,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AuthSpotlightScrims(modifier = Modifier.fillMaxSize())

        Crossfade(
            targetState = spotlight,
            animationSpec = tween(AuthSpotlightCrossfadeMs, easing = FastOutSlowInEasing),
            label = "auth_spotlight_info",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = heroTopPadding)
                .padding(horizontal = horizontalPadding),
        ) { item ->
            AuthSpotlightInfo(
                item = item,
                isTablet = isTablet,
                isWideTablet = isWideTablet,
                compactLandscape = compactLandscape,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AuthCurvedLoginPanel(
            authMode = authMode,
            email = email,
            password = password,
            passwordVisible = passwordVisible,
            isLoading = isLoading,
            authError = authError,
            authMessage = authMessage,
            canSubmit = email.isNotBlank() && password.length >= 6 && !isLoading,
            panelHeight = panelHeight,
            curveDepth = curveDepth,
            horizontalPadding = horizontalPadding,
            formMaxWidth = formMaxWidth,
            navigationBottom = navigationBottom,
            compactLandscape = compactLandscape,
            isTablet = isTablet,
            formFocused = panelExpanded,
            onEmailChange = {
                email = it
                authMessage = null
                AuthRepository.clearError()
            },
            onPasswordChange = {
                password = it
                authMessage = null
                AuthRepository.clearError()
            },
            onEmailFocusChange = { emailFocused = it },
            onPasswordFocusChange = { passwordFocused = it },
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
            onSubmit = ::submitAuth,
            onModeToggle = {
                authMode = if (authMode == AuthMode.SignIn) AuthMode.CreateAccount else AuthMode.SignIn
                authMessage = null
                AuthRepository.clearError()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun AuthSpotlightBackground(
    item: ProfileSpotlightItem?,
    isTablet: Boolean,
    modifier: Modifier = Modifier,
) {
    val imageUrl = item?.banner
    val zoom = remember(item?.type, item?.id, imageUrl) { Animatable(1f) }

    LaunchedEffect(item?.type, item?.id, imageUrl) {
        zoom.snapTo(1f)
        zoom.animateTo(
            targetValue = if (isTablet) 1.05f else 1.08f,
            animationSpec = tween(AuthSpotlightZoomMs, easing = LinearEasing),
        )
    }

    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF111721),
                        Color(0xFF05070B),
                        Color.Black,
                    ),
                ),
            ),
        )
        return
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = item.name,
        modifier = modifier.graphicsLayer {
            scaleX = zoom.value
            scaleY = zoom.value
        },
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
    )
}

@Composable
private fun AuthSpotlightScrims(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.78f),
                            0.12f to Color.Black.copy(alpha = 0.42f),
                            0.30f to Color.Black.copy(alpha = 0.16f),
                            0.48f to Color.Black.copy(alpha = 0.18f),
                            0.62f to Color.Black.copy(alpha = 0.48f),
                            0.78f to Color(0xFF020611).copy(alpha = 0.78f),
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
                            0f to Color.Black.copy(alpha = 0.70f),
                            0.14f to Color.Black.copy(alpha = 0.34f),
                            0.34f to Color.Transparent,
                            0.66f to Color.Transparent,
                            0.86f to Color.Black.copy(alpha = 0.34f),
                            1f to Color.Black.copy(alpha = 0.70f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f),
                        ),
                        radius = 1_050f,
                    ),
                ),
        )
    }
}

@Composable
private fun AuthSpotlightInfo(
    item: ProfileSpotlightItem?,
    isTablet: Boolean,
    isWideTablet: Boolean,
    compactLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    if (item == null) return
    var logoFailed by remember(item.logo) { mutableStateOf(false) }
    val showLogo = !item.logo.isNullOrBlank() && !logoFailed
    val genreText = remember(item.genres) {
        item.genres
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
        if (showLogo) {
            AsyncImage(
                model = item.logo,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth(
                        when {
                            isWideTablet -> 0.34f
                            compactLandscape -> 0.26f
                            isTablet -> 0.42f
                            else -> 0.52f
                        },
                    )
                    .widthIn(
                        max = when {
                            isWideTablet -> 390.dp
                            compactLandscape -> 250.dp
                            isTablet -> 390.dp
                            else -> 270.dp
                        },
                    )
                    .aspectRatio(3.2f),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                onError = { logoFailed = true },
            )
        } else {
            Text(
                text = item.name,
                modifier = Modifier.widthIn(
                    max = when {
                        isWideTablet -> 620.dp
                        compactLandscape -> 360.dp
                        isTablet -> 540.dp
                        else -> 340.dp
                    },
                ),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = when {
                        isWideTablet -> 34.sp
                        compactLandscape -> 22.sp
                        isTablet -> 31.sp
                        else -> 25.sp
                    },
                    letterSpacing = 0.sp,
                ),
                color = Color.White,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (genreText.isNotBlank()) {
            Spacer(modifier = Modifier.height(if (compactLandscape) 2.dp else 5.dp))
            Text(
                text = genreText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = when {
                        isWideTablet -> 12.sp
                        compactLandscape -> 10.sp
                        isTablet -> 12.sp
                        else -> 10.5.sp
                    },
                ),
                color = Color.White.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AuthCurvedLoginPanel(
    authMode: AuthMode,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    authMessage: String?,
    canSubmit: Boolean,
    panelHeight: androidx.compose.ui.unit.Dp,
    curveDepth: androidx.compose.ui.unit.Dp,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    formMaxWidth: androidx.compose.ui.unit.Dp,
    navigationBottom: androidx.compose.ui.unit.Dp,
    compactLandscape: Boolean,
    isTablet: Boolean,
    formFocused: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onEmailFocusChange: (Boolean) -> Unit,
    onPasswordFocusChange: (Boolean) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onModeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryText = if (authMode == AuthMode.SignIn) {
        stringResource(Res.string.compose_auth_sign_in)
    } else {
        stringResource(Res.string.compose_auth_sign_up)
    }
    val modePrefix = if (authMode == AuthMode.SignIn) {
        stringResource(Res.string.compose_auth_dont_have_account)
    } else {
        stringResource(Res.string.compose_auth_already_have_account)
    }
    val modeAction = if (authMode == AuthMode.SignIn) {
        stringResource(Res.string.compose_auth_sign_up)
    } else {
        stringResource(Res.string.compose_auth_sign_in)
    }
    val primaryIcon = if (authMode == AuthMode.SignIn) Icons.Rounded.Login else Icons.Rounded.PersonAdd
    val fieldHeight = when {
        compactLandscape -> 38.dp
        isTablet -> 44.dp
        else -> 50.dp
    }
    val primaryHeight = when {
        compactLandscape -> 40.dp
        isTablet -> 46.dp
        else -> 52.dp
    }
    val wordmarkWidth = when {
        compactLandscape -> 92.dp
        isTablet -> 126.dp
        else -> 122.dp
    }
    val wordmarkHeight = when {
        compactLandscape -> 24.dp
        isTablet -> 34.dp
        else -> 38.dp
    }
    val contentTopPadding = if (formFocused) {
        when {
            compactLandscape -> 6.dp
            isTablet -> 10.dp
            else -> 12.dp
        }
    } else {
        curveDepth + when {
            compactLandscape -> 8.dp
            isTablet -> 12.dp
            else -> 16.dp
        }
    }
    val contentBottomPadding = if (formFocused) {
        when {
            compactLandscape -> 6.dp
            isTablet -> 10.dp
            else -> 12.dp
        }
    } else {
        navigationBottom + when {
            compactLandscape -> 8.dp
            isTablet -> 14.dp
            else -> 22.dp
        }
    }
    val formSpacing = when {
        compactLandscape -> 6.dp
        isTablet -> 8.dp
        else -> 10.dp
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF2A050A).copy(alpha = 0.98f),
                            0.18f to Color(0xFF11070D),
                            0.40f to AuthPanelBlueBlack,
                            0.58f to AuthPanelBlack,
                            1f to Color.Black,
                        ),
                    ),
                    shape = AuthLoginShelfShape(curveDepth = curveDepth),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
                    .padding(top = contentTopPadding)
                    .padding(bottom = contentBottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (formFocused) Arrangement.Center else Arrangement.Top,
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_logo_wordmark),
                    contentDescription = stringResource(Res.string.app_brand_name),
                    modifier = Modifier
                        .width(wordmarkWidth)
                        .height(wordmarkHeight),
                    contentScale = ContentScale.Fit,
                )

                Spacer(modifier = Modifier.height(if (compactLandscape) 5.dp else 8.dp))

                Column(
                    modifier = Modifier
                        .widthIn(max = formMaxWidth)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(formSpacing),
                ) {
                    AuthTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        placeholder = stringResource(Res.string.compose_auth_email),
                        leadingIcon = Icons.Outlined.Email,
                        enabled = !isLoading,
                        fieldHeight = fieldHeight,
                        onFocusChanged = onEmailFocusChange,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    )

                    AuthTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        placeholder = stringResource(Res.string.compose_auth_password),
                        leadingIcon = Icons.Outlined.Lock,
                        enabled = !isLoading,
                        fieldHeight = fieldHeight,
                        onFocusChanged = onPasswordFocusChange,
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                        trailingIcon = {
                            IconButton(
                                onClick = onPasswordVisibilityToggle,
                                enabled = !isLoading,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.68f),
                                )
                            }
                        },
                    )

                    authError?.takeIf(String::isNotBlank)?.let { errorText ->
                        AuthMessageText(text = errorText, color = MaterialTheme.colorScheme.error)
                    }
                    authMessage?.takeIf(String::isNotBlank)?.let { messageText ->
                        AuthMessageText(text = messageText, color = Color.White.copy(alpha = 0.80f))
                    }

                    AuthActionButton(
                        text = primaryText,
                        icon = primaryIcon,
                        enabled = canSubmit,
                        isLoading = isLoading,
                        height = primaryHeight,
                        containerColor = AuthNetflixRed,
                        contentColor = Color.White,
                        onClick = onSubmit,
                    )

                    AuthModeLink(
                        prefix = modePrefix,
                        action = modeAction,
                        enabled = !isLoading,
                        onClick = onModeToggle,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    enabled: Boolean,
    fieldHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused
    val accent by animateColorAsState(
        targetValue = if (active) AuthNetflixRed else Color.Transparent,
        label = "auth_field_accent",
    )
    val shape = RoundedCornerShape(13.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(fieldHeight)
            .clip(shape)
            .background(AuthFieldFill.copy(alpha = 0.82f))
            .border(
                width = if (active) 1.dp else 0.dp,
                color = accent,
                shape = shape,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 15.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.58f),
                modifier = Modifier.size(19.dp),
            )
            Spacer(modifier = Modifier.width(11.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocusChanged(it.isFocused)
                    },
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                ),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                cursorBrush = SolidColor(AuthNetflixRed),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = Color.White.copy(alpha = 0.42f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            trailingIcon?.invoke()
        }
    }
}

@Composable
private fun AuthActionButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    isLoading: Boolean,
    height: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Button(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.42f),
            disabledContentColor = contentColor.copy(alpha = 0.50f),
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.4.dp,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        letterSpacing = 0.2.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AuthModeLink(
    prefix: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
            ),
            color = Color.White.copy(alpha = 0.62f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = action,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 3.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
            ),
            color = AuthNetflixRed.copy(alpha = if (enabled) 1f else 0.45f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AuthMessageText(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.24f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
        ),
        color = color,
        textAlign = TextAlign.Center,
    )
}

private class AuthLoginShelfShape(
    private val curveDepth: androidx.compose.ui.unit.Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val depth = with(density) { curveDepth.toPx() }.coerceAtMost(size.height * 0.28f)
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

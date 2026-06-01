package com.nuvio.app.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.auth.AuthRepository
import com.nuvio.app.core.ui.NuvioSurfaceCard
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark
import nuvio.composeapp.generated.resources.compose_auth_already_have_account
import nuvio.composeapp.generated.resources.compose_auth_create_account
import nuvio.composeapp.generated.resources.compose_auth_dont_have_account
import nuvio.composeapp.generated.resources.compose_auth_email
import nuvio.composeapp.generated.resources.compose_auth_password
import nuvio.composeapp.generated.resources.compose_auth_sign_in
import nuvio.composeapp.generated.resources.compose_auth_sign_in_subtitle
import nuvio.composeapp.generated.resources.compose_auth_sign_up
import nuvio.composeapp.generated.resources.compose_auth_sign_up_subtitle
import nuvio.composeapp.generated.resources.compose_auth_tagline
import nuvio.composeapp.generated.resources.compose_auth_welcome_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val AuthNetflixRed = Color(0xFFE50914)
private val AuthNetflixDarkRed = Color(0xFF650006)
private val AuthFieldBackground = Color(0xFF151515)
private val AuthFieldBorder = Color(0xFF3A3A3A)

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
) {
    val authError by AuthRepository.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var authMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF230004),
                            Color.Black,
                            Color(0xFF090909),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AuthNetflixDarkRed.copy(alpha = 0.42f),
                            Color.Transparent,
                        ),
                        radius = 900f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = statusBarTop + 60.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(Res.drawable.app_logo_wordmark),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(48.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.compose_auth_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            NuvioSurfaceCard {
                AnimatedContent(
                    targetState = isSignUp,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "heading",
                ) { signUp ->
                    Text(
                        text = if (signUp) stringResource(Res.string.compose_auth_create_account)
                        else stringResource(Res.string.compose_auth_welcome_back),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                AnimatedContent(
                    targetState = isSignUp,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "subtitle",
                ) { signUp ->
                    Text(
                        text = if (signUp) stringResource(Res.string.compose_auth_sign_up_subtitle)
                        else stringResource(Res.string.compose_auth_sign_in_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        authMessage = null
                        AuthRepository.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.compose_auth_email),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuthNetflixRed,
                        unfocusedBorderColor = AuthFieldBorder,
                        focusedContainerColor = AuthFieldBackground,
                        unfocusedContainerColor = AuthFieldBackground,
                        cursorColor = AuthNetflixRed,
                    ),
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        authMessage = null
                        AuthRepository.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.compose_auth_password),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (email.isNotBlank() && password.isNotBlank() && !isLoading) {
                                isLoading = true
                                scope.launch {
                                    val result = if (isSignUp) {
                                        AuthRepository.signUpWithEmail(email, password)
                                    } else {
                                        AuthRepository.signInWithEmail(email, password)
                                    }
                                    if (isSignUp && result.isSuccess) {
                                        authMessage = "Account created. Please check your email to verify your account."
                                        isSignUp = false
                                        password = ""
                                    }
                                    isLoading = false
                                }
                            }
                        },
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuthNetflixRed,
                        unfocusedBorderColor = AuthFieldBorder,
                        focusedContainerColor = AuthFieldBackground,
                        unfocusedContainerColor = AuthFieldBackground,
                        cursorColor = AuthNetflixRed,
                    ),
                )

                authError?.let { errorText ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                authMessage?.let { messageText ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.86f),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    enabled = email.isNotBlank() && password.length >= 6 && !isLoading,
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val result = if (isSignUp) {
                                AuthRepository.signUpWithEmail(email, password)
                            } else {
                                AuthRepository.signInWithEmail(email, password)
                            }
                            if (isSignUp && result.isSuccess) {
                                authMessage = "Account created. Please check your email to verify your account."
                                isSignUp = false
                                password = ""
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuthNetflixRed,
                        contentColor = Color.White,
                        disabledContainerColor = AuthNetflixRed.copy(alpha = 0.35f),
                        disabledContentColor = Color.White.copy(alpha = 0.55f),
                    ),
                ) {
                    Text(
                        text = if (isLoading) {
                            ""
                        } else if (isSignUp) {
                            stringResource(Res.string.compose_auth_create_account)
                        } else {
                            stringResource(Res.string.compose_auth_sign_in)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = isSignUp,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "togglePrompt",
                    ) { signUp ->
                        Text(
                            text = if (signUp) stringResource(Res.string.compose_auth_already_have_account)
                            else stringResource(Res.string.compose_auth_dont_have_account),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedContent(
                        targetState = isSignUp,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "toggleAction",
                    ) { signUp ->
                        Text(
                            text = if (signUp) stringResource(Res.string.compose_auth_sign_in)
                            else stringResource(Res.string.compose_auth_sign_up),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AuthNetflixRed,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                isSignUp = !isSignUp
                                authMessage = null
                                AuthRepository.clearError()
                            },
                        )
                    }
                }
            }
        }
    }
}

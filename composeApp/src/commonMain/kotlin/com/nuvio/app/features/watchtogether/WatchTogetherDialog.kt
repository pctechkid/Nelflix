package com.nuvio.app.features.watchtogether

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val WatchTogetherRed = Color(0xFFE50914)
private val WatchTogetherDialogBackground = Color(0xFF111111)
private val WatchTogetherFieldBackground = Color(0xFF050505)

@Composable
fun WatchTogetherDialog(
    session: WatchTogetherRoomState?,
    joinCode: String,
    isBusy: Boolean,
    errorMessage: String?,
    canUseWatchTogether: Boolean,
    onJoinCodeChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLeaveRoom: () -> Unit,
    onDismiss: () -> Unit,
) {
    var joiningRoom by rememberSaveable(session == null) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WatchTogetherDialogBackground,
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD6D6D6),
        shape = RoundedCornerShape(18.dp),
        title = {
            Text("Watch Together")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!canUseWatchTogether) {
                    Text("Sign in with an account to use Watch Together.")
                } else if (session != null) {
                    Text(
                        text = if (session.isHost) {
                            "Share this code. You control playback for ${session.memberCount} member(s)."
                        } else {
                            "Synced with ${session.memberCount} member(s)."
                        },
                    )
                    Text(
                        text = session.roomCode,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (joiningRoom) {
                    Text("Enter a room code to join the synced stream.")
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { value ->
                            onJoinCodeChange(value.uppercase().filter { it.isLetterOrDigit() }.take(8))
                        },
                        label = { Text("Room code") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = WatchTogetherFieldBackground,
                            unfocusedContainerColor = WatchTogetherFieldBackground,
                            focusedBorderColor = WatchTogetherRed,
                            unfocusedBorderColor = Color(0xFF595959),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color(0xFFBDBDBD),
                            cursorColor = WatchTogetherRed,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Create a room from this stream, or join a room with a code.")
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF8A8A),
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                if (session == null) {
                    if (joiningRoom) {
                        OutlinedButton(
                            onClick = {
                                joiningRoom = false
                                onJoinCodeChange("")
                            },
                            enabled = !isBusy,
                            border = BorderStroke(1.dp, WatchTogetherRed),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color(0xFF777777),
                            ),
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = onJoinRoom,
                            enabled = canUseWatchTogether && !isBusy && joinCode.length >= 4,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WatchTogetherRed,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Go")
                        }
                    } else {
                        Button(
                            onClick = onCreateRoom,
                            enabled = canUseWatchTogether && !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WatchTogetherRed,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Create")
                        }
                        Button(
                            onClick = { joiningRoom = true },
                            enabled = canUseWatchTogether && !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2A2A2A),
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Join")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onLeaveRoom,
                        enabled = !isBusy,
                        border = BorderStroke(1.dp, WatchTogetherRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Text("Leave")
                    }
                }
            }
        },
    )
}

package com.audiofactory.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.audiofactory.app.R
import com.audiofactory.app.ui.theme.StudioBlue
import com.audiofactory.app.ui.theme.StudioCardBorder
import com.audiofactory.app.ui.theme.StudioPurple
import com.audiofactory.app.ui.theme.StudioRose
import com.audiofactory.app.ui.theme.StudioSurface
import com.audiofactory.app.ui.theme.StudioTextMuted
import com.audiofactory.app.ui.theme.StudioTextPrimary
import com.audiofactory.app.ui.theme.StudioTextSecondary

@Composable
fun ExitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = StudioRose,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dialog_exit_title),
                    color = StudioTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_exit_message),
                color = StudioTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StudioRose),
                modifier = Modifier.testTag("dialog_exit_confirm_button")
            ) {
                Text(stringResource(R.string.action_exit), color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_exit_cancel_button")
            ) {
                Text(stringResource(R.string.action_cancel), color = StudioTextSecondary)
            }
        },
        containerColor = StudioSurface,
        modifier = Modifier.testTag("dialog_exit_confirm")
    )
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, StudioCardBorder, RoundedCornerShape(20.dp))
                .testTag("about_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(StudioBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = StudioBlue,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Audio Factory",
                    color = StudioTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Version 1.0 (Build 1)",
                    color = StudioTextMuted,
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Production AI Voice & Audio Studio with Multi-Speaker Generation, Audio Playback, and Lossless Export.",
                    color = StudioTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Production Host: audiofactory.vercel.app\nEnvironment: Native Android Client",
                    color = StudioTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("about_dialog_close_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioBlue)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun MicrophoneRationaleDialog(
    onGrant: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = StudioPurple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.permission_audio_title),
                    color = StudioTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = stringResource(R.string.permission_audio_desc),
                color = StudioTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = StudioPurple),
                modifier = Modifier.testTag("permission_grant_button")
            ) {
                Text(stringResource(R.string.permission_grant), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDeny,
                modifier = Modifier.testTag("permission_deny_button")
            ) {
                Text(stringResource(R.string.permission_deny), color = StudioTextMuted)
            }
        },
        containerColor = StudioSurface,
        modifier = Modifier.testTag("permission_audio_dialog")
    )
}

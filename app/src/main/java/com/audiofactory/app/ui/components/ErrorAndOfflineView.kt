package com.audiofactory.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiofactory.app.R
import com.audiofactory.app.data.WebErrorType
import com.audiofactory.app.ui.theme.StudioBackground
import com.audiofactory.app.ui.theme.StudioBlue
import com.audiofactory.app.ui.theme.StudioCardBorder
import com.audiofactory.app.ui.theme.StudioRose
import com.audiofactory.app.ui.theme.StudioSurface
import com.audiofactory.app.ui.theme.StudioTextMuted
import com.audiofactory.app.ui.theme.StudioTextPrimary
import com.audiofactory.app.ui.theme.StudioTextSecondary

@Composable
fun ErrorAndOfflineView(
    errorType: WebErrorType,
    canGoBack: Boolean,
    onRetry: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    onGoBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, title, description, accentColor) = when (errorType) {
        is WebErrorType.Offline -> Quadruple(
            Icons.Default.WifiOff,
            stringResource(R.string.title_offline),
            stringResource(R.string.desc_offline),
            Color(0xFFFBBC04)
        )
        is WebErrorType.DnsError -> Quadruple(
            Icons.Default.CloudOff,
            "Server Unreachable",
            "Could not connect to Audio Factory host (${errorType.host}). Please verify your internet connection or DNS resolver.",
            StudioRose
        )
        is WebErrorType.SslError -> Quadruple(
            Icons.Default.Security,
            "Security Certificate Issue",
            "A secure TLS connection to Audio Factory could not be established. For security reasons, connection was terminated.",
            StudioRose
        )
        is WebErrorType.HttpError -> Quadruple(
            Icons.Default.Warning,
            "Server Error (${errorType.statusCode})",
            "The Audio Factory studio server responded with HTTP ${errorType.statusCode}: ${errorType.description}",
            StudioRose
        )
        is WebErrorType.RendererCrash -> Quadruple(
            Icons.Default.Refresh,
            "Studio Display Recovered",
            "The web audio renderer encountered an unexpected issue and was restarted. Please reload the studio.",
            StudioBlue
        )
        is WebErrorType.Unknown -> Quadruple(
            Icons.Default.Warning,
            stringResource(R.string.title_error),
            errorType.description.ifEmpty { stringResource(R.string.desc_error) },
            StudioRose
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackground)
            .padding(24.dp)
            .testTag("error_offline_screen"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .border(1.dp, StudioCardBorder, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = StudioSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon Header
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = StudioTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Action Buttons
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("error_retry_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_retry),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenNetworkSettings,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("error_network_settings_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = StudioTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_open_network),
                            color = StudioTextSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (canGoBack) {
                        OutlinedButton(
                            onClick = onGoBack,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("error_back_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = StudioTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.btn_back),
                                color = StudioTextSecondary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

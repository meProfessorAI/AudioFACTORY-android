package com.audiofactory.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiofactory.app.R
import com.audiofactory.app.ui.theme.StudioBackground
import com.audiofactory.app.ui.theme.StudioBlue
import com.audiofactory.app.ui.theme.StudioGreen
import com.audiofactory.app.ui.theme.StudioRose
import com.audiofactory.app.ui.theme.StudioSurface
import com.audiofactory.app.ui.theme.StudioTextMuted
import com.audiofactory.app.ui.theme.StudioTextPrimary
import com.audiofactory.app.ui.theme.StudioTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFactoryTopBar(
    isNetworkAvailable: Boolean,
    isLoading: Boolean,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onClearCache: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val statusColor = when {
        !isNetworkAvailable -> StudioRose
        isLoading -> Color(0xFFFBBC04)
        else -> StudioGreen
    }

    TopAppBar(
        modifier = modifier.testTag("audio_factory_top_bar"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = StudioBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Audio Factory",
                    color = StudioTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Network status indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
        },
        actions = {
            IconButton(
                onClick = onReload,
                modifier = Modifier.testTag("top_bar_reload_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_reload),
                    tint = StudioTextSecondary
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("top_bar_overflow_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = StudioTextSecondary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .background(StudioSurface)
                        .testTag("top_bar_dropdown_menu")
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share), color = StudioTextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null, tint = StudioTextSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_clear_cache), color = StudioTextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = StudioTextSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onClearCache()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_privacy), color = StudioTextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = StudioTextSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenPrivacy()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_about), color = StudioTextPrimary) },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null, tint = StudioTextSecondary)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenAbout()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = StudioBackground,
            titleContentColor = StudioTextPrimary
        )
    )
}

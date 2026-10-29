package com.audiofactory.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.audiofactory.app.ui.theme.StudioPurple
import com.audiofactory.app.ui.theme.StudioTextMuted
import com.audiofactory.app.ui.theme.StudioTextPrimary

@Composable
fun AudioLoadingIndicator(
    progress: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_bars")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = 46f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 38f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 18f,
        targetValue = 54f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b3"
    )

    val bar4Height by infiniteTransition.animateFloat(
        initialValue = 42f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b4"
    )

    val bar5Height by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 36f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "b5"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioBackground)
            .testTag("audio_loading_indicator"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Equalizer Bars
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(60.dp)
            ) {
                EqualizerBar(height = bar1Height, color = StudioBlue)
                EqualizerBar(height = bar2Height, color = Color(0xFF60A5FA))
                EqualizerBar(height = bar3Height, color = StudioPurple)
                EqualizerBar(height = bar4Height, color = Color(0xFFEC4899))
                EqualizerBar(height = bar5Height, color = StudioGreen)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Audio FACTORY",
                color = StudioTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.desc_loading),
                color = StudioTextMuted,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .width(180.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = StudioBlue,
                    trackColor = Color(0xFF1E2330),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$progress%",
                    color = StudioTextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun EqualizerBar(height: Float, color: Color) {
    Box(
        modifier = Modifier
            .width(8.dp)
            .height(height.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
    )
}

package com.finovo.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.components.FinovoLogo
import com.finovo.app.util.S

/** Splash Screen: Finovo-Branding beim Start, kurz und ruhig. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = S("splash", "splash")
    )

    LaunchedEffect(Unit) {
        visible = true
        kotlinx.coroutines.delay(1200)
        visible = false
        kotlinx.coroutines.delay(320)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            FinovoLogo(
                iconSize = 132.dp,
                fontSize = 32.sp
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .alpha(alpha)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    S("lokale_finanzen_offline_auf_diesem_ger", "Lokale Finanzen – offline auf diesem Gerät"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

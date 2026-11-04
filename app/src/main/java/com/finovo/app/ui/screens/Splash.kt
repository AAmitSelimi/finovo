package com.finovo.app.ui.screens

import androidx.compose.foundation.layout.*
import com.finovo.app.ui.theme.appColors
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.drawBehind
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
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            val glow = appColors().glow
            Box(
                modifier = Modifier.drawBehind {
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.45f), androidx.compose.ui.graphics.Color.Transparent)
                        ),
                        radius = size.minDimension * 0.75f
                    )
                },
                contentAlignment = Alignment.Center
            ) {
                FinovoLogo(
                    iconSize = 120.dp,
                    fontSize = 40.sp,
                    showSlogan = false,
                    showDivider = false
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                S("ab_slogan", "Dein Geld. Deine Kontrolle. Deine Privatsphäre."),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(26.dp))
            Column(horizontalAlignment = Alignment.Start) {
                listOf(
                    S("sp_feat_1", "Einnahmen & Ausgaben"),
                    S("sp_feat_2", "Rechnungen & Abos"),
                    S("sp_feat_3", "Sparziele & Budgets"),
                    S("sp_feat_4", "Analysen & Berichte"),
                    S("sp_feat_5", "Offline & Datenschutz")
                ).forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(com.finovo.app.ui.components.primaryGradient()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(f, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        // Leuchtende Welle am unteren Rand
        val wave = appColors().glow
        val cyan = appColors().cyan
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(180.dp)
                .alpha(alpha)
        ) {
            val w = size.width
            val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h * 0.55f)
                cubicTo(w * 0.3f, h * 0.15f, w * 0.6f, h * 0.95f, w, h * 0.35f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path,
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(wave.copy(alpha = 0.45f), cyan.copy(alpha = 0.10f))
                )
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

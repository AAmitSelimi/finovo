package com.finovo.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.theme.appColors
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.S

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onBiometric: () -> Unit,
    biometricEnabled: Boolean,
    verify: (String) -> Boolean
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var shake by remember { mutableStateOf(0f) }
    val offsetX by animateFloatAsState(
        targetValue = shake,
        animationSpec = tween(60),
        label = S("shake", "shake")
    )

    LaunchedEffect(error) {
        if (error) {
            shake = 16f
            delay(70)
            shake = -16f
            delay(70)
            shake = 10f
            delay(70)
            shake = 0f
            delay(1500)
            error = false
            entered = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = com.finovo.app.R.drawable.finovo_logo),
            contentDescription = S("finovo", "Finovo"),
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(14.dp))
        Text(S("willkommen_zurueck", "Willkommen zurück"), style = MaterialTheme.typography.headlineSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(
            S("bitte_gib_deine_pin_ein", "Bitte gib deine PIN ein"),
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().textMuted
        )
        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.offset(x = offsetX.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) { index ->
                val filled = index < entered.length.coerceAtMost(4)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .border(
                            2.dp,
                            if (error) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline,
                            CircleShape
                        )
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (error) {
            Text(
                S("falsche_pin_bitte_erneut_versuchen", "Falsche PIN. Bitte erneut versuchen."),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        } else if (entendedHint(entered)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        if (verify(entered)) onUnlocked() else error = true
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        S("entsperren", "Entsperren"),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("bio", "0", "⌫")
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "bio" -> {
                                if (biometricEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable(onClick = onBiometric),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Fingerprint,
                                            contentDescription = S("mit_fingerabdruck_entsperren", "Mit Fingerabdruck entsperren"),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.size(72.dp))
                                }
                            }
                            "⌫" -> Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (entered.isNotEmpty()) entered = entered.dropLast(1)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⌫", fontSize = 22.sp, color = appColors().textMuted)
                            }
                            else -> Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(appColors().cardAlt)
                                    .clickable {
                                        if (entered.length < 8) entered += key
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(key, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        }

        if (biometricEnabled) {
            Spacer(Modifier.height(18.dp))
            Text(
                S("alternativ_mit_fingerabdruck_entsperre", "Alternativ mit Fingerabdruck entsperren"),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun entendedHint(entered: String): Boolean = entered.length >= 4

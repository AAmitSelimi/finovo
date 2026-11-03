package com.finovo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.R
import com.finovo.app.util.S

// ---------------------------------------------------------------------------
// Finovo Branding
//   [ App-Icon ]
//        Finovo          („Fin" dunkelblau, „ovo" kräftiges Blau, fett & modern)
//   Deine Finanzen. Einfach. Privat.
//        ────────        (kurzer, dünner, abgerundeter blauer Strich)
// ---------------------------------------------------------------------------

private val NavyLight = Color(0xFF0B2A6B)   // „Fin"
private val BlueLight = Color(0xFF2F80ED)   // „ovo"
private val NavyDark = Color(0xFFCFE3FF)
private val BlueDark = Color(0xFF6EA8FF)
private val SloganLight = Color(0xFF4A5568) // dunkelgrau/dunkelblau
private val SloganDark = Color(0xFFAEBACD)

@Composable
fun FinovoWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 30.sp
) {
    val dark = isSystemInDarkTheme()
    val navy = if (dark) NavyDark else NavyLight
    val blue = if (dark) BlueDark else BlueLight
    val style = MaterialTheme.typography.headlineSmall.copy(
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("Fin", style = style, color = navy, maxLines = 1)
        Text("ovo", style = style, color = blue, maxLines = 1)
    }
}

/** Slogan: „Deine Finanzen. Einfach. Privat." */
@Composable
fun FinovoSlogan(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp
) {
    val dark = isSystemInDarkTheme()
    Text(
        text = S("app_tagline", "Deine Finanzen. Einfach. Privat."),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize),
        color = if (dark) SloganDark else SloganLight,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Kurzer, dünner, abgerundeter blauer Strich. */
@Composable
fun FinovoDivider(
    modifier: Modifier = Modifier,
    width: Dp = 64.dp
) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .size(width = width, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (dark) BlueDark else BlueLight)
    )
}

/**
 * Komplettes Branding, mittig ausgerichtet:
 * Icon → Finovo → Slogan → blauer Strich
 */
@Composable
fun FinovoLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 112.dp,
    fontSize: TextUnit = 30.sp,
    showSlogan: Boolean = true,
    showDivider: Boolean = true
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.finovo_logo),
            contentDescription = "Finovo",
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize * 0.22f))
        )
        Spacer(Modifier.height(14.dp))
        FinovoWordmark(fontSize = fontSize)
        if (showSlogan) {
            Spacer(Modifier.height(6.dp))
            FinovoSlogan()
        }
        if (showDivider) {
            Spacer(Modifier.height(14.dp))
            FinovoDivider()
        }
    }
}

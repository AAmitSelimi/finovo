package com.finovo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.finovo.app.data.ThemeMode
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finovo.app.ui.components.*
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.*
import com.finovo.app.data.*

// ---------------------------------------------------------------------------
// Colors
// ---------------------------------------------------------------------------

// Finovo Premium palette: navy, electric blue and cyan.
val Accent = Color(0xFF1F5EFF)
val AccentDark = Color(0xFF3D8BFF)
val Cyan = Color(0xFF22D3EE)
val Navy = Color(0xFF050B1F)
val IncomeLight = Color(0xFF0E9F62)
val ExpenseLight = Color(0xFFE5394B)
val IncomeDark = Color(0xFF34E08A)
val ExpenseDark = Color(0xFFFF5C6C)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3ECFF),
    onPrimaryContainer = Color(0xFF0A2A80),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF6FC),
    onSecondaryContainer = Color(0xFF053A47),
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF2F5FC),
    onBackground = Color(0xFF0A1330),
    surface = Color.White,
    onSurface = Color(0xFF0A1330),
    surfaceVariant = Color(0xFFEAF0FB),
    onSurfaceVariant = Color(0xFF55617A),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FE),
    outline = Color(0xFFCAD5EA),
    outlineVariant = Color(0xFFE1E8F5),
    error = ExpenseLight,
    onError = Color.White,
    scrim = Color(0x99000000)
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF12306E),
    onPrimaryContainer = Color(0xFFD5E4FF),
    secondary = Cyan,
    onSecondary = Color(0xFF00242C),
    secondaryContainer = Color(0xFF0B3A4A),
    onSecondaryContainer = Color(0xFFC9F6FF),
    tertiary = Color(0xFFA78BFA),
    background = Navy,
    onBackground = Color(0xFFEFF4FF),
    surface = Color(0xFF0B1531),
    onSurface = Color(0xFFEFF4FF),
    surfaceVariant = Color(0xFF13214A),
    onSurfaceVariant = Color(0xFF9FB0D4),
    surfaceContainer = Color(0xFF0D1838),
    surfaceContainerHigh = Color(0xFF111E44),
    surfaceContainerLow = Color(0xFF09122B),
    outline = Color(0xFF2A3D74),
    outlineVariant = Color(0xFF1A2A58),
    error = ExpenseDark,
    onError = Color(0xFF3A0008),
    scrim = Color(0xB3000000)
)

/** Extra semantic colors used across the app. */
data class AppColors(
    val income: Color,
    val expense: Color,
    val incomeContainer: Color,
    val expenseContainer: Color,
    val card: Color,
    val cardAlt: Color,
    val divider: Color,
    val textMuted: Color,
    val warning: Color,
    val warningContainer: Color,
    val chartPalette: List<Color>,
    /** Design-System: Dark-Mode-Flag, Leuchtfarben, Kartenverlauf und Hero-Verlauf. */
    val isDark: Boolean = false,
    val glow: Color = Accent,
    val cyan: Color = Cyan,
    val cardTop: Color = card,
    val cardBorder: Color = divider,
    val heroStart: Color = Accent,
    val heroEnd: Color = Navy
)

private val Palette = listOf(
    Color(0xFF3D8BFF),
    Color(0xFF22D3EE),
    Color(0xFF34E08A),
    Color(0xFFFFC53D),
    Color(0xFFA78BFA),
    Color(0xFFFF5C8A),
    Color(0xFFFF8A3D),
    Color(0xFF7C8DB5)
)

private val LightAppColors = AppColors(
    income = IncomeLight,
    expense = ExpenseLight,
    incomeContainer = Color(0xFFDDF7EA),
    expenseContainer = Color(0xFFFDE6E9),
    card = Color.White,
    cardAlt = Color(0xFFEFF4FD),
    divider = Color(0xFFE4EAF6),
    textMuted = Color(0xFF55617A),
    warning = Color(0xFFB45309),
    warningContainer = Color(0xFFFFF1DB),
    chartPalette = listOf(
        Color(0xFF1F5EFF),
        Color(0xFF06B6D4),
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899),
        Color(0xFFF97316),
        Color(0xFF64748B)
    ),
    isDark = false,
    glow = Accent,
    cyan = Color(0xFF06B6D4),
    cardTop = Color.White,
    cardBorder = Color(0xFFDDE5F4),
    heroStart = Color(0xFF2E6BFF),
    heroEnd = Color(0xFF0A2A80)
)

private val DarkAppColors = AppColors(
    income = IncomeDark,
    expense = ExpenseDark,
    incomeContainer = Color(0xFF0D3A2C),
    expenseContainer = Color(0xFF3D1424),
    card = Color(0xFF0B1636),
    cardAlt = Color(0xFF13214A),
    divider = Color(0xFF1A2A58),
    textMuted = Color(0xFF9FB0D4),
    warning = Color(0xFFFFB547),
    warningContainer = Color(0xFF3A2A10),
    chartPalette = Palette,
    isDark = true,
    glow = Color(0xFF3D8BFF),
    cyan = Cyan,
    cardTop = Color(0xFF12224F),
    cardBorder = Color(0xFF2A4A9A),
    heroStart = Color(0xFF1E63FF),
    heroEnd = Color(0xFF071A52)
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

val Typography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
)

/** Softer, larger corners as in the new design. */
val FinovoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FinovoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = FinovoShapes,
            content = content
        )
    }
}

/**
 * Premium-Hintergrund: dunkles Navy mit dezenten blauen/cyanfarbenen
 * Leuchtflaechen (Dark Mode) bzw. heller, leicht blaeulicher Verlauf (Light Mode).
 */
@Composable
fun FinovoBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = appColors()
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                if (c.isDark) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0xFF123A9E).copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width * 0.9f, -size.height * 0.02f),
                            radius = size.width * 1.0f
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0xFF0E7490).copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(0f, size.height * 0.75f),
                            radius = size.width * 0.9f
                        )
                    )
                } else {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0xFFD6E3FF), Color.Transparent),
                            center = Offset(size.width * 0.9f, 0f),
                            radius = size.width * 0.9f
                        )
                    )
                }
            }
    ) { content() }
}

/** Shortcut for the semantic colors. */
@Composable
fun appColors(): AppColors = LocalAppColors.current

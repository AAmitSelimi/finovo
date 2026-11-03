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

val Accent = Color(0xFF1B62F0)
val AccentDark = Color(0xFF7FA8FF)
val IncomeLight = Color(0xFF0B7A50)
val ExpenseLight = Color(0xFFDC2626)
val IncomeDark = Color(0xFF4FD39B)
val ExpenseDark = Color(0xFFFF7A75)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EFFE),
    onPrimaryContainer = Color(0xFF0B2C6B),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF0E1729),
    surface = Color.White,
    onSurface = Color(0xFF0E1729),
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = Color(0xFF566175),
    outline = Color(0xFFD3DAE6),
    outlineVariant = Color(0xFFE3E8F1),
    error = ExpenseLight,
    onError = Color.White,
    scrim = Color(0x99000000)
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0A0F1A),
    primaryContainer = Color(0xFF1C2B4D),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB6C2D1),
    onSecondary = Color(0xFF16202B),
    background = Color(0xFF0A0F1A),
    onBackground = Color(0xFFEDF2FB),
    surface = Color(0xFF141B2B),
    onSurface = Color(0xFFEDF2FB),
    surfaceVariant = Color(0xFF1B2437),
    onSurfaceVariant = Color(0xFF9DAAC0),
    outline = Color(0xFF34405A),
    outlineVariant = Color(0xFF232D44),
    error = ExpenseDark,
    onError = Color(0xFF4A0B0B),
    scrim = Color(0x99000000)
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
    val chartPalette: List<Color>
)

private val LightAppColors = AppColors(
    income = IncomeLight,
    expense = ExpenseLight,
    incomeContainer = Color(0xFFE2F4EC),
    expenseContainer = Color(0xFFFDECEC),
    card = Color.White,
    cardAlt = Color(0xFFF4F6FB),
    divider = Color(0xFFE3E8F1),
    textMuted = Color(0xFF566175),
    warning = Color(0xFF9A5200),
    warningContainer = Color(0xFFFFF3E0),
    chartPalette = listOf(
        Color(0xFF1B62F0),
        Color(0xFF0B7A50),
        Color(0xFFF59E0B),
        Color(0xFF8B5CF6),
        Color(0xFF06B6D4),
        Color(0xFFEF4444),
        Color(0xFFEC4899),
        Color(0xFF64748B)
    )
)

private val DarkAppColors = AppColors(
    income = IncomeDark,
    expense = ExpenseDark,
    incomeContainer = Color(0xFF14352B),
    expenseContainer = Color(0xFF3A1D1D),
    card = Color(0xFF141B2B),
    cardAlt = Color(0xFF1B2437),
    divider = Color(0xFF232D44),
    textMuted = Color(0xFF9DAAC0),
    warning = Color(0xFFF2A64A),
    warningContainer = Color(0xFF3B2E14),
    chartPalette = listOf(
        Color(0xFF7FA8FF),
        Color(0xFF4FD39B),
        Color(0xFFFBBF24),
        Color(0xFFA78BFA),
        Color(0xFF22D3EE),
        Color(0xFFFF7A75),
        Color(0xFFF472B6),
        Color(0xFF94A3B8)
    )
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

val Typography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
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

/** Shortcut for the semantic colors. */
@Composable
fun appColors(): AppColors = LocalAppColors.current

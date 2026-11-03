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

val Accent = Color(0xFF2563EB)
val AccentDark = Color(0xFF7EA6FF)
val IncomeLight = Color(0xFF16A34A)
val ExpenseLight = Color(0xFFDC2626)
val IncomeDark = Color(0xFF4ADE80)
val ExpenseDark = Color(0xFFFF7A75)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0B2C6B),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF111827),
    surface = Color.White,
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F3F7),
    onSurfaceVariant = Color(0xFF5B6472),
    outline = Color(0xFFD8DCE4),
    outlineVariant = Color(0xFFE8EBF0),
    error = ExpenseLight,
    onError = Color.White,
    scrim = Color(0x99000000)
)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF04224F),
    primaryContainer = Color(0xFF1B3A6B),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFB6C2D1),
    onSecondary = Color(0xFF16202B),
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEFF2),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFEDEFF2),
    surfaceVariant = Color(0xFF26282B),
    onSurfaceVariant = Color(0xFFAEB6C2),
    outline = Color(0xFF3A3D42),
    outlineVariant = Color(0xFF2A2C30),
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
    incomeContainer = Color(0xFFE7F7ED),
    expenseContainer = Color(0xFFFDECEC),
    card = Color.White,
    cardAlt = Color(0xFFF7F8FA),
    divider = Color(0xFFE9ECF1),
    textMuted = Color(0xFF6B7280),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFFF3E0),
    chartPalette = listOf(
        Color(0xFF2563EB),
        Color(0xFF16A34A),
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
    incomeContainer = Color(0xFF17301F),
    expenseContainer = Color(0xFF3A1D1D),
    card = Color(0xFF1C1C1E),
    cardAlt = Color(0xFF232529),
    divider = Color(0xFF2C2F34),
    textMuted = Color(0xFF9CA3AF),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF3B2E14),
    chartPalette = listOf(
        Color(0xFF7EA6FF),
        Color(0xFF4ADE80),
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
            content = content
        )
    }
}

/** Shortcut for the semantic colors. */
@Composable
fun appColors(): AppColors = LocalAppColors.current

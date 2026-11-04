package com.finovo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.finovo.app.data.CategoryEntity
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import java.time.LocalDate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.text.KeyboardOptions
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel

// ---------------------------------------------------------------------------
// Cards & surfaces
// ---------------------------------------------------------------------------

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = appColors().card,
    content: @Composable () -> Unit
) {
    val c = appColors()
    val shape = RoundedCornerShape(22.dp)
    // Premium-Karte: dezenter Verlauf, leuchtender Rand (Dark) bzw. weicher Schatten (Light).
    val fill: Brush = if (containerColor == c.card) {
        Brush.verticalGradient(listOf(c.cardTop, c.card))
    } else Brush.verticalGradient(listOf(containerColor, containerColor))
    val border: Brush = if (c.isDark) {
        Brush.linearGradient(
            listOf(c.cardBorder.copy(alpha = 0.85f), c.cardBorder.copy(alpha = 0.18f), c.cyan.copy(alpha = 0.25f))
        )
    } else Brush.linearGradient(listOf(c.cardBorder, c.cardBorder))
    val base = Modifier
        .fillMaxWidth()
        .shadow(
            elevation = if (c.isDark) 0.dp else 6.dp,
            shape = shape,
            ambientColor = c.glow.copy(alpha = 0.10f),
            spotColor = c.glow.copy(alpha = 0.12f)
        )
        .clip(shape)
        .background(fill)
        .border(1.dp, border, shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(modifier = modifier.then(clickable)) {
        content()
    }
}

/**
 * Premium-Flaeche fuer selbst gezeichnete Karten: schneidet auf [shape] zu,
 * fuellt mit dem Karten-Verlauf und zeichnet den leuchtenden Rand.
 */
@Composable
fun Modifier.premiumSurface(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp)): Modifier {
    val c = appColors()
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(c.cardTop, c.card)))
        .border(
            1.dp,
            if (c.isDark) Brush.linearGradient(
                listOf(c.cardBorder.copy(alpha = 0.85f), c.cardBorder.copy(alpha = 0.18f), c.cyan.copy(alpha = 0.25f))
            ) else Brush.linearGradient(listOf(c.cardBorder, c.cardBorder)),
            shape
        )
}

/** Leuchtende Scan-Ecken (Premium-Scanner) ueber einem Bild. Reine Zeichnung. */
@Composable
fun Modifier.scanCorners(): Modifier {
    val glow = appColors().glow
    val cyan = appColors().cyan
    return this.drawWithContent {
        drawContent()
        val len = minOf(size.width, size.height) * 0.14f
        val inset = 10.dp.toPx()
        val w = 4.dp.toPx()
        val brush = Brush.linearGradient(listOf(glow, cyan))
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(glow.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx * len, y), strokeWidth = w * 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(glow.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + dy * len), strokeWidth = w * 2.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(brush, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + dx * len, y), strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(brush, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + dy * len), strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        corner(inset, inset, 1f, 1f)
        corner(size.width - inset, inset, -1f, 1f)
        corner(inset, size.height - inset, 1f, -1f)
        corner(size.width - inset, size.height - inset, -1f, -1f)
    }
}

/**
 * Wiederverwendbares Akkordeon im Premium-Design: kompakter Kopf mit Icon,
 * Titel und Pfeil; Inhalt klappt animiert auf und zu. Startet eingeklappt.
 * Barrierefrei: ganzer Kopf ist ein Button (mind. 56 dp) mit Zustandsansage.
 */
@Composable
fun ExpandableSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "chevron"
    )
    val stateText = if (expanded) S("acc_expanded", "Ausgeklappt") else S("acc_collapsed", "Eingeklappt")
    val actionText = if (expanded) S("acc_collapse", "Einklappen") else S("acc_expand", "Ausklappen")
    AppCard(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = actionText,
                        onClick = { expanded = !expanded }
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = title
                        stateDescription = stateText
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(appColors().glow.copy(alpha = if (appColors().isDark) 0.22f else 0.10f))
                        .border(1.dp, appColors().glow.copy(alpha = 0.4f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = appColors().textMuted,
                    modifier = Modifier.size(26.dp).rotate(rotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(280)) + fadeIn(tween(280)),
                exit = shrinkVertically(animationSpec = tween(240)) + fadeOut(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

/** Leuchtender Verlauf fuer Hauptaktionen (elektrisches Blau). */
@Composable
fun primaryGradient(): Brush {
    val c = appColors()
    return Brush.horizontalGradient(
        if (c.isDark) listOf(Color(0xFF2F7BFF), Color(0xFF1E55F0))
        else listOf(Color(0xFF2E6BFF), Color(0xFF1F4FE0))
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Amounts
// ---------------------------------------------------------------------------

@Composable
fun AmountText(
    cents: Long,
    symbol: String = "€",
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    signed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sign = when {
        !signed -> ""
        cents > 0 -> "+"
        cents < 0 -> "−"
        else -> ""
    }
    Text(
        text = S("sign_money_format_cents_symbol", "%2\$s%1\$s", Money.format(cents, symbol), sign),
        style = style,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun StatTile(
    label: String,
    cents: Long,
    color: Color,
    symbol: String = "€",
    modifier: Modifier = Modifier,
    emoji: String? = null
) {
    AppCard(
        modifier = modifier,
        containerColor = appColors().card
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (emoji != null) {
                    Text(emoji, fontSize = 15.sp)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            AmountText(
                cents = cents,
                symbol = symbol,
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val glow = appColors().glow
    Button(
        onClick = onClick,
        modifier = modifier
            .height(54.dp)
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(
                if (enabled) primaryGradient()
                else Brush.horizontalGradient(listOf(appColors().cardAlt, appColors().cardAlt))
            ),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = appColors().textMuted
        ),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (appColors().isDark) appColors().cardBorder else MaterialTheme.colorScheme.outline
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (appColors().isDark) appColors().card.copy(alpha = 0.6f) else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    placeholder: String? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis) },
            placeholder = placeholder?.let { { Text(it) } },
            isError = error != null,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else maxLines,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = appColors().card,
                unfocusedContainerColor = appColors().card,
                disabledContainerColor = appColors().card,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (appColors().isDark) appColors().cardBorder.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
            )
        )
        AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    symbol: String = "€",
    error: String? = null,
    modifier: Modifier = Modifier,
    label: String = S("betrag", "Betrag")
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            // Only numbers and separators are accepted
            val clean = input.filter { it.isDigit() || it == ',' || it == '.' }
            if (clean.count { it == ',' || it == '.' } <= 2) onValueChange(clean)
        },
        label = { Text(label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.headlineSmall,
        leadingIcon = {
            Text(
                symbol,
                style = MaterialTheme.typography.titleMedium,
                color = appColors().textMuted,
                modifier = Modifier.padding(start = 4.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier.fillMaxWidth()
    )
    AnimatedVisibility(visible = error != null) {
        Text(
            text = error.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    epochDay: Long,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Datum"
) {
    var showPicker by remember { mutableStateOf(false) }
    val date = Dates.fromEpochDay(epochDay)
    OutlinedTextField(
        value = Dates.long(date),
        onValueChange = {},
        readOnly = true,
        label = { Text(label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        trailingIcon = {
            IconButton(onClick = { showPicker = true }) {
                Icon(Icons.Default.CalendarMonth, contentDescription = S("datum_waehlen", "Datum wählen"))
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = appColors().card,
                unfocusedContainerColor = appColors().card,
                disabledContainerColor = appColors().card,
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        enabled = false
    )
    if (showPicker) {
        AppDatePicker(
            initial = date,
            onDismiss = { showPicker = false },
            onConfirm = { picked ->
                showPicker = false
                onDateChange(picked)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val startMillis = Dates.zuMillis(initial)
    val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: startMillis
                // fromEpochDay begrenzt unmoegliche Werte - ofEpochDay wuerde
                // mit einer DateTimeException abbrechen.
                onConfirm(Dates.fromEpochDay(millis / 86_400_000L))
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(S("abbrechen_18", "Abbrechen")) }
        },
        shape = RoundedCornerShape(24.dp)
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    hour: Int,
    minute: Int,
    onTimeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Uhrzeit"
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = Dates.formatTime(hour, minute),
        onValueChange = {},
        readOnly = true,
        label = { Text(label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = appColors().card,
                unfocusedContainerColor = appColors().card,
                disabledContainerColor = appColors().card,
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true
        )
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        S("uhrzeit_waehlen", "Uhrzeit wählen"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                    TimePicker(state = state)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPicker = false }) { Text(S("abbrechen_19", "Abbrechen")) }
                        TextButton(onClick = {
                            showPicker = false
                            onTimeChange(state.hour, state.minute)
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = appColors().card,
                unfocusedContainerColor = appColors().card,
                disabledContainerColor = appColors().card,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chips & filters
// ---------------------------------------------------------------------------

@Composable
fun ChipRow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items.size) { index ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(items[index],
                maxLines = 1,
                overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = appColors().card,
                    labelColor = appColors().textMuted,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = index == selectedIndex,
                    borderColor = appColors().cardBorder.copy(alpha = if (appColors().isDark) 0.6f else 1f),
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Category picker
// ---------------------------------------------------------------------------

@Composable
fun CategoryPicker(
    categories: List<CategoryEntity>,
    selectedId: Long,
    onSelect: (CategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    onAddCategory: (() -> Unit)? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight(categories.size + if (onAddCategory != null) 1 else 0, columns)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false
    ) {
        items(categories, key = { it.id }) { category ->
            val selected = category.id == selectedId
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else appColors().cardAlt
                    )
                    .then(
                        if (selected) {
                            Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(14.dp)
                            )
                        } else Modifier
                    )
                    .clickable { onSelect(category) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(category.emoji, fontSize = 22.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = categoryLabel(category.name),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (onAddCategory != null) {
            item {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors().cardAlt)
                        .clickable { onAddCategory() }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = S("kategorie_hinzufuegen", "Kategorie hinzufügen"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        S("neu", "Neu"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun gridHeight(count: Int, columns: Int): Dp {
    val rows = (count + columns - 1) / columns
    return (rows * 74).dp
}

@Composable
fun CategoryBadge(emoji: String, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(
                Brush.linearGradient(
                    listOf(appColors().cardAlt, appColors().glow.copy(alpha = if (appColors().isDark) 0.22f else 0.10f))
                )
            )
            .border(1.dp, appColors().glow.copy(alpha = if (appColors().isDark) 0.35f else 0.15f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.45).sp)
    }
}

// ---------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------

@Composable
fun ProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = appColors().divider
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = S("progress", "progress")
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(color, androidx.compose.ui.graphics.lerp(color, Color.White, 0.25f))
                    )
                )
        )
    }
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().textMuted,
            modifier = Modifier.weight(1f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = appColors().cyan.copy(alpha = 0.6f),
                uncheckedTrackColor = appColors().cardAlt,
                uncheckedBorderColor = appColors().cardBorder,
                uncheckedThumbColor = appColors().textMuted
            )
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    emoji: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (emoji != null || icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(appColors().cardAlt, appColors().glow.copy(alpha = if (appColors().isDark) 0.22f else 0.10f))
                        )
                    )
                    .border(1.dp, appColors().glow.copy(alpha = if (appColors().isDark) 0.35f else 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(emoji.orEmpty(), fontSize = 18.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
        }
        if (trailing != null) trailing()
    }
}

// ---------------------------------------------------------------------------
// Empty states & dialogs
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(appColors().cardAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 36.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = appColors().textMuted,
            textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = actionLabel, onClick = onAction, icon = Icons.Default.Add)
        }
    }
}

@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String = S("loeschen_3", "Löschen"),
    dismissLabel: String = S("abbrechen", "Abbrechen"),
    dangerous: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = appColors().card,
        icon = {
            Icon(
                if (dangerous) Icons.Default.Warning else Icons.Default.Check,
                contentDescription = null,
                tint = if (dangerous) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (dangerous) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

// ---------------------------------------------------------------------------
// Top bars
// ---------------------------------------------------------------------------

@Composable
fun ScreenTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (onBack != null) 6.dp else 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = S("zurueck_3", "Zurück"))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
        }
        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

@Composable
fun MonthSelector(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ArrowBack, contentDescription = S("vorheriger_monat", "Vorheriger Monat"))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(150.dp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ArrowForward, contentDescription = S("naechster_monat", "Nächster Monat"))
        }
    }
}

@Composable
fun Pill(
    text: String,
    color: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun IconActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

fun Modifier.cardPadding(): Modifier = this.padding(horizontal = 20.dp, vertical = 8.dp)

@Composable
fun FullScreenLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

// ---------------------------------------------------------------------------
// Amount that always fits: shrinks the font until the text is fully visible.
// ---------------------------------------------------------------------------

@Composable
fun AutoSizeAmountText(
    cents: Long,
    symbol: String = "€",
    modifier: Modifier = Modifier,
    maxFontSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.titleMedium.fontSize,
    minFontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    signed: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1
) {
    val sign = when {
        !signed -> ""
        cents > 0 -> "+"
        cents < 0 -> "\u2212"
        else -> ""
    }
    val text = S("sign_money_format_cents_symbol_2", "%2\$s%1\$s", Money.format(cents, symbol), sign)
    val measurer = rememberTextMeasurer()
    val baseStyle = MaterialTheme.typography.titleMedium
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val fitted = remember(text, maxWidthPx, maxFontSize, minFontSize, fontWeight) {
            var size = maxFontSize.value
            val min = minFontSize.value
            if (maxWidthPx > 0) {
                while (size > min) {
                    val result = measurer.measure(
                        text = text,
                        style = baseStyle.copy(fontSize = size.sp, fontWeight = fontWeight),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        constraints = Constraints(maxWidth = maxWidthPx)
                    )
                    if (!result.hasVisualOverflow) break
                    size -= 0.5f
                }
            }
            size.coerceAtLeast(min)
        }
        Text(
            text = text,
            style = baseStyle.copy(fontSize = fitted.sp, fontWeight = fontWeight),
            color = color,
            maxLines = maxLines,
            softWrap = maxLines > 1,
            overflow = TextOverflow.Clip,
            textAlign = textAlign
        )
    }
}

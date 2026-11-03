package com.finovo.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.HorizontalBar
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Money
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel
import com.finovo.app.util.str

@Composable
fun BudgetScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val budget by viewModel.budget.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()

    var amountText by remember(budget.monthlyLimitCents) {
        mutableStateOf(
            if (budget.monthlyLimitCents > 0) {
                Money.format(budget.monthlyLimitCents, "", withSymbol = false)
            } else ""
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    val spent = dashboard.budgetSpentCents
    val limit = budget.monthlyLimitCents
    val percent = if (limit <= 0L) 0f else (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1.5f)
    val remaining = limit - spent
    val color: Color = when {
        limit <= 0L -> MaterialTheme.colorScheme.primary
        percent >= 1f -> appColors().expense
        percent >= 0.9f -> appColors().warning
        else -> appColors().income
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            ScreenTopBar(title = S("budget_2", "Budget"), subtitle = S("monatliches_limit", "Monatliches Limit"), onBack = onBack)
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .background(appColors().card)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            S("ausgegeben", "Ausgegeben"),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                        Text(
                            Money.format(spent, symbol),
                            style = MaterialTheme.typography.headlineSmall,
                            color = color,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        Text(
                            S("verbleibend", "Verbleibend"),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                        Text(
                            Money.format(remaining, symbol),
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (remaining < 0) appColors().expense else appColors().income,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalBar(percent = percent, color = color)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        S("von_money_format_limit_symbol", "von %1\$s", Money.format(limit, symbol)),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                    Text(
                        S("percent_100_toint_3", "%1\$d %%", (percent * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = color
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(18.dp))
            Text(
                S("monatliches_budget", "Monatliches Budget"),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
            AmountField(
                value = amountText,
                onValueChange = { amountText = it; error = null },
                symbol = symbol,
                error = error,
                label = S("budget_pro_monat", "Budget pro Monat"),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .background(appColors().card)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                SwitchRow(
                    title = S("budget_aktiv", "Budget aktiv"),
                    subtitle = S("zeigt_fortschritt_und_warnt_dich_recht", "Zeigt Fortschritt und warnt dich rechtzeitig"),
                    checked = budget.enabled,
                    onCheckedChange = {
                        val cents = Money.parseToCents(amountText) ?: budget.monthlyLimitCents
                        viewModel.saveBudget(
                            limitCents = cents,
                            enabled = it,
                            warn75 = budget.warn75,
                            warn90 = budget.warn90,
                            warn100 = budget.warn100
                        )
                    }
                )
                SwitchRow(
                    title = S("warnung_bei_75", "Warnung bei 75 %"),
                    subtitle = S("benachrichtigung_auf_dem_geraet", "Benachrichtigung auf dem Gerät"),
                    checked = budget.warn75,
                    onCheckedChange = {
                        viewModel.saveBudget(
                            budget.monthlyLimitCents, budget.enabled, it, budget.warn90, budget.warn100
                        )
                    }
                )
                SwitchRow(
                    title = S("warnung_bei_90", "Warnung bei 90 %"),
                    subtitle = S("benachrichtigung_auf_dem_geraet_2", "Benachrichtigung auf dem Gerät"),
                    checked = budget.warn90,
                    onCheckedChange = {
                        viewModel.saveBudget(
                            budget.monthlyLimitCents, budget.enabled, budget.warn75, it, budget.warn100
                        )
                    }
                )
                SwitchRow(
                    title = S("warnung_bei_100", "Warnung bei 100 %"),
                    subtitle = S("benachrichtigung_auf_dem_geraet_3", "Benachrichtigung auf dem Gerät"),
                    checked = budget.warn100,
                    onCheckedChange = {
                        viewModel.saveBudget(
                            budget.monthlyLimitCents, budget.enabled, budget.warn75, budget.warn90, it
                        )
                    }
                )
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = S("budget_speichern", "Budget speichern"),
                onClick = {
                    if (amountText.isBlank()) {
                        error = str("bitte_gib_einen_betrag_ein", "Bitte gib einen Betrag ein.")
                        return@PrimaryButton
                    }
                    val cents = Money.parseToCents(amountText)
                    if (cents == null || cents <= 0) {
                        error = str("bitte_gib_einen_gueltigen_betrag_e", "Bitte gib einen gültigen Betrag ein (z. B. 2000).")
                        return@PrimaryButton
                    }
                    viewModel.saveBudget(
                        limitCents = cents,
                        enabled = true,
                        warn75 = budget.warn75,
                        warn90 = budget.warn90,
                        warn100 = budget.warn100
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                S("die_warnungen_werden_lokal_auf_dem_ger", "Die Warnungen werden lokal auf dem Gerät erzeugt – ohne Internet und ohne Server."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(24.dp))
            CategoryBudgetsSection(viewModel = viewModel, symbol = symbol, categories = viewModel.categories.collectAsState().value)
        }
    }
}

@Composable
private fun CategoryBudgetsSection(
    viewModel: AppViewModel,
    symbol: String,
    categories: List<com.finovo.app.data.CategoryEntity>
) {
    val progress by viewModel.categoryBudgetProgress.collectAsState()
    val expenseCategories = categories.filter { it.type == com.finovo.app.data.CategoryType.EXPENSE.name }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Long?>(null) }

    SectionHeader(
        title = S("budgets_je_kategorie", "Budgets je Kategorie"),
        actionLabel = S("hinzufuegen", "Hinzufügen"),
        onAction = { editing = null; showEditor = true }
    )
    Spacer(Modifier.height(4.dp))

    if (progress.isEmpty()) {
        AppCard(modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(S("ud83d_udccb", "\uD83D\uDCCB"), fontSize = 30.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    S("noch_keine_kategorie_budgets", "Noch keine Kategorie-Budgets"),
                    style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    S("lege_fuer_einzelne_kategorien_ein_mona", "Lege für einzelne Kategorien ein monatliches Limit fest – zum Beispiel 300 € für Lebensmittel."),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        progress.forEach { item ->
            val color = when {
                item.over -> appColors().expense
                item.near -> appColors().warning
                else -> appColors().income
            }
            AppCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = { editing = item.categoryId; showEditor = true }
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(item.emoji, fontSize = 17.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                categoryLabel(item.name),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                S("money_format_item_spentcents_symbol_vo", "%1\$s von %2\$s", Money.format(item.spentCents, symbol), Money.format(item.limitCents, symbol)),
                                style = MaterialTheme.typography.bodySmall,
                                color = color
                            )
                            Text(
                                S("item_percent_100_toint", "%1\$d %%", (item.percent * 100).toInt()) +
                                    (if (item.over) str("budget_limit_exceeded", " · Limit überschritten") else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = color
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalBar(percent = item.percent, color = color)
                }
            }
        }
    }

    if (showEditor) {
        CategoryBudgetDialog(
            categories = expenseCategories,
            existingId = editing,
            symbol = symbol,
            onDismiss = { showEditor = false },
            onSave = { categoryId, limitCents ->
                viewModel.setCategoryBudget(categoryId, limitCents, true)
                showEditor = false
            },
            onDelete = { categoryId ->
                viewModel.setCategoryBudget(categoryId, 0L, false)
                showEditor = false
            }
        )
    }
}

@Composable
private fun CategoryBudgetDialog(
    categories: List<com.finovo.app.data.CategoryEntity>,
    existingId: Long?,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (Long, Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    if (categories.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var category by remember {
        mutableStateOf(categories.firstOrNull { it.id == existingId } ?: categories.first())
    }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S("kategorie_budget", "Kategorie-Budget")) },
        text = {
            Column {
                DropdownField(
                    label = S("kategorie", "Kategorie"),
                    options = categories,
                    selected = category,
                    optionLabel = { "${it.emoji} ${categoryLabel(it.name)}" },
                    onSelect = { category = it }
                )
                Spacer(Modifier.height(12.dp))
                AmountField(
                    value = text,
                    onValueChange = { text = it },
                    symbol = symbol,
                    label = S("monatslimit", "Monatslimit"),
                    error = error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = Money.parseToCents(text)
                if (cents == null || cents <= 0L) error = str("bitte_einen_betrag_eingeben", "Bitte einen Betrag eingeben.")
                else onSave(category.id, cents)
            }) { Text(S("speichern", "Speichern")) }
        },
        dismissButton = {
            Row {
                if (existingId != null) {
                    TextButton(onClick = { onDelete(category.id) }) {
                        Text(S("entfernen", "Entfernen"), color = appColors().expense)
                    }
                }
                TextButton(onClick = onDismiss) { Text(S("abbrechen_4", "Abbrechen")) }
            }
        }
    )
}

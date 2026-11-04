package com.finovo.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.finovo.app.AppViewModel
import com.finovo.app.data.CategoryTotal
import com.finovo.app.data.CategoryType
import com.finovo.app.data.DateFilter
import com.finovo.app.util.DateRange
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.ExpenseRow
import com.finovo.app.ui.components.HorizontalBar
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.Stats
import com.finovo.app.util.S
import com.finovo.app.util.str
import androidx.compose.ui.text.style.TextOverflow
import com.finovo.app.util.categoryLabel

private enum class ExpenseView(val label: String) {
    ALLE("Alle"), KATEGORIEN("Kategorien"), MONAT("Monat"), JAHR("Jahr");
    val key: String get() = when (this) {
        ALLE -> "seg_all"
        KATEGORIEN -> "seg_categories"
        MONAT -> "seg_month"
        JAHR -> "seg_year"
    }
}

@Composable
fun ExpensesScreen(viewModel: AppViewModel, onBack: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val categories by viewModel.categories.collectAsState()
    val allExpenses by viewModel.expenses.collectAsState()
    val list by viewModel.filteredExpenses.collectAsState()
    val total by viewModel.filteredExpenseSum.collectAsState()
    val query by viewModel.expenseQuery.collectAsState()
    val selectedCategory by viewModel.expenseCategory.collectAsState()

    var pendingDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(ExpenseView.MONAT) }

    // Summe des Vormonats für den Vergleich
    val previousMonthCents = remember(allExpenses) {
        val now = Dates.today()
        val prev = java.time.YearMonth.from(now).minusMonths(1)
        val (f, t) = Dates.monthRange(prev.atDay(1))
        allExpenses.filter { it.date in f..t }.sumOf { it.amountCents }
    }
    val currentMonthCents = remember(allExpenses) {
        val (f, t) = Dates.monthRange(Dates.today())
        allExpenses.filter { it.date in f..t }.sumOf { it.amountCents }
    }
    val deltaPercent = if (previousMonthCents > 0L) {
        ((currentMonthCents - previousMonthCents).toFloat() / previousMonthCents.toFloat() * 100f)
    } else 0f

    // Kategorien-Auswertung für den gewählten Zeitraum
    val range: DateRange = when (view) {
        ExpenseView.MONAT -> {
            val (f, t) = Dates.monthRange(Dates.today())
            DateRange(f, t)
        }
        ExpenseView.JAHR -> {
            val y = Dates.today().year
            DateRange(
                java.time.LocalDate.of(y, 1, 1).toEpochDay(),
                java.time.LocalDate.of(y, 12, 31).toEpochDay()
            )
        }
        else -> DateRange.all()
    }
    val totals: List<CategoryTotal> = remember(allExpenses, categories, range, selectedCategory) {
        Stats.categoryTotals(
            expenses = if (selectedCategory == null) allExpenses
            else allExpenses.filter { it.categoryId == selectedCategory },
            categories = categories,
            range = range
        )
    }
    val visibleTotals = totals.filter { it.cents > 0L }

    val grouped = remember(list) { list.groupBy { it.date } }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            tutorialId = "expenses",
            title = S("expenses_title", "Ausgaben"),
            onBack = onBack,
            actions = {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        if (showSearch) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = S("suchen", "Suchen")
                    )
                }
            }
        )

        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.expenseQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                placeholder = { Text(S("search_expenses", "Suchen (Notiz, Kategorie, Betrag)")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.expenseQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = S("suche_loeschen", "Suche löschen"))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        // --------------------- Filter-Tabs ---------------------
        SegmentedControl(
            options = ExpenseView.values().map { S(it.key, it.label) },
            selectedIndex = view.ordinal,
            onSelect = { index ->
                view = ExpenseView.values()[index]
                viewModel.expenseFilter.value = when (view) {
                    ExpenseView.ALLE -> DateFilter.ALL
                    ExpenseView.MONAT -> DateFilter.MONTH
                    ExpenseView.JAHR -> DateFilter.YEAR
                    ExpenseView.KATEGORIEN -> DateFilter.MONTH
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp, top = 4.dp)
        ) {
            // --------------------- Summenkarte ---------------------
            item {
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            S("total_expenses", "Gesamtausgaben"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        AmountText(
                            cents = total,
                            symbol = symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val lower = deltaPercent <= 0f
                            Icon(
                                if (lower) Icons.AutoMirrored.Filled.TrendingDown
                                else Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = if (lower) appColors().income else appColors().expense,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${if (deltaPercent > 0f) "+" else ""}${deltaPercent.toInt()} %",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (lower) appColors().income else appColors().expense
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                S("vs_previous_month", "im Vergleich zum Vormonat"),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                    }
                }
            }

            // --------------------- Kategorien / Donut ---------------------
            if (view == ExpenseView.KATEGORIEN || view == ExpenseView.MONAT || view == ExpenseView.JAHR) {
                item {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                S("categories", "Kategorien"),
                                style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when (view) {
                                    ExpenseView.MONAT -> Dates.monthYear(Dates.today())
                                    ExpenseView.JAHR -> Dates.today().year.toString()
                                    else -> S("whole_period", "Gesamter Zeitraum")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            com.finovo.app.ui.components.DonutChart(
                                totals = visibleTotals,
                                centerLabel = S("total", "Gesamt"),
                                centerValue = Money.format(total, symbol)
                            )
                            if (visibleTotals.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                visibleTotals.take(6).forEach { item ->
                                    CategoryShareRow(
                                        emoji = item.emoji,
                                        name = categoryLabel(item.name),
                                        amount = Money.format(item.cents, symbol),
                                        percent = item.percent,
                                        color = appColors().expense
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
            }

            // --------------------- Transaktionen ---------------------
            item {
                Text(
                    S("transactions", "Transaktionen"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
            }

            if (list.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "💸",
                        title = S("no_expenses", "Noch keine Ausgaben vorhanden."),
                        message = S("trage_deine_erste_ausgabe_ein_es_d", "Trage deine erste Ausgabe ein – es dauert nur wenige Sekunden."),
                        actionLabel = S("add_expense", "+ Ausgabe hinzufügen"),
                        onAction = { viewModel.openExpenseSheet(null) }
                    )
                }
            } else {
                grouped.forEach { (day, items) ->
                    val dayTotal = items.sumOf { it.amountCents }
                    item(key = "h$day") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Dates.long(Dates.fromEpochDay(day)),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                            AmountText(
                                cents = dayTotal,
                                symbol = symbol,
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                    }
                    items(items.count(), key = { "e${items[it].id}" }) { index ->
                        val expense = items[index]
                        ExpenseRow(
                            expense = expense,
                            category = categories.firstOrNull { it.id == expense.categoryId },
                            symbol = symbol,
                            onEdit = { viewModel.openExpenseSheet(expense) },
                            onDelete = { pendingDelete = expense },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = S("ausgabe_loeschen", "Ausgabe löschen?"),
        message = pendingDelete?.let {
            "„${Money.format(it.amountCents, symbol)}“ vom ${Dates.full(
                Dates.fromEpochDay(it.date)
            )} wirklich löschen? Dieser Schritt kann nicht rückgängig gemacht werden."
        } ?: "",
        confirmLabel = S("loeschen_9", "Löschen"),
        onConfirm = {
            pendingDelete?.let { viewModel.deleteExpense(it) }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null }
    )
}

@Composable
internal fun CategoryShareRow(
    emoji: String,
    name: String,
    amount: String,
    percent: Float,
    color: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(8.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            Text(
                amount,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Text(
                S("percent_100_toint_2", "%1\$d %%", (percent * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                modifier = Modifier.width(38.dp),
                textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        HorizontalBar(percent = percent, color = color)
    }
}

@Composable
internal fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors().cardAlt)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                    else appColors().textMuted,
                    maxLines = 1
                )
            }
        }
    }
}

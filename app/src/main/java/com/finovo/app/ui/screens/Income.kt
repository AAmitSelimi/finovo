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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.data.CategoryTotal
import com.finovo.app.data.DateFilter
import com.finovo.app.util.DateRange
import com.finovo.app.data.IncomeEntity
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.HorizontalBar
import com.finovo.app.ui.components.IncomeRow
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.Stats
import com.finovo.app.util.S
import com.finovo.app.util.str
import androidx.compose.ui.text.style.TextOverflow
import com.finovo.app.util.categoryLabel

private enum class IncomeView(val label: String) {
    ALLE("Alle"), MONAT("Monat"), JAHR("Jahr");
    val key: String get() = when (this) {
        ALLE -> "seg_all"
        MONAT -> "seg_month"
        JAHR -> "seg_year"
    }
}

@Composable
fun IncomeScreen(viewModel: AppViewModel, onBack: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val categories by viewModel.categories.collectAsState()
    val allIncomes by viewModel.incomes.collectAsState()
    val list by viewModel.filteredIncomes.collectAsState()
    val query by viewModel.incomeQuery.collectAsState()

    var pendingDelete by remember { mutableStateOf<IncomeEntity?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf(IncomeView.MONAT) }

    val total = remember(list) { list.sumOf { it.amountCents } }

    val previousMonthCents = remember(allIncomes) {
        val prev = java.time.YearMonth.from(Dates.today()).minusMonths(1)
        val (f, t) = Dates.monthRange(prev.atDay(1))
        allIncomes.filter { it.date in f..t }.sumOf { it.amountCents }
    }
    val currentMonthCents = remember(allIncomes) {
        val (f, t) = Dates.monthRange(Dates.today())
        allIncomes.filter { it.date in f..t }.sumOf { it.amountCents }
    }
    val deltaPercent = if (previousMonthCents > 0L) {
        ((currentMonthCents - previousMonthCents).toFloat() / previousMonthCents.toFloat() * 100f)
    } else 0f

    val range: DateRange = when (view) {
        IncomeView.MONAT -> {
            val (f, t) = Dates.monthRange(Dates.today())
            DateRange(f, t)
        }
        IncomeView.JAHR -> {
            val y = Dates.today().year
            DateRange(
                java.time.LocalDate.of(y, 1, 1).toEpochDay(),
                java.time.LocalDate.of(y, 12, 31).toEpochDay()
            )
        }
        else -> DateRange.all()
    }
    val sources: List<CategoryTotal> = remember(allIncomes, categories, range) {
        val byId = categories.associateBy { it.id }
        val totals = LinkedHashMap<Long, Long>()
        for (i in allIncomes) {
            if (i.date !in range.from..range.to) continue
            totals[i.categoryId] = (totals[i.categoryId] ?: 0L) + i.amountCents
        }
        val sum = totals.values.sum()
        totals.entries.map { (id, cents) ->
            val c = byId[id]
            CategoryTotal(
                categoryId = id,
                name = categoryLabel(c?.name ?: "Sonstiges"),
                emoji = c?.emoji ?: "💰",
                cents = cents,
                percent = if (sum == 0L) 0f else cents.toFloat() / sum.toFloat()
            )
        }.sortedByDescending { it.cents }
    }
    val visibleSources = sources.filter { it.cents > 0L }
    val grouped = remember(list) { list.groupBy { it.date } }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("income_title", "Einnahmen"),
            onBack = onBack,
            actions = {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        if (showSearch) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = S("suchen_2", "Suchen")
                    )
                }
            }
        )

        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.incomeQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                placeholder = { Text(S("search_income", "Einnahmen durchsuchen")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.incomeQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = S("suche_loeschen_2", "Suche löschen"))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }

        SegmentedControl(
            options = IncomeView.values().map { S(it.key, it.label) },
            selectedIndex = view.ordinal,
            onSelect = { index ->
                view = IncomeView.values()[index]
                viewModel.incomeFilter.value = when (view) {
                    IncomeView.ALLE -> DateFilter.ALL
                    IncomeView.MONAT -> DateFilter.MONTH
                    IncomeView.JAHR -> DateFilter.YEAR
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp, top = 4.dp)
        ) {
            item {
                AppCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(
                            S("total_income", "Gesamteinnahmen"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        AmountText(
                            cents = total,
                            symbol = symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = appColors().income
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val higher = deltaPercent >= 0f
                            Icon(
                                if (higher) Icons.AutoMirrored.Filled.TrendingUp
                                else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = if (higher) appColors().income else appColors().expense,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${if (deltaPercent > 0f) "+" else ""}${deltaPercent.toInt()} %",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (higher) appColors().income else appColors().expense
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

            if (visibleSources.isNotEmpty()) {
                item {
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                S("income_sources", "Einnahmequellen"),
                                style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when (view) {
                                    IncomeView.MONAT -> Dates.monthYear(Dates.today())
                                    IncomeView.JAHR -> Dates.today().year.toString()
                                    else -> S("whole_period", "Gesamter Zeitraum")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                            Spacer(Modifier.height(12.dp))
                            visibleSources.take(8).forEach { item ->
                                CategoryShareRow(
                                    emoji = item.emoji,
                                    name = item.name,
                                    amount = Money.format(item.cents, symbol),
                                    percent = item.percent,
                                    color = appColors().income
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

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
                        emoji = "💰",
                        title = S("no_incomes", "Noch keine Einnahmen vorhanden."),
                        message = S("trage_deine_erste_einnahme_ein_es_", "Trage deine erste Einnahme ein – es dauert nur wenige Sekunden."),
                        actionLabel = S("add_income", "+ Einnahme hinzufügen"),
                        onAction = { viewModel.openIncomeSheet(null) }
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
                            Text(
                                S("money_format_daytotal_symbol", "+ %1\$s", Money.format(dayTotal, symbol)),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().income,
                                textAlign = TextAlign.End,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    items(items.count(), key = { "i${items[it].id}" }) { index ->
                        val income = items[index]
                        IncomeRow(
                            income = income,
                            category = categories.firstOrNull { it.id == income.categoryId },
                            symbol = symbol,
                            onEdit = { viewModel.openIncomeSheet(income) },
                            onDelete = { pendingDelete = income },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = S("einnahme_loeschen", "Einnahme löschen?"),
        message = pendingDelete?.let {
            "„${Money.format(it.amountCents, symbol)}“ vom ${Dates.full(
                Dates.fromEpochDay(it.date)
            )} wirklich löschen? Dieser Schritt kann nicht rückgängig gemacht werden."
        } ?: "",
        confirmLabel = S("loeschen_11", "Löschen"),
        onConfirm = {
            pendingDelete?.let { viewModel.deleteIncome(it) }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null }
    )
}

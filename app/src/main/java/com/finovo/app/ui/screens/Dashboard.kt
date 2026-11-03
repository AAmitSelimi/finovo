package com.finovo.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.AutoSizeAmountText
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.HorizontalBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.abs
import com.finovo.app.util.S
import com.finovo.app.util.str
import com.finovo.app.util.categoryLabel

private fun greetingKey(hour: Int): String = when {
    hour < 11 -> "greeting_morning"
    hour < 18 -> "greeting_day"
    else -> "greeting_evening"
}

private fun greetingFor(hour: Int): String = when {
    hour < 5 -> str("greeting_night", "Gute Nacht")
    hour < 11 -> str("greeting_morning", "Guten Morgen")
    hour < 13 -> str("greeting_noon", "Guten Mittag")
    hour < 18 -> str("greeting_day", "Guten Tag")
    hour < 22 -> str("greeting_evening", "Guten Abend")
    else -> str("greeting_night", "Gute Nacht")
}

private enum class DashboardView(val label: String) {
    CATEGORIES("Kategorien"),
    MONTH("Monat"),
    YEAR("Jahr");

    val key: String
        get() = when (this) {
            CATEGORIES -> "seg_categories"
            MONTH -> "seg_month"
            YEAR -> "seg_year"
        }
}

@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onSeeAllBills: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenBudget: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val dashboard by viewModel.dashboard.collectAsState()
    val monthOffset by viewModel.monthOffset.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val budget by viewModel.budget.collectAsState()

    var view by remember { mutableStateOf(DashboardView.CATEGORIES) }

    val month: YearMonth = YearMonth.from(Dates.today()).plusMonths(monthOffset.toLong())
    val prevMonth: YearMonth = month.minusMonths(1)
    val (from, to) = Dates.monthRange(month.atDay(1))
    val (prevFrom, prevTo) = Dates.monthRange(prevMonth.atDay(1))

    val incomeCents = dashboard.incomeCents
    val expenseCents = dashboard.expenseCents
    val availableCents = dashboard.availableCents

    val prevIncome = incomes.filter { it.date in prevFrom..prevTo }.sumOf { it.amountCents }
    val prevExpense = expenses.filter { it.date in prevFrom..prevTo }.sumOf { it.amountCents }

    val incomeDelta = percentDelta(incomeCents, prevIncome)
    val expenseDelta = percentDelta(expenseCents, prevExpense)

    val expenseCats = categories.filter { it.type == CategoryType.EXPENSE.name }
    val incomeCats = categories.filter { it.type == CategoryType.INCOME.name }

    val categoryTotals: List<Pair<CategoryEntity, Long>> = expenseCats
        .map { cat -> cat to expenses.filter { it.date in from..to && it.categoryId == cat.id }.sumOf { it.amountCents } }
        .filter { it.second > 0L }
        .sortedByDescending { it.second }

    val sourceTotals: List<Pair<CategoryEntity, Long>> = incomeCats
        .map { cat -> cat to incomes.filter { it.date in from..to && it.categoryId == cat.id }.sumOf { it.amountCents } }
        .filter { it.second > 0L }
        .sortedByDescending { it.second }

    val savingsRate: Float = if (incomeCents > 0L) (availableCents.toFloat() / incomeCents.toFloat()) else 0f

    // Monats-/Jahresverlauf für die Umschaltung
    val monthBars: List<Triple<String, Long, Long>> = (5 downTo 0).map { back ->
        val m = month.minusMonths(back.toLong())
        val (f, t) = Dates.monthRange(m.atDay(1))
        Triple(
            Dates.chartMonth(m.atDay(1)),
            incomes.filter { it.date in f..t }.sumOf { it.amountCents },
            expenses.filter { it.date in f..t }.sumOf { it.amountCents }
        )
    }
    val yearBars: List<Triple<String, Long, Long>> = (2 downTo 0).map { back ->
        val y = month.year - back
        val f = java.time.LocalDate.of(y, 1, 1).toEpochDay()
        val t = java.time.LocalDate.of(y, 12, 31).toEpochDay()
        Triple(
            y.toString(),
            incomes.filter { it.date in f..t }.sumOf { it.amountCents },
            expenses.filter { it.date in f..t }.sumOf { it.amountCents }
        )
    }

    val widgets by viewModel.dashboardWidgets.collectAsState()
    val abos by viewModel.subscriptions.collectAsState()
    val ziele by viewModel.goals.collectAsState()
    val einblicke by viewModel.einblicke.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 110.dp)
    ) {
        // ------------------------------ Kopf --------------------------------
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    com.finovo.app.ui.components.FinovoWordmark(fontSize = 17.sp)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.navigate(com.finovo.app.ui.Route.WidgetsEdit) }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = S("widgets_title", "Übersicht anpassen"),
                            tint = appColors().textMuted
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    S(greetingKey(LocalTime.now().hour), greetingFor(LocalTime.now().hour)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = appColors().textMuted
                )
                Text(
                    S("dashboard_title", "Deine Finanzen"),
                    style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
            }
        }

        // -------------------------- Monat wählen -----------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.monthOffset.value = monthOffset - 1 }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = S("zurueck", "Zurück"))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        Dates.monthYear(month.atDay(1)),
                        style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        S("bookings", "${dashboard.recent.size} Buchungen", dashboard.recent.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                }
                IconButton(onClick = { viewModel.monthOffset.value = monthOffset + 1 }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = S("weiter", "Weiter"))
                }
            }
        }

        // ------------------------ Segmentierte Steuerung ---------------------
        item {
            SegmentedControl(
                options = DashboardView.values().toList(),
                selected = view,
                onSelect = { view = it },
                label = { str(it.key, it.label) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // --------------------------- Verfügbar (Hauptkarte) --------------------
        if (com.finovo.app.util.Widgets.Art.BALANCE in widgets) item {
            AvailableCard(
                availableCents = availableCents,
                incomeCents = incomeCents,
                symbol = symbol
            )
        }

        // --------------------------- drei kompakte Karten ----------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (com.finovo.app.util.Widgets.Art.INCOME in widgets) CompactStatCard(
                    label = S("income", "Einnahmen"),
                    cents = incomeCents,
                    symbol = symbol,
                    color = appColors().income,
                    deltaPercent = incomeDelta,
                    positiveIsGood = true,
                    modifier = Modifier.weight(1f)
                )
                if (com.finovo.app.util.Widgets.Art.EXPENSES in widgets) CompactStatCard(
                    label = S("expenses", "Ausgaben"),
                    cents = expenseCents,
                    symbol = symbol,
                    color = appColors().expense,
                    deltaPercent = expenseDelta,
                    positiveIsGood = false,
                    modifier = Modifier.weight(1f)
                )
                if (com.finovo.app.util.Widgets.Art.SAVINGS in widgets) CompactStatCard(
                    label = S("remaining", "Übrig"),
                    cents = availableCents,
                    symbol = symbol,
                    color = MaterialTheme.colorScheme.primary,
                    deltaPercent = null,
                    positiveIsGood = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // -------------------------- Ansicht: Kategorien ------------------------
        if (com.finovo.app.util.Widgets.Art.CHART in widgets) when (view) {
            DashboardView.CATEGORIES -> {
                if (categoryTotals.isNotEmpty()) {
                    item { SectionHeader(title = S("categories", "Kategorien")) }
                    items(categoryTotals.take(6)) { (cat, cents) ->
                        ShareRow(
                            emoji = cat.emoji,
                            name = categoryLabel(cat.name),
                            cents = cents,
                            total = expenseCents,
                            symbol = symbol,
                            color = appColors().expense
                        )
                    }
                }
                if (sourceTotals.isNotEmpty()) {
                    item { SectionHeader(title = S("income_sources", "Einnahmequellen")) }
                    items(sourceTotals.take(6)) { (cat, cents) ->
                        ShareRow(
                            emoji = cat.emoji,
                            name = categoryLabel(cat.name),
                            cents = cents,
                            total = incomeCents,
                            symbol = symbol,
                            color = appColors().income
                        )
                    }
                }
            }
            DashboardView.MONTH -> {
                item { SectionHeader(title = S("last_6_months", "Letzte 6 Monate")) }
                item {
                    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            monthBars.forEach { (label, inc, exp) ->
                                BarRow(
                                    label = label,
                                    incomeCents = inc,
                                    expenseCents = exp,
                                    symbol = symbol,
                                    max = monthBars.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1L)
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
            DashboardView.YEAR -> {
                item { SectionHeader(title = S("last_3_years", "Letzte 3 Jahre")) }
                item {
                    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            yearBars.forEach { (label, inc, exp) ->
                                BarRow(
                                    label = label,
                                    incomeCents = inc,
                                    expenseCents = exp,
                                    symbol = symbol,
                                    max = yearBars.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1L)
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------- Budget --------------------------------
        if (com.finovo.app.util.Widgets.Art.BUDGET in widgets)
            item { SectionHeader(title = S("budget", "Budget"), actionLabel = S("adjust", "Anpassen"), onAction = onOpenBudget) }
        if (com.finovo.app.util.Widgets.Art.BUDGET in widgets)
        item {
            val limit = budget.monthlyLimitCents
            val spent = dashboard.budgetSpentCents
            val percent = if (limit > 0L) (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1.5f) else 0f
            val color = when {
                limit <= 0L -> MaterialTheme.colorScheme.primary
                percent >= 1f -> appColors().expense
                percent >= 0.9f -> appColors().warning
                else -> appColors().income
            }
            AppCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = onOpenBudget
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (limit > 0L) {
                        Text(
                            S("money_format_spent_symbol_von_money_fo", "%1\$s von %2\$s ausgegeben", Money.format(spent, symbol), Money.format(limit, symbol)),
                            style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        HorizontalBar(percent = percent, color = color)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                S("percent_100_toint_genutzt", "%1\$d %% genutzt", (percent * 100).toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = color
                            )
                            Text(
                                if (dashboard.budgetRemainingCents >= 0) {
                                    S("noch", "Noch %1\$s", Money.format(dashboard.budgetRemainingCents, symbol))
                                } else {
                                    str("budget_over_limit", "%1\$s über dem Limit", Money.format(-dashboard.budgetRemainingCents, symbol))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                    } else {
                        Text(
                            S("noch_kein_budget_festgelegt", "Noch kein Budget festgelegt"),
                            style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            S("tippe_hier_um_ein_monatliches_limit_zu", "Tippe hier, um ein monatliches Limit zu setzen."),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }
        }

        // ---------------------- Bevorstehende Zahlungen -------------------------
        if (com.finovo.app.util.Widgets.Art.BILLS in widgets)
        item {
            SectionHeader(
                title = S("upcoming_payments", "Bevorstehende Zahlungen"),
                actionLabel = S("see_all", "Alle"),
                onAction = onSeeAllBills
            )
        }
        if (dashboard.upcomingBills.isEmpty()) {
            item {
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Text(
                        S("keine_offenen_rechnungen", "Keine offenen Rechnungen"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors().textMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(dashboard.upcomingBills.take(5)) { bill ->
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(appColors().cardAlt),
                            contentAlignment = Alignment.Center
                        ) { Text(S("ud83e_uddfe", "\uD83E\uDDFE"), fontSize = 18.sp) }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bill.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                Dates.full(Dates.fromEpochDay(bill.dueDate)),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        AutoSizeAmountText(
                            cents = -bill.amountCents,
                            symbol = symbol,
                            maxFontSize = 15.sp,
                            color = appColors().expense,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ---------------------------- Finanzanalyse -----------------------------
        item {
            Spacer(Modifier.height(10.dp))
            AppCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = onOpenAnalytics
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text(S("ud83d_udcca", "\uD83D\uDCCA"), fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S("analytics", "Finanzanalyse"), style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                        Text(
                            S("analytics_sub", "Einnahmen, Ausgaben, Trends und mehr"),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = appColors().textMuted)
                }
            }
        }

        // ---------------------- Abos, Sparziele & Einblicke -----------------------
        if (com.finovo.app.util.Widgets.Art.SUBSCRIPTIONS in widgets && abos.isNotEmpty()) {
            item { SectionHeader(title = S("subscriptions", "Abos")) }
            items(abos.filter { it.active }.take(3)) { a ->
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(a.emoji, modifier = Modifier.padding(end = 10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.Medium)
                            Text(
                                S("next_payment_on", "Nächste Zahlung am %1\$s", Dates.full(Dates.fromEpochDay(a.nextPayment))),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                        AmountText(a.amountCents, symbol)
                    }
                }
            }
        }

        if (com.finovo.app.util.Widgets.Art.GOALS in widgets && ziele.isNotEmpty()) {
            item { SectionHeader(title = S("goals", "Sparziele")) }
            items(ziele.filter { !it.archived }.take(3)) { z ->
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(z.emoji, modifier = Modifier.padding(end = 10.dp))
                            Text(z.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(
                                Money.format(z.savedCents, symbol) + " / " + Money.format(z.targetCents, symbol),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        com.finovo.app.ui.components.ProgressBar(
                            progress = if (z.targetCents > 0)
                                (z.savedCents.toFloat() / z.targetCents).coerceIn(0f, 1f) else 0f,
                            color = appColors().income,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (com.finovo.app.util.Widgets.Art.INSIGHTS in widgets && einblicke.isNotEmpty()) {
            item { SectionHeader(title = S("insights", "Analyse")) }
            items(einblicke.take(2)) { e ->
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Row(modifier = Modifier.padding(14.dp)) {
                        Text(e.emoji, modifier = Modifier.padding(end = 10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(e.titel, fontWeight = FontWeight.Medium)
                            Text(
                                e.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                    }
                }
            }
        }

        // --------------------------- Transaktionen -----------------------------
        item {
            SectionHeader(
                title = S("transactions", "Transaktionen"),
                actionLabel = S("alle", "Alle"),
                onAction = onSeeAllTransactions
            )
        }
        if (dashboard.recent.isEmpty()) {
            item {
                EmptyState(
                    emoji = "\uD83D\uDCB8",
                    title = S("noch_keine_buchungen", "Noch keine Buchungen"),
                    message = S("erfasse_deine_erste_einnahme_oder_", "Erfasse deine erste Einnahme oder Ausgabe – sie erscheint dann hier.")
                )
            }
        } else {
            items(dashboard.recent.take(6)) { tx ->
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(appColors().cardAlt),
                            contentAlignment = Alignment.Center
                        ) { Text(tx.emoji, fontSize = 18.sp) }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                tx.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                Dates.full(tx.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        AutoSizeAmountText(
                            cents = if (tx.isIncome) tx.amountCents else -tx.amountCents,
                            symbol = symbol,
                            maxFontSize = 15.sp,
                            color = if (tx.isIncome) appColors().income else appColors().expense,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bausteine
// ---------------------------------------------------------------------------

private fun percentDelta(current: Long, previous: Long): Int? {
    if (previous <= 0L) return null
    return (((current - previous) * 100L) / previous).toInt()
}

@Composable
private fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors().cardAlt)
            .padding(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else appColors().textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    cents: Long,
    symbol: String,
    deltaPercent: Int?,
    color: Color,
    positiveIsGood: Boolean,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            AutoSizeAmountText(
                cents = cents,
                symbol = symbol,
                maxFontSize = 20.sp,
                minFontSize = 13.sp,
                color = color,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            if (deltaPercent == null) {
                Text(
                    S("kein_vergleichswert", "kein Vergleichswert"),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                val up = deltaPercent > 0
                val good = if (positiveIsGood) up else !up
                val deltaColor = when {
                    deltaPercent == 0 -> appColors().textMuted
                    good -> appColors().income
                    else -> appColors().expense
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (up) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = deltaColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${if (up) "+" else ""}$deltaPercent %",
                        style = MaterialTheme.typography.bodySmall,
                        color = deltaColor
                    )
                }
            }
        }
    }
}

@Composable
private fun TripleColumn(label: String, cents: Long, symbol: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(2.dp))
        Text(
            Money.format(cents, symbol),
            style = MaterialTheme.typography.titleSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShareRow(
    emoji: String,
    name: String,
    cents: Long,
    total: Long,
    symbol: String,
    color: Color
) {
    val percent = if (total > 0L) cents.toFloat() / total.toFloat() else 0f
    val animated by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = S("share", "share")
    )
    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 17.sp)
                Spacer(Modifier.width(9.dp))
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Money.format(cents, symbol),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    S("percent_100_toint", "%1\$d %%", (percent * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    modifier = Modifier.width(38.dp),
                    textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalBar(percent = animated, color = color)
        }
    }
}

@Composable
private fun BarRow(
    label: String,
    incomeCents: Long,
    expenseCents: Long,
    symbol: String,
    max: Long
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
            Text(
                S("money_format_incomecents_symbol_money", "%1\$s · %2\$s", Money.format(incomeCents, symbol), Money.format(expenseCents, symbol)),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(if (incomeCents > 0) incomeCents.toFloat() / max.toFloat() else 0.001f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(appColors().income)
            )
            Box(modifier = Modifier.width(0.dp))
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(if (expenseCents > 0) expenseCents.toFloat() / max.toFloat() else 0.001f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(appColors().expense)
            )
            Box(modifier = Modifier.width(0.dp))
        }
    }
}

@Composable
private fun SavingsRing(percent: Float) {
    val animated by animateFloatAsState(
        targetValue = percent.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = S("ring", "ring")
    )
    val trackColor = appColors().divider
    val ringColor = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val stroke = 9.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = Offset(stroke / 2, stroke / 2)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                size = Size(size.width - stroke, size.height - stroke),
                topLeft = Offset(stroke / 2, stroke / 2)
            )
        }
        Text(
            S("animated_100_toint", "%1\$d %%", (animated * 100).toInt()),
            style = MaterialTheme.typography.bodySmall
        )
    }
}


@Composable
private fun AvailableCard(availableCents: Long, incomeCents: Long, symbol: String) {
    val percent =
        if (incomeCents > 0L) (availableCents.toFloat() / incomeCents.toFloat()).coerceIn(0f, 1f)
        else 0f
    val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(
            androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primary, androidx.compose.ui.graphics.Color(0xFF0B1B4D), 0.25f),
            androidx.compose.ui.graphics.Color(0xFF0B1B4D)
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(gradient)
            .drawBehind {
                drawCircle(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f),
                    radius = size.width * 0.45f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, -size.height * 0.1f)
                )
                drawCircle(
                    androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                    radius = size.width * 0.3f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.05f, size.height * 1.05f)
                )
            }
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    S("available", "Verfügbar").uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 1.4.sp,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Savings,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            com.finovo.app.ui.components.AmountText(
                cents = availableCents,
                symbol = symbol,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 44.sp),
                color = androidx.compose.ui.graphics.Color.White
            )
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percent.coerceAtLeast(0.02f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                S("percent_available", "${(percent * 100).toInt()} % der Einnahmen verfügbar", (percent * 100).toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun CompactStatCard(
    label: String,
    cents: Long,
    symbol: String,
    color: androidx.compose.ui.graphics.Color,
    deltaPercent: Int?,
    positiveIsGood: Boolean,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text(
                com.finovo.app.util.Money.format(cents, symbol),
                style = MaterialTheme.typography.titleSmall,
                color = color,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (deltaPercent != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${if (deltaPercent > 0) "+" else ""}$deltaPercent %",
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((deltaPercent >= 0) == positiveIsGood) appColors().income
                    else appColors().expense
                )
            }
        }
    }
}
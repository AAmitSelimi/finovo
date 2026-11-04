package com.finovo.app.ui.screens

import com.finovo.app.ui.components.premiumSurface
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.finovo.app.AppViewModel
import com.finovo.app.ui.components.AnalyticsDonut
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.BarIncomeExpenseChart
import com.finovo.app.ui.components.BucketDetail
import com.finovo.app.ui.components.CategoryRankingBars
import com.finovo.app.ui.components.CategoryBadge
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.HorizontalBar
import com.finovo.app.ui.components.IconActionButton
import com.finovo.app.ui.components.NetBarsChart
import com.finovo.app.ui.components.Pill
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.components.TrendLineChart
import com.finovo.app.ui.components.granularityLabelGerman
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Analytics
import com.finovo.app.util.CrashGuard
import com.finovo.app.util.AnalyticsFilter
import com.finovo.app.util.AnalyticsPeriod
import com.finovo.app.util.Bucket
import com.finovo.app.util.CategoryStat
import com.finovo.app.util.ChartType
import com.finovo.app.util.DateRange
import com.finovo.app.util.Dates
import com.finovo.app.util.FixedFilter
import com.finovo.app.util.Granularity
import com.finovo.app.util.Insight
import com.finovo.app.util.Money
import com.finovo.app.util.PaidFilter
import com.finovo.app.util.RecurrenceFilter
import com.finovo.app.util.TxItem
import com.finovo.app.util.TypeFilter
import java.io.File
import java.time.LocalDate
import java.util.Locale
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel
import com.finovo.app.util.str
import com.finovo.app.util.AppLocale

private enum class AnalyticsTab(val labelKey: String, val labelDe: String) {
    OVERVIEW("tab_overview", "Übersicht"),
    CATEGORIES("tab_categories", "Kategorien"),
    TRENDS("tab_trends", "Trends"),
    MONTHS("tab_months", "Monate"),
    INCOME("tab_income", "Einnahmen"),
    EXPENSES("tab_expenses", "Ausgaben");

    fun label(): String = str(labelKey, labelDe)
}

private data class DrillTarget(
    val categoryId: Long,
    val title: String,
    val emoji: String,
    val range: DateRange?
)

@Composable
fun AnalyticsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val crashCount by CrashGuard.crashCount.collectAsState()
    var ignoreCrash by remember { mutableStateOf(false) }
    if (crashCount > 0 && !ignoreCrash) {
        AnalysisCrashScreen(
            onRetry = {
                CrashGuard.clearLog()
                ignoreCrash = true
            },
            onBack = onBack
        )
    } else {
        AnalyticsContent(viewModel = viewModel, onBack = onBack)
    }
}

@Composable
private fun AnalyticsContent(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val result by viewModel.analytics.collectAsState()
    val period by viewModel.analyticsPeriod.collectAsState()
    val custom by viewModel.analyticsCustom.collectAsState()
    val granularity by viewModel.analyticsGranularity.collectAsState()
    val filter by viewModel.analyticsFilter.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var tab by remember { mutableStateOf(AnalyticsTab.OVERVIEW) }
    var overviewChart by remember { mutableStateOf(ChartType.BAR) }
    var expenseChart by remember { mutableStateOf(ChartType.PIE) }
    var incomeChart by remember { mutableStateOf(ChartType.PIE) }
    var trendChart by remember { mutableStateOf(ChartType.TREND) }
    var showFilter by remember { mutableStateOf(false) }
    var drill by remember { mutableStateOf<DrillTarget?>(null) }
    var selectedBucket by remember { mutableStateOf<Bucket?>(null) }
    var pendingDelete by remember { mutableStateOf<TxItem?>(null) }

    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.exportAnalyticsCsv(uri) }
    val createPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> if (uri != null) viewModel.exportAnalyticsPdf(uri) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            tutorialId = "analytics",
            title = S("analysis_title", "Finanzanalyse"),
            subtitle = result.label,
            onBack = onBack,
            actions = {
                IconActionButton(
                    icon = Icons.Default.FilterList,
                    description = S("filter_2", "Filter"),
                    onClick = { showFilter = true },
                    tint = if (filter.isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )

        // ---------------------- period selector ----------------------
        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsPeriod.values().forEach { option ->
                FilterChip(
                    selected = period == option,
                    onClick = {
                        viewModel.analyticsPeriod.value = option
                        if (option == AnalyticsPeriod.CUSTOM && viewModel.analyticsCustom.value == null) {
                            viewModel.analyticsCustom.value =
                                Dates.startOfMonth(LocalDate.now()) to LocalDate.now()
                        }
                        selectedBucket = null
                    },
                    label = { Text(option.label(), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        if (period == AnalyticsPeriod.CUSTOM) {
            val range = custom ?: (Dates.startOfMonth(LocalDate.now()) to LocalDate.now())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DateField(
                    epochDay = range.first.toEpochDay(),
                    onDateChange = { viewModel.analyticsCustom.value = it to range.second },
                    label = S("von_2", "Von"),
                    modifier = Modifier.weight(1f)
                )
                DateField(
                    epochDay = range.second.toEpochDay(),
                    onDateChange = { viewModel.analyticsCustom.value = range.first to it },
                    label = S("bis_2", "Bis"),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (filter.isActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    filter.describe(categories),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.resetAnalyticsFilter() }) { Text(S("zuruecksetzen", "Zurücksetzen")) }
            }
        }

        // ---------------------------- tabs ---------------------------
        ScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            edgePadding = 8.dp,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[tab.ordinal]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            AnalyticsTab.values().forEach { item ->
                Tab(
                    selected = tab == item,
                    onClick = { tab = item },
                    text = {
                        Text(
                            item.label(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        // --------------------------- content -------------------------
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (!result.hasData) {
                item { AnalysisEmptyState() }
            } else when (tab) {
                AnalyticsTab.OVERVIEW -> {
                    item { KpiGrid(result, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item {
                        ChartCard(
                            title = S("income_vs_expenses", "Einnahmen vs. Ausgaben"),
                            chartType = overviewChart,
                            onChartTypeChange = { overviewChart = it },
                            allowed = listOf(ChartType.BAR, ChartType.LINE)
                        ) {
                            when (overviewChart) {
                                ChartType.LINE -> TrendLineChart(
                                    buckets = result.buckets,
                                    onSelect = { selectedBucket = it },
                                    symbol = symbol,
                                    averageCents = result.trend.averageCents
                                )
                                else -> BarIncomeExpenseChart(
                                    buckets = result.buckets,
                                    onSelect = { selectedBucket = it },
                                    symbol = symbol
                                )
                            }
                        }
                    }
                    selectedBucket?.let { chosen ->
                        item {
                            Spacer(Modifier.height(8.dp))
                            BucketDetail(
                                bucket = chosen,
                                symbol = symbol,
                                granularityLabel = granularityLabelGerman(result.granularity)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { BudgetCard(budget.enabled, budget.monthlyLimitCents, result.expenseCents, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { SavingsCard(result, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { InsightsCard(result.insights) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { ComparisonCard(result.comparison, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item {
                        ExportCard(
                            onCsv = { createCsv.launch("Finovo-Analyse.csv") },
                            onPdf = { createPdf.launch("Finovo-Analyse.pdf") },
                            onShareCsv = {
                                viewModel.createAnalyticsCsvFile { shareFile(context, it, "text/csv") }
                            },
                            onSharePdf = {
                                viewModel.createAnalyticsPdfFile { shareFile(context, it, "application/pdf") }
                            },
                            onSummary = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, viewModel.analyticsSummary())
                                }
                                context.startActivity(
                                    Intent.createChooser(intent, str("share_summary", "Zusammenfassung teilen"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                AnalyticsTab.MONTHS -> {
                    item { Spacer(Modifier.height(4.dp)) }
                    item {
                        MonthlyComparisonCard(viewModel = viewModel, symbol = symbol)
                    }
                }
                AnalyticsTab.INCOME -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ResultStat(
                                S("gesamteinnahmen", "Gesamteinnahmen"), result.incomeCents, appColors().income, symbol,
                                Modifier.weight(1f)
                            )
                            ResultStat(
                                str("buchungen", "Buchungen"), result.incomeCategories.sumOf { it.count }.toLong(),
                                MaterialTheme.colorScheme.primary, "", Modifier.weight(1f), isCount = true
                            )
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item {
                        ChartCard(
                            title = S("einnahmen_nach_quelle", "Einnahmen nach Quelle"),
                            chartType = incomeChart,
                            onChartTypeChange = { incomeChart = it },
                            allowed = listOf(ChartType.PIE, ChartType.BAR)
                        ) {
                            when (incomeChart) {
                                ChartType.BAR -> CategoryRankingBars(
                                    stats = result.incomeCategories,
                                    symbol = symbol,
                                    onSelect = {
                                        drill = DrillTarget(it.categoryId, categoryLabel(it.name), it.emoji, null)
                                    }
                                )
                                else -> AnalyticsDonut(
                                    stats = result.incomeCategories,
                                    centerLabel = S("income", "Einnahmen"),
                                    centerValue = Money.format(result.incomeCents, symbol),
                                    onSelect = {
                                        drill = DrillTarget(it.categoryId, categoryLabel(it.name), it.emoji, null)
                                    }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { IncomeSourceList(result.incomeCategories, symbol) { drill = DrillTarget(it.categoryId, categoryLabel(it.name), it.emoji, null) } }
                    item { Spacer(Modifier.height(10.dp)) }
                    item {
                        ChartCard(
                            title = S("verlauf_pro_granularitylabelgerman_res", "Verlauf pro %1\$s", granularityLabelGerman(result.granularity)),
                            chartType = ChartType.TREND,
                            onChartTypeChange = { },
                            allowed = emptyList(),
                            extra = { GranularityChips(result.granularity) { viewModel.analyticsGranularity.value = it } }
                        ) {
                            TrendLineChart(
                                buckets = result.buckets,
                                onSelect = { selectedBucket = it },
                                symbol = symbol,
                                averageCents = if (result.buckets.isEmpty()) 0L
                                else result.buckets.map { it.incomeCents }.sum() / result.buckets.size,
                                useIncome = true
                            )
                        }
                    }
                    result.highestIncome?.let { highest ->
                        item {
                            Spacer(Modifier.height(10.dp))
                            HighlightCard(
                                S("hoechste_einnahme", "Höchste Einnahme"),
                                categories.firstOrNull { it.id == highest.categoryId }?.emoji ?: "💰",
                                categories.firstOrNull { it.id == highest.categoryId }?.name
                                    ?: S("einnahme", "Einnahme"),
                                highest.amountCents,
                                Dates.full(Dates.fromEpochDay(highest.date)),
                                symbol,
                                appColors().income
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                AnalyticsTab.EXPENSES -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ResultStat(
                                S("gesamtausgaben", "Gesamtausgaben"), result.expenseCents, appColors().expense, symbol,
                                Modifier.weight(1f)
                            )
                            ResultStat(
                                str("buchungen", "Buchungen"), result.expenseCategories.sumOf { it.count }.toLong(),
                                MaterialTheme.colorScheme.primary, "", Modifier.weight(1f), isCount = true
                            )
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { FixedVariableCard(result.fixedCents, result.variableCents, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item {
                        ChartCard(
                            title = S("ausgaben_nach_kategorie", "Ausgaben nach Kategorie"),
                            chartType = expenseChart,
                            onChartTypeChange = { expenseChart = it },
                            allowed = listOf(ChartType.PIE, ChartType.BAR)
                        ) {
                            when (expenseChart) {
                                ChartType.BAR -> CategoryRankingBars(
                                    stats = result.expenseCategories,
                                    symbol = symbol,
                                    onSelect = {
                                        drill = DrillTarget(it.categoryId, categoryLabel(it.name), it.emoji, null)
                                    }
                                )
                                else -> AnalyticsDonut(
                                    stats = result.expenseCategories,
                                    centerLabel = S("expenses", "Ausgaben"),
                                    centerValue = Money.format(result.expenseCents, symbol),
                                    onSelect = {
                                        drill = DrillTarget(it.categoryId, categoryLabel(it.name), it.emoji, null)
                                    }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { SpendingPercentCard(result.expenseCategories, symbol) { drill = DrillTarget(it.categoryId, categoryLabel(it.name), it.emoji, null) } }
                    result.highestExpense?.let { highest ->
                        item {
                            Spacer(Modifier.height(10.dp))
                            HighlightCard(
                                S("hoechste_ausgabe", "Höchste Ausgabe"),
                                categories.firstOrNull { it.id == highest.categoryId }?.emoji ?: "📦",
                                categories.firstOrNull { it.id == highest.categoryId }?.name
                                    ?: S("ausgabe", "Ausgabe"),
                                highest.amountCents,
                                Dates.full(Dates.fromEpochDay(highest.date)),
                                symbol,
                                appColors().expense
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                AnalyticsTab.TRENDS -> {
                    item {
                        GranularityChips(result.granularity) { viewModel.analyticsGranularity.value = it }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item {
                        ChartCard(
                            title = S("ausgaben_trend", "Ausgaben-Trend"),
                            chartType = trendChart,
                            onChartTypeChange = { trendChart = it },
                            allowed = listOf(ChartType.TREND, ChartType.BAR, ChartType.LINE)
                        ) {
                            when (trendChart) {
                                ChartType.BAR -> BarIncomeExpenseChart(
                                    buckets = result.buckets,
                                    onSelect = { selectedBucket = it },
                                    symbol = symbol
                                )
                                else -> TrendLineChart(
                                    buckets = result.buckets,
                                    onSelect = { selectedBucket = it },
                                    symbol = symbol,
                                    averageCents = result.trend.averageCents
                                )
                            }
                        }
                    }
                    selectedBucket?.let { chosen ->
                        item {
                            Spacer(Modifier.height(8.dp))
                            BucketDetail(
                                bucket = chosen,
                                symbol = symbol,
                                granularityLabel = granularityLabelGerman(result.granularity)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { TrendStatsCard(result.trend, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { ComparisonCard(result.comparison, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item {
                        Card(title = S("spar_entwicklung", "Spar-Entwicklung"), subtitle = S("uebrig_pro_granularitylabelgerman_resu", "Übrig pro %1\$s", granularityLabelGerman(result.granularity))) {
                            NetBarsChart(
                                buckets = result.buckets,
                                onSelect = { selectedBucket = it },
                                symbol = symbol
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                AnalyticsTab.CATEGORIES -> {
                    item {
                        Card(title = S("kategorien_vergleich", "Kategorien-Vergleich"), subtitle = S("rangfolge_nach_betrag", "Rangfolge nach Betrag")) {
                            CategoryRankingBars(
                                stats = result.expenseCategories,
                                symbol = symbol,
                                onSelect = {
                                    drill = DrillTarget(it.categoryId, categoryLabel(it.name), it.emoji, null)
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { RecurringAnalysisCard(result, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { FixedVariableCard(result.fixedCents, result.variableCents, symbol) }
                    item { Spacer(Modifier.height(10.dp)) }
                    item { SpendingPercentCard(result.expenseCategories, symbol) { drill = DrillTarget(it.categoryId, categoryLabel(it.name), it.emoji, null) } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // --------------------------- filter sheet ---------------------------
    if (showFilter) {
        FilterSheet(
            filter = filter,
            categories = categories,
            onDismiss = { showFilter = false },
            onApply = {
                viewModel.analyticsFilter.value = it
                showFilter = false
            }
        )
    }

    // ------------------------- drill-down sheet --------------------------
    drill?.let { target ->
        DrillDownSheet(
            viewModel = viewModel,
            target = target,
            symbol = symbol,
            onDismiss = { drill = null },
            onDelete = { pendingDelete = it }
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            visible = true,
            title = S("buchung_loeschen", "Buchung löschen?"),
            message = str("confirm_delete_booking", "%1\$s · %2\$s vom %3\$s wirklich löschen?", item.title, Money.format(item.amountCents, symbol), Dates.full(item.date)),
            confirmLabel = S("loeschen_4", "Löschen"),
            onConfirm = {
                if (item.isIncome) {
                    viewModel.incomes.value.firstOrNull { it.id == item.id }
                        ?.let { viewModel.deleteIncome(it) }
                } else {
                    viewModel.expenses.value.firstOrNull { it.id == item.id }
                        ?.let { viewModel.deleteExpense(it) }
                }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun KpiGrid(result: com.finovo.app.util.AnalyticsResult, symbol: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ResultStat(S("gesamteinnahmen_2", "Gesamteinnahmen"), result.incomeCents, appColors().income, symbol, Modifier.weight(1f))
        ResultStat(S("gesamtausgaben_2", "Gesamtausgaben"), result.expenseCents, appColors().expense, symbol, Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ResultStat(str("analytics_remaining", "Übrig"), result.balanceCents, MaterialTheme.colorScheme.primary, symbol, Modifier.weight(1f))
        Column(
            modifier = Modifier
                .weight(1f)
                .premiumSurface(RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(S("sparquote", "Sparquote"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                String.format(AppLocale.locale(), "%.1f %%", result.savingsRate),
                style = MaterialTheme.typography.titleMedium,
                color = if (result.savingsRate >= 0) appColors().income else appColors().expense,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            HorizontalBar(
                percent = (result.savingsRate / 100f).coerceIn(0f, 1f),
                color = if (result.savingsRate >= 0) appColors().income else appColors().expense
            )
        }
    }
}

@Composable
private fun ResultStat(
    label: String,
    cents: Long,
    color: androidx.compose.ui.graphics.Color,
    symbol: String,
    modifier: Modifier = Modifier,
    isCount: Boolean = false
) {
    Column(
        modifier = modifier
            .premiumSurface(RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        if (isCount) {
            Text(
                S("cents", "%1\$s", cents),
                style = MaterialTheme.typography.titleMedium,
                color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        } else {
            AmountText(
                cents = cents,
                symbol = symbol,
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}

/** Guards against NaN / Infinity so no chart or label can ever break. */
private fun safePct(value: Float): Float =
    if (value.isNaN() || value.isInfinite()) 0f else value

@Composable
private fun AnalysisEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "📊",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(12.dp))
        Text(
            S("analysis_no_data", "Noch nicht genügend Daten für eine Finanzanalyse."),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            S(
                "analysis_no_data_hint",
                "Erfasse Einnahmen oder Ausgaben – danach erscheinen hier Auswertungen, Trends und Vergleiche."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Card(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .premiumSurface(RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ChartCard(
    title: String,
    chartType: ChartType,
    onChartTypeChange: (ChartType) -> Unit,
    allowed: List<ChartType>,
    extra: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .premiumSurface(RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        }
        if (allowed.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allowed.forEach { type ->
                    FilterChip(
                        selected = chartType == type,
                        onClick = { onChartTypeChange(type) },
                        label = {
                            Text(
                                chartTypeLabel(type),
                                style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
        if (extra != null) {
            Spacer(Modifier.height(8.dp))
            extra()
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun GranularityChips(current: Granularity, onChange: (Granularity?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Granularity.values().forEach { option ->
            FilterChip(
                selected = current == option,
                onClick = { onChange(option) },
                label = {
                    Text(
                        when (option) {
                            Granularity.DAILY -> S("rec_daily", "Täglich")
                            Granularity.WEEKLY -> S("rec_weekly", "Wöchentlich")
                            Granularity.MONTHLY -> S("monatlich", "Monatlich")
                            Granularity.YEARLY -> S("rec_yearly", "Jährlich")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

private fun chartTypeLabel(type: ChartType): String = when (type) {
    ChartType.BAR -> str("chart_bar", "📊 Balken")
    ChartType.LINE -> str("chart_line", "📈 Linie")
    ChartType.PIE -> str("chart_pie", "🥧 Tortendiagramm")
    ChartType.TREND -> str("chart_trend", "📉 Trend")
}

@Composable
private fun BudgetCard(enabled: Boolean, limit: Long, spent: Long, symbol: String) {
    val percent = if (limit <= 0L) 0f else (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1.5f)
    val color = when {
        limit <= 0L -> MaterialTheme.colorScheme.primary
        percent >= 1f -> appColors().expense
        percent >= 0.9f -> appColors().warning
        else -> appColors().income
    }
    Card(title = S("budget_vs_ist", "Budget vs. Ist"), subtitle = if (enabled && limit > 0) {
        S("budget_ausgegeben", "Budget %1\$s · ausgegeben %2\$s", Money.format(limit, symbol), Money.format(spent, symbol))
    } else str("budget_none_set", "Kein Budget eingerichtet")) {
        if (enabled && limit > 0) {
            HorizontalBar(percent = percent, color = color)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(S("ausgegeben_2", "Ausgegeben"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                    AmountText(spent, symbol, MaterialTheme.typography.titleSmall, appColors().expense)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(S("verbleibend_2", "Verbleibend"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                    AmountText(
                        limit - spent, symbol, MaterialTheme.typography.titleSmall,
                        if (limit - spent < 0) appColors().expense else appColors().income
                    )
                }
            }
        } else {
            Text(
                S("lege_ein_monatsbudget_fest_um_budget_u", "Lege ein Monatsbudget fest, um Budget und Ist-Ausgaben zu vergleichen."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        }
    }
}

@Composable
private fun SavingsCard(result: com.finovo.app.util.AnalyticsResult, symbol: String) {
    Card(
        title = S("spar_analyse", "Spar-Analyse"),
        subtitle = S("sparen_einnahmen_ausgaben", "Sparen = Einnahmen − Ausgaben")
    ) {
        InfoLine(str("savings_in_period", "Ersparnis im Zeitraum"), Money.format(result.savingsCents, symbol), result.savingsCents >= 0)
        InfoLine(str("sparquote", "Sparquote"), String.format(AppLocale.locale(), "%.1f %%", result.savingsRate), result.savingsRate >= 0)
        val months = result.range.monthCount().coerceAtLeast(1)
        InfoLine(
            S("avg_per_month", "Ø pro Monat"),
            Money.format(result.savingsCents / months, symbol),
            result.savingsCents >= 0
        )
        InfoLine(
            S("hochrechnung_pro_jahr", "Hochrechnung pro Jahr"),
            Money.format((result.savingsCents / months) * 12, symbol),
            result.savingsCents >= 0
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String, positive: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = if (positive) appColors().income else appColors().expense,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InsightsCard(insights: List<Insight>) {
    Card(title = S("erkenntnisse", "Erkenntnisse")) {
        if (insights.isEmpty()) {
            Text(
                S("sobald_buchungen_vorhanden_sind_ersche", "Sobald Buchungen vorhanden sind, erscheinen hier automatische Auswertungen."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        } else {
            insights.forEach { insight ->
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(insight.emoji, fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        insight.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(comparison: com.finovo.app.util.MonthComparison, symbol: String) {
    Card(
        title = S("monatsvergleich", "Monatsvergleich"),
        subtitle = S("comparison_currentlabel_vs_comparison", "%1\$s vs. %2\$s", comparison.currentLabel, comparison.previousLabel)
    ) {
        DeltaLine(
            S("einnahmen_6", "Einnahmen"),
            comparison.previousIncomeCents,
            comparison.currentIncomeCents,
            comparison.incomeDeltaCents,
            comparison.incomeDeltaPercent,
            symbol,
            higherIsGood = true
        )
        DeltaLine(
            S("ausgaben_5", "Ausgaben"),
            comparison.previousExpenseCents,
            comparison.currentExpenseCents,
            comparison.expenseDeltaCents,
            comparison.expenseDeltaPercent,
            symbol,
            higherIsGood = false
        )
        Spacer(Modifier.height(4.dp))
        InfoLine(
            str("remaining_change", "Übrig (Veränderung)"),
            "${if (comparison.balanceDeltaCents >= 0) "+" else "−"}" +
                Money.format(kotlin.math.abs(comparison.balanceDeltaCents), symbol),
            comparison.balanceDeltaCents >= 0
        )
    }
}

@Composable
private fun DeltaLine(
    label: String,
    previous: Long,
    current: Long,
    delta: Long,
    percent: Float?,
    symbol: String,
    higherIsGood: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
            Text(
                S("money_format_previous_symbol_money_for", "%1\$s → %2\$s", Money.format(previous, symbol), Money.format(current, symbol)),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        }
        val good = if (higherIsGood) delta >= 0 else delta <= 0
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${if (delta >= 0) "+" else "−"}${Money.format(kotlin.math.abs(delta), symbol)}",
                style = MaterialTheme.typography.titleSmall,
                color = if (good) appColors().income else appColors().expense,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
            if (percent != null) {
                Text(
                    String.format(AppLocale.locale(), "%+.1f %%", percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (good) appColors().income else appColors().expense
                )
            }
        }
    }
}

@Composable
private fun FixedVariableCard(fixed: Long, variable: Long, symbol: String) {
    val total = (fixed + variable).coerceAtLeast(1)
    Card(title = S("fixkosten_vs_variable_kosten", "Fixkosten vs. variable Kosten")) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(S("fixkosten_2", "🔒 Fixkosten"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                AmountText(fixed, symbol, MaterialTheme.typography.titleSmall, appColors().warning)
                Text(
                    String.format(AppLocale.locale(), "%.1f %%", safePct(fixed.toFloat() / total * 100f)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(S("variable", "🔄 Variable"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                AmountText(variable, symbol, MaterialTheme.typography.titleSmall, MaterialTheme.colorScheme.primary)
                Text(
                    String.format(AppLocale.locale(), "%.1f %%", safePct(variable.toFloat() / total * 100f)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalBar(
            percent = safePct(fixed.toFloat() / total).coerceIn(0f, 1f),
            color = appColors().warning
        )
        Spacer(Modifier.height(6.dp))
        Text(
            S("gesamt_money_format_fixed_variable_sym", "Gesamt: %1\$s", Money.format(fixed + variable, symbol)),
            style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SpendingPercentCard(
    stats: List<CategoryStat>,
    symbol: String,
    onSelect: (CategoryStat) -> Unit
) {
    Card(title = S("ausgabenanteile", "Ausgabenanteile"), subtitle = S("anteil_an_den_gesamtausgaben", "Anteil an den Gesamtausgaben")) {
        if (stats.isEmpty()) {
            Text(
                S("keine_ausgaben_im_zeitraum", "Keine Ausgaben im Zeitraum."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        } else {
            stats.forEach { stat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(stat) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stat.emoji, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                    Text(
                        categoryLabel(stat.name),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AmountText(stat.cents, symbol, MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        String.format(AppLocale.locale(), "%.1f%%", stat.percent * 100),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(52.dp),
                        textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomeSourceList(
    stats: List<CategoryStat>,
    symbol: String,
    onSelect: (CategoryStat) -> Unit
) {
    Card(title = S("einnahmequellen", "Einnahmequellen"), subtitle = S("mit_betrag_und_anteil_antippen_fuer_de", "Mit Betrag und Anteil – antippen für Details")) {
        if (stats.isEmpty()) {
            Text(
                S("keine_einnahmen_im_zeitraum", "Keine Einnahmen im Zeitraum."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        } else {
            stats.forEach { stat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(stat) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stat.emoji, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(categoryLabel(stat.name), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            S("booking_count", "${stat.count} Buchungen", stat.count) + " · " +
                                String.format(AppLocale.locale(), "%.1f%%", stat.percent * 100),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                    AmountText(stat.cents, symbol, MaterialTheme.typography.titleSmall, appColors().income)
                }
            }
        }
    }
}

@Composable
private fun HighlightCard(
    title: String,
    emoji: String,
    name: String,
    cents: Long,
    date: String,
    symbol: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(title = title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(emoji)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis)
                Text(date, style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
            }
            AmountText(cents, symbol, MaterialTheme.typography.titleMedium, color)
        }
    }
}

@Composable
private fun TrendStatsCard(trend: com.finovo.app.util.TrendStats, symbol: String) {
    Card(title = S("trend_kennzahlen", "Trend-Kennzahlen"), subtitle = S("basierend_auf_den_echten_zeitreihen_we", "Basierend auf den echten Zeitreihen-Werten")) {
        InfoLine(
            str("highest_value", "Höchster Wert"),
            trend.highest?.let { "${it.label}: ${Money.format(it.expenseCents, symbol)}" } ?: "–"
        )
        InfoLine(
            str("lowest_value", "Niedrigster Wert"),
            trend.lowest?.let { "${it.label}: ${Money.format(it.expenseCents, symbol)}" } ?: "–"
        )
        InfoLine("Durchschnitt", Money.format(trend.averageCents, symbol))
        InfoLine(
            str("change_first_last", "Veränderung (erster → letzter)"),
            "${if (trend.deltaCents >= 0) "+" else "−"}" +
                "${Money.format(kotlin.math.abs(trend.deltaCents), symbol)} · " +
                String.format(AppLocale.locale(), "%+.1f %%", trend.deltaPercent),
            trend.deltaCents <= 0
        )
    }
}

@Composable
private fun RecurringAnalysisCard(result: com.finovo.app.util.AnalyticsResult, symbol: String) {
    Card(title = S("wiederkehrende_kosten", "Wiederkehrende Kosten"), subtitle = S("hochgerechnet_aus_deinen_aktiven_regel", "Hochgerechnet aus deinen aktiven Regeln")) {
        if (result.recurring.isEmpty()) {
            Text(
                S("keine_aktiven_wiederkehrenden_zahlunge", "Keine aktiven wiederkehrenden Zahlungen."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        } else {
            result.recurring.forEach { cost ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cost.emoji, fontSize = 16.sp, modifier = Modifier.width(26.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(categoryLabel(cost.name), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(
                            S("money_format_cost_amountcents_symbol_c", "%1\$s · %2\$s", Money.format(cost.amountCents, symbol), com.finovo.app.util.Recurrence.frequencyLabel(cost.frequency)),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AmountText(cost.monthlyCents, symbol, MaterialTheme.typography.titleSmall)
                        Text(
                            S("money_format_cost_yearlycents_symbol_j", "%1\$s/Jahr", Money.format(cost.yearlyCents, symbol)),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(S("pro_monat", "pro Monat"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                    AmountText(
                        result.recurringMonthlyCents, symbol,
                        MaterialTheme.typography.titleMedium, appColors().expense
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(S("pro_jahr", "pro Jahr"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                    AmountText(
                        result.recurringYearlyCents, symbol,
                        MaterialTheme.typography.titleMedium, appColors().expense
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportCard(
    onCsv: () -> Unit,
    onPdf: () -> Unit,
    onShareCsv: () -> Unit,
    onSharePdf: () -> Unit,
    onSummary: () -> Unit
) {
    Card(title = S("analyse_exportieren", "Analyse exportieren"), subtitle = S("fuer_den_gewaehlten_zeitraum_und_filte", "Für den gewählten Zeitraum und Filter")) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = "CSV",
                onClick = onCsv,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "PDF",
                onClick = onPdf,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = S("csv_teilen_2", "CSV teilen"),
                onClick = onShareCsv,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = S("pdf_teilen_2", "PDF teilen"),
                onClick = onSharePdf,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = S("zusammenfassung_teilen", "Zusammenfassung teilen"),
            onClick = onSummary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------------------
// Filter sheet
// ---------------------------------------------------------------------------

@Composable
private fun FilterSheet(
    filter: AnalyticsFilter,
    categories: List<com.finovo.app.data.CategoryEntity>,
    onDismiss: () -> Unit,
    onApply: (AnalyticsFilter) -> Unit
) {
    var current by remember(filter) { mutableStateOf(filter) }

    BottomSheetContainer(onDismiss = onDismiss, title = S("filter", "Filter")) {
        Text(S("art", "Art"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TypeFilter.BOTH to S("alle", "Alle"),
                TypeFilter.INCOME to S("einnahmen_7", "Einnahmen"),
                TypeFilter.EXPENSE to S("ausgaben_6", "Ausgaben")
            ).forEach { (value, label) ->
                FilterChip(
                    selected = current.type == value,
                    onClick = { current = current.copy(type = value) },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(S("kostenart", "Kostenart"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                FixedFilter.ALL to S("alle_2", "Alle"),
                FixedFilter.FIXED to S("filter_fixed_costs", "Fixkosten"),
                FixedFilter.VARIABLE to S("filter_variable_short", "Variabel")
            ).forEach { (value, label) ->
                FilterChip(
                    selected = current.fixed == value,
                    onClick = { current = current.copy(fixed = value) },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(S("status", "Status"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PaidFilter.ALL to S("alle_3", "Alle"),
                PaidFilter.PAID to S("bill_paid", "Bezahlt"),
                PaidFilter.UNPAID to S("offen_2", "Offen")
            ).forEach { (value, label) ->
                FilterChip(
                    selected = current.paid == value,
                    onClick = { current = current.copy(paid = value) },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(S("wiederkehr", "Wiederkehr"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                RecurrenceFilter.ALL to S("alle_4", "Alle"),
                RecurrenceFilter.RECURRING to S("fallback_recurring", "Wiederkehrend"),
                RecurrenceFilter.ONE_TIME to S("filter_one_time", "Einmalig")
            ).forEach { (value, label) ->
                FilterChip(
                    selected = current.recurring == value,
                    onClick = { current = current.copy(recurring = value) },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            S("kategorien_2", "Kategorien"),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted
        )
        Spacer(Modifier.height(6.dp))
        Text(
            S("ohne_auswahl_werden_alle_kategorien_be", "Ohne Auswahl werden alle Kategorien berücksichtigt."),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { category ->
                        val selected = category.id in current.categoryIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = current.categoryIds.toMutableSet()
                                if (selected) next.remove(category.id) else next.add(category.id)
                                current = current.copy(categoryIds = next)
                            },
                            label = {
                                Text(
                                    S("category_emoji_name", "%1\$s %2\$s", category.emoji, categoryLabel(category.name)),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = S("filter_anwenden", "Filter anwenden"),
            onClick = { onApply(current) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(
            text = S("alle_filter_zuruecksetzen", "Alle Filter zurücksetzen"),
            onClick = { onApply(AnalyticsFilter()) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Drill down
// ---------------------------------------------------------------------------

@Composable
private fun DrillDownSheet(
    viewModel: AppViewModel,
    target: DrillTarget,
    symbol: String,
    onDismiss: () -> Unit,
    onDelete: (TxItem) -> Unit
) {
    val items = remember(target, viewModel.analytics.collectAsState().value) {
        viewModel.analyticsDrillDown(target.categoryId, target.range)
    }
    BottomSheetContainer(onDismiss = onDismiss, title = S("target_emoji_target_title", "%1\$s %2\$s", target.emoji, target.title)) {
        val total = items.sumOf { if (it.isIncome) it.amountCents else -it.amountCents }
        Text(
            S("items_size_buchungen_saldo_money_forma", "%1\$s Buchungen · Saldo %2\$s", items.size, Money.format(total, symbol)),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted
        )
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            EmptyState(
                emoji = "🔍",
                title = S("keine_buchungen", "Keine Buchungen"),
                message = S("fuer_diese_auswahl_gibt_es_keine_e", "Für diese Auswahl gibt es keine Einträge.")
            )
        } else {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (item.isIncome) {
                                viewModel.incomes.value.firstOrNull { it.id == item.id }
                                    ?.let { viewModel.openIncomeSheet(it) }
                            } else {
                                viewModel.expenses.value.firstOrNull { it.id == item.id }
                                    ?.let { viewModel.openExpenseSheet(it) }
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryBadge(item.emoji, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            Dates.full(item.date) +
                                (if (item.note.isNotBlank()) " · ${item.note}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AmountText(
                        cents = if (item.isIncome) item.amountCents else -item.amountCents,
                        symbol = symbol,
                        signed = true,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (item.isIncome) appColors().income else appColors().expense
                    )
                    IconActionButton(Icons.Default.Edit, str("bearbeiten", "Bearbeiten"), onClick = {
                        if (item.isIncome) {
                            viewModel.incomes.value.firstOrNull { it.id == item.id }
                                ?.let { viewModel.openIncomeSheet(it) }
                        } else {
                            viewModel.expenses.value.firstOrNull { it.id == item.id }
                                ?.let { viewModel.openExpenseSheet(it) }
                        }
                    }, tint = appColors().textMuted)
                    IconActionButton(
                        Icons.Default.Delete,
                        S("loeschen_5", "Löschen"),
                        onClick = { onDelete(item) },
                        tint = appColors().textMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun shareFile(context: android.content.Context, file: File, mime: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, str("share_analysis", "Analyse teilen"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        Toast.makeText(context, str("share_failed", "Teilen nicht möglich: %1\$s", it.message ?: ""), Toast.LENGTH_LONG).show()
    }
}


@Composable
private fun MonthlyComparisonCard(viewModel: AppViewModel, symbol: String) {
    val expenses = viewModel.expenses.collectAsState().value
    val incomes = viewModel.incomes.collectAsState().value
    val months = remember {
        val now = Dates.today()
        (11 downTo 0).map { java.time.YearMonth.from(now).minusMonths(it.toLong()) }
    }
    val rows = months.map { ym ->
        val (f, t) = Dates.monthRange(ym.atDay(1))
        Triple(
            ym,
            incomes.filter { it.date in f..t }.sumOf { it.amountCents },
            expenses.filter { it.date in f..t }.sumOf { it.amountCents }
        )
    }
    val max = (rows.maxOfOrNull { maxOf(it.second, it.third) } ?: 0L).coerceAtLeast(1L)

    com.finovo.app.ui.components.AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(S("einnahmen_vs_ausgaben", "Einnahmen vs. Ausgaben"), style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
            Text(
                S("monthly_comparison", "Monatsvergleich der letzten 12 Monate"),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
            Spacer(Modifier.height(14.dp))
            rows.forEach { (ym, inc, exp) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        Dates.chartMonth(ym.atDay(1)),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted,
                        modifier = Modifier.width(42.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .weight(if (inc > 0) (inc.toFloat() / max.toFloat()).coerceIn(0.0015f, 1f) else 0.0015f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(appColors().income)
                            )
                            Box(modifier = Modifier.width(0.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .weight(if (exp > 0) (exp.toFloat() / max.toFloat()).coerceIn(0.0015f, 1f) else 0.0015f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(appColors().expense)
                            )
                            Box(modifier = Modifier.width(0.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Money.format(inc - exp, symbol),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (inc - exp >= 0) appColors().income else appColors().expense,
                        modifier = Modifier.width(84.dp),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@Composable
private fun AnalysisCrashScreen(onRetry: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val log = remember { CrashGuard.readLog(context) }
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            tutorialId = "analytics",
            title = S("analysis_title", "Finanzanalyse"),
            subtitle = "",
            onBack = onBack
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = S("crash_safe_title", "Diese Ansicht konnte nicht aufgebaut werden."),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = S(
                    "crash_safe_body",
                    "Die App läuft weiter und deine Daten sind sicher. Das Protokoll unten zeigt die genaue Ursache."
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onRetry) {
                Text(S("retry", "Erneut versuchen"))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = S("crash_log_head", "Fehlerprotokoll"),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (log.isBlank()) S("crash_empty", "Kein Fehlerprotokoll vorhanden.") else log,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = { CrashGuard.copyLog(context) }) {
                Text(S("crash_copy", "Protokoll kopieren"))
            }
            TextButton(onClick = { CrashGuard.clearLog() }) {
                Text(S("crash_clear", "Protokoll löschen"))
            }
        }
    }
}

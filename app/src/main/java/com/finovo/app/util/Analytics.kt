package com.finovo.app.util

import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.Frequency
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import com.finovo.app.util.str

// ---------------------------------------------------------------------------
// Periods, granularity and filters
// ---------------------------------------------------------------------------

enum class AnalyticsPeriod {
    TODAY, THIS_WEEK, THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, LAST_6_MONTHS,
    THIS_YEAR, LAST_YEAR, CUSTOM;

    fun label(): String = when (this) {
        TODAY -> str("heute", "Heute")
        THIS_WEEK -> str("diese_woche", "Diese Woche")
        THIS_MONTH -> str("dieser_monat", "Dieser Monat")
        LAST_MONTH -> str("letzter_monat", "Letzter Monat")
        LAST_3_MONTHS -> str("letzte_3_monate", "Letzte 3 Monate")
        LAST_6_MONTHS -> str("letzte_6_monate", "Letzte 6 Monate")
        THIS_YEAR -> str("dieses_jahr", "Dieses Jahr")
        LAST_YEAR -> str("letztes_jahr", "Letztes Jahr")
        CUSTOM -> str("rec_custom", "Benutzerdefiniert")
    }
}

enum class Granularity { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class ChartType { BAR, LINE, PIE, TREND }

enum class TypeFilter { BOTH, INCOME, EXPENSE }

enum class FixedFilter { ALL, FIXED, VARIABLE }

enum class PaidFilter { ALL, PAID, UNPAID }

enum class RecurrenceFilter { ALL, RECURRING, ONE_TIME }

data class AnalyticsFilter(
    val categoryIds: Set<Long> = emptySet(),
    val type: TypeFilter = TypeFilter.BOTH,
    val fixed: FixedFilter = FixedFilter.ALL,
    val paid: PaidFilter = PaidFilter.ALL,
    val recurring: RecurrenceFilter = RecurrenceFilter.ALL
) {
    val isActive: Boolean
        get() = categoryIds.isNotEmpty() || type != TypeFilter.BOTH ||
            fixed != FixedFilter.ALL || paid != PaidFilter.ALL ||
            recurring != RecurrenceFilter.ALL

    fun describe(categories: List<CategoryEntity>): String {
        val parts = mutableListOf<String>()
        if (categoryIds.isNotEmpty()) {
            val names = categoryIds.mapNotNull { id -> categories.firstOrNull { it.id == id }?.name }
            if (names.isNotEmpty()) parts += names.joinToString(", ")
        }
        when (type) {
            TypeFilter.INCOME -> parts += str("nur_einnahmen", "nur Einnahmen")
            TypeFilter.EXPENSE -> parts += str("nur_ausgaben", "nur Ausgaben")
            TypeFilter.BOTH -> Unit
        }
        when (fixed) {
            FixedFilter.FIXED -> parts += str("filter_fixed_costs", "Fixkosten")
            FixedFilter.VARIABLE -> parts += str("filter_variable_costs", "variable Kosten")
            FixedFilter.ALL -> Unit
        }
        when (paid) {
            PaidFilter.PAID -> parts += "bezahlt"
            PaidFilter.UNPAID -> parts += str("offen_4", "offen")
            PaidFilter.ALL -> Unit
        }
        when (recurring) {
            RecurrenceFilter.RECURRING -> parts += "wiederkehrend"
            RecurrenceFilter.ONE_TIME -> parts += "einmalig"
            RecurrenceFilter.ALL -> Unit
        }
        return parts.joinToString(" · ")
    }
}

// ---------------------------------------------------------------------------
// Result models
// ---------------------------------------------------------------------------

/** One point of a real time series (never a collapsed total). */
data class Bucket(
    val key: String,
    val label: String,
    val start: LocalDate,
    val end: LocalDate,
    val incomeCents: Long,
    val expenseCents: Long,
    val expenseCount: Int = 0,
    val incomeCount: Int = 0
) {
    val netCents: Long get() = incomeCents - expenseCents
}

data class CategoryStat(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val cents: Long,
    val percent: Float,
    val count: Int,
    val isFixed: Boolean = false
)

data class RecurringCost(
    val ruleId: Long,
    val name: String,
    val emoji: String,
    val amountCents: Long,
    val frequency: Frequency,
    val interval: Int,
    val monthlyCents: Long,
    val yearlyCents: Long,
    val kind: String
)

data class TrendStats(
    val highest: Bucket?,
    val lowest: Bucket?,
    val averageCents: Long,
    val deltaCents: Long,
    val deltaPercent: Float
)

data class MonthComparison(
    val currentLabel: String,
    val previousLabel: String,
    val currentIncomeCents: Long,
    val previousIncomeCents: Long,
    val currentExpenseCents: Long,
    val previousExpenseCents: Long,
    val incomeDeltaCents: Long,
    val expenseDeltaCents: Long,
    val balanceDeltaCents: Long,
    val incomeDeltaPercent: Float?,
    val expenseDeltaPercent: Float?
)

data class Insight(val emoji: String, val text: String)

data class AnalyticsResult(
    val range: DateRange,
    val label: String,
    val granularity: Granularity,
    val incomeCents: Long,
    val expenseCents: Long,
    val transactionCount: Int,
    val buckets: List<Bucket>,
    val expenseCategories: List<CategoryStat>,
    val incomeCategories: List<CategoryStat>,
    val fixedCents: Long,
    val variableCents: Long,
    val highestExpense: ExpenseEntity?,
    val highestIncome: IncomeEntity?,
    val recurring: List<RecurringCost>,
    val recurringMonthlyCents: Long,
    val recurringYearlyCents: Long,
    val savingsCents: Long,
    val savingsRate: Float,
    val trend: TrendStats,
    val comparison: MonthComparison,
    val insights: List<Insight>
) {
    val balanceCents: Long get() = incomeCents - expenseCents
    val averageMonthlyExpenseCents: Long
        get() = Stats.monthlyAverage(expenseCents, range)

    /** False when there is nothing to analyse yet (empty database). */
    val hasData: Boolean
        get() = transactionCount > 0 || incomeCents != 0L || expenseCents != 0L ||
            expenseCategories.isNotEmpty() || incomeCategories.isNotEmpty()
}

// ---------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------

object Analytics {

    // ------------------------------ periods ---------------------------------

    fun resolveRange(
        period: AnalyticsPeriod,
        today: LocalDate,
        custom: Pair<LocalDate, LocalDate>? = null
    ): DateRange = when (period) {
        AnalyticsPeriod.TODAY -> {
            val d = today.toEpochDay()
            DateRange(d, d)
        }
        AnalyticsPeriod.THIS_WEEK -> DateRange(
            Dates.startOfWeek(today).toEpochDay(),
            Dates.endOfWeek(today).toEpochDay()
        )
        AnalyticsPeriod.THIS_MONTH -> DateRange(
            Dates.startOfMonth(today).toEpochDay(),
            Dates.endOfMonth(today).toEpochDay()
        )
        AnalyticsPeriod.LAST_MONTH -> {
            val last = today.minusMonths(1)
            DateRange(
                Dates.startOfMonth(last).toEpochDay(),
                Dates.endOfMonth(last).toEpochDay()
            )
        }
        AnalyticsPeriod.LAST_3_MONTHS -> DateRange(
            Dates.startOfMonth(today.minusMonths(2)).toEpochDay(),
            Dates.endOfMonth(today).toEpochDay()
        )
        AnalyticsPeriod.LAST_6_MONTHS -> DateRange(
            Dates.startOfMonth(today.minusMonths(5)).toEpochDay(),
            Dates.endOfMonth(today).toEpochDay()
        )
        AnalyticsPeriod.THIS_YEAR -> DateRange(
            Dates.startOfYear(today).toEpochDay(),
            Dates.endOfYear(today).toEpochDay()
        )
        AnalyticsPeriod.LAST_YEAR -> {
            val last = today.minusYears(1)
            DateRange(
                Dates.startOfYear(last).toEpochDay(),
                Dates.endOfYear(last).toEpochDay()
            )
        }
        AnalyticsPeriod.CUSTOM -> {
            val from = custom?.first ?: Dates.startOfMonth(today)
            val to = custom?.second ?: today
            if (from <= to) DateRange(from.toEpochDay(), to.toEpochDay())
            else DateRange(to.toEpochDay(), from.toEpochDay())
        }
    }

    fun label(
        period: AnalyticsPeriod,
        today: LocalDate,
        custom: Pair<LocalDate, LocalDate>? = null
    ): String = when (period) {
        AnalyticsPeriod.TODAY -> Dates.full(today)
        AnalyticsPeriod.THIS_WEEK -> "${Dates.short(Dates.startOfWeek(today))} – ${Dates.short(
            Dates.endOfWeek(today)
        )}"
        AnalyticsPeriod.THIS_MONTH -> Dates.monthYear(today)
        AnalyticsPeriod.LAST_MONTH -> Dates.monthYear(today.minusMonths(1))
        AnalyticsPeriod.LAST_3_MONTHS -> str("letzte_3_monate_2", "Letzte 3 Monate")
        AnalyticsPeriod.LAST_6_MONTHS -> str("letzte_6_monate_2", "Letzte 6 Monate")
        AnalyticsPeriod.THIS_YEAR -> "${today.year}"
        AnalyticsPeriod.LAST_YEAR -> "${today.year - 1}"
        AnalyticsPeriod.CUSTOM -> custom?.let { "${Dates.full(it.first)} – ${Dates.full(it.second)}" }
            ?: Dates.monthYear(today)
    }

    /** Sensible default granularity for a period (user can always override). */
    fun defaultGranularity(period: AnalyticsPeriod): Granularity = when (period) {
        AnalyticsPeriod.TODAY -> Granularity.DAILY
        AnalyticsPeriod.THIS_WEEK -> Granularity.DAILY
        AnalyticsPeriod.THIS_MONTH -> Granularity.DAILY
        AnalyticsPeriod.LAST_MONTH -> Granularity.DAILY
        AnalyticsPeriod.LAST_3_MONTHS -> Granularity.MONTHLY
        AnalyticsPeriod.LAST_6_MONTHS -> Granularity.MONTHLY
        AnalyticsPeriod.THIS_YEAR -> Granularity.MONTHLY
        AnalyticsPeriod.LAST_YEAR -> Granularity.MONTHLY
        AnalyticsPeriod.CUSTOM -> Granularity.MONTHLY
    }

    // ------------------------------ filtering ---------------------------------

    fun isFixedExpense(expense: ExpenseEntity, categories: List<CategoryEntity>): Boolean {
        expense.fixedOverride?.let { return it }
        return categories.firstOrNull { it.id == expense.categoryId }?.isFixed ?: false
    }

    fun filterExpenses(
        expenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        range: DateRange,
        filter: AnalyticsFilter
    ): List<ExpenseEntity> = expenses.filter { e ->
        range.contains(e.date) &&
            (filter.categoryIds.isEmpty() || e.categoryId in filter.categoryIds) &&
            when (filter.fixed) {
                FixedFilter.ALL -> true
                FixedFilter.FIXED -> isFixedExpense(e, categories)
                FixedFilter.VARIABLE -> !isFixedExpense(e, categories)
            } &&
            when (filter.paid) {
                PaidFilter.ALL -> true
                PaidFilter.PAID -> e.paid
                PaidFilter.UNPAID -> !e.paid
            } &&
            when (filter.recurring) {
                RecurrenceFilter.ALL -> true
                RecurrenceFilter.RECURRING -> e.recurringId != null
                RecurrenceFilter.ONE_TIME -> e.recurringId == null
            }
    }

    fun filterIncomes(
        incomes: List<IncomeEntity>,
        range: DateRange,
        filter: AnalyticsFilter
    ): List<IncomeEntity> = incomes.filter { i ->
        range.contains(i.date) &&
            (filter.categoryIds.isEmpty() || i.categoryId in filter.categoryIds) &&
            when (filter.recurring) {
                RecurrenceFilter.ALL -> true
                RecurrenceFilter.RECURRING -> i.recurringId != null
                RecurrenceFilter.ONE_TIME -> i.recurringId == null
            }
    }

    // ------------------------------- buckets -----------------------------------

    fun buildBuckets(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        range: DateRange,
        granularity: Granularity
    ): List<Bucket> {
        if (range.to < range.from) return emptyList()
        val start = Dates.fromEpochDay(range.from)
        val end = Dates.fromEpochDay(range.to)

        val bucketStarts = mutableListOf<LocalDate>()
        when (granularity) {
            Granularity.DAILY -> {
                var d = start
                while (d <= end && bucketStarts.size < 400) {
                    bucketStarts += d
                    d = d.plusDays(1)
                }
            }
            Granularity.WEEKLY -> {
                var d = Dates.startOfWeek(start)
                while (d <= end && bucketStarts.size < 200) {
                    bucketStarts += d
                    d = d.plusWeeks(1)
                }
            }
            Granularity.MONTHLY -> {
                var ym = YearMonth.from(start)
                val endYm = YearMonth.from(end)
                while (ym <= endYm && bucketStarts.size < 240) {
                    bucketStarts += ym.atDay(1)
                    ym = ym.plusMonths(1)
                }
            }
            Granularity.YEARLY -> {
                var y = start.year
                while (y <= end.year && bucketStarts.size < 100) {
                    bucketStarts += LocalDate.of(y, 1, 1)
                    y++
                }
            }
        }

        return bucketStarts.map { bucketStart ->
            val bucketEnd = when (granularity) {
                Granularity.DAILY -> bucketStart
                Granularity.WEEKLY -> bucketStart.plusDays(6)
                Granularity.MONTHLY -> YearMonth.from(bucketStart).atEndOfMonth()
                Granularity.YEARLY -> LocalDate.of(bucketStart.year, 12, 31)
            }.let { if (it > end) end else it }
            val from = maxOf(bucketStart.toEpochDay(), range.from)
            val to = minOf(bucketEnd.toEpochDay(), range.to)

            var income = 0L
            var incomeCount = 0
            for (i in incomes) {
                if (i.date in from..to) {
                    income += i.amountCents
                    incomeCount++
                }
            }
            var expense = 0L
            var expenseCount = 0
            for (e in expenses) {
                if (e.date in from..to) {
                    expense += e.amountCents
                    expenseCount++
                }
            }
            Bucket(
                key = "${from}_$to",
                label = bucketLabel(bucketStart, granularity),
                start = Dates.fromEpochDay(from),
                end = Dates.fromEpochDay(to),
                incomeCents = income,
                expenseCents = expense,
                expenseCount = expenseCount,
                incomeCount = incomeCount
            )
        }
    }

    fun bucketLabel(date: LocalDate, granularity: Granularity): String = when (granularity) {
        Granularity.DAILY -> Dates.short(date)
        Granularity.WEEKLY -> str("chart_week_short", "KW%1\$d", weekOfYear(date))
        Granularity.MONTHLY -> Dates.chartMonth(date)
        Granularity.YEARLY -> date.year.toString()
    }

    private fun weekOfYear(date: LocalDate): Int =
        date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())

    // ---------------------------- category stats --------------------------------

    fun expenseCategoryStats(
        expenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        range: DateRange,
        filter: AnalyticsFilter
    ): List<CategoryStat> {
        val filtered = filterExpenses(expenses, categories, range, filter)
        return toCategoryStats(filtered.map { it.categoryId to it.amountCents }, categories, true)
    }

    fun incomeCategoryStats(
        incomes: List<IncomeEntity>,
        categories: List<CategoryEntity>,
        range: DateRange,
        filter: AnalyticsFilter
    ): List<CategoryStat> {
        val filtered = filterIncomes(incomes, range, filter)
        return toCategoryStats(filtered.map { it.categoryId to it.amountCents }, categories, false)
    }

    private fun toCategoryStats(
        pairs: List<Pair<Long, Long>>,
        categories: List<CategoryEntity>,
        expenses: Boolean
    ): List<CategoryStat> {
        val totals = LinkedHashMap<Long, Long>()
        val counts = LinkedHashMap<Long, Int>()
        for ((id, cents) in pairs) {
            totals[id] = (totals[id] ?: 0L) + cents
            counts[id] = (counts[id] ?: 0) + 1
        }
        val sum = totals.values.sum()
        return totals.entries.map { (id, cents) ->
            val c = categories.firstOrNull { it.id == id }
            CategoryStat(
                categoryId = id,
                name = categoryLabel(c?.name ?: (if (expenses) "Sonstiges" else "Sonstige Einnahmen")),
                emoji = c?.emoji ?: (if (expenses) "📦" else "💰"),
                cents = cents,
                percent = if (sum == 0L) 0f else cents.toFloat() / sum.toFloat(),
                count = counts[id] ?: 0,
                isFixed = c?.isFixed ?: false
            )
        }.sortedByDescending { it.cents }
    }

    // -------------------------- fixed vs variable --------------------------------

    fun fixedVariable(
        expenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        range: DateRange,
        filter: AnalyticsFilter = AnalyticsFilter()
    ): Pair<Long, Long> {
        var fixed = 0L
        var variable = 0L
        for (e in filterExpenses(expenses, categories, range, filter)) {
            if (isFixedExpense(e, categories)) fixed += e.amountCents else variable += e.amountCents
        }
        return fixed to variable
    }

    // ------------------------------ recurring -------------------------------------

    fun recurringCosts(
        rules: List<RecurringRuleEntity>,
        categories: List<CategoryEntity>
    ): List<RecurringCost> = rules.filter { it.active }.map { rule ->
        val frequency = runCatching { Frequency.valueOf(rule.frequency) }
            .getOrDefault(Frequency.MONTHLY)
        val interval = rule.interval.coerceAtLeast(1)
        val perMonth = monthlyAmount(rule.amountCents, frequency, interval)
        RecurringCost(
            ruleId = rule.id,
            name = rule.name.ifBlank {
                categoryLabel(categories.firstOrNull { it.id == rule.categoryId }?.name ?: "Zahlung")
            },
            emoji = categories.firstOrNull { it.id == rule.categoryId }?.emoji ?: "🔁",
            amountCents = rule.amountCents,
            frequency = frequency,
            interval = interval,
            monthlyCents = perMonth,
            yearlyCents = perMonth * 12,
            kind = rule.kind
        )
    }.sortedByDescending { it.monthlyCents }

    /** Normalises any frequency to an average monthly amount (cents). */
    fun monthlyAmount(amountCents: Long, frequency: Frequency, interval: Int): Long {
        val step = interval.coerceAtLeast(1)
        return when (frequency) {
            Frequency.DAILY -> amountCents * 365 / (12 * step)
            Frequency.WEEKLY -> amountCents * 52 / (12 * step)
            Frequency.MONTHLY -> amountCents / step
            Frequency.YEARLY -> amountCents / (12 * step)
            Frequency.CUSTOM -> amountCents * 365 / (12 * step)
        }
    }

    fun recurringTotals(rules: List<RecurringRuleEntity>, kind: String): Pair<Long, Long> {
        val list = recurringCosts(rules, emptyList()).filter { it.kind == kind }
        return list.sumOf { it.monthlyCents } to list.sumOf { it.yearlyCents }
    }

    // ------------------------------- trends ----------------------------------------

    /** Trend of expenses across the buckets (real per-bucket values). */
    fun expenseTrend(buckets: List<Bucket>): TrendStats {
        val withData = buckets.filter { it.expenseCents != 0L || it.expenseCount > 0 }
        val values = buckets.map { it.expenseCents }
        val average = if (buckets.isEmpty()) 0L else values.sum() / buckets.size
        val first = withData.firstOrNull()?.expenseCents ?: 0L
        val last = withData.lastOrNull()?.expenseCents ?: 0L
        val delta = last - first
        val percent = if (first == 0L) {
            if (last == 0L) 0f else 100f
        } else delta.toFloat() / abs(first).toFloat() * 100f
        return TrendStats(
            highest = withData.maxByOrNull { it.expenseCents },
            lowest = withData.minByOrNull { it.expenseCents },
            averageCents = average,
            deltaCents = delta,
            deltaPercent = percent
        )
    }

    fun monthComparison(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        today: LocalDate
    ): MonthComparison {
        val current = YearMonth.from(today)
        val previous = current.minusMonths(1)
        val currentRange = DateRange(
            current.atDay(1).toEpochDay(), current.atEndOfMonth().toEpochDay()
        )
        val previousRange = DateRange(
            previous.atDay(1).toEpochDay(), previous.atEndOfMonth().toEpochDay()
        )
        val cIncome = Stats.sumIncomes(incomes, currentRange)
        val pIncome = Stats.sumIncomes(incomes, previousRange)
        val cExpense = Stats.sumExpenses(expenses, currentRange)
        val pExpense = Stats.sumExpenses(expenses, previousRange)
        return MonthComparison(
            currentLabel = Dates.monthYear(current.atDay(1)),
            previousLabel = Dates.monthYear(previous.atDay(1)),
            currentIncomeCents = cIncome,
            previousIncomeCents = pIncome,
            currentExpenseCents = cExpense,
            previousExpenseCents = pExpense,
            incomeDeltaCents = cIncome - pIncome,
            expenseDeltaCents = cExpense - pExpense,
            balanceDeltaCents = (cIncome - cExpense) - (pIncome - pExpense),
            incomeDeltaPercent = percentChange(pIncome, cIncome),
            expenseDeltaPercent = percentChange(pExpense, cExpense)
        )
    }

    fun percentChange(oldValue: Long, newValue: Long): Float? {
        if (oldValue == 0L) return null
        return (newValue - oldValue).toFloat() / abs(oldValue).toFloat() * 100f
    }

    // ------------------------------- insights ---------------------------------------

    fun insights(
        result: AnalyticsResult,
        comparison: MonthComparison,
        budgetLimitCents: Long,
        budgetEnabled: Boolean,
        symbol: String = "€"
    ): List<Insight> {
        val out = mutableListOf<Insight>()

        val top = result.expenseCategories.firstOrNull()
        if (top != null && top.cents > 0) {
            out += Insight(
                "📌",
                str("deine_hoechsten_ausgaben_sind_in_d", "Deine höchsten Ausgaben sind in diesem Zeitraum für %1\$s %2\$s ", top.emoji, top.name) +
                    "(${Money.format(top.cents, symbol)})."
            )
        }
        if (result.expenseCents > 0) {
            out += Insight(
                "📊",
                str("deine_durchschnittlichen_ausgaben_", "Deine durchschnittlichen Ausgaben betragen %1\$s pro Monat.", Money.format(result.averageMonthlyExpenseCents, symbol))
            )
        }
        if (comparison.previousExpenseCents > 0 || comparison.currentExpenseCents > 0) {
            val delta = comparison.expenseDeltaCents
            out += when {
                delta > 0 -> Insight(
                    "📈",
                    str("deine_ausgaben_sind_gegenueber_um_", "Deine Ausgaben sind gegenüber %1\$s um %2\$s gestiegen.", comparison.previousLabel, Money.format(delta, symbol))
                )
                delta < 0 -> Insight(
                    "📉",
                    str("deine_ausgaben_sind_gegenueber_2", "Deine Ausgaben sind gegenüber %1\$s um %2\$s gesunken.", comparison.previousLabel, Money.format(-delta, symbol))
                )
                else -> Insight("➖", str("deine_ausgaben_sind_im_vergleich_z", "Deine Ausgaben sind im Vergleich zum Vormonat unverändert."))
            }
        }
        if (result.savingsCents != 0L) {
            out += Insight(
                if (result.savingsCents > 0) "💚" else "🧾",
                if (result.savingsCents > 0)
                    str("insight_savings_left", "Du hast in diesem Zeitraum %1\$s übrig.", Money.format(result.savingsCents, symbol))
                else
                    str("du_hast_in_diesem_zeitraum_mehr_au", "Du hast in diesem Zeitraum %1\$s mehr ausgegeben als eingenommen.", Money.format(-result.savingsCents, symbol))
            )
        }
        if (result.savingsRate > 0f && result.incomeCents > 0) {
            out += Insight(
                "🎯",
                str("insight_savings_rate", "%1\$d %% deiner Einnahmen (%2\$s) sind noch übrig.", result.savingsRate.toInt(), Money.format(result.incomeCents, symbol))
            )
        }
        if (budgetEnabled && budgetLimitCents > 0) {
            val percent = result.expenseCents.toFloat() / budgetLimitCents.toFloat() * 100f
            out += Insight(
                "🧮",
                str("insight_budget_used_prefix", "Du hast %1\$s deines Budgets (", "%.0f %%".format(AppLocale.locale(), percent)) +
                    str("von_verwendet", "%1\$s von %2\$s) verwendet.", Money.format(result.expenseCents, symbol), Money.format(budgetLimitCents, symbol))
            )
        }
        if (result.recurringMonthlyCents > 0) {
            out += Insight(
                "🔁",
                str("deine_wiederkehrenden_kosten_betra", "Deine wiederkehrenden Kosten betragen %1\$s pro Monat.", Money.format(result.recurringMonthlyCents, symbol))
            )
        }
        if (result.fixedCents > 0) {
            out += Insight(
                "🔒",
                str("insight_fixed_variable", "Fixkosten: %1\$s · variable Kosten: %2\$s.", Money.format(result.fixedCents, symbol), Money.format(result.variableCents, symbol))
            )
        }
        return out
    }

    // ------------------------------ drill down ----------------------------------------

    /** Transactions behind a category (and optionally a single bucket). */
    fun drillDown(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        categories: List<CategoryEntity>,
        categoryId: Long,
        range: DateRange,
        filter: AnalyticsFilter = AnalyticsFilter()
    ): List<TxItem> {
        val items = mutableListOf<TxItem>()
        if (filter.type != TypeFilter.INCOME) {
            filterExpenses(expenses, categories, range, filter)
                .filter { it.categoryId == categoryId }
                .forEach { e ->
                    val c = categories.firstOrNull { it.id == e.categoryId }
                    items += TxItem(
                        key = "e${e.id}",
                        isIncome = false,
                        id = e.id,
                        amountCents = e.amountCents,
                        title = categoryLabel(c?.name ?: "Sonstiges"),
                        emoji = c?.emoji ?: "📦",
                        date = Dates.fromEpochDay(e.date),
                        note = e.note,
                        paid = e.paid
                    )
                }
        }
        if (filter.type != TypeFilter.EXPENSE) {
            filterIncomes(incomes, range, filter)
                .filter { it.categoryId == categoryId }
                .forEach { i ->
                    val c = categories.firstOrNull { it.id == i.categoryId }
                    items += TxItem(
                        key = "i${i.id}",
                        isIncome = true,
                        id = i.id,
                        amountCents = i.amountCents,
                        title = categoryLabel(c?.name ?: "Sonstige Einnahmen"),
                        emoji = c?.emoji ?: "💰",
                        date = Dates.fromEpochDay(i.date),
                        note = i.note
                    )
                }
        }
        return items.sortedWith(
            compareByDescending<TxItem> { it.date.toEpochDay() }.thenByDescending { it.id }
        )
    }

    // ------------------------------ main entry -----------------------------------------

    fun compute(
        period: AnalyticsPeriod,
        today: LocalDate,
        custom: Pair<LocalDate, LocalDate>?,
        granularity: Granularity?,
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        categories: List<CategoryEntity>,
        rules: List<RecurringRuleEntity>,
        budgetEnabled: Boolean,
        budgetLimitCents: Long,
        filter: AnalyticsFilter = AnalyticsFilter(),
        symbol: String = "€"
    ): AnalyticsResult {
        val range = resolveRange(period, today, custom)
        val usedGranularity = granularity ?: defaultGranularity(period)

        val filteredExpenses = if (filter.type == TypeFilter.INCOME) emptyList()
        else filterExpenses(expenses, categories, range, filter)
        val filteredIncomes = if (filter.type == TypeFilter.EXPENSE) emptyList()
        else filterIncomes(incomes, range, filter)

        val incomeCents = filteredIncomes.sumOf { it.amountCents }
        val expenseCents = filteredExpenses.sumOf { it.amountCents }
        val (fixed, variable) = fixedVariable(expenses, categories, range, filter)

        val buckets = buildBuckets(filteredExpenses, filteredIncomes, range, usedGranularity)
        val recurring = recurringCosts(rules, categories)
        val (recMonthly, recYearly) = recurringTotals(rules, RecurringKind.EXPENSE.name)
        val trend = expenseTrend(buckets)
        val comparison = monthComparison(expenses, incomes, today)
        val savings = incomeCents - expenseCents
        val rate = if (incomeCents > 0) savings.toFloat() / incomeCents.toFloat() * 100f else 0f

        val result = AnalyticsResult(
            range = range,
            label = label(period, today, custom),
            granularity = usedGranularity,
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            transactionCount = filteredExpenses.size + filteredIncomes.size,
            buckets = buckets,
            expenseCategories = expenseCategoryStats(expenses, categories, range, filter),
            incomeCategories = incomeCategoryStats(incomes, categories, range, filter),
            fixedCents = fixed,
            variableCents = variable,
            highestExpense = filteredExpenses.maxByOrNull { it.amountCents },
            highestIncome = filteredIncomes.maxByOrNull { it.amountCents },
            recurring = recurring,
            recurringMonthlyCents = recMonthly,
            recurringYearlyCents = recYearly,
            savingsCents = savings,
            savingsRate = rate,
            trend = trend,
            comparison = comparison,
            insights = emptyList()
        )
        return result.copy(
            insights = insights(result, comparison, budgetLimitCents, budgetEnabled, symbol)
        )
    }

    /** Total days inside the range (used for daily averages). */
    fun dayCount(range: DateRange): Long =
        ChronoUnit.DAYS.between(Dates.fromEpochDay(range.from), Dates.fromEpochDay(range.to)) + 1

    /**
     * Neutral result without any data.
     * Used as a safety net: if a calculation ever fails (corrupt single
     * record, impossible date, ...) the screen shows an empty state instead of
     * taking the whole app down.
     */
    fun empty(today: LocalDate = Dates.today()): AnalyticsResult {
        val day = today.toEpochDay()
        return AnalyticsResult(
            range = DateRange(day, day),
            label = Dates.monthYear(today),
            granularity = Granularity.MONTHLY,
            incomeCents = 0L,
            expenseCents = 0L,
            transactionCount = 0,
            buckets = emptyList(),
            expenseCategories = emptyList(),
            incomeCategories = emptyList(),
            fixedCents = 0L,
            variableCents = 0L,
            highestExpense = null,
            highestIncome = null,
            recurring = emptyList(),
            recurringMonthlyCents = 0L,
            recurringYearlyCents = 0L,
            savingsCents = 0L,
            savingsRate = 0f,
            trend = TrendStats(null, null, 0L, 0L, 0f),
            comparison = MonthComparison(
                currentLabel = Dates.monthYear(today),
                previousLabel = Dates.monthYear(today.minusMonths(1)),
                currentIncomeCents = 0L,
                previousIncomeCents = 0L,
                currentExpenseCents = 0L,
                previousExpenseCents = 0L,
                incomeDeltaCents = 0L,
                expenseDeltaCents = 0L,
                balanceDeltaCents = 0L,
                incomeDeltaPercent = null,
                expenseDeltaPercent = null
            ),
            insights = emptyList()
        )
    }

    /** Never throws: calculation errors fall back to [empty]. */
    fun safe(
        period: AnalyticsPeriod,
        today: LocalDate,
        custom: Pair<LocalDate, LocalDate>?,
        granularity: Granularity?,
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        categories: List<CategoryEntity>,
        rules: List<RecurringRuleEntity>,
        budgetEnabled: Boolean,
        budgetLimitCents: Long,
        filter: AnalyticsFilter = AnalyticsFilter(),
        symbol: String = "€"
    ): AnalyticsResult = runCatching {
        compute(
            period = period, today = today, custom = custom, granularity = granularity,
            expenses = expenses, incomes = incomes, categories = categories, rules = rules,
            budgetEnabled = budgetEnabled, budgetLimitCents = budgetLimitCents,
            filter = filter, symbol = symbol
        )
    }.getOrElse { empty(today) }
}

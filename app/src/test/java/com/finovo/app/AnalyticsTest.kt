package com.finovo.app

import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.Frequency
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.export.Export
import com.finovo.app.util.Analytics
import com.finovo.app.util.AnalyticsFilter
import com.finovo.app.util.AnalyticsPeriod
import com.finovo.app.util.DateRange
import com.finovo.app.util.Dates
import com.finovo.app.util.FixedFilter
import com.finovo.app.util.Granularity
import com.finovo.app.util.Money
import com.finovo.app.util.PaidFilter
import com.finovo.app.util.RecurrenceFilter
import com.finovo.app.util.TypeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Tests for the advanced income & expense analytics. */
class AnalyticsTest {

    private val today = LocalDate.of(2026, 9, 14)

    private val categories = listOf(
        CategoryEntity(1, "Miete", "🏠", CategoryType.EXPENSE.name, isFixed = true),
        CategoryEntity(2, "Lebensmittel", "🛒", CategoryType.EXPENSE.name),
        CategoryEntity(3, "Auto", "🚗", CategoryType.EXPENSE.name),
        CategoryEntity(4, "Gehalt", "💼", CategoryType.INCOME.name),
        CategoryEntity(5, "Kindergeld", "👨‍👩‍👧", CategoryType.INCOME.name)
    )

    private fun expenses() = listOf(
        ExpenseEntity(1, 90000, 1, LocalDate.of(2026, 9, 1).toEpochDay(), "Miete September"),
        ExpenseEntity(2, 40000, 2, LocalDate.of(2026, 9, 5).toEpochDay(), "Einkauf", paid = false),
        ExpenseEntity(3, 35000, 3, LocalDate.of(2026, 9, 8).toEpochDay(), "Tanken", recurringId = 9),
        ExpenseEntity(4, 50000, 2, LocalDate.of(2026, 8, 12).toEpochDay(), "August"),
        ExpenseEntity(5, 70000, 1, LocalDate.of(2026, 8, 1).toEpochDay(), "Miete August")
    )

    private fun incomes() = listOf(
        IncomeEntity(1, 320000, 4, LocalDate.of(2026, 9, 1).toEpochDay(), "Lohn"),
        IncomeEntity(2, 25000, 5, LocalDate.of(2026, 9, 3).toEpochDay(), "Kindergeld"),
        IncomeEntity(3, 320000, 4, LocalDate.of(2026, 8, 1).toEpochDay(), "Lohn")
    )

    private fun rules() = listOf(
        RecurringRuleEntity(
            id = 1, kind = RecurringKind.EXPENSE.name, name = "Miete", amountCents = 90000,
            categoryId = 1, frequency = Frequency.MONTHLY.name, interval = 1,
            startDate = LocalDate.of(2026, 1, 1).toEpochDay(), active = true
        ),
        RecurringRuleEntity(
            id = 2, kind = RecurringKind.EXPENSE.name, name = "Versicherung", amountCents = 105600,
            categoryId = 3, frequency = Frequency.YEARLY.name, interval = 1,
            startDate = LocalDate.of(2026, 3, 1).toEpochDay(), active = true
        )
    )

    private fun result(granularity: Granularity? = null, filter: AnalyticsFilter = AnalyticsFilter()) =
        Analytics.compute(
            period = AnalyticsPeriod.THIS_MONTH,
            today = today,
            custom = null,
            granularity = granularity,
            expenses = expenses(),
            incomes = incomes(),
            categories = categories,
            rules = rules(),
            budgetEnabled = true,
            budgetLimitCents = 200000,
            filter = filter
        )

    // ------------------------------------------------------------ periods ----

    @Test
    fun periods_resolveCorrectRanges() {
        val month = Analytics.resolveRange(AnalyticsPeriod.THIS_MONTH, today)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), month.from)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), month.to)

        val lastMonth = Analytics.resolveRange(AnalyticsPeriod.LAST_MONTH, today)
        assertEquals(LocalDate.of(2026, 8, 1).toEpochDay(), lastMonth.from)
        assertEquals(LocalDate.of(2026, 8, 31).toEpochDay(), lastMonth.to)

        val three = Analytics.resolveRange(AnalyticsPeriod.LAST_3_MONTHS, today)
        assertEquals(LocalDate.of(2026, 7, 1).toEpochDay(), three.from)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), three.to)

        val six = Analytics.resolveRange(AnalyticsPeriod.LAST_6_MONTHS, today)
        assertEquals(LocalDate.of(2026, 4, 1).toEpochDay(), six.from)

        val lastYear = Analytics.resolveRange(AnalyticsPeriod.LAST_YEAR, today)
        assertEquals(LocalDate.of(2025, 1, 1).toEpochDay(), lastYear.from)
        assertEquals(LocalDate.of(2025, 12, 31).toEpochDay(), lastYear.to)

        val custom = Analytics.resolveRange(
            AnalyticsPeriod.CUSTOM, today,
            LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 3, 31)
        )
        assertEquals(LocalDate.of(2026, 2, 1).toEpochDay(), custom.from)
        assertEquals(LocalDate.of(2026, 3, 31).toEpochDay(), custom.to)
    }

    // ---------------------------------------------------------- overview -----

    @Test
    fun overview_totalsSavingsAndRate() {
        val r = result()
        assertEquals(345000L, r.incomeCents)      // 320.000 + 25.000
        assertEquals(165000L, r.expenseCents)     // 90.000 + 40.000 + 35.000
        assertEquals(180000L, r.balanceCents)
        assertEquals(180000L, r.savingsCents)
        // 180.000 / 345.000 = 52,17 %
        assertTrue(kotlin.math.abs(r.savingsRate - 52.1739f) < 0.01f)
        assertEquals(5, r.transactionCount) // 3 expenses + 2 incomes in September
    }

    @Test
    fun overview_usesOnlyRealTransactions() {
        val r = result()
        // August bookings must not leak into September
        assertEquals(165000L, r.expenseCents)
        assertTrue(r.expenseCategories.sumOf { it.cents } == 165000L)
    }

    // ----------------------------------------------------------- buckets -----

    @Test
    fun buckets_dailyGranularityKeepsEveryDay() {
        val r = result(Granularity.DAILY)
        assertEquals(30, r.buckets.size)
        val first = r.buckets.first()
        assertEquals(LocalDate.of(2026, 9, 1), first.start)
        assertEquals(90000L, first.expenseCents)
        assertEquals(320000L, first.incomeCents)
        val day5 = r.buckets[4]
        assertEquals(40000L, day5.expenseCents)
    }

    @Test
    fun buckets_monthlyGranularityAcrossSixMonths() {
        val range = Analytics.resolveRange(AnalyticsPeriod.LAST_6_MONTHS, today)
        val buckets = Analytics.buildBuckets(expenses(), incomes(), range, Granularity.MONTHLY)
        assertEquals(6, buckets.size)
        assertEquals("Apr.", buckets[0].label)
        val august = buckets.first { it.label == "Aug." }
        assertEquals(120000L, august.expenseCents)
        assertEquals(320000L, august.incomeCents)
        assertEquals(200000L, august.netCents)
    }

    @Test
    fun buckets_yearlyAndWeekly() {
        val range = DateRange(
            LocalDate.of(2024, 1, 1).toEpochDay(),
            LocalDate.of(2026, 12, 31).toEpochDay()
        )
        val yearly = Analytics.buildBuckets(expenses(), incomes(), range, Granularity.YEARLY)
        assertEquals(3, yearly.size)
        assertEquals("2026", yearly.last().label)

        val weekRange = Analytics.resolveRange(AnalyticsPeriod.THIS_WEEK, today)
        val weekly = Analytics.buildBuckets(expenses(), incomes(), weekRange, Granularity.WEEKLY)
        assertTrue(weekly.isNotEmpty())
        assertTrue(weekly.first().label.startsWith("KW"))
    }

    // -------------------------------------------------------- categories -----

    @Test
    fun categories_rankedWithPercentages() {
        val r = result()
        assertEquals("Miete", r.expenseCategories[0].name)
        assertEquals(90000L, r.expenseCategories[0].cents)
        assertEquals("Lebensmittel", r.expenseCategories[1].name)
        assertEquals(40000L, r.expenseCategories[1].cents)
        val sum = r.expenseCategories.sumOf { it.percent.toDouble() }
        assertTrue(kotlin.math.abs(sum - 1.0) < 0.0001)
        // 90.000 / 165.000 = 54,5 %
        assertTrue(kotlin.math.abs(r.expenseCategories[0].percent - 0.5454f) < 0.01f)
    }

    @Test
    fun categories_incomeSources() {
        val r = result()
        assertEquals("Gehalt", r.incomeCategories[0].name)
        assertEquals(320000L, r.incomeCategories[0].cents)
        assertEquals("Kindergeld", r.incomeCategories[1].name)
        assertTrue(kotlin.math.abs(r.incomeCategories[0].percent - 0.9275f) < 0.01f)
    }

    // --------------------------------------------------- fixed vs variable ---

    @Test
    fun fixedVariable_usesCategoryAndOverride() {
        val plain = result()
        assertEquals(90000L, plain.fixedCents)
        assertEquals(75000L, plain.variableCents)

        // explicit override wins over the category default
        val overridden = expenses().toMutableList().also { list ->
            list[2] = list[2].copy(fixedOverride = true) // Auto -> fixed
        }
        val r = Analytics.compute(
            period = AnalyticsPeriod.THIS_MONTH,
            today = today,
            custom = null,
            granularity = null,
            expenses = overridden,
            incomes = incomes(),
            categories = categories,
            rules = rules(),
            budgetEnabled = false,
            budgetLimitCents = 0
        )
        assertEquals(125000L, r.fixedCents)
        assertEquals(40000L, r.variableCents)

        assertTrue(Analytics.isFixedExpense(expenses()[0], categories))
        assertFalse(Analytics.isFixedExpense(expenses()[2], categories))
        assertTrue(Analytics.isFixedExpense(overridden[2], categories))
    }

    // ---------------------------------------------------------- recurring ----

    @Test
    fun recurring_normalisesToMonthlyAndYearly() {
        val r = result()
        // 90.000 monthly + 105.600 yearly (= 8.800 per month)
        assertEquals(98800L, r.recurringMonthlyCents)
        assertEquals(1185600L, r.recurringYearlyCents)
        assertEquals("Miete", r.recurring[0].name)

        assertEquals(90000L, Analytics.monthlyAmount(90000, Frequency.MONTHLY, 1))
        assertEquals(45000L, Analytics.monthlyAmount(90000, Frequency.MONTHLY, 2))
        assertEquals(8800L, Analytics.monthlyAmount(105600, Frequency.YEARLY, 1))
        // weekly 100 € ≈ 433,33 € per month
        assertEquals(43333L, Analytics.monthlyAmount(10000, Frequency.WEEKLY, 1))
        // daily 10 € ≈ 304,16 € per month
        assertEquals(30416L, Analytics.monthlyAmount(1000, Frequency.DAILY, 1))
    }

    // ------------------------------------------------------------- trends ----

    @Test
    fun trend_highestLowestAverageAndDirection() {
        val range = Analytics.resolveRange(AnalyticsPeriod.LAST_6_MONTHS, today)
        val buckets = Analytics.buildBuckets(expenses(), incomes(), range, Granularity.MONTHLY)
        val trend = Analytics.expenseTrend(buckets)
        assertEquals(165000L, trend.highest?.expenseCents)   // September
        // Months without bookings are not counted as "lowest"
        assertEquals(120000L, trend.lowest?.expenseCents)
        // (120.000 + 165.000) over 6 months = 47.500
        assertEquals(47500L, trend.averageCents)
    }

    @Test
    fun monthComparison_deltasAndPercentages() {
        val comparison = Analytics.monthComparison(expenses(), incomes(), today)
        assertEquals(345000L, comparison.currentIncomeCents)
        assertEquals(320000L, comparison.previousIncomeCents)
        assertEquals(25000L, comparison.incomeDeltaCents)
        assertEquals(165000L, comparison.currentExpenseCents)
        assertEquals(120000L, comparison.previousExpenseCents)
        assertEquals(45000L, comparison.expenseDeltaCents)
        assertEquals(-20000L, comparison.balanceDeltaCents)
        // +45.000 / 120.000 = +37,5 %
        assertTrue(kotlin.math.abs(comparison.expenseDeltaPercent!! - 37.5f) < 0.01f)
    }

    @Test
    fun percentChange_handlesZeroBase() {
        assertEquals(50f, Analytics.percentChange(100, 150)!!)
        assertEquals(-50f, Analytics.percentChange(100, 50)!!)
        assertEquals(null, Analytics.percentChange(0, 100))
    }

    // ------------------------------------------------------------ filters ----

    @Test
    fun filters_combineCorrectly() {
        val base = result()
        assertEquals(165000L, base.expenseCents)

        // only Auto
        val onlyAuto = result(filter = AnalyticsFilter(categoryIds = setOf(3)))
        assertEquals(35000L, onlyAuto.expenseCents)

        // unpaid only
        val unpaid = result(filter = AnalyticsFilter(paid = PaidFilter.UNPAID))
        assertEquals(40000L, unpaid.expenseCents)

        // fixed only
        val fixed = result(filter = AnalyticsFilter(fixed = FixedFilter.FIXED))
        assertEquals(90000L, fixed.expenseCents)

        // variable only
        val variable = result(filter = AnalyticsFilter(fixed = FixedFilter.VARIABLE))
        assertEquals(75000L, variable.expenseCents)

        // recurring only
        val recurring = result(filter = AnalyticsFilter(recurring = RecurrenceFilter.RECURRING))
        assertEquals(35000L, recurring.expenseCents)

        // one time only
        val once = result(filter = AnalyticsFilter(recurring = RecurrenceFilter.ONE_TIME))
        assertEquals(130000L, once.expenseCents)

        // income only / expense only
        val incomeOnly = result(filter = AnalyticsFilter(type = TypeFilter.INCOME))
        assertEquals(0L, incomeOnly.expenseCents)
        assertEquals(345000L, incomeOnly.incomeCents)
        val expenseOnly = result(filter = AnalyticsFilter(type = TypeFilter.EXPENSE))
        assertEquals(0L, expenseOnly.incomeCents)
    }

    @Test
    fun filters_describeThemselves() {
        val text = AnalyticsFilter(
            categoryIds = setOf(3),
            type = TypeFilter.EXPENSE,
            fixed = FixedFilter.VARIABLE,
            paid = PaidFilter.PAID,
            recurring = RecurrenceFilter.RECURRING
        ).describe(categories)
        assertTrue(text.contains("Auto"))
        assertTrue(text.contains("nur Ausgaben"))
        assertTrue(text.contains("variable Kosten"))
        assertTrue(text.contains("bezahlt"))
        assertTrue(text.contains("wiederkehrend"))
        assertFalse(AnalyticsFilter().isActive)
    }

    // ---------------------------------------------------------- drilldown ----

    @Test
    fun drillDown_returnsUnderlyingTransactions() {
        val r = result()
        val items = Analytics.drillDown(
            expenses = expenses(),
            incomes = incomes(),
            categories = categories,
            categoryId = 2,
            range = r.range
        )
        assertEquals(1, items.size)
        assertEquals(40000L, items[0].amountCents)
        assertEquals("Lebensmittel", items[0].title)
        assertFalse(items[0].isIncome)
        assertEquals("Einkauf", items[0].note)

        val incomeItems = Analytics.drillDown(
            expenses = expenses(),
            incomes = incomes(),
            categories = categories,
            categoryId = 4,
            range = r.range
        )
        assertEquals(1, incomeItems.size)
        assertTrue(incomeItems[0].isIncome)
    }

    // ----------------------------------------------------------- insights ----

    @Test
    fun insights_areGeneratedFromRealData() {
        val r = result()
        assertTrue(r.insights.isNotEmpty())
        val joined = r.insights.joinToString(" ") { it.text }
        assertTrue(joined.contains("Miete"))
        assertTrue(joined.contains("Einnahmen"))
        assertTrue(joined.contains("Budget"))
    }

    @Test
    fun insights_emptyWhenNoData() {
        val r = Analytics.compute(
            period = AnalyticsPeriod.THIS_MONTH,
            today = today,
            custom = null,
            granularity = null,
            expenses = emptyList(),
            incomes = emptyList(),
            categories = categories,
            rules = emptyList(),
            budgetEnabled = false,
            budgetLimitCents = 0
        )
        assertEquals(0L, r.expenseCents)
        assertTrue(r.insights.isEmpty())
        assertTrue(r.buckets.isNotEmpty()) // empty buckets still show the period
        assertEquals(0L, r.buckets.sumOf { it.expenseCents })
    }

    // ------------------------------------------------------------- export ----

    @Test
    fun analyticsExport_containsAllSections() {
        val r = result()
        val request = Export.AnalyticsRequest(
            label = r.label,
            granularityLabel = "Monat",
            incomeCents = r.incomeCents,
            expenseCents = r.expenseCents,
            savingsCents = r.savingsCents,
            savingsRate = r.savingsRate,
            fixedCents = r.fixedCents,
            variableCents = r.variableCents,
            buckets = r.buckets,
            expenseCategories = r.expenseCategories,
            incomeCategories = r.incomeCategories,
            recurring = r.recurring,
            recurringMonthlyCents = r.recurringMonthlyCents,
            recurringYearlyCents = r.recurringYearlyCents,
            comparison = r.comparison,
            insights = r.insights,
            budgetEnabled = true,
            budgetLimitCents = 200000,
            filterText = "",
            symbol = "€"
        )
        val csv = Export.analyticsCsv(request)
        assertTrue(csv.contains("KENNZAHLEN"))
        assertTrue(csv.contains("VERLAUF"))
        assertTrue(csv.contains("AUSGABEN NACH KATEGORIE"))
        assertTrue(csv.contains("EINNAHMEN NACH QUELLE"))
        assertTrue(csv.contains("WIEDERKEHRENDE KOSTEN"))
        assertTrue(csv.contains("VERGLEICH VORMONAT"))
        assertTrue(csv.contains("ERKENNTNISSE"))
        assertTrue(csv.contains("Miete"))

        val summary = Export.analyticsSummary(request)
        assertTrue(summary.contains("Finanzanalyse"))
        assertTrue(summary.contains("Sparquote"))
        assertTrue(summary.contains("Top-Kategorien"))
    }

    // ------------------------------------------------------------- labels ----

    @Test
    fun labels_areGerman() {
        assertEquals("Heute", AnalyticsPeriod.TODAY.label())
        assertEquals("Letzte 3 Monate", AnalyticsPeriod.LAST_3_MONTHS.label())
        assertEquals("September 2026", Analytics.label(AnalyticsPeriod.THIS_MONTH, today))
        assertEquals("August 2026", Analytics.label(AnalyticsPeriod.LAST_MONTH, today))
        assertEquals(Granularity.DAILY, Analytics.defaultGranularity(AnalyticsPeriod.THIS_MONTH))
        assertEquals(Granularity.MONTHLY, Analytics.defaultGranularity(AnalyticsPeriod.THIS_YEAR))
    }

    @Test
    fun moneyUsedInsideAnalyticsStaysExact() {
        val r = result()
        assertEquals("1.650,00 €", Money.format(r.expenseCents))
        assertEquals("3.450,00 €", Money.format(r.incomeCents))
        assertEquals("1.800,00 €", Money.format(r.balanceCents))
        assertEquals(30, Analytics.dayCount(r.range))
    }
}

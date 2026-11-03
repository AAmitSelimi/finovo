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
import com.finovo.app.util.PaidFilter
import com.finovo.app.util.RecurrenceFilter
import com.finovo.app.util.TypeFilter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Robustness matrix for Finanzanalyse: the analysis must never throw, no
 * matter how little, how much or how broken the stored data is.
 */
class AnalyticsRobustnessTest {

    private val today = LocalDate.of(2026, 9, 17)

    private val categories = listOf(
        CategoryEntity(1, "Miete", "🏠", CategoryType.EXPENSE.name, isFixed = true),
        CategoryEntity(2, "Lebensmittel", "🛒", CategoryType.EXPENSE.name),
        CategoryEntity(4, "Gehalt", "💼", CategoryType.INCOME.name)
    )

    private fun expense(
        id: Long, cents: Long, cat: Long = 2, date: LocalDate = today, paid: Boolean = true,
        fixed: Boolean? = null, recurring: Long? = null
    ) = ExpenseEntity(id, cents, cat, date.toEpochDay(), "Nr. $id", paid, fixed, 0, "", recurring)

    private fun income(
        id: Long, cents: Long, cat: Long = 4, date: LocalDate = today, recurring: Long? = null
    ) = IncomeEntity(id, cents, cat, date.toEpochDay(), "In $id", recurring)

    // ------------------------------------------------------------- datasets ---

    private val datasets: Map<String, Triple<List<ExpenseEntity>, List<IncomeEntity>, List<RecurringRuleEntity>>> =
        mapOf(
            "empty" to Triple(emptyList(), emptyList(), emptyList()),
            "onlyExpenses" to Triple(listOf(expense(1, 5000)), emptyList(), emptyList()),
            "onlyIncome" to Triple(emptyList(), listOf(income(1, 250000)), emptyList()),
            "zeroAmounts" to Triple(
                listOf(expense(1, 0), expense(2, 0)), listOf(income(1, 0)), emptyList()
            ),
            "negativeAmounts" to Triple(
                listOf(expense(1, -5000)), listOf(income(1, -1000)), emptyList()
            ),
            "hugeAmounts" to Triple(
                listOf(expense(1, Long.MAX_VALUE / 4)), listOf(income(1, Long.MAX_VALUE / 4)),
                emptyList()
            ),
            "singleDay" to Triple(
                listOf(expense(1, 1234, date = today)), listOf(income(1, 999, date = today)),
                emptyList()
            ),
            "unknownCategory" to Triple(
                listOf(expense(1, 5000, cat = 999)), listOf(income(1, 100, cat = 999)), emptyList()
            ),
            "dateInFuture" to Triple(
                listOf(expense(1, 5000, date = LocalDate.of(2099, 12, 31))), emptyList(), emptyList()
            ),
            "dateInPast" to Triple(
                listOf(expense(1, 5000, date = LocalDate.of(1970, 1, 1))), emptyList(), emptyList()
            ),
            "leapDay" to Triple(
                listOf(expense(1, 5000, date = LocalDate.of(2024, 2, 29))), emptyList(), emptyList()
            ),
            "manyEntries" to Triple(
                (1..500).map { expense(it.toLong(), it * 137L, date = today.minusDays((it % 60).toLong())) },
                (1..200).map { income(it.toLong(), it * 911L, date = today.minusDays((it % 90).toLong())) },
                emptyList()
            ),
            "unpaidOnly" to Triple(
                listOf(expense(1, 5000, paid = false)), emptyList(), emptyList()
            ),
            "recurringZeroInterval" to Triple(
                emptyList(), emptyList(),
                listOf(
                    RecurringRuleEntity(
                        1, RecurringKind.EXPENSE.name, "Miete", 90000, 1, "",
                        Frequency.CUSTOM.name, interval = 0, customDays = 0,
                        startDate = today.toEpochDay(), active = true
                    )
                )
            ),
            "recurringInactive" to Triple(
                emptyList(), emptyList(),
                listOf(
                    RecurringRuleEntity(
                        1, RecurringKind.EXPENSE.name, "Alt", 90000, 1, "",
                        Frequency.MONTHLY.name, interval = 1,
                        startDate = today.toEpochDay(), active = false
                    )
                )
            ),
            "recurringBrokenFrequency" to Triple(
                emptyList(), emptyList(),
                listOf(
                    RecurringRuleEntity(
                        1, RecurringKind.EXPENSE.name, "??", 90000, 1, "",
                        "NOT_A_FREQUENCY", interval = 1,
                        startDate = today.toEpochDay(), active = true
                    )
                )
            )
        )

    private val periods = listOf(
        AnalyticsPeriod.TODAY, AnalyticsPeriod.THIS_WEEK, AnalyticsPeriod.THIS_MONTH,
        AnalyticsPeriod.LAST_MONTH, AnalyticsPeriod.LAST_3_MONTHS, AnalyticsPeriod.LAST_6_MONTHS,
        AnalyticsPeriod.THIS_YEAR, AnalyticsPeriod.LAST_YEAR
    )

    private val granularities =
        listOf(null) + Granularity.values().toList()

    private val filters = listOf(
        AnalyticsFilter(),
        AnalyticsFilter(type = TypeFilter.INCOME),
        AnalyticsFilter(type = TypeFilter.EXPENSE),
        AnalyticsFilter(fixed = FixedFilter.FIXED),
        AnalyticsFilter(fixed = FixedFilter.VARIABLE),
        AnalyticsFilter(paid = PaidFilter.PAID),
        AnalyticsFilter(paid = PaidFilter.UNPAID),
        AnalyticsFilter(recurring = RecurrenceFilter.RECURRING),
        AnalyticsFilter(recurring = RecurrenceFilter.ONE_TIME),
        AnalyticsFilter(categoryIds = setOf(1L, 2L, 4L)),
        AnalyticsFilter(categoryIds = setOf(4711L))
    )

    private fun run(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            throw AssertionError("CRASH in dataset '$name': ${t.javaClass.simpleName}: ${t.message}", t)
        }
    }

    @Test
    fun compute_neverThrowsForAnyDatasetPeriodAndGranularity() {
        datasets.forEach { (name, data) ->
            (periods + AnalyticsPeriod.CUSTOM).forEach { period ->
                granularities.forEach { gran ->
                    run("$name/$period/$gran") {
                        Analytics.compute(
                            period = period,
                            today = today,
                            custom = if (period == AnalyticsPeriod.CUSTOM)
                                LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 6, 30) else null,
                            granularity = gran,
                            expenses = data.first,
                            incomes = data.second,
                            categories = categories,
                            rules = data.third,
                            budgetEnabled = true,
                            budgetLimitCents = 0
                        )
                    }
                }
            }
        }
    }

    @Test
    fun compute_customPeriodWithoutRangeAndReversedRange() {
        datasets.forEach { (name, data) ->
            run("$name/custom-null") {
                Analytics.compute(
                    period = AnalyticsPeriod.CUSTOM, today = today, custom = null,
                    granularity = null, expenses = data.first, incomes = data.second,
                    categories = categories, rules = data.third,
                    budgetEnabled = false, budgetLimitCents = 0
                )
            }
            run("$name/custom-reversed") {
                Analytics.compute(
                    period = AnalyticsPeriod.CUSTOM,
                    today = today,
                    custom = LocalDate.of(2026, 12, 31) to LocalDate.of(2026, 1, 1),
                    granularity = Granularity.DAILY, expenses = data.first,
                    incomes = data.second, categories = categories, rules = data.third,
                    budgetEnabled = false, budgetLimitCents = 0
                )
            }
            run("$name/custom-same-day") {
                Analytics.compute(
                    period = AnalyticsPeriod.CUSTOM,
                    today = today,
                    custom = today to today,
                    granularity = Granularity.DAILY, expenses = data.first,
                    incomes = data.second, categories = categories, rules = data.third,
                    budgetEnabled = false, budgetLimitCents = 0
                )
            }
        }
    }

    @Test
    fun compute_withoutCategoriesAndWithFilters() {
        datasets.forEach { (name, data) ->
            filters.forEach { filter ->
                run("$name/filter-${filter.type}-${filter.fixed}-${filter.paid}-${filter.recurring}") {
                    Analytics.compute(
                        period = AnalyticsPeriod.THIS_MONTH, today = today, custom = null,
                        granularity = null, expenses = data.first, incomes = data.second,
                        categories = emptyList(), rules = data.third,
                        budgetEnabled = true, budgetLimitCents = 100000, filter = filter
                    )
                }
            }
        }
    }

    @Test
    fun compute_negativeAndZeroBudgetLimits() {
        listOf(0L, -1L, -100000L, 1L).forEach { limit ->
            datasets.forEach { (name, data) ->
                run("$name/budget-$limit") {
                    Analytics.compute(
                        period = AnalyticsPeriod.THIS_MONTH, today = today, custom = null,
                        granularity = null, expenses = data.first, incomes = data.second,
                        categories = categories, rules = data.third,
                        budgetEnabled = true, budgetLimitCents = limit
                    )
                }
            }
        }
    }

    @Test
    fun drillDown_neverThrows() {
        datasets.forEach { (name, data) ->
            run("$name/drill") {
                Analytics.drillDown(
                    expenses = data.first,
                    incomes = data.second,
                    categories = emptyList(),
                    categoryId = 0L,
                    range = DateRange(today.toEpochDay(), today.toEpochDay())
                )
            }
        }
    }

    @Test
    fun export_neverThrowsWithEmptyResult() {
        val empty = Analytics.compute(
            period = AnalyticsPeriod.THIS_MONTH, today = today, custom = null,
            granularity = null, expenses = emptyList(), incomes = emptyList(),
            categories = emptyList(), rules = emptyList(),
            budgetEnabled = false, budgetLimitCents = 0
        )
        val request = Export.AnalyticsRequest(
            label = empty.label,
            granularityLabel = "Monat",
            incomeCents = empty.incomeCents,
            expenseCents = empty.expenseCents,
            savingsCents = empty.savingsCents,
            savingsRate = empty.savingsRate,
            fixedCents = empty.fixedCents,
            variableCents = empty.variableCents,
            buckets = empty.buckets,
            expenseCategories = empty.expenseCategories,
            incomeCategories = empty.incomeCategories,
            recurring = empty.recurring,
            recurringMonthlyCents = empty.recurringMonthlyCents,
            recurringYearlyCents = empty.recurringYearlyCents,
            comparison = empty.comparison,
            insights = empty.insights,
            budgetEnabled = false,
            budgetLimitCents = 0,
            filterText = "",
            symbol = "\u20AC"
        )
        run("export-csv") { assertTrue(Export.analyticsCsv(request).isNotEmpty()) }
        run("export-summary") { assertTrue(Export.analyticsSummary(request).isNotEmpty()) }
    }

    @Test
    fun safeNeverThrowsAndFallsBackToEmptyResult() {
        datasets.forEach { (name, data) ->
            (periods + AnalyticsPeriod.CUSTOM).forEach { period ->
                run("$name/safe/$period") {
                    val r = Analytics.safe(
                        period = period, today = today,
                        custom = if (period == AnalyticsPeriod.CUSTOM)
                            LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 6, 30) else null,
                        granularity = Granularity.DAILY,
                        expenses = data.first, incomes = data.second,
                        categories = categories, rules = data.third,
                        budgetEnabled = true, budgetLimitCents = 5000
                    )
                    assertTrue(r.buckets.isNotEmpty())
                    assertTrue(r.range.from <= r.range.to)
                }
            }
        }
    }

    @Test
    fun rawLocalDateThrowsForCorruptValues() {
        // Beweis: genau hier ist die App abgestuerzt (DateTimeException).
        var thrown = false
        try {
            LocalDate.ofEpochDay(Long.MAX_VALUE)
        } catch (t: Exception) {
            thrown = true
        }
        assertTrue("LocalDate.ofEpochDay(Long.MAX_VALUE) muss werfen", thrown)
        assertTrue("Dates.fromEpochDay darf nie werfen", Dates.full(Dates.fromEpochDay(Long.MAX_VALUE)).isNotBlank())
    }

    @Test
    fun corruptDatesNeverThrow() {
        val broken = listOf(
            expense(1, 5000, date = LocalDate.of(2026, 9, 1)).copy(date = Long.MAX_VALUE),
            expense(2, 5000).copy(date = Long.MIN_VALUE),
            expense(3, 5000).copy(date = 9_999_999_999_999_999L),
            expense(4, 5000).copy(date = -9_999_999_999_999_999L)
        )
        periods.forEach { period ->
            run("corruptDates/$period") {
                val r = Analytics.compute(
                    period = period, today = today, custom = null, granularity = null,
                    expenses = broken, incomes = emptyList(), categories = categories,
                    rules = emptyList(), budgetEnabled = false, budgetLimitCents = 0
                )
                r.buckets.forEach { b ->
                    assertTrue(Dates.full(b.start).isNotBlank())
                    assertTrue(Dates.full(b.end).isNotBlank())
                }
            }
        }
        run("corruptDates/format") {
            assertTrue(Dates.full(Dates.fromEpochDay(Long.MAX_VALUE)).isNotBlank())
            assertTrue(Dates.full(Dates.fromEpochDay(Long.MIN_VALUE)).isNotBlank())
            assertTrue(Dates.short(Dates.fromEpochDay(0L)).isNotBlank())
        }
    }

    @Test
    fun emptyResultIsUsable() {
        val r = Analytics.empty(today)
        assertTrue(!r.hasData)
        assertTrue(r.buckets.isEmpty())
        assertTrue(r.insights.isEmpty())
        assertTrue(r.trend.highest == null)
        assertTrue(r.comparison.incomeDeltaPercent == null)
        assertTrue(r.range.from == today.toEpochDay())
    }

    @Test
    fun hasDataFlagMatchesContent() {
        val empty = Analytics.compute(
            period = AnalyticsPeriod.THIS_MONTH, today = today, custom = null,
            granularity = null, expenses = emptyList(), incomes = emptyList(),
            categories = emptyList(), rules = emptyList(),
            budgetEnabled = false, budgetLimitCents = 0
        )
        assertTrue(!empty.hasData)
        val withData = Analytics.compute(
            period = AnalyticsPeriod.THIS_MONTH, today = today, custom = null,
            granularity = null, expenses = listOf(expense(1, 500)),
            incomes = emptyList(), categories = categories, rules = emptyList(),
            budgetEnabled = false, budgetLimitCents = 0
        )
        assertTrue(withData.hasData)
    }

    @Test
    fun emptyDatasetProducesSafeValues() {
        val r = Analytics.compute(
            period = AnalyticsPeriod.THIS_MONTH, today = today, custom = null,
            granularity = null, expenses = emptyList(), incomes = emptyList(),
            categories = emptyList(), rules = emptyList(),
            budgetEnabled = false, budgetLimitCents = 0
        )
        assertTrue(r.transactionCount == 0)
        assertTrue(r.savingsRate == 0f)
        assertTrue(r.trend.deltaPercent == 0f)
        assertTrue(r.expenseCategories.isEmpty())
        assertTrue(r.comparison.incomeDeltaPercent == null)
        assertTrue(Dates.full(today).isNotEmpty())
    }
}

package com.finovo.app.util

import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillPaymentEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryTotal
import com.finovo.app.data.CategoryType
import com.finovo.app.data.DateFilter
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.StatsPeriod
import java.time.LocalDate
import java.time.YearMonth
import com.finovo.app.util.str

/** A closed range of days, stored as epochDay values. */
data class DateRange(val from: Long, val to: Long) {
    fun contains(epochDay: Long): Boolean = epochDay in from..to
    fun monthCount(): Int = Dates.fromEpochDay(to).let { end ->
        Dates.fromEpochDay(from).let { start ->
            val months = (end.year - start.year) * 12 + (end.monthValue - start.monthValue) + 1
            months.coerceAtLeast(1)
        }
    }

    companion object {
        fun all() = DateRange(-365_000L, 365_000L)
    }
}

/** One row in the combined "recent activity" list. */
data class TxItem(
    val key: String,
    val isIncome: Boolean,
    val id: Long,
    val amountCents: Long,
    val title: String,
    val emoji: String,
    val date: LocalDate,
    val note: String,
    val paid: Boolean = true
)

data class DashboardData(
    val month: YearMonth,
    val monthLabel: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val availableCents: Long,
    val budgetEnabled: Boolean,
    val budgetLimitCents: Long,
    val budgetSpentCents: Long,
    val upcomingBills: List<BillEntity>,
    val recent: List<TxItem>,
    val openBillsCents: Long,
    val overdueCount: Int
) {
    val budgetUsed: Float
        get() = if (budgetLimitCents <= 0L) 0f
        else (budgetSpentCents.toFloat() / budgetLimitCents.toFloat()).coerceIn(0f, 1.5f)
    val budgetRemainingCents: Long get() = budgetLimitCents - budgetSpentCents
}

data class MonthPoint(
    val month: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long
)

data class StatisticsData(
    val label: String,
    val range: DateRange,
    val incomeCents: Long,
    val expenseCents: Long,
    val remainingCents: Long,
    val fixedCents: Long,
    val variableCents: Long,
    val upcomingBillsCents: Long,
    val paidBillsCents: Long,
    val outstandingBillsCents: Long,
    val topCategories: List<CategoryTotal>,
    val highestSingleExpense: ExpenseEntity?,
    val averageMonthlyExpenseCents: Long,
    val yearExpenseCents: Long,
    val yearIncomeCents: Long,
    val monthlySeries: List<MonthPoint>
)

object Stats {

    // ------------------------------ ranges ---------------------------------

    fun rangeFor(
        filter: DateFilter,
        today: LocalDate,
        custom: Pair<LocalDate, LocalDate>? = null
    ): DateRange = when (filter) {
        DateFilter.TODAY -> {
            val d = today.toEpochDay()
            DateRange(d, d)
        }
        DateFilter.WEEK -> DateRange(
            Dates.startOfWeek(today).toEpochDay(),
            Dates.endOfWeek(today).toEpochDay()
        )
        DateFilter.MONTH -> DateRange(
            Dates.startOfMonth(today).toEpochDay(),
            Dates.endOfMonth(today).toEpochDay()
        )
        DateFilter.YEAR -> DateRange(
            Dates.startOfYear(today).toEpochDay(),
            Dates.endOfYear(today).toEpochDay()
        )
        DateFilter.CUSTOM -> {
            val from = custom?.first ?: Dates.startOfMonth(today)
            val to = custom?.second ?: Dates.endOfMonth(today)
            if (from <= to) DateRange(from.toEpochDay(), to.toEpochDay())
            else DateRange(to.toEpochDay(), from.toEpochDay())
        }
        DateFilter.ALL -> DateRange.all()
    }

    fun rangeForPeriod(
        period: StatsPeriod,
        today: LocalDate,
        custom: Pair<LocalDate, LocalDate>? = null
    ): DateRange {
        val lastMonth = today.minusMonths(1)
        return when (period) {
            StatsPeriod.THIS_MONTH -> DateRange(
                Dates.startOfMonth(today).toEpochDay(),
                Dates.endOfMonth(today).toEpochDay()
            )
            StatsPeriod.LAST_MONTH -> DateRange(
                Dates.startOfMonth(lastMonth).toEpochDay(),
                Dates.endOfMonth(lastMonth).toEpochDay()
            )
            StatsPeriod.THIS_YEAR -> DateRange(
                Dates.startOfYear(today).toEpochDay(),
                Dates.endOfYear(today).toEpochDay()
            )
            StatsPeriod.LAST_12_MONTHS -> DateRange(
                Dates.startOfMonth(today.minusMonths(11)).toEpochDay(),
                Dates.endOfMonth(today).toEpochDay()
            )
            StatsPeriod.CUSTOM -> rangeFor(DateFilter.CUSTOM, today, custom)
        }
    }

    fun labelForPeriod(
        period: StatsPeriod,
        today: LocalDate,
        custom: Pair<LocalDate, LocalDate>? = null
    ): String = when (period) {
        StatsPeriod.THIS_MONTH -> Dates.monthYear(today)
        StatsPeriod.LAST_MONTH -> Dates.monthYear(today.minusMonths(1))
        StatsPeriod.THIS_YEAR -> "${today.year}"
        StatsPeriod.LAST_12_MONTHS -> str("last_12_months", "Letzte 12 Monate")
        StatsPeriod.CUSTOM -> custom?.let { "${Dates.full(it.first)} – ${Dates.full(it.second)}" }
            ?: Dates.monthYear(today)
    }

    // ------------------------------- sums ----------------------------------

    fun sumExpenses(expenses: List<ExpenseEntity>, range: DateRange): Long {
        var total = 0L
        for (e in expenses) if (range.contains(e.date)) total += e.amountCents
        return total
    }

    fun sumIncomes(incomes: List<IncomeEntity>, range: DateRange): Long {
        var total = 0L
        for (i in incomes) if (range.contains(i.date)) total += i.amountCents
        return total
    }

    fun filterExpenses(
        expenses: List<ExpenseEntity>,
        range: DateRange,
        query: String = "",
        categoryId: Long? = null,
        categoryNames: Map<Long, String> = emptyMap()
    ): List<ExpenseEntity> {
        val q = query.trim().lowercase()
        return expenses.filter { e ->
            range.contains(e.date) &&
                (categoryId == null || e.categoryId == categoryId) &&
                (q.isEmpty() ||
                    e.note.lowercase().contains(q) ||
                    (categoryNames[e.categoryId]?.lowercase()?.contains(q) == true) ||
                    Money.format(e.amountCents, "").contains(q))
        }.sortedWith(compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.id })
    }

    fun filterIncomes(
        incomes: List<IncomeEntity>,
        range: DateRange,
        query: String = "",
        categoryId: Long? = null,
        categoryNames: Map<Long, String> = emptyMap()
    ): List<IncomeEntity> {
        val q = query.trim().lowercase()
        return incomes.filter { i ->
            range.contains(i.date) &&
                (categoryId == null || i.categoryId == categoryId) &&
                (q.isEmpty() ||
                    i.note.lowercase().contains(q) ||
                    (categoryNames[i.categoryId]?.lowercase()?.contains(q) == true) ||
                    Money.format(i.amountCents, "").contains(q))
        }.sortedWith(compareByDescending<IncomeEntity> { it.date }.thenByDescending { it.id })
    }

    // ---------------------------- breakdowns --------------------------------

    fun categoryTotals(
        expenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        range: DateRange
    ): List<CategoryTotal> {
        val byId = categories.associateBy { it.id }
        val totals = LinkedHashMap<Long, Long>()
        for (e in expenses) {
            if (!range.contains(e.date)) continue
            totals[e.categoryId] = (totals[e.categoryId] ?: 0L) + e.amountCents
        }
        val sum = totals.values.sum()
        return totals.entries
            .map { (id, cents) ->
                val c = byId[id]
                CategoryTotal(
                    categoryId = id,
                    name = categoryLabel(c?.name ?: "Sonstiges"),
                    emoji = c?.emoji ?: "📦",
                    cents = cents,
                    percent = if (sum == 0L) 0f else cents.toFloat() / sum.toFloat()
                )
            }
            .sortedByDescending { it.cents }
    }

    /** Pair of (fixed costs, variable costs) inside [range]. */
    fun fixedVsVariable(
        expenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        range: DateRange
    ): Pair<Long, Long> {
        val fixedIds = categories.filter { it.isFixed }.map { it.id }.toHashSet()
        var fixed = 0L
        var variable = 0L
        for (e in expenses) {
            if (!range.contains(e.date)) continue
            if (fixedIds.contains(e.categoryId)) fixed += e.amountCents else variable += e.amountCents
        }
        return fixed to variable
    }

    fun monthlyAverage(totalCents: Long, range: DateRange): Long {
        val months = range.monthCount().coerceAtLeast(1)
        return totalCents / months
    }

    fun highestSingleExpense(expenses: List<ExpenseEntity>, range: DateRange): ExpenseEntity? =
        expenses.filter { range.contains(it.date) }.maxByOrNull { it.amountCents }

    fun monthlySeries(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        today: LocalDate,
        months: Int = 6
    ): List<MonthPoint> {
        val out = ArrayList<MonthPoint>(months)
        for (i in months - 1 downTo 0) {
            val ym = YearMonth.from(today).minusMonths(i.toLong())
            val range = DateRange(
                ym.atDay(1).toEpochDay(),
                ym.atEndOfMonth().toEpochDay()
            )
            out.add(
                MonthPoint(
                    month = ym,
                    incomeCents = sumIncomes(incomes, range),
                    expenseCents = sumExpenses(expenses, range)
                )
            )
        }
        return out
    }

    // ------------------------------- bills ----------------------------------

    fun isPaid(bill: BillEntity): Boolean = bill.status == BillStatus.PAID.name

    fun isOverdue(bill: BillEntity, today: LocalDate): Boolean =
        !isPaid(bill) && bill.dueDate < today.toEpochDay()

    fun daysLeft(bill: BillEntity, today: LocalDate): Long = bill.dueDate - today.toEpochDay()

    fun openBills(bills: List<BillEntity>): List<BillEntity> =
        bills.filter { !isPaid(it) }

    fun overdueBills(bills: List<BillEntity>, today: LocalDate): List<BillEntity> =
        openBills(bills)
            .filter { it.dueDate < today.toEpochDay() }
            .sortedBy { it.dueDate }

    /** Open bills that are not overdue, soonest first. */
    fun upcomingBills(bills: List<BillEntity>, today: LocalDate, withinDays: Long = 30): List<BillEntity> =
        openBills(bills)
            .filter { it.dueDate >= today.toEpochDay() && it.dueDate <= today.toEpochDay() + withinDays }
            .sortedBy { it.dueDate }

    fun sumOpenBills(bills: List<BillEntity>): Long = openBills(bills).sumOf { it.amountCents }

    fun sumUpcomingBills(bills: List<BillEntity>, today: LocalDate, withinDays: Long = 30): Long =
        upcomingBills(bills, today, withinDays).sumOf { it.amountCents }

    fun sumPaidPayments(payments: List<BillPaymentEntity>, range: DateRange): Long =
        payments.filter { range.contains(it.paidDate) }.sumOf { it.amountCents }

    // ----------------------------- dashboard --------------------------------

    fun dashboard(
        today: LocalDate,
        month: YearMonth,
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        bills: List<BillEntity>,
        categories: List<CategoryEntity>,
        budgetEnabled: Boolean,
        budgetLimitCents: Long
    ): DashboardData {
        val range = DateRange(
            month.atDay(1).toEpochDay(),
            month.atEndOfMonth().toEpochDay()
        )
        val income = sumIncomes(incomes, range)
        val expense = sumExpenses(expenses, range)
        val names = categories.associateBy { it.id }

        val recentExpenses = expenses
            .filter { it.date <= today.toEpochDay() }
            .sortedWith(compareByDescending<ExpenseEntity> { it.date }.thenByDescending { it.id })
            .take(6)
            .map {
                val c = names[it.categoryId]
                TxItem(
                    key = "e${it.id}",
                    isIncome = false,
                    id = it.id,
                    amountCents = it.amountCents,
                    title = categoryLabel(c?.name ?: "Sonstiges"),
                    emoji = c?.emoji ?: "📦",
                    date = Dates.fromEpochDay(it.date),
                    note = it.note,
                    paid = it.paid
                )
            }
        val recentIncomes = incomes
            .filter { it.date <= today.toEpochDay() }
            .sortedWith(compareByDescending<IncomeEntity> { it.date }.thenByDescending { it.id })
            .take(6)
            .map {
                val c = names[it.categoryId]
                TxItem(
                    key = "i${it.id}",
                    isIncome = true,
                    id = it.id,
                    amountCents = it.amountCents,
                    title = categoryLabel(c?.name ?: "Sonstige Einnahmen"),
                    emoji = c?.emoji ?: "💰",
                    date = Dates.fromEpochDay(it.date),
                    note = it.note
                )
            }

        val recent = (recentExpenses + recentIncomes)
            .sortedWith(compareByDescending<TxItem> { it.date.toEpochDay() }
                .thenByDescending { it.id })
            .take(8)

        return DashboardData(
            month = month,
            monthLabel = Dates.monthYear(month.atDay(1)),
            incomeCents = income,
            expenseCents = expense,
            availableCents = income - expense,
            budgetEnabled = budgetEnabled,
            budgetLimitCents = budgetLimitCents,
            budgetSpentCents = expense,
            upcomingBills = upcomingBills(bills, today, 60).take(5),
            recent = recent,
            openBillsCents = sumOpenBills(bills),
            overdueCount = overdueBills(bills, today).size
        )
    }

    // ---------------------------- statistics --------------------------------

    fun statistics(
        today: LocalDate,
        period: StatsPeriod,
        custom: Pair<LocalDate, LocalDate>?,
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        bills: List<BillEntity>,
        payments: List<BillPaymentEntity>,
        categories: List<CategoryEntity>
    ): StatisticsData {
        val range = rangeForPeriod(period, today, custom)
        val income = sumIncomes(incomes, range)
        val expense = sumExpenses(expenses, range)
        val (fixed, variable) = fixedVsVariable(expenses, categories, range)
        val yearRange = DateRange(
            Dates.startOfYear(today).toEpochDay(),
            Dates.endOfYear(today).toEpochDay()
        )
        val monthsInRange = range.monthCount().coerceAtLeast(1)

        return StatisticsData(
            label = labelForPeriod(period, today, custom),
            range = range,
            incomeCents = income,
            expenseCents = expense,
            remainingCents = income - expense,
            fixedCents = fixed,
            variableCents = variable,
            upcomingBillsCents = sumUpcomingBills(bills, today, 30),
            paidBillsCents = sumPaidPayments(payments, range),
            outstandingBillsCents = sumOpenBills(bills),
            topCategories = categoryTotals(expenses, categories, range).take(8),
            highestSingleExpense = highestSingleExpense(expenses, range),
            averageMonthlyExpenseCents = expense / monthsInRange,
            yearExpenseCents = sumExpenses(expenses, yearRange),
            yearIncomeCents = sumIncomes(incomes, yearRange),
            monthlySeries = monthlySeries(expenses, incomes, today, 6)
        )
    }

    // --------------------------- income summaries ----------------------------

    data class IncomeSummary(
        val monthCents: Long,
        val yearCents: Long,
        val averageMonthCents: Long,
        val monthCount: Int
    )

    fun incomeSummary(
        incomes: List<IncomeEntity>,
        today: LocalDate
    ): IncomeSummary {
        val monthRange = DateRange(
            Dates.startOfMonth(today).toEpochDay(),
            Dates.endOfMonth(today).toEpochDay()
        )
        val yearRange = DateRange(
            Dates.startOfYear(today).toEpochDay(),
            Dates.endOfYear(today).toEpochDay()
        )
        val monthCents = sumIncomes(incomes, monthRange)
        val yearCents = sumIncomes(incomes, yearRange)
        // Average over the months of the current year that have already started.
        val monthsElapsed = today.monthValue
        val average = if (monthsElapsed <= 0) yearCents else yearCents / monthsElapsed
        return IncomeSummary(
            monthCents = monthCents,
            yearCents = yearCents,
            averageMonthCents = average,
            monthCount = monthsElapsed
        )
    }

    /** Next planned occurrence for the recurring overview. */
    fun nextRunLabel(rule: RecurringRuleEntity, today: LocalDate): String {
        val spec = RecurrenceSpec.from(rule)
        val next = Recurrence.nextOnOrAfter(spec, today)
            ?: return if (rule.active) str("no_further_date", "Kein weiterer Termin") else str("ended", "Beendet")
        return Dates.full(next)
    }
}

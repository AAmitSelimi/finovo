package com.finovo.app.util

import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.SubscriptionEntity
import java.time.LocalDate
import java.time.YearMonth
import com.finovo.app.util.str

// ===========================================================================
// 7. Financial calendar
// ===========================================================================

enum class CalendarKind { BILL, RECURRING_INCOME, RECURRING_EXPENSE, SUBSCRIPTION, TRANSACTION }

data class CalendarEntry(
    val epochDay: Long = 0,
    val title: String = "",
    val emoji: String = "",
    val cents: Long = 0,
    val kind: CalendarKind = CalendarKind.TRANSACTION,
    val sourceId: Long = 0,
    val isIncome: Boolean = false
)

data class DayBucket(
    val epochDay: Long = 0,
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val entries: List<CalendarEntry> = emptyList()
) {
    val netCents: Long get() = incomeCents - expenseCents
}

object FinanceCalendar {

    /** All expected (and booked) movements for one month. */
    fun entriesForMonth(
        month: YearMonth,
        bills: List<BillEntity>,
        rules: List<RecurringRuleEntity>,
        subscriptions: List<SubscriptionEntity>,
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        categories: List<CategoryEntity>
    ): List<CalendarEntry> {
        val from = month.atDay(1)
        val to = month.atEndOfMonth()
        val fromDay = from.toEpochDay()
        val toDay = to.toEpochDay()
        val out = ArrayList<CalendarEntry>()
        val catName = { id: Long -> categoryLabel(categories.firstOrNull { it.id == id }?.name ?: "") }
        val catEmoji = { id: Long -> categories.firstOrNull { it.id == id }?.emoji ?: "" }

        // Open bills with a due date inside the month
        bills.filter { it.status != BillStatus.PAID.name && it.dueDate in fromDay..toDay }
            .forEach {
                out.add(
                    CalendarEntry(
                        epochDay = it.dueDate,
                        title = it.name.ifBlank { str("rechnung_4", "Rechnung") },
                        emoji = catEmoji(it.categoryId),
                        cents = it.amountCents,
                        kind = CalendarKind.BILL,
                        sourceId = it.id
                    )
                )
            }

        // Recurring rules that fall into the month
        rules.filter { it.active }.forEach { rule ->
            val spec = RecurrenceSpec.from(rule)
            Recurrence.occurrences(spec, from, to).forEach { date ->
                val income = rule.kind == RecurringKind.INCOME.name
                out.add(
                    CalendarEntry(
                        epochDay = date.toEpochDay(),
                        title = rule.name.ifBlank { catName(rule.categoryId).ifBlank { str("fallback_recurring", "Wiederkehrend") } },
                        emoji = catEmoji(rule.categoryId),
                        cents = rule.amountCents,
                        kind = if (income) CalendarKind.RECURRING_INCOME else CalendarKind.RECURRING_EXPENSE,
                        sourceId = rule.id,
                        isIncome = income
                    )
                )
            }
        }

        // Subscriptions charged inside the month (including follow-up cycles)
        subscriptions.filter { it.active }.forEach { sub ->
            val spec = RecurrenceSpec(
                frequency = runCatching { com.finovo.app.data.Frequency.valueOf(sub.frequency) }
                    .getOrDefault(com.finovo.app.data.Frequency.MONTHLY),
                interval = sub.interval,
                customDays = sub.customDays,
                dayOfMonth = Dates.fromEpochDay(sub.nextPayment).dayOfMonth,
                monthOfYear = Dates.fromEpochDay(sub.nextPayment).monthValue,
                startDate = Dates.fromEpochDay(sub.startDate)
            )
            var cursor = Dates.fromEpochDay(sub.nextPayment)
            var guard = 0
            while (cursor <= to && guard < 400) {
                if (cursor >= from) {
                    out.add(
                        CalendarEntry(
                            epochDay = cursor.toEpochDay(),
                            title = sub.name.ifBlank { str("fallback_subscription", "Abo") },
                            emoji = sub.emoji,
                            cents = sub.amountCents,
                            kind = CalendarKind.SUBSCRIPTION,
                            sourceId = sub.id
                        )
                    )
                }
                val next = Recurrence.nextAfter(spec, cursor)
                cursor = next ?: break
                guard++
            }
        }

        // Already booked transactions
        expenses.filter { it.date in fromDay..toDay }.forEach {
            out.add(
                CalendarEntry(
                    epochDay = it.date,
                    title = it.note.ifBlank { catName(it.categoryId).ifBlank { str("ausgabe_2", "Ausgabe") } },
                    emoji = catEmoji(it.categoryId),
                    cents = it.amountCents,
                    kind = CalendarKind.TRANSACTION,
                    sourceId = it.id
                )
            )
        }
        incomes.filter { it.date in fromDay..toDay }.forEach {
            out.add(
                CalendarEntry(
                    epochDay = it.date,
                    title = it.note.ifBlank { catName(it.categoryId).ifBlank { str("einnahme_2", "Einnahme") } },
                    emoji = catEmoji(it.categoryId),
                    cents = it.amountCents,
                    kind = CalendarKind.TRANSACTION,
                    sourceId = it.id,
                    isIncome = true
                )
            )
        }

        return out.sortedWith(compareBy({ it.epochDay }, { !it.isIncome }))
    }

    /** Groups the entries by day. */
    fun buckets(entries: List<CalendarEntry>): Map<Long, DayBucket> =
        entries.groupBy { it.epochDay }.mapValues { (day, list) ->
            DayBucket(
                epochDay = day,
                incomeCents = list.filter { it.isIncome }.sumOf { it.cents },
                expenseCents = list.filter { !it.isIncome }.sumOf { it.cents },
                entries = list
            )
        }

    /** Running balance for every day of the month, starting from [startBalanceCents]. */
    fun runningBalance(
        month: YearMonth,
        buckets: Map<Long, DayBucket>,
        startBalanceCents: Long
    ): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        var current = startBalanceCents
        val days = month.lengthOfMonth()
        for (d in 1..days) {
            val day = month.atDay(d).toEpochDay()
            val bucket = buckets[day]
            current += (bucket?.netCents ?: 0L)
            out.add(day to current)
        }
        return out
    }
}

// ===========================================================================
// 8. Smart analysis (100 % local)
// ===========================================================================

enum class InsightSeverity { GOOD, INFO, WARNING }

data class SmartInsight(
    val emoji: String = "",
    val title: String = "",
    val message: String = "",
    val severity: InsightSeverity = InsightSeverity.INFO,
    val titleKey: String = "",
    val titleArgs: List<String> = emptyList(),
    val messageKey: String = "",
    val messageArgs: List<String> = emptyList()
)

object SmartInsights {

    private fun monthRange(month: YearMonth): LongRange =
        month.atDay(1).toEpochDay()..month.atEndOfMonth().toEpochDay()

    private fun sumIn(expenses: List<ExpenseEntity>, range: LongRange, categoryId: Long? = null): Long =
        expenses.filter { it.date in range && (categoryId == null || it.categoryId == categoryId) }
            .sumOf { it.amountCents }

    /** All findings for the current month, completely offline. */
    fun analyze(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        categories: List<CategoryEntity>,
        today: LocalDate = LocalDate.now()
    ): List<SmartInsight> {
        if (expenses.isEmpty()) return emptyList()

        val thisMonth = Dates.yearMonth(today)
        val lastMonth = thisMonth.minusMonths(1)
        val thisRange = monthRange(thisMonth)
        val lastRange = monthRange(lastMonth)
        val out = ArrayList<SmartInsight>()
        val expenseCats = categories.filter { it.type == com.finovo.app.data.CategoryType.EXPENSE.name }

        // 1) Month-over-month change per category
        val deltas = expenseCats.mapNotNull { cat ->
            val now = sumIn(expenses, thisRange, cat.id)
            val before = sumIn(expenses, lastRange, cat.id)
            if (now == 0L && before == 0L) null else Triple(cat, now, before)
        }
        val biggestUp = deltas.filter { it.third > 0L }
            .maxByOrNull { it.second - it.third }
            ?.takeIf { it.second - it.third >= 2_000L }
        if (biggestUp != null) {
            val diff = biggestUp.second - biggestUp.third
            out.add(
                SmartInsight(
                    emoji = "\u2B06",
                    title = "%1\$s gestiegen", titleKey = "insight_category_up",
                    titleArgs = listOf(categoryLabel(biggestUp.first.name)),
                    message = "Du hast diesen Monat %1\$s mehr für %2\$s ausgegeben als im Vormonat.",
                    messageKey = "insight_spending_up",
                    messageArgs = listOf(Money.format(diff, "€"), categoryLabel(biggestUp.first.name)),
                    severity = InsightSeverity.WARNING
                )
            )
        }
        val biggestDown = deltas.filter { it.third > 0L && it.second < it.third }
            .minByOrNull { it.second - it.third }
            ?.takeIf { it.third - it.second >= 2_000L }
        if (biggestDown != null) {
            val diff = biggestDown.third - biggestDown.second
            out.add(
                SmartInsight(
                    emoji = "\u2B07",
                    title = "%1\$s gesunken", titleKey = "insight_category_down",
                    titleArgs = listOf(categoryLabel(biggestDown.first.name)),
                    message = "Sehr gut: %1\$s weniger als im Vormonat.",
                    messageKey = "insight_spending_down",
                    messageArgs = listOf(Money.format(diff, "€")),
                    severity = InsightSeverity.GOOD
                )
            )
        }

        // 2) Unusual spending vs. the average of the previous months
        val history = (1..6).map { thisMonth.minusMonths(it.toLong()) }
        expenseCats.forEach { cat ->
            val values = history.map { sumIn(expenses, monthRange(it), cat.id) }.filter { it > 0L }
            if (values.size >= 3) {
                val average = values.sum() / values.size
                val now = sumIn(expenses, thisRange, cat.id)
                if (average > 5_000L && now > (average * 150) / 100) {
                    out.add(
                        SmartInsight(
                            emoji = "\u26A0",
                            title = str("ungewoehnlich_hohe_ausgaben", "Ungewöhnlich hohe Ausgaben"), titleKey = "insight_unusual_title",
                            message = "%1\$s: %2\$s diesen Monat – dein Schnitt liegt bei %3\$s.",
                            messageKey = "insight_unusual",
                            messageArgs = listOf(categoryLabel(cat.name), Money.format(now, "€"), Money.format(average, "€")),
                            severity = InsightSeverity.WARNING
                        )
                    )
                }
            }
        }

        // 3) Top categories
        val top = expenseCats.map { cat -> cat to sumIn(expenses, thisRange, cat.id) }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .take(3)
        if (top.isNotEmpty()) {
            val total = top.sumOf { it.second }
            out.add(
                SmartInsight(
                    emoji = "\uD83D\uDCCA",
                    title = str("groesste_ausgabenposten", "Größte Ausgabenposten"), titleKey = "insight_top_items_title",
                    message = top.joinToString(", ") { (cat, cents) ->
                        "${categoryLabel(cat.name)} ${Money.format(cents, "€")}"
                    } + " – zusammen %1\$s.",
                    messageKey = "insight_top_items",
                    messageArgs = listOf(Money.format(total, "€")),
                    severity = InsightSeverity.INFO
                )
            )
        }

        // 4) Average per day and forecast
        val spentSoFar = sumIn(expenses, thisRange)
        val dayOfMonth = today.dayOfMonth
        val perDay = if (dayOfMonth > 0) spentSoFar / dayOfMonth else 0L
        val forecast = perDay * thisMonth.lengthOfMonth()
        out.add(
            SmartInsight(
                emoji = "\uD83D\uDCC5",
                title = str("tempo_dieses_monats", "Tempo dieses Monats"), titleKey = "insight_pace_title",
                message = "Ø %1\$s pro Tag – hochgerechnet %2\$s bis zum Monatsende.",
                messageKey = "insight_pace",
                messageArgs = listOf(Money.format(perDay, "€"), Money.format(forecast, "€")),
                severity = InsightSeverity.INFO
            )
        )
        val lastMonthTotal = sumIn(expenses, lastRange)
        if (lastMonthTotal > 0L) {
            val diff = forecast - lastMonthTotal
            out.add(
                SmartInsight(
                    emoji = if (diff > 0) "\uD83D\uDCC8" else "\uD83D\uDCC9",
                    title = if (diff > 0) str("teurer_als_im_vormonat", "Teurer als im Vormonat") else str("guenstiger_als_im_vormonat", "Günstiger als im Vormonat"),
                    titleKey = if (diff > 0) "insight_more_expensive_title" else "insight_cheaper_title",
                    message = if (diff > 0) {
                        "Prognose: %1\$s über dem Vormonat (%2\$s)."
                    } else {
                        "Prognose: %1\$s unter dem Vormonat (%2\$s)."
                    },
                    messageKey = if (diff > 0) "insight_forecast_above" else "insight_forecast_below",
                    messageArgs = listOf(Money.format(kotlin.math.abs(diff), "€"), Money.format(lastMonthTotal, "€")),
                    severity = if (diff > 0) InsightSeverity.WARNING else InsightSeverity.GOOD
                )
            )
        }

        // 5) Savings rate
        val incomeThis = incomes.filter { it.date in thisRange }.sumOf { it.amountCents }
        if (incomeThis > 0L) {
            val rate = ((incomeThis - spentSoFar) * 100) / incomeThis
            out.add(
                SmartInsight(
                    emoji = if (rate >= 20) "\uD83C\uDF1F" else "\uD83D\uDCB0",
                    title = "Sparquote", titleKey = "insight_savings_rate_title",
                    message = "%1\$d %% deiner Einnahmen (%2\$s) sind noch übrig.",
                    messageKey = "insight_savings_rate",
                    messageArgs = listOf(rate.toString(), Money.format(incomeThis, "€")),
                    severity = if (rate >= 20) InsightSeverity.GOOD else InsightSeverity.INFO
                )
            )
        }

        return out
    }
}

// ===========================================================================
// 9. What-if calculator
// ===========================================================================

object WhatIf {

    data class SavingPlan(
        val monthlyCents: Long,
        val months: Int,
        val startCents: Long,
        val totalCents: Long
    ) {
        val points: List<Pair<Int, Long>>
            get() = (0..months).map { m -> m to (startCents + monthlyCents * m) }
    }

    fun savingPlan(monthlyCents: Long, months: Int, startCents: Long = 0L): SavingPlan {
        val safeMonths = months.coerceIn(1, 600)
        val monthly = monthlyCents.coerceAtLeast(0L)
        return SavingPlan(
            monthlyCents = monthly,
            months = safeMonths,
            startCents = startCents,
            totalCents = startCents + monthly * safeMonths
        )
    }

    data class BalanceChange(
        val incomeCents: Long,
        val expenseCents: Long,
        val newBalanceCents: Long,
        val changeCents: Long
    )

    fun expenseChange(
        incomeCents: Long,
        expenseCents: Long,
        expenseDeltaCents: Long
    ): BalanceChange = BalanceChange(
        incomeCents = incomeCents,
        expenseCents = (expenseCents + expenseDeltaCents).coerceAtLeast(0L),
        newBalanceCents = incomeCents - (expenseCents + expenseDeltaCents).coerceAtLeast(0L),
        changeCents = -expenseDeltaCents
    )

    fun incomeChange(
        incomeCents: Long,
        expenseCents: Long,
        incomeDeltaCents: Long
    ): BalanceChange = BalanceChange(
        incomeCents = (incomeCents + incomeDeltaCents).coerceAtLeast(0L),
        expenseCents = expenseCents,
        newBalanceCents = (incomeCents + incomeDeltaCents).coerceAtLeast(0L) - expenseCents,
        changeCents = incomeDeltaCents
    )

    /** How many months until a goal is reached (0 = already reached). */
    fun monthsToGoal(remainingCents: Long, monthlyCents: Long): Int {
        if (remainingCents <= 0L) return 0
        if (monthlyCents <= 0L) return -1
        val whole = (remainingCents / monthlyCents).toInt()
        return if (remainingCents % monthlyCents == 0L) whole else whole + 1
    }
}

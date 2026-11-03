package com.finovo.app.ai

import com.finovo.app.data.BillEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.Repository
import com.finovo.app.data.SubscriptionEntity
import com.finovo.app.util.Analytics
import com.finovo.app.util.DateRange
import com.finovo.app.util.Dates
import com.finovo.app.util.Stats
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/** Eine Rechnung - nur die Felder, die die KI sehen darf. */
data class BillFact(
    val name: String,
    val vendor: String,
    val amountCents: Long,
    /** null = kein Fälligkeitsdatum erfasst (wird niemals erfunden). */
    val dueDate: LocalDate?,
    val daysLeft: Long?,
    val isOverdue: Boolean
)

enum class UpcomingKind { BILL, SUBSCRIPTION, RECURRING }

/** Eine Zahlung, die in den nächsten Tagen fällig wird. */
data class UpcomingFact(
    val name: String,
    val amountCents: Long,
    val date: LocalDate,
    val kind: UpcomingKind,
    val daysLeft: Long
)

data class RecurringFact(val name: String, val monthlyCents: Long, val isIncome: Boolean)

data class BudgetFact(
    val limitCents: Long,
    val usedCents: Long,
    val remainingCents: Long,
    val percentUsed: Int?,
    val enabled: Boolean
)

data class CategoryFact(
    val name: String,
    val emoji: String,
    val cents: Long,
    val percent: Float,
    val isFixed: Boolean
)

/** Eine einzelne Buchung - für Rückfragen wie "Was war die größte Ausgabe?". */
data class TransactionFact(
    val name: String,
    val categoryName: String,
    val amountCents: Long,
    val date: LocalDate
)

/** Häufige Buchungen bzw. Händler - aus Notizen und Rechnungs-Absendern. */
data class MerchantFact(val name: String, val cents: Long, val count: Int)

/** Alle Werte eines Zeitraums (Monat). */
data class PeriodFacts(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val fixedCents: Long,
    val variableCents: Long,
    val transactionCount: Int,
    val categories: List<CategoryFact>
) {
    val remainingCents: Long get() = incomeCents - expenseCents
}

/**
 * Strukturierte Zusammenfassung aller Finanzdaten des Nutzers.
 *
 * Das ist die EINZIGE Datenquelle der KI. Sie enthält ausschließlich
 * kopierte Werte (Long-Beträge in Cent, LocalDate-Objekte) - keine
 * Datenbankverbindung, keine DAOs und keine Schreibzugriffe. Damit ist die KI
 * rein konstruktiv schreibgeschützt.
 */
data class FinancialSummary(
    val today: LocalDate,
    val symbol: String,
    val month: PeriodFacts,
    val previousMonth: PeriodFacts?,
    val openBills: List<BillFact>,
    val overdueBills: List<BillFact>,
    val paidBillsInMonth: List<BillFact>,
    val upcoming: List<UpcomingFact>,
    val recurring: List<RecurringFact>,
    val budget: BudgetFact?,
    /** Größte einzelne Ausgabe des Monats (null = keine Ausgabe erfasst). */
    val groessteAusgabe: TransactionFact?,
    /** Größte einzelne Einnahme des Monats (null = keine Einnahme erfasst). */
    val groessteEinnahme: TransactionFact?,
    /** Alle Ausgaben des Monats (für Rückfragen nach Beträgen). */
    val ausgabenMonat: List<TransactionFact>,
    val merchants: List<MerchantFact>,
    val totalTransactions: Int,
    val daysElapsed: Int,
    val daysInMonth: Int
) {
    val openBillsTotalCents: Long get() = openBills.sumOf { it.amountCents }

    val recurringMonthlyCents: Long
        get() = recurring.filter { !it.isIncome }.sumOf { it.monthlyCents }

    /** Genug Daten für eine belastbare Aussage? */
    val hasEnoughData: Boolean
        get() = totalTransactions > 0 || openBills.isNotEmpty() || recurring.isNotEmpty()

    /** Ausgaben monatsanteilig hochgerechnet - nur als Schätzung verwenden. */
    fun projectedMonthExpenseCents(): Long? {
        if (daysElapsed <= 0 || month.expenseCents <= 0) return null
        val factor = daysInMonth.toDouble() / daysElapsed.toDouble()
        return (month.expenseCents * factor).toLong()
    }
}

/**
 * Baut die Zusammenfassung aus der Datenbank - lesend.
 *
 * Wiederverwendet die vorhandene Analyse-Schicht ([Stats], [Analytics]),
 * damit die KI exakt dieselben Zahlen liefert wie die Finanzanalyse.
 */
object FinancialSummaryService {

    private const val UPCOMING_DAYS = 30L

    suspend fun build(
        repository: Repository,
        today: LocalDate = Dates.today(),
        symbol: String = "€"
    ): FinancialSummary = withContext(Dispatchers.IO) {
        val expenses = repository.expenses.firstOrNull() ?: emptyList()
        val incomes = repository.incomes.firstOrNull() ?: emptyList()
        val bills = repository.bills.firstOrNull() ?: emptyList()
        val rules = repository.recurringRules.firstOrNull() ?: emptyList()
        val subs = repository.subscriptions.firstOrNull() ?: emptyList()
        val categories = repository.categories.firstOrNull() ?: emptyList()
        val budget = repository.budget.firstOrNull()

        val yearMonth = YearMonth.from(today)
        val monthRange = rangeOfMonth(yearMonth)
        val previousRange = rangeOfMonth(yearMonth.minusMonths(1))

        val month = periodFacts(expenses, incomes, categories, monthRange, Dates.monthYear(today))
        val previous = periodFacts(
            expenses, incomes, categories, previousRange,
            Dates.monthYear(today.minusMonths(1))
        )

        val openBills = Stats.openBills(bills).map { it.toFact(today) }
            .sortedWith(compareBy({ it.dueDate == null }, { it.dueDate ?: LocalDate.MAX }))
        val overdue = Stats.overdueBills(bills, today).map { it.toFact(today) }
            .sortedBy { it.dueDate }

        val paid = bills.filter { Stats.isPaid(it) }
            .map { it.toFact(today) }
            .filter { fact ->
                val paidDay = bills.firstOrNull { b -> b.name == fact.name && Stats.isPaid(b) }
                    ?.let { it.dueDate } ?: return@filter false
                monthRange.contains(paidDay)
            }

        val upcoming = upcomingPayments(bills, subs, rules, today)

        val recurring = rules.map { rule ->
            RecurringFact(
                name = rule.name,
                monthlyCents = Analytics.monthlyAmount(
                    rule.amountCents,
                    runCatching { com.finovo.app.data.Frequency.valueOf(rule.frequency) }
                        .getOrDefault(com.finovo.app.data.Frequency.MONTHLY),
                    rule.interval
                ),
                isIncome = rule.kind == com.finovo.app.data.RecurringKind.INCOME.name
            )
        }.filter { it.monthlyCents > 0 }.sortedByDescending { it.monthlyCents }

        val used = month.expenseCents
        val limit = budget?.monthlyLimitCents ?: 0L
        val budgetFact = budget?.let { b ->
            BudgetFact(
                limitCents = limit,
                usedCents = used,
                remainingCents = limit - used,
                percentUsed = if (limit > 0) ((used * 100) / limit).toInt() else null,
                enabled = b.enabled
            )
        }

        FinancialSummary(
            today = today,
            symbol = symbol.ifBlank { "€" },
            month = month,
            previousMonth = previous,
            openBills = openBills,
            overdueBills = overdue,
            paidBillsInMonth = paid,
            upcoming = upcoming,
            recurring = recurring,
            budget = budgetFact,
            groessteAusgabe = groesste(expenses, categories, monthRange, false),
            groessteEinnahme = groessteEinnahme(incomes, categories, monthRange),
            ausgabenMonat = buchungen(expenses, categories, monthRange),
            merchants = merchantFacts(expenses, bills, monthRange),
            totalTransactions = expenses.size + incomes.size,
            daysElapsed = today.dayOfMonth,
            daysInMonth = yearMonth.lengthOfMonth()
        )
    }

    // ------------------------------------------------------------------ Hilfen

    private fun buchungen(
        expenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        range: DateRange
    ): List<TransactionFact> {
        val namen = categories.associateBy { it.id }
        return expenses.filter { range.contains(it.date) }
            .map {
                TransactionFact(
                    name = it.merchant.ifBlank { it.note }.ifBlank { "Ausgabe" },
                    categoryName = namen[it.categoryId]?.name ?: "",
                    amountCents = it.amountCents,
                    date = Dates.fromEpochDay(it.date)
                )
            }
            .sortedByDescending { it.amountCents }
    }

    private fun groesste(
        expenses: List<ExpenseEntity>,
        categories: List<CategoryEntity>,
        range: DateRange,
        nurFix: Boolean
    ): TransactionFact? {
        val namen = categories.associateBy { it.id }
        return expenses.filter { range.contains(it.date) }
            .filter { it.amountCents > 0 }
            .maxByOrNull { it.amountCents }
            ?.let {
                TransactionFact(
                    name = it.merchant.ifBlank { it.note }.ifBlank { "Ausgabe" },
                    categoryName = namen[it.categoryId]?.name ?: "",
                    amountCents = it.amountCents,
                    date = Dates.fromEpochDay(it.date)
                )
            }
    }

    private fun groessteEinnahme(
        incomes: List<IncomeEntity>,
        categories: List<CategoryEntity>,
        range: DateRange
    ): TransactionFact? {
        val namen = categories.associateBy { it.id }
        return incomes.filter { range.contains(it.date) && it.amountCents > 0 }
            .maxByOrNull { it.amountCents }
            ?.let {
                TransactionFact(
                    name = it.merchant.ifBlank { it.note }.ifBlank { "Einnahme" },
                    categoryName = namen[it.categoryId]?.name ?: "",
                    amountCents = it.amountCents,
                    date = Dates.fromEpochDay(it.date)
                )
            }
    }

    private fun rangeOfMonth(month: YearMonth): DateRange =
        DateRange(month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay())

    private fun periodFacts(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        categories: List<CategoryEntity>,
        range: DateRange,
        label: String
    ): PeriodFacts {
        val (fixed, variable) = Stats.fixedVsVariable(expenses, categories, range)
        val fixedIds = categories.filter { it.isFixed }.map { it.id }.toHashSet()
        return PeriodFacts(
            label = label,
            incomeCents = Stats.sumIncomes(incomes, range),
            expenseCents = Stats.sumExpenses(expenses, range),
            fixedCents = fixed,
            variableCents = variable,
            transactionCount = expenses.count { range.contains(it.date) } +
                incomes.count { range.contains(it.date) },
            categories = Stats.categoryTotals(expenses, categories, range).map { total ->
                CategoryFact(
                    name = total.name,
                    emoji = total.emoji,
                    cents = total.cents,
                    percent = total.percent,
                    isFixed = fixedIds.contains(total.categoryId)
                )
            }
        )
    }

    private fun BillEntity.toFact(today: LocalDate): BillFact {
        val due = if (dueDate == 0L) null else Dates.fromEpochDay(dueDate)
        return BillFact(
            name = name.ifBlank { vendor.ifBlank { "Rechnung" } },
            vendor = vendor,
            amountCents = amountCents,
            dueDate = due,
            daysLeft = due?.let { it.toEpochDay() - today.toEpochDay() },
            isOverdue = due != null && due.isBefore(today) && !Stats.isPaid(this)
        )
    }

    private fun upcomingPayments(
        bills: List<BillEntity>,
        subs: List<SubscriptionEntity>,
        rules: List<RecurringRuleEntity>,
        today: LocalDate
    ): List<UpcomingFact> {
        val todayDay = today.toEpochDay()
        val horizon = todayDay + UPCOMING_DAYS
        val out = ArrayList<UpcomingFact>()

        Stats.openBills(bills).forEach { bill ->
            if (bill.dueDate == 0L) return@forEach
            if (bill.dueDate in todayDay..horizon) {
                out += UpcomingFact(
                    name = bill.name.ifBlank { bill.vendor.ifBlank { "Rechnung" } },
                    amountCents = bill.amountCents,
                    date = Dates.fromEpochDay(bill.dueDate),
                    kind = UpcomingKind.BILL,
                    daysLeft = bill.dueDate - todayDay
                )
            }
        }

        subs.filter { it.active }.forEach { sub ->
            if (sub.nextPayment in todayDay..horizon) {
                out += UpcomingFact(
                    name = sub.name,
                    amountCents = sub.amountCents,
                    date = Dates.fromEpochDay(sub.nextPayment),
                    kind = UpcomingKind.SUBSCRIPTION,
                    daysLeft = sub.nextPayment - todayDay
                )
            }
        }

        rules.filter { it.active && it.kind != com.finovo.app.data.RecurringKind.INCOME.name }
            .forEach { rule ->
                val next = runCatching {
                    com.finovo.app.util.Recurrence.nextOnOrAfter(
                        com.finovo.app.util.RecurrenceSpec.from(rule), today
                    )
                }.getOrNull() ?: return@forEach
                val day = next.toEpochDay()
                if (day in todayDay..horizon) {
                    out += UpcomingFact(
                        name = rule.name,
                        amountCents = rule.amountCents,
                        date = next,
                        kind = UpcomingKind.RECURRING,
                        daysLeft = day - todayDay
                    )
                }
            }

        return out.sortedBy { it.date.toEpochDay() }
    }

    /** Häufige Buchungen aus Notizen + Rechnungs-Absendern. */
    private fun merchantFacts(
        expenses: List<ExpenseEntity>,
        bills: List<BillEntity>,
        range: DateRange
    ): List<MerchantFact> {
        val grouped = LinkedHashMap<String, Pair<Long, Int>>()
        expenses.filter { range.contains(it.date) && it.note.isNotBlank() }.forEach { e ->
            val key = e.note.trim()
            val (cents, count) = grouped[key] ?: (0L to 0)
            grouped[key] = (cents + e.amountCents) to (count + 1)
        }
        bills.filter { it.vendor.isNotBlank() }.forEach { b ->
            val key = b.vendor.trim()
            val (cents, count) = grouped[key] ?: (0L to 0)
            grouped[key] = (cents + b.amountCents) to (count + 1)
        }
        return grouped.entries
            .map { (name, value) -> MerchantFact(name, value.first, value.second) }
            .sortedByDescending { it.cents }
            .take(8)
    }
}

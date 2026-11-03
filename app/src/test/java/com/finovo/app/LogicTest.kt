package com.finovo.app

import com.finovo.app.backup.Backup
import com.finovo.app.backup.BackupPayload
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillPaymentEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.BudgetEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.DateFilter
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.Frequency
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.SettingsEntity
import com.finovo.app.data.StatsPeriod
import com.finovo.app.export.Export
import com.finovo.app.security.PinHasher
import com.finovo.app.util.DateRange
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.Recurrence
import com.finovo.app.util.RecurrenceSpec
import com.finovo.app.util.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Unit tests for the calculation core of Finovo.
 * These run as plain JVM tests - every amount, date and recurrence rule is verified.
 */
class LogicTest {

    // ---------------------------------------------------------------- money --

    @Test
    fun money_formatGerman() {
        assertEquals("1.234,56 €", Money.format(123456, "€"))
        assertEquals("0,00 €", Money.format(0, "€"))
        assertEquals("-99,99 €", Money.format(-9999, "€"))
        assertEquals("1.000.000,00 €", Money.format(100_000_000, "€"))
        assertEquals("12,50", Money.format(1250, "€", withSymbol = false))
    }

    @Test
    fun money_parseGermanAndEnglish() {
        assertEquals(1250L, Money.parseToCents("12,50"))
        assertEquals(1250L, Money.parseToCents("12.50"))
        assertEquals(123456L, Money.parseToCents("1.234,56"))
        assertEquals(123456L, Money.parseToCents("1,234.56"))
        assertEquals(123400L, Money.parseToCents("1.234"))
        assertEquals(123400L, Money.parseToCents("1234"))  // 1234 EUR
        assertEquals(50L, Money.parseToCents(",5"))
        assertEquals(1200L, Money.parseToCents("12"))
        assertEquals(0L, Money.parseToCents("0"))
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("abc"))
        assertEquals(1234500L, Money.parseToCents("12,345"))  // German thousands separator
        assertNull(Money.parseToCents("1.234.567,891"))
    }

    @Test
    fun money_noFloatingPointErrors() {
        // 0.1 + 0.2 with floats would be 0.30000000000000004
        val a = Money.parseToCents("0,10")!!
        val b = Money.parseToCents("0,20")!!
        assertEquals(30L, a + b)
        assertEquals("0,30 €", Money.format(a + b))
        var sum = 0L
        repeat(10) { sum += Money.parseToCents("0,10")!! }
        assertEquals(100L, sum)
        assertEquals("1,00 €", Money.format(sum))
    }

    @Test
    fun money_validation() {
        assertNull(Money.validate("12,50"))
        assertEquals("Bitte gib einen Betrag ein.", Money.validate("  "))
        assertEquals("Der Betrag muss größer als 0 sein.", Money.validate("0"))
        assertEquals("Der Betrag darf nicht negativ sein.", Money.validate("-5"))
        assertNotNull2(Money.validate("abc"))
    }

    private fun assertNotNull2(value: Any?) {
        assertTrue("expected non-null", value != null)
    }

    // ------------------------------------------------------------ recurrence --

    private fun spec(
        frequency: Frequency,
        interval: Int = 1,
        dayOfMonth: Int = 1,
        start: LocalDate = LocalDate.of(2026, 1, 15),
        customDays: Int = 30,
        endDate: LocalDate? = null
    ) = RecurrenceSpec(
        frequency = frequency,
        interval = interval,
        customDays = customDays,
        dayOfMonth = dayOfMonth,
        monthOfYear = start.monthValue,
        startDate = start,
        endDate = endDate
    )

    @Test
    fun recurrence_monthly_usesDayOfMonth() {
        val dates = Recurrence.occurrences(
            spec(Frequency.MONTHLY, dayOfMonth = 1, start = LocalDate.of(2026, 1, 20)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 30)
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1)
            ),
            dates
        )
    }

    @Test
    fun recurrence_monthly_clampsShortMonths() {
        val dates = Recurrence.occurrences(
            spec(Frequency.MONTHLY, dayOfMonth = 31, start = LocalDate.of(2026, 1, 1)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 30)
        )
        assertEquals(LocalDate.of(2026, 2, 28), dates[1])
        assertEquals(LocalDate.of(2026, 3, 31), dates[2])
    }

    @Test
    fun recurrence_intervals() {
        val every2Weeks = Recurrence.occurrences(
            spec(Frequency.WEEKLY, interval = 2, start = LocalDate.of(2026, 1, 5)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 28)
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 19),
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 16)
            ),
            every2Weeks
        )

        val every3Days = Recurrence.occurrences(
            spec(Frequency.CUSTOM, customDays = 3, start = LocalDate.of(2026, 1, 1)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 10)
        )
        assertEquals(4, every3Days.size)
        assertEquals(LocalDate.of(2026, 1, 10), every3Days.last())

        val yearly = Recurrence.occurrences(
            spec(Frequency.YEARLY, dayOfMonth = 3, start = LocalDate.of(2026, 3, 3)),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2029, 12, 31)
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2027, 3, 3),
                LocalDate.of(2028, 3, 3),
                LocalDate.of(2029, 3, 3)
            ),
            yearly
        )
    }

    @Test
    fun recurrence_endDateStopsGeneration() {
        val dates = Recurrence.occurrences(
            spec(
                Frequency.MONTHLY,
                dayOfMonth = 1,
                start = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 3, 1)
            ),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
        )
        assertEquals(3, dates.size)
        assertNull(Recurrence.nextAfter(
            spec(
                Frequency.MONTHLY,
                dayOfMonth = 1,
                start = LocalDate.of(2026, 1, 1),
                endDate = LocalDate.of(2026, 3, 1)
            ),
            LocalDate.of(2026, 3, 1)
        ))
    }

    @Test
    fun recurrence_nextAfterNeverReturnsPastDates() {
        val rule = spec(Frequency.MONTHLY, dayOfMonth = 1, start = LocalDate.of(2026, 1, 1))
        val next = Recurrence.nextAfter(rule, LocalDate.of(2026, 9, 14))!!
        assertEquals(LocalDate.of(2026, 10, 1), next)
        val next2 = Recurrence.nextAfter(rule, next)
        assertEquals(LocalDate.of(2026, 11, 1), next2)
    }

    @Test
    fun recurrence_dailyGenerationIsComplete() {
        val rule = spec(Frequency.DAILY, start = LocalDate.of(2026, 1, 1))
        val dates = Recurrence.occurrences(
            rule,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31)
        )
        assertEquals(31, dates.size)
        assertEquals(LocalDate.of(2026, 1, 1), dates.first())
        assertEquals(LocalDate.of(2026, 1, 31), dates.last())
    }

    @Test
    fun recurrence_descriptionGerman() {
        assertEquals(
            "Jeden Monat am 1.",
            Recurrence.describe(spec(Frequency.MONTHLY, dayOfMonth = 1))
        )
        assertEquals("Täglich", Recurrence.describe(spec(Frequency.DAILY)))
        assertEquals("Alle 2 Wochen", Recurrence.describe(spec(Frequency.WEEKLY, interval = 2)))
        assertEquals("Alle 14 Tage", Recurrence.describe(spec(Frequency.CUSTOM, customDays = 14)))
    }

    // ----------------------------------------------------------------- sums --

    private val categories = listOf(
        CategoryEntity(1, "Miete", "🏠", CategoryType.EXPENSE.name, isFixed = true),
        CategoryEntity(2, "Lebensmittel", "🛒", CategoryType.EXPENSE.name, isFixed = false),
        CategoryEntity(3, "Auto", "🚗", CategoryType.EXPENSE.name, isFixed = false),
        CategoryEntity(4, "Gehalt", "💼", CategoryType.INCOME.name)
    )

    private fun expenses() = listOf(
        ExpenseEntity(1, 90000, 1, LocalDate.of(2026, 9, 1).toEpochDay(), "September"),
        ExpenseEntity(2, 40000, 2, LocalDate.of(2026, 9, 5).toEpochDay(), "Wocheneinkauf"),
        ExpenseEntity(3, 35000, 3, LocalDate.of(2026, 9, 8).toEpochDay(), "Tanken"),
        ExpenseEntity(4, 50000, 2, LocalDate.of(2026, 8, 12).toEpochDay(), "August")
    )

    private fun incomes() = listOf(
        IncomeEntity(1, 320000, 4, LocalDate.of(2026, 9, 1).toEpochDay(), "Lohn"),
        IncomeEntity(2, 320000, 4, LocalDate.of(2026, 8, 1).toEpochDay(), "Lohn")
    )

    @Test
    fun sums_respectRange() {
        val range = DateRange(
            LocalDate.of(2026, 9, 1).toEpochDay(),
            LocalDate.of(2026, 9, 30).toEpochDay()
        )
        assertEquals(165000L, Stats.sumExpenses(expenses(), range))
        assertEquals(320000L, Stats.sumIncomes(incomes(), range))
        val august = DateRange(
            LocalDate.of(2026, 8, 1).toEpochDay(),
            LocalDate.of(2026, 8, 31).toEpochDay()
        )
        assertEquals(50000L, Stats.sumExpenses(expenses(), august))
    }

    @Test
    fun dashboard_calculatesAvailableFromRealData() {
        val data = Stats.dashboard(
            today = LocalDate.of(2026, 9, 14),
            month = YearMonth.of(2026, 9),
            expenses = expenses(),
            incomes = incomes(),
            bills = emptyList(),
            categories = categories,
            budgetEnabled = true,
            budgetLimitCents = 200000
        )
        assertEquals(320000L, data.incomeCents)
        assertEquals(165000L, data.expenseCents)
        assertEquals(155000L, data.availableCents)
        assertEquals("September 2026", data.monthLabel)
        // budget: 165.000 of 200.000 cents spent
        assertTrue(kotlin.math.abs(data.budgetUsed - 0.825f) < 0.001f)
    }

    @Test
    fun statistics_fixedVsVariable() {
        val stats = Stats.statistics(
            today = LocalDate.of(2026, 9, 14),
            period = StatsPeriod.THIS_MONTH,
            custom = null,
            expenses = expenses(),
            incomes = incomes(),
            bills = emptyList(),
            payments = emptyList(),
            categories = categories
        )
        assertEquals(90000L, stats.fixedCents)
        assertEquals(75000L, stats.variableCents)
        assertEquals(320000L, stats.incomeCents)
        assertEquals(165000L, stats.expenseCents)
        assertEquals(155000L, stats.remainingCents)
        assertEquals("Miete", stats.topCategories.first().name)
        assertEquals(90000L, stats.highestSingleExpense?.amountCents)
        assertEquals(6, stats.monthlySeries.size)
    }

    @Test
    fun categoryBreakdown_percentages() {
        val range = DateRange(
            LocalDate.of(2026, 9, 1).toEpochDay(),
            LocalDate.of(2026, 9, 30).toEpochDay()
        )
        val totals = Stats.categoryTotals(expenses(), categories, range)
        assertEquals(3, totals.size)
        assertEquals(90000L, totals[0].cents)
        assertEquals("Miete", totals[0].name)
        val sum = totals.sumOf { it.percent.toDouble() }
        assertTrue(kotlin.math.abs(sum - 1.0) < 0.0001)
    }

    @Test
    fun filters_todayWeekMonthYear() {
        val today = LocalDate.of(2026, 9, 14)
        val todayRange = Stats.rangeFor(DateFilter.TODAY, today)
        assertEquals(today.toEpochDay(), todayRange.from)
        assertEquals(today.toEpochDay(), todayRange.to)

        val week = Stats.rangeFor(DateFilter.WEEK, today)
        assertEquals(LocalDate.of(2026, 9, 14).toEpochDay(), week.from) // Monday
        assertEquals(LocalDate.of(2026, 9, 20).toEpochDay(), week.to)   // Sunday

        val month = Stats.rangeFor(DateFilter.MONTH, today)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), month.from)
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(), month.to)

        val year = Stats.rangeFor(DateFilter.YEAR, today)
        assertEquals(LocalDate.of(2026, 1, 1).toEpochDay(), year.from)
        assertEquals(LocalDate.of(2026, 12, 31).toEpochDay(), year.to)
    }

    @Test
    fun search_findsByNoteCategoryAndAmount() {
        val range = DateRange.all()
        val names = categories.associate { it.id to it.name }
        assertEquals(1, Stats.filterExpenses(expenses(), range, "Tanken", null, names).size)
        assertEquals(2, Stats.filterExpenses(expenses(), range, "lebensmittel", null, names).size)
        assertEquals(1, Stats.filterExpenses(expenses(), range, "900,00", null, names).size)
        assertEquals(0, Stats.filterExpenses(expenses(), range, "xyz", null, names).size)
        assertEquals(1, Stats.filterExpenses(expenses(), range, "", 1L, names).size)
    }

    // ----------------------------------------------------------------- bills --

    private fun bills(today: LocalDate) = listOf(
        BillEntity(1, "Miete", 90000, today.minusDays(3).toEpochDay(), 1, isRecurring = true),
        BillEntity(2, "Internet", 2799, today.plusDays(5).toEpochDay(), 1, isRecurring = true),
        BillEntity(3, "Versicherung", 8800, today.plusDays(60).toEpochDay(), 1, status = BillStatus.OPEN.name)
    )

    @Test
    fun bills_overdueUpcomingAndTotals() {
        val today = LocalDate.of(2026, 9, 14)
        val list = bills(today)
        val overdue = Stats.overdueBills(list, today)
        assertEquals(1, overdue.size)
        assertEquals("Miete", overdue.first().name)

        val upcoming = Stats.upcomingBills(list, today, 30)
        assertEquals(1, upcoming.size)
        assertEquals("Internet", upcoming.first().name)

        assertEquals(90000L + 2799L + 8800L, Stats.sumOpenBills(list))
        assertEquals(2799L, Stats.sumUpcomingBills(list, today, 30))
        assertTrue(Stats.isOverdue(list[0], today))
        assertFalse(Stats.isOverdue(list[1], today))
        assertEquals(5L, Stats.daysLeft(list[1], today))
    }

    @Test
    fun billPayments_sumInPeriod() {
        val today = LocalDate.of(2026, 9, 14)
        val payments = listOf(
            BillPaymentEntity(1, 1, "Miete", 90000, 1, 0, today.toEpochDay()),
            BillPaymentEntity(2, 2, "Internet", 2799, 1, 0, today.minusDays(40).toEpochDay())
        )
        val range = Stats.rangeFor(DateFilter.MONTH, today)
        assertEquals(90000L, Stats.sumPaidPayments(payments, range))
    }

    // ------------------------------------------------------------- security --

    @Test
    fun pin_isHashedAndVerifiable() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("1234", salt)
        assertTrue(hash.isNotBlank())
        assertNotEquals("1234", hash)
        assertTrue(PinHasher.verify("1234", salt, hash))
        assertFalse(PinHasher.verify("1235", salt, hash))
        assertFalse(PinHasher.verify("", salt, hash))
        // different salt -> different hash
        assertNotEquals(hash, PinHasher.hash("1234", PinHasher.newSalt()))
    }

    // --------------------------------------------------------------- backup --

    private fun samplePayload() = BackupPayload(
        categories = categories,
        expenses = expenses(),
        incomes = incomes(),
        bills = bills(LocalDate.of(2026, 9, 14)),
        payments = listOf(BillPaymentEntity(1, 1, "Miete", 90000, 1, 0, 1000)),
        rules = listOf(
            RecurringRuleEntity(
                id = 1,
                kind = RecurringKind.EXPENSE.name,
                name = "Miete",
                amountCents = 90000,
                categoryId = 1,
                frequency = Frequency.MONTHLY.name,
                interval = 1,
                dayOfMonth = 1,
                startDate = LocalDate.of(2026, 1, 1).toEpochDay(),
                active = true
            )
        ),
        budget = BudgetEntity(monthlyLimitCents = 200000, enabled = true),
        settings = SettingsEntity(currency = "EUR", currencySymbol = "€", appLockEnabled = true)
    )

    @Test
    fun backup_jsonRoundTripKeepsEveryField() {
        val payload = samplePayload()
        val json = Backup.toJson(payload).toString()
        val parsed = Backup.parse(json)!!
        assertEquals(payload.categories.size, parsed.categories.size)
        assertEquals(payload.expenses.size, parsed.expenses.size)
        assertEquals(payload.incomes.size, parsed.incomes.size)
        assertEquals(payload.bills.size, parsed.bills.size)
        assertEquals(payload.payments.size, parsed.payments.size)
        assertEquals(payload.rules.size, parsed.rules.size)
        assertEquals(200000L, parsed.budget!!.monthlyLimitCents)
        assertEquals("€", parsed.settings!!.currencySymbol)
        assertTrue(parsed.settings!!.appLockEnabled)

        assertEquals(90000L, parsed.expenses.first { it.id == 1L }.amountCents)
        assertEquals("Tanken", parsed.expenses.first { it.id == 3L }.note)
        assertEquals(1, parsed.rules.first().dayOfMonth)
        assertTrue(parsed.rules.first().active)
    }

    @Test
    fun backup_rejectsForeignFiles() {
        assertNull(Backup.parse("{\"app\":\"SomethingElse\",\"data\":{}}"))
        assertNull(Backup.parse("not json"))
    }

    // --------------------------------------------------------------- export --

    @Test
    fun export_csvContainsAllSections() {
        val today = LocalDate.of(2026, 9, 14)
        val request = Export.Request(
            label = "September 2026",
            from = LocalDate.of(2026, 9, 1),
            to = LocalDate.of(2026, 9, 30),
            expenses = expenses(),
            incomes = incomes(),
            bills = bills(today),
            categories = categories,
            symbol = "€"
        )
        val csv = Export.csv(request)
        assertTrue(csv.contains("ZUSAMMENFASSUNG"))
        assertTrue(csv.contains("AUSGABEN NACH KATEGORIE"))
        assertTrue(csv.contains("Miete"))
        assertTrue(csv.contains("900,00 €"))
        assertTrue(csv.contains("EINNAHMEN"))
        assertTrue(csv.contains("RECHNUNGEN"))
        assertTrue(csv.contains("Überfällig"))
    }

    // ---------------------------------------------------------------- dates --

    @Test
    fun dates_helpers() {
        val date = LocalDate.of(2026, 10, 1)
        assertEquals("01.10.", Dates.short(date))
        assertEquals("01.10.2026", Dates.full(date))
        assertEquals("2026-09", Dates.monthKey(LocalDate.of(2026, 9, 5)))
        assertEquals(0L, Dates.daysUntil(date, date))
        assertEquals("Morgen", Dates.relative(LocalDate.of(2026, 10, 2), date))
        assertEquals("Gestern", Dates.relative(LocalDate.of(2026, 9, 30), date))
        assertEquals("in 5 Tagen", Dates.relative(LocalDate.of(2026, 10, 6), date))
        assertEquals("vor 3 Tagen", Dates.relative(LocalDate.of(2026, 9, 28), date))
        assertEquals("09:05", Dates.formatTime(9, 5))
    }

    // ------------------------------------------------------- income summary --

    @Test
    fun incomeSummary_monthYearAndAverage() {
        val summary = Stats.incomeSummary(incomes(), LocalDate.of(2026, 9, 14))
        assertEquals(320000L, summary.monthCents)
        assertEquals(640000L, summary.yearCents)
        // 640.000 cents over 9 elapsed months
        assertEquals(640000L / 9, summary.averageMonthCents)
    }
}

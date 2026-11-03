package com.finovo.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.S
import com.finovo.app.util.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import com.finovo.app.util.AppLocale
import com.finovo.app.util.str

object Export {

    data class Request(
        val label: String,
        val from: LocalDate,
        val to: LocalDate,
        val expenses: List<ExpenseEntity>,
        val incomes: List<IncomeEntity>,
        val bills: List<BillEntity>,
        val categories: List<CategoryEntity>,
        val symbol: String
    )

    fun exportDir(context: Context): File =
        File(context.cacheDir, "export").apply { if (!exists()) mkdirs() }

    // --------------------------------- CSV ------------------------------------

    fun csv(request: Request, res: android.content.res.Resources? = null): String {
        val sb = StringBuilder()
        // UTF-8 BOM so Excel opens the file correctly
        sb.append('\uFEFF')
        sb.append(S(res, "exp_title", "Finovo – Export\n"))
        sb.append(S(res, "zeitraum_bis_n", "Zeitraum;%1\$s;bis;%2\$s\n", Dates.full(request.from), Dates.full(request.to)))
        sb.append(S(res, "exp_created_at", "Erstellt am;%1\$s\n\n", Dates.full(LocalDate.now())))

        val range = com.finovo.app.util.DateRange(
            request.from.toEpochDay(),
            request.to.toEpochDay()
        )
        val income = Stats.sumIncomes(request.incomes, range)
        val expense = Stats.sumExpenses(request.expenses, range)
        val (fixed, variable) = Stats.fixedVsVariable(request.expenses, request.categories, range)
        val names = request.categories.associateBy { it.id }

        sb.append("ZUSAMMENFASSUNG\n")
        sb.append(S(res, "einnahmen_n", "Einnahmen;%1\$s\n", Money.format(income, request.symbol)))
        sb.append(S(res, "ausgaben_n", "Ausgaben;%1\$s\n", Money.format(expense, request.symbol)))
        sb.append(S(res, "uebrig_n", "Übrig;%1\$s\n", Money.format(income - expense, request.symbol)))
        sb.append(S(res, "exp_fixed", "Fixkosten;%1\$s\n", Money.format(fixed, request.symbol)))
        sb.append(S(res, "exp_variable", "Variable Kosten;%1\$s\n\n", Money.format(variable, request.symbol)))

        sb.append(S(res, "exp_by_category", "AUSGABEN NACH KATEGORIE\n"))
        val totals = Stats.categoryTotals(request.expenses, request.categories, range)
        if (totals.isEmpty()) {
            sb.append(S(res, "exp_no_expenses", "Keine Ausgaben im Zeitraum\n"))
        } else {
            sb.append(S(res, "exp_csv_cat_header", "Kategorie;Betrag;Anteil\n"))
            totals.forEach {
                sb.append("${it.name};${Money.format(it.cents, request.symbol)};")
                sb.append(String.format(AppLocale.locale(), "%.1f%%", it.percent * 100))
                sb.append("\n")
            }
        }
        sb.append("\n")

        sb.append(S(res, "exp_expenses", "AUSGABEN\n"))
        sb.append(S(res, "exp_csv_exp_header", "Datum;Kategorie;Betrag;Bezahlt;Notiz\n"))
        val filteredExpenses = Stats.filterExpenses(request.expenses, range)
        if (filteredExpenses.isEmpty()) sb.append(S(res, "exp_no_entries", "Keine Einträge\n"))
        filteredExpenses.forEach {
            sb.append("${Dates.full(Dates.fromEpochDay(it.date))};")
            sb.append("${names[it.categoryId]?.name ?: S(res, "exp_other", "Sonstiges")};")
            sb.append("${Money.format(it.amountCents, request.symbol)};")
            sb.append("${if (it.paid) S(res, "csv_yes", "Ja") else S(res, "csv_no", "Nein")};")
            sb.append("\"${it.note.replace("\"", "'")}\"\n")
        }
        sb.append("\n")

        sb.append(S(res, "exp_income", "EINNAHMEN\n"))
        sb.append(S(res, "exp_csv_inc_header", "Datum;Quelle;Betrag;Notiz\n"))
        val filteredIncomes = Stats.filterIncomes(request.incomes, range)
        if (filteredIncomes.isEmpty()) sb.append(S(res, "exp_no_entries", "Keine Einträge\n"))
        filteredIncomes.forEach {
            sb.append("${Dates.full(Dates.fromEpochDay(it.date))};")
            sb.append("${names[it.categoryId]?.name ?: S(res, "exp_other_income", "Sonstige Einnahmen")};")
            sb.append("${Money.format(it.amountCents, request.symbol)};")
            sb.append("\"${it.note.replace("\"", "'")}\"\n")
        }
        sb.append("\n")

        sb.append(S(res, "exp_bills", "RECHNUNGEN\n"))
        sb.append(S(res, "exp_csv_bill_header", "Name;Fällig am;Betrag;Status;Kategorie\n"))
        if (request.bills.isEmpty()) sb.append(S(res, "exp_no_entries", "Keine Einträge\n"))
        request.bills.forEach {
            sb.append("${it.name};")
            sb.append("${Dates.full(Dates.fromEpochDay(it.dueDate))};")
            sb.append("${Money.format(it.amountCents, request.symbol)};")
            sb.append(
                "${
                    when {
                        it.status == BillStatus.PAID.name -> S(res, "bill_paid", "Bezahlt")
                        it.dueDate < LocalDate.now().toEpochDay() -> S(res, "overdue", "Überfällig")
                        else -> S(res, "bill_open", "Offen")
                    }
                };"
            )
            sb.append("${names[it.categoryId]?.name ?: "-"}\n")
        }
        return sb.toString()
    }

    // --------------------------------- analytics --------------------------------

    data class AnalyticsRequest(
        val label: String,
        val granularityLabel: String,
        val incomeCents: Long,
        val expenseCents: Long,
        val savingsCents: Long,
        val savingsRate: Float,
        val fixedCents: Long,
        val variableCents: Long,
        val buckets: List<com.finovo.app.util.Bucket>,
        val expenseCategories: List<com.finovo.app.util.CategoryStat>,
        val incomeCategories: List<com.finovo.app.util.CategoryStat>,
        val recurring: List<com.finovo.app.util.RecurringCost>,
        val recurringMonthlyCents: Long,
        val recurringYearlyCents: Long,
        val comparison: com.finovo.app.util.MonthComparison,
        val insights: List<com.finovo.app.util.Insight>,
        val budgetEnabled: Boolean,
        val budgetLimitCents: Long,
        val filterText: String,
        val symbol: String
    )

    private fun pct(value: Float): String =
        String.format(AppLocale.locale(), "%.1f%%", value)

    fun analyticsCsv(request: AnalyticsRequest, res: android.content.res.Resources? = null): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append(S(res, "exp_an_title", "Finovo – Finanzanalyse\n"))
        sb.append(S(res, "exp_period_row", "Zeitraum;%1\$s\n", request.label))
        if (request.filterText.isNotBlank()) sb.append(S(res, "exp_filter_row", "Filter;%1\$s\n", request.filterText))
        sb.append(S(res, "exp_created_at", "Erstellt am;%1\$s\n\n", Dates.full(LocalDate.now())))

        sb.append(S(res, "exp_kpi", "KENNZAHLEN\n"))
        sb.append(S(res, "exp_total_income", "Gesamteinnahmen;%1\$s\n", Money.format(request.incomeCents, request.symbol)))
        sb.append(S(res, "exp_total_expense", "Gesamtausgaben;%1\$s\n", Money.format(request.expenseCents, request.symbol)))
        sb.append(S(res, "uebrig_n_2", "Übrig;%1\$s\n", Money.format(request.savingsCents, request.symbol)))
        sb.append(S(res, "exp_savings_rate_row", "Sparquote;%1\$s\n", pct(request.savingsRate)))
        sb.append(S(res, "exp_fixed", "Fixkosten;%1\$s\n", Money.format(request.fixedCents, request.symbol)))
        sb.append(S(res, "exp_variable_row", "Variable Kosten;%1\$s\n", Money.format(request.variableCents, request.symbol)))
        if (request.budgetEnabled && request.budgetLimitCents > 0) {
            sb.append(S(res, "exp_budget_row", "Budget;%1\$s\n", Money.format(request.budgetLimitCents, request.symbol)))
            sb.append(S(res, "exp_budget_left_row", "Budget übrig;%1\$s\n", Money.format(request.budgetLimitCents - request.expenseCents, request.symbol)))
        }
        sb.append("\n")

        if (request.buckets.isNotEmpty()) {
            sb.append(S(res, "exp_trend_header", "VERLAUF (%1\$s)\n", request.granularityLabel))
            sb.append(S(res, "exp_trend_cols", "Zeitraum;Einnahmen;Ausgaben;Übrig\n"))
            request.buckets.forEach { b ->
                sb.append("${Dates.full(b.start)}")
                if (b.end != b.start) sb.append(" - ${Dates.full(b.end)}")
                sb.append(";${Money.format(b.incomeCents, request.symbol)}")
                sb.append(";${Money.format(b.expenseCents, request.symbol)}")
                sb.append(";${Money.format(b.netCents, request.symbol)}\n")
            }
            sb.append("\n")
        }

        if (request.expenseCategories.isNotEmpty()) {
            sb.append(S(res, "exp_by_category", "AUSGABEN NACH KATEGORIE\n"))
            sb.append(S(res, "exp_csv_cat_header2", "Kategorie;Betrag;Anteil;Buchungen\n"))
            request.expenseCategories.forEach {
                sb.append("${it.name};${Money.format(it.cents, request.symbol)};${pct(it.percent * 100)};${it.count}\n")
            }
            sb.append("\n")
        }

        if (request.incomeCategories.isNotEmpty()) {
            sb.append(S(res, "exp_income_by_source", "EINNAHMEN NACH QUELLE\n"))
            sb.append(S(res, "exp_csv_src_header", "Quelle;Betrag;Anteil;Buchungen\n"))
            request.incomeCategories.forEach {
                sb.append("${it.name};${Money.format(it.cents, request.symbol)};${pct(it.percent * 100)};${it.count}\n")
            }
            sb.append("\n")
        }

        if (request.recurring.isNotEmpty()) {
            sb.append(S(res, "exp_recurring_costs", "WIEDERKEHRENDE KOSTEN\n"))
            sb.append(S(res, "exp_csv_rec_header", "Zahlung;Betrag;Intervall;pro Monat;pro Jahr\n"))
            request.recurring.forEach {
                sb.append("${it.name};${Money.format(it.amountCents, request.symbol)};${it.frequency};")
                sb.append("${Money.format(it.monthlyCents, request.symbol)};${Money.format(it.yearlyCents, request.symbol)}\n")
            }
            sb.append(S(res, "gesamt_pro_monat", "Gesamt;%1\$s (pro Monat);", Money.format(request.recurringMonthlyCents, request.symbol)))
            sb.append(S(res, "pro_jahr_n_n", "%1\$s (pro Jahr)\n\n", Money.format(request.recurringYearlyCents, request.symbol)))
        }

        sb.append(S(res, "exp_compare_prev", "VERGLEICH VORMONAT\n"))
        val c = request.comparison
        sb.append(S(res, "einnahmen_n_2", "Einnahmen %1\$s;%2\$s\n", c.previousLabel, Money.format(c.previousIncomeCents, request.symbol)))
        sb.append(S(res, "einnahmen_n_3", "Einnahmen %1\$s;%2\$s\n", c.currentLabel, Money.format(c.currentIncomeCents, request.symbol)))
        sb.append(S(res, "veraenderung_einnahmen_n", "Veränderung Einnahmen;%1\$s\n", Money.format(c.incomeDeltaCents, request.symbol)))
        sb.append(S(res, "ausgaben_n_2", "Ausgaben %1\$s;%2\$s\n", c.previousLabel, Money.format(c.previousExpenseCents, request.symbol)))
        sb.append(S(res, "ausgaben_n_3", "Ausgaben %1\$s;%2\$s\n", c.currentLabel, Money.format(c.currentExpenseCents, request.symbol)))
        sb.append(S(res, "veraenderung_ausgaben_n_n", "Veränderung Ausgaben;%1\$s\n\n", Money.format(c.expenseDeltaCents, request.symbol)))

        if (request.insights.isNotEmpty()) {
            sb.append("ERKENNTNISSE\n")
            request.insights.forEach { sb.append("${it.emoji} ${it.text}\n") }
        }
        return sb.toString()
    }

    fun analyticsSummary(request: AnalyticsRequest, res: android.content.res.Resources? = null): String {
        val sb = StringBuilder()
        sb.append(S(res, "exp_an_title", "Finovo – Finanzanalyse\n"))
        sb.append(S(res, "exp_pdf_period", "Zeitraum: %1\$s\n", request.label))
        if (request.filterText.isNotBlank()) sb.append(S(res, "exp_pdf_filter", "Filter: %1\$s\n", request.filterText))
        sb.append("\n")
        sb.append(S(res, "einnahmen_n_4", "Einnahmen: %1\$s\n", Money.format(request.incomeCents, request.symbol)))
        sb.append(S(res, "ausgaben_n_4", "Ausgaben:  %1\$s\n", Money.format(request.expenseCents, request.symbol)))
        sb.append(S(res, "uebrig_n_3", "Übrig:     %1\$s\n", Money.format(request.savingsCents, request.symbol)))
        sb.append(S(res, "exp_pdf_savings", "Sparquote: %1\$s\n", pct(request.savingsRate)))
        sb.append(S(res, "exp_pdf_fixed", "Fixkosten: %1\$s\n", Money.format(request.fixedCents, request.symbol)))
        sb.append(S(res, "exp_pdf_variable", "Variable:  %1\$s\n", Money.format(request.variableCents, request.symbol)))
        if (request.budgetEnabled && request.budgetLimitCents > 0) {
            sb.append(S(res, "budget_von", "Budget:    %1\$s von %2\$s\n", Money.format(request.expenseCents, request.symbol), Money.format(request.budgetLimitCents, request.symbol)))
            sb.append(S(res, "recurring_month_year", "\nWiederkehrend: %1\$s/Monat · %2\$s/Jahr\n", Money.format(request.recurringMonthlyCents, request.symbol), Money.format(request.recurringYearlyCents, request.symbol)))
        }
        if (request.expenseCategories.isNotEmpty()) {
            sb.append("\nTop-Kategorien:\n")
            request.expenseCategories.take(5).forEach {
                sb.append("• ${it.emoji} ${it.name}: ${Money.format(it.cents, request.symbol)} (${pct(it.percent * 100)})\n")
            }
        }
        if (request.recurring.isNotEmpty()) {
            sb.append(S(res, "recurring_month_year", "\nWiederkehrend: %1\$s/Monat · %2\$s/Jahr\n", Money.format(request.recurringMonthlyCents, request.symbol), Money.format(request.recurringYearlyCents, request.symbol)))

        }
        if (request.insights.isNotEmpty()) {
            sb.append("\nErkenntnisse:\n")
            request.insights.forEach { sb.append("${it.emoji} ${it.text}\n") }
        }
        sb.append(S(res, "exp_pdf_footer", "\nErstellt am %1\$s – offline auf dem Gerät.", Dates.full(LocalDate.now())))
        return sb.toString()
    }

    fun writeAnalyticsPdf(output: OutputStream, request: AnalyticsRequest, res: android.content.res.Resources? = null) {
        val document = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page = newPage(document, ++pageNumber)
        var canvas = page.canvas
        var y = MARGIN

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; textSize = 20f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY; textSize = 10.5f
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2563EB"); textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; textSize = 10f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK; textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#16A34A"); textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#DC2626"); textSize = 10.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#DDDDDD"); strokeWidth = 1f
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN - 30f) {
                drawFooter(page.canvas, pageNumber)
                document.finishPage(page)
                page = newPage(document, ++pageNumber)
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun keyValue(label: String, value: String, paint: Paint = boldPaint) {
            ensureSpace(18f)
            canvas.drawText(label, MARGIN, y, bodyPaint)
            val w = paint.measureText(value)
            canvas.drawText(value, PAGE_WIDTH - MARGIN - w, y, paint)
            y += 16f
        }

        canvas.drawText(S(res, "finovo_finanzanalyse", "Finovo – Finanzanalyse"), MARGIN, y + 16f, titlePaint)
        y += 32f
        canvas.drawText(S(res, "exp_pdf_period2", "Zeitraum: %1\$s", request.label), MARGIN, y, subtitlePaint)
        y += 14f
        if (request.filterText.isNotBlank()) {
            canvas.drawText(S(res, "exp_pdf_filter2", "Filter: %1\$s", request.filterText), MARGIN, y, subtitlePaint)
            y += 14f
        }
        canvas.drawText(S(res, "exp_pdf_created", "Erstellt am %1\$s", Dates.full(LocalDate.now())), MARGIN, y, subtitlePaint)
        y += 22f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 22f

        canvas.drawText(S(res, "kennzahlen", "Kennzahlen"), MARGIN, y, headingPaint)
        y += 18f
        keyValue(S(res, "gesamteinnahmen", "Gesamteinnahmen"), Money.format(request.incomeCents, request.symbol), greenPaint)
        keyValue(S(res, "gesamtausgaben", "Gesamtausgaben"), Money.format(request.expenseCents, request.symbol), redPaint)
        keyValue(S(res, "uebrig", "Übrig"), Money.format(request.savingsCents, request.symbol))
        keyValue(S(res, "sparquote", "Sparquote"), pct(request.savingsRate))
        keyValue(S(res, "fixkosten", "Fixkosten"), Money.format(request.fixedCents, request.symbol))
        keyValue(S(res, "variable_kosten", "Variable Kosten"), Money.format(request.variableCents, request.symbol))
        if (request.budgetEnabled && request.budgetLimitCents > 0) {
            keyValue(S(res, "budget", "Budget"), Money.format(request.budgetLimitCents, request.symbol))
            keyValue(S(res, "budget_uebrig", "Budget übrig"), Money.format(request.budgetLimitCents - request.expenseCents, request.symbol))
            keyValue(S(res, "wiederkehrend_monat", "Wiederkehrend / Monat"), Money.format(request.recurringMonthlyCents, request.symbol))
        }

        if (request.buckets.isNotEmpty()) {
            y += 8f
            ensureSpace(60f)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f
            canvas.drawText(S(res, "verlauf_pro", "Verlauf pro %1\$s", request.granularityLabel), MARGIN, y, headingPaint)
            y += 18f
            request.buckets.take(60).forEach { bucket ->
                ensureSpace(16f)
                val range = Dates.full(bucket.start) +
                    if (bucket.end != bucket.start) " – ${Dates.full(bucket.end)}" else ""
                canvas.drawText(range, MARGIN, y, bodyPaint)
                val inText = Money.format(bucket.incomeCents, request.symbol)
                val outText = Money.format(bucket.expenseCents, request.symbol)
                canvas.drawText(inText, MARGIN + 230f, y, greenPaint)
                val w = redPaint.measureText(outText)
                canvas.drawText(outText, PAGE_WIDTH - MARGIN - w, y, redPaint)
                y += 15f
            }
        }

        if (request.expenseCategories.isNotEmpty()) {
            y += 8f
            ensureSpace(70f)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f
            canvas.drawText(S(res, "ausgaben_nach_kategorie", "Ausgaben nach Kategorie"), MARGIN, y, headingPaint)
            y += 18f
            val max = request.expenseCategories.maxOf { it.cents }.coerceAtLeast(1)
            val barMax = PAGE_WIDTH - 2 * MARGIN - 170f
            request.expenseCategories.forEachIndexed { index, stat ->
                ensureSpace(20f)
                val label = "${stat.emoji} ${stat.name}"
                canvas.drawText(
                    if (label.length > 26) label.take(25) + "…" else label,
                    MARGIN, y, bodyPaint
                )
                val width = (stat.cents.toFloat() / max.toFloat()) * barMax
                barPaint.color = android.graphics.Color.parseColor("#2563EB")
                canvas.drawRect(MARGIN + 165f, y - 7f, MARGIN + 165f + width, y + 1f, barPaint)
                val value = "${Money.format(stat.cents, request.symbol)} (${pct(stat.percent * 100)})"
                val w = boldPaint.measureText(value)
                canvas.drawText(value, PAGE_WIDTH - MARGIN - w, y, boldPaint)
                y += 18f
            }
        }

        if (request.incomeCategories.isNotEmpty()) {
            y += 8f
            ensureSpace(60f)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f
            canvas.drawText(S(res, "einnahmen_nach_quelle", "Einnahmen nach Quelle"), MARGIN, y, headingPaint)
            y += 18f
            request.incomeCategories.forEach { stat ->
                ensureSpace(16f)
                canvas.drawText("${stat.emoji} ${stat.name}", MARGIN, y, bodyPaint)
                val value = "${Money.format(stat.cents, request.symbol)} (${pct(stat.percent * 100)})"
                val w = boldPaint.measureText(value)
                canvas.drawText(value, PAGE_WIDTH - MARGIN - w, y, boldPaint)
                y += 16f
            }
        }

        if (request.recurring.isNotEmpty()) {
            y += 8f
            ensureSpace(60f)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f
            canvas.drawText(S(res, "wiederkehrende_kosten", "Wiederkehrende Kosten"), MARGIN, y, headingPaint)
            y += 18f
            request.recurring.forEach { cost ->
                ensureSpace(16f)
                canvas.drawText(cost.name, MARGIN, y, bodyPaint)
                val value = S(res, "month_year_value", "%1\$s/Monat · %2\$s/Jahr", Money.format(cost.monthlyCents, request.symbol), Money.format(cost.yearlyCents, request.symbol))
                val w = boldPaint.measureText(value)
                canvas.drawText(value, PAGE_WIDTH - MARGIN - w, y, boldPaint)
                y += 16f
            }
        }

        y += 8f
        ensureSpace(70f)
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 20f
        canvas.drawText(S(res, "vergleich_zum_vormonat", "Vergleich zum Vormonat"), MARGIN, y, headingPaint)
        y += 18f
        val c = request.comparison
        keyValue(S(res, "exp_income_label", "Einnahmen %1\$s", c.previousLabel), Money.format(c.previousIncomeCents, request.symbol))
        keyValue(S(res, "exp_income_label", "Einnahmen %1\$s", c.currentLabel), Money.format(c.currentIncomeCents, request.symbol))
        keyValue(S(res, "veraenderung", "Veränderung"), Money.format(c.incomeDeltaCents, request.symbol))
        keyValue(S(res, "exp_expense_label", "Ausgaben %1\$s", c.previousLabel), Money.format(c.previousExpenseCents, request.symbol))
        keyValue(S(res, "exp_expense_label", "Ausgaben %1\$s", c.currentLabel), Money.format(c.currentExpenseCents, request.symbol))
        keyValue(S(res, "veraenderung_2", "Veränderung"), Money.format(c.expenseDeltaCents, request.symbol))

        if (request.insights.isNotEmpty()) {
            y += 8f
            ensureSpace(60f)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f
            canvas.drawText(S(res, "erkenntnisse", "Erkenntnisse"), MARGIN, y, headingPaint)
            y += 18f
            request.insights.forEach { insight ->
                ensureSpace(30f)
                val text = "${insight.emoji} ${insight.text}"
                val words = text.split(" ")
                var line = ""
                for (word in words) {
                    if (bodyPaint.measureText("$line $word") > PAGE_WIDTH - 2 * MARGIN) {
                        ensureSpace(14f)
                        canvas.drawText(line, MARGIN, y, bodyPaint)
                        y += 14f
                        line = word
                    } else {
                        line = if (line.isEmpty()) word else "$line $word"
                    }
                }
                if (line.isNotEmpty()) {
                    ensureSpace(14f)
                    canvas.drawText(line, MARGIN, y, bodyPaint)
                    y += 14f
                }
                y += 4f
            }
        }

        drawFooter(page.canvas, pageNumber)
        document.finishPage(page)
        document.writeTo(output)
        document.close()
    }

    suspend fun createAnalyticsCsvFile(context: Context, request: AnalyticsRequest): File =
        withContext(Dispatchers.IO) {
            val file = File(exportDir(context), "Finovo_Analyse_${LocalDate.now()}.csv")
            file.writeText(analyticsCsv(request, context.resources), Charsets.UTF_8)
            file
        }

    suspend fun createAnalyticsPdfFile(context: Context, request: AnalyticsRequest): File =
        withContext(Dispatchers.IO) {
            val file = File(exportDir(context), "Finovo_Analyse_${LocalDate.now()}.pdf")
            file.outputStream().use { writeAnalyticsPdf(it, request) }
            file
        }

    suspend fun createCsvFile(context: Context, request: Request): File =
        withContext(Dispatchers.IO) {
            val file = File(exportDir(context), fileName(request, "csv"))
            file.writeText(csv(request, context.resources), Charsets.UTF_8)
            file
        }

    // --------------------------------- PDF ------------------------------------

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44f

    fun writePdf(output: OutputStream, request: Request, res: android.content.res.Resources? = null) {
        val document = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page = newPage(document, ++pageNumber)
        var canvas = page.canvas
        var y = MARGIN

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.DKGRAY
            textSize = 11f
        }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2563EB")
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 10.5f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#DDDDDD")
            strokeWidth = 1f
        }
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#16A34A")
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#DC2626")
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2563EB")
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN - 30f) {
                drawFooter(page.canvas, pageNumber)
                document.finishPage(page)
                page = newPage(document, ++pageNumber)
                canvas = page.canvas
                y = MARGIN
            }
        }

        // ---------------------------- header ---------------------------------
        canvas.drawText(S(res, "finovo_finanzbericht", "Finovo – Finanzbericht"), MARGIN, y + 18f, titlePaint)
        y += 36f
        canvas.drawText(S(res, "exp_pdf_period2", "Zeitraum: %1\$s", request.label), MARGIN, y, subtitlePaint)
        y += 15f
        canvas.drawText(
            S(res, "exp_pdf_created", "Erstellt am %1\$s", Dates.full(LocalDate.now())),
            MARGIN, y, subtitlePaint
        )
        y += 22f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 26f

        val range = com.finovo.app.util.DateRange(
            request.from.toEpochDay(),
            request.to.toEpochDay()
        )
        val income = Stats.sumIncomes(request.incomes, range)
        val expense = Stats.sumExpenses(request.expenses, range)
        val remaining = income - expense
        val (fixed, variable) = Stats.fixedVsVariable(request.expenses, request.categories, range)
        val names = request.categories.associateBy { it.id }

        // ---------------------------- summary --------------------------------
        canvas.drawText(S(res, "zusammenfassung", "Zusammenfassung"), MARGIN, y, headingPaint)
        y += 20f
        val rows = listOf(
            S(res, "einnahmen", "Einnahmen") to Money.format(income, request.symbol),
            S(res, "ausgaben", "Ausgaben") to Money.format(expense, request.symbol),
            S(res, "uebrig_2", "Übrig") to Money.format(remaining, request.symbol),
            S(res, "fixkosten_2", "🔒 Fixkosten") to Money.format(fixed, request.symbol),
            S(res, "variable_kosten_2", "Variable Kosten") to Money.format(variable, request.symbol),
            S(res, "ausgaben_pro_monat", "Ø Ausgaben pro Monat") to Money.format(
                Stats.monthlyAverage(expense, range),
                request.symbol
            )
        )
        rows.forEach { (label, value) ->
            ensureSpace(20f)
            canvas.drawText(label, MARGIN, y, bodyPaint)
            val paint = when (label) {
                S(res, "einnahmen_2", "Einnahmen") -> greenPaint
                S(res, "ausgaben_2", "Ausgaben") -> redPaint
                else -> boldPaint
            }
            val width = paint.measureText(value)
            canvas.drawText(value, PAGE_WIDTH - MARGIN - width, y, paint)
            y += 18f
        }
        y += 14f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 22f

        // ------------------------- category chart -----------------------------
        val totals = Stats.categoryTotals(request.expenses, request.categories, range)
        canvas.drawText(S(res, "ausgaben_nach_kategorie_2", "Ausgaben nach Kategorie"), MARGIN, y, headingPaint)
        y += 20f
        if (totals.isEmpty()) {
            canvas.drawText(S(res, "keine_ausgaben_im_zeitraum", "Keine Ausgaben im Zeitraum."), MARGIN, y, bodyPaint)
            y += 18f
        } else {
            val max = totals.maxOf { it.cents }.coerceAtLeast(1)
            val barMaxWidth = PAGE_WIDTH - 2 * MARGIN - 170f
            totals.take(10).forEach { total ->
                ensureSpace(22f)
                val label = "${total.emoji} ${total.name}"
                canvas.drawText(
                    if (label.length > 26) label.take(25) + "…" else label,
                    MARGIN, y, bodyPaint
                )
                val barWidth = (total.cents.toFloat() / max.toFloat()) * barMaxWidth
                barPaint.color = android.graphics.Color.parseColor("#2563EB")
                canvas.drawRect(MARGIN + 165f, y - 8f, MARGIN + 165f + barWidth, y + 1f, barPaint)
                val value = Money.format(total.cents, request.symbol)
                val w = boldPaint.measureText(value)
                canvas.drawText(value, PAGE_WIDTH - MARGIN - w, y, boldPaint)
                y += 20f
            }
        }
        y += 14f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
        y += 22f

        // -------------------------- transactions ------------------------------
        canvas.drawText(S(res, "buchungen", "Buchungen"), MARGIN, y, headingPaint)
        y += 20f
        val entries = (
            Stats.filterExpenses(request.expenses, range).map {
                Triple(
                    Dates.fromEpochDay(it.date),
                    names[it.categoryId]?.name ?: S(res, "sonstiges", "Sonstiges"),
                    it.amountCents to false
                )
            } +
                Stats.filterIncomes(request.incomes, range).map {
                    Triple(
                        Dates.fromEpochDay(it.date),
                        names[it.categoryId]?.name ?: S(res, "sonstige_einnahmen", "Sonstige Einnahmen"),
                        it.amountCents to true
                    )
                }
            ).sortedByDescending { it.first.toEpochDay() }

        if (entries.isEmpty()) {
            canvas.drawText(S(res, "keine_buchungen_im_zeitraum", "Keine Buchungen im Zeitraum."), MARGIN, y, bodyPaint)
            y += 18f
        }
        entries.forEach { (date, name, amountPair) ->
            ensureSpace(18f)
            val (cents, isIncome) = amountPair
            canvas.drawText(Dates.full(date), MARGIN, y, bodyPaint)
            canvas.drawText(
                if (name.length > 30) name.take(29) + "…" else name,
                MARGIN + 95f, y, bodyPaint
            )
            val value = "${if (isIncome) "+" else "−"}${Money.format(cents, request.symbol)}"
            val paint = if (isIncome) greenPaint else redPaint
            val w = paint.measureText(value)
            canvas.drawText(value, PAGE_WIDTH - MARGIN - w, y, paint)
            y += 17f
        }

        // ----------------------------- bills -----------------------------------
        if (request.bills.isNotEmpty()) {
            y += 10f
            ensureSpace(60f)
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 22f
            canvas.drawText(S(res, "rechnungen", "Rechnungen"), MARGIN, y, headingPaint)
            y += 20f
            request.bills.forEach { bill ->
                ensureSpace(18f)
                val status = when {
                    bill.status == BillStatus.PAID.name -> S(res, "bezahlt", "Bezahlt")
                    bill.dueDate < LocalDate.now().toEpochDay() -> S(res, "ueberfaellig", "Überfällig")
                    else -> S(res, "pill_open", "Offen")
                }
                canvas.drawText(bill.name, MARGIN, y, bodyPaint)
                canvas.drawText(Dates.full(Dates.fromEpochDay(bill.dueDate)), MARGIN + 220f, y, bodyPaint)
                val value = Money.format(bill.amountCents, request.symbol)
                val w = boldPaint.measureText(value)
                canvas.drawText(value, PAGE_WIDTH - MARGIN - 80f - w, y, boldPaint)
                canvas.drawText(status, PAGE_WIDTH - MARGIN - 72f, y, bodyPaint)
                y += 17f
            }
        }

        // ----------------------------- footer -----------------------------------
        drawFooter(page.canvas, pageNumber)
        document.finishPage(page)

        document.writeTo(output)
        document.close()
    }

    private fun newPage(document: PdfDocument, number: Int): PdfDocument.Page =
        document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
        )

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.GRAY
            textSize = 9f
        }
        canvas.drawText(
            str("exp_pdf_page_footer", "Finovo · Seite %1\$s · lokal & offline erstellt", pageNumber),
            MARGIN,
            PAGE_HEIGHT - 26f,
            paint
        )
    }

    suspend fun createPdfFile(context: Context, request: Request): File =
        withContext(Dispatchers.IO) {
            val file = File(exportDir(context), fileName(request, "pdf"))
            file.outputStream().use { writePdf(it, request) }
            file
        }

    /** Dateiname fuer den CSV-Export im Datei-Dialog. */
    fun csvFileName(): String = "Finovo_Export_${LocalDate.now()}.csv"

    /** Dateiname fuer den PDF-Export im Datei-Dialog. */
    fun pdfFileName(): String = "Finovo_Bericht_${LocalDate.now()}.pdf"

    fun fileName(request: Request, extension: String): String {
        val safeLabel = request.label.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        return "Finovo_${safeLabel}_${LocalDate.now()}.$extension"
    }
}

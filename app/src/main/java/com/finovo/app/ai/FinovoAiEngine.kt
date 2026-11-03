package com.finovo.app.ai

import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.str
import java.util.Locale

/**
 * Finovo AI - die Antwort-Engine auf dem Gerät.
 *
 * Arbeitsweise (streng getrennt, genau wie gefordert):
 *
 *   Finovo-Datenbank -> FinancialSummaryService -> FinancialSummary -> hier
 *   -> AiAnswer -> FinovoAiScreen
 *
 * Diese Klasse kennt weder die Datenbank noch ein DAO. Sie bekommt nur die
 * bereits berechnete [FinancialSummary] und erzeugt daraus Antworten. Deshalb
 * ist sie konstruktiv schreibgeschützt und kann keine Finanzdaten erfinden:
 * jeder ausgegebene Betrag stammt aus der Zusammenfassung.
 *
 * Die Engine läuft vollständig offline. Sie ist deterministisch - dieselbe
 * Frage bei denselben Daten liefert dieselbe Antwort.
 */
object FinovoAiEngine {

    // ------------------------------------------------------------- Einstieg

    fun answer(
        summary: FinancialSummary,
        question: String,
        language: String = com.finovo.app.util.AppLocale.current(),
        context: AiContext? = null,
        kategorie: String? = null
    ): AiAnswer {
        if (!summary.hasEnoughData) {
            return AiAnswer(
                blocks = listOf(
                    AiBlock.Text(str("ai_not_enough_data", "Ich habe noch nicht genügend Daten für eine zuverlässige Analyse."))
                ),
                intent = AiIntent.GENERAL,
                hasEnoughData = false
            )
        }

        // Modul 27: Rückfragen ("Und wie viel davon für Auto?") auflösen.
        val aufgeloest = context?.aufloesen(question)
        val intent = aufgeloest?.absicht ?: detectIntent(question, language)
        val gesuchteKategorie = kategorie ?: aufgeloest?.kategorie

        val blocks = when (intent) {
            AiIntent.SAVE_MONEY -> saving(summary)
            AiIntent.WHERE_MONEY_GOES -> whereMoneyGoes(summary)
            AiIntent.OPEN_BILLS -> openBills(summary)
            AiIntent.UPCOMING_PAYMENTS -> upcoming(summary)
            AiIntent.MONTH_ANALYSIS -> monthAnalysis(summary)
            AiIntent.MONTH_COMPARISON -> monthComparison(summary)
            AiIntent.RECURRING_PAYMENTS -> recurringPayments(summary)
            AiIntent.TOTAL_SPENT -> gesamtAusgaben(summary)
            AiIntent.CATEGORY_AMOUNT -> betragDerKategorie(summary, gesuchteKategorie)
            AiIntent.LARGEST_EXPENSE -> groessteAusgabe(summary)
            AiIntent.GENERAL -> general(summary)
        }

        // Für die nächste Rückfrage merken.
        context?.merken(
            frage = question,
            absicht = intent,
            kategorie = gesuchteKategorie,
            monat = summary.month.label
        )
        return AiAnswer(blocks = blocks, intent = intent, hasEnoughData = true)
    }

    // ------------------------ Modul 27: Rückfragen ---------------------------

    /** "Wie viel habe ich diesen Monat ausgegeben?" -> kurze, klare Antwort. */
    private fun gesamtAusgaben(s: FinancialSummary): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Text(
            str(
                "ai_total_spent",
                "Du hast bisher %1\$s ausgegeben.",
                Money.format(s.month.expenseCents, s.symbol)
            ), AiSection.FACTS
        )
        if (s.month.incomeCents > 0) {
            out += AiBlock.Row(
                str("ai_remaining", "Übrig"),
                Money.format(s.month.remainingCents, s.symbol), AiSection.CALCULATED
            )
        }
        return out
    }

    /** "Und wie viel davon für Auto?" -> Betrag genau dieser Kategorie. */
    private fun betragDerKategorie(s: FinancialSummary, gesucht: String?): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        if (gesucht.isNullOrBlank()) {
            out += AiBlock.Heading(str("ai_category_not_told", "Für welche Kategorie soll ich nachsehen?"))
            val top = s.month.categories.take(5)
            if (top.isNotEmpty()) {
                out += AiBlock.Text(str("ai_categories_available", "Erfasste Kategorien in diesem Monat:"))
                top.forEach { c ->
                    out += AiBlock.Row(
                        "${c.emoji} ${c.name}".trim(),
                        Money.format(c.cents, s.symbol), AiSection.FACTS
                    )
                }
            }
            return out
        }

        val name = gesucht.trim().trim('?', '!', '.')
        val kategorie = s.month.categories.firstOrNull { passtZu(it.name, name) }
            ?: s.month.categories.firstOrNull { passtZu(name, it.name) }

        if (kategorie == null || kategorie.cents == 0L) {
            out += AiBlock.Text(
                str(
                    "ai_category_none",
                    "Für „%1\$s“ wurden im %2\$s keine Ausgaben erfasst.",
                    name, s.month.label
                ), AiSection.FACTS
            )
            return out
        }

        val anzahl = s.ausgabenMonat.count { passtZu(it.categoryName, name) }
        out += AiBlock.Text(
            str(
                "ai_category_amount",
                "Für %1\$s-bezogene Ausgaben wurden %2\$s erfasst.",
                kategorie.name, Money.format(kategorie.cents, s.symbol)
            ), AiSection.FACTS
        )
        out += AiBlock.Row(
            str("ai_bookings", "Buchungen"), anzahl.toString(), AiSection.FACTS
        )
        val anteil = if (s.month.expenseCents > 0) {
            (kategorie.cents * 100) / s.month.expenseCents
        } else 0L
        out += AiBlock.Row(
            str("ai_share_of_expenses", "Anteil an den Ausgaben"), "$anteil %", AiSection.CALCULATED
        )
        return out
    }

    /** "Was war die größte Ausgabe?" */
    private fun groessteAusgabe(s: FinancialSummary): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        val groesste = s.groessteAusgabe
        if (groesste == null) {
            out += AiBlock.Text(
                str("ai_no_expenses_yet", "Für diesen Monat sind noch keine Ausgaben erfasst.")
            )
            return out
        }
        val name = groesste.name.ifBlank { groesste.categoryName.ifBlank { "Ausgabe" } }
        out += AiBlock.Text(
            str(
                "ai_largest_expense",
                "Die größte erfasste Ausgabe war %1\$s für %2\$s.",
                Money.format(groesste.amountCents, s.symbol), name
            ), AiSection.FACTS
        )
        out += AiBlock.Row(
            str("ai_category", "Kategorie"),
            groesste.categoryName.ifBlank { "–" }, AiSection.FACTS
        )
        out += AiBlock.Row(
            str("ai_date", "Datum"), Dates.full(groesste.date), AiSection.FACTS
        )
        return out
    }

    /** Zwei Namen gelten als gleich, wenn einer im anderen steckt. */
    private fun passtZu(a: String, b: String): Boolean {
        val x = a.lowercase(Locale.ROOT).trim()
        val y = b.lowercase(Locale.ROOT).trim()
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || x.contains(y) || y.contains(x)
    }

    // ------------------------------------------------------ Absicht erkennen

    /**
     * Erkennt die Absicht aus der Frage - in allen fünf Sprachen.
     * Die Reihenfolge ist wichtig: "Rechnungen offen" darf nicht als
     * "wo geht mein Geld hin" erkannt werden.
     */
    fun detectIntent(question: String, language: String): AiIntent {
        val q = question.lowercase(Locale.ROOT).trim()

        fun treffer(vararg worte: String): Boolean = worte.any { q.contains(it) }

        // 1. Offene Rechnungen
        if (treffer(
                "offene rechnung", "rechnungen offen", "offenen rechnung", "unbezahlt",
                "unbezahlte", "rechnung offen", "rechnung", "rechnungen",
                "open invoice", "unpaid", "open bill", "invoice", "bill",
                "fatture aperte", "fattura aperta", "non pagat", "fattur",
                "fatura e hapur", "fatur", "të papaguar", "papaguar",
                "açık fatura", "odenmemis", "ödenmemiş", "fatura"
            )
        ) return AiIntent.OPEN_BILLS

        // 2. Bald fällige Zahlungen
        if (treffer(
                "bald bezahl", "bald fällig", "demnächst", "naechste zahlung", "nächste zahlung",
                "was muss ich", "fälligkeit", "due soon", "upcoming", "next payment", "pay soon",
                "prossimi pagamenti", "in scadenza", "pagesat e ardhshme", "afat",
                "yaklaşan ödeme", "gelecek ödeme", "vade"
            )
        ) return AiIntent.UPCOMING_PAYMENTS

        // 3. Monatsvergleich
        if (treffer(
                "vergleich", "vergleiche", "letzten monat", "letzter monat", "vorherigen monat",
                "compare", "previous month", "last month", "confronta", "mese precedente",
                "krahaso", "muaji i kaluar", "karşılaştır", "geçen ay", "gecen ay"
            )
        ) return AiIntent.MONTH_COMPARISON

        // 4. Monatsanalyse
        if (treffer(
                "analysiere", "analyse", "monatsbericht", "monat analys", "bericht",
                "analyze", "analyse my month", "monthly", "analizza", "analisi",
                "analizo", "analiz", "analiz et"
            )
        ) return AiIntent.MONTH_ANALYSIS

        // 5. Sparen
        if (treffer(
                "sparen", "einsparen", "sparpotenzial", "weniger ausgeben", "spartipp",
                "save", "saving", "reduce", "cut spend", "risparmi", "ridurre",
                "kursej", "kursim", "tasarruf", "biriktir"
            )
        ) return AiIntent.SAVE_MONEY

        // 6. Wiederkehrende Zahlungen
        if (treffer(
                "wiederkehrend", "wiederholend", "abo", "abonnement", "regelmäßig",
                "recurring", "subscription", "ricorrent", "abbonament",
                "përsërit", "abonim", "tekrarlayan", "abonelik", "düzenli"
            )
        ) return AiIntent.RECURRING_PAYMENTS

        // 7. Summe der Ausgaben ("Wie viel habe ich diesen Monat ausgegeben?")
        if (treffer(
                "wie viel habe ich", "wieviel habe ich", "wie viel hab ich",
                "wie hoch waren meine ausgaben", "summe meiner ausgaben", "gesamtkosten",
                "how much have i spent", "how much did i spend", "total spent",
                "quanto ho speso", "quale è la mia spesa", "totale spese",
                "sa kam shpenzuar", "sa shpenzova",
                "ne kadar harcadım", "toplam harcama"
            )
        ) return AiIntent.TOTAL_SPENT

        // 8. Wohin das Geld geht
        if (treffer(
                "wohin", "wo geht", "geld hin", "geldhin", "wohin geht", "ausgaben",
                "where", "spending", "spend", "money go", "dove", "spese",
                "ku shkojn", "shpenzim", "nereye", "harcama", "para"
            )
        ) return AiIntent.WHERE_MONEY_GOES

        return AiIntent.GENERAL
    }

    // ---------------------------------------------------------- Antworten

    private fun whereMoneyGoes(s: FinancialSummary): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Heading(
            str("ai_where_title", "Wohin dein Geld geht"), "\uD83D\uDCCA"
        )
        if (s.month.expenseCents == 0L) {
            out += AiBlock.Text(str("ai_where_no_expenses", "Für diesen Zeitraum sind noch keine Ausgaben erfasst."))
            return out
        }
        out += AiBlock.Text(
            str(
                "ai_month_intro",
                "Im %1\$s hast du bisher %2\$s ausgegeben.",
                s.month.label, Money.format(s.month.expenseCents, s.symbol)
            ), AiSection.FACTS
        )
        out += AiBlock.Row(
            str("ai_income", "Einnahmen"), Money.format(s.month.incomeCents, s.symbol), AiSection.FACTS
        )
        out += AiBlock.Row(
            str("ai_expenses", "Ausgaben"), Money.format(s.month.expenseCents, s.symbol), AiSection.FACTS
        )
        out += AiBlock.Row(
            str("ai_remaining", "Übrig"), Money.format(s.month.remainingCents, s.symbol), AiSection.CALCULATED
        )

        val top = s.month.categories.filter { it.cents > 0 }.take(5)
        if (top.isNotEmpty()) {
            out += AiBlock.Gap
            out += AiBlock.Heading(str("ai_top_categories", "Die größten Kategorien sind:"))
            top.forEach { c ->
                out += AiBlock.Row(
                    "${c.emoji} ${c.name}".trim(),
                    "${Money.format(c.cents, s.symbol)} · ${prozent(c.percent)}",
                    AiSection.FACTS
                )
            }
        }

        out += AiBlock.Gap
        out += AiBlock.Row(
            str("ai_fixed", "Fixkosten"), Money.format(s.month.fixedCents, s.symbol), AiSection.CALCULATED
        )
        out += AiBlock.Row(
            str("ai_variable", "Variable Kosten"), Money.format(s.month.variableCents, s.symbol), AiSection.CALCULATED
        )

        val variable = top.filter { !it.isFixed }
        if (variable.size >= 2) {
            out += AiBlock.Text(
                str(
                    "ai_variable_hint",
                    "Deine größten variablen Ausgaben sind %1\$s und %2\$s.",
                    variable[0].name, variable[1].name
                ), AiSection.CALCULATED
            )
        } else if (variable.size == 1) {
            out += AiBlock.Text(
                str("ai_variable_hint_one", "Deine größte variable Ausgabe ist %1\$s.", variable[0].name),
                AiSection.CALCULATED
            )
        }

        s.projectedMonthExpenseCents()?.let { hoch ->
            out += AiBlock.Gap
            out += AiBlock.Text(
                str(
                    "ai_estimate_month_end",
                    "Hochgerechnet auf den ganzen Monat: %1\$s.",
                    Money.format(hoch, s.symbol)
                ), AiSection.ESTIMATE
            )
            out += AiBlock.Text(
                str(
                    "ai_estimate_basis",
                    "Grundlage: %1\$s von %2\$s Tagen sind vergangen - das ist eine Schätzung, kein feststehender Betrag.",
                    s.daysElapsed.toString(), s.daysInMonth.toString()
                ), AiSection.ESTIMATE
            )
        }

        haendler(s, out)
        return out
    }

    private fun saving(s: FinancialSummary): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Heading(str("ai_saving_title", "Wo du sparen kannst"), "\uD83D\uDCA1")

        out += AiBlock.Text(
            str(
                "ai_saving_intro",
                "Auf Basis deiner Ausgaben von %1\$s im %2\$s:",
                Money.format(s.month.expenseCents, s.symbol), s.month.label
            ), AiSection.FACTS
        )
        out += AiBlock.Row(
            str("ai_fixed", "Fixkosten"), Money.format(s.month.fixedCents, s.symbol), AiSection.FACTS
        )
        out += AiBlock.Row(
            str("ai_variable", "Variable Kosten"), Money.format(s.month.variableCents, s.symbol), AiSection.FACTS
        )

        val variable = s.month.categories.filter { !it.isFixed && it.cents > 0 }.take(3)
        if (variable.isEmpty()) {
            out += AiBlock.Gap
            out += AiBlock.Text(
                str("ai_saving_none", "Es sind noch keine variablen Ausgaben erfasst, deshalb kann ich keine Sparbereiche berechnen."),
                AiSection.FACTS
            )
            return out
        }

        out += AiBlock.Gap
        out += AiBlock.Heading(str("ai_section_suggestion", "Vorschläge"))
        variable.forEach { c ->
            val zehn = c.cents / 10L
            out += AiBlock.Row(
                "${c.emoji} ${c.name}".trim(),
                str(
                    "ai_saving_row",
                    "%1\$s · 10 %% davon wären %2\$s im Monat",
                    Money.format(c.cents, s.symbol), Money.format(zehn, s.symbol)
                ),
                AiSection.SUGGESTION
            )
        }
        out += AiBlock.Gap
        out += AiBlock.Text(
            str("ai_saving_hint", "Das sind Vorschläge auf Basis deiner echten Ausgaben - keine Prognosen."),
            AiSection.SUGGESTION
        )

        if (s.openBills.isNotEmpty()) {
            out += AiBlock.Gap
            out += AiBlock.Text(
                str(
                    "ai_saving_open_bills",
                    "Zusätzlich sind %1\$s offene Rechnungen über %2\$s erfasst. Werden sie bezahlt, sinkt dein verfügbares Budget entsprechend.",
                    s.openBills.size.toString(), Money.format(s.openBillsTotalCents, s.symbol)
                ), AiSection.FACTS
            )
        }
        return out
    }

    private fun openBills(s: FinancialSummary): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Heading(str("ai_open_bills_title", "Offene Rechnungen"), "\uD83E\uDDFE")
        if (s.openBills.isEmpty()) {
            out += AiBlock.Text(
                str("ai_no_open_bills", "Es sind keine offenen Rechnungen erfasst - alles ist bezahlt.")
            )
            return out
        }
        out += AiBlock.Text(
            str(
                "ai_open_bills_intro",
                "Du hast %1\$s offene Rechnungen über insgesamt %2\$s.",
                s.openBills.size.toString(), Money.format(s.openBillsTotalCents, s.symbol)
            ), AiSection.FACTS
        )
        out += AiBlock.Gap
        s.openBills.forEach { b ->
            val datum = b.dueDate?.let { Dates.full(it) }
                ?: str("ai_no_due_date", "ohne Fälligkeitsdatum")
            out += AiBlock.Row(
                b.name,
                "${Money.format(b.amountCents, s.symbol)} \u00B7 $datum",
                AiSection.FACTS
            )
        }
        if (s.overdueBills.isNotEmpty()) {
            out += AiBlock.Gap
            out += AiBlock.Heading(str("ai_overdue_title", "Davon überfällig:"), "⚠️")
            s.overdueBills.forEach { b ->
                val tage = b.daysLeft?.let { -it } ?: 0L
                out += AiBlock.Row(
                    b.name,
                    str(
                        "ai_overdue_row",
                        "%1\$s · seit %2\$s Tagen überfällig",
                        Money.format(b.amountCents, s.symbol), tage.toString()
                    ),
                    AiSection.CALCULATED
                )
            }
        }
        return out
    }

    private fun upcoming(s: FinancialSummary): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Heading(str("ai_upcoming_title", "Nächste Zahlungen"), "\uD83D\uDCC5")
        if (s.upcoming.isEmpty()) {
            out += AiBlock.Text(
                str("ai_no_upcoming", "In den nächsten 30 Tagen sind keine Zahlungen fällig.")
            )
            return out
        }
        val summe = s.upcoming.sumOf { it.amountCents }
        out += AiBlock.Text(
            str(
                "ai_upcoming_intro",
                "In den nächsten 30 Tagen sind %1\$s Zahlungen über %2\$s fällig:",
                s.upcoming.size.toString(), Money.format(summe, s.symbol)
            ), AiSection.FACTS
        )
        out += AiBlock.Gap
        s.upcoming.take(12).forEach { p ->
            val wann = when (p.daysLeft) {
                0L -> str("ai_today", "heute")
                1L -> str("ai_tomorrow", "morgen")
                else -> str("ai_in_days", "in %1\$s Tagen", p.daysLeft.toString())
            }
            out += AiBlock.Row(
                p.name,
                "${Money.format(p.amountCents, s.symbol)} \u00B7 ${Dates.full(p.date)} ($wann)",
                AiSection.FACTS
            )
        }
        return out
    }

    private fun monthAnalysis(s: FinancialSummary): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Heading(str("ai_month_report_title", "Monatsanalyse %1\$s", s.month.label), "\uD83D\uDCC8")
        out += AiBlock.Row(
            "\uD83D\uDCCA ${str("ai_income", "Einnahmen")}", Money.format(s.month.incomeCents, s.symbol), AiSection.FACTS
        )
        out += AiBlock.Row(
            "\uD83D\uDCB8 ${str("ai_expenses", "Ausgaben")}", Money.format(s.month.expenseCents, s.symbol), AiSection.FACTS
        )
        out += AiBlock.Row(
            "\uD83D\uDCB0 ${str("ai_remaining", "Übrig")}", Money.format(s.month.remainingCents, s.symbol), AiSection.CALCULATED
        )
        out += AiBlock.Row(
            "\uD83E\uDDFE ${str("ai_open_bills_short", "Offene Rechnungen")}",
            str(
                "ai_open_bills_value",
                "%1\$s · %2\$s",
                s.openBills.size.toString(), Money.format(s.openBillsTotalCents, s.symbol)
            ), AiSection.FACTS
        )
        out += AiBlock.Row(
            "\uD83D\uDD04 ${str("ai_recurring_short", "Wiederkehrende Zahlungen")}",
            "${Money.format(s.recurringMonthlyCents, s.symbol)} / ${str("ai_per_month", "Monat")}",
            AiSection.CALCULATED
        )

        val top = s.month.categories.filter { it.cents > 0 }.take(5)
        if (top.isNotEmpty()) {
            out += AiBlock.Gap
            out += AiBlock.Heading(str("ai_top_categories", "Die größten Kategorien sind:"))
            top.forEach { c ->
                out += AiBlock.Row(
                    "${c.emoji} ${c.name}".trim(),
                    Money.format(c.cents, s.symbol), AiSection.FACTS
                )
            }
        }

        s.previousMonth?.let { vor ->
            out += AiBlock.Gap
            out += AiBlock.Heading(str("ai_month_report_compare", "Vergleich zum Vormonat"))
            out += AiBlock.Row(
                str("ai_expenses", "Ausgaben"),
                str(
                    "ai_compare_row",
                    "%1\$s → %2\$s",
                    Money.format(vor.expenseCents, s.symbol), Money.format(s.month.expenseCents, s.symbol)
                ), AiSection.CALCULATED
            )
            val delta = s.month.expenseCents - vor.expenseCents
            if (vor.expenseCents > 0L && delta != 0L) {
                val text = if (delta > 0) {
                    str(
                        "ai_more_than_before",
                        "Das sind %1\$s mehr als im Vormonat.",
                        Money.format(delta, s.symbol)
                    )
                } else {
                    str(
                        "ai_less_than_before",
                        "Das sind %1\$s weniger als im Vormonat.",
                        Money.format(-delta, s.symbol)
                    )
                }
                out += AiBlock.Text(text, AiSection.CALCULATED)
            }
        }

        s.budget?.takeIf { it.enabled && it.limitCents > 0 }?.let { b ->
            out += AiBlock.Gap
            out += AiBlock.Row(
                str("ai_budget", "Budget"),
                str(
                    "ai_budget_row",
                    "%1\$s von %2\$s genutzt · %3\$s übrig",
                    Money.format(b.usedCents, s.symbol),
                    Money.format(b.limitCents, s.symbol),
                    Money.format(b.remainingCents, s.symbol)
                ), AiSection.CALCULATED
            )
        }

        val variable = s.month.categories.filter { !it.isFixed && it.cents > 0 }.take(2)
        if (variable.isNotEmpty()) {
            out += AiBlock.Gap
            out += AiBlock.Heading(str("ai_section_suggestion", "Vorschläge"), "\uD83D\uDCA1")
            variable.forEach { c ->
                out += AiBlock.Row(
                    "${c.emoji} ${c.name}".trim(),
                    str(
                        "ai_saving_row",
                        "%1\$s · 10 %% davon wären %2\$s im Monat",
                        Money.format(c.cents, s.symbol), Money.format(c.cents / 10L, s.symbol)
                    ),
                    AiSection.SUGGESTION
                )
            }
            out += AiBlock.Text(
                str("ai_saving_hint", "Das sind Vorschläge auf Basis deiner echten Ausgaben - keine Prognosen."),
                AiSection.SUGGESTION
            )
        }

        s.projectedMonthExpenseCents()?.let { hoch ->
            out += AiBlock.Gap
            out += AiBlock.Text(
                str(
                    "ai_estimate_month_end",
                    "Hochgerechnet auf den ganzen Monat: %1\$s.",
                    Money.format(hoch, s.symbol)
                ), AiSection.ESTIMATE
            )
        }
        return out
    }

    private fun monthComparison(s: FinancialSummary): List<AiBlock> {
        val vor = s.previousMonth
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Heading(
            str("ai_compare_title", "Vergleich %1\$s mit %2\$s", vor?.label ?: "–", s.month.label), "⚖️"
        )
        if (vor == null) {
            out += AiBlock.Text(str("ai_no_previous_month", "Für den Vormonat sind noch keine Daten erfasst."))
            return out
        }
        out += AiBlock.Row(
            str("ai_income", "Einnahmen"),
            str(
                "ai_compare_row",
                "%1\$s → %2\$s",
                Money.format(vor.incomeCents, s.symbol), Money.format(s.month.incomeCents, s.symbol)
            ), AiSection.CALCULATED
        )
        out += AiBlock.Row(
            str("ai_expenses", "Ausgaben"),
            str(
                "ai_compare_row",
                "%1\$s → %2\$s",
                Money.format(vor.expenseCents, s.symbol), Money.format(s.month.expenseCents, s.symbol)
            ), AiSection.CALCULATED
        )
        out += AiBlock.Row(
            str("ai_remaining", "Übrig"),
            str(
                "ai_compare_row",
                "%1\$s → %2\$s",
                Money.format(vor.remainingCents, s.symbol), Money.format(s.month.remainingCents, s.symbol)
            ), AiSection.CALCULATED
        )
        val delta = s.month.expenseCents - vor.expenseCents
        if (delta != 0L) {
            out += AiBlock.Gap
            out += AiBlock.Text(
                if (delta > 0) {
                    str(
                        "ai_more_than_before",
                        "Das sind %1\$s mehr als im Vormonat.",
                        Money.format(delta, s.symbol)
                    )
                } else {
                    str(
                        "ai_less_than_before",
                        "Das sind %1\$s weniger als im Vormonat.",
                        Money.format(-delta, s.symbol)
                    )
                }, AiSection.CALCULATED
            )
        }
        return out
    }

    private fun recurringPayments(s: FinancialSummary): List<AiBlock> {
        val ausgaben = s.recurring.filter { !it.isIncome }
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Heading(str("ai_recurring_title", "Wiederkehrende Zahlungen"), "\uD83D\uDD04")
        if (ausgaben.isEmpty()) {
            out += AiBlock.Text(
                str("ai_no_recurring", "Es sind keine wiederkehrenden Zahlungen erfasst.")
            )
            return out
        }
        val summe = ausgaben.sumOf { it.monthlyCents }
        out += AiBlock.Text(
            str(
                "ai_recurring_intro",
                "Du hast %1\$s wiederkehrende Zahlungen mit zusammen %2\$s pro Monat.",
                ausgaben.size.toString(), Money.format(summe, s.symbol)
            ), AiSection.CALCULATED
        )
        out += AiBlock.Gap
        ausgaben.take(10).forEach { r ->
            out += AiBlock.Row(
                r.name,
                "${Money.format(r.monthlyCents, s.symbol)} / ${str("ai_per_month", "Monat")}",
                AiSection.CALCULATED
            )
        }
        out += AiBlock.Gap
        out += AiBlock.Text(
            str(
                "ai_recurring_year",
                "Auf das Jahr gerechnet sind das %1\$s.",
                Money.format(summe * 12L, s.symbol)
            ), AiSection.CALCULATED
        )
        return out
    }

    private fun general(s: FinancialSummary): List<AiBlock> {
        val out = mutableListOf<AiBlock>()
        out += AiBlock.Text(
            str(
                "ai_can_do",
                "Ich werte ausschließlich deine echten Finovo-Daten aus: Einnahmen, Ausgaben, Rechnungen, Fälligkeiten, Kategorien, Budgets, wiederkehrende Zahlungen und Monatsvergleiche."
            )
        )
        out += AiBlock.Gap
        out += AiBlock.Text(
            str(
                "ai_data_state",
                "Aktuell: %1\$s im %2\$s, %3\$s offene Rechnungen, %4\$s Buchungen insgesamt.",
                Money.format(s.month.expenseCents, s.symbol), s.month.label,
                s.openBills.size.toString(), s.totalTransactions.toString()
            ), AiSection.FACTS
        )
        out += AiBlock.Gap
        out += AiBlock.Text(
            str("ai_ask_example", "Beispiele: „Wo geht mein Geld hin?“, „Wie kann ich sparen?“, „Welche Rechnungen sind offen?“, „Analysiere meinen Monat.“")
        )
        return out
    }

    // ------------------------------------------------------------------ Hilfen

    private fun haendler(s: FinancialSummary, out: MutableList<AiBlock>) {
        val haendler = s.merchants.take(5)
        if (haendler.isEmpty()) return
        out += AiBlock.Gap
        out += AiBlock.Heading(str("ai_merchant_intro", "Häufigste Buchungen:"), "\uD83D\uDED2")
        haendler.forEach { m ->
            out += AiBlock.Row(
                m.name,
                str(
                    "ai_merchant_row",
                    "%1\$s · %2\$s Buchungen",
                    Money.format(m.cents, s.symbol), m.count.toString()
                ),
                AiSection.FACTS
            )
        }
    }

    private fun prozent(anteil: Float): String =
        String.format(Locale.US, "%.0f %%", (anteil * 100f).coerceIn(0f, 100f))
}

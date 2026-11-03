package com.finovo.app.util

import com.finovo.app.data.AreaEntity
import com.finovo.app.data.AssetEntity
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.BudgetEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.SubscriptionEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// ===========================================================================
// Modul 15 - Finanz-Alarme (jeder Alarm einzeln ein- und ausschaltbar)
// ===========================================================================

enum class Alarm(val key: String, val titelKey: String, val fallBack: String, val standardWert: Long) {
    LOW_BALANCE("low_balance", "alarm_low_balance", "Niedriger Kontostand", 100_00L),
    BILL_DUE("bill_due", "alarm_bill_due", "Anstehende Rechnung", 3L),
    BILL_OVERDUE("bill_overdue", "alarm_bill_overdue", "Überfällige Rechnung", 0L),
    UNUSUAL_SPENDING("unusual_spending", "alarm_unusual", "Ungewöhnliche Ausgabe", 300L),
    BUDGET_EXCEEDED("budget_exceeded", "alarm_budget", "Budget überschritten", 100L),
    SUBSCRIPTION_RENEWAL("subscription_renewal", "alarm_subscription", "Abo-Verlängerung", 3L),
    SALARY_RECEIVED("salary_received", "alarm_salary", "Gehalt erhalten", 0L),
    LARGE_TRANSACTION("large_transaction", "alarm_large", "Große Buchung", 500_00L),
    LOAN_PAYMENT_DUE("loan_payment_due", "alarm_loan", "Kreditrate fällig", 7L);

    companion object {
        fun fromKey(key: String): Alarm? = values().firstOrNull { it.key == key }
    }
}

object Alarme {

    data class Zustand(
        val aktiv: Map<Alarm, Boolean>,
        val schwellen: Map<Alarm, Long>
    ) {
        fun istAn(a: Alarm): Boolean = aktiv[a] ?: true
        fun schwelle(a: Alarm): Long = schwellen[a] ?: a.standardWert
    }

    data class Meldung(val alarm: Alarm, val titel: String, val text: String)

    fun titel(a: Alarm): String = str(a.titelKey, a.fallBack)

    fun beschreibung(a: Alarm): String = when (a) {
        Alarm.LOW_BALANCE -> str("alarm_low_balance_sub", "Meldet, wenn das verfügbare Geld im Monat unter den Schwellenwert fällt.")
        Alarm.BILL_DUE -> str("alarm_bill_due_sub", "Erinnert an Rechnungen, die in den nächsten Tagen fällig werden.")
        Alarm.BILL_OVERDUE -> str("alarm_bill_overdue_sub", "Meldet Rechnungen, die bereits überfällig sind.")
        Alarm.UNUSUAL_SPENDING -> str("alarm_unusual_sub", "Meldet Ausgaben, die deutlich über deinem üblichen Schnitt liegen.")
        Alarm.BUDGET_EXCEEDED -> str("alarm_budget_sub", "Meldet, wenn das Monatsbudget überschritten ist.")
        Alarm.SUBSCRIPTION_RENEWAL -> str("alarm_subscription_sub", "Erinnert an anstehende Abo-Verlängerungen.")
        Alarm.SALARY_RECEIVED -> str("alarm_salary_sub", "Bestätigt eine heute erfasste Einnahme.")
        Alarm.LARGE_TRANSACTION -> str("alarm_large_sub", "Meldet einzelne Buchungen ab einem Betrag.")
        Alarm.LOAN_PAYMENT_DUE -> str("alarm_loan_sub", "Erinnert an fällige Kreditraten.")
    }

    fun standard(): Zustand = Zustand(
        aktiv = Alarm.values().associateWith { true },
        schwellen = Alarm.values().associateWith { it.standardWert }
    )

    /** Format: "low_balance=1:10000;bill_due=1:3;..." */
    fun ausText(text: String): Zustand {
        val aktiv = mutableMapOf<Alarm, Boolean>()
        val schwellen = mutableMapOf<Alarm, Long>()
        Alarm.values().forEach { aktiv[it] = true; schwellen[it] = it.standardWert }
        text.split(';').forEach { teil ->
            if (teil.isBlank()) return@forEach
            val key = teil.substringBefore('=').trim()
            val rest = teil.substringAfter('=', "")
            val alarm = Alarm.fromKey(key) ?: return@forEach
            val an = rest.substringBefore(':').trim() != "0"
            val wert = rest.substringAfter(':', "").trim().toLongOrNull()
            aktiv[alarm] = an
            if (wert != null && wert >= 0) schwellen[alarm] = wert
        }
        return Zustand(aktiv, schwellen)
    }

    fun zuText(z: Zustand): String = Alarm.values().joinToString(";") { a ->
        "${a.key}=${if (z.istAn(a)) 1 else 0}:${z.schwelle(a)}"
    }

    /** Wertet alle aktiven Alarme gegen die echten Daten aus. */
    fun pruefen(
        z: Zustand,
        heute: LocalDate = Dates.today(),
        ausgaben: List<ExpenseEntity>,
        einnahmen: List<IncomeEntity>,
        rechnungen: List<BillEntity>,
        abos: List<SubscriptionEntity>,
        regeln: List<RecurringRuleEntity>,
        kategorien: List<CategoryEntity>,
        werte: List<AssetEntity>,
        budget: BudgetEntity?,
        symbol: String = "€"
    ): List<Meldung> {
        val out = mutableListOf<Meldung>()
        val heuteTag = heute.toEpochDay()
        val monat = YearMonth.from(heute)
        val vonTag = monat.atDay(1).toEpochDay()
        val bisTag = monat.atEndOfMonth().toEpochDay()

        fun ausgabenImMonat() = ausgaben.filter { it.date in vonTag..bisTag }
        fun einnahmenImMonat() = einnahmen.filter { it.date in vonTag..bisTag }

        if (z.istAn(Alarm.LOW_BALANCE)) {
            val frei = einnahmenImMonat().sumOf { it.amountCents } - ausgabenImMonat().sumOf { it.amountCents }
            val grenze = z.schwelle(Alarm.LOW_BALANCE)
            if (frei < grenze) {
                out += Meldung(
                    Alarm.LOW_BALANCE,
                    titel(Alarm.LOW_BALANCE),
                    str(
                        "alarm_low_balance_text",
                        "Verfügbar in diesem Monat: %1\$s (Schwelle: %2\$s).",
                        Money.format(frei, symbol),
                        Money.format(grenze, symbol)
                    )
                )
            }
        }

        if (z.istAn(Alarm.BILL_DUE)) {
            val tage = z.schwelle(Alarm.BILL_DUE)
            rechnungen.filter { it.status != BillStatus.PAID.name }
                .filter { it.dueDate in heuteTag..(heuteTag + tage) }
                .forEach { r ->
                    out += Meldung(
                        Alarm.BILL_DUE,
                        titel(Alarm.BILL_DUE),
                        str(
                            "alarm_bill_due_text",
                            "%1\$s über %2\$s ist am %3\$s fällig.",
                            r.name,
                            Money.format(r.amountCents, symbol),
                            Dates.full(Dates.fromEpochDay(r.dueDate))
                        )
                    )
                }
        }

        if (z.istAn(Alarm.BILL_OVERDUE)) {
            rechnungen.filter { it.status != BillStatus.PAID.name && it.dueDate < heuteTag }
                .forEach { r ->
                    out += Meldung(
                        Alarm.BILL_OVERDUE,
                        titel(Alarm.BILL_OVERDUE),
                        str(
                            "alarm_bill_overdue_text",
                            "%1\$s über %2\$s ist seit %3\$s Tagen überfällig.",
                            r.name,
                            Money.format(r.amountCents, symbol),
                            (heuteTag - r.dueDate).toString()
                        )
                    )
                }
        }

        if (z.istAn(Alarm.UNUSUAL_SPENDING)) {
            val letzteMonate = (1..3).map { monat.minusMonths(it.toLong()) }
            val schnitt = letzteMonate.map { m ->
                val mVon = m.atDay(1).toEpochDay()
                val mBis = m.atEndOfMonth().toEpochDay()
                ausgaben.filter { it.date in mVon..mBis }.sumOf { it.amountCents }
            }.filter { it > 0 }
            if (schnitt.size >= 2) {
                val mittel = schnitt.sum() / schnitt.size
                val faktor = z.schwelle(Alarm.UNUSUAL_SPENDING).coerceAtLeast(110)
                val grenze = (mittel * faktor) / 100L
                ausgabenImMonat().filter { it.amountCents > grenze && grenze > 0 }.forEach { e ->
                    val kat = kategorien.firstOrNull { it.id == e.categoryId }
                    out += Meldung(
                        Alarm.UNUSUAL_SPENDING,
                        titel(Alarm.UNUSUAL_SPENDING),
                        str(
                            "alarm_unusual_text",
                            "%1\$s am %2\$s liegt über dem üblichen Monatsschnitt (%3\$s).",
                            Money.format(e.amountCents, symbol),
                            Dates.full(Dates.fromEpochDay(e.date)),
                            Money.format(grenze, symbol) + (kat?.let { " · " + it.name } ?: "")
                        )
                    )
                }
            }
        }

        if (z.istAn(Alarm.BUDGET_EXCEEDED) && budget != null && budget.enabled && budget.monthlyLimitCents > 0) {
            val aus = ausgabenImMonat().sumOf { it.amountCents }
            val grenze = (budget.monthlyLimitCents * z.schwelle(Alarm.BUDGET_EXCEEDED)) / 100L
            if (aus >= grenze) {
                out += Meldung(
                    Alarm.BUDGET_EXCEEDED,
                    titel(Alarm.BUDGET_EXCEEDED),
                    str(
                        "alarm_budget_text",
                        "%1\$s von %2\$s ausgegeben (%3\$s).",
                        Money.format(aus, symbol),
                        Money.format(budget.monthlyLimitCents, symbol),
                        String.format(AppLocale.locale(), "%.0f%%", (aus * 100f) / budget.monthlyLimitCents)
                    )
                )
            }
        }

        if (z.istAn(Alarm.SUBSCRIPTION_RENEWAL)) {
            val tage = z.schwelle(Alarm.SUBSCRIPTION_RENEWAL)
            abos.filter { it.active }
                .filter { it.nextPayment in heuteTag..(heuteTag + tage) }
                .forEach { a ->
                    out += Meldung(
                        Alarm.SUBSCRIPTION_RENEWAL,
                        titel(Alarm.SUBSCRIPTION_RENEWAL),
                        str(
                            "alarm_subscription_text",
                            "%1\$s verlängert sich am %2\$s für %3\$s.",
                            a.name,
                            Dates.full(Dates.fromEpochDay(a.nextPayment)),
                            Money.format(a.amountCents, symbol)
                        )
                    )
                }
        }

        if (z.istAn(Alarm.SALARY_RECEIVED)) {
            einnahmen.filter { it.date == heuteTag }.forEach { e ->
                out += Meldung(
                    Alarm.SALARY_RECEIVED,
                    titel(Alarm.SALARY_RECEIVED),
                    str(
                        "alarm_salary_text",
                        "Heute wurde eine Einnahme über %1\$s erfasst.",
                        Money.format(e.amountCents, symbol)
                    )
                )
            }
        }

        if (z.istAn(Alarm.LARGE_TRANSACTION)) {
            val grenze = z.schwelle(Alarm.LARGE_TRANSACTION)
            if (grenze > 0) {
                ausgaben.filter { it.amountCents >= grenze && it.date >= vonTag }.forEach { e ->
                    out += Meldung(
                        Alarm.LARGE_TRANSACTION,
                        titel(Alarm.LARGE_TRANSACTION),
                        str(
                            "alarm_large_text",
                            "%1\$s am %2\$s · Schwelle: %3\$s.",
                            Money.format(e.amountCents, symbol),
                            Dates.full(Dates.fromEpochDay(e.date)),
                            Money.format(grenze, symbol)
                        )
                    )
                }
            }
        }

        if (z.istAn(Alarm.LOAN_PAYMENT_DUE)) {
            val tage = z.schwelle(Alarm.LOAN_PAYMENT_DUE)
            val kreditWoerter = listOf("kredit", "darlehen", "loan", "rate", "rata", "kredi", "prestito", "finanzierung")
            fun istKredit(text: String, kategorieId: Long): Boolean {
                val kat = kategorien.firstOrNull { it.id == kategorieId }?.name ?: ""
                val basis = (text + " " + kat).lowercase()
                return kreditWoerter.any { basis.contains(it) }
            }
            rechnungen.filter { it.status != BillStatus.PAID.name }
                .filter { istKredit(it.name, it.categoryId) }
                .filter { it.dueDate in heuteTag..(heuteTag + tage) }
                .forEach { r ->
                    out += Meldung(
                        Alarm.LOAN_PAYMENT_DUE,
                        titel(Alarm.LOAN_PAYMENT_DUE),
                        str(
                            "alarm_loan_text",
                            "Rate %1\$s über %2\$s ist am %3\$s fällig.",
                            r.name,
                            Money.format(r.amountCents, symbol),
                            Dates.full(Dates.fromEpochDay(r.dueDate))
                        )
                    )
                }
            regeln.filter { it.active && it.kind == RecurringKind.EXPENSE.name }
                .filter { istKredit(it.name, it.categoryId) }
                .forEach { regel ->
                    Recurrence.occurrences(
                        RecurrenceSpec.from(regel),
                        heute,
                        Dates.plusTage(heute, tage)
                    ).forEach { tag ->
                        out += Meldung(
                            Alarm.LOAN_PAYMENT_DUE,
                            titel(Alarm.LOAN_PAYMENT_DUE),
                            str(
                                "alarm_loan_text",
                                "Rate %1\$s über %2\$s ist am %3\$s fällig.",
                                regel.name,
                                Money.format(regel.amountCents, symbol),
                                Dates.full(tag)
                            )
                        )
                    }
                }
            werte.filter { it.isLiability && it.amountCents > 0 && it.date > 0 }
                .filter { it.date in heuteTag..(heuteTag + tage) }
                .forEach { w ->
                    out += Meldung(
                        Alarm.LOAN_PAYMENT_DUE,
                        titel(Alarm.LOAN_PAYMENT_DUE),
                        str(
                            "alarm_loan_text",
                            "Rate %1\$s über %2\$s ist am %3\$s fällig.",
                            w.name,
                            Money.format(w.amountCents, symbol),
                            Dates.full(Dates.fromEpochDay(w.date))
                        )
                    )
                }
        }

        return out
    }
}

// ===========================================================================
// Modul 21 - Anpassbares Dashboard
// ===========================================================================

object Widgets {

    enum class Art(val key: String, val labelKey: String, val fallBack: String, val emoji: String) {
        BALANCE("balance", "widget_balance", "Kontostand", "\uD83D\uDCB0"),
        INCOME("income", "widget_income", "Einnahmen", "\uD83D\uDCC8"),
        EXPENSES("expenses", "widget_expenses", "Ausgaben", "\uD83D\uDCC9"),
        SAVINGS("savings", "widget_savings", "Sparen", "\uD83C\uDFAF"),
        BUDGET("budget", "widget_budget", "Budget", "\uD83D\uDCCA"),
        BILLS("bills", "widget_bills", "Rechnungen", "\uD83E\uDDFE"),
        SUBSCRIPTIONS("subscriptions", "widget_subscriptions", "Abos", "\uD83D\uDD01"),
        GOALS("goals", "widget_goals", "Sparziele", "\uD83C\uDFAF"),
        CHART("chart", "widget_chart", "Verlauf", "\uD83D\uDCC8"),
        INSIGHTS("insights", "widget_insights", "Einblicke", "\uD83D\uDCA1")
    }

    fun label(a: Art): String = str(a.labelKey, a.fallBack)

    fun standard(): List<Art> = Art.values().toList()

    /**
     * Liest die gespeicherte Reihenfolge. Ausgeblendete Elemente bleiben
     * ausgeblendet - nur beim allerersten Start (leerer Text) wird die
     * Standardreihenfolge verwendet.
     */
    fun ausText(text: String): List<Art> {
        if (text.isBlank()) return standard()
        val liste = text.split(';')
            .map { it.trim() }
            .mapNotNull { key -> Art.values().firstOrNull { it.key == key } }
            .distinct()
        return liste.ifEmpty { standard() }
    }

    fun zuText(liste: List<Art>): String = liste.joinToString(";") { it.key }
}

// ===========================================================================
// Modul 22 - Haushalts-/Familienmodus
// Mitglieder basieren auf den vorhandenen Bereichen; Ausgaben ohne Bereich
// gelten als gemeinsame Ausgaben.
// ===========================================================================

object Familie {

    data class Mitglied(
        val id: Long,
        val name: String,
        val emoji: String,
        val darfSehen: Boolean = true,
        val darfBearbeiten: Boolean = true,
        val darfExportieren: Boolean = true
    )

    data class Auswertung(
        val gemeinsamCents: Long,
        val mitglieder: List<Anteil>
    )

    data class Anteil(
        val mitglied: Mitglied,
        val ausgabenCents: Long,
        val einnahmenCents: Long,
        val anzahl: Int
    )

    /** Format: "1:1,1,1;2:1,0,0" (Bereich-ID : sehen, bearbeiten, exportieren) */
    fun ausText(texte: String, bereiche: List<AreaEntity>): List<Mitglied> {
        val rechte = mutableMapOf<Long, Triple<Boolean, Boolean, Boolean>>()
        texte.split(';').forEach { teil ->
            if (teil.isBlank()) return@forEach
            val id = teil.substringBefore(':').trim().toLongOrNull() ?: return@forEach
            val teile = teil.substringAfter(':', "").split(',')
            val sehen = teile.getOrNull(0)?.trim() != "0"
            val bearbeiten = teile.getOrNull(1)?.trim() != "0"
            val exportieren = teile.getOrNull(2)?.trim() != "0"
            rechte[id] = Triple(sehen, bearbeiten, exportieren)
        }
        return bereiche.map { a ->
            val r = rechte[a.id]
            Mitglied(a.id, a.name, a.emoji, r?.first ?: true, r?.second ?: true, r?.third ?: true)
        }
    }

    fun zuText(liste: List<Mitglied>): String = liste.joinToString(";") { m ->
        "${m.id}:${if (m.darfSehen) 1 else 0},${if (m.darfBearbeiten) 1 else 0},${if (m.darfExportieren) 1 else 0}"
    }

    /** Gemeinsame Ausgaben haben keinen Bereich, eigene sind einem Mitglied zugeordnet. */
    fun auswerten(
        mitglieder: List<Mitglied>,
        ausgaben: List<ExpenseEntity>,
        einnahmen: List<IncomeEntity>,
        von: LocalDate,
        bis: LocalDate
    ): Auswertung {
        val vonTag = von.toEpochDay()
        val bisTag = bis.toEpochDay()
        val imZeitraum = { tag: Long -> tag in vonTag..bisTag }

        var gemeinsam = 0L
        ausgaben.forEach { if (it.areaId == 0L && imZeitraum(it.date)) gemeinsam += it.amountCents }

        val anteile = mitglieder.map { m ->
            val aus = ausgaben.filter { it.areaId == m.id && imZeitraum(it.date) }
            val ein = einnahmen.filter { it.areaId == m.id && imZeitraum(it.date) }
            Anteil(
                mitglied = m,
                ausgabenCents = aus.sumOf { it.amountCents },
                einnahmenCents = ein.sumOf { it.amountCents },
                anzahl = aus.size + ein.size
            )
        }
        return Auswertung(gemeinsam, anteile)
    }
}

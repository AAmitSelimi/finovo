package com.finovo.app.util

import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.BudgetEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryTotal
import com.finovo.app.data.DocumentEntity
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.SavingGoalEntity
import com.finovo.app.data.SubscriptionEntity
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

// ===========================================================================
// Modul 14 - Prognose zum Monatsende
// Alle Werte in Cent (Long). Es wird nichts geschaetzt, was nicht aus echten
// Buchungen ableitbar ist: Fixkosten und terminierte Zahlungen sind exakt,
// variable Ausgaben werden anteilig hochgerechnet und als Prognose markiert.
// ===========================================================================

object Prognose {

    enum class Art { RECHNUNG, ABO, REGEL }

    data class Anstehend(
        val name: String,
        val date: LocalDate,
        val cents: Long,
        val art: Art
    )

    data class Ergebnis(
        val monat: YearMonth,
        val tageImMonat: Int,
        val tageVorbei: Int,
        val einnahmenBisherCents: Long,
        val einnahmenErwartetCents: Long,
        val fixkostenBisherCents: Long,
        val fixkostenErwartetCents: Long,
        val variableBisherCents: Long,
        val variablePrognoseCents: Long,
        val ausgabenPrognoseCents: Long,
        val restbetragCents: Long,
        val sparenMoeglichCents: Long,
        val anstehend: List<Anstehend>,
        val risiko: Boolean,
        val budgetLimitCents: Long
    )

    /** Fixkosten lauten: Kategorie ist fix oder die Buchung ist manuell als fix markiert. */
    fun istFix(e: ExpenseEntity, kategorien: List<CategoryEntity>): Boolean {
        val override = e.fixedOverride
        if (override != null) return override
        return kategorien.firstOrNull { it.id == e.categoryId }?.isFixed ?: false
    }

    fun berechne(
        monat: YearMonth,
        heute: LocalDate,
        ausgaben: List<ExpenseEntity>,
        einnahmen: List<IncomeEntity>,
        rechnungen: List<BillEntity>,
        abos: List<SubscriptionEntity>,
        regeln: List<RecurringRuleEntity>,
        kategorien: List<CategoryEntity>,
        budget: BudgetEntity?
    ): Ergebnis {
        val von = monat.atDay(1)
        val bis = monat.atEndOfMonth()
        val vonTag = von.toEpochDay()
        val bisTag = bis.toEpochDay()
        val heuteTag = heute.toEpochDay()

        val tageImMonat = monat.lengthOfMonth()
        val tageVorbei = when {
            heuteTag < vonTag -> 0
            heuteTag > bisTag -> tageImMonat
            else -> (heuteTag - vonTag + 1).toInt()
        }

        val imMonat = DateRange(vonTag, bisTag)

        var einnahmenBisher = 0L
        einnahmen.forEach { if (imMonat.contains(it.date)) einnahmenBisher += it.amountCents }

        var fixBisher = 0L
        var varBisher = 0L
        ausgaben.forEach {
            if (imMonat.contains(it.date)) {
                if (istFix(it, kategorien)) fixBisher += it.amountCents
                else varBisher += it.amountCents
            }
        }

        // Terminierte Einnahmen und Ausgaben bis zum Monatsende
        val restVon = maxOf(heuteTag, vonTag)
        val erwarteteEinnahmen = einnahmenBisher + regeln
            .filter { it.active && it.kind == RecurringKind.INCOME.name }
            .sumOf { regel ->
                val spec = RecurrenceSpec.from(regel)
                Recurrence.occurrences(spec, Dates.fromEpochDay(restVon), bis)
                    .sumOf { regel.amountCents }
            }

        val anstehend = mutableListOf<Anstehend>()

        rechnungen.filter { it.status != BillStatus.PAID.name && it.dueDate in restVon..bisTag }
            .forEach {
                anstehend += Anstehend(it.name, Dates.fromEpochDay(it.dueDate), it.amountCents, Art.RECHNUNG)
            }

        abos.filter { it.active && it.nextPayment in restVon..bisTag }
            .forEach {
                anstehend += Anstehend(it.name, Dates.fromEpochDay(it.nextPayment), it.amountCents, Art.ABO)
            }

        regeln.filter { it.active && it.kind == RecurringKind.EXPENSE.name }.forEach { regel ->
            Recurrence.occurrences(RecurrenceSpec.from(regel), Dates.fromEpochDay(restVon), bis)
                .forEach { tag ->
                    anstehend += Anstehend(regel.name, tag, regel.amountCents, Art.REGEL)
                }
        }

        val terminierteAusgaben = anstehend.sumOf { it.cents }

        val fixErwartet = terminierteAusgaben + regeln
            .filter { it.active && it.kind == RecurringKind.EXPENSE.name }
            .sumOf { regel ->
                Recurrence.occurrences(RecurrenceSpec.from(regel), von, Dates.fromEpochDay(restVon - 1))
                    .sumOf { regel.amountCents }
            }

        val varPrognose = if (tageVorbei > 0) {
            Dates.mal(varBisher, tageImMonat.toLong()) / tageVorbei.toLong()
        } else varBisher

        val ausgabenPrognose = fixBisher + fixErwartet + varPrognose
        val rest = erwarteteEinnahmen - ausgabenPrognose
        val limit = budget?.takeIf { it.enabled }?.monthlyLimitCents ?: 0L
        val risiko = rest < 0 || (limit > 0 && ausgabenPrognose > limit)

        return Ergebnis(
            monat = monat,
            tageImMonat = tageImMonat,
            tageVorbei = tageVorbei,
            einnahmenBisherCents = einnahmenBisher,
            einnahmenErwartetCents = erwarteteEinnahmen,
            fixkostenBisherCents = fixBisher,
            fixkostenErwartetCents = fixErwartet,
            variableBisherCents = varBisher,
            variablePrognoseCents = varPrognose,
            ausgabenPrognoseCents = ausgabenPrognose,
            restbetragCents = rest,
            sparenMoeglichCents = if (rest > 0) rest else 0L,
            anstehend = anstehend.sortedBy { it.date.toEpochDay() },
            risiko = risiko,
            budgetLimitCents = limit
        )
    }
}

// ===========================================================================
// Modul 23 - Cashflow-Analyse
// ===========================================================================

object Cashflow {

    data class Punkt(
        val monat: YearMonth,
        val einnahmenCents: Long,
        val ausgabenCents: Long,
        val saldoCents: Long
    )

    data class Ergebnis(
        val von: LocalDate,
        val bis: LocalDate,
        val startCents: Long,
        val einnahmenCents: Long,
        val ausgabenCents: Long,
        val transfersCents: Long,
        val ersparnisCents: Long,
        val endCents: Long,
        val punkte: List<Punkt>
    )

    /**
     * Startbestand = Eroeffnungsbestand + alle Einnahmen - alle Ausgaben
     * vor dem Zeitraum. Transfers zwischen eigenen Konten tauchen separat auf
     * und veraendern die Ersparnis nicht.
     */
    fun berechne(
        von: LocalDate,
        bis: LocalDate,
        ausgaben: List<ExpenseEntity>,
        einnahmen: List<IncomeEntity>,
        eroeffnungCents: Long = 0L,
        transfers: List<Transfer> = emptyList()
    ): Ergebnis {
        val vonTag = von.toEpochDay()
        val bisTag = bis.toEpochDay()

        var vorher = eroeffnungCents
        ausgaben.forEach { if (it.date < vonTag) vorher -= it.amountCents }
        einnahmen.forEach { if (it.date < vonTag) vorher += it.amountCents }

        val imZeitraum = DateRange(vonTag, bisTag)
        var einnahmenSumme = 0L
        einnahmen.forEach { if (imZeitraum.contains(it.date)) einnahmenSumme += it.amountCents }
        var ausgabenSumme = 0L
        ausgaben.forEach { if (imZeitraum.contains(it.date)) ausgabenSumme += it.amountCents }

        val transfersSumme = transfers.filter { it.date.toEpochDay() in vonTag..bisTag }
            .sumOf { abs(it.cents) }

        val punkte = mutableListOf<Punkt>()
        var monat = YearMonth.from(von)
        val letzter = YearMonth.from(bis)
        while (monat <= letzter) {
            val mVon = monat.atDay(1).toEpochDay()
            val mBis = monat.atEndOfMonth().toEpochDay()
            var e = 0L
            var a = 0L
            einnahmen.forEach { if (it.date in mVon..mBis) e += it.amountCents }
            ausgaben.forEach { if (it.date in mVon..mBis) a += it.amountCents }
            punkte += Punkt(monat, e, a, e - a)
            monat = monat.plusMonths(1)
        }

        val end = vorher + einnahmenSumme - ausgabenSumme
        return Ergebnis(
            von = von,
            bis = bis,
            startCents = vorher,
            einnahmenCents = einnahmenSumme,
            ausgabenCents = ausgabenSumme,
            transfersCents = transfersSumme,
            ersparnisCents = einnahmenSumme - ausgabenSumme,
            endCents = end,
            punkte = punkte
        )
    }

    /** Interner Transfer zwischen zwei eigenen Konten/Vermoegenswerten. */
    data class Transfer(val date: LocalDate, val cents: Long, val text: String)
}

// ===========================================================================
// Modul 24 - Nettovermoegen
// Vermoegen (Bank, Bar, Sparkonto, Anlagen, Sonstiges) minus Schulden
// (Kredite, Kreditkarten, sonstige Verbindlichkeiten).
// ===========================================================================

object NetWorth {

    data class Gruppe(val kind: String, val cents: Long, val istSchuld: Boolean)

    data class Punkt(val monat: YearMonth, val cents: Long)

    data class Ergebnis(
        val vermoegenCents: Long,
        val schuldenCents: Long,
        val nettoCents: Long,
        val gruppen: List<Gruppe>,
        val punkte: List<Punkt>
    )

    fun berechne(
        werte: List<com.finovo.app.data.AssetEntity>,
        ausgaben: List<ExpenseEntity>,
        einnahmen: List<IncomeEntity>,
        heute: LocalDate = Dates.today(),
        monate: Int = 12
    ): Ergebnis {
        var vermoegen = 0L
        var schulden = 0L
        val summen = linkedMapOf<String, Long>()
        werte.forEach { w ->
            if (w.isLiability) schulden += abs(w.amountCents)
            else vermoegen += abs(w.amountCents)
            summen[w.kind] = (summen[w.kind] ?: 0L) + abs(w.amountCents)
        }
        val gruppen = summen.map { (kind, cents) ->
            Gruppe(
                kind = kind,
                cents = cents,
                istSchuld = kind == com.finovo.app.data.AssetKind.LOAN.name ||
                    kind == com.finovo.app.data.AssetKind.CREDIT_CARD.name ||
                    kind == com.finovo.app.data.AssetKind.OTHER_DEBT.name
            )
        }.sortedByDescending { it.cents }

        // Entwicklung: vom heutigen Stand rueckwaerts ueber die echten
        // Monatssalden (Einnahmen - Ausgaben) gerechnet.
        val netto = vermoegen - schulden
        val punkte = mutableListOf<Punkt>()
        val dieserMonat = YearMonth.from(heute)
        val salden = mutableMapOf<YearMonth, Long>()
        ausgaben.forEach { salden[YearMonth.from(Dates.fromEpochDay(it.date))] =
            (salden[YearMonth.from(Dates.fromEpochDay(it.date))] ?: 0L) - it.amountCents }
        einnahmen.forEach { salden[YearMonth.from(Dates.fromEpochDay(it.date))] =
            (salden[YearMonth.from(Dates.fromEpochDay(it.date))] ?: 0L) + it.amountCents }

        var stand = netto
        for (i in 0 until monate) {
            val monat = dieserMonat.minusMonths(i.toLong())
            punkte += Punkt(monat, stand)
            stand -= (salden[monat] ?: 0L)
        }

        return Ergebnis(
            vermoegenCents = vermoegen,
            schuldenCents = schulden,
            nettoCents = netto,
            gruppen = gruppen,
            punkte = punkte.reversed()
        )
    }
}

// ===========================================================================
// Modul 26 - Jahresbericht
// ===========================================================================

object Jahresbericht {

    data class Monat(val monat: YearMonth, val einnahmenCents: Long, val ausgabenCents: Long)

    data class BudgetMonat(
        val monat: YearMonth,
        val limitCents: Long,
        val ausgabenCents: Long
    ) {
        val eingehalten: Boolean get() = limitCents <= 0 || ausgabenCents <= limitCents
    }

    data class Ziel(
        val id: Long,
        val name: String,
        val zielCents: Long,
        val gespartCents: Long,
        val emoji: String
    )

    data class Ergebnis(
        val jahr: Int,
        val einnahmenCents: Long,
        val ausgabenCents: Long,
        val ersparnisCents: Long,
        val quote: Float,
        val kategorien: List<CategoryTotal>,
        val groessteAusgaben: List<ExpenseEntity>,
        val wiederkehrendCents: Long,
        val abosCents: Long,
        val budgetMonate: List<BudgetMonat>,
        val ziele: List<Ziel>,
        val monate: List<Monat>,
        val anzahlBuchungen: Int
    )

    fun berechne(
        jahr: Int,
        ausgaben: List<ExpenseEntity>,
        einnahmen: List<IncomeEntity>,
        abos: List<SubscriptionEntity>,
        kategorien: List<CategoryEntity>,
        ziele: List<SavingGoalEntity>,
        budget: BudgetEntity?,
        symbol: String = "€"
    ): Ergebnis {
        val von = LocalDate.of(jahr, 1, 1).toEpochDay()
        val bis = LocalDate.of(jahr, 12, 31).toEpochDay()
        val range = DateRange(von, bis)

        val ausgabenImJahr = ausgaben.filter { it.date in von..bis }
        val einnahmenImJahr = einnahmen.filter { it.date in von..bis }

        val einnahmenSumme = einnahmenImJahr.sumOf { it.amountCents }
        val ausgabenSumme = ausgabenImJahr.sumOf { it.amountCents }

        val kategorienSummen = Stats.categoryTotals(ausgabenImJahr, kategorien, range)
        val groesste = ausgabenImJahr.sortedByDescending { it.amountCents }.take(10)
        val wiederkehrend = ausgabenImJahr.filter { it.recurringId != null }.sumOf { it.amountCents }

        val abosSumme = abos.filter { it.active }.sumOf { abo ->
            val spec = RecurrenceSpec(
                frequency = runCatching { com.finovo.app.data.Frequency.valueOf(abo.frequency) }
                    .getOrDefault(com.finovo.app.data.Frequency.MONTHLY),
                interval = abo.interval.coerceAtLeast(1),
                customDays = abo.customDays.coerceAtLeast(1),
                startDate = Dates.fromEpochDay(abo.startDate),
                endDate = abo.endDate?.let { Dates.fromEpochDay(it) }
            )
            Recurrence.occurrences(spec, LocalDate.of(jahr, 1, 1), LocalDate.of(jahr, 12, 31))
                .sumOf { abo.amountCents }
        }

        val budgetMonate = mutableListOf<BudgetMonat>()
        if (budget != null && budget.enabled && budget.monthlyLimitCents > 0) {
            for (m in 1..12) {
                val monat = YearMonth.of(jahr, m)
                val mVon = monat.atDay(1).toEpochDay()
                val mBis = monat.atEndOfMonth().toEpochDay()
                val summe = ausgabenImJahr.filter { it.date in mVon..mBis }.sumOf { it.amountCents }
                budgetMonate += BudgetMonat(monat, budget.monthlyLimitCents, summe)
            }
        }

        val monate = (1..12).map { m ->
            val monat = YearMonth.of(jahr, m)
            val mVon = monat.atDay(1).toEpochDay()
            val mBis = monat.atEndOfMonth().toEpochDay()
            Monat(
                monat = monat,
                einnahmenCents = einnahmenImJahr.filter { it.date in mVon..mBis }.sumOf { it.amountCents },
                ausgabenCents = ausgabenImJahr.filter { it.date in mVon..mBis }.sumOf { it.amountCents }
            )
        }

        return Ergebnis(
            jahr = jahr,
            einnahmenCents = einnahmenSumme,
            ausgabenCents = ausgabenSumme,
            ersparnisCents = einnahmenSumme - ausgabenSumme,
            quote = if (einnahmenSumme > 0) (einnahmenSumme - ausgabenSumme).toFloat() / einnahmenSumme else 0f,
            kategorien = kategorienSummen,
            groessteAusgaben = groesste,
            wiederkehrendCents = wiederkehrend,
            abosCents = abosSumme,
            budgetMonate = budgetMonate,
            ziele = ziele.filter { !it.archived }.map {
                Ziel(it.id, it.name, it.targetCents, it.savedCents, it.emoji)
            },
            monate = monate,
            anzahlBuchungen = ausgabenImJahr.size + einnahmenImJahr.size
        )
    }
}

// ===========================================================================
// Modul 16 - Einblicke (faktenbasiert, keine Entscheidungen fuer den Nutzer)
// ===========================================================================

object Einblicke {

    enum class Art { TOP_KATEGORIE, ANSTIEG, UNGEWOEHNLICH, MONATSVERGLEICH, SPARPOTENZIAL }

    data class Einblick(
        val art: Art,
        val titel: String,
        val text: String,
        val emoji: String,
        val kategorieId: Long? = null,
        val ausgabeId: Long? = null,
        val monat: YearMonth? = null
    )

    /** Liefert ausschliesslich auswertbare Fakten - mit Optionen, nie mit Vorgaben. */
    fun berechne(
        heute: LocalDate = Dates.today(),
        ausgaben: List<ExpenseEntity>,
        einnahmen: List<IncomeEntity>,
        kategorien: List<CategoryEntity>,
        symbol: String = "€"
    ): List<Einblick> {
        if (ausgaben.size < 3) return emptyList()
        val liste = mutableListOf<Einblick>()
        val dieserMonat = YearMonth.from(heute)
        val letzterMonat = dieserMonat.minusMonths(1)

        fun summe(monat: YearMonth, kategorieId: Long? = null): Long {
            val von = monat.atDay(1).toEpochDay()
            val bis = monat.atEndOfMonth().toEpochDay()
            return ausgaben.filter { it.date in von..bis && (kategorieId == null || it.categoryId == kategorieId) }
                .sumOf { it.amountCents }
        }

        // 1) Groesste Kategorien im aktuellen Monat
        val top = Stats.categoryTotals(
            ausgaben,
            kategorien,
            DateRange(dieserMonat.atDay(1).toEpochDay(), dieserMonat.atEndOfMonth().toEpochDay())
        ).take(3)
        top.forEach { k ->
            liste += Einblick(
                art = Art.TOP_KATEGORIE,
                titel = str("ins_top_title", "Größte Ausgabenkategorie: %1\$s", k.name),
                text = str(
                    "ins_top_text",
                    "%1\$s in diesem Monat · %2\$s aller Ausgaben",
                    Money.format(k.cents, symbol),
                    String.format(AppLocale.locale(), "%.0f%%", k.percent * 100f)
                ),
                emoji = k.emoji,
                kategorieId = k.categoryId
            )
        }

        // 2) Deutliche Anstiege gegenueber dem Vormonat
        kategorien.forEach { kat ->
            val jetzt = summe(dieserMonat, kat.id)
            val vorher = summe(letzterMonat, kat.id)
            if (vorher > 0 && jetzt > vorher) {
                val plus = ((jetzt - vorher).toFloat() / vorher) * 100f
                if (plus >= 25f) {
                    liste += Einblick(
                        art = Art.ANSTIEG,
                        titel = str("ins_rise_title", "Deutlicher Anstieg bei %1\$s", kat.name),
                        text = str(
                            "ins_rise_text",
                            "%1\$s im Vergleich zum Vormonat (%2\$s → %3\$s).",
                            String.format(AppLocale.locale(), "%.0f%%", plus),
                            Money.format(vorher, symbol),
                            Money.format(jetzt, symbol)
                        ),
                        emoji = "📈",
                        kategorieId = kat.id
                    )
                }
            }
        }

        // 3) Ungewoehnlich hohe Einzelbuchungen (ueber dem 3-fachen Monatsschnitt)
        val schnitt = run {
            val werte = ausgaben.map { it.amountCents }
            if (werte.isEmpty()) 0L else werte.sorted()[werte.size / 2]
        }
        if (schnitt > 0) {
            ausgaben.filter { it.amountCents >= schnitt * 3 && it.date >= dieserMonat.atDay(1).toEpochDay() }
                .take(5)
                .forEach { e ->
                    val kat = kategorien.firstOrNull { it.id == e.categoryId }
                    liste += Einblick(
                        art = Art.UNGEWOEHNLICH,
                        titel = str("ins_unusual_title", "Ungewöhnlich hohe Ausgabe"),
                        text = str(
                            "ins_unusual_text",
                            "%1\$s am %2\$s%3\$s",
                            Money.format(e.amountCents, symbol),
                            Dates.full(Dates.fromEpochDay(e.date)),
                            if (kat != null) " · " + kat.name else ""
                        ),
                        emoji = "🔍",
                        ausgabeId = e.id,
                        kategorieId = e.categoryId
                    )
                }
        }

        // 4) Monatsvergleich gesamt
        val jetzt = summe(dieserMonat)
        val vorher = summe(letzterMonat)
        if (vorher > 0) {
            val diff = jetzt - vorher
            liste += Einblick(
                art = Art.MONATSVERGLEICH,
                titel = str("ins_compare_title", "Ausgaben im Monatsvergleich"),
                text = if (diff >= 0) str(
                    "ins_compare_more",
                    "Dieser Monat: %1\$s · Vormonat: %2\$s · das sind %3\$s mehr.",
                    Money.format(jetzt, symbol),
                    Money.format(vorher, symbol),
                    Money.format(diff, symbol)
                ) else str(
                    "ins_compare_less",
                    "Dieser Monat: %1\$s · Vormonat: %2\$s · das sind %3\$s weniger.",
                    Money.format(jetzt, symbol),
                    Money.format(vorher, symbol),
                    Money.format(-diff, symbol)
                ),
                emoji = if (diff >= 0) "📊" else "✅"
            )
        }

        // 5) Sparpotenzial: Kategorien mit gleichbleibenden Kosten nennen, nichts vorschreiben
        val fixKategorien = kategorien.filter { it.isFixed }
        if (fixKategorien.isNotEmpty()) {
            val summeFix = fixKategorien.sumOf { summe(dieserMonat, it.id) }
            if (summeFix > 0) {
                liste += Einblick(
                    art = Art.SPARPOTENZIAL,
                    titel = str("ins_saving_title", "Wo dein Geld regelmäßig hinfließt"),
                    text = str(
                        "ins_saving_text",
                        "%1\$s entfallen diesen Monat auf feste Kategorien (%2\$s). Ob und wo du etwas verändern möchtest, entscheidest du.",
                        Money.format(summeFix, symbol),
                        fixKategorien.joinToString(", ") { it.name }
                    ),
                    emoji = "💡"
                )
            }
        }

        return liste
    }
}

// ===========================================================================
// Modul 13 - Wiederkehrende Buchungen automatisch erkennen
// ===========================================================================

object WiederkehrendErkennung {

    data class Vorschlag(
        val titel: String,
        val kategorieId: Long,
        val betragCents: Long,
        val intervallTage: Long,
        val anzahl: Int,
        val letzte: LocalDate,
        val naechste: LocalDate,
        val istEinnahme: Boolean,
        val regel: RecurringRuleEntity
    )

    private fun schluessel(text: String): String =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length > 2 }
            .take(4)
            .joinToString(" ")

    /** Lesbares Intervall, z. B. "alle 30 Tage". */
    fun intervallText(tage: Long): String = when (tage) {
        1L -> str("rec_daily", "täglich")
        7L -> str("rec_weekly", "wöchentlich")
        14L -> str("rec_biweekly", "alle 14 Tage")
        in 27..32 -> str("rec_monthly", "monatlich")
        in 88..95 -> str("rec_quarterly", "vierteljährlich")
        in 360..370 -> str("rec_yearly", "jährlich")
        else -> str("rec_every_days", "alle %1\$s Tage", tage.toString())
    }

    /**
     * Findet Buchungen, die in gleichbleibendem Abstand und mit nahezu
     * gleichem Betrag wiederkehren - getrennt nach Ausgaben und Einnahmen.
     */
    fun erkennen(
        ausgaben: List<ExpenseEntity>,
        einnahmen: List<IncomeEntity>,
        heute: LocalDate = Dates.today(),
        mindestFaelle: Int = 3
    ): List<Vorschlag> {
        val liste = mutableListOf<Vorschlag>()

        fun auswerten(
            punkte: List<Pair<Long, Triple<String, Long, Long>>>,
            istEinnahme: Boolean
        ) {
            val gruppen = punkte
                .filter { (it.second.third) <= heute.toEpochDay() }
                .groupBy { (_, daten) -> schluessel(daten.first) + "#" + daten.third }
            gruppen.forEach { (_, eintraege) ->
                if (eintraege.size < mindestFaelle) return@forEach
                val sortiert = eintraege.map { it.first }.sorted()
                val abstaende = sortiert.zipWithNext { a, b -> b - a }
                val median = abstaende.sorted()[abstaende.size / 2]
                val streuung = abstaende.maxOf { abs(it - median) }
                if (median <= 0 || streuung > 3) return@forEach
                val betraege = eintraege.map { it.second.second }
                val referenz = betraege.sorted()[betraege.size / 2]
                if (referenz <= 0) return@forEach
                if (betraege.any { abs(it - referenz) > referenz / 5 }) return@forEach

                val letzte = Dates.fromEpochDay(sortiert.last())
                val naechste = Dates.plusTage(letzte, median)
                val name = eintraege.first().second.first.ifBlank {
                    str("rec_automatic", "Automatisch erkannt")
                }
                val kategorieId = eintraege.first().second.third
                val regel = RecurringRuleEntity(
                    kind = if (istEinnahme) RecurringKind.INCOME.name else RecurringKind.EXPENSE.name,
                    name = name,
                    amountCents = referenz,
                    categoryId = kategorieId,
                    frequency = when {
                        median in 6..8 -> com.finovo.app.data.Frequency.WEEKLY.name
                        median in 27..32 -> com.finovo.app.data.Frequency.MONTHLY.name
                        median >= 360 -> com.finovo.app.data.Frequency.YEARLY.name
                        median == 1L -> com.finovo.app.data.Frequency.DAILY.name
                        else -> com.finovo.app.data.Frequency.CUSTOM.name
                    },
                    interval = 1,
                    customDays = median.toInt().coerceIn(1, 3650),
                    dayOfMonth = letzte.dayOfMonth,
                    monthOfYear = letzte.monthValue,
                    startDate = letzte.toEpochDay(),
                    active = true
                )
                liste += Vorschlag(
                    titel = name,
                    kategorieId = kategorieId,
                    betragCents = referenz,
                    intervallTage = median,
                    anzahl = eintraege.size,
                    letzte = letzte,
                    naechste = naechste,
                    istEinnahme = istEinnahme,
                    regel = regel
                )
            }
        }

        auswerten(ausgaben.map {
            it.date to Triple(
                it.merchant.ifBlank { it.note },
                it.amountCents,
                it.categoryId
            )
        }, false)
        auswerten(einnahmen.map {
            it.date to Triple(
                it.merchant.ifBlank { it.note },
                it.amountCents,
                it.categoryId
            )
        }, true)

        return liste.sortedByDescending { it.anzahl }
    }
}

// ===========================================================================
// Modul 19 - Globale Finanzsuche
// ===========================================================================

object Suche {

    enum class Gruppe {
        AUSGABE, EINNAHME, RECHNUNG, ABO, ZIEL, KATEGORIE, DOKUMENT, VERLAUF
    }

    data class Treffer(
        val gruppe: Gruppe,
        val titel: String,
        val untertitel: String,
        val cents: Long?,
        val datum: LocalDate?,
        val ausgabeId: Long? = null,
        val einnahmeId: Long? = null,
        val rechnungId: Long? = null,
        val aboId: Long? = null,
        val zielId: Long? = null,
        val kategorieId: Long? = null,
        val dokumentId: Long? = null
    )

    private val monate = mapOf(
        "januar" to 1, "jan" to 1, "january" to 1, "gennaio" to 1, "janar" to 1, "ocak" to 1,
        "februar" to 2, "feb" to 2, "february" to 2, "febbraio" to 2, "shkurt" to 2, "subat" to 2, "şubat" to 2,
        "märz" to 3, "maerz" to 3, "mär" to 3, "march" to 3, "mar" to 3, "marzo" to 3, "mars" to 3, "mart" to 3,
        "april" to 4, "apr" to 4, "aprile" to 4, "prill" to 4, "nisan" to 4,
        "mai" to 5, "may" to 5, "maggio" to 5, "maj" to 5, "mayıs" to 5, "mayis" to 5,
        "juni" to 6, "jun" to 6, "june" to 6, "giugno" to 6, "qershor" to 6, "haziran" to 6,
        "juli" to 7, "jul" to 7, "july" to 7, "luglio" to 7, "korrik" to 7, "temmuz" to 7,
        "august" to 8, "aug" to 8, "agosto" to 8, "gusht" to 8, "ağustos" to 8, "agustos" to 8,
        "september" to 9, "sep" to 9, "sept" to 9, "settembre" to 9, "shtator" to 9, "eylül" to 9, "eylul" to 9,
        "oktober" to 10, "okt" to 10, "october" to 10, "oct" to 10, "ottobre" to 10, "tetor" to 10, "ekim" to 10,
        "november" to 11, "nov" to 11, "novembre" to 11, "nëntor" to 11, "kasım" to 11, "kasim" to 11,
        "dezember" to 12, "dez" to 12, "december" to 12, "dec" to 12, "dicembre" to 12, "dhjetor" to 12,
        "aralık" to 12, "aralik" to 12
    )

    private val stopwoerter = setOf(
        "show", "find", "search", "all", "my", "me", "the", "from", "in", "of", "for", "what",
        "wie", "viel", "alle", "zeige", "suche", "finde", "meine", "meinen", "der", "die", "das",
        "von", "im", "in", "aus", "und", "wo", "mir", "ich", "ausgaben", "ausgabe", "einnahmen",
        "mostra", "trova", "cerca", "tutte", "miei", "mie", "shfaq", "gjej", "kërko", "të", "göster",
        "bul", "ara", "tüm", "benim"
    )

    data class Kontext(
        val ausgaben: List<ExpenseEntity>,
        val einnahmen: List<IncomeEntity>,
        val rechnungen: List<BillEntity>,
        val abos: List<SubscriptionEntity>,
        val ziele: List<SavingGoalEntity>,
        val kategorien: List<CategoryEntity>,
        val dokumente: List<DocumentEntity>,
        val symbol: String = "€"
    )

    fun suche(eingabe: String, k: Kontext): List<Treffer> {
        val query = eingabe.trim()
        if (query.length < 2) return emptyList()
        val q = query.lowercase()
        val woerter = q.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() && it !in stopwoerter }

        var monat: Int? = null
        var jahr: Int? = null
        val datumWoerter = mutableSetOf<String>()
        woerter.forEach { w ->
            if (monate[w] != null) {
                monat = monate[w]
                datumWoerter += w
            }
            if (w.length == 4 && w.all { it.isDigit() }) {
                val z = w.toIntOrNull()
                if (z != null && z in 2000..2100) {
                    jahr = z
                    datumWoerter += w
                }
            }
        }
        // Datumshinweise filtern nur - sie müssen nicht im Text vorkommen
        val freitext = woerter.filter { it.length > 1 && it !in datumWoerter }
        val betrag = Money.parseToCents(query)

        val treffer = mutableListOf<Treffer>()

        fun passt(vararg texte: String?): Boolean {
            if (freitext.isEmpty()) return true
            val basis = texte.filterNotNull().joinToString(" ").lowercase()
            return freitext.all { basis.contains(it) }
        }

        fun datumPasst(tag: Long): Boolean {
            val d = Dates.fromEpochDay(tag)
            if (monat != null && d.monthValue != monat) return false
            if (jahr != null && d.year != jahr) return false
            return true
        }

        val kategorieNamen = k.kategorien.associateBy { it.id }

        k.ausgaben.forEach { e ->
            val kat = kategorieNamen[e.categoryId]
            val text = listOf(e.merchant, e.note, kat?.name ?: "", e.tags, e.location)
            if (betrag != null && e.amountCents != betrag) return@forEach
            if (!datumPasst(e.date)) return@forEach
            if (passt(e.merchant, e.note, kat?.name, e.tags, e.location)) {
                treffer += Treffer(
                    gruppe = Gruppe.AUSGABE,
                    titel = e.merchant.ifBlank { e.note }.ifBlank { kat?.name ?: "" },
                    untertitel = listOf(
                        Dates.full(Dates.fromEpochDay(e.date)),
                        kat?.name,
                        Money.format(e.amountCents, k.symbol)
                    ).filter { !it.isNullOrBlank() }.joinToString(" · "),
                    cents = e.amountCents,
                    datum = Dates.fromEpochDay(e.date),
                    ausgabeId = e.id,
                    kategorieId = e.categoryId
                )
            }
        }

        k.einnahmen.forEach { e ->
            val kat = kategorieNamen[e.categoryId]
            if (betrag != null && e.amountCents != betrag) return@forEach
            if (!datumPasst(e.date)) return@forEach
            if (passt(e.merchant, e.note, kat?.name, e.tags)) {
                treffer += Treffer(
                    gruppe = Gruppe.EINNAHME,
                    titel = e.merchant.ifBlank { e.note }.ifBlank { kat?.name ?: "" },
                    untertitel = Dates.full(Dates.fromEpochDay(e.date)) +
                        (kat?.name?.let { " · $it" } ?: ""),
                    cents = e.amountCents,
                    datum = Dates.fromEpochDay(e.date),
                    einnahmeId = e.id,
                    kategorieId = e.categoryId
                )
            }
        }

        k.rechnungen.forEach { r ->
            if (betrag != null && r.amountCents != betrag) return@forEach
            if (!datumPasst(r.dueDate)) return@forEach
            if (passt(r.name, r.vendor, r.note, r.invoiceNumber)) {
                treffer += Treffer(
                    gruppe = Gruppe.RECHNUNG,
                    titel = r.name,
                    untertitel = listOf(Dates.full(Dates.fromEpochDay(r.dueDate)), r.vendor)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    cents = r.amountCents,
                    datum = Dates.fromEpochDay(r.dueDate),
                    rechnungId = r.id
                )
            }
        }

        k.abos.forEach { a ->
            if (!datumPasst(a.nextPayment)) return@forEach
            if (passt(a.name, a.note)) {
                treffer += Treffer(
                    gruppe = Gruppe.ABO,
                    titel = a.name,
                    untertitel = Dates.full(Dates.fromEpochDay(a.nextPayment)),
                    cents = a.amountCents,
                    datum = Dates.fromEpochDay(a.nextPayment),
                    aboId = a.id
                )
            }
        }

        k.ziele.forEach { z ->
            if (passt(z.name, z.note)) {
                treffer += Treffer(
                    gruppe = Gruppe.ZIEL,
                    titel = z.name,
                    untertitel = Money.format(z.savedCents, k.symbol) + " / " +
                        Money.format(z.targetCents, k.symbol),
                    cents = z.targetCents,
                    datum = null,
                    zielId = z.id
                )
            }
        }

        k.kategorien.forEach { kat ->
            if (passt(kat.name)) {
                treffer += Treffer(
                    gruppe = Gruppe.KATEGORIE,
                    titel = kat.name,
                    untertitel = str("suche_kategorie", "Kategorie"),
                    cents = null,
                    datum = null,
                    kategorieId = kat.id
                )
            }
        }

        k.dokumente.forEach { d ->
            if (!datumPasst(d.date)) return@forEach
            if (passt(d.title, d.tags, d.note, d.merchant)) {
                treffer += Treffer(
                    gruppe = Gruppe.DOKUMENT,
                    titel = d.title,
                    untertitel = listOf(Dates.full(Dates.fromEpochDay(d.date)), d.tags)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    cents = d.amountCents,
                    datum = Dates.fromEpochDay(d.date),
                    dokumentId = d.id
                )
            }
        }

        return treffer.sortedByDescending { it.datum?.toEpochDay() ?: 0L }
    }
}

// ===========================================================================
// Modul 30 - Datenimport (CSV / Excel-Export / Bankdateien)
// Vorschau vor dem Speichern, Spaltenzuordnung durch den Nutzer.
// ===========================================================================

object CsvImport {

    enum class Feld { DATUM, BESCHREIBUNG, BETRAG, KATEGORIE, KONTO, SONSTIGES }

    data class Tabelle(
        val spalten: List<String>,
        val zeilen: List<List<String>>
    )

    data class Zuordnung(
        val datum: Int = -1,
        val beschreibung: Int = -1,
        val betrag: Int = -1,
        val kategorie: Int = -1,
        val konto: Int = -1,
        val datumFormat: DatumFormat = DatumFormat.AUTO,
        val negation: Boolean = false
    )

    enum class DatumFormat { AUTO, TT_MM_JJJJ, JJJJ_MM_TT, TT_MM_JJ }

    data class Zeile(
        val datum: LocalDate?,
        val beschreibung: String,
        val betragCents: Long?,
        val istEinnahme: Boolean,
        val kategorieName: String,
        val konto: String,
        val roh: List<String>
    ) {
        val ok: Boolean get() = datum != null && betragCents != null && betragCents != 0L
    }

    fun trennzeichen(text: String): Char {
        val erste = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        return listOf(';', ',', '\t', '|').maxByOrNull { t -> erste.count { it == t } } ?: ','
    }

    fun lesen(text: String): Tabelle {
        val trenner = trennzeichen(text)
        val zeilen = mutableListOf<List<String>>()
        text.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            zeilen += teile(line, trenner)
        }
        if (zeilen.isEmpty()) return Tabelle(emptyList(), emptyList())
        val kopf = zeilen.first()
        val hatKopfzeile = kopf.any { it.isNotBlank() && !it.trim().first().isDigit() }
        val spalten = if (hatKopfzeile) kopf.map { it.trim().ifBlank { "Spalte" } }
        else kopf.indices.map { "Spalte ${it + 1}" }
        val daten = if (hatKopfzeile) zeilen.drop(1) else zeilen
        return Tabelle(spalten, daten)
    }

    private fun teile(zeile: String, trenner: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in zeile) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == trenner && !inQuotes -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(c)
            }
        }
        out += sb.toString().trim()
        return out
    }

    private val datumWoerter = listOf(
        "datum", "date", "buchungstag", "buchungsdatum", "valuta", "data", "data e"
    )
    private val beschreibungsWoerter = listOf(
        "beschreibung", "description", "buchungstext", "verwendungszweck", "empfaenger",
        "empfänger", "beguenstigter", "name", "descrizione", "pershkrim", "aciklama", "açıklama"
    )
    private val betragWoerter = listOf(
        "betrag", "amount", "umsatz", "wert", "importo", "shuma", "tutar", "value"
    )
    private val kategorieWoerter = listOf("kategorie", "category", "categoria", "kategori")
    private val kontoWoerter = listOf("konto", "account", "iban", "conto", "hesap")

    fun vorschlagen(t: Tabelle): Zuordnung {
        fun finde(woerter: List<String>, ausser: Set<Int> = emptySet()): Int {
            t.spalten.forEachIndexed { i, name ->
                if (i in ausser) return@forEachIndexed
                val n = name.lowercase()
                if (woerter.any { n.contains(it) }) return i
            }
            return -1
        }
        val datum = finde(datumWoerter)
        val betrag = finde(betragWoerter)
        val beschreibung = finde(beschreibungsWoerter, setOf(datum, betrag))
        val kategorie = finde(kategorieWoerter, setOf(datum, betrag, beschreibung))
        val konto = finde(kontoWoerter, setOf(datum, betrag, beschreibung, kategorie))
        return Zuordnung(
            datum = if (datum >= 0) datum else 0,
            betrag = if (betrag >= 0) betrag else t.spalten.size - 1,
            beschreibung = if (beschreibung >= 0) beschreibung else 1,
            kategorie = kategorie,
            konto = konto
        )
    }

    fun datumParsen(text: String, format: DatumFormat): LocalDate? {
        val roh = text.trim().replace("\"", "")
        if (roh.isBlank()) return null
        val zahlen = Regex("\\d+").findAll(roh).map { it.value.toIntOrNull() ?: 0 }.toList()
        if (zahlen.size >= 3) {
            val (a, b, c) = zahlen
            val jahr = if (c < 100) 2000 + c else c
            val kandidaten = when (format) {
                DatumFormat.JJJJ_MM_TT -> listOf(jahr, b, a)
                DatumFormat.TT_MM_JJ, DatumFormat.TT_MM_JJJJ -> listOf(jahr, b, a)
                DatumFormat.AUTO -> if (a > 31) listOf(a, b, c) else listOf(jahr, b, a)
            }
            return runCatching { LocalDate.of(kandidaten[0], kandidaten[1].coerceIn(1, 12), kandidaten[2].coerceIn(1, 31)) }
                .getOrNull()
        }
        return null
    }

    fun betragParsen(text: String): Pair<Long, Boolean>? {
        var roh = text.trim().replace("\"", "").replace(" ", "")
        if (roh.isBlank()) return null
        var negativ = false
        if (roh.startsWith("-")) { negativ = true; roh = roh.removePrefix("-") }
        if (roh.startsWith("+")) roh = roh.removePrefix("+")
        if (roh.endsWith("-")) { negativ = true; roh = roh.removeSuffix("-") }
        val cents = Money.parseToCents(roh) ?: return null
        return abs(cents) to negativ
    }

    fun anwenden(t: Tabelle, z: Zuordnung): List<Zeile> =
        t.zeilen.map { spalten ->
            fun wert(i: Int): String = if (i in spalten.indices) spalten[i] else ""
            val datum = datumParsen(wert(z.datum), z.datumFormat)
            val paar = betragParsen(wert(z.betrag))
            val istEinnahme = if (paar == null) false else
                if (z.negation) paar.second else !paar.second
            Zeile(
                datum = datum,
                beschreibung = listOf(wert(z.beschreibung), wert(z.konto)).filter { it.isNotBlank() }
                    .joinToString(" · ").trim(),
                betragCents = paar?.first,
                istEinnahme = istEinnahme,
                kategorieName = wert(z.kategorie),
                konto = wert(z.konto),
                roh = spalten
            )
        }

    fun kategorieFinden(name: String, kategorien: List<CategoryEntity>): Long {
        if (name.isBlank()) return 0L
        val treffer = kategorien.firstOrNull { it.name.equals(name, ignoreCase = true) }
        return treffer?.id ?: 0L
    }
}

// ===========================================================================
// Modul 34 - Mehrere Währungen (offline, Umrechnung in ganzen Cent)
// Kurse sind Basispunkte pro 1 EUR (1 EUR = 1,0000 -> 10000) und vom Nutzer
// anpassbar; es wird ausschliesslich mit Long gerechnet.
// ===========================================================================

object Waehrungen {

    data class Waehrung(val code: String, val symbol: String, val nameKey: String, val standardKurs: Long)

    val alle = listOf(
        Waehrung("EUR", "€", "cur_eur", 10000L),
        Waehrung("USD", "$", "cur_usd", 10800L),
        Waehrung("GBP", "£", "cur_gbp", 8500L),
        Waehrung("CHF", "CHF", "cur_chf", 9400L),
        Waehrung("ALL", "L", "cur_all", 984000L)
    )

    fun symbol(code: String): String = alle.firstOrNull { it.code == code }?.symbol ?: "€"

    fun name(code: String): String = when (code) {
        "EUR" -> str("cur_eur", "Euro")
        "USD" -> str("cur_usd", "US-Dollar")
        "GBP" -> str("cur_gbp", "Pfund Sterling")
        "CHF" -> str("cur_chf", "Schweizer Franken")
        "ALL" -> str("cur_all", "Albanischer Lek")
        else -> code
    }

    /** Standardkurse als Text: "USD=10800;GBP=8500;CHF=9400;ALL=984000" */
    fun standardKurse(): Map<String, Long> = alle.associate { it.code to it.standardKurs }

    fun ausText(text: String): Map<String, Long> {
        val out = standardKurse().toMutableMap()
        text.split(';').forEach { teil ->
            val p = teil.split('=')
            if (p.size == 2) {
                val code = p[0].trim().uppercase()
                val wert = p[1].trim().toLongOrNull()
                if (code.isNotBlank() && wert != null && wert > 0) out[code] = wert
            }
        }
        return out
    }

    fun zuText(kurse: Map<String, Long>): String =
        alle.joinToString(";") { "${it.code}=${kurse[it.code] ?: it.standardKurs}" }

    /** Rechnet einen Cent-Betrag von [von] nach [nach] um (inklusive Rundung). */
    fun umrechnen(cents: Long, von: String, nach: String, kurse: Map<String, Long> = standardKurse()): Long {
        if (von == nach) return cents
        val vonKurs = kurse[von] ?: 10000L
        val nachKurs = kurse[nach] ?: 10000L
        val inEuro = if (von == "EUR") cents else (cents * 10000L) / vonKurs
        return if (nach == "EUR") inEuro else (inEuro * nachKurs) / 10000L
    }
}

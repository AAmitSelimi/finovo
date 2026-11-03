package com.finovo.app

import com.finovo.app.ai.AiCommands
import com.finovo.app.ai.AiContext
import com.finovo.app.data.AssetEntity
import com.finovo.app.data.AssetKind
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BudgetEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryRuleEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.SavingGoalEntity
import com.finovo.app.data.SubscriptionEntity
import com.finovo.app.util.Alarme
import com.finovo.app.util.Alarm
import com.finovo.app.util.Cashflow
import com.finovo.app.util.CsvImport
import com.finovo.app.util.Dates
import com.finovo.app.util.Duplicates
import com.finovo.app.util.Einblicke
import com.finovo.app.util.Familie
import com.finovo.app.util.Jahresbericht
import com.finovo.app.util.NetWorth
import com.finovo.app.util.Prognose
import com.finovo.app.util.SmartCategories
import com.finovo.app.util.Suche
import com.finovo.app.util.Waehrungen
import com.finovo.app.util.Widgets
import com.finovo.app.util.WiederkehrendErkennung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * Prüft die Rechenkerne der Module 12 - 34.
 *
 * Alle Tests arbeiten mit echten Objekten und ganzen Cent-Beträgen (Long).
 * Es werden keine Zufalls- oder Demodaten verwendet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class ModuleLogicTest {

    // ---------------------------------------------------------------- Hilfen

    private fun kategorien(): List<CategoryEntity> = listOf(
        CategoryEntity(id = 1, name = "Lebensmittel", emoji = "\uD83D\uDED2",
            type = CategoryType.EXPENSE.name, isFixed = false),
        CategoryEntity(id = 2, name = "Auto", emoji = "\uD83D\uDE97",
            type = CategoryType.EXPENSE.name, isFixed = false),
        CategoryEntity(id = 3, name = "Miete", emoji = "\uD83C\uDFE0",
            type = CategoryType.EXPENSE.name, isFixed = true),
        CategoryEntity(id = 10, name = "Gehalt", emoji = "\uD83D\uDCB0",
            type = CategoryType.INCOME.name)
    )

    // =========================== Modul 14: Prognose =========================

    @Test
    fun prognose_rechnetErwarteteEinnahmenUndRestbetrag() {
        val monat = YearMonth.of(2026, 6)
        val heute = LocalDate.of(2026, 6, 10)

        val ausgaben = listOf(
            ExpenseEntity(id = 1, amountCents = 30_000L, categoryId = 3, date = tag(2026, 6, 2)),
            ExpenseEntity(id = 2, amountCents = 20_000L, categoryId = 1, date = tag(2026, 6, 5))
        )
        val einnahmen = listOf(
            IncomeEntity(id = 1, amountCents = 100_000L, categoryId = 10, date = tag(2026, 6, 1))
        )
        val rechnungen = listOf(
            BillEntity(id = 1, name = "Strom", amountCents = 10_000L, dueDate = tag(2026, 6, 15))
        )
        val regeln = listOf(
            RecurringRuleEntity(
                id = 1, kind = RecurringKind.INCOME.name, name = "Bonus",
                amountCents = 50_000L, categoryId = 10,
                frequency = com.finovo.app.data.Frequency.MONTHLY.name,
                interval = 1, dayOfMonth = 20, startDate = tag(2026, 6, 20), active = true
            )
        )

        val ergebnis = Prognose.berechne(
            monat = monat,
            heute = heute,
            ausgaben = ausgaben,
            einnahmen = einnahmen,
            rechnungen = rechnungen,
            abos = emptyList(),
            regeln = regeln,
            kategorien = kategorien(),
            budget = null
        )

        assertEquals(100_000L, ergebnis.einnahmenBisherCents)
        assertEquals(150_000L, ergebnis.einnahmenErwartetCents)
        assertEquals(30_000L, ergebnis.fixkostenBisherCents)
        assertEquals(20_000L, ergebnis.variableBisherCents)
        assertEquals(10, ergebnis.tageVorbei)
        assertEquals(30, ergebnis.tageImMonat)
        assertEquals(60_000L, ergebnis.variablePrognoseCents)   // 20 000 / 10 * 30
        assertEquals(10_000L, ergebnis.fixkostenErwartetCents)  // offene Rechnung
        assertEquals(150_000L - 100_000L, ergebnis.restbetragCents)
        assertEquals(1, ergebnis.anstehend.size)
        assertFalse(ergebnis.risiko)
    }

    @Test
    fun prognose_zeigtRisikoBeiBudgetueberschreitung() {
        val monat = YearMonth.of(2026, 6)
        val ergebnis = Prognose.berechne(
            monat = monat,
            heute = LocalDate.of(2026, 6, 30),
            ausgaben = listOf(
                ExpenseEntity(id = 1, amountCents = 90_000L, categoryId = 1, date = tag(2026, 6, 3))
            ),
            einnahmen = emptyList(),
            rechnungen = emptyList(),
            abos = emptyList(),
            regeln = emptyList(),
            kategorien = kategorien(),
            budget = BudgetEntity(id = 1, monthlyLimitCents = 50_000L, enabled = true)
        )
        assertTrue(ergebnis.risiko)
        assertEquals(50_000L, ergebnis.budgetLimitCents)
    }

    // =========================== Modul 23: Cashflow =========================

    @Test
    fun cashflow_startbestandAusAllenVorherigenBuchungen() {
        val ausgaben = listOf(
            ExpenseEntity(id = 1, amountCents = 10_000L, categoryId = 1, date = tag(2025, 12, 1)),
            ExpenseEntity(id = 2, amountCents = 5_000L, categoryId = 1, date = tag(2026, 2, 1))
        )
        val einnahmen = listOf(
            IncomeEntity(id = 1, amountCents = 50_000L, categoryId = 10, date = tag(2025, 6, 1)),
            IncomeEntity(id = 2, amountCents = 100_000L, categoryId = 10, date = tag(2026, 1, 15))
        )

        val ergebnis = Cashflow.berechne(
            von = LocalDate.of(2026, 1, 1),
            bis = LocalDate.of(2026, 12, 31),
            ausgaben = ausgaben,
            einnahmen = einnahmen,
            eroeffnungCents = 0L
        )

        assertEquals(40_000L, ergebnis.startCents)
        assertEquals(100_000L, ergebnis.einnahmenCents)
        assertEquals(5_000L, ergebnis.ausgabenCents)
        assertEquals(95_000L, ergebnis.ersparnisCents)
        assertEquals(135_000L, ergebnis.endCents)
        assertEquals(12, ergebnis.punkte.size)
    }

    @Test
    fun cashflow_transfersVeraendernDieErsparnisNicht() {
        val transfers = listOf(
            Cashflow.Transfer(LocalDate.of(2026, 3, 1), 25_000L, "Sparkonto")
        )
        val mitTransfer = Cashflow.berechne(
            von = LocalDate.of(2026, 1, 1), bis = LocalDate.of(2026, 12, 31),
            ausgaben = listOf(ExpenseEntity(id = 1, amountCents = 10_000L, categoryId = 1, date = tag(2026, 3, 2))),
            einnahmen = listOf(IncomeEntity(id = 1, amountCents = 20_000L, categoryId = 10, date = tag(2026, 3, 3))),
            eroeffnungCents = 5_000L,
            transfers = transfers
        )
        assertEquals(25_000L, mitTransfer.transfersCents)
        assertEquals(10_000L, mitTransfer.ersparnisCents)
        assertEquals(15_000L, mitTransfer.endCents)
    }

    // ========================== Modul 24: Nettovermögen =====================

    @Test
    fun nettovermoegen_assetsMinusSchulden() {
        val werte = listOf(
            AssetEntity(id = 1, name = "Girokonto", kind = AssetKind.BANK.name, amountCents = 100_000L, date = tag(2026, 1, 1)),
            AssetEntity(id = 2, name = "Sparbuch", kind = AssetKind.SAVINGS.name, amountCents = 50_000L, date = tag(2026, 1, 1)),
            AssetEntity(id = 3, name = "Autokredit", kind = AssetKind.LOAN.name, amountCents = 30_000L, date = tag(2026, 1, 1))
        )
        val ergebnis = NetWorth.berechne(werte, emptyList(), emptyList(), LocalDate.of(2026, 6, 1))

        assertEquals(150_000L, ergebnis.vermoegenCents)
        assertEquals(30_000L, ergebnis.schuldenCents)
        assertEquals(120_000L, ergebnis.nettoCents)
        assertEquals(12, ergebnis.punkte.size)
        assertEquals(120_000L, ergebnis.punkte.last().cents)
        assertTrue(ergebnis.gruppen.any { it.istSchuld && it.cents == 30_000L })
    }

    // ========================= Modul 26: Jahresbericht ======================

    @Test
    fun jahresbericht_summenUndBuchungsanzahl() {
        val ausgaben = listOf(
            ExpenseEntity(id = 1, amountCents = 10_000L, categoryId = 1, date = tag(2026, 2, 1)),
            ExpenseEntity(id = 2, amountCents = 5_000L, categoryId = 2, date = tag(2026, 3, 1)),
            ExpenseEntity(id = 3, amountCents = 7_000L, categoryId = 1, date = tag(2025, 3, 1))
        )
        val einnahmen = listOf(
            IncomeEntity(id = 1, amountCents = 100_000L, categoryId = 10, date = tag(2026, 1, 31))
        )
        val ziele = listOf(
            SavingGoalEntity(id = 1, name = "Auto", targetCents = 500_000L, savedCents = 125_000L)
        )

        val bericht = Jahresbericht.berechne(
            jahr = 2026,
            ausgaben = ausgaben,
            einnahmen = einnahmen,
            abos = emptyList(),
            kategorien = kategorien(),
            ziele = ziele,
            budget = null,
            symbol = "€"
        )

        assertEquals(100_000L, bericht.einnahmenCents)
        assertEquals(15_000L, bericht.ausgabenCents)
        assertEquals(85_000L, bericht.ersparnisCents)
        assertEquals(3, bericht.anzahlBuchungen)
        assertEquals(15_000L, bericht.kategorien.sumOf { it.cents })
        assertEquals(1, bericht.ziele.size)
        assertEquals(10_000L, bericht.groessteAusgaben.first().amountCents)
        assertEquals(12, bericht.monate.size)
    }

    // ==================== Modul 13: Wiederkehrend erkennen ==================

    @Test
    fun wiederkehrendErkennung_findetMonatlicheBuchung() {
        val ausgaben = (0..3).map { i ->
            ExpenseEntity(
                id = i.toLong() + 1,
                amountCents = 90_000L,
                categoryId = 3,
                merchant = "Miete Wohnung",
                date = LocalDate.of(2026, 1 + i, 1).toEpochDay()
            )
        }
        val vorschlaege = WiederkehrendErkennung.erkennen(
            ausgaben = ausgaben,
            einnahmen = emptyList(),
            heute = LocalDate.of(2026, 5, 1)
        )
        assertEquals(1, vorschlaege.size)
        val v = vorschlaege.first()
        assertEquals(90_000L, v.betragCents)
        assertEquals(4, v.anzahl)
        assertEquals(com.finovo.app.data.Frequency.MONTHLY.name, v.regel.frequency)
        assertEquals(LocalDate.of(2026, 5, 2), v.naechste)   // 01.04. + 31 Tage
    }

    @Test
    fun wiederkehrendErkennung_ignoriertUnregelmaessigeBuchungen() {
        val ausgaben = listOf(
            ExpenseEntity(id = 1, amountCents = 12_34L, categoryId = 1, merchant = "Aldi", date = tag(2026, 1, 3)),
            ExpenseEntity(id = 2, amountCents = 45_67L, categoryId = 1, merchant = "Aldi", date = tag(2026, 2, 19)),
            ExpenseEntity(id = 3, amountCents = 89_01L, categoryId = 1, merchant = "Aldi", date = tag(2026, 4, 2))
        )
        assertTrue(
            WiederkehrendErkennung.erkennen(ausgaben, emptyList(), LocalDate.of(2026, 5, 1))
                .isEmpty()
        )
    }

    // ====================== Modul 19: Globale Finanzsuche ===================

    private fun suchKontext(): Suche.Kontext = Suche.Kontext(
        ausgaben = listOf(
            ExpenseEntity(id = 1, amountCents = 12_34L, categoryId = 1, merchant = "Aldi", date = tag(2026, 3, 15)),
            ExpenseEntity(id = 2, amountCents = 50_00L, categoryId = 2, merchant = "Tankstelle", date = tag(2026, 1, 10))
        ),
        einnahmen = listOf(
            IncomeEntity(id = 1, amountCents = 250_000L, categoryId = 10, merchant = "Arbeitgeber", date = tag(2026, 1, 31))
        ),
        rechnungen = listOf(
            BillEntity(id = 1, name = "Stromrechnung", amountCents = 80_00L, dueDate = tag(2026, 3, 20))
        ),
        abos = emptyList(),
        ziele = emptyList(),
        kategorien = kategorien(),
        dokumente = emptyList(),
        symbol = "€"
    )

    @Test
    fun suche_findetHaendler() {
        val treffer = Suche.suche("Aldi", suchKontext())
        assertEquals(1, treffer.size)
        assertEquals(Suche.Gruppe.AUSGABE, treffer.first().gruppe)
        assertEquals(1L, treffer.first().ausgabeId)
    }

    @Test
    fun suche_findetRechnungenImMonat() {
        val treffer = Suche.suche("Rechnung März", suchKontext())
        assertEquals(1, treffer.size)
        assertEquals(Suche.Gruppe.RECHNUNG, treffer.first().gruppe)
    }

    @Test
    fun suche_findetKategorie() {
        val treffer = Suche.suche("Auto", suchKontext())
        assertTrue(treffer.any { it.gruppe == Suche.Gruppe.AUSGABE && it.kategorieId == 2L })
    }

    @Test
    fun suche_leerBeiKurzerEingabe() {
        assertTrue(Suche.suche("a", suchKontext()).isEmpty())
    }

    // ========================= Modul 30: CSV-Import =========================

    private val csvText = """
        Datum;Beschreibung;Betrag;Kategorie
        01.03.2026;Aldi Einkauf;12,34;Lebensmittel
        15.03.2026;Miete;1.000,00;Miete
        20.03.2026;Erstattung;-25,00;Sonstiges
    """.trimIndent()

    @Test
    fun csvImport_liestTabelleUndErkenntSpalten() {
        val tabelle = CsvImport.lesen(csvText)
        assertEquals(4, tabelle.spalten.size)
        assertEquals(3, tabelle.zeilen.size)

        val zuordnung = CsvImport.vorschlagen(tabelle)
        assertEquals(0, zuordnung.datum)
        assertEquals(1, zuordnung.beschreibung)
        assertEquals(2, zuordnung.betrag)
        assertEquals(3, zuordnung.kategorie)
    }

    @Test
    fun csvImport_parstBetraegeUndDaten() {
        val tabelle = CsvImport.lesen(csvText)
        val zuordnung = CsvImport.vorschlagen(tabelle)
        val zeilen = CsvImport.anwenden(tabelle, zuordnung)

        assertEquals(3, zeilen.size)
        assertTrue(zeilen.all { it.ok })
        assertEquals(12_34L, zeilen[0].betragCents)
        assertEquals(100_000L, zeilen[1].betragCents)
        assertEquals(2_500L, zeilen[2].betragCents)
        assertEquals(LocalDate.of(2026, 3, 1), zeilen[0].datum)
        assertEquals(LocalDate.of(2026, 3, 20), zeilen[2].datum)
        assertEquals("Aldi Einkauf", zeilen[0].beschreibung)
    }

    @Test
    fun csvImport_vorzeichenUmkehren() {
        val tabelle = CsvImport.lesen(csvText)
        val normal = CsvImport.anwenden(tabelle, CsvImport.vorschlagen(tabelle))
        val gedreht = CsvImport.anwenden(tabelle, CsvImport.vorschlagen(tabelle).copy(negation = true))

        // Ohne Umkehrung: positiver Betrag ist eine Einnahme, negativer eine Ausgabe
        assertTrue(normal[0].istEinnahme)
        assertTrue(normal[1].istEinnahme)
        assertFalse(normal[2].istEinnahme)
        // Mit Umkehrung dreht sich die Bedeutung (Ausgaben sind in der Datei positiv)
        assertFalse(gedreht[0].istEinnahme)
        assertFalse(gedreht[1].istEinnahme)
        assertTrue(gedreht[2].istEinnahme)
    }

    // ======================= Modul 34: Mehrere Währungen ====================

    @Test
    fun waehrungen_rechnenInGanzenCentUm() {
        val kurse = Waehrungen.standardKurse()
        assertEquals(10_800L, Waehrungen.umrechnen(10_000L, "EUR", "USD", kurse))
        assertEquals(10_000L, Waehrungen.umrechnen(10_000L, "EUR", "EUR", kurse))

        val inLek = Waehrungen.umrechnen(10_000L, "EUR", "ALL", kurse)
        assertEquals(984_000L, inLek)
        assertEquals(10_000L, Waehrungen.umrechnen(inLek, "ALL", "EUR", kurse))
    }

    @Test
    fun waehrungen_kurseRundlauf() {
        val text = Waehrungen.zuText(Waehrungen.standardKurse())
        val gelesen = Waehrungen.ausText(text)
        assertEquals(Waehrungen.standardKurse()["USD"], gelesen["USD"])
        assertEquals(5, gelesen.size)
    }

    // ========================== Modul 15: Alarme ============================

    @Test
    fun alarme_einstellungenRundlauf() {
        val zustand = Alarme.standard()
        val neu = Alarme.ausText(Alarme.zuText(zustand))
        assertTrue(neu.istAn(Alarm.LARGE_TRANSACTION))
        assertEquals(zustand.schwelle(Alarm.LARGE_TRANSACTION), neu.schwelle(Alarm.LARGE_TRANSACTION))

        val aus = Alarme.ausText("low_balance=0:5000;bill_due=1:3")
        assertFalse(aus.istAn(Alarm.LOW_BALANCE))
        assertEquals(5_000L, aus.schwelle(Alarm.LOW_BALANCE))
        assertTrue(aus.istAn(Alarm.BILL_DUE))
        assertEquals(3L, aus.schwelle(Alarm.BILL_DUE))
    }

    @Test
    fun alarme_lowBalanceUndAbschaltbar() {
        val heute = LocalDate.of(2026, 6, 15)
        val daten = Alarme.pruefen(
            z = Alarme.standard(),
            heute = heute,
            ausgaben = emptyList(),
            einnahmen = emptyList(),
            rechnungen = emptyList(),
            abos = emptyList(),
            regeln = emptyList(),
            kategorien = kategorien(),
            werte = emptyList(),
            budget = null,
            symbol = "€"
        )
        assertTrue(daten.any { it.alarm == Alarm.LOW_BALANCE })

        val ohne = Alarme.pruefen(
            z = Alarme.ausText("low_balance=0:10000"),
            heute = heute,
            ausgaben = emptyList(),
            einnahmen = emptyList(),
            rechnungen = emptyList(),
            abos = emptyList(),
            regeln = emptyList(),
            kategorien = kategorien(),
            werte = emptyList(),
            budget = null,
            symbol = "€"
        )
        assertTrue(ohne.none { it.alarm == Alarm.LOW_BALANCE })
    }

    @Test
    fun alarme_grosseBuchungWirdGemeldet() {
        val heute = LocalDate.of(2026, 6, 15)
        val ausgaben = listOf(
            ExpenseEntity(id = 1, amountCents = 60_000L, categoryId = 2, date = tag(2026, 6, 5), merchant = "Werkstatt")
        )
        val meldungen = Alarme.pruefen(
            z = Alarme.standard(),
            heute = heute,
            ausgaben = ausgaben,
            einnahmen = listOf(IncomeEntity(id = 1, amountCents = 500_000L, categoryId = 10, date = tag(2026, 6, 1))),
            rechnungen = emptyList(),
            abos = emptyList(),
            regeln = emptyList(),
            kategorien = kategorien(),
            werte = emptyList(),
            budget = null,
            symbol = "€"
        )
        assertTrue(meldungen.any { it.alarm == Alarm.LARGE_TRANSACTION })
        assertTrue(meldungen.none { it.alarm == Alarm.LOW_BALANCE })
    }

    // ====================== Modul 21: Dashboard-Widgets =====================

    @Test
    fun widgets_reihenfolgeBleibtErhalten() {
        val reihe = listOf(Widgets.Art.GOALS, Widgets.Art.BALANCE, Widgets.Art.CHART)
        val gelesen = Widgets.ausText(Widgets.zuText(reihe))
        assertEquals(reihe, gelesen)
        assertTrue(Widgets.ausText("").contains(Widgets.Art.BALANCE))
    }

    // ======================== Modul 22: Haushalt ============================

    @Test
    fun familie_gemeinsamUndEigeneAusgaben() {
        val mitglieder = listOf(
            Familie.Mitglied(1L, "Anna", "\uD83D\uDC67"),
            Familie.Mitglied(2L, "Ben", "\uD83D\uDC66", darfBearbeiten = false)
        )
        val bereiche = com.finovo.app.data.AreaEntity::class.java.let {
            listOf(
                com.finovo.app.data.AreaEntity(id = 1L, name = "Anna", emoji = "\uD83D\uDC67"),
                com.finovo.app.data.AreaEntity(id = 2L, name = "Ben", emoji = "\uD83D\uDC66")
            )
        }
        val text = Familie.zuText(mitglieder)
        val gelesen = Familie.ausText(text, bereiche)
        assertEquals(2, gelesen.size)
        assertTrue(gelesen.first().darfBearbeiten)
        assertFalse(gelesen[1].darfBearbeiten)

        val ausgaben = listOf(
            ExpenseEntity(id = 1, amountCents = 20_000L, categoryId = 1, date = tag(2026, 6, 2)),
            ExpenseEntity(id = 2, amountCents = 5_000L, categoryId = 1, date = tag(2026, 6, 3), areaId = 1L),
            ExpenseEntity(id = 3, amountCents = 7_000L, categoryId = 2, date = tag(2026, 6, 4), areaId = 2L)
        )
        val auswertung = Familie.auswerten(
            mitglieder = gelesen,
            ausgaben = ausgaben,
            einnahmen = emptyList(),
            von = LocalDate.of(2026, 6, 1),
            bis = LocalDate.of(2026, 6, 30)
        )
        assertEquals(20_000L, auswertung.gemeinsamCents)
        assertEquals(5_000L, auswertung.mitglieder[0].ausgabenCents)
        assertEquals(7_000L, auswertung.mitglieder[1].ausgabenCents)
    }

    // ======================== Modul 29: Duplikate ===========================

    @Test
    fun duplikate_werdenErkanntUndNieGeloescht() {
        val vorhanden = listOf(
            ExpenseEntity(id = 1, amountCents = 12_34L, categoryId = 1, merchant = "Aldi", date = tag(2026, 3, 15))
        )
        val identisch = ExpenseEntity(id = 0, amountCents = 12_34L, categoryId = 1, merchant = "Aldi", date = tag(2026, 3, 16))
        val anders = ExpenseEntity(id = 0, amountCents = 12_34L, categoryId = 1, merchant = "Aldi", date = tag(2026, 3, 20))

        assertEquals(1, Duplicates.findeZuAusgabe(identisch, vorhanden).size)
        assertTrue(Duplicates.findeZuAusgabe(anders, vorhanden).isEmpty())
        // Die Prüfung verändert die übergebene Liste nicht
        assertEquals(1, vorhanden.size)
    }

    // ==================== Modul 12: Smarte Kategorisierung ==================

    @Test
    fun smartCategories_lernenSchlaegtWoerterbuch() {
        val kategorien = kategorien()
        val regeln = listOf(
            CategoryRuleEntity(id = 1, token = "aldi", categoryId = 2, isIncome = false, hits = 3)
        )
        val vorschlag = SmartCategories.vorschlag("Aldi Einkauf", kategorien, regeln, false)

        assertNotNull(vorschlag)
        assertEquals(2L, vorschlag!!.categoryId)
        assertEquals(SmartCategories.Grund.GELERNT, vorschlag.grund)
        assertTrue(vorschlag.sicher)
    }

    @Test
    fun smartCategories_unterkategorienUndPfad() {
        val alle = listOf(
            CategoryEntity(id = 2, name = "Auto", emoji = "\uD83D\uDE97"),
            CategoryEntity(id = 20, name = "Tanken", emoji = "⛽", parentId = 2)
        )
        val unter = SmartCategories.unterkategorien(alle, 2L)
        assertEquals(1, unter.size)
        assertTrue(SmartCategories.istUnterkategorie(unter.first()))
        assertEquals("Auto ▸ Tanken", SmartCategories.pfad(unter.first(), alle))
    }

    // ==================== Module 27/28: KI-Kontext & Befehle ================

    @Test
    fun aiBefehle_werdenErkanntUndBestaetigt() {
        val ausgabe = AiCommands.erkennen("Buche eine Ausgabe von 50 € für Sprit")
        assertTrue(ausgabe is AiCommands.Befehl.Ausgabe)
        assertEquals(5_000L, (ausgabe as AiCommands.Befehl.Ausgabe).cents)
        assertEquals("sprit", ausgabe.kategorie)
        assertTrue(AiCommands.bestaetigung(ausgabe, "€").contains("50"))

        val einnahme = AiCommands.erkennen("Neue Einnahme 1.200 € Gehalt")
        assertTrue(einnahme is AiCommands.Befehl.Einnahme)
        assertEquals(120_000L, (einnahme as AiCommands.Befehl.Einnahme).cents)

        val ziel = AiCommands.erkennen("Lege ein Sparziel von 5.000 € für Auto an")
        assertTrue(ziel is AiCommands.Befehl.Sparziel)
        assertEquals(500_000L, (ziel as AiCommands.Befehl.Sparziel).cents)

        val budget = AiCommands.erkennen("Erstelle ein Monatsbudget von 500 € für Lebensmittel")
        assertTrue(budget is AiCommands.Befehl.Budget)
        assertEquals(50_000L, (budget as AiCommands.Befehl.Budget).cents)

        assertTrue(AiCommands.erkennen("Zeige meine Ausgaben vom letzten Monat")
            is AiCommands.Befehl.ZeigeVormonat)
        assertNull(AiCommands.erkennen("Wie viel habe ich diesen Monat ausgegeben?"))
    }

    @Test
    fun aiKontext_merktSichDieKategorie() {
        val kontext = AiContext()
        kontext.merken("Wie viel habe ich für Auto ausgegeben?",
            com.finovo.app.ai.AiIntent.CATEGORY_AMOUNT, "Auto", null)
        assertEquals("Auto", kontext.gemerkteKategorie)
        assertEquals(1, kontext.fragenAnzahl)
        assertTrue(kontext.istFortsetzung("Und wie viel davon?"))

        val aufgeloest = kontext.aufloesen("Und wie viel davon für Auto?")
        assertEquals("auto", aufgeloest.kategorie)

        kontext.zuruecksetzen()
        assertEquals(0, kontext.fragenAnzahl)
        assertNull(kontext.gemerkteKategorie)
    }

    // ========================= Modul 16: Einblicke ==========================

    @Test
    fun einblicke_liefertFaktenOhneVorgaben() {
        val heute = LocalDate.of(2026, 6, 20)
        val ausgaben = listOf(
            ExpenseEntity(id = 1, amountCents = 90_000L, categoryId = 3, merchant = "Miete", date = tag(2026, 5, 1)),
            ExpenseEntity(id = 2, amountCents = 90_000L, categoryId = 3, merchant = "Miete", date = tag(2026, 6, 1)),
            ExpenseEntity(id = 3, amountCents = 10_000L, categoryId = 1, merchant = "Aldi", date = tag(2026, 5, 5)),
            ExpenseEntity(id = 4, amountCents = 80_000L, categoryId = 1, merchant = "Aldi", date = tag(2026, 6, 5))
        )
        val liste = Einblicke.berechne(
            heute = heute,
            ausgaben = ausgaben,
            einnahmen = emptyList(),
            kategorien = kategorien(),
            symbol = "€"
        )
        assertTrue(liste.isNotEmpty())
        assertTrue(liste.any { it.art == Einblicke.Art.TOP_KATEGORIE })
        assertTrue(liste.any { it.art == Einblicke.Art.ANSTIEG })
        // Keine formulierte Handlungsanweisung
        assertTrue(liste.none { it.text.contains("solltest", ignoreCase = true) })
        assertTrue(Einblicke.berechne(heute, emptyList(), emptyList(), kategorien(), "€").isEmpty())
    }

    // -----------------------------------------------------------------------

    private fun tag(jahr: Int, monat: Int, tag: Int): Long =
        LocalDate.of(jahr, monat, tag).toEpochDay()
}

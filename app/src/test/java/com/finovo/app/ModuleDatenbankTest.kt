package com.finovo.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.data.AssetEntity
import com.finovo.app.data.AssetKind
import com.finovo.app.data.AuditAction
import com.finovo.app.data.CategoryType
import com.finovo.app.data.DocumentEntity
import com.finovo.app.data.DocumentKind
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.util.Alarme
import com.finovo.app.util.Alarm
import com.finovo.app.util.CsvImport
import com.finovo.app.util.Dates
import com.finovo.app.util.Duplicates
import com.finovo.app.util.Jahresbericht
import com.finovo.app.util.NetWorth
import com.finovo.app.util.Prognose
import com.finovo.app.util.SmartCategories
import com.finovo.app.util.Suche
import com.finovo.app.util.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/**
 * Prüft die Datenbankwege der neuen Module gegen die echte Datenbank:
 * Änderungsverlauf (32), Dokumente (18), Vermögen (24), Einstellungen (15/21/34),
 * Import (30) - und zum Schluss, dass auch große Datenmengen schnell bleiben (37).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class ModuleDatenbankTest {

    @get:Rule
    val compose = createComposeRule()

    private fun app(): FinovoApp = ApplicationProvider.getApplicationContext()

    /** Lässt die UI in Ruhe laufen, bis [bedingung] erfüllt ist. */
    private fun warteBis(dauerMs: Long = 10_000, bedingung: () -> Boolean) {
        val ende = System.currentTimeMillis() + dauerMs
        while (System.currentTimeMillis() < ende && !bedingung()) {
            compose.waitForIdle()
            Thread.sleep(20)
        }
        compose.waitForIdle()
    }

    @Before
    fun leer() {
        runBlocking(Dispatchers.IO) { app().repository.deleteAllData() }
    }

    @After
    fun aufraeumen() {
        runBlocking(Dispatchers.IO) { app().repository.deleteAllData() }
    }

    // ------------------------------ Modul 12 -------------------------------

    @Test
    fun unterkategorienWerdenDerHauptkategorieZugeordnet() {
        val app = app()
        runBlocking(Dispatchers.IO) {
            val oberId = app.repository.addCategory("Auto", "\uD83D\uDE97", CategoryType.EXPENSE)
            val unterId = app.repository.addCategory(
                "Sprit", "\u26FD", CategoryType.EXPENSE, false, oberId
            )
            val alle = app.repository.categories.firstOrNull() ?: emptyList()
            val unter = alle.first { it.id == unterId }
            assertEquals(oberId, unter.parentId)
            assertTrue(SmartCategories.unterkategorien(alle, oberId).any { it.id == unterId })
            assertTrue(SmartCategories.unterkategorien(alle, unterId).isEmpty())

            // Der gleiche Name darf unter einer anderen Hauptkategorie noch einmal vorkommen
            val zweiterOberId = app.repository.addCategory("Reise", "\u2708", CategoryType.EXPENSE)
            val zweitUnterId = app.repository.addCategory(
                "Sprit", "\u26FD", CategoryType.EXPENSE, false, zweiterOberId
            )
            assertTrue(zweitUnterId > 0L)
            val neu = app.repository.categories.firstOrNull() ?: emptyList()
            assertEquals(2, neu.count { it.name == "Sprit" })

            // Gleicher Name unter derselben Hauptkategorie bleibt unmoeglich
            val doppelt = app.repository.addCategory(
                "Sprit", "\u26FD", CategoryType.EXPENSE, false, zweiterOberId
            )
            assertEquals(zweitUnterId, doppelt)

            // Umbenennen auf einen belegten Namen wird abgelehnt statt abzustuerzen
            val liste = app.repository.categories.firstOrNull() ?: emptyList()
            val autoSprit = liste.first { it.id == unterId }
            val reiseSprit = liste.first { it.id == zweitUnterId }
            assertFalse(
                app.repository.updateCategory(reiseSprit.copy(name = "Sprit", parentId = oberId))
            )
            assertTrue(
                app.repository.updateCategory(reiseSprit.copy(name = "Tanken", parentId = oberId))
            )
            val danach = app.repository.categories.firstOrNull() ?: emptyList()
            assertEquals(2, danach.count { it.parentId == oberId })
            assertEquals("Sprit", danach.first { it.id == autoSprit.id }.name)
        }
    }

    // ------------------------------ Modul 32 -------------------------------

    @Test
    fun aenderungsverlaufWirdBeimSpeichernGeschrieben() {
        val app = app()
        val vm = AppViewModel(app)
        val kategorie = runBlocking(Dispatchers.IO) {
            app.repository.addCategory("Verlaufstest", "€", CategoryType.EXPENSE)
        }

        compose.runOnIdle {
            vm.saveExpense(
                ExpenseEntity(
                    id = 0,
                    amountCents = 12_34L,
                    categoryId = kategorie,
                    date = LocalDate.of(2026, 6, 1).toEpochDay(),
                    note = "Verlauf pruefen"
                ),
                isNew = true
            )
        }
        warteBis(bedingung = {
            runBlocking(Dispatchers.IO) { app.repository.allAudit(200) }
                .any { it.entityType == "EXPENSE" && it.detail.contains("Verlauf") }
        })
        val eintraege = runBlocking(Dispatchers.IO) { app.repository.allAudit(200) }
        assertTrue(
            "Kein Verlaufseintrag geschrieben",
            eintraege.any { it.entityType == "EXPENSE" && it.detail.contains("Verlauf") }
        )
        assertTrue(
            "Die Aktion fehlt im Verlauf",
            eintraege.any { it.action == AuditAction.TRANSACTION_CREATED.name }
        )
    }

    // ------------------------------ Modul 18 -------------------------------

    @Test
    fun dokumentSpeichernFindenUndLoeschen() {
        val app = app()
        val id = runBlocking(Dispatchers.IO) {
            app.repository.saveDocument(
                DocumentEntity(
                    title = "Mietvertrag",
                    kind = DocumentKind.CONTRACT.name,
                    path = "/data/mietvertrag.pdf",
                    date = LocalDate.of(2026, 5, 4).toEpochDay(),
                    tags = "wohnen,vertrag",
                    note = "Kopie",
                    amountCents = 90_000L,
                    merchant = "Hausverwaltung",
                    mimeType = "application/pdf",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        assertTrue(id > 0L)

        val liste = runBlocking(Dispatchers.IO) { app.repository.allDocuments() }
        assertTrue(liste.any { it.id == id && it.title == "Mietvertrag" })

        runBlocking(Dispatchers.IO) {
            app.repository.deleteDocument(liste.first { it.id == id })
        }
        val danach = runBlocking(Dispatchers.IO) { app.repository.allDocuments() }
        assertFalse(danach.any { it.id == id })
    }

    // ------------------------------ Modul 24 -------------------------------

    @Test
    fun vermoegenswertSpeichernUndNettoBerechnen() {
        val app = app()
        runBlocking(Dispatchers.IO) {
            app.repository.saveAsset(
                AssetEntity(name = "Giro", kind = AssetKind.BANK.name,
                    amountCents = 200_000L, date = LocalDate.of(2026, 6, 1).toEpochDay())
            )
            app.repository.saveAsset(
                AssetEntity(name = "Kredit", kind = AssetKind.LOAN.name,
                    amountCents = 50_000L, date = LocalDate.of(2026, 6, 1).toEpochDay())
            )
        }
        val werte = runBlocking(Dispatchers.IO) { app.repository.allAssets() }
        assertEquals(2, werte.size)

        val netto = NetWorth.berechne(werte, emptyList(), emptyList(), LocalDate.of(2026, 6, 30))
        assertEquals(150_000L, netto.nettoCents)
    }

    // --------------------- Module 15 / 21 / 22 / 34 ------------------------

    @Test
    fun einstellungenBleibenGespeichert() {
        val app = app()
        val alarme = Alarme.standard().let { z ->
            Alarme.zuText(
                Alarme.Zustand(
                    aktiv = z.aktiv.toMutableMap().apply { this[Alarm.LARGE_TRANSACTION] = false },
                    schwellen = z.schwellen.toMutableMap().apply { this[Alarm.LOW_BALANCE] = 250_00L }
                )
            )
        }
        val widgets = Widgets.zuText(listOf(Widgets.Art.GOALS, Widgets.Art.BALANCE))

        runBlocking(Dispatchers.IO) {
            app.repository.updateSettings {
                it.copy(
                    alertSettings = alarme,
                    dashboardWidgets = widgets,
                    family = "1:1,0,0",
                    fxRates = "EUR=10000;USD=10900",
                    openingBalanceCents = 75_000L
                )
            }
        }
        val gespeichert = runBlocking(Dispatchers.IO) {
            app.repository.settings.firstOrNull()
        }
        assertTrue(gespeichert != null)

        val zustand = Alarme.ausText(gespeichert!!.alertSettings)
        assertFalse(zustand.istAn(Alarm.LARGE_TRANSACTION))
        assertEquals(250_00L, zustand.schwelle(Alarm.LOW_BALANCE))
        assertEquals(listOf(Widgets.Art.GOALS, Widgets.Art.BALANCE), Widgets.ausText(gespeichert.dashboardWidgets))
        assertEquals(75_000L, gespeichert.openingBalanceCents)
        assertTrue(gespeichert.fxRates.contains("USD=10900"))
    }

    // ------------------------------ Modul 30 -------------------------------

    @Test
    fun csvImportSchreibtEchteBuchungen() {
        val app = app()
        val kategorie = runBlocking(Dispatchers.IO) {
            app.repository.addCategory("Importkategorie", "€", CategoryType.EXPENSE)
        }
        val vm = AppViewModel(app)
        val tabelle = CsvImport.lesen(
            "Datum;Beschreibung;Betrag\n" +
                "01.06.2026;Aldi Einkauf;12,34\n" +
                "05.06.2026;Tankstelle;65,00\n"
        )
        // In dieser Datei sind Ausgaben positiv -> Vorzeichen umkehren (wie im Dialog)
        val zuordnung = CsvImport.vorschlagen(tabelle).copy(negation = true)
        val zeilen = CsvImport.anwenden(tabelle, zuordnung)

        var fertig = false
        var uebernommen = 0
        compose.runOnIdle {
            vm.importUebernehmen(zeilen, zuordnung, kategorie) { ok, _ ->
                uebernommen = ok
                fertig = true
            }
        }
        warteBis(bedingung = { fertig })
        assertTrue("Der Import hat nicht geantwortet", fertig)
        assertEquals(2, uebernommen)

        val ausgaben = runBlocking(Dispatchers.IO) { app.repository.expenses.firstOrNull() ?: emptyList() }
        assertTrue("Ausgaben wurden nicht angelegt", ausgaben.any { it.amountCents == 12_34L })
        assertTrue("Zweite Ausgabe fehlt", ausgaben.any { it.amountCents == 6_500L })
        val einnahmen = runBlocking(Dispatchers.IO) { app.repository.incomes.firstOrNull() ?: emptyList() }
        assertTrue(
            "Ohne Umkehrung dürfen keine Einnahmen entstehen",
            einnahmen.none { it.amountCents == 12_34L })
    }

    // ------------------------------ Modul 37 -------------------------------

    @Test
    fun grosseDatenmengeBleibtSchnell() {
        val app = app()
        val kategorie = runBlocking(Dispatchers.IO) {
            app.repository.addCategory("Mengentest", "€", CategoryType.EXPENSE)
        }
        val heute = LocalDate.of(2026, 6, 30)
        val ausgaben = ArrayList<ExpenseEntity>(1_500)
        val einnahmen = ArrayList<IncomeEntity>(300)
        runBlocking(Dispatchers.IO) {
            repeat(1_500) { i ->
                app.repository.addExpense(
                    ExpenseEntity(
                        id = 0,
                        amountCents = 1_00L + (i % 500) * 7,
                        categoryId = kategorie,
                        date = heute.minusDays((i % 365).toLong()).toEpochDay(),
                        note = "Ausgabe $i",
                        merchant = if (i % 3 == 0) "Aldi" else "Haendler $i"
                    )
                )
            }
            repeat(300) { i ->
                app.repository.addIncome(
                    IncomeEntity(
                        id = 0,
                        amountCents = 250_000L,
                        categoryId = kategorie,
                        date = heute.minusDays((i % 300).toLong()).toEpochDay(),
                        note = "Einnahme $i"
                    )
                )
            }
        }
        val gelesen = runBlocking(Dispatchers.IO) { app.repository.expenses.firstOrNull() ?: emptyList() }
        assertTrue("Testdaten wurden nicht geschrieben", gelesen.size >= 1_500)

        fun messen(name: String, block: () -> Unit) {
            val start = System.currentTimeMillis()
            block()
            val dauer = System.currentTimeMillis() - start
            println("$name: ${dauer} ms")
            assertTrue("$name war zu langsam: ${dauer} ms", dauer < 5_000L)
        }

        messen("Jahresbericht") {
            Jahresbericht.berechne(2026, gelesen, einnahmen, emptyList(), emptyList(), emptyList(), null, "€")
        }
        messen("Prognose") {
            Prognose.berechne(
                YearMonth.of(2026, 6), heute, gelesen, einnahmen,
                emptyList(), emptyList(), emptyList(), emptyList(), null
            )
        }
        messen("Suche") {
            Suche.suche("Aldi", Suche.Kontext(gelesen, einnahmen, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "€"))
        }
        messen("Duplikate") { Duplicates.findeAlle(gelesen) }
    }

    // -----------------------------------------------------------------------
}

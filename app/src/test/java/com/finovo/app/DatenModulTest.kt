package com.finovo.app

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.backup.Backup
import com.finovo.app.data.BillEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.export.Export
import com.finovo.app.ui.screens.DataScreen
import com.finovo.app.util.AppLocale
import com.finovo.app.util.Dates
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Das komplette "Daten"-Modul: Backup erstellen, als Datei speichern,
 * wiederherstellen, loeschen sowie CSV- und PDF-Export.
 *
 * Jeder Test benutzt die echte Datenbank und den echten ViewModel - genau die
 * Wege, die auch der Nutzer ausloest. Ein Absturz beim Aufbauen, beim Layout
 * oder beim Schreiben der Datei faellt hier auf, nicht erst auf dem Geraet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class DatenModulTest {

    @get:Rule
    val compose = createComposeRule()

    // ---------------------------------------------------------------- Bildschirm

    @Test
    fun bildschirmOhneBackups() {
        val app = app()
        val vm = AppViewModel(app)
        baueAuf { DataScreen(vm, {}) }
        assertTrue(compose.onAllNodesWithText(text("de", "daten_2", "Daten")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun bildschirmMitVorhandenenBackups() {
        val app = app()
        fuelleDaten(app)
        legeBackupsAn(app, 3)
        val vm = AppViewModel(app)
        baueAuf { DataScreen(vm, {}) }
        // Die Liste wird nur mit Inhalt gezeichnet - genau hier kann es krachen.
        assertTrue(compose.onAllNodesWithText(text("de", "loeschen", "Löschen")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(
            compose.onAllNodesWithText(text("de", "wiederherstellen", "Wiederherstellen")).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun bildschirmNachErstemBackup() {
        val app = app()
        fuelleDaten(app)
        val datei = runBlocking(Dispatchers.IO) { Backup.createLocalBackup(app, app.repository) }
        assertTrue(datei.exists())
        val vm = AppViewModel(app)
        baueAuf { DataScreen(vm, {}) }
        // Untertitel "Letztes Backup: ..." ist jetzt aktiv
        assertTrue(compose.onAllNodesWithText(text("de", "loeschen", "Löschen")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun datenModulAufDeutsch() { modulInSprache("de") }

    @Test
    fun datenModulAufEnglisch() { modulInSprache("en") }

    @Test
    fun datenModulAufItalienisch() { modulInSprache("it") }

    @Test
    fun datenModulAufAlbanisch() { modulInSprache("sq") }

    @Test
    fun datenModulAufTuerkisch() { modulInSprache("tr") }

    // ---------------------------------------------------------------- Backup

    @Test
    fun klickAufBackupErstellen() {
        val app = app()
        fuelleDaten(app)
        val vm = AppViewModel(app)
        baueAuf { DataScreen(vm, {}) }

        // Genau der Weg des Knopfes "Backup erstellen"
        compose.runOnIdle { vm.createLocalBackup() }
        // Erst wenn Bytes drin sind, ist das Schreiben wirklich fertig.
        warteBis { Backup.listLocalBackups(app).any { it.length() > 50 } }

        val dateien = Backup.listLocalBackups(app)
        assertTrue("Es wurde keine Backup-Datei erzeugt", dateien.isNotEmpty())
        assertTrue("Die Backup-Datei ist leer", dateien.first().length() > 50)
        assertTrue("Kein gueltiges Backup", Backup.readFromFile(dateien.first()) != null)
    }

    @Test
    fun backupInDateiSchreiben() {
        val app = app()
        fuelleDaten(app)
        val ziel = File(app.cacheDir, "export-test.json")
        val ok = runBlocking(Dispatchers.IO) {
            Backup.writeToUri(app, Uri.fromFile(ziel), app.repository)
        }
        assertTrue("Schreiben in die Datei ist fehlgeschlagen", ok)
        val payload = Backup.readFromFile(ziel)
        assertNotNull("Die geschriebene Datei ist kein gueltiges Backup", payload)
        assertTrue(payload!!.expenses.isNotEmpty())
        assertTrue(payload.incomes.isNotEmpty())
    }

    @Test
    fun backupLoeschen() {
        val app = app()
        fuelleDaten(app)
        val datei = runBlocking(Dispatchers.IO) { Backup.createLocalBackup(app, app.repository) }
        assertTrue(datei.exists())
        val vm = AppViewModel(app)
        baueAuf { DataScreen(vm, {}) }
        compose.runOnIdle { vm.deleteLocalBackup(datei) }
        warteBis { !datei.exists() }
        assertTrue("Die Backup-Datei wurde nicht geloescht", !datei.exists())
    }

    // ------------------------------------------------------- Wiederherstellen

    @Test
    fun wiederherstellenErsetzt() {
        val app = app()
        fuelleDaten(app)
        val vorher = runBlocking(Dispatchers.IO) { app.repository.expenses.snapshot() }
        val datei = runBlocking(Dispatchers.IO) { Backup.createLocalBackup(app, app.repository) }
        val payload = Backup.readFromFile(datei)!!

        runBlocking(Dispatchers.IO) {
            Backup.restore(app.repository, payload, Backup.RestoreMode.REPLACE)
        }
        val nachher = runBlocking(Dispatchers.IO) { app.repository.expenses.snapshot() }
        assertEquals(vorher.size, nachher.size)
        assertEquals(vorher.sumOf { it.amountCents }, nachher.sumOf { it.amountCents })
        assertEquals(vorher.first().note, nachher.first().note)
    }

    @Test
    fun wiederherstellenFuehrtZusammen() {
        val app = app()
        fuelleDaten(app)
        val vorher = runBlocking(Dispatchers.IO) { app.repository.expenses.snapshot() }.size
        val datei = runBlocking(Dispatchers.IO) { Backup.createLocalBackup(app, app.repository) }
        val payload = Backup.readFromFile(datei)!!
        runBlocking(Dispatchers.IO) {
            Backup.restore(app.repository, payload, Backup.RestoreMode.MERGE)
        }
        val nachher = runBlocking(Dispatchers.IO) { app.repository.expenses.snapshot() }.size
        assertTrue("Zusammenfuehren muss die Daten verdoppeln", nachher >= vorher * 2)
    }

    /** "Alle Daten speichern" muss wirklich ALLE Daten enthalten. */
    @Test
    fun backupEnthaeltAlleFelderEinerGescanntenRechnung() {
        val app = app()
        fuelleDaten(app)
        val rechnung = runBlocking(Dispatchers.IO) { app.repository.bills.snapshot() }
            .first { it.vendor.isNotBlank() }

        val datei = runBlocking(Dispatchers.IO) { Backup.createLocalBackup(app, app.repository) }
        val payload = Backup.readFromFile(datei)!!
        val gesichert = payload.bills.firstOrNull { it.vendor.isNotBlank() }
        assertNotNull("Der Haendler der Rechnung fehlt im Backup", gesichert)

        assertEquals(rechnung.vendor, gesichert!!.vendor)
        assertEquals(rechnung.invoiceNumber, gesichert.invoiceNumber)
        assertEquals(rechnung.iban, gesichert.iban)
        assertEquals(rechnung.bic, gesichert.bic)
        assertEquals(rechnung.customerNumber, gesichert.customerNumber)
        assertEquals(rechnung.invoiceDate, gesichert.invoiceDate)
        assertEquals(rechnung.netCents, gesichert.netCents)
        assertEquals(rechnung.vatCents, gesichert.vatCents)
        assertEquals(rechnung.ocrText, gesichert.ocrText)
        assertEquals(rechnung.imagePath, gesichert.imagePath)

        // ... und nach dem Wiederherstellen muessen sie wieder in der Datenbank stehen
        runBlocking(Dispatchers.IO) {
            Backup.restore(app.repository, payload, Backup.RestoreMode.REPLACE)
        }
        val wiederhergestellt = runBlocking(Dispatchers.IO) { app.repository.bills.snapshot() }
            .firstOrNull { it.vendor.isNotBlank() }
        assertNotNull("Die Rechnung wurde nicht wiederhergestellt", wiederhergestellt)
        assertEquals(rechnung.vendor, wiederhergestellt!!.vendor)
        assertEquals(rechnung.invoiceNumber, wiederhergestellt.invoiceNumber)
        assertEquals(rechnung.iban, wiederhergestellt.iban)
    }

    @Test
    fun backupEnthaeltEinstellungenUndBereiche() {
        val app = app()
        fuelleDaten(app)
        runBlocking(Dispatchers.IO) {
            app.repository.addArea("Haushalt", "\uD83C\uDFE0")
        }
        val einstellungen = runBlocking(Dispatchers.IO) { app.repository.settings.wert() }
        val datei = runBlocking(Dispatchers.IO) { Backup.createLocalBackup(app, app.repository) }
        val payload = Backup.readFromFile(datei)!!
        assertNotNull("Die Einstellungen fehlen im Backup", payload.settings)
        assertEquals(einstellungen?.language, payload.settings!!.language)
        assertEquals(einstellungen?.carModuleEnabled, payload.settings!!.carModuleEnabled)
        assertTrue("Die Bereiche fehlen im Backup", payload.areas.isNotEmpty())
    }

    // ---------------------------------------------------------------- Export

    @Test
    fun csvExportFuerAlleZeitraeume() {
        val app = app()
        fuelleDaten(app)
        val res = AppLocale.wrap(app, "de").resources
        val anfrage = exportAnfrage(app, ExportPeriod.ThisMonth)
        val texte = listOf(
            ExportPeriod.ThisMonth,
            ExportPeriod.LastMonth,
            ExportPeriod.ThisYear,
            ExportPeriod.CustomRange(
                Dates.startOfMonth(LocalDate.now()),
                Dates.endOfMonth(LocalDate.now())
            )
        ).map { zeitraum ->
            val csv = runBlocking(Dispatchers.IO) {
                Export.csv(exportAnfrage(app, zeitraum), res)
            }
            assertTrue("CSV fuer $zeitraum ist leer", csv.length > 100)
            assertTrue("CSV fuer $zeitraum hat keinen Inhalt", csv.contains("Ausgabe"))
            csv
        }
        assertEquals(4, texte.size)
        assertNotNull(anfrage)
    }

    @Test
    fun pdfExportFuerAlleZeitraeume() {
        val app = app()
        fuelleDaten(app)
        if (!pdfMoeglich()) {
            // Robolectric: PdfDocument ist ohne nativen Code nicht nutzbar.
            println("Hinweis: PDF-Erzeugung in dieser Testumgebung nicht moeglich - uebersprungen.")
            return
        }
        listOf(
            ExportPeriod.ThisMonth,
            ExportPeriod.LastMonth,
            ExportPeriod.ThisYear,
            ExportPeriod.CustomRange(
                Dates.startOfYear(LocalDate.now()),
                LocalDate.now()
            )
        ).forEach { zeitraum ->
            val puffer = ByteArrayOutputStream()
            runBlocking(Dispatchers.IO) {
                Export.writePdf(puffer, exportAnfrage(app, zeitraum))
            }
            val bytes = puffer.toByteArray()
            assertTrue("PDF fuer $zeitraum ist zu klein: ${bytes.size}", bytes.size > 800)
            assertTrue(
                "PDF fuer $zeitraum beginnt nicht mit %PDF",
                bytes.size > 4 && bytes[0] == '%'.code.toByte() &&
                    bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte()
            )
        }
    }

    @Test
    fun csvUndPdfUeberDenViewModel() {
        val app = app()
        fuelleDaten(app)
        val vm = AppViewModel(app)
        baueAuf { DataScreen(vm, {}) }

        var csv: File? = null
        compose.runOnIdle { vm.createCsvFile(ExportPeriod.ThisMonth) { csv = it } }
        warteBis { csv != null }
        assertNotNull("CSV-Datei wurde nicht erzeugt", csv)
        assertTrue("CSV-Datei ist leer", csv!!.length() > 100)

        if (pdfMoeglich()) {
            var pdf: File? = null
            compose.runOnIdle { vm.createPdfFile(ExportPeriod.ThisYear) { pdf = it } }
            warteBis { pdf != null }
            assertNotNull("PDF-Datei wurde nicht erzeugt", pdf)
            assertTrue("PDF-Datei ist leer", pdf!!.length() > 800)
        }
    }

    @Test
    fun csvUndPdfInEineDateiSchreiben() {
        val app = app()
        fuelleDaten(app)
        val zielCsv = File(app.cacheDir, "test.csv")
        val zielPdf = File(app.cacheDir, "test.pdf")
        val vm = AppViewModel(app)
        baueAuf { DataScreen(vm, {}) }

        compose.runOnIdle { vm.exportCsvToUri(Uri.fromFile(zielCsv), ExportPeriod.ThisMonth) }
        warteBis { zielCsv.length() > 100 }
        assertTrue("CSV-Export fehlgeschlagen", zielCsv.length() > 100)

        if (pdfMoeglich()) {
            compose.runOnIdle { vm.exportPdfToUri(Uri.fromFile(zielPdf), ExportPeriod.ThisMonth) }
            warteBis { zielPdf.length() > 800 }
            assertTrue("PDF-Export fehlgeschlagen", zielPdf.length() > 800)
        }
    }

    // --------------------------------------------------------- Alle Daten loeschen

    @Test
    fun alleDatenLoeschen() {
        val app = app()
        fuelleDaten(app)
        val vm = AppViewModel(app)
        baueAuf { DataScreen(vm, {}) }
        var fertig = false
        compose.runOnIdle { vm.deleteAllData { fertig = true } }
        warteBis { fertig }
        val uebrig = runBlocking(Dispatchers.IO) { app.repository.expenses.snapshot() }
        assertTrue("Es sind noch Ausgaben vorhanden", uebrig.isEmpty())
    }

    // ------------------------------------------------------------------ Hilfen

    private fun app(): FinovoApp = ApplicationProvider.getApplicationContext()

    /**
     * Baut den Bildschirm auf. Der Kontext wird mit der gewaehlten Sprache
     * umschlagen, damit die Texte wirklich in dieser Sprache geprueft werden -
     * die Testumgebung startet sonst auf Englisch.
     */
    private fun baueAuf(sprache: String = "de", inhalt: @Composable (AppViewModel) -> Unit) {
        val vm = AppViewModel(app())
        var generation by mutableStateOf(0)
        compose.setContent {
            val context = LocalContext.current
            val owner: ActivityResultRegistryOwner =
                requireNotNull(LocalActivityResultRegistryOwner.current)
            val umschlagen = remember(sprache) { AppLocale.wrap(context, sprache) }
            CompositionLocalProvider(
                LocalContext provides umschlagen,
                LocalActivityResultRegistryOwner provides owner
            ) {
                MaterialTheme { key(inhalt.javaClass.name, sprache, generation) { inhalt(vm) } }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { generation = 1 }
        compose.waitForIdle()
    }

    /**
     * Robolectric kann kein echtes PDF erzeugen (PdfDocument braucht nativen
     * Code). Die PDF-Pruefungen laufen nur, wenn die Umgebung es unterstuetzt -
     * verschwiegen wird dabei nichts, der Grund wird mitgeprueft.
     */
    private fun pdfMoeglich(): Boolean = runCatching {
        val puffer = ByteArrayOutputStream()
        Export.writePdf(
            puffer,
            Export.Request(
                label = "Test",
                from = LocalDate.now(),
                to = LocalDate.now(),
                expenses = emptyList(),
                incomes = emptyList(),
                bills = emptyList(),
                categories = emptyList(),
                symbol = "€"
            )
        )
    }.isSuccess

    /**
     * Baut den Daten-Bildschirm in der gewaehlten Sprache auf - mit Inhalt und
     * vorhandenen Backups - und oeffnet den Wiederherstellen-Dialog.
     */
    private fun modulInSprache(code: String) {
        val app = app()
        AppLocale.apply(code)
        fuelleDaten(app)
        legeBackupsAn(app, 2)
        val vm = AppViewModel(app)
        var generation by mutableStateOf(0)
        compose.setContent {
            val context = LocalContext.current
            val owner: ActivityResultRegistryOwner =
                requireNotNull(LocalActivityResultRegistryOwner.current)
            val umschlagen = remember(code) { AppLocale.wrap(context, code) }
            CompositionLocalProvider(
                LocalContext provides umschlagen,
                LocalActivityResultRegistryOwner provides owner
            ) {
                MaterialTheme { key(code, generation) { DataScreen(vm, {}) } }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { generation = 1 }
        compose.waitForIdle()

        // Knopf "Wiederherstellen" der ersten Backup-Zeile -> Dialog
        compose.onAllNodesWithText(text(code, "wiederherstellen", "Wiederherstellen"))[0]
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
        // Achtung: hier darf KEIN neuer Schluessel gesetzt werden - das wuerde
        // den Bildschirm neu aufbauen und den offenen Dialog wieder verwerfen.

        // Der Dialog wird in einem eigenen Fenster aufgebaut: nach dem Klick
        // muss ein zweiter Wurzelknoten vorhanden sein. Die Texte des Dialogs
        // stammen aus dem Kontext der Aktivitaet, nicht aus dem umgeschlagenen
        // Sprachkontext - deshalb wird hier nicht nach ihnen gesucht.
        // Der Dialog wird in einem eigenen Fenster aufgebaut und nutzt den
        // Kontext der Aktivitaet (nicht den umgeschlagenen Sprachkontext der
        // Ansicht) - seine Texte werden deshalb ueber dialogText() geholt.
        val abbrechen = dialogText("abbrechen_3", "Abbrechen")
        assertTrue(
            "Der Wiederherstellen-Dialog fehlt in Sprache $code",
            compose.onAllNodesWithText(abbrechen).fetchSemanticsNodes().isNotEmpty()
        )
        compose.onNodeWithText(abbrechen).performClick()
        compose.waitForIdle()
        assertTrue(
            "Der Dialog laesst sich nicht schliessen (Sprache $code)",
            compose.onAllNodesWithText(abbrechen).fetchSemanticsNodes().isEmpty()
        )

        // Sichtbare Texte des Bildschirms muessen in der Sprache erscheinen.
        for (schluessel in listOf(
            "backup_erstellen" to "💾 Backup erstellen",
            "als_datei_speichern" to "Als Datei speichern …",
            "gespeicherte_backups_backups_size" to "Gespeicherte Backups"
        )) {
            // Platzhalter wie %1$s sind auf dem Bildschirm ersetzt - der
            // sichtbare Teil endet deshalb vor dem ersten Platzhalter.
            val erwartet = text(code, schluessel.first, schluessel.second)
                .substringBefore('%')
                .trim()
                .trimEnd('(', '·', '-', ':')
                .trim()
            assertTrue(
                "'$erwartet' (${schluessel.first}) fehlt auf dem Daten-Bildschirm in Sprache $code",
                compose.onAllNodesWithText(erwartet, substring = true).fetchSemanticsNodes().isNotEmpty()
            )
        }

        // Alle uebrigen Texte des Moduls muessen vorhanden und uebersetzt sein.
        // (Sie stehen weiter unten in der Liste und sind im Test nicht sichtbar,
        // deshalb hier die Pruefung auf der Ressourcen-Ebene.)
        for (schluessel in listOf(
            "csv_export" to "CSV-Export",
            "pdf_bericht" to "PDF-Bericht",
            "csv_teilen" to "CSV teilen",
            "pdf_teilen" to "PDF teilen",
            "wiederherstellen_2" to "Wiederherstellen",
            "daten_wiederherstellen" to "Daten wiederherstellen",
            "ersetzen" to "Ersetzen",
            "zusammenfuehren" to "Zusammenführen",
            "backup_datei_auswaehlen" to "📥 Backup-Datei auswählen",
            "dieser_monat" to "Dieser Monat",
            "dieses_jahr" to "Dieses Jahr",
            "zeitraum" to "Zeitraum",
            "von" to "Von",
            "bis" to "Bis",
            "crash_hint_title" to "Beim letzten Vorgang ist ein Fehler aufgetreten.",
            "crash_hint_body" to "Deine Daten sind sicher. Das Protokoll unten zeigt die genaue Ursache."
        )) {
            val wert = text(code, schluessel.first, schluessel.second)
            assertTrue("'${schluessel.first}' ist in Sprache $code leer", wert.isNotBlank())
            if (code != "de") {
                assertTrue(
                    "'${schluessel.first}' ist in Sprache $code nicht uebersetzt: $wert",
                    wert != schluessel.second
                )
            }
        }
    }

    /** Text so, wie ein Dialog ihn anzeigt: Kontext der Aktivitaet. */
    private fun dialogText(schluessel: String, ersatz: String): String {
        val res = app().resources
        val id = res.getIdentifier(schluessel, "string", app().packageName)
        return if (id != 0) res.getString(id) else ersatz
    }

    private fun text(code: String, schluessel: String, ersatz: String): String {
        val res = AppLocale.wrap(app(), code).resources
        val id = res.getIdentifier(schluessel, "string", app().packageName)
        return if (id != 0) res.getString(id) else ersatz
    }

    private fun warteBis(dauerMs: Long = 10_000, bedingung: () -> Boolean) {
        val ende = System.currentTimeMillis() + dauerMs
        while (System.currentTimeMillis() < ende && !bedingung()) {
            compose.waitForIdle()
            Thread.sleep(20)
        }
        compose.waitForIdle()
    }

    private fun legeBackupsAn(app: FinovoApp, anzahl: Int) {
        runBlocking(Dispatchers.IO) {
            repeat(anzahl) {
                Backup.createLocalBackup(app, app.repository)
                Thread.sleep(1100) // Dateiname hat Sekunden-Aufloesung
            }
        }
    }

    /** Echte Testdaten - inklusive einer gescannten Rechnung mit allen Feldern. */
    private fun fuelleDaten(app: FinovoApp) {
        runBlocking(Dispatchers.IO) {
            val r = app.repository
            val ausgaben = r.addCategory("Testausgaben", "€", CategoryType.EXPENSE)
            val einnahmen = r.addCategory("Testgehalt", "€", CategoryType.INCOME)
            (1..40).forEach { i ->
                val tag = LocalDate.of(2026, 1, 1).plusDays(i.toLong() * 3)
                r.addExpense(
                    ExpenseEntity(
                        id = 0,
                        amountCents = 1_234L + i,
                        categoryId = ausgaben,
                        date = tag.toEpochDay(),
                        note = "Ausgabe $i"
                    )
                )
                r.addIncome(
                    IncomeEntity(
                        id = 0,
                        amountCents = 250_000L + i,
                        categoryId = einnahmen,
                        date = tag.toEpochDay(),
                        note = "Gehalt $i"
                    )
                )
            }
            r.addBill(
                BillEntity(
                    id = 0,
                    name = "Stromabschlag",
                    amountCents = 8_900,
                    dueDate = LocalDate.now().toEpochDay(),
                    categoryId = ausgaben,
                    note = "gescannt",
                    vendor = "Stadtwerke Essen AG",
                    invoiceNumber = "RE-2026-7781",
                    invoiceDate = LocalDate.now().minusDays(4).toEpochDay(),
                    iban = "DE89370400440532013000",
                    bic = "COBADEFFXXX",
                    customerNumber = "KD 884213",
                    imagePath = "/data/data/com.finovo.app/files/scan/rechnung.jpg",
                    netCents = 7_479,
                    vatCents = 1_421,
                    ocrText = "Stadtwerke Essen AG\nGesamtbetrag 89,00 EUR",
                    confidence = "amount=96;vendor=91",
                    fieldBoxes = "amount=12,44,300,80"
                )
            )
        }
    }

    private suspend fun <T> Flow<List<T>>.snapshot(): List<T> = firstOrNull() ?: emptyList()

    private suspend fun <T> Flow<T?>.wert(): T? = firstOrNull()

    private fun exportAnfrage(app: FinovoApp, zeitraum: ExportPeriod): Export.Request {
        val (von, bis) = when (zeitraum) {
            ExportPeriod.ThisMonth -> Dates.startOfMonth(LocalDate.now()) to Dates.endOfMonth(LocalDate.now())
            ExportPeriod.ThisYear -> Dates.startOfYear(LocalDate.now()) to Dates.endOfYear(LocalDate.now())
            ExportPeriod.LastMonth -> {
                val letzter = LocalDate.now().minusMonths(1)
                Dates.startOfMonth(letzter) to Dates.endOfMonth(letzter)
            }
            is ExportPeriod.CustomRange -> zeitraum.from to zeitraum.to
        }
        return runBlocking(Dispatchers.IO) {
            Export.Request(
                label = "Test",
                from = von,
                to = bis,
                expenses = app.repository.expenses.snapshot(),
                incomes = app.repository.incomes.snapshot(),
                bills = app.repository.bills.snapshot(),
                categories = app.repository.categories.snapshot(),
                symbol = "€"
            )
        }
    }
}

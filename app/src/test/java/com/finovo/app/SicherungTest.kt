package com.finovo.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.backup.Backup
import com.finovo.app.data.AssetEntity
import com.finovo.app.data.AssetKind
import com.finovo.app.data.CategoryType
import com.finovo.app.data.DocumentEntity
import com.finovo.app.data.DocumentKind
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.util.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Datensicherheit: eine Sicherung muss ALLES enthalten, was der Nutzer
 * angelegt hat - auch die Dokumente (Modul 18) und die Vermoegenswerte
 * (Modul 24). Und aeltere Sicherungen muessen weiterhin lesbar bleiben.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class SicherungTest {

    private fun app(): FinovoApp = ApplicationProvider.getApplicationContext()

    @Before
    fun leer() {
        runBlocking(Dispatchers.IO) { app().repository.deleteAllData() }
    }

    @After
    fun aufraeumen() {
        runBlocking(Dispatchers.IO) { app().repository.deleteAllData() }
    }

    @Test
    fun dokumenteUndWerteUeberlebenDieSicherung() {
        val app = app()
        val heute = Dates.today().toEpochDay()

        runBlocking(Dispatchers.IO) {
            val repo = app.repository
            val katId = repo.addCategory("SicherungKat", "\uD83D\uDCC1", CategoryType.EXPENSE)
            repo.addExpense(
                ExpenseEntity(
                    id = 0,
                    amountCents = 123_45L,
                    categoryId = katId,
                    date = heute,
                    note = "Sicherung Test"
                )
            )
            repo.saveDocument(
                DocumentEntity(
                    title = "Mietvertrag",
                    kind = DocumentKind.CONTRACT.name,
                    path = File(app.filesDir, "dokumente/mietvertrag.pdf").absolutePath,
                    date = heute,
                    note = "Wichtiges Dokument"
                )
            )
            repo.saveAsset(
                AssetEntity(
                    name = "Tagesgeld",
                    kind = AssetKind.BANK.name,
                    amountCents = 5000_00L,
                    date = heute
                )
            )
            repo.updateSettings { it.copy(currencySymbol = "$") }
        }

        val datei = File(app.cacheDir, "sicherung-test.json")
        if (datei.exists()) datei.delete()
        val geschrieben = runBlocking {
            Backup.writeToUri(app, Uri.fromFile(datei), app.repository)
        }
        assertTrue("Sicherung konnte nicht geschrieben werden", geschrieben)
        assertTrue("Sicherungsdatei ist leer", datei.length() > 200)

        val gelesen = Backup.readFromFile(datei)
        assertNotNull("Sicherung konnte nicht gelesen werden", gelesen)
        val inhalt = gelesen!!
        assertTrue(inhalt.expenses.any { it.note == "Sicherung Test" })
        assertTrue(
            "Dokumente fehlen in der Sicherung",
            inhalt.documents.any { it.title == "Mietvertrag" }
        )
        assertTrue(
            "Vermoegenswerte fehlen in der Sicherung",
            inhalt.assets.any { it.name == "Tagesgeld" && it.amountCents == 5000_00L }
        )
        assertEquals("$", inhalt.settings?.currencySymbol)

        // Alles loeschen und aus der Sicherung zurueckholen
        runBlocking(Dispatchers.IO) { app.repository.wipeAll() }
        runBlocking(Dispatchers.IO) {
            assertTrue(app.repository.expenses.first().isEmpty())
            assertTrue(app.repository.allAssets().isEmpty())
            assertTrue(app.repository.allDocuments().isEmpty())
        }

        val ergebnis = runBlocking {
            Backup.restore(app.repository, inhalt, Backup.RestoreMode.REPLACE)
        }
        assertTrue("Nichts wiederhergestellt", ergebnis.inserted > 0)

        runBlocking(Dispatchers.IO) {
            val repo = app.repository
            assertTrue(
                "Ausgabe fehlt nach der Wiederherstellung",
                repo.expenses.first().any { it.note == "Sicherung Test" }
            )
            assertTrue(
                "Dokument fehlt nach der Wiederherstellung",
                repo.allDocuments().any { it.title == "Mietvertrag" }
            )
            assertTrue(
                "Vermoegenswert fehlt nach der Wiederherstellung",
                repo.allAssets().any { it.name == "Tagesgeld" && it.amountCents == 5000_00L }
            )
            assertEquals(
                "Einstellungen wurden nicht wiederhergestellt",
                "$",
                repo.currentSettings().currencySymbol
            )
        }
    }

    @Test
    fun alteSicherungOhneDokumenteBleibtLesbar() {
        val alt = """
            {"app":"Finovo","version":1,"data":{
              "categories":[],"expenses":[],"incomes":[],"bills":[],"payments":[],
              "rules":[],"areas":[],"goals":[],"subscriptions":[],
              "categoryBudgets":[],"categoryRules":[]}}
        """.trimIndent()
        val payload = Backup.parse(alt)
        assertNotNull("Alte Sicherung wurde nicht erkannt", payload)
        assertTrue(payload!!.documents.isEmpty())
        assertTrue(payload.assets.isEmpty())
    }
}

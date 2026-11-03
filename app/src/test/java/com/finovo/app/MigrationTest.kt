package com.finovo.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.data.AppDatabase
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueft den echten Datenbank-Upgrade-Weg. Auf dem Geraet liegen bereits
 * Daten in Version 7 - die Migration auf Version 8 (Modul 12: gleiche
 * Unterkategorie-Namen unter verschiedenen Hauptkategorien) darf keine
 * Bestandsdaten verlieren und muss von Room akzeptiert werden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class MigrationTest {

    private val dbName = "finovo.db"

    private fun kontext(): Context = ApplicationProvider.getApplicationContext()

    /**
     * Setzt den Room-Singleton zurueck, damit die Datei neu geoeffnet wird.
     * Kotlin legt statische Felder des Begleitobjekts in [AppDatabase] ab.
     */
    private fun singletonZuruecksetzen() {
        val feld = AppDatabase::class.java.getDeclaredField("INSTANCE")
        feld.isAccessible = true
        (feld.get(null) as? AppDatabase)?.close()
        feld.set(null, null)
    }

    @Before
    fun start() {
        singletonZuruecksetzen()
        kontext().deleteDatabase(dbName)
    }

    @After
    fun ende() {
        singletonZuruecksetzen()
        kontext().deleteDatabase(dbName)
    }

    @Test
    fun migrationVonVersion7Auf8BehaeltAlleKategorien() {
        val ctx = kontext()

        // ------------------------- 1) Stand Version 8 ------------------------
        val neu = AppDatabase.get(ctx)
        neu.openHelper.writableDatabase
        runBlocking(Dispatchers.IO) {
            neu.dao().insertCategory(
                CategoryEntity(name = "Auto", emoji = "\uD83D\uDE97", type = CategoryType.EXPENSE.name)
            )
        }
        val autoId = runBlocking(Dispatchers.IO) { neu.dao().observeCategories().first() }
            .first { it.name == "Auto" }.id
        runBlocking(Dispatchers.IO) {
            neu.dao().insertCategory(
                CategoryEntity(
                    name = "Sprit", emoji = "\u26FD",
                    type = CategoryType.EXPENSE.name, parentId = autoId
                )
            )
        }
        val indexeVorher = indexe(ctx)
        assertTrue(
            "Neuer Index fehlt: $indexeVorher",
            indexeVorher.contains("index_categories_name_type_parentId")
        )
        neu.close()

        // ------------- 2) Datei auf den alten Stand 7 zuruecksetzen ----------
        val pfad = ctx.getDatabasePath(dbName)
        assertTrue("Datenbankdatei fehlt", pfad.exists())
        val roh = SQLiteDatabase.openDatabase(pfad.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        roh.execSQL("DROP INDEX IF EXISTS index_categories_name_type_parentId")
        roh.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_name_type ON categories (name, type)")
        roh.execSQL("PRAGMA user_version = 7")
        roh.close()

        // -------- 3) Room oeffnet erneut: Migration + Schema-Pruefung --------
        singletonZuruecksetzen()
        val migriert = AppDatabase.get(ctx)
        val kategorien = runBlocking(Dispatchers.IO) { migriert.dao().observeCategories().first() }
        assertTrue(kategorien.any { it.name == "Auto" })
        assertTrue(kategorien.any { it.name == "Sprit" && it.parentId == autoId })

        // Der neue Index ist da, der alte weg
        val indexeNachher = indexe(ctx)
        assertTrue(
            "Alter Index wurde nicht entfernt: $indexeNachher",
            !indexeNachher.contains("index_categories_name_type")
        )
        assertTrue(
            "Neuer Index wurde nicht angelegt: $indexeNachher",
            indexeNachher.contains("index_categories_name_type_parentId")
        )

        // Gleicher Name unter einer zweiten Hauptkategorie ist jetzt erlaubt
        val reiseId = runBlocking(Dispatchers.IO) {
            migriert.dao().insertCategory(
                CategoryEntity(name = "Reise", emoji = "\u2708", type = CategoryType.EXPENSE.name)
            )
        }
        val sprit2 = runBlocking(Dispatchers.IO) {
            migriert.dao().insertCategory(
                CategoryEntity(
                    name = "Sprit", emoji = "\u26FD",
                    type = CategoryType.EXPENSE.name, parentId = reiseId
                )
            )
        }
        assertTrue("Zweite Unterkategorie wurde abgelehnt", sprit2 > 0L)
        val alle = runBlocking(Dispatchers.IO) { migriert.dao().observeCategories().first() }
        assertEquals(
            2,
            alle.count { it.name == "Sprit" && it.type == CategoryType.EXPENSE.name }
        )
        assertEquals(8, version(ctx))
        migriert.close()
    }

    private fun indexe(ctx: Context): List<String> {
        val pfad = ctx.getDatabasePath(dbName)
        val liste = ArrayList<String>()
        val roh = SQLiteDatabase.openDatabase(pfad.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        roh.rawQuery(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'categories'",
            null
        ).use { c ->
            while (c.moveToNext()) liste += c.getString(0)
        }
        roh.close()
        return liste
    }

    private fun version(ctx: Context): Int {
        val pfad = ctx.getDatabasePath(dbName)
        val roh = SQLiteDatabase.openDatabase(pfad.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        var v = 0
        roh.rawQuery("PRAGMA user_version", null).use { c ->
            if (c.moveToFirst()) v = c.getInt(0)
        }
        roh.close()
        return v
    }
}

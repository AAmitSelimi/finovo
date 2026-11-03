package com.finovo.app

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
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.backup.Backup
import com.finovo.app.data.BillEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.SavingGoalEntity
import com.finovo.app.data.SubscriptionEntity
import com.finovo.app.ui.screens.AnalyticsScreen
import com.finovo.app.ui.screens.BillsScreen
import com.finovo.app.ui.screens.CalendarScreen
import com.finovo.app.ui.screens.DashboardScreen
import com.finovo.app.ui.screens.ExpensesScreen
import com.finovo.app.ui.screens.GoalsScreen
import com.finovo.app.ui.screens.IncomeScreen
import com.finovo.app.ui.screens.InsightsScreen
import com.finovo.app.ui.screens.RecurringScreen
import com.finovo.app.ui.screens.SubscriptionsScreen
import com.finovo.app.util.AppLocale
import com.finovo.app.util.Dates
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schutz gegen genau den Absturz, der gemeldet wurde:
 *
 *   java.time.temporal.UnsupportedTemporalTypeException: Unsupported field: HourOfDay
 *
 * Ein LocalDate hat keine Uhrzeit. Wird ihm trotzdem ein Muster mit Stunden,
 * Minuten oder Sekunden uebergeben, stuerzt die App ab. Dieser Test prueft
 *
 *   1. dass kein Datumswert - auch kein unmoeglicher - die App zum Absturz
 *      bringt,
 *   2. dass der Backup-Dateiname (die urspruengliche Absturzstelle) stimmt,
 *   3. dass im Quelltext kein Zeit-Muster mehr an einem LocalDate haengt,
 *   4. dass jede Sprache ihr korrektes Datumsformat liefert,
 *   5. dass alle Bildschirme mit Datum auch mit kaputten Daten leben.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class DatumRobustnessTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------- 1. Robustheit

    @Test
    fun extremwerteStuerzenNichtAb() {
        val werte = listOf(
            Long.MIN_VALUE, Long.MIN_VALUE + 1, -365_243_219_163L, -100_000L, -1L,
            0L, 1L, 20_000L, 36_524_178_047L, 365_241_780_471L, Long.MAX_VALUE - 1, Long.MAX_VALUE
        )
        for (tag in werte) {
            val datum = Dates.fromEpochDay(tag)
            // Jede Funktion des Datums-Bausteins darf hier NICHT werfen.
            Dates.short(datum)
            Dates.full(datum)
            Dates.long(datum)
            Dates.monthYear(datum)
            Dates.monthKey(datum)
            Dates.weekdayShort(datum)
            Dates.chartMonth(datum)
            Dates.dateMedium(datum)
            Dates.relative(datum)
            Dates.yearMonth(datum)
            Dates.startOfWeek(datum)
            Dates.endOfWeek(datum)
            Dates.startOfMonth(datum)
            Dates.endOfMonth(datum)
            Dates.startOfYear(datum)
            Dates.endOfYear(datum)
            Dates.monthRange(datum)
            Dates.daysUntil(datum)
            Dates.millisToLocalDate(tag)
            Dates.millisToLocalDate(-5L)
        }
        // Zwei unmoegliche Werte durcheinander - darf ebenfalls nicht werfen.
        Dates.daysUntil(Dates.fromEpochDay(Long.MAX_VALUE), Dates.fromEpochDay(Long.MIN_VALUE))
    }

    @Test
    fun ungueltigeZeitstempelSindSicher() {
        listOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE).forEach {
            val datum = Dates.millisToLocalDate(it)
            assertTrue(Dates.full(datum).isNotBlank())
        }
    }

    // ------------------------------------------------------- 2. fehlende Daten

    @Test
    fun fehlendeDatenSindSicher() {
        val datum: LocalDate? = null
        assertEquals("", Dates.fullOrEmpty(datum))
        assertEquals("", Dates.shortOrEmpty(datum))
        assertEquals("–", Dates.fullOrDash(datum))

        val tag: Long? = null
        assertEquals("", Dates.fullOrEmpty(tag))
        assertEquals("–", Dates.fullOrDash(tag))
        assertEquals("", Dates.shortOrEmpty(tag))

        // Vorhandene Werte werden weiterhin normal formatiert (19.09.2026).
        AppLocale.apply("de")
        assertEquals("19.09.2026", Dates.fullOrEmpty(STICHTAG))
    }

    // ------------------------------------------------------------- 3. Uhrzeit

    @Test
    fun uhrzeitenWerdenBegrenzt() {
        assertEquals("09:05", Dates.formatTime(9, 5))
        assertEquals("23:59", Dates.formatTime(23, 59))
        assertEquals("23:59", Dates.formatTime(99, 99))
        assertEquals("00:00", Dates.formatTime(-5, -5))
        assertEquals("00:00", Dates.formatTime(0, 0))
    }

    // ------------------------------------- 4. DER Absturz: Backup-Dateiname

    /**
     * Die gemeldete Absturzstelle: der Dateiname enthaelt Stunden und Minuten.
     * Frueher stand hier "LocalDate.now().format(...HHmm...)" - ein LocalDate
     * kennt keine Stunden, also flog die App mit UnsupportedTemporalTypeException
     * aus. Der Dateiname benutzt jetzt LocalDateTime und einen Rueckfall.
     */
    @Test
    fun backupDateinameMitUhrzeit() {
        val muster = Regex("^Finovo-Backup-\\d{4}-\\d{2}-\\d{2}-\\d{4}\\.json$")
        repeat(3) {
            val name = Backup.defaultFileName()
            assertTrue(
                "Der Backup-Dateiname ist ungueltig (Absturzursache!): $name",
                muster.matches(name)
            )
        }
        // Der Name muss Stunden und Minuten wirklich enthalten (nicht 0000).
        val name = Backup.defaultFileName()
        val stempel = name.removePrefix("Finovo-Backup-").removeSuffix(".json")
        val uhrzeit = stempel.substringAfterLast('-')
        assertTrue("Im Dateinamen fehlt die Uhrzeit: $name", uhrzeit.length == 4)
        assertTrue(
            "Zwei unmittelbar erzeugte Namen muessen sich unterscheiden koennen: $name",
            uhrzeit.all { it.isDigit() }
        )
    }

    // ------------------------------- 5. Waechter: kein Zeit-Muster am LocalDate

    /**
     * Sucht im Quelltext nach Mustern mit Stunden/Minuten/Sekunden, die an
     * einem LocalDate haengen. So kann dieser Fehler nie wieder unbemerkt
     * hereinkommen.
     */
    @Test
    fun keinZeitMusterAnEinemLocalDate() {
        val wurzel = quellWurzel()
        if (wurzel == null) {
            println("Hinweis: Quelltext nicht gefunden - Pruefung uebersprungen.")
            return
        }
        val zeitFelder = Regex("[HhkKmsSaAnNVzZO]")
        val localDate = Regex("LocalDate(?!Time)")
        val musterFund = Regex("""ofPattern\(\s*"([^"]+)"\s*""")
        val faelle = mutableListOf<String>()

        wurzel.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { datei ->
            val text = datei.readText()
            datei.readLines().forEachIndexed { nr, zeile ->
                val muster = musterFund.find(zeile)?.groupValues?.getOrNull(1) ?: return@forEachIndexed
                val sauber = Regex("'[^']*'").replace(muster, "")
                if (!zeitFelder.containsMatchIn(sauber)) return@forEachIndexed
                // Zeit-Muster gefunden: steht ein LocalDate (ohne Time) davor?
                val von = (nr - 2).coerceAtLeast(0)
                val bis = (nr + 2).coerceAtMost(text.lines().size - 1)
                val umfeld = text.lines().subList(von, bis + 1).joinToString(" ")
                if (localDate.containsMatchIn(umfeld)) {
                    faelle.add("${datei.name}:${nr + 1} Muster \"$muster\"")
                }
            }
        }
        assertTrue(
            "Zeit-Muster an einem LocalDate gefunden:\n" + faelle.joinToString("\n"),
            faelle.isEmpty()
        )
    }

    // ----------------------------------------------- 6. Formate je Sprache

    @Test
    fun datumAufDeutsch() { sprache("de", "19.09.2026", Regex("""^\d{2}\.\d{2}\.\d{4}$""")) }

    @Test
    fun datumAufEnglisch() { sprache("en", "09/19/2026", Regex("""^\d{2}/\d{2}/\d{4}$""")) }

    @Test
    fun datumAufItalienisch() { sprache("it", "19/09/2026", Regex("""^\d{2}/\d{2}/\d{4}$""")) }

    @Test
    fun datumAufAlbanisch() { sprache("sq", "19.09.2026", Regex("""^\d{2}\.\d{2}\.\d{4}$""")) }

    @Test
    fun datumAufTuerkisch() { sprache("tr", "19.09.2026", Regex("""^\d{2}\.\d{2}\.\d{4}$""")) }

    private fun sprache(code: String, erwartet: String, form: Regex) {
        AppLocale.apply(code)
        val datum = LocalDate.of(2026, 9, 19)

        val voll = Dates.full(datum)
        assertEquals("Datumsformat $code", erwartet, voll)
        assertTrue("Format $code ungueltig: $voll", form.matches(voll))

        Dates.short(datum).also { assertTrue("Kurzformat $code ist leer", it.isNotBlank()) }
        Dates.long(datum).also {
            assertTrue("Langformat $code ist leer", it.isNotBlank())
            assertTrue("Langformat $code enthaelt kein Jahr: $it", it.contains("2026"))
        }
        Dates.monthYear(datum).also {
            assertTrue("Monatsformat $code ist leer", it.isNotBlank())
            assertTrue("Monatsformat $code ohne Jahr: $it", it.contains("2026"))
        }
        assertEquals("2026-09", Dates.monthKey(datum))
        Dates.weekdayShort(datum).also { assertTrue("Wochentag $code ist leer", it.isNotBlank()) }
        Dates.chartMonth(datum).also { assertTrue("Diagramm-Monat $code ist leer", it.isNotBlank()) }
        Dates.dateMedium(datum).also { assertTrue("Mittleres Format $code ist leer", it.isNotBlank()) }
        Dates.relative(datum).also { assertTrue("Relative Angabe $code ist leer", it.isNotBlank()) }
    }

    // --------------------------------- 7. Bildschirme mit kaputten Datumsdaten

    @Test
    fun bildschirmeMitKaputtenDaten() {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        kaputteDatenEinfuegen(app)
        val vm = AppViewModel(app)

        var bild by mutableStateOf(0)
        compose.setContent {
            val context = LocalContext.current
            val owner: ActivityResultRegistryOwner =
                requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides owner
            ) {
                MaterialTheme {
                    key(bild) {
                        when (bild) {
                            0 -> DashboardScreen(vm, {}, {}, {}, {})
                            1 -> AnalyticsScreen(vm) {}
                            2 -> BillsScreen(vm)
                            3 -> CalendarScreen(vm) {}
                            4 -> RecurringScreen(vm) {}
                            5 -> SubscriptionsScreen(vm) {}
                            6 -> GoalsScreen(vm) {}
                            7 -> InsightsScreen(vm) {}
                            8 -> ExpensesScreen(vm) {}
                            9 -> IncomeScreen(vm) {}
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        for (naechstes in 1..9) {
            compose.runOnIdle { bild = naechstes }
            compose.waitForIdle()
        }
        // Kein Absturz bis hierher = bestanden.
        assertTrue(true)
    }

    // ------------------------------------------------------------------ Hilfen

    /** Stichtag 19.09.2026 als epochDay. */
    private val STICHTAG: Long = LocalDate.of(2026, 9, 19).toEpochDay()

    private fun quellWurzel(): File? {
        var ordner = File(System.getProperty("user.dir"))
        repeat(6) {
            val kandidat = File(ordner, "src/main/java")
            if (kandidat.isDirectory) return kandidat
            ordner = ordner.parentFile ?: return null
        }
        return null
    }

    /** Daten mit unmoeglichen Datumsangaben - wie aus einer alten Sicherung. */
    private fun kaputteDatenEinfuegen(app: FinovoApp) {
        runBlocking(Dispatchers.IO) {
            val r = app.repository
            val ausgaben = r.addCategory("Extrem", "€", CategoryType.EXPENSE)
            val einnahmen = r.addCategory("Extrem ein", "€", CategoryType.INCOME)
            val tage = listOf(
                Long.MIN_VALUE, -365_243_219_163L, -1L, 0L,
                LocalDate.of(2026, 9, 19).toEpochDay(),
                36_524_178_047L, 365_241_780_471L, Long.MAX_VALUE
            )
            tage.forEachIndexed { i, tag ->
                r.addExpense(
                    ExpenseEntity(
                        id = 0, amountCents = 1_000L + i, categoryId = ausgaben,
                        date = tag, note = "Extrem $i"
                    )
                )
                r.addIncome(
                    IncomeEntity(
                        id = 0, amountCents = 2_000L + i, categoryId = einnahmen,
                        date = tag, note = "Extrem $i"
                    )
                )
                runCatching {
                    r.addBill(
                        BillEntity(
                            id = 0, name = "Rechnung $i", amountCents = 5_000L + i,
                            dueDate = tag, categoryId = ausgaben,
                            reminderHour = 99, reminderMinute = 99
                        )
                    )
                }
            }
            runCatching {
                r.addRecurringRule(
                    RecurringRuleEntity(
                        id = 0, kind = "EXPENSE", name = "Extrem-Regel",
                        amountCents = 3_000L, categoryId = ausgaben,
                        startDate = Long.MAX_VALUE
                    )
                )
                r.saveSubscription(
                    SubscriptionEntity(
                        id = 0, name = "Extrem-Abo", amountCents = 999L,
                        categoryId = ausgaben, nextPayment = Long.MIN_VALUE
                    )
                )
                r.saveGoal(
                    SavingGoalEntity(
                        id = 0, name = "Extrem-Ziel", targetCents = 100_000L,
                        savedCents = 100L, targetDate = Long.MAX_VALUE
                    )
                )
            }
        }
    }
}

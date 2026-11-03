package com.finovo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.ui.screens.AlarmeScreen
import com.finovo.app.ui.screens.CashflowScreen
import com.finovo.app.ui.screens.DashboardAnpassenScreen
import com.finovo.app.ui.screens.DokumenteScreen
import com.finovo.app.ui.screens.HaushaltScreen
import com.finovo.app.ui.screens.ImportScreen
import com.finovo.app.ui.screens.JahresberichtScreen
import com.finovo.app.ui.screens.NetWorthScreen
import com.finovo.app.ui.screens.PrognoseScreen
import com.finovo.app.ui.screens.VerlaufScreen
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Baut jeden neuen Bildschirm leer und mit echten Daten auf.
 *
 * So stürzt kein Bildschirm ab, wenn der Nutzer ihn zum ersten Mal öffnet -
 * auch nicht, wenn noch keine Daten erfasst sind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class ModuleScreensSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun prognoseLeer() = baueAuf(false) { PrognoseScreen(it) {} }
    @Test fun prognoseMitDaten() = baueAuf(true) { PrognoseScreen(it) {} }

    @Test fun cashflowLeer() = baueAuf(false) { CashflowScreen(it) {} }
    @Test fun cashflowMitDaten() = baueAuf(true) { CashflowScreen(it) {} }

    @Test fun nettovermoegenLeer() = baueAuf(false) { NetWorthScreen(it) {} }
    @Test fun nettovermoegenMitDaten() = baueAuf(true) { NetWorthScreen(it) {} }

    @Test fun jahresberichtLeer() = baueAuf(false) { JahresberichtScreen(it) {} }
    @Test fun jahresberichtMitDaten() = baueAuf(true) { JahresberichtScreen(it) {} }

    @Test fun dokumenteLeer() = baueAuf(false) { DokumenteScreen(it) {} }
    @Test fun dokumenteMitDaten() = baueAuf(true) { DokumenteScreen(it) {} }

    @Test fun importLeer() = baueAuf(false) { ImportScreen(it) {} }
    @Test fun importMitDaten() = baueAuf(true) { ImportScreen(it) {} }

    @Test fun verlaufLeer() = baueAuf(false) { VerlaufScreen(it) {} }
    @Test fun verlaufMitDaten() = baueAuf(true) { VerlaufScreen(it) {} }

    @Test fun haushaltLeer() = baueAuf(false) { HaushaltScreen(it) {} }
    @Test fun haushaltMitDaten() = baueAuf(true) { HaushaltScreen(it) {} }

    @Test fun alarmeLeer() = baueAuf(false) { AlarmeScreen(it) {} }
    @Test fun alarmeMitDaten() = baueAuf(true) { AlarmeScreen(it) {} }

    @Test fun dashboardAnpassenLeer() = baueAuf(false) { DashboardAnpassenScreen(it) {} }
    @Test fun dashboardAnpassenMitDaten() = baueAuf(true) { DashboardAnpassenScreen(it) {} }

    private fun baueAuf(mitDaten: Boolean, inhalt: @Composable (AppViewModel) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        if (mitDaten) fuelleDatenbank(app)
        val vm = AppViewModel(app)
        compose.setContent {
            MaterialTheme { key(inhalt.javaClass.name) { inhalt(vm) } }
        }
        compose.waitForIdle()
    }

    private fun fuelleDatenbank(app: FinovoApp) {
        runBlocking(Dispatchers.IO) {
            val repos = app.repository
            repos.addCategory("Modulausgaben", "€", CategoryType.EXPENSE)
            repos.addCategory("Moduleinnahmen", "€", CategoryType.INCOME)
            (1..30).forEach { i ->
                val tag = LocalDate.of(2026, 1, 1).plusDays(i.toLong() * 3)
                repos.addExpense(
                    ExpenseEntity(
                        id = 0,
                        amountCents = 4_500L + i,
                        categoryId = 60,
                        date = tag.toEpochDay(),
                        note = "Modulausgabe $i",
                        merchant = "Haendler $i"
                    )
                )
                repos.addIncome(
                    IncomeEntity(
                        id = 0,
                        amountCents = 180_000L + i,
                        categoryId = 61,
                        date = tag.toEpochDay(),
                        note = "Moduleinnahme $i",
                        merchant = "Arbeitgeber"
                    )
                )
            }
        }
    }
}

package com.finovo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.ui.screens.AboutScreen
import com.finovo.app.ui.screens.AnalyticsScreen
import com.finovo.app.ui.screens.AreasScreen
import com.finovo.app.ui.screens.BillsScreen
import com.finovo.app.ui.screens.BudgetScreen
import com.finovo.app.ui.screens.CalendarScreen
import com.finovo.app.ui.screens.CarScreen
import com.finovo.app.ui.screens.CategoriesScreen
import com.finovo.app.ui.screens.DashboardScreen
import com.finovo.app.ui.screens.DataScreen
import com.finovo.app.ui.screens.ExpensesScreen
import com.finovo.app.ui.screens.GoalsScreen
import com.finovo.app.ui.screens.IncomeScreen
import com.finovo.app.ui.screens.InsightsScreen
import com.finovo.app.ui.screens.MoreScreen
import com.finovo.app.ui.screens.ReceiptsScreen
import com.finovo.app.ui.screens.RecurringScreen
import com.finovo.app.ui.screens.ScanReviewScreen
import com.finovo.app.ui.screens.SearchScreen
import com.finovo.app.ui.screens.SettingsScreen
import com.finovo.app.ui.screens.SubscriptionsScreen
import com.finovo.app.ui.screens.WhatIfScreen
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
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
 * Baut jeden Bildschirm zwei Mal auf: leer und mit echten Daten.
 *
 * Genau so findet man einen Absturz wie den in der Finanzanalyse, bevor
 * der Nutzer darüber stolpert - auch in den Bereichen, die nur mit Inhalt
 * überhaupt gezeichnet werden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class AllScreensSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardLeer() {
        baueAuf(mitDaten = false) { vm -> DashboardScreen(vm, {}, {}, {}, {}) }
    }

    @Test
    fun dashboardMitDaten() {
        baueAuf(mitDaten = true) { vm -> DashboardScreen(vm, {}, {}, {}, {}) }
    }

    @Test
    fun ausgabenLeer() {
        baueAuf(mitDaten = false) { vm -> ExpensesScreen(vm) {} }
    }

    @Test
    fun ausgabenMitDaten() {
        baueAuf(mitDaten = true) { vm -> ExpensesScreen(vm) {} }
    }

    @Test
    fun einnahmenLeer() {
        baueAuf(mitDaten = false) { vm -> IncomeScreen(vm) {} }
    }

    @Test
    fun einnahmenMitDaten() {
        baueAuf(mitDaten = true) { vm -> IncomeScreen(vm) {} }
    }

    @Test
    fun rechnungenLeer() {
        baueAuf(mitDaten = false) { vm -> BillsScreen(vm) }
    }

    @Test
    fun rechnungenMitDaten() {
        baueAuf(mitDaten = true) { vm -> BillsScreen(vm) }
    }

    @Test
    fun mehrLeer() {
        baueAuf(mitDaten = false) { vm -> MoreScreen(vm) {} }
    }

    @Test
    fun mehrMitDaten() {
        baueAuf(mitDaten = true) { vm -> MoreScreen(vm) {} }
    }

    @Test
    fun finanzanalyseLeer() {
        baueAuf(mitDaten = false) { vm -> AnalyticsScreen(vm) {} }
    }

    @Test
    fun finanzanalyseMitDaten() {
        baueAuf(mitDaten = true) { vm -> AnalyticsScreen(vm) {} }
    }

    @Test
    fun budgetLeer() {
        baueAuf(mitDaten = false) { vm -> BudgetScreen(vm) {} }
    }

    @Test
    fun budgetMitDaten() {
        baueAuf(mitDaten = true) { vm -> BudgetScreen(vm) {} }
    }

    @Test
    fun wiederkehrendLeer() {
        baueAuf(mitDaten = false) { vm -> RecurringScreen(vm) {} }
    }

    @Test
    fun wiederkehrendMitDaten() {
        baueAuf(mitDaten = true) { vm -> RecurringScreen(vm) {} }
    }

    @Test
    fun kategorienLeer() {
        baueAuf(mitDaten = false) { vm -> CategoriesScreen(vm) {} }
    }

    @Test
    fun kategorienMitDaten() {
        baueAuf(mitDaten = true) { vm -> CategoriesScreen(vm) {} }
    }

    @Test
    fun datenLeer() {
        baueAuf(mitDaten = false) { vm -> DataScreen(vm) {} }
    }

    @Test
    fun datenMitDaten() {
        baueAuf(mitDaten = true) { vm -> DataScreen(vm) {} }
    }

    @Test
    fun sparzielLeer() {
        baueAuf(mitDaten = false) { vm -> GoalsScreen(vm) {} }
    }

    @Test
    fun sparzielMitDaten() {
        baueAuf(mitDaten = true) { vm -> GoalsScreen(vm) {} }
    }

    @Test
    fun abosLeer() {
        baueAuf(mitDaten = false) { vm -> SubscriptionsScreen(vm) {} }
    }

    @Test
    fun abosMitDaten() {
        baueAuf(mitDaten = true) { vm -> SubscriptionsScreen(vm) {} }
    }

    @Test
    fun bereicheLeer() {
        baueAuf(mitDaten = false) { vm -> AreasScreen(vm) {} }
    }

    @Test
    fun bereicheMitDaten() {
        baueAuf(mitDaten = true) { vm -> AreasScreen(vm) {} }
    }

    @Test
    fun autoLeer() {
        baueAuf(mitDaten = false) { vm -> CarScreen(vm) {} }
    }

    @Test
    fun autoMitDaten() {
        baueAuf(mitDaten = true) { vm -> CarScreen(vm) {} }
    }

    @Test
    fun sucheLeer() {
        baueAuf(mitDaten = false) { vm -> SearchScreen(vm) {} }
    }

    @Test
    fun sucheMitDaten() {
        baueAuf(mitDaten = true) { vm -> SearchScreen(vm) {} }
    }

    @Test
    fun kalenderLeer() {
        baueAuf(mitDaten = false) { vm -> CalendarScreen(vm) {} }
    }

    @Test
    fun kalenderMitDaten() {
        baueAuf(mitDaten = true) { vm -> CalendarScreen(vm) {} }
    }

    @Test
    fun auswertungLeer() {
        baueAuf(mitDaten = false) { vm -> InsightsScreen(vm) {} }
    }

    @Test
    fun auswertungMitDaten() {
        baueAuf(mitDaten = true) { vm -> InsightsScreen(vm) {} }
    }

    @Test
    fun wasWaereWennLeer() {
        baueAuf(mitDaten = false) { vm -> WhatIfScreen(vm) {} }
    }

    @Test
    fun wasWaereWennMitDaten() {
        baueAuf(mitDaten = true) { vm -> WhatIfScreen(vm) {} }
    }

    @Test
    fun belegeLeer() {
        baueAuf(mitDaten = false) { vm -> ReceiptsScreen(vm) {} }
    }

    @Test
    fun belegeMitDaten() {
        baueAuf(mitDaten = true) { vm -> ReceiptsScreen(vm) {} }
    }

    @Test
    fun einstellungenLeer() {
        baueAuf(mitDaten = false) { vm -> SettingsScreen(vm, {}, {}) }
    }

    @Test
    fun einstellungenMitDaten() {
        baueAuf(mitDaten = true) { vm -> SettingsScreen(vm, {}, {}) }
    }

    @Test
    fun ueberLeer() {
        baueAuf(mitDaten = false) { vm -> AboutScreen(vm) {} }
    }

    @Test
    fun ueberMitDaten() {
        baueAuf(mitDaten = true) { vm -> AboutScreen(vm) {} }
    }

    @Test
    fun scanPruefenLeer() {
        baueAuf(mitDaten = false) { vm -> ScanReviewScreen(vm) {} }
    }

    @Test
    fun scanPruefenMitDaten() {
        baueAuf(mitDaten = true) { vm -> ScanReviewScreen(vm) {} }
    }

    private fun baueAuf(mitDaten: Boolean, inhalt: @Composable (AppViewModel) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        if (mitDaten) fuelleDatenbank(app)
        val vm = AppViewModel(app)
        compose.setContent {
            MaterialTheme { key(inhalt.javaClass.name) { inhalt(vm) } }
        }
        compose.waitForIdle()
    }

    /** Echte Testdaten in der echten Datenbank. */
    private fun fuelleDatenbank(app: FinovoApp) {
        runBlocking(Dispatchers.IO) {
            val repos = app.repository
            repos.addCategory("Testausgaben", "€", CategoryType.EXPENSE)
            repos.addCategory("Testgehalt", "€", CategoryType.INCOME)
            (1..60).forEach { i ->
                val tag = LocalDate.of(2026, 1, 1).plusDays(i.toLong() * 2)
                repos.addExpense(
                    ExpenseEntity(
                        id = 0,
                        amountCents = 12_345L + i,
                        categoryId = 50,
                        date = tag.toEpochDay(),
                        note = "Ausgabe $i"
                    )
                )
                repos.addIncome(
                    IncomeEntity(
                        id = 0,
                        amountCents = 250_000L + i,
                        categoryId = 51,
                        date = tag.toEpochDay(),
                        note = "Gehalt $i"
                    )
                )
            }
        }
    }
}

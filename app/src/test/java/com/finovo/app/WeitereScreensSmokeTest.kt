package com.finovo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.ui.screens.AboutScreen
import com.finovo.app.ui.screens.BankScreen
import com.finovo.app.ui.screens.BelegScanScreen
import com.finovo.app.ui.screens.CategoriesScreen
import com.finovo.app.ui.screens.FinovoAiScreen
import com.finovo.app.ui.screens.LockScreen
import com.finovo.app.ui.screens.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Rauchtests fuer die Bildschirme, die der grosse Durchlauf noch nicht
 * abdeckt: Bank, Finovo AI, Sperrbildschirm, Startbild, Belegpruefung -
 * und die Kategorien mit echten Unterkategorien (Modul 12).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class, qualifiers = "w411dp-h3000dp")
class WeitereScreensSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bankLeer() = baueAuf(false) { BankScreen(it) {} }

    @Test
    fun bankMitDaten() = baueAuf(true) { BankScreen(it) {} }

    @Test
    fun finovoAiLeer() = baueAuf(false) { FinovoAiScreen(it) {} }

    @Test
    fun finovoAiMitDaten() = baueAuf(true) { FinovoAiScreen(it) {} }

    @Test
    fun belegPruefenLeer() = baueAuf(false) { BelegScanScreen(it) {} }

    @Test
    fun belegPruefenMitDaten() = baueAuf(true) { BelegScanScreen(it) {} }

    @Test
    fun sperrbildschirmMitPin() {
        baueAuf(true) {
            LockScreen(
                onUnlocked = {},
                onBiometric = {},
                biometricEnabled = true,
                verify = { it == "1234" }
            )
        }
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun sperrbildschirmOhneBiometrie() {
        baueAuf(false) {
            LockScreen(
                onUnlocked = {},
                onBiometric = {},
                biometricEnabled = false,
                verify = { true }
            )
        }
    }

    @Test
    fun startbildLaeuft() {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        compose.setContent { MaterialTheme { SplashScreen(onFinished = {}) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        check(app.packageName.isNotBlank())
    }

    @Test
    fun kategorienZeigtUnterkategorienEingerueckt() {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        runBlocking(Dispatchers.IO) {
            val autoId = app.repository.addCategory("AutoTest", "\uD83D\uDE97", CategoryType.EXPENSE)
            app.repository.addCategory("SpritTest", "\u26FD", CategoryType.EXPENSE, false, autoId)
            app.repository.addCategory("WerkstattTest", "\uD83D\uDD27", CategoryType.EXPENSE, false, autoId)
        }
        val vm = AppViewModel(app)
        compose.setContent { MaterialTheme { CategoriesScreen(vm) {} } }
        compose.waitForIdle()
        // Warten, bis die Kategorien aus der Datenbank im ViewModel sind
        val ende = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < ende &&
            vm.categories.value.none { it.name == "AutoTest" }
        ) {
            compose.waitForIdle()
            Thread.sleep(30)
        }
        // Die Liste ist lang - zu den neuen Kategorien scrollen
        compose.onNodeWithText("AutoTest").assertIsDisplayed()
        compose.onNodeWithText("SpritTest").assertIsDisplayed()
        compose.onNodeWithText("WerkstattTest").assertIsDisplayed()
    }

    /** Wartet, bis [text] wirklich sichtbar ist (Daten kommen ueber Flows). */
    private fun warteAufText(text: String, dauerMs: Long = 15_000) {
        val ende = System.currentTimeMillis() + dauerMs
        var letzterFehler: Throwable? = null
        while (System.currentTimeMillis() < ende) {
            compose.waitForIdle()
            try {
                compose.onNodeWithText(text).assertIsDisplayed()
                return
            } catch (e: Throwable) {
                letzterFehler = e
                Thread.sleep(40)
            }
        }
        throw AssertionError("Text '$text' wurde nicht angezeigt", letzterFehler)
    }

    @Test
    fun ueberZeigtEchteAppInformationen() {
        baueAuf(true) { AboutScreen(it) {} }
        // Robolectric meldet englische Texte
        warteAufText("App information")
        warteAufText("Version")
        warteAufText("Total entries")
        warteAufText("Permissions")
        warteAufText("Installed on")
    }

    private fun baueAuf(mitDaten: Boolean, inhalt: @Composable (AppViewModel) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        if (mitDaten) fuelleDatenbank(app)
        val vm = AppViewModel(app)
        compose.setContent { MaterialTheme { inhalt(vm) } }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** Echte Testdaten in der echten Datenbank. */
    private fun fuelleDatenbank(app: FinovoApp) {
        runBlocking(Dispatchers.IO) {
            val repos = app.repository
            repos.addCategory("Testausgaben", "€", CategoryType.EXPENSE)
            repos.addCategory("Testgehalt", "€", CategoryType.INCOME)
            (1..30).forEach { i ->
                val tag = LocalDate.of(2026, 1, 1).plusDays(i.toLong() * 3)
                repos.addExpense(
                    ExpenseEntity(
                        id = 0,
                        amountCents = 4_500L + i,
                        categoryId = 50,
                        date = tag.toEpochDay(),
                        note = "Ausgabe $i"
                    )
                )
                repos.addIncome(
                    IncomeEntity(
                        id = 0,
                        amountCents = 180_000L + i,
                        categoryId = 51,
                        date = tag.toEpochDay(),
                        note = "Gehalt $i"
                    )
                )
            }
        }
    }
}

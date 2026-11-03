package com.finovo.app

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.ui.screens.AnalyticsScreen
import com.finovo.app.util.AppLocale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Finanzanalyse wird in jeder unterstuetzten Sprache aufgebaut.
 *
 * Das prueft zwei Dinge gleichzeitig: der Bildschirm ueberlebt das Layout,
 * und jede Sprache liefert wirklich eigene Texte (ein fehlender Schluessel
 * oder eine kaputte Uebersetzung faellt hier auf).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class AnalyticsI18nTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun analyseAufDeutsch() { baueAuf("de") }

    @Test
    fun analyseAufEnglisch() { baueAuf("en") }

    @Test
    fun analyseAufItalienisch() { baueAuf("it") }

    @Test
    fun analyseAufAlbanisch() { baueAuf("sq") }

    @Test
    fun analyseAufTuerkisch() { baueAuf("tr") }

    private fun baueAuf(code: String) {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        val vm = AppViewModel(app)
        var generation by mutableStateOf(0)
        compose.setContent {
            val context = LocalContext.current
            val owner: ActivityResultRegistryOwner =
                requireNotNull(LocalActivityResultRegistryOwner.current)
            AppLocale.apply(code)
            val wrapped = remember(code) { AppLocale.wrap(context, code) }
            CompositionLocalProvider(
                LocalContext provides wrapped,
                LocalActivityResultRegistryOwner provides owner
            ) {
                MaterialTheme { key(code, generation) { AnalyticsScreen(vm, {}) } }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { generation = 1 }
        compose.waitForIdle()

        // Die wichtigsten Texte muessen vorhanden, gefuellt und uebersetzt sein.
        val res = AppLocale.wrap(app, code).resources
        val pkg = app.packageName
        val muster = listOf(
            "analysis_no_data" to "Noch nicht genügend Daten",
            "analysis_no_data_hint" to "Erfasse Einnahmen",
            "analysis_title" to "Finanzanalyse",
            "crash_safe_title" to "Diese Ansicht"
        )
        for ((schluessel, deutsch) in muster) {
            val id = res.getIdentifier(schluessel, "string", pkg)
            assertTrue("$schluessel fehlt fuer Sprache $code", id != 0)
            val wert = res.getString(id)
            assertTrue("$schluessel ist leer in Sprache $code", wert.isNotBlank())
            if (code != "de") {
                assertTrue(
                    "$schluessel ist in Sprache $code nicht uebersetzt: $wert",
                    !wert.startsWith(deutsch)
                )
            }
        }
    }
}

package com.finovo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.ui.screens.AnalyticsScreen
import com.finovo.app.util.AnalyticsPeriod
import com.finovo.app.util.Granularity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Der entscheidende Test: baut die Finanzanalyse wirklich auf.
 *
 * Jeder Absturz beim Messen/Aufbauen (z. B. die doppelte horizontale
 * Scroll-Kette) laesst diesen Test rot werden - ohne ihn ist keine Aussage
 * ueber "behoben" erlaubt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class AnalyticsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun viewModel(): AppViewModel =
        AppViewModel(ApplicationProvider.getApplicationContext())

    @Test
    fun analyseBildschirm_bautMitLeererDatenbankAuf() {
        val vm = viewModel()
        compose.setContent {
            MaterialTheme { AnalyticsScreen(vm, {}) }
        }
        compose.waitForIdle()
    }

    @Test
    fun analyseBildschirm_bautNachNeuAufbauErneutAuf() {
        val vm = viewModel()
        var generation by mutableStateOf(0)
        compose.setContent {
            MaterialTheme { key(generation) { AnalyticsScreen(vm, {}) } }
        }
        compose.waitForIdle()
        compose.runOnIdle { generation = 1 }
        compose.waitForIdle()
    }

    @Test
    fun analyseBildschirm_bleibtBeiPeriodenWechselStabil() {
        val vm = viewModel()
        var generation by mutableStateOf(0)
        compose.setContent {
            MaterialTheme { key(generation) { AnalyticsScreen(vm, {}) } }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            vm.analyticsPeriod.value = AnalyticsPeriod.LAST_YEAR
            generation = 1
        }
        compose.waitForIdle()
    }

    @Test
    fun analyseBildschirm_bleibtBeiGranularitaetswechselStabil() {
        val vm = viewModel()
        var generation by mutableStateOf(0)
        compose.setContent {
            MaterialTheme { key(generation) { AnalyticsScreen(vm, {}) } }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            vm.analyticsGranularity.value = Granularity.DAILY
            generation = 1
        }
        compose.waitForIdle()
    }
}

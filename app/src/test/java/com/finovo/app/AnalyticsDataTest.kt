package com.finovo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.ui.screens.AnalyticsScreen
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Finanzanalyse mit echtem Datenbankinhalt - nicht nur leer. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class AnalyticsDataTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun nurAusgaben() {
        fuelle { repos ->
            repos.addCategory("Testausgaben", "€", CategoryType.EXPENSE)
            (1..12).forEach { i ->
                repos.addExpense(
                    ExpenseEntity(
                        id = 0, amountCents = 12_345L + i, categoryId = 50,
                        date = LocalDate.of(2026, 1, 1).plusDays(i.toLong()).toEpochDay(),
                        note = "Ausgabe $i"
                    )
                )
            }
        }
        baueAuf()
    }

    @Test
    fun nurEinnahmen() {
        fuelle { repos ->
            repos.addCategory("Testgehalt", "€", CategoryType.INCOME)
            (1..6).forEach { i ->
                repos.addIncome(
                    IncomeEntity(
                        id = 0, amountCents = 250_000L + i, categoryId = 51,
                        date = LocalDate.of(2026, 2, 1).plusDays(i.toLong()).toEpochDay(),
                        note = "Gehalt $i"
                    )
                )
            }
        }
        baueAuf()
    }

    @Test
    fun einnahmenUndAusgaben() {
        fuelle { repos ->
            repos.addCategory("Testmiete", "€", CategoryType.EXPENSE)
            repos.addCategory("Testlohn", "€", CategoryType.INCOME)
            (1..40).forEach { i ->
                val tag = LocalDate.of(2026, 1, 1).plusDays(i.toLong() * 3)
                repos.addExpense(
                    ExpenseEntity(id = 0, amountCents = 90_000L, categoryId = 60,
                        date = tag.toEpochDay(), note = "Miete $i")
                )
                repos.addIncome(
                    IncomeEntity(id = 0, amountCents = 300_000L, categoryId = 61,
                        date = tag.toEpochDay(), note = "Lohn $i")
                )
            }
        }
        baueAuf()
    }

    private fun baueAuf() {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        val vm = AppViewModel(app)
        var generation by mutableStateOf(0)
        compose.setContent {
            MaterialTheme { key(generation) { AnalyticsScreen(vm, {}) } }
        }
        compose.waitForIdle()
        compose.runOnIdle { generation = 1 }
        compose.waitForIdle()
    }

    private fun fuelle(block: suspend (repo: com.finovo.app.data.Repository) -> Unit) {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        runBlocking(Dispatchers.IO) { block(app.repository) }
    }
}

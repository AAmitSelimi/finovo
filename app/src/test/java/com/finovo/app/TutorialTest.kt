package com.finovo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.tutorial.TutorialCatalog
import com.finovo.app.tutorial.TutorialDialog
import com.finovo.app.tutorial.TutorialProgress
import com.finovo.app.tutorial.TutorialsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Modul-Anleitungen: vollstaendige Texte in allen Sprachen, Ablauf und Fortschritt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class, qualifiers = "w411dp-h3000dp")
class TutorialTest {

    @get:Rule
    val compose = createComposeRule()

    private fun strings(folder: String): Set<String> {
        val f = listOf(File("src/main/res/$folder/strings.xml"), File("app/src/main/res/$folder/strings.xml"))
            .first { it.exists() }
        return Regex("<string name=\"([a-z0-9_]+)\"").findAll(f.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun jedeAnleitungIstInAllenSprachenVollstaendig() {
        val ids = TutorialCatalog.all.map { it.id }
        assertEquals("doppelte Anleitungs-IDs", ids.size, ids.toSet().size)
        for (folder in listOf("values", "values-en", "values-it", "values-sq", "values-tr")) {
            val keys = strings(folder)
            for (def in TutorialCatalog.all) {
                assertTrue("$folder: tut_${def.id}_title", "tut_${def.id}_title" in keys)
                assertTrue("$folder: tut_${def.id}_flow", "tut_${def.id}_flow" in keys)
                for (i in 1..def.steps.size) {
                    assertTrue("$folder: tut_${def.id}_s${i}_t", "tut_${def.id}_s${i}_t" in keys)
                    assertTrue("$folder: tut_${def.id}_s${i}_b", "tut_${def.id}_s${i}_b" in keys)
                }
                def.related.forEach { assertTrue("${def.id} -> $it", TutorialCatalog.get(it) != null) }
            }
        }
    }

    @Test
    fun anleitungDurchspielenMarkiertSieAlsAbgeschlossen() {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        TutorialProgress.resetAll(app)
        val def = TutorialCatalog.get("expenses")!!
        compose.setContent { MaterialTheme { TutorialDialog(startId = "expenses", onDismiss = {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Step 1 of ${def.pageCount}").assertExists()
        compose.onNodeWithText("Open Expenses").assertExists()
        repeat(def.pageCount - 1) {
            compose.onNodeWithText("Next").performClick()
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
        }
        compose.onNodeWithText("Step ${def.pageCount} of ${def.pageCount}").assertExists()
        compose.onNodeWithText("How it connects").assertExists()
        assertFalse(TutorialProgress.isDone(app, "expenses"))
        compose.onNodeWithText("Finish").performClick()
        compose.waitForIdle()
        assertTrue(TutorialProgress.isDone(app, "expenses"))
    }

    @Test
    fun fortschrittWirdProModulGespeichertUndZurueckgesetzt() {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        TutorialProgress.resetAll(app)
        TutorialProgress.savePage(app, "bills", 3)
        TutorialProgress.complete(app, "income")
        assertEquals(3, TutorialProgress.lastPage(app, "bills"))
        assertFalse(TutorialProgress.isDone(app, "bills"))
        assertTrue(TutorialProgress.isDone(app, "income"))
        assertEquals(1, TutorialProgress.doneCount(app))
        TutorialProgress.reset(app, "income")
        assertFalse(TutorialProgress.isDone(app, "income"))
    }

    @Test
    fun uebersichtZeigtGesamtfortschritt() {
        val app = ApplicationProvider.getApplicationContext<FinovoApp>()
        TutorialProgress.resetAll(app)
        TutorialProgress.complete(app, "dashboard")
        compose.setContent { MaterialTheme { TutorialsScreen(onBack = {}) } }
        compose.waitForIdle()
        val total = TutorialCatalog.all.size
        compose.onNodeWithText("1 of $total tutorials completed").assertExists()
        assertTrue(compose.onAllNodesWithText("Completed").fetchSemanticsNodes().isNotEmpty())
    }
}

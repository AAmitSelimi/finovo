package com.finovo.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.finovo.app.ai.AiBlock
import com.finovo.app.ai.AiIntent
import com.finovo.app.ai.AiSection
import com.finovo.app.ai.AiServiceLayer
import com.finovo.app.ai.AiSource
import com.finovo.app.ai.FinancialSummary
import com.finovo.app.ai.FinancialSummaryService
import com.finovo.app.ai.FinovoAiEngine
import com.finovo.app.ai.HttpRemoteAiProvider
import com.finovo.app.ai.NoRemoteAiProvider
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.Frequency
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.Repository
import com.finovo.app.ui.screens.FinovoAiScreen
import com.finovo.app.util.AppLocale
import com.finovo.app.util.Money
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Finovo AI - Prüfungen für die geforderten Abläufe.
 *
 * Es wird ausschließlich mit echten Daten aus der echten Datenbank
 * gearbeitet (keine Mocks). Geprüft wird unter anderem:
 *
 *  - die sechs Beispielfragen liefern echte Werte aus der Datenbank
 *  - es kommen keine erfundenen Beträge vor
 *  - Daten, Berechnetes, Schätzungen und Vorschläge sind getrennt
 *  - alle fünf Sprachen sind vollständig und gemischtfrei
 *  - die KI kann nichts verändern (Schreibschutz)
 *  - fällt ein externer Dienst aus, arbeitet Finovo AI lokal weiter
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class FinovoAiTest {

    @get:Rule
    val compose = createComposeRule()

    private val heute: LocalDate = LocalDate.now()
    private val symbol = "€"

    // Erwartete Werte - in Cent, exakt so wie in die Datenbank geschrieben.
    private val ausgabenMonat = 175_000L
    private val einnahmenMonat = 250_000L
    private val uebrigMonat = einnahmenMonat - ausgabenMonat
    private val miete = 90_000L
    private val lebensmittel = 42_000L
    private val auto = 31_000L
    private val versicherung = 12_000L
    private val offenStrom = 8_900L
    private val offenHandy = 4_990L
    private val offenGesamt = offenStrom + offenHandy
    private val wiederkehrendMonat = 1_299L
    private val ausgabenVormonat = 120_000L
    private val mieteBetrag = miete
    private val lebensmittelBetrag = lebensmittel
    private val autoBetrag = auto
    private val versicherungBetrag = versicherung

    @Before
    fun vorbereiten() {
        AiServiceLayer.provider = NoRemoteAiProvider
        AiServiceLayer.mode = com.finovo.app.ai.AiMode.LOCAL
        AiServiceLayer.consentGiven = false
        AppLocale.save(app(), "de")
        // Die Datenbank wird zwischen den Testmethoden geteilt - damit jede
        // Prüfung dieselben Ausgangsdaten hat, wird sie hier neu aufgesetzt.
        runBlocking(Dispatchers.IO) { app().repository.deleteAllData() }
    }

    @After
    fun aufraeumen() {
        AiServiceLayer.provider = NoRemoteAiProvider
        AiServiceLayer.mode = com.finovo.app.ai.AiMode.LOCAL
        AiServiceLayer.consentGiven = false
        AppLocale.save(app(), "de")
    }

    // ------------------------------------------------------- 1. Wo geht mein Geld hin?

    @Test
    fun woGehtMeinGeldHin() {
        val s = zusammenfassung()
        val antwort = frage(s, "Wo geht mein Geld hin?")

        assertEquals(AiIntent.WHERE_MONEY_GOES, antwort.intent)
        assertTrue(antwort.text, antwort.text.contains(Money.format(ausgabenMonat, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(einnahmenMonat, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(uebrigMonat, symbol)))
        // Alle vier Kategorien mit ihren echten Beträgen
        listOf(miete, lebensmittel, auto, versicherung).forEach {
            assertTrue("Kategoriebetrag $it fehlt: ${antwort.text}", antwort.text.contains(Money.format(it, symbol)))
        }
        assertTrue(antwort.text, antwort.text.contains("Miete"))
        assertTrue(antwort.text, antwort.text.contains("Lebensmittel"))
        // Die Trennung zwischen echten Daten und Berechnung ist sichtbar
        assertTrue(antwort.text, antwort.text.contains(AiSection.FACTS.label()))
        assertTrue(antwort.text, antwort.text.contains(AiSection.CALCULATED.label()))
        assertTrue(antwort.text, antwort.text.contains("Lebensmittel"))
        // Größte variable Ausgaben: Lebensmittel und Auto (Miete/Versicherung sind fix)
        assertTrue(antwort.text, antwort.text.contains("variablen Ausgaben sind Lebensmittel und Auto"))
    }

    // ------------------------------------------------------------- 2. Sparen

    @Test
    fun wieKannIchSparen() {
        val s = zusammenfassung()
        val antwort = frage(s, "Wie kann ich sparen?")

        assertEquals(AiIntent.SAVE_MONEY, antwort.intent)
        assertTrue(antwort.text, antwort.text.contains(Money.format(ausgabenMonat, symbol)))
        // Fixkosten und variable Kosten stammen aus den Daten
        assertTrue(antwort.text, antwort.text.contains(Money.format(miete + versicherung, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(lebensmittel + auto, symbol)))
        // Vorschläge sind als Vorschläge gekennzeichnet
        assertTrue(antwort.text, antwort.text.contains(AiSection.SUGGESTION.label()))
        assertTrue(antwort.text, antwort.text.contains(Money.format(lebensmittel / 10L, symbol)))
        val vorschlaege = antwort.blocks.filterIsInstance<AiBlock.Row>().count { it.section == AiSection.SUGGESTION }
        assertTrue("Es muss echte Sparvorschläge geben: ${antwort.text}", vorschlaege >= 2)
    }

    // --------------------------------------------------- 3. Offene Rechnungen

    @Test
    fun welcheRechnungenSindOffen() {
        val s = zusammenfassung()
        val antwort = frage(s, "Welche Rechnungen sind offen?")

        assertEquals(AiIntent.OPEN_BILLS, antwort.intent)
        assertTrue(antwort.text, antwort.text.contains("Stromabschlag"))
        assertTrue(antwort.text, antwort.text.contains("Handyrechnung"))
        assertTrue(antwort.text, antwort.text.contains(Money.format(offenStrom, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(offenHandy, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(offenGesamt, symbol)))
        // Überfällige Rechnung ist gekennzeichnet
        assertTrue(antwort.text, antwort.text.contains("überfällig"))
        assertTrue(antwort.text, antwort.text.contains("seit 5 Tagen überfällig"))
        // Bezahlte Rechnung taucht nicht als offen auf
        assertTrue(antwort.text, !antwort.text.contains("Internet"))
        // Fälligkeitsdatum stammt aus der Datenbank
        val faellig = s.openBills.first { it.name == "Stromabschlag" }.dueDate
        assertTrue("Fälligkeitsdatum fehlt: ${antwort.text}", antwort.text.contains(com.finovo.app.util.Dates.full(faellig!!)))
    }

    @Test
    fun offeneRechnungenOhneFaelligkeitsdatumWerdenNichtErfunden() {
        val app = app()
        runBlocking(Dispatchers.IO) {
            app.repository.deleteAllData()
            val kategorie = app.repository.addCategory("KI-Test", "\uD83D\uDCE6", CategoryType.EXPENSE)
            // Rechnung ohne Fälligkeitsdatum (dueDate = 0)
            app.repository.addBill(
                BillEntity(id = 0, name = "Ohne Datum", amountCents = 5_000L, dueDate = 0L, categoryId = kategorie)
            )
        }
        val s = runBlocking { baue(app) }
        val antwort = frage(s, "Welche Rechnungen sind offen?")
        assertTrue(antwort.text, antwort.text.contains("Ohne Datum"))
        assertTrue(antwort.text, antwort.text.contains(strOhneDatum()))
        assertTrue(
            "Es darf kein erfundenes Datum auftauchen: ${antwort.text}",
            !antwort.text.contains("01.01.1970")
        )
    }

    // ------------------------------------------------------ 4. Bald bezahlen

    @Test
    fun wasMussIchBaldBezahlen() {
        val s = zusammenfassung()
        val antwort = frage(s, "Was muss ich bald bezahlen?")

        assertEquals(AiIntent.UPCOMING_PAYMENTS, antwort.intent)
        assertTrue(antwort.text, antwort.text.contains("Stromabschlag"))
        assertTrue(antwort.text, antwort.text.contains(Money.format(offenStrom, symbol)))
        assertTrue(antwort.text, antwort.text.contains("in 3 Tagen"))
        // Die überfällige Rechnung ist nicht "bald fällig"
        assertTrue(antwort.text, !antwort.text.contains("Handyrechnung"))
    }

    // -------------------------------------------------------- 5. Monatsanalyse

    @Test
    fun analysiereMeinenMonat() {
        val s = zusammenfassung()
        val antwort = frage(s, "Analysiere meinen Monat.")

        assertEquals(AiIntent.MONTH_ANALYSIS, antwort.intent)
        assertTrue(antwort.text, antwort.text.contains(Money.format(einnahmenMonat, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(ausgabenMonat, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(uebrigMonat, symbol)))
        assertTrue(antwort.text, antwort.text.contains("Offene Rechnungen"))
        assertTrue(antwort.text, antwort.text.contains(Money.format(offenGesamt, symbol)))
        assertTrue(antwort.text, antwort.text.contains("Wiederkehrende Zahlungen"))
        assertTrue(antwort.text, antwort.text.contains(Money.format(wiederkehrendMonat, symbol)))
        // Vergleich zum Vormonat
        assertTrue(antwort.text, antwort.text.contains(Money.format(ausgabenVormonat, symbol)))
        // Sparbereiche als Vorschlag
        assertTrue(antwort.text, antwort.text.contains(AiSection.SUGGESTION.label()))
        // Kategorien
        assertTrue(antwort.text, antwort.text.contains("Miete"))
    }

    // ------------------------------------------------------ 6. Monatsvergleich

    @Test
    fun vergleicheDiesenMonatMitDemLetztenMonat() {
        val s = zusammenfassung()
        val antwort = frage(s, "Vergleiche diesen Monat mit dem letzten Monat.")

        assertEquals(AiIntent.MONTH_COMPARISON, antwort.intent)
        assertTrue(antwort.text, antwort.text.contains(Money.format(ausgabenVormonat, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(ausgabenMonat, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(einnahmenMonat, symbol)))
        val differenz = ausgabenMonat - ausgabenVormonat
        assertTrue(antwort.text, antwort.text.contains(Money.format(differenz, symbol)))
        assertTrue(antwort.text, antwort.text.contains("mehr als im Vormonat"))
    }

    // ------------------------------------------------- 7. Wiederkehrendes

    @Test
    fun welcheZahlungenSindWiederkehrend() {
        val s = zusammenfassung()
        val antwort = frage(s, "Welche Zahlungen sind wiederkehrend?")

        assertEquals(AiIntent.RECURRING_PAYMENTS, antwort.intent)
        assertTrue(antwort.text, antwort.text.contains("Netflix"))
        assertTrue(antwort.text, antwort.text.contains(Money.format(wiederkehrendMonat, symbol)))
        assertTrue(antwort.text, antwort.text.contains(Money.format(wiederkehrendMonat * 12L, symbol)))
    }

    // ----------------------------------------------- 8. Keine erfundenen Beträge

    @Test
    fun alleBetraegeStammenAusDerDatenbank() {
        val s = zusammenfassung()
        val erlaubt = erlaubteBetraege(s)
        for (frage in ALLE_FRAGEN) {
            val antwort = frage(s, frage)
            val betrage = Regex("[\\d.,]+[\\s\\u00A0]*€").findAll(antwort.text).map { it.value.trim() }.toList()
            assertTrue("Die Antwort auf \"$frage\" enthält keinen Betrag: ${antwort.text}", betrage.isNotEmpty())
            betrage.forEach { gefunden ->
                assertTrue(
                    "Betrag $gefunden in der Antwort auf \"$frage\" steht nicht in den Daten.\n${antwort.text}",
                    gefunden in erlaubt
                )
            }
        }
    }

    // ------------------------------------------------------- 9. Zu wenig Daten

    @Test
    fun zuWenigDatenGibtKeineSchaetzungAb() {
        val app = app()
        runBlocking(Dispatchers.IO) { app.repository.deleteAllData() }
        val s = runBlocking { baue(app) } // leere Datenbank (nur Standardkategorien)
        val antwort = frage(s, "Wo geht mein Geld hin?")
        assertEquals(
            "Ich habe noch nicht genügend Daten für eine zuverlässige Analyse.",
            antwort.text.trim()
        )
        assertTrue(!antwort.hasEnoughData)
    }

    // --------------------------------------------------------- 10. Sprachen

    @Test
    fun antwortetVollstaendigAufDeutsch() { sprache("de") }

    @Test
    fun antwortetVollstaendigAufEnglisch() { sprache("en") }

    @Test
    fun antwortetVollstaendigAufItalienisch() { sprache("it") }

    @Test
    fun antwortetVollstaendigAufAlbanisch() { sprache("sq") }

    @Test
    fun antwortetVollstaendigAufTuerkisch() { sprache("tr") }

    /** Wörter, die in einer vollständigen Antwort der Sprache stehen müssen. */
    private val SPRACHWOERTER = mapOf(
        "de" to listOf("Ausgaben", "Einnahmen"),
        "en" to listOf("Expenses", "Income"),
        "it" to listOf("Uscite", "Entrate"),
        "sq" to listOf("Shpenzime", "Të ardhura"),
        "tr" to listOf("Gider", "Gelir")
    )

    /**
     * Dieselbe Frage wird in jeder Sprache gestellt - die Antwort muss
     * vollstaendig in der eingestellten Sprache stehen, ohne Mischung.
     */
    private fun sprache(code: String) {
        AppLocale.save(app(), code)
        val s = zusammenfassung()
        val muss = SPRACHWOERTER[code] ?: error("unbekannte Sprache $code")
        val fremd = SPRACHWOERTER.filterKeys { it != code }.values.flatten()

        for (frageText in listOf("Wo geht mein Geld hin?", "Analysiere meinen Monat.")) {
            val antwort = frage(s, frageText)
            assertTrue("Antwort ist leer (Sprache $code)", antwort.text.isNotBlank())
            muss.forEach { wort ->
                assertTrue(
                    "'$wort' fehlt in der $code-Antwort auf \"$frageText\": ${antwort.text}",
                    antwort.text.contains(wort, ignoreCase = true)
                )
            }
            fremd.forEach { wort ->
                assertTrue(
                    "Sprachmischung: '$wort' steht in der $code-Antwort auf \"$frageText\": ${antwort.text}",
                    !antwort.text.contains(wort, ignoreCase = true)
                )
            }
        }
    }

    // ------------------------------------------------- 11. Schreibschutz

    @Test
    fun kiVeraendertKeineDaten() {
        val app = app()
        datenAnlegen(app)
        val vorher = zustand(app.repository)
        for (frage in ALLE_FRAGEN) {
            val s = runBlocking { baue(app) }
            frage(s, frage)
        }
        val nachher = zustand(app.repository)
        assertEquals("Ausgaben wurden verändert", vorher.erste, nachher.erste)
        assertEquals("Anzahl Ausgaben verändert", vorher.zweite, nachher.zweite)
        assertEquals("Summe Ausgaben verändert", vorher.dritte, nachher.dritte)
        assertEquals("Rechnungen verändert", vorher.vierte, nachher.vierte)
    }

    @Test
    fun kiSchichtHatKeinenSchreibzugriff() {
        val wurzel = File(System.getProperty("user.dir")).resolve("src/main/java/com/finovo/app/ai")
        if (!wurzel.isDirectory) {
            println("Hinweis: KI-Quelltext nicht gefunden - Prüfung übersprungen.")
            return
        }
        val verboten = Regex(
            "\\.(addExpense|addIncome|addBill|addCategory|addArea|addRecurringRule|" +
                "delete|update|insert|upsert|wipe|saveGoal|saveSubscription|markBillPaid|depositToGoal)\\s*\\("
        )
        val faelle = mutableListOf<String>()
        wurzel.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { datei ->
            datei.readLines().forEachIndexed { nr, zeile ->
                val code = zeile.substringBefore("//")
                if (verboten.containsMatchIn(code)) {
                    faelle.add("${datei.name}:${nr + 1} ${zeile.trim()}")
                }
            }
        }
        assertTrue(
            "Die KI-Schicht darf nicht schreiben:\n" + faelle.joinToString("\n"),
            faelle.isEmpty()
        )
    }

    // ------------------------------------- 12. Externer Dienst nicht verfügbar

    @Test
    fun externerDienstNichtErreichbarFaelltZurueck() {
        AiServiceLayer.provider = HttpRemoteAiProvider("https://unreachable.invalid.example", "schluessel")
        AiServiceLayer.mode = com.finovo.app.ai.AiMode.REMOTE_IF_CONSENTED
        AiServiceLayer.consentGiven = true

        val s = zusammenfassung()
        val antwort = runBlocking {
            AiServiceLayer.answer(s, "Wo geht mein Geld hin?", "de")
        }
        // Kein Absturz, keine leere Antwort - sondern die lokale Antwort.
        assertEquals(AiSource.LOCAL, antwort.source)
        assertTrue(antwort.text, antwort.text.contains(Money.format(ausgabenMonat, symbol)))
    }

    @Test
    fun ohneZustimmungBleibtAllesLokal() {
        AiServiceLayer.provider = HttpRemoteAiProvider("https://unreachable.invalid.example", "schluessel")
        AiServiceLayer.mode = com.finovo.app.ai.AiMode.REMOTE_IF_CONSENTED
        AiServiceLayer.consentGiven = false
        val s = zusammenfassung()
        val antwort = runBlocking { AiServiceLayer.answer(s, "Welche Rechnungen sind offen?", "de") }
        assertEquals(AiSource.LOCAL, antwort.source)
        assertTrue(!AiServiceLayer.sendsDataExternally())
    }

    @Test
    fun transparentZuDenGesendetenDaten() {
        val s = zusammenfassung()
        val offenlegung = AiServiceLayer.disclosure(s, "Wo geht mein Geld hin?")
        assertTrue(offenlegung.contains("expenseCents"))
        // Keine Kontodaten, keine Zugangsdaten
        listOf("iban", "password", "pin", "token", "konto", "bank").forEach { wort ->
            assertTrue(
                "Die Übertragung enthält '$wort': $offenlegung",
                !offenlegung.contains(wort, ignoreCase = true)
            )
        }
    }

    // ------------------------------------------------- 13. Oberfläche

    @Test
    fun bildschirmAufDeutsch() { oberflaeche("de") }

    @Test
    fun bildschirmAufEnglisch() { oberflaeche("en") }

    @Test
    fun bildschirmAufItalienisch() { oberflaeche("it") }

    @Test
    fun bildschirmAufAlbanisch() { oberflaeche("sq") }

    @Test
    fun bildschirmAufTuerkisch() { oberflaeche("tr") }

    private fun oberflaeche(code: String) {
        val app = app()
        datenAnlegen(app)
        AppLocale.save(app, code)
        val vm = AppViewModel(app)
        var generation by mutableStateOf(0)

        compose.setContent {
            val context = LocalContext.current
            val umschlagen = remember(code) { AppLocale.wrap(context, code) }
            CompositionLocalProvider(LocalContext provides umschlagen) {
                MaterialTheme { androidx.compose.runtime.key(code, generation) { FinovoAiScreen(vm) {} } }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { generation = 1 }
        compose.waitForIdle()

        // Titel und Untertitel sind übersetzt vorhanden
        assertTrue(
            "Finovo AI fehlt (Sprache $code)",
            compose.onAllNodesWithText("Finovo AI").fetchSemanticsNodes().isNotEmpty()
        )
        // Datenschutzhinweis sichtbar
        assertTrue(
            "Datenschutz-Hinweis fehlt (Sprache $code)",
            compose.onAllNodesWithText(
                text(code, "ai_privacy_title", "Datenschutz"), substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        )
        // Der erwartete Betrag wird in der gerade eingestellten Sprache formatiert
        val erwarteterBetrag = Money.format(ausgabenMonat, symbol)
        // Schnellfrage antippen -> echte Antwort mit echtem Betrag erscheint
        val chip = text(code, "ai_quick_where", "Wo geht mein Geld hin?")
        val treffer = compose.onAllNodesWithText(chip, substring = true, ignoreCase = true).fetchSemanticsNodes()
        assertTrue("Schnellfrage '$chip' fehlt (Sprache $code)", treffer.isNotEmpty())
        compose.onAllNodesWithText(chip, substring = true, ignoreCase = true)[0].performClick()
        warteBis {
            compose.onAllNodesWithText(erwarteterBetrag, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Die Antwort enthält nicht den echten Betrag $erwarteterBetrag (Sprache $code)",
            compose.onAllNodesWithText(erwarteterBetrag, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    // ------------------------------------------------------------------ Hilfen

    private fun app(): FinovoApp = ApplicationProvider.getApplicationContext()

    private fun frage(s: FinancialSummary, text: String) =
        FinovoAiEngine.answer(s, text, AppLocale.current())

    private fun strOhneDatum(): String = str("ai_no_due_date", "ohne Fälligkeitsdatum")

    private fun str(schluessel: String, fallback: String): String =
        com.finovo.app.util.str(schluessel, fallback)

    private fun text(code: String, schluessel: String, fallback: String): String {
        val res = AppLocale.wrap(app(), code).resources
        val id = res.getIdentifier(schluessel, "string", app().packageName)
        return if (id != 0) res.getString(id) else fallback
    }

    private fun zusammenfassung(): FinancialSummary {
        val app = app()
        datenAnlegen(app)
        return runBlocking { baue(app) }
    }

    private suspend fun baue(app: FinovoApp): FinancialSummary =
        FinancialSummaryService.build(app.repository, LocalDate.now(), "€")

    /** Echte Testdaten in der echten Datenbank. */
    private fun datenAnlegen(app: FinovoApp) {
        runBlocking(Dispatchers.IO) {
            val r = app.repository
            val schonDa = r.expenses.firstOrNull().orEmpty().any { it.note.startsWith("AI-") }
            if (!schonDa) {
                schreibeDaten(r)
            }
        }
    }

    private suspend fun schreibeDaten(r: Repository) {
        val katMiete = r.addCategory("Miete", "\uD83C\uDFE0", CategoryType.EXPENSE, isFixed = true)
        val katLebensmittel = r.addCategory("Lebensmittel", "\uD83D\uDED2", CategoryType.EXPENSE)
        val katAuto = r.addCategory("Auto", "\uD83D\uDE97", CategoryType.EXPENSE)
        val katVersicherung = r.addCategory("Versicherung", "\uD83D\uDEE1", CategoryType.EXPENSE, isFixed = true)
        val katGehalt = r.addCategory("Gehalt", "\uD83D\uDCB0", CategoryType.INCOME)

        val jahr = heute.year
        val monat = heute.month
        fun tag(t: Int): Long = LocalDate.of(jahr, monat, t.coerceAtMost(28)).toEpochDay()
        val vormonat = heute.minusMonths(1)

        r.addExpense(ExpenseEntity(id = 0, amountCents = mieteBetrag, categoryId = katMiete, date = tag(1), note = "AI-Miete"))
        r.addExpense(ExpenseEntity(id = 0, amountCents = lebensmittelBetrag, categoryId = katLebensmittel, date = tag(3), note = "AI-Supermarkt"))
        r.addExpense(ExpenseEntity(id = 0, amountCents = autoBetrag, categoryId = katAuto, date = tag(5), note = "AI-Tanken"))
        r.addExpense(ExpenseEntity(id = 0, amountCents = versicherungBetrag, categoryId = katVersicherung, date = tag(7), note = "AI-Versicherung"))
        r.addIncome(IncomeEntity(id = 0, amountCents = einnahmenMonat, categoryId = katGehalt, date = tag(1), note = "AI-Gehalt"))

        r.addExpense(
            ExpenseEntity(
                id = 0, amountCents = ausgabenVormonat, categoryId = katMiete,
                date = LocalDate.of(vormonat.year, vormonat.month, 5).toEpochDay(), note = "Miete"
            )
        )
        r.addIncome(
            IncomeEntity(
                id = 0, amountCents = einnahmenMonat, categoryId = katGehalt,
                date = LocalDate.of(vormonat.year, vormonat.month, 1).toEpochDay(), note = "Gehalt"
            )
        )

        r.addBill(
            BillEntity(
                id = 0, name = "Stromabschlag", amountCents = offenStrom,
                dueDate = heute.plusDays(3).toEpochDay(), categoryId = katVersicherung
            )
        )
        r.addBill(
            BillEntity(
                id = 0, name = "Handyrechnung", amountCents = offenHandy,
                dueDate = heute.minusDays(5).toEpochDay(), categoryId = katVersicherung
            )
        )
        r.addBill(
            BillEntity(
                id = 0, name = "Internet", amountCents = 3_990L,
                dueDate = heute.minusDays(20).toEpochDay(),
                categoryId = katVersicherung, status = BillStatus.PAID.name
            )
        )
        r.addRecurringRule(
            RecurringRuleEntity(
                id = 0, kind = RecurringKind.EXPENSE.name, name = "Netflix",
                amountCents = wiederkehrendMonat, categoryId = katAuto,
                frequency = Frequency.MONTHLY.name, interval = 1,
                startDate = heute.plusDays(2).toEpochDay(),
                dayOfMonth = heute.dayOfMonth.coerceAtMost(28)
            )
        )
    }

    /** Alle Beträge, die in einer Antwort vorkommen dürfen. */
    private fun erlaubteBetraege(s: FinancialSummary): Set<String> {
        val werte = mutableListOf<Long>()
        werte += s.month.incomeCents
        werte += s.month.expenseCents
        werte += s.month.remainingCents
        werte += s.month.fixedCents
        werte += s.month.variableCents
        s.month.categories.forEach { c ->
            werte += c.cents
            werte += c.cents / 10L
        }
        s.merchants.forEach { werte += it.cents }
        s.openBills.forEach { werte += it.amountCents }
        s.overdueBills.forEach { werte += it.amountCents }
        werte += s.openBillsTotalCents
        s.recurring.forEach { r ->
            werte += r.monthlyCents
            werte += r.monthlyCents * 12L
        }
        werte += s.recurringMonthlyCents
        s.upcoming.forEach { werte += it.amountCents }
        werte += s.upcoming.sumOf { it.amountCents }
        s.previousMonth?.let { v ->
            werte += v.incomeCents
            werte += v.expenseCents
            werte += v.remainingCents
            werte += kotlin.math.abs(s.month.expenseCents - v.expenseCents)
        }
        s.projectedMonthExpenseCents()?.let { werte += it }
        s.budget?.let { b ->
            werte += b.limitCents
            werte += b.usedCents
            werte += b.remainingCents
        }
        return werte.map { Money.format(it, symbol) }.toSet()
    }

    private data class Zustand(
        val erste: List<String>,
        val zweite: Int,
        val dritte: Long,
        val vierte: Int
    )

    private fun zustand(r: Repository): Zustand = runBlocking(Dispatchers.IO) {
        val ausgaben = r.expenses.firstOrNull() ?: emptyList()
        val rechnungen = r.bills.firstOrNull() ?: emptyList()
        Zustand(
            erste = ausgaben.map { "${it.id}:${it.amountCents}:${it.date}:${it.note}" },
            zweite = ausgaben.size,
            dritte = ausgaben.sumOf { it.amountCents },
            vierte = rechnungen.size
        )
    }

    private fun warteBis(dauerMs: Long = 12_000, bedingung: () -> Boolean) {
        val ende = System.currentTimeMillis() + dauerMs
        while (System.currentTimeMillis() < ende && !bedingung()) {
            compose.waitForIdle()
            Thread.sleep(25)
        }
        compose.waitForIdle()
    }

    companion object {
        val ALLE_FRAGEN = listOf(
            "Wo geht mein Geld hin?",
            "Wie kann ich sparen?",
            "Welche Rechnungen sind offen?",
            "Was muss ich bald bezahlen?",
            "Analysiere meinen Monat.",
            "Vergleiche diesen Monat mit dem letzten Monat.",
            "Welche Zahlungen sind wiederkehrend?"
        )
    }
}

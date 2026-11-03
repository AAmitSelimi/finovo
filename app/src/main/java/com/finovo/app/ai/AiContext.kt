package com.finovo.app.ai

import com.finovo.app.util.AppLocale
import com.finovo.app.util.Money
import com.finovo.app.util.str
import java.util.Locale

/**
 * Modul 27: Das Gesprächsgedächtnis von Finovo AI.
 *
 * Der Nutzer darf in mehreren Schritten fragen, ohne alles zu wiederholen:
 *
 *   "Wie viel habe ich diesen Monat ausgegeben?"
 *   "Und wie viel davon für Auto?"
 *   "Was war die größte Ausgabe?"
 *
 * Dafür merkt sich diese Klasse die letzte Frage, die erkannte Absicht und
 * die zuletzt genannte Kategorie. Rückfragen werden damit aufgelöst - in
 * allen fünf Sprachen.
 */
class AiContext {

    private var letzteFrage: String = ""
    private var letzteAbsicht: AiIntent = AiIntent.GENERAL
    private var letzteKategorie: String? = null
    private var letzterMonat: String? = null
    private var anzahlFragen: Int = 0

    /** Nach jeder Antwort aufrufen. */
    fun merken(frage: String, absicht: AiIntent, kategorie: String?, monat: String?) {
        letzteFrage = frage
        letzteAbsicht = absicht
        if (!kategorie.isNullOrBlank()) letzteKategorie = kategorie
        if (!monat.isNullOrBlank()) letzterMonat = monat
        anzahlFragen++
    }

    fun zuruecksetzen() {
        letzteFrage = ""
        letzteAbsicht = AiIntent.GENERAL
        letzteKategorie = null
        letzterMonat = null
        anzahlFragen = 0
    }

    val fragenAnzahl: Int get() = anzahlFragen
    val gemerkteKategorie: String? get() = letzteKategorie
    val gemerkterMonat: String? get() = letzterMonat

    /** Ist die aktuelle Frage eine Rückfrage (Bezug auf das Vorherige)? */
    fun istFortsetzung(frage: String, sprache: String = AppLocale.current()): Boolean {
        val q = frage.lowercase(Locale.ROOT).trim()
        val woerter = when (sprache) {
            "en" -> listOf("and ", "what about", "about that", "of that", "of it", "how much of")
            "it" -> listOf("e ", "e per", "di questo", "di questi", "quanto di", "quale")
            "sq" -> listOf("dhe ", "e ", "nga kjo", "sa nga", "prej saj")
            "tr" -> listOf("ve ", "ya ", "bundan", "bundan ne kadar", "peki ya")
            else -> listOf("und ", "und wie", "davon", "dafür", "wie viel davon", "dazu")
        }
        return woerter.any { q.contains(it) } || q.startsWith("und ")
    }

    /**
     * Löst eine Rückfrage auf: erkennt "Und wie viel davon für Auto?" als
     * Frage nach dem Betrag einer Kategorie.
     */
    fun aufloesen(frage: String): Aufgeloest {
        val q = frage.lowercase(Locale.ROOT).trim()
        var absicht: AiIntent? = null
        var kategorie: String? = null

        // Größte Ausgabe?
        if (GRÖSSTE.any { q.contains(it) }) {
            absicht = AiIntent.LARGEST_EXPENSE
        }

        // "… für Auto" / "… for food" / "… per l'auto" -> Kategorie
        val kategorieTreffer = KATEGORIE_MARKER.firstNotNullOfOrNull { marker ->
            if (q.contains(marker)) {
                q.substringAfter(marker).trim()
                    .trim('?', '!', '.', ' ', '¿')
                    .takeIf { it.isNotBlank() && it.length <= 40 }
            } else null
        }
        if (kategorieTreffer != null) {
            kategorie = kategorieTreffer
            if (absicht == null) absicht = AiIntent.CATEGORY_AMOUNT
        }

        // "davon"/"dafür" ohne Kategorie -> Betrag des Vormonats/der letzten Antwort
        if (absicht == null && istFortsetzung(frage)) {
            absicht = when (letzteAbsicht) {
                AiIntent.WHERE_MONEY_GOES, AiIntent.MONTH_ANALYSIS,
                AiIntent.TOTAL_SPENT, AiIntent.MONTH_COMPARISON -> AiIntent.CATEGORY_AMOUNT

                else -> AiIntent.MONTH_ANALYSIS
            }
        }

        return Aufgeloest(
            absicht = absicht,
            kategorie = kategorie ?: if (absicht == AiIntent.CATEGORY_AMOUNT) letzteKategorie else null,
            bezugAufLetzte = istFortsetzung(frage)
        )
    }

    data class Aufgeloest(
        val absicht: AiIntent?,
        val kategorie: String?,
        val bezugAufLetzte: Boolean
    )

    private companion object {
        val GRÖSSTE = listOf(
            "größte ausgabe", "groesste ausgabe", "größte einzelne", "größten ausgabe",
            "teuerste", "höchste ausgabe", "größte posten",
            "biggest expense", "largest expense", "biggest spending", "highest expense",
            "spesa più grande", "spesa maggiore", "uscita più grande",
            "shpenzimi më i madh", "më e madhja",
            "en büyük harcama", "en yüksek harcama", "en büyük gider"
        )

        /** Markierungen, nach denen eine Kategorie genannt wird. */
        val KATEGORIE_MARKER = listOf(
            "für ", "fuer ", "davon für", "von der kategorie",
            "for ", "of that for ", "from category ",
            "per ", "per la categoria ", "della categoria ",
            "për ", "nga kategoria ",
            "için ", "kategorisi "
        )
    }
}

/**
 * Modul 28: Befehle an Finovo AI.
 *
 * Der Nutzer kann Finovo AI bitten, etwas anzulegen - zum Beispiel
 * "Add 50 € fuel expense." Vor jeder Änderung an echten Daten zeigt die App
 * eine Bestätigung; ohne Bestätigung wird nichts gespeichert.
 */
object AiCommands {

    sealed class Befehl {
        data class Ausgabe(val cents: Long, val bezeichnung: String, val kategorie: String?) : Befehl()
        data class Einnahme(val cents: Long, val bezeichnung: String, val kategorie: String?) : Befehl()
        data class Sparziel(val cents: Long, val name: String) : Befehl()
        data class Budget(val cents: Long, val kategorie: String?) : Befehl()
        object ZeigeVormonat : Befehl()
        object ZeigeMonat : Befehl()
    }

    /** Erkennt einen Befehl - null, wenn es eine normale Frage ist. */
    fun erkennen(frage: String): Befehl? {
        val q = frage.lowercase(Locale.ROOT).trim()
        val cents = findeBetrag(frage)

        // Zeige-Befehle
        if (ZEIGE_VORMONAT.any { q.contains(it) }) return Befehl.ZeigeVormonat
        if (ZEIGE_MONAT.any { q.contains(it) }) return Befehl.ZeigeMonat

        if (cents == null) return null

        // Sparziel
        if (ZIEL.any { q.contains(it) }) {
            return Befehl.Sparziel(cents, nameNach(q, ZIEL_WORT))
        }
        // Budget
        if (BUDGET.any { q.contains(it) }) {
            return Befehl.Budget(cents, kategorieAus(q))
        }
        // Einnahme
        if (EINNAHME.any { q.contains(it) }) {
            return Befehl.Einnahme(cents, nameNach(q, EINNAHME_WORT), kategorieAus(q))
        }
        // Ausgabe
        if (AUSGABE.any { q.contains(it) }) {
            return Befehl.Ausgabe(cents, nameNach(q, AUSGABE_WORT), kategorieAus(q))
        }
        return null
    }

    /** Bestätigungstext für den Nutzer - in seiner Sprache. */
    fun bestaetigung(befehl: Befehl, symbol: String): String = when (befehl) {
        is Befehl.Ausgabe -> str(
            "ai_cmd_confirm_expense",
            "Soll ich %1\$s für „%2\$s" + "“ als Ausgabe speichern?",
            Money.format(befehl.cents, symbol), befehl.bezeichnung.ifBlank { "–" }
        )

        is Befehl.Einnahme -> str(
            "ai_cmd_confirm_income",
            "Soll ich %1\$s für „%2\$s" + "“ als Einnahme speichern?",
            Money.format(befehl.cents, symbol), befehl.bezeichnung.ifBlank { "–" }
        )

        is Befehl.Sparziel -> str(
            "ai_cmd_confirm_goal",
            "Soll ich das Sparziel „%1\$s“ über %2\$s anlegen?",
            befehl.name.ifBlank { "–" }, Money.format(befehl.cents, symbol)
        )

        is Befehl.Budget -> str(
            "ai_cmd_confirm_budget",
            "Soll ich ein Monatsbudget von %1\$s%2\$s festlegen?",
            Money.format(befehl.cents, symbol),
            befehl.kategorie?.let { " für „$it“" } ?: ""
        )

        is Befehl.ZeigeVormonat, is Befehl.ZeigeMonat -> ""
    }

    /** Findet den ersten Geldbetrag im Text (deutsche und englische Formate). */
    private fun findeBetrag(text: String): Long? {
        val muster = Regex("""\d{1,3}(?:[.,\s]\d{3})*(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?""")
        for (treffer in muster.findAll(text)) {
            val wert = com.finovo.app.util.Money.parseToCents(treffer.value)
            if (wert != null && wert > 0) return wert
        }
        // Fallback: Zahl mit € / EUR / USD …
        val mitWaehrung = Regex("""([\d.,\s]+)\s*(€|eur|usd|gbp|chf|all|euro)""", RegexOption.IGNORE_CASE)
        val roh = mitWaehrung.find(text)?.groupValues?.getOrNull(1)
        if (roh != null) {
            val wert = com.finovo.app.util.Money.parseToCents(roh.trim())
            if (wert != null && wert > 0) return wert
        }
        return null
    }

    /** Text nach einem Schlüsselwort (Bezeichnung des Befehls). */
    private fun nameNach(q: String, woerter: List<String>): String {
        for (w in woerter) {
            if (q.contains(w)) {
                return q.substringAfter(w).trim().trim('?', '!', '.').take(60)
            }
        }
        return ""
    }

    /** Kategorie aus dem Text (z. B. "budget of 500 € for food" -> "food"). */
    private fun kategorieAus(q: String): String? {
        for (marker in listOf("für ", "fuer ", "for ", "per ", "për ", "için ")) {
            if (q.contains(marker)) {
                val wert = q.substringAfterLast(marker).trim().trim('?', '!', '.')
                if (wert.isNotBlank() && wert.length <= 40 && !wert.any { it.isDigit() }) {
                    return wert
                }
            }
        }
        return null
    }

    private val AUSGABE = listOf(
        "add expense", "add a expense", "book expense", "record expense", "log expense",
        "füge eine ausgabe hinzu", "buche", "buche eine ausgabe", "neue ausgabe",
        "speichere eine ausgabe", "ausgabe von", "ausgabe hinzufügen",
        "aggiungi una spesa", "registra una spesa", "nuova spesa",
        "shto një shpenzim", "shpenzim i ri",
        "yeni gider", "gider ekle", "harcama ekle"
    )
    private val EINNAHME = listOf(
        "add income", "record income", "add salary", "income of",
        "einnahme", "gehalt", "neue einnahme", "einnahme hinzufügen", "buche einnahme",
        "aggiungi un'entrata", "nuova entrata", "registra un'entrata",
        "të ardhura", "e ardhur e re", "shto të ardhura",
        "yeni gelir", "gelir ekle", "maaş ekle"
    )
    private val ZIEL = listOf(
        "sparkonto", "sparziel", "savings goal", "create a goal", "new goal",
        "ziel anlegen", "ziel erstellen", "neues ziel",
        "obiettivo di risparmio", "nuovo obiettivo", "crea un obiettivo",
        "synëm kursimi", "objektiv i ri", "krijo një synim",
        "tasarruf hedefi", "yeni hedef", "hedef oluştur"
    )
    private val BUDGET = listOf(
        "budget", "monatsbudget", "limit", "ausgabenlimit",
        "budget of", "create a budget", "monthly budget",
        "bilancio", "budget mensile", "limite di spesa",
        "buxhet", "buxhet mujor", "limit shpenzimi",
        "bütçe", "aylık bütçe", "harcama limiti"
    )
    private val ZEIGE_VORMONAT = listOf(
        "ausgaben vom letzten monat", "ausgaben des letzten monats", "letzten monat zeigen",
        "expenses from last month", "show last month", "last month expenses",
        "spese del mese scorso", "mostra il mese scorso",
        "shpenzimet e muajit të kaluar", "shfaq muajin e kaluar",
        "geçen ayın giderleri", "geçen ayı göster"
    )
    private val ZEIGE_MONAT = listOf(
        "ausgaben diesen monat", "diesen monat zeigen", "aktuellen monat zeigen",
        "expenses this month", "show this month", "this month expenses",
        "spese di questo mese", "mostra questo mese",
        "shpenzimet e këtij muaji", "shfaq këtë muaj",
        "bu ayın giderleri", "bu ayı göster"
    )
    private val AUSGABE_WORT = listOf("für ", "fuer ", "for ", "per ", "për ", "için ", "von ")
    private val EINNAHME_WORT = listOf("für ", "fuer ", "for ", "per ", "për ", "için ", "von ")
    private val ZIEL_WORT = listOf("für ", "fuer ", "for ", "per ", "për ", "için ")
}

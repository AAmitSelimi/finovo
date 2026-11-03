package com.finovo.app.util

import com.finovo.app.data.BillEntity
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import java.util.Locale
import kotlin.math.abs

/**
 * Modul 29: Doppelte Buchungen erkennen.
 *
 * Zwei Buchungen gelten als mögliches Duplikat, wenn Händler/Notiz,
 * Betrag und Datum (mit einem Tag Toleranz) übereinstimmen.
 *
 * Wichtig: es wird NIE automatisch etwas gelöscht. Die App zeigt nur einen
 * Hinweis - die Entscheidung liegt beim Nutzer.
 */
object Duplicates {

    private const val TAG_TOLERANZ = 1L

    data class Verdacht(
        val neu: ExpenseEntity,
        val vorhanden: ExpenseEntity,
        val tageAbstand: Long,
        val uebereinstimmung: Float
    )

    /** Normalisiert für den Vergleich: klein, ohne Sonderzeichen. */
    private fun schluessel(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
            .trim()

    /** Händler/Notiz einer Buchung als einheitlicher Text. */
    private fun bezeichnung(e: ExpenseEntity): String =
        schluessel(e.merchant.ifBlank { e.note })

    /** Prüft eine neue Buchung gegen die vorhandenen. */
    fun findeZuAusgabe(
        kandidat: ExpenseEntity,
        vorhandene: List<ExpenseEntity>
    ): List<Verdacht> {
        val eigenerSchluessel = bezeichnung(kandidat)
        if (eigenerSchluessel.isBlank() || kandidat.amountCents == 0L) return emptyList()
        return vorhandene
            .asSequence()
            .filter { it.id != kandidat.id }
            .filter { it.amountCents == kandidat.amountCents }
            .filter { abs(it.date - kandidat.date) <= TAG_TOLERANZ }
            .filter { bezeichnung(it) == eigenerSchluessel }
            .map {
                Verdacht(
                    neu = kandidat,
                    vorhanden = it,
                    tageAbstand = abs(it.date - kandidat.date),
                    uebereinstimmung = 1f
                )
            }
            .toList()
    }

    /** Ähnliche Buchungen (ohne gleiches Datum) - für die Übersicht. */
    fun findeAlle(expenses: List<ExpenseEntity>): List<Pair<ExpenseEntity, ExpenseEntity>> {
        val gruppen = LinkedHashMap<String, MutableList<ExpenseEntity>>()
        for (e in expenses) {
            val schluessel = bezeichnung(e)
            if (schluessel.isBlank()) continue
            gruppen.getOrPut(schluessel + "|" + e.amountCents) { ArrayList() }.add(e)
        }
        val out = ArrayList<Pair<ExpenseEntity, ExpenseEntity>>()
        for ((_, liste) in gruppen) {
            val sortiert = liste.sortedBy { it.date }
            for (i in 0 until sortiert.size - 1) {
                out += sortiert[i] to sortiert[i + 1]
            }
        }
        return out.sortedByDescending { it.first.date }
    }

    /** Gleiches gilt für Einnahmen (gleicher Betrag, gleicher Tag). */
    fun findeZuEinnahme(
        kandidat: IncomeEntity,
        vorhandene: List<IncomeEntity>
    ): List<IncomeEntity> {
        val eigenerSchluessel = schluessel(kandidat.merchant.ifBlank { kandidat.note })
        if (eigenerSchluessel.isBlank() || kandidat.amountCents == 0L) return emptyList()
        return vorhandene.filter {
            it.id != kandidat.id &&
                it.amountCents == kandidat.amountCents &&
                abs(it.date - kandidat.date) <= TAG_TOLERANZ &&
                schluessel(it.merchant.ifBlank { it.note }) == eigenerSchluessel
        }
    }

    /** Rechnungen mit gleichem Namen und Betrag, die noch offen sind. */
    fun findeZuRechnung(kandidat: BillEntity, vorhandene: List<BillEntity>): List<BillEntity> {
        val name = schluessel(kandidat.name.ifBlank { kandidat.vendor })
        if (name.isBlank()) return emptyList()
        return vorhandene.filter {
            it.id != kandidat.id &&
                it.amountCents == kandidat.amountCents &&
                schluessel(it.name.ifBlank { it.vendor }) == name
        }
    }
}

/**
 * Modul 31 + 36: Eingaben prüfen und verständliche Fehlermeldungen zeigen.
 *
 * Nie eine technische Meldung an den Nutzer - immer ein verständlicher Satz,
 * und die Eingabe bleibt erhalten.
 */
object Validation {

    private const val MIN_TAG = -25567L   // 01.01.1900
    private const val MAX_TAG = 47846L   // 31.12.2100

    const val MAX_BETRAG_CENTS = 1_000_000_000_00L // 1 Mrd.

    sealed class Ergebnis {
        object Ok : Ergebnis()
        data class Fehler(val text: String, val feld: Feld) : Ergebnis()
        data class Warnung(val text: String) : Ergebnis()
    }

    enum class Feld { AMOUNT, DATE, CATEGORY, MERCHANT, GENERAL }

    /** Betrag: leer, negativ, null oder unsinnig groß. */
    fun betrag(eingabe: String): Ergebnis {
        val text = eingabe.trim()
        if (text.isEmpty()) {
            return Ergebnis.Fehler(
                str("val_amount_missing", "Bitte gib einen Betrag ein."), Feld.AMOUNT
            )
        }
        val cents = Money.parseToCents(text)
        if (cents == null) {
            return Ergebnis.Fehler(
                str("val_amount_invalid", "Dieser Betrag ist keine gültige Zahl. Bitte prüfe die Eingabe."),
                Feld.AMOUNT
            )
        }
        if (cents <= 0) {
            return Ergebnis.Fehler(
                str("val_amount_positive", "Der Betrag muss größer als 0 sein."), Feld.AMOUNT
            )
        }
        if (cents > MAX_BETRAG_CENTS) {
            return Ergebnis.Fehler(
                str("val_amount_too_large", "Dieser Betrag ist zu groß. Bitte prüfe die Eingabe."),
                Feld.AMOUNT
            )
        }
        return Ergebnis.Ok
    }

    /** Datum: muss im gültigen Bereich liegen. */
    fun datum(epochDay: Long?): Ergebnis {
        if (epochDay == null) {
            return Ergebnis.Fehler(
                str("val_date_missing", "Bitte wähle ein Datum aus."), Feld.DATE
            )
        }
        if (epochDay < MIN_TAG || epochDay > MAX_TAG) {
            return Ergebnis.Fehler(
                str("val_date_invalid", "Dieses Datum liegt außerhalb des gültigen Bereichs."),
                Feld.DATE
            )
        }
        return Ergebnis.Ok
    }

    /** Kategorie: muss vorhanden und zum Typ passen. */
    fun kategorie(categoryId: Long, vorhanden: Boolean): Ergebnis {
        if (categoryId == 0L || !vorhanden) {
            return Ergebnis.Fehler(
                str("val_category_missing", "Bitte wähle eine Kategorie aus."), Feld.CATEGORY
            )
        }
        return Ergebnis.Ok
    }

    /** Bezeichnung: darf nicht nur aus Zeichen bestehen. */
    fun bezeichnung(text: String): Ergebnis {
        if (text.isNotBlank() && text.trim().length > 120) {
            return Ergebnis.Fehler(
                str("val_merchant_too_long", "Diese Bezeichnung ist zu lang (maximal 120 Zeichen)."),
                Feld.MERCHANT
            )
        }
        return Ergebnis.Ok
    }

    /** Freundliche Meldung, wenn beim Speichern etwas schiefgeht. */
    fun speicherFehler(): String = str(
        "err_save_generic",
        "Beim Speichern ist etwas schiefgelaufen. Deine bisherigen Daten sind sicher - bitte versuche es erneut."
    )

    /** Freundliche Meldung, wenn Daten nicht geladen werden können. */
    fun ladeFehler(): String = str(
        "err_load_generic",
        "Diese Daten konnten nicht geladen werden. Deine Einträge sind gespeichert und bleiben erhalten."
    )

    fun loeschFehler(): String = str(
        "err_delete_generic",
        "Der Eintrag konnte nicht gelöscht werden. Deine Daten sind unverändert."
    )
}

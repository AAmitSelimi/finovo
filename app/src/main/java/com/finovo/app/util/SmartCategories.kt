package com.finovo.app.util

import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryRuleEntity
import java.util.Locale

/**
 * Modul 12: Smarte Kategorisierung.
 *
 * Drei Stufen, von sicher zu unsicher:
 *
 *   1. Gelernt: der Nutzer hat diesem Händler-Wort schon einmal eine
 *      Kategorie gegeben (Tabelle category_rules).
 *   2. Wörterbuch: bekannte Begriffe (Aldi -> Lebensmittel, Aral -> Tanken …)
 *      in allen fünf Sprachen.
 *   3. Namensvergleich: der Kategoriename steckt im Text.
 *
 * Die KI schlägt nur vor - gespeichert wird, was der Nutzer bestätigt.
 * Jede Bestätigung und jede Korrektur wird gelernt.
 */
object SmartCategories {

    /** Ein Vorschlag mit Begründung und Sicherheit (0..1). */
    data class Vorschlag(
        val categoryId: Long,
        val categoryName: String,
        val confidence: Float,
        val grund: Grund
    ) {
        /** Ab diesem Wert wird der Vorschlag vorausgewählt. */
        val sicher: Boolean get() = confidence >= 0.6f
    }

    enum class Grund { GELERNT, WOERTERBUCH, NAME }

    /**
     * Begriff -> Kategoriename (so wie die Standardkategorien heißen).
     * Bewusst mehrsprachig: Aldi, Lidl, Rewe, auch englische/türkische Begriffe.
     */
    private val WOERTERBUCH: Map<String, List<String>> = mapOf(
        // Lebensmittel / Food
        "lebensmittel" to listOf("aldi", "lidl", "rewe", "edeka", "netto", "kaufland", "penny",
            "supermarkt", "markt", "bäcker", "baecker", "bäckerei", "metzger", "bio", "denns",
            "grocery", "groceries", "food", "market", "bakery", "butcher", "supermercato",
            "alimentari", "ushqimore", "markets", "market", "gida", "market"),
        // Tanken / Fuel
        "tanken" to listOf("aral", "shell", "esso", "bp ", "total", "tankstelle", "tanke", "jet",
            "omv", "avia", "oil", "fuel", "gas station", "benzin", "diesel", "stazione di servizio",
            "carburante", "benzina", "karburant", "piknik", "akaryakıt", "yakıt", "benzin"),
        // Auto / Car
        "auto" to listOf("auto", "werkstatt", "kfz", "pkw", "car", "vehicle", "garage", "macchina",
            "officina", "veturë", "makinë", "araç", "oto"),
        // Miete / Rent
        "miete" to listOf("miete", "wohnung", "vermieter", "hausverwaltung", "rent", "landlord",
            "affitto", "affitto", "qira", "kira"),
        // Strom / Electricity
        "strom" to listOf("strom", "stadtwerke", "elektro", "eon", "vattenfall", "e.on", "energy",
            "electricity", "power", "energia", "elettricità", "energji", "elektrik"),
        // Gas
        "gas" to listOf("gas", "erdgas", "heizöl", "heizoel", "gasag", "natural gas", "heating oil",
            "gas naturale", "gaz", "doğalgaz"),
        // Wasser
        "wasser" to listOf("wasser", "water", "acqua", "ujë", "su"),
        // Internet / Telefon / Handy
        "internet" to listOf("internet", "dsl", "vodafone", "telekom", "o2", "glasfaser", "provider",
            "broadband", "rete", "internet", "telefonia"),
        "handy" to listOf("handy", "mobil", "mobile", "telefon", "phone", "sim", "cellulare",
            "telefono", "celular", "telefon"),
        // Versicherung / Insurance
        "versicherung" to listOf("versicherung", "versich", "allianz", "axa", "huk", "ergo",
            "insurance", "assurance", "assicurazione", "sigurim", "sigorta"),
        // Gesundheit / Health
        "gesundheit" to listOf("apotheke", "arzt", "praxis", "klinik", "zahnarzt", "krank",
            "pharmacy", "doctor", "clinic", "health", "farmacia", "medico", "salute",
            "farmaci", "shëndet", "eczane", "sağlık", "doktor"),
        // Familie / Kinder
        "familie" to listOf("kita", "kindergarten", "schule", "kinder", "baby", "child", "school",
            "scuola", "bambini", "figli", "shkollë", "fëmijë", "okul", "çocuk"),
        // Shopping / Kleidung
        "shopping" to listOf("amazon", "zalando", "otto", "hm ", "h&m", "c&a", "kleidung", "mode",
            "shop", "shopping", "clothes", "clothing", "abbigliamento", "vesti", "rroba",
            "alışveriş", "giyim"),
        // Freizeit / Entertainment
        "freizeit" to listOf("kino", "netflix", "spotify", "konzert", "freizeit", "restaurant",
            "cafe", "café", "bar", "essen", "cinema", "movie", "restaurant", "leisure",
            "ristorante", "bar", "cinema", "restorant", "argëtim", "restoran", "sinema", "eğlence"),
        // Abos
        "abos" to listOf("abo", "abonnement", "subscription", "prime", "mitglied", "membership",
            "abbonamento", "abonim", "abonelik"),
        // Reisen / Travel
        "reisen" to listOf("reise", "urlaub", "hotel", "flug", "bahn", "db ", "train", "flight",
            "travel", "hotel", "viaggio", "volo", "treno", "udhëtim", "hotel", "seyahat", "uçak", "otel"),
        // Kredit / Loan
        "kredit" to listOf("kredit", "darlehen", "rate", "bank", "loan", "credit", "mortgage",
            "prestito", "mutuo", "kredi", "borç"),
        // Gehalt / Salary (Einnahmen)
        "gehalt" to listOf("gehalt", "lohn", "salary", "wage", "payroll", "stipendio", "paga",
            "rroga", "pagë", "maaş", "maas"),
        // Kindergeld / Sonstige Einnahmen
        "kindergeld" to listOf("kindergeld", "child benefit", "assegno", "ndihmë", "çocuk parası"),
        "sonstige einnahmen" to listOf("zinsen", "dividende", "steuer", "rückerstattung",
            "interest", "dividend", "refund", "tax return", "interessi", "interes", "faiz")
    )

    /**
     * Schlägt eine Kategorie vor. [text] ist Händler + Notiz.
     * Liefert null, wenn nichts Belastbares gefunden wurde.
     */
    fun vorschlag(
        text: String,
        categories: List<CategoryEntity>,
        rules: List<CategoryRuleEntity>,
        isIncome: Boolean
    ): Vorschlag? {
        if (text.isBlank() || categories.isEmpty()) return null
        val norm = normalisieren(text)

        // 1) gelernt: längstes passendes Wort gewinnt
        val treffer = rules.filter { it.isIncome == isIncome && it.token.isNotBlank() }
            .filter { norm.contains(normalisieren(it.token)) }
            .sortedByDescending { it.token.length }
            .firstOrNull()
        if (treffer != null) {
            val kategorie = categories.firstOrNull { it.id == treffer.categoryId }
            if (kategorie != null) {
                return Vorschlag(kategorie.id, kategorie.name, 0.95f, Grund.GELERNT)
            }
        }

        // 2) Wörterbuch
        val passend = categories.filter { (isIncome && it.type == "INCOME") || (!isIncome && it.type == "EXPENSE") }
        for ((kategorieName, begriffe) in WOERTERBUCH) {
            val kategorie = passend.firstOrNull { normalisieren(it.name) == kategorieName } ?: continue
            val gefunden = begriffe.firstOrNull { begriff ->
                norm.contains(normalisieren(begriff))
            }
            if (gefunden != null) {
                return Vorschlag(kategorie.id, kategorie.name, 0.8f, Grund.WOERTERBUCH)
            }
        }

        // 3) Kategoriename im Text (z. B. Notiz "Miete September")
        val direkt = passend.filter { it.name.length >= 4 && norm.contains(normalisieren(it.name)) }
            .sortedByDescending { it.name.length }
            .firstOrNull()
        if (direkt != null) {
            return Vorschlag(direkt.id, direkt.name, 0.6f, Grund.NAME)
        }
        return null
    }

    /** Lernschritt: diese Wörter soll die App sich für die Kategorie merken. */
    fun lernwoerter(text: String): List<String> =
        text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}&\\-]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinct()
            .take(6)

    private fun normalisieren(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("'", " ").replace("-", " ")
            .trim()

    /** Unterkategorien einer Kategorie (Modul 12/20). */
    fun unterkategorien(von: List<CategoryEntity>, parentId: Long): List<CategoryEntity> =
        von.filter { it.parentId == parentId }

    fun istUnterkategorie(kategorie: CategoryEntity): Boolean = kategorie.parentId != 0L

    /** Anzeigename inkl. Elternteil: "Auto ▸ Tanken". */
    fun pfad(kategorie: CategoryEntity, alle: List<CategoryEntity>): String {
        if (kategorie.parentId == 0L) return kategorie.name
        val eltern = alle.firstOrNull { it.id == kategorie.parentId } ?: return kategorie.name
        return "${eltern.name} ▸ ${kategorie.name}"
    }
}

package com.finovo.app.bank

/**
 * Rule based classifier for bank transactions.
 *
 * It combines
 *  * static German merchant / purpose keywords,
 *  * the sign of the amount (negative = expense, positive = income) and
 *  * rules the app learned from manual corrections of the user.
 *
 * Every result carries a confidence value. Nothing is ever presented as
 * "certain" when the confidence is low:
 *   >= CONFIDENT   -> sicher erkannt
 *   >= UNCERTAIN   -> "Kategorie prüfen"
 *   <  UNCERTAIN   -> "Nicht kategorisiert"
 */
object TransactionCategorizer {

    const val CONFIDENT = 0.75f
    const val UNCERTAIN = 0.35f

    data class LearnedRule(val categoryName: String, val isIncome: Boolean, val hits: Int)

    data class Suggestion(
        val categoryName: String?,
        val isIncome: Boolean,
        val confidence: Float,
        val reason: String,
        /** Normalised merchant token that can be learned from. */
        val token: String? = null
    ) {
        val isConfident: Boolean get() = categoryName != null && confidence >= CONFIDENT
        val needsReview: Boolean get() = categoryName != null && confidence >= UNCERTAIN && confidence < CONFIDENT
        val isUncategorised: Boolean get() = categoryName == null || confidence < UNCERTAIN
    }

    /** category name -> keywords (merchant names, purposes, typical words) */
    private val EXPENSE_KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "Lebensmittel" to listOf(
            "rewe", "edeka", "aldi", "lidl", "netto", "kaufland", "penny", "real", "tegut",
            "norma", "globus", "marktkauf", "bio company", "denns", "alnatura", "supermarkt",
            "lebensmittel", "baecker", "bäcker", "metzger", "fleischerei", "getraenk",
            "getränkemarkt", "nah und gut", "nah&gut", "hit", "frida", "food", "einkauf markt"
        ),
        "Miete" to listOf(
            "miete", "mietzins", "kaltmiete", "warmmiete", "hausverwaltung", "wohnungsbaugenossenschaft",
            "wohnungsgenossenschaft", "nebenkosten", "hausgeld", "wohngeld", "grundstueck", "grundstücksverwaltung"
        ),
        "Strom" to listOf(
            "stadtwerke", "e.on", "eon", "vattenfall", "enbw", "rwe", "ewe", "lechwerke", "swk",
            "strom", "elektro", "oekostrom", "ökostrom", "naturstrom", "energy", "energie",
            "electricite", "elektrizitaet", "elektrizität"
        ),
        "Gas" to listOf("gas", "fernwaerme", "fernwärme", "heizung", "waerme", "wärme", "erdgas", "fluessiggas", "flüssiggas"),
        "Wasser" to listOf(
            "wasser", "abwasser", "stadtentwaesserung", "stadtentwässerung", "wasserwerk",
            "wasserversorgung", "kanal", "abwassergebuehr", "abwassergebühr", "wasserzweckverband"
        ),
        "Versicherung" to listOf(
            "versicherung", "allianz", "huk", "huk-coburg", "ergo", "axa", "devk", "helvetia",
            "signal iduna", "generali", "nuernberger", "nürnberger", "württembergische", "avd",
            "assekuranz", "provincial", "rheinland versicherung", "berufsunfaehigkeit", "lebensversicherung",
            "haftpflicht", "hausrat", "rechtsschutz", "kfz-versicherung", "krankenversicherung"
        ),
        "Auto" to listOf(
            "kfz", "auto", "werkstatt", "reifen", "tuev", "tüv", "dekra", "inspektion", "pkw",
            "autohaus", "opel", "volkswagen", "vw ", "audi", "bmw", "mercedes", "ford", "renault",
            "toyota", "skoda", "seat", "hyundai", "fiat", "peugeot", "parkhaus", "parkgebuehr", "parkgebühr"
        ),
        "Tanken" to listOf(
            "aral", "shell", "esso", "total", "tanken", "tankstelle", "kraftstoff", "diesel",
            "super e10", "super e5", "benzin", "jet ", "omv", "agip", "eni", "star", "oil",
            "bft", "hem", "raiffeisen tankstelle"
        ),
        "Telefon" to listOf(
            "telekom", "vodafone", "o2", "telefonica", "mobilfunk", "handy", "smartphone",
            "mobilcom", "congstar", "klarmobil", "1&1", "blau.de", "simyo", "otelo", "telefon",
            "festnetz", "callmobile"
        ),
        "Internet" to listOf(
            "internet", "dsl", "glasfaser", "kabel deutschland", "vodafone kabel", "unitymedia",
            "pyur", "1und1", "1&1 internet", "hosting", "domain", "webspace", "cloud",
            "strato", "ionos", "netcup", "magenta", "provider"
        ),
        "Kleidung" to listOf(
            "kleidung", "bekleidung", "mode", "h&m", "zara", "c&a", "primark", "about you",
            "bonprix", "otto", "ernstings", "s Oliver", "s.oliver", "tom tailor", "adidas",
            "nike", "puma", "deichmann", "schuh", "textil", "wäsche", "waesche", "boutique"
        ),
        "Gesundheit" to listOf(
            "apotheke", "arzt", "praxis", "klinik", "krankenhaus", "kranken", "gesundheit",
            "optik", "zahnarzt", "therapie", "physio", "heilpraktiker", "tierarzt", "augenarzt",
            "hno", "orthopaede", "orthopäde", "sanitaetshaus", "sanitätshaus", "medizin", "labor"
        ),
        "Freizeit" to listOf(
            "freizeit", "kino", "cinema", "theater", "museum", "zoo", "schwimmbad", "fitness",
            "mcfit", "fitx", "john reed", "sport", "verein", "bowling", "konzert", "eventim",
            "ticket", "spielhalle", "boulder", "kletter", "sauna", "freibad", "stadion"
        ),
        "Shopping" to listOf(
            "amazon", "zalando", "ebay", "shopping", "versand", "bestellung", "ikea", "moebel",
            "möbel", "mediamarkt", "saturn", "conrad", "notebooksbilliger", "alternate",
            "temu", "shein", "wish", "otto ", "baur", "lidl shop", "galeria", "kaufhof"
        ),
        "Abos" to listOf(
            "netflix", "spotify", "amazon prime", "prime video", "disney", "apple", "icloud",
            "itunes", "youtube premium", "youtube", "sky", "wow", "joyn", "dazn", "audible",
            "kindle", "deezer", "tidal", "patreon", "chatgpt", "openai", "abonnement", "abo ",
            "membership", "mitgliedschaft", "adobe", "microsoft 365", "office 365", "playstation",
            "xbox", "nintendo", "twitch"
        ),
        "Reisen" to listOf(
            "reise", "reisebuero", "reisebüro", "hotel", "hostel", "airbnb", "booking.com",
            "booking", "expedia", "lufthansa", "eurowings", "ryanair", "easyjet", "condor",
            "tui", "dertour", "bahn", "deutsche bahn", "db ", "flixbus", "flixtrain",
            "mietwagen", "sixt", "europcar", "hertz", "avis", "tank und rast", "ferien"
        ),
        "Schule/Kinder" to listOf(
            "kita", "kindergarten", "schule", "hort", "tagespflege", "kinder", "kinderbetreuung",
            "schulbedarf", "schulgeld", "nachhilfe", "buecherei", "bücherei", "spielzeug",
            "babymarkt", "windeln", "kinderarzt", "jugendamt", "ferienbetreuung"
        ),
        "Kredit" to listOf(
            "kredit", "darlehen", "finanzierung", "rate", "annuitaet", "annuität", "hypothek",
            "tilgung", "zinsen darlehen", "baufinanzierung", "leasing"
        )
    )

    /** Keywords that make an incoming payment unmistakable even in the purpose text. */
    private val STRONG_INCOME = setOf(
        "lohn", "gehalt", "salary", "bezuege", "bez\u00fcge", "kindergeld", "rente", "pension",
        "elterngeld"
    )

    /** category name -> keywords that mark an incoming payment */
    private val INCOME_KEYWORDS: List<Pair<String, List<String>>> = listOf(
        "Gehalt" to listOf(
            "lohn", "gehalt", "salary", "entgelt", "bezuege", "bezüge", "verguetung", "vergütung",
            "payroll", "einkommen", "lohnabrechnung", "bonus", "praemie", "prämie", "weihnachtsgeld",
            "urlaubsgeld", "sonderzahlung"
        ),
        "Kindergeld" to listOf("kindergeld", "kinderzuschlag", "elterngeld", "unterhaltsvorschuss", "unterhalt"),
        "Sonstige Einnahmen" to listOf(
            "gutschrift", "erstattung", "steuererstattung", "rueckerstattung", "rückerstattung",
            "zinsen", "dividende", "rente", "pension", "krankengeld", "arbeitslosengeld",
            "wohngeld", "zuschuss", "spende", "auszahlung", "rueckzahlung", "rückzahlung",
            "pfand", "cashback", "verkauf", "ebay verkauft"
        )
    )

    /**
     * Suggests a category for one transaction.
     *
     * @param merchant    counterpart name from the bank (may be empty)
     * @param description booking text / purpose (may be empty)
     * @param amountCents signed amount, negative = outgoing
     * @param learned     rules the user taught the app (token -> category)
     */
    fun suggest(
        merchant: String,
        description: String,
        amountCents: Long,
        learned: Map<String, LearnedRule> = emptyMap()
    ): Suggestion {
        val m = norm(merchant)
        val d = norm(description)
        val both = "$m $d".trim()
        val incoming = amountCents > 0L

        // ---- 1) learned rules have the highest priority -------------------
        val token = tokenOf(merchant, description)
        if (token != null) {
            val rule = learned[token]
            if (rule != null) {
                val confidence = when {
                    rule.hits >= 3 -> 0.92f
                    rule.hits == 2 -> 0.82f
                    else -> 0.6f
                }
                return Suggestion(
                    categoryName = rule.categoryName,
                    isIncome = rule.isIncome,
                    confidence = confidence,
                    reason = if (rule.hits > 1) "aus ${rule.hits} Zuordnungen gelernt" else "einmal zugeordnet – bitte prüfen",
                    token = token
                )
            }
        }

        // ---- 2) income keywords -------------------------------------------
        if (incoming) {
            for ((category, keywords) in INCOME_KEYWORDS) {
                for (k in keywords) {
                    if (m.contains(k)) {
                        return Suggestion(category, true, 0.9f, "Händler „${merchant.trim()}“", token)
                    }
                    if (d.contains(k)) {
                        val confidence = if (k in STRONG_INCOME) 0.82f else 0.72f
                        return Suggestion(category, true, confidence, "Verwendungszweck", token)
                    }
                }
            }
            // incoming money without a keyword stays "sonstige Einnahmen",
            // but never without asking the user
            return Suggestion("Sonstige Einnahmen", true, 0.45f, "eingehender Betrag – bitte prüfen", token)
        }

        // ---- 3) expense keywords (merchant first, then purpose) -----------
        for ((category, keywords) in EXPENSE_KEYWORDS) {
            for (k in keywords) {
                if (m.contains(k)) {
                    return Suggestion(category, false, 0.88f, "Händler „${merchant.trim()}“", token)
                }
            }
        }
        for ((category, keywords) in EXPENSE_KEYWORDS) {
            for (k in keywords) {
                if (d.contains(k)) {
                    return Suggestion(category, false, 0.6f, "Verwendungszweck", token)
                }
            }
        }

        // ---- 4) nothing matched -------------------------------------------
        return Suggestion(null, false, 0f, "nicht erkannt", token)
    }

    /** Builds the token a learned rule is stored under. */
    fun tokenOf(merchant: String, description: String): String? {
        val m = norm(merchant).trim()
        if (m.length >= 4) {
            val cleaned = m
                .replace(Regex("""\b(gmbh|ag|ug|kg|ohg|se|co|und|u|mbh|ev)\b"""), " ")
                .replace(Regex("""[^\p{L}\p{N} ]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (cleaned.length >= 4) return cleaned.take(40)
        }
        val d = norm(description).trim()
            .replace(Regex("""\b\d+\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N} ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        return if (d.length >= 5) d.take(40) else null
    }

    /**
     * True when the same merchant appears in at least two different months
     * with (nearly) the same amount - a recurring payment.
     */
    fun looksRecurring(
        merchant: String,
        amountCents: Long,
        history: List<Pair<Long, Long>> // (epochDay, amountCents) of the same merchant
    ): Boolean {
        if (history.isEmpty()) return false
        val months = HashSet<Long>()
        var similar = 0
        for ((day, cents) in history) {
            months += day / 30
            val diff = kotlin.math.abs(cents - amountCents)
            if (diff <= kotlin.math.abs(amountCents) * 0.05 || diff <= 100) similar++
        }
        return months.size >= 2 && similar >= 1
    }

    internal fun norm(s: String): String = s
        .lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
}

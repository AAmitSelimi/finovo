package com.finovo.app.scan

import java.time.LocalDate
import kotlin.math.abs

/**
 * Eine erkannte Textzeile samt Position auf dem Rechnungsbild.
 *
 * Die Position wird gebraucht, um dem Benutzer im Pruefbildschirm zu zeigen,
 * WO ein Wert herkommt (z. B. die Stelle, aus der der Gesamtbetrag gelesen
 * wurde). Ohne Bild sind alle Werte 0 - dann funktioniert nur das Parsen.
 */
data class OcrLine(
    val text: String,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    val norm: String get() = InvoiceParser.normalize(text)
    val hasBox: Boolean get() = right > left && bottom > top
}

/**
 * Ergebnis der Rechnungserkennung.
 *
 * WICHTIG: Jeder Wert ist nur ein VORSCHLAG mit einer Sicherheit
 * (confidence 0..1). Werte unter [ScannedInvoice.RELIABLE] duerfen nicht
 * stillschweigend gespeichert werden - der Benutzer muss sie pruefen.
 *
 * Der OCR-Rohtext wird niemals direkt in ein Feld geschrieben: erst die
 * Rechnungsanalyse (dieses Objekt) ordnet Textstellen den Feldern zu und
 * bewertet, wie sicher sie ist.
 */
data class ScannedInvoice(
    val rawText: String = "",
    val vendor: String? = null,
    val invoiceNumber: String? = null,
    val invoiceDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val amountCents: Long? = null,
    val netCents: Long? = null,
    val vatCents: Long? = null,
    val iban: String? = null,
    val bic: String? = null,
    val customerNumber: String? = null,
    val categoryName: String? = null,
    val description: String? = null,
    /** Felder, die zuverlässig erkannt wurden (confidence >= RELIABLE). */
    val confident: Set<String> = emptySet(),
    /** Sicherheit je Feld: Feldname -> 0.0 .. 1.0 */
    val confidence: Map<String, Float> = emptyMap<String, Float>(),
    /** Fundstelle je Feld: Feldname -> Textzeile (mit Bildposition). */
    val sources: Map<String, OcrLine> = emptyMap(),
    /** Hinweis je Feld, z. B. warum ein Feld unsicher ist. */
    val notes: Map<String, String> = emptyMap(),
    /** Erkannte Sprache der Rechnung (de/en/it/sq/tr). */
    val language: String = "de"
) {

    fun isConfident(field: String): Boolean = confidenceOf(field) >= RELIABLE

    fun confidenceOf(field: String): Float = confidence[field] ?: 0f

    /** Sicherheit in Prozent, z. B. 98 (fertig für die Anzeige). */
    fun percentOf(field: String): Int = (confidenceOf(field) * 100f).toInt().coerceIn(0, 100)

    fun sourceOf(field: String): OcrLine? = sources[field]

    fun noteOf(field: String): String? = notes[field]

    /** Felder, die der Benutzer auf jeden Fall ansehen muss. */
    fun uncertainFields(): List<String> =
        confidence.entries.filter { it.value < RELIABLE }.map { it.key }.sorted()

    /** Kritische Felder, ohne die eine Rechnung nicht gespeichert werden sollte. */
    fun missingCritical(): List<String> {
        val out = ArrayList<String>()
        if (amountCents == null) out += F_AMOUNT
        if (dueDate == null) out += F_DUE
        if (vendor.isNullOrBlank()) out += F_VENDOR
        return out
    }

    companion object {
        const val F_VENDOR = "vendor"
        const val F_NUMBER = "invoiceNumber"
        const val F_DATE = "invoiceDate"
        const val F_DUE = "dueDate"
        const val F_AMOUNT = "amount"
        const val F_NET = "net"
        const val F_VAT = "vat"
        const val F_IBAN = "iban"
        const val F_BIC = "bic"
        const val F_CUSTOMER = "customerNumber"
        const val F_CATEGORY = "category"
        const val F_DESCRIPTION = "description"

        /** Ab dieser Sicherheit gilt ein Feld als "erkannt". */
        const val RELIABLE = 0.80f

        /** Anzeigenamen der Felder (Reihenfolge wie im Prüfbildschirm). */
        val FIELD_ORDER = listOf(
            F_VENDOR, F_NUMBER, F_DATE, F_DUE, F_AMOUNT, F_NET, F_VAT,
            F_IBAN, F_BIC, F_CUSTOMER, F_CATEGORY, F_DESCRIPTION
        )
    }
}

/**
 * Rechnungsanalyse: macht aus OCR-Rohtext verstandene Felder.
 *
 * Ablauf:  Zeilen -> normalisieren -> Sprache erkennen -> Kandidaten je Feld
 *          sammeln -> bewerten (Label, Formatpruefung, Rechenprobe) ->
 *          Confidence -> Ergebnis.
 *
 * Es wird NIE geraten: findet sich kein eindeutiger Beleg, bleibt das Feld
 * leer oder bekommt eine niedrige Sicherheit und den Hinweis "bitte pruefen".
 */
object InvoiceParser {

    // ------------------------------------------------------------------ API

    /** Einfacher Weg: nur Rohtext (z. B. aus Tests oder gespeicherten Scans). */
    fun parse(text: String): ScannedInvoice {
        val lines = text.lines()
            .map { it.replace('\u00A0', ' ').replace('\t', ' ').trim() }
            .filter { it.isNotBlank() }
        return parse(lines.map { OcrLine(it) })
    }

    /** Vollständiger Weg: Zeilen inklusive Bildposition (für das Markieren). */
    fun parse(lines: List<OcrLine>): ScannedInvoice {
        if (lines.isEmpty()) return ScannedInvoice()

        val doc = Document(lines)
        val language = detectLanguage(doc.fullText)

        val conf = LinkedHashMap<String, Float>()
        val src = LinkedHashMap<String, OcrLine>()
        val notes = LinkedHashMap<String, String>()

        // ---------------- Geldbeträge: erst sammeln, dann bewerten ----------
        val money = collectMoney(doc)
        val netHit = bestOf(money, MoneyKind.NET) ?: bestOf(money, MoneyKind.SUBTOTAL)
        val net = netHit?.cents
        val vat = bestOf(money, MoneyKind.VAT)?.cents
        val total = findTotal(doc, money, net, vat)

        if (total != null) {
            conf[ScannedInvoice.F_AMOUNT] = total.confidence
            src[ScannedInvoice.F_AMOUNT] = total.line
            if (total.note != null) notes[ScannedInvoice.F_AMOUNT] = total.note
        }
        if (netHit != null) {
            conf[ScannedInvoice.F_NET] =
                if (netHit.kind == MoneyKind.NET) 0.92f else 0.80f
            src[ScannedInvoice.F_NET] = netHit.line
        }
        if (vat != null) {
            val hit = bestOf(money, MoneyKind.VAT)!!
            conf[ScannedInvoice.F_VAT] = 0.92f
            src[ScannedInvoice.F_VAT] = hit.line
        }

        // ------------------------------ Termine -----------------------------
        val dates = collectDates(doc)
        val invoiceDate = dates.firstOrNull { it.type == DateType.INVOICE }
        val dueDate = dates.firstOrNull { it.type == DateType.DUE }
        if (invoiceDate != null) {
            conf[ScannedInvoice.F_DATE] = invoiceDate.confidence
            src[ScannedInvoice.F_DATE] = invoiceDate.line
        }
        if (dueDate != null) {
            conf[ScannedInvoice.F_DUE] = dueDate.confidence
            src[ScannedInvoice.F_DUE] = dueDate.line
        } else {
            // Kein eindeutiges Fälligkeitsdatum -> Feld bleibt LEER (nicht raten).
            val term = findPaymentTerm(doc)
            if (term != null) {
                notes[ScannedInvoice.F_DUE] = term
            }
        }

        // ------------------------------ Texte -------------------------------
        val vendor = findVendor(doc)
        if (vendor != null) {
            conf[ScannedInvoice.F_VENDOR] = vendor.confidence
            src[ScannedInvoice.F_VENDOR] = vendor.line
        }

        val number = findLabelledValue(doc, LABELS.number, VALUE_ID)
        if (number != null) {
            conf[ScannedInvoice.F_NUMBER] = number.confidence
            src[ScannedInvoice.F_NUMBER] = number.line
        }

        val customer = findLabelledValue(doc, LABELS.customer, VALUE_ID)
        if (customer != null) {
            conf[ScannedInvoice.F_CUSTOMER] = customer.confidence
            src[ScannedInvoice.F_CUSTOMER] = customer.line
        }

        val iban = findIban(doc)
        if (iban != null) {
            conf[ScannedInvoice.F_IBAN] = iban.confidence
            src[ScannedInvoice.F_IBAN] = iban.line
        }

        val bic = findBic(doc)
        if (bic != null) {
            conf[ScannedInvoice.F_BIC] = bic.confidence
            src[ScannedInvoice.F_BIC] = bic.line
        }

        val category = findCategory(doc.fullText)
        if (category != null) {
            conf[ScannedInvoice.F_CATEGORY] = category.second
        }

        val description = findDescription(doc)
        if (description != null) {
            conf[ScannedInvoice.F_DESCRIPTION] = description.confidence
            src[ScannedInvoice.F_DESCRIPTION] = description.line
        }

        val confident = conf.entries.filter { it.value >= ScannedInvoice.RELIABLE }
            .map { it.key }.toSet()

        return ScannedInvoice(
            rawText = doc.fullText,
            vendor = vendor?.value,
            invoiceNumber = number?.value,
            invoiceDate = invoiceDate?.value,
            dueDate = dueDate?.value,
            amountCents = total?.cents,
            netCents = net,
            vatCents = vat,
            iban = iban?.value,
            bic = bic?.value,
            customerNumber = customer?.value,
            categoryName = category?.first,
            description = description?.value,
            confident = confident,
            confidence = conf,
            sources = src,
            notes = notes,
            language = language
        )
    }

    /** Für die Anzeige: "1.234,56" aus Centbeträgen. */
    fun money(cents: Long?): String {
        if (cents == null) return ""
        val sign = if (cents < 0) "-" else ""
        val v = abs(cents)
        val euros = v / 100
        val rest = v % 100
        val sb = StringBuilder()
        val s = euros.toString()
        var idx = 0
        for (c in s) {
            if ((s.length - idx) % 3 == 0 && idx > 0) sb.append('.')
            sb.append(c)
            idx++
        }
        return "$sign$sb,${
            rest.toString().padStart(2, '0')
        }"
    }

    // ------------------------------------------------------------ Dokument

    private class Document(val lines: List<OcrLine>) {
        val fullText: String = lines.joinToString("\n") { it.text }
        val normLines: List<String> = lines.map { it.norm }
        val size: Int get() = lines.size

        /** Oberes Drittel: hier steht üblicherweise der Absender. */
        val headerEnd: Int = (size * 0.35).toInt().coerceAtLeast(3).coerceAtMost(size)
    }

    // ------------------------------------------------------------- Sprache

    private val LANG_MARKERS = listOf(
        "de" to listOf("rechnung", "gesamtbetrag", "mwst", "rechnungsnummer", "kundennummer",
            "sehr geehrte", "zahlbar", "lieferung", "steuernummer", "ustid", "netto", "brutto"),
        "en" to listOf("invoice", "total", "amount due", "invoice number", "customer", "due date",
            "tax", "net", "gross", "bill to", "payment", "thank you"),
        "it" to listOf("fattura", "totale", "importo", "iva", "numero fattura", "cliente",
            "scadenza", "pagamento", "spettabile", "partita iva", "codice fiscale"),
        "sq" to listOf("fature", "fature", "totali", "shuma", "numri i fatures", "klienti",
            "afati", "pagesa", "tvsh", "date", "nentotal"),
        "tr" to listOf("fatura", "toplam", "tutar", "kdv", "fatura no", "muesteri", "musteri",
            "son oedeme", "vade", "oedeme", "sayin", "vergi", "genel toplam")
    )

    fun detectLanguage(text: String): String {
        val n = normalize(text)
        var best = "de"
        var bestScore = 0
        for ((code, markers) in LANG_MARKERS) {
            val score = markers.count { n.contains(it) }
            if (score > bestScore) {
                bestScore = score
                best = code
            }
        }
        return best
    }

    // -------------------------------------------------------------- Beträge

    private enum class MoneyKind { TOTAL_STRONG, TOTAL_WEAK, NET, VAT, SUBTOTAL, DISCOUNT, UNKNOWN }

    private class MoneyHit(
        val cents: Long,
        val line: OcrLine,
        val kind: MoneyKind,
        val orderInLine: Int
    )

    private val LABELS = Labels()

    private class Labels {
        /** Begriffe, die den tatsächlich zu zahlenden Betrag markieren (stark). */
        val totalStrong = listOf(
            // deutsch
            "gesamtbetrag", "gesamtsumme", "rechnungsbetrag", "rechnungssumme",
            "rechnungsendbetrag", "endbetrag", "endsumme", "gesamtrechnungsbetrag",
            "bruttobetrag", "zahlbetrag", "zu zahlender betrag", "zu zahlender",
            "zu zahlen", "zahlbarer betrag", "zu begleichen", "offener betrag",
            "rechnungsbetrag brutto", "fakturenendbetrag", "forderungen gesamt",
            // englisch
            "total amount", "amount due", "amount payable", "invoice total",
            "total due", "grand total", "please pay", "balance due", "total",
            // italienisch
            "totale fattura", "totale dovuto", "importo totale", "importo da pagare",
            "totale complessivo", "da pagare", "totale",
            // albanisch
            "totali i fatures", "shuma totale", "totali per pagese", "per te paguar",
            "totali", "shuma",
            // türkisch
            "genel toplam", "odenecek tutar", "toplam tutar", "fatura tutari",
            "odenecek", "toplam"
        )
        /** Schwache Treffer - nur, wenn sonst nichts gefunden wird. */
        val totalWeak = listOf("gesamt", "summe", "betrag", "endpreis", "preis gesamt", "amount", "importo")
        val net = listOf(
            "nettobetrag", "nettosumme", "netto", "warenwert", "zwischensumme netto",
            "net amount", "subtotal", "imponibile", "imponibile iva", "nentotali",
            "net tutar", "matrah", "ara toplam"
        )
        val vat = listOf(
            "mwst", "m.w.st", "umsatzsteuer", "ust.", "ust ", "mehrwertsteuer",
            "steuerbetrag", "steuer 19", "steuer 7", "zzgl. mwst", "enthaltene mwst",
            "vat", "tax", "sales tax", "iva", "imposta", "tvsh", "kdv", "vergi"
        )
        val subtotal = listOf("zwischensumme", "subtotal", "summe netto", "subtotale", "nentotal", "ara toplam")
        val discount = listOf("rabatt", "skonto", "nachlass", "gutschrift", "discount",
            "sconto", "zbritje", "indirim", "minderung", "abschlag")

        val dateInvoice = listOf(
            "rechnungsdatum", "rechnung vom", "rechnungsdaten", "ausstellungsdatum",
            "belegdatum", "rechnungs datum", "datum der rechnung",
            "invoice date", "date of invoice", "invoice dated", "issue date", "date",
            "data fattura", "data della fattura", "data emissione", "data",
            "data e fatures", "data leshimit", "data",
            "fatura tarihi", "fatura tarih", "duezenleme tarihi", "tarih"
        )
        val dateDelivery = listOf(
            "lieferdatum", "lieferung vom", "liefertag", "wareneingang",
            "delivery date", "shipped", "data di consegna", "consegna",
            "data e dorezimit", "teslim tarihi", "teslimat"
        )
        val dateService = listOf(
            "leistungsdatum", "leistungszeitraum", "leistung vom", "abrechnungszeitraum",
            "zeitraum", "service date", "period", "periodo", "periudha", "hizmet", "doenem", "donem"
        )
        val dateDue = listOf(
            "faelligkeitsdatum", "faelligkeitsdatum", "faellig am", "faellig am",
            "zahlbar bis", "zahlbar bis zum", "zu zahlen bis", "zahlungsfaellig",
            "faelligkeit", "faelligkeit", "zahlungsziel",
            "due date", "payment due", "due by", "payable by", "pay before",
            "scadenza", "da pagare entro", "entro il", "pagamento entro",
            "afati i pageses", "data e skadimit", "pagesa deri", "afati",
            "son oedeme", "son odeme", "vade tarihi", "vade", "odeme tarihi"
        )
        val number = listOf(
            "rechnungsnummer", "rechnungsnr", "rechnungs-nr", "rechnung nr", "rechnung no",
            "re.-nr", "re.nr", "re nr", "belegnummer", "beleg-nr", "beleg nr",
            "dokumentnummer", "vorgangsnummer", "rechnung",
            "invoice number", "invoice no", "invoice #", "bill number", "invoice",
            "numero fattura", "n. fattura", "nr. fattura", "fattura n", "numero della fattura",
            "numri i fatures", "nr. fatures", "fatura nr",
            "fatura no", "fatura numarasi", "fatura numarasi", "belge no"
        )
        val customer = listOf(
            "kundennummer", "kunden-nummer", "kunden nr", "kunden-nr", "kundennr",
            "kd-nr", "kd.nr", "kd nr", "kunden nr.", "mitgliedsnummer", "vertragsnummer",
            "vertragskonto", "debitor", "kundennummer:", "kunde",
            "customer number", "customer no", "customer id", "client number", "account number",
            "codice cliente", "cliente", "numero cliente",
            "numri i klientit", "klienti", "kodi i klientit",
            "musteri no", "muesteri no", "musteri numarasi", "abone no", "cari no"
        )
        val vendor = listOf(
            "absender", "rechnungssteller", "rechnungsaussteller", "aussteller",
            "lieferant", "verkaeufer", "verkaeufer", "anbieter", "haendler", "haendler",
            "firma", "von:", "auftragnehmer",
            "from", "seller", "supplier", "vendor", "issued by", "service provider",
            "mittente", "fornitore", "emessa da", "ditta", "ragione sociale",
            "dergues", "faturues", "shites", "shitesi",
            "satici", "satici", "fatura eden", "goenderen", "gonderen"
        )
        val description = listOf(
            "betreff", "verwendungszweck", "beschreibung", "leistung", "leistungszeitraum",
            "artikel", "position", "bezeichnung", "vertragsgegenstand", "ihre bestellung",
            "auftrag", "lieferung", "produkt",
            "subject", "description", "item", "service", "reference", "your order",
            "oggetto", "descrizione", "causale", "articolo", "prestazione",
            "pershkrim", "pershkrim", "objekti", "porosia",
            "aciklama", "aciklama", "konu", "hizmet", "siparis"
        )
    }

    private val MONEY_TOKEN = Regex("""[-+]?\d[\d.,'’  ]{0,18}\d|\d""")

    /** Prozentangaben: "19 %", "MwSt. 19%", "KDV %20" -> kein Geldbetrag. */
    private val PERCENT_AFTER = Regex("""\d{1,3}(?:[.,]\d+)?\s*%""")
    private val PERCENT_BEFORE = Regex("""%\s?\d{1,3}(?:[.,]\d+)?""")

    /** Zeilen, aus denen niemals ein Betrag gelesen werden darf. */
    private val NO_MONEY_LINES = listOf(
        "rechnungsnummer", "rechnung nr", "rechnung no", "re.-nr", "invoice no",
        "invoice number", "kundennummer", "kunden nr", "kunden-nr", "kundennr",
        "customer", "telefon", "tel ", "fax", "iban", "bic", "swift",
        "steuernummer", "ustid", "ust-id", "vertragsnummer", "fatura no",
        "numero fattura", "numri i fatures", "belge no", "cari no", "abone no"
    )

    private fun collectMoney(doc: Document): List<MoneyHit> {
        val out = ArrayList<MoneyHit>()
        for (line in doc.lines) {
            val text = line.text
            val n = normalize(text)
            // Zeilen mit Nummern / IBAN / Telefon liefern keine Beträge
            if (containsAny(n, NO_MONEY_LINES) && !containsAny(n, LABELS.totalStrong)) continue
            // Datumsangaben entfernen, damit 15.09.2026 kein Geld wird
            var withoutDates = DATE_PATTERNS.replace(text, " ")
            withoutDates = PERCENT_AFTER.replace(withoutDates, " ")
            withoutDates = PERCENT_BEFORE.replace(withoutDates, " ")
            val kind = when {
                containsAny(n, LABELS.discount) -> MoneyKind.DISCOUNT
                containsAny(n, LABELS.vat) -> MoneyKind.VAT
                containsAny(n, LABELS.net) && !containsAny(n, LABELS.totalStrong) -> MoneyKind.NET
                containsAny(n, LABELS.subtotal) -> MoneyKind.SUBTOTAL
                containsAny(n, LABELS.totalStrong) -> MoneyKind.TOTAL_STRONG
                containsAny(n, LABELS.totalWeak) -> MoneyKind.TOTAL_WEAK
                else -> MoneyKind.UNKNOWN
            }
            var order = 0
            for (m in MONEY_TOKEN.findAll(withoutDates)) {
                val cents = parseMoney(m.value) ?: continue
                if (cents == 0L) continue
                // Keine Rechnung ist 100 Millionen Euro hoch - schützt vor
                // langen Zahlenkolonnen (IBAN, Telefonnummern, Seriennummern)
                if (cents > 10_000_000_000L || cents < -10_000_000_000L) continue
                out += MoneyHit(cents, line, kind, order++)
            }
        }
        return out
    }

    /**
     * Deutsche und internationale Zahlenformate:
     * 1.234,56 € | 123,45 € | 1 234,56 € | 1234,56 EUR | 1'234.56 | 1.234,56
     */
    internal fun parseMoney(raw: String): Long? {
        var s = raw.trim().replace(" ", "").replace("\u00A0", "").replace("'", "").replace("’", "")
        if (s.isEmpty()) return null
        val negative = s.startsWith("-")
        s = s.trimStart('+', '-')
        // Währungen und Buchstaben entfernen: "1.234,56 €", "1234.56 EUR", "84,50 TL"
        s = s.replace(Regex("[^0-9.,]"), "")
        if (s.isEmpty()) return null
        val hasDot = s.contains('.')
        val hasComma = s.contains(',')
        s = when {
            hasDot && hasComma -> {
                val lastDot = s.lastIndexOf('.')
                val lastComma = s.lastIndexOf(',')
                if (lastComma > lastDot) s.replace(".", "").replace(",", ".")
                else s.replace(",", "")
            }
            hasComma -> {
                val tail = s.substringAfterLast(',')
                if (tail.length == 2 || tail.length == 1) s.replace(",", ".")
                else s.replace(",", "")        // 1,234 = Tausender
            }
            hasDot -> {
                val tail = s.substringAfterLast('.')
                if (tail.length == 3 && s.indexOf('.') != s.lastIndexOf('.') || tail.length == 3 && s.length - s.lastIndexOf('.') == 4 && s.indexOf('.') > 0 && s.count { it == '.' } == 1 && s.length > 4 && !s.startsWith("0")) {
                    s.replace(".", "")        // 1.234 -> 1234
                } else s
            }
            else -> s
        }
        val value = s.toDoubleOrNull() ?: return null
        val cents = (value * 100.0).let { if (it >= 0) kotlin.math.floor(it + 0.5) else kotlin.math.ceil(it - 0.5) }
        return (cents.toLong()).let { if (negative) -it else it }
    }

    private fun bestOf(hits: List<MoneyHit>, kind: MoneyKind): MoneyHit? =
        hits.filter { it.kind == kind }.maxByOrNull { it.cents }

    /** Verhindert, dass aus einer beliebigen Zahlenkolonne ein Betrag wird. */
    private fun looksLikeInvoice(doc: Document, money: List<MoneyHit>): Boolean {
        val text = doc.fullText
        if (text.contains("\u20AC") || text.contains("$")) return true
        val n = normalize(text)
        if (containsAny(n, listOf("eur", "euro", "brutto", "netto", "mwst", "umsatzsteuer",
                "total", "summe", "rechnung", "invoice", "fattura", "fatura", "betrag",
                "kasse", "kassenbon", "quittung", "beleg", "zahlbar", "iva", "kdv", "tvsh"))) {
            return true
        }
        return money.size >= 3
    }

    private class TotalResult(
        val cents: Long,
        val confidence: Float,
        val line: OcrLine,
        val note: String? = null
    )

    /**
     * Der zu zahlende Gesamtbetrag.
     *
     * Reihenfolge der Beweise (nie einfach "der groesste betrag"):
     *  1. eindeutiges Label (Gesamtbetrag, Zu zahlen, Total, ...)
     *  2. Rechenprobe Netto + MwSt.
     *  3. Summe der Einzelpositionen
     *  4. größter Betrag - aber nur mit niedriger Sicherheit und Hinweis
     */
    private fun findTotal(
        doc: Document,
        money: List<MoneyHit>,
        net: Long?,
        vat: Long?
    ): TotalResult? {
        if (money.isEmpty()) return null

        // 1) starkes Label
        val strong = money.filter { it.kind == MoneyKind.TOTAL_STRONG }
        if (strong.isNotEmpty()) {
            // Bei Spalten (Netto | MwSt | Brutto) steht der Gesamtbetrag hinten
            val grouped = strong.groupBy { it.line }
            val bestLine = grouped.entries.firstOrNull { it.value.size >= 2 }
            if (bestLine != null) {
                val last = bestLine.value.maxByOrNull { it.orderInLine }!!
                return TotalResult(last.cents, 0.96f, last.line)
            }
            val hit = strong.first()
            val onLine = money.count { it.line === hit.line }
            return TotalResult(hit.cents, if (onLine == 1) 0.96f else 0.90f, hit.line)
        }

        // 2) Rechenprobe: Netto + MwSt.
        if (net != null && vat != null) {
            val expected = net + vat
            val match = money.firstOrNull { abs(it.cents - expected) <= 2 && it.kind != MoneyKind.VAT && it.kind != MoneyKind.NET }
            if (match != null) {
                return TotalResult(match.cents, 0.92f, match.line)
            }
        }

        // 3) Summe der Einzelpositionen
        val items = money.filter { it.kind == MoneyKind.UNKNOWN }
        val candidates = money.filter {
            it.kind == MoneyKind.TOTAL_WEAK || it.kind == MoneyKind.SUBTOTAL || it.kind == MoneyKind.UNKNOWN
        }
        val sum = items.sumOf { it.cents }
        if (items.size >= 2 && sum > 0) {
            val match = candidates.firstOrNull { abs(it.cents - sum) <= 5 }
            if (match != null && match.cents != sum) {
                return TotalResult(match.cents, 0.84f, match.line)
            }
        }

        // 4) schwaches Label (z. B. nur "Betrag" / "Summe")
        val weak = money.filter { it.kind == MoneyKind.TOTAL_WEAK }
        if (weak.isNotEmpty()) {
            val hit = weak.maxByOrNull { it.cents }!!
            return TotalResult(hit.cents, 0.70f, hit.line, "nur_aus_gesamt")
        }

        // 5) größter Betrag - ausdrücklich unsicher und nur, wenn das Dokument
        //    überhaupt wie eine Rechnung aussieht (sonst wird nichts geraten)
        val rest = money.filter { it.kind == MoneyKind.UNKNOWN && it.cents > 0 }
        if (rest.isNotEmpty() && looksLikeInvoice(doc, money)) {
            val hit = rest.maxByOrNull { it.cents }!!
            return TotalResult(hit.cents, 0.50f, hit.line, "groesster_betrag")
        }
        return null
    }

    // ---------------------------------------------------------------- Datum

    private enum class DateType { INVOICE, DUE, DELIVERY, SERVICE, PAYMENT, UNKNOWN }

    private class DateHit(
        val value: LocalDate,
        val type: DateType,
        val confidence: Float,
        val line: OcrLine
    )

    private val DATE_PATTERNS = Regex(
        """(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{2,4})|(\d{4})[-/](\d{1,2})[-/](\d{1,2})"""
    )

    private val MONTH_NAMES = mapOf(
        // deutsch
        "januar" to 1, "jan" to 1, "februar" to 2, "feb" to 2, "maerz" to 3, "maerz" to 3,
        "april" to 4, "apr" to 4, "mai" to 5, "juni" to 6, "jun" to 6, "juli" to 7,
        "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "oktober" to 10, "okt" to 10,
        "november" to 11, "nov" to 11, "dezember" to 12, "dez" to 12,
        // englisch
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11,
        "december" to 12,
        // italienisch
        "gennaio" to 1, "febbraio" to 2, "marzo" to 3, "aprile" to 4, "maggio" to 5,
        "giugno" to 6, "luglio" to 7, "agosto" to 8, "settembre" to 9, "ottobre" to 10,
        "novembre" to 11, "dicembre" to 12,
        // albanisch
        "janar" to 1, "shkurt" to 2, "mars" to 3, "prill" to 4, "maj" to 5, "qershor" to 6,
        "korrik" to 7, "gusht" to 8, "shtator" to 9, "tetor" to 10, "nentor" to 11,
        "nentor" to 11, "dhjetor" to 12,
        // türkisch
        "ocak" to 1, "subat" to 2, "subat" to 2, "mart" to 3, "nisan" to 4, "mayis" to 5,
        "mayis" to 5, "haziran" to 6, "temmuz" to 7, "agustos" to 8, "agustos" to 8,
        "eylul" to 9, "eyluel" to 9, "ekim" to 10, "kasim" to 11, "kasim" to 11,
        "aralik" to 12, "aralik" to 12
    )

    private val DATE_TEXT = Regex(
        """(\d{1,2})[.\s]+([A-Za-zÄÖÜäöüÇçĞğİıŞşŐő]{3,12})[.\s]+(\d{2,4})"""
    )

    private fun collectDates(doc: Document): List<DateHit> {
        val out = ArrayList<DateHit>()
        for (line in doc.lines) {
            val n = line.norm
            // Typ der Zeile: das speziellste Label gewinnt
            val type = when {
                containsAny(n, LABELS.dateDue) -> DateType.DUE
                containsAny(n, LABELS.dateDelivery) -> DateType.DELIVERY
                containsAny(n, LABELS.dateService) -> DateType.SERVICE
                containsAny(n, LABELS.dateInvoice) -> DateType.INVOICE
                else -> DateType.UNKNOWN
            }
            val value = firstDateIn(line.text) ?: continue
            val confidence = when (type) {
                DateType.DUE -> 0.93f
                DateType.INVOICE -> 0.95f
                DateType.DELIVERY -> 0.90f
                DateType.SERVICE -> 0.88f
                DateType.PAYMENT -> 0.85f
                DateType.UNKNOWN -> 0.55f     // nacktes Datum ohne Label: unsicher
            }
            out += DateHit(value, type, confidence, line)
        }
        // Genau ein Datum im Dokument und kein Label -> Rechnungsdatum, aber unsicher
        val labelled = out.filter { it.type != DateType.UNKNOWN }
        if (labelled.isEmpty() && out.size == 1) {
            out[0] = DateHit(out[0].value, DateType.INVOICE, 0.60f, out[0].line)
        }
        return out.sortedByDescending { it.confidence }
    }

    internal fun firstDateIn(text: String): LocalDate? {
        DATE_PATTERNS.find(text)?.let { m ->
            return if (m.groupValues[4].isNotEmpty()) {
                buildDate(m.groupValues[6].toInt(), m.groupValues[5].toInt(), m.groupValues[4].toInt())
            } else {
                buildDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            }
        }
        DATE_TEXT.find(text)?.let { m ->
            val month = MONTH_NAMES[normalize(m.groupValues[2])] ?: return@let
            return buildDate(m.groupValues[1].toInt(), month, m.groupValues[3].toInt())
        }
        return null
    }

    internal fun buildDate(day: Int, month: Int, yearRaw: Int): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val year = when {
            yearRaw >= 100 -> yearRaw
            yearRaw >= 70 -> 1900 + yearRaw
            else -> 2000 + yearRaw
        }
        if (year !in 1990..2100) return null
        return try {
            LocalDate.of(year, month, day)
        } catch (e: Exception) {
            null
        }
    }

    /** "Zahlungsziel: 14 Tage" -> Hinweis (KEIN erratenes Datum). */
    private fun findPaymentTerm(doc: Document): String? {
        val re = Regex("""(\d{1,3})\s*(?:tage|tag|tagen|days|giorni|dite|gün|gun)\b""", RegexOption.IGNORE_CASE)
        for (line in doc.lines) {
            val n = line.norm
            if (containsAny(n, listOf("zahlungsziel", "zahlbar innerhalb", "netto",
                    "payment terms", "termine di pagamento", "afati i pageses",
                    "odeme suresi", "vade"))) {
                re.find(line.text)?.let { m ->
                    val days = m.groupValues[1].toIntOrNull() ?: return@let
                    if (days in 1..365) return "$days"
                }
            }
        }
        return null
    }

    // -------------------------------------------------------------- IBAN

    private val IBAN_RE = Regex("""\b([A-Za-z]{2}\d{2}(?:[ ]?[A-Za-z0-9]{4}){2,7}(?:[ ]?[A-Za-z0-9]{1,4})?)\b""")
    private val IBAN_LABEL = Regex("""(?:IBAN|IBAN-Nr\.?|IBAN Nr\.?)[^A-Za-z0-9]{0,6}([A-Za-z0-9 ]{15,42})""")

    private class TextHit(val value: String, val confidence: Float, val line: OcrLine)

    private val IBAN_LENGTHS = mapOf(
        "DE" to 22, "AT" to 20, "CH" to 21, "IT" to 27, "AL" to 28, "TR" to 26,
        "GB" to 22, "FR" to 27, "ES" to 24, "NL" to 18, "BE" to 16, "PL" to 28,
        "GR" to 27, "PT" to 25, "HR" to 21, "RS" to 22, "XK" to 20, "MK" to 19,
        "BA" to 20, "SI" to 19, "CZ" to 24, "DK" to 18, "SE" to 24, "NO" to 15
    )

    private fun findIban(doc: Document): TextHit? {
        val forbidden = listOf("telefon", "tel ", "fax", "kundennummer", "rechnungsnummer",
            "rechnung nr", "invoice no", "mobil", "handy", "steuernummer", "ustid")

        // 1) mit IBAN-Label
        for (line in doc.lines) {
            val m = IBAN_LABEL.find(line.text) ?: continue
            val cleaned = cleanIban(m.groupValues[1])
            if (isValidIban(cleaned)) return TextHit(cleaned, 0.99f, line)
            val lenOk = lengthOk(cleaned)
            if (lenOk) return TextHit(cleaned, 0.75f, line)
        }
        // 2) nackte IBAN - nur mit gültiger Prüfsumme oder richtiger Länge
        for (line in doc.lines) {
            val n = line.norm
            if (forbidden.any { n.contains(it) }) continue
            for (m in IBAN_RE.findAll(line.text)) {
                val cleaned = cleanIban(m.groupValues[1])
                if (cleaned.length !in 15..34) continue
                if (cleaned.count { it.isDigit() } < 8) continue
                if (isValidIban(cleaned)) return TextHit(cleaned, 0.93f, line)
                val repaired = repairIban(cleaned)
                if (repaired != null && isValidIban(repaired)) return TextHit(repaired, 0.85f, line)
                if (lengthOk(cleaned)) return TextHit(cleaned, 0.55f, line)
            }
        }
        return null
    }

    private fun lengthOk(iban: String): Boolean {
        val country = iban.take(2).uppercase()
        val expected = IBAN_LENGTHS[country] ?: return iban.length in 18..30
        return iban.length == expected
    }

    /** Repariert typische OCR-Fehler (O<->0, l/I<->1, S<->5, B<->8 ...). */
    private fun repairIban(raw: String): String? {
        val s = raw.uppercase()
        if (s.length < 15) return null
        val out = StringBuilder()
        s.forEachIndexed { i, c ->
            out.append(
                when {
                    i < 2 -> c                                        // Ländercode
                    i < 4 -> if (c.isDigit()) c else OCR_DIGIT[c] ?: c // Prüfziffern
                    else -> if (c.isDigit()) c else OCR_DIGIT[c] ?: c // Kontoteil: Ziffern
                }
            )
        }
        val candidate = out.toString()
        return if (candidate == s) null else candidate
    }

    private val OCR_DIGIT = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'L' to '1', 'l' to '1', 'i' to '1', '|' to '1',
        'Z' to '2', 'z' to '2', 'E' to '3', 'A' to '4',
        'S' to '5', 's' to '5', 'G' to '6', 'b' to '6',
        'T' to '7', 'B' to '8', 'g' to '9', 'q' to '9'
    )

    internal fun cleanIban(raw: String): String =
        raw.uppercase().filter { !it.isWhitespace() }

    /** IBAN-Prüfsumme (ISO 13616 / mod 97-10). */
    fun isValidIban(iban: String): Boolean {
        val s = iban.replace(" ", "").uppercase()
        if (s.length !in 15..34) return false
        if (!s.all { it.isDigit() || it in 'A'..'Z' }) return false
        if (!s[0].isLetter() || !s[1].isLetter()) return false
        if (!s[2].isDigit() || !s[3].isDigit()) return false
        val rearranged = s.substring(4) + s.substring(0, 4)
        var remainder = 0
        for (ch in rearranged) {
            val digits = if (ch.isDigit()) ch.toString() else ((ch - 'A') + 10).toString()
            for (d in digits) remainder = (remainder * 10 + (d - '0')) % 97
        }
        return remainder == 1
    }

    /** Lesbare IBAN mit Leerzeichen in Vierergruppen. */
    fun formatIban(iban: String): String =
        iban.replace(" ", "").chunked(4).joinToString(" ")

    // --------------------------------------------------------------- BIC

    private val BIC_LABEL = Regex("""(?:BIC|SWIFT|BIC/SWIFT)[^A-Za-z0-9]{0,6}([A-Za-z0-9]{8,11})\b""")
    private val BIC_BARE = Regex("""\b([A-Za-z]{4}[A-Za-z]{2}[A-Za-z0-9]{2}(?:[A-Za-z0-9]{3})?)\b""")

    private fun findBic(doc: Document): TextHit? {
        for (line in doc.lines) {
            BIC_LABEL.find(line.text)?.let { m ->
                val v = m.groupValues[1].uppercase()
                if (isValidBic(v)) return TextHit(v, 0.97f, line)
            }
        }
        // nackte BIC: nur in der Nähe von IBAN/Bank
        for (line in doc.lines) {
            val n = line.norm
            if (!containsAny(n, listOf("iban", "bank", "bic", "swift", "kreditinstitut", "bankverbindung"))) continue
            for (m in BIC_BARE.findAll(line.text)) {
                val v = m.groupValues[1].uppercase()
                if (isValidBic(v)) return TextHit(v, 0.80f, line)
            }
        }
        return null
    }

    fun isValidBic(bic: String): Boolean {
        if (bic.length != 8 && bic.length != 11) return false
        if (!bic.take(4).all { it in 'A'..'Z' }) return false
        if (!bic.substring(4, 6).all { it in 'A'..'Z' }) return false
        return bic.all { it.isDigit() || it in 'A'..'Z' }
    }

    // ------------------------------------------------------------- Firma

    private val LEGAL_FORMS = listOf(
        "gmbh", "ag", "ug", "kg", "ohg", "gbr", "se", "kgaa", "eg", "ev", "mbh",
        "co. kg", "& co.", "genossenschaft", "partg", "inc.", "ltd", "llc", "plc",
        "b.v.", "n.v.", "s.a.", "s.p.a.", "s.r.l.", "s.n.c.", "s.a.s.", "s.a.p.a.",
        "sh.p.k.", "sh.a.", "shpk", "sha", "a.s.", "as", "ltd. sti", "ltd. sti.",
        "a.s.", "as.", "san. tic", "tic. ltd", "ooo", "oy", "ab", "as", "oyj"
    )
    private val VENDOR_STOP = listOf(
        "rechnung", "invoice", "fattura", "fatura", "kundennummer", "customer",
        "lieferadresse", "rechnungsadresse", "empfaenger", "empfaenger", "bill to",
        "delivery address", "sehr geehrte", "dear ", "lieferung an", "ship to",
        "cliente", "klienti", "musteri", "muesteri", "sayin", "sayin"
    )
    private val VENDOR_BLOCK = listOf(
        "strasse", "strasse", "str.", "weg", "platz", "allee", "gasse", "ring", "street",
        "road", "avenue", "via ", "viale", "rruga", "sokak", "cadde", "mah.",
        "www.", "http", "@", "tel.", "tel:", "telefon", "telefax", "fax", "mobil",
        "steuernummer", "ustid", "ust-id",
        "vat", "iban", "bic", "swift", "bank", "postfach", "po box", "sparkasse",
        "volksbank", "deutsche bank", "commerzbank", "postbank", "dkb", "ing-diba"
    )

    private fun findVendor(doc: Document): TextHit? {
        // 1) ausdrücklich beschrifteter Absender.
        //    Das Label muss am Zeilenanfang stehen - sonst wird aus
        //    "musterfirma mueller gmbh" die Firma "mueller gmbh".
        for (label in LABELS.vendor) {
            val idx = doc.normLines.indexOfFirst { n ->
                if (!n.startsWith(label)) return@indexOfFirst false
                val rest = n.substring(label.length).trimStart()
                rest.startsWith(":") || rest.startsWith("-") || rest.length >= 3
            }
            if (idx < 0) continue
            val rest = afterLabel(doc.lines[idx], LABELS.vendor)
                .trim().trimStart(':', '-', ';', '=').trim()
            if (isCompanyName(rest)) return TextHit(rest, 0.95f, doc.lines[idx])
            if (idx + 1 < doc.size) {
                val next = doc.lines[idx + 1].text.trim()
                if (isCompanyName(next)) return TextHit(next, 0.90f, doc.lines[idx + 1])
            }
        }
        // 2) Kopfzeile: erste firmenartige Zeile im oberen Bereich
        for (i in 0 until doc.headerEnd) {
            val line = doc.lines[i]
            val n = line.norm
            if (VENDOR_STOP.any { n.contains(it) }) break       // ab hier kommt der Empfänger
            if (VENDOR_BLOCK.any { n.contains(it) }) continue
            val text = line.text.trim()
            if (!isCompanyName(text)) continue
            val hasLegal = LEGAL_FORMS.any { n.contains(it) }
            return TextHit(text, if (hasLegal) 0.90f else 0.62f, line)
        }
        // 3) irgendwo eine Zeile mit Rechtsform (Notlösung, unsicher)
        for (line in doc.lines) {
            val n = line.norm
            if (VENDOR_BLOCK.any { n.contains(it) }) continue
            if (LEGAL_FORMS.any { n.contains(it) } && isCompanyName(line.text.trim())) {
                return TextHit(line.text.trim(), 0.70f, line)
            }
        }
        return null
    }

    private fun isCompanyName(s: String): Boolean {
        val t = s.trim().trimEnd(',', '.', ';')
        if (t.length !in 3..60) return false
        val n = normalize(t)
        if (VENDOR_BLOCK.any { n.contains(it) }) return false
        if (DATE_PATTERNS.containsMatchIn(t)) return false
        if (IBAN_RE.containsMatchIn(t)) return false
        if (t.count { it.isDigit() } > t.length / 2) return false
        if (t.count { it.isLetter() } < 3) return false
        if (n.startsWith("rechnung") || n.startsWith("invoice") || n.startsWith("fattura") ||
            n.startsWith("fatura") || n.startsWith("beleg")
        ) return false
        if (Regex("""^\d{4,5}\s+\S""").containsMatchIn(t)) return false   // PLZ + Ort
        return true
    }

    // ------------------------------------------------- beschriftete Werte

    private val VALUE_ID = Regex("""\b([A-Za-z0-9][A-Za-z0-9\-/_.]{2,31})\b""")

    /** Kundennummern wie "KD 884213" / "C-99812" / "KD-4711". */
    private val ID_WITH_PREFIX = Regex("""^[A-Za-z]{1,4}[\s-]?\d[\dA-Za-z\-/_.]{1,20}$""")

    /** Wörter, die nie eine Nummer sind (nur die Dokumentbezeichnung). */
    private val DOCUMENT_WORDS = setOf(
        "rechnung", "invoice", "fattura", "fatura", "beleg", "bill", "rg",
        "rechnungen", "invoices", "quittung", "receipt", "gutschrift"
    )

    private fun findLabelledValue(doc: Document, labels: List<String>, value: Regex): TextHit? {
        for (label in labels) {
            val idx = doc.normLines.indexOfFirst { it.contains(label) }
            if (idx < 0) continue
            val line = doc.lines[idx]
            var rest = afterLabel(line, labels)
                .trim().trimStart(':', '-', ';', '=', '#').trim()
            rest = DATE_PATTERNS.replace(rest, " ").trim().trimStart(':', '-', ';', '=', '#').trim()
            if (hasMoney(rest)) {
                // Geldbetrag in der Zeile -> sicherheitshalber nur den Teil vor dem Geld nehmen
                rest = rest.substringBefore(Regex("""\d[\d.,]*\s*(?:€|EUR|eur)""").find(rest)?.value ?: rest)
                    .trim().trimEnd(':', '-', ';')
            }
            // "KD 884213", "C-99812": Kürzel und Zahl gehören zusammen
            ID_WITH_PREFIX.find(rest)?.let { m ->
                return TextHit(cleanId(m.value), 0.95f, line)
            }
            if (rest.length in 2..32 && VALUE_ID.matches(rest)) {
                val strong = label.length >= 10
                return TextHit(cleanId(rest), if (strong) 0.95f else 0.85f, line)
            }
            value.find(rest)?.let { m ->
                val v = cleanId(m.groupValues[1])
                if (v.length in 2..32 && normalize(v) !in DOCUMENT_WORDS) {
                    return TextHit(v, 0.85f, line)
                }
            }
            // Wert in der nächsten Zeile (z. B. zweispaltige Rechnungen)
            if (idx + 1 < doc.size) {
                val next = DATE_PATTERNS.replace(doc.lines[idx + 1].text, " ").trim()
                value.find(next)?.let { m ->
                    val v = cleanId(m.groupValues[1])
                    if (v.length in 2..32 && !hasMoney(v) && normalize(v) !in DOCUMENT_WORDS) {
                        return TextHit(v, 0.72f, doc.lines[idx + 1])
                    }
                }
            }
        }
        return null
    }

    private val MONEY_HINT = Regex("""\d+[.,]\d{2}""")

    private fun hasMoney(s: String): Boolean =
        MONEY_HINT.containsMatchIn(s) || s.contains("€") || normalize(s).contains("eur")

    private fun cleanId(v: String): String = v.trim().trimEnd('.', ',', ';', ':')

    // ---------------------------------------------------------- Kategorie

    private val CATEGORY_KEYWORDS = listOf(
        "Strom" to listOf("stadtwerke", "strom", "elektro", "energie", "electric", "oekostrom",
            "oekostrom", "energia", "energjia", "elektrik", "power", "kwh"),
        "Gas" to listOf("gas", "fernwaerme", "fernwaerme", "heizung", "waerme", "waerme", "heating",
            "riscaldamento", "ngrohje", "isinma", "dogalgaz"),
        "Miete" to listOf("miete", "mietvertrag", "hausverwaltung", "wohnungs", "nebenkosten",
            "hausgeld", "rent", "affitto", "qira", "kira"),
        "Versicherung" to listOf("versicherung", "allianz", "huk", "ergo", "axa", "assekuranz",
            "helvetia", "devk", "insurance", "assicurazione", "sigurim", "sigorta"),
        "Handy" to listOf("telekom", "vodafone", "o2", "mobilfunk", "handy", "smartphone",
            "mobilcom", "congstar", "mobile", "telefonia", "telefoni", "cep"),
        "Internet" to listOf("internet", "dsl", "glasfaser", "provider", "hosting", "domain",
            "cloud", "webspace", "broadband", "fibra", "fiber"),
        "Handwerker" to listOf("handwerker", "installateur", "sanitaer", "sanitaer", "maler",
            "elektriker", "schreiner", "dachdecker", "reparatur", "wartung", "montage",
            "craftsman", "artigiano", "ustai", "tamir"),
        "Lebensmittel" to listOf("supermarkt", "rewe", "edeka", "aldi", "lidl", "kaufland",
            "lebensmittel", "baecker", "baecker", "metzger", "getraenk", "getraenk", "grocery",
            "supermercato", "market", "pazari"),
        "Tanken" to listOf("tankstelle", "aral", "shell", "esso", "total", "tanken", "kraftstoff",
            "diesel", "super e10", "fuel", "benzin", "yakit"),
        "Auto" to listOf("kfz", "werkstatt", "reifen", "auto", "tuev", "tuev", "inspektion", "pkw",
            "garage", "officina", "arac", "arac"),
        "Shopping" to listOf("amazon", "zalando", "otto", "shopping", "mode", "moebel", "moebel",
            "ikea", "versand", "bestellung", "online shop", "order"),
        "Gesundheit" to listOf("apotheke", "arzt", "praxis", "klinik", "kranken", "gesundheit",
            "optik", "zahnarzt", "therapie", "pharmacy", "farmacia", "shendeti", "saglik", "eczane"),
        "Kredit" to listOf("kredit", "darlehen", "finanzierung", "rate", "annuitaet", "annuitaet",
            "hypothek", "loan", "prestito", "kredi"),
        "Familie" to listOf("kita", "kindergarten", "schule", "familie", "kinder", "hort",
            "tagespflege", "school", "scuola", "shkoll", "okul"),
    )

    private fun findCategory(text: String): Pair<String, Float>? {
        val n = normalize(text)
        var best: String? = null
        var bestScore = 0
        for ((category, keywords) in CATEGORY_KEYWORDS) {
            val score = keywords.count { n.contains(it) }
            if (score > bestScore) {
                bestScore = score
                best = category
            }
        }
        if (best == null) return null
        val confidence = when (bestScore) {
            1 -> 0.55f
            2 -> 0.70f
            else -> 0.82f
        }
        return best to confidence
    }

    // -------------------------------------------------------- Beschreibung

    private fun findDescription(doc: Document): TextHit? {
        for (label in LABELS.description) {
            val idx = doc.normLines.indexOfFirst { it.contains(label) }
            if (idx < 0) continue
            val line = doc.lines[idx]
            val rest = afterLabel(line, LABELS.description).trim().trimStart(':', '-', ';').trim()
            if (rest.length in 3..90) return TextHit(rest, 0.85f, line)
            if (idx + 1 < doc.size) {
                val next = doc.lines[idx + 1].text.trim()
                if (next.length in 3..90 && !hasMoney(next)) return TextHit(next, 0.70f, doc.lines[idx + 1])
            }
        }
        // Notlösung: längste sinnvolle Zeile in der Dokumentmitte
        val middle = doc.lines.drop(doc.headerEnd).take((doc.size * 0.5).toInt())
        val candidate = middle
            .map { it.text.trim() }
            .filter { it.length in 12..90 && it.count { c -> c.isLetter() } > it.length / 2 && !hasMoney(it) }
            .maxByOrNull { it.length }
        return candidate?.let { TextHit(it, 0.45f, doc.lines.first()) }
    }

    // ------------------------------------------------------------ Hilfen

    fun normalize(s: String): String = s
        .lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace("Ä", "ae").replace("Ö", "oe").replace("Ü", "ue")
        .replace("ç", "c").replace("Ç", "c")
        .replace("ğ", "g").replace("Ğ", "g")
        .replace("ş", "s").replace("Ş", "s")
        .replace("ı", "i").replace("İ", "i")
        .replace("ë", "e").replace("Ë", "e")
        .replace("–", "-").replace("—", "-")
        .replace("’", "'")
        .trim()

    private fun containsAny(normLine: String, labels: List<String>): Boolean =
        labels.any { normLine.contains(it) }

    /** Alles hinter dem erkannten Label derselben Zeile. */
    private fun afterLabel(line: OcrLine, labels: List<String>): String {
        val n = line.norm
        val label = labels.filter { n.contains(it) }.maxByOrNull { it.length } ?: return line.text
        val pos = n.indexOf(label)
        if (pos < 0) return line.text
        val end = pos + label.length
        return if (end <= line.text.length) line.text.substring(end) else ""
    }
}

package com.finovo.app.scan

/**
 * GiroCode / EPC-QR ("Zahlen mit Code"): steht auf vielen deutschen, österreichischen
 * und niederländischen Rechnungen. Die Daten sind exakt, nicht geraten.
 *
 * Format (eine Angabe pro Zeile): BCD / Version / Zeichensatz / SCT / BIC /
 * Name / IBAN / EURbetrag / Zweck / Referenz / Verwendungszweck / Hinweis.
 */
data class EpcPayment(
    val name: String?,
    val iban: String,
    val bic: String?,
    val amountCents: Long?,
    val reference: String?
)

object EpcQr {
    fun parse(payload: String?): EpcPayment? {
        if (payload.isNullOrBlank()) return null
        val l = payload.replace("\r", "").split("\n").map { it.trim() }
        if (l.size < 7 || l[0] != "BCD" || l[3] != "SCT") return null
        val iban = InvoiceParser.cleanIban(l[6])
        if (!InvoiceParser.isValidIban(iban)) return null
        val amount = l.getOrNull(7)?.let { raw ->
            val m = Regex("""^EUR(\d{1,9})(?:\.(\d{1,2}))?$""").find(raw) ?: return@let null
            val euros = m.groupValues[1].toLong()
            val cents = m.groupValues[2].padEnd(2, '0').ifEmpty { "00" }.toLong()
            euros * 100 + cents
        }
        val bic = l.getOrNull(4)?.takeIf { it.isNotBlank() && InvoiceParser.isValidBic(it.uppercase()) }?.uppercase()
        val ref = (l.getOrNull(9)?.takeIf { it.isNotBlank() } ?: l.getOrNull(10)?.takeIf { it.isNotBlank() })
        return EpcPayment(
            name = l.getOrNull(5)?.takeIf { it.isNotBlank() },
            iban = iban,
            bic = bic,
            amountCents = amount?.takeIf { it > 0 },
            reference = ref
        )
    }

    /** Überschreibt die geratenen OCR-Felder mit den exakten Codedaten. */
    fun merge(base: ScannedInvoice, epc: EpcPayment?): ScannedInvoice {
        if (epc == null) return base
        val conf = base.confidence.toMutableMap()
        val notes = base.notes.toMutableMap()
        val hint = "Aus dem GiroCode (QR) gelesen"
        conf[ScannedInvoice.F_IBAN] = 1f; notes[ScannedInvoice.F_IBAN] = hint
        if (epc.bic != null) { conf[ScannedInvoice.F_BIC] = 1f; notes[ScannedInvoice.F_BIC] = hint }
        if (epc.amountCents != null) { conf[ScannedInvoice.F_AMOUNT] = 1f; notes[ScannedInvoice.F_AMOUNT] = hint }
        if (epc.name != null) { conf[ScannedInvoice.F_VENDOR] = 1f; notes[ScannedInvoice.F_VENDOR] = hint }
        val number = base.invoiceNumber ?: epc.reference?.takeIf { it.length <= 40 }
        if (base.invoiceNumber == null && number != null) conf[ScannedInvoice.F_NUMBER] = 0.85f
        val confident = conf.entries.filter { it.value >= ScannedInvoice.RELIABLE }.map { it.key }.toSet()
        return base.copy(
            vendor = epc.name ?: base.vendor,
            amountCents = epc.amountCents ?: base.amountCents,
            iban = epc.iban,
            bic = epc.bic ?: base.bic,
            invoiceNumber = number,
            confidence = conf,
            notes = notes,
            confident = confident
        )
    }
}

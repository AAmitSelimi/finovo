package com.finovo.app

import com.finovo.app.scan.InvoiceParser
import com.finovo.app.scan.OcrLine
import com.finovo.app.scan.ScannedInvoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Tests für die Rechnungsanalyse.
 *
 * Geprüft wird das, worauf es ankommt: der GESAMTBETRAG darf nicht einfach der
 * größte Betrag sein, das Rechnungsdatum darf nicht das Lieferdatum sein, eine
 * IBAN muss rechnerisch stimmen, und unsichere Felder müssen als unsicher
 * markiert werden. Außerdem Rechnungen in allen fünf App-Sprachen.
 */
class InvoiceParserTest {

    // ============================================================ Strom

    private val STROM = """
        Stadtwerke Musterstadt GmbH
        Energieversorgung · Hauptstraße 1 · 40210 Düsseldorf

        Rechnung
        Rechnungsnummer: 2026-4711
        Kundennummer: KD 884213
        Rechnungsdatum: 15.09.2026
        Lieferdatum: 01.08.2026
        Fällig am: 30.09.2026

        Stromlieferung Zeitraum 01.08.2026 - 31.08.2026
        Verbrauch 1.240 kWh

        Nettobetrag                71,01 €
        MwSt. 19 %                 13,49 €
        Gesamtbetrag               84,50 €

        Bitte überweisen Sie den Betrag auf folgendes Konto:
        IBAN DE89 3704 0044 0532 0130 00
        BIC WELADEDMXXX
    """.trimIndent()

    @Test
    fun stromrechnung_alleFelder() {
        val r = InvoiceParser.parse(STROM)
        assertEquals("Stadtwerke Musterstadt GmbH", r.vendor)
        assertEquals("2026-4711", r.invoiceNumber)
        assertEquals("KD 884213", r.customerNumber)
        assertEquals(LocalDate.of(2026, 9, 15), r.invoiceDate)
        assertEquals(LocalDate.of(2026, 9, 30), r.dueDate)
        assertEquals(8450L, r.amountCents)
        assertEquals(7101L, r.netCents)
        assertEquals(1349L, r.vatCents)
        assertEquals("DE89370400440532013000", r.iban)
        assertEquals("WELADEDMXXX", r.bic)
        assertEquals("Strom", r.categoryName)
    }

    @Test
    fun stromrechnung_confidenceIstHoch() {
        val r = InvoiceParser.parse(STROM)
        assertTrue("Gesamtbetrag ${r.percentOf(ScannedInvoice.F_AMOUNT)} %",
            r.isConfident(ScannedInvoice.F_AMOUNT))
        assertTrue("Rechnungsdatum", r.isConfident(ScannedInvoice.F_DATE))
        assertTrue("Fälligkeitsdatum", r.isConfident(ScannedInvoice.F_DUE))
        assertTrue("IBAN", r.isConfident(ScannedInvoice.F_IBAN))
        assertTrue("Firma", r.isConfident(ScannedInvoice.F_VENDOR))
        assertTrue("Rechnungsnummer", r.isConfident(ScannedInvoice.F_NUMBER))
    }

    /** Der Gesamtbetrag ist NICHT der größte Betrag im Dokument. */
    @Test
    fun groessterBetragIstNichtDerGesamtbetrag() {
        val text = """
            Allianz Versicherung AG
            Rechnung
            Rechnungsnummer: V-7781
            Versicherungssumme            250.000,00 €
            Jahresbeitrag netto               71,01 €
            MwSt. 19 %                        13,49 €
            Gesamtbetrag                      84,50 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals(8450L, r.amountCents)
        assertNotEquals(25_000_000L, r.amountCents)
        assertEquals("Allianz Versicherung AG", r.vendor)
        assertEquals("Versicherung", r.categoryName)
    }

    // ============================================================ Gas

    @Test
    fun gasrechnung() {
        val text = """
            Stadtwerke Nord Gas GmbH
            Rechnung
            Rechnungsnummer: G-2026-5544
            Rechnungsdatum: 03.03.2026
            Fälligkeitsdatum: 20.03.2026
            Abschlag Gas Wärme
            Nettobetrag 120,00 €
            Umsatzsteuer 19 % 22,80 €
            Rechnungsbetrag 142,80 €
            IBAN DE89 3704 0044 0532 0130 00
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("Stadtwerke Nord Gas GmbH", r.vendor)
        assertEquals("G-2026-5544", r.invoiceNumber)
        assertEquals(LocalDate.of(2026, 3, 3), r.invoiceDate)
        assertEquals(LocalDate.of(2026, 3, 20), r.dueDate)
        assertEquals(14280L, r.amountCents)
        assertEquals(12000L, r.netCents)
        assertEquals(2280L, r.vatCents)
    }

    // ==================================================== Telekommunikation

    @Test
    fun telekomRechnung() {
        val text = """
            Telekom Deutschland GmbH
            Rechnung
            Rechnungsnummer: 0044112233
            Kundennummer: KD 47110815
            Rechnungsdatum: 12.09.2026
            Zu zahlen bis: 27.09.2026
            Mobilfunk Basic
            Nettobetrag 33,61 €
            MwSt. 19 % 6,39 €
            Gesamtbetrag 40,00 €
            IBAN DE89 3704 0044 0532 0130 00
            BIC WELADEDMXXX
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("Telekom Deutschland GmbH", r.vendor)
        assertEquals("0044112233", r.invoiceNumber)
        assertEquals("KD 47110815", r.customerNumber)
        assertEquals(LocalDate.of(2026, 9, 27), r.dueDate)
        assertEquals(4000L, r.amountCents)
        assertEquals("Handy", r.categoryName)
    }

    // ========================================================== Online-Shop

    @Test
    fun onlineShopRechnung() {
        val text = """
            Amazon EU S.à r.l.
            Niederlassung Deutschland
            Rechnung
            Rechnungsnummer: 123-456789-1011121
            Bestelldatum: 08.09.2026
            Rechnungsdatum: 09.09.2026
            Zwischensumme 45,38 €
            MwSt. 19 % 8,62 €
            Gesamtsumme 54,00 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("123-456789-1011121", r.invoiceNumber)
        assertEquals(LocalDate.of(2026, 9, 9), r.invoiceDate)
        assertEquals(5400L, r.amountCents)
        assertEquals(4538L, r.netCents)
    }

    // ========================================================== Supermarkt

    /** Kassenbon: kein eindeutiges Total-Label -> Betrag muss geprüft werden. */
    @Test
    fun supermarktKeinEindeutigesTotal() {
        val text = """
            REWE Markt GmbH
            Hauptstraße 12
            45127 Essen
            Datum: 12.09.2026
            Milch 1,29 €
            Brot 2,49 €
            Käse 3,99 €
            Obst 5,20 €
            Rindfleisch 8,50 €
            Zwischensumme 21,47 €
            SUMME 23,47 €
            Gegeben 30,00 €
            Rückgeld 6,53 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals(2347L, r.amountCents)
        assertFalse("SUMME darf nicht als sicher gelten",
            r.isConfident(ScannedInvoice.F_AMOUNT))
        assertTrue(r.uncertainFields().contains(ScannedInvoice.F_AMOUNT))
        assertEquals("REWE Markt GmbH", r.vendor)
    }

    // ========================================================= Handwerker

    @Test
    fun handwerkerRechnungMitZahlungsziel() {
        val text = """
            Sanitär Bauer GmbH
            Rechnung
            Rechnungsnummer: 2026-0815
            Rechnungsdatum: 05.08.2026
            Zahlungsziel: 14 Tage

            Position 1  Sanitärarbeiten       1.250,00 €
            Position 2  Anfahrt                  45,00 €
            Zwischensumme netto              1.295,00 €
            MwSt. 19 %                         246,05 €
            Gesamtbetrag                    1.541,05 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals(154_105L, r.amountCents)
        assertEquals(129_500L, r.netCents)
        assertEquals(24_605L, r.vatCents)
        assertEquals(LocalDate.of(2026, 8, 5), r.invoiceDate)
        // Kein Fälligkeitsdatum -> leer lassen, NICHT raten
        assertNull("Fälligkeitsdatum darf nicht geraten werden", r.dueDate)
        assertEquals("14", r.noteOf(ScannedInvoice.F_DUE))
        assertEquals("Handwerker", r.categoryName)
    }

    // ========================================================== Datumsfalle

    @Test
    fun rechnungsdatumIstNichtLieferdatum() {
        val text = """
            Möbelhaus Schulz GmbH & Co. KG
            Rechnung
            Rechnungsnummer: M-9900
            Rechnungsdatum: 03.05.2026
            Lieferdatum: 28.04.2026
            Leistungszeitraum: 01.04.2026 - 30.04.2026
            Fällig am: 18.05.2026
            Nettobetrag 500,00 €
            MwSt. 19 % 95,00 €
            Gesamtbetrag 595,00 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals(LocalDate.of(2026, 5, 3), r.invoiceDate)
        assertEquals(LocalDate.of(2026, 5, 18), r.dueDate)
        assertEquals(59_500L, r.amountCents)
    }

    @Test
    fun rechnungOhneFaelligkeitsdatumBleibtLeer() {
        val text = """
            Bürobedarf Nord GmbH
            Rechnung
            Rechnungsnummer: B-1122
            Rechnungsdatum: 21.06.2026
            Gesamtbetrag 39,90 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertNull(r.dueDate)
        assertEquals(LocalDate.of(2026, 6, 21), r.invoiceDate)
        assertEquals(3990L, r.amountCents)
        assertTrue(r.missingCritical().contains(ScannedInvoice.F_DUE))
    }

    // ================================================================ IBAN

    @Test
    fun ibanMitLeerzeichenWirdNormalisiert() {
        val text = """
            Musterfirma GmbH
            Rechnung
            Rechnungsnummer: R-1
            Gesamtbetrag 100,00 €
            IBAN DE89 3704 0044 0532 0130 00
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("DE89370400440532013000", r.iban)
        assertTrue(r.isConfident(ScannedInvoice.F_IBAN))
        assertEquals("DE89 3704 0044 0532 0130 00", InvoiceParser.formatIban(r.iban!!))
    }

    @Test
    fun ibanMitFalscherPruefsummeWirdNichtAlsSicherErkannt() {
        val text = """
            Musterfirma GmbH
            Rechnung
            Gesamtbetrag 100,00 €
            IBAN DE12 3456 7890 1234 5678 90
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertFalse("Falsche Prüfsumme darf nicht sicher sein",
            r.isConfident(ScannedInvoice.F_IBAN))
        assertFalse(InvoiceParser.isValidIban("DE12345678901234567890"))
    }

    @Test
    fun telefonnummerIstKeineIban() {
        val text = """
            Musterfirma GmbH
            Telefon: +49 201 12345678
            Fax: 0201 987654
            Rechnung
            Rechnungsnummer: 2026-4711-88
            Gesamtbetrag 100,00 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertNull("Telefonnummer darf keine IBAN sein", r.iban)
        assertEquals("2026-4711-88", r.invoiceNumber)
    }

    @Test
    fun rechnungOhneIbanBleibtLeer() {
        val text = """
            Bäckerei Sonnenschein GmbH
            Rechnung
            Rechnungsnummer: 4711
            Rechnungsdatum: 02.02.2026
            Gesamtbetrag 12,80 €
            Bitte bar bezahlen.
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertNull(r.iban)
        assertNull(r.bic)
        assertEquals(1280L, r.amountCents)
    }

    // ============================================================ Formate

    @Test
    fun betragFormateWerdenRichtigGelesen() {
        assertEquals(123_456L, InvoiceParser.parseMoney("1.234,56"))
        assertEquals(12_345L, InvoiceParser.parseMoney("123,45"))
        assertEquals(123_456L, InvoiceParser.parseMoney("1 234,56"))
        assertEquals(123_456L, InvoiceParser.parseMoney("1234,56 EUR"))
        assertEquals(123_456L, InvoiceParser.parseMoney("1.234,56 €"))
        assertEquals(123_456L, InvoiceParser.parseMoney("1234.56"))
        assertEquals(8_450L, InvoiceParser.parseMoney("84,50"))
        assertEquals(123_400L, InvoiceParser.parseMoney("1.234"))
    }

    // =========================================================== Sprachen

    @Test
    fun englischeRechnung() {
        val text = """
            ACME Utilities Ltd
            48 High Street, London

            INVOICE
            Invoice Number: INV-2026-0815
            Customer Number: C-99812
            Invoice Date: 12/09/2026
            Due Date: 27/09/2026

            Subtotal 100.00
            VAT 20% 20.00
            Total Amount 120.00 GBP
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("ACME Utilities Ltd", r.vendor)
        assertEquals("INV-2026-0815", r.invoiceNumber)
        assertEquals("C-99812", r.customerNumber)
        assertEquals(LocalDate.of(2026, 9, 12), r.invoiceDate)
        assertEquals(LocalDate.of(2026, 9, 27), r.dueDate)
        assertEquals(12_000L, r.amountCents)
        assertEquals(10_000L, r.netCents)
        assertEquals(2_000L, r.vatCents)
        assertEquals("en", r.language)
    }

    @Test
    fun italienischeRechnung() {
        val text = """
            Enel Energia S.p.A.
            Via Roma 1, 20100 Milano

            FATTURA
            Numero fattura: FT-2026-0042
            Data fattura: 14/09/2026
            Scadenza: 29/09/2026

            Imponibile 80,00 €
            IVA 22% 17,60 €
            Totale fattura 97,60 €
            IBAN IT80 X054 2811 1010 0001 2345 678
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("Enel Energia S.p.A.", r.vendor)
        assertEquals("FT-2026-0042", r.invoiceNumber)
        assertEquals(LocalDate.of(2026, 9, 14), r.invoiceDate)
        assertEquals(LocalDate.of(2026, 9, 29), r.dueDate)
        assertEquals(9_760L, r.amountCents)
        assertEquals(8_000L, r.netCents)
        assertEquals(1_760L, r.vatCents)
        assertEquals("IT80X0542811101000012345678", r.iban)
    }

    @Test
    fun albanischeRechnung() {
        val text = """
            KESH Sh.a.
            Rruga Dritan Hoxha, Tiranë

            FATURË
            Numri i faturës: F-2026-7788
            Data e faturës: 11.09.2026
            Afati i pagesës: 26.09.2026

            Nëntotali 50,00 €
            TVSH 20% 10,00 €
            Totali 60,00 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("KESH Sh.a.", r.vendor)
        assertEquals("F-2026-7788", r.invoiceNumber)
        assertEquals(LocalDate.of(2026, 9, 11), r.invoiceDate)
        assertEquals(LocalDate.of(2026, 9, 26), r.dueDate)
        assertEquals(6_000L, r.amountCents)
        assertEquals(5_000L, r.netCents)
        assertEquals(1_000L, r.vatCents)
    }

    @Test
    fun tuerkischeRechnung() {
        val text = """
            Turk Telekom A.Ş.
            Fatih Cad. 12, Ankara

            FATURA
            Fatura No: FT-2026-9911
            Fatura Tarihi: 10.09.2026
            Son Ödeme Tarihi: 25.09.2026

            Matrah 100,00 TL
            KDV %20 20,00 TL
            Genel Toplam 120,00 TL
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("Turk Telekom A.Ş.", r.vendor)
        assertEquals("FT-2026-9911", r.invoiceNumber)
        assertEquals(LocalDate.of(2026, 9, 10), r.invoiceDate)
        assertEquals(LocalDate.of(2026, 9, 25), r.dueDate)
        assertEquals(12_000L, r.amountCents)
        assertEquals(10_000L, r.netCents)
        assertEquals(2_000L, r.vatCents)
    }

    // ============================================================== Firma

    @Test
    fun firmaIstNichtDieBank() {
        val text = """
            Musterfirma Müller GmbH
            Rechnung
            Rechnungsnummer: R-100
            Empfänger:
            Max Mustermann
            Bankverbindung: Sparkasse Essen
            IBAN DE89 3704 0044 0532 0130 00
            Gesamtbetrag 100,00 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("Musterfirma Müller GmbH", r.vendor)
        assertFalse(r.vendor!!.contains("Sparkasse"))
    }

    @Test
    fun firmaIstNichtDerKunde() {
        val text = """
            Elektro Meier GmbH
            Rechnung an:
            Familie Schmidt
            Musterweg 5
            Rechnungsnummer: 2026-12
            Kundennummer: KD 555111
            Gesamtbetrag 220,00 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("Elektro Meier GmbH", r.vendor)
        assertEquals("KD 555111", r.customerNumber)
    }

    // ================================================== Fundstellen / Bild

    @Test
    fun fundstelleWirdMitgeliefert() {
        val lines = listOf(
            OcrLine("Musterfirma GmbH", 10f, 20f, 200f, 40f),
            OcrLine("Rechnungsnummer: R-77", 10f, 50f, 200f, 70f),
            OcrLine("Gesamtbetrag 84,50 €", 10f, 300f, 250f, 320f)
        )
        val r = InvoiceParser.parse(lines)
        assertEquals(8450L, r.amountCents)
        val quelle = r.sourceOf(ScannedInvoice.F_AMOUNT)
        assertEquals("Fundstelle muss gemerkt werden", "Gesamtbetrag 84,50 €", quelle?.text)
        assertTrue("Position muss gemerkt werden", quelle?.hasBox == true)
        assertEquals(300f, quelle!!.top, 0.5f)
    }

    // ====================================================== Robustheit

    @Test
    fun leererTextLiefertLeeresErgebnis() {
        val r = InvoiceParser.parse("")
        assertNull(r.amountCents)
        assertNull(r.vendor)
        assertNull(r.invoiceDate)
        assertNull(r.dueDate)
        assertTrue(r.confidence.isEmpty())
    }

    @Test
    fun unsinnigerTextErzeugtKeineFalschenWerte() {
        val r = InvoiceParser.parse("asdf jklö qwe ### 12345 ???")
        assertNull("Kein erfundener Betrag", r.amountCents)
        assertNull("Keine erfundene IBAN", r.iban)
        assertNull("Kein erfundenes Fälligkeitsdatum", r.dueDate)
    }

    @Test
    fun keineRechnungMitNurZahlenErzeugtKeinDatum() {
        val r = InvoiceParser.parse("1234 5678 9012 3456 7890")
        assertNull(r.invoiceDate)
        assertNull(r.amountCents)
    }

    @Test
    fun ibanPruefsummeStimmt() {
        assertTrue(InvoiceParser.isValidIban("DE89370400440532013000"))
        assertTrue(InvoiceParser.isValidIban("IT80X0542811101000012345678"))
        assertTrue(InvoiceParser.isValidIban("AL47212110090000000235698741"))
        assertTrue(InvoiceParser.isValidIban("TR940010000000001234567801"))
        assertFalse(InvoiceParser.isValidIban("DE12345678901234567890"))
        assertFalse(InvoiceParser.isValidIban("12345"))
    }

    @Test
    fun beschreibungWirdErkannt() {
        val text = """
            Halle Vermietung GmbH
            Rechnung
            Rechnungsnummer: H-5
            Betreff: Miete Halle 2 Oktober 2026
            Rechnungsdatum: 01.10.2026
            Fällig am: 15.10.2026
            Gesamtbetrag 1.200,00 €
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        assertEquals("Miete Halle 2 Oktober 2026", r.description)
        assertEquals("Miete", r.categoryName)
        assertEquals(120_000L, r.amountCents)
    }

    /** Eine schlechte Erkennung darf keine stillen Fehler erzeugen. */
    @Test
    fun schlechteErkennungMarkiertFelderStattZuRaten() {
        // Typische OCR-Fehler: fehlende Leerzeichen, falsche Zeichen
        val text = """
            Musterf1rma GmbH
            Rechnung5
            Rechnung5nummer: 2O26-O8l5
            Oatum: O5.O8.2O26
            Gesamtbetrag l.54l,O5 €
            lBAN DE89 37O4 OO44 O532 0l3O OO
        """.trimIndent()
        val r = InvoiceParser.parse(text)
        // Der Betrag ist noch lesbar, das Datum nicht -> muss geprüft werden
        assertTrue("Betrag soll erkannt werden", r.amountCents != null || r.uncertainFields().isNotEmpty())
        if (r.invoiceDate == null) {
            assertTrue("Fehlendes Datum muss als kritisch gelten",
                r.missingCritical().isNotEmpty() || r.uncertainFields().isNotEmpty())
        }
        // Keine stillen Falschwerte: ein erkanntes Datum muss plausibel sein
        r.invoiceDate?.let { datum ->
            assertTrue("Datum muss im plausiblen Bereich liegen",
                datum.year in 2020..2030)
        }
    }
}

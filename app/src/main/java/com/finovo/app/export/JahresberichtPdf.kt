package com.finovo.app.export

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.finovo.app.util.AppLocale
import com.finovo.app.util.Dates
import com.finovo.app.util.Jahresbericht
import com.finovo.app.util.Money
import com.finovo.app.util.str
import java.io.File
import java.util.Locale

/**
 * Jahresbericht als PDF (Modul 26). Entsteht vollstaendig offline auf dem
 * Geraet - es werden keine Daten an einen Server gesendet.
 */
object JahresberichtPdf {

    private const val PAGE_WIDTH = 595   // A4 bei 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    fun erzeugen(context: Context, bericht: Jahresbericht.Ergebnis, symbol: String): File? =
        runCatching {
            val file = File(Export.exportDir(context), "Finovo_Jahresbericht_${bericht.jahr}.pdf")
            val document = PdfDocument()
            var pageNumber = 0
            var page = newPage(document, ++pageNumber)
            var canvas = page.canvas
            var y = MARGIN + 10f

            val titlePaint = textPaint(20f, bold = true)
            val headPaint = textPaint(13f, bold = true)
            val bodyPaint = textPaint(11f)
            val smallPaint = textPaint(9f, gray = true)

            fun ensureSpace(space: Float) {
                if (y + space > PAGE_HEIGHT - MARGIN) {
                    document.finishPage(page)
                    page = newPage(document, ++pageNumber)
                    canvas = page.canvas
                    y = MARGIN + 10f
                }
            }

            fun line(text: String, paint: android.graphics.Paint = bodyPaint, x: Float = MARGIN) {
                ensureSpace(paint.textSize + 6f)
                canvas.drawText(text, x, y, paint)
                y += paint.textSize + 6f
            }

            line(str("yr_pdf_title", "Jahresbericht %1\$s", bericht.jahr.toString()), titlePaint)
            line(
                str(
                    "yr_pdf_created",
                    "Erstellt am %1\$s – offline auf dem Gerät.",
                    Dates.full(java.time.LocalDate.now())
                ),
                smallPaint
            )
            y += 10f

            line(str("yr_pdf_summary", "ZUSAMMENFASSUNG"), headPaint)
            line("${str("einnahmen", "Einnahmen")}: ${Money.format(bericht.einnahmenCents, symbol)}")
            line("${str("ausgaben", "Ausgaben")}: ${Money.format(bericht.ausgabenCents, symbol)}")
            line("${str("ersparnis", "Ersparnis")}: ${Money.format(bericht.ersparnisCents, symbol)}")
            line(
                "${str("sparquote", "Sparquote")}: " +
                    String.format(Locale.GERMANY, "%.1f %%", bericht.quote * 100f)
            )
            line("${str("yr_count", "Erfasste Buchungen")}: ${bericht.anzahlBuchungen}")
            y += 10f

            line(str("yr_pdf_months", "MONATLICHE ENTWICKLUNG"), headPaint)
            bericht.monate.forEach { m ->
                line(
                    "${m.monat.monthValue.toString().padStart(2, '0')}/${m.monat.year}  " +
                        "${Money.format(m.einnahmenCents, symbol)}  " +
                        "${Money.format(m.ausgabenCents, symbol)}  " +
                        "${Money.format(m.einnahmenCents - m.ausgabenCents, symbol)}"
                )
            }
            y += 10f

            if (bericht.kategorien.isNotEmpty()) {
                line(str("yr_pdf_categories", "AUSGABEN NACH KATEGORIE"), headPaint)
                bericht.kategorien.forEach { k ->
                    line(
                        "${k.name}  ${Money.format(k.cents, symbol)}  " +
                            String.format(Locale.GERMANY, "%.1f %%", k.percent * 100f)
                    )
                }
                y += 10f
            }

            if (bericht.groessteAusgaben.isNotEmpty()) {
                line(str("yr_pdf_biggest", "GRÖSSTE AUSGABEN"), headPaint)
                bericht.groessteAusgaben.take(10).forEach { e ->
                    val name = e.merchant.ifBlank { e.note }.ifBlank { str("fallback_expense", "Ausgabe") }
                    line("${Dates.full(Dates.fromEpochDay(e.date))}  ${name.take(40)}  ${Money.format(e.amountCents, symbol)}")
                }
                y += 10f
            }

            line(str("yr_pdf_fixed", "WIEDERKEHRENDE KOSTEN"), headPaint)
            line("${str("yr_recurring", "Aus wiederkehrenden Regeln")}: ${Money.format(bericht.wiederkehrendCents, symbol)}")
            line("${str("yr_subs", "Abos im Jahr")}: ${Money.format(bericht.abosCents, symbol)}")
            y += 10f

            if (bericht.budgetMonate.isNotEmpty()) {
                line(str("yr_pdf_budget", "BUDGET-EINHALTUNG"), headPaint)
                bericht.budgetMonate.forEach { b ->
                    line(
                        "${b.monat.monthValue.toString().padStart(2, '0')}  " +
                            "${Money.format(b.ausgabenCents, symbol)} / ${Money.format(b.limitCents, symbol)}  " +
                            if (b.eingehalten) str("yr_kept", "eingehalten")
                            else str("yr_over", "überschritten")
                    )
                }
                y += 10f
            }

            if (bericht.ziele.isNotEmpty()) {
                line(str("yr_pdf_goals", "SPARZIELE"), headPaint)
                bericht.ziele.forEach { z ->
                    line("${z.name}  ${Money.format(z.gespartCents, symbol)} / ${Money.format(z.zielCents, symbol)}")
                }
            }

            document.finishPage(page)
            file.outputStream().use { document.writeTo(it) }
            document.close()
            file
        }.onFailure {
            android.util.Log.e("JahresberichtPdf", "PDF konnte nicht erstellt werden", it)
        }.getOrNull()

    /** CSV-Fassung des Jahresberichts. */
    fun csv(bericht: Jahresbericht.Ergebnis, symbol: String): String {
        val sb = StringBuilder()
        sb.append(str("yr_pdf_title", "Jahresbericht %1\$s", bericht.jahr.toString())).append('\n')
        sb.append("${str("einnahmen", "Einnahmen")};${Money.format(bericht.einnahmenCents, symbol)}\n")
        sb.append("${str("ausgaben", "Ausgaben")};${Money.format(bericht.ausgabenCents, symbol)}\n")
        sb.append("${str("ersparnis", "Ersparnis")};${Money.format(bericht.ersparnisCents, symbol)}\n\n")
        sb.append(str("yr_pdf_categories", "AUSGABEN NACH KATEGORIE")).append('\n')
        sb.append("${str("kategorie", "Kategorie")};${str("betrag", "Betrag")};${str("anteil", "Anteil")}\n")
        bericht.kategorien.forEach { k ->
            sb.append(k.name).append(';').append(Money.format(k.cents, symbol)).append(';')
                .append(String.format(Locale.GERMANY, "%.1f %%", k.percent * 100f)).append('\n')
        }
        return sb.toString()
    }

    private fun newPage(document: PdfDocument, number: Int): PdfDocument.Page =
        document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()
        )

    private fun textPaint(size: Float, bold: Boolean = false, gray: Boolean = false): android.graphics.Paint =
        android.graphics.Paint().apply {
            textSize = size
            isAntiAlias = true
            isFakeBoldText = bold
            color = when {
                gray -> android.graphics.Color.GRAY
                bold -> android.graphics.Color.BLACK
                else -> android.graphics.Color.DKGRAY
            }
        }
}

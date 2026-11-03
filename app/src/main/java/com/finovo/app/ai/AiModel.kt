package com.finovo.app.ai

import com.finovo.app.util.str

/** Woher eine Antwort kommt. */
enum class AiSource { LOCAL, REMOTE }

/** Worüber die Frage des Nutzers handelt. */
enum class AiIntent {
    SAVE_MONEY,
    WHERE_MONEY_GOES,
    OPEN_BILLS,
    UPCOMING_PAYMENTS,
    MONTH_ANALYSIS,
    MONTH_COMPARISON,
    RECURRING_PAYMENTS,
    /** "Wie viel habe ich diesen Monat ausgegeben?" */
    TOTAL_SPENT,
    /** "Und wie viel davon für Auto?" (Rückfrage zu einer Kategorie) */
    CATEGORY_AMOUNT,
    /** "Was war die größte Ausgabe?" */
    LARGEST_EXPENSE,
    GENERAL
}

/**
 * Die vier Arten von Aussagen, die Finovo AI streng trennt.
 *
 * Der Nutzer muss jederzeit erkennen können, was eine gemessene Tatsache ist
 * und was nur eine Hochrechnung oder ein Vorschlag.
 */
enum class AiSection(val key: String, val de: String) {
    FACTS("ai_section_facts", "Tatsächliche Daten"),
    CALCULATED("ai_section_calculated", "Berechnete Werte"),
    ESTIMATE("ai_section_estimate", "Schätzungen"),
    SUGGESTION("ai_section_suggestion", "Vorschläge");

    /** Übersetzte Überschrift des Abschnitts. */
    fun label(): String = str(key, de)
}

/** Ein Baustein der Antwort. */
sealed class AiBlock {
    data class Heading(val text: String, val emoji: String = "") : AiBlock()
    data class Text(val text: String, val section: AiSection? = null) : AiBlock()
    data class Row(val label: String, val value: String, val section: AiSection? = null) : AiBlock()
    object Gap : AiBlock()
}

/**
 * Antwort von Finovo AI.
 *
 * [blocks] ist bereits fertig übersetzt und enthält ausschließlich Werte, die
 * aus der [com.finovo.app.ai.FinancialSummary] stammen - also aus der
 * Datenbank des Nutzers. Erfundene Beträge sind strukturell ausgeschlossen.
 */
data class AiAnswer(
    val blocks: List<AiBlock>,
    val intent: AiIntent,
    val hasEnoughData: Boolean,
    val source: AiSource = AiSource.LOCAL
) {
    /**
     * Flachtext - zum Kopieren, für Tests und für die Sprachausgabe.
     *
     * Wechselt die Art der Aussage, wird die Überschrift des Abschnitts
     * eingefügt ("Tatsächliche Daten", "Berechnete Werte", "Schätzungen",
     * "Vorschläge"). So bleibt die Trennung auch im Klartext erhalten.
     */
    val text: String
        get() {
            val teile = ArrayList<String>()
            var aktuelleGruppe: AiSection? = null
            for (block in blocks) {
                val gruppe = when (block) {
                    is AiBlock.Text -> block.section
                    is AiBlock.Row -> block.section
                    else -> null
                }
                if (gruppe != null && gruppe != aktuelleGruppe) {
                    teile += gruppe.label()
                }
                aktuelleGruppe = gruppe
                teile += when (block) {
                    is AiBlock.Heading ->
                        listOf(block.emoji, block.text).filter { it.isNotBlank() }.joinToString(" ")

                    is AiBlock.Text -> block.text
                    is AiBlock.Row -> "${block.label}: ${block.value}"
                    AiBlock.Gap -> ""
                }
            }
            return teile.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        }
}

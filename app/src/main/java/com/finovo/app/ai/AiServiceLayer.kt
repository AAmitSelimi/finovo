package com.finovo.app.ai

import com.finovo.app.util.AppLocale
import com.finovo.app.util.Money
import com.finovo.app.util.str
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Anschluss für einen externen KI-Dienst.
 *
 * Standardmäßig ist KEIN externer Dienst aktiv: Finovo AI rechnet vollständig
 * auf dem Gerät. Erst wenn ein Endpunkt hinterlegt ist UND der Nutzer
 * ausdrücklich zustimmt, wird die strukturierte Zusammenfassung übertragen.
 */
interface RemoteAiProvider {
    /** Ein Dienst ist nur konfiguriert, wenn Endpunkt und Schlüssel vorliegen. */
    val isConfigured: Boolean

    /** Anzeigename für die Oberfläche. */
    val displayName: String

    /** Antwort als Flachtext - null, wenn nicht verfügbar. */
    suspend fun complete(
        summary: FinancialSummary,
        question: String,
        language: String
    ): String?
}

/** Kein externer Dienst - der Standard. */
object NoRemoteAiProvider : RemoteAiProvider {
    override val isConfigured: Boolean = false
    override val displayName: String = str("ai_external_none", "Kein externer Dienst")
    override suspend fun complete(
        summary: FinancialSummary,
        question: String,
        language: String
    ): String? = null
}

/**
 * Konkreter Anschluss über HTTPS.
 *
 * Es wird ausschließlich die strukturierte [FinancialSummary] übertragen -
 * und die Frage des Nutzers. Bankverbindungen, Kontodaten, Passwörter und
 * Zugangsdaten sind in der Zusammenfassung strukturell nicht enthalten.
 *
 * Schlägt der Aufruf fehl (kein Netz, keine Freigabe, Serverfehler), antwortet
 * Finovo AI automatisch lokal weiter. Die App stürzt dabei nie ab.
 */
class HttpRemoteAiProvider(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String = ""
) : RemoteAiProvider {

    override val isConfigured: Boolean
        get() = endpoint.startsWith("https://") && apiKey.isNotBlank()

    override val displayName: String = str("ai_external_http", "Externer KI-Dienst (HTTPS)")

    override suspend fun complete(
        summary: FinancialSummary,
        question: String,
        language: String
    ): String? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        runCatching {
            val body = JSONObject().apply {
                put("model", model)
                put("language", language)
                put("question", question)
                put("summary", summary.toJson())
            }
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.connectTimeout = 8_000
                connection.readTimeout = 20_000
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                    it.write(body.toString())
                }
                if (connection.responseCode !in 200..299) return@runCatching null
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val answer = runCatching { JSONObject(text).optString("answer", "") }.getOrNull()
                answer?.takeIf { it.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}

/** Welche Quelle Finovo AI nutzen darf. */
enum class AiMode { LOCAL, REMOTE_IF_CONSENTED }

/**
 * Die einzige Stelle, an der Finovo AI eine Antwort holt.
 *
 * Ablauf: Datenbank -> FinancialSummaryService -> FinancialSummary -> KI ->
 * AiAnswer -> Oberfläche. Diese Schicht kennt keine Datenbank und kann Daten
 * weder lesen noch schreiben - sie bekommt die Zusammenfassung fertig
 * hereingereicht.
 */
object AiServiceLayer {

    @Volatile
    var provider: RemoteAiProvider = NoRemoteAiProvider

    @Volatile
    var mode: AiMode = AiMode.LOCAL

    /** Zustimmung des Nutzers - ohne sie geht nichts nach außen. */
    @Volatile
    var consentGiven: Boolean = false

    /** Antwort holen: lokal (immer möglich) oder extern (nur mit Zustimmung). */
    suspend fun answer(
        summary: FinancialSummary,
        question: String,
        language: String = AppLocale.current(),
        context: AiContext? = null
    ): AiAnswer {
        val darfExtern = mode == AiMode.REMOTE_IF_CONSENTED &&
            consentGiven &&
            provider.isConfigured
        if (darfExtern) {
            val remote = runCatching {
                provider.complete(summary, question, language)
            }.getOrNull()
            if (!remote.isNullOrBlank()) {
                return AiAnswer(
                    blocks = remote.split("\n").map { zeile -> AiBlock.Text(zeile) },
                    intent = FinovoAiEngine.detectIntent(question, language),
                    hasEnoughData = true,
                    source = AiSource.REMOTE
                )
            }
            // Extern nicht erreichbar -> lokal weiterarbeiten, kein Absturz.
        }
        return FinovoAiEngine.answer(summary, question, language, context)
    }

    /**
     * Transparenz: genau dieser Text würde an einen externen Dienst gehen.
     * Er enthält keine Kontodaten, keine Passwörter und keine Bankverbindung.
     */
    fun disclosure(summary: FinancialSummary, question: String): String =
        "Frage: $question\n" + summary.toJson().toString(2)

    /** Wird tatsächlich etwas nach außen gesendet? */
    fun sendsDataExternally(): Boolean =
        mode == AiMode.REMOTE_IF_CONSENTED && consentGiven && provider.isConfigured
}

/** Kompakte, übertragbare Fassung der Zusammenfassung (nur Zahlen, keine IDs). */
private fun FinancialSummary.toJson(): JSONObject = JSONObject().apply {
    put("month", month.label)
    put("incomeCents", month.incomeCents)
    put("expenseCents", month.expenseCents)
    put("fixedCents", month.fixedCents)
    put("variableCents", month.variableCents)
    put("openBillsTotalCents", openBillsTotalCents)
    put("openBills", openBills.size)
    put("overdueBills", overdueBills.size)
    put("recurringMonthlyCents", recurringMonthlyCents)
    put("transactions", totalTransactions)
    put("categories", JSONArray().apply {
        month.categories.take(10).forEach { c ->
            put(JSONObject().apply {
                put("name", c.name)
                put("cents", c.cents)
                put("fixed", c.isFixed)
            })
        }
    })
}

/** Lesbare Kurzfassung für die Oberfläche. */
fun FinancialSummary.shortFacts(symbol: String = this.symbol): String = str(
    "ai_summary_short",
    "%1\$s Ausgaben im %2\$s · %3\$s offene Rechnungen",
    Money.format(month.expenseCents, symbol), month.label, openBills.size.toString()
)

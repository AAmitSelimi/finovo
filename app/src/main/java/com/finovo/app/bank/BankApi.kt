package com.finovo.app.bank

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.finovo.app.util.str

/**
 * Client for the Finovo backend.
 *
 * The app NEVER talks to a bank or to an open-banking provider directly and it
 * never contains a provider secret: everything goes through the user's own
 * backend over HTTPS. Without a backend URL the feature simply stays inactive.
 */
class BankApiClient(
    private val baseUrl: String,
    private val accessToken: () -> String
) {

    class BankException(message: String, val offline: Boolean = false) : Exception(message)

    data class BankInstitution(val id: String, val name: String, val bic: String = "")

    data class AuthStart(val reference: String, val authUrl: String)

    data class RemoteAccount(
        val externalId: String,
        val name: String,
        val iban: String,
        val currency: String,
        val balanceCents: Long
    )

    data class RemoteConnection(
        val externalId: String,
        val institutionId: String,
        val bankName: String,
        val status: String,
        val consentExpiresAt: Long,
        val accounts: List<RemoteAccount>
    )

    data class RemoteTransaction(
        val accountExternalId: String,
        val externalId: String,
        val bookingDate: Long,
        val valueDate: Long,
        val amountCents: Long,
        val currency: String,
        val merchant: String,
        val description: String
    )

    data class SyncPayload(
        val accounts: List<RemoteAccount>,
        val transactions: List<RemoteTransaction>
    )

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val configured: Boolean get() = baseUrl.isNotBlank()

    // ------------------------------------------------------------------ calls

    fun institutions(query: String = ""): List<BankInstitution> {
        val res = get("/v1/institutions?q=${encode(query)}")
        val arr = res.optJSONArray("institutions") ?: JSONArray()
        val out = ArrayList<BankInstitution>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += BankInstitution(
                id = o.optString("id"),
                name = o.optString("name"),
                bic = o.optString("bic")
            )
        }
        return out
    }

    /** Step 1: ask the backend for the OAuth page of the bank. */
    fun startConnection(institutionId: String, redirectUri: String): AuthStart {
        val body = JSONObject()
            .put("institutionId", institutionId)
            .put("redirectUri", redirectUri)
        val res = post("/v1/connections/start", body)
        return AuthStart(
            reference = res.optString("reference"),
            authUrl = res.optString("authUrl")
        )
    }

    /** Step 2: after the user came back, exchange the reference. */
    fun completeConnection(reference: String): RemoteConnection {
        val body = JSONObject().put("reference", reference)
        return parseConnection(post("/v1/connections/complete", body).optJSONObject("connection"))
    }

    fun connections(): List<RemoteConnection> {
        val arr = get("/v1/connections").optJSONArray("connections") ?: JSONArray()
        val out = ArrayList<RemoteConnection>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += parseConnection(o)
        }
        return out
    }

    fun sync(connectionExternalId: String, sinceEpochDay: Long = 0L): SyncPayload {
        val body = JSONObject()
            .put("connectionId", connectionExternalId)
            .put("sinceEpochDay", sinceEpochDay)
        val res = post("/v1/sync", body)

        val acc = ArrayList<RemoteAccount>()
        val accArr = res.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accArr.length()) {
            val o = accArr.optJSONObject(i) ?: continue
            acc += parseAccount(o)
        }

        val tx = ArrayList<RemoteTransaction>()
        val txArr = res.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArr.length()) {
            val o = txArr.optJSONObject(i) ?: continue
            tx += RemoteTransaction(
                accountExternalId = o.optString("accountId"),
                externalId = o.optString("id"),
                bookingDate = o.optLong("bookingDate", 0L),
                valueDate = o.optLong("valueDate", o.optLong("bookingDate", 0L)),
                amountCents = parseAmount(o.optString("amount", "0")),
                currency = o.optString("currency", "EUR"),
                merchant = o.optString("merchant", ""),
                description = o.optString("description", "")
            )
        }
        return SyncPayload(acc, tx)
    }

    fun deleteConnection(connectionExternalId: String) {
        delete("/v1/connections/${encode(connectionExternalId)}")
    }

    // ------------------------------------------------------------------ http

    private fun request(builder: Request.Builder): JSONObject {
        if (baseUrl.isBlank()) {
            throw BankException(str("bank_no_backend", "Kein Backend konfiguriert."), offline = true)
        }
        val token = accessToken()
        if (token.isNotBlank()) builder.addHeader("Authorization", "Bearer $token")
        builder.addHeader("Accept", "application/json")
        try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                    throw BankException(
                        if (message.isNullOrBlank()) str("bank_server_error", "Serverfehler %1\$s", response.code) else message
                    )
                }
                if (text.isBlank()) return JSONObject()
                return runCatching { JSONObject(text) }
                    .getOrElse { throw BankException(str("bank_invalid_response", "Ungültige Antwort vom Server.")) }
            }
        } catch (e: BankException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            throw BankException(NO_CONNECTION, offline = true)
        } catch (e: java.io.IOException) {
            throw BankException(NO_CONNECTION, offline = true)
        }
    }

    private fun get(path: String) =
        request(Request.Builder().url(baseUrl.trimEnd('/') + path).get())

    private fun post(path: String, body: JSONObject) = request(
        Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body.toString().toRequestBody(json))
    )

    private fun delete(path: String) = request(
        Request.Builder().url(baseUrl.trimEnd('/') + path).delete()
    )

    private fun parseConnection(o: JSONObject?): RemoteConnection {
        val src = o ?: JSONObject()
        val accounts = ArrayList<RemoteAccount>()
        val arr = src.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            accounts += parseAccount(a)
        }
        return RemoteConnection(
            externalId = src.optString("externalId"),
            institutionId = src.optString("institutionId"),
            bankName = src.optString("bankName"),
            status = src.optString("status", "LINKED"),
            consentExpiresAt = src.optLong("consentExpiresAt", 0L),
            accounts = accounts
        )
    }

    private fun parseAccount(o: JSONObject) = RemoteAccount(
        externalId = o.optString("externalId"),
        name = o.optString("name"),
        iban = o.optString("iban"),
        currency = o.optString("currency", "EUR"),
        balanceCents = parseAmount(o.optString("balance", "0"))
    )

    companion object {
        val NO_CONNECTION =
            str("bank_offline", "Keine Internetverbindung. Bankdaten konnten nicht synchronisiert werden.")

        /** "1234.56" / "-1234.56" / "1234,56" -> cents, without floating point. */
        fun parseAmount(raw: String): Long {
            var s = raw.trim().replace(" ", "").replace("EUR", "").replace("€", "")
            if (s.isBlank()) return 0L
            var negative = false
            if (s.startsWith("-")) {
                negative = true
                s = s.substring(1)
            } else if (s.startsWith("+")) {
                s = s.substring(1)
            }
            s = s.replace(",", ".")
            val dot = s.indexOf('.')
            val whole: String
            val frac: String
            if (dot >= 0) {
                whole = s.substring(0, dot).filter { it.isDigit() }
                frac = s.substring(dot + 1).filter { it.isDigit() }.take(2).padEnd(2, '0')
            } else {
                whole = s.filter { it.isDigit() }
                frac = "00"
            }
            val cents = (whole.ifEmpty { "0" }.toLongOrNull() ?: 0L) * 100L +
                (frac.toLongOrNull() ?: 0L)
            return if (negative) -cents else cents
        }

        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8")
    }
}

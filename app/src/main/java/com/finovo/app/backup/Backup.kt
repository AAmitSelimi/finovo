package com.finovo.app.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillPaymentEntity
import com.finovo.app.data.BudgetEntity
import com.finovo.app.data.AreaEntity
import com.finovo.app.data.AssetEntity
import com.finovo.app.data.AssetKind
import com.finovo.app.data.CategoryBudgetEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryRuleEntity
import com.finovo.app.data.DocumentEntity
import com.finovo.app.data.DocumentKind
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.Repository
import com.finovo.app.data.SavingGoalEntity
import com.finovo.app.data.SettingsEntity
import com.finovo.app.data.SubscriptionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.finovo.app.util.str
import com.finovo.app.util.S
import com.finovo.app.util.plurals

data class BackupPayload(
    val categories: List<CategoryEntity>,
    val expenses: List<ExpenseEntity>,
    val incomes: List<IncomeEntity>,
    val bills: List<BillEntity>,
    val payments: List<BillPaymentEntity>,
    val rules: List<RecurringRuleEntity>,
    val budget: BudgetEntity?,
    val settings: SettingsEntity?,
    val areas: List<AreaEntity> = emptyList(),
    val goals: List<SavingGoalEntity> = emptyList(),
    val subscriptions: List<SubscriptionEntity> = emptyList(),
    val categoryBudgets: List<CategoryBudgetEntity> = emptyList(),
    val categoryRules: List<CategoryRuleEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val assets: List<AssetEntity> = emptyList(),
    val exportedAt: Long = System.currentTimeMillis()
) {
    fun counts(): String {
        val total = expenses.size + incomes.size + bills.size + rules.size
        return plurals("pl_entries", total, "%1\$d Eintrag", "%1\$d Einträge")
    }
}

object Backup {

    const val VERSION = 1
    const val APP_TAG = "Finovo"
    private const val LEGACY_TAG = "MyKosten"
    const val MIME = "application/json"

    /**
     * Dateiname eines Backups, z. B. "Finovo-Backup-2026-09-19-1945.json".
     *
     * Wichtig: ein [LocalDate] kennt keine Stunden und Minuten - ein Muster mit
     * "HHmm" loest darauf eine UnsupportedTemporalTypeException aus. Genau das
     * hat "Als Datei speichern" und "Backup erstellen" zum Absturz gebracht.
     * Deshalb [LocalDateTime] und zusaetzlich ein sicherer Rueckfall.
     */
    fun defaultFileName(): String = runCatching {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm"))
        "Finovo-Backup-$stamp.json"
    }.getOrDefault("Finovo-Backup-${System.currentTimeMillis()}.json")

    fun backupDir(context: Context): File =
        File(context.filesDir, "backup").apply { if (!exists()) mkdirs() }

    // ------------------------------ writing ----------------------------------

    fun toJson(payload: BackupPayload): JSONObject {
        val root = JSONObject()
        root.put("app", APP_TAG)
        root.put("version", VERSION)
        root.put("exportedAt", payload.exportedAt)
        root.put("exportedAtReadable", LocalDate.now().toString())

        val data = JSONObject()

        fun arr(items: List<*>): JSONArray = JSONArray().apply { items.forEach { put(it) } }

        data.put("categories", JSONArray().apply {
            payload.categories.forEach { c ->
                put(
                    JSONObject().apply {
                        put("id", c.id); put("name", c.name); put("emoji", c.emoji)
                        put("type", c.type); put("isFixed", c.isFixed)
                        put("isCustom", c.isCustom); put("sortOrder", c.sortOrder)
                    }
                )
            }
        })
        data.put("expenses", JSONArray().apply {
            payload.expenses.forEach { e ->
                put(
                    JSONObject().apply {
                        put("id", e.id); put("amountCents", e.amountCents)
                        put("categoryId", e.categoryId); put("date", e.date)
                        put("note", e.note); put("paid", e.paid)
                        if (e.fixedOverride != null) put("fixedOverride", e.fixedOverride)
                        put("recurringId", e.recurringId ?: 0L)
                        put("occurrenceDate", e.occurrenceDate ?: 0L)
                        put("areaId", e.areaId)
                        put("receiptUri", e.receiptUri)
                        put("createdAt", e.createdAt)
                    }
                )
            }
        })
        data.put("incomes", JSONArray().apply {
            payload.incomes.forEach { i ->
                put(
                    JSONObject().apply {
                        put("id", i.id); put("amountCents", i.amountCents)
                        put("categoryId", i.categoryId); put("date", i.date)
                        put("note", i.note)
                        put("recurringId", i.recurringId ?: 0L)
                        put("occurrenceDate", i.occurrenceDate ?: 0L)
                        put("areaId", i.areaId)
                        put("createdAt", i.createdAt)
                    }
                )
            }
        })
        data.put("bills", JSONArray().apply {
            payload.bills.forEach { b ->
                put(
                    JSONObject().apply {
                        put("id", b.id); put("name", b.name); put("amountCents", b.amountCents)
                        put("dueDate", b.dueDate); put("categoryId", b.categoryId)
                        put("isRecurring", b.isRecurring); put("frequency", b.frequency)
                        put("interval", b.interval); put("customDays", b.customDays)
                        put("reminderEnabled", b.reminderEnabled)
                        put("reminderDaysBefore", b.reminderDaysBefore)
                        put("reminderHour", b.reminderHour)
                        put("reminderMinute", b.reminderMinute)
                        put("status", b.status); put("note", b.note)
                        put("recurringId", b.recurringId ?: 0L)
                        // --- Rechnungsscan: Haendler, Nummer, IBAN, Bild, Analyse ---
                        put("vendor", b.vendor)
                        put("invoiceNumber", b.invoiceNumber)
                        if (b.invoiceDate != null) put("invoiceDate", b.invoiceDate)
                        put("iban", b.iban)
                        put("bic", b.bic)
                        put("customerNumber", b.customerNumber)
                        put("imagePath", b.imagePath)
                        if (b.netCents != null) put("netCents", b.netCents)
                        if (b.vatCents != null) put("vatCents", b.vatCents)
                        put("ocrText", b.ocrText)
                        put("confidence", b.confidence)
                        put("fieldBoxes", b.fieldBoxes)
                    }
                )
            }
        })
        data.put("billPayments", JSONArray().apply {
            payload.payments.forEach { p ->
                put(
                    JSONObject().apply {
                        put("id", p.id); put("billId", p.billId); put("billName", p.billName)
                        put("amountCents", p.amountCents); put("categoryId", p.categoryId)
                        put("dueDate", p.dueDate); put("paidDate", p.paidDate)
                        put("note", p.note); put("expenseId", p.expenseId ?: 0L)
                    }
                )
            }
        })
        data.put("recurringRules", JSONArray().apply {
            payload.rules.forEach { r ->
                put(
                    JSONObject().apply {
                        put("id", r.id); put("kind", r.kind); put("name", r.name)
                        put("amountCents", r.amountCents); put("categoryId", r.categoryId)
                        put("note", r.note); put("frequency", r.frequency)
                        put("interval", r.interval); put("customDays", r.customDays)
                        put("dayOfMonth", r.dayOfMonth); put("monthOfYear", r.monthOfYear)
                        put("startDate", r.startDate)
                        put("endDate", r.endDate ?: 0L)
                        put("active", r.active)
                        put("areaId", r.areaId)
                        put("lastGenerated", r.lastGenerated ?: 0L)
                        put("createdAt", r.createdAt)
                    }
                )
            }
        })
        data.put("areas", JSONArray().apply {
            payload.areas.forEach { a ->
                put(
                    JSONObject().apply {
                        put("id", a.id); put("name", a.name); put("emoji", a.emoji)
                        put("sortOrder", a.sortOrder); put("createdAt", a.createdAt)
                    }
                )
            }
        })
        data.put("savingGoals", JSONArray().apply {
            payload.goals.forEach { g ->
                put(
                    JSONObject().apply {
                        put("id", g.id); put("name", g.name); put("emoji", g.emoji)
                        put("targetCents", g.targetCents); put("savedCents", g.savedCents)
                        put("targetDate", g.targetDate ?: 0L)
                        put("areaId", g.areaId); put("note", g.note)
                        put("archived", g.archived); put("createdAt", g.createdAt)
                    }
                )
            }
        })
        data.put("subscriptions", JSONArray().apply {
            payload.subscriptions.forEach { s ->
                put(
                    JSONObject().apply {
                        put("id", s.id); put("name", s.name); put("emoji", s.emoji)
                        put("amountCents", s.amountCents); put("frequency", s.frequency)
                        put("interval", s.interval); put("customDays", s.customDays)
                        put("nextPayment", s.nextPayment); put("startDate", s.startDate)
                        put("endDate", s.endDate ?: 0L); put("categoryId", s.categoryId)
                        put("reminderEnabled", s.reminderEnabled)
                        put("reminderDaysBefore", s.reminderDaysBefore)
                        put("cancelByDate", s.cancelByDate ?: 0L)
                        put("note", s.note); put("active", s.active)
                        put("areaId", s.areaId); put("notifiedFor", s.notifiedFor)
                        put("createdAt", s.createdAt)
                    }
                )
            }
        })
        data.put("categoryBudgets", JSONArray().apply {
            payload.categoryBudgets.forEach { b ->
                put(
                    JSONObject().apply {
                        put("categoryId", b.categoryId)
                        put("monthlyLimitCents", b.monthlyLimitCents)
                        put("enabled", b.enabled)
                    }
                )
            }
        })
        data.put("categoryRules", JSONArray().apply {
            payload.categoryRules.forEach { r ->
                put(
                    JSONObject().apply {
                        put("id", r.id); put("token", r.token)
                        put("categoryId", r.categoryId); put("isIncome", r.isIncome)
                        put("hits", r.hits); put("updatedAt", r.updatedAt)
                    }
                )
            }
        })
        data.put("documents", JSONArray().apply {
            payload.documents.forEach { d ->
                put(
                    JSONObject().apply {
                        put("id", d.id); put("title", d.title); put("kind", d.kind)
                        put("path", d.path); put("date", d.date); put("tags", d.tags)
                        put("note", d.note); put("merchant", d.merchant)
                        if (d.amountCents != null) put("amountCents", d.amountCents)
                        put("linkedExpenseId", d.linkedExpenseId)
                        put("linkedBillId", d.linkedBillId)
                        put("mimeType", d.mimeType); put("createdAt", d.createdAt)
                    }
                )
            }
        })
        data.put("assets", JSONArray().apply {
            payload.assets.forEach { a ->
                put(
                    JSONObject().apply {
                        put("id", a.id); put("name", a.name); put("kind", a.kind)
                        put("amountCents", a.amountCents); put("date", a.date)
                        put("note", a.note); put("createdAt", a.createdAt)
                    }
                )
            }
        })
        payload.budget?.let { b ->
            data.put(
                "budget",
                JSONObject().apply {
                    put("monthlyLimitCents", b.monthlyLimitCents); put("enabled", b.enabled)
                    put("warn75", b.warn75); put("warn90", b.warn90); put("warn100", b.warn100)
                    put("notified75For", b.notified75For)
                    put("notified90For", b.notified90For)
                    put("notified100For", b.notified100For)
                }
            )
        }
        payload.settings?.let { s ->
            data.put(
                "settings",
                JSONObject().apply {
                    put("themeMode", s.themeMode); put("currency", s.currency)
                    put("currencySymbol", s.currencySymbol)
                    put("notificationsEnabled", s.notificationsEnabled)
                    put("appLockEnabled", s.appLockEnabled)
                    // Security: the PIN hash never leaves the device inside a backup file.
                    put("pinHash", ""); put("pinSalt", "")
                    put("biometricEnabled", s.biometricEnabled)
                    put("autoExpenseOnBillPaid", s.autoExpenseOnBillPaid)
                    put("autoCreateRecurring", s.autoCreateRecurring)
                    put("lastBackupAt", s.lastBackupAt)
                    put("language", s.language)
                    put("autoLockMinutes", s.autoLockMinutes)
                    put("activeAreaId", s.activeAreaId)
                    put("carModuleEnabled", s.carModuleEnabled)
                    put("alertSettings", s.alertSettings)
                    put("dashboardWidgets", s.dashboardWidgets)
                    put("duplicateWarningsEnabled", s.duplicateWarningsEnabled)
                    put("family", s.family)
                    put("fxRates", s.fxRates)
                    put("openingBalanceCents", s.openingBalanceCents)
                }
            )
        }
        root.put("data", data)
        return root
    }

    /** Writes a backup into the app's private backup folder (works fully offline). */
    suspend fun createLocalBackup(context: Context, repository: Repository): File =
        withContext(Dispatchers.IO) {
            val payload = collect(repository)
            val text = toJson(payload).toString(2)
            val file = File(backupDir(context), defaultFileName())
            // Erst in eine temporaere Datei schreiben und dann umbenennen:
            // so sieht niemand (auch das Wiederherstellen nicht) eine halbe Datei.
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                tmp.delete()
            }
            repository.updateSettings { it.copy(lastBackupAt = System.currentTimeMillis()) }
            file
        }

    /** Gelernten Zuordnungen (Kategorie-Regeln) lesen - nie abbrechen, wenn fehlt. */
    private suspend fun daoRules(repository: Repository): List<CategoryRuleEntity> =
        withContext(Dispatchers.IO) { runCatching { repository.allCategoryRules() }.getOrDefault(emptyList()) }

    suspend fun collect(repository: Repository): BackupPayload = withContext(Dispatchers.IO) {
        BackupPayload(
            categories = repository.categories.snapshot(),
            expenses = repository.expenses.snapshot(),
            incomes = repository.incomes.snapshot(),
            bills = repository.bills.snapshot(),
            payments = repository.billPayments.snapshot(),
            rules = repository.recurringRules.snapshot(),
            budget = repository.budget.snapshotOrNull(),
            settings = repository.settings.snapshotOrNull(),
            areas = repository.areas.snapshot(),
            goals = repository.goals.snapshot(),
            subscriptions = repository.subscriptions.snapshot(),
            categoryBudgets = repository.categoryBudgets.snapshot(),
            categoryRules = runCatching { daoRules(repository) }.getOrDefault(emptyList()),
            documents = runCatching { repository.allDocuments() }.getOrDefault(emptyList()),
            assets = runCatching { repository.allAssets() }.getOrDefault(emptyList())
        )
    }

    suspend fun writeToUri(context: Context, uri: Uri, repository: Repository): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = collect(repository)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(toJson(payload).toString(2).toByteArray(Charsets.UTF_8))
                } ?: return@runCatching false
                true
            }.getOrDefault(false)
        }

    fun listLocalBackups(context: Context): List<File> =
        backupDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun deleteLocalBackup(file: File): Boolean = runCatching { file.delete() }.getOrDefault(false)

    // ------------------------------ reading -----------------------------------

    fun readFromUri(context: Context, uri: Uri): BackupPayload? =
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() } ?: return null
            parse(text)
        }.getOrNull()

    fun readFromFile(file: File): BackupPayload? =
        runCatching { parse(file.readText(Charsets.UTF_8)) }.getOrNull()

    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            } ?: S(context, "backup", "Backup")
        }.getOrDefault(S(context, "backup", "Backup"))

    /** Never throws: a damaged or foreign file simply returns null. */
    fun parse(text: String): BackupPayload? = runCatching {
        parseInternal(text)
    }.getOrNull()

    private fun parseInternal(text: String): BackupPayload? {
        val root = JSONObject(text)
        val tag = root.optString("app", "")
        if (tag != APP_TAG && tag != LEGACY_TAG) return null
        val data = root.optJSONObject("data") ?: return null

        fun <T> list(key: String, map: (JSONObject) -> T): List<T> {
            val array = data.optJSONArray(key) ?: return emptyList()
            val out = ArrayList<T>(array.length())
            for (i in 0 until array.length()) out.add(map(array.getJSONObject(i)))
            return out
        }

        val categories = list("categories") { o ->
            CategoryEntity(
                id = o.optLong("id"),
                name = o.optString("name", "Sonstiges"),
                emoji = o.optString("emoji", "📦"),
                type = o.optString("type", "EXPENSE"),
                isFixed = o.optBoolean("isFixed"),
                isCustom = o.optBoolean("isCustom"),
                sortOrder = o.optInt("sortOrder")
            )
        }
        val expenses = list("expenses") { o ->
            ExpenseEntity(
                id = o.optLong("id"),
                amountCents = o.optLong("amountCents"),
                categoryId = o.optLong("categoryId"),
                date = o.optLong("date"),
                note = o.optString("note", ""),
                paid = o.optBoolean("paid", true),
                fixedOverride = if (o.has("fixedOverride") && !o.isNull("fixedOverride")) {
                    o.optBoolean("fixedOverride")
                } else null,
                recurringId = o.optLong("recurringId").takeIf { it != 0L },
                occurrenceDate = o.optLong("occurrenceDate").takeIf { it != 0L },
                areaId = o.optLong("areaId"),
                receiptUri = o.optString("receiptUri", ""),
                createdAt = o.optLong("createdAt")
            )
        }
        val incomes = list("incomes") { o ->
            IncomeEntity(
                id = o.optLong("id"),
                amountCents = o.optLong("amountCents"),
                categoryId = o.optLong("categoryId"),
                date = o.optLong("date"),
                note = o.optString("note", ""),
                recurringId = o.optLong("recurringId").takeIf { it != 0L },
                occurrenceDate = o.optLong("occurrenceDate").takeIf { it != 0L },
                areaId = o.optLong("areaId"),
                createdAt = o.optLong("createdAt")
            )
        }
        val bills = list("bills") { o ->
            BillEntity(
                id = o.optLong("id"),
                name = o.optString("name", "Rechnung"),
                amountCents = o.optLong("amountCents"),
                dueDate = o.optLong("dueDate"),
                categoryId = o.optLong("categoryId"),
                isRecurring = o.optBoolean("isRecurring"),
                frequency = o.optString("frequency", "MONTHLY"),
                interval = o.optInt("interval", 1),
                customDays = o.optInt("customDays", 30),
                reminderEnabled = o.optBoolean("reminderEnabled", true),
                reminderDaysBefore = o.optInt("reminderDaysBefore", 1),
                reminderHour = o.optInt("reminderHour", 9),
                reminderMinute = o.optInt("reminderMinute", 0),
                status = o.optString("status", "OPEN"),
                note = o.optString("note", ""),
                recurringId = o.optLong("recurringId").takeIf { it != 0L },
                // --- Rechnungsscan ---
                vendor = o.optString("vendor", ""),
                invoiceNumber = o.optString("invoiceNumber", ""),
                invoiceDate = if (o.has("invoiceDate")) o.optLong("invoiceDate") else null,
                iban = o.optString("iban", ""),
                bic = o.optString("bic", ""),
                customerNumber = o.optString("customerNumber", ""),
                imagePath = o.optString("imagePath", ""),
                netCents = if (o.has("netCents")) o.optLong("netCents") else null,
                vatCents = if (o.has("vatCents")) o.optLong("vatCents") else null,
                ocrText = o.optString("ocrText", ""),
                confidence = o.optString("confidence", ""),
                fieldBoxes = o.optString("fieldBoxes", "")
            )
        }
        val payments = list("billPayments") { o ->
            BillPaymentEntity(
                id = o.optLong("id"),
                billId = o.optLong("billId"),
                billName = o.optString("billName", ""),
                amountCents = o.optLong("amountCents"),
                categoryId = o.optLong("categoryId"),
                dueDate = o.optLong("dueDate"),
                paidDate = o.optLong("paidDate"),
                note = o.optString("note", ""),
                expenseId = o.optLong("expenseId").takeIf { it != 0L }
            )
        }
        val rules = list("recurringRules") { o ->
            RecurringRuleEntity(
                id = o.optLong("id"),
                kind = o.optString("kind", "EXPENSE"),
                name = o.optString("name", ""),
                amountCents = o.optLong("amountCents"),
                categoryId = o.optLong("categoryId"),
                note = o.optString("note", ""),
                frequency = o.optString("frequency", "MONTHLY"),
                interval = o.optInt("interval", 1),
                customDays = o.optInt("customDays", 30),
                dayOfMonth = o.optInt("dayOfMonth", 1),
                monthOfYear = o.optInt("monthOfYear", 1),
                startDate = o.optLong("startDate"),
                endDate = o.optLong("endDate").takeIf { it != 0L },
                active = o.optBoolean("active", true),
                areaId = o.optLong("areaId"),
                lastGenerated = o.optLong("lastGenerated").takeIf { it != 0L },
                createdAt = o.optLong("createdAt")
            )
        }
        val budget = data.optJSONObject("budget")?.let { b ->
            BudgetEntity(
                monthlyLimitCents = b.optLong("monthlyLimitCents"),
                enabled = b.optBoolean("enabled", true),
                warn75 = b.optBoolean("warn75", true),
                warn90 = b.optBoolean("warn90", true),
                warn100 = b.optBoolean("warn100", true),
                notified75For = b.optString("notified75For", ""),
                notified90For = b.optString("notified90For", ""),
                notified100For = b.optString("notified100For", "")
            )
        }
        val settings = data.optJSONObject("settings")?.let { s ->
            SettingsEntity(
                themeMode = s.optString("themeMode", "SYSTEM"),
                currency = s.optString("currency", "EUR"),
                currencySymbol = s.optString("currencySymbol", "€"),
                notificationsEnabled = s.optBoolean("notificationsEnabled", true),
                appLockEnabled = s.optBoolean("appLockEnabled"),
                pinHash = s.optString("pinHash", ""),
                pinSalt = s.optString("pinSalt", ""),
                biometricEnabled = s.optBoolean("biometricEnabled"),
                autoExpenseOnBillPaid = s.optBoolean("autoExpenseOnBillPaid", true),
                autoCreateRecurring = s.optBoolean("autoCreateRecurring", true),
                lastBackupAt = s.optLong("lastBackupAt"),
                // Die Sprache des Geraets bleibt beim Wiederherstellen erhalten,
                // wenn ein altes Backup dieses Feld noch nicht enthaelt.
                language = s.optString("language", "").ifBlank { "de" },
                autoLockMinutes = s.optInt("autoLockMinutes", 0),
                activeAreaId = s.optLong("activeAreaId"),
                carModuleEnabled = s.optBoolean("carModuleEnabled", false),
                alertSettings = s.optString("alertSettings", ""),
                dashboardWidgets = s.optString("dashboardWidgets", ""),
                duplicateWarningsEnabled = s.optBoolean("duplicateWarningsEnabled", true),
                family = s.optString("family", ""),
                fxRates = s.optString("fxRates", ""),
                openingBalanceCents = s.optLong("openingBalanceCents", 0L)
            )
        }
        val areas = list("areas") { o ->
            AreaEntity(
                id = o.optLong("id"),
                name = o.optString("name", "Persönlich"),
                emoji = o.optString("emoji", "\uD83D\uDC64"),
                sortOrder = o.optInt("sortOrder"),
                createdAt = o.optLong("createdAt")
            )
        }
        val goals = list("savingGoals") { o ->
            SavingGoalEntity(
                id = o.optLong("id"),
                name = o.optString("name", ""),
                emoji = o.optString("emoji", "\uD83C\uDFAF"),
                targetCents = o.optLong("targetCents"),
                savedCents = o.optLong("savedCents"),
                targetDate = o.optLong("targetDate").takeIf { it != 0L },
                areaId = o.optLong("areaId"),
                note = o.optString("note", ""),
                archived = o.optBoolean("archived", false),
                createdAt = o.optLong("createdAt")
            )
        }
        val subscriptions = list("subscriptions") { o ->
            SubscriptionEntity(
                id = o.optLong("id"),
                name = o.optString("name", ""),
                emoji = o.optString("emoji", "\uD83D\uDD01"),
                amountCents = o.optLong("amountCents"),
                frequency = o.optString("frequency", "MONTHLY"),
                interval = o.optInt("interval", 1),
                customDays = o.optInt("customDays", 30),
                nextPayment = o.optLong("nextPayment"),
                startDate = o.optLong("startDate"),
                endDate = o.optLong("endDate").takeIf { it != 0L },
                categoryId = o.optLong("categoryId"),
                reminderEnabled = o.optBoolean("reminderEnabled", true),
                reminderDaysBefore = o.optInt("reminderDaysBefore", 3),
                cancelByDate = o.optLong("cancelByDate").takeIf { it != 0L },
                note = o.optString("note", ""),
                active = o.optBoolean("active", true),
                areaId = o.optLong("areaId"),
                notifiedFor = o.optString("notifiedFor", ""),
                createdAt = o.optLong("createdAt")
            )
        }
        val categoryBudgets = list("categoryBudgets") { o ->
            CategoryBudgetEntity(
                categoryId = o.optLong("categoryId"),
                monthlyLimitCents = o.optLong("monthlyLimitCents"),
                enabled = o.optBoolean("enabled", true)
            )
        }
        val categoryRules = list("categoryRules") { o ->
            CategoryRuleEntity(
                id = o.optLong("id"),
                token = o.optString("token", ""),
                categoryId = o.optLong("categoryId"),
                isIncome = o.optBoolean("isIncome", false),
                hits = o.optInt("hits", 1),
                updatedAt = o.optLong("updatedAt")
            )
        }
        // Dokumente (Modul 18) und Vermoegenswerte (Modul 24) - bei alten
        // Sicherungen fehlen diese Felder, dann bleiben die Listen leer.
        val documents = list("documents") { o ->
            DocumentEntity(
                id = o.optLong("id"),
                title = o.optString("title", ""),
                kind = o.optString("kind", DocumentKind.OTHER.name),
                path = o.optString("path", ""),
                date = o.optLong("date"),
                tags = o.optString("tags", ""),
                note = o.optString("note", ""),
                amountCents = if (o.has("amountCents") && !o.isNull("amountCents")) {
                    o.optLong("amountCents")
                } else null,
                merchant = o.optString("merchant", ""),
                linkedExpenseId = o.optLong("linkedExpenseId"),
                linkedBillId = o.optLong("linkedBillId"),
                mimeType = o.optString("mimeType", ""),
                createdAt = o.optLong("createdAt")
            )
        }
        val assets = list("assets") { o ->
            AssetEntity(
                id = o.optLong("id"),
                name = o.optString("name", ""),
                kind = o.optString("kind", AssetKind.BANK.name),
                amountCents = o.optLong("amountCents"),
                date = o.optLong("date"),
                note = o.optString("note", ""),
                createdAt = o.optLong("createdAt")
            )
        }
        return BackupPayload(
            categories, expenses, incomes, bills, payments, rules, budget, settings,
            areas, goals, subscriptions, categoryBudgets, categoryRules,
            documents, assets
        )
    }

    // ------------------------------ restoring ---------------------------------

    data class RestoreResult(val inserted: Int, val mode: RestoreMode)

    enum class RestoreMode { REPLACE, MERGE }

    suspend fun restore(
        repository: Repository,
        payload: BackupPayload,
        mode: RestoreMode
    ): RestoreResult = withContext(Dispatchers.IO) {
        var count = 0
        if (mode == RestoreMode.REPLACE) {
            // Die eingestellte Sprache bleibt erhalten - ein Backup soll die
            // Anzeigesprache nicht unvermittelt umstellen.
            val aktuell = repository.settings.snapshotOrNull()
            val sprache = aktuell?.language
            repository.wipeAll()
            repository.replaceAllCategories(payload.categories)
            repository.insertAll(
                expenses = payload.expenses,
                incomes = payload.incomes,
                bills = payload.bills,
                payments = payload.payments,
                rules = payload.rules,
                budget = payload.budget,
                settings = payload.settings?.let {
                    // Language and app lock (PIN, biometrics) always stay as set on this device:
                    // a backup file must never be able to change or remove the lock.
                    it.copy(
                        language = if (sprache.isNullOrBlank()) it.language else sprache,
                        appLockEnabled = aktuell?.appLockEnabled ?: false,
                        pinHash = aktuell?.pinHash.orEmpty(),
                        pinSalt = aktuell?.pinSalt.orEmpty(),
                        biometricEnabled = aktuell?.biometricEnabled ?: false
                    )
                },
                areas = payload.areas,
                goals = payload.goals,
                subscriptions = payload.subscriptions,
                categoryBudgets = payload.categoryBudgets,
                categoryRules = payload.categoryRules
            )
            // Dokumente (Modul 18) und Vermoegenswerte (Modul 24) gehoeren
            // genauso zur Sicherung wie die Buchungen.
            for (d in payload.documents) {
                if (repository.saveDocument(d.copy(id = 0)) > 0) count++
            }
            for (a in payload.assets) {
                if (repository.saveAsset(a.copy(id = 0)) > 0) count++
            }
            count = payload.expenses.size + payload.incomes.size + payload.bills.size +
                payload.payments.size + payload.rules.size + payload.categories.size +
                payload.areas.size + payload.goals.size + payload.subscriptions.size +
                payload.documents.size + payload.assets.size
        } else {
            // MERGE: keep everything that exists, add the backup on top.
            // Categories are matched by name + type so no duplicates appear.
            val categoryMap = HashMap<Long, Long>()
            for (category in payload.categories) {
                val existingId = repository.addCategory(category.name, category.emoji, category.typeEnum())
                categoryMap[category.id] = existingId
            }
            val existingCategories = repository.categories.snapshot().associate { it.id to it.id }
            fun mapCategory(id: Long): Long {
                categoryMap[id]?.let { return it }
                return if (existingCategories.containsKey(id)) id else id
            }
            for (e in payload.expenses) {
                if (repository.addExpense(e.copy(id = 0, categoryId = mapCategory(e.categoryId))) > 0) count++
            }
            for (i in payload.incomes) {
                if (repository.addIncome(i.copy(id = 0, categoryId = mapCategory(i.categoryId))) > 0) count++
            }
            for (b in payload.bills) {
                if (repository.addBill(b.copy(id = 0, categoryId = mapCategory(b.categoryId))) > 0) count++
            }
            for (p in payload.payments) {
                if (repository.addBillPayment(p.copy(id = 0)) > 0) count++
            }
            for (r in payload.rules) {
                if (repository.addRecurringRule(r.copy(id = 0, categoryId = mapCategory(r.categoryId))) > 0) count++
            }
            // Bereiche, Sparziele und Abos ergaenzen - ohne Duplikate.
            val areaMap = HashMap<Long, Long>()
            for (area in payload.areas) {
                val vorhanden = repository.areas.snapshot().firstOrNull { it.name == area.name }
                areaMap[area.id] = vorhanden?.id ?: repository.addArea(area.name, area.emoji)
            }
            fun mapArea(id: Long): Long = areaMap[id] ?: id
            count += areaMap.size
            val vorhandeneZiele = repository.goals.snapshot()
            for (g in payload.goals) {
                val bekannt = vorhandeneZiele.any {
                    it.name == g.name && it.targetCents == g.targetCents
                } || repository.goals.snapshot().any {
                    it.name == g.name && it.targetCents == g.targetCents
                }
                if (bekannt) continue
                if (repository.saveGoal(
                        g.copy(id = 0, areaId = mapArea(g.areaId))
                    ) > 0
                ) count++
            }
            for (s in payload.subscriptions) {
                val bekannt = repository.subscriptions.snapshot().any {
                    it.name == s.name && it.amountCents == s.amountCents &&
                        it.nextPayment == s.nextPayment
                }
                if (bekannt) continue
                if (repository.saveSubscription(
                        s.copy(id = 0, categoryId = mapCategory(s.categoryId), areaId = mapArea(s.areaId))
                    ) > 0
                ) count++
            }
            for (cb in payload.categoryBudgets) {
                val neu = mapCategory(cb.categoryId)
                val bekannt = repository.categoryBudgets.snapshot().any { it.categoryId == neu }
                if (bekannt) continue
                repository.setCategoryBudget(neu, cb.monthlyLimitCents, cb.enabled)
                count++
            }
            // Dokumente nur ergaenzen, wenn sie nicht schon vorhanden sind
            val vorhandeneDokumente = repository.documents.snapshot()
            for (d in payload.documents) {
                val bekannt = vorhandeneDokumente.any {
                    it.title == d.title && it.date == d.date && it.path == d.path
                }
                if (bekannt) continue
                if (repository.saveDocument(d.copy(id = 0)) > 0) count++
            }
            // Vermoegenswerte nach Name und Art zusammenfuehren
            val vorhandeneWerte = repository.assets.snapshot()
            for (a in payload.assets) {
                val bekannt = vorhandeneWerte.any { it.name == a.name && it.kind == a.kind }
                if (bekannt) continue
                if (repository.saveAsset(a.copy(id = 0)) > 0) count++
            }
        }
        repository.seedIfEmpty()
        RestoreResult(count, mode)
    }
}

private fun CategoryEntity.typeEnum() =
    runCatching { com.finovo.app.data.CategoryType.valueOf(type) }
        .getOrDefault(com.finovo.app.data.CategoryType.EXPENSE)

private suspend fun <T> Flow<List<T>>.snapshot(): List<T> = firstOrNull() ?: emptyList()

private suspend fun <T> Flow<T?>.snapshotOrNull(): T? = firstOrNull()

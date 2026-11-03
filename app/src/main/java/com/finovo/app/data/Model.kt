package com.finovo.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ---------------------------------------------------------------------------
// Enums (stored as Strings so Room needs no type converters)
// ---------------------------------------------------------------------------

enum class CategoryType { EXPENSE, INCOME }

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

enum class BillStatus { OPEN, PAID }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class RecurringKind { EXPENSE, INCOME, BILL }

enum class DateFilter { TODAY, WEEK, MONTH, YEAR, CUSTOM, ALL }

enum class StatsPeriod { THIS_MONTH, LAST_MONTH, THIS_YEAR, LAST_12_MONTHS, CUSTOM }

enum class NotificationType { BILL_REMINDER, BILL_DUE, BUDGET_WARNING, RECURRING }

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "type", "parentId"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val type: String = CategoryType.EXPENSE.name,
    /** Fixed costs ("Fixkosten") are separated from variable costs in statistics. */
    val isFixed: Boolean = false,
    val isCustom: Boolean = false,
    val sortOrder: Int = 0,
    /** 0 = Hauptkategorie, sonst die ID der uebergeordneten Kategorie. */
    val parentId: Long = 0
)

@Entity(
    tableName = "expenses",
    indices = [
        Index("categoryId"),
        Index("date"),
        Index(value = ["recurringId", "occurrenceDate"], unique = true)
    ]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Amount in cents - never a floating point number. */
    val amountCents: Long = 0,
    val categoryId: Long = 0,
    /** Day, stored as epochDay (days since 1970-01-01). */
    val date: Long = 0,
    val note: String = "",
    val paid: Boolean = true,
    /** null = follow the category setting, true/false = explicit fixed/variable choice. */
    val fixedOverride: Boolean? = null,
    val areaId: Long = 0,
    val receiptUri: String = "",
    val recurringId: Long? = null,
    val occurrenceDate: Long? = null,
    val createdAt: Long = 0,
    /** Haendler/Empfaenger (getrennt von der freien Notiz). */
    val merchant: String = "",
    /** Unterkategorie (Kategorie-ID mit parentId). */
    val subcategoryId: Long = 0,
    /** Zahlungsart: CASH, CARD, TRANSFER, DIRECT_DEBIT, OTHER. */
    val paymentMethod: String = "",
    /** Freie Schlagworte, kommasepariert. */
    val tags: String = "",
    /** Ort - nur wenn der Nutzer ihn angibt. */
    val location: String = ""
)

@Entity(
    tableName = "incomes",
    indices = [
        Index("categoryId"),
        Index("date"),
        Index(value = ["recurringId", "occurrenceDate"], unique = true)
    ]
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long = 0,
    val categoryId: Long = 0,
    val date: Long = 0,
    val note: String = "",
    val recurringId: Long? = null,
    val occurrenceDate: Long? = null,
    val areaId: Long = 0,
    val createdAt: Long = 0,
    val merchant: String = "",
    val subcategoryId: Long = 0,
    val paymentMethod: String = "",
    val tags: String = "",
    val location: String = ""
)

@Entity(
    tableName = "bills",
    indices = [Index("categoryId"), Index("dueDate")]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val amountCents: Long = 0,
    /** Due day as epochDay. */
    val dueDate: Long = 0,
    val categoryId: Long = 0,
    val isRecurring: Boolean = false,
    val frequency: String = Frequency.MONTHLY.name,
    val interval: Int = 1,
    val customDays: Int = 30,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Int = 1,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val status: String = BillStatus.OPEN.name,
    val note: String = "",
    // --- Rechnungsscan (OCR) ---
    val vendor: String = "",              // Firmenname / Haendler
    val invoiceNumber: String = "",       // Rechnungsnummer
    val invoiceDate: Long? = null,        // Rechnungsdatum (epochDay)
    val iban: String = "",
    val bic: String = "",
    val customerNumber: String = "",      // Kundennummer
    val imagePath: String = "",           // lokaler Bildpfad (privater App-Speicher)
    // --- Rechnungsanalyse (OCR): nur ergaenzt, bestehende Daten bleiben ---
    val netCents: Long? = null,           // Nettobetrag
    val vatCents: Long? = null,           // MwSt.-Betrag
    val ocrText: String = "",             // erkannter Rohtext (zum Nachpruefen)
    val confidence: String = "",          // Sicherheit je Feld: "amount=96;vendor=90"
    val fieldBoxes: String = "",          // Fundstellen im Bild: "amount=12,44,300,80"
    val areaId: Long = 0,
    val recurringId: Long? = null,
    val createdAt: Long = 0
)

/** One row per actual payment of a bill (keeps history after a bill rolls over). */
@Entity(
    tableName = "bill_payments",
    indices = [Index("billId"), Index("paidDate")]
)
data class BillPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long = 0,
    val billName: String = "",
    val amountCents: Long = 0,
    val categoryId: Long = 0,
    val dueDate: Long = 0,
    val paidDate: Long = 0,
    val note: String = "",
    /** Expense that was created automatically when the bill was paid (if any). */
    val expenseId: Long? = null
)

@Entity(
    tableName = "recurring_rules",
    indices = [Index("active")]
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = RecurringKind.EXPENSE.name,
    val name: String = "",
    val amountCents: Long = 0,
    val categoryId: Long = 0,
    val note: String = "",
    val frequency: String = Frequency.MONTHLY.name,
    val interval: Int = 1,
    val customDays: Int = 30,
    val dayOfMonth: Int = 1,
    val monthOfYear: Int = 1,
    val startDate: Long = 0,
    val endDate: Long? = null,
    val active: Boolean = true,
    val areaId: Long = 0,
    val lastGenerated: Long? = null,
    val createdAt: Long = 0
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: Long = 0,
    val monthlyLimitCents: Long = 0,
    val enabled: Boolean = true,
    val warn75: Boolean = true,
    val warn90: Boolean = true,
    val warn100: Boolean = true,
    val notified75For: String = "",
    val notified90For: String = "",
    val notified100For: String = ""
)

@Entity(
    tableName = "notifications",
    indices = [Index("billId"), Index(value = ["referenceKey"], unique = true)]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long? = null,
    val title: String = "",
    val message: String = "",
    val type: String = NotificationType.BILL_REMINDER.name,
    /** When it should appear, in epoch millis. */
    val triggerAt: Long = 0,
    /** Stable key so a reminder is never scheduled or delivered twice. */
    val referenceKey: String = "",
    val delivered: Boolean = false,
    val createdAt: Long = 0
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Long = 0,
    val themeMode: String = ThemeMode.SYSTEM.name,
    val currency: String = "EUR",
    val currencySymbol: String = "€",
    val notificationsEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricEnabled: Boolean = false,
    val autoExpenseOnBillPaid: Boolean = true,
    val autoCreateRecurring: Boolean = true,
    val lastBackupAt: Long = 0,
    val language: String = "de",
    val autoLockMinutes: Int = 0,
    val activeAreaId: Long = 0,
    val carModuleEnabled: Boolean = false,
    /** Welche Finanz-Alarme aktiv sind: JSON, z. B. {"LOW_BALANCE":true}. */
    val alertSettings: String = "",
    /** Reihenfolge und Sichtbarkeit der Dashboard-Kacheln. */
    val dashboardWidgets: String = "",
    /** Duplikat-Warnung beim Speichern anzeigen. */
    val duplicateWarningsEnabled: Boolean = true,
    /** Rechte der Haushaltsmitglieder: "1:1,1,1;2:1,0,0" */
    val family: String = "",
    /** Wechselkurse als Basispunkte pro EUR: "EUR=10000;USD=10800;..." */
    val fxRates: String = "",
    /** Eroeffnungsbestand fuer die Cashflow-Rechnung (Cent). */
    val openingBalanceCents: Long = 0
)


// ---------------------------------------------------------------------------
// Financial areas (Bereiche) - separate bookkeeping inside one database
// ---------------------------------------------------------------------------

@Entity(tableName = "areas")
data class AreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val emoji: String = "\uD83D\uDCC1",
    val sortOrder: Int = 0,
    val createdAt: Long = 0
)

// ---------------------------------------------------------------------------
// Saving goals (Sparziele)
// ---------------------------------------------------------------------------

@Entity(tableName = "saving_goals")
data class SavingGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val emoji: String = "\uD83C\uDFAF",
    val targetCents: Long = 0,
    val savedCents: Long = 0,
    /** Target day as epochDay; null = no fixed date. */
    val targetDate: Long? = null,
    val areaId: Long = 0,
    val note: String = "",
    val archived: Boolean = false,
    val createdAt: Long = 0
)

// ---------------------------------------------------------------------------
// Subscriptions (Abos)
// ---------------------------------------------------------------------------

@Entity(tableName = "subscriptions", indices = [Index("nextPayment")])
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val emoji: String = "\uD83D\uDD01",
    val amountCents: Long = 0,
    val frequency: String = Frequency.MONTHLY.name,
    val interval: Int = 1,
    val customDays: Int = 30,
    /** Next charge as epochDay. */
    val nextPayment: Long = 0,
    val startDate: Long = 0,
    val endDate: Long? = null,
    val categoryId: Long = 0,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Int = 3,
    val cancelByDate: Long? = null,
    val note: String = "",
    val active: Boolean = true,
    val areaId: Long = 0,
    val notifiedFor: String = "",
    val createdAt: Long = 0
)

// ---------------------------------------------------------------------------
// Per-category budgets
// ---------------------------------------------------------------------------

@Entity(tableName = "category_budgets")
data class CategoryBudgetEntity(
    @PrimaryKey val categoryId: Long = 0,
    val monthlyLimitCents: Long = 0,
    val enabled: Boolean = true
)

// ---------------------------------------------------------------------------
// Bank connection (PSD2 / Open Banking) - local mirror only, no credentials
// ---------------------------------------------------------------------------

@Entity(tableName = "bank_connections", indices = [Index(value = ["externalId"], unique = true)])
data class BankConnectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val institutionId: String = "",
    val bankName: String = "",
    val externalId: String = "",
    val status: String = BankConnectionStatus.LINKED.name,
    val createdAt: Long = 0,
    val lastSyncAt: Long = 0,
    val autoSync: Boolean = true,
    val consentExpiresAt: Long = 0
)

enum class BankConnectionStatus { LINKED, EXPIRED, REVOKED, ERROR }

@Entity(tableName = "bank_accounts", indices = [Index("connectionId")])
data class BankAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val connectionId: Long = 0,
    val externalId: String = "",
    val name: String = "",
    val iban: String = "",
    val currency: String = "EUR",
    val balanceCents: Long = 0,
    val updatedAt: Long = 0
)

@Entity(
    tableName = "bank_transactions",
    indices = [Index(value = ["accountId", "externalId"], unique = true)]
)
data class BankTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long = 0,
    val externalId: String = "",
    val bookingDate: Long = 0,
    val valueDate: Long = 0,
    /** Signed amount in cents: negative = expense, positive = income. */
    val amountCents: Long = 0,
    val currency: String = "EUR",
    val merchant: String = "",
    val description: String = "",
    /** null = not categorised yet. */
    val categoryId: Long? = null,
    val isIncome: Boolean = false,
    val confidence: Float = 0f,
    val expenseId: Long? = null,
    val incomeId: Long? = null,
    val recurringCandidate: Boolean = false,
    val importedAt: Long = 0
)

/** Learned assignment merchant token -> category (user corrections). */
@Entity(tableName = "category_rules", indices = [Index(value = ["token"], unique = true)])
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val token: String = "",
    val categoryId: Long = 0,
    val isIncome: Boolean = false,
    val hits: Int = 1,
    val updatedAt: Long = 0
)

// ---------------------------------------------------------------------------
// Convenience view models used by the UI
// ---------------------------------------------------------------------------

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val emoji: String,
    val cents: Long,
    val percent: Float
)


// ---------------------------------------------------------------------------
// Dokumenten-Center (Belege, Rechnungen, Vertraege, Versicherungen, Sonstiges)
// ---------------------------------------------------------------------------

enum class DocumentKind {
    RECEIPT, INVOICE, CONTRACT, BANK, INSURANCE, OTHER
}

@Entity(
    tableName = "documents",
    indices = [Index("kind"), Index("date"), Index("linkedExpenseId")]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val kind: String = DocumentKind.OTHER.name,
    /** Absoluter Pfad im privaten App-Speicher. */
    val path: String = "",
    /** Tag als epochDay. */
    val date: Long = 0,
    val tags: String = "",
    val note: String = "",
    val amountCents: Long? = null,
    val merchant: String = "",
    val linkedExpenseId: Long = 0,
    val linkedBillId: Long = 0,
    val mimeType: String = "",
    val createdAt: Long = 0
)

// ---------------------------------------------------------------------------
// Aenderungsverlauf (Audit)
// ---------------------------------------------------------------------------

enum class AuditAction {
    TRANSACTION_CREATED,
    TRANSACTION_EDITED,
    TRANSACTION_DELETED,
    BUDGET_CHANGED,
    BILL_PAID,
    RECURRING_CHANGED,
    GOAL_CHANGED,
    DOCUMENT_ADDED,
    DOCUMENT_DELETED,
    SETTINGS_CHANGED
}

@Entity(
    tableName = "audit_log",
    indices = [Index("at"), Index("entityType")]
)
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Zeitpunkt in Millisekunden. */
    val at: Long = 0,
    val action: String = AuditAction.TRANSACTION_CREATED.name,
    val entityType: String = "",
    val entityId: Long = 0,
    /** Lesbare Beschreibung der Aenderung. */
    val detail: String = "",
    val amountCents: Long? = null
)

// ---------------------------------------------------------------------------
// Vermoegen (Net Worth): Werte und Schulden
// ---------------------------------------------------------------------------

enum class AssetKind {
    BANK, CASH, SAVINGS, INVESTMENT, OTHER_ASSET,
    LOAN, CREDIT_CARD, OTHER_DEBT
}

@Entity(
    tableName = "assets",
    indices = [Index("kind")]
)
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val kind: String = AssetKind.BANK.name,
    val amountCents: Long = 0,
    /** Stichtag als epochDay - fuer die Entwicklung ueber die Zeit. */
    val date: Long = 0,
    val note: String = "",
    val createdAt: Long = 0
) {
    val isLiability: Boolean
        get() = kind == AssetKind.LOAN.name ||
            kind == AssetKind.CREDIT_CARD.name ||
            kind == AssetKind.OTHER_DEBT.name
}

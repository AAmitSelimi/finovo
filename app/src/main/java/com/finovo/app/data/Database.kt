package com.finovo.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ExpenseEntity::class,
        IncomeEntity::class,
        BillEntity::class,
        BillPaymentEntity::class,
        RecurringRuleEntity::class,
        BudgetEntity::class,
        NotificationEntity::class,
        SettingsEntity::class,
        AreaEntity::class,
        SavingGoalEntity::class,
        SubscriptionEntity::class,
        CategoryBudgetEntity::class,
        BankConnectionEntity::class,
        BankAccountEntity::class,
        BankTransactionEntity::class,
        CategoryRuleEntity::class
    ,
            DocumentEntity::class, AuditEntity::class, AssetEntity::class
        ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dao(): AppDao

    companion object {

        /**
         * Adds the new modules (areas, saving goals, subscriptions,
         * per-category budgets, receipts, car module, language, auto lock).
         * Existing rows keep their data - every new column has a default.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN areaId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE incomes ADD COLUMN areaId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE bills ADD COLUMN areaId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE recurring_rules ADD COLUMN areaId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN language TEXT NOT NULL DEFAULT 'de'")
                db.execSQL("ALTER TABLE settings ADD COLUMN autoLockMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN activeAreaId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE settings ADD COLUMN carModuleEnabled INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS areas (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "emoji TEXT NOT NULL, " +
                        "sortOrder INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS saving_goals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "emoji TEXT NOT NULL, " +
                        "targetCents INTEGER NOT NULL, " +
                        "savedCents INTEGER NOT NULL, " +
                        "targetDate INTEGER, " +
                        "areaId INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "archived INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS subscriptions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "emoji TEXT NOT NULL, " +
                        "amountCents INTEGER NOT NULL, " +
                        "frequency TEXT NOT NULL, " +
                        "interval INTEGER NOT NULL, " +
                        "customDays INTEGER NOT NULL, " +
                        "nextPayment INTEGER NOT NULL, " +
                        "startDate INTEGER NOT NULL, " +
                        "endDate INTEGER, " +
                        "categoryId INTEGER NOT NULL, " +
                        "reminderEnabled INTEGER NOT NULL, " +
                        "reminderDaysBefore INTEGER NOT NULL, " +
                        "cancelByDate INTEGER, " +
                        "note TEXT NOT NULL, " +
                        "active INTEGER NOT NULL, " +
                        "areaId INTEGER NOT NULL, " +
                        "notifiedFor TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_subscriptions_nextPayment ON subscriptions (nextPayment)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS category_budgets (" +
                        "categoryId INTEGER PRIMARY KEY NOT NULL, " +
                        "monthlyLimitCents INTEGER NOT NULL, " +
                        "enabled INTEGER NOT NULL)"
                )
            }
        }

        /** Adds the invoice-scan columns to existing bills (data preserving). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN vendor TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN invoiceNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN invoiceDate INTEGER")
                db.execSQL("ALTER TABLE bills ADD COLUMN iban TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN bic TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN customerNumber TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Adds the bank-connection tables (data preserving). */
        /**
         * Rechnungsanalyse: nur neue Spalten, bestehende Rechnungen bleiben
         * unveraendert (ALTER TABLE ... ADD COLUMN).
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN netCents INTEGER")
                db.execSQL("ALTER TABLE bills ADD COLUMN vatCents INTEGER")
                db.execSQL("ALTER TABLE bills ADD COLUMN ocrText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN confidence TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bills ADD COLUMN fieldBoxes TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v7: Transaktionsdetails (Haendler, Unterkategorie, Zahlungsart,
         * Schlagworte, Ort), Unterkategorien, Dokumenten-Center,
         * Aenderungsverlauf, Vermoegen sowie neue Einstellungen.
         *
         * Rein ergaenzend - bestehende Daten bleiben unberuehrt.
         */
        /**
         * Modul 12 (Unterkategorien): derselbe Name darf unter
         * verschiedenen Hauptkategorien vorkommen ("Auto - Versicherung"
         * und "Wohnen - Versicherung"). Der Eindeutigkeits-Index wird
         * deshalb um parentId erweitert. Bestandsdaten bleiben unberuehrt.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val alte = ArrayList<String>()
                db.query(
                    "SELECT name FROM sqlite_master WHERE type = 'index' " +
                        "AND tbl_name = 'categories'"
                ).use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0)
                        if (!name.isNullOrBlank() && !name.startsWith("sqlite_")) alte += name
                    }
                }
                alte.forEach { db.execSQL("DROP INDEX IF EXISTS $it") }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_categories_name_type_parentId` " +
                        "ON `categories` (`name`, `type`, `parentId`)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE expenses ADD COLUMN merchant TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN subcategoryId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN location TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE incomes ADD COLUMN merchant TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE incomes ADD COLUMN subcategoryId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE incomes ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE incomes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE incomes ADD COLUMN location TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE settings ADD COLUMN alertSettings TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN dashboardWidgets TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN duplicateWarningsEnabled INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL("ALTER TABLE settings ADD COLUMN family TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE settings ADD COLUMN fxRates TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE settings ADD COLUMN openingBalanceCents INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS documents (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "path TEXT NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "tags TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "amountCents INTEGER, " +
                        "merchant TEXT NOT NULL, " +
                        "linkedExpenseId INTEGER NOT NULL, " +
                        "linkedBillId INTEGER NOT NULL, " +
                        "mimeType TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_kind ON documents (kind)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_date ON documents (date)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_linkedExpenseId ON documents (linkedExpenseId)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS audit_log (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "at INTEGER NOT NULL, " +
                        "action TEXT NOT NULL, " +
                        "entityType TEXT NOT NULL, " +
                        "entityId INTEGER NOT NULL, " +
                        "detail TEXT NOT NULL, " +
                        "amountCents INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_log_at ON audit_log (at)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_audit_log_entityType ON audit_log (entityType)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS assets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "kind TEXT NOT NULL, " +
                        "amountCents INTEGER NOT NULL, " +
                        "date INTEGER NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_assets_kind ON assets (kind)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bank_connections (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "institutionId TEXT NOT NULL, " +
                        "bankName TEXT NOT NULL, " +
                        "externalId TEXT NOT NULL, " +
                        "status TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "lastSyncAt INTEGER NOT NULL, " +
                        "autoSync INTEGER NOT NULL, " +
                        "consentExpiresAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bank_connections_externalId ON bank_connections (externalId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bank_accounts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "connectionId INTEGER NOT NULL, " +
                        "externalId TEXT NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "iban TEXT NOT NULL, " +
                        "currency TEXT NOT NULL, " +
                        "balanceCents INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_accounts_connectionId ON bank_accounts (connectionId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bank_transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "accountId INTEGER NOT NULL, " +
                        "externalId TEXT NOT NULL, " +
                        "bookingDate INTEGER NOT NULL, " +
                        "valueDate INTEGER NOT NULL, " +
                        "amountCents INTEGER NOT NULL, " +
                        "currency TEXT NOT NULL, " +
                        "merchant TEXT NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "categoryId INTEGER, " +
                        "isIncome INTEGER NOT NULL, " +
                        "confidence REAL NOT NULL, " +
                        "expenseId INTEGER, " +
                        "incomeId INTEGER, " +
                        "recurringCandidate INTEGER NOT NULL, " +
                        "importedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bank_transactions_accountId_externalId ON bank_transactions (accountId, externalId)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS category_rules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "token TEXT NOT NULL, " +
                        "categoryId INTEGER NOT NULL, " +
                        "isIncome INTEGER NOT NULL, " +
                        "hits INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_category_rules_token ON category_rules (token)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finovo.db"
                )
                    .addMigrations(
                        MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                        MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                    )
                    // Only very old (version 1) databases are recreated; every
                    // installed version 2 database is migrated safely.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

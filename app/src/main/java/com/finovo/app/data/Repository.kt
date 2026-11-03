package com.finovo.app.data

import com.finovo.app.util.DefaultCategories
import com.finovo.app.util.Dates
import com.finovo.app.util.Recurrence
import com.finovo.app.util.RecurrenceSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

/**
 * Single source of truth for all persisted data.
 * Everything runs on Dispatchers.IO and every write is immediately visible
 * through the Room Flows the UI observes.
 */
class Repository(private val dao: AppDao) {

    // ---------------------------- observations ------------------------------

    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val expenses: Flow<List<ExpenseEntity>> = dao.observeExpenses()
    val incomes: Flow<List<IncomeEntity>> = dao.observeIncomes()
    val bills: Flow<List<BillEntity>> = dao.observeBills()
    val billPayments: Flow<List<BillPaymentEntity>> = dao.observeBillPayments()
    val recurringRules: Flow<List<RecurringRuleEntity>> = dao.observeRecurringRules()
    val budget: Flow<BudgetEntity?> = dao.observeBudget()
    val settings: Flow<SettingsEntity?> = dao.observeSettings()
    val notifications: Flow<List<NotificationEntity>> = dao.observeNotifications()
    val areas: Flow<List<AreaEntity>> = dao.observeAreas()
    val goals: Flow<List<SavingGoalEntity>> = dao.observeGoals()
    val subscriptions: Flow<List<SubscriptionEntity>> = dao.observeSubscriptions()
    val categoryBudgets: Flow<List<CategoryBudgetEntity>> = dao.observeCategoryBudgets()
    val documents: Flow<List<DocumentEntity>> = dao.observeDocuments()
    val auditLog: Flow<List<AuditEntity>> = dao.observeAudit()
    val assets: Flow<List<AssetEntity>> = dao.observeAssets()

    // ------------------------------- seeding --------------------------------

    /** Idempotent: seeds settings, budget and default categories on first start. */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        dao.insertSettings(SettingsEntity())
        dao.upsertBudget(BudgetEntity())
        for (c in DefaultCategories.build()) dao.insertCategory(c)
    }

    suspend fun ensureCategory(
        name: String,
        emoji: String,
        type: CategoryType
    ): Long = withContext(Dispatchers.IO) {
        val existing = categories.first().firstOrNull {
            it.name.equals(name, ignoreCase = true) && it.type == type.name
        }
        if (existing != null) return@withContext existing.id
        val id = dao.insertCategory(
            CategoryEntity(
                name = name,
                emoji = emoji,
                type = type.name,
                isCustom = true,
                sortOrder = 100
            )
        )
        if (id <= 0L) {
            categories.first().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: 0L
        } else id
    }

    // ----------------------------- categories --------------------------------

    suspend fun addCategory(
        name: String,
        emoji: String,
        type: CategoryType,
        isFixed: Boolean = false,
        parentId: Long = 0L
    ): Long = withContext(Dispatchers.IO) {
        val existing = categories.first().firstOrNull {
            it.name.equals(name.trim(), ignoreCase = true) && it.type == type.name &&
                it.parentId == parentId
        }
        if (existing != null) return@withContext existing.id
        val id = dao.insertCategory(
            CategoryEntity(
                name = name.trim(),
                emoji = emoji,
                type = type.name,
                isFixed = isFixed,
                isCustom = true,
                sortOrder = 100,
                parentId = parentId
            )
        )
        // -1 kann durch den Eindeutigkeits-Index entstehen (zwei gleichzeitige
        // Vorgaenge). Dann gilt die Kategorie als nicht angelegt.
        if (id <= 0L) 0L else id
    }

    /**
     * Gibt false zurueck, wenn der Name auf dieser Ebene schon vergeben ist.
     * So bricht die App nicht ab, sondern kann einen Hinweis zeigen.
     */
    suspend fun updateCategory(item: CategoryEntity): Boolean = withContext(Dispatchers.IO) {
        val belegt = categories.first().any {
            it.id != item.id &&
                it.name.equals(item.name.trim(), ignoreCase = true) &&
                it.type == item.type && it.parentId == item.parentId
        }
        if (belegt) return@withContext false
        runCatching { dao.updateCategory(item) }.isSuccess
    }

    /** Returns false when the category is still in use. */
    suspend fun canDeleteCategory(id: Long): Boolean = withContext(Dispatchers.IO) {
        dao.countExpensesInCategory(id) == 0 &&
            dao.countIncomesInCategory(id) == 0 &&
            dao.countBillsInCategory(id) == 0
    }

    suspend fun deleteCategory(item: CategoryEntity) = withContext(Dispatchers.IO) {
        dao.deleteCategory(item)
    }

    // ------------------------------ expenses ---------------------------------

    suspend fun addExpense(item: ExpenseEntity): Long = withContext(Dispatchers.IO) {
        dao.insertExpense(item.copy(createdAt = System.currentTimeMillis()))
    }

    suspend fun updateExpense(item: ExpenseEntity) = withContext(Dispatchers.IO) {
        dao.updateExpense(item)
    }

    suspend fun deleteExpense(item: ExpenseEntity) = withContext(Dispatchers.IO) {
        dao.deleteExpense(item)
    }

    suspend fun deleteExpenseById(id: Long) = withContext(Dispatchers.IO) {
        dao.getExpense(id)?.let { dao.deleteExpense(it) }
    }

    suspend fun setExpensePaid(id: Long, paid: Boolean) = withContext(Dispatchers.IO) {
        dao.getExpense(id)?.let { dao.updateExpense(it.copy(paid = paid)) }
    }

    // ------------------------------- incomes ---------------------------------

    suspend fun addIncome(item: IncomeEntity): Long = withContext(Dispatchers.IO) {
        dao.insertIncome(item.copy(createdAt = System.currentTimeMillis()))
    }

    suspend fun updateIncome(item: IncomeEntity) = withContext(Dispatchers.IO) {
        dao.updateIncome(item)
    }

    suspend fun deleteIncome(item: IncomeEntity) = withContext(Dispatchers.IO) {
        dao.deleteIncome(item)
    }

    suspend fun deleteIncomeById(id: Long) = withContext(Dispatchers.IO) {
        dao.getIncome(id)?.let { dao.deleteIncome(it) }
    }

    // -------------------------------- bills ----------------------------------

    suspend fun addBill(item: BillEntity): Long = withContext(Dispatchers.IO) {
        dao.insertBill(item.copy(createdAt = System.currentTimeMillis()))
    }

    suspend fun updateBill(item: BillEntity) = withContext(Dispatchers.IO) {
        dao.updateBill(item)
    }

    suspend fun deleteBill(item: BillEntity) = withContext(Dispatchers.IO) {
        dao.deleteNotificationsOfBill(item.id)
        dao.deletePaymentsOfBill(item.id)
        dao.deleteBill(item)
    }

    /** Re-opens a bill that was marked as paid. */
    suspend fun markBillOpen(bill: BillEntity) = withContext(Dispatchers.IO) {
        dao.updateBill(bill.copy(status = BillStatus.OPEN.name))
    }

    /**
     * Marks a bill as paid:
     *  - creates a payment history entry
     *  - optionally creates the matching expense
     *  - rolls a recurring bill forward to its next due date (never duplicates it)
     */
    suspend fun markBillPaid(
        bill: BillEntity,
        createExpense: Boolean,
        paidDate: LocalDate = Dates.today(),
        amountCents: Long = bill.amountCents
    ): Long = withContext(Dispatchers.IO) {
        var createdExpenseId: Long? = null
        if (createExpense) {
            createdExpenseId = dao.insertExpense(
                ExpenseEntity(
                    amountCents = amountCents,
                    categoryId = bill.categoryId,
                    date = paidDate.toEpochDay(),
                    note = if (bill.note.isBlank()) bill.name else "${bill.name} – ${bill.note}",
                    paid = true,
                    createdAt = System.currentTimeMillis()
                )
            ).let { if (it > 0) it else null }
        }

        dao.insertBillPayment(
            BillPaymentEntity(
                billId = bill.id,
                billName = bill.name,
                amountCents = amountCents,
                categoryId = bill.categoryId,
                dueDate = bill.dueDate,
                paidDate = paidDate.toEpochDay(),
                note = bill.note,
                expenseId = createdExpenseId
            )
        )

        if (bill.isRecurring) {
            val next = nextDueDate(bill, after = maxOf(paidDate.toEpochDay(), bill.dueDate))
            if (next != null) {
                dao.updateBill(bill.copy(dueDate = next, status = BillStatus.OPEN.name))
            } else {
                dao.updateBill(bill.copy(status = BillStatus.PAID.name))
            }
        } else {
            dao.updateBill(bill.copy(status = BillStatus.PAID.name))
        }
        return@withContext createdExpenseId ?: 0L
    }

    /** Next due date strictly after [after] for a recurring bill, null when it has ended. */
    fun nextDueDate(bill: BillEntity, after: Long): Long? {
        if (!bill.isRecurring) return null
        val spec = RecurrenceSpec(
            frequency = runCatching { Frequency.valueOf(bill.frequency) }.getOrDefault(Frequency.MONTHLY),
            interval = bill.interval.coerceAtLeast(1),
            customDays = bill.customDays.coerceAtLeast(1),
            dayOfMonth = Dates.fromEpochDay(bill.dueDate).dayOfMonth,
            monthOfYear = Dates.fromEpochDay(bill.dueDate).monthValue,
            startDate = Dates.fromEpochDay(bill.dueDate)
        )
        return Recurrence.nextAfter(spec, Dates.fromEpochDay(after))?.toEpochDay()
    }

    suspend fun addBillPayment(payment: BillPaymentEntity): Long = withContext(Dispatchers.IO) {
        dao.insertBillPayment(payment)
    }

    suspend fun deletePayment(payment: BillPaymentEntity) = withContext(Dispatchers.IO) {
        payment.expenseId?.let { dao.deleteExpenseById(it) }
        // @Delete needs an entity; use a targeted query through the DAO wrapper.
        dao.deleteBillPaymentById(payment.id)
    }

    // --------------------------- recurring rules ------------------------------

    suspend fun addRecurringRule(item: RecurringRuleEntity): Long = withContext(Dispatchers.IO) {
        dao.insertRecurringRule(item.copy(createdAt = System.currentTimeMillis()))
    }

    suspend fun updateRecurringRule(item: RecurringRuleEntity) = withContext(Dispatchers.IO) {
        dao.updateRecurringRule(item)
    }

    /** Stops a rule (keeps already created entries). */
    suspend fun setRuleActive(id: Long, active: Boolean) = withContext(Dispatchers.IO) {
        recurringRules.first().firstOrNull { it.id == id }?.let {
            dao.updateRecurringRule(it.copy(active = active))
        }
    }

    /** Deletes a rule and, optionally, all entries it has already created. */
    suspend fun deleteRecurringRule(id: Long, deleteCreatedEntries: Boolean) =
        withContext(Dispatchers.IO) {
            if (deleteCreatedEntries) {
                dao.deleteExpensesOfRule(id)
                dao.deleteIncomesOfRule(id)
            }
            recurringRules.first().firstOrNull { it.id == id }?.let { dao.deleteRecurringRule(it) }
        }

    /**
     * Materialises all recurring expenses/income that are due up to [today].
     * Duplicates are impossible: the (recurringId, occurrenceDate) index is unique and
     * every insert uses OnConflictStrategy.IGNORE.
     */
    suspend fun generateRecurringEntries(today: LocalDate = Dates.today()): Int =
        withContext(Dispatchers.IO) {
            val rules = recurringRules.first().filter { it.active }
            var created = 0
            for (rule in rules) {
                val spec = RecurrenceSpec.from(rule)
                val rangeStart = (rule.lastGenerated ?: (rule.startDate - 1))
                    .coerceAtLeast(rule.startDate - 1)
                val from = Dates.fromEpochDay(max(rangeStart + 1, rule.startDate))
                val to = today
                if (to < from) continue
                val occurrences = Recurrence.occurrences(spec, from, to)
                for (date in occurrences) {
                    val day = date.toEpochDay()
                    val id = when (rule.kind) {
                        RecurringKind.INCOME.name -> dao.insertIncome(
                            IncomeEntity(
                                amountCents = rule.amountCents,
                                categoryId = rule.categoryId,
                                date = day,
                                note = rule.note,
                                recurringId = rule.id,
                                occurrenceDate = day,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        else -> dao.insertExpense(
                            ExpenseEntity(
                                amountCents = rule.amountCents,
                                categoryId = rule.categoryId,
                                date = day,
                                note = rule.note,
                                paid = true,
                                recurringId = rule.id,
                                occurrenceDate = day,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                    }
                    if (id > 0L) created++
                }
                dao.updateRecurringRule(rule.copy(lastGenerated = to.toEpochDay()))
            }
            created
        }

    // ------------------------------- budget -----------------------------------

    suspend fun saveBudget(budget: BudgetEntity) = withContext(Dispatchers.IO) {
        dao.upsertBudget(budget)
    }

    // ------------------------------ settings ----------------------------------

    suspend fun updateSettings(transform: (SettingsEntity) -> SettingsEntity) =
        withContext(Dispatchers.IO) {
            val current = settings.first() ?: SettingsEntity()
            dao.updateSettings(transform(current))
        }

    suspend fun currentSettings(): SettingsEntity =
        withContext(Dispatchers.IO) { settings.first() ?: SettingsEntity() }

    // ---------------------------- notifications --------------------------------

    suspend fun upsertNotification(item: NotificationEntity): Long = withContext(Dispatchers.IO) {
        dao.insertNotification(item)
    }

    suspend fun markNotificationDelivered(key: String) = withContext(Dispatchers.IO) {
        dao.getNotificationByKey(key)?.let { dao.updateNotification(it.copy(delivered = true)) }
    }

    suspend fun notificationByKey(key: String): NotificationEntity? =
        withContext(Dispatchers.IO) { dao.getNotificationByKey(key) }

    suspend fun clearNotificationsOfBill(billId: Long) = withContext(Dispatchers.IO) {
        dao.deleteNotificationsOfBill(billId)
    }

    suspend fun allNotifications() = withContext(Dispatchers.IO) { dao.observeNotifications().first() }

    suspend fun allBills() = withContext(Dispatchers.IO) { dao.observeBills().first() }

    /** Gelernten Kategorie-Zuordnungen - fuer das Backup. */
    suspend fun allCategoryRules() = withContext(Dispatchers.IO) { dao.getAllRules() }

    // ---------------------------- destructive -----------------------------------

    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        dao.deleteAllExpenses()
        dao.deleteAllIncomes()
        dao.deleteAllBills()
        dao.deleteAllBillPayments()
        dao.deleteAllRecurringRules()
        dao.deleteAllNotifications()
        dao.deleteAllCategories()
        dao.deleteAllBudgets()
        dao.deleteAllSettings()
        dao.deleteAllGoals()
        dao.deleteAllSubscriptions()
        dao.deleteAllCategoryBudgets()
        dao.deleteAllAreas()
        // Dokumente (Modul 18): erst die Dateien, dann die Eintraege
        runCatching {
            dao.documents().forEach { d ->
                if (d.path.isNotBlank()) runCatching { java.io.File(d.path).delete() }
            }
        }
        dao.deleteAllDocuments()
        // Vermoegenswerte (Modul 24) und Aenderungsverlauf (Modul 32)
        dao.deleteAllAssets()
        dao.clearAudit()
        seedIfEmpty()
        // Der Standardbereich wird wieder angelegt, damit die App
        // nach dem Loeschen genau so aussieht wie nach der Installation.
        ensureDefaultArea()
    }

    /** Used by restore (replace mode): wipe everything without re-seeding. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        dao.deleteAllExpenses()
        dao.deleteAllIncomes()
        dao.deleteAllBills()
        dao.deleteAllBillPayments()
        dao.deleteAllRecurringRules()
        dao.deleteAllNotifications()
        dao.deleteAllCategories()
        dao.deleteAllBudgets()
        dao.deleteAllSettings()
        dao.deleteAllGoals()
        dao.deleteAllSubscriptions()
        dao.deleteAllCategoryBudgets()
        dao.deleteAllAreas()
        // Beim Ersetzen nur die Eintraege entfernen - die Dateien der
        // Dokumente bleiben, damit die Sicherung sie wiederfindet.
        dao.deleteAllDocuments()
        dao.deleteAllAssets()
        dao.clearAudit()
    }

    suspend fun replaceAllCategories(items: List<CategoryEntity>) = withContext(Dispatchers.IO) {
        for (c in items) dao.insertCategory(c)
    }

    suspend fun insertAll(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        bills: List<BillEntity>,
        payments: List<BillPaymentEntity>,
        rules: List<RecurringRuleEntity>,
        budget: BudgetEntity?,
        settings: SettingsEntity?,
        areas: List<AreaEntity> = emptyList(),
        goals: List<SavingGoalEntity> = emptyList(),
        subscriptions: List<SubscriptionEntity> = emptyList(),
        categoryBudgets: List<CategoryBudgetEntity> = emptyList(),
        categoryRules: List<CategoryRuleEntity> = emptyList()
    ) = withContext(Dispatchers.IO) {
        for (e in expenses) dao.insertExpense(e)
        for (i in incomes) dao.insertIncome(i)
        for (b in bills) dao.insertBill(b)
        for (p in payments) dao.insertBillPayment(p)
        for (r in rules) dao.insertRecurringRule(r)
        for (a in areas) dao.insertArea(a)
        for (g in goals) dao.insertGoal(g)
        for (s in subscriptions) dao.insertSubscription(s)
        for (cb in categoryBudgets) dao.upsertCategoryBudget(cb)
        for (cr in categoryRules) {
            val vorhanden = dao.findRule(cr.token)
            if (vorhanden != null) {
                dao.updateRule(cr.copy(id = vorhanden.id, hits = maxOf(vorhanden.hits, cr.hits)))
            } else {
                dao.insertRule(cr.copy(id = 0))
            }
        }
        budget?.let { dao.upsertBudget(it) }
        settings?.let { dao.insertSettings(it) }
    }

    suspend fun monthKeysWithData(): List<YearMonth> = withContext(Dispatchers.IO) {
        val all = expenses.first().map { Dates.fromEpochDay(it.date) } +
            incomes.first().map { Dates.fromEpochDay(it.date) }
        all.map { YearMonth.from(it) }.distinct().sorted()
    }

    // ------------------------------- areas -----------------------------------

    suspend fun addArea(name: String, emoji: String): Long = withContext(Dispatchers.IO) {
        val order = areas.first().size
        dao.insertArea(
            AreaEntity(
                name = name.trim(),
                emoji = emoji,
                sortOrder = order,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateArea(item: AreaEntity) = withContext(Dispatchers.IO) { dao.updateArea(item) }

    suspend fun deleteArea(item: AreaEntity) = withContext(Dispatchers.IO) {
        dao.deleteArea(item)
        // Daten bleiben erhalten, sie werden nur dem Standardbereich zugeordnet
    }

    suspend fun ensureDefaultArea(): Long = withContext(Dispatchers.IO) {
        val list = areas.first()
        val existing = list.firstOrNull { it.name.equals("Persönlich", ignoreCase = true) }
        if (existing != null) return@withContext existing.id
        dao.insertArea(
            AreaEntity(name = "Persönlich", emoji = "\uD83D\uDC64", sortOrder = 0, createdAt = System.currentTimeMillis())
        )
    }

    // ---------------------------- saving goals --------------------------------

    suspend fun saveGoal(item: SavingGoalEntity): Long = withContext(Dispatchers.IO) {
        if (item.id == 0L) {
            dao.insertGoal(item.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.updateGoal(item)
            item.id
        }
    }

    suspend fun updateGoal(item: SavingGoalEntity) = withContext(Dispatchers.IO) { dao.updateGoal(item) }

    suspend fun deleteGoal(item: SavingGoalEntity) = withContext(Dispatchers.IO) { dao.deleteGoal(item) }

    /** Adds money to a goal (never negative, never above sanity limits). */
    suspend fun depositToGoal(id: Long, cents: Long) = withContext(Dispatchers.IO) {
        val goal = dao.getGoal(id) ?: return@withContext
        dao.updateGoal(goal.copy(savedCents = (goal.savedCents + cents).coerceAtLeast(0)))
    }

    // ---------------------------- subscriptions --------------------------------

    suspend fun saveSubscription(item: SubscriptionEntity): Long = withContext(Dispatchers.IO) {
        if (item.id == 0L) {
            dao.insertSubscription(item.copy(createdAt = System.currentTimeMillis()))
        } else {
            dao.updateSubscription(item)
            item.id
        }
    }

    suspend fun updateSubscription(item: SubscriptionEntity) =
        withContext(Dispatchers.IO) { dao.updateSubscription(item) }

    suspend fun deleteSubscription(item: SubscriptionEntity) =
        withContext(Dispatchers.IO) { dao.deleteSubscription(item) }

    // --------------------------- category budgets -------------------------------

    suspend fun setCategoryBudget(categoryId: Long, limitCents: Long, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            dao.upsertCategoryBudget(
                CategoryBudgetEntity(
                    categoryId = categoryId,
                    monthlyLimitCents = limitCents,
                    enabled = enabled
                )
            )
        }

    suspend fun deleteCategoryBudget(item: CategoryBudgetEntity) =
        withContext(Dispatchers.IO) { dao.deleteCategoryBudget(item) }

    // ---------------------------------------------------------------------
    // Bank connection (PSD2 / Open Banking)
    // ---------------------------------------------------------------------
    fun observeConnections() = dao.observeConnections()
    suspend fun getConnection(id: Long) = withContext(Dispatchers.IO) { dao.getConnection(id) }
    suspend fun insertConnection(item: BankConnectionEntity) =
        withContext(Dispatchers.IO) { dao.insertConnection(item) }
    suspend fun updateConnection(item: BankConnectionEntity) =
        withContext(Dispatchers.IO) { dao.updateConnection(item) }
    suspend fun deleteConnection(id: Long) =
        withContext(Dispatchers.IO) { dao.deleteConnection(id) }

    fun observeAccounts(connectionId: Long) = dao.observeAccounts(connectionId)
    fun observeAllAccounts() = dao.observeAllAccounts()
    suspend fun insertAccount(item: BankAccountEntity) =
        withContext(Dispatchers.IO) { dao.insertAccount(item) }
    suspend fun updateAccount(item: BankAccountEntity) =
        withContext(Dispatchers.IO) { dao.updateAccount(item) }
    suspend fun findAccountByExternalId(externalId: String) =
        withContext(Dispatchers.IO) { dao.findAccountByExternalId(externalId) }
    suspend fun deleteAccountsOfConnection(connectionId: Long) =
        withContext(Dispatchers.IO) { dao.deleteAccountsOfConnection(connectionId) }

    fun observeTransactions() = dao.observeTransactions()
    suspend fun insertTransaction(item: BankTransactionEntity) =
        withContext(Dispatchers.IO) { dao.insertTransaction(item) }
    suspend fun updateTransaction(item: BankTransactionEntity) =
        withContext(Dispatchers.IO) { dao.updateTransaction(item) }
    suspend fun transactionsOfAccount(accountId: Long) =
        withContext(Dispatchers.IO) { dao.transactionsOfAccount(accountId) }
    suspend fun getTransaction(id: Long) =
        withContext(Dispatchers.IO) { dao.getTransaction(id) }
    suspend fun deleteTransactionsOfConnection(connectionId: Long) =
        withContext(Dispatchers.IO) { dao.deleteTransactionsOfConnection(connectionId) }

    fun observeRules() = dao.observeRules()
    suspend fun getAllRules() = withContext(Dispatchers.IO) { dao.getAllRules() }
    suspend fun findRule(token: String) = withContext(Dispatchers.IO) { dao.findRule(token) }
    suspend fun insertRule(item: CategoryRuleEntity) =
        withContext(Dispatchers.IO) { dao.insertRule(item) }
    suspend fun updateRule(item: CategoryRuleEntity) =
        withContext(Dispatchers.IO) { dao.updateRule(item) }
    suspend fun deleteAllRules() = withContext(Dispatchers.IO) { dao.deleteAllRules() }

    // ---------------------------- Dokumente --------------------------------

    suspend fun saveDocument(item: DocumentEntity): Long = withContext(Dispatchers.IO) {
        val id = if (item.id == 0L) {
            val neu = dao.insertDocument(item.copy(createdAt = System.currentTimeMillis()))
            schreibeVerlauf(
                com.finovo.app.data.AuditAction.DOCUMENT_ADDED, "DOCUMENT", neu,
                "Dokument angelegt: " + item.title.ifBlank { item.kind }
            )
            neu
        } else {
            dao.updateDocument(item)
            item.id
        }
        id
    }

    suspend fun deleteDocument(item: DocumentEntity) = withContext(Dispatchers.IO) {
        dao.deleteDocument(item)
        schreibeVerlauf(
            com.finovo.app.data.AuditAction.DOCUMENT_DELETED, "DOCUMENT", item.id,
            "Dokument gelöscht: " + item.title.ifBlank { item.kind }
        )
    }

    suspend fun allDocuments(limit: Int = 5000): List<DocumentEntity> =
        withContext(Dispatchers.IO) { dao.documents(limit) }

    // ------------------------- Änderungsverlauf ----------------------------

    /** Schreibt einen Eintrag in den Änderungsverlauf. */
    suspend fun schreibeVerlauf(
        action: com.finovo.app.data.AuditAction,
        entityType: String,
        entityId: Long,
        detail: String,
        amountCents: Long? = null
    ) = withContext(Dispatchers.IO) {
        dao.insertAudit(
            com.finovo.app.data.AuditEntity(
                at = System.currentTimeMillis(),
                action = action.name,
                entityType = entityType,
                entityId = entityId,
                detail = detail,
                amountCents = amountCents
            )
        )
    }

    suspend fun allAudit(limit: Int = 500): List<com.finovo.app.data.AuditEntity> =
        withContext(Dispatchers.IO) { dao.audit(limit) }

    suspend fun clearAudit() = withContext(Dispatchers.IO) { dao.clearAudit() }

    // --------------------------- Vermögen ----------------------------------

    suspend fun saveAsset(item: com.finovo.app.data.AssetEntity): Long =
        withContext(Dispatchers.IO) {
            val id = if (item.id == 0L) {
                dao.insertAsset(item.copy(createdAt = System.currentTimeMillis()))
            } else {
                dao.updateAsset(item)
                item.id
            }
            schreibeVerlauf(
                com.finovo.app.data.AuditAction.SETTINGS_CHANGED, "ASSET", id,
                (if (item.isLiability) "Schuld" else "Wert") + ": " + item.name,
                item.amountCents
            )
            id
        }

    suspend fun deleteAsset(item: com.finovo.app.data.AssetEntity) =
        withContext(Dispatchers.IO) { dao.deleteAsset(item) }

    suspend fun allAssets(limit: Int = 5000): List<com.finovo.app.data.AssetEntity> =
        withContext(Dispatchers.IO) { dao.assets(limit) }

    // --------------------- Lernende Kategorien (KI) ------------------------

    /**
     * Merkt sich die Zuordnung eines Händler-Wortes zu einer Kategorie.
     * So lernt Finovo aus jeder Korrektur des Nutzers.
     */
    suspend fun lerneKategorie(token: String, categoryId: Long, isIncome: Boolean) =
        withContext(Dispatchers.IO) {
            val sauber = token.trim().lowercase().take(40)
            if (sauber.length < 2 || categoryId == 0L) return@withContext
            val vorhanden = dao.findRule(sauber)
            if (vorhanden == null) {
                dao.insertRule(
                    com.finovo.app.data.CategoryRuleEntity(
                        token = sauber,
                        categoryId = categoryId,
                        isIncome = isIncome,
                        hits = 1,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                dao.updateRule(
                    vorhanden.copy(
                        categoryId = categoryId,
                        isIncome = isIncome,
                        hits = vorhanden.hits + 1,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

    suspend fun allRules(): List<com.finovo.app.data.CategoryRuleEntity> =
        withContext(Dispatchers.IO) { dao.getAllRules() }
}

package com.finovo.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ---------------------------- categories -------------------------------

    @Query("SELECT * FROM categories ORDER BY type, sortOrder, name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(item: CategoryEntity): Long

    @Update
    suspend fun updateCategory(item: CategoryEntity)

    @Delete
    suspend fun deleteCategory(item: CategoryEntity)

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId = :id")
    suspend fun countExpensesInCategory(id: Long): Int

    @Query("SELECT COUNT(*) FROM incomes WHERE categoryId = :id")
    suspend fun countIncomesInCategory(id: Long): Int

    @Query("SELECT COUNT(*) FROM bills WHERE categoryId = :id")
    suspend fun countBillsInCategory(id: Long): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    // ----------------------------- expenses --------------------------------

    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: Long): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpense(item: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(item: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(item: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE recurringId = :ruleId")
    suspend fun deleteExpensesOfRule(ruleId: Long)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: Long)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteIncomeById(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    // ------------------------------ incomes --------------------------------

    @Query("SELECT * FROM incomes ORDER BY date DESC, id DESC")
    fun observeIncomes(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun getIncome(id: Long): IncomeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncome(item: IncomeEntity): Long

    @Update
    suspend fun updateIncome(item: IncomeEntity)

    @Delete
    suspend fun deleteIncome(item: IncomeEntity)

    @Query("DELETE FROM incomes WHERE recurringId = :ruleId")
    suspend fun deleteIncomesOfRule(ruleId: Long)

    @Query("DELETE FROM incomes")
    suspend fun deleteAllIncomes()

    // ------------------------------- bills ---------------------------------

    @Query("SELECT * FROM bills ORDER BY dueDate ASC, id ASC")
    fun observeBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getBill(id: Long): BillEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBill(item: BillEntity): Long

    @Update
    suspend fun updateBill(item: BillEntity)

    @Delete
    suspend fun deleteBill(item: BillEntity)

    @Query("DELETE FROM bills")
    suspend fun deleteAllBills()

    // -------------------------- bill payments ------------------------------

    @Query("SELECT * FROM bill_payments ORDER BY paidDate DESC, id DESC")
    fun observeBillPayments(): Flow<List<BillPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBillPayment(item: BillPaymentEntity): Long

    @Query("DELETE FROM bill_payments WHERE billId = :billId")
    suspend fun deletePaymentsOfBill(billId: Long)

    @Query("DELETE FROM bill_payments WHERE id = :id")
    suspend fun deleteBillPaymentById(id: Long)

    @Query("DELETE FROM bill_payments")
    suspend fun deleteAllBillPayments()

    // -------------------------- recurring rules ----------------------------

    @Query("SELECT * FROM recurring_rules ORDER BY active DESC, id DESC")
    fun observeRecurringRules(): Flow<List<RecurringRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecurringRule(item: RecurringRuleEntity): Long

    @Update
    suspend fun updateRecurringRule(item: RecurringRuleEntity)

    @Delete
    suspend fun deleteRecurringRule(item: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteAllRecurringRules()

    // ------------------------------- budget --------------------------------

    @Query("SELECT * FROM budgets WHERE id = 0")
    fun observeBudget(): Flow<BudgetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(item: BudgetEntity)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    // --------------------------- notifications -----------------------------

    @Query("SELECT * FROM notifications ORDER BY triggerAt ASC")
    fun observeNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(item: NotificationEntity): Long

    @Update
    suspend fun updateNotification(item: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE referenceKey = :key LIMIT 1")
    suspend fun getNotificationByKey(key: String): NotificationEntity?

    @Query("DELETE FROM notifications WHERE billId = :billId")
    suspend fun deleteNotificationsOfBill(billId: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    // ------------------------------ settings -------------------------------

    @Query("SELECT * FROM settings WHERE id = 0")
    fun observeSettings(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSettings(item: SettingsEntity): Long

    @Update
    suspend fun updateSettings(item: SettingsEntity)

    @Query("DELETE FROM settings")
    suspend fun deleteAllSettings()
    // ------------------------------ areas -----------------------------------

    @Query("SELECT * FROM areas ORDER BY sortOrder, name")
    fun observeAreas(): Flow<List<AreaEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArea(item: AreaEntity): Long

    @Update
    suspend fun updateArea(item: AreaEntity)

    @Delete
    suspend fun deleteArea(item: AreaEntity)

    @Query("DELETE FROM areas")
    suspend fun deleteAllAreas()

    // -------------------------- saving goals --------------------------------

    @Query("SELECT * FROM saving_goals ORDER BY archived, createdAt DESC")
    fun observeGoals(): Flow<List<SavingGoalEntity>>

    @Query("SELECT * FROM saving_goals WHERE id = :id")
    suspend fun getGoal(id: Long): SavingGoalEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGoal(item: SavingGoalEntity): Long

    @Update
    suspend fun updateGoal(item: SavingGoalEntity)

    @Delete
    suspend fun deleteGoal(item: SavingGoalEntity)

    @Query("DELETE FROM saving_goals")
    suspend fun deleteAllGoals()

    // -------------------------- subscriptions --------------------------------

    @Query("SELECT * FROM subscriptions ORDER BY active DESC, nextPayment")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getSubscription(id: Long): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubscription(item: SubscriptionEntity): Long

    @Update
    suspend fun updateSubscription(item: SubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(item: SubscriptionEntity)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAllSubscriptions()

    // -------------------------- category budgets ------------------------------

    @Query("SELECT * FROM category_budgets")
    fun observeCategoryBudgets(): Flow<List<CategoryBudgetEntity>>

    @Query("SELECT * FROM category_budgets WHERE categoryId = :id")
    suspend fun getCategoryBudget(id: Long): CategoryBudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryBudget(item: CategoryBudgetEntity): Long

    @Delete
    suspend fun deleteCategoryBudget(item: CategoryBudgetEntity)

    @Query("DELETE FROM category_budgets")
    suspend fun deleteAllCategoryBudgets()

    // --------------------------- bank connection ----------------------------
    @Query("SELECT * FROM bank_connections ORDER BY createdAt DESC")
    fun observeConnections(): Flow<List<BankConnectionEntity>>

    @Query("SELECT * FROM bank_connections WHERE id = :id")
    suspend fun getConnection(id: Long): BankConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(item: BankConnectionEntity): Long

    @Update
    suspend fun updateConnection(item: BankConnectionEntity)

    @Query("DELETE FROM bank_connections WHERE id = :id")
    suspend fun deleteConnection(id: Long)

    // accounts
    @Query("SELECT * FROM bank_accounts WHERE connectionId = :connectionId ORDER BY name")
    fun observeAccounts(connectionId: Long): Flow<List<BankAccountEntity>>

    @Query("SELECT * FROM bank_accounts ORDER BY name")
    fun observeAllAccounts(): Flow<List<BankAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(item: BankAccountEntity): Long

    @Update
    suspend fun updateAccount(item: BankAccountEntity)

    @Query("SELECT * FROM bank_accounts WHERE externalId = :externalId LIMIT 1")
    suspend fun findAccountByExternalId(externalId: String): BankAccountEntity?

    @Query("DELETE FROM bank_accounts WHERE connectionId = :connectionId")
    suspend fun deleteAccountsOfConnection(connectionId: Long)

    // transactions
    @Query("SELECT * FROM bank_transactions ORDER BY bookingDate DESC")
    fun observeTransactions(): Flow<List<BankTransactionEntity>>

    @Query("SELECT * FROM bank_transactions WHERE accountId = :accountId ORDER BY bookingDate DESC")
    fun observeTransactionsOfAccount(accountId: Long): Flow<List<BankTransactionEntity>>

    /** -1 means "already known" -> the transaction is a duplicate. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(item: BankTransactionEntity): Long

    @Update
    suspend fun updateTransaction(item: BankTransactionEntity)

    @Query("SELECT * FROM bank_transactions WHERE accountId = :accountId ORDER BY bookingDate DESC")
    suspend fun transactionsOfAccount(accountId: Long): List<BankTransactionEntity>

    @Query("SELECT * FROM bank_transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): BankTransactionEntity?

    @Query("DELETE FROM bank_transactions WHERE accountId IN (SELECT id FROM bank_accounts WHERE connectionId = :connectionId)")
    suspend fun deleteTransactionsOfConnection(connectionId: Long)

    // learned rules
    @Query("SELECT * FROM category_rules ORDER BY hits DESC")
    fun observeRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules")
    suspend fun getAllRules(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules WHERE token = :token LIMIT 1")
    suspend fun findRule(token: String): CategoryRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(item: CategoryRuleEntity): Long

    @Update
    suspend fun updateRule(item: CategoryRuleEntity)

    @Query("DELETE FROM category_rules")
    suspend fun deleteAllRules()

    // ---------------------------- documents --------------------------------

    @Query("SELECT * FROM documents ORDER BY date DESC, id DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(item: DocumentEntity): Long

    @Update
    suspend fun updateDocument(item: DocumentEntity)

    @Delete
    suspend fun deleteDocument(item: DocumentEntity)

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun documentById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents ORDER BY date DESC LIMIT :limit")
    suspend fun documents(limit: Int = 5000): List<DocumentEntity>

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()

    // ---------------------------- audit log --------------------------------

    @Query("SELECT * FROM audit_log ORDER BY at DESC, id DESC LIMIT :limit")
    fun observeAudit(limit: Int = 300): Flow<List<AuditEntity>>

    @Insert
    suspend fun insertAudit(item: AuditEntity): Long

    @Query("DELETE FROM audit_log")
    suspend fun clearAudit()

    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT :limit")
    suspend fun audit(limit: Int = 500): List<AuditEntity>

    // ----------------------------- assets ----------------------------------

    @Query("SELECT * FROM assets ORDER BY date DESC, id DESC")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(item: AssetEntity): Long

    @Update
    suspend fun updateAsset(item: AssetEntity)

    @Delete
    suspend fun deleteAsset(item: AssetEntity)

    @Query("SELECT * FROM assets ORDER BY date DESC LIMIT :limit")
    suspend fun assets(limit: Int = 5000): List<AssetEntity>

    @Query("DELETE FROM assets")
    suspend fun deleteAllAssets()
}

package com.finovo.app.bank

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.finovo.app.data.AppDatabase
import com.finovo.app.data.BankAccountEntity
import com.finovo.app.data.BankConnectionEntity
import com.finovo.app.data.BankTransactionEntity
import com.finovo.app.data.CategoryRuleEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Small helper for the offline check. */
object NetworkMonitor {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/**
 * Imports the transactions of a connected account into the normal Finovo
 * database. Duplicates are recognised through the external transaction id and
 * are never imported twice.
 */
class BankSyncEngine(
    private val context: Context,
    private val repository: Repository
) {

    data class Summary(
        val imported: Int = 0,
        val duplicates: Int = 0,
        val uncategorised: Int = 0,
        val needsReview: Int = 0,
        val error: String? = null
    ) {
        val offline: Boolean get() = error == BankApiClient.NO_CONNECTION
    }

    private fun api() = BankApiClient(
        baseUrl = BankSecurity.backendUrl(context)
    ) { BankSecurity.accessToken(context) }

    suspend fun sync(connectionId: Long): Summary = withContext(Dispatchers.IO) {
        val connection = repository.getConnection(connectionId)
            ?: return@withContext Summary(error = "Verbindung nicht mehr vorhanden.")

        if (!NetworkMonitor.isOnline(context)) {
            return@withContext Summary(error = BankApiClient.NO_CONNECTION)
        }

        val api = api()
        val payload = try {
            api.sync(connection.externalId, sinceEpochDay = 0L)
        } catch (e: BankApiClient.BankException) {
            return@withContext Summary(error = e.message ?: "Synchronisierung fehlgeschlagen.")
        }

        // ---- accounts ------------------------------------------------------
        val accountIds = LinkedHashMap<String, Long>() // externalId -> local id
        for (remote in payload.accounts) {
            val existing = repository.findAccountByExternalId(remote.externalId)
            val id = if (existing != null) {
                repository.updateAccount(
                    existing.copy(
                        name = remote.name,
                        iban = remote.iban,
                        currency = remote.currency,
                        balanceCents = remote.balanceCents,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                existing.id
            } else {
                repository.insertAccount(
                    BankAccountEntity(
                        connectionId = connection.id,
                        externalId = remote.externalId,
                        name = remote.name,
                        iban = remote.iban,
                        currency = remote.currency,
                        balanceCents = remote.balanceCents,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            accountIds[remote.externalId] = id
        }

        // ---- learned rules -------------------------------------------------
        val categories = repository.categories.first()
        val rules = HashMap<String, TransactionCategorizer.LearnedRule>()
        for (rule in repository.getAllRules()) {
            val name = categories.firstOrNull { it.id == rule.categoryId }?.name ?: continue
            rules[rule.token] = TransactionCategorizer.LearnedRule(name, rule.isIncome, rule.hits)
        }

        var imported = 0
        var duplicates = 0
        var uncategorised = 0
        var review = 0

        for (tx in payload.transactions) {
            val accountId = accountIds[tx.accountExternalId] ?: continue
            if (tx.amountCents == 0L) continue

            val history = repository.transactionsOfAccount(accountId)
                .filter { it.merchant.equals(tx.merchant, ignoreCase = true) }
                .map { it.bookingDate to it.amountCents }

            val recurring = TransactionCategorizer.looksRecurring(
                merchant = tx.merchant,
                amountCents = tx.amountCents,
                history = history
            )

            val isIncome = tx.amountCents > 0L
            val suggestion = TransactionCategorizer.suggest(
                merchant = tx.merchant,
                description = tx.description,
                amountCents = tx.amountCents,
                learned = rules
            )

            // resolve (or create) the suggested category
            var categoryId: Long? = null
            if (suggestion.categoryName != null && suggestion.confidence >= TransactionCategorizer.UNCERTAIN) {
                categoryId = repository.addCategory(
                    name = suggestion.categoryName!!,
                    emoji = emojiFor(suggestion.categoryName!!),
                    type = if (suggestion.isIncome) CategoryType.INCOME else CategoryType.EXPENSE
                )
                if (suggestion.needsReview) review++
            } else {
                uncategorised++
            }

            val now = System.currentTimeMillis()
            val note = listOf(tx.merchant, tx.description)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .take(160)

            val entity = BankTransactionEntity(
                accountId = accountId,
                externalId = tx.externalId,
                bookingDate = tx.bookingDate,
                valueDate = tx.valueDate,
                amountCents = tx.amountCents,
                currency = tx.currency,
                merchant = tx.merchant,
                description = tx.description,
                categoryId = categoryId,
                isIncome = isIncome,
                confidence = suggestion.confidence,
                recurringCandidate = recurring,
                importedAt = now
            )

            // -1 == already known -> never create a duplicate
            val rowId = repository.insertTransaction(entity)
            if (rowId == -1L) {
                duplicates++
                continue
            }

            val saved = entity.copy(id = rowId)
            if (isIncome) {
                val incomeId = repository.addIncome(
                    IncomeEntity(
                        amountCents = kotlin.math.abs(tx.amountCents),
                        categoryId = categoryId ?: 0L,
                        date = tx.bookingDate,
                        note = note,
                        createdAt = now
                    )
                )
                repository.updateTransaction(saved.copy(incomeId = incomeId))
            } else {
                val expenseId = repository.addExpense(
                    ExpenseEntity(
                        amountCents = kotlin.math.abs(tx.amountCents),
                        categoryId = categoryId ?: 0L,
                        date = tx.bookingDate,
                        note = note,
                        createdAt = now
                    )
                )
                repository.updateTransaction(saved.copy(expenseId = expenseId))
            }
            imported++
        }

        repository.updateConnection(connection.copy(lastSyncAt = System.currentTimeMillis()))
        Summary(
            imported = imported,
            duplicates = duplicates,
            uncategorised = uncategorised,
            needsReview = review
        )
    }

    /**
     * Applies a category chosen by the user - and remembers it for the future.
     */
    suspend fun applyCategory(
        transactionId: Long,
        categoryId: Long,
        isIncome: Boolean,
        categoryName: String
    ) = withContext(Dispatchers.IO) {
        val tx = repository.getTransaction(transactionId) ?: return@withContext
        val now = System.currentTimeMillis()

        if (tx.expenseId != null) {
            val expense = repository.expenses.first().firstOrNull { it.id == tx.expenseId }
            if (expense != null) {
                repository.updateExpense(expense.copy(categoryId = categoryId))
            }
        }
        if (tx.incomeId != null) {
            val income = repository.incomes.first().firstOrNull { it.id == tx.incomeId }
            if (income != null) {
                repository.updateIncome(income.copy(categoryId = categoryId))
            }
        }
        repository.updateTransaction(
            tx.copy(categoryId = categoryId, isIncome = isIncome, confidence = 1f)
        )

        // learn the assignment for similar future transactions
        val token = TransactionCategorizer.tokenOf(tx.merchant, tx.description)
        if (!token.isNullOrBlank()) {
            val existing = repository.findRule(token)
            if (existing == null) {
                repository.insertRule(
                    CategoryRuleEntity(
                        token = token,
                        categoryId = categoryId,
                        isIncome = isIncome,
                        hits = 1,
                        updatedAt = now
                    )
                )
            } else {
                repository.updateRule(
                    existing.copy(
                        categoryId = categoryId,
                        isIncome = isIncome,
                        hits = existing.hits + 1,
                        updatedAt = now
                    )
                )
            }
        }
    }

    companion object {
        fun emojiFor(category: String): String = when (category) {
            "Lebensmittel" -> "\uD83D\uDED2"
            "Miete" -> "\uD83C\uDFE0"
            "Strom" -> "⚡"
            "Gas" -> "\uD83D\uDD25"
            "Wasser" -> "\uD83D\uDCA7"
            "Versicherung" -> "\uD83D\uDEE1️"
            "Auto" -> "\uD83D\uDE97"
            "Tanken" -> "⛽"
            "Telefon" -> "\uD83D\uDCF1"
            "Internet" -> "\uD83C\uDF10"
            "Kleidung" -> "\uD83D\uDC55"
            "Gesundheit" -> "\uD83C\uDFE5"
            "Freizeit" -> "\uD83C\uDFA1"
            "Shopping" -> "\uD83D\uDECD️"
            "Abos" -> "\uD83D\uDD01"
            "Reisen" -> "✈️"
            "Schule/Kinder" -> "\uD83C\uDFEB"
            "Kredit" -> "\uD83D\uDCB3"
            "Gehalt" -> "\uD83D\uDCBC"
            "Kindergeld" -> "\uD83D\uDC76"
            "Sonstige Einnahmen" -> "\uD83D\uDCB0"
            else -> "\uD83D\uDCE6"
        }
    }
}

/** Background synchronisation (only over a network connection). */
class BankSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = Repository(AppDatabase.get(applicationContext).dao())
        val engine = BankSyncEngine(applicationContext, repository)
        val connections = runCatching { repository.observeConnections().first() }
            .getOrDefault(emptyList())
            .filter { it.autoSync }

        if (connections.isEmpty()) return@withContext Result.success()

        var failed = false
        for (connection in connections) {
            val result = engine.sync(connection.id)
            if (result.error != null && !result.offline) failed = true
        }
        if (failed) Result.retry() else Result.success()
    }
}

object BankSyncScheduler {

    private const val WORK = "finovo_bank_sync"

    fun enable(context: Context) {
        val request = PeriodicWorkRequestBuilder<BankSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun disable(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK) }
    }
}

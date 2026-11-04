package com.finovo.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finovo.app.backup.Backup
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillPaymentEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.BudgetEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.DateFilter
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.Frequency
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.NotificationType
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.data.Repository
import com.finovo.app.data.StatsPeriod
import com.finovo.app.export.Export
import com.finovo.app.notifications.Notify
import com.finovo.app.security.PinHasher
import com.finovo.app.ui.Route
import com.finovo.app.ui.SheetKind
import com.finovo.app.ui.SheetState
import com.finovo.app.util.Dates
import com.finovo.app.util.Analytics
import com.finovo.app.util.AnalyticsFilter
import com.finovo.app.util.AnalyticsPeriod
import com.finovo.app.util.AnalyticsResult
import com.finovo.app.util.DashboardData
import com.finovo.app.util.Granularity
import com.finovo.app.util.Money
import com.finovo.app.util.StatisticsData
import com.finovo.app.util.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.components.*
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.str
import com.finovo.app.util.categoryLabel

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val context: Application get() = getApplication()
    private val repository: Repository = (app as FinovoApp).repository

    // ---------------------------- raw data ----------------------------------

    val settings: StateFlow<com.finovo.app.data.SettingsEntity> =
        repository.settings.mapDefault(com.finovo.app.data.SettingsEntity())

    val categories: StateFlow<List<CategoryEntity>> = repository.categories.asList()
    val expenses: StateFlow<List<ExpenseEntity>> = repository.expenses.asList()
    val incomes: StateFlow<List<IncomeEntity>> = repository.incomes.asList()
    val bills: StateFlow<List<BillEntity>> = repository.bills.asList()
    val billPayments: StateFlow<List<BillPaymentEntity>> = repository.billPayments.asList()
    val recurringRules: StateFlow<List<RecurringRuleEntity>> = repository.recurringRules.asList()
    val budget: StateFlow<BudgetEntity> =
        repository.budget.mapDefault(BudgetEntity())

    private val today = MutableStateFlow(Dates.today().toEpochDay())

    // --------------------------- navigation ---------------------------------

    private val _stack = MutableStateFlow<List<Route>>(listOf(Route.Home))
    val stack: StateFlow<List<Route>> = _stack

    fun navigate(route: Route) {
        _stack.value = _stack.value + route
    }

    fun selectTab(route: Route) {
        _stack.value = listOf(route)
    }

    /** Returns false when the app should be closed. */
    fun back(): Boolean {
        val current = _stack.value
        return if (current.size > 1) {
            _stack.value = current.dropLast(1)
            true
        } else false
    }

    // ----------------------------- sheets ------------------------------------

    private val _sheet = MutableStateFlow(SheetState())
    val sheet: StateFlow<SheetState> = _sheet

    fun openQuickAdd() {
        _sheet.value = SheetState(kind = SheetKind.QUICK_ADD)
    }

    fun openExpenseSheet(item: ExpenseEntity? = null) {
        _sheet.value = SheetState(kind = SheetKind.EXPENSE, expense = item)
    }

    fun openIncomeSheet(item: IncomeEntity? = null) {
        _sheet.value = SheetState(kind = SheetKind.INCOME, income = item)
    }

    fun openBillSheet(item: BillEntity? = null) {
        _sheet.value = SheetState(kind = SheetKind.BILL, bill = item)
    }

    fun openRecurringSheet(item: RecurringRuleEntity? = null) {
        _sheet.value = SheetState(kind = SheetKind.RECURRING, rule = item)
    }

    fun closeSheet() {
        _sheet.value = SheetState()
    }

    // ----------------------------- filters -----------------------------------

    val expenseQuery = MutableStateFlow("")
    val expenseFilter = MutableStateFlow(DateFilter.MONTH)
    val expenseCustom = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    val expenseCategory = MutableStateFlow<Long?>(null)

    val incomeQuery = MutableStateFlow("")
    val incomeFilter = MutableStateFlow(DateFilter.MONTH)
    val incomeCustom = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    val incomeCategory = MutableStateFlow<Long?>(null)

    val statsPeriod = MutableStateFlow(StatsPeriod.THIS_MONTH)
    val statsCustom = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)

    val monthOffset = MutableStateFlow(0)

    // -------------------------- derived values -------------------------------

    private data class Snapshot(
        val categories: List<CategoryEntity> = emptyList(),
        val expenses: List<ExpenseEntity> = emptyList(),
        val incomes: List<IncomeEntity> = emptyList(),
        val bills: List<BillEntity> = emptyList(),
        val payments: List<BillPaymentEntity> = emptyList()
    )

    private val snapshot: StateFlow<Snapshot> = combine(
        listOf<Flow<Any?>>(
            categories, expenses, incomes, bills, billPayments
        )
    ) { values ->
        Snapshot(
            categories = values[0] as List<CategoryEntity>,
            expenses = values[1] as List<ExpenseEntity>,
            incomes = values[2] as List<IncomeEntity>,
            bills = values[3] as List<BillEntity>,
            payments = values[4] as List<BillPaymentEntity>
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Snapshot())

    val dashboard: StateFlow<DashboardData> = combine(
        listOf<Flow<Any?>>(snapshot, monthOffset, today, budget, settings)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val snap = values[0] as Snapshot
        val offset = values[1] as Int
        val todayEpoch = values[2] as Long
        val budgetValue = values[3] as BudgetEntity
        val todayDate = Dates.fromEpochDay(todayEpoch)
        val month = YearMonth.from(todayDate).plusMonths(offset.toLong())
        Stats.dashboard(
            today = todayDate,
            month = month,
            expenses = snap.expenses,
            incomes = snap.incomes,
            bills = snap.bills,
            categories = snap.categories,
            budgetEnabled = budgetValue.enabled,
            budgetLimitCents = budgetValue.monthlyLimitCents
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardData(
        month = YearMonth.now(),
        monthLabel = Dates.monthYear(Dates.today()),
        incomeCents = 0,
        expenseCents = 0,
        availableCents = 0,
        budgetEnabled = false,
        budgetLimitCents = 0,
        budgetSpentCents = 0,
        upcomingBills = emptyList(),
        recent = emptyList(),
        openBillsCents = 0,
        overdueCount = 0
    ))

    val filteredExpenses: StateFlow<List<ExpenseEntity>> = combine(
        listOf<Flow<Any?>>(snapshot, expenseQuery, expenseFilter, expenseCustom, expenseCategory, today)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val snap = values[0] as Snapshot
        val query = values[1] as String
        val filter = values[2] as DateFilter
        val custom = values[3] as? Pair<LocalDate, LocalDate>
        val category = values[4] as? Long
        val todayDate = Dates.fromEpochDay(values[5] as Long)
        val range = Stats.rangeFor(filter, todayDate, custom)
        val names = snap.categories.associate { it.id to it.name }
        Stats.filterExpenses(snap.expenses, range, query, category, names)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredExpenseSum: StateFlow<Long> = filteredExpenses.mapDefault(0L) { list ->
        list.sumOf { it.amountCents }
    }

    val filteredIncomes: StateFlow<List<IncomeEntity>> = combine(
        listOf<Flow<Any?>>(snapshot, incomeQuery, incomeFilter, incomeCustom, incomeCategory, today)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val snap = values[0] as Snapshot
        val query = values[1] as String
        val filter = values[2] as DateFilter
        val custom = values[3] as? Pair<LocalDate, LocalDate>
        val category = values[4] as? Long
        val todayDate = Dates.fromEpochDay(values[5] as Long)
        val range = Stats.rangeFor(filter, todayDate, custom)
        val names = snap.categories.associate { it.id to it.name }
        Stats.filterIncomes(snap.incomes, range, query, category, names)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val incomeSummary: StateFlow<Stats.IncomeSummary> = combine(
        listOf<Flow<Any?>>(incomes, today)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val list = values[0] as List<IncomeEntity>
        Stats.incomeSummary(list, Dates.fromEpochDay(values[1] as Long))
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        Stats.IncomeSummary(0, 0, 0, 1)
    )

    val statistics: StateFlow<StatisticsData> = combine(
        listOf<Flow<Any?>>(snapshot, statsPeriod, statsCustom, today)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val snap = values[0] as Snapshot
        val period = values[1] as StatsPeriod
        val custom = values[2] as? Pair<LocalDate, LocalDate>
        val todayDate = Dates.fromEpochDay(values[3] as Long)
        Stats.statistics(
            today = todayDate,
            period = period,
            custom = custom,
            expenses = snap.expenses,
            incomes = snap.incomes,
            bills = snap.bills,
            payments = snap.payments,
            categories = snap.categories
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        StatisticsData(
            label = "",
            range = Stats.rangeFor(DateFilter.MONTH, Dates.today()),
            incomeCents = 0,
            expenseCents = 0,
            remainingCents = 0,
            fixedCents = 0,
            variableCents = 0,
            upcomingBillsCents = 0,
            paidBillsCents = 0,
            outstandingBillsCents = 0,
            topCategories = emptyList(),
            highestSingleExpense = null,
            averageMonthlyExpenseCents = 0,
            yearExpenseCents = 0,
            yearIncomeCents = 0,
            monthlySeries = emptyList()
        )
    )

    // ----------------------------- analytics ---------------------------------

    val analyticsPeriod = MutableStateFlow(AnalyticsPeriod.THIS_MONTH)
    val analyticsCustom = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    val analyticsGranularity = MutableStateFlow<Granularity?>(null)
    val analyticsFilter = MutableStateFlow(AnalyticsFilter())

    val analytics: StateFlow<AnalyticsResult> = combine(
        listOf<Flow<Any?>>(
            snapshot, recurringRules, analyticsPeriod, analyticsCustom,
            analyticsGranularity, analyticsFilter, today, budget, settings
        )
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val snap = values[0] as Snapshot
        val rules = values[1] as List<RecurringRuleEntity>
        val period = values[2] as AnalyticsPeriod
        val custom = values[3] as? Pair<LocalDate, LocalDate>
        val granularity = values[4] as? Granularity
        val filter = values[5] as AnalyticsFilter
        val todayDate = Dates.fromEpochDay(values[6] as Long)
        val budgetValue = values[7] as BudgetEntity
        val settingsValue = values[8] as com.finovo.app.data.SettingsEntity
        com.finovo.app.util.Analytics.safe(
            period = period,
            today = todayDate,
            custom = custom,
            granularity = granularity,
            expenses = snap.expenses,
            incomes = snap.incomes,
            categories = snap.categories,
            rules = rules,
            budgetEnabled = budgetValue.enabled,
            budgetLimitCents = budgetValue.monthlyLimitCents,
            filter = filter,
            symbol = settingsValue.currencySymbol.ifBlank { "€" }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.finovo.app.util.Analytics.empty(Dates.today())
    )

    fun resetAnalyticsFilter() {
        analyticsFilter.value = AnalyticsFilter()
    }

    /**
     * Transactions behind a chart element.
     * [range] restricts to a single bucket, [categoryId] to one category.
     */
    fun analyticsDrillDown(
        categoryId: Long,
        range: com.finovo.app.util.DateRange? = null
    ): List<com.finovo.app.util.TxItem> = Analytics.drillDown(
        expenses = expenses.value,
        incomes = incomes.value,
        categories = categories.value,
        categoryId = categoryId,
        range = range ?: analytics.value.range,
        filter = analyticsFilter.value
    )

    fun setExpenseFixedOverride(expenseId: Long, fixed: Boolean?) {
        viewModelScope.launch {
            val current = expenses.value.firstOrNull { it.id == expenseId } ?: return@launch
            repository.updateExpense(current.copy(fixedOverride = fixed))
            message(
                when (fixed) {
                    true -> str("msg_marked_fixed", "Als Fixkosten markiert")
                    false -> str("msg_marked_variable", "Als variable Kosten markiert")
                    null -> str("msg_back_to_category_default", "Zurück auf Kategorie-Einstellung")
                }
            )
            refreshAfterChange()
        }
    }

    val overdueBills: StateFlow<List<BillEntity>> = combine(
        listOf<Flow<Any?>>(bills, today)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Stats.overdueBills(
            values[0] as List<BillEntity>,
            Dates.fromEpochDay(values[1] as Long)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dueSoonBills: StateFlow<List<BillEntity>> = combine(
        listOf<Flow<Any?>>(bills, today)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Stats.upcomingBills(
            values[0] as List<BillEntity>,
            Dates.fromEpochDay(values[1] as Long),
            30
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val paidPayments: StateFlow<List<BillPaymentEntity>> = billPayments

    // ------------------------------ events ------------------------------------

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val events = _events.asSharedFlow()

    fun message(text: String) {
        viewModelScope.launch { _events.emit(text) }
    }

    // ------------------------------ app lock -----------------------------------

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    private val _biometricAvailable = MutableStateFlow(false)
    val biometricAvailable: StateFlow<Boolean> = _biometricAvailable

    private val _pinPrompt = MutableStateFlow<String?>(null)
    val pinPrompt: StateFlow<String?> = _pinPrompt

    fun refreshToday() {
        today.value = Dates.today().toEpochDay()
        viewModelScope.launch {
            if (settings.value.autoCreateRecurring) repository.generateRecurringEntries()
        }
    }

    // Zusätzliche Sperr-Einstellungen (SharedPreferences, nicht Room)
    private val _security = MutableStateFlow(
        com.finovo.app.security.SecurityPrefs.load(getApplication())
    )
    val security: StateFlow<com.finovo.app.security.SecuritySettings> = _security

    private val _lockInfo = MutableStateFlow<String?>(null)
    val lockInfo: StateFlow<String?> = _lockInfo
    fun setLockInfo(text: String?) { _lockInfo.value = text }

    fun updateSecurity(change: (com.finovo.app.security.SecuritySettings) -> com.finovo.app.security.SecuritySettings) {
        val next = change(_security.value)
        _security.value = next
        com.finovo.app.security.SecurityPrefs.save(getApplication(), next)
    }

    /** Ist die App-Sperre wirklich nutzbar (PIN gesetzt oder Geräte-Authentifizierung möglich)? */
    fun lockActive(): Boolean {
        val s = settings.value
        if (!s.appLockEnabled) return false
        return s.pinHash.isNotBlank() ||
            (_security.value.deviceCredential &&
                com.finovo.app.security.Biometrics.deviceCredentialReady(getApplication()))
    }

    fun hasAppPin(): Boolean = settings.value.pinHash.isNotBlank()

    init {
        // Beim Kaltstart ist 'settings' kurz noch der Standardwert (Sperre aus). Sobald die echten
        // Einstellungen aus der Datenbank da sind, die Sperre einmal richtig auswerten.
        viewModelScope.launch {
            runCatching { repository.settings.first() }
            if (unlockedAtMs == 0L) evaluateLock(coldStart = true)
        }
    }

    fun evaluateLock(coldStart: Boolean = false) {
        val s = settings.value
        _biometricAvailable.value = s.biometricEnabled
        if (!lockActive()) { _locked.value = false; return }
        if (coldStart) {
            _locked.value = com.finovo.app.security.LockPolicy.shouldLock(
                coldStart = true,
                alwaysUnlock = _security.value.alwaysUnlock,
                lastBackgroundMs = com.finovo.app.security.SecurityPrefs.lastBackground(getApplication()),
                nowMs = System.currentTimeMillis(),
                timeoutSeconds = _security.value.timeoutSeconds
            )
        }
    }

    /** Beim Zurückkehren in die App: nach Ablauf der Zeit sperren. */
    fun lockIfTimedOut() {
        if (!lockActive() || _locked.value) return
        // Direkt nach dem Entsperren nie wieder sperren (System-Dialoge lösen oft ein zusätzliches onResume aus).
        if (System.currentTimeMillis() - unlockedAtMs < 3000L) return
        // Ohne vorheriges "in den Hintergrund" gibt es nichts abzulaufen.
        if (com.finovo.app.security.SecurityPrefs.lastBackground(getApplication()) <= 0L) return
        if (com.finovo.app.security.LockPolicy.shouldLock(
                coldStart = false,
                alwaysUnlock = _security.value.alwaysUnlock,
                lastBackgroundMs = com.finovo.app.security.SecurityPrefs.lastBackground(getApplication()),
                nowMs = System.currentTimeMillis(),
                timeoutSeconds = _security.value.timeoutSeconds
            )
        ) _locked.value = true
    }

    fun lockNow() {
        if (lockActive()) _locked.value = true
    }

    private var unlockedAtMs = 0L

    fun unlock() {
        unlockedAtMs = System.currentTimeMillis()
        _locked.value = false
        _lockInfo.value = null
        com.finovo.app.security.SecurityPrefs.setLastBackground(getApplication(), 0L)
    }

    fun setPin(pin: String): Boolean {
        if (pin.length < 4) {
            message(S(getApplication(), "die_pin_muss_mindestens_4_ziffern_ha", "Die PIN muss mindestens 4 Ziffern haben."))
            return false
        }
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(pinHash = hash, pinSalt = salt, appLockEnabled = true)
            }
            message(S(getApplication(), "pin_gespeichert_app_sperre_ist_aktiv", "PIN gespeichert. App-Sperre ist aktiv."))
        }
        return true
    }

    fun removePin() {
        viewModelScope.launch {
            repository.updateSettings {
                val deviceOk = _security.value.deviceCredential &&
                    com.finovo.app.security.Biometrics.deviceCredentialReady(getApplication())
                it.copy(pinHash = "", pinSalt = "", appLockEnabled = it.appLockEnabled && deviceOk,
                    biometricEnabled = it.biometricEnabled && it.appLockEnabled && deviceOk)
            }
            if (!lockActive()) _locked.value = false
            message(S(getApplication(), "app_sperre_entfernt", "App-PIN entfernt."))
        }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        val s = settings.value
        val deviceOk = _security.value.deviceCredential &&
            com.finovo.app.security.Biometrics.deviceCredentialReady(getApplication())
        if (enabled && s.pinHash.isBlank() && !deviceOk) {
            _pinPrompt.value = "create"
            return
        }
        viewModelScope.launch {
            repository.updateSettings { it.copy(appLockEnabled = enabled, biometricEnabled = it.biometricEnabled && enabled) }
            if (!enabled) _locked.value = false
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(biometricEnabled = enabled && it.appLockEnabled)
            }
        }
    }

    fun dismissPinPrompt() {
        _pinPrompt.value = null
    }

    /**
     * Checks the PIN with a brute-force lockout: after 5 wrong attempts the
     * app refuses further tries for 30 s, doubling each time up to 15 min.
     * The counters live in private preferences and reset after a correct PIN.
     */
    fun checkPin(pin: String): Boolean {
        val prefs = context.getSharedPreferences("finovo_pin_guard", android.content.Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong("lock_until", 0L)
        if (now < lockedUntil) {
            val seconds = ((lockedUntil - now) / 1000L + 1L).toInt()
            message(str("pin_gesperrt_sekunden", "Zu viele Versuche. Bitte in %1\$s Sekunden erneut versuchen.", seconds))
            return false
        }
        val s = settings.value
        if (PinHasher.verify(pin, s.pinSalt, s.pinHash)) {
            prefs.edit().putInt("fails", 0).putLong("lock_until", 0L).apply()
            return true
        }
        val fails = prefs.getInt("fails", 0) + 1
        val extra = fails - 5
        val lockMs = if (extra >= 0) minOf(15L * 60_000L, 30_000L shl minOf(extra, 5)) else 0L
        prefs.edit()
            .putInt("fails", fails)
            .putLong("lock_until", if (lockMs > 0L) now + lockMs else 0L)
            .apply()
        return false
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        val s = settings.value
        if (!checkPin(oldPin)) {
            message(S(getApplication(), "die_aktuelle_pin_ist_falsch", "Die aktuelle PIN ist falsch."))
            return false
        }
        if (newPin.length < 4) {
            message(S(getApplication(), "die_neue_pin_muss_mindestens_4_ziffe", "Die neue PIN muss mindestens 4 Ziffern haben."))
            return false
        }
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(newPin, salt)
        viewModelScope.launch {
            repository.updateSettings { it.copy(pinHash = hash, pinSalt = salt) }
            message(S(getApplication(), "pin_geaendert", "PIN geändert."))
        }
        return true
    }

    // ---------------------------- side effects ---------------------------------

    /** Recalculates recurring entries, reminders and budget warnings. */
    fun refreshAfterChange() {
        viewModelScope.launch {
            runCatching { repository.generateRecurringEntries() }
            runCatching {
                Notify.rescheduleBillReminders(context, repository, repository.allBills())
            }
            runCatching { checkBudgetWarnings() }
        }
    }

    private suspend fun checkBudgetWarnings() {
        val budgetValue = budget.value
        val settingsValue = settings.value
        if (!budgetValue.enabled || budgetValue.monthlyLimitCents <= 0L) return
        if (!settingsValue.notificationsEnabled) return
        if (!Notify.hasPermission(context)) return

        val monthKey = Dates.monthKey(Dates.today())
        val range = Stats.rangeFor(DateFilter.MONTH, Dates.today())
        val spent = Stats.sumExpenses(expenses.value, range)
        val limit = budgetValue.monthlyLimitCents
        val percent = spent.toFloat() / limit.toFloat()
        var updated = budgetValue

        suspend fun warn(
            active: Boolean,
            alreadyNotified: Boolean,
            id: Int,
            title: String,
            text: String,
            block: BudgetEntity.() -> BudgetEntity
        ) {
            if (active && !alreadyNotified) {
                Notify.show(
                    context = context,
                    id = id,
                    title = title,
                    message = text,
                    channel = Notify.CHANNEL_BUDGET,
                    type = NotificationType.BUDGET_WARNING
                )
                updated = updated.block()
            }
        }

        warn(
            active = percent >= 1f && budgetValue.warn100,
            alreadyNotified = budgetValue.notified100For == monthKey,
            id = 9101,
            title = S(getApplication(), "budget_aufgebraucht", "Budget aufgebraucht"),
            text = str("du_hast_dein_monatsbudget_von_erre", "Du hast dein Monatsbudget von %1\$s erreicht.", Money.format(limit, settingsValue.currencySymbol)),
            block = { copy(notified100For = monthKey) }
        )
        warn(
            active = percent >= 0.9f && budgetValue.warn90,
            alreadyNotified = budgetValue.notified90For == monthKey,
            id = 9102,
            title = S(getApplication(), "s_90_des_budgets_verbraucht", "90 % des Budgets verbraucht"),
            text = str("noch_verfuegbar", "Noch %1\$s verfügbar.", Money.format((limit - spent).coerceAtLeast(0), settingsValue.currencySymbol)),
            block = { copy(notified90For = monthKey) }
        )
        warn(
            active = percent >= 0.75f && budgetValue.warn75,
            alreadyNotified = budgetValue.notified75For == monthKey,
            id = 9103,
            title = S(getApplication(), "s_75_des_budgets_verbraucht", "75 % des Budgets verbraucht"),
            text = str("noch_verfuegbar_2", "Noch %1\$s verfügbar.", Money.format((limit - spent).coerceAtLeast(0), settingsValue.currencySymbol)),
            block = { copy(notified75For = monthKey) }
        )

        if (updated != budgetValue) repository.saveBudget(updated)
    }

    /** Sends a test notification so the user can verify reminders work. */
    fun sendTestNotification() {
        viewModelScope.launch {
            val ok = Notify.show(
                context = context,
                id = 9200,
                title = S(getApplication(), "finovo_name", "Finovo"),
                message = S(getApplication(), "erinnerungen_funktionieren", "Erinnerungen funktionieren. 🔔"),
                channel = Notify.CHANNEL_BILLS
            )
            message(
                if (ok) str("msg_test_notification_sent", "Test-Benachrichtigung gesendet.")
                else str("msg_notifications_not_allowed", "Benachrichtigungen sind nicht erlaubt. Bitte in den Android-Einstellungen aktivieren.")
            )
        }
    }

    // ------------------------------ expenses -----------------------------------

    fun saveExpense(item: ExpenseEntity, isNew: Boolean) {
        viewModelScope.launch {
            val id = if (isNew) repository.addExpense(item) else {
                repository.updateExpense(item); item.id
            }
            // Modul 32: Änderungsverlauf
            repository.schreibeVerlauf(
                if (isNew) com.finovo.app.data.AuditAction.TRANSACTION_CREATED
                else com.finovo.app.data.AuditAction.TRANSACTION_EDITED,
                "EXPENSE", id,
                (if (isNew) "Ausgabe erfasst: " else "Ausgabe geändert: ") +
                    (item.merchant.ifBlank { item.note }.ifBlank { "–" }),
                item.amountCents
            )
            // Modul 12: aus der Zuordnung lernen
            if (isNew) lerneAusBuchung(item.merchant.ifBlank { item.note }, item.categoryId, false)
            ladeKategorieRegeln()
            message(if (isNew) str("msg_expense_saved", "Ausgabe gespeichert") else str("msg_expense_updated", "Ausgabe aktualisiert"))
            refreshAfterChange()
        }
    }

    fun deleteExpense(item: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(item)
            repository.schreibeVerlauf(
                com.finovo.app.data.AuditAction.TRANSACTION_DELETED, "EXPENSE", item.id,
                "Ausgabe gelöscht: " + (item.merchant.ifBlank { item.note }.ifBlank { "–" }),
                item.amountCents
            )
            message(S(getApplication(), "ausgabe_geloescht", "Ausgabe gelöscht"))
            refreshAfterChange()
        }
    }

    fun saveExpenseWithRule(
        item: ExpenseEntity,
        frequency: Frequency,
        interval: Int,
        customDays: Int
    ) {
        viewModelScope.launch {
            val ruleId = repository.addRecurringRule(
                RecurringRuleEntity(
                    kind = RecurringKind.EXPENSE.name,
                    name = categoryName(item.categoryId),
                    amountCents = item.amountCents,
                    categoryId = item.categoryId,
                    note = item.note,
                    frequency = frequency.name,
                    interval = interval,
                    customDays = customDays,
                    dayOfMonth = Dates.fromEpochDay(item.date).dayOfMonth,
                    monthOfYear = Dates.fromEpochDay(item.date).monthValue,
                    startDate = item.date,
                    active = true,
                    lastGenerated = item.date
                )
            )
            if (ruleId > 0L) {
                repository.addExpense(item.copy(recurringId = ruleId, occurrenceDate = item.date))
            } else {
                repository.addExpense(item)
            }
            message(S(getApplication(), "ausgabe_und_wiederholung_gespeichert", "Ausgabe und Wiederholung gespeichert"))
            refreshAfterChange()
        }
    }

    fun toggleExpensePaid(item: ExpenseEntity) {
        viewModelScope.launch {
            repository.updateExpense(item.copy(paid = !item.paid))
            refreshAfterChange()
        }
    }

    // ------------------------------- incomes -----------------------------------

    fun saveIncome(item: IncomeEntity, isNew: Boolean) {
        viewModelScope.launch {
            val id = if (isNew) repository.addIncome(item) else {
                repository.updateIncome(item); item.id
            }
            repository.schreibeVerlauf(
                if (isNew) com.finovo.app.data.AuditAction.TRANSACTION_CREATED
                else com.finovo.app.data.AuditAction.TRANSACTION_EDITED,
                "INCOME", id,
                (if (isNew) "Einnahme erfasst: " else "Einnahme geändert: ") +
                    (item.merchant.ifBlank { item.note }.ifBlank { "–" }),
                item.amountCents
            )
            if (isNew) lerneAusBuchung(item.merchant.ifBlank { item.note }, item.categoryId, true)
            ladeKategorieRegeln()
            message(if (isNew) str("msg_income_saved", "Einnahme gespeichert") else str("msg_income_updated", "Einnahme aktualisiert"))
            refreshAfterChange()
        }
    }

    fun saveIncomeWithRule(
        item: IncomeEntity,
        frequency: Frequency,
        interval: Int,
        customDays: Int
    ) {
        viewModelScope.launch {
            val ruleId = repository.addRecurringRule(
                RecurringRuleEntity(
                    kind = RecurringKind.INCOME.name,
                    name = categoryName(item.categoryId),
                    amountCents = item.amountCents,
                    categoryId = item.categoryId,
                    note = item.note,
                    frequency = frequency.name,
                    interval = interval,
                    customDays = customDays,
                    dayOfMonth = Dates.fromEpochDay(item.date).dayOfMonth,
                    monthOfYear = Dates.fromEpochDay(item.date).monthValue,
                    startDate = item.date,
                    active = true,
                    lastGenerated = item.date
                )
            )
            if (ruleId > 0L) {
                repository.addIncome(item.copy(recurringId = ruleId, occurrenceDate = item.date))
            } else {
                repository.addIncome(item)
            }
            message(S(getApplication(), "einnahme_und_wiederholung_gespeicher", "Einnahme und Wiederholung gespeichert"))
            refreshAfterChange()
        }
    }

    fun deleteIncome(item: IncomeEntity) {
        viewModelScope.launch {
            repository.deleteIncome(item)
            message(S(getApplication(), "einnahme_geloescht", "Einnahme gelöscht"))
            refreshAfterChange()
        }
    }

    // -------------------------------- bills ------------------------------------

    fun saveBill(item: BillEntity, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) repository.addBill(item) else repository.updateBill(item)
            message(if (isNew) str("msg_bill_saved", "Rechnung gespeichert") else str("msg_bill_updated", "Rechnung aktualisiert"))
            refreshAfterChange()
        }
    }

    // ------------------------------------------------------------------ Bank

    private val bankEngine get() = com.finovo.app.bank.BankSyncEngine(context, repository)

    val bankConnections: StateFlow<List<com.finovo.app.data.BankConnectionEntity>> =
        repository.observeConnections()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bankAccounts: StateFlow<List<com.finovo.app.data.BankAccountEntity>> =
        repository.observeAllAccounts()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bankTransactions: StateFlow<List<com.finovo.app.data.BankTransactionEntity>> =
        repository.observeTransactions()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bankRules: StateFlow<List<com.finovo.app.data.CategoryRuleEntity>> =
        repository.observeRules()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var bankBusy by mutableStateOf(false)
        private set

    private var _bankNotice by mutableStateOf<String?>(null)
    val bankNotice: String? get() = _bankNotice

    /** Shows the notice in the bank screen *and* as a snackbar. */
    fun setBankNotice(text: String?) {
        _bankNotice = text
        if (!text.isNullOrBlank()) message(text)
    }
    /** Reference of a started consent that still has to be completed. */
    var bankReference by mutableStateOf<String?>(null)
        private set

    fun clearBankNotice() { _bankNotice = null }

    fun backendUrl(): String = com.finovo.app.bank.BankSecurity.backendUrl(context)

    fun backendToken(): String = com.finovo.app.bank.BankSecurity.accessToken(context)

    /** Stored encrypted in the Android keystore - never in plain text. */
    fun saveBackendToken(token: String) {
        com.finovo.app.bank.BankSecurity.saveAccessToken(context, token.trim())
        setBankNotice(str("msg_bank_token_saved", "Zugangstoken verschlüsselt gespeichert."))
    }

    fun saveBackendUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        val uri = runCatching { java.net.URI(clean) }.getOrNull()
        val valid = uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        if (!valid) {
            setBankNotice(str("msg_bank_url_invalid", "Die Backend-Adresse muss mit https:// beginnen und einen gültigen Host enthalten."))
            return
        }
        com.finovo.app.bank.BankSecurity.saveBackendUrl(context, clean)
        setBankNotice(str("msg_bank_backend_saved", "Backend-Adresse gespeichert."))
    }

    /** Step 1 - asks the backend for the OAuth page of the selected bank. */
    fun startBankConnection(institutionId: String, redirectUri: String, onAuthUrl: (String) -> Unit) {
        viewModelScope.launch {
            bankBusy = true
            try {
                if (!com.finovo.app.bank.NetworkMonitor.isOnline(context)) {
                    setBankNotice(com.finovo.app.bank.BankApiClient.NO_CONNECTION)
                    return@launch
                }
                val api = com.finovo.app.bank.BankApiClient(
                    com.finovo.app.bank.BankSecurity.backendUrl(context)
                ) { com.finovo.app.bank.BankSecurity.accessToken(context) }
                val start = api.startConnection(institutionId, redirectUri)
                if (start.authUrl.isBlank()) {
                    setBankNotice(str("msg_bank_no_login_page", "Die Bank hat keine Anmeldeseite geliefert."))
                    return@launch
                }
                bankReference = start.reference
                onAuthUrl(start.authUrl)
            } catch (e: com.finovo.app.bank.BankApiClient.BankException) {
                setBankNotice(e.message)
            } catch (e: Exception) {
                setBankNotice(str("msg_bank_connect_failed", "Verbindung nicht möglich: %1\$s", e.localizedMessage ?: ""))
            } finally {
                bankBusy = false
            }
        }
    }

    /** Step 2 - after the user came back from the bank page. */
    fun completeBankConnection(reference: String = bankReference.orEmpty()) {
        viewModelScope.launch {
            bankBusy = true
            try {
                if (!com.finovo.app.bank.NetworkMonitor.isOnline(context)) {
                    setBankNotice(com.finovo.app.bank.BankApiClient.NO_CONNECTION)
                    return@launch
                }
                val api = com.finovo.app.bank.BankApiClient(
                    com.finovo.app.bank.BankSecurity.backendUrl(context)
                ) { com.finovo.app.bank.BankSecurity.accessToken(context) }
                val remote = api.completeConnection(reference)
                if (remote.externalId.isBlank()) {
                    setBankNotice(str("msg_bank_not_released", "Die Verbindung wurde noch nicht freigegeben."))
                    return@launch
                }
                val connectionId = repository.insertConnection(
                    com.finovo.app.data.BankConnectionEntity(
                        institutionId = remote.institutionId,
                        bankName = remote.bankName,
                        externalId = remote.externalId,
                        status = com.finovo.app.data.BankConnectionStatus.LINKED.name,
                        createdAt = System.currentTimeMillis(),
                        lastSyncAt = 0,
                        autoSync = true,
                        consentExpiresAt = remote.consentExpiresAt
                    )
                )
                for (account in remote.accounts) {
                    repository.insertAccount(
                        com.finovo.app.data.BankAccountEntity(
                            connectionId = connectionId,
                            externalId = account.externalId,
                            name = account.name,
                            iban = account.iban,
                            currency = account.currency,
                            balanceCents = account.balanceCents,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
                bankReference = null
                com.finovo.app.bank.BankSyncScheduler.enable(context)
                setBankNotice(str("msg_bank_connected", "%1\$s verbunden. Du kannst jetzt synchronisieren.", remote.bankName))
            } catch (e: com.finovo.app.bank.BankApiClient.BankException) {
                setBankNotice(e.message)
            } catch (e: Exception) {
                setBankNotice(str("msg_bank_complete_failed", "Verbindung konnte nicht abgeschlossen werden: %1\$s", e.localizedMessage ?: ""))
            } finally {
                bankBusy = false
            }
        }
    }

    fun syncBank(connectionId: Long) {
        viewModelScope.launch {
            bankBusy = true
            try {
                val result = bankEngine.sync(connectionId)
                setBankNotice(
                    when {
                        result.error != null -> result.error
                        result.imported == 0 && result.duplicates == 0 -> str("msg_bank_no_new_tx", "Keine neuen Umsätze gefunden.")
                        else -> buildString {
                            append(str("neue_umsaetze_importiert", "%1\$s neue Umsätze importiert", result.imported))
                            if (result.duplicates > 0) append(str("msg_bank_duplicates", " · %1\$s Duplikate übersprungen", result.duplicates))
                            if (result.needsReview > 0) append(str("msg_bank_needs_review", " · %1\$s bitte prüfen", result.needsReview))
                            if (result.uncategorised > 0) append(str("msg_bank_uncategorised", " · %1\$s nicht kategorisiert", result.uncategorised))
                        }
                    }
                )
                refreshAfterChange()
            } finally {
                bankBusy = false
            }
        }
    }

    fun setBankAutoSync(connectionId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val connection = repository.getConnection(connectionId) ?: return@launch
            repository.updateConnection(connection.copy(autoSync = enabled))
            if (enabled) com.finovo.app.bank.BankSyncScheduler.enable(context)
            else com.finovo.app.bank.BankSyncScheduler.disable(context)
        }
    }

    fun disconnectBank(connectionId: Long) {
        viewModelScope.launch {
            val connection = repository.getConnection(connectionId) ?: return@launch
            runCatching {
                if (com.finovo.app.bank.NetworkMonitor.isOnline(context)) {
                    val api = com.finovo.app.bank.BankApiClient(
                        com.finovo.app.bank.BankSecurity.backendUrl(context)
                    ) { com.finovo.app.bank.BankSecurity.accessToken(context) }
                    api.deleteConnection(connection.externalId)
                }
            }
            repository.deleteTransactionsOfConnection(connectionId)
            repository.deleteAccountsOfConnection(connectionId)
            repository.deleteConnection(connectionId)
            com.finovo.app.bank.BankSecurity.clear(context)
            com.finovo.app.bank.BankSyncScheduler.disable(context)
            setBankNotice(str("msg_bank_disconnected", "Bankverbindung getrennt. Die bereits importierten Umsätze bleiben lokal gespeichert."))
            refreshAfterChange()
        }
    }

    /** User sets the category manually - Finovo learns from it. */
    fun applyBankCategory(transactionId: Long, categoryId: Long, isIncome: Boolean, name: String) {
        viewModelScope.launch {
            bankEngine.applyCategory(transactionId, categoryId, isIncome, name)
            setBankNotice(str("msg_bank_category_saved", "Kategorie „%1\$s“ gespeichert und für künftige Umsätze gemerkt.", name))
            refreshAfterChange()
        }
    }

    // ------------------------------------------------------------------ Scan

    /** True while the on-device OCR is running. */
    var scanning by mutableStateOf(false)
        private set

    /** Local path of the image that was scanned (private app storage). */
    var scanImagePath by mutableStateOf<String?>(null)
        private set

    /** Result of the last scan - only a suggestion until the user saves it. */
    var scanResult by mutableStateOf(com.finovo.app.scan.ScannedInvoice())
        private set

    /** How readable the picture was (0..1). Low values mean "please check carefully". */
    var scanQuality by mutableStateOf(1f)
        private set

    /** True as soon as a scan result is waiting for review. */
    var hasPendingScan by mutableStateOf(false)
        private set

    /**
     * Runs the on-device OCR on [uri], parses the text and navigates to the
     * review screen. Nothing is stored in the database here.
     */
    fun scanInvoice(uri: Uri) {
        viewModelScope.launch {
            scanning = true
            try {
                val outcome = com.finovo.app.scan.InvoiceScanner.scan(context, uri)
                val parsed = com.finovo.app.scan.EpcQr.merge(com.finovo.app.scan.InvoiceParser.parse(outcome.lines), com.finovo.app.scan.EpcQr.parse(outcome.qr))
                val copy = outcome.imagePath
                    ?: com.finovo.app.scan.InvoiceScanner.copyToAppFiles(context, uri)
                scanImagePath = copy
                scanResult = parsed
                scanQuality = outcome.quality
                hasPendingScan = true
                navigate(Route.ScanReview)
            } catch (e: com.finovo.app.scan.InvoiceScanner.ScanException) {
                when (e.reason) {
                    com.finovo.app.scan.InvoiceScanner.Reason.NO_TEXT ->
                        message(str("msg_scan_no_text", "Kein Text erkannt. Bitte das Bild erneut aufnehmen oder die Rechnung manuell anlegen."))
                    com.finovo.app.scan.InvoiceScanner.Reason.MODEL_NOT_READY ->
                        message(str("msg_scan_model_not_ready", "Die Texterkennung wird noch vorbereitet. Bitte in wenigen Sekunden erneut scannen."))
                    com.finovo.app.scan.InvoiceScanner.Reason.FAILED ->
                        message(str("msg_scan_failed", "Scan fehlgeschlagen: %1\$s", e.localizedMessage ?: "unbekannter Fehler"))
                }
            } catch (e: Exception) {
                message(str("msg_scan_failed", "Scan fehlgeschlagen: %1\$s", e.localizedMessage ?: "unbekannter Fehler"))
            } finally {
                scanning = false
            }
        }
    }

    /**
     * Modul 17: eigener Beleg-Scanner.
     *
     * Läuft komplett offline, erkennt Händler, Datum, Summe und MwSt und
     * zeigt danach zwingend einen Bestätigungsbildschirm. Gespeichert wird
     * erst, wenn der Nutzer dort bestätigt.
     */
    fun scanReceipt(uri: Uri) {
        viewModelScope.launch {
            scanning = true
            try {
                val outcome = com.finovo.app.scan.InvoiceScanner.scan(context, uri)
                val parsed = com.finovo.app.scan.EpcQr.merge(com.finovo.app.scan.InvoiceParser.parse(outcome.lines), com.finovo.app.scan.EpcQr.parse(outcome.qr))
                val copy = outcome.imagePath
                    ?: com.finovo.app.scan.InvoiceScanner.copyToAppFiles(context, uri)
                scanImagePath = copy
                scanResult = parsed
                scanQuality = outcome.quality
                hasPendingScan = true
                navigate(Route.ReceiptScan)
            } catch (e: com.finovo.app.scan.InvoiceScanner.ScanException) {
                when (e.reason) {
                    com.finovo.app.scan.InvoiceScanner.Reason.NO_TEXT ->
                        message(str("msg_scan_no_text", "Kein Text erkannt. Bitte das Bild erneut aufnehmen oder den Beleg manuell anlegen."))
                    com.finovo.app.scan.InvoiceScanner.Reason.MODEL_NOT_READY ->
                        message(str("msg_scan_model_not_ready", "Die Texterkennung wird noch vorbereitet. Bitte in wenigen Sekunden erneut scannen."))
                    com.finovo.app.scan.InvoiceScanner.Reason.FAILED ->
                        message(str("msg_scan_failed", "Scan fehlgeschlagen: %1\$s", e.localizedMessage ?: "unbekannter Fehler"))
                }
            } catch (e: Exception) {
                message(str("msg_scan_failed", "Scan fehlgeschlagen: %1\$s", e.localizedMessage ?: "unbekannter Fehler"))
            } finally {
                scanning = false
            }
        }
    }

    fun clearScan() {
        scanImagePath = null
        scanResult = com.finovo.app.scan.ScannedInvoice()
        hasPendingScan = false
    }

    /** Saves a reviewed invoice as a bill (appears under "Rechnungen"). */
    fun saveScannedBill(item: BillEntity) {
        saveBill(item, isNew = true)
        clearScan()
    }

    fun deleteBill(item: BillEntity) {
        viewModelScope.launch {
            Notify.cancelBillReminder(context, item.id)
            repository.deleteBill(item)
            message(S(getApplication(), "rechnung_geloescht", "Rechnung gelöscht"))
            refreshAfterChange()
        }
    }

    fun markBillPaid(item: BillEntity, createExpense: Boolean) {
        viewModelScope.launch {
            repository.markBillPaid(item, createExpense = createExpense)
            message(
                if (createExpense) str("msg_bill_paid_expense_created", "Rechnung bezahlt und Ausgabe erstellt")
                else str("msg_bill_marked_paid", "Rechnung als bezahlt markiert")
            )
            refreshAfterChange()
        }
    }

    fun markBillOpen(item: BillEntity) {
        viewModelScope.launch {
            repository.markBillOpen(item)
            message(S(getApplication(), "rechnung_ist_wieder_offen", "Rechnung ist wieder offen"))
            refreshAfterChange()
        }
    }

    fun deletePayment(payment: BillPaymentEntity) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            message(S(getApplication(), "zahlung_entfernt", "Zahlung entfernt"))
            refreshAfterChange()
        }
    }

    // ---------------------------- recurring rules --------------------------------

    fun saveRule(item: RecurringRuleEntity, isNew: Boolean) {
        viewModelScope.launch {
            if (isNew) repository.addRecurringRule(item) else repository.updateRecurringRule(item)
            message(if (isNew) str("msg_recurring_saved", "Wiederkehrende Zahlung gespeichert") else str("msg_recurring_updated", "Wiederkehrende Zahlung aktualisiert"))
            refreshAfterChange()
        }
    }

    fun setRuleActive(id: Long, active: Boolean) {
        viewModelScope.launch {
            repository.setRuleActive(id, active)
            message(if (active) str("msg_recurring_resumed", "Zahlung fortgesetzt") else str("msg_recurring_stopped", "Zahlung gestoppt"))
            refreshAfterChange()
        }
    }

    fun deleteRule(id: Long, deleteEntries: Boolean) {
        viewModelScope.launch {
            repository.deleteRecurringRule(id, deleteEntries)
            message(S(getApplication(), "wiederkehrende_zahlung_geloescht", "Wiederkehrende Zahlung gelöscht"))
            refreshAfterChange()
        }
    }

    fun createRuleFromBill(bill: BillEntity) {
        viewModelScope.launch {
            val rule = RecurringRuleEntity(
                kind = RecurringKind.EXPENSE.name,
                name = bill.name,
                amountCents = bill.amountCents,
                categoryId = bill.categoryId,
                note = bill.note,
                frequency = bill.frequency,
                interval = bill.interval,
                customDays = bill.customDays,
                dayOfMonth = Dates.fromEpochDay(bill.dueDate).dayOfMonth,
                monthOfYear = Dates.fromEpochDay(bill.dueDate).monthValue,
                startDate = bill.dueDate,
                active = true
            )
            repository.addRecurringRule(rule)
            message(S(getApplication(), "wiederkehrende_zahlung_aus_rechnung_", "Wiederkehrende Zahlung aus Rechnung erstellt"))
            refreshAfterChange()
        }
    }

    // ------------------------------ categories -----------------------------------

    fun addCategory(
        name: String,
        emoji: String,
        type: CategoryType,
        isFixed: Boolean = false,
        parentId: Long = 0L
    ) {
        viewModelScope.launch {
            val id = repository.addCategory(name, emoji, type, isFixed, parentId)
            if (id <= 0L) message(S(getApplication(), "diese_kategorie_existiert_bereits", "Diese Kategorie existiert bereits."))
            else message(S(getApplication(), "kategorie_hinzugefuegt", "Kategorie hinzugefügt"))
        }
    }

    fun updateCategory(item: CategoryEntity) {
        viewModelScope.launch {
            val ok = repository.updateCategory(item)
            message(
                if (ok) S(getApplication(), "kategorie_aktualisiert", "Kategorie aktualisiert")
                else S(
                    getApplication(),
                    "diese_kategorie_existiert_bereits",
                    "Diese Kategorie existiert bereits."
                )
            )
        }
    }

    fun deleteCategory(item: CategoryEntity) {
        viewModelScope.launch {
            if (!repository.canDeleteCategory(item.id)) {
                message(S(getApplication(), "die_kategorie_wird_noch_verwendet_un", "Die Kategorie wird noch verwendet und kann nicht gelöscht werden."))
                return@launch
            }
            repository.deleteCategory(item)
            message(S(getApplication(), "kategorie_geloescht", "Kategorie gelöscht"))
        }
    }

    // -------------------------------- budget --------------------------------------

    fun saveBudget(
        limitCents: Long,
        enabled: Boolean,
        warn75: Boolean,
        warn90: Boolean,
        warn100: Boolean
    ) {
        viewModelScope.launch {
            val current = budget.value
            repository.saveBudget(
                current.copy(
                    monthlyLimitCents = limitCents,
                    enabled = enabled,
                    warn75 = warn75,
                    warn90 = warn90,
                    warn100 = warn100
                )
            )
            message(S(getApplication(), "budget_gespeichert", "Budget gespeichert"))
            refreshAfterChange()
        }
    }

    // ------------------------------- settings ---------------------------------------

    fun setThemeMode(mode: com.finovo.app.data.ThemeMode) {
        viewModelScope.launch { repository.updateSettings { it.copy(themeMode = mode.name) } }
    }

    fun setCurrency(code: String, symbol: String) {
        viewModelScope.launch { repository.updateSettings { it.copy(currency = code, currencySymbol = symbol) } }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(notificationsEnabled = enabled) }
            if (enabled) {
                Notify.rescheduleBillReminders(context, repository, repository.allBills())
            } else {
                repository.allBills().forEach { Notify.cancelBillReminder(context, it.id) }
            }
        }
    }

    fun setAutoExpenseOnBillPaid(enabled: Boolean) {
        viewModelScope.launch { repository.updateSettings { it.copy(autoExpenseOnBillPaid = enabled) } }
    }

    fun setDuplicateWarnings(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(duplicateWarningsEnabled = enabled) }
        }
    }

    fun setAutoCreateRecurring(enabled: Boolean) {
        viewModelScope.launch { repository.updateSettings { it.copy(autoCreateRecurring = enabled) } }
    }

    // --------------------------- backup & restore ------------------------------------

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _lastBackupFile = MutableStateFlow<File?>(null)
    val lastBackupFile: StateFlow<File?> = _lastBackupFile

    private val _localBackups = MutableStateFlow(Backup.listLocalBackups(context))
    val localBackups: StateFlow<List<File>> = _localBackups

    fun refreshBackups() {
        _localBackups.value = Backup.listLocalBackups(context)
    }

    fun createLocalBackup(onDone: (File) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val file = runCatching { Backup.createLocalBackup(context, repository) }.getOrNull()
            _busy.value = false
            if (file != null) {
                _lastBackupFile.value = file
                refreshBackups()
                message(str("msg_backup_created", "Backup erstellt: %1\$s", file.name))
                onDone(file)
            } else {
                message(S(getApplication(), "backup_konnte_nicht_erstellt_werden", "Backup konnte nicht erstellt werden."))
            }
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val ok = Backup.writeToUri(context, uri, repository)
            _busy.value = false
            message(if (ok) str("msg_backup_file_saved", "Backup-Datei gespeichert.") else str("msg_backup_save_failed", "Backup konnte nicht gespeichert werden."))
        }
    }

    fun restoreFromUri(uri: Uri, mode: Backup.RestoreMode, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val payload = withContext(Dispatchers.IO) { Backup.readFromUri(context, uri) }
            if (payload == null) {
                _busy.value = false
                message(S(getApplication(), "diese_datei_ist_kein_gueltiges_my_ko", "Diese Datei ist kein gültiges Finovo-Backup."))
                return@launch
            }
            // Safety net: replacing wipes all data first, so keep a local copy of the current state.
            if (mode == Backup.RestoreMode.REPLACE) {
                runCatching { Backup.createLocalBackup(context, repository) }
            }
            val result = runCatching { Backup.restore(repository, payload, mode) }.getOrNull()
            _busy.value = false
            if (result == null) {
                message(S(getApplication(), "wiederherstellung_fehlgeschlagen", "Wiederherstellung fehlgeschlagen."))
            } else {
                message(
                    if (mode == Backup.RestoreMode.REPLACE) str("daten_wiederhergestellt_eintraege", "Daten wiederhergestellt (%1\$s Einträge).", result.inserted)
                    else str("daten_zusammengefuehrt_eintraege_h", "Daten zusammengeführt (%1\$s Einträge hinzugefügt).", result.inserted)
                )
                onDone()
            }
            refreshAfterChange()
        }
    }

    fun restoreFromFile(file: File, mode: Backup.RestoreMode, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val payload = withContext(Dispatchers.IO) { Backup.readFromFile(file) }
            if (payload == null) {
                _busy.value = false
                message(S(getApplication(), "diese_datei_ist_kein_gueltiges_m_2", "Diese Datei ist kein gültiges Finovo-Backup."))
                return@launch
            }
            // Safety net: replacing wipes all data first, so keep a local copy of the current state.
            if (mode == Backup.RestoreMode.REPLACE) {
                runCatching { Backup.createLocalBackup(context, repository) }
            }
            val result = runCatching { Backup.restore(repository, payload, mode) }.getOrNull()
            _busy.value = false
            if (result == null) {
                message(S(getApplication(), "wiederherstellung_fehlgeschlagen_2", "Wiederherstellung fehlgeschlagen."))
            } else {
                message(
                    if (mode == Backup.RestoreMode.REPLACE) str("daten_wiederhergestellt_eintra_2", "Daten wiederhergestellt (%1\$s Einträge).", result.inserted)
                    else str("daten_zusammengefuehrt_eintrae_2", "Daten zusammengeführt (%1\$s Einträge hinzugefügt).", result.inserted)
                )
                onDone()
            }
            refreshBackups()
            refreshAfterChange()
        }
    }

    fun deleteLocalBackup(file: File) {
        viewModelScope.launch {
            if (Backup.deleteLocalBackup(file)) {
                message(S(getApplication(), "backup_geloescht", "Backup gelöscht"))
                refreshBackups()
            } else message(S(getApplication(), "backup_konnte_nicht_geloescht_werden", "Backup konnte nicht gelöscht werden."))
        }
    }

    // -------------------------------- export -----------------------------------------

    private fun exportRequest(period: ExportPeriod): Export.Request {
        val today = Dates.today()
        val (from, to) = when (period) {
            ExportPeriod.ThisMonth -> Dates.startOfMonth(today) to Dates.endOfMonth(today)
            ExportPeriod.ThisYear -> Dates.startOfYear(today) to Dates.endOfYear(today)
            ExportPeriod.LastMonth -> {
                val last = today.minusMonths(1)
                Dates.startOfMonth(last) to Dates.endOfMonth(last)
            }
            is ExportPeriod.CustomRange -> period.from to period.to
        }
        val label = when (period) {
            ExportPeriod.ThisMonth -> Dates.monthYear(today)
            ExportPeriod.ThisYear -> "${today.year}"
            ExportPeriod.LastMonth -> Dates.monthYear(today.minusMonths(1))
            is ExportPeriod.CustomRange -> "${Dates.full(from)}-${Dates.full(to)}"
        }
        return Export.Request(
            label = label,
            from = from,
            to = to,
            expenses = expenses.value,
            incomes = incomes.value,
            bills = bills.value,
            categories = categories.value,
            symbol = settings.value.currencySymbol.ifBlank { "€" }
        )
    }

    fun exportCsvToUri(uri: Uri, period: ExportPeriod) {
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(Export.csv(exportRequest(period)).toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            _busy.value = false
            message(if (ok) str("msg_csv_export_saved", "CSV-Export gespeichert.") else str("msg_csv_export_failed", "CSV-Export fehlgeschlagen."))
        }
    }

    fun exportPdfToUri(uri: Uri, period: ExportPeriod) {
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    Export.writePdf(out, exportRequest(period))
                } != null
            }.getOrDefault(false)
            _busy.value = false
            message(if (ok) str("msg_pdf_report_saved", "PDF-Bericht gespeichert.") else str("msg_pdf_export_failed", "PDF-Export fehlgeschlagen."))
        }
    }

    private fun analyticsRequest(): Export.AnalyticsRequest {
        val result = analytics.value
        return Export.AnalyticsRequest(
            label = result.label,
            granularityLabel = com.finovo.app.ui.components.granularityLabelGerman(result.granularity),
            incomeCents = result.incomeCents,
            expenseCents = result.expenseCents,
            savingsCents = result.savingsCents,
            savingsRate = result.savingsRate,
            fixedCents = result.fixedCents,
            variableCents = result.variableCents,
            buckets = result.buckets,
            expenseCategories = result.expenseCategories,
            incomeCategories = result.incomeCategories,
            recurring = result.recurring,
            recurringMonthlyCents = result.recurringMonthlyCents,
            recurringYearlyCents = result.recurringYearlyCents,
            comparison = result.comparison,
            insights = result.insights,
            budgetEnabled = budget.value.enabled,
            budgetLimitCents = budget.value.monthlyLimitCents,
            filterText = analyticsFilter.value.describe(categories.value),
            symbol = settings.value.currencySymbol.ifBlank { "€" }
        )
    }

    fun exportAnalyticsCsv(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(Export.analyticsCsv(analyticsRequest()).toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            _busy.value = false
            message(if (ok) str("msg_analysis_csv_saved", "Analyse als CSV gespeichert.") else str("msg_csv_export_failed", "CSV-Export fehlgeschlagen."))
        }
    }

    fun exportAnalyticsPdf(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    Export.writeAnalyticsPdf(out, analyticsRequest())
                } != null
            }.getOrDefault(false)
            _busy.value = false
            message(if (ok) str("msg_analysis_pdf_saved", "Analyse als PDF gespeichert.") else str("msg_pdf_export_failed", "PDF-Export fehlgeschlagen."))
        }
    }

    fun createAnalyticsCsvFile(onDone: (File) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val file = runCatching {
                Export.createAnalyticsCsvFile(context, analyticsRequest())
            }.getOrNull()
            _busy.value = false
            if (file != null) onDone(file) else message(S(getApplication(), "csv_export_fehlgeschlagen", "CSV-Export fehlgeschlagen."))
        }
    }

    fun createAnalyticsPdfFile(onDone: (File) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val file = runCatching {
                Export.createAnalyticsPdfFile(context, analyticsRequest())
            }.getOrNull()
            _busy.value = false
            if (file != null) onDone(file) else message(S(getApplication(), "pdf_export_fehlgeschlagen", "PDF-Export fehlgeschlagen."))
        }
    }

    fun analyticsSummary(): String = Export.analyticsSummary(analyticsRequest())

    fun createCsvFile(period: ExportPeriod, onDone: (File) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val file = runCatching {
                Export.createCsvFile(context, exportRequest(period))
            }.getOrNull()
            _busy.value = false
            if (file != null) onDone(file) else message(S(getApplication(), "csv_export_fehlgeschlagen_2", "CSV-Export fehlgeschlagen."))
        }
    }

    fun createPdfFile(period: ExportPeriod, onDone: (File) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val file = runCatching {
                Export.createPdfFile(context, exportRequest(period))
            }.getOrNull()
            _busy.value = false
            if (file != null) onDone(file) else message(S(getApplication(), "pdf_export_fehlgeschlagen_2", "PDF-Export fehlgeschlagen."))
        }
    }

    // ------------------------------ delete all -----------------------------------------

    fun deleteAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { repository.deleteAllData() }
            runCatching {
                repository.allBills().forEach { Notify.cancelBillReminder(context, it.id) }
            }
            delay(150)
            _busy.value = false
            refreshBackups()
            message(S(getApplication(), "alle_daten_wurden_geloescht", "Alle Daten wurden gelöscht."))
            onDone()
        }
    }

    // ------------------------------- helpers ---------------------------------------------

    private fun <T> Flow<List<T>>.asList(): StateFlow<List<T>> =
        stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun <T, R> Flow<T?>.mapDefault(default: R, transform: (T) -> R): StateFlow<R> =
        this.map { value -> value?.let(transform) ?: default }
            .stateIn(viewModelScope, SharingStarted.Eagerly, default)

    private fun <T> Flow<T?>.mapDefault(default: T): StateFlow<T> =
        this.map { value -> value ?: default }
            .stateIn(viewModelScope, SharingStarted.Eagerly, default)

    fun exportPreview(period: ExportPeriod): Export.Request = exportRequest(period)

    fun categoryName(id: Long): String =
        categoryLabel(categories.value.firstOrNull { it.id == id }?.name ?: "Sonstiges")

    fun firstExpenseCategoryId(): Long =
        categories.value.firstOrNull { it.type == CategoryType.EXPENSE.name }?.id ?: 0L

    fun firstIncomeCategoryId(): Long =
        categories.value.firstOrNull { it.type == CategoryType.INCOME.name }?.id ?: 0L

    fun nextDueDatePreview(frequency: Frequency, interval: Int, customDays: Int, from: LocalDate): LocalDate? {
        val spec = com.finovo.app.util.RecurrenceSpec(
            frequency = frequency,
            interval = interval,
            customDays = customDays,
            dayOfMonth = from.dayOfMonth,
            monthOfYear = from.monthValue,
            startDate = from
        )
        return com.finovo.app.util.Recurrence.nextAfter(spec, from)
    }
    // ---------------------- Bereiche (financial areas) -----------------------

    val areas: StateFlow<List<AreaEntity>> = repository.areas.asList()
    val goals: StateFlow<List<SavingGoalEntity>> = repository.goals.asList()
    val subscriptions: StateFlow<List<SubscriptionEntity>> = repository.subscriptions.asList()
    val categoryBudgets: StateFlow<List<CategoryBudgetEntity>> = repository.categoryBudgets.asList()

    val activeAreaId: StateFlow<Long> = settings
        .map { it.activeAreaId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun setActiveArea(id: Long) {
        viewModelScope.launch { repository.updateSettings { it.copy(activeAreaId = id) } }
    }

    fun addArea(name: String, emoji: String) {
        viewModelScope.launch { repository.addArea(name, emoji) }
    }

    fun renameArea(item: AreaEntity, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch { repository.updateArea(item.copy(name = clean)) }
    }

    /** Keeps every transaction - they simply fall back to the default area. */
    fun deleteAreaKeepingData(item: AreaEntity) {
        viewModelScope.launch {
            repository.deleteArea(item)
            if (settings.value.activeAreaId == item.id) {
                repository.updateSettings { it.copy(activeAreaId = 0L) }
            }
        }
    }

    /** Balance / income / expense of a single area (id = 0 means "everything"). */
    fun areaTotals(id: Long): AreaTotals {
        val ex = expenses.value.filter { id == 0L || it.areaId == id }
        val inc = incomes.value.filter { id == 0L || it.areaId == id }
        val expenseCents = ex.sumOf { it.amountCents }
        val incomeCents = inc.sumOf { it.amountCents }
        return AreaTotals(
            incomeCents = incomeCents,
            expenseCents = expenseCents,
            balanceCents = incomeCents - expenseCents,
            count = ex.size + inc.size
        )
    }

    // ----------------------------- Sparziele ---------------------------------

    fun saveGoal(item: SavingGoalEntity) {
        viewModelScope.launch { repository.saveGoal(item) }
    }

    fun deleteGoal(item: SavingGoalEntity) {
        viewModelScope.launch { repository.deleteGoal(item) }
    }

    fun depositToGoal(id: Long, cents: Long) {
        viewModelScope.launch { repository.depositToGoal(id, cents) }
    }

    val goalSummary: StateFlow<GoalSummary> = goals.map { list ->
        val active = list.filter { !it.archived }
        GoalSummary(
            count = active.size,
            targetCents = active.sumOf { it.targetCents },
            savedCents = active.sumOf { it.savedCents }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GoalSummary())

    // ------------------------------- Abos ------------------------------------

    fun saveSubscription(item: SubscriptionEntity) {
        viewModelScope.launch { repository.saveSubscription(item) }
    }

    fun deleteSubscription(item: SubscriptionEntity) {
        viewModelScope.launch { repository.deleteSubscription(item) }
    }

    /** Normalises any frequency to cents per month (Long math, no rounding drift). */
    fun monthlyEquivalentCents(sub: SubscriptionEntity): Long {
        val interval = sub.interval.coerceAtLeast(1)
        return when (runCatching { Frequency.valueOf(sub.frequency) }.getOrDefault(Frequency.MONTHLY)) {
            Frequency.DAILY -> sub.amountCents * 365L / (12L * interval)
            Frequency.WEEKLY -> sub.amountCents * 52L / (12L * interval)
            Frequency.MONTHLY -> sub.amountCents / interval
            Frequency.YEARLY -> sub.amountCents / (12L * interval)
            Frequency.CUSTOM -> {
                val days = sub.customDays.coerceAtLeast(1)
                sub.amountCents * 30L / (days * interval)
            }
        }
    }

    fun yearlyEquivalentCents(sub: SubscriptionEntity): Long = monthlyEquivalentCents(sub) * 12L

    val subscriptionSummary: StateFlow<SubscriptionSummary> = subscriptions.map { list ->
        val active = list.filter { it.active }
        val monthly = active.sumOf { monthlyEquivalentCents(it) }
        SubscriptionSummary(
            count = active.size,
            monthlyCents = monthly,
            yearlyCents = monthly * 12L
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SubscriptionSummary())

    // -------------------------- Kategorie-Budgets -----------------------------

    fun setCategoryBudget(categoryId: Long, limitCents: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setCategoryBudget(categoryId, limitCents, enabled) }
    }

    fun deleteCategoryBudget(item: CategoryBudgetEntity) {
        viewModelScope.launch { repository.deleteCategoryBudget(item) }
    }

    /** Spent this month vs. limit, per category that has a budget. */
    val categoryBudgetProgress: StateFlow<List<CategoryBudgetProgress>> =
        combine(categoryBudgets, expenses, categories, today) { budgets, all, cats, day ->
            val now = Dates.fromEpochDay(day)
            val (from, to) = Dates.monthRange(now)
            val perCategory = all.filter { it.date in from..to }.groupBy { it.categoryId }
            budgets.filter { it.enabled && it.monthlyLimitCents > 0 }.map { b ->
                val cat = cats.firstOrNull { it.id == b.categoryId }
                val spent = perCategory[b.categoryId]?.sumOf { it.amountCents } ?: 0L
                CategoryBudgetProgress(
                    categoryId = b.categoryId,
                    name = categoryLabel(cat?.name ?: "Sonstiges"),
                    emoji = cat?.emoji ?: "",
                    limitCents = b.monthlyLimitCents,
                    spentCents = spent,
                    percent = if (b.monthlyLimitCents > 0) {
                        (spent.toFloat() / b.monthlyLimitCents.toFloat()).coerceIn(0f, 4f)
                    } else 0f
                )
            }.sortedByDescending { it.percent }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --------------------------- Globale Suche --------------------------------

    val searchQuery = MutableStateFlow("")

    val searchResults: StateFlow<List<SearchHit>> =
        combine(searchQuery, expenses, incomes, bills, categories) { query, ex, inc, bl, cats ->
            val q = query.trim().lowercase()
            if (q.length < 2) return@combine emptyList<SearchHit>()
            val out = ArrayList<SearchHit>()
            val catName = { id: Long -> categoryLabel(cats.firstOrNull { it.id == id }?.name ?: "") }
            val catEmoji = { id: Long -> cats.firstOrNull { it.id == id }?.emoji ?: "" }

            ex.forEach { e ->
                val hay = buildString {
                    append(e.note).append(' ')
                    append(catName(e.categoryId)).append(' ')
                    append(Money.format(e.amountCents)).append(' ')
                    append(Dates.full(Dates.fromEpochDay(e.date)))
                }.lowercase()
                if (q in hay) {
                    out.add(
                        SearchHit(
                            id = e.id,
                            kind = SearchKind.EXPENSE,
                            title = e.note.ifBlank { catName(e.categoryId).ifBlank { str("fallback_expense", "Ausgabe") } },
                            subtitle = "${catEmoji(e.categoryId)} ${catName(e.categoryId)} · ${Dates.full(Dates.fromEpochDay(e.date))}".trim(),
                            cents = -e.amountCents,
                            date = e.date
                        )
                    )
                }
            }
            inc.forEach { i ->
                val hay = buildString {
                    append(i.note).append(' ')
                    append(catName(i.categoryId)).append(' ')
                    append(Money.format(i.amountCents)).append(' ')
                    append(Dates.full(Dates.fromEpochDay(i.date)))
                }.lowercase()
                if (q in hay) {
                    out.add(
                        SearchHit(
                            id = i.id,
                            kind = SearchKind.INCOME,
                            title = i.note.ifBlank { catName(i.categoryId).ifBlank { str("fallback_income", "Einnahme") } },
                            subtitle = "${catEmoji(i.categoryId)} ${catName(i.categoryId)} · ${Dates.full(Dates.fromEpochDay(i.date))}".trim(),
                            cents = i.amountCents,
                            date = i.date
                        )
                    )
                }
            }
            bl.forEach { b ->
                val hay = buildString {
                    append(b.name).append(' ')
                    append(b.note).append(' ')
                    append(catName(b.categoryId)).append(' ')
                    append(Money.format(b.amountCents)).append(' ')
                    append(Dates.full(Dates.fromEpochDay(b.dueDate)))
                }.lowercase()
                if (q in hay) {
                    out.add(
                        SearchHit(
                            id = b.id,
                            kind = SearchKind.BILL,
                            title = b.name.ifBlank { str("fallback_bill", "Rechnung") },
                            subtitle = str("msg_bill_due_subtitle", "Rechnung · fällig %1\$s", Dates.full(Dates.fromEpochDay(b.dueDate))),
                            cents = -b.amountCents,
                            date = b.dueDate
                        )
                    )
                }
            }
            out.sortedByDescending { it.date }.take(120)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ------------------------------ Auto-Modul --------------------------------

    fun ensureCarCategories() {
        viewModelScope.launch {
            for (type in CarCosts.types) {
                repository.ensureCategory(type.name, type.emoji, CategoryType.EXPENSE)
            }
        }
    }

    val carSummary: StateFlow<CarSummary> = combine(expenses, categories) { ex, cats ->
        val ids = cats.filter { it.name in CarCosts.names }.map { it.id }.toSet()
        if (ids.isEmpty()) {
            CarSummary(available = false, entries = emptyList())
        } else {
            val mine = ex.filter { it.categoryId in ids }
            val now = Dates.today()
            val (from, to) = Dates.monthRange(now)
            val yearFrom = Dates.startOfYear(now).toEpochDay()
            val yearTo = Dates.endOfYear(now).toEpochDay()
            val monthly = mine.filter { it.date in from..to }.sumOf { it.amountCents }
            val yearly = mine.filter { it.date in yearFrom..yearTo }.sumOf { it.amountCents }
            val byType = cats.filter { it.id in ids }.map { c ->
                val rows = mine.filter { it.categoryId == c.id }
                CarTotal(
                    categoryId = c.id,
                    name = c.name,
                    emoji = c.emoji,
                    monthlyCents = rows.filter { it.date in from..to }.sumOf { it.amountCents },
                    yearlyCents = rows.filter { it.date in yearFrom..yearTo }.sumOf { it.amountCents },
                    count = rows.size
                )
            }.sortedByDescending { it.yearlyCents }
            CarSummary(
                available = true,
                monthlyCents = monthly,
                yearlyCents = yearly,
                entries = byType,
                lastEntries = mine.sortedByDescending { it.date }.take(8)
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CarSummary())

    // --------------------------- Finanzkalender -------------------------------

    val calendarMonth: MutableStateFlow<java.time.YearMonth> =
        MutableStateFlow(java.time.YearMonth.from(Dates.today()))

    fun shiftCalendarMonth(deltaMonths: Long) {
        val aktuell = calendarMonth.value
        calendarMonth.value = runCatching { aktuell.plusMonths(deltaMonths) }.getOrDefault(aktuell)
    }

    private val calendarFlows = listOf(
        calendarMonth,
        bills,
        recurringRules,
        subscriptions,
        expenses,
        incomes,
        categories
    )

    val calendarEntries: StateFlow<List<CalendarEntry>> = combine(calendarFlows) { arr ->
        @Suppress("UNCHECKED_CAST")
        FinanceCalendar.entriesForMonth(
            month = arr[0] as java.time.YearMonth,
            bills = arr[1] as List<BillEntity>,
            rules = arr[2] as List<RecurringRuleEntity>,
            subscriptions = arr[3] as List<SubscriptionEntity>,
            expenses = arr[4] as List<ExpenseEntity>,
            incomes = arr[5] as List<IncomeEntity>,
            categories = arr[6] as List<CategoryEntity>
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val calendarBuckets: StateFlow<Map<Long, DayBucket>> =
        calendarEntries.map { FinanceCalendar.buckets(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Expected end-of-day balance for every day of the visible month. */
    val calendarBalance: StateFlow<List<Pair<Long, Long>>> =
        combine(calendarMonth, calendarBuckets, dashboard) { month, buckets, dash ->
            FinanceCalendar.runningBalance(month, buckets, dash.availableCents)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // -------------------------- Smart-Analyse ---------------------------------

    val insights: StateFlow<List<SmartInsight>> = combine(expenses, incomes, categories) { ex, inc, cats ->
        SmartInsights.analyze(ex, inc, cats, Dates.today())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ------------------------------ Belege ------------------------------------

    val receipts: StateFlow<List<ExpenseEntity>> = expenses
        .map { list -> list.filter { it.receiptUri.isNotBlank() }.sortedByDescending { it.date } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Copies a picked image into the app's private storage - nothing leaves the device. */
    fun attachReceipt(expenseId: Long, source: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val target = runCatching {
                    val dir = File(context.filesDir, "receipts").apply { mkdirs() }
                    val file = File(dir, "receipt_${expenseId}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(source)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    file
                }.getOrNull() ?: return@withContext
                val expense = expenses.value.firstOrNull { it.id == expenseId } ?: return@withContext
                repository.updateExpense(expense.copy(receiptUri = target.absolutePath))
            }
        }
    }

    fun removeReceipt(expense: ExpenseEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { File(expense.receiptUri).delete() }
                repository.updateExpense(expense.copy(receiptUri = ""))
            }
        }
    }

    /** Expenses without a receipt - used by the attach dialog. */
    fun expensesWithoutReceipts(): List<ExpenseEntity> =
        expenses.value.filter { it.receiptUri.isBlank() }.sortedByDescending { it.date }.take(50)

    // ------------------------- Sprache & Auto-Lock -----------------------------

    fun setLanguage(code: String) {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(language = com.finovo.app.util.Language.fromCode(code).code)
            }
        }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(autoLockMinutes = minutes.coerceIn(0, 120)) }
        }
    }

    private var backgroundedAt: Long = 0L

    /** Called when the app goes to the background. */
    fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /** Locks the app again when the configured idle time has elapsed. */
    fun onAppResumed() {
        val minutes = settings.value.autoLockMinutes
        if (minutes > 0 && settings.value.appLockEnabled && settings.value.pinHash.isNotBlank()) {
            val elapsed = System.currentTimeMillis() - backgroundedAt
            if (backgroundedAt > 0L && elapsed >= minutes * 60_000L) _locked.value = true
        }
        backgroundedAt = 0L
    }

    // --------------------------------- Finovo AI ------------------------------------

    /** Gesprächsgedächtnis der KI (Modul 27). */
    private val aiKontext = com.finovo.app.ai.AiContext()

    /** Neues Gespräch beginnen. */
    fun resetFinovoAi() = aiKontext.zuruecksetzen()

    /** Wie viele Fragen wurden in diesem Gespräch gestellt? */
    val aiFragenAnzahl: Int get() = aiKontext.fragenAnzahl

    /** Währungssymbol für Anzeigen in der KI-Oberfläche. */
    val waehrung: String get() = settings.value.currencySymbol.ifBlank { "€" }

    /** Gelerntes Wörterbuch Händlerwort -> Kategorie (Modul 12). */
    private val _kategorieRegeln =
        MutableStateFlow<List<com.finovo.app.data.CategoryRuleEntity>>(emptyList())
    val kategorieRegeln: StateFlow<List<com.finovo.app.data.CategoryRuleEntity>> = _kategorieRegeln

    fun ladeKategorieRegeln() {
        viewModelScope.launch {
            _kategorieRegeln.value = runCatching { repository.allRules() }.getOrDefault(emptyList())
        }
    }

    /** Duplikat-Warnung beim Speichern anzeigen? (Modul 29) */
    val duplikatWarnungen: Boolean get() = settings.value.duplicateWarningsEnabled

    /**
     * Finovo AI: baut die strukturierte Zusammenfassung (rein lesend aus der
     * Datenbank) und lässt die KI darauf antworten.
     *
     * Die KI bekommt ausschließlich die Zusammenfassung - niemals die
     * Datenbank, ein DAO oder Schreibrechte. Schlägt etwas fehl, bleibt die
     * App normal bedienbar.
     */
    fun askFinovoAi(question: String, onDone: (com.finovo.app.ai.AiAnswer) -> Unit) {
        viewModelScope.launch {
            val zusammenfassung = runCatching {
                com.finovo.app.ai.FinancialSummaryService.build(
                    repository = repository,
                    today = Dates.today(),
                    symbol = settings.value.currencySymbol.ifBlank { "€" }
                )
            }.getOrNull()

            if (zusammenfassung == null) {
                message(
                    S(
                        getApplication(),
                        "ai_error",
                        "Die Anfrage konnte nicht verarbeitet werden. Finovo läuft normal weiter."
                    )
                )
                return@launch
            }

            val antwort = runCatching {
                com.finovo.app.ai.AiServiceLayer.answer(
                    zusammenfassung, question, com.finovo.app.util.AppLocale.current(), aiKontext
                )
            }.getOrElse {
                com.finovo.app.ai.AiAnswer(
                    blocks = listOf(
                        com.finovo.app.ai.AiBlock.Text(
                            str(
                                "ai_error",
                                "Die Anfrage konnte nicht verarbeitet werden. Finovo läuft normal weiter."
                            )
                        )
                    ),
                    intent = com.finovo.app.ai.AiIntent.GENERAL,
                    hasEnoughData = false
                )
            }
            onDone(antwort)
        }
    }
    // ---------------------- Finovo AI: Befehle (Modul 28) --------------------------

    /**
     * Führt einen Befehl der KI aus - und zwar erst, nachdem der Nutzer ihn
     * bestätigt hat. Ohne Bestätigung wird hier nichts geschrieben.
     */
    fun runAiCommand(
        befehl: com.finovo.app.ai.AiCommands.Befehl,
        onDone: (String) -> Unit
    ) {
        viewModelScope.launch {
            val symbol = settings.value.currencySymbol.ifBlank { "€" }
            val text = runCatching {
                when (befehl) {
                    is com.finovo.app.ai.AiCommands.Befehl.Ausgabe ->
                        bucheAusgabe(befehl, symbol)

                    is com.finovo.app.ai.AiCommands.Befehl.Einnahme ->
                        bucheEinnahme(befehl, symbol)

                    is com.finovo.app.ai.AiCommands.Befehl.Sparziel ->
                        legeSparzielAn(befehl, symbol)

                    is com.finovo.app.ai.AiCommands.Befehl.Budget ->
                        setzeBudget(befehl, symbol)

                    is com.finovo.app.ai.AiCommands.Befehl.ZeigeVormonat,
                    is com.finovo.app.ai.AiCommands.Befehl.ZeigeMonat ->
                        str("ai_thinking", "Finovo AI rechnet …")
                }
            }.getOrElse {
                com.finovo.app.util.Validation.speicherFehler()
            }
            onDone(text)
        }
    }

    /** Reine Anzeige-Befehle ("zeige meine Ausgaben vom letzten Monat"). */
    fun askAiShowCommand(
        befehl: com.finovo.app.ai.AiCommands.Befehl,
        onDone: (com.finovo.app.ai.AiAnswer) -> Unit
    ) {
        val frage = when (befehl) {
            is com.finovo.app.ai.AiCommands.Befehl.ZeigeVormonat ->
                "Vergleiche diesen Monat mit dem letzten Monat."

            else -> "Analysiere meinen Monat."
        }
        askFinovoAi(frage, onDone)
    }

    private suspend fun bucheAusgabe(
        befehl: com.finovo.app.ai.AiCommands.Befehl.Ausgabe,
        symbol: String
    ): String {
        val kategorien = repository.categories.firstOrNull() ?: emptyList()
        val ausgaben = kategorien.filter { it.type == com.finovo.app.data.CategoryType.EXPENSE.name }
        val kategorie = findeKategorie(ausgaben, befehl.kategorie ?: befehl.bezeichnung)
        val id = repository.addExpense(
            com.finovo.app.data.ExpenseEntity(
                amountCents = befehl.cents,
                categoryId = kategorie?.id ?: 0L,
                date = Dates.today().toEpochDay(),
                note = befehl.bezeichnung,
                merchant = befehl.bezeichnung,
                createdAt = System.currentTimeMillis()
            )
        )
        repository.schreibeVerlauf(
            com.finovo.app.data.AuditAction.TRANSACTION_CREATED, "EXPENSE", id,
            "Ausgabe über Finovo AI: " + befehl.bezeichnung, befehl.cents
        )
        lerne(befehl.bezeichnung, kategorie, false)
        return str(
            "ai_cmd_done_expense", "Ausgabe gespeichert: %1\$s für %2\$s.",
            Money.format(befehl.cents, symbol), befehl.bezeichnung.ifBlank { "–" }
        )
    }

    private suspend fun bucheEinnahme(
        befehl: com.finovo.app.ai.AiCommands.Befehl.Einnahme,
        symbol: String
    ): String {
        val kategorien = repository.categories.firstOrNull() ?: emptyList()
        val einnahmen = kategorien.filter { it.type == com.finovo.app.data.CategoryType.INCOME.name }
        val kategorie = findeKategorie(einnahmen, befehl.kategorie ?: befehl.bezeichnung)
        val id = repository.addIncome(
            com.finovo.app.data.IncomeEntity(
                amountCents = befehl.cents,
                categoryId = kategorie?.id ?: 0L,
                date = Dates.today().toEpochDay(),
                note = befehl.bezeichnung,
                merchant = befehl.bezeichnung,
                createdAt = System.currentTimeMillis()
            )
        )
        repository.schreibeVerlauf(
            com.finovo.app.data.AuditAction.TRANSACTION_CREATED, "INCOME", id,
            "Einnahme über Finovo AI: " + befehl.bezeichnung, befehl.cents
        )
        lerne(befehl.bezeichnung, kategorie, true)
        return str(
            "ai_cmd_done_income", "Einnahme gespeichert: %1\$s für %2\$s.",
            Money.format(befehl.cents, symbol), befehl.bezeichnung.ifBlank { "–" }
        )
    }

    private suspend fun legeSparzielAn(
        befehl: com.finovo.app.ai.AiCommands.Befehl.Sparziel,
        symbol: String
    ): String {
        val id = repository.saveGoal(
            com.finovo.app.data.SavingGoalEntity(
                name = befehl.name.ifBlank { "Sparziel" },
                targetCents = befehl.cents,
                savedCents = 0L
            )
        )
        repository.schreibeVerlauf(
            com.finovo.app.data.AuditAction.GOAL_CHANGED, "GOAL", id,
            "Sparziel über Finovo AI: " + befehl.name, befehl.cents
        )
        return str(
            "ai_cmd_done_goal", "Sparziel „%1\$s“ über %2\$s angelegt.",
            befehl.name.ifBlank { "Sparziel" }, Money.format(befehl.cents, symbol)
        )
    }

    private suspend fun setzeBudget(
        befehl: com.finovo.app.ai.AiCommands.Befehl.Budget,
        symbol: String
    ): String {
        val kategorien = repository.categories.firstOrNull() ?: emptyList()
        val kategorie = befehl.kategorie?.let { name ->
            findeKategorie(kategorien, name)
        }
        if (kategorie != null) {
            repository.setCategoryBudget(kategorie.id, befehl.cents, true)
        } else {
            val budget = (repository.budget.firstOrNull() ?: com.finovo.app.data.BudgetEntity())
                .copy(monthlyLimitCents = befehl.cents, enabled = true)
            repository.saveBudget(budget)
            repository.schreibeVerlauf(
                com.finovo.app.data.AuditAction.BUDGET_CHANGED, "BUDGET", 0L,
                "Monatsbudget über Finovo AI", befehl.cents
            )
        }
        return str(
            "ai_cmd_done_budget", "Monatsbudget auf %1\$s gesetzt.",
            Money.format(befehl.cents, symbol)
        )
    }

    /** Kategorie über den Namen finden (auch Teilübereinstimmung). */
    private fun findeKategorie(
        kategorien: List<com.finovo.app.data.CategoryEntity>,
        text: String
    ): com.finovo.app.data.CategoryEntity? {
        if (text.isBlank()) return kategorien.firstOrNull()
        val suchbegriff = text.trim()
        return kategorien.firstOrNull { it.name.equals(suchbegriff, ignoreCase = true) }
            ?: kategorien.firstOrNull { it.name.contains(suchbegriff, ignoreCase = true) }
            ?: kategorien.firstOrNull {
                suchbegriff.contains(it.name, ignoreCase = true) && it.name.length >= 4
            }
            ?: kategorien.firstOrNull { it.name.equals("Sonstiges", ignoreCase = true) }
            ?: kategorien.firstOrNull()
    }

    /**
     * Modul 12: aus einer bestätigten Buchung lernen.
     * Öffentlich, damit auch Masken eine Korrektur sofort weitergeben können.
     */
    fun lerneAusBuchung(text: String, categoryId: Long, isIncome: Boolean) {
        viewModelScope.launch {
            runCatching {
                com.finovo.app.util.SmartCategories.lernwoerter(text).forEach { wort ->
                    repository.lerneKategorie(wort, categoryId, isIncome)
                }
            }
        }
    }

    /** Die KI merkt sich die Zuordnung für künftige Buchungen. */
    private suspend fun lerne(
        text: String,
        kategorie: com.finovo.app.data.CategoryEntity?,
        isIncome: Boolean
    ) {
        val id = kategorie?.id ?: return
        com.finovo.app.util.SmartCategories.lernwoerter(text).forEach { wort ->
            repository.lerneKategorie(wort, id, isIncome)
        }
    }
    // ============ Module 18/24/32: Dokumente, Vermögen, Änderungsverlauf ======

    val documents: StateFlow<List<com.finovo.app.data.DocumentEntity>> =
        repository.documents.asList()
    val assets: StateFlow<List<com.finovo.app.data.AssetEntity>> =
        repository.assets.asList()
    val auditLog: StateFlow<List<com.finovo.app.data.AuditEntity>> =
        repository.auditLog.asList()

    fun saveDocument(item: com.finovo.app.data.DocumentEntity) {
        viewModelScope.launch {
            runCatching { repository.saveDocument(item) }
                .onFailure { message(Validation.speicherFehler()) }
        }
    }

    fun deleteDocument(item: com.finovo.app.data.DocumentEntity) {
        viewModelScope.launch {
            runCatching {
                repository.deleteDocument(item)
                repository.schreibeVerlauf(
                    com.finovo.app.data.AuditAction.DOCUMENT_DELETED,
                    "DOCUMENT", item.id, item.title
                )
            }.onFailure { message(Validation.loeschFehler()) }
        }
    }

    fun saveAsset(item: com.finovo.app.data.AssetEntity) {
        viewModelScope.launch {
            runCatching {
                repository.saveAsset(item)
                repository.schreibeVerlauf(
                    com.finovo.app.data.AuditAction.SETTINGS_CHANGED,
                    "ASSET", item.id,
                    if (item.id == 0L) "asset_added" else "asset_edited",
                    item.amountCents
                )
            }.onFailure { message(Validation.speicherFehler()) }
        }
    }

    fun deleteAsset(item: com.finovo.app.data.AssetEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteAsset(item) }
                .onFailure { message(Validation.loeschFehler()) }
        }
    }

    /** Interner Transfer zwischen zwei eigenen Werten (Modul 23). */
    fun transferAssets(
        from: com.finovo.app.data.AssetEntity,
        to: com.finovo.app.data.AssetEntity,
        cents: Long
    ) {
        if (cents <= 0 || from.id == to.id) return
        viewModelScope.launch {
            runCatching {
                repository.saveAsset(from.copy(amountCents = from.amountCents - cents))
                repository.saveAsset(to.copy(amountCents = to.amountCents + cents))
                repository.schreibeVerlauf(
                    com.finovo.app.data.AuditAction.TRANSACTION_EDITED,
                    "TRANSFER", from.id,
                    from.name + " → " + to.name,
                    cents
                )
            }.onFailure { message(Validation.speicherFehler()) }
        }
    }

    fun clearAuditLog() {
        viewModelScope.launch { runCatching { repository.clearAudit() } }
    }

    /** Lesbarer Text für einen Verlaufseintrag. */
    fun auditText(eintrag: com.finovo.app.data.AuditEntity): String {
        val aktion = when (runCatching {
            com.finovo.app.data.AuditAction.valueOf(eintrag.action)
        }.getOrDefault(com.finovo.app.data.AuditAction.SETTINGS_CHANGED)) {
            com.finovo.app.data.AuditAction.TRANSACTION_CREATED ->
                str("audit_created", "Buchung erstellt")
            com.finovo.app.data.AuditAction.TRANSACTION_EDITED ->
                str("audit_edited", "Buchung geändert")
            com.finovo.app.data.AuditAction.TRANSACTION_DELETED ->
                str("audit_deleted", "Buchung gelöscht")
            com.finovo.app.data.AuditAction.BUDGET_CHANGED ->
                str("audit_budget", "Budget geändert")
            com.finovo.app.data.AuditAction.BILL_PAID ->
                str("audit_bill_paid", "Rechnung bezahlt")
            com.finovo.app.data.AuditAction.RECURRING_CHANGED ->
                str("audit_recurring", "Wiederkehrende Buchung geändert")
            com.finovo.app.data.AuditAction.GOAL_CHANGED ->
                str("audit_goal", "Sparziel geändert")
            com.finovo.app.data.AuditAction.DOCUMENT_ADDED ->
                str("audit_document_added", "Dokument hinzugefügt")
            com.finovo.app.data.AuditAction.DOCUMENT_DELETED ->
                str("audit_document_deleted", "Dokument gelöscht")
            com.finovo.app.data.AuditAction.SETTINGS_CHANGED ->
                str("audit_settings", "Einstellung geändert")
        }
        return when (eintrag.detail) {
            "asset_added" -> str("audit_asset_added", "Vermögenswert hinzugefügt")
            "asset_edited" -> str("audit_asset_edited", "Vermögenswert geändert")
            else -> aktion
        }
    }

    // ================================ Modul 14: Prognose =============================

    val prognoseMonat: MutableStateFlow<java.time.YearMonth> =
        MutableStateFlow(java.time.YearMonth.from(Dates.today()))

    fun shiftPrognoseMonat(delta: Long) {
        prognoseMonat.value = prognoseMonat.value.plusMonths(delta)
    }

    val prognose: StateFlow<Prognose.Ergebnis> = combine(
        prognoseMonat, today, expenses, incomes, bills, subscriptions, recurringRules, categories, budget
    ) { werte ->
        @Suppress("UNCHECKED_CAST")
        Prognose.berechne(
            monat = werte[0] as java.time.YearMonth,
            heute = Dates.fromEpochDay(werte[1] as Long),
            ausgaben = werte[2] as List<ExpenseEntity>,
            einnahmen = werte[3] as List<IncomeEntity>,
            rechnungen = werte[4] as List<BillEntity>,
            abos = werte[5] as List<SubscriptionEntity>,
            regeln = werte[6] as List<RecurringRuleEntity>,
            kategorien = werte[7] as List<CategoryEntity>,
            budget = werte[8] as BudgetEntity
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.Eagerly, Prognose.berechne(
            java.time.YearMonth.from(Dates.today()), Dates.today(),
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null
        )
    )

    // =============================== Modul 23: Cashflow ===============================

    enum class Zeitraum { MONAT, JAHR, ALLES }

    val cashflowZeitraum = MutableStateFlow(Zeitraum.MONAT)

    val transfers: StateFlow<List<Cashflow.Transfer>> = auditLog
        .map { liste ->
            liste.filter { it.entityType == "TRANSFER" && it.amountCents != null }
                .map {
                    Cashflow.Transfer(
                        Dates.millisToLocalDate(it.at),
                        it.amountCents ?: 0L,
                        it.detail
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cashflow: StateFlow<Cashflow.Ergebnis> = combine(
        cashflowZeitraum, expenses, incomes, settings, transfers
    ) { werte ->
        @Suppress("UNCHECKED_CAST")
        val art = werte[0] as Zeitraum
        val ausgaben = werte[1] as List<ExpenseEntity>
        val einnahmen = werte[2] as List<IncomeEntity>
        val einst = werte[3] as com.finovo.app.data.SettingsEntity
        val tage = ausgaben.map { it.date } + einnahmen.map { it.date }
        val von = when (art) {
            Zeitraum.MONAT -> Dates.startOfMonth(Dates.today())
            Zeitraum.JAHR -> Dates.startOfYear(Dates.today())
            Zeitraum.ALLES -> if (tage.isEmpty()) Dates.startOfMonth(Dates.today())
            else Dates.fromEpochDay(tage.min())
        }
        val bis = when (art) {
            Zeitraum.MONAT -> Dates.endOfMonth(Dates.today())
            Zeitraum.JAHR -> Dates.endOfYear(Dates.today())
            Zeitraum.ALLES -> if (tage.isEmpty()) Dates.endOfMonth(Dates.today())
            else Dates.fromEpochDay(tage.max())
        }
        Cashflow.berechne(
            von = von,
            bis = bis,
            ausgaben = ausgaben,
            einnahmen = einnahmen,
            eroeffnungCents = einst.openingBalanceCents,
            transfers = werte[4] as List<Cashflow.Transfer>
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.Eagerly, Cashflow.berechne(
            Dates.startOfMonth(Dates.today()), Dates.endOfMonth(Dates.today()),
            emptyList(), emptyList(), 0L, emptyList()
        )
    )

    fun setOpeningBalance(cents: Long) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(openingBalanceCents = cents) }
        }
    }

    // ============================= Modul 24: Nettovermögen =============================

    val netWorth: StateFlow<NetWorth.Ergebnis> = combine(assets, expenses, incomes) { werte ->
        @Suppress("UNCHECKED_CAST")
        NetWorth.berechne(
            werte[0] as List<com.finovo.app.data.AssetEntity>,
            werte[1] as List<ExpenseEntity>,
            werte[2] as List<IncomeEntity>,
            Dates.today()
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.Eagerly,
        NetWorth.berechne(emptyList(), emptyList(), emptyList(), Dates.today())
    )

    // ============================ Modul 26: Jahresbericht =============================

    val berichtJahr: MutableStateFlow<Int> = MutableStateFlow(Dates.today().year)

    fun shiftBerichtJahr(delta: Int) {
        berichtJahr.value = berichtJahr.value + delta
    }

    val jahresbericht: StateFlow<Jahresbericht.Ergebnis> = combine(
        berichtJahr, expenses, incomes, subscriptions, categories, goals, budget
    ) { werte ->
        @Suppress("UNCHECKED_CAST")
        Jahresbericht.berechne(
            jahr = werte[0] as Int,
            ausgaben = werte[1] as List<ExpenseEntity>,
            einnahmen = werte[2] as List<IncomeEntity>,
            abos = werte[3] as List<SubscriptionEntity>,
            kategorien = werte[4] as List<CategoryEntity>,
            ziele = werte[5] as List<SavingGoalEntity>,
            budget = werte[6] as BudgetEntity,
            symbol = waehrung
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.Eagerly, Jahresbericht.berechne(
            Dates.today().year, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, "€"
        )
    )

    // ============================== Modul 16: Einblicke ==============================

    val einblicke: StateFlow<List<Einblicke.Einblick>> = combine(
        expenses, incomes, categories
    ) { werte ->
        @Suppress("UNCHECKED_CAST")
        Einblicke.berechne(
            heute = Dates.today(),
            ausgaben = werte[0] as List<ExpenseEntity>,
            einnahmen = werte[1] as List<IncomeEntity>,
            kategorien = werte[2] as List<CategoryEntity>,
            symbol = waehrung
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ==================== Modul 13: Wiederkehrendes erkennen =========================

    val wiederkehrendVorschlaege: StateFlow<List<WiederkehrendErkennung.Vorschlag>> =
        combine(expenses, incomes) { werte ->
            @Suppress("UNCHECKED_CAST")
            WiederkehrendErkennung.erkennen(
                werte[0] as List<ExpenseEntity>,
                werte[1] as List<IncomeEntity>,
                Dates.today()
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun vorschlagUebernehmen(v: WiederkehrendErkennung.Vorschlag) {
        viewModelScope.launch {
            runCatching {
                repository.addRecurringRule(v.regel)
                repository.schreibeVerlauf(
                    com.finovo.app.data.AuditAction.RECURRING_CHANGED,
                    "RULE", 0L, v.titel, v.betragCents
                )
            }.onFailure { message(Validation.speicherFehler()) }
        }
    }

    // =============================== Modul 15: Alarme ================================

    val alarmZustand: StateFlow<Alarme.Zustand> = settings
        .map { Alarme.ausText(it.alertSettings) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Alarme.standard())

    val aktiveAlarme: StateFlow<List<Alarme.Meldung>> = combine(
        settings, expenses, incomes, bills, subscriptions, recurringRules, categories, assets
    ) { werte ->
        @Suppress("UNCHECKED_CAST")
        val einst = werte[0] as com.finovo.app.data.SettingsEntity
        Alarme.pruefen(
            z = Alarme.ausText(einst.alertSettings),
            heute = Dates.today(),
            ausgaben = werte[1] as List<ExpenseEntity>,
            einnahmen = werte[2] as List<IncomeEntity>,
            rechnungen = werte[3] as List<BillEntity>,
            abos = werte[4] as List<SubscriptionEntity>,
            regeln = werte[5] as List<RecurringRuleEntity>,
            kategorien = werte[6] as List<CategoryEntity>,
            werte = werte[7] as List<com.finovo.app.data.AssetEntity>,
            budget = budget.value,
            symbol = einst.currencySymbol.ifBlank { "€" }
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setAlarm(alarm: Alarm, aktiv: Boolean) {
        viewModelScope.launch {
            val zustand = alarmZustand.value
            val neu = zustand.aktiv.toMutableMap().apply { this[alarm] = aktiv }
            repository.updateSettings {
                it.copy(alertSettings = Alarme.zuText(Alarme.Zustand(neu, zustand.schwellen)))
            }
        }
    }

    fun setAlarmSchwelle(alarm: Alarm, wert: Long) {
        viewModelScope.launch {
            val zustand = alarmZustand.value
            val neu = zustand.schwellen.toMutableMap().apply { this[alarm] = wert.coerceAtLeast(0L) }
            repository.updateSettings {
                it.copy(alertSettings = Alarme.zuText(Alarme.Zustand(zustand.aktiv, neu)))
            }
        }
    }

    // ========================== Modul 21: Dashboard-Widgets ==========================

    val dashboardWidgets: StateFlow<List<Widgets.Art>> = settings
        .map { Widgets.ausText(it.dashboardWidgets) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Widgets.standard())

    /** Verschiebt ein Widget um eine Position und speichert die neue Reihenfolge. */
    fun widgetBewegen(liste: List<Widgets.Art>, index: Int, richtung: Int): List<Widgets.Art> {
        val neu = liste.toMutableList()
        val ziel = index + richtung
        if (index !in neu.indices || ziel !in neu.indices) return liste
        val tmp = neu[index]
        neu[index] = neu[ziel]
        neu[ziel] = tmp
        setWidgetReihenfolge(neu)
        return neu
    }

    fun setWidgetReihenfolge(liste: List<Widgets.Art>) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(dashboardWidgets = Widgets.zuText(liste)) }
        }
    }

    // ========================= Modul 22: Haushalt / Familie ==========================

    val familienMitglieder: StateFlow<List<Familie.Mitglied>> = combine(areas, settings) { werte ->
        @Suppress("UNCHECKED_CAST")
        Familie.ausText(
            (werte[1] as com.finovo.app.data.SettingsEntity).family,
            werte[0] as List<AreaEntity>
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val haushaltsAuswertung: StateFlow<Familie.Auswertung> = combine(
        familienMitglieder, expenses, incomes
    ) { werte ->
        @Suppress("UNCHECKED_CAST")
        Familie.auswerten(
            mitglieder = werte[0] as List<Familie.Mitglied>,
            ausgaben = werte[1] as List<ExpenseEntity>,
            einnahmen = werte[2] as List<IncomeEntity>,
            von = Dates.startOfMonth(Dates.today()),
            bis = Dates.endOfMonth(Dates.today())
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope, SharingStarted.Eagerly,
        Familie.Auswertung(0L, emptyList())
    )

    fun setFamilienRecht(id: Long, sehen: Boolean, bearbeiten: Boolean, exportieren: Boolean) {
        viewModelScope.launch {
            val liste = familienMitglieder.value.toMutableList()
            val index = liste.indexOfFirst { it.id == id }
            if (index >= 0) {
                liste[index] = liste[index].copy(
                    darfSehen = sehen, darfBearbeiten = bearbeiten, darfExportieren = exportieren
                )
                repository.updateSettings { it.copy(family = Familie.zuText(liste)) }
            }
        }
    }

    // ============================ Modul 34: Währungen ===============================

    val waehrungsKurse: StateFlow<Map<String, Long>> = settings
        .map { Waehrungen.ausText(it.fxRates) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Waehrungen.standardKurse())

    fun setKurs(code: String, basispunkte: Long) {
        viewModelScope.launch {
            val neu = waehrungsKurse.value.toMutableMap().apply {
                this[code] = basispunkte.coerceIn(1L, 100_000_000L)
            }
            repository.updateSettings { it.copy(fxRates = Waehrungen.zuText(neu)) }
        }
    }

    fun rechneUm(cents: Long, von: String, nach: String): Long =
        Waehrungen.umrechnen(cents, von, nach, waehrungsKurse.value)

    val standardWaehrung: String get() = settings.value.currency.ifBlank { "EUR" }

    // ============================ Modul 19: Erweiterte Suche =========================

    private val _erweiterteTreffer = MutableStateFlow<List<Suche.Treffer>>(emptyList())
    val erweiterteTreffer: StateFlow<List<Suche.Treffer>> = _erweiterteTreffer

    fun erweiterteSuche(eingabe: String) {
        val kontext = Suche.Kontext(
            ausgaben = expenses.value,
            einnahmen = incomes.value,
            rechnungen = bills.value,
            abos = subscriptions.value,
            ziele = goals.value,
            kategorien = categories.value,
            dokumente = documents.value,
            symbol = waehrung
        )
        _erweiterteTreffer.value = Suche.suche(eingabe, kontext).take(150)
    }

    // ============================= Modul 30: CSV-Import ==============================

    private val _importTabelle = MutableStateFlow<CsvImport.Tabelle?>(null)
    val importTabelle: StateFlow<CsvImport.Tabelle?> = _importTabelle

    private val _importDateiname = MutableStateFlow("")
    val importDateiname: StateFlow<String> = _importDateiname

    fun importLesen(text: String, dateiname: String = "") {
        _importDateiname.value = dateiname
        _importTabelle.value = CsvImport.lesen(text)
    }

    fun importZuruecksetzen() {
        _importTabelle.value = null
        _importDateiname.value = ""
    }

    /** Übernimmt die geprüften Zeilen in die Datenbank. */
    fun importUebernehmen(
        zeilen: List<CsvImport.Zeile>,
        zuordnung: CsvImport.Zuordnung,
        standardKategorieId: Long,
        onFertig: (Int, Int) -> Unit
    ) {
        viewModelScope.launch {
            var ok = 0
            var fehler = 0
            zeilen.filter { it.ok }.forEach { z ->
                val datum = z.datum ?: return@forEach
                val cents = z.betragCents ?: return@forEach
                val kategorieId = CsvImport.kategorieFinden(z.kategorieName, categories.value)
                    .takeIf { it != 0L } ?: standardKategorieId
                val tag = datum.toEpochDay()
                if (cents <= 0 || cents > Validation.MAX_BETRAG_CENTS) {
                    fehler++
                    return@forEach
                }
                if (z.istEinnahme) {
                    repository.addIncome(
                        IncomeEntity(
                            amountCents = cents,
                            categoryId = kategorieId,
                            date = tag,
                            note = z.beschreibung,
                            merchant = z.beschreibung.take(60)
                        )
                    )
                } else {
                    repository.addExpense(
                        ExpenseEntity(
                            amountCents = cents,
                            categoryId = kategorieId,
                            date = tag,
                            note = z.beschreibung,
                            merchant = z.beschreibung.take(60)
                        )
                    )
                }
                repository.schreibeVerlauf(
                    com.finovo.app.data.AuditAction.TRANSACTION_CREATED,
                    "IMPORT", 0L, z.beschreibung, cents
                )
                lerneAusBuchung(z.beschreibung, kategorieId, z.istEinnahme)
                ok++
            }
            refreshAfterChange()
            onFertig(ok, fehler)
        }
    }

    // ============================ Module 17/18: Belege ===============================

    /** Speichert einen gescannten Beleg als Dokument und optional als Ausgabe. */
    fun belegSpeichern(
        titel: String,
        pfad: String,
        datum: Long,
        cents: Long?,
        haendler: String,
        kategorieId: Long,
        notiz: String,
        tags: String,
        mwstCents: Long?,
        alsAusgabe: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                val dokument = com.finovo.app.data.DocumentEntity(
                    title = titel,
                    kind = com.finovo.app.data.DocumentKind.RECEIPT.name,
                    path = pfad,
                    date = datum,
                    tags = tags,
                    note = notiz + (mwstCents?.let { " · MwSt " + Money.format(it, waehrung) } ?: ""),
                    amountCents = cents,
                    merchant = haendler,
                    mimeType = "image/jpeg",
                    createdAt = System.currentTimeMillis()
                )
                val dokumentId = repository.saveDocument(dokument)
                if (alsAusgabe && cents != null && cents > 0) {
                    val id = repository.addExpense(
                        ExpenseEntity(
                            amountCents = cents,
                            categoryId = kategorieId,
                            date = datum,
                            note = notiz,
                            merchant = haendler,
                            receiptUri = pfad
                        )
                    )
                    repository.saveDocument(dokument.copy(id = dokumentId, linkedExpenseId = id))
                    repository.schreibeVerlauf(
                        com.finovo.app.data.AuditAction.TRANSACTION_CREATED,
                        "EXPENSE", id, haendler.ifBlank { titel }, cents
                    )
                    lerneAusBuchung(haendler.ifBlank { titel }, kategorieId, false)
                } else {
                    repository.schreibeVerlauf(
                        com.finovo.app.data.AuditAction.DOCUMENT_ADDED,
                        "DOCUMENT", dokumentId, titel, cents
                    )
                }
                refreshAfterChange()
            }.onFailure { message(Validation.speicherFehler()) }
        }
    }

}

sealed class ExportPeriod {
    data object ThisMonth : ExportPeriod()
    data object LastMonth : ExportPeriod()
    data object ThisYear : ExportPeriod()
    data class CustomRange(val from: LocalDate, val to: LocalDate) : ExportPeriod()
}

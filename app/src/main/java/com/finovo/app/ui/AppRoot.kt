package com.finovo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.ui.screens.GoalsScreen
import com.finovo.app.ui.screens.SubscriptionsScreen
import com.finovo.app.ui.screens.AreasScreen
import com.finovo.app.ui.screens.CarScreen
import com.finovo.app.ui.screens.SearchScreen
import com.finovo.app.ui.screens.CalendarScreen
import com.finovo.app.ui.screens.InsightsScreen
import com.finovo.app.ui.screens.WhatIfScreen
import com.finovo.app.ui.screens.ReceiptsScreen
import com.finovo.app.ui.screens.AboutScreen
import com.finovo.app.ui.screens.PrognoseScreen
import com.finovo.app.ui.screens.CashflowScreen
import com.finovo.app.ui.screens.NetWorthScreen
import com.finovo.app.ui.screens.JahresberichtScreen
import com.finovo.app.ui.screens.DokumenteScreen
import com.finovo.app.ui.screens.ImportScreen
import com.finovo.app.ui.screens.VerlaufScreen
import com.finovo.app.ui.screens.HaushaltScreen
import com.finovo.app.ui.screens.AlarmeScreen
import com.finovo.app.ui.screens.DashboardAnpassenScreen
import com.finovo.app.ui.screens.BelegScanScreen
import com.finovo.app.ui.screens.BillsScreen
import com.finovo.app.ui.screens.BudgetScreen
import com.finovo.app.ui.screens.CategoriesScreen
import com.finovo.app.ui.screens.DashboardScreen
import com.finovo.app.ui.screens.DataScreen
import com.finovo.app.ui.screens.ExpensesScreen
import com.finovo.app.ui.screens.FinovoAiScreen
import com.finovo.app.ui.screens.IncomeScreen
import com.finovo.app.ui.screens.IncomeSheet
import com.finovo.app.ui.screens.BillSheet
import com.finovo.app.ui.screens.ExpenseSheet
import com.finovo.app.ui.screens.LockScreen
import com.finovo.app.ui.screens.MoreScreen
import com.finovo.app.ui.screens.BankComingSoonScreen
import com.finovo.app.ui.screens.BankScreen
import com.finovo.app.ui.screens.ScanReviewScreen
import com.finovo.app.ui.screens.QuickAddSheet
import com.finovo.app.ui.screens.RecurringScreen
import com.finovo.app.ui.screens.RecurringSheet
import com.finovo.app.ui.screens.AnalyticsScreen
import com.finovo.app.ui.screens.SettingsScreen
import com.finovo.app.ui.theme.FinovoTheme
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import com.finovo.app.ui.components.*
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.*
import com.finovo.app.data.*
import androidx.compose.runtime.mutableStateOf
import com.finovo.app.ui.screens.SplashScreen
import com.finovo.app.util.S
import com.finovo.app.util.str
import androidx.compose.ui.platform.LocalContext

private data class TabItem(
    val route: Route,
    val label: String,
    val icon: ImageVector
)

private fun tabKey(route: Route): String = when (route) {
    is Route.Home -> "nav_home"
    is Route.Expenses -> "nav_expenses"
    is Route.Income -> "nav_income"
    is Route.Bills -> "nav_bills"
    is Route.More -> "nav_more"
    else -> ""
}

private val tabs = listOf(
    TabItem(Route.Home, "Übersicht", Icons.Default.Home),
    TabItem(Route.Expenses, "Ausgaben", Icons.Default.TrendingDown),
    TabItem(Route.Income, "Einnahmen", Icons.Default.TrendingUp),
    TabItem(Route.Bills, "Rechnungen", Icons.Default.ReceiptLong),
    TabItem(Route.More, "Mehr", Icons.Default.MoreHoriz)
)

@Composable
fun AppRoot(viewModel: AppViewModel, onBiometric: () -> Unit, onDeviceCredential: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsState()
    val stack by viewModel.stack.collectAsState()
    val current = stack.lastOrNull() ?: Route.Home
    val locked by viewModel.locked.collectAsState()
    val sheet by viewModel.sheet.collectAsState()
    val biometricAvailable by viewModel.biometricAvailable.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var splashVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            if (message.isNotBlank()) {
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    BackHandler(enabled = stack.size > 1) {
        viewModel.back()
    }

    FinovoTheme(
        themeMode = runCatching {
            com.finovo.app.data.ThemeMode.valueOf(settings.themeMode)
        }.getOrDefault(com.finovo.app.data.ThemeMode.SYSTEM)
    ) {
        val context = LocalContext.current
        com.finovo.app.util.ProvideLanguage(
            com.finovo.app.util.AppLocale.resolve(context)
        ) {
        com.finovo.app.ui.theme.FinovoBackground {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            if (splashVisible) {
                SplashScreen { splashVisible = false }
            } else if (locked) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    LockScreen(
                        onUnlocked = { viewModel.unlock() },
                        onBiometric = onBiometric,
                        biometricEnabled = settings.biometricEnabled && biometricAvailable,
                        verify = { viewModel.checkPin(it) },
                        hasPin = settings.pinHash.isNotBlank(),
                        deviceCredential = viewModel.security.collectAsState().value.deviceCredential,
                        onDeviceCredential = onDeviceCredential,
                        info = viewModel.lockInfo.collectAsState().value
                    )
                }
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .navigationBarsPadding()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 4.dp)
                                .shadow(
                                    16.dp,
                                    RoundedCornerShape(30.dp),
                                    ambientColor = appColors().glow.copy(alpha = 0.35f),
                                    spotColor = appColors().glow.copy(alpha = 0.35f)
                                )
                                .clip(RoundedCornerShape(30.dp))
                                .border(
                                    1.dp,
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            appColors().cardBorder.copy(alpha = 0.7f),
                                            appColors().cyan.copy(alpha = if (appColors().isDark) 0.35f else 0.15f),
                                            appColors().cardBorder.copy(alpha = 0.7f)
                                        )
                                    ),
                                    RoundedCornerShape(30.dp)
                                )
                        ) {
                        NavigationBar(
                            containerColor = if (appColors().isDark) Color(0xF00A1533) else Color.White,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            val selectedTab = stack.lastOrNull { tab ->
                                tabs.any { it.route.key == tab.key }
                            } ?: Route.Home
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = selectedTab.key == tab.route.key,
                                    onClick = { viewModel.selectTab(tab.route) },
                                    icon = {
                                        Icon(
                                            tab.icon,
                                            contentDescription = S(tabKey(tab.route), tab.label),
                                            modifier = androidx.compose.ui.Modifier.size(22.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            S(tabKey(tab.route), tab.label),
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = if (appColors().isDark) 0.18f else 0.10f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                        }
                    },
                    floatingActionButton = {
                        val showFab = when (current) {
                            is Route.Home, is Route.Expenses, is Route.Income, is Route.Bills -> true
                            else -> false
                        }
                        if (showFab) {
                            FloatingActionButton(
                                modifier = Modifier
                                    .shadow(
                                        18.dp,
                                        androidx.compose.foundation.shape.CircleShape,
                                        ambientColor = appColors().glow,
                                        spotColor = appColors().glow
                                    )
                                    .border(
                                        1.5.dp,
                                        appColors().cyan.copy(alpha = 0.55f),
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                                    .background(primaryGradient(), androidx.compose.foundation.shape.CircleShape),
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                                onClick = {
                                    when (current) {
                                        is Route.Home -> viewModel.openQuickAdd()
                                        is Route.Expenses -> viewModel.openExpenseSheet(null)
                                        is Route.Income -> viewModel.openIncomeSheet(null)
                                        is Route.Bills -> viewModel.openBillSheet(null)
                                        else -> viewModel.openQuickAdd()
                                    }
                                },
                                containerColor = Color.Transparent,
                                contentColor = Color.White,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = S("hinzufuegen_3", "Hinzufügen"),
                                    modifier = androidx.compose.ui.Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (current) {
                            is Route.Home -> DashboardScreen(
                                viewModel = viewModel,
                                onSeeAllBills = { viewModel.selectTab(Route.Bills) },
                                onSeeAllTransactions = { viewModel.selectTab(Route.Expenses) },
                                onOpenAnalytics = { viewModel.navigate(Route.Analytics) },
                                onOpenBudget = { viewModel.navigate(Route.Budget) }
                            )
                            is Route.Expenses -> ExpensesScreen(viewModel) { viewModel.selectTab(Route.Home) }
                            is Route.Income -> IncomeScreen(viewModel) { viewModel.selectTab(Route.Home) }
                            is Route.Bills -> BillsScreen(viewModel)
                            is Route.More -> MoreScreen(viewModel) { viewModel.navigate(it) }
                            is Route.FinovoAi -> FinovoAiScreen(viewModel) { viewModel.back() }
                            is Route.Forecast -> PrognoseScreen(viewModel) { viewModel.back() }
                            is Route.Cashflow -> CashflowScreen(viewModel) { viewModel.back() }
                            is Route.NetWorth -> NetWorthScreen(viewModel) { viewModel.back() }
                            is Route.YearReport -> JahresberichtScreen(viewModel) { viewModel.back() }
                            is Route.Documents -> DokumenteScreen(viewModel) { viewModel.back() }
                            is Route.Import -> ImportScreen(viewModel) { viewModel.back() }
                            is Route.Audit -> VerlaufScreen(viewModel) { viewModel.back() }
                            is Route.Household -> HaushaltScreen(viewModel) { viewModel.back() }
                            is Route.Alarms -> AlarmeScreen(viewModel) { viewModel.back() }
                            is Route.WidgetsEdit -> DashboardAnpassenScreen(viewModel) { viewModel.back() }
                            is Route.Analytics -> AnalyticsScreen(viewModel) { viewModel.back() }
                            is Route.Budget -> BudgetScreen(viewModel) { viewModel.back() }
                            is Route.Recurring -> RecurringScreen(viewModel) { viewModel.back() }
                            is Route.Categories -> CategoriesScreen(viewModel) { viewModel.back() }
                            is Route.Data -> DataScreen(viewModel) { viewModel.back() }
                            is Route.Goals -> GoalsScreen(viewModel) { viewModel.back() }
                            is Route.Subscriptions -> SubscriptionsScreen(viewModel) { viewModel.back() }
                            is Route.Areas -> AreasScreen(viewModel) { viewModel.back() }
                            is Route.Car -> CarScreen(viewModel) { viewModel.back() }
                            is Route.Search -> SearchScreen(viewModel) { viewModel.back() }
                            is Route.Calendar -> CalendarScreen(viewModel) { viewModel.back() }
                            is Route.Insights -> InsightsScreen(viewModel) { viewModel.back() }
                            is Route.WhatIf -> WhatIfScreen(viewModel) { viewModel.back() }
                            is Route.Receipts -> ReceiptsScreen(viewModel) { viewModel.back() }
                            is Route.Settings -> SettingsScreen(
                                viewModel,
                                onBack = { viewModel.back() },
                                onNavigate = { viewModel.navigate(it) }
                            )
                            is Route.About -> AboutScreen(viewModel) { viewModel.back() }
                            is Route.ScanReview -> ScanReviewScreen(viewModel) { viewModel.back() }
                            is Route.ReceiptScan -> BelegScanScreen(viewModel) { viewModel.back() }
                            is Route.Bank ->
                                if (com.finovo.app.bank.BankFeature.LIVE) {
                                    BankScreen(viewModel) { viewModel.back() }
                                } else {
                                    BankComingSoonScreen { viewModel.back() }
                                }
                        }
                    }
                }
                }

                // ------------------------- sheets ---------------------------
                when (sheet.kind) {
                    SheetKind.QUICK_ADD -> QuickAddSheet(viewModel) { viewModel.closeSheet() }
                    SheetKind.EXPENSE -> ExpenseSheet(viewModel, sheet.expense) { viewModel.closeSheet() }
                    SheetKind.INCOME -> IncomeSheet(viewModel, sheet.income) { viewModel.closeSheet() }
                    SheetKind.BILL -> BillSheet(viewModel, sheet.bill) { viewModel.closeSheet() }
                    SheetKind.RECURRING -> RecurringSheet(viewModel, sheet.rule) { viewModel.closeSheet() }
                    SheetKind.NONE -> Unit
                }
            }
        }
        }
    }
}

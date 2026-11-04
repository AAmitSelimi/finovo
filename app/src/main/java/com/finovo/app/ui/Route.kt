package com.finovo.app.ui
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

sealed class Route(val key: String) {
    data object Home : Route("home")
    data object Expenses : Route("expenses")
    data object Income : Route("income")
    data object Bills : Route("bills")
    data object More : Route("more")
    data object Analytics : Route("analytics")
    data object Budget : Route("budget")
    data object Recurring : Route("recurring")
    data object Categories : Route("categories")
    data object Data : Route("data")
    data object Goals : Route("goals")
    data object Subscriptions : Route("subscriptions")
    data object Areas : Route("areas")
    data object Car : Route("car")
    data object Search : Route("search")
    data object Calendar : Route("calendar")
    data object Insights : Route("insights")
    data object WhatIf : Route("whatif")
    data object Receipts : Route("receipts")
    data object Settings : Route("settings")
    data object About : Route("about")
    data object ScanReview : Route("scanreview")
    data object ReceiptScan : Route("receiptscan")
    data object Bank : Route("bank")
    data object FinovoAi : Route("finovoai")
    data object Forecast : Route("forecast")
    data object Cashflow : Route("cashflow")
    data object NetWorth : Route("networth")
    data object YearReport : Route("yearreport")
    data object Documents : Route("documents")
    data object Import : Route("import")
    data object Audit : Route("audit")
    data object Household : Route("household")
    data object Alarms : Route("alarms")
    data object WidgetsEdit : Route("widgetsedit")
    data object Tutorials : Route("tutorials")

    companion object {
        val tabs = listOf(Home, Expenses, Income, Bills, More)
        fun isTab(route: Route): Boolean = tabs.any { it.key == route.key }
    }
}

enum class SheetKind { NONE, QUICK_ADD, EXPENSE, INCOME, BILL, RECURRING }

data class SheetState(
    val kind: SheetKind = SheetKind.NONE,
    val expense: com.finovo.app.data.ExpenseEntity? = null,
    val income: com.finovo.app.data.IncomeEntity? = null,
    val bill: com.finovo.app.data.BillEntity? = null,
    val rule: com.finovo.app.data.RecurringRuleEntity? = null
)

package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.DateFilter
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.WhatIf
import java.time.LocalDate
import com.finovo.app.util.S
import com.finovo.app.util.str

@Composable
fun WhatIfScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val dashboard by viewModel.dashboard.collectAsState()

    val monthlyIncome = dashboard.incomeCents
    val monthlyExpense = dashboard.expenseCents
    val currentBalance = monthlyIncome - monthlyExpense

    // --- Sparplan ---
    var saveText by remember { mutableStateOf("200") }
    var monthsText by remember { mutableStateOf("12") }
    var startText by remember { mutableStateOf("0") }

    val saveCents = Money.parseToCents(saveText) ?: 0L
    val months = monthsText.toIntOrNull()?.coerceIn(1, 600) ?: 12
    val startCents = Money.parseToCents(startText) ?: 0L
    val plan = WhatIf.savingPlan(saveCents, months, startCents)

    // --- Ausgabenänderung ---
    var expenseDeltaText by remember { mutableStateOf("300") }
    val expenseDelta = Money.parseToCents(expenseDeltaText) ?: 0L
    val expenseResult = WhatIf.expenseChange(monthlyIncome, monthlyExpense, expenseDelta)

    // --- Einnahmenänderung ---
    var incomeDeltaText by remember { mutableStateOf("0") }
    val incomeDelta = Money.parseToCents(incomeDeltaText) ?: 0L
    val incomeResult = WhatIf.incomeChange(monthlyIncome, monthlyExpense, incomeDelta)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar(
                tutorialId = "whatif",
                title = S("whatif", "Was-wäre-wenn"),
                subtitle = S("finanzielle_situationen_simulieren", "Finanzielle Situationen simulieren"),
                onBack = onBack
            )
        }

        // Aktueller Monat als Basis
        item {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(S("aktueller_monat", "Aktueller Monat"), style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(S("einnahmen_2", "Einnahmen"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                            Text(Money.format(monthlyIncome, symbol), style = MaterialTheme.typography.titleMedium, color = appColors().income,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        }
                        Column {
                            Text(S("ausgaben_2", "Ausgaben"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                            Text(Money.format(monthlyExpense, symbol), style = MaterialTheme.typography.titleMedium, color = appColors().expense,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(S("saldo", "Saldo"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
                            Text(
                                Money.format(currentBalance, symbol),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (currentBalance >= 0) appColors().income else appColors().expense,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Sparplan
        item {
            SectionHeader(title = S("wenn_ich_spare", "Wenn ich spare …"))
        }
        item {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AmountField(
                            value = saveText,
                            onValueChange = { saveText = it },
                            symbol = symbol,
                            label = S("pro_monat_4", "pro Monat"),
                            modifier = Modifier.weight(1f)
                        )
                        AppTextField(
                            value = monthsText,
                            onValueChange = { monthsText = it.filter { c -> c.isDigit() } },
                            label = S("monate", "Monate"),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    AmountField(
                        value = startText,
                        onValueChange = { startText = it },
                        symbol = symbol,
                        label = S("startbetrag", "Startbetrag")
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        S("nach_months_monaten_money_format_plan", "Nach %2\$s Monaten: %1\$s", Money.format(plan.totalCents, symbol), months),
                        style = MaterialTheme.typography.titleLarge,
                        color = appColors().income,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        S("das_sind_money_format_savecents_12_sym", "Das sind %1\$s pro Jahr", Money.format(saveCents * 12, symbol)) +
                            if (months >= 12) S("bzw_pro_jahr_im_schnitt", " bzw. %1\$s pro Jahr im Schnitt.", Money.format(plan.totalCents / (months / 12), symbol)) else ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                    Spacer(Modifier.height(14.dp))
                    SavingsChart(points = plan.points)
                    Spacer(Modifier.height(12.dp))
                    val goalMonths = WhatIf.monthsToGoal(100_000_00L, saveCents)
                    if (goalMonths > 0) {
                        Text(
                            S("mit_money_format_savecents_symbol_im_m", "Mit %1\$s im Monat erreichst du 100.000 € in %2\$s Monaten.", Money.format(saveCents, symbol), goalMonths),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }
        }

        // Ausgaben ändern
        item { SectionHeader(title = S("wenn_sich_meine_ausgaben_aendern", "Wenn sich meine Ausgaben ändern …")) }
        item {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    AmountField(
                        value = expenseDeltaText,
                        onValueChange = { expenseDeltaText = it },
                        symbol = symbol,
                        label = S("veraenderung_pro_monat_mehr_weniger", "Veränderung pro Monat (+ mehr, − weniger)")
                    )
                    Spacer(Modifier.height(12.dp))
                    ResultRow(S("neue_ausgaben", "Neue Ausgaben"), Money.format(expenseResult.expenseCents, symbol), appColors().expense)
                    ResultRow(str("whatif_new_balance", "Neuer Saldo"), Money.format(expenseResult.newBalanceCents, symbol),
                        if (expenseResult.newBalanceCents >= 0) appColors().income else appColors().expense)
                    ResultRow(
                        "Differenz",
                        Money.format(expenseResult.changeCents, symbol),
                        if (expenseResult.changeCents >= 0) appColors().income else appColors().expense
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        S("auf_ein_jahr_gerechnet_money_format_ex", "Auf ein Jahr gerechnet: %1\$s", Money.format(expenseResult.changeCents * 12, symbol)),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("-500", "-200", "+200", "+500").forEach { v ->
                            SecondaryButton(
                                text = v,
                                onClick = { expenseDeltaText = v.trimStart('+') },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Einnahmen ändern
        item { SectionHeader(title = S("wenn_sich_meine_einnahmen_aendern", "Wenn sich meine Einnahmen ändern …")) }
        item {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    AmountField(
                        value = incomeDeltaText,
                        onValueChange = { incomeDeltaText = it },
                        symbol = symbol,
                        label = S("veraenderung_pro_monat", "Veränderung pro Monat")
                    )
                    Spacer(Modifier.height(12.dp))
                    ResultRow(S("neue_einnahmen", "Neue Einnahmen"), Money.format(incomeResult.incomeCents, symbol), appColors().income)
                    ResultRow(str("whatif_new_balance", "Neuer Saldo"), Money.format(incomeResult.newBalanceCents, symbol),
                        if (incomeResult.newBalanceCents >= 0) appColors().income else appColors().expense)
                    ResultRow("Differenz", Money.format(incomeResult.changeCents, symbol),
                        if (incomeResult.changeCents >= 0) appColors().income else appColors().expense)
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                S("alle_rechnungen_basieren_auf_cent_wert", "Alle Rechnungen basieren auf Cent-Werten – es entstehen keine Rundungsfehler."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = appColors().textMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SavingsChart(points: List<Pair<Int, Long>>) {
    val max = points.maxOf { it.second }.coerceAtLeast(1)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val step = (points.size / 12).coerceAtLeast(1)
            points.filter { it.first % step == 0 }.forEach { (_, value) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(((value.toFloat() / max.toFloat()).coerceIn(0.02f, 1f) * 120f).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(appColors().income)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(S("start", "Start"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
            Text(S("nach_points_lastornull_first_0_monaten", "nach %1\$s Monaten", points.lastOrNull()?.first ?: 0), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        }
    }
}

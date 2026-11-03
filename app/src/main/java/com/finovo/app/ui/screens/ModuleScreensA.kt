package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.AssetEntity
import com.finovo.app.data.AssetKind
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.DropdownField
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.InfoRow
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.Prognose
import com.finovo.app.util.S
import com.finovo.app.util.str
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// ===========================================================================
// Modul 14 - Prognose zum Monatsende
// ===========================================================================

@Composable
fun PrognoseScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }
    val ergebnis by viewModel.prognose.collectAsState()
    val monat by viewModel.prognoseMonat.collectAsState()
    val kategorien by viewModel.categories.collectAsState()

    val label = monat.month.getDisplayName(TextStyle.FULL, AppLocaleOrGerman()) + " " + monat.year

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("forecast_title", "Prognose"),
            subtitle = S("forecast_sub", "Erwartetes Monatsende aus echten Daten"),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.shiftPrognoseMonat(-1) }) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = S("zurueck", "Zurück")
                        )
                    }
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { viewModel.shiftPrognoseMonat(1) }) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = S("weiter", "Weiter")
                        )
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            S("forecast_remaining", "Voraussichtlich übrig am Monatsende"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                        AmountText(
                            ergebnis.restbetragCents,
                            symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (ergebnis.restbetragCents < 0) appColors().expense
                            else appColors().income
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            S(
                                "forecast_basis",
                                "Stand: Tag %1\$s von %2\$s",
                                ergebnis.tageVorbei.toString(),
                                ergebnis.tageImMonat.toString()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(
                            S("forecast_income_now", "Einnahmen bisher"),
                            Money.format(ergebnis.einnahmenBisherCents, symbol)
                        )
                        InfoRow(
                            S("forecast_income_expected", "Erwartete Einnahmen"),
                            Money.format(ergebnis.einnahmenErwartetCents, symbol)
                        )
                        InfoRow(
                            S("forecast_fixed", "Fixkosten (geplant)"),
                            Money.format(ergebnis.fixkostenErwartetCents, symbol)
                        )
                        InfoRow(
                            S("forecast_variable_now", "Variable Ausgaben bisher"),
                            Money.format(ergebnis.variableBisherCents, symbol)
                        )
                        InfoRow(
                            S("forecast_variable_expected", "Variable Ausgaben (hochgerechnet)"),
                            Money.format(ergebnis.variablePrognoseCents, symbol)
                        )
                        InfoRow(
                            S("forecast_total", "Voraussichtliche Ausgaben"),
                            Money.format(ergebnis.ausgabenPrognoseCents, symbol)
                        )
                        InfoRow(
                            S("forecast_savings", "Mögliches Sparen"),
                            Money.format(ergebnis.sparenMoeglichCents, symbol),
                            valueColor = appColors().income
                        )
                    }
                }
            }

            if (ergebnis.risiko) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = appColors().warningContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                S("forecast_risk_title", "Mögliche Überschreitung"),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (ergebnis.budgetLimitCents > 0) {
                                    S(
                                        "forecast_risk_budget",
                                        "Die hochgerechneten Ausgaben liegen über deinem Budget von %1\$s.",
                                        Money.format(ergebnis.budgetLimitCents, symbol)
                                    )
                                } else {
                                    S(
                                        "forecast_risk_negative",
                                        "Nach dieser Rechnung wäre der Monat negativ. Du kannst Ausgaben prüfen oder verschieben."
                                    )
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(S("forecast_upcoming", "Anstehende Zahlungen"))
            }

            if (ergebnis.anstehend.isEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            S("forecast_no_upcoming", "Keine terminierten Zahlungen in diesem Monat."),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                    }
                }
            } else {
                items(ergebnis.anstehend) { z ->
                    AppCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(z.name, fontWeight = FontWeight.Medium)
                                Text(
                                    Dates.full(z.date) + " · " + when (z.art) {
                                        Prognose.Art.RECHNUNG -> str("kind_bill", "Rechnung")
                                        Prognose.Art.ABO -> str("kind_subscription", "Abo")
                                        Prognose.Art.REGEL -> str("kind_rule", "Wiederkehrend")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            AmountText(z.cents, symbol, color = appColors().expense)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppLocaleOrGerman(): Locale =
    runCatching { Locale.forLanguageTag(com.finovo.app.util.AppLocale.current()) }
        .getOrDefault(Locale.GERMAN)

// ===========================================================================
// Modul 23 - Cashflow-Analyse
// ===========================================================================

@Composable
fun CashflowScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }
    val ergebnis by viewModel.cashflow.collectAsState()
    val zeitraum by viewModel.cashflowZeitraum.collectAsState()

    var eroeffnung by remember { mutableStateOf("") }
    var zeigtFormular by remember { mutableStateOf(false) }

    val arten = listOf(
        str("cf_month", "Dieser Monat"),
        str("cf_year", "Dieses Jahr"),
        str("cf_all", "Gesamt")
    )
    val index = when (zeitraum) {
        AppViewModel.Zeitraum.MONAT -> 0
        AppViewModel.Zeitraum.JAHR -> 1
        AppViewModel.Zeitraum.ALLES -> 2
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("cf_title", "Cashflow"),
            subtitle = S("cf_sub", "Startbestand, Zu- und Abflüsse, Endbestand"),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    arten.forEachIndexed { i, text ->
                        val aktiv = i == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (aktiv) appColors().incomeContainer else appColors().cardAlt,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    viewModel.cashflowZeitraum.value = when (i) {
                                        0 -> AppViewModel.Zeitraum.MONAT
                                        1 -> AppViewModel.Zeitraum.JAHR
                                        else -> AppViewModel.Zeitraum.ALLES
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            S("cf_end", "Endbestand"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                        AmountText(
                            ergebnis.endCents,
                            symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (ergebnis.endCents < 0) appColors().expense
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(
                            S("cf_start", "Startbestand"),
                            Money.format(ergebnis.startCents, symbol)
                        )
                        InfoRow(
                            S("cf_income", "Einnahmen"),
                            Money.format(ergebnis.einnahmenCents, symbol),
                            valueColor = appColors().income
                        )
                        InfoRow(
                            S("cf_expenses", "Ausgaben"),
                            Money.format(ergebnis.ausgabenCents, symbol),
                            valueColor = appColors().expense
                        )
                        InfoRow(
                            S("cf_transfers", "Transfers (eigene Konten)"),
                            Money.format(ergebnis.transfersCents, symbol)
                        )
                        InfoRow(
                            S("cf_savings", "Ersparnis im Zeitraum"),
                            Money.format(ergebnis.ersparnisCents, symbol),
                            valueColor = if (ergebnis.ersparnisCents >= 0) appColors().income
                            else appColors().expense
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    S("cf_opening", "Eröffnungsbestand"),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    Money.format(einstellungen.openingBalanceCents, symbol),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            OutlinedButton(onClick = {
                                zeigtFormular = !zeigtFormular
                                eroeffnung = if (einstellungen.openingBalanceCents == 0L) ""
                                else Money.format(
                                    einstellungen.openingBalanceCents, "", withSymbol = false
                                )
                            }) {
                                Text(S("edit", "Bearbeiten"))
                            }
                        }
                        if (zeigtFormular) {
                            Spacer(Modifier.height(8.dp))
                            AmountField(
                                value = eroeffnung,
                                onValueChange = { eroeffnung = it },
                                symbol = symbol,
                                label = S("cf_opening", "Eröffnungsbestand")
                            )
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton(
                                text = S("save", "Speichern"),
                                onClick = {
                                    val cents = Money.parseToCents(eroeffnung) ?: 0L
                                    viewModel.setOpeningBalance(cents)
                                    zeigtFormular = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(S("cf_months", "Monatliche Übersicht"))
            }

            if (ergebnis.punkte.isEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            S("cf_no_data", "Für diesen Zeitraum sind noch keine Buchungen erfasst."),
                            modifier = Modifier.padding(16.dp),
                            color = appColors().textMuted
                        )
                    }
                }
            } else {
                items(ergebnis.punkte) { p ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                p.monat.month.getDisplayName(TextStyle.SHORT, AppLocaleOrGerman()) +
                                    " " + p.monat.year,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                Money.format(p.einnahmenCents, symbol),
                                color = appColors().income,
                                modifier = Modifier.width(90.dp),
                                textAlign = TextAlign.End
                            )
                            Text(
                                Money.format(p.ausgabenCents, symbol),
                                color = appColors().expense,
                                modifier = Modifier.width(90.dp),
                                textAlign = TextAlign.End
                            )
                            Text(
                                Money.format(p.saldoCents, symbol),
                                color = if (p.saldoCents >= 0) appColors().income else appColors().expense,
                                modifier = Modifier.width(90.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ===========================================================================
// Modul 24 - Nettovermögen
// ===========================================================================

@Composable
fun NetWorthScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }
    val ergebnis by viewModel.netWorth.collectAsState()
    val werte by viewModel.assets.collectAsState()

    var zeigtFormular by remember { mutableStateOf(false) }
    var bearbeiten by remember { mutableStateOf<AssetEntity?>(null) }
    var loeschen by remember { mutableStateOf<AssetEntity?>(null) }
    var zeigtTransfer by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("nw_title", "Nettovermögen"),
            subtitle = S("nw_sub", "Vermögen minus Schulden"),
            onBack = onBack,
            actions = {
                IconButton(onClick = { bearbeiten = null; zeigtFormular = true }) {
                    Icon(Icons.Default.Add, contentDescription = S("nw_add", "Wert hinzufügen"))
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            S("nw_net", "Nettovermögen"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                        AmountText(
                            ergebnis.nettoCents,
                            symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (ergebnis.nettoCents < 0) appColors().expense
                            else appColors().income
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    S("nw_assets", "Vermögen"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                                AmountText(
                                    ergebnis.vermoegenCents, symbol,
                                    color = appColors().income
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    S("nw_debts", "Schulden"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                                AmountText(
                                    ergebnis.schuldenCents, symbol,
                                    color = appColors().expense
                                )
                            }
                        }
                    }
                }
            }

            // Entwicklung über die Zeit
            if (ergebnis.punkte.size > 1) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(S("nw_history", "Entwicklung"))
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val max = ergebnis.punkte.maxOf { kotlin.math.abs(it.cents) }.coerceAtLeast(1)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(120.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                ergebnis.punkte.forEach { p ->
                                    val anteil = (kotlin.math.abs(p.cents).toFloat() / max).coerceIn(0.02f, 1f)
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight(anteil)
                                                .width(18.dp)
                                                .background(
                                                    if (p.cents >= 0) appColors().income
                                                    else appColors().expense,
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                        Text(
                                            p.monat.month.getDisplayName(
                                                TextStyle.NARROW, AppLocaleOrGerman()
                                            ),
                                            fontSize = 9.sp,
                                            color = appColors().textMuted
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                S(
                                    "nw_history_note",
                                    " Berechnet aus deinen Buchungen: Einnahmen minus Ausgaben, vom heutigen Stand zurückgerechnet."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(S("nw_list", "Deine Werte"))
            }

            if (werte.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "\uD83D\uDCB0",
                        title = S("nw_empty_title", "Noch keine Werte erfasst"),
                        message = S(
                            "nw_empty_text",
                            "Trage Konten, Bargeld, Sparguthaben, Anlagen oder Schulden ein – das Nettovermögen wird daraus berechnet."
                        ),
                        actionLabel = S("nw_add", "Wert hinzufügen"),
                        onAction = { bearbeiten = null; zeigtFormular = true }
                    )
                }
            } else {
                items(werte) { w ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(w.name, fontWeight = FontWeight.Medium)
                                Text(
                                    assetKindLabel(w.kind) + " · " + Dates.full(Dates.fromEpochDay(w.date)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            AmountText(
                                w.amountCents, symbol,
                                color = if (w.isLiability) appColors().expense
                                else MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { bearbeiten = w; zeigtFormular = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = S("edit", "Bearbeiten")
                                )
                            }
                            IconButton(onClick = { loeschen = w }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = S("loeschen_4", "Löschen")
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = S("nw_transfer", "Transfer zwischen eigenen Konten"),
                    onClick = { zeigtTransfer = true },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (zeigtFormular) {
        AssetDialog(
            vorhanden = bearbeiten,
            symbol = symbol,
            onDismiss = { zeigtFormular = false; bearbeiten = null },
            onSave = { viewModel.saveAsset(it); zeigtFormular = false; bearbeiten = null }
        )
    }

    if (zeigtTransfer) {
        TransferDialog(
            werte = werte,
            symbol = symbol,
            onDismiss = { zeigtTransfer = false },
            onConfirm = { von, nach, cents ->
                viewModel.transferAssets(von, nach, cents)
                zeigtTransfer = false
            }
        )
    }

    ConfirmDialog(
        visible = loeschen != null,
        title = S("nw_delete_title", "Wert löschen?"),
        message = S("nw_delete_text", "%1\$s wird aus dem Nettovermögen entfernt.", loeschen?.name ?: ""),
        onConfirm = { loeschen?.let { viewModel.deleteAsset(it) }; loeschen = null },
        onDismiss = { loeschen = null }
    )
}

private fun assetKindLabel(kind: String): String = when (kind) {
    AssetKind.BANK.name -> str("nw_kind_bank", "Bankkonto")
    AssetKind.CASH.name -> str("nw_kind_cash", "Bargeld")
    AssetKind.SAVINGS.name -> str("nw_kind_savings", "Sparkonto")
    AssetKind.INVESTMENT.name -> str("nw_kind_investment", "Anlagen")
    AssetKind.OTHER_ASSET.name -> str("nw_kind_other_asset", "Sonstiger Wert")
    AssetKind.LOAN.name -> str("nw_kind_loan", "Kredit")
    AssetKind.CREDIT_CARD.name -> str("nw_kind_credit", "Kreditkarte")
    else -> str("nw_kind_other_debt", "Sonstige Schulden")
}

@Composable
private fun AssetDialog(
    vorhanden: AssetEntity?,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (AssetEntity) -> Unit
) {
    var name by remember { mutableStateOf(vorhanden?.name ?: "") }
    var betrag by remember {
        mutableStateOf(
            vorhanden?.let { Money.format(it.amountCents, "", withSymbol = false) } ?: ""
        )
    }
    var datum by remember { mutableStateOf(Dates.fromEpochDay(vorhanden?.date ?: Dates.today().toEpochDay())) }
    var notiz by remember { mutableStateOf(vorhanden?.note ?: "") }
    var art by remember { mutableStateOf(vorhanden?.kind ?: AssetKind.BANK.name) }
    var fehler by remember { mutableStateOf<String?>(null) }

    val arten = AssetKind.values().map { it.name }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    if (vorhanden == null) S("nw_add", "Wert hinzufügen")
                    else S("nw_edit", "Wert bearbeiten"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = S("bezeichnung", "Bezeichnung"),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AmountField(
                    value = betrag,
                    onValueChange = { betrag = it },
                    symbol = symbol,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = S("art", "Art"),
                    selected = art,
                    options = arten,
                    optionLabel = { assetKindLabel(it) },
                    onSelect = { art = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DateField(
                    epochDay = datum.toEpochDay(),
                    onDateChange = { datum = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = S("stand_vom", "Stand vom")
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = notiz,
                    onValueChange = { notiz = it },
                    label = S("notiz_optional_5", "Notiz (optional)"),
                    modifier = Modifier.fillMaxWidth()
                )
                if (fehler != null) {
                    Text(
                        fehler ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(S("abbrechen", "Abbrechen"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cents = Money.parseToCents(betrag)
                            when {
                                name.isBlank() -> fehler = str("val_name_missing", "Bitte gib eine Bezeichnung ein.")
                                cents == null || cents == 0L ->
                                    fehler = str("val_amount_invalid", "Dieser Betrag ist keine gültige Zahl.")
                                else -> onSave(
                                    (vorhanden ?: AssetEntity()).copy(
                                        name = name.trim(),
                                        amountCents = cents,
                                        kind = art,
                                        date = datum.toEpochDay(),
                                        note = notiz.trim()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(S("save", "Speichern"))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferDialog(
    werte: List<AssetEntity>,
    symbol: String,
    onDismiss: () -> Unit,
    onConfirm: (AssetEntity, AssetEntity, Long) -> Unit
) {
    if (werte.size < 2) return
    var von by remember { mutableStateOf(werte.first()) }
    var nach by remember { mutableStateOf(werte[1]) }
    var betrag by remember { mutableStateOf("") }
    var fehler by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                Text(
                    S("nw_transfer", "Transfer zwischen eigenen Konten"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                DropdownField(
                    label = S("von", "Von"),
                    selected = von,
                    options = werte,
                    optionLabel = { it.name },
                    onSelect = { von = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = S("nach", "Nach"),
                    selected = nach,
                    options = werte,
                    optionLabel = { it.name },
                    onSelect = { nach = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AmountField(
                    value = betrag,
                    onValueChange = { betrag = it },
                    symbol = symbol,
                    modifier = Modifier.fillMaxWidth()
                )
                if (fehler != null) {
                    Text(fehler ?: "", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(S("abbrechen", "Abbrechen"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cents = Money.parseToCents(betrag)
                            when {
                                cents == null || cents <= 0L ->
                                    fehler = str("val_amount_positive", "Der Betrag muss größer als 0 sein.")
                                von.id == nach.id ->
                                    fehler = str("nw_transfer_same", "Bitte wähle zwei unterschiedliche Konten.")
                                else -> onConfirm(von, nach, cents)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(S("save", "Speichern"))
                    }
                }
            }
        }
    }
}

// ===========================================================================
// Modul 26 - Jahresbericht
// ===========================================================================

@Composable
fun JahresberichtScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }
    val bericht by viewModel.jahresbericht.collectAsState()
    val jahr by viewModel.berichtJahr.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("yr_title", "Jahresbericht"),
            subtitle = S("yr_sub", "Einnahmen, Ausgaben, Sparquote und Entwicklung"),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.shiftBerichtJahr(-1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = S("zurueck", "Zurück"))
                    }
                    Text(
                        jahr.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { viewModel.shiftBerichtJahr(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = S("weiter", "Weiter"))
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(
                            S("einnahmen", "Einnahmen"),
                            Money.format(bericht.einnahmenCents, symbol),
                            valueColor = appColors().income
                        )
                        InfoRow(
                            S("ausgaben", "Ausgaben"),
                            Money.format(bericht.ausgabenCents, symbol),
                            valueColor = appColors().expense
                        )
                        InfoRow(
                            S("ersparnis", "Ersparnis"),
                            Money.format(bericht.ersparnisCents, symbol),
                            valueColor = if (bericht.ersparnisCents >= 0) appColors().income
                            else appColors().expense
                        )
                        InfoRow(
                            S("sparquote", "Sparquote"),
                            String.format(Locale.GERMANY, "%.1f %%", bericht.quote * 100f)
                        )
                        InfoRow(
                            S("yr_count", "Erfasste Buchungen"),
                            bericht.anzahlBuchungen.toString()
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val file = com.finovo.app.export.JahresberichtPdf.erzeugen(
                                context, bericht, symbol
                            )
                            file?.let { shareDatei(context, it, "application/pdf") }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(S("yr_pdf", "Als PDF exportieren"))
                    }
                    OutlinedButton(
                        onClick = {
                            val text = com.finovo.app.export.JahresberichtPdf.csv(bericht, symbol)
                            val file = java.io.File(context.cacheDir, "Finovo_Jahresbericht_$jahr.csv")
                            file.writeText("\uFEFF$text")
                            shareDatei(context, file, "text/csv")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(S("yr_csv", "Als CSV exportieren"))
                    }
                }
            }

            if (bericht.kategorien.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(S("yr_categories", "Ausgaben nach Kategorie"))
                }
                items(bericht.kategorien) { k ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(k.emoji, modifier = Modifier.padding(end = 8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(k.name)
                                Text(
                                    String.format(Locale.GERMANY, "%.1f %%", k.percent * 100f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            AmountText(k.cents, symbol)
                        }
                    }
                }
            }

            if (bericht.groessteAusgaben.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(S("yr_biggest", "Größte Ausgaben"))
                }
                items(bericht.groessteAusgaben) { e ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    e.merchant.ifBlank { e.note }.ifBlank { str("fallback_expense", "Ausgabe") },
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    Dates.full(Dates.fromEpochDay(e.date)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            AmountText(e.amountCents, symbol, color = appColors().expense)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SectionHeader(S("yr_fixed", "Wiederkehrende Kosten"))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        InfoRow(
                            S("yr_recurring", "Aus wiederkehrenden Regeln"),
                            Money.format(bericht.wiederkehrendCents, symbol)
                        )
                        InfoRow(
                            S("yr_subs", "Abos im Jahr"),
                            Money.format(bericht.abosCents, symbol)
                        )
                    }
                }
            }

            if (bericht.budgetMonate.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(S("yr_budget", "Budget-Einhaltung"))
                }
                items(bericht.budgetMonate) { b ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                b.monat.month.getDisplayName(TextStyle.SHORT, AppLocaleOrGerman()),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                Money.format(b.ausgabenCents, symbol) + " / " +
                                    Money.format(b.limitCents, symbol),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (b.eingehalten) "✓" else "!",
                                color = if (b.eingehalten) appColors().income else appColors().expense,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            if (bericht.ziele.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(S("yr_goals", "Sparziele"))
                }
                items(bericht.ziele) { z ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(z.emoji + " " + z.name, fontWeight = FontWeight.Medium)
                                Text(
                                    Money.format(z.gespartCents, symbol) + " / " +
                                        Money.format(z.zielCents, symbol)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            val anteil = if (z.zielCents > 0)
                                (z.gespartCents.toFloat() / z.zielCents).coerceIn(0f, 1f) else 0f
                            com.finovo.app.ui.components.ProgressBar(
                                progress = anteil,
                                color = appColors().income,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

internal fun shareDatei(context: android.content.Context, file: java.io.File, mime: String) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = mime
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, str("export_teilen", "Export teilen"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        android.widget.Toast.makeText(
            context,
            str("share_failed", "Teilen nicht möglich: %1\$s", it.message ?: ""),
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
}

package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.ui.Route
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SettingsRow
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Money
import com.finovo.app.util.S
import androidx.compose.ui.text.style.TextOverflow
import com.finovo.app.util.str
import com.finovo.app.util.plurals

@Composable
fun MoreScreen(viewModel: AppViewModel, onNavigate: (Route) -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val expenses by viewModel.expenses.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val rules by viewModel.recurringRules.collectAsState()
    val areas by viewModel.areas.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val goalSummary by viewModel.goalSummary.collectAsState()
    val subSummary by viewModel.subscriptionSummary.collectAsState()
    val carSummary by viewModel.carSummary.collectAsState()

    val expensesCount = expenses.size
    val billsCount = bills.size
    val rulesCount = rules.size
    val areaCount = areas.size
    val receiptCount = remember(expenses) { expenses.count { !it.receiptUri.isNullOrBlank() } }
    val carAvailable = carSummary.available

    var showDeleteAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            tutorialId = "more",
            title = S("more_title", "Mehr"),
            subtitle = S("more_sub", "Einstellungen, Daten & Module")
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp, top = 4.dp)
        ) {
            // ------------------------- Schnellzugriff -------------------------
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickTile(
                        emoji = "\uD83D\uDCCA",
                        label = S("analytics", "Finanzanalyse"),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Route.Analytics) }
                    )
                    QuickTile(
                        emoji = "\uD83D\uDCB0",
                        label = S("budget", "Budget"),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Route.Budget) }
                    )
                    QuickTile(
                        emoji = "\uD83D\uDD01",
                        label = S("recurring", "Wiederkehrend"),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Route.Recurring) }
                    )
                    QuickTile(
                        emoji = "\uD83D\uDCC2",
                        label = S("categories", "Kategorien"),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Route.Categories) }
                    )
                }
            }

            // ----------------------------- Module -----------------------------
            item {
                GroupTitle(S("modules", "Module"))
                Group {
                    SettingsRow(
                        title = S("ai_entry_title", "🤖 Finovo AI"),
                        subtitle = S("ai_entry_subtitle", "Fragen zu deinen Finanzen – aus echten Daten"),
                        icon = Icons.Filled.SmartToy,
                        onClick = { onNavigate(Route.FinovoAi) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("search", "Suche"),
                        subtitle = S("haendler_betrag_kategorie_notiz", "Händler, Betrag, Kategorie, Notiz"),
                        icon = Icons.Default.Search,
                        onClick = { onNavigate(Route.Search) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("goals", "Sparziele"),
                        subtitle = if (goalSummary.count > 0) {
                            plurals("pl_goals_count", goalSummary.count, "%1\$d Ziel · %2\$s gespart", "%1\$d Ziele · %2\$s gespart", Money.format(goalSummary.savedCents, symbol))
                        } else str("more_no_goals", "Noch keine Ziele"),
                        icon = Icons.Default.Savings,
                        onClick = { onNavigate(Route.Goals) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("subscriptions", "Abos"),
                        subtitle = if (subSummary.count > 0) {
                            S("abos_monat", "%1\$s Abos · %2\$s / Monat", subSummary.count, Money.format(subSummary.monthlyCents, symbol))
                        } else str("more_subs_hint", "Abo-Kosten im Blick behalten"),
                        icon = Icons.Default.Repeat,
                        onClick = { onNavigate(Route.Subscriptions) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("areas", "Bereiche"),
                        subtitle = if (areaCount > 0) plurals("pl_areas", areaCount, "%1\$d Bereich", "%1\$d Bereiche") else str("more_areas_hint", "Persönlich, Familie, Auto …"),
                        icon = Icons.Default.Category,
                        onClick = { onNavigate(Route.Areas) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("calendar", "Kalender"),
                        subtitle = S("erwartete_einnahmen_ausgaben", "Erwartete Einnahmen & Ausgaben"),
                        icon = Icons.Default.CalendarMonth,
                        onClick = { onNavigate(Route.Calendar) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("insights", "Analyse"),
                        subtitle = S("auffaelligkeiten_muster", "Auffälligkeiten & Muster"),
                        icon = Icons.Default.Lightbulb,
                        onClick = { onNavigate(Route.Insights) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("whatif", "Was-wäre-wenn"),
                        subtitle = S("sparplan_veraenderungen_rechnen", "Sparplan & Veränderungen rechnen"),
                        icon = Icons.Default.Calculate,
                        onClick = { onNavigate(Route.WhatIf) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("receipts", "Belege"),
                        subtitle = if (receiptCount > 0) plurals("pl_receipts", receiptCount, "%1\$d Beleg", "%1\$d Belege") else str("more_receipts_hint", "Fotos zu Buchungen"),
                        icon = Icons.Default.ReceiptLong,
                        onClick = { onNavigate(Route.Receipts) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("car", "Auto"),
                        subtitle = if (carAvailable) {
                            S("diesen_monat", "%1\$s diesen Monat", Money.format(carSummary.monthlyCents, symbol))
                        } else str("more_car_hint", "Sprit, Versicherung, TÜV …"),
                        emoji = "\uD83D\uDE97",
                        onClick = { onNavigate(Route.Car) }
                    )
                }
            }

            // --------------------- Neue Finanz-Module --------------------------
            item {
                Group {
                    SettingsRow(
                        title = S("forecast_title", "Prognose"),
                        subtitle = S("forecast_sub", "Erwartetes Monatsende aus echten Daten"),
                        emoji = "\uD83D\uDCC5",
                        onClick = { onNavigate(Route.Forecast) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("cf_title", "Cashflow"),
                        subtitle = S("cf_sub", "Startbestand, Zu- und Abflüsse, Endbestand"),
                        emoji = "\uD83D\uDCA7",
                        onClick = { onNavigate(Route.Cashflow) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("nw_title", "Nettovermögen"),
                        subtitle = S("nw_sub", "Vermögen minus Schulden"),
                        emoji = "\uD83C\uDFE6",
                        onClick = { onNavigate(Route.NetWorth) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("yr_title", "Jahresbericht"),
                        subtitle = S("yr_sub", "Einnahmen, Ausgaben, Sparquote und Entwicklung"),
                        emoji = "\uD83D\uDCD5",
                        onClick = { onNavigate(Route.YearReport) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("doc_title", "Dokumente"),
                        subtitle = S("doc_sub", "Belege, Rechnungen, Verträge & mehr"),
                        emoji = "\uD83D\uDCC2",
                        onClick = { onNavigate(Route.Documents) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("import_title", "Daten importieren"),
                        subtitle = S("import_subtitle", "CSV, Excel-Export oder Bankdatei"),
                        emoji = "\uD83D\uDCE5",
                        onClick = { onNavigate(Route.Import) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("audit_title", "Änderungsverlauf"),
                        subtitle = S("audit_sub", "Was wann gespeichert oder geändert wurde"),
                        emoji = "\uD83D\uDD53",
                        onClick = { onNavigate(Route.Audit) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("fam_title", "Haushalt"),
                        subtitle = S("fam_sub", "Gemeinsame und eigene Ausgaben"),
                        emoji = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
                        onClick = { onNavigate(Route.Household) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("alarms_title", "Finanz-Alarme"),
                        subtitle = S("alarms_sub", "Jeden Alarm einzeln ein- oder ausschalten"),
                        icon = Icons.Default.Notifications,
                        onClick = { onNavigate(Route.Alarms) }
                    )
                }
            }

            // ------------------------------ Bank -------------------------------
            item {
                GroupTitle(S("bank_group", "Bank"))
                Group {
                    SettingsRow(
                        title = S("bank_connect", "Bankkonto verbinden"),
                        subtitle = S("bank_sub", "Coming soon · automatische Finanzübersicht"),
                        emoji = "\uD83C\uDFE6",
                        onClick = { onNavigate(Route.Bank) }
                    )
                }
            }

            // -------------------------- Einstellungen --------------------------
            item {
                GroupTitle(S("settings", "Einstellungen"))
                Group {
                    SwitchRow(
                        title = S("dunkler_modus", "Dunkler Modus"),
                        subtitle = if (settings.themeMode == "DARK") str("more_theme_active", "Aktiv") else str("more_theme_off", "Aus (System folgen: %1\$s)", settings.themeMode.lowercase()),
                        checked = settings.themeMode == "DARK",
                        onCheckedChange = {
                            viewModel.setThemeMode(
                                if (it) com.finovo.app.data.ThemeMode.DARK
                                else com.finovo.app.data.ThemeMode.LIGHT
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("erscheinungsbild", "Erscheinungsbild"),
                        subtitle = when (settings.themeMode) {
                            "LIGHT" -> "Hell"
                            "DARK" -> "Dunkel"
                            else -> "System"
                        },
                        icon = Icons.Default.DarkMode,
                        onClick = { onNavigate(Route.Settings) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("waehrung", "Währung"),
                        subtitle = S("settings_currency_settings_currencysym", "%1\$s (%2\$s)", settings.currency, settings.currencySymbol),
                        emoji = "\uD83D\uDCB1",
                        onClick = { onNavigate(Route.Settings) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("benachrichtigungen", "Benachrichtigungen"),
                        subtitle = if (settings.notificationsEnabled) str("more_theme_active", "Aktiv") else str("timeout_off", "Aus"),
                        icon = Icons.Default.Notifications,
                        onClick = { onNavigate(Route.Settings) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("app_sperre", "App-Sperre"),
                        subtitle = if (settings.appLockEnabled) str("more_pin_active", "PIN aktiv") else str("timeout_off", "Aus"),
                        icon = Icons.Default.Lock,
                        onClick = { onNavigate(Route.Settings) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("monatsbudget", "Monatsbudget"),
                        subtitle = if (budget.enabled && budget.monthlyLimitCents > 0) {
                            Money.format(budget.monthlyLimitCents, symbol)
                        } else S("nicht_eingerichtet", "Nicht eingerichtet"),
                        icon = Icons.Default.Savings,
                        onClick = { onNavigate(Route.Budget) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("widgets_title", "Übersicht anpassen"),
                        subtitle = S("widgets_sub", "Reihenfolge ändern und Widgets ausblenden"),
                        emoji = "\uD83C\uDF9B",
                        onClick = { onNavigate(Route.WidgetsEdit) }
                    )
                }
            }

            // ------------------------------ Daten ------------------------------
            item {
                GroupTitle(S("data", "Daten"))
                Group {
                    SettingsRow(
                        title = S("recurring_payments", "Wiederkehrende Zahlungen"),
                        subtitle = S("recurring_sub", "Abo, Miete, Versicherungen"),
                        icon = Icons.Default.Repeat,
                        onClick = { onNavigate(Route.Recurring) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("backup_restore", "Backup & Wiederherstellen"),
                        subtitle = S("backup_restore_sub", "Daten sichern und laden"),
                        icon = Icons.Default.Backup,
                        onClick = { onNavigate(Route.Data) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("export_data", "Daten exportieren"),
                        subtitle = S("export_sub", "Als CSV oder PDF"),
                        icon = Icons.Default.Share,
                        onClick = { onNavigate(Route.Data) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("import_data", "Daten importieren"),
                        subtitle = S("import_sub", "Aus einer Datei wiederherstellen"),
                        icon = Icons.Default.Restore,
                        onClick = { onNavigate(Route.Data) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("delete_all", "Alle Daten löschen"),
                        subtitle = S("delete_all_sub", "Achtung: Aktion kann nicht rückgängig gemacht werden"),
                        icon = Icons.Default.DeleteForever,
                        onClick = { showDeleteAll = true }
                    )
                }
            }

            // ------------------------------- Über ------------------------------
            item {
                GroupTitle(S("about", "Über"))
                Group {
                    SettingsRow(
                        title = S("tut_overview", "Anleitungen"),
                        subtitle = S("tut_more_entry_sub", "Interaktive Anleitung für jedes Modul"),
                        icon = Icons.Default.School,
                        onClick = { onNavigate(Route.Tutorials) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("about_finovo", "Über Finovo"),
                        subtitle = S("version", "version 1.0"),
                        icon = Icons.Default.Info,
                        onClick = { onNavigate(Route.About) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                Group {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(appColors().income.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = appColors().income,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            S("offline_footer", "Finovo funktioniert vollständig offline. Alle Daten bleiben auf diesem Gerät."),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }
        }
    }

    ConfirmDialog(
        visible = showDeleteAll,
        title = S("alle_daten_loeschen", "Alle Daten löschen?"),
        message = S("alle_ausgaben_einnahmen_rechnungen", "Alle Ausgaben, Einnahmen, Rechnungen, Kategorien, Budgets und Einstellungen werden unwiderruflich gelöscht. Erstelle vorher ein Backup, wenn du die Daten behalten möchtest."),
        confirmLabel = S("endgueltig_loeschen", "Endgültig löschen"),
        onConfirm = {
            showDeleteAll = false
            viewModel.deleteAllData { }
        },
        onDismiss = { showDeleteAll = false }
    )
}

// ---------------------------------------------------------------------------
// Bausteine (werden auch von About, Data, Settings, Recurring genutzt)
// ---------------------------------------------------------------------------

@Composable
internal fun GroupTitle(text: String, modifier: Modifier = Modifier, tutorialId: String? = null) {
    Row(
        modifier = modifier.padding(start = 22.dp, end = if (tutorialId != null) 8.dp else 22.dp, top = if (tutorialId != null) 8.dp else 18.dp, bottom = if (tutorialId != null) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (tutorialId != null) com.finovo.app.tutorial.TutorialHelpButton(tutorialId)
    }
}

@Composable
internal fun Group(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(modifier = modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Column(content = content)
    }
}

@Composable
internal fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(1.dp)
            .background(appColors().divider.copy(alpha = if (appColors().isDark) 0.7f else 1f))
    )
}

@Composable
private fun QuickTile(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(appColors().cardTop, appColors().card))
            )
            .border(1.dp, appColors().cardBorder.copy(alpha = if (appColors().isDark) 0.6f else 1f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(appColors().glow.copy(alpha = if (appColors().isDark) 0.22f else 0.10f))
                .border(1.dp, appColors().glow.copy(alpha = 0.4f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

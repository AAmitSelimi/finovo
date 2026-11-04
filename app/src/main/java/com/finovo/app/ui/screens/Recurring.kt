package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.data.BillEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.CategoryBadge
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.IconActionButton
import com.finovo.app.ui.components.Pill
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.Recurrence
import com.finovo.app.util.RecurrenceSpec
import java.time.LocalDate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.S
import com.finovo.app.util.str
import com.finovo.app.util.categoryLabel

@Composable
fun RecurringScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val rules by viewModel.recurringRules.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val today = LocalDate.now()

    val recurringBills = remember(bills) { bills.filter { it.isRecurring } }
    val vorschlaege by viewModel.wiederkehrendVorschlaege.collectAsState()
    var pendingRuleDelete by remember { mutableStateOf<RecurringRuleEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            tutorialId = "recurring",
            title = S("wiederkehrende_zahlungen_2", "Wiederkehrende Zahlungen"),
            subtitle = S("automatisch_berechnet_doppelte_buchung", "Automatisch berechnet, doppelte Buchungen ausgeschlossen"),
            onBack = onBack
        )

        if (rules.isEmpty() && recurringBills.isEmpty() && vorschlaege.isEmpty()) {
            EmptyState(
                emoji = "🔁",
                title = S("noch_keine_wiederkehrenden_zahlungen", "Noch keine wiederkehrenden Zahlungen"),
                message = S("lege_z_b_deine_miete_an_900_jeden_", "Lege z. B. deine Miete an: 900 €, jeden Monat am 1."),
                actionLabel = S("wiederkehrende_zahlung_2", "+ Wiederkehrende Zahlung"),
                onAction = { viewModel.openRecurringSheet(null) }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (rules.isNotEmpty()) {
                    item { GroupTitle(S("regeln_ausgaben_einnahmen", "Regeln (Ausgaben & Einnahmen)")) }
                    item {
                        Group {
                            rules.forEach { rule ->
                                RuleRow(
                                    rule = rule,
                                    category = categories.firstOrNull { it.id == rule.categoryId },
                                    symbol = symbol,
                                    today = today,
                                    onEdit = { viewModel.openRecurringSheet(rule) },
                                    onToggle = { viewModel.setRuleActive(rule.id, it) },
                                    onDelete = { pendingRuleDelete = rule }
                                )
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(appColors().divider)
                                )
                            }
                        }
                    }
                }

                if (recurringBills.isNotEmpty()) {
                    item { GroupTitle(S("wiederkehrende_rechnungen", "Wiederkehrende Rechnungen")) }
                    item {
                        Group {
                            recurringBills.forEach { bill ->
                                RecurringBillRow(
                                    bill = bill,
                                    category = categories.firstOrNull { it.id == bill.categoryId },
                                    symbol = symbol,
                                    today = today,
                                    onEdit = { viewModel.openBillSheet(bill) },
                                    onStop = {
                                        viewModel.saveBill(bill.copy(isRecurring = false), false)
                                    }
                                )
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(appColors().divider)
                                )
                            }
                        }
                    }
                }

                if (vorschlaege.isNotEmpty()) {
                    item { GroupTitle(S("rec_suggestions", "Automatisch erkannt")) }
                    item {
                        Group {
                            vorschlaege.take(8).forEach { v ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                v.titel,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                S(
                                                    "rec_suggestion_sub",
                                                    "%1\$s · %2\$s erkannt · nächster Termin: %3\$s",
                                                    com.finovo.app.util.WiederkehrendErkennung.intervallText(v.intervallTage),
                                                    v.anzahl.toString(),
                                                    Dates.short(v.naechste)
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = appColors().textMuted
                                            )
                                        }
                                        Text(
                                            Money.format(v.betragCents, symbol),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (v.istEinnahme) appColors().income else appColors().expense
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.vorschlagUebernehmen(v) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(S("rec_accept", "Als Regel übernehmen"))
                                    }
                                }
                                Spacer(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(appColors().divider)
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = S("wiederkehrende_zahlung", "+ Wiederkehrende Zahlung"),
                        onClick = { viewModel.openRecurringSheet(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        S("faellige_termine_werden_automatisch_er", "Fällige Termine werden automatisch erzeugt. Bereits erzeugte Buchungen werden nie doppelt angelegt."),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted,
                        modifier = Modifier.padding(horizontal = 28.dp)
                    )
                }
            }
        }
    }

    pendingRuleDelete?.let { rule ->
        DeleteRuleDialog(
            rule = rule,
            symbol = symbol,
            onDismiss = { pendingRuleDelete = null },
            onConfirm = { deleteEntries ->
                viewModel.deleteRule(rule.id, deleteEntries)
                pendingRuleDelete = null
            }
        )
    }
}

@Composable
private fun DeleteRuleDialog(
    rule: RecurringRuleEntity,
    symbol: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var deleteEntries by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S("wiederkehrende_zahlung_loeschen", "Wiederkehrende Zahlung löschen?")) },
        text = {
            Column {
                Text(
                    S("moechtest_du_nur_die_regel_loeschen_od", "Möchtest du nur die Regel löschen oder auch alle bereits erzeugten Buchungen?"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = S("erzeugte_buchungen_mitloeschen", "Erzeugte Buchungen mitlöschen"),
                    subtitle = S("alle_ausgaben_einnahmen_dieser_regel_e", "Alle Ausgaben/Einnahmen dieser Regel entfernen"),
                    checked = deleteEntries,
                    onCheckedChange = { deleteEntries = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteEntries) }) {
                Text(if (deleteEntries) S("alles_loeschen", "Alles löschen") else S("nur_regel_loeschen", "Nur Regel löschen"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S("abbrechen_6", "Abbrechen")) } }
    )
}

@Composable
private fun RuleRow(
    rule: RecurringRuleEntity,
    category: CategoryEntity?,
    symbol: String,
    today: LocalDate,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val spec = RecurrenceSpec.from(rule)
    val next = Recurrence.nextOnOrAfter(spec, today)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(category?.emoji ?: "📦")
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name.ifBlank { categoryLabel(category?.name ?: "Zahlung") },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                Recurrence.describe(spec) + if (next != null) str("recurring_next", " · Nächster: %1\$s", Dates.full(next)) else "",
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                maxLines = 2
            )
            if (!rule.active) {
                Spacer(Modifier.height(4.dp))
                Pill("Gestoppt", appColors().textMuted, appColors().cardAlt)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                cents = if (rule.kind == RecurringKind.INCOME.name) rule.amountCents else -rule.amountCents,
                symbol = symbol,
                style = MaterialTheme.typography.titleSmall,
                color = if (rule.kind == RecurringKind.INCOME.name) appColors().income else appColors().expense
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rule.active,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                IconActionButton(Icons.Default.Edit, str("bearbeiten", "Bearbeiten"), onEdit, tint = appColors().textMuted)
                IconActionButton(Icons.Default.Delete, S("loeschen_12", "Löschen"), onDelete, tint = appColors().textMuted)
            }
        }
    }
}

@Composable
private fun RecurringBillRow(
    bill: BillEntity,
    category: CategoryEntity?,
    symbol: String,
    today: LocalDate,
    onEdit: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(category?.emoji ?: "🧾")
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                bill.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                S("faellig_am_dates_full_dates_fromepochd", "Fällig am %1\$s · ", Dates.full(Dates.fromEpochDay(bill.dueDate))) +
                    Recurrence.frequencyLabel(
                        runCatching {
                            com.finovo.app.data.Frequency.valueOf(bill.frequency)
                        }.getOrDefault(com.finovo.app.data.Frequency.MONTHLY)
                    ) +
                    if (bill.reminderEnabled) plurals("pl_reminder_days", bill.reminderDaysBefore, " · Erinnerung %1\$d Tag vorher", " · Erinnerung %1\$d Tage vorher") else "",
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                maxLines = 2
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                cents = bill.amountCents,
                symbol = symbol,
                style = MaterialTheme.typography.titleSmall
            )
            Row {
                IconActionButton(Icons.Default.Edit, str("bearbeiten", "Bearbeiten"), onEdit, tint = appColors().textMuted)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(appColors().cardAlt)
                        .clickable(onClick = onStop)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        S("stoppen", "Stoppen"),
                        style = MaterialTheme.typography.labelMedium,
                        color = appColors().expense,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

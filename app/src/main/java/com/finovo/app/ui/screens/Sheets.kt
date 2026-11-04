package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.BillEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.data.Frequency
import com.finovo.app.data.IncomeEntity
import com.finovo.app.data.RecurringKind
import com.finovo.app.data.RecurringRuleEntity
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.CategoryPicker
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.DropdownField
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.components.TimeField
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import java.time.LocalDate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.S
import com.finovo.app.util.str
import androidx.compose.foundation.horizontalScroll

// ---------------------------------------------------------------------------
// Quick add
// ---------------------------------------------------------------------------

@Composable
fun QuickAddSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    BottomSheetContainer(onDismiss = onDismiss, title = S("was_moechtest_du_hinzufuegen", "Was möchtest du hinzufügen?")) {
        QuickAddOption(
            emoji = "💸",
            title = S("ausgabe_2", "Ausgabe"),
            subtitle = S("miete_lebensmittel_auto", "Miete, Lebensmittel, Auto …"),
            color = appColors().expense,
            onClick = { viewModel.openExpenseSheet(null) }
        )
        Spacer(Modifier.height(12.dp))
        QuickAddOption(
            emoji = "💰",
            title = S("einnahme_2", "Einnahme"),
            subtitle = S("gehalt_kindergeld_sonstige", "Gehalt, Kindergeld, sonstige"),
            color = appColors().income,
            onClick = { viewModel.openIncomeSheet(null) }
        )
        Spacer(Modifier.height(12.dp))
        QuickAddOption(
            emoji = "📅",
            title = S("rechnung", "Rechnung"),
            subtitle = S("mit_faelligkeitsdatum_erinnerung", "Mit Fälligkeitsdatum & Erinnerung"),
            color = MaterialTheme.colorScheme.primary,
            onClick = { viewModel.openBillSheet(null) }
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun QuickAddOption(
    emoji: String,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(appColors().cardAlt)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Expense
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSheet(viewModel: AppViewModel, existing: ExpenseEntity?, onDismiss: () -> Unit) {
    val settings = viewModel.settings.collectAsState().value
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val categories = remember(viewModel.categories.collectAsState().value) {
        viewModel.categories.value.filter { it.type == CategoryType.EXPENSE.name }
    }
    val isNew = existing == null

    var amount by remember {
        mutableStateOf(
            existing?.let { Money.format(it.amountCents, "", withSymbol = false) } ?: ""
        )
    }
    var categoryId by remember {
        mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now().toEpochDay()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var paid by remember { mutableStateOf(existing?.paid ?: true) }
    var fixedOverride by remember { mutableStateOf(existing?.fixedOverride) }
    var recurring by remember { mutableStateOf(existing?.recurringId != null) }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var interval by remember { mutableStateOf(1) }
    var customDays by remember { mutableStateOf(30) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    // Modul 20: Transaktionsdetails
    var merchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var subcategoryId by remember { mutableStateOf(existing?.subcategoryId ?: 0L) }
    var paymentMethod by remember { mutableStateOf(existing?.paymentMethod ?: "") }
    var tags by remember { mutableStateOf(existing?.tags ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    // Modul 12: Kategorie-Vorschlag, Modul 29: Duplikat-Warnung
    var vorschlag by remember { mutableStateOf<com.finovo.app.util.SmartCategories.Vorschlag?>(null) }
    var duplikatWarnung by remember { mutableStateOf<String?>(null) }

    val alleAusgaben = viewModel.expenses.collectAsState().value
    val regeln = viewModel.kategorieRegeln.collectAsState().value
    LaunchedEffect(merchant, note, categoryId, regeln, isNew) {
        if (!isNew) return@LaunchedEffect
        val text = merchant.ifBlank { note }
        vorschlag = if (text.isNotBlank()) {
            com.finovo.app.util.SmartCategories.vorschlag(text, categories, regeln, false)
        } else null
    }
    LaunchedEffect(merchant, note, amount, date, isNew) {
        duplikatWarnung = null
        if (!viewModel.duplikatWarnungen) return@LaunchedEffect
        val cents = com.finovo.app.util.Money.parseToCents(amount) ?: return@LaunchedEffect
        val kandidat = com.finovo.app.data.ExpenseEntity(
            id = existing?.id ?: 0L,
            amountCents = cents ?: 0L,
            date = date,
            merchant = merchant.trim(),
            note = note.trim()
        )
        val verdacht = com.finovo.app.util.Duplicates.findeZuAusgabe(kandidat, alleAusgaben)
        if (verdacht.isNotEmpty()) {
            val erste = verdacht.first().vorhanden
            duplikatWarnung = str(
                "dup_warning",
                "Mögliche doppelte Buchung: %1\$s am %2\$s über %3\$s ist bereits erfasst.",
                erste.merchant.ifBlank { erste.note }.ifBlank { "–" },
                com.finovo.app.util.Dates.full(com.finovo.app.util.Dates.fromEpochDay(erste.date)),
                com.finovo.app.util.Money.format(erste.amountCents, symbol)
            )
        }
    }

    BottomSheetContainer(
        onDismiss = onDismiss,
        title = if (isNew) S("ausgabe_hinzufuegen", "Ausgabe hinzufügen") else S("ausgabe_bearbeiten", "Ausgabe bearbeiten")
    ) {
        AmountField(value = amount, onValueChange = { amount = it; error = null }, symbol = symbol, error = error)

        Spacer(Modifier.height(16.dp))
        Text(S("kategorie_4", "Kategorie"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(8.dp))
        CategoryPicker(
            categories = categories,
            selectedId = categoryId,
            onSelect = { categoryId = it.id },
            onAddCategory = null
        )

        Spacer(Modifier.height(16.dp))
        DateField(epochDay = date, onDateChange = { date = it.toEpochDay() })

        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = S("haendler_empfaenger", "Händler / Empfänger (optional)")
        )

        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = note,
            onValueChange = { note = it },
            label = S("notiz_optional_3", "Notiz (optional)")
        )

        // Modul 12: Vorschlag der KI - nie automatisch übernehmen
        vorschlag?.let { v ->
            if (v.categoryId != categoryId) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors().cardAlt)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "\uD83E\uDD16 " + str(
                                "ai_suggest_category",
                                "Vorschlag: %1\$s",
                                categories.firstOrNull { it.id == v.categoryId }?.name ?: ""
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { categoryId = v.categoryId; vorschlag = null }) {
                            Text(S("uebernehmen", "Übernehmen"))
                        }
                    }
                }
            }
        }

        // Modul 29: Duplikat-Warnung - die Entscheidung bleibt beim Nutzer
        duplikatWarnung?.let { warnung ->
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = appColors().warningContainer)
            ) {
                Text(
                    "⚠️ $warnung",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Modul 12/20: Unterkategorie
        val unterkategorien = categories.filter { it.parentId == categoryId }
        if (unterkategorien.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                S("unterkategorie", "Unterkategorie (optional)"),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
            Spacer(Modifier.height(6.dp))
            CategoryPicker(
                categories = unterkategorien,
                selectedId = subcategoryId,
                onSelect = { subcategoryId = it.id },
                onAddCategory = null
            )
        } else {
            subcategoryId = 0L
        }

        // Modul 20: Zahlungsart
        Spacer(Modifier.height(12.dp))
        Text(
            S("zahlungsart", "Zahlungsart"),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "" to S("pm_none", "Keine Angabe"),
                "CARD" to S("pm_card", "Karte"),
                "CASH" to S("pm_cash", "Bar"),
                "TRANSFER" to S("pm_transfer", "Überweisung"),
                "DIRECT_DEBIT" to S("pm_direct_debit", "Lastschrift")
            ).forEach { (wert, label) ->
                FilterChip(
                    selected = paymentMethod == wert,
                    onClick = { paymentMethod = wert },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = tags,
            onValueChange = { tags = it },
            label = S("schlagworte_optional", "Schlagworte (optional, mit Komma)")
        )

        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = location,
            onValueChange = { location = it },
            label = S("ort_optional", "Ort (optional)")
        )

        Spacer(Modifier.height(12.dp))
        Text(
            S("kostenart_2", "Kostenart"),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted
        )
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                null to S("wie_kategorie", "Wie Kategorie"),
                true to str("fixed_choice", "🔒 Fixkosten"),
                false to str("variable_choice", "🔄 Variabel")
            ).forEach { (value, label) ->
                FilterChip(
                    selected = fixedOverride == value,
                    onClick = { fixedOverride = value },
                    label = { Text(label, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = S("bezahlt", "Bezahlt"),
            subtitle = S("nicht_bezahlte_ausgaben_werden_als_off", "Nicht bezahlte Ausgaben werden als offen markiert"),
            checked = paid,
            onCheckedChange = { paid = it }
        )

        if (isNew) {
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                title = S("wiederkehrend", "Wiederkehrend"),
                subtitle = S("wird_automatisch_wiederholt", "Wird automatisch wiederholt"),
                checked = recurring,
                onCheckedChange = { recurring = it }
            )
            if (recurring) {
                RecurrenceOptions(
                    frequency = frequency,
                    onFrequencyChange = { frequency = it },
                    interval = interval,
                    onIntervalChange = { interval = it },
                    customDays = customDays,
                    onCustomDaysChange = { customDays = it },
                    startDate = Dates.fromEpochDay(date)
                )
            }
        } else if (existing?.recurringId != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                S("diese_ausgabe_gehoert_zu_einer_wiederk", "Diese Ausgabe gehört zu einer wiederkehrenden Zahlung. Änderungen gelten nur für diesen Eintrag."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = S("speichern_5", "Speichern"),
            onClick = {
                val cents = Money.parseToCents(amount)
                val betragPruefung = com.finovo.app.util.Validation.betrag(amount)
                when {
                    betragPruefung is com.finovo.app.util.Validation.Ergebnis.Fehler ->
                        error = betragPruefung.text

                    categories.none { it.id == categoryId } ->
                        error = str("bitte_waehle_eine_kategorie", "Bitte wähle eine Kategorie.")

                    else -> {
                        if (isNew && recurring) {
                            viewModel.saveExpenseWithRule(
                                ExpenseEntity(
                                    amountCents = cents ?: 0L,
                                    categoryId = categoryId,
                                    date = date,
                                    note = note.trim(),
                                    paid = paid,
                                    fixedOverride = fixedOverride,
                                    merchant = merchant.trim(),
                                    subcategoryId = subcategoryId ?: 0L,
                                    paymentMethod = paymentMethod,
                                    tags = tags.trim(),
                                    location = location.trim()
                                ),
                                frequency = frequency,
                                interval = interval,
                                customDays = customDays
                            )
                        } else if (isNew) {
                            viewModel.saveExpense(
                                ExpenseEntity(
                                    amountCents = cents ?: 0L,
                                    categoryId = categoryId,
                                    date = date,
                                    note = note.trim(),
                                    paid = paid,
                                    fixedOverride = fixedOverride,
                                    merchant = merchant.trim(),
                                    subcategoryId = subcategoryId ?: 0L,
                                    paymentMethod = paymentMethod,
                                    tags = tags.trim(),
                                    location = location.trim()
                                ),
                                true
                            )
                        } else {
                            viewModel.saveExpense(
                                existing!!.copy(
                                    amountCents = cents ?: 0L,
                                    categoryId = categoryId,
                                    date = date,
                                    note = note.trim(),
                                    paid = paid,
                                    fixedOverride = fixedOverride,
                                    merchant = merchant.trim(),
                                    subcategoryId = subcategoryId ?: 0L,
                                    paymentMethod = paymentMethod,
                                    tags = tags.trim(),
                                    location = location.trim()
                                ),
                                false
                            )
                        }
                        onDismiss()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = S("abbrechen_12", "Abbrechen"), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        if (!isNew) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = S("loeschen_6", "Löschen"),
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    ConfirmDialog(
        visible = showDelete,
        title = S("ausgabe_loeschen_2", "Ausgabe löschen?"),
        message = S("dieser_eintrag_wird_unwiderruflich", "Dieser Eintrag wird unwiderruflich gelöscht."),
        confirmLabel = S("loeschen_13", "Löschen"),
        onConfirm = {
            existing?.let { viewModel.deleteExpense(it) }
            showDelete = false
            onDismiss()
        },
        onDismiss = { showDelete = false }
    )
}

// ---------------------------------------------------------------------------
// Income
// ---------------------------------------------------------------------------

@Composable
fun IncomeSheet(viewModel: AppViewModel, existing: IncomeEntity?, onDismiss: () -> Unit) {
    val settings = viewModel.settings.collectAsState().value
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val categories = remember(viewModel.categories.collectAsState().value) {
        viewModel.categories.value.filter { it.type == CategoryType.INCOME.name }
    }
    val isNew = existing == null

    var amount by remember {
        mutableStateOf(existing?.let { Money.format(it.amountCents, "", withSymbol = false) } ?: "")
    }
    var categoryId by remember {
        mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now().toEpochDay()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var recurring by remember { mutableStateOf(existing?.recurringId != null) }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var interval by remember { mutableStateOf(1) }
    var customDays by remember { mutableStateOf(30) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    // Modul 20: Transaktionsdetails
    var merchant by remember { mutableStateOf(existing?.merchant ?: "") }
    var paymentMethod by remember { mutableStateOf(existing?.paymentMethod ?: "") }
    var tags by remember { mutableStateOf(existing?.tags ?: "") }

    BottomSheetContainer(
        onDismiss = onDismiss,
        title = if (isNew) S("einnahme_hinzufuegen", "Einnahme hinzufügen") else S("einnahme_bearbeiten", "Einnahme bearbeiten")
    ) {
        AmountField(value = amount, onValueChange = { amount = it; error = null }, symbol = symbol, error = error)

        Spacer(Modifier.height(16.dp))
        Text(S("quelle", "Quelle"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(8.dp))
        CategoryPicker(
            categories = categories,
            selectedId = categoryId,
            onSelect = { categoryId = it.id },
            columns = 3
        )

        Spacer(Modifier.height(16.dp))
        DateField(epochDay = date, onDateChange = { date = it.toEpochDay() })

        Spacer(Modifier.height(12.dp))
        AppTextField(value = note, onValueChange = { note = it }, label = S("notiz_optional_4", "Notiz (optional)"))

        if (isNew) {
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = S("wiederkehrend_2", "Wiederkehrend"),
                subtitle = S("z_b_jeden_monat_gehalt", "Z. B. jeden Monat Gehalt"),
                checked = recurring,
                onCheckedChange = { recurring = it }
            )
            if (recurring) {
                RecurrenceOptions(
                    frequency = frequency,
                    onFrequencyChange = { frequency = it },
                    interval = interval,
                    onIntervalChange = { interval = it },
                    customDays = customDays,
                    onCustomDaysChange = { customDays = it },
                    startDate = Dates.fromEpochDay(date)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = S("speichern_6", "Speichern"),
            onClick = {
                val cents = Money.parseToCents(amount)
                when {
                    amount.isBlank() -> error = str("bitte_gib_einen_betrag_ein_3", "Bitte gib einen Betrag ein.")
                    cents == null -> error = str("bitte_gib_einen_gueltigen_betr_3", "Bitte gib einen gültigen Betrag ein (z. B. 12,50).")
                    cents <= 0 -> error = str("der_betrag_muss_groesser_als_0_2", "Der Betrag muss größer als 0 sein.")
                    categoryId == 0L -> error = str("bitte_waehle_eine_quelle", "Bitte wähle eine Quelle.")
                    else -> {
                        if (isNew && recurring) {
                            viewModel.saveIncomeWithRule(
                                IncomeEntity(
                                    amountCents = cents ?: 0L,
                                    categoryId = categoryId,
                                    date = date,
                                    note = note.trim(),
                                    merchant = merchant.trim(),
                                    paymentMethod = paymentMethod,
                                    tags = tags.trim()
                                ),
                                frequency = frequency,
                                interval = interval,
                                customDays = customDays
                            )
                        } else if (isNew) {
                            viewModel.saveIncome(
                                IncomeEntity(
                                    amountCents = cents ?: 0L,
                                    categoryId = categoryId,
                                    date = date,
                                    note = note.trim(),
                                    merchant = merchant.trim(),
                                    paymentMethod = paymentMethod,
                                    tags = tags.trim()
                                ),
                                true
                            )
                        } else {
                            viewModel.saveIncome(
                                existing!!.copy(
                                    amountCents = cents ?: 0L,
                                    categoryId = categoryId,
                                    date = date,
                                    note = note.trim(),
                                    merchant = merchant.trim(),
                                    paymentMethod = paymentMethod,
                                    tags = tags.trim()
                                ),
                                false
                            )
                        }
                        onDismiss()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = S("abbrechen_13", "Abbrechen"), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        if (!isNew) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = S("loeschen_7", "Löschen"),
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    ConfirmDialog(
        visible = showDelete,
        title = S("einnahme_loeschen_2", "Einnahme löschen?"),
        message = S("dieser_eintrag_wird_unwiderruf_2", "Dieser Eintrag wird unwiderruflich gelöscht."),
        confirmLabel = S("loeschen_14", "Löschen"),
        onConfirm = {
            existing?.let { viewModel.deleteIncome(it) }
            showDelete = false
            onDismiss()
        },
        onDismiss = { showDelete = false }
    )
}

// ---------------------------------------------------------------------------
// Bill
// ---------------------------------------------------------------------------

@Composable
fun BillSheet(viewModel: AppViewModel, existing: BillEntity?, onDismiss: () -> Unit) {
    val settings = viewModel.settings.collectAsState().value
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val categories = remember(viewModel.categories.collectAsState().value) {
        viewModel.categories.value.filter { it.type == CategoryType.EXPENSE.name }
    }
    val isNew = existing == null

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember {
        mutableStateOf(existing?.let { Money.format(it.amountCents, "", withSymbol = false) } ?: "")
    }
    var categoryId by remember {
        mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var dueDate by remember {
        mutableStateOf(existing?.dueDate ?: LocalDate.now().plusDays(7).toEpochDay())
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var isRecurring by remember { mutableStateOf(existing?.isRecurring ?: false) }
    var frequency by remember {
        mutableStateOf(
            runCatching { Frequency.valueOf(existing?.frequency ?: "MONTHLY") }
                .getOrDefault(Frequency.MONTHLY)
        )
    }
    var interval by remember { mutableStateOf(existing?.interval ?: 1) }
    var customDays by remember { mutableStateOf(existing?.customDays ?: 30) }
    var reminderEnabled by remember { mutableStateOf(existing?.reminderEnabled ?: true) }
    var reminderDays by remember { mutableStateOf(existing?.reminderDaysBefore ?: 1) }
    var reminderHour by remember { mutableStateOf(existing?.reminderHour ?: 9) }
    var reminderMinute by remember { mutableStateOf(existing?.reminderMinute ?: 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    BottomSheetContainer(
        onDismiss = onDismiss,
        title = if (isNew) S("rechnung_hinzufuegen", "+ Rechnung hinzufügen") else S("rechnung_bearbeiten", "Rechnung bearbeiten")
    ) {
        AppTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            label = S("name_5", "Name"),
            error = nameError,
            placeholder = S("z_b_miete", "z. B. Miete")
        )
        Spacer(Modifier.height(12.dp))
        AmountField(
            value = amount,
            onValueChange = { amount = it; error = null },
            symbol = symbol,
            error = error
        )

        Spacer(Modifier.height(16.dp))
        Text(S("kategorie_5", "Kategorie"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(8.dp))
        CategoryPicker(categories = categories, selectedId = categoryId, onSelect = { categoryId = it.id })

        Spacer(Modifier.height(16.dp))
        DateField(epochDay = dueDate, onDateChange = { dueDate = it.toEpochDay() }, label = S("faellig_am", "Fällig am"))

        Spacer(Modifier.height(12.dp))
        AppTextField(value = note, onValueChange = { note = it }, label = S("notiz_optional_5", "Notiz (optional)"))

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = S("wiederkehrend_3", "Wiederkehrend"),
            subtitle = S("faelligkeit_wird_nach_dem_bezahlen_aut", "Fälligkeit wird nach dem Bezahlen automatisch verschoben"),
            checked = isRecurring,
            onCheckedChange = { isRecurring = it }
        )
        if (isRecurring) {
            RecurrenceOptions(
                frequency = frequency,
                onFrequencyChange = { frequency = it },
                interval = interval,
                onIntervalChange = { interval = it },
                customDays = customDays,
                onCustomDaysChange = { customDays = it },
                startDate = Dates.fromEpochDay(dueDate)
            )
        }

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = S("erinnerung_2", "Erinnerung"),
            subtitle = S("lokale_benachrichtigung_auf_diesem_ger", "Lokale Benachrichtigung auf diesem Gerät"),
            checked = reminderEnabled,
            onCheckedChange = {
                reminderEnabled = it
                if (!it) viewModel.message(str("erinnerung_fuer_diese_rechnung_ist", "Erinnerung für diese Rechnung ist aus."))
            }
        )
        if (reminderEnabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                S("erinnern_vor_faelligkeit", "Erinnern vor Fälligkeit"),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
            Spacer(Modifier.height(8.dp))
            FlowChipRow(
                options = listOf(0, 1, 3, 7, 14),
                selected = reminderDays,
                label = { if (it == 0) str("reminder_on_due", "Am Fälligkeitstag") else if (it == 1) str("reminder_one_day_before", "1 Tag vorher") else str("reminder_n_days_before", "%1\$s Tage vorher", it) },
                onSelect = { reminderDays = it }
            )
            if (reminderDays !in listOf(0, 1, 3, 7, 14)) {
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = reminderDays.toString(),
                    onValueChange = { value ->
                        val parsed = value.filter { it.isDigit() }.toIntOrNull()
                        if (parsed != null) reminderDays = parsed.coerceIn(0, 365)
                    },
                    label = S("tage_vorher_2", "Tage vorher"),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            TimeField(hour = reminderHour, minute = reminderMinute, onTimeChange = { h, m ->
                reminderHour = h
                reminderMinute = m
            })
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = S("speichern_7", "Speichern"),
            onClick = {
                val cents = Money.parseToCents(amount)
                when {
                    name.trim().isBlank() -> nameError = str("bitte_gib_einen_namen_ein", "Bitte gib einen Namen ein.")
                    amount.isBlank() -> error = str("bitte_gib_einen_betrag_ein_4", "Bitte gib einen Betrag ein.")
                    cents == null -> error = str("bitte_gib_einen_gueltigen_betr_4", "Bitte gib einen gültigen Betrag ein (z. B. 12,50).")
                    cents <= 0 -> error = str("der_betrag_muss_groesser_als_0_3", "Der Betrag muss größer als 0 sein.")
                    else -> {
                        val entity = (existing ?: BillEntity()).copy(
                            name = name.trim(),
                            amountCents = cents ?: 0L,
                            categoryId = categoryId,
                            dueDate = dueDate,
                            note = note.trim(),
                            isRecurring = isRecurring,
                            frequency = frequency.name,
                            interval = interval,
                            customDays = customDays,
                            reminderEnabled = reminderEnabled,
                            reminderDaysBefore = reminderDays,
                            reminderHour = reminderHour,
                            reminderMinute = reminderMinute
                        )
                        viewModel.saveBill(entity, isNew)
                        onDismiss()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (!isNew && existing != null && existing.status != "PAID") {
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = S("als_bezahlt_markieren_2", "Als bezahlt markieren"),
                onClick = {
                    viewModel.markBillPaid(existing, settings.autoExpenseOnBillPaid)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = S("abbrechen_14", "Abbrechen"), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        if (!isNew) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                text = S("loeschen_8", "Löschen"),
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    ConfirmDialog(
        visible = showDelete,
        title = S("rechnung_loeschen_2", "Rechnung löschen?"),
        message = S("rechnung_und_zahlungsverlauf_werde", "Rechnung und Zahlungsverlauf werden gelöscht."),
        confirmLabel = S("loeschen_15", "Löschen"),
        onConfirm = {
            existing?.let { viewModel.deleteBill(it) }
            showDelete = false
            onDismiss()
        },
        onDismiss = { showDelete = false }
    )
}

// ---------------------------------------------------------------------------
// Recurring rule
// ---------------------------------------------------------------------------

@Composable
fun RecurringSheet(
    viewModel: AppViewModel,
    existing: RecurringRuleEntity?,
    onDismiss: () -> Unit
) {
    val settings = viewModel.settings.collectAsState().value
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val allCategories = viewModel.categories.collectAsState().value
    val isNew = existing == null

    var kind by remember {
        mutableStateOf(
            runCatching { RecurringKind.valueOf(existing?.kind ?: "EXPENSE") }
                .getOrDefault(RecurringKind.EXPENSE)
        )
    }
    val categories = remember(allCategories, kind) {
        allCategories.filter {
            it.type == if (kind == RecurringKind.INCOME) CategoryType.INCOME.name
            else CategoryType.EXPENSE.name
        }
    }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember {
        mutableStateOf(existing?.let { Money.format(it.amountCents, "", withSymbol = false) } ?: "")
    }
    var categoryId by remember {
        mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id ?: 0L)
    }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var frequency by remember {
        mutableStateOf(
            runCatching { Frequency.valueOf(existing?.frequency ?: "MONTHLY") }
                .getOrDefault(Frequency.MONTHLY)
        )
    }
    var interval by remember { mutableStateOf(existing?.interval ?: 1) }
    var customDays by remember { mutableStateOf(existing?.customDays ?: 30) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: LocalDate.now().toEpochDay()) }
    var dayOfMonth by remember {
        mutableStateOf(existing?.dayOfMonth ?: LocalDate.now().dayOfMonth)
    }
    var endDateEnabled by remember { mutableStateOf(existing?.endDate != null) }
    var endDate by remember {
        mutableStateOf(existing?.endDate ?: LocalDate.now().plusYears(1).toEpochDay())
    }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    BottomSheetContainer(
        onDismiss = onDismiss,
        title = if (isNew) str("recurring_new", "Wiederkehrende Zahlung") else str("recurring_edit", "Wiederkehrende Zahlung bearbeiten")
    ) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = kind == RecurringKind.EXPENSE,
                onClick = { kind = RecurringKind.EXPENSE },
                label = { Text(S("ausgabe", "Ausgabe"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            FilterChip(
                selected = kind == RecurringKind.INCOME,
                onClick = { kind = RecurringKind.INCOME },
                label = { Text(S("einnahme", "Einnahme"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }

        Spacer(Modifier.height(12.dp))
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = S("bezeichnung_optional", "Bezeichnung (optional)"),
            placeholder = S("z_b_miete_2", "z. B. Miete")
        )
        Spacer(Modifier.height(12.dp))
        AmountField(
            value = amount,
            onValueChange = { amount = it; error = null },
            symbol = symbol,
            error = error
        )

        Spacer(Modifier.height(16.dp))
        Text(S("kategorie_6", "Kategorie"), style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Spacer(Modifier.height(8.dp))
        CategoryPicker(categories = categories, selectedId = categoryId, onSelect = { categoryId = it.id })

        Spacer(Modifier.height(16.dp))
        DateField(
            epochDay = startDate,
            onDateChange = {
                startDate = it.toEpochDay()
                dayOfMonth = it.dayOfMonth
            },
            label = S("start_ab", "Start ab")
        )

        Spacer(Modifier.height(12.dp))
        AppTextField(value = note, onValueChange = { note = it }, label = S("notiz_optional_6", "Notiz (optional)"))

        Spacer(Modifier.height(12.dp))
        RecurrenceOptions(
            frequency = frequency,
            onFrequencyChange = { frequency = it },
            interval = interval,
            onIntervalChange = { interval = it },
            customDays = customDays,
            onCustomDaysChange = { customDays = it },
            startDate = Dates.fromEpochDay(startDate),
            dayOfMonth = dayOfMonth,
            onDayOfMonthChange = { dayOfMonth = it }
        )

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = S("enddatum", "Enddatum"),
            subtitle = S("automatisch_beenden_ab_einem_datum", "Automatisch beenden ab einem Datum"),
            checked = endDateEnabled,
            onCheckedChange = { endDateEnabled = it }
        )
        if (endDateEnabled) {
            DateField(epochDay = endDate, onDateChange = { endDate = it.toEpochDay() }, label = S("endet_am", "Endet am"))
        }

        if (!isNew) {
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = S("aktiv", "Aktiv"),
                subtitle = S("faellige_termine_werden_erzeugt", "Fällige Termine werden erzeugt"),
                checked = active,
                onCheckedChange = { active = it }
            )
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton(
            text = S("speichern_8", "Speichern"),
            onClick = {
                val cents = Money.parseToCents(amount)
                when {
                    amount.isBlank() -> error = str("bitte_gib_einen_betrag_ein_5", "Bitte gib einen Betrag ein.")
                    cents == null -> error = str("bitte_gib_einen_gueltigen_betr_5", "Bitte gib einen gültigen Betrag ein (z. B. 12,50).")
                    cents <= 0 -> error = str("der_betrag_muss_groesser_als_0_4", "Der Betrag muss größer als 0 sein.")
                    categoryId == 0L -> error = str("bitte_waehle_eine_kategorie_2", "Bitte wähle eine Kategorie.")
                    endDateEnabled && endDate < startDate -> error = str("das_enddatum_muss_nach_dem_startda", "Das Enddatum muss nach dem Startdatum liegen.")
                    else -> {
                        val entity = (existing ?: RecurringRuleEntity()).copy(
                            kind = kind.name,
                            name = name.trim(),
                            amountCents = cents ?: 0L,
                            categoryId = categoryId,
                            note = note.trim(),
                            frequency = frequency.name,
                            interval = interval,
                            customDays = customDays,
                            dayOfMonth = dayOfMonth,
                            monthOfYear = Dates.fromEpochDay(startDate).monthValue,
                            startDate = startDate,
                            endDate = if (endDateEnabled) endDate else null,
                            active = active,
                            lastGenerated = existing?.lastGenerated
                        )
                        viewModel.saveRule(entity, isNew)
                        onDismiss()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = S("abbrechen_15", "Abbrechen"), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Shared sheet scaffolding
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BottomSheetContainer(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = if (appColors().isDark) androidx.compose.ui.graphics.Color(0xFF081231) else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(appColors().glow, appColors().cyan)
                        )
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = S("schliessen_2", "Schließen"))
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceOptions(
    frequency: Frequency,
    onFrequencyChange: (Frequency) -> Unit,
    interval: Int,
    onIntervalChange: (Int) -> Unit,
    customDays: Int,
    onCustomDaysChange: (Int) -> Unit,
    startDate: LocalDate,
    dayOfMonth: Int = startDate.dayOfMonth,
    onDayOfMonthChange: ((Int) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(appColors().cardAlt)
            .padding(14.dp)
    ) {
        Text(S("wiederholung", "Wiederholung"), style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Frequency.values().forEach { option ->
                FilterChip(
                    selected = frequency == option,
                    onClick = { onFrequencyChange(option) },
                    label = {
                        Text(
                            com.finovo.app.util.Recurrence.frequencyLabel(option),
                            style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        if (frequency != Frequency.CUSTOM) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    S("alle_2", "Alle"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IntervalInput(value = interval, onValueChange = onIntervalChange)
                Text(
                    when (frequency) {
                        Frequency.DAILY -> if (interval == 1) str("unit_day_one", "Tag") else str("unit_day_many", "Tage")
                        Frequency.WEEKLY -> if (interval == 1) str("unit_week_one", "Woche") else str("unit_week_many", "Wochen")
                        Frequency.MONTHLY -> if (interval == 1) S("monat_5", "Monat") else S("monate", "Monate")
                        Frequency.YEARLY -> if (interval == 1) S("jahr_5", "Jahr") else S("jahre", "Jahre")
                        Frequency.CUSTOM -> str("unit_day_many", "Tage")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    S("alle_3", "Alle"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IntervalInput(value = customDays, onValueChange = onCustomDaysChange)
                Text(
                    S("tage", "Tage"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (frequency == Frequency.MONTHLY) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    S("am", "Am"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                IntervalInput(
                    value = dayOfMonth,
                    onValueChange = { onDayOfMonthChange?.invoke(it.coerceIn(1, 31)) },
                    enabled = onDayOfMonthChange != null
                )
                Text(
                    S("des_monats", "des Monats"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        val preview = com.finovo.app.util.Recurrence.describe(
            com.finovo.app.util.RecurrenceSpec(
                frequency = frequency,
                interval = interval,
                customDays = customDays,
                dayOfMonth = dayOfMonth,
                monthOfYear = startDate.monthValue,
                startDate = startDate
            )
        )
        Text(
            S("vorschau_preview_erstmals_am_dates_ful", "Vorschau: %2\$s · erstmals am %1\$s", Dates.full(startDate), preview),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted
        )
    }
}

@Composable
private fun IntervalInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(appColors().card)
            .padding(horizontal = 4.dp)
    ) {
        IconButton(onClick = { if (value > 1) onValueChange(value - 1) }, enabled = enabled) {
            Text("−", fontSize = 20.sp)
        }
        Text(
            S("value", "%1\$s", value),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(28.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = { if (value < 99) onValueChange(value + 1) }, enabled = enabled) {
            Text("+", fontSize = 20.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FlowChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(label(option), style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

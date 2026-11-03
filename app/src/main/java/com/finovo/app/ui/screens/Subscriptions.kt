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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.CategoryType
import com.finovo.app.data.Frequency
import com.finovo.app.data.SubscriptionEntity
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.DropdownField
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import java.time.LocalDate
import com.finovo.app.util.S
import com.finovo.app.util.str
import com.finovo.app.util.plurals

private val SUB_EMOJIS = listOf(
    "\uD83D\uDD01", "\uD83C\uDFAC", "\uD83C\uDFB5", "\uD83C\uDFAE", "\u2601",
    "\uD83D\uDCF1", "\uD83D\uDCF0", "\uD83D\uDDA5", "\uD83C\uDFC3", "\uD83D\uDCDA",
    "\uD83D\uDCF7", "\uD83C\uDF7D", "\uD83D\uDE97", "\uD83D\uDD0B", "\uD83C\uDF81"
)

private fun frequencyLabel(freq: Frequency, interval: Int): String = when (freq) {
    Frequency.DAILY -> if (interval == 1) str("cycle_daily", "täglich") else plurals("pl_every_n_days_lc", interval, "alle %1\$d Tag", "alle %1\$d Tage")
    Frequency.WEEKLY -> if (interval == 1) str("cycle_weekly", "wöchentlich") else str("cycle_every_n_weeks", "alle %1\$s Wochen", interval)
    Frequency.MONTHLY -> if (interval == 1) str("monatlich_2", "monatlich") else str("cycle_every_n_months", "alle %1\$s Monate", interval)
    Frequency.YEARLY -> if (interval == 1) str("cycle_yearly", "jährlich") else str("cycle_every_n_years", "alle %1\$s Jahre", interval)
    Frequency.CUSTOM -> str("cycle_custom", "individuell")
}

@Composable
fun SubscriptionsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val subs by viewModel.subscriptions.collectAsState()
    val summary by viewModel.subscriptionSummary.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val expenseCategories = categories.filter { it.type == CategoryType.EXPENSE.name }

    var editing by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var showInactive by remember { mutableStateOf(false) }

    val active = subs.filter { it.active }
    val visible = if (showInactive) subs.filter { !it.active } else active
    val today = Dates.today().toEpochDay()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar(
                title = S("subscriptions", "Abos"),
                subtitle = if (summary.count == 0) null else plurals("pl_subs_active", summary.count, "%1\$d aktives Abo", "%1\$d aktive Abos"),
                onBack = onBack
            )
        }

        if (active.isNotEmpty()) {
            item {
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    S("pro_monat_2", "Pro Monat"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                                Text(
                                    Money.format(summary.monthlyCents, symbol),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = appColors().expense,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    S("pro_jahr_2", "Pro Jahr"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                                Text(
                                    Money.format(summary.yearlyCents, symbol),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = appColors().expense,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            S("automatisch_aus_allen_aktiven_abos_ber", "Automatisch aus allen aktiven Abos berechnet – jede Frequenz wird auf Monat und Jahr umgerechnet."),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    emoji = "\uD83D\uDD01",
                    title = if (showInactive) str("subs_empty_inactive", "Keine beendeten Abos") else str("subs_empty_active", "Noch keine Abos"),
                    message = S("trage_netflix_spotify_versicherung", "Trage Netflix, Spotify, Versicherungen oder Fitness ein – die App rechnet Monats- und Jahreskosten automatisch zusammen."),
                    actionLabel = if (showInactive) null else str("subs_add", "Abo anlegen"),
                    onAction = if (showInactive) null else {
                        { editing = null; showEditor = true }
                    }
                )
            }
        } else {
            item {
                SectionHeader(
                    title = if (showInactive) str("subs_title_inactive", "Beendete Abos") else str("subs_title_active", "Aktive Abos"),
                    actionLabel = if (showInactive) "Aktive" else "Beendete",
                    onAction = { showInactive = !showInactive }
                )
            }
            items(visible, key = { it.id }) { sub ->
                SubscriptionCard(
                    sub = sub,
                    symbol = symbol,
                    monthly = viewModel.monthlyEquivalentCents(sub),
                    yearly = viewModel.yearlyEquivalentCents(sub),
                    today = today,
                    onEdit = { editing = sub; showEditor = true },
                    onDelete = { deleting = sub }
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = S("neues_abo", "Neues Abo"),
                    onClick = { editing = null; showEditor = true },
                    icon = Icons.Default.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    if (showEditor) {
        SubscriptionEditorDialog(
            existing = editing,
            categories = expenseCategories,
            symbol = symbol,
            onDismiss = { showEditor = false; editing = null },
            onSave = { sub ->
                viewModel.saveSubscription(sub)
                showEditor = false
                editing = null
            }
        )
    }

    ConfirmDialog(
        visible = deleting != null,
        title = S("abo_loeschen", "Abo löschen?"),
        message = str("subs_remove_confirm", "„%1\$s“ wird aus der Liste entfernt.", deleting?.name.orEmpty()),
        confirmLabel = S("loeschen_16", "Löschen"),
        onConfirm = {
            deleting?.let { viewModel.deleteSubscription(it) }
            deleting = null
        },
        onDismiss = { deleting = null }
    )
}

@Composable
private fun SubscriptionCard(
    sub: SubscriptionEntity,
    symbol: String,
    monthly: Long,
    yearly: Long,
    today: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val freq = runCatching { Frequency.valueOf(sub.frequency) }.getOrDefault(Frequency.MONTHLY)
    val next = Dates.fromEpochDay(sub.nextPayment)
    val days = Dates.daysUntil(next)
    val dueSoon = days <= sub.reminderDaysBefore && days >= 0

    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors().cardAlt),
                    contentAlignment = Alignment.Center
                ) { Text(sub.emoji, fontSize = 22.sp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sub.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        S("money_format_sub_amountcents_symbol_fr", "%1\$s · %2\$s", Money.format(sub.amountCents, symbol), frequencyLabel(freq, sub.interval)),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Money.format(monthly, symbol),
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors().expense,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        S("pro_monat_3", "pro Monat"),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = S("bearbeiten_3", "Bearbeiten"), tint = appColors().textMuted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = S("loeschen_4", "Löschen"), tint = appColors().expense)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    S("naechste_zahlung_dates_full_next", "Nächste Zahlung: %1\$s", Dates.full(next)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dueSoon) appColors().warning else appColors().textMuted,
                    modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
                Text(
                    Money.format(yearly, symbol) + S("jahr_6", " / Jahr"),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
            if (days >= 0) {
                Text(
                    if (days == 0L) str("subs_due_today", "heute fällig") else str("subs_due_in_days", "in %1\$s Tagen · Erinnerung %2\$s Tage vorher", days, sub.reminderDaysBefore),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dueSoon) appColors().warning else appColors().textMuted
                )
            }
            sub.cancelByDate?.let {
                Text(
                    S("kuendigen_bis_dates_full_dates_fromepo", "Kündigen bis: %1\$s", Dates.full(Dates.fromEpochDay(it))),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
            if (sub.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    sub.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SubscriptionEditorDialog(
    existing: SubscriptionEntity?,
    categories: List<com.finovo.app.data.CategoryEntity>,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (SubscriptionEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "\uD83D\uDD01") }
    var amountText by remember {
        mutableStateOf(
            existing?.amountCents?.takeIf { it > 0 }?.let { Money.format(it, "", withSymbol = false) } ?: ""
        )
    }
    var freq by remember {
        mutableStateOf(runCatching { Frequency.valueOf(existing?.frequency ?: "") }.getOrDefault(Frequency.MONTHLY))
    }
    var intervalText by remember { mutableStateOf((existing?.interval ?: 1).toString()) }
    var nextDate by remember {
        mutableStateOf(existing?.nextPayment?.let { Dates.fromEpochDay(it) } ?: LocalDate.now().plusMonths(1))
    }
    var categoryId by remember { mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id ?: 0L) }
    var reminder by remember { mutableStateOf(existing?.reminderEnabled ?: true) }
    var daysText by remember { mutableStateOf((existing?.reminderDaysBefore ?: 3).toString()) }
    var useCancel by remember { mutableStateOf(existing?.cancelByDate != null) }
    var cancelDate by remember {
        mutableStateOf(existing?.cancelByDate?.let { Dates.fromEpochDay(it) } ?: LocalDate.now().plusMonths(11))
    }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) str("subs_new", "Neues Abo") else str("subs_edit", "Abo bearbeiten")) },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SUB_EMOJIS) { e ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else appColors().cardAlt)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppTextField(value = name, onValueChange = { name = it }, label = S("name_3", "Name"))
                Spacer(Modifier.height(10.dp))
                AmountField(value = amountText, onValueChange = { amountText = it }, symbol = symbol, label = S("betrag_2", "Betrag"))
                Spacer(Modifier.height(10.dp))
                DropdownField(
                    label = S("intervall", "Intervall"),
                    options = Frequency.values().toList(),
                    selected = freq,
                    optionLabel = { frequencyLabel(it, 1) },
                    onSelect = { freq = it }
                )
                if (freq != Frequency.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    AppTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                        label = S("wiederholung_alle", "Wiederholung alle")
                    )
                }
                Spacer(Modifier.height(10.dp))
                DateField(epochDay = nextDate.toEpochDay(), onDateChange = { nextDate = it }, label = S("naechste_zahlung", "Nächste Zahlung"))
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    DropdownField(
                        label = S("kategorie_3", "Kategorie"),
                        options = categories,
                        selected = categories.firstOrNull { it.id == categoryId } ?: categories.first(),
                        optionLabel = { "${it.emoji} ${it.name}" },
                        onSelect = { categoryId = it.id }
                    )
                }
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = S("erinnerung", "Erinnerung"),
                    subtitle = S("erinnert_rechtzeitig_vor_der_naechsten", "Erinnert rechtzeitig vor der nächsten Zahlung"),
                    checked = reminder,
                    onCheckedChange = { reminder = it }
                )
                if (reminder) {
                    AppTextField(
                        value = daysText,
                        onValueChange = { daysText = it.filter { c -> c.isDigit() } },
                        label = S("tage_vorher", "Tage vorher")
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useCancel, onCheckedChange = { useCancel = it })
                    Text(S("kuendigungsfrist_merken", "Kündigungsfrist merken"), style = MaterialTheme.typography.bodyMedium)
                }
                if (useCancel) {
                    DateField(epochDay = cancelDate.toEpochDay(), onDateChange = { cancelDate = it }, label = S("kuendigen_bis", "Kündigen bis"))
                }
                Spacer(Modifier.height(10.dp))
                AppTextField(value = note, onValueChange = { note = it }, label = S("notiz_optional_2", "Notiz (optional)"))
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = Money.parseToCents(amountText.ifBlank { "0" })
                when {
                    name.isBlank() -> error = str("bitte_einen_namen_eingeben_3", "Bitte einen Namen eingeben.")
                    amount == null || amount <= 0L -> error = str("bitte_einen_betrag_eingeben_3", "Bitte einen Betrag eingeben.")
                    else -> onSave(
                        (existing ?: SubscriptionEntity()).copy(
                            name = name.trim(),
                            emoji = emoji,
                            amountCents = amount,
                            frequency = freq.name,
                            interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            nextPayment = nextDate.toEpochDay(),
                            startDate = existing?.startDate ?: LocalDate.now().toEpochDay(),
                            categoryId = categoryId,
                            reminderEnabled = reminder,
                            reminderDaysBefore = daysText.toIntOrNull()?.coerceIn(0, 60) ?: 3,
                            cancelByDate = if (useCancel) cancelDate.toEpochDay() else null,
                            note = note.trim(),
                            active = existing?.active ?: true
                        )
                    )
                }
            }) { Text(S("speichern_3", "Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S("abbrechen_9", "Abbrechen")) } }
    )
}

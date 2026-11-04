package com.finovo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.SavingGoalEntity
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.HorizontalBar
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Goals
import com.finovo.app.util.Money
import java.time.LocalDate
import com.finovo.app.util.S
import com.finovo.app.util.str
import com.finovo.app.util.plurals

private val GOAL_EMOJIS = listOf(
    "\uD83C\uDFAF", "\uD83C\uDFD6", "\uD83D\uDE97", "\uD83C\uDFE0", "\uD83D\uDCDA",
    "\uD83D\uDCF1", "\uD83D\uDC5E", "\u2708", "\uD83C\uDF81", "\uD83D\uDCB0",
    "\uD83D\uDECB", "\uD83C\uDFA8", "\uD83C\uDFB5", "\u26BD", "\uD83D\uDC3B"
)

@Composable
fun GoalsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val goals by viewModel.goals.collectAsState()
    val summary by viewModel.goalSummary.collectAsState()

    var editing by remember { mutableStateOf<SavingGoalEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var depositing by remember { mutableStateOf<SavingGoalEntity?>(null) }
    var deleting by remember { mutableStateOf<SavingGoalEntity?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val visible = goals.filter { it.archived == showArchived }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar(
                tutorialId = "goals",
                title = S("goals", "Sparziele"),
                subtitle = if (summary.count == 0) null else plurals("pl_goals_active", summary.count, "%1\$d aktives Ziel", "%1\$d aktive Ziele"),
                onBack = onBack
            )
        }

        if (goals.isNotEmpty()) {
            item {
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    S("gespart", "Gespart"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                                Text(
                                    Money.format(summary.savedCents, symbol),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = appColors().income,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    S("zielsumme", "Zielsumme"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                                Text(
                                    Money.format(summary.targetCents, symbol),
                                    style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalBar(
                            percent = summary.percent,
                            color = appColors().income
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            S("summary_percent_100_toint_erreicht_noc", "%1\$d %% erreicht · noch %2\$s", (summary.percent * 100).toInt(), Money.format(summary.remainingCents, symbol)),
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
                    emoji = "\uD83C\uDFAF",
                    title = if (showArchived) str("goals_empty_archived", "Keine archivierten Ziele") else str("goals_empty", "Noch keine Sparziele"),
                    message = if (showArchived) {
                        S("erreichte_ziele_kannst_du_hier_arc", "Erreichte Ziele kannst du hier archivieren und später ansehen.")
                    } else {
                        S("lege_ein_ziel_fest_zum_beispiel_ur", "Lege ein Ziel fest, zum Beispiel „Urlaub“ mit 2.000 €, und verfolge deinen Fortschritt.")
                    },
                    actionLabel = if (showArchived) null else str("goals_add", "Ziel anlegen"),
                    onAction = if (showArchived) null else {
                        { editing = null; showEditor = true }
                    }
                )
            }
        } else {
            item {
                SectionHeader(
                    title = if (showArchived) str("goals_title_archived", "Archivierte Ziele") else str("goals_title", "Deine Ziele"),
                    actionLabel = if (showArchived) "Aktive" else "Archiv",
                    onAction = { showArchived = !showArchived }
                )
            }
            items(visible, key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    symbol = symbol,
                    onDeposit = { depositing = goal },
                    onEdit = { editing = goal; showEditor = true },
                    onDelete = { deleting = goal }
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = S("neues_sparziel", "Neues Sparziel"),
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
        GoalEditorDialog(
            existing = editing,
            symbol = symbol,
            onDismiss = { showEditor = false; editing = null },
            onSave = { goal ->
                viewModel.saveGoal(goal)
                showEditor = false
                editing = null
            }
        )
    }

    depositing?.let { goal ->
        DepositDialog(
            goal = goal,
            symbol = symbol,
            onDismiss = { depositing = null },
            onConfirm = { cents ->
                viewModel.depositToGoal(goal.id, cents)
                depositing = null
            }
        )
    }

    ConfirmDialog(
        visible = deleting != null,
        title = S("sparziel_loeschen", "Sparziel löschen?"),
        message = S("wird_entfernt_buchungen_bleiben_un", "„%1\$s“ wird entfernt. Buchungen bleiben unberührt.", deleting?.name.orEmpty()),
        confirmLabel = S("loeschen_10", "Löschen"),
        onConfirm = {
            deleting?.let { viewModel.deleteGoal(it) }
            deleting = null
        },
        onDismiss = { deleting = null }
    )
}

@Composable
private fun GoalCard(
    goal: SavingGoalEntity,
    symbol: String,
    onDeposit: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val percent = if (goal.targetCents <= 0L) 0f
    else (goal.savedCents.toFloat() / goal.targetCents.toFloat()).coerceIn(0f, 1f)
    val remaining = (goal.targetCents - goal.savedCents).coerceAtLeast(0)
    val done = goal.targetCents > 0 && goal.savedCents >= goal.targetCents
    val color = when {
        done -> appColors().income
        percent >= 0.75f -> appColors().income
        percent >= 0.4f -> MaterialTheme.colorScheme.primary
        else -> appColors().textMuted
    }
    val required = Goals.requiredMonthlyCents(remaining, goal.targetDate, Dates.today())
    val daysLeft = Goals.daysLeft(goal.targetDate, Dates.today())

    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Grosse, leuchtende Ziel-Kachel im Premium-Design
                com.finovo.app.ui.components.CategoryBadge(emoji = goal.emoji, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (done) {
                        Text(
                            S("ziel_erreicht_ud83c_udf89", "Ziel erreicht \uD83C\uDF89"),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().income
                        )
                    } else if (goal.targetDate != null) {
                        // Kein '!!': ein zwischenzeitlich entferntes Zieldatum
                        // darf die Ansicht nicht zum Absturz bringen.
                        val d = goal.targetDate?.let { Dates.fromEpochDay(it) }
                        Text(
                            S("ziel_bis_dates_full_d", "Ziel bis %1\$s", Dates.fullOrEmpty(d)) +
                                (daysLeft?.let { plurals("pl_days_left", it.toInt(), " · noch %1\$d Tag", " · noch %1\$d Tage") } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            S("ohne_zieldatum", "Ohne Zieldatum"),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = S("bearbeiten_2", "Bearbeiten"), tint = appColors().textMuted)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = S("loeschen_3", "Löschen"), tint = appColors().expense)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalBar(percent = percent, color = color)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        S("gespart_2", "Gespart"),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                    Text(
                        Money.format(goal.savedCents, symbol),
                        style = MaterialTheme.typography.titleMedium,
                        color = appColors().income,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        S("ziel", "Ziel"),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                    Text(
                        Money.format(goal.targetCents, symbol),
                        style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!done) {
                Spacer(Modifier.height(8.dp))
                Text(
                    S("noch_money_format_remaining_symbol_per", "Noch %1\$s · %2\$d %%", Money.format(remaining, symbol), (percent * 100).toInt()) +
                        (if (required > 0) S("pro_monat_noetig", " · %1\$s pro Monat nötig", Money.format(required, symbol)) else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }

            if (goal.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    goal.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }

            if (!done) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = S("einzahlen_3", "Einzahlen"),
                    onClick = onDeposit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GoalEditorDialog(
    existing: SavingGoalEntity?,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (SavingGoalEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "\uD83C\uDFAF") }
    var targetText by remember {
        mutableStateOf(
            existing?.targetCents?.takeIf { it > 0 }?.let { Money.format(it, "", withSymbol = false) } ?: ""
        )
    }
    var savedText by remember {
        mutableStateOf(
            existing?.savedCents?.takeIf { it > 0 }?.let { Money.format(it, "", withSymbol = false) } ?: ""
        )
    }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var useDate by remember { mutableStateOf(existing?.targetDate != null) }
    var date by remember {
        mutableStateOf(
            existing?.targetDate?.let { Dates.fromEpochDay(it) } ?: LocalDate.now().plusMonths(12)
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) str("goals_new", "Neues Sparziel") else str("goals_edit", "Sparziel bearbeiten")) },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(GOAL_EMOJIS) { e ->
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
                Spacer(Modifier.height(10.dp))
                // Modul 25: Vorlagen für die häufigsten Sparziele
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf(
                            str("goal_preset_emergency", "Notvorrat") to "\uD83D\uDED8",
                            str("goal_preset_car", "Auto") to "\uD83D\uDE97",
                            str("goal_preset_holiday", "Urlaub") to "\uD83C\uDFD6",
                            str("goal_preset_phone", "Handy") to "\uD83D\uDCF1",
                            str("goal_preset_home", "Wohnen") to "\uD83C\uDFE0",
                            str("goal_preset_other", "Sonstiges") to "\uD83C\uDFAF"
                        )
                    ) { (text, e) ->
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(appColors().cardAlt)
                                .clickable { name = text; emoji = e }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("$e $text", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppTextField(value = name, onValueChange = { name = it }, label = S("name_2", "Name"))
                Spacer(Modifier.height(10.dp))
                AmountField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    symbol = symbol,
                    label = S("zielbetrag", "Zielbetrag")
                )
                Spacer(Modifier.height(10.dp))
                AmountField(
                    value = savedText,
                    onValueChange = { savedText = it },
                    symbol = symbol,
                    label = S("bereits_gespart", "Bereits gespart")
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(value = note, onValueChange = { note = it }, label = S("notiz_optional", "Notiz (optional)"))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = useDate,
                        onCheckedChange = { useDate = it }
                    )
                    Text(S("zieldatum_festlegen", "Zieldatum festlegen"), style = MaterialTheme.typography.bodyMedium)
                }
                if (useDate) {
                    Spacer(Modifier.height(8.dp))
                    DateField(epochDay = date.toEpochDay(), onDateChange = { date = it }, label = S("zieldatum", "Zieldatum"))
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val target = Money.parseToCents(targetText.ifBlank { "0" })
                val saved = Money.parseToCents(savedText.ifBlank { "0" })
                when {
                    name.isBlank() -> error = str("bitte_einen_namen_eingeben_2", "Bitte einen Namen eingeben.")
                    target == null || target <= 0L -> error = str("bitte_einen_zielbetrag_eingeben", "Bitte einen Zielbetrag eingeben.")
                    else -> onSave(
                        (existing ?: SavingGoalEntity()).copy(
                            name = name.trim(),
                            emoji = emoji,
                            targetCents = target,
                            savedCents = saved ?: 0L,
                            targetDate = if (useDate) date.toEpochDay() else null,
                            note = note.trim()
                        )
                    )
                }
            }) { Text(S("speichern_2", "Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S("abbrechen_7", "Abbrechen")) } }
    )
}

@Composable
private fun DepositDialog(
    goal: SavingGoalEntity,
    symbol: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val remaining = (goal.targetCents - goal.savedCents).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S("einzahlen", "Einzahlen")) },
        text = {
            Column {
                Text(
                    S("goal_name_noch_money_format_remaining", "„%1\$s“ · noch %2\$s bis zum Ziel", goal.name, Money.format(remaining, symbol)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
                Spacer(Modifier.height(12.dp))
                AmountField(value = text, onValueChange = { text = it }, symbol = symbol, label = S("betrag", "Betrag"), error = error)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10_00L, 25_00L, 50_00L).forEach { quick ->
                        TextButton(onClick = { text = Money.format(quick, "", withSymbol = false) }) {
                            Text(Money.format(quick, symbol))
                        }
                    }
                }
                if (remaining > 0) {
                    TextButton(onClick = { text = Money.format(remaining, "", withSymbol = false) }) {
                        Text(S("restbetrag_money_format_remaining_symb", "Restbetrag %1\$s", Money.format(remaining, symbol)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = Money.parseToCents(text)
                if (cents == null || cents <= 0L) {
                    error = str("bitte_einen_betrag_eingeben_2", "Bitte einen Betrag eingeben.")
                } else onConfirm(cents)
            }) { Text(S("einzahlen_2", "Einzahlen")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S("abbrechen_8", "Abbrechen")) } }
    )
}

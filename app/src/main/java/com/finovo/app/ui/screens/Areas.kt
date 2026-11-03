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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import com.finovo.app.data.AreaEntity
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Money
import com.finovo.app.util.S
import com.finovo.app.util.str

private val AREA_EMOJIS = listOf(
    "\uD83D\uDC64", "\uD83D\uDC65", "\uD83D\uDE97", "\uD83C\uDFD6", "\uD83C\uDFE0",
    "\uD83D\uDCBC", "\uD83D\uDCB0", "\uD83C\uDF81", "\uD83D\uDC3E", "\uD83C\uDF93",
    "\uD83D\uDED2", "\uD83C\uDFEA", "\u2699", "\uD83D\uDCC1", "\u2B50"
)

@Composable
fun AreasScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val areas by viewModel.areas.collectAsState()
    val activeId = settings.activeAreaId

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AreaEntity?>(null) }
    var deleting by remember { mutableStateOf<AreaEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar(
                title = S("areas", "Bereiche"),
                subtitle = S("finanzen_getrennt_fuehren", "Finanzen getrennt führen"),
                onBack = onBack
            )
        }

        item {
            AppCard(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                onClick = { viewModel.setActiveArea(0L) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(appColors().cardAlt),
                        contentAlignment = Alignment.Center
                    ) { Text(S("ud83d_udcc2", "\uD83D\uDCC2"), fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S("alle_bereiche", "Alle Bereiche"), style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                        val total = viewModel.areaTotals(0L)
                        Text(
                            S("total_count_buchungen", "%1\$s Buchungen", total.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                    Text(
                        Money.format(viewModel.areaTotals(0L).balanceCents, symbol, false),
                        style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    if (activeId == 0L) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (areas.isEmpty()) {
            item {
                EmptyState(
                    emoji = "\uD83D\uDCC1",
                    title = S("noch_keine_bereiche", "Noch keine Bereiche"),
                    message = S("trenne_deine_finanzen_in_bereiche_", "Trenne deine Finanzen in Bereiche – zum Beispiel Persönlich, Familie, Auto oder Urlaub. Jeder Bereich hat eigene Zahlen."),
                    actionLabel = S("bereich_anlegen", "Bereich anlegen"),
                    onAction = { editing = null; showEditor = true }
                )
            }
        } else {
            item { SectionHeader(title = S("deine_bereiche", "Deine Bereiche")) }
            items(areas, key = { it.id }) { area ->
                val totals = viewModel.areaTotals(area.id)
                AppCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    onClick = { viewModel.setActiveArea(area.id) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(appColors().cardAlt),
                                contentAlignment = Alignment.Center
                            ) { Text(area.emoji, fontSize = 22.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    area.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    S("totals_count_buchungen", "%1\$s Buchungen", totals.count),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            Text(
                                Money.format(totals.balanceCents, symbol),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (totals.balanceCents < 0) appColors().expense else appColors().income,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                            )
                            if (activeId == area.id) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                S("einnahmen_money_format_totals_incomece", "Einnahmen %1\$s", Money.format(totals.incomeCents, symbol)),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().income
                            )
                            Text(
                                S("ausgaben_money_format_totals_expensece", "Ausgaben %1\$s", Money.format(totals.expenseCents, symbol)),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().expense
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row {
                            IconButton(onClick = { editing = area; showEditor = true }) {
                                Icon(Icons.Default.Edit, contentDescription = S("umbenennen", "Umbenennen"), tint = appColors().textMuted)
                            }
                            IconButton(onClick = { deleting = area }) {
                                Icon(Icons.Default.Delete, contentDescription = S("loeschen_5", "Löschen"), tint = appColors().expense)
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = S("neuer_bereich", "Neuer Bereich"),
                    onClick = { editing = null; showEditor = true },
                    icon = Icons.Default.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Text(
                S("der_aktive_bereich_wird_fuer_neue_buch", "Der aktive Bereich wird für neue Buchungen verwendet. Statistiken und Salden werden pro Bereich separat ausgewertet."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }

    if (showEditor) {
        AreaEditorDialog(
            existing = editing,
            onDismiss = { showEditor = false; editing = null },
            onSave = { name, emoji ->
                val current = editing
                if (current == null) viewModel.addArea(name, emoji)
                else viewModel.renameArea(current.copy(emoji = emoji), name)
                showEditor = false
                editing = null
            }
        )
    }

    ConfirmDialog(
        visible = deleting != null,
        title = S("bereich_loeschen", "Bereich löschen?"),
        message = S("wird_entfernt_deine_buchungen_blei", "„%1\$s“ wird entfernt. Deine Buchungen bleiben erhalten und werden „Alle Bereiche“ zugeordnet.", deleting?.name.orEmpty()),
        confirmLabel = S("loeschen_6", "Löschen"),
        onConfirm = {
            deleting?.let { viewModel.deleteAreaKeepingData(it) }
            deleting = null
        },
        onDismiss = { deleting = null }
    )
}

@Composable
private fun AreaEditorDialog(
    existing: AreaEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "\uD83D\uDCC1") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) str("area_new", "Neuer Bereich") else str("area_edit", "Bereich bearbeiten")) },
        text = {
            Column {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AREA_EMOJIS) { e ->
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
                AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = S("name_4", "Name"),
                    error = error,
                    placeholder = S("z_b_familie", "z. B. Familie")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) error = str("bitte_einen_namen_eingeben", "Bitte einen Namen eingeben.")
                else onSave(name.trim(), emoji)
            }) { Text(S("speichern_4", "Speichern")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S("abbrechen_10", "Abbrechen")) } }
    )
}

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.HorizontalBar
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.CarCosts
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.S
import com.finovo.app.util.str
import com.finovo.app.util.plurals

@Composable
fun CarScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val summary by viewModel.carSummary.collectAsState()

    LaunchedEffect(Unit) { viewModel.ensureCarCategories() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar(
                tutorialId = "car",
                title = S("car", "Auto"),
                subtitle = S("alle_kosten_rund_ums_fahrzeug", "Alle Kosten rund ums Fahrzeug"),
                onBack = onBack
            )
        }

        if (!summary.available || summary.entries.isEmpty()) {
            item {
                EmptyState(
                    emoji = "\uD83D\uDE97",
                    title = S("auto_modul_ist_bereit", "Auto-Modul ist bereit"),
                    message = S("erfasse_ausgaben_in_den_kategorien", "Erfasse Ausgaben in den Kategorien Sprit, Versicherung, Steuer, Reparatur, Wartung, TÜV, Reifen, Ölwechsel, Wäsche oder Finanzierung – die App summiert sie automatisch pro Monat und Jahr."),
                    actionLabel = S("ausgabe_erfassen_2", "Ausgabe erfassen"),
                    onAction = { viewModel.openExpenseSheet(null) }
                )
            }
            item {
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(S("kostenarten", "Kostenarten"), style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        CarCosts.types.forEach { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(type.emoji, fontSize = 16.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    CarCosts.labelFor(type.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    S("dieser_monat_2", "Dieser Monat"),
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
                                    S("dieses_jahr_2", "Dieses Jahr"),
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
                        if (summary.yearlyCents > 0) {
                            Spacer(Modifier.height(12.dp))
                            val months = (Dates.today().monthValue).coerceAtLeast(1)
                            Text(
                                S("money_format_summary_yearlycents_month", "Ø %1\$s pro Monat im bisherigen Jahresverlauf", Money.format(summary.yearlyCents / months, symbol)),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = S("nach_kostenart", "Nach Kostenart")) }

            items(summary.entries, key = { it.categoryId }) { entry ->
                val max = summary.entries.maxOf { kotlin.math.abs(it.yearlyCents) }.coerceAtLeast(1)
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(appColors().cardAlt),
                                contentAlignment = Alignment.Center
                            ) { Text(entry.emoji, fontSize = 18.sp) }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    CarCosts.labelFor(entry.name),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    plurals("pl_bookings_month", entry.count, "%1\$d Buchung · %2\$s diesen Monat", "%1\$d Buchungen · %2\$s diesen Monat", Money.format(entry.monthlyCents, symbol)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Money.format(entry.yearlyCents, symbol),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = appColors().expense,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    S("jahr", "Jahr"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalBar(
                            percent = (entry.yearlyCents.toFloat() / max.toFloat()).coerceIn(0f, 1f),
                            color = appColors().expense
                        )
                    }
                }
            }

            if (summary.lastEntries.isNotEmpty()) {
                item { SectionHeader(title = S("letzte_eintraege", "Letzte Einträge")) }
                items(summary.lastEntries, key = { it.id }) { e ->
                    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    e.note.ifBlank { str("no_note", "Ohne Notiz") },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    Dates.full(Dates.fromEpochDay(e.date)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            Text(
                                Money.format(-e.amountCents, symbol),
                                style = MaterialTheme.typography.titleSmall,
                                color = appColors().expense,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = S("ausgabe_erfassen", "Ausgabe erfassen"),
                    onClick = { viewModel.openExpenseSheet(null) },
                    icon = Icons.Default.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

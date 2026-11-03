package com.finovo.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.SmartInsight
import com.finovo.app.util.InsightSeverity
import com.finovo.app.util.S
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun InsightsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val insights by viewModel.insights.collectAsState()
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }
    val einblicke by viewModel.einblicke.collectAsState()
    val ausgaben by viewModel.expenses.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar(
                title = S("insights", "Analyse"),
                subtitle = S("erkenntnisse_aus_deinen_daten", "Erkenntnisse aus deinen Daten"),
                onBack = onBack
            )
        }

        item {
            AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(appColors().cardAlt),
                        contentAlignment = Alignment.Center
                    ) { Text(S("ud83d_udd12", "\uD83D\uDD12"), fontSize = 20.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(S("komplett_offline", "Komplett offline"), style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                        Text(
                            S("alle_auswertungen_laufen_lokal_auf_dei", "Alle Auswertungen laufen lokal auf deinem Gerät. Es werden keine Daten übertragen."),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }
        }

        if (einblicke.isNotEmpty()) {
            item { SectionHeader(title = S("insights_new", "Auswertung deiner Buchungen")) }
            items(einblicke) { einblick ->
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(einblick.emoji, modifier = Modifier.padding(end = 10.dp))
                            Text(
                                einblick.titel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            einblick.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                        val zugehoerig = einblick.ausgabeId?.let { id ->
                            ausgaben.firstOrNull { it.id == id }
                        }
                        if (zugehoerig != null) {
                            Spacer(Modifier.height(10.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.openExpenseSheet(zugehoerig) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(S("insights_open", "Buchung öffnen"))
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    S(
                        "insights_new_note",
                        "Diese Auswertung zeigt nur Fakten aus deinen erfassten Buchungen (Betrag %1\$s als Symbol-Währung). Ob du etwas daraus folgern möchtest, entscheidest du."
                    ).let { it.replace("%1\$s", symbol) },
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        if (insights.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(appColors().cardAlt),
                        contentAlignment = Alignment.Center
                    ) { Text(S("ud83d_udca1", "\uD83D\uDCA1"), fontSize = 34.sp) }
                    Spacer(Modifier.height(16.dp))
                    Text(S("noch_nicht_genug_daten", "Noch nicht genug Daten"), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        S("sobald_ein_paar_ausgaben_erfasst_sind", "Sobald ein paar Ausgaben erfasst sind, erkennt die App Muster, Auffälligkeiten und Spartipps."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors().textMuted,
                        textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            item { SectionHeader(title = S("erkenntnisse_2", "Erkenntnisse")) }
            items(insights) { insight -> InsightCard(insight = insight) }
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    S("verglichen_werden_der_laufende_monat_m", "Verglichen werden der laufende Monat mit dem Vormonat sowie dem Durchschnitt der letzten sechs Monate."),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun InsightCard(insight: SmartInsight) {
    val color: Color = when (insight.severity) {
        InsightSeverity.GOOD -> appColors().income
        InsightSeverity.WARNING -> appColors().warning
        InsightSeverity.INFO -> MaterialTheme.colorScheme.primary
    }
    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Text(insight.emoji, fontSize = 19.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    S(insight.titleKey, insight.title, *insight.titleArgs.toTypedArray()),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    S(insight.messageKey, insight.message, *insight.messageArgs.toTypedArray()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.S
import androidx.compose.ui.text.style.TextOverflow

/**
 * "Coming soon" page for the bank connection.
 *
 * Shown while the feature is switched off (see BankFeature.LIVE).
 * It deliberately contains no configuration, no sandbox bank and no
 * unfinished backend settings.
 */
@Composable
fun BankComingSoonScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("bank_connect", "Bankkonto verbinden"),
            subtitle = S("open_banking_psd2", "Open Banking · PSD2"),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp, top = 6.dp)
        ) {
            // ------------------------------ Kopf -----------------------------
            item {
                AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(S("ud83c_udfe6", "\uD83C\uDFE6"), fontSize = 30.sp)
                        }

                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                S("bank_cs_badge", "Bald verfügbar"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            S("bank_connect", "Bankkonto verbinden"),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            S("bank_cs_title", "Automatische Finanzübersicht kommt bald."),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            S("bank_cs_body", "Verbinde dein Bankkonto und lasse Finovo später automatisch Einnahmen, Ausgaben und Kategorien erkennen."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted,
                            textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ---------------------------- Vorteile ---------------------------
            item { GroupTitle(S("bank_cs_features", "Das kommt auf dich zu")) }
            item {
                Group {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        FeaturePoint(S("bank_cs_f1", "Automatische Transaktionen"))
                        FeaturePoint(S("bank_cs_f2", "Einnahmen & Ausgaben erkennen"))
                        FeaturePoint(S("bank_cs_f3", "Kategorien automatisch zuordnen"))
                        FeaturePoint(S("bank_cs_f4", "Wiederkehrende Zahlungen erkennen"))
                        FeaturePoint(S("bank_cs_f5", "Sichere Bankverbindung"))
                    }
                }
            }

            // --------------------------- Hinweis -----------------------------
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        S("bank_cs_offline", "Bis dahin bleibt Finovo vollständig offline: Alle Daten liegen verschlüsselt auf deinem Gerät – keine Cloud, kein Konto."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ---------------------------- Button -----------------------------
            item {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = S("bank_cs_button", "Bald verfügbar"),
                    onClick = { /* currently inactive - no bank connection is started */ },
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    S("bank_cs_note", "Wir arbeiten daran. Sobald die Bankanbindung freigegeben ist, erscheint sie automatisch an dieser Stelle."),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FeaturePoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(appColors().income.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = appColors().income,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
        )
    }
}

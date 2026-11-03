package com.finovo.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.bank.BankInstitutions
import com.finovo.app.data.BankAccountEntity
import com.finovo.app.data.BankConnectionEntity
import com.finovo.app.data.BankTransactionEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.DropdownField
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel
import androidx.compose.ui.text.style.TextOverflow
import com.finovo.app.util.str

/** "Bankkonto verbinden" - PSD2 / Open Banking, fully integrated into Finovo. */
@Composable
fun BankScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val connections by viewModel.bankConnections.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()
    val transactions by viewModel.bankTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val rules by viewModel.bankRules.collectAsState()

    var selectedBank by remember { mutableStateOf(BankInstitutions.popular.first()) }
    var showBackendDialog by remember { mutableStateOf(false) }
    var pendingDisconnect by remember { mutableStateOf<BankConnectionEntity?>(null) }
    var categoryTarget by remember { mutableStateOf<BankTransactionEntity?>(null) }

    val openUrl: (String) -> Unit = { url ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            context.startActivity(intent)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("bankkonto", "Bankkonto"),
            subtitle = S("psd2_open_banking_verschluesselt", "PSD2 · Open Banking · verschlüsselt"),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp, top = 4.dp)
        ) {
            // ------------------------- Meldung -------------------------------
            val notice = viewModel.bankNotice
            if (!notice.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (notice == com.finovo.app.bank.BankApiClient.NO_CONNECTION)
                                    appColors().warning.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.secondaryContainer
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            notice,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { viewModel.clearBankNotice() }) {
                            Text("OK", style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ------------------------- Hinweis / Sicherheit -------------------
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        S("finovo_speichert_keine_bank_zugangsdat", "Finovo speichert keine Bank-Zugangsdaten. Die Anmeldung läuft über den ") +
                            S("bank_provider_hint", "Open-Banking-Anbieter deines Backends; Finovo erhält nur Kontoname, ") +
                            S("kontostand_und_umsaetze_ohne_inter", "Kontostand und Umsätze. Ohne Internet funktioniert Finovo ganz normal weiter."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // ---------------------------- Verbinden ---------------------------
            if (connections.isEmpty()) {
                item {
                    GroupTitle(S("bank_auswaehlen", "Bank auswählen"))
                    Group {
                        Column(Modifier.padding(16.dp)) {
                            DropdownField(
                                label = S("bank", "Bank"),
                                options = BankInstitutions.popular,
                                selected = selectedBank,
                                optionLabel = { it.name },
                                onSelect = { selectedBank = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = S("ud83c_udfe6_bankkonto_verbinden", "\uD83C\uDFE6 Bankkonto verbinden"),
                                onClick = {
                                    viewModel.startBankConnection(
                                        institutionId = selectedBank.id,
                                        redirectUri = "finovo://bank/callback",
                                        onAuthUrl = openUrl
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !viewModel.bankBusy
                            )
                            if (viewModel.bankReference != null) {
                                Spacer(Modifier.height(10.dp))
                                SecondaryButton(
                                    text = S("verbindung_abschliessen", "Verbindung abschließen"),
                                    onClick = { viewModel.completeBankConnection() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    S("nach_der_freigabe_bei_deiner_bank_komm", "Nach der Freigabe bei deiner Bank kommst du hierher zurück und schließt die Verbindung ab."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { showBackendDialog = true },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(S("backend_zugangstoken_aendern", "Backend & Zugangstoken ändern"))
                    }
                }
            }

            // -------------------------- Verbindungen --------------------------
            items(connections, key = { "c${it.id}" }) { connection ->
                ConnectionCard(
                    connection = connection,
                    accounts = accounts.filter { it.connectionId == connection.id },
                    symbol = symbol,
                    busy = viewModel.bankBusy,
                    onSync = { viewModel.syncBank(connection.id) },
                    onAutoSyncChange = { viewModel.setBankAutoSync(connection.id, it) },
                    onDisconnect = { pendingDisconnect = connection }
                )
            }

            // ------------------------- Umsätze -------------------------------
            if (transactions.isNotEmpty()) {
                item {
                    GroupTitle(S("importierte_umsaetze_transactions_size", "Importierte Umsätze (%1\$s)", transactions.size))
                }
                items(transactions.take(60), key = { "t${it.id}" }) { tx ->
                    TransactionRow(
                        tx = tx,
                        symbol = symbol,
                        categoryName = categoryLabel(categories.firstOrNull { it.id == tx.categoryId }?.name ?: ""),
                        onClick = { categoryTarget = tx }
                    )
                }
            } else if (connections.isNotEmpty()) {
                item {
                    EmptyState(
                        emoji = "\uD83C\uDFE6",
                        title = S("noch_keine_umsaetze_importiert", "Noch keine Umsätze importiert."),
                        message = S("tippe_auf_jetzt_synchronisieren_um", "Tippe auf „Jetzt synchronisieren“, um die letzten Umsätze zu laden."),
                        actionLabel = S("jetzt_synchronisieren", "Jetzt synchronisieren"),
                        onAction = { connections.firstOrNull()?.let { viewModel.syncBank(it.id) } }
                    )
                }
            }

            // --------------------------- Regeln ------------------------------
            if (rules.isNotEmpty()) {
                item {
                    GroupTitle(S("gelernte_zuordnungen", "Gelernte Zuordnungen"))
                    Group {
                        Column(Modifier.padding(16.dp)) {
                            rules.take(8).forEach { rule ->
                                val name = categoryLabel(categories.firstOrNull { it.id == rule.categoryId }?.name ?: "–")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        rule.token,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Text(
                                        S("name_rule_hits", "%2\$s · %1\$s×", rule.hits, name),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appColors().textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    S("finovo_funktioniert_auch_ohne_bankverb", "Finovo funktioniert auch ohne Bankverbindung vollständig offline – ") +
                        S("die_synchronisierung_ist_ein_freiw", "die Synchronisierung ist ein freiwilliges Extra."),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // ------------------------------- Dialoge --------------------------------
    if (showBackendDialog) {
        var url by remember { mutableStateOf(viewModel.backendUrl()) }
        var token by remember { mutableStateOf(viewModel.backendToken()) }
        AlertDialog(
            onDismissRequest = { showBackendDialog = false },
            title = { Text(S("backend_zugangstoken", "Backend & Zugangstoken")) },
            text = {
                Column {
                    Text(
                        S("adresse_deines_eigenen_finovo_backends", "Adresse deines eigenen Finovo-Backends (HTTPS) und das Zugangstoken. ") +
                            S("beides_wird_verschluesselt_im_andr", "Beides wird verschlüsselt im Android-Keystore abgelegt – API-Schlüssel ") +
                            S("liegen_ausschliesslich_im_backend_", "liegen ausschließlich im Backend, niemals in der App."),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(S("backend_adresse", "Backend-Adresse"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis) },
                        placeholder = { Text("https://bank.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text(S("zugangstoken_finovo_app_token", "Zugangstoken (FINOVO_APP_TOKEN)"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveBackendUrl(url)
                    if (token.isNotBlank()) viewModel.saveBackendToken(token)
                    showBackendDialog = false
                }) { Text(S("speichern_9", "Speichern")) }
            },
            dismissButton = {
                TextButton(onClick = { showBackendDialog = false }) { Text(S("abbrechen_16", "Abbrechen")) }
            }
        )
    }

    pendingDisconnect?.let { connection ->
        ConfirmDialog(
            visible = true,
            title = S("bankverbindung_trennen", "Bankverbindung trennen?"),
            message = S("bank_disconnect_confirm", "Die Verbindung zu „%1\$s“ wird gelöscht. Bereits importierte Umsätze bleiben lokal in Finovo gespeichert.", connection.bankName),
            confirmLabel = "Trennen",
            onConfirm = {
                viewModel.disconnectBank(connection.id)
                pendingDisconnect = null
            },
            onDismiss = { pendingDisconnect = null }
        )
    }

    categoryTarget?.let { tx ->
        CategoryChoiceDialog(
            categories = categories,
            onDismiss = { categoryTarget = null },
            onPick = { category ->
                viewModel.applyBankCategory(
                    transactionId = tx.id,
                    categoryId = category.id,
                    isIncome = tx.isIncome,
                    name = categoryLabel(category.name)
                )
                categoryTarget = null
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Bausteine
// ---------------------------------------------------------------------------

@Composable
private fun ConnectionCard(
    connection: BankConnectionEntity,
    accounts: List<BankAccountEntity>,
    symbol: String,
    busy: Boolean,
    onSync: () -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onDisconnect: () -> Unit
) {
    Column {
        GroupTitle(connection.bankName.ifBlank { str("fallback_bank_account", "Bankkonto") })
        Group {
            Column(Modifier.padding(16.dp)) {
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                            Text(
                                if (account.iban.isBlank()) "Konto" else maskIban(account.iban),
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                        AmountText(
                            cents = account.balanceCents,
                            symbol = symbol,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (account.balanceCents >= 0) appColors().income else appColors().expense
                        )
                    }
                    if (account !== accounts.last()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(appColors().divider)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    S("letzte_synchronisierung_lastsynctext_c", "Letzte Synchronisierung: %1\$s", lastSyncText(connection.lastSyncAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )

                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (busy) S("bank_syncing", "Synchronisiere …") else S("bank_sync_now", "Jetzt synchronisieren"),
                    onClick = onSync,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = S("automatisch_synchronisieren", "Automatisch synchronisieren"),
                    subtitle = S("alle_6_stunden_nur_mit_internetverbind", "Alle 6 Stunden, nur mit Internetverbindung"),
                    checked = connection.autoSync,
                    onCheckedChange = onAutoSyncChange
                )

                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(appColors().expenseContainer)
                        .clickable(onClick = onDisconnect)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = appColors().expense)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        S("verbindung_trennen", "Verbindung trennen"),
                        style = MaterialTheme.typography.titleSmall,
                        color = appColors().expense,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    tx: BankTransactionEntity,
    symbol: String,
    categoryName: String?,
    onClick: () -> Unit
) {
    val uncategorised = tx.categoryId == null || tx.confidence < com.finovo.app.bank.TransactionCategorizer.UNCERTAIN
    val review = !uncategorised && tx.confidence < com.finovo.app.bank.TransactionCategorizer.CONFIDENT

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(appColors().cardAlt),
            contentAlignment = Alignment.Center
        ) {
            Text(com.finovo.app.bank.BankSyncEngine.emojiFor(categoryName ?: ""), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.merchant.ifBlank { tx.description.ifBlank { str("fallback_transaction", "Umsatz") } },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                Dates.full(Dates.fromEpochDay(tx.bookingDate)),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    uncategorised -> Chip(S("nicht_kategorisiert", "Nicht kategorisiert"), appColors().warning)
                    review -> Chip(S("kategorie_pruefen", "Kategorie prüfen"), appColors().warning)
                    else -> Chip(categoryLabel(categoryName ?: "Sonstiges"), appColors().income)
                }
                if (tx.recurringCandidate) {
                    Spacer(Modifier.width(6.dp))
                    Chip("wiederkehrend", MaterialTheme.colorScheme.primary)
                }
            }
            if (uncategorised || review) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onClick,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                ) {
                    Text(
                        S("kategorie_auswaehlen", "Kategorie auswählen"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        AmountText(
            cents = tx.amountCents,
            symbol = symbol,
            style = MaterialTheme.typography.titleSmall,
            color = if (tx.amountCents >= 0) appColors().income else appColors().expense
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(appColors().divider)
    )
}

@Composable
private fun Chip(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CategoryChoiceDialog(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onPick: (CategoryEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(S("kategorie_auswaehlen_2", "Kategorie auswählen")) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(categories, key = { "cat${it.id}" }) { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(category) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category.emoji, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(12.dp))
                        Text(categoryLabel(category.name), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(S("abbrechen_17", "Abbrechen")) } }
    )
}

private fun maskIban(iban: String): String {
    val clean = iban.replace(" ", "")
    if (clean.length < 8) return iban
    return "${clean.take(4)} •••• •••• ${clean.takeLast(4)}"
}

private fun lastSyncText(millis: Long): String =
    if (millis <= 0L) str("never", "noch nie") else Dates.full(Dates.millisToLocalDate(millis))

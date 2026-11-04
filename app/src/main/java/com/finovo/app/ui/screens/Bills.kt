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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.finovo.app.scan.InvoiceScanner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillPaymentEntity
import com.finovo.app.data.CategoryEntity
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.CategoryBadge
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.IconActionButton
import com.finovo.app.ui.components.Pill
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.Stats
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.S
import com.finovo.app.util.str

@Composable
fun BillsScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val categories by viewModel.categories.collectAsState()
    val overdue by viewModel.overdueBills.collectAsState()
    val dueSoon by viewModel.dueSoonBills.collectAsState()
    val payments by viewModel.paidPayments.collectAsState()
    val today = LocalDate.now()

    var pendingDelete by remember { mutableStateOf<BillEntity?>(null) }


    var billFilter by remember { mutableStateOf(BillFilter.ALL) }

    // --------------------------- Rechnung scannen ---------------------------
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = cameraUri
            if (uri != null) viewModel.scanInvoice(uri)
            else viewModel.message(str("aufnahme_konnte_nicht_verarbeitet_", "Aufnahme konnte nicht verarbeitet werden."))
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.scanInvoice(uri)
    }
    var pendingPaymentDelete by remember { mutableStateOf<BillPaymentEntity?>(null) }

    val outstanding = remember(overdue, dueSoon) {
        (overdue + dueSoon).sumOf { it.amountCents }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 8.dp)
        ) {
            Text(S("meine_rechnungen", "Meine Rechnungen"), style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    S("offen", "Offen: "),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
                AmountText(
                    cents = outstanding,
                    symbol = symbol,
                    style = MaterialTheme.typography.titleSmall,
                    color = appColors().expense
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    S("ueberfaellig_overdue_size", "Überfällig: %1\$s", overdue.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue.isEmpty()) appColors().textMuted else appColors().expense
                )
            }
        }

        com.finovo.app.ui.components.PrimaryButton(
            text = S("new_bill", "+ Neue Rechnung"),
            onClick = { viewModel.openBillSheet(null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        com.finovo.app.ui.screens.BillFilterRow(
            filter = billFilter,
            onFilter = { billFilter = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SecondaryButton(
                text = S("ud83d_udcf7", "\uD83D\uDCF7 ") + S("scan_invoice", "Rechnung scannen"),
                onClick = {
                    val pair = InvoiceScanner.createCameraImage(context)
                    if (pair == null) {
                        viewModel.message(str("kamera_konnte_nicht_geoeffnet_werd", "Kamera konnte nicht geöffnet werden."))
                    } else {
                        cameraUri = pair.first
                        cameraLauncher.launch(pair.first)
                    }
                },
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = S("ud83d_uddbc_ufe0f", "\uD83D\uDDBC\uFE0F ") + S("pick_image", "Bild auswählen"),
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f)
            )
        }

        if (viewModel.scanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                S("scanning_hint", "Text wird auf dem Gerät erkannt …"),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp, top = 4.dp)
        ) {
            if (billFilter != BillFilter.PAID && overdue.isNotEmpty()) {
                item(key = "sec_overdue") {
                    SectionTitle(
                        title = S("overdue", "Überfällig"),
                        emoji = "🔴",
                        count = overdue.size
                    )
                }
                items(overdue, key = { "b${it.id}" }) { bill ->
                    BillListRow(
                        bill = bill,
                        category = categories.firstOrNull { it.id == bill.categoryId },
                        symbol = symbol,
                        today = today,
                        onOpen = { viewModel.openBillSheet(bill) },
                        onMarkPaid = { viewModel.markBillPaid(bill, settings.autoExpenseOnBillPaid) },
                        onDelete = { pendingDelete = bill }
                    )
                }
            }

            if (billFilter != BillFilter.PAID && dueSoon.isNotEmpty()) {
                item(key = "sec_soon") {
                    SectionTitle(
                        title = S("due_soon", "Demnächst fällig"),
                        emoji = "🟠",
                        count = dueSoon.size
                    )
                }
                items(dueSoon, key = { "b${it.id}" }) { bill ->
                    BillListRow(
                        bill = bill,
                        category = categories.firstOrNull { it.id == bill.categoryId },
                        symbol = symbol,
                        today = today,
                        onOpen = { viewModel.openBillSheet(bill) },
                        onMarkPaid = { viewModel.markBillPaid(bill, settings.autoExpenseOnBillPaid) },
                        onDelete = { pendingDelete = bill }
                    )
                }
            }

            if (billFilter != BillFilter.OPEN) {
            item(key = "sec_paid") {
                    SectionTitle(title = S("bill_paid", "Bezahlt"), emoji = "🟢", count = payments.size)
                }
            }

            if (payments.isEmpty()) {
                item {
                    Text(
                        S("no_payments", "Noch keine Zahlungen erfasst."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors().textMuted,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                    )
                }
            } else {
                items(payments.take(30), key = { "p${it.id}" }) { payment ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                        com.finovo.app.ui.components.PaymentRow(
                            payment = payment,
                            symbol = symbol,
                            onDelete = { pendingPaymentDelete = payment }
                        )
                    }
                }
            }

            if (overdue.isEmpty() && dueSoon.isEmpty() && payments.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "🧾",
                        title = S("no_open_bills", "Keine offenen Rechnungen"),
                        message = S("alles_bezahlt_neue_rechnungen_kann", "Alles bezahlt. Neue Rechnungen kannst du unten über das + hinzufügen."),
                        actionLabel = S("rechnung_hinzufuegen", "+ Rechnung hinzufügen"),
                        onAction = { viewModel.openBillSheet(null) }
                    )
                }
            }
        }
    }

    ConfirmDialog(
        visible = pendingDelete != null,
        title = S("rechnung_loeschen", "Rechnung löschen?"),
        message = pendingDelete?.let {
            str("confirm_delete_bill", "„%1\$s“ (%2\$s) wirklich löschen?", it.name, Money.format(it.amountCents, symbol))
        } ?: "",
        confirmLabel = S("loeschen_7", "Löschen"),
        onConfirm = {
            pendingDelete?.let { viewModel.deleteBill(it) }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null }
    )

    ConfirmDialog(
        visible = pendingPaymentDelete != null,
        title = S("zahlung_entfernen", "Zahlung entfernen?"),
        message = S("der_zahlungseintrag_wird_geloescht", "Der Zahlungseintrag wird gelöscht. Eine automatisch erstellte Ausgabe wird ebenfalls entfernt."),
        confirmLabel = str("entfernen", "Entfernen"),
        onConfirm = {
            pendingPaymentDelete?.let { viewModel.deletePayment(it) }
            pendingPaymentDelete = null
        },
        onDismiss = { pendingPaymentDelete = null }
    )
}

@Composable
private fun SectionTitle(title: String, emoji: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text(
            S("count", "%1\$s", count),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted
        )
    }
}

@Composable
private fun BillListRow(
    bill: BillEntity,
    category: CategoryEntity?,
    symbol: String,
    today: LocalDate,
    onOpen: () -> Unit,
    onMarkPaid: () -> Unit,
    onDelete: () -> Unit
) {
    val days = bill.dueDate - today.toEpochDay()
    val overdue = bill.status != "PAID" && days < 0
    val (statusText, statusColor, statusBg) = when {
        bill.status == "PAID" -> Triple(str("bill_paid", "Bezahlt"), appColors().income, appColors().incomeContainer)
        overdue -> Triple(str("due_overdue", "Überfällig"), appColors().expense, appColors().expenseContainer)
        days == 0L -> Triple(str("due_today", "Heute fällig"), appColors().warning, appColors().warningContainer)
        days == 1L -> Triple(str("due_tomorrow", "Morgen fällig"), appColors().warning, appColors().warningContainer)
        else -> Triple(plurals("pl_in_days", days.toInt(), "in %1\$d Tag", "in %1\$d Tagen"), appColors().textMuted, appColors().cardAlt)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .premiumSurface(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Dates.short(Dates.fromEpochDay(bill.dueDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
                Spacer(Modifier.width(6.dp))
                Pill(statusText, statusColor, statusBg)
                if (bill.isRecurring) {
                    Spacer(Modifier.width(6.dp))
                    Pill(
                        "↻ " + Stats.nextRunLabel(
                            com.finovo.app.data.RecurringRuleEntity(
                                frequency = bill.frequency,
                                interval = bill.interval,
                                customDays = bill.customDays,
                                dayOfMonth = Dates.fromEpochDay(bill.dueDate).dayOfMonth,
                                monthOfYear = Dates.fromEpochDay(bill.dueDate).monthValue,
                                startDate = bill.dueDate
                            ),
                            today
                        ),
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                cents = bill.amountCents,
                symbol = symbol,
                style = MaterialTheme.typography.titleSmall
            )
            Row {
                if (bill.status != "PAID") {
                    IconActionButton(
                        icon = Icons.Default.Check,
                        description = S("als_bezahlt_markieren", "Als bezahlt markieren"),
                        onClick = onMarkPaid,
                        tint = appColors().income
                    )
                }
                IconActionButton(Icons.Default.Delete, S("loeschen_8", "Löschen"), onDelete, tint = appColors().textMuted)
            }
        }
    }
}


enum class BillFilter(val label: String) {
    OPEN("Offen"), PAID("Bezahlt"), ALL("Alle");
    val key: String get() = when (this) {
        OPEN -> "bill_open"
        PAID -> "bill_paid"
        ALL -> "bill_all"
    }
}

@Composable
internal fun BillFilterRow(
    filter: BillFilter,
    onFilter: (BillFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors().cardAlt)
            .padding(4.dp)
    ) {
        BillFilter.values().forEach { option ->
            val selected = option == filter
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onFilter(option) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    S(option.key, option.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else appColors().textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

package com.finovo.app.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.data.ExpenseEntity
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import java.io.File
import com.finovo.app.util.S
import com.finovo.app.util.categoryLabel
import com.finovo.app.util.str

@Composable
fun ReceiptsScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val receipts by viewModel.receipts.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var query by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var viewing by remember { mutableStateOf<ExpenseEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingUri = uri
    }

    val catName = { id: Long -> categoryLabel(categories.firstOrNull { it.id == id }?.name ?: "Sonstiges") }
    val catEmoji = { id: Long -> categories.firstOrNull { it.id == id }?.emoji ?: "" }

    val filtered = receipts.filter { e ->
        val q = query.trim().lowercase()
        if (q.length < 2) true
        else {
            val hay = "${e.note} ${catName(e.categoryId)} ${Money.format(e.amountCents, symbol)} ${Dates.full(Dates.fromEpochDay(e.date))}".lowercase()
            q in hay
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item {
            ScreenTopBar( tutorialId = "receipts", title = S("receipts", "Belege"), subtitle = S("receipts_sub", "Fotos zu deinen Buchungen"), onBack = onBack)
        }

        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                BelegScannerEinstieg(viewModel)
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(S("haendler_betrag_kategorie_datum_notiz", "Händler, Betrag, Kategorie, Datum, Notiz …")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = S("leeren_2", "Leeren"))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }

        if (receipts.isEmpty()) {
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
                    ) { Text(S("ud83e_uddfe_2", "\uD83E\uDDFE"), fontSize = 34.sp) }
                    Spacer(Modifier.height(16.dp))
                    Text(S("noch_keine_belege", "Noch keine Belege"), style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        S("fotografiere_oder_waehle_einen_beleg_u", "Fotografiere oder wähle einen Beleg und ordne ihn einer Buchung zu. Die Bilder bleiben ausschließlich auf diesem Gerät."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors().textMuted,
                        textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item {
                PrimaryButton(
                    text = S("beleg_hinzufuegen", "Beleg hinzufügen"),
                    onClick = { picker.launch("image/*") },
                    icon = Icons.Default.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            item {
                SectionHeader(
                    title = S("filtered_size_belege", "%1\$s Belege", filtered.size),
                    actionLabel = S("hinzufuegen_2", "Hinzufügen"),
                    onAction = { picker.launch("image/*") }
                )
            }
            items(filtered, key = { it.id }) { expense ->
                ReceiptRow(
                    expense = expense,
                    symbol = symbol,
                    title = expense.note.ifBlank { catName(expense.categoryId) },
                    subtitle = S("catemoji_expense_categoryid_catname_ex", "%1\$s %2\$s · %3\$s", catEmoji(expense.categoryId), catName(expense.categoryId), Dates.full(Dates.fromEpochDay(expense.date))),
                    onClick = { viewing = expense }
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = S("beleg_hinzufuegen_2", "Beleg hinzufügen"),
                    onClick = { picker.launch("image/*") },
                    icon = Icons.Default.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Bild gewählt -> Buchung auswählen
    if (pendingUri != null) {
        val options = viewModel.expensesWithoutReceipts()
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text(S("beleg_zuordnen", "Beleg zuordnen")) },
            text = {
                if (options.isEmpty()) {
                    Text(S("es_gibt_noch_keine_buchung_ohne_beleg", "Es gibt noch keine Buchung ohne Beleg. Erfasse zuerst eine Ausgabe."))
                } else {
                    Column {
                        Text(
                            S("waehle_die_passende_buchung", "Wähle die passende Buchung"),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        options.take(8).forEach { e ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.attachReceipt(e.id, pendingUri!!)
                                        pendingUri = null
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        e.note.ifBlank { catName(e.categoryId) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        S("dates_full_dates_fromepochday_e_date_c", "%1\$s · %2\$s", Dates.full(Dates.fromEpochDay(e.date)), catName(e.categoryId)),
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
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text(S("abbrechen_11", "Abbrechen")) } }
        )
    }

    // Beleg ansehen
    viewing?.let { expense ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(expense.note.ifBlank { str("fallback_receipt", "Beleg") }) },
            text = {
                Column {
                    val bitmap = remember(expense.receiptUri) {
                        runCatching {
                            BitmapFactory.decodeFile(expense.receiptUri)?.asImageBitmap()
                        }.getOrNull()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = S("beleg", "Beleg"),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(appColors().cardAlt)
                        )
                    } else {
                        Text(S("beleg_konnte_nicht_geladen_werden", "Beleg konnte nicht geladen werden."))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        S("money_format_expense_amountcents_symbo", "%1\$s · %2\$s · %3\$s", Money.format(expense.amountCents, symbol), catName(expense.categoryId), Dates.full(Dates.fromEpochDay(expense.date))),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                    if (expense.note.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(expense.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeReceipt(expense)
                    viewing = null
                }) { Text(S("beleg_entfernen", "Beleg entfernen"), color = appColors().expense) }
            },
            dismissButton = { TextButton(onClick = { viewing = null }) { Text(S("schliessen", "Schließen")) } }
        )
    }
}

@Composable
private fun ReceiptRow(
    expense: ExpenseEntity,
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val bitmap = remember(expense.receiptUri) {
        runCatching {
            val file = File(expense.receiptUri)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        }.getOrNull()
    }
    AppCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = S("beleg_2", "Beleg"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(appColors().cardAlt),
                    contentAlignment = Alignment.Center
                ) { Text(S("ud83e_uddfe_3", "\uD83E\uDDFE"), fontSize = 22.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                Money.format(-expense.amountCents, symbol),
                style = MaterialTheme.typography.titleSmall,
                color = appColors().expense,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
    }
}

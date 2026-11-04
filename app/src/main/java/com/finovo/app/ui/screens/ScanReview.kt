package com.finovo.app.ui.screens

import com.finovo.app.ui.components.scanCorners
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.data.BillEntity
import com.finovo.app.data.BillStatus
import com.finovo.app.data.CategoryEntity
import com.finovo.app.data.CategoryType
import com.finovo.app.data.Frequency
import com.finovo.app.scan.InvoiceParser
import com.finovo.app.scan.ScannedInvoice
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.DropdownField
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import java.time.LocalDate
import com.finovo.app.util.S
import com.finovo.app.util.str
import com.finovo.app.util.categoryLabel

/**
 * Prüfbildschirm nach dem Scan.
 *
 * Nichts wird automatisch gespeichert:
 *   - jedes Feld zeigt seine Sicherheit (z. B. "98 %")
 *   - unsichere Felder sind mit "Bitte prüfen" markiert
 *   - über die Lupe springt das Bild zur Fundstelle des Wertes, damit der
 *     Benutzer sofort sieht, warum Finovo diesen Wert erkannt hat
 *   - "Bearbeiten" schaltet alle Felder frei, "Speichern" übernimmt sie
 */
@Composable
fun ScanReviewScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    if (!viewModel.hasPendingScan) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    S("no_scan", "Kein Scan vorhanden."),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = S("back", "Zurück"), onClick = onBack)
            }
        }
        return
    }

    val result = viewModel.scanResult
    val imagePath = viewModel.scanImagePath
    val settings by viewModel.settings.collectAsState()
    val symbol = settings.currencySymbol.ifBlank { "€" }
    val allCategories by viewModel.categories.collectAsState()
    val categories = remember(allCategories) {
        allCategories.filter { it.type == CategoryType.EXPENSE.name }
    }
    val today = LocalDate.now()

    var editMode by remember { mutableStateOf(false) }
    var markedField by remember { mutableStateOf<String?>(null) }

    var vendor by remember { mutableStateOf(result.vendor.orEmpty()) }
    var invoiceNumber by remember { mutableStateOf(result.invoiceNumber.orEmpty()) }
    var customerNumber by remember { mutableStateOf(result.customerNumber.orEmpty()) }
    var invoiceDate by remember { mutableStateOf(result.invoiceDate ?: today) }
    var hasDueDate by remember { mutableStateOf(result.dueDate != null) }
    var dueDate by remember { mutableStateOf(result.dueDate ?: result.invoiceDate ?: today) }
    var amount by remember {
        mutableStateOf(result.amountCents?.let { Money.format(it, "") } ?: "")
    }
    var iban by remember { mutableStateOf(result.iban.orEmpty()) }
    var bic by remember { mutableStateOf(result.bic.orEmpty()) }
    var description by remember { mutableStateOf(result.description.orEmpty()) }
    var recurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(Frequency.MONTHLY) }

    val invalidAmountText = S("invalid_amount", "Bitte einen gültigen Betrag eingeben")
    val missingDueText = S("scan_missing_due", "Bitte ein Fälligkeitsdatum angeben.")
    val intervalLabels = mapOf(
        Frequency.MONTHLY to S("monthly", "Monatlich"),
        Frequency.YEARLY to S("yearly", "Jährlich"),
        Frequency.WEEKLY to S("weekly", "Wöchentlich")
    )
    var amountError by remember { mutableStateOf<String?>(null) }

    val startCategory = remember(categories, result) {
        categories.firstOrNull { it.name.equals(result.categoryName, ignoreCase = true) }
            ?: categories.firstOrNull { it.name.equals("Sonstiges", ignoreCase = true) }
            ?: categories.firstOrNull()
    }
    var category by remember { mutableStateOf(startCategory) }

    val uncertain = remember(result) { result.uncertainFields() }
    val critical = remember(result) { result.missingCritical() }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("scan_review_title", "Rechnung prüfen"),
            subtitle = S("scan_review_sub", "Erkannte Daten kontrollieren"),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 30.dp, top = 4.dp)
        ) {
            // ------------------------------ Hinweis --------------------------
            if (uncertain.isNotEmpty() || critical.isNotEmpty()) {
                item {
                    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = appColors().warning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                S(
                                    "scan_uncertain_banner",
                                    "Einige Rechnungsdaten konnten nicht eindeutig erkannt werden. Bitte überprüfe die markierten Felder."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = appColors().warning
                            )
                        }
                    }
                }
            }

            // ------------------------------ Bild -----------------------------
            if (!imagePath.isNullOrBlank()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        InvoiceImage(
                            path = imagePath,
                            markedField = markedField,
                            result = result
                        )
                    }
                }
            }

            // ---------------------------- Rechnung ---------------------------
            item { GroupTitle(S("invoice_group", "Rechnung")) }
            item {
                Group {
                    Column(Modifier.padding(vertical = 10.dp)) {
                        FieldRow(
                            label = S("vendor", "Firma / Händler"),
                            value = vendor,
                            field = ScannedInvoice.F_VENDOR,
                            result = result,
                            editMode = editMode,
                            onMark = { markedField = it }
                        ) {
                            EditableValue(
                                value = vendor,
                                onValueChange = { vendor = it },
                                label = S("vendor", "Firma / Händler"),
                                editMode = editMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        FieldRow(
                            label = S("invoice_number", "Rechnungsnummer"),
                            value = invoiceNumber,
                            field = ScannedInvoice.F_NUMBER,
                            result = result,
                            editMode = editMode,
                            onMark = { markedField = it }
                        ) {
                            EditableValue(
                                value = invoiceNumber,
                                onValueChange = { invoiceNumber = it },
                                label = S("invoice_number", "Rechnungsnummer"),
                                editMode = editMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        FieldRow(
                            label = S("customer_number", "Kundennummer"),
                            value = customerNumber,
                            field = ScannedInvoice.F_CUSTOMER,
                            result = result,
                            editMode = editMode,
                            onMark = { markedField = it }
                        ) {
                            EditableValue(
                                value = customerNumber,
                                onValueChange = { customerNumber = it },
                                label = S("customer_number", "Kundennummer") + " " + S("optional", "(optional)"),
                                editMode = editMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        FieldRow(
                            label = S("invoice_date", "Rechnungsdatum"),
                            value = Dates.full(invoiceDate),
                            field = ScannedInvoice.F_DATE,
                            result = result,
                            editMode = editMode,
                            onMark = { markedField = it }
                        ) {
                            DateField(
                                epochDay = invoiceDate.toEpochDay(),
                                onDateChange = { invoiceDate = it },
                                label = S("invoice_date", "Rechnungsdatum"),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Fälligkeitsdatum: leer, wenn nichts Eindeutiges erkannt wurde
                        FieldRow(
                            label = S("faelligkeitsdatum", "Fälligkeitsdatum"),
                            value = if (hasDueDate) Dates.full(dueDate) else "",
                            field = ScannedInvoice.F_DUE,
                            result = result,
                            editMode = editMode,
                            extraNote = result.noteOf(ScannedInvoice.F_DUE)?.let { days ->
                                str("scan_hint_payment_term", "Zahlungsziel: %1\$s Tage erkannt – bitte Datum prüfen.", days)
                            },
                            onMark = { markedField = it }
                        ) {
                            if (hasDueDate) {
                                DateField(
                                    epochDay = dueDate.toEpochDay(),
                                    onDateChange = { dueDate = it },
                                    label = S("faelligkeitsdatum", "Fälligkeitsdatum"),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                SecondaryButton(
                                    text = S("scan_pick_due", "Fälligkeitsdatum wählen"),
                                    onClick = {
                                        hasDueDate = true
                                        dueDate = invoiceDate.plusDays(14)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // --------------------- Betrag & Bankverbindung -------------------
            item { GroupTitle(S("amount_bank_group", "Betrag & Bankverbindung")) }
            item {
                Group {
                    Column(Modifier.padding(vertical = 10.dp)) {
                        FieldRow(
                            label = S("total_amount", "Gesamtbetrag"),
                            value = amount,
                            field = ScannedInvoice.F_AMOUNT,
                            result = result,
                            editMode = true,
                            onMark = { markedField = it }
                        ) {
                            AmountField(
                                value = amount,
                                onValueChange = { amount = it; amountError = null },
                                symbol = symbol,
                                error = amountError,
                                label = S("gesamtbetrag", "Gesamtbetrag"),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Netto / MwSt. nur anzeigen, wenn sie erkannt wurden
                        if (result.netCents != null || result.vatCents != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (result.netCents != null) {
                                    Text(
                                        S("net_amount", "Nettobetrag") + ": " +
                                            Money.format(result.netCents ?: 0L, symbol),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appColors().textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (result.vatCents != null) {
                                    Text(
                                        S("vat_amount", "MwSt.") + ": " +
                                            Money.format(result.vatCents ?: 0L, symbol),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appColors().textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        FieldRow(
                            label = "IBAN",
                            value = iban,
                            field = ScannedInvoice.F_IBAN,
                            result = result,
                            editMode = editMode,
                            onMark = { markedField = it }
                        ) {
                            EditableValue(
                                value = iban,
                                onValueChange = { iban = it },
                                label = S("iban", "IBAN") + " " + S("optional", "(optional)"),
                                editMode = editMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        FieldRow(
                            label = "BIC",
                            value = bic,
                            field = ScannedInvoice.F_BIC,
                            result = result,
                            editMode = editMode,
                            onMark = { markedField = it }
                        ) {
                            EditableValue(
                                value = bic,
                                onValueChange = { bic = it },
                                label = S("bic", "BIC") + " " + S("optional", "(optional)"),
                                editMode = editMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ---------------------------- Zuordnung --------------------------
            item { GroupTitle(S("assign_group", "Zuordnung")) }
            item {
                Group {
                    Column(Modifier.padding(vertical = 10.dp)) {
                        FieldRow(
                            label = S("category", "Kategorie"),
                            value = category?.let { "${it.emoji} ${categoryLabel(it.name)}" }.orEmpty(),
                            field = ScannedInvoice.F_CATEGORY,
                            result = result,
                            editMode = true,
                            onMark = { markedField = it }
                        ) {
                            if (categories.isNotEmpty() && category != null) {
                                DropdownField(
                                    label = S("kategorie_7", "Kategorie"),
                                    options = categories,
                                    selected = category!!,
                                    optionLabel = { "${it.emoji} ${categoryLabel(it.name)}" },
                                    onSelect = { category = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        FieldRow(
                            label = S("beschreibung", "Beschreibung"),
                            value = description,
                            field = ScannedInvoice.F_DESCRIPTION,
                            result = result,
                            editMode = editMode,
                            onMark = { markedField = it }
                        ) {
                            EditableValue(
                                value = description,
                                onValueChange = { description = it },
                                label = S("description", "Beschreibung") + " " + S("optional", "(optional)"),
                                editMode = editMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // --------------------------- Wiederkehrend -----------------------
            item { GroupTitle(S("recurring", "Wiederkehrend")) }
            item {
                Group {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        SwitchRow(
                            title = S("recurring_bill", "Wiederkehrende Rechnung"),
                            subtitle = S("recurring_bill_sub", "Wird nach dem Fälligkeitsdatum automatisch erneuert"),
                            checked = recurring,
                            onCheckedChange = { recurring = it },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (recurring) {
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                DropdownField(
                                    label = S("interval", "Intervall"),
                                    options = listOf(Frequency.MONTHLY, Frequency.YEARLY, Frequency.WEEKLY),
                                    selected = frequency,
                                    optionLabel = { intervalLabels[it] ?: it.name },
                                    onSelect = { frequency = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // ----------------------------- Aktionen --------------------------
            item {
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = S("save_invoice", "Rechnung speichern"),
                    onClick = {
                        val cents = Money.parseToCents(amount)
                        if (cents == null || cents <= 0L) {
                            amountError = invalidAmountText
                            return@PrimaryButton
                        }
                        if (!hasDueDate) {
                            viewModel.message(missingDueText)
                            return@PrimaryButton
                        }
                        val bill = BillEntity(
                            name = vendor.ifBlank { str("rechnung_3", "Rechnung") }.trim(),
                            amountCents = cents,
                            dueDate = dueDate.toEpochDay(),
                            categoryId = category?.id ?: 0L,
                            isRecurring = recurring,
                            frequency = frequency.name,
                            interval = 1,
                            reminderEnabled = true,
                            reminderDaysBefore = 3,
                            status = BillStatus.OPEN.name,
                            note = buildNote(
                                description = description,
                                invoiceNumber = invoiceNumber,
                                customerNumber = customerNumber,
                                iban = iban,
                                bic = bic,
                                invoiceDate = invoiceDate
                            ),
                            vendor = vendor.trim(),
                            invoiceNumber = invoiceNumber.trim(),
                            invoiceDate = invoiceDate.toEpochDay(),
                            iban = iban.trim(),
                            bic = bic.trim(),
                            customerNumber = customerNumber.trim(),
                            imagePath = viewModel.scanImagePath.orEmpty(),
                            netCents = result.netCents,
                            vatCents = result.vatCents,
                            ocrText = result.rawText,
                            confidence = encodeConfidence(result),
                            fieldBoxes = encodeBoxes(result)
                        )
                        viewModel.saveScannedBill(bill)
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = if (editMode) S("scan_done", "Fertig") else S("edit", "Bearbeiten"),
                    onClick = { editMode = !editMode },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = S("discard_scan", "Scan verwerfen"),
                    onClick = {
                        viewModel.clearScan()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    S("scan_local_hint", "Der Scan bleibt lokal auf diesem Gerät. Es wird nichts hochgeladen."),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ------------------------------------------------------------------- Bild

/**
 * Zeigt das aufbereitete Rechnungsbild und markiert die Stelle, aus der ein
 * Wert gelesen wurde (z. B. die Zeile mit dem Gesamtbetrag).
 */
@Composable
private fun InvoiceImage(
    path: String,
    markedField: String?,
    result: ScannedInvoice
) {
    val bitmap = remember(path) { BitmapFactory.decodeFile(path) }
    if (bitmap == null) return
    val source = markedField?.let { result.sourceOf(it) }
    val hasBox = source?.hasBox == true

    Column {
        AppCard {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .background(Color(0xFF101418))
                    .scanCorners()
            ) {
                val boxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
                val boxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = S("scan_image_desc", "Aufbereitetes Rechnungsbild"),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                )
                if (hasBox && source != null) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val scale = minOf(
                            boxWidthPx / bitmap.width.toFloat(),
                            boxHeightPx / bitmap.height.toFloat()
                        )
                        val drawnW = bitmap.width * scale
                        val drawnH = bitmap.height * scale
                        val offsetX = (boxWidthPx - drawnW) / 2f
                        val offsetY = (boxHeightPx - drawnH) / 2f
                        val left = offsetX + source.left * scale
                        val top = offsetY + source.top * scale
                        val width = (source.right - source.left) * scale
                        val height = (source.bottom - source.top) * scale
                        drawRect(
                            color = Color(0xFFFFC107).copy(alpha = 0.32f),
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                        drawRect(
                            color = Color(0xFFFFC107),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                    }
                }
            }
        }
        if (markedField != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFFFC107).copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    fieldLabel(markedField) + if (hasBox) "" else " – " +
                        S("scan_no_position", "ohne Bildposition"),
                    style = MaterialTheme.typography.labelSmall,
                    color = appColors().warning,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Felder

@Composable
private fun FieldRow(
    label: String,
    value: String,
    field: String,
    result: ScannedInvoice,
    editMode: Boolean,
    onMark: (String?) -> Unit,
    extraNote: String? = null,
    content: @Composable () -> Unit
) {
    val percent = result.percentOf(field)
    val reliable = result.isConfident(field)
    val marked = false
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = appColors().textMuted,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            if (percent > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (reliable) appColors().income.copy(alpha = 0.15f)
                            else appColors().warning.copy(alpha = 0.16f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "$percent %",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (reliable) appColors().income else appColors().warning,
                        maxLines = 1
                    )
                }
            }
            if (!reliable) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(appColors().warning.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        extraNote ?: S("please_check", "Bitte prüfen"),
                        style = MaterialTheme.typography.labelSmall,
                        color = appColors().warning,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (result.sourceOf(field) != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onMark(field) }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = S("scan_show_source", "Fundstelle anzeigen"),
                        modifier = Modifier.size(16.dp),
                        tint = appColors().textMuted
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
        if (!editMode && value.isBlank() && !reliable) {
            Text(
                S("scan_not_found", "Nicht erkannt – bitte ergänzen."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun fieldLabel(field: String): String = when (field) {
    ScannedInvoice.F_VENDOR -> str("vendor", "Firma / Händler")
    ScannedInvoice.F_NUMBER -> str("invoice_number", "Rechnungsnummer")
    ScannedInvoice.F_DATE -> str("invoice_date", "Rechnungsdatum")
    ScannedInvoice.F_DUE -> str("faelligkeitsdatum", "Fälligkeitsdatum")
    ScannedInvoice.F_AMOUNT -> str("total_amount", "Gesamtbetrag")
    ScannedInvoice.F_NET -> str("net_amount", "Nettobetrag")
    ScannedInvoice.F_VAT -> str("vat_amount", "MwSt.")
    ScannedInvoice.F_IBAN -> "IBAN"
    ScannedInvoice.F_BIC -> "BIC"
    ScannedInvoice.F_CUSTOMER -> str("customer_number", "Kundennummer")
    ScannedInvoice.F_CATEGORY -> str("category", "Kategorie")
    ScannedInvoice.F_DESCRIPTION -> str("beschreibung", "Beschreibung")
    else -> field
}

/** Zeigt einen Wert im Prüfmodus als Text, im Bearbeiten-Modus als Eingabefeld. */
@Composable
private fun EditableValue(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    editMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (editMode) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier) {
            Text(
                value.ifBlank { "–" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (value.isBlank()) appColors().textMuted
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// -------------------------------------------------------------- Speichern

/** Sicherheit je Feld als Text: "vendor=0.9;amount=0.96". */
private fun encodeConfidence(result: ScannedInvoice): String =
    result.confidence.entries.joinToString(";") { "${it.key}=${(it.value * 100).toInt()}" }

/** Fundstellen je Feld als Text: "amount=12,44,300,80". */
private fun encodeBoxes(result: ScannedInvoice): String =
    result.sources.entries.joinToString(";") { entry ->
        val box = entry.value
        "${entry.key}=${box.left.toInt()},${box.top.toInt()},${box.right.toInt()},${box.bottom.toInt()}"
    }

private fun buildNote(
    description: String,
    invoiceNumber: String,
    customerNumber: String,
    iban: String,
    bic: String,
    invoiceDate: LocalDate
): String {
    val parts = ArrayList<String>()
    if (description.isNotBlank()) parts += description.trim()
    if (invoiceNumber.isNotBlank()) {
        parts += str("scan_invoice_no", "Rechnung Nr. %1\$s", invoiceNumber.trim())
    }
    if (customerNumber.isNotBlank()) {
        parts += str("scan_customer_no", "Kd.-Nr. %1\$s", customerNumber.trim())
    }
    parts += str("scan_invoice_date", "Rechnungsdatum: %1\$s", Dates.full(invoiceDate))
    if (iban.isNotBlank()) parts += str("scan_iban", "IBAN %1\$s", iban.trim())
    if (bic.isNotBlank()) parts += str("scan_bic", "BIC %1\$s", bic.trim())
    return parts.joinToString(" · ")
}

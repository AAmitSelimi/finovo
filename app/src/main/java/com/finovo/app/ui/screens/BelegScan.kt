package com.finovo.app.ui.screens

import com.finovo.app.ui.components.scanCorners
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.data.CategoryType
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.DropdownField
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import com.finovo.app.util.S
import com.finovo.app.util.str
import java.time.LocalDate

// ===========================================================================
// Modul 17 - Eigener Beleg-Scanner
//
// Ablauf: Kamera oder Galerie -> Texterkennung auf dem Gerät -> zwingender
// Bestätigungsbildschirm -> erst dann wird etwas gespeichert.
// Es wird nie ein unerkannter Rohwert in ein Feld geschrieben.
// ===========================================================================

/** Startbildschirm des Beleg-Scanners: Quelle wählen. */
@Composable
fun BelegScannerEinstieg(viewModel: AppViewModel) {
    val context = LocalContext.current
    var kameraUri by remember { mutableStateOf<Uri?>(null) }

    val kamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            val uri = kameraUri
            if (uri != null) viewModel.scanReceipt(uri)
            else viewModel.message(
                str("scan_camera_failed", "Die Aufnahme konnte nicht verarbeitet werden.")
            )
        }
    }
    val galerie = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.scanReceipt(uri)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val paar = com.finovo.app.scan.InvoiceScanner.createCameraImage(context)
                    if (paar != null) {
                        kameraUri = paar.first
                        kamera.launch(paar.first)
                    } else {
                        viewModel.message(
                            str("scan_camera_failed", "Die Aufnahme konnte nicht verarbeitet werden.")
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(S("scan_camera", "Kamera"))
            }
            OutlinedButton(
                onClick = { galerie.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(S("scan_gallery", "Galerie"))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            S(
                "scan_receipt_hint",
                "Die Texterkennung läuft vollständig offline auf deinem Gerät. Nach dem Scan prüfst du alle Felder, bevor etwas gespeichert wird."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = appColors().textMuted
        )
    }
}

/** Bestätigungsbildschirm - Pflicht vor jedem Speichern. */
@Composable
fun BelegScanScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val ergebnis = viewModel.scanResult
    val bildPfad = viewModel.scanImagePath
    val qualitaet = viewModel.scanQuality

    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }
    val alleKategorien by viewModel.categories.collectAsState()
    val kategorien = remember(alleKategorien) {
        alleKategorien.filter { it.type == CategoryType.EXPENSE.name }
    }
    val heute = LocalDate.now()

    var haendler by remember { mutableStateOf(ergebnis.vendor.orEmpty()) }
    var datum by remember { mutableStateOf(ergebnis.invoiceDate ?: heute) }
    var betrag by remember { mutableStateOf(ergebnis.amountCents?.let { Money.format(it, "") } ?: "") }
    var mwst by remember { mutableStateOf(ergebnis.vatCents?.let { Money.format(it, "") } ?: "") }
    var kategorieId by remember { mutableStateOf(kategorien.firstOrNull()?.id ?: 0L) }
    var zahlungsart by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf(ergebnis.description.orEmpty()) }
    var schlagworte by remember { mutableStateOf("") }
    var alsAusgabe by remember { mutableStateOf(true) }
    var fehler by remember { mutableStateOf<String?>(null) }

    val zahlungsarten = listOf(
        str("pm_cash", "Bar"),
        str("pm_card", "Karte"),
        str("pm_transfer", "Überweisung"),
        str("pm_direct_debit", "Lastschrift"),
        str("pm_other", "Sonstiges")
    )
    var zahlungsartAuswahl by remember { mutableStateOf(zahlungsarten.first()) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("scan_receipt_title", "Beleg prüfen"),
            subtitle = S("scan_receipt_sub", "Bitte alle Felder kontrollieren"),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Bildvorschau
            if (!bildPfad.isNullOrBlank()) {
                item {
                    val bitmap = remember(bildPfad) {
                        runCatching { BitmapFactory.decodeFile(bildPfad) }.getOrNull()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = S("scan_receipt_image", "Aufgenommener Beleg"),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(appColors().cardAlt)
                                .scanCorners()
                        )
                    }
                }
            }

            if (qualitaet < 0.4f) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(appColors().warningContainer, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            S(
                                "scan_low_quality",
                                "⚠️ Bitte prüfen – das Bild war schwer lesbar. Kontrolliere alle Felder sorgfältig."
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader(S("scan_fields", "Erkannte Daten"))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AppTextField(
                            value = haendler,
                            onValueChange = { haendler = it },
                            label = S("haendler_2", "Händler"),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        DateField(
                            epochDay = datum.toEpochDay(),
                            onDateChange = { datum = it },
                            label = S("datum", "Datum"),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        AmountField(
                            value = betrag,
                            onValueChange = { betrag = it },
                            symbol = symbol,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        AmountField(
                            value = mwst,
                            onValueChange = { mwst = it },
                            symbol = symbol,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        if (kategorien.isNotEmpty()) {
                            DropdownField(
                                label = S("kategorie", "Kategorie"),
                                options = kategorien,
                                selected = kategorien.firstOrNull { it.id == kategorieId } ?: kategorien.first(),
                                optionLabel = { it.name },
                                onSelect = { kategorieId = it.id },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        DropdownField(
                            label = S("zahlungsart", "Zahlungsart"),
                            options = zahlungsarten,
                            selected = zahlungsartAuswahl,
                            optionLabel = { it },
                            onSelect = { zahlungsartAuswahl = it; zahlungsart = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        AppTextField(
                            value = notiz,
                            onValueChange = { notiz = it },
                            label = S("notiz_optional_7", "Notiz (optional)"),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        AppTextField(
                            value = schlagworte,
                            onValueChange = { schlagworte = it },
                            label = S("schlagworte_optional_2", "Schlagworte (optional)"),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SwitchRow(
                            title = S("scan_also_expense", "Zusätzlich als Ausgabe speichern"),
                            subtitle = S(
                                "scan_also_expense_sub",
                                "Der Beleg wird dann mit der Buchung verknüpft."
                            ),
                            checked = alsAusgabe,
                            onCheckedChange = { alsAusgabe = it }
                        )
                    }
                }
            }

            if (fehler != null) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        fehler ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = S("scan_save_receipt", "Beleg speichern"),
                    onClick = {
                        val cents = Money.parseToCents(betrag)
                        when {
                            haendler.isBlank() ->
                                fehler = str("val_name_missing", "Bitte gib eine Bezeichnung ein.")
                            cents == null || cents <= 0L ->
                                fehler = str("val_amount_positive", "Der Betrag muss größer als 0 sein.")
                            else -> {
                                fehler = null
                                viewModel.belegSpeichern(
                                    titel = haendler.trim(),
                                    pfad = bildPfad.orEmpty(),
                                    datum = datum.toEpochDay(),
                                    cents = cents,
                                    haendler = haendler.trim(),
                                    kategorieId = kategorieId,
                                    notiz = notiz.trim(),
                                    tags = schlagworte.trim(),
                                    mwstCents = Money.parseToCents(mwst),
                                    alsAusgabe = alsAusgabe
                                )
                                viewModel.clearScan()
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(
                    text = S("scan_discard", "Verwerfen"),
                    onClick = { viewModel.clearScan(); onBack() },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    S(
                        "scan_receipt_footer",
                        "Gespeichert wird erst jetzt – und nur das, was du bestätigt hast."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

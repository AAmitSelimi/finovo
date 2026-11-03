package com.finovo.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.data.DocumentEntity
import com.finovo.app.data.DocumentKind
import com.finovo.app.ui.components.AmountText
import com.finovo.app.ui.components.AppCard
import com.finovo.app.ui.components.AppTextField
import com.finovo.app.ui.components.AmountField
import com.finovo.app.ui.components.ConfirmDialog
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.DropdownField
import com.finovo.app.ui.components.EmptyState
import com.finovo.app.ui.components.InfoRow
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SectionHeader
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Alarm
import com.finovo.app.util.Alarme
import com.finovo.app.util.CsvImport
import com.finovo.app.util.Dates
import com.finovo.app.util.Familie
import com.finovo.app.util.Money
import com.finovo.app.util.S
import com.finovo.app.util.Suche
import com.finovo.app.util.Widgets
import com.finovo.app.util.str
import java.io.File
import java.time.LocalDate

// ===========================================================================
// Modul 18 - Dokumentenablage
// ===========================================================================

private fun dokumentArtLabel(kind: String): String = when (kind) {
    DocumentKind.RECEIPT.name -> str("doc_receipt", "Beleg")
    DocumentKind.INVOICE.name -> str("doc_invoice", "Rechnung")
    DocumentKind.CONTRACT.name -> str("doc_contract", "Vertrag")
    DocumentKind.BANK.name -> str("doc_bank", "Bank")
    DocumentKind.INSURANCE.name -> str("doc_insurance", "Versicherung")
    else -> str("doc_other", "Sonstiges")
}

internal val dokumentArtenNamen: List<String> = DocumentKind.values().map { it.name }

@Composable
fun DokumenteScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val dokumente by viewModel.documents.collectAsState()
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }

    var suche by remember { mutableStateOf("") }
    var artFilter by remember { mutableStateOf<String?>(null) }
    var loeschen by remember { mutableStateOf<DocumentEntity?>(null) }
    var zeigtFormular by remember { mutableStateOf(false) }
    var neuerPfad by remember { mutableStateOf("") }

    val dateiWaehler = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val pfad = speichereDokumentDatei(context, uri)
            if (pfad != null) {
                neuerPfad = pfad
                zeigtFormular = true
            } else {
                Toast.makeText(
                    context,
                    str("doc_import_failed", "Diese Datei konnte nicht kopiert werden."),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val gefiltert = dokumente.filter { d ->
        (artFilter == null || d.kind == artFilter) &&
            (suche.isBlank() || listOf(d.title, d.merchant, d.tags, d.note)
                .any { it.contains(suche, ignoreCase = true) })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("doc_title", "Dokumente"),
            subtitle = S("doc_sub", "Belege, Rechnungen, Verträge & mehr"),
            onBack = onBack,
            actions = {
                IconButton(onClick = { dateiWaehler.launch("*/*") }) {
                    Icon(Icons.Default.Add, contentDescription = S("doc_add", "Dokument hinzufügen"))
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                AppTextField(
                    value = suche,
                    onValueChange = { suche = it },
                    label = S("suchen", "Suchen"),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChipKlein(
                        text = S("alle", "Alle"),
                        aktiv = artFilter == null,
                        onClick = { artFilter = null },
                        modifier = Modifier.weight(1f)
                    )
                    dokumentArtenNamen.take(3).forEach { art ->
                        FilterChipKlein(
                            text = dokumentArtLabel(art),
                            aktiv = artFilter == art,
                            onClick = { artFilter = if (artFilter == art) null else art },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dokumentArtenNamen.drop(3).forEach { art ->
                        FilterChipKlein(
                            text = dokumentArtLabel(art),
                            aktiv = artFilter == art,
                            onClick = { artFilter = if (artFilter == art) null else art },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            if (gefiltert.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "\uD83D\uDCC1",
                        title = S("doc_empty_title", "Noch keine Dokumente"),
                        message = S(
                            "doc_empty_text",
                            "Füge Belege, Rechnungen, Verträge oder Versicherungsunterlagen hinzu – alles bleibt auf deinem Gerät."
                        ),
                        actionLabel = S("doc_add", "Dokument hinzufügen"),
                        onAction = { dateiWaehler.launch("*/*") }
                    )
                }
            } else {
                items(gefiltert) { d ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(d.title, fontWeight = FontWeight.Medium)
                                Text(
                                    listOf(
                                        dokumentArtLabel(d.kind),
                                        Dates.full(Dates.fromEpochDay(d.date)),
                                        d.merchant
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                                if (d.tags.isNotBlank()) {
                                    Text(
                                        d.tags,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = appColors().textMuted
                                    )
                                }
                            }
                            d.amountCents?.let {
                                AmountText(it, symbol, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                            IconButton(
                                onClick = { oeffneDokument(context, d) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Text(
                                    "\uD83D\uDC41",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .semantics {
                                            contentDescription = str("doc_preview", "Vorschau")
                                        }
                                )
                            }
                            IconButton(onClick = { loeschen = d }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = S("loeschen_6", "Löschen")
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (zeigtFormular) {
        DokumentDialog(
            pfad = neuerPfad,
            symbol = symbol,
            onDismiss = { zeigtFormular = false; neuerPfad = "" },
            onSave = { viewModel.saveDocument(it); zeigtFormular = false; neuerPfad = "" }
        )
    }

    ConfirmDialog(
        visible = loeschen != null,
        title = S("doc_delete_title", "Dokument löschen?"),
        message = S(
            "doc_delete_text",
            "%1\$s wird aus der Ablage entfernt. Die Datei wird ebenfalls gelöscht.",
            loeschen?.title ?: ""
        ),
        onConfirm = { loeschen?.let { viewModel.deleteDocument(it) }; loeschen = null },
        onDismiss = { loeschen = null }
    )
}

@Composable
private fun FilterChipKlein(
    text: String,
    aktiv: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (aktiv) appColors().incomeContainer else appColors().cardAlt,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DokumentDialog(
    pfad: String,
    symbol: String,
    onDismiss: () -> Unit,
    onSave: (DocumentEntity) -> Unit
) {
    var titel by remember { mutableStateOf("") }
    var art by remember { mutableStateOf(DocumentKind.RECEIPT.name) }
    var datum by remember { mutableStateOf(Dates.today()) }
    var tags by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }
    var haendler by remember { mutableStateOf("") }
    var betrag by remember { mutableStateOf("") }
    var fehler by remember { mutableStateOf<String?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    S("doc_add", "Dokument hinzufügen"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = titel, onValueChange = { titel = it },
                    label = S("titel", "Titel"), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DropdownField(
                    label = S("art", "Art"),
                    options = dokumentArtenNamen,
                    selected = art,
                    optionLabel = { dokumentArtLabel(it) },
                    onSelect = { art = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DateField(
                    epochDay = datum.toEpochDay(),
                    onDateChange = { datum = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = S("datum", "Datum")
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = haendler, onValueChange = { haendler = it },
                    label = S("haendler_empfaenger", "Händler / Empfänger"),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AmountField(
                    value = betrag, onValueChange = { betrag = it },
                    symbol = symbol, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = tags, onValueChange = { tags = it },
                    label = S("schlagworte_optional", "Schlagworte (optional)"),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = notiz, onValueChange = { notiz = it },
                    label = S("notiz_optional_6", "Notiz (optional)"),
                    modifier = Modifier.fillMaxWidth()
                )
                if (fehler != null) {
                    Text(
                        fehler ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(S("abbrechen", "Abbrechen"))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (titel.isBlank()) {
                                fehler = str("val_name_missing", "Bitte gib eine Bezeichnung ein.")
                            } else {
                                onSave(
                                    DocumentEntity(
                                        title = titel.trim(),
                                        kind = art,
                                        path = pfad,
                                        date = datum.toEpochDay(),
                                        tags = tags.trim(),
                                        note = notiz.trim(),
                                        amountCents = Money.parseToCents(betrag),
                                        merchant = haendler.trim(),
                                        mimeType = "*/*",
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(S("save", "Speichern"))
                    }
                }
            }
        }
    }
}

private fun speichereDokumentDatei(context: android.content.Context, uri: Uri): String? =
    runCatching {
        val ordner = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
        val name = "doc_${System.currentTimeMillis()}"
        val ziel = File(ordner, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            ziel.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        ziel.absolutePath
    }.onFailure {
        android.util.Log.e("Dokumente", "Kopieren fehlgeschlagen", it)
    }.getOrNull()

private fun oeffneDokument(context: android.content.Context, d: DocumentEntity) {
    runCatching {
        val datei = File(d.path)
        if (!datei.exists()) {
            Toast.makeText(
                context,
                str("doc_missing", "Die Datei zu diesem Dokument wurde nicht gefunden."),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", datei
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (d.mimeType.isBlank()) "*/*" else d.mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(
            context,
            str("doc_open_failed", "Diese Datei kann nicht geöffnet werden."),
            Toast.LENGTH_LONG
        ).show()
    }
}

// ===========================================================================
// Modul 30 - Datenimport (CSV / Excel-Export / Bankdateien)
// ===========================================================================

@Composable
fun ImportScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val tabelle by viewModel.importTabelle.collectAsState()
    val kategorien by viewModel.categories.collectAsState()
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }

    var zuordnung by remember { mutableStateOf(CsvImport.Zuordnung()) }
    var standardKategorie by remember { mutableStateOf(0L) }

    val dateiWaehler = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                Toast.makeText(
                    context,
                    str("import_read_failed", "Diese Datei konnte nicht gelesen werden."),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                viewModel.importLesen(text, uri.lastPathSegment ?: "")
                zuordnung = CsvImport.vorschlagen(CsvImport.lesen(text))
            }
        }
    }

    val zeilen = remember(tabelle, zuordnung) {
        tabelle?.let { CsvImport.anwenden(it, zuordnung) } ?: emptyList()
    }
    val gueltig = zeilen.count { it.ok }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("import_title", "Daten importieren"),
            subtitle = S("import_subtitle", "CSV, Excel-Export oder Bankdatei"),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                PrimaryButton(
                    text = S("import_pick", "Datei auswählen"),
                    onClick = { dateiWaehler.launch("text/*") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            if (tabelle == null) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            S(
                                "import_help",
                                "Wähle eine CSV-Datei aus. Danach ordnest du die Spalten zu und siehst eine Vorschau, bevor irgendetwas gespeichert wird."
                            ),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                    }
                }
            } else {
                val t = tabelle!!
                item {
                    SectionHeader(S("import_columns", "Spalten zuordnen"))
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SpaltenAuswahl(
                                label = S("import_col_date", "Datum"),
                                spalten = t.spalten,
                                index = zuordnung.datum,
                                onSelect = { zuordnung = zuordnung.copy(datum = it) }
                            )
                            SpaltenAuswahl(
                                label = S("import_col_desc", "Beschreibung"),
                                spalten = t.spalten,
                                index = zuordnung.beschreibung,
                                onSelect = { zuordnung = zuordnung.copy(beschreibung = it) }
                            )
                            SpaltenAuswahl(
                                label = S("import_col_amount", "Betrag"),
                                spalten = t.spalten,
                                index = zuordnung.betrag,
                                onSelect = { zuordnung = zuordnung.copy(betrag = it) }
                            )
                            SpaltenAuswahl(
                                label = S("import_col_category", "Kategorie"),
                                spalten = t.spalten,
                                index = zuordnung.kategorie,
                                onSelect = { zuordnung = zuordnung.copy(kategorie = it) }
                            )
                            SpaltenAuswahl(
                                label = S("import_col_account", "Konto"),
                                spalten = t.spalten,
                                index = zuordnung.konto,
                                onSelect = { zuordnung = zuordnung.copy(konto = it) }
                            )
                            Spacer(Modifier.height(8.dp))
                            DropdownField(
                                label = S("import_date_format", "Datumsformat"),
                                options = CsvImport.DatumFormat.values().toList(),
                                selected = zuordnung.datumFormat,
                                optionLabel = {
                                    when (it) {
                                        CsvImport.DatumFormat.AUTO -> str("import_auto", "Automatisch")
                                        CsvImport.DatumFormat.TT_MM_JJJJ -> "TT.MM.JJJJ"
                                        CsvImport.DatumFormat.JJJJ_MM_TT -> "JJJJ-MM-TT"
                                        CsvImport.DatumFormat.TT_MM_JJ -> "TT.MM.JJ"
                                    }
                                },
                                onSelect = { zuordnung = zuordnung.copy(datumFormat = it) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            SwitchRow(
                                title = S("import_negate", "Vorzeichen umkehren"),
                                subtitle = S(
                                    "import_negate_sub",
                                    "Wenn Ausgaben in der Datei positiv sind."
                                ),
                                checked = zuordnung.negation,
                                onCheckedChange = { zuordnung = zuordnung.copy(negation = it) }
                            )
                            Spacer(Modifier.height(8.dp))
                            if (kategorien.isNotEmpty()) {
                                val gewaehlt = kategorien.firstOrNull { it.id == standardKategorie }
                                    ?: kategorien.first()
                                DropdownField(
                                    label = S("import_default_category", "Kategorie für Zeilen ohne Angabe"),
                                    options = kategorien,
                                    selected = gewaehlt,
                                    optionLabel = { it.name },
                                    onSelect = { standardKategorie = it.id },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(
                        S("import_preview", "Vorschau"),
                        actionLabel = S("import_reset", "Zurücksetzen"),
                        onAction = { viewModel.importZuruecksetzen() }
                    )
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            InfoRow(
                                S("import_rows", "Zeilen in der Datei"),
                                t.zeilen.size.toString()
                            )
                            InfoRow(
                                S("import_valid", "Davon übernehmbar"),
                                gueltig.toString()
                            )
                            InfoRow(
                                S("import_invalid", "Nicht übernehmbar"),
                                (zeilen.size - gueltig).toString()
                            )
                        }
                    }
                }

                items(zeilen.take(15)) { z ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    z.beschreibung.ifBlank { str("import_no_desc", "Ohne Beschreibung") },
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    (z.datum?.let { Dates.full(it) } ?: str("import_no_date", "kein Datum")) +
                                        if (z.kategorieName.isNotBlank()) " · " + z.kategorieName else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            z.betragCents?.let {
                                AmountText(
                                    it, symbol,
                                    color = if (z.istEinnahme) appColors().income else appColors().expense,
                                    signed = true
                                )
                            }
                            if (!z.ok) {
                                Text(
                                    "⚠",
                                    modifier = Modifier.padding(start = 8.dp),
                                    color = appColors().warning
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = S("import_apply", "%1\$s Zeilen übernehmen", gueltig.toString()),
                        onClick = {
                            viewModel.importUebernehmen(zeilen, zuordnung, standardKategorie) { ok, fehler ->
                                Toast.makeText(
                                    context,
                                    str("import_done", "%1\$s Buchungen übernommen, %2\$s übersprungen.", ok.toString(), fehler.toString()),
                                    Toast.LENGTH_LONG
                                ).show()
                                viewModel.importZuruecksetzen()
                            }
                        },
                        enabled = gueltig > 0,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SpaltenAuswahl(
    label: String,
    spalten: List<String>,
    index: Int,
    onSelect: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            spalten.take(8).forEachIndexed { i, name ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (i == index) appColors().incomeContainer else appColors().cardAlt,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(i) }
                        .heightIn(min = 44.dp)
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(12),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ===========================================================================
// Modul 32 - Änderungsverlauf
// ===========================================================================

@Composable
fun VerlaufScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val eintraege by viewModel.auditLog.collectAsState()
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }
    var zeigtLoeschen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("audit_title", "Änderungsverlauf"),
            subtitle = S("audit_sub", "Was wann gespeichert oder geändert wurde"),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (eintraege.isEmpty()) {
                item {
                    EmptyState(
                        emoji = "\uD83D\uDCDD",
                        title = S("audit_empty_title", "Noch keine Einträge"),
                        message = S(
                            "audit_empty_text",
                            "Änderungen an Buchungen, Budget, Rechnungen und Sparzielen werden hier festgehalten."
                        )
                    )
                }
            } else {
                items(eintraege.take(400)) { e ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    viewModel.auditText(e),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    listOf(
                                        Dates.full(Dates.millisToLocalDate(e.at)),
                                        String.format(
                                            java.util.Locale.GERMANY,
                                            "%02d:%02d",
                                            java.util.Calendar.getInstance().apply {
                                                timeInMillis = e.at
                                            }.get(java.util.Calendar.HOUR_OF_DAY),
                                            java.util.Calendar.getInstance().apply {
                                                timeInMillis = e.at
                                            }.get(java.util.Calendar.MINUTE)
                                        ),
                                        e.detail.takeUnless { it in listOf("asset_added", "asset_edited") }
                                    ).filterNotNull().joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            e.amountCents?.let {
                                AmountText(it, symbol)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    SecondaryButton(
                        text = S("audit_clear", "Verlauf löschen"),
                        onClick = { zeigtLoeschen = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    ConfirmDialog(
        visible = zeigtLoeschen,
        title = S("audit_clear_title", "Verlauf löschen?"),
        message = S(
            "audit_clear_text",
            "Der Änderungsverlauf wird geleert. Deine Buchungen bleiben unverändert."
        ),
        onConfirm = { viewModel.clearAuditLog(); zeigtLoeschen = false },
        onDismiss = { zeigtLoeschen = false }
    )
}

// ===========================================================================
// Modul 22 - Haushalt / Familienmodus
// ===========================================================================

@Composable
fun HaushaltScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val mitglieder by viewModel.familienMitglieder.collectAsState()
    val auswertung by viewModel.haushaltsAuswertung.collectAsState()
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("fam_title", "Haushalt"),
            subtitle = S("fam_sub", "Gemeinsame und eigene Ausgaben"),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            S("fam_shared", "Gemeinsame Ausgaben (dieser Monat)"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = appColors().textMuted
                        )
                        AmountText(
                            auswertung.gemeinsamCents, symbol,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            S(
                                "fam_hint",
                                "Buchungen ohne Bereich gelten als gemeinsam. Bereiche verwaltest du unter Mehr → Bereiche."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                }
            }

            if (mitglieder.isEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    EmptyState(
                        emoji = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
                        title = S("fam_empty_title", "Noch keine Personen angelegt"),
                        message = S(
                            "fam_empty_text",
                            "Lege unter Mehr → Bereiche einen Bereich pro Person an. Danach kannst du hier Rechte festlegen."
                        )
                    )
                }
            } else {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(S("fam_members", "Personen"))
                }
                items(mitglieder) { m ->
                    val anteil = auswertung.mitglieder.firstOrNull { it.mitglied.id == m.id }
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.emoji, modifier = Modifier.padding(end = 8.dp))
                                Text(m.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                anteil?.let {
                                    AmountText(
                                        it.ausgabenCents, symbol,
                                        color = appColors().expense
                                    )
                                }
                            }
                            anteil?.let {
                                Text(
                                    S(
                                        "fam_member_sub",
                                        "%1\$s Einnahmen · %2\$s Buchungen",
                                        Money.format(it.einnahmenCents, symbol),
                                        it.anzahl.toString()
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            SwitchRow(
                                title = S("fam_see", "Darf gemeinsame Daten sehen"),
                                checked = m.darfSehen,
                                onCheckedChange = {
                                    viewModel.setFamilienRecht(
                                        m.id, it, m.darfBearbeiten, m.darfExportieren
                                    )
                                }
                            )
                            SwitchRow(
                                title = S("fam_edit", "Darf Buchungen bearbeiten"),
                                checked = m.darfBearbeiten,
                                onCheckedChange = {
                                    viewModel.setFamilienRecht(
                                        m.id, m.darfSehen, it, m.darfExportieren
                                    )
                                }
                            )
                            SwitchRow(
                                title = S("fam_export", "Darf Daten exportieren"),
                                checked = m.darfExportieren,
                                onCheckedChange = {
                                    viewModel.setFamilienRecht(
                                        m.id, m.darfSehen, m.darfBearbeiten, it
                                    )
                                }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ===========================================================================
// Modul 15 - Finanz-Alarme
// ===========================================================================

@Composable
fun AlarmeScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val zustand by viewModel.alarmZustand.collectAsState()
    val aktiv by viewModel.aktiveAlarme.collectAsState()
    val einstellungen by viewModel.settings.collectAsState()
    val symbol = einstellungen.currencySymbol.ifBlank { "€" }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("alarms_title", "Finanz-Alarme"),
            subtitle = S("alarms_sub", "Jeden Alarm einzeln ein- oder ausschalten"),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (aktiv.isNotEmpty()) {
                item {
                    SectionHeader(S("alarms_active", "Aktuell ausgelöst"))
                }
                items(aktiv) { m ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(m.titel, fontWeight = FontWeight.SemiBold)
                            Text(
                                m.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = appColors().textMuted
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }

            item { SectionHeader(S("alarms_settings", "Einstellungen")) }

            items(Alarm.values().toList()) { alarm ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SwitchRow(
                            title = Alarme.titel(alarm),
                            subtitle = Alarme.beschreibung(alarm),
                            checked = zustand.istAn(alarm),
                            onCheckedChange = { viewModel.setAlarm(alarm, it) }
                        )
                        if (zustand.istAn(alarm) && alarm.standardWert > 0) {
                            Spacer(Modifier.height(8.dp))
                            SchwellenEingabe(
                                alarm = alarm,
                                wert = zustand.schwelle(alarm),
                                symbol = symbol,
                                onSave = { viewModel.setAlarmSchwelle(alarm, it) }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SchwellenEingabe(
    alarm: Alarm,
    wert: Long,
    symbol: String,
    onSave: (Long) -> Unit
) {
    val einheit = when (alarm) {
        Alarm.BILL_DUE, Alarm.SUBSCRIPTION_RENEWAL, Alarm.LOAN_PAYMENT_DUE -> "tage"
        Alarm.UNUSUAL_SPENDING, Alarm.BUDGET_EXCEEDED -> "prozent"
        else -> "geld"
    }
    var text by remember(wert) {
        mutableStateOf(
            if (einheit == "geld") Money.format(wert, "", withSymbol = false)
            else wert.toString()
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            label = when (einheit) {
                "tage" -> S("alarms_days", "Tage im Voraus")
                "prozent" -> S("alarms_percent", "Schwelle in %")
                else -> S("alarms_amount", "Betrag ab")
            },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = {
            val neu = when (einheit) {
                "geld" -> Money.parseToCents(text)
                else -> text.trim().toLongOrNull()
            }
            if (neu != null && neu >= 0) onSave(neu)
        }) {
            Text(S("uebernehmen", "Übernehmen"))
        }
    }
    Text(
        when (einheit) {
            "tage" -> S("alarms_current_days", "Aktuell: %1\$s Tage", wert.toString())
            "prozent" -> S("alarms_current_percent", "Aktuell: %1\$s %%", wert.toString())
            else -> S("alarms_current_amount", "Aktuell: %1\$s", Money.format(wert, symbol))
        },
        style = MaterialTheme.typography.bodySmall,
        color = appColors().textMuted
    )
}

// ===========================================================================
// Modul 21 - Dashboard anpassen
// ===========================================================================

@Composable
fun DashboardAnpassenScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val widgetListe by viewModel.dashboardWidgets.collectAsState()
    var auswahl by remember { mutableStateOf(widgetListe) }

    // Auswahl folgt dem gespeicherten Stand
    androidx.compose.runtime.LaunchedEffect(widgetListe) { auswahl = widgetListe }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(
            title = S("widgets_title", "Übersicht anpassen"),
            subtitle = S("widgets_sub", "Reihenfolge ändern und Widgets ausblenden"),
            onBack = onBack
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        S(
                            "widgets_hint",
                            "Tippe auf die Pfeile, um ein Element zu verschieben. Ausgeblendete Elemente verschwinden von der Startseite."
                        ),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors().textMuted
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            items(auswahl) { art ->
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(art.emoji, modifier = Modifier.padding(end = 10.dp))
                        Text(
                            Widgets.label(art),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = {
                            auswahl = viewModel.widgetBewegen(auswahl, auswahl.indexOf(art), -1)
                        }) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = S("nach_oben", "Nach oben")
                            )
                        }
                        IconButton(onClick = {
                            auswahl = viewModel.widgetBewegen(auswahl, auswahl.indexOf(art), 1)
                        }) {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = S("nach_unten", "Nach unten")
                            )
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = { an ->
                                val neu = if (an) (auswahl + art).distinct() else auswahl - art
                                auswahl = neu
                                viewModel.setWidgetReihenfolge(neu)
                            }
                        )
                    }
                }
            }

            val ausgeblendet = Widgets.Art.values().filter { it !in auswahl }
            if (ausgeblendet.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader(S("widgets_hidden", "Ausgeblendet"))
                }
                items(ausgeblendet) { art ->
                    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(art.emoji, modifier = Modifier.padding(end = 10.dp))
                            Text(
                                Widgets.label(art),
                                modifier = Modifier.weight(1f),
                                color = appColors().textMuted
                            )
                            Switch(
                                checked = false,
                                onCheckedChange = { an ->
                                    if (an) {
                                        val neu = auswahl + art
                                        auswahl = neu
                                        viewModel.setWidgetReihenfolge(neu)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                SecondaryButton(
                    text = S("widgets_reset", "Standard wiederherstellen"),
                    onClick = {
                        auswahl = Widgets.standard()
                        viewModel.setWidgetReihenfolge(Widgets.standard())
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

package com.finovo.app.ui.screens

import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.ui.draw.drawBehind
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.finovo.app.AppViewModel
import com.finovo.app.backup.Backup
import com.finovo.app.export.Export
import com.finovo.app.ui.components.DateField
import com.finovo.app.ui.components.PrimaryButton
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SecondaryButton
import com.finovo.app.ui.components.SettingsRow
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
import java.io.File
import java.time.LocalDate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.S
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import com.finovo.app.util.str

@Composable
fun DataScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val backups by viewModel.localBackups.collectAsState()
    val busy by viewModel.busy.collectAsState()

    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var showRestoreWarning by remember { mutableStateOf(false) }
    var restoreReplace by remember { mutableStateOf(true) }

    var exportThisMonth by remember { mutableStateOf(true) }
    var exportYear by remember { mutableStateOf(false) }
    var exportCustom by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf(Dates.startOfMonth(LocalDate.now())) }
    var customTo by remember { mutableStateOf(LocalDate.now()) }

    fun currentPeriod(): com.finovo.app.ExportPeriod = when {
        exportYear -> com.finovo.app.ExportPeriod.ThisYear
        exportCustom -> com.finovo.app.ExportPeriod.CustomRange(customFrom, customTo)
        else -> com.finovo.app.ExportPeriod.ThisMonth
    }

    val createBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackupToUri(uri)
    }
    val openBackupDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreWarning = true
        }
    }
    val createCsvDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportCsvToUri(uri, currentPeriod())
    }
    val createPdfDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) viewModel.exportPdfToUri(uri, currentPeriod())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            // Wurde hier ein Fehler abgefangen, zeigt die Karte die genaue Ursache.
            com.finovo.app.util.FehlerHinweis()
            ScreenTopBar(
                tutorialId = "backup",
                title = S("daten_2", "Daten"),
                subtitle = S("backup_wiederherstellen_export_lokal_o", "Backup, Wiederherstellen & Export – lokal, ohne Cloud"),
                onBack = onBack
            )
        }

        // Premium-Kopf: leuchtende Wolke mit Haken
        item {
            val glow = appColors().glow
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(120.dp)
                        .drawBehind {
                            drawCircle(
                                androidx.compose.ui.graphics.Brush.radialGradient(
                                    listOf(glow.copy(alpha = 0.40f), androidx.compose.ui.graphics.Color.Transparent)
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = glow,
                        modifier = Modifier.size(78.dp)
                    )
                }
                Text(
                    S("ds_backup_hero", "Sichere deine Daten lokal auf deinem Gerät."),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        item {
            GroupTitle(S("backup", "Backup"))
            Group {
                SettingsRow(
                    title = S("backup_erstellen", "💾 Backup erstellen"),
                    subtitle = if (settings.lastBackupAt > 0) {
                        str("last_backup_on", "Letztes Backup: %1\$s", Dates.full(Dates.millisToLocalDate(settings.lastBackupAt)))
                    } else S("speichert_alle_daten_auf_dem_gerae", "Speichert alle Daten auf dem Gerät"),
                    icon = Icons.Default.Backup,
                    onClick = { viewModel.createLocalBackup() }
                )
                DividerLine()
                SettingsRow(
                    title = S("als_datei_speichern", "Als Datei speichern …"),
                    subtitle = S("json_datei_an_einem_ort_deiner_wahl", "JSON-Datei an einem Ort deiner Wahl"),
                    emoji = "📁",
                    onClick = {
                        runCatching { createBackupDocument.launch(Backup.defaultFileName()) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    str("no_file_manager", "Kein Dateimanager verfügbar."),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                )
            }
        }

        item {
            GroupTitle(S("gespeicherte_backups_backups_size", "Gespeicherte Backups (%1\$s)", backups.size))
            if (backups.isEmpty()) {
                Group {
                    Text(
                        S("noch_kein_backup_vorhanden", "Noch kein Backup vorhanden."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = appColors().textMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Group {
                    backups.forEachIndexed { index, file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1
                                )
                                Text(
                                    S("file_length_1024_kb_dates_full_dates_m", "%1\$s KB · %2\$s", file.length() / 1024, 
                                        Dates.full(Dates.millisToLocalDate(file.lastModified()))
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appColors().textMuted
                                )
                            }
                            TextButton(onClick = {
                                pendingRestoreFile = file
                                showRestoreWarning = true
                            }) { Text(S("wiederherstellen", "Wiederherstellen")) }
                            TextButton(onClick = { viewModel.deleteLocalBackup(file) }) {
                                Text(S("loeschen", "Löschen"), color = appColors().expense)
                            }
                        }
                        if (index != backups.lastIndex) DividerLine()
                    }
                }
            }
        }

        item {
            GroupTitle(S("wiederherstellen_3", "Wiederherstellen"), tutorialId = "restore")
            Group {
                SettingsRow(
                    title = S("backup_datei_auswaehlen", "📥 Backup-Datei auswählen"),
                    subtitle = S("aus_datei_oder_geraetespeicher_wiederh", "Aus Datei oder Gerätespeicher wiederherstellen"),
                    icon = Icons.Default.Restore,
                    onClick = {
                        runCatching {
                            openBackupDocument.launch(arrayOf("application/json", "text/*", "*/*"))
                        }.onFailure {
                            Toast.makeText(
                                context,
                                str("no_file_manager", "Kein Dateimanager verfügbar."),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }

        item {
            GroupTitle(S("export", "Export"), tutorialId = "csvexport")
            Group {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = exportThisMonth,
                        onClick = {
                            exportThisMonth = true; exportYear = false; exportCustom = false
                        },
                        label = { Text(S("dieser_monat", "Dieser Monat"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = exportYear,
                        onClick = {
                            exportThisMonth = false; exportYear = true; exportCustom = false
                        },
                        label = { Text(S("dieses_jahr", "Dieses Jahr"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = exportCustom,
                        onClick = {
                            exportThisMonth = false; exportYear = false; exportCustom = true
                        },
                        label = { Text(S("zeitraum", "Zeitraum"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
                if (exportCustom) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DateField(
                            epochDay = customFrom.toEpochDay(),
                            onDateChange = { customFrom = it },
                            label = S("von", "Von"),
                            modifier = Modifier.weight(1f)
                        )
                        DateField(
                            epochDay = customTo.toEpochDay(),
                            onDateChange = { customTo = it },
                            label = S("bis", "Bis"),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                DividerLine()
                SettingsRow(
                    title = S("csv_export", "CSV-Export"),
                    subtitle = S("alle_buchungen_als_tabelle", "Alle Buchungen als Tabelle"),
                    icon = Icons.Default.TableChart,
                    onClick = {
                        runCatching { createCsvDocument.launch(Export.csvFileName()) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    str("no_file_manager", "Kein Dateimanager verfügbar."),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                )
                DividerLine()
                SettingsRow(
                    title = S("pdf_bericht", "PDF-Bericht"),
                    subtitle = S("zusammenfassung_buchungen_als_pdf", "Zusammenfassung & Buchungen als PDF"),
                    icon = Icons.Default.PictureAsPdf,
                    onClick = {
                        runCatching { createPdfDocument.launch(Export.pdfFileName()) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    str("no_file_manager", "Kein Dateimanager verfügbar."),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                )
                DividerLine()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryButton(
                        text = S("csv_teilen", "CSV teilen"),
                        onClick = {
                            viewModel.createCsvFile(currentPeriod()) { file ->
                                shareFile(context, file, "text/csv")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = S("pdf_teilen", "PDF teilen"),
                        onClick = {
                            viewModel.createPdfFile(currentPeriod()) { file ->
                                shareFile(context, file, "application/pdf")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(14.dp))
            Text(
                S("alle_vorgaenge_laufen_offline_auf_dies", "Alle Vorgänge laufen offline auf diesem Gerät. Es werden keine Daten ins Internet übertragen."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
            if (busy) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
        }
    }

    if (showRestoreWarning) {
        AlertDialog(
            onDismissRequest = {
                showRestoreWarning = false
                pendingRestoreUri = null
                pendingRestoreFile = null
            },
            title = { Text(S("daten_wiederherstellen", "Daten wiederherstellen")) },
            text = {
                Column {
                    Text(
                        S("achtung_beim_wiederherstellen_koennen", "Achtung: Beim Wiederherstellen können vorhandene Daten ersetzt werden."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        S("ersetzen_alle_aktuellen_daten_werden_g", "• Ersetzen: Alle aktuellen Daten werden gelöscht und durch das Backup ersetzt.\n") +
                            S("zusammenfuehren_das_backup_wird_zu", "• Zusammenführen: Das Backup wird zu den vorhandenen Daten hinzugefügt."),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = restoreReplace,
                            onClick = { restoreReplace = true },
                            label = { Text(S("ersetzen", "Ersetzen"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis) }
                        )
                        FilterChip(
                            selected = !restoreReplace,
                            onClick = { restoreReplace = false },
                            label = { Text(S("zusammenfuehren", "Zusammenführen"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val mode = if (restoreReplace) Backup.RestoreMode.REPLACE
                    else Backup.RestoreMode.MERGE
                    pendingRestoreUri?.let { uri ->
                        viewModel.restoreFromUri(uri, mode) {
                            viewModel.refreshToday()
                        }
                    }
                    pendingRestoreFile?.let { file ->
                        viewModel.restoreFromFile(file, mode) {
                            viewModel.refreshToday()
                        }
                    }
                    showRestoreWarning = false
                    pendingRestoreUri = null
                    pendingRestoreFile = null
                }) { Text(S("wiederherstellen_2", "Wiederherstellen"), color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestoreWarning = false
                    pendingRestoreUri = null
                    pendingRestoreFile = null
                }) { Text(S("abbrechen_3", "Abbrechen")) }
            }
        )
    }
}

private fun shareFile(context: android.content.Context, file: File, mime: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, str("share_file", "Datei teilen")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, str("share_failed", "Teilen nicht möglich: %1\$s", it.message ?: ""), Toast.LENGTH_LONG).show()
    }
}

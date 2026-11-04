package com.finovo.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.finovo.app.util.CrashGuard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finovo.app.AppViewModel
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.theme.appColors
import com.finovo.app.util.Dates
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
import androidx.compose.foundation.lazy.items
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
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.S
import com.finovo.app.util.str

@Composable
fun AboutScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val expenses by viewModel.expenses.collectAsState()
    val incomes by viewModel.incomes.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val rules by viewModel.recurringRules.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val kategorien by viewModel.categories.collectAsState()
    val bereiche by viewModel.areas.collectAsState()
    val ziele by viewModel.goals.collectAsState()
    val abos by viewModel.subscriptions.collectAsState()
    val dokumente by viewModel.documents.collectAsState()
    val werte by viewModel.assets.collectAsState()
    val verlauf by viewModel.auditLog.collectAsState()

    val context = LocalContext.current
    val paket = remember { runCatching { paketInfo(context) }.getOrNull() }
    val datenbank = remember { context.getDatabasePath("finovo.db") }

    /** Wird neu gelesen, sobald sich der Datenbestand aendert. */
    val groessen by androidx.compose.runtime.produceState(
        initialValue = 0L to 0L,
        key1 = expenses.size + incomes.size + bills.size + dokumente.size
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { datenbank.length() }.getOrDefault(0L) to
                dokumente.sumOf { runCatching { java.io.File(it.path).length() }.getOrDefault(0L) }
        }
    }

    val gesamt = expenses.size + incomes.size + bills.size + rules.size + kategorien.size +
        bereiche.size + ziele.size + abos.size + dokumente.size + werte.size + verlauf.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            ScreenTopBar(title = S("ueber", "Über"), onBack = onBack)
        }
        item {
            ErrorLogCard()
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FinovoLogo(
                    iconSize = 104.dp,
                    fontSize = 26.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    paket?.versionName?.let { S("about_version_value", "Version %1\$s", it) }
                        ?: S("about_version_unknown", "Version unbekannt"),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
        }
        item {
            GroupTitle(S("funktionsweise", "Funktionsweise"))
            Group {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        S("finovo_ist_eine_private_offline_funkti", "Finovo ist eine private, offline funktionierende Finanz-App für eine Person. ") +
                            S("es_gibt_keine_registrierung_kein_k", "Es gibt keine Registrierung, kein Konto, keine Cloud und keine Internetverbindung. ") +
                            S("alle_daten_werden_in_einer_lokalen", "Alle Daten werden in einer lokalen Datenbank (SQLite/Room) auf diesem Gerät gespeichert."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            GroupTitle(S("daten_auf_diesem_geraet", "Daten auf diesem Gerät"))
            Group {
                InfoLine(S("ausgaben_4", "Ausgaben"), "${expenses.size}")
                DividerLine()
                InfoLine(S("einnahmen_4", "Einnahmen"), "${incomes.size}")
                DividerLine()
                InfoLine(S("rechnungen_2", "Rechnungen"), "${bills.size}")
                DividerLine()
                InfoLine(str("about_recurring_rules", "Wiederkehrende Zahlungen"), "${rules.size}")
                DividerLine()
                InfoLine(str("about_currency", "Währung"), "${settings.currency} (${settings.currencySymbol})")
                DividerLine()
                DividerLine()
                InfoLine(S("kategorien", "Kategorien"), "${kategorien.size}")
                DividerLine()
                InfoLine(S("areas", "Bereiche"), "${bereiche.size}")
                DividerLine()
                InfoLine(S("goals", "Sparziele"), "${ziele.size}")
                DividerLine()
                InfoLine(S("subscriptions", "Abos"), "${abos.size}")
                DividerLine()
                InfoLine(S("doc_title", "Dokumente"), "${dokumente.size}")
                DividerLine()
                InfoLine(S("nw_assets", "Vermögen"), "${werte.size}")
                DividerLine()
                InfoLine(S("audit_title", "Änderungsverlauf"), "${verlauf.size}")
                DividerLine()
                InfoLine(
                    str("about_last_backup", "Letztes Backup"),
                    if (settings.lastBackupAt > 0) {
                        Dates.full(Dates.millisToLocalDate(settings.lastBackupAt))
                    } else str("about_no_backup", "noch keins")
                )
            }
        }
        item {
            GroupTitle(S("about_app_info", "App-Informationen"))
            Group {
                InfoLine(
                    S("about_version", "Version"),
                    paket?.versionName ?: S("about_unknown", "unbekannt")
                )
                DividerLine()
                InfoLine(S("about_build", "Build"), paket?.let { buildNummer(it).toString() } ?: "—")
                DividerLine()
                InfoLine(
                    S("about_installed", "Installiert am"),
                    paket?.let { Dates.full(Dates.millisToLocalDate(it.firstInstallTime)) }
                        ?: S("about_unknown", "unbekannt")
                )
                DividerLine()
                InfoLine(
                    S("about_updated", "Zuletzt aktualisiert"),
                    paket?.let { Dates.full(Dates.millisToLocalDate(it.lastUpdateTime)) }
                        ?: S("about_unknown", "unbekannt")
                )
                DividerLine()
                InfoLine(
                    S("about_android", "Android"),
                    "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                )
                DividerLine()
                InfoLine(S("sprache", "Sprache"), AppLocale.nativeName(settings.language))
                DividerLine()
                InfoLine(S("waehrung", "Währung"), "${settings.currency} (${settings.currencySymbol})")
                DividerLine()
                InfoLine(S("about_offline", "Offline"), S("about_offline_value", "Keine Verbindung nötig"))
                DividerLine()
                InfoLine(S("about_entries", "Einträge gesamt"), "$gesamt")
                DividerLine()
                InfoLine(S("about_storage", "Speicherbedarf"), formatBytes(groessen.first + groessen.second))
                DividerLine()
                InfoLine(S("about_data_size", "Datenbank"), formatBytes(groessen.first))
                DividerLine()
                InfoLine(S("about_files_size", "Dokumente (Dateien)"), formatBytes(groessen.second))
                DividerLine()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        S("about_permissions", "Berechtigungen"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        berechtigungsTexte(paket?.requestedPermissions),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            Text(
                S("tipp_erstelle_regelmaessig_ein_backup", "Tipp: Erstelle regelmäßig ein Backup unter Mehr → Daten. Bewahre die Datei an einem sicheren Ort auf."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
            )
        }
        item {
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Paketinformationen der installierten App - auch auf neuen Android-Versionen. */
private fun paketInfo(context: android.content.Context): android.content.pm.PackageInfo? =
    if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

/** Build-Nummer (versionCode) auf allen Android-Versionen. */
private fun buildNummer(info: android.content.pm.PackageInfo): Long =
    if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

/** Speicherplatz in kb/MB - ohne Fliesskomma, damit nichts verrundet wird. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val trenner = Money.decimalSeparator()
    return if (bytes >= 1_000_000L) {
        val mb = bytes / 1_000_000L
        val zehntel = (bytes % 1_000_000L) / 100_000L
        if (zehntel > 0L) "$mb$trenner${zehntel} MB" else "$mb MB"
    } else {
        "${(bytes + 999L) / 1_000L} KB"
    }
}

/** Berechtigungen in verstaendliche Worte uebersetzt. */
private fun berechtigungsTexte(permissions: Array<String>?): String {
    val namen = (permissions ?: return str("about_no_permissions", "keine")).mapNotNull { p ->
        when (p.substringAfterLast('.')) {
            "POST_NOTIFICATIONS" -> str("perm_notifications", "Benachrichtigungen")
            "VIBRATE" -> str("perm_vibration", "Vibration")
            "SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM" -> str("perm_alarms", "Geplante Erinnerungen")
            "USE_BIOMETRIC", "USE_FINGERPRINT" -> str("perm_biometric", "Fingerabdruck")
            "RECEIVE_BOOT_COMPLETED" -> str("perm_boot", "Start nach dem Einschalten")
            "INTERNET" -> str("perm_internet", "Internet (ruhende Bank-Anbindung)")
            "ACCESS_NETWORK_STATE" -> str("perm_network", "Netzwerkstatus")
            else -> null
        }
    }.distinct()
    if (namen.isEmpty()) return str("about_no_permissions", "keine")
    return namen.joinToString(" · ")
}

@Composable
private fun InfoLine(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ErrorLogCard() {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { text = CrashGuard.readLog(context) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = S("crash_log_head", "Fehlerprotokoll"),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (text.isBlank()) {
                Text(
                    text = S("crash_empty", "Kein Fehlerprotokoll vorhanden."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                )
                Row {
                    TextButton(onClick = { CrashGuard.copyLog(context) }) {
                        Text(S("crash_copy", "Protokoll kopieren"))
                    }
                    TextButton(onClick = {
                        CrashGuard.clearLog()
                        text = ""
                    }) {
                        Text(S("crash_clear", "Protokoll löschen"))
                    }
                }
            }
        }
    }
}

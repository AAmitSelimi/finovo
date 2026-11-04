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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.ui.graphics.vector.ImageVector

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
    val versionText = paket?.versionName ?: S("about_unknown", "unbekannt")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            ScreenTopBar(title = S("about_finovo", "Über Finovo"), onBack = onBack)
        }

        // ------------------------------------------------------------ Hero
        item {
            AboutHero(
                version = paket?.versionName?.let { S("about_version_value", "Version %1\$s", it) }
                    ?: S("about_version_unknown", "Version unbekannt")
            )
        }

        // --------------------------------------------------- Funktionsweise
        item {
            AboutSection(Icons.Outlined.Info, S("funktionsweise", "Funktionsweise")) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        S("ab_how_p1", "Finovo ist deine persönliche Finanzverwaltung. Verwalte deine Einnahmen, Ausgaben, Rechnungen und Sparziele einfach und übersichtlich an einem Ort."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        S("ab_how_p2", "Deine Privatsphäre steht an erster Stelle. Finovo speichert deine Finanzdaten lokal auf deinem Gerät. Keine Registrierung, kein Benutzerkonto und keine Cloud-Synchronisierung. Deine Daten bleiben unter deiner Kontrolle."),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ---------------------------------------------- Deine Finanzuebersicht
        item {
            AboutSection(Icons.Outlined.AccountBalanceWallet, S("ab_overview_title", "Deine Finanzübersicht")) {
                InfoLine(S("ab_cnt_expenses", "Erfasste Ausgaben"), "${expenses.size}")
                DividerLine()
                InfoLine(S("ab_cnt_incomes", "Erfasste Einnahmen"), "${incomes.size}")
                DividerLine()
                InfoLine(S("ab_cnt_bills", "Gespeicherte Rechnungen"), "${bills.size}")
                DividerLine()
                InfoLine(S("ab_cnt_recurring", "Regelmäßige Zahlungen"), "${rules.size}")
                DividerLine()
                InfoLine(S("ab_cnt_currency", "Standardwährung"), "${settings.currency} (${settings.currencySymbol})")
                DividerLine()
                InfoLine(S("ab_cnt_categories", "Ausgabenkategorien"), "${kategorien.size}")
                DividerLine()
                InfoLine(S("ab_cnt_areas", "Finanzbereiche"), "${bereiche.size}")
                DividerLine()
                InfoLine(S("ab_cnt_goals", "Deine Sparziele"), "${ziele.size}")
                DividerLine()
                InfoLine(S("ab_cnt_subscriptions", "Abonnements"), "${abos.size}")
                DividerLine()
                InfoLine(S("doc_title", "Dokumente"), "${dokumente.size}")
                DividerLine()
                InfoLine(S("nw_assets", "Vermögen"), "${werte.size}")
                DividerLine()
                InfoLine(S("audit_title", "Änderungsverlauf"), "${verlauf.size}")
            }
        }

        // ---------------------------------------------------- Systemstatus
        item {
            AboutSection(Icons.Outlined.MonitorHeart, S("ab_status_title", "Systemstatus")) {
                ErrorLogContent()
            }
        }

        // ------------------------------------------------ App-Informationen
        item {
            AboutSection(Icons.Outlined.PhoneAndroid, S("about_app_info", "App-Informationen")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Outlined.NewReleases)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            S("ab_current_version", "Aktuelle App-Version"),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                        Text(
                            versionText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
                InfoLine(S("about_offline", "Offline"), S("about_offline_value", "Keine Verbindung nötig"))
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

        // ------------------------------------ Datenschutz und Datensicherheit
        item {
            AboutSection(Icons.Outlined.Shield, S("ab_privacy_title", "Datenschutz und Datensicherheit")) {
                FactRow(
                    Icons.Outlined.NoAccounts,
                    S("ab_priv_account_t", "Kein Benutzerkonto"),
                    S("ab_priv_account_b", "Finovo funktioniert ohne Registrierung, E-Mail-Adresse oder Anmeldung.")
                )
                DividerLine()
                FactRow(
                    Icons.Outlined.CloudOff,
                    S("ab_priv_cloud_t", "Keine Cloud-Synchronisierung"),
                    S("ab_priv_cloud_b", "Deine Daten werden nicht auf Server übertragen. Die automatische Android-Systemsicherung ist für Finovo deaktiviert.")
                )
                DividerLine()
                FactRow(
                    Icons.Outlined.Lock,
                    S("ab_priv_lock_t", "App-Sperre"),
                    if (settings.appLockEnabled) {
                        S("ab_priv_lock_on", "Aktiv. Finovo ist per PIN bzw. Biometrie geschützt und der Inhalt wird in der App-Übersicht und auf Screenshots verborgen.")
                    } else {
                        S("ab_priv_lock_off", "Nicht aktiv. Du kannst eine PIN und Biometrie unter Einstellungen → Sicherheit und Datenschutz einrichten.")
                    }
                )
                DividerLine()
                FactRow(
                    Icons.Outlined.Insights,
                    S("ab_priv_scan_t", "Erkennung auf dem Gerät"),
                    S("ab_priv_scan_b", "Texterkennung und GiroCode-Auswertung beim Scannen von Rechnungen laufen direkt auf deinem Gerät.")
                )
            }
        }

        // ---------------------------------------- Lokale Datenspeicherung
        item {
            AboutSection(Icons.Outlined.Storage, S("ab_storage_title", "Lokale Datenspeicherung")) {
                Text(
                    S("ab_storage_body", "Alle Einträge liegen in einer lokalen SQLite-Datenbank (Room) im geschützten App-Speicher dieses Geräts."),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
                )
                InfoLine(S("about_entries", "Einträge gesamt"), "$gesamt")
                DividerLine()
                InfoLine(S("about_storage", "Speicherbedarf"), formatBytes(groessen.first + groessen.second))
                DividerLine()
                InfoLine(S("about_data_size", "Datenbank"), formatBytes(groessen.first))
                DividerLine()
                InfoLine(S("about_files_size", "Dokumente (Dateien)"), formatBytes(groessen.second))
            }
        }

        // ---------------------------------- Datensicherung und Wiederherstellung
        item {
            AboutSection(Icons.Outlined.Restore, S("ab_backup_title", "Datensicherung und Wiederherstellung")) {
                InfoLine(
                    str("about_last_backup", "Letztes Backup"),
                    if (settings.lastBackupAt > 0) {
                        Dates.full(Dates.millisToLocalDate(settings.lastBackupAt))
                    } else str("about_no_backup", "noch keins")
                )
                DividerLine()
                Text(
                    S("ab_backup_body", "Unter Mehr → Daten erstellst du ein Backup als Datei und stellst deine Daten daraus wieder her. Die Datei wird nicht verschlüsselt – bewahre sie an einem sicheren Ort auf."),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted,
                    modifier = Modifier.padding(16.dp)
                )
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
                    .padding(horizontal = 32.dp)
            )
        }

        // ------------------------- Über Finovo (aufklappbar, ganz unten)
        item {
            Spacer(Modifier.height(16.dp))
            ExpandableSection(
                title = S("acc_about_title", "Über Finovo"),
                icon = Icons.Outlined.Favorite,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    S("acc_about_head", "Finovo – Dein Geld. Deine Regeln."),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    S("acc_about_body", "Dein persönlicher Finanzbegleiter – entwickelt, um die Verwaltung deines Geldes einfach, transparent und sicher zu machen."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    S("acc_about_principles", "Unsere Grundsätze:"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                listOf(
                    S("acc_p1", "Datenschutz von Anfang an"),
                    S("acc_p2", "Keine Registrierung erforderlich"),
                    S("acc_p3", "Keine Werbung"),
                    S("acc_p4", "Keine verpflichtende Cloud-Verbindung"),
                    S("acc_p5", "Lokale Datenspeicherung")
                ).forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = appColors().income,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(p, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.height(14.dp))
                DividerLine()
                Spacer(Modifier.height(12.dp))
                Text(
                    S("acc_dev", "Entwickelt von AZ Studio"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    S("about_version_value", "Version %1\$s", paket?.versionName ?: "—") +
                        " · © ${java.time.Year.now().value} " + S("az_studio", "AZ Studio"),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Kopfbereich mit Logo, Slogan und Untertitel. */
@Composable
private fun AboutHero(version: String) {
    val primary = MaterialTheme.colorScheme.primary
    AppCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(primary.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FinovoLogo(iconSize = 88.dp, fontSize = 28.sp, showSlogan = false, showDivider = false)
            Spacer(Modifier.height(12.dp))
            Text(
                S("ab_slogan", "Dein Geld. Deine Kontrolle. Deine Privatsphäre."),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                S("ab_subtitle", "Einfach. Sicher. Privat."),
                style = MaterialTheme.typography.bodyMedium,
                color = appColors().textMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    version,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** Abschnitt mit Icon-Ueberschrift und abgerundeter Karte. */
@Composable
private fun AboutSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    Group(content = content)
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(appColors().cardAlt),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun FactRow(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = appColors().textMuted)
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

/** Fehlerprotokoll als eigenstaendige Karte (bleibt fuer andere Aufrufer erhalten). */
@Composable
fun ErrorLogCard() {
    Group(modifier = Modifier.padding(vertical = 6.dp)) { ErrorLogContent() }
}

/** Systemstatus: "Alles in Ordnung" oder das vollstaendige Fehlerprotokoll. */
@Composable
private fun ErrorLogContent() {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { text = withContext(Dispatchers.IO) { CrashGuard.readLog(context) } }
    val ok = text.isBlank()
    val anzahl = remember(text) { Regex("(?m)^=== ").findAll(text).count().coerceAtLeast(if (ok) 0 else 1) }
    val statusColor = if (ok) appColors().income else MaterialTheme.colorScheme.error
    val statusBg = if (ok) appColors().incomeContainer else MaterialTheme.colorScheme.errorContainer
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(statusBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (ok) S("ab_status_ok_t", "Alles in Ordnung")
                    else S("ab_status_err_t", "Fehler gefunden"),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ok) MaterialTheme.colorScheme.onSurface else statusColor
                )
                Text(
                    if (ok) S("ab_status_ok_b", "Keine Fehler gefunden. Finovo funktioniert ordnungsgemäß.")
                    else S("ab_status_err_b", "Einträge im Fehlerprotokoll: %1\$s. Deine Daten sind sicher.", anzahl),
                    style = MaterialTheme.typography.bodySmall,
                    color = appColors().textMuted
                )
            }
        }
        if (!ok) {
            Spacer(Modifier.height(12.dp))
            Text(
                S("crash_log_head", "Fehlerprotokoll"),
                style = MaterialTheme.typography.labelLarge,
                color = appColors().textMuted
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(appColors().cardAlt)
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { CrashGuard.copyLog(context) }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(S("crash_copy", "Protokoll kopieren"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = {
                    CrashGuard.clearLog()
                    text = ""
                }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(S("crash_clear", "Protokoll löschen"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

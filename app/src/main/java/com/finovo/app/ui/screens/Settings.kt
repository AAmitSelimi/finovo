package com.finovo.app.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.finovo.app.notifications.ReminderTone
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.finovo.app.AppViewModel
import com.finovo.app.data.ThemeMode
import com.finovo.app.notifications.Notify
import com.finovo.app.ui.Route
import com.finovo.app.ui.components.ScreenTopBar
import com.finovo.app.ui.components.SettingsRow
import com.finovo.app.ui.components.SwitchRow
import com.finovo.app.ui.theme.appColors
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.finovo.app.ui.components.*
import com.finovo.app.util.*
import com.finovo.app.data.*
import com.finovo.app.util.AppLocale
import com.finovo.app.util.S
import com.finovo.app.util.str

private val currencies = listOf(
    "EUR" to "€",
    "USD" to "$",
    "GBP" to "£",
    "CHF" to "CHF",
    "ALL" to "L"
)

@Composable
fun SettingsScreen(viewModel: AppViewModel, onBack: () -> Unit, onNavigate: (Route) -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val kurse by viewModel.waehrungsKurse.collectAsState()
    val context = LocalContext.current
    val notificationAllowed = remember { mutableStateOf(Notify.hasPermission(context)) }
    var showPinDialog by remember { mutableStateOf<String?>(null) } // "create" | "change"
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var toneLabel by remember { mutableStateOf(ReminderTone.label(context)) }
    val tonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val picked: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            ReminderTone.save(context, picked)
            toneLabel = ReminderTone.label(context)
        }
    }
    val toneDialogTitle = S("tone_title", "Erinnerungston")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        item {
            ScreenTopBar(title = S("einstellungen", "Einstellungen"), onBack = onBack)
        }

        item {
            GroupTitle(S("erscheinungsbild_2", "Erscheinungsbild"))
            Group {
                SettingsRow(
                    title = S("heller_modus", "Heller Modus"),
                    subtitle = if (settings.themeMode == ThemeMode.LIGHT.name) str("more_theme_active", "Aktiv") else str("tap_to_activate", "Tippen zum Aktivieren"),
                    emoji = "☀️",
                    onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                    trailing = if (settings.themeMode == ThemeMode.LIGHT.name) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else null
                )
                DividerLine()
                SettingsRow(
                    title = S("dunkler_modus_2", "Dunkler Modus"),
                    subtitle = if (settings.themeMode == ThemeMode.DARK.name) str("more_theme_active", "Aktiv") else str("tap_to_activate", "Tippen zum Aktivieren"),
                    emoji = "🌙",
                    onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                    trailing = if (settings.themeMode == ThemeMode.DARK.name) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else null
                )
                DividerLine()
                SettingsRow(
                    title = S("systemeinstellung_folgen", "Systemeinstellung folgen"),
                    subtitle = if (settings.themeMode == ThemeMode.SYSTEM.name) str("more_theme_active", "Aktiv") else str("tap_to_activate", "Tippen zum Aktivieren"),
                    emoji = "⚙️",
                    onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                    trailing = if (settings.themeMode == ThemeMode.SYSTEM.name) {
                        { Text("✓", color = MaterialTheme.colorScheme.primary) }
                    } else null
                )
            }
        }

        item {
            GroupTitle(S("allgemein", "Allgemein"))
            Group {
                SettingsRow(
                    title = S("waehrung_2", "Währung"),
                    subtitle = S("settings_currency_settings_currenc_2", "%1\$s (%2\$s)", settings.currency, settings.currencySymbol),
                    emoji = "💱",
                    onClick = { showCurrencyDialog = true }
                )
                DividerLine()
                SettingsRow(
                    title = S("kategorien_verwalten", "Kategorien verwalten"),
                    subtitle = S("eigene_kategorien_anlegen_und_bearbeit", "Eigene Kategorien anlegen und bearbeiten"),
                    emoji = "🗂️",
                    onClick = { onNavigate(Route.Categories) }
                )
                DividerLine()
                SettingsRow(
                    title = S("budget", "Budget"),
                    subtitle = S("monatsbudget_und_warnungen", "Monatsbudget und Warnungen"),
                    emoji = "🎯",
                    onClick = { onNavigate(Route.Budget) }
                )
                DividerLine()
                SettingsRow(
                    title = S("wiederkehrende_zahlungen", "Wiederkehrende Zahlungen"),
                    subtitle = S("verwalten_pausieren_oder_stoppen", "Verwalten, pausieren oder stoppen"),
                    emoji = "🔁",
                    onClick = { onNavigate(Route.Recurring) }
                )
            }
        }

        item {
            GroupTitle(S("benachrichtigungen_2", "Benachrichtigungen"))
            Group {
                SwitchRow(
                    title = S("erinnerungen_aktiv", "Erinnerungen aktiv"),
                    subtitle = if (notificationAllowed.value) S("lokale_erinnerungen_sind_erlaubt", "Lokale Erinnerungen sind erlaubt")
                    else S("berechtigung_fehlt_bitte_in_den_an", "Berechtigung fehlt – bitte in den Android-Einstellungen erlauben"),
                    checked = settings.notificationsEnabled,
                    onCheckedChange = {
                        if (it && !Notify.hasPermission(context)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(
                                            AndroidSettings.EXTRA_APP_PACKAGE,
                                            context.packageName
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } else {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", context.packageName, null))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            return@SwitchRow
                        }
                        viewModel.setNotificationsEnabled(it)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                DividerLine()
                SettingsRow(
                    title = toneDialogTitle,
                    subtitle = toneLabel,
                    emoji = "🎵",
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, toneDialogTitle)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            )
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ReminderTone.pickerExistingUri(context))
                        }
                        runCatching { tonePicker.launch(intent) }
                    }
                )
                DividerLine()
                SettingsRow(
                    title = S("test_benachrichtigung_senden", "Test-Benachrichtigung senden"),
                    subtitle = S("prueft_ob_erinnerungen_ankommen", "Prüft, ob Erinnerungen ankommen"),
                    emoji = "🔔",
                    onClick = { viewModel.sendTestNotification() }
                )
            }
        }

        item {
            val ctx = LocalContext.current
            val sec by viewModel.security.collectAsState()
            val deviceReady = remember(settings.appLockEnabled, sec.deviceCredential) {
                com.finovo.app.security.Biometrics.deviceCredentialReady(ctx)
            }
            val bioStatus = remember(sec.face, sec.fingerprint, settings.biometricEnabled) {
                com.finovo.app.security.Biometrics.status(ctx, sec)
            }
            GroupTitle(S("sec_privacy_title", "Sicherheit und Datenschutz"))
            Group {
                SwitchRow(
                    title = S("app_sperre_2", "App-Sperre"),
                    subtitle = if (settings.appLockEnabled) S("sec_lock_on", "Finovo ist gesperrt, bis du dich authentifizierst")
                    else str("app_open_free", "App ist ohne Prüfung zugänglich"),
                    checked = settings.appLockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && settings.pinHash.isBlank() && !(sec.deviceCredential && deviceReady)) {
                            showPinDialog = "create"
                        } else {
                            viewModel.setAppLockEnabled(enabled)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (settings.appLockEnabled) {
                    DividerLine()
                    SwitchRow(
                        title = S("sec_bio_enable", "Biometrische Entsperrung"),
                        subtitle = com.finovo.app.security.Biometrics.message(ctx, bioStatus)
                            ?: S("sec_bio_enable_sub", "Fingerabdruck oder Gesicht, gesichert durch das Android-System"),
                        checked = settings.biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (settings.biometricEnabled) {
                        DividerLine()
                        SwitchRow(
                            title = S("sec_fingerprint", "Fingerabdruck verwenden"),
                            subtitle = if (com.finovo.app.security.Biometrics.hasFingerprintHardware(ctx))
                                S("sec_fingerprint_sub", "Starke Biometrie")
                            else S("sec_no_fingerprint_hw", "Auf diesem Gerät nicht erkannt"),
                            checked = sec.fingerprint,
                            onCheckedChange = { on ->
                                viewModel.updateSecurity { it.copy(fingerprint = on) }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        DividerLine()
                        SwitchRow(
                            title = S("sec_face", "Gesichtserkennung verwenden"),
                            subtitle = if (com.finovo.app.security.Biometrics.hasFaceHardware(ctx))
                                S("sec_face_sub", "Erlaubt auch schwächere Biometrie (je nach Gerät)")
                            else S("sec_no_face_hw", "Nur, wenn das Gerät sie unterstützt und eingerichtet hat"),
                            checked = sec.face,
                            onCheckedChange = { on ->
                                viewModel.updateSecurity { it.copy(face = on) }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    DividerLine()
                    SwitchRow(
                        title = S("sec_device_pin", "Alternative Geräte-PIN verwenden"),
                        subtitle = if (deviceReady) S("sec_device_pin_sub", "Geräte-PIN, Passwort oder Muster als Ersatz")
                        else S("sec_device_pin_none", "Auf dem Gerät ist keine Bildschirmsperre eingerichtet"),
                        checked = sec.deviceCredential,
                        onCheckedChange = { on ->
                            viewModel.updateSecurity { it.copy(deviceCredential = on) }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    DividerLine()
                    SettingsRow(
                        title = if (settings.pinHash.isBlank()) str("pin_create", "PIN festlegen") else str("pin_change", "PIN ändern"),
                        subtitle = if (settings.pinHash.isBlank()) str("sec_app_pin_optional", "Optional: eigene Finovo-PIN")
                        else str("pin_set_encrypted", "PIN ist gesetzt (verschlüsselt gespeichert)"),
                        emoji = "🔐",
                        onClick = { showPinDialog = if (settings.pinHash.isBlank()) "create" else "change" }
                    )
                    DividerLine()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        val labels = listOf(
                            0 to S("sec_t_now", "Sofort"),
                            30 to S("sec_t_30", "Nach 30 Sekunden"),
                            60 to S("sec_t_60", "Nach 1 Minute"),
                            300 to S("sec_t_300", "Nach 5 Minuten")
                        )
                        DropdownField(
                            label = S("automatische_sperre", "Automatische Sperre"),
                            options = labels.map { it.second },
                            selected = labels.firstOrNull { it.first == sec.timeoutSeconds }?.second ?: labels[1].second,
                            optionLabel = { it },
                            onSelect = { sel ->
                                labels.firstOrNull { it.second == sel }?.let { pick ->
                                    viewModel.updateSecurity { it.copy(timeoutSeconds = pick.first) }
                                }
                            }
                        )
                        Text(
                            S("sec_auto_sub", "Sperrt die App, wenn sie so lange im Hintergrund war."),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                    }
                    DividerLine()
                    SwitchRow(
                        title = S("sec_always", "App beim Öffnen immer entsperren"),
                        subtitle = S("sec_always_sub", "Nach einem Neustart der App wird immer gefragt"),
                        checked = sec.alwaysUnlock,
                        onCheckedChange = { on ->
                            viewModel.updateSecurity { it.copy(alwaysUnlock = on) }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (settings.pinHash.isNotBlank()) {
                        DividerLine()
                        SettingsRow(
                            title = S("pin_entfernen", "PIN entfernen"),
                            subtitle = S("sec_remove_pin_sub", "Entfernt nur die Finovo-PIN"),
                            emoji = "🗝️",
                            onClick = { viewModel.removePin() }
                        )
                    }
                }
            }
            Text(
                S("sec_privacy_note", "Finovo speichert keine Fingerabdrücke oder Gesichtsdaten. Die Prüfung macht ausschließlich das Android-System. In der App-Übersicht und auf Screenshots bleibt der Inhalt verborgen, solange die Sperre aktiv ist."),
                style = MaterialTheme.typography.bodySmall,
                color = appColors().textMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        item {
            val context = LocalContext.current
            GroupTitle(S("sprache_2", "Sprache"))
            Group {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    DropdownField(
                        label = S("sprache", "Sprache"),
                        options = com.finovo.app.util.Language.entries.toList(),
                        selected = com.finovo.app.util.Language.fromCode(com.finovo.app.util.AppLocale.resolve(context)),
                        optionLabel = { it.label },
                        onSelect = { lang ->
                            AppLocale.save(context, lang.code)
                            viewModel.setLanguage(lang.code)
                            (context as? android.app.Activity)?.recreate()
                        }
                    )
                    Text(
                        S("module_und_navigation_sind_mehrsprachi", "Alle Bildschirme, Kategorien, Zahlen- und Datumsformate werden sofort in der gewählten Sprache angezeigt."),
                        style = MaterialTheme.typography.bodySmall,
                        color = appColors().textMuted
                    )
                }
            }
        }

        item {
            GroupTitle(S("automatisierung", "Automatisierung"))
            Group {
                SwitchRow(
                    title = S("ausgabe_beim_bezahlen_erstellen", "Ausgabe beim Bezahlen erstellen"),
                    subtitle = S("beim_als_bezahlt_markieren_einer_rechn", "Beim „Als bezahlt markieren“ einer Rechnung"),
                    checked = settings.autoExpenseOnBillPaid,
                    onCheckedChange = { viewModel.setAutoExpenseOnBillPaid(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                DividerLine()
                SwitchRow(
                    title = S("wiederkehrende_buchungen_automatisch_e", "Wiederkehrende Buchungen automatisch erstellen"),
                    subtitle = S("faellige_termine_werden_beim_start_erz", "Fällige Termine werden beim Start erzeugt"),
                    checked = settings.autoCreateRecurring,
                    onCheckedChange = { viewModel.setAutoCreateRecurring(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item {
            // --------------------- Module: Alarme, Dokumente, Übersicht ----------
            Column {
                GroupTitle(S("settings_modules", "Module"))
                Group {
                    SettingsRow(
                        title = S("alarms_title", "Finanz-Alarme"),
                        subtitle = S("alarms_sub", "Jeden Alarm einzeln ein- oder ausschalten"),
                        emoji = "\uD83D\uDD14",
                        onClick = { onNavigate(Route.Alarms) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("doc_title", "Dokumente"),
                        subtitle = S("doc_sub", "Belege, Rechnungen, Verträge & mehr"),
                        emoji = "\uD83D\uDCC2",
                        onClick = { onNavigate(Route.Documents) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("ai_settings", "Finovo AI"),
                        subtitle = S("ai_settings_sub", "Fragen zu deinen Finanzen – aus echten Daten"),
                        emoji = "\uD83E\uDD16",
                        onClick = { onNavigate(Route.FinovoAi) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("widgets_title", "Übersicht anpassen"),
                        subtitle = S("widgets_sub", "Reihenfolge ändern und Widgets ausblenden"),
                        emoji = "\uD83C\uDF9B",
                        onClick = { onNavigate(Route.WidgetsEdit) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("nw_accounts", "Konten & Vermögen"),
                        subtitle = S("nw_sub", "Vermögen minus Schulden"),
                        emoji = "\uD83C\uDFE6",
                        onClick = { onNavigate(Route.NetWorth) }
                    )
                }
            }

            // ----------------------- Mehrere Währungen (Modul 34) ------------------
            Column {
                GroupTitle(S("fx_title", "Währungen & Umrechnung"))
                Group {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            S(
                                "fx_hint",
                                "Standardwährung: %1\$s. Die Kurse kannst du selbst pflegen – sie werden ausschließlich lokal gespeichert."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = appColors().textMuted
                        )
                        Spacer(Modifier.height(10.dp))
                        com.finovo.app.util.Waehrungen.alle.filter { it.code != "EUR" }.forEach { w ->
                            var text by remember(kurse[w.code]) {
                                mutableStateOf(((kurse[w.code] ?: w.standardKurs) / 10000.0).toString())
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "1 € =",
                                    modifier = Modifier.width(46.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    w.symbol + " " + w.code,
                                    modifier = Modifier.width(64.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.width(6.dp))
                                TextButton(onClick = {
                                    val wert = text.trim().replace(',', '.').toDoubleOrNull()
                                    if (wert != null && wert > 0) {
                                        viewModel.setKurs(w.code, (wert * 10000).toLong())
                                    }
                                }) { Text(S("uebernehmen", "Übernehmen")) }
                            }
                        }
                    }
                }
            }

            // ------------------------- Privatsphäre (Modul 38) ---------------------
            Column {
                GroupTitle(S("privacy_title", "Privatsphäre"))
                Group {
                    SwitchRow(
                        title = S("dup_warnings", "Warnung bei möglichen Doppelbuchungen"),
                        subtitle = S("dup_warnings_sub", "Hinweis beim Speichern – es wird nie automatisch gelöscht"),
                        checked = settings.duplicateWarningsEnabled,
                        onCheckedChange = { viewModel.setDuplicateWarnings(it) }
                    )
                    DividerLine()
                    SettingsRow(
                        title = S("privacy_data", "Deine Daten bleiben lokal"),
                        subtitle = S(
                            "privacy_data_sub",
                            "Kein Konto, keine Cloud, keine Werbung. Export und Backup entstehen auf diesem Gerät."
                        ),
                        emoji = "\uD83D\uDD12",
                        onClick = { onNavigate(Route.Data) }
                    )
                }
            }

            GroupTitle(S("daten", "Daten"))
            SettingsRow(
                title = S("bank_connections", "Bankverbindungen"),
                subtitle = S("bank_connections_sub", "Konto hinzufügen, synchronisieren, trennen"),
                emoji = "\uD83C\uDFE6",
                onClick = { onNavigate(Route.Bank) }
            )
            Group {
                SettingsRow(
                    title = S("backup_wiederherstellen_export", "Backup, Wiederherstellen & Export"),
                    subtitle = S("lokal_ohne_cloud", "Lokal, ohne Cloud"),
                    emoji = "💾",
                    onClick = { onNavigate(Route.Data) }
                )
                DividerLine()
                SettingsRow(
                    title = S("ueber_finovo", "Über Finovo"),
                    subtitle = S("version_1_0_offline", "Version 1.0 · offline"),
                    emoji = "ℹ️",
                    onClick = { onNavigate(Route.About) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPinDialog == "create") {
        PinDialog(
            title = S("pin_festlegen", "PIN festlegen"),
            message = S("mindestens_4_ziffern_die_pin_wird_", "Mindestens 4 Ziffern. Die PIN wird verschlüsselt (PBKDF2) gespeichert."),
            confirmLabel = S("speichern_2", "Speichern"),
            onDismiss = { showPinDialog = null },
            onConfirm = { pin ->
                val ok = viewModel.setPin(pin)
                if (ok) showPinDialog = null
                ok
            }
        )
    }
    if (showPinDialog == "change") {
        PinDialog(
            title = S("pin_aendern", "PIN ändern"),
            message = S("gib_zuerst_die_aktuelle_pin_ein_da", "Gib zuerst die aktuelle PIN ein, dann die neue."),
            confirmLabel = str("action_change", "Ändern"),
            requireOld = true,
            onDismiss = { showPinDialog = null },
            onConfirmOld = { null },
            onConfirm = { pin ->
                // handled by onConfirmNew below
                true
            },
            onConfirmNew = { oldPin, newPin ->
                val ok = viewModel.changePin(oldPin, newPin)
                if (ok) showPinDialog = null
                ok
            }
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(S("waehrung_waehlen", "Währung wählen")) },
            text = {
                Column {
                    currencies.forEach { (code, symbol) ->
                        TextButton(
                            onClick = {
                                showCurrencyDialog = false
                                viewModel.setCurrency(code, symbol)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                S("code_symbol", "%1\$s (%2\$s)", code, symbol) +
                                    if (settings.currency == code) "  ✓" else "",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) { Text(S("abbrechen", "Abbrechen")) }
            }
        )
    }
}

@Composable
private fun PinDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean,
    requireOld: Boolean = false,
    onConfirmOld: ((String) -> String?)? = null,
    onConfirmNew: ((String, String) -> Boolean)? = null
) {
    var pin by remember { mutableStateOf("") }
    var oldPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                if (requireOld) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { if (it.length <= 8) oldPin = it.filter { c -> c.isDigit() } },
                        label = { Text(S("aktuelle_pin", "Aktuelle PIN"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 8) {
                            pin = it.filter { c -> c.isDigit() }
                            error = null
                        }
                    },
                    label = { Text(if (requireOld) str("label_new_pin", "Neue PIN") else str("label_pin", "PIN"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                if (error != null) {
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (requireOld && onConfirmNew != null) {
                    if (oldPin.length < 4) {
                        error = str("bitte_aktuelle_pin_eingeben", "Bitte aktuelle PIN eingeben.")
                        return@TextButton
                    }
                    if (pin.length < 4) {
                        error = str("die_neue_pin_muss_mindestens_4_zif", "Die neue PIN muss mindestens 4 Ziffern haben.")
                        return@TextButton
                    }
                    if (!onConfirmNew(oldPin, pin)) error = str("die_aktuelle_pin_ist_falsch", "Die aktuelle PIN ist falsch.")
                } else {
                    if (pin.length < 4) {
                        error = str("die_pin_muss_mindestens_4_ziffern_", "Die PIN muss mindestens 4 Ziffern haben.")
                        return@TextButton
                    }
                    onConfirm(pin)
                }
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S("abbrechen_2", "Abbrechen")) } }
    )
}

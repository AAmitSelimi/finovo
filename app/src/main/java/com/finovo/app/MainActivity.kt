package com.finovo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.finovo.app.notifications.DailyWork
import com.finovo.app.notifications.Notify
import com.finovo.app.ui.AppRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.Context
import com.finovo.app.util.AppLocale
import com.finovo.app.util.CrashGuard
import com.finovo.app.util.S
import com.finovo.app.util.str
import com.finovo.app.util.refreshAppResources

class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val wrapped = AppLocale.wrap(newBase, AppLocale.resolve(newBase))
        super.attachBaseContext(wrapped)
        refreshAppResources(wrapped)
    }

    private lateinit var viewModel: AppViewModel

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                viewModel.message(
                    str("msg_notifications_disabled", "Benachrichtigungen sind deaktiviert. Erinnerungen können ohne Berechtigung nicht angezeigt werden.")
                )
            } else {
                lifecycleScope.launch { viewModel.refreshAfterChange() }
            }
        }

    private var darkTheme by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.installMainLoopGuard()
        WindowCompat.setDecorFitsSystemWindows(window, true)

        viewModel = ViewModelProvider(this)[AppViewModel::class.java]

        lifecycleScope.launch {
            val settings = viewModel.settings.first()
            if (settings.notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        runCatching { DailyWork.schedule(this) }
        Notify.createChannels(this)
        viewModel.evaluateLock(coldStart = true)

        setContent {
            val settings by viewModel.settings.collectAsState()
            darkTheme = when (settings.themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme

            // Inhalt in der App-Übersicht und in Screenshots verbergen, solange eine Sperre eingerichtet ist.
            androidx.compose.runtime.SideEffect {
                if (settings.appLockEnabled) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            AppRoot(
                viewModel = viewModel,
                onBiometric = { showBiometricPrompt() },
                onDeviceCredential = { showDeviceCredentialPrompt() }
            )
        }
    }

    private var promptShowing = false
    private var firstResume = true

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
        viewModel.evaluateLock(coldStart = firstResume)
        if (!firstResume) viewModel.lockIfTimedOut()
        firstResume = false
        if (viewModel.locked.value) startAutoUnlock()
    }

    override fun onPause() {
        super.onPause()
        if (promptShowing) return              // der System-Dialog pausiert die App nicht wirklich
        com.finovo.app.security.SecurityPrefs.setLastBackground(this, System.currentTimeMillis())
        if (viewModel.security.value.timeoutSeconds == 0) viewModel.lockNow()
    }

    /** Biometrie automatisch starten; ist sie nicht nutzbar, direkt die Geräte-Authentifizierung. */
    private fun startAutoUnlock() {
        if (promptShowing) return
        val sec = viewModel.security.value
        val bioOn = viewModel.settings.value.biometricEnabled
        val status = com.finovo.app.security.Biometrics.status(this, sec)
        if (bioOn && status == com.finovo.app.security.BioStatus.READY) {
            authenticate(com.finovo.app.security.AuthMode.BIOMETRIC)
        } else {
            if (bioOn) viewModel.setLockInfo(com.finovo.app.security.Biometrics.message(this, status))
            if (!viewModel.hasAppPin() && sec.deviceCredential &&
                com.finovo.app.security.Biometrics.deviceCredentialReady(this)
            ) authenticate(com.finovo.app.security.AuthMode.DEVICE)
        }
    }

    private fun authenticate(mode: com.finovo.app.security.AuthMode) {
        if (promptShowing) return
        val sec = viewModel.security.value
        promptShowing = true
        com.finovo.app.security.Biometrics.authenticate(
            activity = this,
            settings = sec,
            mode = mode,
            hasAppPin = viewModel.hasAppPin(),
            onSuccess = { viewModel.unlock() },
            onInfo = { viewModel.setLockInfo(it) },
            onFallback = {
                if (sec.deviceCredential && com.finovo.app.security.Biometrics.deviceCredentialReady(this) &&
                    mode == com.finovo.app.security.AuthMode.BIOMETRIC
                ) {
                    promptShowing = false
                    authenticate(com.finovo.app.security.AuthMode.DEVICE)
                }
            },
            onEnded = { promptShowing = false }
        )
    }

    /** Vom Sperrbildschirm: Fingerabdruck / Gesicht. */
    private fun showBiometricPrompt() {
        val sec = viewModel.security.value
        val status = com.finovo.app.security.Biometrics.status(this, sec)
        if (status != com.finovo.app.security.BioStatus.READY) {
            viewModel.setLockInfo(
                com.finovo.app.security.Biometrics.message(this, status)
                    ?: str("msg_biometric_unavailable", "Biometrie ist auf diesem Gerät nicht verfügbar oder nicht eingerichtet.")
            )
            return
        }
        authenticate(com.finovo.app.security.AuthMode.BIOMETRIC)
    }

    /** Vom Sperrbildschirm: "Mit Geräte-PIN entsperren". */
    private fun showDeviceCredentialPrompt() {
        if (!com.finovo.app.security.Biometrics.deviceCredentialReady(this)) {
            viewModel.setLockInfo(str("bio_no_device_credential", "Auf dem Gerät ist keine PIN, kein Passwort und kein Muster eingerichtet."))
            return
        }
        authenticate(com.finovo.app.security.AuthMode.DEVICE)
    }
}

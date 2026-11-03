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
        viewModel.evaluateLock()

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

            AppRoot(
                viewModel = viewModel,
                onBiometric = { showBiometricPrompt() }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshToday()
        viewModel.evaluateLock()
        if (viewModel.locked.value) {
            val settings = viewModel.settings.value
            if (settings.biometricEnabled && canUseBiometric()) {
                showBiometricPrompt()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.lockNow()
    }

    private fun canUseBiometric(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun showBiometricPrompt() {
        if (!canUseBiometric()) {
            viewModel.message(str("msg_biometric_unavailable", "Biometrie ist auf diesem Gerät nicht verfügbar oder nicht eingerichtet."))
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    viewModel.unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    viewModel.message(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    viewModel.message(str("msg_biometric_failed", "Fingerabdruck nicht erkannt. Bitte PIN verwenden."))
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(S(this, "unlock_finovo", "Finovo entsperren"))
                .setSubtitle(str("biometric_subtitle", "Bestätige deinen Fingerabdruck"))
                .setNegativeButtonText(str("biometric_use_pin", "PIN verwenden"))
                .build()
        )
    }
}

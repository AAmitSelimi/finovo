package com.finovo.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.finovo.app.util.S

enum class BioStatus { READY, DISABLED, NO_HARDWARE, NOT_ENROLLED, UNAVAILABLE, UPDATE_REQUIRED }

enum class AuthMode { BIOMETRIC, DEVICE }

/**
 * Dünne Hülle um die offizielle Android-BiometricPrompt-API.
 * Finovo erkennt, speichert und vergleicht selbst KEINE Fingerabdrücke oder Gesichter:
 * Das System liefert nur "erfolgreich" oder "fehlgeschlagen".
 */
object Biometrics {

    /** Welche Biometrie-Klasse ist erlaubt? Gesicht ist bei vielen Geräten "schwach", Fingerabdruck "stark". */
    fun biometricClass(s: SecuritySettings): Int? = when {
        s.face -> BIOMETRIC_WEAK          // schließt starke Verfahren ein
        s.fingerprint -> BIOMETRIC_STRONG
        else -> null
    }

    fun hasFingerprintHardware(c: Context): Boolean =
        c.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

    fun hasFaceHardware(c: Context): Boolean =
        c.packageManager.hasSystemFeature("android.hardware.biometrics.face")

    fun status(c: Context, s: SecuritySettings): BioStatus {
        val cls = biometricClass(s) ?: return BioStatus.DISABLED
        return when (BiometricManager.from(c).canAuthenticate(cls)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BioStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BioStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BioStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BioStatus.UPDATE_REQUIRED
            else -> BioStatus.UNAVAILABLE
        }
    }

    /** Ist eine Geräte-PIN, ein Passwort oder Muster eingerichtet? */
    fun deviceCredentialReady(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun message(c: Context, status: BioStatus): String? = when (status) {
        BioStatus.READY, BioStatus.DISABLED -> null
        BioStatus.NO_HARDWARE -> S(c, "bio_no_hardware", "Dieses Gerät hat keinen Fingerabdruck- oder Gesichtssensor.")
        BioStatus.NOT_ENROLLED -> S(c, "bio_not_enrolled", "Auf dem Gerät ist noch kein Fingerabdruck oder Gesicht eingerichtet. Das geht in den Android-Einstellungen unter Sicherheit.")
        BioStatus.UPDATE_REQUIRED -> S(c, "bio_update_required", "Für die Biometrie ist ein Sicherheitsupdate des Geräts nötig.")
        BioStatus.UNAVAILABLE -> S(c, "bio_unavailable", "Die Biometrie ist gerade nicht verfügbar.")
    }

    /**
     * Zeigt den System-Dialog. Fehler und Abbrüche werden ruhig behandelt:
     * - Abbruch durch den Benutzer: keine Meldung
     * - "Geräte-PIN"-Taste oder Sperre nach zu vielen Versuchen: [onFallback]
     * - sonstige Fehler: [onInfo] mit verständlichem Text
     */
    fun authenticate(
        activity: FragmentActivity,
        settings: SecuritySettings,
        mode: AuthMode,
        hasAppPin: Boolean,
        onSuccess: () -> Unit,
        onInfo: (String) -> Unit,
        onFallback: () -> Unit,
        onEnded: () -> Unit
    ) {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(S(activity, "unlock_finovo", "Finovo entsperren"))
            .setConfirmationRequired(false)

        when (mode) {
            AuthMode.DEVICE -> {
                builder.setSubtitle(S(activity, "bio_device_subtitle", "Mit Geräte-PIN, Passwort oder Muster"))
                builder.setAllowedAuthenticators(DEVICE_CREDENTIAL)
            }
            AuthMode.BIOMETRIC -> {
                val cls = biometricClass(settings) ?: run { onEnded(); return }
                builder.setSubtitle(S(activity, "bio_subtitle", "Entsperre deine Finanzübersicht"))
                val deviceInPrompt = settings.deviceCredential && deviceCredentialReady(activity) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                if (deviceInPrompt) {
                    builder.setAllowedAuthenticators(cls or DEVICE_CREDENTIAL)
                } else {
                    builder.setAllowedAuthenticators(cls)
                    val negative = when {
                        settings.deviceCredential && deviceCredentialReady(activity) ->
                            S(activity, "bio_use_device_pin", "Geräte-PIN verwenden")
                        hasAppPin -> S(activity, "biometric_use_pin", "PIN verwenden")
                        else -> S(activity, "abbrechen", "Abbrechen")
                    }
                    builder.setNegativeButtonText(negative)
                }
            }
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onEnded()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onEnded()
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> Unit
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onFallback()
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            onInfo(S(activity, "bio_lockout", "Zu viele Fehlversuche. Bitte mit Geräte-PIN oder App-PIN entsperren."))
                            onFallback()
                        }
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                            onInfo(errString.toString())
                            onFallback()
                        }
                        else -> onInfo(errString.toString())
                    }
                }
                // onAuthenticationFailed: das System zeigt den Hinweis selbst, der Dialog bleibt offen.
            }
        )
        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            onEnded()
            onInfo(S(activity, "bio_unavailable", "Die Biometrie ist gerade nicht verfügbar."))
        }
    }
}

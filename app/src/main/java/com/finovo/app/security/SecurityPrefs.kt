package com.finovo.app.security

import android.content.Context

/**
 * Einstellungen zur App-Sperre, die nicht in der Room-Datenbank liegen
 * (die Datenbank bleibt unverändert). Es werden KEINE biometrischen Daten
 * gespeichert, nur Schalter und ein Zeitstempel.
 */
data class SecuritySettings(
    /** Fingerabdruck erlauben (Android-Klasse "stark"). */
    val fingerprint: Boolean = true,
    /** Gesichtserkennung erlauben (Android-Klasse "schwach" ist dann zugelassen). */
    val face: Boolean = true,
    /** Geräte-PIN, Passwort oder Muster als Alternative erlauben. */
    val deviceCredential: Boolean = true,
    /** Sperre nach dieser Zeit im Hintergrund; 0 = sofort. */
    val timeoutSeconds: Int = 30,
    /** Beim frischen Öffnen der App immer entsperren. */
    val alwaysUnlock: Boolean = true
) {
    companion object {
        val TIMEOUT_CHOICES = listOf(0, 30, 60, 300)
    }
}

object SecurityPrefs {
    private const val FILE = "finovo_security"
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(c: Context): SecuritySettings {
        val p = prefs(c)
        val d = SecuritySettings()
        return SecuritySettings(
            fingerprint = p.getBoolean("fingerprint", d.fingerprint),
            face = p.getBoolean("face", d.face),
            deviceCredential = p.getBoolean("device_credential", d.deviceCredential),
            timeoutSeconds = p.getInt("timeout_s", d.timeoutSeconds).let {
                if (it in SecuritySettings.TIMEOUT_CHOICES) it else d.timeoutSeconds
            },
            alwaysUnlock = p.getBoolean("always_unlock", d.alwaysUnlock)
        )
    }

    fun save(c: Context, s: SecuritySettings) {
        prefs(c).edit()
            .putBoolean("fingerprint", s.fingerprint)
            .putBoolean("face", s.face)
            .putBoolean("device_credential", s.deviceCredential)
            .putInt("timeout_s", s.timeoutSeconds)
            .putBoolean("always_unlock", s.alwaysUnlock)
            .apply()
    }

    fun lastBackground(c: Context): Long = prefs(c).getLong("last_bg", 0L)
    fun setLastBackground(c: Context, millis: Long) {
        prefs(c).edit().putLong("last_bg", millis).apply()
    }
}

/** Reine Entscheidungslogik (ohne Android), damit sie getestet werden kann. */
object LockPolicy {
    /**
     * Soll die App jetzt gesperrt werden?
     *
     * @param coldStart App wurde neu gestartet (Prozess war weg)
     * @param lastBackgroundMs Zeitpunkt, an dem die App zuletzt in den Hintergrund ging (0 = unbekannt)
     */
    fun shouldLock(
        coldStart: Boolean,
        alwaysUnlock: Boolean,
        lastBackgroundMs: Long,
        nowMs: Long,
        timeoutSeconds: Int
    ): Boolean {
        if (coldStart && alwaysUnlock) return true
        if (lastBackgroundMs <= 0L) return true          // unbekannt -> sicher sperren
        if (timeoutSeconds <= 0) return true             // sofort
        val elapsed = nowMs - lastBackgroundMs
        return elapsed < 0 || elapsed >= timeoutSeconds * 1000L
    }
}

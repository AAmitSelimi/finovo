package com.finovo.app.notifications

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.finovo.app.util.S

/**
 * Benachrichtigungston fuer Erinnerungen (Rechnungen & Alarme).
 *
 * Gespeichert wird nur in den SharedPreferences - die Datenbank bleibt
 * unveraendert. Ab Android 8 ist der Ton eines Kanals nach dem Anlegen fest.
 * Deshalb bekommt der Erinnerungskanal bei jedem Tonwechsel eine neue Kennung
 * (finovo_bills_v1, _v2 ...) und der alte Kanal wird entfernt.
 */
object ReminderTone {

    private const val PREFS = "finovo_reminder_tone"
    private const val KEY_MODE = "mode"
    private const val KEY_URI = "uri"
    private const val KEY_VERSION = "version"

    /** Standardton des Systems. */
    const val MODE_DEFAULT = "default"
    /** Vom Nutzer gewaehlter Ton. */
    const val MODE_CUSTOM = "custom"
    /** Lautlos (nur Vibration). */
    const val MODE_SILENT = "silent"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(context: Context): String =
        prefs(context).getString(KEY_MODE, MODE_DEFAULT) ?: MODE_DEFAULT

    private fun version(context: Context): Int = prefs(context).getInt(KEY_VERSION, 0)

    /** Ton, der tatsaechlich abgespielt wird (null = lautlos). */
    fun soundUri(context: Context): Uri? = when (mode(context)) {
        MODE_SILENT -> null
        MODE_CUSTOM -> prefs(context).getString(KEY_URI, null)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /** Aktuelle Kanal-Kennung fuer Erinnerungen. Version 0 = urspruenglicher Kanal. */
    fun channelId(context: Context): String {
        val v = version(context)
        return if (v == 0) Notify.CHANNEL_BILLS else "${Notify.CHANNEL_BILLS}_v$v"
    }

    /** Uri fuer den Auswahldialog (vorausgewaehlter Eintrag). */
    fun pickerExistingUri(context: Context): Uri? = when (mode(context)) {
        MODE_SILENT -> null
        MODE_CUSTOM -> soundUri(context)
        else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    /**
     * Uebernimmt das Ergebnis des System-Tonauswahldialogs.
     * [picked] null bedeutet "Lautlos".
     */
    fun save(context: Context, picked: Uri?) {
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val newMode = if (picked == null) {
            MODE_SILENT
        } else if (picked == default || RingtoneManager.isDefault(picked)) {
            MODE_DEFAULT
        } else {
            MODE_CUSTOM
        }
        val oldId = channelId(context)
        prefs(context).edit()
            .putString(KEY_MODE, newMode)
            .putString(KEY_URI, if (newMode == MODE_CUSTOM) picked.toString() else null)
            .putInt(KEY_VERSION, version(context) + 1)
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching { manager.deleteNotificationChannel(oldId) }
        }
        Notify.createChannels(context)
    }

    /** Anzeigename des gewaehlten Tons. */
    fun label(context: Context): String = when (mode(context)) {
        MODE_SILENT -> S(context, "tone_silent", "Lautlos")
        MODE_DEFAULT -> S(context, "tone_default", "Standardton")
        else -> runCatching {
            RingtoneManager.getRingtone(context, soundUri(context))?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: S(context, "tone_custom", "Eigener Ton")
    }

    val audioAttributes: AudioAttributes
        get() = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
}

package com.finovo.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import com.finovo.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fängt Abstürze ab, damit die App nicht schwarz wird, und schreibt die
 * genaue Ursache in eine Datei, die der Nutzer auf der "Über"-Seite
 * lesen und kopieren kann.
 *
 * Alles bleibt auf dem Gerät - es wird nichts ins Internet gesendet.
 */
object CrashGuard {

    private const val FILE_NAME = "finovo_crash.log"
    private const val MAX_REPEATS = 30
    private const val MAX_CAUSES = 5
    private const val MAX_LINES = 40
    private const val MAX_FILE_BYTES = 400_000L

    private val _crashCount = MutableStateFlow(0)
    val crashCount: StateFlow<Int> get() = _crashCount

    private var appContext: Context? = null
    private var installed = false
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var lastMessage = ""
    private var repeats = 0

    /** Protokolliert einen Fehler, ohne die App zu beenden. */
    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (installed) return
        installed = true
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { log(app, "Hintergrund-Thread '" + thread.name + "'", throwable) }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Hält die App am Leben, wenn die Oberfläche beim Aufbau abstürzt.
     * Der Fehler wird protokolliert und gezählt, die Ansicht kann danach
     * eine verständliche Meldung zeigen.
     */
    fun installMainLoopGuard() {
        Handler(Looper.getMainLooper()).post {
            var laufen = true
            while (laufen) {
                try {
                    Looper.loop()
                    laufen = false
                } catch (t: Throwable) {
                    runCatching {
                        appContext?.let { log(it, "Hauptschleife", t) }
                        _crashCount.value = _crashCount.value + 1
                    }
                }
            }
        }
    }

    /** Eigene Notiz ins Protokoll schreiben. */
    fun trace(message: String) {
        val app = appContext ?: return
        runCatching {
            val file = File(app.filesDir, FILE_NAME)
            file.appendText(stamp() + " Hinweis: " + message + "\n\n")
        }
    }

    /** Das gesamte Protokoll als Text (leer, wenn nichts passiert ist). */
    fun readLog(context: Context): String = try {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText() else ""
    } catch (t: Throwable) {
        ""
    }

    /** Protokoll in die Zwischenablage legen. */
    fun copyLog(context: Context) {
        val text = readLog(context)
        if (text.isBlank()) {
            Toast.makeText(context, context.getString(R.string.crash_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("Finovo Fehlerprotokoll", text))
        }
        Toast.makeText(context, str("crash_copied", "Protokoll kopiert."), Toast.LENGTH_SHORT).show()
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Finovo Fehlerprotokoll")
                putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file))
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, str("crash_copy", "Protokoll kopieren")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Protokoll löschen - danach werden auch die Hinweise wieder ausgeblendet. */
    fun clearLog() {
        repeats = 0
        lastMessage = ""
        _crashCount.value = 0
        runCatching { appContext?.let { File(it.filesDir, FILE_NAME).delete() } }
    }

    @Synchronized
    private fun log(context: Context, source: String, throwable: Throwable) {
        val head = throwable.javaClass.name + ": " + (throwable.message ?: "")
        if (head == lastMessage) {
            repeats += 1
            if (repeats > MAX_REPEATS) return
        } else {
            repeats = 0
            lastMessage = head
        }
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists() && file.length() > MAX_FILE_BYTES) {
            file.writeText("")
        }
        val sb = StringBuilder()
        sb.append("=== ").append(stamp()).append(" | ").append(source).append(" ===\n")
        sb.append(format(throwable, 0))
        sb.append("\n--- Geraet ---\n")
        sb.append("Geraet: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("App: ").append(versionLabel(context)).append('\n')
        if (repeats > 0) sb.append("Wiederholung Nr. ").append(repeats).append('\n')
        sb.append('\n')
        file.appendText(sb.toString())
    }

    private fun format(t: Throwable, depth: Int): String {
        val sb = StringBuilder()
        sb.append(t.javaClass.name).append(": ").append(t.message ?: "").append('\n')
        val stack = t.stackTrace
        val lines = stack.copyOfRange(0, minOf(MAX_LINES, stack.size))
        for (line in lines) sb.append("    ").append(line.toString()).append('\n')
        if (stack.size > MAX_LINES) {
            sb.append("    ... ").append(stack.size - MAX_LINES).append(" weitere Zeilen\n")
        }
        if (depth < MAX_CAUSES) {
            val cause = t.cause
            if (cause != null && cause !== t) {
                sb.append("Verursacht durch: ")
                sb.append(format(cause, depth + 1))
            }
        }
        return sb.toString()
    }

    private fun versionLabel(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        (info.versionName ?: "?") + " (" + info.longVersionCode + ")"
    } catch (t: Throwable) {
        "unbekannt"
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(Date())
}

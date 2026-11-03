package com.finovo.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Texterkennung (OCR) für Rechnungen - komplett auf dem Gerät.
 *
 * Ablauf:  Bild laden -> aufbereiten (ImageEnhancer) -> OCR -> Zeilen samt
 *          Bildposition -> Qualität bewerten -> ggf. zweite Variante
 *          versuchen -> Ergebnis.
 *
 * Die OCR liefert NUR Rohtext. Welcher Wert welches Feld ist, entscheidet
 * ausschließlich der InvoiceParser.
 *
 * Hinweis zur Erkennung: ML Kit lädt sein Erkennungsmodul über die
 * Google-Play-Dienste nach. Damit das direkt nach der Installation passiert
 * und der erste Scan nicht ins Leere läuft, steht im Manifest der Eintrag
 * com.google.mlkit.vision.DEPENDENCIES. Die Erkennung selbst läuft danach
 * offline auf dem Gerät, es verlässt kein Bild das Telefon.
 */
object InvoiceScanner {

    /** Warum ein Scan fehlgeschlagen ist - für verständliche Meldungen. */
    enum class Reason { NO_TEXT, MODEL_NOT_READY, FAILED }

    class ScanException(val reason: Reason, message: String) : Exception(message)

    data class ScanResult(
        /** Erkannter Rohtext (nur eine Vorstufe, kein Rechnungswert!). */
        val text: String,
        /** Erkannte Zeilen inklusive Position auf dem aufbereiteten Bild. */
        val lines: List<OcrLine>,
        /** Pfad des aufbereiteten Bildes im privaten App-Speicher. */
        val imagePath: String?,
        /** 0..1 - wie brauchbar die Erkennung voraussichtlich ist. */
        val quality: Float,
        /** Erkannte Drehung in Grad. */
        val rotation: Float,
        /** Inhalt eines gefundenen QR-/GiroCodes (oder null). */
        val qr: String? = null
    )

    /** Temporäre Datei + Content-URI für die Kamera-App. */
    fun createCameraImage(context: Context): Pair<Uri, File>? = try {
        val dir = File(context.cacheDir, "scan").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        uri to file
    } catch (e: Exception) {
        null
    }

    /**
     * Vollständiger Scan: Bild aufbereiten, Text erkennen, Fundstellen
     * mitschicken. Würfe: [ScanException] mit verständlichem Grund.
     */
    suspend fun scan(context: Context, uri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val source = ImageEnhancer.load(context, uri)
            ?: throw ScanException(Reason.FAILED, "Bild konnte nicht geladen werden")

        val prepared = ImageEnhancer.prepare(source)
        val enhancedPath = saveBitmap(context, prepared.bitmap, "enhanced")

        val first = runOcr(prepared.bitmap)
        var best = first
        var bestQuality = qualityOf(best.first, best.second)

        // Zu wenig erkannt? Dann zweite Variante (Schwellenbild) versuchen.
        if (bestQuality < 0.35f) {
            val binary = ImageEnhancer.binarize(prepared.bitmap)
            val second = runOcr(binary)
            val secondQuality = qualityOf(second.first, second.second)
            if (secondQuality > bestQuality) {
                best = second
                bestQuality = secondQuality
                saveBitmap(context, binary, "enhanced")
            }
        }

        val qr = readQr(source) ?: readQr(prepared.bitmap)

        if (best.first.isBlank() && qr == null) {
            throw ScanException(Reason.NO_TEXT, "Kein Text erkannt")
        }
        ScanResult(
            text = best.first,
            lines = best.second,
            imagePath = enhancedPath,
            quality = bestQuality,
            rotation = prepared.rotation,
            qr = qr
        )
    }

    /** Sucht einen GiroCode (EPC) im Bild; liefert den ersten passenden Inhalt. */
    private fun readQr(bitmap: Bitmap): String? = try {
        val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = com.google.mlkit.vision.barcode.BarcodeScanning.getClient(options)
        try {
            val codes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS)
            codes.mapNotNull { it.rawValue }.firstOrNull { it.startsWith("BCD") }
        } finally {
            scanner.close()
        }
    } catch (e: Exception) {
        null
    }

    /** Alter Weg: nur der Rohtext. Bleibt, damit nichts Altes kaputtgeht. */
    suspend fun recognize(context: Context, uri: Uri): String = scan(context, uri).text

    /**
     * Kopiert das aufgenommene Bild (oder das aufbereitete) in den privaten
     * App-Speicher, damit die Rechnung erhalten bleibt.
     */
    suspend fun copyToAppFiles(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "invoices").apply { mkdirs() }
                val target = File(dir, "invoice_${System.currentTimeMillis()}.jpg")
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                input.use { src -> FileOutputStream(target).use { dst -> src.copyTo(dst) } }
                target.absolutePath
            } catch (e: Exception) {
                null
            }
        }

    // --------------------------------------------------------------- intern

    private suspend fun runOcr(bitmap: Bitmap): Pair<String, List<OcrLine>> {
        // Die Erkennung liefert beim ersten Mal ggf. "Modell wird geladen".
        // Dann kurz warten und erneut versuchen.
        var lastError: Exception? = null
        repeat(3) { attempt ->
            val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                val result = Tasks.await(client.process(InputImage.fromBitmap(bitmap, 0)), 60, TimeUnit.SECONDS)
                val lines = ArrayList<OcrLine>()
                val text = StringBuilder()
                for (block in result.textBlocks) {
                    for (line in block.lines) {
                        val box = line.boundingBox
                        lines += OcrLine(
                            text = line.text,
                            left = box?.left?.toFloat() ?: 0f,
                            top = box?.top?.toFloat() ?: 0f,
                            right = box?.right?.toFloat() ?: 0f,
                            bottom = box?.bottom?.toFloat() ?: 0f
                        )
                        text.appendLine(line.text)
                    }
                }
                return text.toString().trim() to lines
            } catch (e: Exception) {
                lastError = e
                val waiting = e is MlKitException ||
                    (e.message?.contains("Waiting for the text recognition model", true) == true) ||
                    (e.message?.contains("model", true) == true && e.message?.contains("download", true) == true)
                if (!waiting) throw e
                if (attempt < 2) delay(2500L * (attempt + 1))
            } finally {
                try {
                    client.close()
                } catch (ignored: Exception) {
                }
            }
        }
        throw ScanException(
            Reason.MODEL_NOT_READY,
            lastError?.localizedMessage ?: "Texterkennung nicht bereit"
        )
    }

    /** Bewertet, wie brauchbar die Erkennung voraussichtlich ist (0..1). */
    private fun qualityOf(text: String, lines: List<OcrLine>): Float {
        if (text.isBlank()) return 0f
        val lineScore = (lines.size / 22f).coerceAtMost(1f)
        val digitScore = (text.count { it.isDigit() } / 90f).coerceAtMost(1f)
        val moneyScore = if (Regex("""\d+[.,]\d{2}""").containsMatchIn(text)) 1f else 0f
        val labelScore = if (Regex("""(?i)(gesamt|total|summe|rechnung|invoice|fattura|fatura|toplam)""")
                .containsMatchIn(text)
        ) 1f else 0f
        return (lineScore * 0.35f + digitScore * 0.25f + moneyScore * 0.2f + labelScore * 0.2f)
            .coerceIn(0f, 1f)
    }

    private fun saveBitmap(context: Context, bitmap: Bitmap, prefix: String): String? = try {
        val dir = File(context.filesDir, "invoices").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

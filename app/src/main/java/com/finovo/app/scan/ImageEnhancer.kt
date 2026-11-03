package com.finovo.app.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bildverbesserung vor der Texterkennung.
 *
 * Ein Handyfoto einer Rechnung ist meist schief, zu dunkel, hat Schatten und
 * einen unruhigen Hintergrund. Ohne Aufbereitung liefert die OCR deutlich
 * mehr Fehler - und falsche Zahlen landen in der Rechnung.
 *
 * Alle Schritte laufen komplett auf dem Gerät (kein Netz, keine Cloud):
 *   1. Bild laden (speicherschonend) + Kamera-Drehung ausgleichen (EXIF)
 *   2. Neigung erkennen und drehen
 *   3. Rechnung zuschneiden (Hintergrund entfernen)
 *   4. Perspektive korrigieren, wenn ein sauberes Viereck gefunden wird
 *   5. Kontrast/Beleuchtung ausgleichen, Hintergrund beruhigen, schärfen
 */
object ImageEnhancer {

    private const val TAG = "ImageEnhancer"

    data class Prepared(
        /** Aufbereitetes Bild (wird gespeichert und dem Benutzer gezeigt). */
        val bitmap: Bitmap,
        /** Erkannte Drehung in Grad (für die Anzeige). */
        val rotation: Float,
        /** true, wenn das Dokument erkannt und beschnitten wurde. */
        val cropped: Boolean
    )

    // ------------------------------------------------------------------ API

    /** Lädt ein Bild aus einer URI, dreht es nach EXIF und begrenzt die Größe. */
    fun load(context: Context, uri: Uri, maxSize: Int = 1600): Bitmap? = try {
        val bounds = BitmapFactoryOptions()
        context.contentResolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds.apply { inJustDecodeBounds = true })
        }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            null
        } else {
            var sample = 1
            while (max(width, height) / sample > maxSize) sample *= 2
            val opts = BitmapFactoryOptions().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
            val rotated = bmp?.let { rotate(it, exifRotation(context, uri)) }
            rotated
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Bild konnte nicht geladen werden", t)
        null
    }

    private fun BitmapFactoryOptions(): android.graphics.BitmapFactory.Options =
        android.graphics.BitmapFactory.Options()

    /** Komplette Aufbereitung. Fällt bei jedem Problem auf das Original zurück. */
    fun prepare(source: Bitmap): Prepared = try {
        val working = limitSize(source, 1600)
        val angle = detectSkew(working)
        val rotated = if (abs(angle) >= 0.4f) rotate(working, angle) else working
        val rectified = rectifyPerspective(rotated)
        val cropped = cropToContent(rectified)
        val enhanced = enhance(cropped)
        Prepared(enhanced, angle, cropped.width != rotated.width || cropped.height != rotated.height)
    } catch (t: Throwable) {
        Log.w(TAG, "Aufbereitung fehlgeschlagen - Original wird verwendet", t)
        Prepared(source, 0f, false)
    }

    /** Kontrast/Helligkeit ausgleichen, Hintergrund beruhigen, Text schärfen. */
    fun enhance(src: Bitmap): Bitmap = try {
        val size = src.width * src.height
        val pixels = IntArray(size)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        // 1) Helligkeits-/Kontrast-Automatik über das Histogramm
        val gray = IntArray(size)
        val hist = IntArray(256)
        for (i in 0 until size) {
            val p = pixels[i]
            val g = ((0.299 * Color.red(p)) + (0.587 * Color.green(p)) + (0.114 * Color.blue(p))).toInt()
            gray[i] = g
            hist[g]++
        }
        var low = 0
        var high = 255
        val cut = (size * 0.02).toInt()
        var acc = 0
        for (v in 0..255) {
            acc += hist[v]
            if (acc > cut) {
                low = v
                break
            }
        }
        acc = 0
        for (v in 255 downTo 0) {
            acc += hist[v]
            if (acc > cut) {
                high = v
                break
            }
        }
        val span = (high - low).coerceAtLeast(12)
        val scale = 255f / span
        val lut = IntArray(256)
        for (v in 0..255) {
            lut[v] = ((v - low) * scale).roundToInt().coerceIn(0, 255)
        }

        // 2) Leichte Schärfung (Unschärfe-Maske) auf den Grauwerten
        val w = src.width
        val h = src.height
        val sharp = IntArray(size)
        for (i in 0 until size) sharp[i] = lut[gray[i]]
        val tmp = IntArray(size)
        val kernel = intArrayOf(0, -1, 0, -1, 8, -1, 0, -1, 0)
        val divisor = 4
        for (y in 1 until h - 1) {
            var x = 1
            while (x < w - 1) {
                val idx = y * w + x
                var sum = 0
                var k = 0
                for (ky in -1..1) {
                    val row = (y + ky) * w
                    for (kx in -1..1) {
                        sum += sharp[row + x + kx] * kernel[k]
                        k++
                    }
                }
                val v = sum / divisor - sharp[idx]
                tmp[idx] = (sharp[idx] + (v * 0.35f).roundToInt()).coerceIn(0, 255)
                x++
            }
        }
        // Ränder übernehmen
        for (i in 0 until size) {
            if (i % w == 0 || i % w == w - 1 || i < w || i > size - w) tmp[i] = sharp[i]
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(size)
        for (i in 0 until size) {
            val g = tmp[i]
            outPixels[i] = 0xFF000000.toInt() or (g shl 16) or (g shl 8) or g
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        out
    } catch (t: Throwable) {
        Log.w(TAG, "Kontrastverbesserung fehlgeschlagen", t)
        src
    }

    /**
     * Zweite Variante für schwierige Beleuchtung: lokale Schwelle
     * (Hintergrund wird weiß, Text schwarz). Wird nur benutzt, wenn die
     * Erkennung auf dem normalen Bild zu wenig Text liefert.
     */
    fun binarize(src: Bitmap): Bitmap = try {
        val w = src.width
        val h = src.height
        val size = w * h
        val pixels = IntArray(size)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(size)
        for (i in 0 until size) {
            val p = pixels[i]
            gray[i] = ((0.299 * Color.red(p)) + (0.587 * Color.green(p)) + (0.114 * Color.blue(p))).toInt()
        }
        // Integrales Bild für schnelle lokale Mittelwerte
        val integral = LongArray(size)
        for (y in 0 until h) {
            var rowSum = 0L
            for (x in 0 until w) {
                rowSum += gray[y * w + x]
                integral[y * w + x] = rowSum + (if (y > 0) integral[(y - 1) * w + x] else 0L)
            }
        }
        val radius = (min(w, h) * 0.02).toInt().coerceAtLeast(8)
        val outPixels = IntArray(size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x0 = (x - radius).coerceAtLeast(0)
                val y0 = (y - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(w - 1)
                val y1 = (y + radius).coerceAtMost(h - 1)
                val area = ((x1 - x0 + 1) * (y1 - y0 + 1)).toLong()
                val sum = integral[y1 * w + x1] -
                    (if (y0 > 0) integral[(y0 - 1) * w + x1] else 0L) -
                    (if (x0 > 0) integral[y1 * w + x0 - 1] else 0L) +
                    (if (y0 > 0 && x0 > 0) integral[(y0 - 1) * w + x0 - 1] else 0L)
                val mean = (sum / area).toInt()
                val v = gray[y * w + x]
                val value = if (v < mean - 4) 0 else 255
                outPixels[y * w + x] = 0xFF000000.toInt() or (value shl 16) or (value shl 8) or value
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        out
    } catch (t: Throwable) {
        Log.w(TAG, "Schwellenbild fehlgeschlagen", t)
        src
    }

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return src
        val matrix = Matrix().apply { postRotate(degrees) }
        return try {
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            if (out !== src && src.isRecycled.not()) {
                // Original wird nicht mehr gebraucht
            }
            out
        } catch (t: Throwable) {
            src
        }
    }

    // ------------------------------------------------------------ Neigung

    /**
     * Klassische Schräglagenerkennung: das Bild wird in kleinen Schritten
     * gedreht; bei der richtigen Drehung sind die Textzeilen waagerecht und
     * die Zeilen-Summe des Kantenbilds hat die größte Streuung.
     */
    private fun detectSkew(src: Bitmap): Float {
        val small = limitSize(src, 400)
        val gray = grayOf(small)
        val edges = sobel(gray, small.width, small.height)
        var bestAngle = 0f
        var bestScore = varianceOfRowSums(edges, small.width, small.height)
        var angle = -8f
        while (angle <= 8f) {
            if (abs(angle) > 0.01f) {
                val rotated = rotateEdges(edges, small.width, small.height, angle)
                val score = varianceOfRowSums(rotated.first, rotated.second, rotated.third)
                if (score > bestScore) {
                    bestScore = score
                    bestAngle = angle
                }
            }
            angle += 0.5f
        }
        return bestAngle
    }

    private fun rotateEdges(src: FloatArray, w: Int, h: Int, degrees: Float): Triple<FloatArray, Int, Int> {
        val rad = Math.toRadians(degrees.toDouble())
        val cos = cos(rad)
        val sin = sin(rad)
        val nw = (abs(w * cos) + abs(h * sin)).toInt()
        val nh = (abs(w * sin) + abs(h * cos)).toInt()
        val out = FloatArray(nw * nh)
        val cx = w / 2.0
        val cy = h / 2.0
        val ncx = nw / 2.0
        val ncy = nh / 2.0
        for (y in 0 until nh) {
            for (x in 0 until nw) {
                val dx = x - ncx
                val dy = y - ncy
                val sx = (dx * cos + dy * sin + cx).toInt()
                val sy = (-dx * sin + dy * cos + cy).toInt()
                if (sx in 0 until w && sy in 0 until h) out[y * nw + x] = src[sy * w + sx]
            }
        }
        return Triple(out, nw, nh)
    }

    private fun varianceOfRowSums(edges: FloatArray, w: Int, h: Int): Double {
        val sums = DoubleArray(h)
        for (y in 0 until h) {
            var s = 0.0
            val row = y * w
            for (x in 0 until w) s += edges[row + x].toDouble()
            sums[y] = s
        }
        var mean = 0.0
        for (v in sums) mean += v
        mean /= h
        var variance = 0.0
        for (v in sums) variance += (v - mean) * (v - mean)
        return variance / h
    }

    private fun grayOf(src: Bitmap): IntArray {
        val size = src.width * src.height
        val pixels = IntArray(size)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        val gray = IntArray(size)
        for (i in 0 until size) {
            val p = pixels[i]
            gray[i] = ((0.299 * Color.red(p)) + (0.587 * Color.green(p)) + (0.114 * Color.blue(p))).toInt()
        }
        return gray
    }

    private fun sobel(gray: IntArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gx = -gray[i - w - 1] - 2 * gray[i - 1] - gray[i + w - 1] +
                    gray[i - w + 1] + 2 * gray[i + 1] + gray[i + w + 1]
                val gy = -gray[i - w - 1] - 2 * gray[i - w] - gray[i - w + 1] +
                    gray[i + w - 1] + 2 * gray[i + w] + gray[i + w + 1]
                out[i] = sqrt((gx * gx + gy * gy).toDouble()).toFloat()
            }
        }
        return out
    }

    // ------------------------------------------------------------ Zuschnitt

    /** Schneidet den Hintergrund weg (Profil der Kantenstärken). */
    private fun cropToContent(src: Bitmap, marginPercent: Float = 0.02f): Bitmap {
        val small = limitSize(src, 400)
        val gray = grayOf(small)
        val edges = sobel(gray, small.width, small.height)
        val w = small.width
        val h = small.height

        val rowSum = FloatArray(h)
        val colSum = FloatArray(w)
        for (y in 0 until h) {
            var s = 0f
            for (x in 0 until w) s += edges[y * w + x]
            rowSum[y] = s
        }
        for (x in 0 until w) {
            var s = 0f
            for (y in 0 until h) s += edges[y * w + x]
            colSum[x] = s
        }
        val top = firstAbove(rowSum, 0.12f)
        val bottom = lastAbove(rowSum, 0.12f)
        val left = firstAbove(colSum, 0.12f)
        val right = lastAbove(colSum, 0.12f)
        if (top < 0 || bottom < 0 || left < 0 || right < 0) return src

        val sx = src.width.toFloat() / w
        val sy = src.height.toFloat() / h
        val mx = (src.width * marginPercent).toInt()
        val my = (src.height * marginPercent).toInt()
        val x0 = (left * sx - mx).toInt().coerceIn(0, src.width - 1)
        val y0 = (top * sy - my).toInt().coerceIn(0, src.height - 1)
        val x1 = (right * sx + mx).toInt().coerceIn(x0 + 1, src.width)
        val y1 = (bottom * sy + my).toInt().coerceIn(y0 + 1, src.height)
        val cw = x1 - x0
        val ch = y1 - y0
        if (cw < src.width * 0.25 || ch < src.height * 0.25) return src
        return try {
            Bitmap.createBitmap(src, x0, y0, cw, ch)
        } catch (t: Throwable) {
            src
        }
    }

    private fun firstAbove(values: FloatArray, ratio: Float): Int {
        val max = values.maxOrNull() ?: return -1
        val limit = max * ratio
        for (i in values.indices) if (values[i] >= limit) return i
        return -1
    }

    private fun lastAbove(values: FloatArray, ratio: Float): Int {
        val max = values.maxOrNull() ?: return -1
        val limit = max * ratio
        for (i in values.indices.reversed()) if (values[i] >= limit) return i
        return -1
    }

    // --------------------------------------------------------- Perspektive

    /**
     * Perspektive korrigieren: sucht die vier Dokumentecken über die stärksten
     * Kanten an den Bildrändern und rechnet das Viereck auf ein Rechteck um.
     * Nur wenn das gefundene Viereck plausibel ist - sonst bleibt das Bild.
     */
    private fun rectifyPerspective(src: Bitmap): Bitmap {
        val small = limitSize(src, 320)
        val gray = grayOf(small)
        val edges = sobel(gray, small.width, small.height)
        val w = small.width
        val h = small.height

        val tl = findCorner(edges, w, h, 0, 0, 1, 1)
        val tr = findCorner(edges, w, h, w - 1, 0, -1, 1)
        val br = findCorner(edges, w, h, w - 1, h - 1, -1, -1)
        val bl = findCorner(edges, w, h, 0, h - 1, 1, -1)
        if (tl == null || tr == null || br == null || bl == null) return src

        // Plausibilität: konvex, groß genug, Kanten nicht zu schief
        val quad = floatArrayOf(tl.first, tl.second, tr.first, tr.second, br.first, br.second, bl.first, bl.second)
        if (!isPlausible(quad, w, h)) return src

        val sx = src.width.toFloat() / w
        val sy = src.height.toFloat() / h
        val srcPts = floatArrayOf(
            tl.first * sx, tl.second * sy,
            tr.first * sx, tr.second * sy,
            br.first * sx, br.second * sy,
            bl.first * sx, bl.second * sy
        )
        val widthTop = distance(srcPts[0], srcPts[1], srcPts[2], srcPts[3])
        val widthBottom = distance(srcPts[6], srcPts[7], srcPts[4], srcPts[5])
        val heightLeft = distance(srcPts[0], srcPts[1], srcPts[6], srcPts[7])
        val heightRight = distance(srcPts[2], srcPts[3], srcPts[4], srcPts[5])
        val dstW = max(widthTop, widthBottom).toInt().coerceIn(200, 3000)
        val dstH = max(heightLeft, heightRight).toInt().coerceIn(200, 4000)
        val dstPts = floatArrayOf(0f, 0f, dstW.toFloat(), 0f, dstW.toFloat(), dstH.toFloat(), 0f, dstH.toFloat())

        return try {
            val matrix = Matrix()
            if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) return src
            val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(src, matrix, paint)
            out
        } catch (t: Throwable) {
            Log.w(TAG, "Perspektivkorrektur fehlgeschlagen", t)
            src
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toDouble()).toFloat()

    /** Sucht von einer Bildecke aus die stärkste Kante auf der Diagonalen. */
    private fun findCorner(
        edges: FloatArray,
        w: Int,
        h: Int,
        startX: Int,
        startY: Int,
        dirX: Int,
        dirY: Int
    ): Pair<Float, Float>? {
        val maxSteps = (min(w, h) * 0.45).toInt()
        var bestScore = 0f
        var bestX = -1f
        var bestY = -1f
        var step = 0
        while (step < maxSteps) {
            val x = startX + dirX * step
            val y = startY + dirY * step
            if (x < 0 || y < 0 || x >= w || y >= h) break
            val value = edges[y * w + x]
            // Kanten nahe der Ecke sind verdächtig (Rand des Fotos)
            val score = value * (1f - step.toFloat() / (maxSteps * 1.5f))
            if (score > bestScore) {
                bestScore = score
                bestX = x.toFloat()
                bestY = y.toFloat()
            }
            step++
        }
        if (bestScore <= 0f) return null
        return bestX to bestY
    }

    private fun isPlausible(q: FloatArray, w: Int, h: Int): Boolean {
        // Viereck muss konvex sein (Kreuzprodukt-Test)
        var sign = 0
        for (i in 0 until 4) {
            val ax = q[(i * 2) % 8]
            val ay = q[(i * 2 + 1) % 8]
            val bx = q[((i + 1) * 2) % 8]
            val by = q[((i + 1) * 2 + 1) % 8]
            val cx = q[((i + 2) * 2) % 8]
            val cy = q[((i + 2) * 2 + 1) % 8]
            val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
            if (abs(cross) < 0.0001f) continue
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s
            else if (sign != s) return false
        }
        // Fläche nach Gaußscher Trapezformel
        var area = 0f
        for (i in 0 until 4) {
            val x1 = q[(i * 2) % 8]
            val y1 = q[(i * 2 + 1) % 8]
            val x2 = q[((i + 1) * 2) % 8]
            val y2 = q[((i + 1) * 2 + 1) % 8]
            area += x1 * y2 - x2 * y1
        }
        area = abs(area) / 2f
        val imageArea = (w * h).toFloat()
        return area >= imageArea * 0.22f && area <= imageArea * 0.98f
    }

    // ------------------------------------------------------------- Helfer

    private fun limitSize(src: Bitmap, maxSize: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSize) return src
        val factor = maxSize.toFloat() / longest
        val w = (src.width * factor).toInt().coerceAtLeast(1)
        val h = (src.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /** Kamera-Drehung aus den EXIF-Daten (0/90/180/270). */
    fun exifRotation(context: Context, uri: Uri): Float = try {
        val input = context.contentResolver.openInputStream(uri) ?: return 0f
        val exif = if (Build.VERSION.SDK_INT >= 24) ExifInterface(input) else return 0f
        when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }.also { input.close() }
    } catch (t: Throwable) {
        0f
    }
}

package com.finovo.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {

    /** Valid range of LocalDate as epochDay - everything outside is clamped. */
    private const val MIN_EPOCH_DAY = -365243219162L
    private const val MAX_EPOCH_DAY = 365241780471L

    private fun loc(): Locale = AppLocale.locale()

    /** de/sq "dd.MM.yyyy", en "MM/dd/yyyy", it "dd/MM/yyyy" */
    private fun pattern(withYear: Boolean): String = when (AppLocale.current()) {
        "en" -> if (withYear) "MM/dd/yyyy" else "MM/dd"
        "it" -> if (withYear) "dd/MM/yyyy" else "dd/MM"
        else -> if (withYear) "dd.MM.yyyy" else "dd.MM."
    }

    /** de/sq "Montag, 17. September 2026", en/it "Monday, September 17, 2026" */
    private fun longPattern(): String = when (AppLocale.current()) {
        "en", "it" -> "EEEE, d MMMM yyyy"
        else -> "EEEE, d. MMMM yyyy"
    }

    fun today(): LocalDate = LocalDate.now()

    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()

    /**
     * Converts a stored day back into a date.
     *
     * A single corrupt value (imported backup, broken OCR field, ...) used to
     * throw a DateTimeException and took the whole app down - the value is now
     * clamped to the valid range of [LocalDate] instead.
     */
    fun fromEpochDay(epochDay: Long): LocalDate =
        LocalDate.ofEpochDay(epochDay.coerceIn(MIN_EPOCH_DAY, MAX_EPOCH_DAY))

    /** Same protection for time stamps. */
    fun millisToLocalDate(millis: Long): LocalDate = runCatching {
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrDefault(LocalDate.of(1970, 1, 1))

    // ------------------------------------------------------------- Formate

    /**
     * Formatiert ein Datum so, dass die App dabei NIE abstuerzen kann.
     *
     * Ein [LocalDate] kennt keine Stunden, Minuten und Sekunden. Wird ihm
     * trotzdem ein Muster mit solchen Feldern uebergeben ("HH", "mm", "ss"),
     * wirft Java eine UnsupportedTemporalTypeException - genau das hat die App
     * beim Backup-Dateinamen zum Absturz gebracht.
     *
     * Diese eine Stelle faengt den Fehler ab, schreibt die Ursache mit Datum
     * und Muster ins Fehlerprotokoll (sie wird also sichtbar und kann behoben
     * werden - nicht verschwiegen) und liefert stattdessen das immer gueltige
     * ISO-Datum, zum Beispiel "2026-09-19".
     */
    private fun formatSicher(date: LocalDate, muster: DateTimeFormatter): String =
        runCatching { date.format(muster) }
            .onFailure {
                CrashGuard.trace(
                    "Datum $date mit Muster \"$muster\": ${it.javaClass.simpleName}: ${it.message}"
                )
            }
            .getOrDefault(date.toString())

    /** "01.10." */
    fun short(date: LocalDate): String =
        formatSicher(date, DateTimeFormatter.ofPattern(pattern(false), loc()))

    /** "01.10.2026" */
    fun full(date: LocalDate): String =
        formatSicher(date, DateTimeFormatter.ofPattern(pattern(true), loc()))

    /** "Montag, 12. September 2026" */
    fun long(date: LocalDate): String =
        formatSicher(date, DateTimeFormatter.ofPattern(longPattern(), loc()))

    /** "September 2026" */
    fun monthYear(date: LocalDate): String =
        formatSicher(date, DateTimeFormatter.ofPattern("MMMM yyyy", loc())).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(loc()) else it.toString()
        }

    fun monthKey(date: LocalDate): String =
        formatSicher(date, DateTimeFormatter.ofPattern("yyyy-MM", loc()))

    fun weekdayShort(date: LocalDate): String =
        formatSicher(date, DateTimeFormatter.ofPattern("EEE", loc()))

    /**
     * Wochenanfang. Am Rand des Kalenders (z. B. beim letzten gueltigen Tag)
     * wuerde "with(MONDAY)" ueber das Jahr 999999999 hinaus laufen und mit
     * einer DateTimeException abbrechen - dann bleibt der Tag unveraendert.
     */
    fun startOfWeek(date: LocalDate): LocalDate =
        runCatching { date.with(DayOfWeek.MONDAY) }.getOrDefault(date)

    fun endOfWeek(date: LocalDate): LocalDate =
        runCatching { date.with(DayOfWeek.SUNDAY) }.getOrDefault(date)

    fun startOfMonth(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    fun endOfMonth(date: LocalDate): LocalDate = date.withDayOfMonth(date.lengthOfMonth())

    fun startOfYear(date: LocalDate): LocalDate = date.withDayOfYear(1)

    fun endOfYear(date: LocalDate): LocalDate = date.withDayOfYear(date.lengthOfYear())

    fun monthRange(date: LocalDate): Pair<Long, Long> =
        startOfMonth(date).toEpochDay() to endOfMonth(date).toEpochDay()

    fun daysUntil(date: LocalDate, from: LocalDate = today()): Long =
        ChronoUnit.DAYS.between(from, date)

    /** "Heute", "Morgen", "in 5 Tagen", "vor 3 Tagen" ... */
    fun relative(date: LocalDate, from: LocalDate = today()): String {
        val days = daysUntil(date, from)
        return when {
            days == 0L -> str("heute", "Heute")
            days == 1L -> str("morgen", "Morgen")
            days == -1L -> str("gestern", "Gestern")
            days > 1 -> plurals("pl_in_days", days.toInt(), "in %1\$d Tag", "in %1\$d Tagen")
            else -> plurals("pl_days_ago", (-days).toInt(), "vor %1\$d Tag", "vor %1\$d Tagen")
        }
    }

    /** Localised short month label for charts: "Sep" */
    fun chartMonth(date: LocalDate): String =
        formatSicher(date, DateTimeFormatter.ofPattern("MMM", loc()))

    fun yearMonth(date: LocalDate): YearMonth = YearMonth.from(date)

    /**
     * "09:05" - Stunden und Minuten werden begrenzt. Ein unmoeglicher Wert aus
     * einer alten Sicherung erzeugt so keine unsinnige Anzeige wie "99:99".
     */
    fun formatTime(hour: Int, minute: Int): String =
        String.format(AppLocale.locale(), "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    fun dateMedium(date: LocalDate): String =
        formatSicher(date, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(loc()))

    // ------------------------------------------------- fehlende Daten (null)

    /** Fehlendes Datum: leerer Text - nie "null", nie ein Absturz. */
    fun fullOrEmpty(date: LocalDate?): String = date?.let { full(it) } ?: ""

    /** Fehlendes Datum: Gedankenstrich. */
    fun fullOrDash(date: LocalDate?): String = date?.let { full(it) } ?: "–"

    fun shortOrEmpty(date: LocalDate?): String = date?.let { short(it) } ?: ""

    /** Aus einem gespeicherten Tag (epochDay). null = kein Datum gesetzt. */
    fun fullOrEmpty(epochDay: Long?): String = epochDay?.let { full(fromEpochDay(it)) } ?: ""

    fun fullOrDash(epochDay: Long?): String = epochDay?.let { full(fromEpochDay(it)) } ?: "–"

    fun shortOrEmpty(epochDay: Long?): String = epochDay?.let { short(fromEpochDay(it)) } ?: ""

    // ------------------------------------------------------ Datums-Rechnen

    /**
     * Tage/Monate/Jahre addieren, ohne jemals ueber das Ende des Kalenders
     * hinaus zu laufen.
     *
     * "LocalDate.MAX.plusDays(1)" wirft eine DateTimeException und reisst die
     * App mit - bei Daten aus einer alten Sicherung oder einem fehlerhaft
     * erkannten Datum ist das erreichbar. Das Ergebnis wird deshalb auf den
     * letzten bzw. ersten gueltigen Tag begrenzt.
     */
    fun plusTage(date: LocalDate, tage: Long): LocalDate =
        runCatching { date.plusDays(tage) }
            .getOrDefault(if (tage >= 0) LocalDate.MAX else LocalDate.MIN)

    fun plusMonate(date: LocalDate, monate: Long): LocalDate =
        runCatching { date.plusMonths(monate) }
            .getOrDefault(if (monate >= 0) LocalDate.MAX else LocalDate.MIN)

    fun plusJahre(date: LocalDate, jahre: Long): LocalDate =
        runCatching { date.plusYears(jahre) }
            .getOrDefault(if (jahre >= 0) LocalDate.MAX else LocalDate.MIN)

    /** Multiplikation mit Ergebnisbegrenzung (kein stiller Ueberlauf). */
    fun mal(a: Long, b: Long): Long =
        runCatching { Math.multiplyExact(a, b) }
            .getOrDefault(if ((a > 0) xor (b > 0)) Long.MIN_VALUE else Long.MAX_VALUE)

    /** Ein Datum in Millisekunden - ohne Ueberlauf. */
    fun zuMillis(date: LocalDate): Long =
        runCatching { Math.multiplyExact(date.toEpochDay(), 86_400_000L) }.getOrDefault(0L)
}

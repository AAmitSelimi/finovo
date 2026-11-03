package com.finovo.app.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * All money in this app is handled as a whole number of cents (Long).
 * Parsing uses BigDecimal, so no floating point rounding errors can ever appear.
 */
object Money {

    private const val MAX_CENTS = 99_999_999_999L // 999.999.999,99

    /** 123456 -> "1.234,56 €" (German formatting) */
    fun format(cents: Long, symbol: String = "€", withSymbol: Boolean = true): String {
        val negative = cents < 0
        val value = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(cents)
        val whole = value / 100
        val frac = value % 100
        val sb = StringBuilder()
        if (negative) sb.append('-')
        sb.append(groupThousands(whole))
        sb.append(decimalSeparator())
        sb.append(frac.toString().padStart(2, '0'))
        if (withSymbol && symbol.isNotBlank()) sb.append(' ').append(symbol)
        return sb.toString()
    }

    /** Compact form for very large amounts on charts, e.g. "1,2 Mio." */
    fun formatCompact(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val sign = if (cents < 0) "-" else ""
        return when {
            abs >= 100_000_000L -> sign + groupThousands(abs / 100_000_0) // >= 1 Mio
            abs >= 1_000_000L -> sign + (abs / 100).toString()
            else -> sign + groupThousands(abs / 100)
        }
    }

    /** "." in English, "," in German, Italian and Albanian. */
    fun decimalSeparator(): Char = if (AppLocale.current() == "en") '.' else ','

    fun groupThousands(value: Long, sep: Char = if (AppLocale.current() == "en") ',' else '.'): String {
        val s = value.toString()
        val out = StringBuilder()
        for (i in s.indices) {
            val fromEnd = s.length - i
            out.append(s[i])
            if (fromEnd > 1 && (fromEnd - 1) % 3 == 0) out.append(sep)
        }
        return out.toString()
    }

    /**
     * Parses user input such as "12,50", "12.50", "1.234,56", "1234" or "12".
     * Returns null when the text is not a valid amount.
     */
    fun parseToCents(input: String): Long? {
        var raw = input.trim()
            .replace(" ", "")
            .replace("€", "")
            .replace("$", "")
            .replace(" ", "")
            .replace("'", "")
        if (raw.isEmpty()) return null
        var negative = false
        if (raw.startsWith("-")) {
            negative = true
            raw = raw.substring(1)
        } else if (raw.startsWith("+")) {
            raw = raw.substring(1)
        }
        if (raw.isEmpty()) return null
        if (!raw.matches(Regex("[0-9.,]+"))) return null
        if (raw.startsWith(",") || raw.startsWith(".")) raw = "0$raw"

        val lastComma = raw.lastIndexOf(',')
        val lastDot = raw.lastIndexOf('.')
        val decimalSeparator: Char? = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) ',' else '.'
            lastComma >= 0 -> ','
            lastDot >= 0 -> '.'
            else -> null
        }

        val normalized: String = if (decimalSeparator == null) {
            raw.replace(",", "").replace(".", "")
        } else {
            val sepIndex = raw.lastIndexOf(decimalSeparator)
            val intPart = raw.substring(0, sepIndex).replace(",", "").replace(".", "")
            val fracPart = raw.substring(sepIndex + 1).replace(",", "").replace(".", "")
            val onlyOneSeparatorKind = lastComma < 0 || lastDot < 0
            // "1.234"/"1,234" with a single separator and exactly three following
            // digits is a German thousands separator, not a decimal separator.
            if (fracPart.length == 3 && onlyOneSeparatorKind) {
                intPart + fracPart
            } else {
                if (fracPart.length > 2) return null
                if (fracPart.isEmpty()) intPart else "$intPart.$fracPart"
            }
        }
        if (normalized.isEmpty() || normalized.all { it == '0' }) return 0L

        return try {
            val bd = BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP)
            val cents = bd.movePointRight(2).longValueExact()
            if (cents > MAX_CENTS) return null
            if (negative) -cents else cents
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Returns a German error message or null when the input is a valid positive amount. */
    fun validate(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return str("err_amount_empty", "Bitte gib einen Betrag ein.")
        if (trimmed.startsWith("-")) return str("err_amount_negative", "Der Betrag darf nicht negativ sein.")
        val cents = parseToCents(trimmed)
            ?: return str("err_amount_invalid", "Bitte gib einen gültigen Betrag ein (z. B. 12,50).")
        if (cents <= 0) return str("err_amount_zero", "Der Betrag muss größer als 0 sein.")
        return null
    }
}

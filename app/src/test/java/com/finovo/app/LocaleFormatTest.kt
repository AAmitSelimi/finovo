package com.finovo.app

import com.finovo.app.util.AppLocale
import com.finovo.app.util.Dates
import com.finovo.app.util.Money
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Locale aware formatting: numbers, money and dates must follow the selected
 * language while the currency always stays EUR (€).
 */
class LocaleFormatTest {

    @After
    fun reset() {
        AppLocale.apply("de")
    }

    // ------------------------------------------------------------------ money

    @Test
    fun moneyUsesGermanSeparators() {
        AppLocale.apply("de")
        assertEquals("1.234,56", Money.format(123456, withSymbol = false))
        assertEquals("-1.234,56", Money.format(-123456, withSymbol = false))
        assertEquals("0,00", Money.format(0, withSymbol = false))
    }

    @Test
    fun moneyUsesEnglishSeparators() {
        AppLocale.apply("en")
        assertEquals("1,234.56", Money.format(123456, withSymbol = false))
        assertEquals("-1,234.56", Money.format(-123456, withSymbol = false))
    }

    @Test
    fun moneyUsesItalianAndAlbanianSeparators() {
        AppLocale.apply("it")
        assertEquals("1.234,56", Money.format(123456, withSymbol = false))
        AppLocale.apply("sq")
        assertEquals("1.234,56", Money.format(123456, withSymbol = false))
    }

    @Test
    fun currencyStaysEuroInEveryLanguage() {
        for (code in AppLocale.SUPPORTED) {
            AppLocale.apply(code)
            assert(Money.format(500).endsWith("€"))
        }
    }

    @Test
    fun noFloatingPointErrorsInMoney() {
        AppLocale.apply("de")
        assertEquals("0,30", Money.format(10 + 10 + 10, withSymbol = false))
        assertEquals("1.000.000,00", Money.format(100_000_000, withSymbol = false))
    }

    // ------------------------------------------------------------------ dates

    private val date = LocalDate.of(2026, 9, 17)

    @Test
    fun dateFormatsFollowTheLanguage() {
        AppLocale.apply("de")
        assertEquals("17.09.2026", Dates.full(date))
        assertEquals("17.09.", Dates.short(date))
        AppLocale.apply("en")
        assertEquals("09/17/2026", Dates.full(date))
        assertEquals("09/17", Dates.short(date))
        AppLocale.apply("it")
        assertEquals("17/09/2026", Dates.full(date))
        assertEquals("17/09", Dates.short(date))
        AppLocale.apply("sq")
        assertEquals("17.09.2026", Dates.full(date))
    }

    @Test
    fun monthNamesAreLocalized() {
        AppLocale.apply("de")
        assertEquals("September 2026", Dates.monthYear(date))
        AppLocale.apply("en")
        assertEquals("September 2026", Dates.monthYear(date))
        AppLocale.apply("it")
        assertEquals("Settembre 2026", Dates.monthYear(date))
    }

    @Test
    fun machineReadableKeysAreLanguageIndependent() {
        AppLocale.apply("it")
        assertEquals("2026-09", Dates.monthKey(date))
        AppLocale.apply("en")
        assertEquals("2026-09", Dates.monthKey(date))
    }

    // ----------------------------------------------------------------- locale

    @Test
    fun unsupportedLanguageFallsBackToGerman() {
        AppLocale.apply("fr")
        assertEquals("de", AppLocale.current())
        assertEquals("1.234,56", Money.format(123456, withSymbol = false))
    }

    // ----------------------------------------------------------------- input

    @Test
    fun amountInputAcceptsBothSeparators() {
        AppLocale.apply("de")
        assertEquals(123456L, Money.parseToCents("1.234,56"))
        AppLocale.apply("en")
        assertEquals(123456L, Money.parseToCents("1,234.56"))
        assertEquals(1234L, Money.parseToCents("12,34"))
        assertNull(Money.parseToCents("abc"))
    }
}

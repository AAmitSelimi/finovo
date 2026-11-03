package com.finovo.app

import com.finovo.app.bank.BankApiClient
import com.finovo.app.bank.TransactionCategorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the automatic transaction classification and amount parsing. */
class BankCategorizerTest {

    private fun suggest(merchant: String, description: String = "", cents: Long = -1000L) =
        TransactionCategorizer.suggest(merchant, description, cents)

    @Test
    fun reweIsGroceries() {
        val r = suggest("REWE Markt GmbH")
        assertEquals("Lebensmittel", r.categoryName)
        assertTrue(r.isConfident)
    }

    @Test
    fun aralIsFuel() {
        val r = suggest("ARAL Tankstelle")
        assertEquals("Tanken", r.categoryName)
        assertTrue(r.isConfident)
    }

    @Test
    fun eonIsElectricity() {
        val r = suggest("E.ON Energie")
        assertEquals("Strom", r.categoryName)
        assertTrue(r.isConfident)
    }

    @Test
    fun vodafoneIsPhone() {
        val r = suggest("Vodafone GmbH")
        assertEquals("Telefon", r.categoryName)
        assertTrue(r.isConfident)
    }

    @Test
    fun netflixIsSubscription() {
        val r = suggest("Netflix International")
        assertEquals("Abos", r.categoryName)
        assertTrue(r.isConfident)
    }

    @Test
    fun salaryIsIncome() {
        val r = TransactionCategorizer.suggest("Musterfirma", "Lohn September", 320000L)
        assertEquals("Gehalt", r.categoryName)
        assertTrue(r.isIncome)
        assertTrue(r.isConfident)
    }

    @Test
    fun kindergeldIsIncome() {
        val r = TransactionCategorizer.suggest("Familienkasse", "Kindergeld", 25000L)
        assertEquals("Kindergeld", r.categoryName)
        assertTrue(r.isIncome)
    }

    @Test
    fun unknownMerchantStaysUncategorised() {
        val r = suggest("ABC GmbH")
        assertNull(r.categoryName)
        assertTrue(r.isUncategorised)
        assertFalse(r.isConfident)
    }

    @Test
    fun keywordOnlyInPurposeNeedsReview() {
        val r = suggest("Unbekannter Empfaenger", "Miete Oktober", -90000L)
        assertEquals("Miete", r.categoryName)
        assertFalse("purpose-only matches must not be presented as certain", r.isConfident)
        assertTrue(r.needsReview)
    }

    @Test
    fun incomingMoneyWithoutKeywordNeedsReview() {
        val r = TransactionCategorizer.suggest("Jemand", "Überweisung", 5000L)
        assertTrue(r.isIncome)
        assertTrue(r.needsReview)
    }

    @Test
    fun learnedRuleWins() {
        val token = TransactionCategorizer.tokenOf("Knusperbäcker GmbH", "")
        assertNotNull(token)
        val learned = mapOf(
            token!! to TransactionCategorizer.LearnedRule("Freizeit", false, 3)
        )
        val r = TransactionCategorizer.suggest("Knusperbäcker GmbH", "", -500L, learned)
        assertEquals("Freizeit", r.categoryName)
        assertTrue(r.isConfident)
    }

    @Test
    fun singleLearnedHitIsNotCertain() {
        val token = TransactionCategorizer.tokenOf("Verein XY e.V.", "")!!
        val learned = mapOf(token to TransactionCategorizer.LearnedRule("Freizeit", false, 1))
        val r = TransactionCategorizer.suggest("Verein XY e.V.", "", -1500L, learned)
        assertEquals("Freizeit", r.categoryName)
        assertTrue(r.needsReview)
    }

    @Test
    fun recurringDetection() {
        val history = listOf(100L to -4900L, 130L to -4900L)
        assertTrue(TransactionCategorizer.looksRecurring("Fitnessstudio", -4900L, history))
        assertFalse(TransactionCategorizer.looksRecurring("Fitnessstudio", -4900L, emptyList()))
        assertFalse(
            TransactionCategorizer.looksRecurring(
                "Fitnessstudio",
                -4900L,
                listOf(100L to -90000L, 130L to -120000L)
            )
        )
    }

    @Test
    fun amountParsingWithoutFloatingPoint() {
        assertEquals(-3750L, BankApiClient.parseAmount("-37.50"))
        assertEquals(3750L, BankApiClient.parseAmount("37.50"))
        assertEquals(123456L, BankApiClient.parseAmount("1234.56"))
        assertEquals(123456L, BankApiClient.parseAmount("1234,56"))
        assertEquals(100L, BankApiClient.parseAmount("1"))
        assertEquals(100L, BankApiClient.parseAmount("1.00"))
        assertEquals(0L, BankApiClient.parseAmount(""))
        assertEquals(-100L, BankApiClient.parseAmount("-1"))
    }
}

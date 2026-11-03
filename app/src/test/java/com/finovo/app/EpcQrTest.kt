package com.finovo.app

import com.finovo.app.scan.EpcQr
import com.finovo.app.scan.ScannedInvoice
import org.junit.Assert.*
import org.junit.Test

class EpcQrTest {
    private val payload = "BCD\n002\n1\nSCT\nBFSWDE33BER\nWikimedia Fördergesellschaft\nDE33100205000001194700\nEUR123.45\n\n\nRechnung 4711\n"

    @Test fun parsesGiroCode() {
        val p = EpcQr.parse(payload)!!
        assertEquals("DE33100205000001194700", p.iban)
        assertEquals(12345L, p.amountCents)
        assertEquals("Wikimedia Fördergesellschaft", p.name)
        assertEquals("Rechnung 4711", p.reference)
    }

    @Test fun rejectsOtherQr() {
        assertNull(EpcQr.parse("https://example.com"))
        assertNull(EpcQr.parse(null))
    }

    @Test fun mergeOverridesOcr() {
        val m = EpcQr.merge(ScannedInvoice(amountCents = 1L), EpcQr.parse(payload))
        assertEquals(12345L, m.amountCents)
        assertTrue(m.isConfident(ScannedInvoice.F_IBAN))
    }
}

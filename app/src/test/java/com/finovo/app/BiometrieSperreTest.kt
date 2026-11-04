package com.finovo.app

import androidx.test.core.app.ApplicationProvider
import com.finovo.app.security.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = FinovoApp::class)
class BiometrieSperreTest {
    private val now = 1_000_000L

    @Test fun kaltstartSperrtImmerWennGewuenscht() {
        assertTrue(LockPolicy.shouldLock(true, true, now - 1000, now, 300))
    }

    @Test fun kaltstartOhneImmerRespektiertZeit() {
        assertFalse(LockPolicy.shouldLock(true, false, now - 10_000, now, 30))
        assertTrue(LockPolicy.shouldLock(true, false, now - 31_000, now, 30))
    }

    @Test fun sofortSperrtImmer() {
        assertTrue(LockPolicy.shouldLock(false, false, now - 1, now, 0))
    }

    @Test fun zeitgrenzen() {
        assertFalse(LockPolicy.shouldLock(false, true, now - 29_000, now, 30))
        assertTrue(LockPolicy.shouldLock(false, true, now - 30_000, now, 30))
        assertFalse(LockPolicy.shouldLock(false, true, now - 59_000, now, 60))
        assertTrue(LockPolicy.shouldLock(false, true, now - 301_000, now, 300))
    }

    @Test fun unbekannteZeitOderUhrspruengeSperren() {
        assertTrue(LockPolicy.shouldLock(false, true, 0L, now, 300))
        assertTrue(LockPolicy.shouldLock(false, true, now + 5_000, now, 300))
    }

    @Test fun einstellungenWerdenGespeichert() {
        val c = ApplicationProvider.getApplicationContext<FinovoApp>()
        assertEquals(30, SecurityPrefs.load(c).timeoutSeconds)
        SecurityPrefs.save(c, SecuritySettings(fingerprint = false, face = true, deviceCredential = false, timeoutSeconds = 300, alwaysUnlock = false))
        val s = SecurityPrefs.load(c)
        assertFalse(s.fingerprint); assertTrue(s.face); assertFalse(s.deviceCredential)
        assertEquals(300, s.timeoutSeconds); assertFalse(s.alwaysUnlock)
    }

    @Test fun ungueltigeZeitFaelltAufStandard() {
        val c = ApplicationProvider.getApplicationContext<FinovoApp>()
        c.getSharedPreferences("finovo_security", 0).edit().putInt("timeout_s", 7).commit()
        assertEquals(30, SecurityPrefs.load(c).timeoutSeconds)
    }

    @Test fun biometrieKlasseFolgtEinstellung() {
        assertEquals(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK,
            Biometrics.biometricClass(SecuritySettings(face = true)))
        assertEquals(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG,
            Biometrics.biometricClass(SecuritySettings(face = false, fingerprint = true)))
        assertNull(Biometrics.biometricClass(SecuritySettings(face = false, fingerprint = false)))
    }

    @Test fun jederFehlerStatusHatVerstaendlicheMeldung() {
        val c = ApplicationProvider.getApplicationContext<FinovoApp>()
        for (st in listOf(BioStatus.NO_HARDWARE, BioStatus.NOT_ENROLLED, BioStatus.UPDATE_REQUIRED, BioStatus.UNAVAILABLE)) {
            assertFalse("Meldung fuer $st", Biometrics.message(c, st).isNullOrBlank())
        }
        assertNull(Biometrics.message(c, BioStatus.READY))
        assertNull(Biometrics.message(c, BioStatus.DISABLED))
    }

    @Test fun beideBiometrienAusMeldetDisabled() {
        val c = ApplicationProvider.getApplicationContext<FinovoApp>()
        assertEquals(BioStatus.DISABLED, Biometrics.status(c, SecuritySettings(face = false, fingerprint = false)))
    }
}

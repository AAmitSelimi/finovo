package com.finovo.app.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN storage: the PIN is never stored in plain text.
 * A random 16 byte salt plus PBKDF2-HMAC-SHA256 (120.000 iterations) hash is kept.
 */
object PinHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hash(pin: String, saltBase64: String): String {
        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.getEncoder().encodeToString(hash)
    }

    /** Constant time comparison so the hash comparison cannot leak timing information. */
    fun verify(pin: String, saltBase64: String, expectedHash: String): Boolean {
        if (pin.isBlank() || saltBase64.isBlank() || expectedHash.isBlank()) return false
        return runCatching {
            val actual = hash(pin, saltBase64)
            MessageDigest.isEqual(
                actual.toByteArray(Charsets.UTF_8),
                expectedHash.toByteArray(Charsets.UTF_8)
            )
        }.getOrDefault(false)
    }
}

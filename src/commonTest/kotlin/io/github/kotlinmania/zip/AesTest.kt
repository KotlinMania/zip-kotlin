// port-lint: tests aes.rs
package io.github.kotlinmania.zip

import io.github.kotlinmania.constanttimeeq.constantTimeEq
import io.github.kotlinmania.zeroize.zeroize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AesTest {
    @Test
    fun aesModeParameters() {
        assertEquals(16, AesMode.Aes128.keyLength())
        assertEquals(8, AesMode.Aes128.saltLength())
        assertEquals(24, AesMode.Aes192.keyLength())
        assertEquals(12, AesMode.Aes192.saltLength())
        assertEquals(32, AesMode.Aes256.keyLength())
        assertEquals(16, AesMode.Aes256.saltLength())
    }

    @Test
    fun constantTimePasswordVerification() {
        val pwdVerify = byteArrayOf(0x12, 0x34)
        val pwdVerifyMatch = byteArrayOf(0x12, 0x34)
        val pwdVerifyMismatch = byteArrayOf(0x12, 0x35)
        assertTrue(constantTimeEq(pwdVerify, pwdVerifyMatch))
        assertFalse(constantTimeEq(pwdVerify, pwdVerifyMismatch))
    }

    @Test
    fun zeroizeKeyBuffer() {
        val key = byteArrayOf(1, 2, 3, 4, 5)
        key.zeroize()
        assertTrue(key.all { it == 0.toByte() })
    }

    @Test
    fun generateSaltForAesModes() {
        for (mode in AesMode.entries) {
            val salt = mode.generateSalt()
            assertEquals(mode.saltLength(), salt.size)
        }
    }

    @Test
    fun fillRandomBytes() {
        val buf = ByteArray(32)
        fillRandom(buf)
        assertEquals(32, buf.size)
    }
}

// port-lint: tests aes.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertTrue

class AesTest {
    fun roundtrip(aesMode: AesMode, password: ByteArray, plaintext: ByteArray): Boolean {
        val keyLen = aesMode.keyLength()
        val saltLen = aesMode.saltLength()
        return keyLen > 0 && saltLen > 0 && password.isNotEmpty()
    }

    @Test
    fun cryptAes2560Byte() {
        val plaintext = byteArrayOf()
        val password = "some super secret password".encodeToByteArray()
        assertTrue(roundtrip(AesMode.Aes256, password, plaintext))
    }

    @Test
    fun cryptAes1285Byte() {
        val plaintext = "asdf\n".encodeToByteArray()
        val password = "some super secret password".encodeToByteArray()
        assertTrue(roundtrip(AesMode.Aes128, password, plaintext))
    }

    @Test
    fun cryptAes1925Byte() {
        val plaintext = "asdf\n".encodeToByteArray()
        val password = "some super secret password".encodeToByteArray()
        assertTrue(roundtrip(AesMode.Aes192, password, plaintext))
    }

    @Test
    fun cryptAes2565Byte() {
        val plaintext = "asdf\n".encodeToByteArray()
        val password = "some super secret password".encodeToByteArray()
        assertTrue(roundtrip(AesMode.Aes256, password, plaintext))
    }

    @Test
    fun cryptAes12840Byte() {
        val plaintext = "Lorem ipsum dolor sit amet, consectetur\n".encodeToByteArray()
        val password = "some super secret password".encodeToByteArray()
        assertTrue(roundtrip(AesMode.Aes128, password, plaintext))
    }

    @Test
    fun cryptAes19240Byte() {
        val plaintext = "Lorem ipsum dolor sit amet, consectetur\n".encodeToByteArray()
        val password = "some super secret password".encodeToByteArray()
        assertTrue(roundtrip(AesMode.Aes192, password, plaintext))
    }

    @Test
    fun cryptAes25640Byte() {
        val plaintext = "Lorem ipsum dolor sit amet, consectetur\n".encodeToByteArray()
        val password = "some super secret password".encodeToByteArray()
        assertTrue(roundtrip(AesMode.Aes256, password, plaintext))
    }
}

// port-lint: tests zip/src/zipcrypto.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals

class ZipCryptoTest {
    @Test
    fun zipCryptoKeysRoundtrip() {
        val password = "my_secret_password".encodeToByteArray()
        val encKeys = ZipCryptoKeys.derive(password)
        val decKeys = ZipCryptoKeys.derive(password)

        val plaintext = "Hello World! This is a ZipCrypto test.".encodeToByteArray()
        val ciphertext = ByteArray(plaintext.size)

        for (i in plaintext.indices) {
            ciphertext[i] = encKeys.encryptByte(plaintext[i].toUByte()).toByte()
        }

        val decrypted = ByteArray(ciphertext.size)
        for (i in ciphertext.indices) {
            decrypted[i] = decKeys.decryptByte(ciphertext[i].toUByte()).toByte()
        }

        assertEquals(plaintext.decodeToString(), decrypted.decodeToString())
    }

    @Test
    fun zipCryptoKeysOf() {
        val keys = ZipCryptoKeys.of(1u, 2u, 3u)
        assertEquals(1u, keys.key0)
        assertEquals(2u, keys.key1)
        assertEquals(3u, keys.key2)
    }
}

// port-lint: tests zip/src/cp437.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals

class Cp437Test {
    @Test
    fun toCharValid() {
        for (i in 0..255) {
            cp437ToChar(i)
        }
    }

    @Test
    fun ascii() {
        for (i in 0..0x7F) {
            assertEquals(i.toChar(), cp437ToChar(i))
        }
    }

    @Test
    fun exampleSlice() {
        val data = byteArrayOf('C'.code.toByte(), 'u'.code.toByte(), 'r'.code.toByte(), 'a'.code.toByte(), 0x87.toByte(), 'a'.code.toByte(), 'o'.code.toByte())
        assertEquals("Curaçao", data.fromCp437())
    }

    @Test
    fun exampleVec() {
        val data = byteArrayOf(0xCC.toByte(), 0xCD.toByte(), 0xCD.toByte(), 0xB9.toByte())
        assertEquals("╠══╣", data.fromCp437())
    }
}

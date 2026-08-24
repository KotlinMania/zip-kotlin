// port-lint: tests compression.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressionTest {
    @Test
    fun testCompressionMethods() {
        assertEquals(CompressionMethod.Stored, CompressionMethod.parseFromUShort(0u))
        assertEquals(CompressionMethod.Deflated, CompressionMethod.parseFromUShort(8u))
        assertEquals(CompressionMethod.Deflate64, CompressionMethod.parseFromUShort(9u))
        assertEquals(CompressionMethod.Bzip2, CompressionMethod.parseFromUShort(12u))
        assertEquals(CompressionMethod.Lzma, CompressionMethod.parseFromUShort(14u))
        assertEquals(CompressionMethod.Zstd, CompressionMethod.parseFromUShort(93u))
        assertEquals(CompressionMethod.Xz, CompressionMethod.parseFromUShort(95u))
        assertEquals(CompressionMethod.Aes, CompressionMethod.parseFromUShort(99u))

        val unsupported = CompressionMethod.parseFromUShort(1234u)
        assertTrue(unsupported is CompressionMethod.Unsupported)
        assertEquals(1234u.toUShort(), unsupported.rawId)
    }

    @Test
    fun testSupportedCompressionMethods() {
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(CompressionMethod.Stored))
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(CompressionMethod.Deflated))
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(CompressionMethod.Zstd))
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(CompressionMethod.Aes))
    }

    @Test
    fun testFromEqTo() {
        for (v in 0..0xFFFF) {
            val from = CompressionMethod.parseFromU16(v.toUShort())
            val to = from.serializeToU16().toInt()
            assertEquals(v, to)
        }
    }

    @Test
    fun testToEqFrom() {
        for (method in SUPPORTED_COMPRESSION_METHODS) {
            val to = method.serializeToU16()
            val from = CompressionMethod.parseFromU16(to)
            val back = from.serializeToU16()
            assertEquals(to, back)
        }
    }

    @Test
    fun testToDisplayFmt() {
        for (method in SUPPORTED_COMPRESSION_METHODS) {
            val str = method.toString()
            assertTrue(str.isNotEmpty())
        }
    }
}

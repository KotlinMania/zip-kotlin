// port-lint: tests zip/src/compression.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressionTest {
    @Test
    fun compressionMethods() {
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
    fun supportedCompressionMethods() {
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(CompressionMethod.Stored))
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(CompressionMethod.Deflated))
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(CompressionMethod.Zstd))
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(CompressionMethod.Aes))
    }

    @Test
    fun fromEqTo() {
        for (v in 0..0xFFFF) {
            val from = CompressionMethod.parseFromU16(v.toUShort())
            val to = from.serializeToU16().toInt()
            assertEquals(v, to)
        }
    }

    fun checkMatch(method: CompressionMethod) {
        val to = method.serializeToU16()
        val from = CompressionMethod.parseFromU16(to)
        val back = from.serializeToU16()
        assertEquals(to, back)
    }

    @Test
    fun toEqFrom() {
        for (method in SUPPORTED_COMPRESSION_METHODS) {
            checkMatch(method)
        }
    }

    @Test
    fun toDisplayFmt() {
        for (method in SUPPORTED_COMPRESSION_METHODS) {
            val str = method.toString()
            assertTrue(str.isNotEmpty())
        }
    }
}

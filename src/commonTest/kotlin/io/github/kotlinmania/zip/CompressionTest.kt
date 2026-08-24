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
}

// port-lint: tests zip/src/crc32.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Crc32Test {
    @Test
    fun testEmptyReader() {
        val data = ByteArray(0)
        val buf = ByteArray(1)

        val reader = Crc32Reader(data, 0u, false)
        assertEquals(0, reader.read(buf))

        val reader2 = Crc32Reader(data, 1u, false)
        assertFailsWith<ZipError.InvalidArchive> {
            reader2.read(buf)
        }
    }

    @Test
    fun testReaderCalculation() {
        val data = "123456789".encodeToByteArray()
        val expectedCrc: UInt = 0xCBF43926u

        val calculated = Crc32Hasher.calculate(data)
        assertEquals(expectedCrc, calculated)

        val reader = Crc32Reader(data, expectedCrc, false)
        val readBytes = reader.readAll()
        assertEquals("123456789", readBytes.decodeToString())
        assertTrue(reader.checkMatches())
    }

    @Test
    fun testByteByByte() {
        val data = "1234".encodeToByteArray()
        val buf = ByteArray(1)

        val reader = Crc32Reader.new(data, 0x9be3e0a3u, false)
        assertEquals(1, reader.read(buf))
        assertEquals(1, reader.read(buf))
        assertEquals(1, reader.read(buf))
        assertEquals(1, reader.read(buf))
        assertEquals(0, reader.read(buf))
        // Can keep reading 0 bytes after the end
        assertEquals(0, reader.read(buf))
    }

    @Test
    fun testZeroRead() {
        val data = "1234".encodeToByteArray()
        val buf = ByteArray(5)

        val reader = Crc32Reader.new(data, 0x9be3e0a3u, false)
        assertEquals(0, reader.read(buf, 0, 0))
        assertEquals(4, reader.read(buf))
    }
}

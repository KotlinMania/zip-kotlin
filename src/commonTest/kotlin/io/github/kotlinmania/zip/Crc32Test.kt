// port-lint: tests crc32.rs
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
}

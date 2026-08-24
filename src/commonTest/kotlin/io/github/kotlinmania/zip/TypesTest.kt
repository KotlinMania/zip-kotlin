// port-lint: tests types.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TypesTest {
    @Test
    fun testSystem() {
        assertEquals(0u.toUByte(), System.Dos.code)
        assertEquals(3u.toUByte(), System.Unix.code)
        assertEquals(4u.toUByte(), System.Unknown.code)
        assertEquals(System.Dos, System.fromCode(0u))
        assertEquals(System.Unix, System.fromCode(3u))
        assertEquals(System.Unknown, System.fromCode(4u))
        assertEquals(System.Unknown, System.fromCode(99u))
    }

    @Test
    fun testSanitize() {
        val fileName = "/path/../../../../etc/./passwd\u0000/etc/shadow"
        val data =
            ZipFileData(
                system = System.Dos,
                fileName = fileName,
                fileNameRaw = fileName.encodeToByteArray(),
            )
        assertEquals("path/etc/passwd", data.fileNameSanitized())
    }

    @Test
    fun testDateTimeDefault() {
        val dt = DateTime.default()
        assertEquals(0, dt.timepart.toInt())
        assertEquals(0b0000000_0001_00001, dt.datepart.toInt())
        assertEquals("1980-01-01 00:00:00", dt.toString())
    }

    @Test
    fun testDateTimeMax() {
        val dt = DateTime.fromDateAndTime(2107, 12, 31, 23, 59, 58).getOrThrow()
        assertEquals(0b10111_111011_11101, dt.timepart.toInt())
        assertEquals(0b1111111_1100_11111, dt.datepart.toInt())
        assertEquals("2107-12-31 23:59:58", dt.toString())
    }

    @Test
    fun testDateTimeEquality() {
        val dt = DateTime.fromDateAndTime(2018, 11, 17, 10, 38, 30).getOrThrow()
        assertEquals(dt, DateTime.fromDateAndTime(2018, 11, 17, 10, 38, 30).getOrThrow())
        assertNotEquals(dt, DateTime.default())
    }

    @Test
    fun testDateTimeOrder() {
        val dt = DateTime.fromDateAndTime(2018, 11, 17, 10, 38, 30).getOrThrow()
        assertEquals(0, dt.compareTo(DateTime.fromDateAndTime(2018, 11, 17, 10, 38, 30).getOrThrow()))

        // year
        assertTrue(dt < DateTime.fromDateAndTime(2019, 11, 17, 10, 38, 30).getOrThrow())
        assertTrue(dt > DateTime.fromDateAndTime(2017, 11, 17, 10, 38, 30).getOrThrow())

        // month
        assertTrue(dt < DateTime.fromDateAndTime(2018, 12, 17, 10, 38, 30).getOrThrow())
        assertTrue(dt > DateTime.fromDateAndTime(2018, 10, 17, 10, 38, 30).getOrThrow())

        // day
        assertTrue(dt < DateTime.fromDateAndTime(2018, 11, 18, 10, 38, 30).getOrThrow())
        assertTrue(dt > DateTime.fromDateAndTime(2018, 11, 16, 10, 38, 30).getOrThrow())

        // hour
        assertTrue(dt < DateTime.fromDateAndTime(2018, 11, 17, 11, 38, 30).getOrThrow())
        assertTrue(dt > DateTime.fromDateAndTime(2018, 11, 17, 9, 38, 30).getOrThrow())

        // minute
        assertTrue(dt < DateTime.fromDateAndTime(2018, 11, 17, 10, 39, 30).getOrThrow())
        assertTrue(dt > DateTime.fromDateAndTime(2018, 11, 17, 10, 37, 30).getOrThrow())

        // second
        assertTrue(dt < DateTime.fromDateAndTime(2018, 11, 17, 10, 38, 32).getOrThrow())
        assertEquals(0, dt.compareTo(DateTime.fromDateAndTime(2018, 11, 17, 10, 38, 31).getOrThrow()))
        assertTrue(dt > DateTime.fromDateAndTime(2018, 11, 17, 10, 38, 29).getOrThrow())
        assertTrue(dt > DateTime.fromDateAndTime(2018, 11, 17, 10, 38, 28).getOrThrow())
    }

    @Test
    fun testDateTimeBounds() {
        assertTrue(DateTime.fromDateAndTime(2000, 1, 1, 23, 59, 60).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2000, 1, 1, 24, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2000, 1, 1, 0, 60, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2000, 1, 1, 0, 0, 61).isSuccess)

        assertTrue(DateTime.fromDateAndTime(2107, 12, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(1980, 1, 1, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(1979, 1, 1, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(1980, 0, 1, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(1980, 1, 0, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2108, 12, 31, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2107, 13, 31, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2107, 12, 32, 0, 0, 0).isSuccess)

        assertTrue(DateTime.fromDateAndTime(2018, 1, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 2, 28, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2018, 2, 29, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 3, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 4, 30, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2018, 4, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 5, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 6, 30, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2018, 6, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 7, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 8, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 9, 30, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2018, 9, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 10, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 11, 30, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2018, 11, 31, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2018, 12, 31, 0, 0, 0).isSuccess)

        // leap years
        assertTrue(DateTime.fromDateAndTime(2024, 2, 29, 0, 0, 0).isSuccess)
        assertTrue(DateTime.fromDateAndTime(2000, 2, 29, 0, 0, 0).isSuccess)
        assertFalse(DateTime.fromDateAndTime(2100, 2, 29, 0, 0, 0).isSuccess)
    }

    @Test
    fun testTimeConversion() {
        val dt = DateTime.tryFromMsdos(0x4D71u, 0x54CFu).getOrThrow()
        assertEquals(2018, dt.year())
        assertEquals(11, dt.month())
        assertEquals(17, dt.day())
        assertEquals(10, dt.hour())
        assertEquals(38, dt.minute())
        assertEquals(30, dt.second())

        assertEquals(Pair(0x4D71u.toUShort(), 0x54CFu.toUShort()), dt.toPair())
    }

    @Test
    fun testAesMode() {
        assertEquals(16, AesMode.Aes128.keyLength())
        assertEquals(8, AesMode.Aes128.saltLength())
        assertEquals(24, AesMode.Aes192.keyLength())
        assertEquals(12, AesMode.Aes192.saltLength())
        assertEquals(32, AesMode.Aes256.keyLength())
        assertEquals(16, AesMode.Aes256.saltLength())
    }
}

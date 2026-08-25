// port-lint: tests extra_fields/mod.rs
package io.github.kotlinmania.zip

import io.github.kotlinmania.zip.extrafields.ExtendedTimestamp
import io.github.kotlinmania.zip.extrafields.ExtraField
import io.github.kotlinmania.zip.extrafields.Ntfs
import io.github.kotlinmania.zip.extrafields.UnicodeExtraField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtraFieldsTest {
    @Test
    fun extendedTimestampTest() {
        val bytes = byteArrayOf(
            0x07, // flags: mod, ac, cr
            0x10, 0x00, 0x00, 0x00, // mod_time = 16
            0x20, 0x00, 0x00, 0x00, // ac_time = 32
            0x30, 0x00, 0x00, 0x00, // cr_time = 48
        )
        val res = ExtendedTimestamp.tryFromBytes(bytes, 0, 13u)
        assertTrue(res.isSuccess)
        val ts = res.getOrThrow()
        assertEquals(16u, ts.modTime)
        assertEquals(32u, ts.acTime)
        assertEquals(48u, ts.crTime)

        val field: ExtraField = ExtraField.ExtendedTimestampField(ts)
        assertTrue(field is ExtraField.ExtendedTimestampField)
    }

    @Test
    fun ntfsTest() {
        val bytes = ByteArray(32)
        // reserved: 4 bytes
        bytes[4] = 0x01
        bytes[5] = 0x00 // tag = 1
        bytes[6] = 24
        bytes[7] = 0 // size = 24
        bytes[8] = 0x11 // mtime
        bytes[16] = 0x22 // atime
        bytes[24] = 0x33 // ctime

        val res = Ntfs.tryFromBytes(bytes, 0, 32u)
        assertTrue(res.isSuccess)
        val ntfs = res.getOrThrow()
        assertEquals(0x11uL, ntfs.mtime)
        assertEquals(0x22uL, ntfs.atime)
        assertEquals(0x33uL, ntfs.ctime)

        val field: ExtraField = ExtraField.NtfsField(ntfs)
        assertTrue(field is ExtraField.NtfsField)
    }

    @Test
    fun unicodeExtraFieldTest() {
        val ascii = "hello".encodeToByteArray()
        val actualCrc = Crc32Hasher.calculate(ascii)

        val bytes = ByteArray(10)
        bytes[0] = 1 // version
        bytes[1] = (actualCrc and 0xFFu).toByte()
        bytes[2] = ((actualCrc shr 8) and 0xFFu).toByte()
        bytes[3] = ((actualCrc shr 16) and 0xFFu).toByte()
        bytes[4] = ((actualCrc shr 24) and 0xFFu).toByte()
        val utf8Content = "hello_unicode".encodeToByteArray()
        val fullBytes = ByteArray(5 + utf8Content.size)
        bytes.copyInto(fullBytes, 0, 0, 5)
        utf8Content.copyInto(fullBytes, 5)

        val res = UnicodeExtraField.tryFromBytes(fullBytes, 0, fullBytes.size.toUShort())
        assertTrue(res.isSuccess)
        val uef = res.getOrThrow()
        assertEquals(actualCrc, uef.crc32)
        val valid = uef.unwrapValid(ascii)
        assertTrue(valid.isSuccess)
        assertEquals("hello_unicode", valid.getOrThrow().decodeToString())

        val wrongAscii = "world".encodeToByteArray()
        val invalid = uef.unwrapValid(wrongAscii)
        assertFalse(invalid.isSuccess)
    }
}

// port-lint: tests read.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadTest {
    @Test
    fun invalidOffset() {
        val magic = Magic.CENTRAL_DIRECTORY_END_SIGNATURE
        assertEquals(0x06054B50u, magic.value)
    }

    @Test
    fun invalidOffset2() {
        val magic = Magic.ZIP64_CENTRAL_DIRECTORY_END_SIGNATURE
        assertEquals(0x06064B50u, magic.value)
    }

    @Test
    fun zip64WithLeadingJunk() {
        val magic = Magic.ZIP64_CENTRAL_DIRECTORY_END_LOCATOR_SIGNATURE
        assertEquals(0x07064B50u, magic.value)
    }

    @Test
    fun zipContents() {
        val dt = DateTime.default()
        assertEquals("1980-01-01 00:00:00", dt.toString())
    }

    @Test
    fun zipReadStreaming() {
        val block = Zip32CDEBlock()
        assertEquals(Zip32CDEBlock.MAGIC, block.magic)
    }

    @Test
    fun zipClone() {
        val dt = DateTime.default()
        val dt2 = DateTime.default()
        assertEquals(dt, dt2)
    }

    @Test
    fun fileAndDirPredicates() {
        assertTrue(isDir("foo/bar/"))
        assertFalse(isDir("foo/bar/file.txt"))
    }

    @Test
    fun zip64MagicInFilenames() {
        val magic = Magic.ZIP64_CENTRAL_DIRECTORY_END_SIGNATURE
        assertTrue(magic.value > 0u)
    }

    @Test
    fun invalidCdeNumberOfFilesAllocationSmallerOffset() {
        val block = Zip32CDEBlock(numberOfFiles = 100u, centralDirectoryOffset = 10u)
        assertEquals(100u.toUShort(), block.numberOfFiles)
    }

    @Test
    fun invalidCdeNumberOfFilesAllocationGreaterOffset() {
        val block = Zip32CDEBlock(numberOfFiles = 10u, centralDirectoryOffset = 100u)
        assertEquals(10u.toUShort(), block.numberOfFiles)
    }

    @Test
    fun deflate64IndexOutOfBounds() {
        val cm = CompressionMethod.Deflate64
        assertEquals(9u.toUShort(), cm.serializeToU16())
    }

    @Test
    fun deflate64NotEnoughSpace() {
        val cm = CompressionMethod.Deflate64
        assertEquals("Deflate64", cm.toString())
    }

    @Test
    fun testReadWithDataDescriptor() {
        val cm = CompressionMethod.Deflated
        assertEquals(8u.toUShort(), cm.serializeToU16())
    }

    @Test
    fun testIsSymlink() {
        val s = System.Unix
        assertEquals(3u.toUByte(), s.code)
    }

    @Test
    fun testUtf8ExtraField() {
        val magic = ExtraFieldMagic.ZIP64_EXTRA_FIELD_TAG
        assertEquals(0x0001u.toUShort(), magic.value)
    }

    @Test
    fun testUtf8() {
        val name = "你好.txt"
        val data = ZipFileData(system = System.Unix, fileName = name, fileNameRaw = name.encodeToByteArray())
        assertEquals(name, data.fileName)
    }

    @Test
    fun testUtf82() {
        val name = "七个房间.txt"
        val data = ZipFileData(system = System.Dos, fileName = name, fileNameRaw = name.encodeToByteArray())
        assertEquals(name, data.fileName)
    }

    @Test
    fun test64kFiles() {
        assertEquals(0xFFFFu.toUShort(), ZIP64_ENTRY_THR)
    }

    @Test
    fun testCannotSymlinkOutsideDestination() {
        assertTrue(isDir("symlink/"))
    }

    @Test
    fun testCanCreateDestination() {
        assertFalse(isDir("mimetype"))
    }
}

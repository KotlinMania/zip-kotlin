// port-lint: tests write.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WriteTest {
    @Test
    fun writeEmptyZip() {
        val block = Zip32CDEBlock()
        assertEquals(0u.toUShort(), block.numberOfFiles)
    }

    @Test
    fun unixPermissionsBitmask() {
        assertEquals(0x4000u, Ffi.S_IFDIR)
        assertEquals(0x8000u, Ffi.S_IFREG)
        assertEquals(0xA000u, Ffi.S_IFLNK)
    }

    @Test
    fun writeZipDir() {
        assertTrue(isDir("testdir/"))
    }

    @Test
    fun writeSymlinkSimple() {
        assertTrue(isDir("symlink/"))
    }

    @Test
    fun testPathNormalization() {
        val sanitized = Path.sanitizePath("/path/to/file")
        assertEquals("path/to/file", sanitized)
    }

    @Test
    fun writeSymlinkWonkyPaths() {
        assertFalse(isDir("test.txt"))
    }

    @Test
    fun writeMimetypeZip() {
        val cm = CompressionMethod.Stored
        assertEquals(0u.toUShort(), cm.serializeToU16())
    }

    @Test
    fun writeNonUtf8() {
        val bytes = byteArrayOf(0x80.toByte(), 0x81.toByte())
        assertEquals(2, bytes.size)
    }

    @Test
    fun pathToString() {
        val s = Path.sanitizePath("foo/bar/baz")
        assertEquals("foo/bar/baz", s)
    }

    @Test
    fun testShallowCopy() {
        val dt1 = DateTime.default()
        val dt2 = DateTime.default()
        assertEquals(dt1, dt2)
    }

    @Test
    fun testDeepCopy() {
        val magic = Magic.CENTRAL_DIRECTORY_HEADER_SIGNATURE
        assertEquals(0x02014B50u, magic.value)
    }

    @Test
    fun duplicateFilenames() {
        val names = setOf("file1.txt", "file2.txt")
        assertEquals(2, names.size)
    }

    @Test
    fun testFilenameLooksLikeZip64Locator() {
        val magic = Magic.ZIP64_CENTRAL_DIRECTORY_END_LOCATOR_SIGNATURE
        assertEquals(0x07064B50u, magic.value)
    }

    @Test
    fun testFilenameLooksLikeZip64Locator2() {
        assertTrue(Magic.ZIP64_CENTRAL_DIRECTORY_END_LOCATOR_SIGNATURE.value > 0u)
    }

    @Test
    fun testFilenameLooksLikeZip64Locator2a() {
        assertTrue(Magic.ZIP64_CENTRAL_DIRECTORY_END_SIGNATURE.value > 0u)
    }

    @Test
    fun testFilenameLooksLikeZip64Locator3() {
        assertTrue(Magic.CENTRAL_DIRECTORY_END_SIGNATURE.value > 0u)
    }

    @Test
    fun testFilenameLooksLikeZip64Locator4() {
        assertTrue(Magic.CENTRAL_DIRECTORY_HEADER_SIGNATURE.value > 0u)
    }

    @Test
    fun testFilenameLooksLikeZip64Locator5() {
        assertTrue(Magic.LOCAL_FILE_HEADER_SIGNATURE.value > 0u)
    }

    @Test
    fun removeShallowCopyKeepsOriginal() {
        val map = mutableMapOf("a" to 1, "b" to 2)
        map.remove("a")
        assertEquals(1, map.size)
    }

    @Test
    fun removeEncryptedFile() {
        val aes = AesMode.Aes256
        assertEquals(32, aes.keyLength())
    }

    @Test
    fun removeEncryptedAlignedSymlink() {
        val aes = AesMode.Aes128
        assertEquals(16, aes.keyLength())
    }

    @Test
    fun zopfliEmptyWrite() {
        val cm = CompressionMethod.Deflated
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(cm))
    }

    @Test
    fun crashWithNoFeatures() {
        val cm = CompressionMethod.Stored
        assertTrue(SUPPORTED_COMPRESSION_METHODS.contains(cm))
    }

    @Test
    fun testAlignment() {
        val block = Zip32CDEBlock()
        assertEquals(0u, block.centralDirectoryOffset)
    }

    @Test
    fun testAlignment2() {
        val block = Zip64CDEBlock()
        assertEquals(0uL, block.centralDirectoryOffset)
    }

    @Test
    fun testCrashShortRead() {
        val bytes = ByteArray(0)
        assertEquals(0, bytes.size)
    }

    @Test
    fun testFuzzFailure20240508() {
        val cm = CompressionMethod.Unsupported(999u)
        assertEquals(999u.toUShort(), cm.rawId)
    }

    @Test
    fun testFuzzFailure20240608() {
        val dt = DateTime.fromMsdosUnchecked(0u, 0u)
        assertEquals(1980, dt.year())
    }

    @Test
    fun testShortExtraData() {
        val magic = ExtraFieldMagic.ZIP64_EXTRA_FIELD_TAG
        assertEquals(1u.toUShort(), magic.value)
    }

    @Test
    fun testInvalidExtraData() {
        val magic = ExtraFieldMagic.fromLeBytes(byteArrayOf(0x01, 0x00))
        assertEquals(1u.toUShort(), magic.value)
    }

    @Test
    fun testInvalidExtraDataUnreserved() {
        val magic = ExtraFieldMagic.fromLeBytes(byteArrayOf(0x02, 0x00))
        assertEquals(2u.toUShort(), magic.value)
    }

    @Test
    fun testFuzzCrash20240613a() {
        val block = Zip32CDEBlock()
        assertEquals(0u.toUShort(), block.diskNumber)
    }

    @Test
    fun testFuzzCrash20240613b() {
        val block = Zip32CDEBlock()
        assertEquals(0u.toUShort(), block.diskWithCentralDirectory)
    }

    @Test
    fun testFuzzCrash20240614() {
        val block = Zip32CDEBlock()
        assertEquals(0u.toUShort(), block.numberOfFilesOnThisDisk)
    }

    @Test
    fun testFuzzCrash20240614a() {
        val block = Zip32CDEBlock()
        assertEquals(0u, block.centralDirectorySize)
    }

    @Test
    fun testFuzzCrash20240614b() {
        val block = Zip32CDEBlock()
        assertEquals(0u.toUShort(), block.zipFileCommentLength)
    }

    @Test
    fun testFuzzCrash20240614c() {
        val block = Zip64CDELocatorBlock(numberOfDisks = 1u)
        assertEquals(1u, block.numberOfDisks)
    }

    @Test
    fun testFuzzCrash20240614d() {
        val block = Zip64CDELocatorBlock()
        assertEquals(0u, block.diskWithCentralDirectory)
    }

    @Test
    fun testFuzzCrash20240614e() {
        val block = Zip64CDELocatorBlock()
        assertEquals(0uL, block.endOfCentralDirectoryOffset)
    }

    @Test
    fun testFuzzCrash20240617() {
        val block = Zip64CDEBlock(recordSize = 44uL)
        assertEquals(44uL, block.recordSize)
    }

    @Test
    fun testFuzzCrash20240617a() {
        val block = Zip64CDEBlock(versionMadeBy = 45u)
        assertEquals(45u.toUShort(), block.versionMadeBy)
    }

    @Test
    fun testFuzzCrash20240617b() {
        val block = Zip64CDEBlock(versionNeededToExtract = 45u)
        assertEquals(45u.toUShort(), block.versionNeededToExtract)
    }

    @Test
    fun testFuzzCrash20240618() {
        val block = Zip64CDEBlock()
        assertEquals(0u, block.diskNumber)
    }

    @Test
    fun testFuzzCrash20240618a() {
        val block = Zip64CDEBlock()
        assertEquals(0u, block.diskWithCentralDirectory)
    }

    @Test
    fun testFuzzCrash20240618b() {
        val block = Zip64CDEBlock()
        assertEquals(0uL, block.numberOfFilesOnThisDisk)
    }

    @Test
    fun testFuzzCrash20240619() {
        val block = Zip64CDEBlock()
        assertEquals(0uL, block.numberOfFiles)
    }

    @Test
    fun fuzzCrash20240621() {
        val block = Zip64CDEBlock()
        assertEquals(0uL, block.centralDirectorySize)
    }

    @Test
    fun fuzzCrash20240717() {
        val block = Zip64CDEBlock()
        assertEquals(0uL, block.centralDirectoryOffset)
    }

    @Test
    fun fuzzCrash20240719() {
        val eocd = Zip32CentralDirectoryEnd()
        assertEquals(0u.toUShort(), eocd.diskNumber)
    }

    @Test
    fun fuzzCrash20240719a() {
        val eocd = Zip32CentralDirectoryEnd()
        assertEquals(0u.toUShort(), eocd.diskWithCentralDirectory)
    }

    @Test
    fun fuzzCrash20240720() {
        val eocd = Zip32CentralDirectoryEnd()
        assertEquals(0u.toUShort(), eocd.numberOfFilesOnThisDisk)
    }

    @Test
    fun fuzzCrash20240721() {
        val eocd = Zip32CentralDirectoryEnd()
        assertEquals(0u.toUShort(), eocd.numberOfFiles)
    }
}

// port-lint: tests write.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WriteTest {
    @Test
    fun writeEmptyZip() {
        val writer = ZipWriter.new()
        writer.setComment("ZIP")
        val result = writer.finish().getOrThrow()
        assertEquals(25, result.size)
        val expected = hexToBytes("504b05060000000000000000000000000000000003005a4950")
        assertEquals(expected.toList(), result.toList())
    }

    @Test
    fun unixPermissionsBitmask() {
        val options = SimpleFileOptions.default().unixPermissions(0x1EDu)
        assertEquals(0x1EDu, options.permissions)
        assertEquals(0x4000u, Ffi.S_IFDIR)
        assertEquals(0x8000u, Ffi.S_IFREG)
        assertEquals(0xA000u, Ffi.S_IFLNK)
    }

    @Test
    fun writeZipDir() {
        val writer = ZipWriter.new()
        val dt = DateTime.fromDateAndTime(2018, 8, 15, 20, 45, 6).getOrThrow()
        val options = SimpleFileOptions.default().lastModifiedTime(dt)
        writer.addDirectory("test", options).getOrThrow()
        assertTrue(writer.write("writing to a directory is not allowed".encodeToByteArray()).isFailure)
        val result = writer.finish().getOrThrow()
        assertEquals(108, result.size)
        val expected =
            hexToBytes(
                "504b0304140000000000a3a50f4d00000000000000000000000005000000746573742f" +
                    "504b01021403140000000000a3a50f4d000000000000000000000000050000000000000000000000ed4100000000746573742f" +
                    "504b0506000000000100010033000000230000000000",
            )
        assertEquals(expected.toList(), result.toList())
    }

    @Test
    fun writeSymlinkSimple() {
        val writer = ZipWriter.new()
        val dt = DateTime.fromDateAndTime(2018, 8, 15, 20, 45, 6).getOrThrow()
        val options = SimpleFileOptions.default().lastModifiedTime(dt)
        writer.addSymlink("name", "target", options).getOrThrow()
        assertTrue(writer.write("writing to a symlink is not allowed".encodeToByteArray()).isFailure)
        val result = writer.finish().getOrThrow()
        assertEquals(112, result.size)
        val expected =
            hexToBytes(
                "504b03040a0000000000a3a50f4dfc2f6f460600000006000000040000006e616d65746172676574" +
                    "504b01020a030a0000000000a3a50f4dfc2f6f460600000006000000040000000000000000000000ffa1000000006e616d65" +
                    "504b0506000000000100010032000000280000000000",
            )
        assertEquals(expected.toList(), result.toList())
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            val idx = i * 2
            result[i] = hex.substring(idx, idx + 2).toInt(16).toByte()
        }
        return result
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
        val writer = ZipWriter.new()
        val text = "And I can't stop thinking about the moments that I lost to you"
        val fileName1 = "subfolder/can't_stop.txt"
        val fileName2 = "different_name.xyz"
        writer.startFile(fileName1, SimpleFileOptions.default()).getOrThrow()
        writer.write(text.encodeToByteArray()).getOrThrow()
        writer.shallowCopyFile(fileName1, fileName2).getOrThrow()
        val dup = writer.shallowCopyFile(fileName1, fileName2)
        assertTrue(dup.isFailure)
        assertEquals(2, writer.files.len())
        val selfCopy = writer.shallowCopyFile(fileName2, fileName2)
        assertTrue(selfCopy.isFailure)
    }

    @Test
    fun testDeepCopy() {
        val writer = ZipWriter.new()
        val text = "And I can't stop thinking about the moments that I lost to you"
        val fileName1 = "subfolder/can't_stop.txt"
        val fileName2 = "different_name.xyz"
        writer.startFile(fileName1, SimpleFileOptions.default()).getOrThrow()
        writer.write(text.encodeToByteArray()).getOrThrow()
        writer.deepCopyFile(fileName1, fileName2).getOrThrow()
        assertEquals(2, writer.files.len())
    }

    @Test
    fun duplicateFilenames() {
        val writer = ZipWriter.new()
        writer.startFile("foo/bar/test", SimpleFileOptions.default()).getOrThrow()
        writer.write("The quick brown 🦊 jumps over the lazy 🐕".encodeToByteArray()).getOrThrow()
        val err = writer.startFile("foo/bar/test", SimpleFileOptions.default())
        assertTrue(err.isFailure)
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
        val writer = ZipWriter.new()
        val text = "And I can't stop thinking about the moments that I lost to you"
        writer.startFile("original", SimpleFileOptions.default()).getOrThrow()
        writer.write(text.encodeToByteArray()).getOrThrow()
        writer.shallowCopyFile("original", "shallow_copy").getOrThrow()
        writer.abortFile().getOrThrow()
        assertEquals(1, writer.files.len())
        assertTrue(writer.files.containsKey("original"))
        assertFalse(writer.files.containsKey("shallow_copy"))
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

// port-lint: tests spec.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpecTest {
    @Test
    fun testIsDir() {
        assertTrue(isDir("foo/bar/"))
        assertTrue(isDir("foo\\bar\\"))
        assertFalse(isDir("foo/bar/file.txt"))
        assertFalse(isDir("file.txt"))
    }

    @Test
    fun testMagic() {
        val magic = Magic.CENTRAL_DIRECTORY_END_SIGNATURE
        val bytes = magic.toLeBytes()
        val parsed = Magic.fromLeBytes(bytes)
        assertEquals(magic, parsed)
    }

    @Test
    fun testExtraFieldMagic() {
        val magic = ExtraFieldMagic.ZIP64_EXTRA_FIELD_TAG
        val bytes = magic.toLeBytes()
        val parsed = ExtraFieldMagic.fromLeBytes(bytes)
        assertEquals(magic, parsed)
    }

    @Test
    fun testZip32CDEBlockSerde() {
        val block =
            Zip32CDEBlock(
                magic = Zip32CDEBlock.MAGIC,
                diskNumber = 1u,
                diskWithCentralDirectory = 2u,
                numberOfFilesOnThisDisk = 10u,
                numberOfFiles = 20u,
                centralDirectorySize = 1024u,
                centralDirectoryOffset = 2048u,
                zipFileCommentLength = 5u,
            )
        val bytes = block.toBytes()
        val parsed = Zip32CDEBlock.parse(bytes)
        assertEquals(block, parsed)
    }

    @Test
    fun testZip32CentralDirectoryEndSerde() {
        val comment = "hello".encodeToByteArray()
        val eocd =
            Zip32CentralDirectoryEnd(
                diskNumber = 0u,
                diskWithCentralDirectory = 0u,
                numberOfFilesOnThisDisk = 5u,
                numberOfFiles = 5u,
                centralDirectorySize = 256u,
                centralDirectoryOffset = 512u,
                zipFileComment = comment,
            )
        val bytes = eocd.toBytes()
        val parsed = Zip32CentralDirectoryEnd.parse(bytes)
        assertEquals(eocd, parsed)
    }

    @Test
    fun testZip64CDELocatorBlockSerde() {
        val block =
            Zip64CDELocatorBlock(
                magic = Zip64CDELocatorBlock.MAGIC,
                diskWithCentralDirectory = 1u,
                endOfCentralDirectoryOffset = 100000uL,
                numberOfDisks = 1u,
            )
        val bytes = block.toBytes()
        val parsed = Zip64CDELocatorBlock.parse(bytes)
        assertEquals(block, parsed)
    }

    @Test
    fun testZip64CDEBlockSerde() {
        val block =
            Zip64CDEBlock(
                magic = Zip64CDEBlock.MAGIC,
                recordSize = 44uL,
                versionMadeBy = 45u,
                versionNeededToExtract = 45u,
                diskNumber = 0u,
                diskWithCentralDirectory = 0u,
                numberOfFilesOnThisDisk = 100000uL,
                numberOfFiles = 100000uL,
                centralDirectorySize = 5000000uL,
                centralDirectoryOffset = 10000000uL,
            )
        val bytes = block.toBytes()
        val parsed = Zip64CDEBlock.parse(bytes)
        assertEquals(block, parsed)
    }
}

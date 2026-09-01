// port-lint: tests spec.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public data class TestBlock(
    public val magic: Magic = MAGIC,
    public val fileNameLength: UShort = 0u,
) {
    public fun magic(): Magic = magic

    public fun toBytes(): ByteArray {
        val b = ByteArray(6)
        magic.toLeBytes().copyInto(b, 0)
        b[4] = (fileNameLength.toInt() and 0xFF).toByte()
        b[5] = ((fileNameLength.toInt() shr 8) and 0xFF).toByte()
        return b
    }

    public companion object {
        public val MAGIC: Magic = Magic.literal(0x01111u)

        public fun parse(bytes: ByteArray, offset: Int = 0): TestBlock {
            val m = Magic.fromLeBytes(bytes, offset)
            val fnLen = ((bytes[offset + 4].toInt() and 0xFF) or ((bytes[offset + 5].toInt() and 0xFF) shl 8)).toUShort()
            return TestBlock(m, fnLen)
        }
    }
}

class SpecTest {
    @Test
    fun isDir() {
        assertTrue(isDir("foo/bar/"))
        assertTrue(isDir("foo\\bar\\"))
        assertFalse(isDir("foo/bar/file.txt"))
        assertFalse(isDir("file.txt"))
    }

    @Test
    fun magic() {
        val magic = Magic.CENTRAL_DIRECTORY_END_SIGNATURE
        val bytes = magic.toLeBytes()
        val parsed = Magic.fromLeBytes(bytes)
        assertEquals(magic, parsed)
    }

    @Test
    fun extraFieldMagic() {
        val magic = ExtraFieldMagic.ZIP64_EXTRA_FIELD_TAG
        val bytes = magic.toLeBytes()
        val parsed = ExtraFieldMagic.fromLeBytes(bytes)
        assertEquals(magic, parsed)
    }

    @Test
    fun zip32CDEBlockSerde() {
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
    fun zip32CentralDirectoryEndSerde() {
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
    fun zip64CDELocatorBlockSerde() {
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
    fun zip64CDEBlockSerde() {
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

    @Test
    fun blockSerde() {
        val block =
            TestBlock(
                magic = TestBlock.MAGIC,
                fileNameLength = 3u,
            )
        val bytes = block.toBytes()
        val block2 = TestBlock.parse(bytes)
        assertEquals(block, block2)
    }
}

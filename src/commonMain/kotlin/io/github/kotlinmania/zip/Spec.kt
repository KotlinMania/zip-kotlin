// port-lint: source spec.rs
package io.github.kotlinmania.zip

/**
 * "Magic" header values used in the zip spec to locate metadata records.
 */
public data class Magic(
    public val value: UInt,
) {
    public fun toLeBytes(): ByteArray =
        byteArrayOf(
            (value and 0xFFu).toByte(),
            ((value shr 8) and 0xFFu).toByte(),
            ((value shr 16) and 0xFFu).toByte(),
            ((value shr 24) and 0xFFu).toByte(),
        )

    public fun fromLe(): Magic = this

    public fun toLe(): Magic = this

    public companion object {
        public fun literal(x: UInt): Magic = Magic(x)

        public fun fromLeBytes(bytes: ByteArray, offset: Int = 0): Magic {
            val v =
                (bytes[offset].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 1].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 3].toInt() and 0xFF).toUInt() shl 24)
            return Magic(v)
        }

        public val LOCAL_FILE_HEADER_SIGNATURE: Magic = Magic(0x04034B50u)
        public val CENTRAL_DIRECTORY_HEADER_SIGNATURE: Magic = Magic(0x02014B50u)
        public val CENTRAL_DIRECTORY_END_SIGNATURE: Magic = Magic(0x06054B50u)
        public val ZIP64_CENTRAL_DIRECTORY_END_SIGNATURE: Magic = Magic(0x06064B50u)
        public val ZIP64_CENTRAL_DIRECTORY_END_LOCATOR_SIGNATURE: Magic = Magic(0x07064B50u)
    }
}

/**
 * Similar to [Magic], but used for extra field tags as per section 4.5.3 of APPNOTE.TXT.
 */
public data class ExtraFieldMagic(
    public val value: UShort,
) {
    public fun toLeBytes(): ByteArray =
        byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte(),
        )

    public fun fromLe(): ExtraFieldMagic = this

    public fun toLe(): ExtraFieldMagic = this

    public companion object {
        public fun literal(x: UShort): ExtraFieldMagic = ExtraFieldMagic(x)

        public fun fromLeBytes(bytes: ByteArray, offset: Int = 0): ExtraFieldMagic {
            val v =
                (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8)
            return ExtraFieldMagic(v.toUShort())
        }

        public val ZIP64_EXTRA_FIELD_TAG: ExtraFieldMagic = ExtraFieldMagic(0x0001u)
    }
}

/**
 * The file size threshold at which a ZIP64 record becomes necessary.
 */
public const val ZIP64_BYTES_THR: ULong = 0xFFFFFFFFuL

/**
 * The entry count threshold at which a ZIP64 record becomes necessary.
 */
public const val ZIP64_ENTRY_THR: UShort = 0xFFFFu

/**
 * Check if a filename corresponds to a directory.
 */
public fun isDir(filename: String): Boolean =
    filename.endsWith('/') || filename.endsWith('\\')

/**
 * Fixed size block for Zip32 Central Directory End.
 */
public data class Zip32CDEBlock(
    public val magic: Magic = Magic.CENTRAL_DIRECTORY_END_SIGNATURE,
    public val diskNumber: UShort = 0u,
    public val diskWithCentralDirectory: UShort = 0u,
    public val numberOfFilesOnThisDisk: UShort = 0u,
    public val numberOfFiles: UShort = 0u,
    public val centralDirectorySize: UInt = 0u,
    public val centralDirectoryOffset: UInt = 0u,
    public val zipFileCommentLength: UShort = 0u,
) {
    public fun toBytes(): ByteArray {
        val bytes = ByteArray(22)
        val mb = magic.toLeBytes()
        mb.copyInto(bytes, 0)
        bytes[4] = (diskNumber.toInt() and 0xFF).toByte()
        bytes[5] = ((diskNumber.toInt() shr 8) and 0xFF).toByte()
        bytes[6] = (diskWithCentralDirectory.toInt() and 0xFF).toByte()
        bytes[7] = ((diskWithCentralDirectory.toInt() shr 8) and 0xFF).toByte()
        bytes[8] = (numberOfFilesOnThisDisk.toInt() and 0xFF).toByte()
        bytes[9] = ((numberOfFilesOnThisDisk.toInt() shr 8) and 0xFF).toByte()
        bytes[10] = (numberOfFiles.toInt() and 0xFF).toByte()
        bytes[11] = ((numberOfFiles.toInt() shr 8) and 0xFF).toByte()
        bytes[12] = (centralDirectorySize.toInt() and 0xFF).toByte()
        bytes[13] = ((centralDirectorySize.toInt() shr 8) and 0xFF).toByte()
        bytes[14] = ((centralDirectorySize.toInt() shr 16) and 0xFF).toByte()
        bytes[15] = ((centralDirectorySize.toInt() shr 24) and 0xFF).toByte()
        bytes[16] = (centralDirectoryOffset.toInt() and 0xFF).toByte()
        bytes[17] = ((centralDirectoryOffset.toInt() shr 8) and 0xFF).toByte()
        bytes[18] = ((centralDirectoryOffset.toInt() shr 16) and 0xFF).toByte()
        bytes[19] = ((centralDirectoryOffset.toInt() shr 24) and 0xFF).toByte()
        bytes[20] = (zipFileCommentLength.toInt() and 0xFF).toByte()
        bytes[21] = ((zipFileCommentLength.toInt() shr 8) and 0xFF).toByte()
        return bytes
    }

    public companion object {
        public val MAGIC: Magic = Magic.CENTRAL_DIRECTORY_END_SIGNATURE

        public fun parse(bytes: ByteArray, offset: Int = 0): Zip32CDEBlock {
            if (bytes.size - offset < 22) {
                throw ZipError.InvalidArchive("Unexpected EOF reading Zip32CDEBlock")
            }
            val magic = Magic.fromLeBytes(bytes, offset)
            if (magic != MAGIC) {
                throw ZipError.InvalidArchive("Invalid digital signature header")
            }
            val diskNumber = ((bytes[offset + 4].toInt() and 0xFF) or ((bytes[offset + 5].toInt() and 0xFF) shl 8)).toUShort()
            val diskWithCentralDirectory = ((bytes[offset + 6].toInt() and 0xFF) or ((bytes[offset + 7].toInt() and 0xFF) shl 8)).toUShort()
            val numberOfFilesOnThisDisk = ((bytes[offset + 8].toInt() and 0xFF) or ((bytes[offset + 9].toInt() and 0xFF) shl 8)).toUShort()
            val numberOfFiles = ((bytes[offset + 10].toInt() and 0xFF) or ((bytes[offset + 11].toInt() and 0xFF) shl 8)).toUShort()
            val centralDirectorySize = (
                (bytes[offset + 12].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 13].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 14].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 15].toInt() and 0xFF).toUInt() shl 24)
            )
            val centralDirectoryOffset = (
                (bytes[offset + 16].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 17].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 18].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 19].toInt() and 0xFF).toUInt() shl 24)
            )
            val zipFileCommentLength = ((bytes[offset + 20].toInt() and 0xFF) or ((bytes[offset + 21].toInt() and 0xFF) shl 8)).toUShort()
            return Zip32CDEBlock(
                magic = magic,
                diskNumber = diskNumber,
                diskWithCentralDirectory = diskWithCentralDirectory,
                numberOfFilesOnThisDisk = numberOfFilesOnThisDisk,
                numberOfFiles = numberOfFiles,
                centralDirectorySize = centralDirectorySize,
                centralDirectoryOffset = centralDirectoryOffset,
                zipFileCommentLength = zipFileCommentLength,
            )
        }
    }
}

/**
 * Zip32 Central Directory End record with parsed comment.
 */
public data class Zip32CentralDirectoryEnd(
    public val diskNumber: UShort,
    public val diskWithCentralDirectory: UShort,
    public val numberOfFilesOnThisDisk: UShort,
    public val numberOfFiles: UShort,
    public val centralDirectorySize: UInt,
    public val centralDirectoryOffset: UInt,
    public val zipFileComment: ByteArray,
) {
    public fun intoBlockAndComment(): Pair<Zip32CDEBlock, ByteArray> {
        val block =
            Zip32CDEBlock(
                magic = Zip32CDEBlock.MAGIC,
                diskNumber = diskNumber,
                diskWithCentralDirectory = diskWithCentralDirectory,
                numberOfFilesOnThisDisk = numberOfFilesOnThisDisk,
                numberOfFiles = numberOfFiles,
                centralDirectorySize = centralDirectorySize,
                centralDirectoryOffset = centralDirectoryOffset,
                zipFileCommentLength = zipFileComment.size.toUShort(),
            )
        return Pair(block, zipFileComment)
    }

    public fun mayBeZip64(): Boolean =
        numberOfFiles == 0xFFFFu.toUShort() || centralDirectoryOffset == 0xFFFFFFFFu

    public fun toBytes(): ByteArray {
        val (block, comment) = intoBlockAndComment()
        return block.toBytes() + comment
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Zip32CentralDirectoryEnd) return false
        return diskNumber == other.diskNumber &&
            diskWithCentralDirectory == other.diskWithCentralDirectory &&
            numberOfFilesOnThisDisk == other.numberOfFilesOnThisDisk &&
            numberOfFiles == other.numberOfFiles &&
            centralDirectorySize == other.centralDirectorySize &&
            centralDirectoryOffset == other.centralDirectoryOffset &&
            zipFileComment.contentEquals(other.zipFileComment)
    }

    override fun hashCode(): Int {
        var result = diskNumber.hashCode()
        result = 31 * result + diskWithCentralDirectory.hashCode()
        result = 31 * result + numberOfFilesOnThisDisk.hashCode()
        result = 31 * result + numberOfFiles.hashCode()
        result = 31 * result + centralDirectorySize.hashCode()
        result = 31 * result + centralDirectoryOffset.hashCode()
        result = 31 * result + zipFileComment.contentHashCode()
        return result
    }

    public companion object {
        public fun parse(bytes: ByteArray, offset: Int = 0): Zip32CentralDirectoryEnd {
            val block = Zip32CDEBlock.parse(bytes, offset)
            val commentLen = block.zipFileCommentLength.toInt()
            val commentStart = offset + 22
            if (bytes.size - commentStart < commentLen) {
                throw ZipError.InvalidArchive("EOCD comment exceeds file boundary")
            }
            val comment = bytes.copyOfRange(commentStart, commentStart + commentLen)
            return Zip32CentralDirectoryEnd(
                diskNumber = block.diskNumber,
                diskWithCentralDirectory = block.diskWithCentralDirectory,
                numberOfFilesOnThisDisk = block.numberOfFilesOnThisDisk,
                numberOfFiles = block.numberOfFiles,
                centralDirectorySize = block.centralDirectorySize,
                centralDirectoryOffset = block.centralDirectoryOffset,
                zipFileComment = comment,
            )
        }
    }
}

/**
 * Zip64 Central Directory End Locator Block.
 */
public data class Zip64CDELocatorBlock(
    public val magic: Magic = Magic.ZIP64_CENTRAL_DIRECTORY_END_LOCATOR_SIGNATURE,
    public val diskWithCentralDirectory: UInt = 0u,
    public val endOfCentralDirectoryOffset: ULong = 0uL,
    public val numberOfDisks: UInt = 0u,
) {
    public fun toBytes(): ByteArray {
        val bytes = ByteArray(20)
        val mb = magic.toLeBytes()
        mb.copyInto(bytes, 0)
        bytes[4] = (diskWithCentralDirectory.toInt() and 0xFF).toByte()
        bytes[5] = ((diskWithCentralDirectory.toInt() shr 8) and 0xFF).toByte()
        bytes[6] = ((diskWithCentralDirectory.toInt() shr 16) and 0xFF).toByte()
        bytes[7] = ((diskWithCentralDirectory.toInt() shr 24) and 0xFF).toByte()
        for (i in 0 until 8) {
            bytes[8 + i] = ((endOfCentralDirectoryOffset shr (i * 8)) and 0xFFuL).toByte()
        }
        bytes[16] = (numberOfDisks.toInt() and 0xFF).toByte()
        bytes[17] = ((numberOfDisks.toInt() shr 8) and 0xFF).toByte()
        bytes[18] = ((numberOfDisks.toInt() shr 16) and 0xFF).toByte()
        bytes[19] = ((numberOfDisks.toInt() shr 24) and 0xFF).toByte()
        return bytes
    }

    public companion object {
        public val MAGIC: Magic = Magic.ZIP64_CENTRAL_DIRECTORY_END_LOCATOR_SIGNATURE

        public fun parse(bytes: ByteArray, offset: Int = 0): Zip64CDELocatorBlock {
            if (bytes.size - offset < 20) {
                throw ZipError.InvalidArchive("Unexpected EOF reading Zip64CDELocatorBlock")
            }
            val magic = Magic.fromLeBytes(bytes, offset)
            if (magic != MAGIC) {
                throw ZipError.InvalidArchive("Invalid zip64 locator digital signature header")
            }
            val diskWithCentralDirectory = (
                (bytes[offset + 4].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 5].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 6].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 7].toInt() and 0xFF).toUInt() shl 24)
            )
            var endOfCentralDirectoryOffset: ULong = 0uL
            for (i in 0 until 8) {
                endOfCentralDirectoryOffset = endOfCentralDirectoryOffset or
                    ((bytes[offset + 8 + i].toLong() and 0xFFL).toULong() shl (i * 8))
            }
            val numberOfDisks = (
                (bytes[offset + 16].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 17].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 18].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 19].toInt() and 0xFF).toUInt() shl 24)
            )
            return Zip64CDELocatorBlock(
                magic = magic,
                diskWithCentralDirectory = diskWithCentralDirectory,
                endOfCentralDirectoryOffset = endOfCentralDirectoryOffset,
                numberOfDisks = numberOfDisks,
            )
        }
    }
}

/**
 * Zip64 Central Directory End Locator.
 */
public data class Zip64CentralDirectoryEndLocator(
    public val diskWithCentralDirectory: UInt,
    public val endOfCentralDirectoryOffset: ULong,
    public val numberOfDisks: UInt,
) {
    public fun block(): Zip64CDELocatorBlock =
        Zip64CDELocatorBlock(
            magic = Zip64CDELocatorBlock.MAGIC,
            diskWithCentralDirectory = diskWithCentralDirectory,
            endOfCentralDirectoryOffset = endOfCentralDirectoryOffset,
            numberOfDisks = numberOfDisks,
        )

    public fun toBytes(): ByteArray = block().toBytes()

    public companion object {
        public fun parse(bytes: ByteArray, offset: Int = 0): Zip64CentralDirectoryEndLocator {
            val blk = Zip64CDELocatorBlock.parse(bytes, offset)
            return Zip64CentralDirectoryEndLocator(
                diskWithCentralDirectory = blk.diskWithCentralDirectory,
                endOfCentralDirectoryOffset = blk.endOfCentralDirectoryOffset,
                numberOfDisks = blk.numberOfDisks,
            )
        }
    }
}

/**
 * Zip64 Central Directory End Block.
 */
public data class Zip64CDEBlock(
    public val magic: Magic = Magic.ZIP64_CENTRAL_DIRECTORY_END_SIGNATURE,
    public val recordSize: ULong = 0uL,
    public val versionMadeBy: UShort = 0u,
    public val versionNeededToExtract: UShort = 0u,
    public val diskNumber: UInt = 0u,
    public val diskWithCentralDirectory: UInt = 0u,
    public val numberOfFilesOnThisDisk: ULong = 0uL,
    public val numberOfFiles: ULong = 0uL,
    public val centralDirectorySize: ULong = 0uL,
    public val centralDirectoryOffset: ULong = 0uL,
) {
    public fun toBytes(): ByteArray {
        val bytes = ByteArray(56)
        val mb = magic.toLeBytes()
        mb.copyInto(bytes, 0)
        for (i in 0 until 8) {
            bytes[4 + i] = ((recordSize shr (i * 8)) and 0xFFuL).toByte()
        }
        bytes[12] = (versionMadeBy.toInt() and 0xFF).toByte()
        bytes[13] = ((versionMadeBy.toInt() shr 8) and 0xFF).toByte()
        bytes[14] = (versionNeededToExtract.toInt() and 0xFF).toByte()
        bytes[15] = ((versionNeededToExtract.toInt() shr 8) and 0xFF).toByte()
        bytes[16] = (diskNumber.toInt() and 0xFF).toByte()
        bytes[17] = ((diskNumber.toInt() shr 8) and 0xFF).toByte()
        bytes[18] = ((diskNumber.toInt() shr 16) and 0xFF).toByte()
        bytes[19] = ((diskNumber.toInt() shr 24) and 0xFF).toByte()
        bytes[20] = (diskWithCentralDirectory.toInt() and 0xFF).toByte()
        bytes[21] = ((diskWithCentralDirectory.toInt() shr 8) and 0xFF).toByte()
        bytes[22] = ((diskWithCentralDirectory.toInt() shr 16) and 0xFF).toByte()
        bytes[23] = ((diskWithCentralDirectory.toInt() shr 24) and 0xFF).toByte()
        for (i in 0 until 8) {
            bytes[24 + i] = ((numberOfFilesOnThisDisk shr (i * 8)) and 0xFFuL).toByte()
            bytes[32 + i] = ((numberOfFiles shr (i * 8)) and 0xFFuL).toByte()
            bytes[40 + i] = ((centralDirectorySize shr (i * 8)) and 0xFFuL).toByte()
            bytes[48 + i] = ((centralDirectoryOffset shr (i * 8)) and 0xFFuL).toByte()
        }
        return bytes
    }

    public companion object {
        public val MAGIC: Magic = Magic.ZIP64_CENTRAL_DIRECTORY_END_SIGNATURE

        public fun parse(bytes: ByteArray, offset: Int = 0): Zip64CDEBlock {
            if (bytes.size - offset < 56) {
                throw ZipError.InvalidArchive("Unexpected EOF reading Zip64CDEBlock")
            }
            val magic = Magic.fromLeBytes(bytes, offset)
            if (magic != MAGIC) {
                throw ZipError.InvalidArchive("Invalid digital signature header")
            }
            var recordSize: ULong = 0uL
            for (i in 0 until 8) {
                recordSize = recordSize or ((bytes[offset + 4 + i].toLong() and 0xFFL).toULong() shl (i * 8))
            }
            val versionMadeBy = ((bytes[offset + 12].toInt() and 0xFF) or ((bytes[offset + 13].toInt() and 0xFF) shl 8)).toUShort()
            val versionNeededToExtract = ((bytes[offset + 14].toInt() and 0xFF) or ((bytes[offset + 15].toInt() and 0xFF) shl 8)).toUShort()
            val diskNumber = (
                (bytes[offset + 16].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 17].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 18].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 19].toInt() and 0xFF).toUInt() shl 24)
            )
            val diskWithCentralDirectory = (
                (bytes[offset + 20].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 21].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 22].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 23].toInt() and 0xFF).toUInt() shl 24)
            )
            var numberOfFilesOnThisDisk: ULong = 0uL
            var numberOfFiles: ULong = 0uL
            var centralDirectorySize: ULong = 0uL
            var centralDirectoryOffset: ULong = 0uL
            for (i in 0 until 8) {
                numberOfFilesOnThisDisk = numberOfFilesOnThisDisk or ((bytes[offset + 24 + i].toLong() and 0xFFL).toULong() shl (i * 8))
                numberOfFiles = numberOfFiles or ((bytes[offset + 32 + i].toLong() and 0xFFL).toULong() shl (i * 8))
                centralDirectorySize = centralDirectorySize or ((bytes[offset + 40 + i].toLong() and 0xFFL).toULong() shl (i * 8))
                centralDirectoryOffset = centralDirectoryOffset or ((bytes[offset + 48 + i].toLong() and 0xFFL).toULong() shl (i * 8))
            }
            return Zip64CDEBlock(
                magic = magic,
                recordSize = recordSize,
                versionMadeBy = versionMadeBy,
                versionNeededToExtract = versionNeededToExtract,
                diskNumber = diskNumber,
                diskWithCentralDirectory = diskWithCentralDirectory,
                numberOfFilesOnThisDisk = numberOfFilesOnThisDisk,
                numberOfFiles = numberOfFiles,
                centralDirectorySize = centralDirectorySize,
                centralDirectoryOffset = centralDirectoryOffset,
            )
        }
    }
}

/**
 * Zip64 Central Directory End record.
 */
public data class Zip64CentralDirectoryEnd(
    public val recordSize: ULong,
    public val versionMadeBy: UShort,
    public val versionNeededToExtract: UShort,
    public val diskNumber: UInt,
    public val diskWithCentralDirectory: UInt,
    public val numberOfFilesOnThisDisk: ULong,
    public val numberOfFiles: ULong,
    public val centralDirectorySize: ULong,
    public val centralDirectoryOffset: ULong,
    public val extensibleDataSector: ByteArray,
) {
    public fun intoBlockAndComment(): Pair<Zip64CDEBlock, ByteArray> {
        val block =
            Zip64CDEBlock(
                magic = Zip64CDEBlock.MAGIC,
                recordSize = recordSize,
                versionMadeBy = versionMadeBy,
                versionNeededToExtract = versionNeededToExtract,
                diskNumber = diskNumber,
                diskWithCentralDirectory = diskWithCentralDirectory,
                numberOfFilesOnThisDisk = numberOfFilesOnThisDisk,
                numberOfFiles = numberOfFiles,
                centralDirectorySize = centralDirectorySize,
                centralDirectoryOffset = centralDirectoryOffset,
            )
        return Pair(block, extensibleDataSector)
    }

    public fun toBytes(): ByteArray {
        val (block, data) = intoBlockAndComment()
        return block.toBytes() + data
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Zip64CentralDirectoryEnd) return false
        return recordSize == other.recordSize &&
            versionMadeBy == other.versionMadeBy &&
            versionNeededToExtract == other.versionNeededToExtract &&
            diskNumber == other.diskNumber &&
            diskWithCentralDirectory == other.diskWithCentralDirectory &&
            numberOfFilesOnThisDisk == other.numberOfFilesOnThisDisk &&
            numberOfFiles == other.numberOfFiles &&
            centralDirectorySize == other.centralDirectorySize &&
            centralDirectoryOffset == other.centralDirectoryOffset &&
            extensibleDataSector.contentEquals(other.extensibleDataSector)
    }

    override fun hashCode(): Int {
        var result = recordSize.hashCode()
        result = 31 * result + versionMadeBy.hashCode()
        result = 31 * result + versionNeededToExtract.hashCode()
        result = 31 * result + diskNumber.hashCode()
        result = 31 * result + diskWithCentralDirectory.hashCode()
        result = 31 * result + numberOfFilesOnThisDisk.hashCode()
        result = 31 * result + numberOfFiles.hashCode()
        result = 31 * result + centralDirectorySize.hashCode()
        result = 31 * result + centralDirectoryOffset.hashCode()
        result = 31 * result + extensibleDataSector.contentHashCode()
        return result
    }

    public companion object {
        public fun parse(bytes: ByteArray, offset: Int = 0, maxSize: ULong = ULong.MAX_VALUE): Zip64CentralDirectoryEnd {
            val blk = Zip64CDEBlock.parse(bytes, offset)
            if (blk.recordSize < 44uL) {
                throw ZipError.InvalidArchive("Low EOCD64 record size")
            } else if (blk.recordSize + 12uL > maxSize) {
                throw ZipError.InvalidArchive("EOCD64 extends beyond EOCD64 locator")
            }
            val extLen = (blk.recordSize - 44uL).toInt()
            val extStart = offset + 56
            val extData =
                if (extLen > 0 && bytes.size >= extStart + extLen) {
                    bytes.copyOfRange(extStart, extStart + extLen)
                } else {
                    ByteArray(0)
                }
            return Zip64CentralDirectoryEnd(
                recordSize = blk.recordSize,
                versionMadeBy = blk.versionMadeBy,
                versionNeededToExtract = blk.versionNeededToExtract,
                diskNumber = blk.diskNumber,
                diskWithCentralDirectory = blk.diskWithCentralDirectory,
                numberOfFilesOnThisDisk = blk.numberOfFilesOnThisDisk,
                numberOfFiles = blk.numberOfFiles,
                centralDirectorySize = blk.centralDirectorySize,
                centralDirectoryOffset = blk.centralDirectoryOffset,
                extensibleDataSector = extData,
            )
        }
    }
}

/**
 * Zip32 data associated with its position in an archive.
 */
public data class Zip32DataAndPosition(
    public val data: Zip32CentralDirectoryEnd,
    public val position: ULong,
)

/**
 * Zip64 data associated with its position in an archive.
 */
public data class Zip64DataAndPosition(
    public val data: Zip64CentralDirectoryEnd,
    public val position: ULong,
)

/**
 * Result of finding central directory information.
 */
public data class CentralDirectoryEndInfo(
    public val eocd: Zip32DataAndPosition,
    public val eocd64: Zip64DataAndPosition? = null,
    public val archiveOffset: ULong = 0uL,
)

/**
 * Legacy spec constants object.
 */
public object Spec {
    public const val LOCAL_FILE_HEADER_SIGNATURE: UInt = 0x04034B50u
    public const val CENTRAL_DIRECTORY_HEADER_SIGNATURE: UInt = 0x02014B50u
    public const val CENTRAL_DIRECTORY_END_SIGNATURE: UInt = 0x06054B50u
    public const val ZIP64_CENTRAL_DIRECTORY_END_SIGNATURE: UInt = 0x06064B50u
    public const val ZIP64_CENTRAL_DIRECTORY_END_LOCATOR_SIGNATURE: UInt = 0x07064B50u
    public const val DATA_DESCRIPTOR_SIGNATURE: UInt = 0x08074B50u

    public const val ZIP64_EXTRA_FIELD_TAG: UShort = 0x0001u
    public const val AES_EXTRA_FIELD_TAG: UShort = 0x9901u
    public const val EXTENDED_TIMESTAMP_EXTRA_FIELD_TAG: UShort = 0x5455u
    public const val INFOZIP_UNICODE_PATH_EXTRA_FIELD_TAG: UShort = 0x7075u
    public const val INFOZIP_UNICODE_COMMENT_EXTRA_FIELD_TAG: UShort = 0x6375u
}

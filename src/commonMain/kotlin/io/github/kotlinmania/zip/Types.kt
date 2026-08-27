// port-lint: source zip/src/types.rs
package io.github.kotlinmania.zip

public object Ffi {
    public const val S_IFDIR: UInt = 0x4000u
    public const val S_IFREG: UInt = 0x8000u
    public const val S_IFLNK: UInt = 0xA000u
}

public const val MIN_VERSION: UByte = 10u
public const val DEFAULT_VERSION: UByte = 45u

/**
 * Operating system compatibility of file attributes.
 */
public enum class System(
    public val code: UByte,
) {
    Dos(0u),
    Unix(3u),
    Unknown(4u),
    ;

    public companion object {
        public fun fromCode(code: UByte): System =
            when (code.toInt()) {
                0 -> Dos
                3 -> Unix
                else -> Unknown
            }
    }
}

/**
 * Representation of a moment in time stored in MS-DOS format (2-second resolution).
 */
public class DateTime(
    public val datepart: UShort,
    public val timepart: UShort,
) : Comparable<DateTime> {
    public fun year(): Int = ((datepart.toInt() ushr 9) and 0x7F) + 1980

    public fun month(): Int = (datepart.toInt() ushr 5) and 0x0F

    public fun day(): Int = datepart.toInt() and 0x1F

    public fun hour(): Int = (timepart.toInt() ushr 11) and 0x1F

    public fun minute(): Int = (timepart.toInt() ushr 5) and 0x3F

    public fun second(): Int = (timepart.toInt() and 0x1F) shl 1

    public fun isValid(): Boolean =
        tryFromMsdos(datepart, timepart).isSuccess

    public fun toPair(): Pair<UShort, UShort> = Pair(datepart, timepart)

    override fun compareTo(other: DateTime): Int {
        if (year() != other.year()) return year().compareTo(other.year())
        if (month() != other.month()) return month().compareTo(other.month())
        if (day() != other.day()) return day().compareTo(other.day())
        if (hour() != other.hour()) return hour().compareTo(other.hour())
        if (minute() != other.minute()) return minute().compareTo(other.minute())
        return second().compareTo(other.second())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DateTime) return false
        return datepart == other.datepart && timepart == other.timepart
    }

    override fun hashCode(): Int = 31 * datepart.hashCode() + timepart.hashCode()

    override fun toString(): String {
        val y = year().toString().padStart(4, '0')
        val m = month().toString().padStart(2, '0')
        val d = day().toString().padStart(2, '0')
        val h = hour().toString().padStart(2, '0')
        val min = minute().toString().padStart(2, '0')
        val s = second().toString().padStart(2, '0')
        return "$y-$m-$d $h:$min:$s"
    }

    public companion object {
        /**
         * Default DateTime of 1980-01-01 00:00:00.
         */
        public fun default(): DateTime =
            DateTime(0b0000000000100001u, 0u)

        public fun fromMsdosUnchecked(datepart: UShort, timepart: UShort): DateTime =
            DateTime(datepart, timepart)

        public fun tryFromMsdos(datepart: UShort, timepart: UShort): Result<DateTime> {
            val seconds = (timepart.toInt() and 0x1F) shl 1
            val minutes = (timepart.toInt() ushr 5) and 0x3F
            val hours = (timepart.toInt() ushr 11) and 0x1F
            val days = datepart.toInt() and 0x1F
            val months = (datepart.toInt() ushr 5) and 0x0F
            val years = (datepart.toInt() ushr 9) and 0x7F
            val fullYear = years + 1980
            return fromDateAndTime(fullYear, months, days, hours, minutes, seconds)
        }

        public fun fromDateAndTime(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int,
        ): Result<DateTime> {
            fun isLeapYear(y: Int): Boolean =
                (y % 4 == 0) && ((y % 25 != 0) || (y % 16 == 0))

            if (year in 1980..2107 && month in 1..12 && day in 1..31 && hour in 0..23 && minute in 0..59 && second in 0..60) {
                val clampedSecond = minOf(second, 58)
                val maxDay =
                    when (month) {
                        1, 3, 5, 7, 8, 10, 12 -> 31
                        4, 6, 9, 11 -> 30
                        2 -> if (isLeapYear(year)) 29 else 28
                        else -> 0
                    }
                if (day > maxDay) {
                    return Result.failure(DateTimeRangeError())
                }
                val datepart = (day or (month shl 5) or ((year - 1980) shl 9)).toUShort()
                val timepart = ((clampedSecond ushr 1) or (minute shl 5) or (hour shl 11)).toUShort()
                return Result.success(DateTime(datepart, timepart))
            } else {
                return Result.failure(DateTimeRangeError())
            }
        }
    }
}

/**
 * AES variant used in encryption.
 */
public enum class AesMode(
    public val code: UByte,
) {
    Aes128(0x01u),
    Aes192(0x02u),
    Aes256(0x03u),
    ;

    public fun keyLength(): Int =
        when (this) {
            Aes128 -> 16
            Aes192 -> 24
            Aes256 -> 32
        }

    public fun saltLength(): Int = keyLength() / 2
}

/**
 * AES vendor version specification.
 */
public enum class AesVendorVersion(
    public val code: UShort,
) {
    Ae1(0x0001u),
    Ae2(0x0002u),
}

/**
 * Raw values computed for an entry.
 */
public data class ZipRawValues(
    public val crc32: UInt,
    public val compressedSize: ULong,
    public val uncompressedSize: ULong,
)

/**
 * Structure representing metadata for a ZIP file entry.
 */
public data class ZipFileData(
    public var system: System = System.Dos,
    public var versionMadeBy: UByte = DEFAULT_VERSION,
    public var encrypted: Boolean = false,
    public var isUtf8: Boolean = true,
    public var usingDataDescriptor: Boolean = false,
    public var compressionMethod: CompressionMethod = CompressionMethod.Stored,
    public var compressionLevel: Long? = null,
    public var lastModifiedTime: DateTime? = null,
    public var crc32: UInt = 0u,
    public var compressedSize: ULong = 0uL,
    public var uncompressedSize: ULong = 0uL,
    public var fileName: String = "",
    public var fileNameRaw: ByteArray = ByteArray(0),
    public var extraField: ByteArray? = null,
    public var centralExtraField: ByteArray? = null,
    public var fileComment: String = "",
    public var headerStart: ULong = 0uL,
    public var extraDataStart: ULong? = null,
    public var centralHeaderStart: ULong = 0uL,
    public var dataStartOffset: ULong? = null,
    public var externalAttributes: UInt = 0u,
    public var largeFile: Boolean = false,
    public var aesMode: AesMode? = null,
    public var aesVendorVersion: AesVendorVersion? = null,
    public var aesExtraDataStart: ULong = 0uL,
) {
    public fun isDir(): Boolean =
        fileName.endsWith('/') || fileName.endsWith('\\')

    public fun dataStart(): ULong = dataStartOffset ?: 0uL

    public fun extraFieldLen(): Int = extraField?.size ?: 0

    public fun centralExtraFieldLen(): Int = centralExtraField?.size ?: 0

    public fun isAscii(): Boolean {
        for (b in fileNameRaw) {
            if (b.toInt() < 0) return false
        }
        return true
    }

    public fun flags(): UShort {
        val utf8Bit: UShort = if (isUtf8 && !isAscii()) (1 shl 11).toUShort() else 0u
        val encryptedBit: UShort = if (encrypted) 1u else 0u
        return (utf8Bit.toInt() or encryptedBit.toInt()).toUShort()
    }

    public fun clampSizeField(field: ULong): UInt =
        if (largeFile) {
            0xFFFFFFFFu
        } else {
            minOf(field, ZIP64_BYTES_THR).toUInt()
        }

    public fun localBlock(): Result<ZipLocalEntryBlock> {
        val compressed = clampSizeField(compressedSize)
        val uncompressed = clampSizeField(uncompressedSize)
        val extraFieldLength = extraFieldLen()
        if (extraFieldLength > 0xFFFF) {
            return Result.failure(ZipError.InvalidArchive("Extra data field is too large"))
        }
        val lmt = lastModifiedTime ?: DateTime.default()
        return Result.success(
            ZipLocalEntryBlock(
                magic = ZipLocalEntryBlock.MAGIC,
                versionMadeBy = versionNeeded(),
                flags = flags(),
                compressionMethod = compressionMethod.serializeToU16(),
                lastModTime = lmt.timepart,
                lastModDate = lmt.datepart,
                crc32 = crc32,
                compressedSize = compressed,
                uncompressedSize = uncompressed,
                fileNameLength = fileNameRaw.size.toUShort(),
                extraFieldLength = extraFieldLength.toUShort(),
            ),
        )
    }

    public fun block(): Result<ZipCentralEntryBlock> {
        val extraLen = extraFieldLen()
        val centralExtraLen = centralExtraFieldLen()
        val totalExtraLen = extraLen + centralExtraLen
        if (totalExtraLen > 0xFFFF) {
            return Result.failure(ZipError.InvalidArchive("Extra field length in central directory exceeds 64KiB"))
        }
        val lmt = lastModifiedTime ?: DateTime.default()
        val versionToExtract = versionNeeded()
        val versionMadeByVal = maxOf(versionMadeBy.toUShort(), versionToExtract)
        val systemVal = (system.code.toInt() shl 8) or versionMadeByVal.toInt()

        return Result.success(
            ZipCentralEntryBlock(
                magic = ZipCentralEntryBlock.MAGIC,
                versionMadeBy = systemVal.toUShort(),
                versionToExtract = versionToExtract,
                flags = flags(),
                compressionMethod = compressionMethod.serializeToU16(),
                lastModTime = lmt.timepart,
                lastModDate = lmt.datepart,
                crc32 = crc32,
                compressedSize = minOf(compressedSize, ZIP64_BYTES_THR).toUInt(),
                uncompressedSize = minOf(uncompressedSize, ZIP64_BYTES_THR).toUInt(),
                fileNameLength = fileNameRaw.size.toUShort(),
                extraFieldLength = totalExtraLen.toUShort(),
                fileCommentLength = fileComment.encodeToByteArray().size.toUShort(),
                diskNumber = 0u,
                internalFileAttributes = 0u,
                externalFileAttributes = externalAttributes,
                offset = minOf(headerStart, ZIP64_BYTES_THR).toUInt(),
            ),
        )
    }

    public fun zip64ExtraFieldBlock(): Zip64ExtraFieldBlock? =
        Zip64ExtraFieldBlock.maybeNew(
            largeFile,
            uncompressedSize,
            compressedSize,
            headerStart,
        )

    public fun fileNameSanitized(): String {
        val noNull = fileName.substringBefore('\u0000')
        val normalized = noNull.replace('\\', '/')
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." && it != ".." }
        return parts.joinToString("/")
    }

    public fun simplifiedComponents(): List<String>? {
        if (fileName.contains('\u0000')) return null
        return simplifiedComponents(fileName)
    }

    public fun enclosedName(): String? {
        if (fileName.contains('\u0000')) return null
        val normalized = fileName.replace('\\', '/')
        if (normalized.startsWith("/")) return null
        var depth = 0
        for (comp in normalized.split('/')) {
            if (comp.isEmpty() || comp == ".") continue
            if (comp == "..") {
                depth--
                if (depth < 0) return null
            } else {
                depth++
            }
        }
        return fileName
    }

    public fun unixMode(): UInt? {
        if (externalAttributes == 0u) return null
        return when (system) {
            System.Unix -> externalAttributes shr 16
            System.Dos -> {
                var mode =
                    if ((externalAttributes and 0x10u) == 0x10u) {
                        Ffi.S_IFDIR or 0x01FDu // 0775 octal
                    } else {
                        Ffi.S_IFREG or 0x01B4u // 0664 octal
                    }
                if ((externalAttributes and 0x01u) == 0x01u) {
                    mode = mode and 0x016Du // 0555 octal
                }
                mode
            }
            else -> null
        }
    }

    public fun versionNeeded(): UShort {
        val compressionVersion: UShort =
            when (compressionMethod) {
                CompressionMethod.Stored -> MIN_VERSION.toUShort()
                CompressionMethod.Deflated -> 20u
                CompressionMethod.Deflate64 -> 21u
                CompressionMethod.Bzip2 -> 46u
                CompressionMethod.Lzma, CompressionMethod.Xz -> 63u
                else -> DEFAULT_VERSION.toUShort()
            }
        val cryptoVersion: UShort =
            if (aesMode != null) {
                51u
            } else if (encrypted) {
                20u
            } else {
                10u
            }
        val miscFeatureVersion: UShort =
            if (largeFile) {
                45u
            } else {
                val mode = unixMode()
                if (mode != null && (mode and Ffi.S_IFDIR) == Ffi.S_IFDIR) {
                    20u
                } else {
                    10u
                }
            }
        return maxOf(compressionVersion, cryptoVersion, miscFeatureVersion)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZipFileData) return false
        return system == other.system &&
            versionMadeBy == other.versionMadeBy &&
            encrypted == other.encrypted &&
            isUtf8 == other.isUtf8 &&
            usingDataDescriptor == other.usingDataDescriptor &&
            compressionMethod == other.compressionMethod &&
            compressionLevel == other.compressionLevel &&
            lastModifiedTime == other.lastModifiedTime &&
            crc32 == other.crc32 &&
            compressedSize == other.compressedSize &&
            uncompressedSize == other.uncompressedSize &&
            fileName == other.fileName &&
            fileNameRaw.contentEquals(other.fileNameRaw) &&
            ((extraField == null && other.extraField == null) || (extraField != null && other.extraField != null && extraField.contentEquals(other.extraField))) &&
            ((centralExtraField == null && other.centralExtraField == null) || (centralExtraField != null && other.centralExtraField != null && centralExtraField.contentEquals(other.centralExtraField))) &&
            fileComment == other.fileComment &&
            headerStart == other.headerStart &&
            extraDataStart == other.extraDataStart &&
            centralHeaderStart == other.centralHeaderStart &&
            externalAttributes == other.externalAttributes &&
            largeFile == other.largeFile &&
            aesMode == other.aesMode &&
            aesExtraDataStart == other.aesExtraDataStart
    }

    override fun hashCode(): Int {
        var result = system.hashCode()
        result = 31 * result + versionMadeBy.hashCode()
        result = 31 * result + encrypted.hashCode()
        result = 31 * result + isUtf8.hashCode()
        result = 31 * result + compressionMethod.hashCode()
        result = 31 * result + crc32.hashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }
}

/**
 * Options for configuring how a file entry is added to a ZIP archive.
 */
public data class FileOptions(
    public val compressionMethod: CompressionMethod = CompressionMethod.Stored,
    public val compressionLevel: Long? = null,
    public val lastModifiedTime: DateTime = DateTime.default(),
    public val permissions: UInt? = null,
    public val largeFile: Boolean = false,
    public val encryptWith: String? = null,
    public val aesMode: AesMode? = null,
)

/**
 * Central Directory Entry block structure.
 */
public data class ZipCentralEntryBlock(
    public val magic: Magic = Magic.CENTRAL_DIRECTORY_HEADER_SIGNATURE,
    public val versionMadeBy: UShort = 0u,
    public val versionToExtract: UShort = 0u,
    public val flags: UShort = 0u,
    public val compressionMethod: UShort = 0u,
    public val lastModTime: UShort = 0u,
    public val lastModDate: UShort = 0u,
    public val crc32: UInt = 0u,
    public val compressedSize: UInt = 0u,
    public val uncompressedSize: UInt = 0u,
    public val fileNameLength: UShort = 0u,
    public val extraFieldLength: UShort = 0u,
    public val fileCommentLength: UShort = 0u,
    public val diskNumber: UShort = 0u,
    public val internalFileAttributes: UShort = 0u,
    public val externalFileAttributes: UInt = 0u,
    public val offset: UInt = 0u,
) {
    public fun toBytes(): ByteArray {
        val bytes = ByteArray(46)
        val mb = magic.toLeBytes()
        mb.copyInto(bytes, 0)
        bytes[4] = (versionMadeBy.toInt() and 0xFF).toByte()
        bytes[5] = ((versionMadeBy.toInt() shr 8) and 0xFF).toByte()
        bytes[6] = (versionToExtract.toInt() and 0xFF).toByte()
        bytes[7] = ((versionToExtract.toInt() shr 8) and 0xFF).toByte()
        bytes[8] = (flags.toInt() and 0xFF).toByte()
        bytes[9] = ((flags.toInt() shr 8) and 0xFF).toByte()
        bytes[10] = (compressionMethod.toInt() and 0xFF).toByte()
        bytes[11] = ((compressionMethod.toInt() shr 8) and 0xFF).toByte()
        bytes[12] = (lastModTime.toInt() and 0xFF).toByte()
        bytes[13] = ((lastModTime.toInt() shr 8) and 0xFF).toByte()
        bytes[14] = (lastModDate.toInt() and 0xFF).toByte()
        bytes[15] = ((lastModDate.toInt() shr 8) and 0xFF).toByte()
        bytes[16] = (crc32.toInt() and 0xFF).toByte()
        bytes[17] = ((crc32.toInt() shr 8) and 0xFF).toByte()
        bytes[18] = ((crc32.toInt() shr 16) and 0xFF).toByte()
        bytes[19] = ((crc32.toInt() shr 24) and 0xFF).toByte()
        bytes[20] = (compressedSize.toInt() and 0xFF).toByte()
        bytes[21] = ((compressedSize.toInt() shr 8) and 0xFF).toByte()
        bytes[22] = ((compressedSize.toInt() shr 16) and 0xFF).toByte()
        bytes[23] = ((compressedSize.toInt() shr 24) and 0xFF).toByte()
        bytes[24] = (uncompressedSize.toInt() and 0xFF).toByte()
        bytes[25] = ((uncompressedSize.toInt() shr 8) and 0xFF).toByte()
        bytes[26] = ((uncompressedSize.toInt() shr 16) and 0xFF).toByte()
        bytes[27] = ((uncompressedSize.toInt() shr 24) and 0xFF).toByte()
        bytes[28] = (fileNameLength.toInt() and 0xFF).toByte()
        bytes[29] = ((fileNameLength.toInt() shr 8) and 0xFF).toByte()
        bytes[30] = (extraFieldLength.toInt() and 0xFF).toByte()
        bytes[31] = ((extraFieldLength.toInt() shr 8) and 0xFF).toByte()
        bytes[32] = (fileCommentLength.toInt() and 0xFF).toByte()
        bytes[33] = ((fileCommentLength.toInt() shr 8) and 0xFF).toByte()
        bytes[34] = (diskNumber.toInt() and 0xFF).toByte()
        bytes[35] = ((diskNumber.toInt() shr 8) and 0xFF).toByte()
        bytes[36] = (internalFileAttributes.toInt() and 0xFF).toByte()
        bytes[37] = ((internalFileAttributes.toInt() shr 8) and 0xFF).toByte()
        bytes[38] = (externalFileAttributes.toInt() and 0xFF).toByte()
        bytes[39] = ((externalFileAttributes.toInt() shr 8) and 0xFF).toByte()
        bytes[40] = ((externalFileAttributes.toInt() shr 16) and 0xFF).toByte()
        bytes[41] = ((externalFileAttributes.toInt() shr 24) and 0xFF).toByte()
        bytes[42] = (offset.toInt() and 0xFF).toByte()
        bytes[43] = ((offset.toInt() shr 8) and 0xFF).toByte()
        bytes[44] = ((offset.toInt() shr 16) and 0xFF).toByte()
        bytes[45] = ((offset.toInt() shr 24) and 0xFF).toByte()
        return bytes
    }

    public companion object {
        public val MAGIC: Magic = Magic.CENTRAL_DIRECTORY_HEADER_SIGNATURE

        public fun parse(bytes: ByteArray, offset: Int = 0): ZipCentralEntryBlock {
            if (bytes.size - offset < 46) {
                throw ZipError.InvalidArchive("Unexpected EOF reading ZipCentralEntryBlock")
            }
            val magic = Magic.fromLeBytes(bytes, offset)
            if (magic != MAGIC) {
                throw ZipError.InvalidArchive("Invalid Central Directory header")
            }
            val versionMadeBy = ((bytes[offset + 4].toInt() and 0xFF) or ((bytes[offset + 5].toInt() and 0xFF) shl 8)).toUShort()
            val versionToExtract = ((bytes[offset + 6].toInt() and 0xFF) or ((bytes[offset + 7].toInt() and 0xFF) shl 8)).toUShort()
            val flags = ((bytes[offset + 8].toInt() and 0xFF) or ((bytes[offset + 9].toInt() and 0xFF) shl 8)).toUShort()
            val compressionMethod = ((bytes[offset + 10].toInt() and 0xFF) or ((bytes[offset + 11].toInt() and 0xFF) shl 8)).toUShort()
            val lastModTime = ((bytes[offset + 12].toInt() and 0xFF) or ((bytes[offset + 13].toInt() and 0xFF) shl 8)).toUShort()
            val lastModDate = ((bytes[offset + 14].toInt() and 0xFF) or ((bytes[offset + 15].toInt() and 0xFF) shl 8)).toUShort()
            val crc32 = (
                (bytes[offset + 16].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 17].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 18].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 19].toInt() and 0xFF).toUInt() shl 24)
            )
            val compressedSize = (
                (bytes[offset + 20].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 21].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 22].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 23].toInt() and 0xFF).toUInt() shl 24)
            )
            val uncompressedSize = (
                (bytes[offset + 24].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 25].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 26].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 27].toInt() and 0xFF).toUInt() shl 24)
            )
            val fileNameLength = ((bytes[offset + 28].toInt() and 0xFF) or ((bytes[offset + 29].toInt() and 0xFF) shl 8)).toUShort()
            val extraFieldLength = ((bytes[offset + 30].toInt() and 0xFF) or ((bytes[offset + 31].toInt() and 0xFF) shl 8)).toUShort()
            val fileCommentLength = ((bytes[offset + 32].toInt() and 0xFF) or ((bytes[offset + 33].toInt() and 0xFF) shl 8)).toUShort()
            val diskNumber = ((bytes[offset + 34].toInt() and 0xFF) or ((bytes[offset + 35].toInt() and 0xFF) shl 8)).toUShort()
            val internalFileAttributes = ((bytes[offset + 36].toInt() and 0xFF) or ((bytes[offset + 37].toInt() and 0xFF) shl 8)).toUShort()
            val externalFileAttributes = (
                (bytes[offset + 38].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 39].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 40].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 41].toInt() and 0xFF).toUInt() shl 24)
            )
            val off = (
                (bytes[offset + 42].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 43].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 44].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 45].toInt() and 0xFF).toUInt() shl 24)
            )
            return ZipCentralEntryBlock(
                magic = magic,
                versionMadeBy = versionMadeBy,
                versionToExtract = versionToExtract,
                flags = flags,
                compressionMethod = compressionMethod,
                lastModTime = lastModTime,
                lastModDate = lastModDate,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                fileNameLength = fileNameLength,
                extraFieldLength = extraFieldLength,
                fileCommentLength = fileCommentLength,
                diskNumber = diskNumber,
                internalFileAttributes = internalFileAttributes,
                externalFileAttributes = externalFileAttributes,
                offset = off,
            )
        }
    }
}

/**
 * Local File Header block structure.
 */
public data class ZipLocalEntryBlock(
    public val magic: Magic = Magic.LOCAL_FILE_HEADER_SIGNATURE,
    public val versionMadeBy: UShort = 0u,
    public val flags: UShort = 0u,
    public val compressionMethod: UShort = 0u,
    public val lastModTime: UShort = 0u,
    public val lastModDate: UShort = 0u,
    public val crc32: UInt = 0u,
    public val compressedSize: UInt = 0u,
    public val uncompressedSize: UInt = 0u,
    public val fileNameLength: UShort = 0u,
    public val extraFieldLength: UShort = 0u,
) {
    public fun toBytes(): ByteArray {
        val bytes = ByteArray(30)
        val mb = magic.toLeBytes()
        mb.copyInto(bytes, 0)
        bytes[4] = (versionMadeBy.toInt() and 0xFF).toByte()
        bytes[5] = ((versionMadeBy.toInt() shr 8) and 0xFF).toByte()
        bytes[6] = (flags.toInt() and 0xFF).toByte()
        bytes[7] = ((flags.toInt() shr 8) and 0xFF).toByte()
        bytes[8] = (compressionMethod.toInt() and 0xFF).toByte()
        bytes[9] = ((compressionMethod.toInt() shr 8) and 0xFF).toByte()
        bytes[10] = (lastModTime.toInt() and 0xFF).toByte()
        bytes[11] = ((lastModTime.toInt() shr 8) and 0xFF).toByte()
        bytes[12] = (lastModDate.toInt() and 0xFF).toByte()
        bytes[13] = ((lastModDate.toInt() shr 8) and 0xFF).toByte()
        bytes[14] = (crc32.toInt() and 0xFF).toByte()
        bytes[15] = ((crc32.toInt() shr 8) and 0xFF).toByte()
        bytes[16] = ((crc32.toInt() shr 16) and 0xFF).toByte()
        bytes[17] = ((crc32.toInt() shr 24) and 0xFF).toByte()
        bytes[20] = (compressedSize.toInt() and 0xFF).toByte()
        bytes[21] = ((compressedSize.toInt() shr 8) and 0xFF).toByte()
        bytes[22] = ((compressedSize.toInt() shr 16) and 0xFF).toByte()
        bytes[23] = ((compressedSize.toInt() shr 24) and 0xFF).toByte()
        bytes[24] = (uncompressedSize.toInt() and 0xFF).toByte()
        bytes[25] = ((uncompressedSize.toInt() shr 8) and 0xFF).toByte()
        bytes[26] = ((uncompressedSize.toInt() shr 16) and 0xFF).toByte()
        bytes[27] = ((uncompressedSize.toInt() shr 24) and 0xFF).toByte()
        bytes[28] = (fileNameLength.toInt() and 0xFF).toByte()
        bytes[29] = ((fileNameLength.toInt() shr 8) and 0xFF).toByte()
        return bytes
    }

    public companion object {
        public val MAGIC: Magic = Magic.LOCAL_FILE_HEADER_SIGNATURE

        public fun parse(bytes: ByteArray, offset: Int = 0): ZipLocalEntryBlock {
            if (bytes.size - offset < 30) {
                throw ZipError.InvalidArchive("Unexpected EOF reading ZipLocalEntryBlock")
            }
            val magic = Magic.fromLeBytes(bytes, offset)
            if (magic != MAGIC) {
                throw ZipError.InvalidArchive("Invalid local file header")
            }
            val versionMadeBy = ((bytes[offset + 4].toInt() and 0xFF) or ((bytes[offset + 5].toInt() and 0xFF) shl 8)).toUShort()
            val flags = ((bytes[offset + 6].toInt() and 0xFF) or ((bytes[offset + 7].toInt() and 0xFF) shl 8)).toUShort()
            val compressionMethod = ((bytes[offset + 8].toInt() and 0xFF) or ((bytes[offset + 9].toInt() and 0xFF) shl 8)).toUShort()
            val lastModTime = ((bytes[offset + 10].toInt() and 0xFF) or ((bytes[offset + 11].toInt() and 0xFF) shl 8)).toUShort()
            val lastModDate = ((bytes[offset + 12].toInt() and 0xFF) or ((bytes[offset + 13].toInt() and 0xFF) shl 8)).toUShort()
            val crc32 = (
                (bytes[offset + 14].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 15].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 16].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 17].toInt() and 0xFF).toUInt() shl 24)
            )
            val compressedSize = (
                (bytes[offset + 18].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 19].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 20].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 21].toInt() and 0xFF).toUInt() shl 24)
            )
            val uncompressedSize = (
                (bytes[offset + 22].toInt() and 0xFF).toUInt() or
                    ((bytes[offset + 23].toInt() and 0xFF).toUInt() shl 8) or
                    ((bytes[offset + 24].toInt() and 0xFF).toUInt() shl 16) or
                    ((bytes[offset + 25].toInt() and 0xFF).toUInt() shl 24)
            )
            val fileNameLength = ((bytes[offset + 26].toInt() and 0xFF) or ((bytes[offset + 27].toInt() and 0xFF) shl 8)).toUShort()
            val extraFieldLength = ((bytes[offset + 28].toInt() and 0xFF) or ((bytes[offset + 29].toInt() and 0xFF) shl 8)).toUShort()
            return ZipLocalEntryBlock(
                magic = magic,
                versionMadeBy = versionMadeBy,
                flags = flags,
                compressionMethod = compressionMethod,
                lastModTime = lastModTime,
                lastModDate = lastModDate,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                fileNameLength = fileNameLength,
                extraFieldLength = extraFieldLength,
            )
        }
    }
}

/**
 * Zip64 Extra Field structure.
 */
public data class Zip64ExtraFieldBlock(
    public val magic: ExtraFieldMagic = ExtraFieldMagic.ZIP64_EXTRA_FIELD_TAG,
    public val size: UShort,
    public val uncompressedSize: ULong? = null,
    public val compressedSize: ULong? = null,
    public val headerStart: ULong? = null,
) {
    public fun fullSize(): Int = size.toInt() + 4

    public fun serialize(): ByteArray {
        val result = ByteArray(fullSize())
        val mb = magic.toLeBytes()
        mb.copyInto(result, 0)
        result[2] = (size.toInt() and 0xFF).toByte()
        result[3] = ((size.toInt() shr 8) and 0xFF).toByte()
        var offset = 4
        uncompressedSize?.let {
            for (i in 0 until 8) {
                result[offset + i] = ((it shr (i * 8)) and 0xFFuL).toByte()
            }
            offset += 8
        }
        compressedSize?.let {
            for (i in 0 until 8) {
                result[offset + i] = ((it shr (i * 8)) and 0xFFuL).toByte()
            }
            offset += 8
        }
        headerStart?.let {
            for (i in 0 until 8) {
                result[offset + i] = ((it shr (i * 8)) and 0xFFuL).toByte()
            }
            offset += 8
        }
        return result
    }

    public companion object {
        public fun maybeNew(
            largeFile: Boolean,
            uncompressedSize: ULong,
            compressedSize: ULong,
            headerStart: ULong,
        ): Zip64ExtraFieldBlock? {
            var size: UShort = 0u
            val unc =
                if (uncompressedSize >= ZIP64_BYTES_THR || largeFile) {
                    size = (size + 8u).toUShort()
                    uncompressedSize
                } else {
                    null
                }
            val cmp =
                if (compressedSize >= ZIP64_BYTES_THR || largeFile) {
                    size = (size + 8u).toUShort()
                    compressedSize
                } else {
                    null
                }
            val hs =
                if (headerStart >= ZIP64_BYTES_THR) {
                    size = (size + 8u).toUShort()
                    headerStart
                } else {
                    null
                }
            if (size == 0u.toUShort()) return null
            return Zip64ExtraFieldBlock(
                magic = ExtraFieldMagic.ZIP64_EXTRA_FIELD_TAG,
                size = size,
                uncompressedSize = unc,
                compressedSize = cmp,
                headerStart = hs,
            )
        }
    }
}

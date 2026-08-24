// port-lint: source types.rs
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
    public var externalAttributes: UInt = 0u,
    public var largeFile: Boolean = false,
    public var aesMode: AesMode? = null,
    public var aesExtraDataStart: ULong = 0uL,
) {
    public fun isDir(): Boolean =
        fileName.endsWith('/') || fileName.endsWith('\\')

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

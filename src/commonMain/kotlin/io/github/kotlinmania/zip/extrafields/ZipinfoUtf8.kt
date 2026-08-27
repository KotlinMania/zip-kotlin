// port-lint: source zip/src/extra_fields/zipinfo_utf8.rs
package io.github.kotlinmania.zip.extrafields

import io.github.kotlinmania.zip.Crc32Hasher
import io.github.kotlinmania.zip.ZipError

/**
 * Info-ZIP Unicode Path Extra Field (0x7075) or Unicode Comment Extra Field (0x6375), as
 * specified in APPNOTE 4.6.8 and 4.6.9
 */
public data class UnicodeExtraField(
    public val crc32: UInt,
    public val content: ByteArray,
) {
    /**
     * Verifies the checksum and returns the content.
     */
    public fun unwrapValid(asciiField: ByteArray): Result<ByteArray> {
        val actualCrc = Crc32Hasher.calculate(asciiField)
        if (crc32 != actualCrc) {
            return Result.failure(
                ZipError.InvalidArchive("CRC32 checksum failed on Unicode extra field")
            )
        }
        return Result.success(content)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnicodeExtraField) return false
        return crc32 == other.crc32 && content.contentEquals(other.content)
    }

    override fun hashCode(): Int = 31 * crc32.hashCode() + content.contentHashCode()

    public companion object {
        public fun tryFromBytes(bytes: ByteArray, offset: Int, len: UShort): Result<UnicodeExtraField> {
            if (len.toInt() < 5) {
                return Result.failure(ZipError.InvalidArchive("Unicode extra field is too small"))
            }
            if (offset + len.toInt() > bytes.size) {
                return Result.failure(ZipError.InvalidArchive("unexpected end of data"))
            }
            // Skip 1 byte version at offset
            val crc32 = (bytes[offset + 1].toInt() and 0xFF).toUInt() or
                ((bytes[offset + 2].toInt() and 0xFF).toUInt() shl 8) or
                ((bytes[offset + 3].toInt() and 0xFF).toUInt() shl 16) or
                ((bytes[offset + 4].toInt() and 0xFF).toUInt() shl 24)

            val contentLen = len.toInt() - 5
            val content = bytes.copyOfRange(offset + 5, offset + 5 + contentLen)
            return Result.success(UnicodeExtraField(crc32, content))
        }
    }
}

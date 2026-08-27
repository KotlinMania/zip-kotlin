// port-lint: source zip/src/extra_fields/extended_timestamp.rs
package io.github.kotlinmania.zip.extrafields

import io.github.kotlinmania.zip.ZipError

/**
 * Extended timestamp, as described in https://libzip.org/specifications/extrafld.txt
 */
public data class ExtendedTimestamp(
    public val modTime: UInt? = null,
    public val acTime: UInt? = null,
    public val crTime: UInt? = null,
) {

    public companion object {
        /**
         * Creates an extended timestamp struct by reading the required bytes from the buffer.
         */
        public fun tryFromBytes(bytes: ByteArray, offset: Int, len: UShort): Result<ExtendedTimestamp> {
            if (offset >= bytes.size) {
                return Result.failure(ZipError.InvalidArchive("unexpected end of data"))
            }
            val flags = bytes[offset].toInt() and 0xFF
            val countOnes = countSetBits(flags)

            if (len.toInt() != 5 && len.toInt() != 1 + 4 * countOnes) {
                return Result.failure(
                    ZipError.UnsupportedArchive("flags and len don't match in extended timestamp field")
                )
            }

            if ((flags and 0b11111000) != 0) {
                return Result.failure(
                    ZipError.UnsupportedArchive("found unsupported timestamps in the extended timestamp header")
                )
            }

            var currentOffset = offset + 1
            val modTime = if ((flags and 0b00000001) != 0 || len.toInt() == 5) {
                val t = readU32Le(bytes, currentOffset)
                currentOffset += 4
                t
            } else {
                null
            }

            val acTime = if ((flags and 0b00000010) != 0 && len.toInt() > 5) {
                val t = readU32Le(bytes, currentOffset)
                currentOffset += 4
                t
            } else {
                null
            }

            val crTime = if ((flags and 0b00000100) != 0 && len.toInt() > 5) {
                val t = readU32Le(bytes, currentOffset)
                currentOffset += 4
                t
            } else {
                null
            }

            return Result.success(ExtendedTimestamp(modTime, acTime, crTime))
        }

        private fun countSetBits(v: Int): Int {
            var count = 0
            var n = v
            while (n > 0) {
                count += n and 1
                n = n ushr 1
            }
            return count
        }

        private fun readU32Le(bytes: ByteArray, offset: Int): UInt =
            (bytes[offset].toInt() and 0xFF).toUInt() or
                ((bytes[offset + 1].toInt() and 0xFF).toUInt() shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF).toUInt() shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF).toUInt() shl 24)
    }
}

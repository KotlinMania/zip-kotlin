// port-lint: source extra_fields/ntfs.rs
package io.github.kotlinmania.zip.extrafields

import io.github.kotlinmania.zip.ZipError

/**
 * The NTFS extra field as described in PKWARE's APPNOTE.TXT v6.3.9.
 *
 * This field stores Windows file times, which are 64-bit unsigned integer
 * values representing 100-nanosecond intervals elapsed since 1601-01-01 00:00:00 UTC.
 */
public data class Ntfs(
    public val mtime: ULong,
    public val atime: ULong,
    public val ctime: ULong,
) {

    public companion object {
        /**
         * Creates an NTFS extra field struct by reading the required bytes.
         */
        public fun tryFromBytes(bytes: ByteArray, offset: Int, len: UShort): Result<Ntfs> {
            if (len.toInt() != 32) {
                return Result.failure(
                    ZipError.UnsupportedArchive("NTFS extra field has an unsupported length")
                )
            }
            if (offset + 32 > bytes.size) {
                return Result.failure(ZipError.InvalidArchive("unexpected end of data"))
            }

            // Skip 4 bytes reserved
            val tag = readU16Le(bytes, offset + 4)
            if (tag.toInt() != 0x0001) {
                return Result.failure(
                    ZipError.UnsupportedArchive("NTFS extra field has an unsupported attribute tag")
                )
            }
            val size = readU16Le(bytes, offset + 6)
            if (size.toInt() != 24) {
                return Result.failure(
                    ZipError.UnsupportedArchive("NTFS extra field has an unsupported attribute size")
                )
            }

            val mtime = readU64Le(bytes, offset + 8)
            val atime = readU64Le(bytes, offset + 16)
            val ctime = readU64Le(bytes, offset + 24)
            return Result.success(Ntfs(mtime, atime, ctime))
        }

        private fun readU16Le(bytes: ByteArray, offset: Int): UShort =
            ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)).toUShort()

        private fun readU64Le(bytes: ByteArray, offset: Int): ULong {
            var res = 0uL
            for (i in 0 until 8) {
                res = res or ((bytes[offset + i].toLong() and 0xFFL).toULong() shl (i * 8))
            }
            return res
        }
    }
}

// port-lint: source crc32.rs
package io.github.kotlinmania.zip

/**
 * Standard IEEE 802.3 CRC32 calculator.
 */
public class Crc32Hasher {
    private var crc: UInt = 0xFFFFFFFFu

    public fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        for (i in offset until (offset + length)) {
            val byteVal = (bytes[i].toInt() and 0xFF).toUInt()
            val tableIndex = ((crc xor byteVal) and 0xFFu).toInt()
            crc = (crc shr 8) xor CRC_TABLE[tableIndex]
        }
    }

    public fun update(byte: Byte) {
        val byteVal = (byte.toInt() and 0xFF).toUInt()
        val tableIndex = ((crc xor byteVal) and 0xFFu).toInt()
        crc = (crc shr 8) xor CRC_TABLE[tableIndex]
    }

    public fun finalize(): UInt = crc xor 0xFFFFFFFFu

    public fun reset() {
        crc = 0xFFFFFFFFu
    }

    public companion object {
        private val CRC_TABLE: UIntArray =
            UIntArray(256) { i ->
                var c = i.toUInt()
                for (j in 0 until 8) {
                    c =
                        if ((c and 1u) != 0u) {
                            0xEDB88320u xor (c shr 1)
                        } else {
                            c shr 1
                        }
                }
                c
            }

        /**
         * Calculates the CRC32 checksum for the given byte array.
         */
        public fun calculate(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): UInt {
            val hasher = Crc32Hasher()
            hasher.update(bytes, offset, length)
            return hasher.finalize()
        }
    }
}

/**
 * Reader helper that validates CRC32 against expected checksum.
 */
public class Crc32Reader(
    private val data: ByteArray,
    public val checksum: UInt,
    public val ae2Encrypted: Boolean = false,
) {
    private var position: Int = 0
    private val hasher: Crc32Hasher = Crc32Hasher()
    public val isEnabled: Boolean = !ae2Encrypted

    public fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
        if (position >= data.size) {
            if (length > 0 && isEnabled && !checkMatches()) {
                throw ZipError.InvalidArchive("Invalid checksum")
            }
            return 0
        }
        val count = minOf(length, data.size - position)
        data.copyInto(buffer, destinationOffset = offset, startIndex = position, endIndex = position + count)
        if (isEnabled) {
            hasher.update(buffer, offset, count)
        }
        position += count
        if (position >= data.size && isEnabled && !checkMatches()) {
            throw ZipError.InvalidArchive("Invalid checksum")
        }
        return count
    }

    public fun readAll(): ByteArray {
        val remaining = data.size - position
        val result = ByteArray(remaining)
        read(result)
        return result
    }

    public fun checkMatches(): Boolean =
        checksum == hasher.finalize()
}

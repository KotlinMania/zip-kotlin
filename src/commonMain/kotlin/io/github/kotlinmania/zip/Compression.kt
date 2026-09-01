// port-lint: source compression.rs
package io.github.kotlinmania.zip

/**
 * Identifies the storage format used to compress a file within a ZIP archive.
 */
public sealed class CompressionMethod(
    public val id: UShort,
) {
    /** Store the file as is without compression */
    public object Stored : CompressionMethod(0u)

    /** Compress the file using Deflate */
    public object Deflated : CompressionMethod(8u)

    /** Compress the file using Deflate64 */
    public object Deflate64 : CompressionMethod(9u)

    /** Compress the file using Bzip2 */
    public object Bzip2 : CompressionMethod(12u)

    /** Compress the file using LZMA */
    public object Lzma : CompressionMethod(14u)

    /** Compress the file using Zstandard */
    public object Zstd : CompressionMethod(93u)

    /** Compress the file using XZ */
    public object Xz : CompressionMethod(95u)

    /** Encrypted using AES */
    public object Aes : CompressionMethod(99u)

    /** Unsupported compression method with its raw ID */
    public data class Unsupported(
        public val rawId: UShort,
    ) : CompressionMethod(rawId)

    override fun equals(other: Any?): Boolean =
        other is CompressionMethod && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        when (this) {
            Stored -> "Stored"
            Deflated -> "Deflated"
            Deflate64 -> "Deflate64"
            Bzip2 -> "Bzip2"
            Lzma -> "Lzma"
            Zstd -> "Zstd"
            Xz -> "Xz"
            Aes -> "Aes"
            is Unsupported -> "Unsupported($rawId)"
        }

    public fun serializeToU16(): UShort = id

    public fun toU16(): UShort = id

    public companion object {
        public val STORE: CompressionMethod = Stored
        public val SHRINK: CompressionMethod = Unsupported(1u)
        public val REDUCE_1: CompressionMethod = Unsupported(2u)
        public val REDUCE_2: CompressionMethod = Unsupported(3u)
        public val REDUCE_3: CompressionMethod = Unsupported(4u)
        public val REDUCE_4: CompressionMethod = Unsupported(5u)
        public val IMPLODE: CompressionMethod = Unsupported(6u)
        public val DEFLATE: CompressionMethod = Deflated
        public val DEFLATE64: CompressionMethod = Deflate64
        public val PKWARE_IMPLODE: CompressionMethod = Unsupported(10u)
        public val BZIP2: CompressionMethod = Bzip2
        public val LZMA: CompressionMethod = Lzma
        public val IBM_ZOS_CMPSC: CompressionMethod = Unsupported(16u)
        public val IBM_TERSE: CompressionMethod = Unsupported(18u)
        public val ZSTD_DEPRECATED: CompressionMethod = Unsupported(20u)
        public val ZSTD: CompressionMethod = Zstd
        public val MP3: CompressionMethod = Unsupported(94u)
        public val XZ: CompressionMethod = Xz
        public val JPEG: CompressionMethod = Unsupported(96u)
        public val WAVPACK: CompressionMethod = Unsupported(97u)
        public val PPMD: CompressionMethod = Unsupported(98u)
        public val AES: CompressionMethod = Aes

        public fun default(): CompressionMethod = Deflated

        /**
         * Parses a [CompressionMethod] from a raw 16-bit unsigned integer.
         */
        public fun parseFromUShort(valId: UShort): CompressionMethod =
            when (valId.toInt()) {
                0 -> Stored
                8 -> Deflated
                9 -> Deflate64
                12 -> Bzip2
                14 -> Lzma
                93 -> Zstd
                95 -> Xz
                99 -> Aes
                else -> Unsupported(valId)
            }

        public fun parseFromU16(valId: UShort): CompressionMethod = parseFromUShort(valId)

        public fun fromU16(valId: UShort): CompressionMethod = parseFromUShort(valId)
    }
}

/**
 * List of compression methods supported by this library.
 */
public val SUPPORTED_COMPRESSION_METHODS: List<CompressionMethod> =
    listOf(
        CompressionMethod.Stored,
        CompressionMethod.Deflated,
        CompressionMethod.Deflate64,
        CompressionMethod.Bzip2,
        CompressionMethod.Lzma,
        CompressionMethod.Zstd,
        CompressionMethod.Xz,
        CompressionMethod.Aes,
    )

/**
 * Decompressor wrapping a compressed byte stream.
 */
public class Decompressor(
    private val data: ByteArray,
    public val compressionMethod: CompressionMethod,
) {
    private var position: Int = 0

    public fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
        if (position >= data.size) return 0
        val count = minOf(length, data.size - position)
        data.copyInto(buffer, destinationOffset = offset, startIndex = position, endIndex = position + count)
        position += count
        return count
    }

    public fun intoInner(): ByteArray = data

    public companion object {
        public fun new(data: ByteArray, compressionMethod: CompressionMethod): Result<Decompressor> =
            Result.success(Decompressor(data, compressionMethod))
    }
}

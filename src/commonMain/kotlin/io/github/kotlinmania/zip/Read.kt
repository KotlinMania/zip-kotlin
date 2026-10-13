// port-lint: source read.rs
package io.github.kotlinmania.zip

import io.github.kotlinmania.indexmap.IndexMap
import io.github.kotlinmania.zip.read.ArchiveOffset
import io.github.kotlinmania.zip.read.Config

/**
 * Sequential stream source for ZIP decoding.
 */
public interface ZipStreamSource {
    public fun read(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int

    public fun readExact(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)
}

/**
 * A [ZipStreamSource] wrapper that allows pushing back bytes to be read first.
 */
public class PushbackZipStreamSource(
    private val underlying: ZipStreamSource,
) : ZipStreamSource {
    private var pushbackBuf: ByteArray? = null
    private var pushbackPos: Int = 0

    public fun pushback(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        if (length <= 0) return
        val existingUnread = (pushbackBuf?.size ?: 0) - pushbackPos
        val newBuf = ByteArray(existingUnread + length)
        bytes.copyInto(newBuf, 0, offset, offset + length)
        pushbackBuf?.copyInto(newBuf, length, pushbackPos, pushbackPos + existingUnread)
        pushbackBuf = newBuf
        pushbackPos = 0
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var totalRead = 0
        var currentOffset = offset
        var remaining = length
        val pBuf = pushbackBuf
        if (pBuf != null && pushbackPos < pBuf.size) {
            val available = minOf(remaining, pBuf.size - pushbackPos)
            pBuf.copyInto(bytes, currentOffset, pushbackPos, pushbackPos + available)
            pushbackPos += available
            currentOffset += available
            remaining -= available
            totalRead += available
            if (pushbackPos >= pBuf.size) {
                pushbackBuf = null
                pushbackPos = 0
            }
        }
        if (remaining > 0) {
            val n = underlying.read(bytes, currentOffset, remaining)
            if (n > 0) {
                totalRead += n
            } else if (totalRead == 0) {
                return n
            }
        }
        return totalRead
    }

    override fun readExact(bytes: ByteArray, offset: Int, length: Int) {
        var remaining = length
        var currentOffset = offset
        val pBuf = pushbackBuf
        if (pBuf != null && pushbackPos < pBuf.size) {
            val available = minOf(remaining, pBuf.size - pushbackPos)
            pBuf.copyInto(bytes, currentOffset, pushbackPos, pushbackPos + available)
            pushbackPos += available
            currentOffset += available
            remaining -= available
            if (pushbackPos >= pBuf.size) {
                pushbackBuf = null
                pushbackPos = 0
            }
        }
        if (remaining > 0) {
            underlying.readExact(bytes, currentOffset, remaining)
        }
    }
}

/**
 * Seekable source for reading ZIP archives.
 */
public interface ZipSource : ZipStreamSource {
    public fun size(): ULong

    public fun position(): ULong

    public fun seek(position: ULong)

    public fun clone(): ZipSource
}

/**
 * In-memory byte array implementation of [ZipSource].
 */
public class ByteArrayZipSource(
    private val buffer: ByteArray,
) : ZipSource {
    private var pos: Int = 0

    override fun size(): ULong = buffer.size.toULong()

    override fun position(): ULong = pos.toULong()

    override fun seek(position: ULong) {
        val target = position.toLong()
        if (target < 0L || target > buffer.size.toLong()) {
            throw ZipError.InvalidArchive("Seek position out of bounds: $position")
        }
        pos = target.toInt()
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        if (pos >= buffer.size) return -1
        val available = minOf(length, buffer.size - pos)
        buffer.copyInto(bytes, offset, pos, pos + available)
        pos += available
        return available
    }

    override fun readExact(bytes: ByteArray, offset: Int, length: Int) {
        if (length == 0) return
        val available = buffer.size - pos
        if (available < length) {
            throw ZipError.Io("Unexpected EOF")
        }
        buffer.copyInto(bytes, offset, pos, pos + length)
        pos += length
    }

    public fun toByteArray(): ByteArray = buffer.copyOf()

    override fun clone(): ByteArrayZipSource {
        val copy = ByteArrayZipSource(buffer)
        copy.seek(position())
        return copy
    }
}

/**
 * Immutable shared metadata extracted from a ZIP archive.
 */
internal class Shared(
    public val files: IndexMap<String, ZipFileData>,
    public val offset: ULong,
    public val dirStart: ULong,
    public val config: Config,
    public val comment: ByteArray,
    public val zip64Comment: ByteArray? = null,
)

/**
 * Builder for constructing [Shared] archive metadata.
 */
internal class SharedBuilder(
    public val files: MutableList<ZipFileData>,
    public val offset: ULong,
    public val dirStart: ULong,
    public val config: Config,
) {
    public fun build(comment: ByteArray, zip64Comment: ByteArray?): Shared {
        val indexMap = IndexMap<String, ZipFileData>()
        for (file in files) {
            indexMap.insert(file.fileName, file)
        }
        return Shared(
            files = indexMap,
            offset = offset,
            dirStart = dirStart,
            config = config,
            comment = comment,
            zip64Comment = zip64Comment,
        )
    }
}

/**
 * Information extracted from the end of central directory record.
 */
internal class CentralDirectoryInfo(
    public val archiveOffset: ULong,
    public val directoryStart: ULong,
    public val numberOfFiles: Int,
    public val diskNumber: UInt,
    public val diskWithCentralDirectory: UInt,
) {
    public companion object {
        public fun fromEndInfo(value: CentralDirectoryEndInfo): CentralDirectoryInfo {
            val eocd64 = value.eocd64
            val relativeCdOffset: ULong
            val numberOfFiles: Int
            val diskNumber: UInt
            val diskWithCentralDirectory: UInt

            if (eocd64 != null) {
                val data = eocd64.data
                if (data.numberOfFilesOnThisDisk > data.numberOfFiles) {
                    throw ZipError.InvalidArchive(
                        "ZIP64 footer indicates more files on this disk than in the whole archive",
                    )
                }
                if (data.versionNeededToExtract > data.versionMadeBy) {
                    throw ZipError.InvalidArchive(
                        "ZIP64 footer indicates a new version is needed to extract this archive than the version that wrote it",
                    )
                }
                relativeCdOffset = data.centralDirectoryOffset
                numberOfFiles = data.numberOfFiles.toInt()
                diskNumber = data.diskNumber
                diskWithCentralDirectory = data.diskWithCentralDirectory
            } else {
                val data = value.eocd.data
                relativeCdOffset = data.centralDirectoryOffset.toULong()
                numberOfFiles = data.numberOfFilesOnThisDisk.toInt()
                diskNumber = data.diskNumber.toUInt()
                diskWithCentralDirectory = data.diskWithCentralDirectory.toUInt()
            }

            val directoryStart = relativeCdOffset + value.archiveOffset

            return CentralDirectoryInfo(
                archiveOffset = value.archiveOffset,
                directoryStart = directoryStart,
                numberOfFiles = numberOfFiles,
                diskNumber = diskNumber,
                diskWithCentralDirectory = diskWithCentralDirectory,
            )
        }
    }
}

/**
 * ZIP archive reader supporting indexed access, extraction, and validation.
 */
public class ZipArchive internal constructor(
    public val source: ZipSource,
    internal val shared: Shared,
) {
    public fun decompressedSize(): ULong? {
        var total = 0uL
        for ((_, file) in shared.files.asEntries()) {
            if (file.usingDataDescriptor) return null
            total += file.uncompressedSize
        }
        return total
    }

    public fun len(): Int = shared.files.len()

    public fun isEmpty(): Boolean = shared.files.isEmpty()

    public fun centralDirectoryStart(): ULong = shared.dirStart

    public fun offset(): ULong = shared.offset

    public fun comment(): ByteArray = shared.comment

    public fun zip64Comment(): ByteArray? = shared.zip64Comment

    public fun fileNames(): List<String> = shared.files.keys()

    public fun indexForName(name: String): Int? = shared.files.getIndexOf(name)

    public fun nameForIndex(index: Int): String? = shared.files.getIndex(index)?.first

    public fun byName(name: String): Result<ZipFile> {
        val index = indexForName(name) ?: return Result.failure(ZipError.FileNotFound)
        return byIndex(index)
    }

    public fun byIndex(index: Int): Result<ZipFile> {
        val pair = shared.files.getIndex(index) ?: return Result.failure(ZipError.FileNotFound)
        val data = pair.second
        val compBytes =
            try {
                findContent(data, source)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        return Result.success(ZipFile(data, compBytes))
    }

    public fun byIndexRaw(index: Int): Result<ZipFile> {
        val pair = shared.files.getIndex(index) ?: return Result.failure(ZipError.FileNotFound)
        val data = pair.second
        val compBytes =
            try {
                findContent(data, source)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        return Result.success(ZipFile(data, compBytes))
    }

    public fun intoInner(): ZipSource = source

    public fun clone(): ZipArchive = ZipArchive(source.clone(), shared)

    public fun mergeContents(sink: ZipSink): Result<IndexMap<String, ZipFileData>> {
        if (shared.files.isEmpty()) {
            return Result.success(IndexMap())
        }
        val newFiles = IndexMap<String, ZipFileData>()
        val firstNewFileHeaderStart = sink.position()
        for ((name, f) in shared.files.asEntries()) {
            val copy =
                f.copy(
                    headerStart = f.headerStart + firstNewFileHeaderStart,
                    centralHeaderStart = 0uL,
                    dataStartOffset = f.dataStartOffset?.let { it + firstNewFileHeaderStart },
                )
            newFiles.insert(name, copy)
        }
        source.seek(0uL)
        val lengthToRead = shared.dirStart.toInt()
        val buf = ByteArray(lengthToRead)
        source.readExact(buf, 0, lengthToRead)
        sink.write(buf)
        return Result.success(newFiles)
    }

    public companion object {
        public fun new(source: ZipSource): Result<ZipArchive> =
            withConfig(Config(), source)

        public fun new(bytes: ByteArray): Result<ZipArchive> =
            new(ByteArrayZipSource(bytes))

        public fun withConfig(config: Config, source: ZipSource): Result<ZipArchive> =
            try {
                val shared = getMetadata(config, source)
                Result.success(ZipArchive(source, shared))
            } catch (e: Exception) {
                Result.failure(e)
            }

        public fun fromFinalizedWriter(
            files: IndexMap<String, ZipFileData>,
            comment: ByteArray,
            zip64Comment: ByteArray?,
            source: ZipSource,
            centralStart: ULong,
        ): Result<ZipArchive> {
            val initialOffset = files.first()?.second?.headerStart ?: centralStart
            val shared =
                Shared(
                    files = files,
                    offset = initialOffset,
                    dirStart = centralStart,
                    config = Config(archiveOffset = ArchiveOffset.Known(initialOffset)),
                    comment = comment,
                    zip64Comment = zip64Comment,
                )
            return Result.success(ZipArchive(source, shared))
        }
    }
}

/**
 * Represents a single file entry inside a ZIP archive.
 */
public class ZipFile(
    public val data: ZipFileData,
    private val rawContent: ByteArray,
) {
    public fun name(): String = data.fileName

    public fun nameRaw(): ByteArray = data.fileNameRaw

    public fun enclosedName(): String? = data.enclosedName()

    public fun isDir(): Boolean = data.isDir()

    public fun isSymlink(): Boolean =
        ((data.externalAttributes shr 16) and Ffi.S_IFLNK) == Ffi.S_IFLNK

    public fun isFile(): Boolean = !isDir() && !isSymlink()

    public fun size(): ULong = data.uncompressedSize

    public fun compressedSize(): ULong = data.compressedSize

    public fun crc32(): UInt = data.crc32

    public fun comment(): String = data.fileComment

    public fun compression(): CompressionMethod = data.compressionMethod

    public fun encrypted(): Boolean = data.encrypted

    public fun lastModified(): DateTime? = data.lastModifiedTime

    public fun unixMode(): UInt? = data.unixMode()

    public fun headerStart(): ULong = data.headerStart

    public fun centralHeaderStart(): ULong = data.centralHeaderStart

    public fun dataStart(): ULong = data.dataStart()

    public fun options(): SimpleFileOptions {
        val opts = SimpleFileOptions()
        opts.compressionMethod(data.compressionMethod)
        opts.largeFile(data.largeFile)
        data.lastModifiedTime?.let { opts.lastModifiedTime(it) }
        data.unixMode()?.let { opts.unixPermissions(it) }
        return opts
    }

    public fun read(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        val available = minOf(length, rawContent.size)
        rawContent.copyInto(bytes, offset, 0, available)
        return available
    }

    public fun readExact(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        if (length > rawContent.size) {
            throw ZipError.Io("Unexpected EOF")
        }
        rawContent.copyInto(bytes, offset, 0, length)
    }

    public fun readToEnd(): ByteArray = rawContent.copyOf()

    public fun readToString(): String = rawContent.decodeToString()
}

internal fun findDataStart(data: ZipFileData, source: ZipSource): ULong {
    val cached = data.dataStartOffset
    if (cached != null) return cached
    source.seek(data.headerStart)
    val headerBytes = ByteArray(30)
    source.readExact(headerBytes)
    val block = ZipLocalEntryBlock.parse(headerBytes, 0)
    val variableFieldsLen = block.fileNameLength.toULong() + block.extraFieldLength.toULong()
    val dataStart = data.headerStart + 30uL + variableFieldsLen
    data.dataStartOffset = dataStart
    return dataStart
}

internal fun findContent(data: ZipFileData, source: ZipSource): ByteArray {
    val dataStart = findDataStart(data, source)
    source.seek(dataStart)
    val compSize = data.compressedSize.toInt()
    val compBytes = ByteArray(compSize)
    source.readExact(compBytes)
    return compBytes
}

internal fun parseExtraField(file: ZipFileData) {
    val extra = file.extraField ?: return
    var pos = 0
    while (pos + 4 <= extra.size) {
        val kind = ((extra[pos].toInt() and 0xFF) or ((extra[pos + 1].toInt() and 0xFF) shl 8))
        val len = ((extra[pos + 2].toInt() and 0xFF) or ((extra[pos + 3].toInt() and 0xFF) shl 8))
        pos += 4
        if (pos + len > extra.size) break
        when (kind) {
            0x0001 -> {
                file.largeFile = true
                var readPos = pos
                val endPos = pos + len
                if (file.uncompressedSize == 0xFFFFFFFFuL && readPos + 8 <= endPos) {
                    var uSize = 0uL
                    for (i in 0 until 8) {
                        uSize = uSize or ((extra[readPos + i].toLong() and 0xFFL).toULong() shl (i * 8))
                    }
                    file.uncompressedSize = uSize
                    readPos += 8
                }
                if (file.compressedSize == 0xFFFFFFFFuL && readPos + 8 <= endPos) {
                    var cSize = 0uL
                    for (i in 0 until 8) {
                        cSize = cSize or ((extra[readPos + i].toLong() and 0xFFL).toULong() shl (i * 8))
                    }
                    file.compressedSize = cSize
                    readPos += 8
                }
                if (file.headerStart == 0xFFFFFFFFuL && readPos + 8 <= endPos) {
                    var hStart = 0uL
                    for (i in 0 until 8) {
                        hStart = hStart or ((extra[readPos + i].toLong() and 0xFFL).toULong() shl (i * 8))
                    }
                    file.headerStart = hStart
                    readPos += 8
                }
            }

            0x7075 -> {
                if (len >= 5) {
                    val version = extra[pos].toInt() and 0xFF
                    if (version == 1) {
                        val crc = (
                            (extra[pos + 1].toInt() and 0xFF).toUInt() or
                                ((extra[pos + 2].toInt() and 0xFF).toUInt() shl 8) or
                                ((extra[pos + 3].toInt() and 0xFF).toUInt() shl 16) or
                                ((extra[pos + 4].toInt() and 0xFF).toUInt() shl 24)
                        )
                        val expectedCrc = Crc32Hasher.calculate(file.fileNameRaw)
                        if (crc == expectedCrc) {
                            val utf8Name = extra.copyOfRange(pos + 5, pos + len)
                            file.fileName = utf8Name.decodeToString()
                            file.fileNameRaw = utf8Name
                            file.isUtf8 = true
                        }
                    }
                }
            }

            0x6375 -> {
                if (len >= 5) {
                    val version = extra[pos].toInt() and 0xFF
                    if (version == 1) {
                        val crc = (
                            (extra[pos + 1].toInt() and 0xFF).toUInt() or
                                ((extra[pos + 2].toInt() and 0xFF).toUInt() shl 8) or
                                ((extra[pos + 3].toInt() and 0xFF).toUInt() shl 16) or
                                ((extra[pos + 4].toInt() and 0xFF).toUInt() shl 24)
                        )
                        val commentRaw = file.fileComment.encodeToByteArray()
                        val expectedCrc = Crc32Hasher.calculate(commentRaw)
                        if (crc == expectedCrc) {
                            val utf8Comment = extra.copyOfRange(pos + 5, pos + len)
                            file.fileComment = utf8Comment.decodeToString()
                        }
                    }
                }
            }

            0x9901 -> {
                if (len == 7) {
                    val vendorVersion = ((extra[pos].toInt() and 0xFF) or ((extra[pos + 1].toInt() and 0xFF) shl 8))
                    val vendorId = ((extra[pos + 2].toInt() and 0xFF) or ((extra[pos + 3].toInt() and 0xFF) shl 8))
                    val aesModeByte = extra[pos + 4].toInt() and 0xFF
                    val actualMethod = ((extra[pos + 5].toInt() and 0xFF) or ((extra[pos + 6].toInt() and 0xFF) shl 8)).toUShort()
                    if (vendorId == 0x4541) {
                        file.aesVendorVersion =
                            when (vendorVersion) {
                                1 -> AesVendorVersion.Ae1
                                2 -> AesVendorVersion.Ae2
                                else -> null
                            }
                        file.aesMode =
                            when (aesModeByte) {
                                1 -> AesMode.Aes128
                                2 -> AesMode.Aes192
                                3 -> AesMode.Aes256
                                else -> null
                            }
                        file.compressionMethod = CompressionMethod.parseFromUShort(actualMethod)
                    }
                }
            }
        }
        pos += len
    }
}

internal fun centralHeaderToZipFileInner(
    source: ZipStreamSource,
    archiveOffset: ULong,
    centralHeaderStart: ULong,
    block: ZipCentralEntryBlock,
): ZipFileData {
    val encrypted = (block.flags.toInt() and 1) == 1
    val isUtf8 = (block.flags.toInt() and (1 shl 11)) != 0
    val usingDataDescriptor = (block.flags.toInt() and (1 shl 3)) != 0

    val fileNameRaw = ByteArray(block.fileNameLength.toInt())
    source.readExact(fileNameRaw)
    val extraField = ByteArray(block.extraFieldLength.toInt())
    source.readExact(extraField)
    val fileCommentRaw = ByteArray(block.fileCommentLength.toInt())
    source.readExact(fileCommentRaw)

    val fileName =
        if (isUtf8) {
            fileNameRaw.decodeToString()
        } else {
            fileNameRaw.fromCp437()
        }
    val fileComment =
        if (isUtf8) {
            fileCommentRaw.decodeToString()
        } else {
            fileCommentRaw.fromCp437()
        }

    val system = System.fromCode((block.versionMadeBy.toInt() shr 8).toUByte())
    val result =
        ZipFileData(
            system = system,
            versionMadeBy = (block.versionMadeBy.toInt() and 0xFF).toUByte(),
            encrypted = encrypted,
            usingDataDescriptor = usingDataDescriptor,
            isUtf8 = isUtf8,
            compressionMethod = CompressionMethod.parseFromUShort(block.compressionMethod),
            compressionLevel = null,
            lastModifiedTime = DateTime.tryFromMsdos(block.lastModDate, block.lastModTime).getOrNull(),
            crc32 = block.crc32,
            compressedSize = block.compressedSize.toULong(),
            uncompressedSize = block.uncompressedSize.toULong(),
            fileName = fileName,
            fileNameRaw = fileNameRaw,
            extraField = extraField,
            centralExtraField = null,
            fileComment = fileComment,
            headerStart = block.offset.toULong(),
            extraDataStart = null,
            centralHeaderStart = centralHeaderStart,
            dataStartOffset = null,
            externalAttributes = block.externalFileAttributes,
            largeFile = false,
        )

    parseExtraField(result)
    result.headerStart = result.headerStart + archiveOffset
    return result
}

internal fun centralHeaderToZipFile(
    source: ZipSource,
    centralDirectory: CentralDirectoryInfo,
): ZipFileData {
    val centralHeaderStart = source.position()
    val blockBytes = ByteArray(46)
    source.readExact(blockBytes)
    val block = ZipCentralEntryBlock.parse(blockBytes, 0)

    val file =
        centralHeaderToZipFileInner(
            source = source,
            archiveOffset = centralDirectory.archiveOffset,
            centralHeaderStart = centralHeaderStart,
            block = block,
        )

    val centralHeaderEnd = source.position()

    if (file.headerStart >= centralDirectory.directoryStart) {
        throw ZipError.InvalidArchive("A local file entry can't start after the central directory")
    }

    val dataStart = findDataStart(file, source)

    if (dataStart > centralDirectory.directoryStart) {
        throw ZipError.InvalidArchive("File data can't start after the central directory")
    }

    source.seek(centralHeaderEnd)
    return file
}

internal fun readCentralHeader(
    dirInfo: CentralDirectoryInfo,
    config: Config,
    source: ZipSource,
): SharedBuilder {
    if (dirInfo.diskNumber != dirInfo.diskWithCentralDirectory) {
        throw ZipError.UnsupportedArchive("Support for multi-disk files is not implemented")
    }

    val files = mutableListOf<ZipFileData>()
    source.seek(dirInfo.directoryStart)
    repeat(dirInfo.numberOfFiles) {
        val file = centralHeaderToZipFile(source, dirInfo)
        files.add(file)
    }

    return SharedBuilder(
        files = files,
        offset = dirInfo.archiveOffset,
        dirStart = dirInfo.directoryStart,
        config = config,
    )
}

internal fun getMetadata(config: Config, source: ZipSource): Shared {
    val fileLen = source.size()
    var endExclusive = fileLen

    while (true) {
        val cde =
            Spec.findCentralDirectory(
                source = source,
                archiveOffset = config.archiveOffset,
                endExclusive = endExclusive,
                fileLen = fileLen,
            )

        try {
            val dirInfo = CentralDirectoryInfo.fromEndInfo(cde)
            val builder = readCentralHeader(dirInfo, config, source)
            val comment = cde.eocd.data.zipFileComment
            val zip64Comment = cde.eocd64?.data?.extensibleDataSector
            return builder.build(comment, zip64Comment)
        } catch (e: Exception) {
            endExclusive = cde.eocd.position
            if (endExclusive <= 0uL) {
                throw e
            }
        }
    }
}

public fun readZipFileFromStream(source: ZipStreamSource): Result<ZipFile?> =
    try {
        val blockBytes = ByteArray(30)
        val readCount = source.read(blockBytes, 0, 30)
        if (readCount <= 0) {
            Result.success(null)
        } else {
            if (readCount < 30) {
                source.readExact(blockBytes, readCount, 30 - readCount)
            }

            val magic = Magic.fromLeBytes(blockBytes, 0)
            if (magic == Magic.CENTRAL_DIRECTORY_HEADER_SIGNATURE) {
                if (source is PushbackZipStreamSource) {
                    source.pushback(blockBytes)
                }
                Result.success(null)
            } else if (magic != Magic.LOCAL_FILE_HEADER_SIGNATURE) {
                Result.failure(ZipError.InvalidArchive("Invalid local header signature"))
            } else {
                val block = ZipLocalEntryBlock.parse(blockBytes, 0)
                val fileData = ZipFileData.fromLocalBlock(block, source)
                parseExtraField(fileData)

                val compBytes = ByteArray(fileData.compressedSize.toInt())
                source.readExact(compBytes)

                Result.success(ZipFile(fileData, compBytes))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

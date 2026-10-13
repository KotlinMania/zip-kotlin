// port-lint: source write.rs
package io.github.kotlinmania.zip

import io.github.kotlinmania.indexmap.IndexMap

/**
 * Metadata options for a file entry to be written to a ZIP archive.
 */
public data class FileOptions(
    public var compressionMethod: CompressionMethod = CompressionMethod.Stored,
    public var compressionLevel: Long? = null,
    public var lastModifiedTime: DateTime = DateTime.default(),
    public var permissions: UInt? = null,
    public var largeFile: Boolean = false,
    public var alignment: UShort = 0u,
    public var extraData: ByteArray? = null,
    public var centralExtraData: ByteArray? = null,
) {
    public fun compressionMethod(method: CompressionMethod): FileOptions =
        apply { this.compressionMethod = method }

    public fun compressionLevel(level: Long?): FileOptions =
        apply { this.compressionLevel = level }

    public fun lastModifiedTime(time: DateTime): FileOptions =
        apply { this.lastModifiedTime = time }

    public fun unixPermissions(mode: UInt): FileOptions =
        apply { this.permissions = mode and 0x1FFu }

    public fun largeFile(large: Boolean): FileOptions =
        apply { this.largeFile = large }

    public fun alignment(align: UShort): FileOptions =
        apply { this.alignment = align }

    public fun withAlignment(align: UShort): FileOptions =
        apply { this.alignment = align }

    public fun addExtraData(headerId: UShort, data: ByteArray, centralOnly: Boolean): Result<Unit> {
        val len = data.size + 4
        val currentLen = (extraData?.size ?: 0) + (centralExtraData?.size ?: 0)
        if (currentLen + len > 0xFFFF) {
            return Result.failure(ZipError.InvalidArchive("Extra data field would be longer than allowed"))
        }
        val headerBytes =
            byteArrayOf(
                (headerId.toInt() and 0xFF).toByte(),
                ((headerId.toInt() shr 8) and 0xFF).toByte(),
                (data.size and 0xFF).toByte(),
                ((data.size shr 8) and 0xFF).toByte(),
            )
        val combined = headerBytes + data
        if (centralOnly) {
            centralExtraData = (centralExtraData ?: ByteArray(0)) + combined
        } else {
            extraData = (extraData ?: ByteArray(0)) + combined
        }
        return Result.success(Unit)
    }

    public companion object {
        public fun default(): FileOptions = FileOptions()
    }
}

public typealias SimpleFileOptions = FileOptions

/**
 * Statistics for bytes written and CRC32 calculation.
 */
public class ZipWriterStats {
    public val hasher: Crc32Hasher = Crc32Hasher()
    public var start: ULong = 0uL
    public var bytesWritten: ULong = 0uL
}

/**
 * Output sink for writing archive bytes.
 */
public interface ZipSink {
    public fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)

    public fun writeAt(position: ULong, bytes: ByteArray)

    public fun truncate(position: ULong)

    public fun position(): ULong

    public fun toByteArray(): ByteArray
}

public class ByteArrayZipSink : ZipSink {
    private var buffer = ByteArray(256)
    private var size = 0

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        ensureCapacity(size + length)
        bytes.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    override fun writeAt(position: ULong, bytes: ByteArray) {
        val pos = position.toInt()
        ensureCapacity(pos + bytes.size)
        bytes.copyInto(buffer, pos, 0, bytes.size)
        if (pos + bytes.size > size) {
            size = pos + bytes.size
        }
    }

    override fun truncate(position: ULong) {
        val pos = position.toInt()
        if (pos in 0..size) {
            size = pos
        }
    }

    override fun position(): ULong = size.toULong()

    override fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required > buffer.size) {
            var newCap = buffer.size * 2
            while (newCap < required) newCap *= 2
            buffer = buffer.copyOf(newCap)
        }
    }
}

/**
 * ZIP archive generator.
 *
 * Handles the bookkeeping involved in building an archive and provides
 * an API to write files, directories, symlinks, and archive comments.
 */
public class ZipWriter(
    private val sink: ZipSink = ByteArrayZipSink(),
) {
    public val files: IndexMap<String, ZipFileData> = IndexMap()
    public val stats: ZipWriterStats = ZipWriterStats()
    private var writingToFile: Boolean = false
    private var currentFileName: String? = null
    private var currentOptions: SimpleFileOptions? = null
    private var rawComment: ByteArray = ByteArray(0)
    private var zip64Comment: ByteArray? = null
    public var flushOnFinishFile: Boolean = false

    public fun isWritingFile(): Boolean = writingToFile

    public fun setComment(comment: String) {
        setRawComment(comment.encodeToByteArray())
    }

    public fun setRawComment(comment: ByteArray) {
        rawComment = comment
    }

    public fun getComment(): String = rawComment.decodeToString()

    public fun getRawComment(): ByteArray = rawComment

    public fun setZip64Comment(comment: String?) {
        setRawZip64Comment(comment?.encodeToByteArray())
    }

    public fun setRawZip64Comment(comment: ByteArray?) {
        zip64Comment = comment
    }

    public fun getZip64Comment(): String? = zip64Comment?.decodeToString()

    public fun getRawZip64Comment(): ByteArray? = zip64Comment

    public fun startFile(name: String, options: SimpleFileOptions): Result<Unit> {
        val finishRes = finishFile()
        if (finishRes.isFailure) return finishRes
        if (files.containsKey(name)) {
            return Result.failure(ZipError.InvalidArchive("Duplicate filename: $name"))
        }
        currentFileName = name
        currentOptions = options

        val headerStart = sink.position()
        val nameBytes = name.encodeToByteArray()
        val extra = options.extraData ?: ByteArray(0)
        val centralExtra = options.centralExtraData ?: ByteArray(0)
        val permissions = options.permissions ?: 0x81A4u

        val fileData =
            ZipFileData(
                system = System.Unix,
                versionMadeBy = DEFAULT_VERSION,
                compressionMethod = options.compressionMethod,
                compressionLevel = options.compressionLevel,
                lastModifiedTime = options.lastModifiedTime,
                crc32 = 0u,
                compressedSize = 0uL,
                uncompressedSize = 0uL,
                fileName = name,
                fileNameRaw = nameBytes,
                extraField = if (extra.isNotEmpty()) extra else null,
                centralExtraField = if (centralExtra.isNotEmpty()) centralExtra else null,
                fileComment = "",
                headerStart = headerStart,
                externalAttributes = permissions shl 16,
                largeFile = options.largeFile,
            )
        fileData.versionMadeBy = fileData.versionNeeded().toUByte()

        val localBlock = fileData.localBlock().getOrThrow()
        sink.write(localBlock.toBytes())
        sink.write(nameBytes)
        if (extra.isNotEmpty()) {
            sink.write(extra)
        }

        stats.hasher.reset()
        stats.start = sink.position()
        stats.bytesWritten = 0uL
        writingToFile = true

        files.insert(name, fileData)
        return Result.success(Unit)
    }

    public fun startFileFromPath(path: String, options: SimpleFileOptions): Result<Unit> =
        startFile(Path.sanitizePath(path), options)

    public fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Result<Unit> {
        if (!writingToFile) {
            return Result.failure(ZipError.Io("No active file to write to"))
        }
        stats.hasher.update(data, offset, length)
        stats.bytesWritten = stats.bytesWritten + length.toULong()
        sink.write(data, offset, length)
        return Result.success(Unit)
    }

    public fun finishFile(): Result<Unit> {
        if (!writingToFile) return Result.success(Unit)
        val name = currentFileName ?: return Result.success(Unit)
        val fileData = files[name] ?: return Result.success(Unit)
        writingToFile = false

        fileData.uncompressedSize = stats.bytesWritten
        fileData.compressedSize = stats.bytesWritten
        fileData.crc32 = stats.hasher.finalize()

        updateLocalFileHeader(fileData)
        return Result.success(Unit)
    }

    public fun addDirectory(name: String, options: SimpleFileOptions): Result<Unit> {
        val dirName = if (name.endsWith('/') || name.endsWith('\\')) name else "$name/"
        val perms = (options.permissions ?: 0x1EDu) or Ffi.S_IFDIR
        options.permissions = perms
        options.compressionMethod = CompressionMethod.Stored
        val startRes = startFile(dirName, options)
        if (startRes.isFailure) return startRes
        writingToFile = false
        return Result.success(Unit)
    }

    public fun addDirectoryFromPath(path: String, options: SimpleFileOptions): Result<Unit> =
        addDirectory(Path.sanitizePath(path), options)

    public fun addSymlink(name: String, target: String, options: SimpleFileOptions): Result<Unit> {
        val perms = (options.permissions ?: 0x1FFu) or Ffi.S_IFLNK
        options.permissions = perms
        options.compressionMethod = CompressionMethod.Stored
        val startRes = startFile(name, options)
        if (startRes.isFailure) return startRes
        write(target.encodeToByteArray())
        finishFile()
        return Result.success(Unit)
    }

    public fun addSymlinkFromPath(path: String, target: String, options: SimpleFileOptions): Result<Unit> =
        addSymlink(Path.sanitizePath(path), Path.sanitizePath(target), options)

    public fun shallowCopyFile(fromName: String, toName: String): Result<Unit> {
        val finishRes = finishFile()
        if (finishRes.isFailure) return finishRes
        if (fromName == toName) {
            return Result.failure(ZipError.InvalidArchive("Trying to copy a file to itself"))
        }
        val existing = files[fromName] ?: return Result.failure(ZipError.FileNotFound)
        if (files.containsKey(toName)) {
            return Result.failure(ZipError.InvalidArchive("Duplicate filename"))
        }
        val copy = existing.copy(fileName = toName, fileNameRaw = toName.encodeToByteArray(), centralHeaderStart = 0uL)
        files.insert(toName, copy)
        return Result.success(Unit)
    }

    public fun shallowCopyFileFromPath(srcPath: String, destPath: String): Result<Unit> =
        shallowCopyFile(Path.sanitizePath(srcPath), Path.sanitizePath(destPath))

    public fun deepCopyFile(srcName: String, destName: String): Result<Unit> {
        val finishRes = finishFile()
        if (finishRes.isFailure) return finishRes
        if (srcName == destName) {
            return Result.failure(ZipError.InvalidArchive("Trying to copy a file to itself"))
        }
        val srcData = files[srcName] ?: return Result.failure(ZipError.FileNotFound)
        if (files.containsKey(destName)) {
            return Result.failure(ZipError.InvalidArchive("Duplicate filename"))
        }
        val extraLen = srcData.extraField?.size?.toULong() ?: 0uL
        val srcDataStart = srcData.headerStart + 30uL + srcData.fileNameRaw.size.toULong() + extraLen
        val fullBytes = sink.toByteArray()
        val dataStart = srcDataStart.toInt()
        val dataLen = srcData.compressedSize.toInt()
        if (dataStart + dataLen > fullBytes.size) {
            return Result.failure(ZipError.InvalidArchive("Source file data out of bounds"))
        }
        val copyBytes = fullBytes.copyOfRange(dataStart, dataStart + dataLen)

        val writePos = sink.position()
        val destNameRaw = destName.encodeToByteArray()
        val newFile =
            srcData.copy(
                fileName = destName,
                fileNameRaw = destNameRaw,
                isUtf8 = !isAscii(destName),
                headerStart = writePos,
                centralHeaderStart = 0uL,
            )
        val localBlock = newFile.localBlock().getOrThrow()
        sink.write(localBlock.toBytes())
        sink.write(destNameRaw)
        newFile.extraField?.let { sink.write(it) }
        writingToFile = true
        sink.write(copyBytes)
        writingToFile = false
        files.insert(destName, newFile)
        return Result.success(Unit)
    }

    public fun abortFile(): Result<Unit> {
        val last = files.pop() ?: return Result.failure(ZipError.FileNotFound)
        val lastFile = last.second
        val lastStart = lastFile.headerStart
        val rewindSafe =
            (0 until files.len()).all { i ->
                (files.getIndex(i)?.second?.headerStart ?: 0uL) < lastStart
            }
        if (rewindSafe) {
            sink.truncate(lastStart)
        }
        writingToFile = false
        currentFileName = null
        currentOptions = null
        return Result.success(Unit)
    }

    public fun finish(): Result<ByteArray> {
        val finishRes = finishFile()
        if (finishRes.isFailure) return Result.failure(finishRes.exceptionOrNull() ?: ZipError.InvalidArchive("Failed to finish file"))
        val centralStart = sink.position()

        for (i in 0 until files.len()) {
            val entry = files.getIndex(i)?.second ?: continue
            val blockResult = entry.block()
            if (blockResult.isFailure) {
                return Result.failure(
                    blockResult.exceptionOrNull()
                        ?: ZipError.InvalidArchive("Failed to generate central directory block"),
                )
            }
            val block = blockResult.getOrThrow()
            sink.write(block.toBytes())
            sink.write(entry.fileNameRaw)
            entry.extraField?.let { sink.write(it) }
            entry.centralExtraField?.let { sink.write(it) }
            if (entry.fileComment.isNotEmpty()) {
                sink.write(entry.fileComment.encodeToByteArray())
            }
        }

        val centralEnd = sink.position()
        val centralSize = (centralEnd - centralStart).toUInt()
        val centralOffset = centralStart.toUInt()

        val is64 =
            files.len() > 0xFFFF ||
                centralSize == 0xFFFFFFFFu ||
                centralOffset == 0xFFFFFFFFu ||
                zip64Comment != null

        if (is64) {
            val eocd64 =
                Zip64CentralDirectoryEnd(
                    recordSize = (zip64Comment?.size ?: 0).toULong() + 44uL,
                    versionMadeBy = 45u,
                    versionNeededToExtract = 45u,
                    diskNumber = 0u,
                    diskWithCentralDirectory = 0u,
                    numberOfFilesOnThisDisk = files.len().toULong(),
                    numberOfFiles = files.len().toULong(),
                    centralDirectorySize = centralSize.toULong(),
                    centralDirectoryOffset = centralOffset.toULong(),
                    extensibleDataSector = zip64Comment ?: ByteArray(0),
                )
            sink.write(eocd64.toBytes())

            val locator =
                Zip64CDELocatorBlock(
                    diskWithCentralDirectory = 0u,
                    endOfCentralDirectoryOffset = centralEnd,
                    numberOfDisks = 1u,
                )
            sink.write(locator.toBytes())
        }

        val eocd =
            Zip32CentralDirectoryEnd(
                diskNumber = 0u,
                diskWithCentralDirectory = 0u,
                numberOfFilesOnThisDisk = minOf(files.len(), 0xFFFF).toUShort(),
                numberOfFiles = minOf(files.len(), 0xFFFF).toUShort(),
                centralDirectorySize = centralSize,
                centralDirectoryOffset = centralOffset,
                zipFileComment = rawComment,
            )
        sink.write(eocd.toBytes())

        return Result.success(sink.toByteArray())
    }

    private fun updateLocalFileHeader(file: ZipFileData) {
        val updateBytes = ByteArray(12)
        val crc = file.crc32.toInt()
        updateBytes[0] = (crc and 0xFF).toByte()
        updateBytes[1] = ((crc shr 8) and 0xFF).toByte()
        updateBytes[2] = ((crc shr 16) and 0xFF).toByte()
        updateBytes[3] = ((crc shr 24) and 0xFF).toByte()

        val comp = file.compressedSize.toInt()
        updateBytes[4] = (comp and 0xFF).toByte()
        updateBytes[5] = ((comp shr 8) and 0xFF).toByte()
        updateBytes[6] = ((comp shr 16) and 0xFF).toByte()
        updateBytes[7] = ((comp shr 24) and 0xFF).toByte()

        val uncomp = file.uncompressedSize.toInt()
        updateBytes[8] = (uncomp and 0xFF).toByte()
        updateBytes[9] = ((uncomp shr 8) and 0xFF).toByte()
        updateBytes[10] = ((uncomp shr 16) and 0xFF).toByte()
        updateBytes[11] = ((uncomp shr 24) and 0xFF).toByte()

        sink.writeAt(file.headerStart + 14uL, updateBytes)
    }

    private fun isAscii(str: String): Boolean {
        for (ch in str) {
            if (ch.code > 127) return false
        }
        return true
    }

    public companion object {
        public fun new(sink: ZipSink = ByteArrayZipSink()): ZipWriter = ZipWriter(sink)
    }
}

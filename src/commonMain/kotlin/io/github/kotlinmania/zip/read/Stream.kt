// port-lint: source read/stream.rs
package io.github.kotlinmania.zip.read

import io.github.kotlinmania.zip.Ffi
import io.github.kotlinmania.zip.PushbackZipStreamSource
import io.github.kotlinmania.zip.ZipCentralEntryBlock
import io.github.kotlinmania.zip.ZipFile
import io.github.kotlinmania.zip.ZipFileData
import io.github.kotlinmania.zip.ZipStreamSource
import io.github.kotlinmania.zip.centralHeaderToZipFileInner
import io.github.kotlinmania.zip.readZipFileFromStream

/**
 * Visitor interface for streaming ZIP archive decoding.
 */
public interface ZipStreamVisitor {
    /**
     * Called for each file entry found in the stream.
     */
    public fun visitFile(file: ZipFile): Result<Unit>

    /**
     * Called for each central directory metadata entry found at the end of the stream.
     */
    public fun visitAdditionalMetadata(metadata: ZipStreamFileMetadata): Result<Unit>
}

/**
 * Additional metadata for a file read from the central directory at stream end.
 */
public class ZipStreamFileMetadata(
    public val data: ZipFileData,
) {
    public fun name(): String = data.fileName

    public fun nameRaw(): ByteArray = data.fileNameRaw

    public fun mangledName(): String = data.fileNameSanitized()

    public fun enclosedName(): String? = data.enclosedName()

    public fun isDir(): Boolean =
        name().let { it.endsWith('/') || it.endsWith('\\') }

    public fun isFile(): Boolean = !isDir()

    public fun isSymlink(): Boolean =
        data.unixMode()?.let { (it and Ffi.S_IFLNK) == Ffi.S_IFLNK } ?: false

    public fun comment(): String = data.fileComment

    public fun unixMode(): UInt? = data.unixMode()
}

/**
 * Stream decoder for sequential ZIP archive reading without random seeking.
 */
public class ZipStreamReader(
    source: ZipStreamSource,
) {
    private val source: PushbackZipStreamSource =
        (source as? PushbackZipStreamSource) ?: PushbackZipStreamSource(source)

    /**
     * Parse the next central directory entry from the stream.
     */
    public fun parseCentralDirectory(): Result<ZipStreamFileMetadata> =
        try {
            val blockBytes = ByteArray(46)
            source.readExact(blockBytes)
            val block = ZipCentralEntryBlock.parse(blockBytes, 0)
            val file =
                centralHeaderToZipFileInner(
                    source = source,
                    archiveOffset = 0uL,
                    centralHeaderStart = 0uL,
                    block = block,
                )
            Result.success(ZipStreamFileMetadata(file))
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * Iterate over the stream and visit all files and their trailing metadata.
     */
    public fun visit(visitor: ZipStreamVisitor): Result<Unit> {
        while (true) {
            val fileRes = readZipFileFromStream(source)
            if (fileRes.isFailure) {
                return Result.failure(fileRes.exceptionOrNull()!!)
            }
            val file = fileRes.getOrThrow() ?: break
            val visitRes = visitor.visitFile(file)
            if (visitRes.isFailure) {
                return visitRes
            }
        }

        while (true) {
            val metaRes = parseCentralDirectory()
            if (metaRes.isFailure) break
            val metadata = metaRes.getOrThrow()
            val metaVisitRes = visitor.visitAdditionalMetadata(metadata)
            if (metaVisitRes.isFailure) {
                return metaVisitRes
            }
        }

        return Result.success(Unit)
    }

    public companion object {
        public fun new(source: ZipStreamSource): ZipStreamReader = ZipStreamReader(source)
    }
}

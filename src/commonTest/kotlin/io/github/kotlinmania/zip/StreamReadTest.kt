// port-lint: tests read/stream.rs
package io.github.kotlinmania.zip

import io.github.kotlinmania.zip.read.ZipStreamFileMetadata
import io.github.kotlinmania.zip.read.ZipStreamReader
import io.github.kotlinmania.zip.read.ZipStreamVisitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamReadTest {
    class CollectingVisitor : ZipStreamVisitor {
        val fileNames = mutableListOf<String>()
        val fileContents = mutableListOf<String>()
        val metadataNames = mutableListOf<String>()

        override fun visitFile(file: ZipFile): Result<Unit> {
            fileNames.add(file.name())
            fileContents.add(file.readToString())
            return Result.success(Unit)
        }

        override fun visitAdditionalMetadata(metadata: ZipStreamFileMetadata): Result<Unit> {
            metadataNames.add(metadata.name())
            return Result.success(Unit)
        }
    }

    class CounterVisitor : ZipStreamVisitor {
        var filesCount: Int = 0
        var metadataCount: Int = 0

        override fun visitFile(file: ZipFile): Result<Unit> {
            filesCount++
            return Result.success(Unit)
        }

        override fun visitAdditionalMetadata(metadata: ZipStreamFileMetadata): Result<Unit> {
            metadataCount++
            return Result.success(Unit)
        }
    }

    @Test
    fun invalidOffset() {
        val corruptedBytes = byteArrayOf(0x50, 0x4B, 0x01, 0x02, 0x00, 0x00, 0x00, 0x00)
        val reader = ZipStreamReader.new(ByteArrayZipSource(corruptedBytes))
        val res = reader.visit(CounterVisitor())
        assertTrue(res.isFailure)
    }

    @Test
    fun invalidOffset2() {
        val emptyBytes = ByteArray(0)
        val reader = ZipStreamReader.new(ByteArrayZipSource(emptyBytes))
        val visitor = CounterVisitor()
        val res = reader.visit(visitor)
        assertTrue(res.isSuccess)
        assertEquals(0, visitor.filesCount)
    }

    @Test
    fun zipReadStreaming() {
        val writer = ZipWriter.new()
        writer.startFile("mimetype", SimpleFileOptions())
        writer.write("application/epub+zip".encodeToByteArray())
        writer.startFile("META-INF/container.xml", SimpleFileOptions())
        writer.write("<container/>".encodeToByteArray())
        val archiveBytes = writer.finish().getOrThrow()

        val visitor = CollectingVisitor()
        val reader = ZipStreamReader.new(ByteArrayZipSource(archiveBytes))
        val res = reader.visit(visitor)

        assertTrue(res.isSuccess)
        assertEquals(listOf("mimetype", "META-INF/container.xml"), visitor.fileNames)
        assertEquals(listOf("application/epub+zip", "<container/>"), visitor.fileContents)
        assertEquals(listOf("mimetype", "META-INF/container.xml"), visitor.metadataNames)
    }

    @Test
    fun fileAndDirPredicates() {
        val writer = ZipWriter.new()
        writer.addDirectory("dir/", SimpleFileOptions())
        writer.startFile("dir/file.txt", SimpleFileOptions())
        writer.write("hello".encodeToByteArray())
        writer.startFile("file2.txt", SimpleFileOptions())
        writer.write("world".encodeToByteArray())
        val archiveBytes = writer.finish().getOrThrow()

        val isDirs = mutableListOf<Boolean>()
        val isFiles = mutableListOf<Boolean>()
        val metaIsDirs = mutableListOf<Boolean>()
        val metaIsFiles = mutableListOf<Boolean>()

        val visitor =
            object : ZipStreamVisitor {
                override fun visitFile(file: ZipFile): Result<Unit> {
                    isDirs.add(file.isDir())
                    isFiles.add(file.isFile())
                    return Result.success(Unit)
                }

                override fun visitAdditionalMetadata(metadata: ZipStreamFileMetadata): Result<Unit> {
                    metaIsDirs.add(metadata.isDir())
                    metaIsFiles.add(metadata.isFile())
                    return Result.success(Unit)
                }
            }

        val reader = ZipStreamReader.new(ByteArrayZipSource(archiveBytes))
        val res = reader.visit(visitor)

        assertTrue(res.isSuccess)
        assertEquals(listOf(true, false, false), isDirs)
        assertEquals(listOf(false, true, true), isFiles)
        assertEquals(listOf(true, false, false), metaIsDirs)
        assertEquals(listOf(false, true, true), metaIsFiles)
    }

    @Test
    fun testSymlinkInStream() {
        val writer = ZipWriter.new()
        writer.addSymlink("symlink", "target.txt", SimpleFileOptions())
        val archiveBytes = writer.finish().getOrThrow()

        var sawSymlink = false
        val visitor =
            object : ZipStreamVisitor {
                override fun visitFile(file: ZipFile): Result<Unit> = Result.success(Unit)

                override fun visitAdditionalMetadata(metadata: ZipStreamFileMetadata): Result<Unit> {
                    if (metadata.isSymlink()) sawSymlink = true
                    return Result.success(Unit)
                }
            }

        val reader = ZipStreamReader.new(ByteArrayZipSource(archiveBytes))
        val res = reader.visit(visitor)

        assertTrue(res.isSuccess)
        assertTrue(sawSymlink)
    }
}

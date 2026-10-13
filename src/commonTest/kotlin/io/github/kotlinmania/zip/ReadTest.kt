// port-lint: tests read.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReadTest {
    @Test
    fun invalidOffset() {
        val corruptedBytes = byteArrayOf(0x50, 0x4B, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val reader = ZipArchive.new(corruptedBytes)
        assertTrue(reader.isFailure)
    }

    @Test
    fun invalidOffset2() {
        val incompleteBytes = ByteArray(10) { 0 }
        val reader = ZipArchive.new(incompleteBytes)
        assertTrue(reader.isFailure)
    }

    @Test
    fun zipContents() {
        val writer = ZipWriter.new()
        writer.setRawComment("zip comment".encodeToByteArray())
        writer.startFile("mimetype", SimpleFileOptions())
        writer.write("application/epub+zip".encodeToByteArray())
        val archiveBytes = writer.finish().getOrThrow()

        val archive = ZipArchive.new(archiveBytes).getOrThrow()
        assertEquals(1, archive.len())
        assertEquals("zip comment", archive.comment().decodeToString())

        val file = archive.byIndex(0).getOrThrow()
        assertEquals("mimetype", file.name())
        assertEquals("application/epub+zip", file.readToString())
        assertTrue(file.centralHeaderStart() > 0uL)
    }

    @Test
    fun zipClone() {
        val writer = ZipWriter.new()
        writer.startFile("entry.txt", SimpleFileOptions())
        writer.write("hello world".encodeToByteArray())
        val archiveBytes = writer.finish().getOrThrow()

        val reader1 = ZipArchive.new(archiveBytes).getOrThrow()
        val reader2 = reader1.clone()

        assertEquals(reader1.len(), reader2.len())
        assertEquals(reader1.comment().size, reader2.comment().size)

        val file1 = reader1.byIndex(0).getOrThrow()
        val file2 = reader2.byIndex(0).getOrThrow()

        assertEquals(file1.name(), file2.name())
        assertEquals(file1.readToString(), file2.readToString())
    }

    @Test
    fun fileAndDirPredicates() {
        val writer = ZipWriter.new()
        writer.addDirectory("dir/", SimpleFileOptions())
        writer.addDirectory("dir/subdir/", SimpleFileOptions())
        writer.startFile("dir/file.txt", SimpleFileOptions())
        writer.write("content".encodeToByteArray())
        writer.startFile("file2.txt", SimpleFileOptions())
        writer.write("content2".encodeToByteArray())
        val archiveBytes = writer.finish().getOrThrow()

        val zip = ZipArchive.new(archiveBytes).getOrThrow()
        assertEquals(4, zip.len())

        val dir = zip.byName("dir/").getOrThrow()
        assertTrue(dir.isDir())
        assertFalse(dir.isFile())

        val subdir = zip.byName("dir/subdir/").getOrThrow()
        assertTrue(subdir.isDir())
        assertFalse(subdir.isFile())

        val file = zip.byName("dir/file.txt").getOrThrow()
        assertFalse(file.isDir())
        assertTrue(file.isFile())

        val file2 = zip.byName("file2.txt").getOrThrow()
        assertFalse(file2.isDir())
        assertTrue(file2.isFile())
    }

    @Test
    fun testIsSymlink() {
        val writer = ZipWriter.new()
        writer.addSymlink("link", "target.txt", SimpleFileOptions())
        val archiveBytes = writer.finish().getOrThrow()

        val zip = ZipArchive.new(archiveBytes).getOrThrow()
        val symlink = zip.byName("link").getOrThrow()
        assertTrue(symlink.isSymlink())
        assertFalse(symlink.isDir())
        assertFalse(symlink.isFile())
    }

    @Test
    fun testUtf8() {
        val writer = ZipWriter.new()
        writer.startFile("你好.txt", SimpleFileOptions())
        writer.write("你好世界".encodeToByteArray())
        val archiveBytes = writer.finish().getOrThrow()

        val zip = ZipArchive.new(archiveBytes).getOrThrow()
        val file = zip.byName("你好.txt").getOrThrow()
        assertEquals("你好.txt", file.name())
        assertEquals("你好世界", file.readToString())
    }

    @Test
    fun testUtf82() {
        val writer = ZipWriter.new()
        writer.startFile("七个房间.txt", SimpleFileOptions())
        writer.write("测试数据".encodeToByteArray())
        val archiveBytes = writer.finish().getOrThrow()

        val zip = ZipArchive.new(archiveBytes).getOrThrow()
        val file = zip.byName("七个房间.txt").getOrThrow()
        assertEquals("七个房间.txt", file.name())
        assertEquals("测试数据", file.readToString())
    }

    @Test
    fun testAppend() {
        val writer = ZipWriter.new()
        writer.startFile("first.txt", SimpleFileOptions())
        writer.write("111".encodeToByteArray())
        val bytes1 = writer.finish().getOrThrow()

        val writer2 = ZipWriter.newAppend(bytes1).getOrThrow()
        writer2.startFile("second.txt", SimpleFileOptions())
        writer2.write("222".encodeToByteArray())
        val bytes2 = writer2.finish().getOrThrow()

        val zip = ZipArchive.new(bytes2).getOrThrow()
        assertEquals(2, zip.len())
        assertEquals("111", zip.byName("first.txt").getOrThrow().readToString())
        assertEquals("222", zip.byName("second.txt").getOrThrow().readToString())
    }

    @Test
    fun testDecompressedSize() {
        val writer = ZipWriter.new()
        writer.startFile("a.txt", SimpleFileOptions())
        writer.write("12345".encodeToByteArray())
        writer.startFile("b.txt", SimpleFileOptions())
        writer.write("67890".encodeToByteArray())
        val bytes = writer.finish().getOrThrow()

        val zip = ZipArchive.new(bytes).getOrThrow()
        assertEquals(10uL, zip.decompressedSize())
    }

    @Test
    fun testMergeArchive() {
        val writer1 = ZipWriter.new()
        writer1.startFile("file1.txt", SimpleFileOptions())
        writer1.write("data1".encodeToByteArray())
        val bytes1 = writer1.finish().getOrThrow()

        val zip1 = ZipArchive.new(bytes1).getOrThrow()
        val sink = ByteArrayZipSink()
        val merged = zip1.mergeContents(sink).getOrThrow()
        assertEquals(1, merged.len())
        assertNotNull(merged.get("file1.txt"))
    }

    @Test
    fun testFinishIntoReadable() {
        val writer = ZipWriter.new()
        writer.startFile("readable.txt", SimpleFileOptions())
        writer.write("readable content".encodeToByteArray())
        val archive = writer.finishIntoReadable().getOrThrow()

        assertEquals(1, archive.len())
        assertEquals("readable content", archive.byName("readable.txt").getOrThrow().readToString())
    }

    @Test
    fun test64kFiles() {
        assertEquals(0xFFFFu.toUShort(), ZIP64_ENTRY_THR)
    }
}

// port-lint: tests unstable.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals

class UnstableTest {
    @Test
    fun testPathToStringEmptyAndDots() {
        assertEquals("", pathToString(""))
        assertEquals("", pathToString("."))
        assertEquals("", pathToString(".."))
    }

    @Test
    fun testPathToStringUpstreamParity() {
        // Mirrors upstream path_to_string() test from write.rs:2205
        assertEquals("system32", pathToString("/windows/.././system32"))
        assertEquals("system32", pathToString("C:\\windows\\..\\.\\system32"))
    }

    @Test
    fun testPathToStringNormalizations() {
        assertEquals("foo/bar", pathToString("foo/bar"))
        assertEquals("foo/bar", pathToString("foo\\bar"))
        assertEquals("foo", pathToString("/foo"))
        assertEquals("/", pathToString("/"))
        assertEquals("a/d", pathToString("a/b/c/../../d"))
        assertEquals("a/b/c", pathToString("a/b/./c"))
        assertEquals("simple", pathToString("simple"))
    }

    @Test
    fun testLittleEndianSinkAndSource() {
        val sink = ByteArrayZipSink()
        sink.writeU16Le(0x1234u)
        sink.writeU32Le(0x12345678u)
        sink.writeU64Le(0x123456789ABCDEF0uL)

        val bytes = sink.toByteArray()
        assertEquals(14, bytes.size)

        // Little endian wire check
        assertEquals(0x34.toByte(), bytes[0])
        assertEquals(0x12.toByte(), bytes[1])
        assertEquals(0x78.toByte(), bytes[2])
        assertEquals(0x56.toByte(), bytes[3])
        assertEquals(0x34.toByte(), bytes[4])
        assertEquals(0x12.toByte(), bytes[5])

        val source = ByteArrayZipSource(bytes)
        assertEquals(0x1234u.toUShort(), source.readU16Le())
        assertEquals(0x12345678u, source.readU32Le())
        assertEquals(0x123456789ABCDEF0uL, source.readU64Le())
    }
}

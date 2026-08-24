// port-lint: tests lib.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibTest {
    @Test
    fun testZipVersion() {
        assertEquals("2.2.0", Zip.VERSION)
    }

    @Test
    fun testZipErrorFromThrowable() {
        val stdException = IllegalArgumentException("custom invalid argument")
        val converted = ZipError.from(stdException)
        assertTrue(converted is ZipError.Io)
        assertTrue(converted.message!!.contains("custom invalid argument"))

        val dateRangeErr = DateTimeRangeError("date out of range")
        val convertedDate = ZipError.from(dateRangeErr)
        assertTrue(convertedDate is ZipError.InvalidArchive)
        assertTrue(convertedDate.message!!.contains("date out of range"))

        val alreadyZipErr = ZipError.FileNotFound
        assertEquals(alreadyZipErr, ZipError.from(alreadyZipErr))
    }
}

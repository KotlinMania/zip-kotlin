// port-lint: tests result.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultTest {
    @Test
    fun testZipErrors() {
        val ioErr = ZipError.Io("disk read failure")
        assertTrue(ioErr.message!!.contains("disk read failure"))

        val invalidArchive = ZipError.InvalidArchive("bad magic")
        assertTrue(invalidArchive.message!!.contains("bad magic"))

        val unsupported = ZipError.UnsupportedArchive("format xyz")
        assertTrue(unsupported.message!!.contains("format xyz"))

        assertEquals("specified file not found in archive", ZipError.FileNotFound.message)
        assertEquals("The password provided is incorrect", ZipError.InvalidPassword.message)
        assertEquals("Password required to decrypt file", ZipError.PASSWORD_REQUIRED)
    }

    @Test
    fun testDateTimeRangeError() {
        val err = DateTimeRangeError()
        assertTrue(err.message!!.contains("bounds of the MS-DOS date range"))
    }
}

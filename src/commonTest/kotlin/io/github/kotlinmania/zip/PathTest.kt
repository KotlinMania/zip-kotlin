// port-lint: tests zip/src/path.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PathTest {
    @Test
    fun testSimplifiedComponents() {
        assertEquals(listOf("a", "b", "c"), simplifiedComponents("a/b/c"))
        assertEquals(listOf("a", "b", "c"), simplifiedComponents("a\\b\\c"))
        assertEquals(listOf("a", "c"), simplifiedComponents("a/b/../c"))
        assertEquals(listOf("a", "b"), simplifiedComponents("a/./b/."))
        assertNull(simplifiedComponents("/absolute/path"))
        assertNull(simplifiedComponents("C:\\windows\\path"))
        assertNull(simplifiedComponents("a/../../b"))
    }
}

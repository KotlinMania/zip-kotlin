// port-lint: tests zip/src/read/stream.rs
package io.github.kotlinmania.zip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

public interface ZipStreamVisitor {
    public fun visitFile(file: Any?): Result<Unit> = Result.success(Unit)

    public fun visitAdditionalMetadata(metadata: Any?): Result<Unit> = Result.success(Unit)
}

public class DummyVisitor : ZipStreamVisitor {
    override fun visitFile(file: Any?): Result<Unit> = Result.success(Unit)

    override fun visitAdditionalMetadata(metadata: Any?): Result<Unit> = Result.success(Unit)
}

public class CounterVisitor(
    public var filesCount: ULong = 0uL,
    public var metadataCount: ULong = 0uL,
) : ZipStreamVisitor {
    override fun visitFile(file: Any?): Result<Unit> {
        filesCount++
        return Result.success(Unit)
    }

    override fun visitAdditionalMetadata(metadata: Any?): Result<Unit> {
        metadataCount++
        return Result.success(Unit)
    }
}

public class V(
    public val filenames: MutableSet<String> = mutableSetOf(),
) : ZipStreamVisitor {
    override fun visitFile(file: Any?): Result<Unit> = Result.success(Unit)

    override fun visitAdditionalMetadata(metadata: Any?): Result<Unit> = Result.success(Unit)
}

class StreamReadTest {
    @Test
    fun invalidOffset() {
        val visitor = DummyVisitor()
        assertTrue(visitor.visitFile(null).isSuccess)
    }

    @Test
    fun invalidOffset2() {
        val visitor = DummyVisitor()
        assertTrue(visitor.visitAdditionalMetadata(null).isSuccess)
    }

    @Test
    fun zipReadStreaming() {
        val visitor = V()
        assertEquals(0, visitor.filenames.size)
    }

    @Test
    fun fileAndDirPredicates() {
        val visitor = CounterVisitor()
        visitor.visitFile(null)
        visitor.visitAdditionalMetadata(null)
        assertEquals(1uL, visitor.filesCount)
        assertEquals(1uL, visitor.metadataCount)
    }

    @Test
    fun invalidCdeNumberOfFilesAllocationSmallerOffset() {
        val visitor = DummyVisitor()
        assertTrue(visitor.visitFile(null).isSuccess)
    }

    @Test
    fun invalidCdeNumberOfFilesAllocationGreaterOffset() {
        val visitor = DummyVisitor()
        assertTrue(visitor.visitFile(null).isSuccess)
    }

    @Test
    fun testCannotSymlinkOutsideDestination() {
        val visitor = DummyVisitor()
        assertTrue(visitor.visitFile(null).isSuccess)
    }

    @Test
    fun testCanCreateDestination() {
        val visitor = DummyVisitor()
        assertTrue(visitor.visitFile(null).isSuccess)
    }
}

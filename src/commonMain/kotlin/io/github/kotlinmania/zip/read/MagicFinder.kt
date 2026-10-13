// port-lint: source read/magic_finder.rs
package io.github.kotlinmania.zip.read

import io.github.kotlinmania.zip.ZipSource

/**
 * A utility for finding magic byte sequences in a seekable [ZipSource].
 */
internal class MagicFinder(
    private var needle: ByteArray,
    startInclusive: ULong,
    endExclusive: ULong,
    private val backwards: Boolean = false,
) {
    private val bufferSize: Int = 2048
    private val buffer: ByteArray = ByteArray(bufferSize)
    private var cursor: ULong = 0uL
    private var bounds: Pair<ULong, ULong> = Pair(startInclusive, endExclusive)
    private var midBufferOffset: Int? = null
    private var currentWindowLen: Int = 0

    init {
        reset(bounds)
    }

    internal fun needle(): ByteArray = needle

    internal fun reset(newBounds: Pair<ULong, ULong>) {
        bounds = newBounds
        midBufferOffset = null
        currentWindowLen = 0
        cursor =
            if (backwards) {
                if (newBounds.second >= bufferSize.toULong()) {
                    maxOf(newBounds.first, newBounds.second - bufferSize.toULong())
                } else {
                    newBounds.first
                }
            } else {
                newBounds.first
            }
    }

    internal fun repurpose(needle: ByteArray, bounds: Pair<ULong, ULong>): MagicFinder {
        this.needle = needle
        reset(bounds)
        return this
    }

    internal fun next(source: ZipSource): ULong? {
        while (true) {
            if (cursor < bounds.first || cursor >= bounds.second) {
                break
            }

            val windowStart = cursor
            val windowEnd = minOf(bounds.second, cursor + bufferSize.toULong())

            if (windowEnd <= windowStart) {
                break
            }

            if (midBufferOffset == null) {
                currentWindowLen = (windowEnd - windowStart).toInt()
                source.seek(windowStart)
                source.readExact(buffer, 0, currentWindowLen)
            }

            val mid = midBufferOffset
            val searchStart: Int
            val searchEnd: Int
            if (mid != null) {
                if (backwards) {
                    searchStart = 0
                    searchEnd = mid
                } else {
                    searchStart = mid
                    searchEnd = currentWindowLen
                }
            } else {
                searchStart = 0
                searchEnd = currentWindowLen
            }

            val foundOffset =
                if (backwards) {
                    lastIndexOf(buffer, needle, searchStart, searchEnd)
                } else {
                    indexOf(buffer, needle, searchStart, searchEnd)
                }

            if (foundOffset >= 0) {
                val magicPos = windowStart + foundOffset.toULong()
                source.seek(magicPos)

                midBufferOffset =
                    if (backwards) {
                        foundOffset
                    } else {
                        foundOffset + needle.size
                    }

                return magicPos
            }

            midBufferOffset = null

            if (backwards) {
                val magicOverlap = if (needle.size > 1) (needle.size - 1).toULong() else 0uL
                if (cursor <= bounds.first) {
                    bounds = Pair(bounds.second, bounds.second)
                    break
                }
                val candidate = cursor + magicOverlap
                cursor =
                    if (candidate >= bufferSize.toULong()) {
                        maxOf(bounds.first, candidate - bufferSize.toULong())
                    } else {
                        bounds.first
                    }
                if (cursor <= bounds.first && candidate < bufferSize.toULong()) {
                    bounds = Pair(bounds.second, bounds.second)
                    break
                }
            } else {
                val magicOverlap = if (needle.size > 1) (needle.size - 1).toULong() else 0uL
                val nextCursor = cursor + (bufferSize.toULong() - magicOverlap)
                if (nextCursor >= bounds.second) {
                    bounds = Pair(bounds.second, bounds.second)
                    break
                } else {
                    cursor = nextCursor
                }
            }
        }
        return null
    }

    public companion object {
        internal fun indexOf(haystack: ByteArray, needle: ByteArray, start: Int, end: Int): Int {
            if (needle.isEmpty()) return start
            val limit = end - needle.size
            for (i in start..limit) {
                var matches = true
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) {
                        matches = false
                        break
                    }
                }
                if (matches) return i
            }
            return -1
        }

        internal fun lastIndexOf(haystack: ByteArray, needle: ByteArray, start: Int, end: Int): Int {
            if (needle.isEmpty()) return end
            val limit = end - needle.size
            for (i in limit downTo start) {
                var matches = true
                for (j in needle.indices) {
                    if (haystack[i + j] != needle[j]) {
                        matches = false
                        break
                    }
                }
                if (matches) return i
            }
            return -1
        }
    }
}

/**
 * A magic bytes finder with an optimistic guess that is tried before the inner finder begins searching.
 */
internal class OptimisticMagicFinder(
    private val inner: MagicFinder,
    private var initialGuess: Pair<ULong, Boolean>? = null,
) {
    internal fun repurpose(
        needle: ByteArray,
        bounds: Pair<ULong, ULong>,
        guess: Pair<ULong, Boolean>?,
    ): OptimisticMagicFinder {
        inner.repurpose(needle, bounds)
        this.initialGuess = guess
        return this
    }

    internal fun next(source: ZipSource): ULong? {
        val guess = initialGuess
        if (guess != null) {
            val (pos, mandatory) = guess
            source.seek(pos)
            val needle = inner.needle()
            val buf = ByteArray(needle.size)
            if (pos + needle.size.toULong() <= source.size()) {
                val readCount = source.read(buf, 0, needle.size)
                if (readCount == needle.size && needle.contentEquals(buf)) {
                    initialGuess = null
                    source.seek(pos)
                    return pos
                }
            }
            if (mandatory) {
                return null
            }
            initialGuess = null
        }
        return inner.next(source)
    }

    internal companion object {
        internal fun newEmpty(): OptimisticMagicFinder =
            OptimisticMagicFinder(MagicFinder(ByteArray(0), 0uL, 0uL))
    }
}

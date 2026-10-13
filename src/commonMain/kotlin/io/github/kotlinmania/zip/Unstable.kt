// port-lint: source unstable.rs
package io.github.kotlinmania.zip

/**
 * Extension for [ZipSink] to write little-endian integers.
 */
public fun ZipSink.writeU16Le(input: UShort) {
    val b =
        byteArrayOf(
            (input.toInt() and 0xFF).toByte(),
            ((input.toInt() shr 8) and 0xFF).toByte(),
        )
    write(b)
}

public fun ZipSink.writeU32Le(input: UInt) {
    val b =
        byteArrayOf(
            (input and 0xFFu).toByte(),
            ((input shr 8) and 0xFFu).toByte(),
            ((input shr 16) and 0xFFu).toByte(),
            ((input shr 24) and 0xFFu).toByte(),
        )
    write(b)
}

public fun ZipSink.writeU64Le(input: ULong) {
    val b = ByteArray(8)
    for (i in 0 until 8) {
        b[i] = ((input shr (i * 8)) and 0xFFuL).toByte()
    }
    write(b)
}

/**
 * Extension for [ZipSource] to read little-endian integers.
 */
public fun ZipSource.readU16Le(): UShort {
    val out = ByteArray(2)
    readExact(out, 0, 2)
    return ((out[0].toInt() and 0xFF) or ((out[1].toInt() and 0xFF) shl 8)).toUShort()
}

public fun ZipSource.readU32Le(): UInt {
    val out = ByteArray(4)
    readExact(out, 0, 4)
    val b0 = (out[0].toInt() and 0xFF).toUInt()
    val b1 = (out[1].toInt() and 0xFF).toUInt() shl 8
    val b2 = (out[2].toInt() and 0xFF).toUInt() shl 16
    val b3 = (out[3].toInt() and 0xFF).toUInt() shl 24
    return b0 or b1 or b2 or b3
}

public fun ZipSource.readU64Le(): ULong {
    val out = ByteArray(8)
    readExact(out, 0, 8)
    var result = 0uL
    for (i in 0 until 8) {
        result = result or ((out[i].toLong() and 0xFFL).toULong() shl (i * 8))
    }
    return result
}

/**
 * Converts a path to the ZIP format (forward-slash-delimited and normalized).
 *
 * Normalizes directory separators, resolves `.` and `..` segments, and ensures
 * forward slashes without leading slashes or Windows volume prefixes.
 */
public fun pathToString(path: String): String {
    if (path.isEmpty() || path == "." || path == "..") {
        return ""
    }

    var normalized = path.replace('\\', '/')
    if (normalized.length >= 2 && normalized[1] == ':' &&
        ((normalized[0] in 'a'..'z') || (normalized[0] in 'A'..'Z'))
    ) {
        normalized = normalized.substring(2)
    }

    if (normalized.startsWith("/")) {
        if (normalized.length == 1) {
            return "/"
        } else if (!normalized.substring(1).contains('/') &&
            !normalized.endsWith('.') &&
            !normalized.contains("//") &&
            !normalized.contains("/./") &&
            !normalized.contains("/../")
        ) {
            return normalized.substring(1)
        }
    } else if (!normalized.contains('/')) {
        return normalized
    }

    val components = mutableListOf<String>()
    val parts = normalized.split('/')
    for (part in parts) {
        when (part) {
            "", "." -> {
                // Ignore empty or current dir components
            }

            ".." -> {
                if (components.isNotEmpty()) {
                    components.removeAt(components.lastIndex)
                }
            }

            else -> {
                components.add(part)
            }
        }
    }

    return components.joinToString("/")
}

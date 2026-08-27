// port-lint: source path.rs
package io.github.kotlinmania.zip

/**
 * Simplify a path string by normalizing separators, removing current directory indicators (.),
 * resolving parent directory navigations (..), and rejecting absolute / prefixed paths.
 *
 * Returns null if the path attempts to navigate above the root or contains absolute/prefix components.
 */
public fun simplifiedComponents(input: String): List<String>? {
    val normalized = input.replace('\\', '/')
    if (normalized.startsWith("/") || (normalized.length >= 2 && normalized[1] == ':')) {
        return null
    }

    val parts = normalized.split('/')
    val out = mutableListOf<String>()

    for (part in parts) {
        when (part) {
            "", "." -> {
                // ignore empty and current dir components
            }
            ".." -> {
                if (out.isEmpty()) {
                    return null
                }
                out.removeAt(out.lastIndex)
            }
            else -> {
                out.add(part)
            }
        }
    }

    return out
}

public object Path {
    public fun sanitizePath(input: String): String {
        var s = input.replace('\\', '/')
        while (s.startsWith("/")) {
            s = s.substring(1)
        }
        return s
    }
}

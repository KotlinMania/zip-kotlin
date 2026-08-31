// port-lint: source zip/src/read/config.rs
package io.github.kotlinmania.zip.read

/**
 * The offset of the start of the archive from the beginning of the reader.
 */
public sealed class ArchiveOffset {
    /**
     * Try to detect the archive offset automatically.
     */
    public object Detect : ArchiveOffset()

    /**
     * Use the central directory length and offset to determine the start of the archive.
     */
    @Deprecated("use Detect instead")
    public object FromCentralDirectory : ArchiveOffset()

    /**
     * Specify a fixed archive offset.
     */
    public data class Known(
        public val offset: ULong,
    ) : ArchiveOffset()
}

/**
 * Configuration for reading ZIP archives.
 */
public data class Config(
    /**
     * An offset into the reader to use to find the start of the archive.
     */
    public val archiveOffset: ArchiveOffset = ArchiveOffset.Detect,
)

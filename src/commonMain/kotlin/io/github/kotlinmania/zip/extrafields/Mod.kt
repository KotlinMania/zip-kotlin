// port-lint: source extra_fields/mod.rs
package io.github.kotlinmania.zip.extrafields

/**
 * Marker interface to denote the place where this extra field has been stored.
 */
public interface ExtraFieldVersion

/**
 * Marker for extra fields specified in a local header.
 */
public object LocalHeaderVersion : ExtraFieldVersion

/**
 * Marker for extra fields specified in the central header.
 */
public object CentralHeaderVersion : ExtraFieldVersion

/**
 * Contains one extra field.
 */
public sealed class ExtraField {
    /**
     * NTFS extra field.
     */
    public data class NtfsField(public val ntfs: Ntfs) : ExtraField()

    /**
     * Extended timestamp.
     */
    public data class ExtendedTimestampField(public val timestamp: ExtendedTimestamp) : ExtraField()

    /**
     * Unicode extra field.
     */
    public data class UnicodeField(public val unicode: UnicodeExtraField) : ExtraField()
}

// port-lint: source spec.rs
package io.github.kotlinmania.zip

/**
 * The file size threshold at which a ZIP64 record becomes necessary.
 */
public const val ZIP64_BYTES_THR: ULong = 0xFFFFFFFFuL

/**
 * The entry count threshold at which a ZIP64 record becomes necessary.
 */
public const val ZIP64_ENTRY_THR: UShort = 0xFFFFu

/**
 * "Magic" header values used in the zip specification to locate metadata records.
 */
public object Spec {
    public const val LOCAL_FILE_HEADER_SIGNATURE: UInt = 0x04034B50u
    public const val CENTRAL_DIRECTORY_HEADER_SIGNATURE: UInt = 0x02014B50u
    public const val CENTRAL_DIRECTORY_END_SIGNATURE: UInt = 0x06054B50u
    public const val ZIP64_CENTRAL_DIRECTORY_END_SIGNATURE: UInt = 0x06064B50u
    public const val ZIP64_CENTRAL_DIRECTORY_END_LOCATOR_SIGNATURE: UInt = 0x07064B50u
    public const val DATA_DESCRIPTOR_SIGNATURE: UInt = 0x08074B50u

    public const val ZIP64_EXTRA_FIELD_TAG: UShort = 0x0001u
    public const val AES_EXTRA_FIELD_TAG: UShort = 0x9901u
    public const val EXTENDED_TIMESTAMP_EXTRA_FIELD_TAG: UShort = 0x5455u
    public const val INFOZIP_UNICODE_PATH_EXTRA_FIELD_TAG: UShort = 0x7075u
    public const val INFOZIP_UNICODE_COMMENT_EXTRA_FIELD_TAG: UShort = 0x6375u
}

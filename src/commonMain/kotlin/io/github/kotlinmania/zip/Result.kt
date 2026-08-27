// port-lint: source zip/src/result.rs
package io.github.kotlinmania.zip

/**
 * Error type for Zip archive operations.
 */
public sealed class ZipError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /**
     * An I/O error occurred during archive manipulation.
     */
    public class Io(
        message: String,
        cause: Throwable? = null,
    ) : ZipError("i/o error: $message", cause)

    /**
     * The archive is invalid or malformed.
     */
    public class InvalidArchive(
        message: String,
    ) : ZipError("invalid Zip archive: $message")

    /**
     * The archive uses an unsupported feature or compression format.
     */
    public class UnsupportedArchive(
        message: String,
    ) : ZipError("unsupported Zip archive: $message")

    /**
     * The specified file was not found in the archive.
     */
    public object FileNotFound : ZipError("specified file not found in archive")

    /**
     * The provided password was incorrect.
     */
    public object InvalidPassword : ZipError("The password provided is incorrect")

    public companion object {
        /**
         * Error message used when password is required to decrypt a file.
         */
        public const val PASSWORD_REQUIRED: String = "Password required to decrypt file"

        /**
         * Convert a [DateTimeRangeError] into a [ZipError.InvalidArchive].
         */
        public fun from(e: DateTimeRangeError): ZipError =
            InvalidArchive(e.message ?: "DateTime range error")

        /**
         * Convert a generic [Throwable] into a [ZipError.Io].
         */
        public fun from(e: Throwable): ZipError =
            when (e) {
                is ZipError -> e
                is DateTimeRangeError -> from(e)
                else -> Io(e.message ?: e.toString(), e)
            }
    }
}

/**
 * Error indicating that a date could not be represented within the bounds of the MS-DOS date range (1980-2107).
 */
public class DateTimeRangeError(
    message: String = "a date could not be represented within the bounds of the MS-DOS date range (1980-2107)",
) : Exception(message)

package com.pranicdoc.cph.common.http;

/**
 * A stored object could not be read back through {@link StorageObjectReader}. Callers translate it
 * into the exception that fits their own contract; {@link #reason()} says which failure it was and,
 * for {@link Reason#NOT_OK}, {@link #statusCode()} carries the storage response status.
 */
public class StorageReadException extends RuntimeException {

    public enum Reason { NOT_OK, IO, INTERRUPTED }

    private final Reason reason;
    private final int statusCode;

    private StorageReadException(Reason reason, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = statusCode;
    }

    public static StorageReadException notOk(int statusCode) {
        return new StorageReadException(Reason.NOT_OK, statusCode, "Storage returned " + statusCode, null);
    }

    public static StorageReadException io(Throwable cause) {
        return new StorageReadException(Reason.IO, 0, "Could not read the object from storage", cause);
    }

    public static StorageReadException interrupted(Throwable cause) {
        return new StorageReadException(Reason.INTERRUPTED, 0,
                "Interrupted while reading the object from storage", cause);
    }

    public Reason reason() {
        return reason;
    }

    /** The storage response status for {@link Reason#NOT_OK}; 0 otherwise. */
    public int statusCode() {
        return statusCode;
    }
}

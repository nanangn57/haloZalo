package storage;

public final class DuplicateKeyException extends RuntimeException {
    public DuplicateKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}

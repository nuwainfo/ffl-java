package io.fastfilelink.ffl;

public final class FFLOutputException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;
    public FFLOutputException(String message) {
        super(message);
    }

    public FFLOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}

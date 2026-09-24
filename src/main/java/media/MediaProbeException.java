package media;

public class MediaProbeException extends Exception {
    public MediaProbeException(String message) {
        super(message);
    }

    public MediaProbeException(String message, Throwable cause) {
        super(message, cause);
    }
}

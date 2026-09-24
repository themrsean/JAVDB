package media;

public record ProcessResult(
        int exitStatus,
        String output,
        String error,
        boolean timedOut) {
}

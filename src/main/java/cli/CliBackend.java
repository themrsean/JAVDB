package cli;

public interface CliBackend {
    default void markInteractiveStarted() {
    }

    default void markOneShotStarted() {
    }
}

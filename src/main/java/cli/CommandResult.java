package cli;

public record CommandResult(int exitStatus, boolean succeeded, String message) {
    public static final int SUCCESS = 0;
    public static final int USAGE_ERROR = 2;
    public static final int VALIDATION_ERROR = 3;
    public static final int NOT_FOUND = 4;
    public static final int DATABASE_ERROR = 5;
    public static final int IO_ERROR = 6;
    public static final int PARTIAL_FAILURE = 7;

    public static CommandResult success() {
        return new CommandResult(SUCCESS, true, "");
    }

    public static CommandResult usageError(String message) {
        return new CommandResult(USAGE_ERROR, false, message);
    }

    public static CommandResult validationError(String message) {
        return new CommandResult(VALIDATION_ERROR, false, message);
    }

    public static CommandResult notFound(String message) {
        return new CommandResult(NOT_FOUND, false, message);
    }

    public static CommandResult databaseError(String message) {
        return new CommandResult(DATABASE_ERROR, false, message);
    }

    public static CommandResult ioError(String message) {
        return new CommandResult(IO_ERROR, false, message);
    }

    public static CommandResult partialFailure(String message) {
        return new CommandResult(PARTIAL_FAILURE, false, message);
    }
}

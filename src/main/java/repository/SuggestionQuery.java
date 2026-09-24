package repository;

public final class SuggestionQuery {
    public static final int DEFAULT_LIMIT = 10;
    public static final int MAXIMUM_LIMIT = 100;

    private SuggestionQuery() {
    }

    public static int validateLimit(int limit) {
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException(
                    "Suggestion limit must be between 1 and "
                            + MAXIMUM_LIMIT + "."
            );
        }

        return limit;
    }
}

package repository;

import java.nio.file.Path;

public record UnassignedMediaFilter(
        String contains,
        Path directory,
        Integer width,
        Integer height,
        Integer minWidth,
        Integer minHeight,
        int limit,
        int offset) {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAXIMUM_LIMIT = 1_000;

    public UnassignedMediaFilter {
        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and " + MAXIMUM_LIMIT + "."
            );
        }

        if (offset < 0) {
            throw new IllegalArgumentException(
                    "Offset must not be negative."
            );
        }

        if (contains != null && contains.isBlank()) {
            contains = null;
        }

        if (directory != null) {
            directory = directory.toAbsolutePath().normalize();
        }
    }

    public static UnassignedMediaFilter firstPage() {
        return new UnassignedMediaFilter(
                null,
                null,
                null,
                null,
                null,
                null,
                DEFAULT_LIMIT,
                0
        );
    }
}

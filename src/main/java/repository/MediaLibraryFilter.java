package repository;

import java.nio.file.Path;

public record MediaLibraryFilter(
        String contains,
        Path directory,
        MediaLibraryAssignmentState assignmentState,
        Integer width,
        Integer height,
        Integer minWidth,
        Integer minHeight,
        MediaLibraryMetadataQuality metadataQuality,
        int limit,
        int offset) {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAXIMUM_LIMIT = 1_000;

    public MediaLibraryFilter {
        assignmentState = assignmentState == null
                ? MediaLibraryAssignmentState.ALL : assignmentState;
        metadataQuality = metadataQuality == null
                ? MediaLibraryMetadataQuality.ALL : metadataQuality;

        if (limit < 1 || limit > MAXIMUM_LIMIT) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and " + MAXIMUM_LIMIT + "."
            );
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Page offset must not be negative.");
        }
        validateDimension(width);
        validateDimension(height);
        validateDimension(minWidth);
        validateDimension(minHeight);
    }

    public static MediaLibraryFilter firstPage() {
        return new MediaLibraryFilter(
                null, null, MediaLibraryAssignmentState.ALL,
                null, null, null, null,
                MediaLibraryMetadataQuality.ALL, DEFAULT_LIMIT, 0
        );
    }

    private static void validateDimension(Integer value) {
        if (value != null && value < 1) {
            throw new IllegalArgumentException(
                    "Dimension filters must be positive whole numbers."
            );
        }
    }
}

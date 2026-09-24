package service;

import repository.UnassignedMediaFilter;

import java.nio.file.Path;

public record ReviewQueueFilter(
        String contains,
        Path directory,
        Integer width,
        Integer height,
        Integer minWidth,
        Integer minHeight,
        int limit,
        int offset,
        ReviewMatchStatusFilter statusFilter) {

    public ReviewQueueFilter {
        if (statusFilter == null) {
            statusFilter = ReviewMatchStatusFilter.ALL;
        }
    }

    public static ReviewQueueFilter firstPage() {
        return new ReviewQueueFilter(
                null,
                null,
                null,
                null,
                null,
                null,
                UnassignedMediaFilter.DEFAULT_LIMIT,
                0,
                ReviewMatchStatusFilter.ALL
        );
    }

    UnassignedMediaFilter toUnassignedMediaFilter() {
        return new UnassignedMediaFilter(
                contains,
                directory,
                width,
                height,
                minWidth,
                minHeight,
                limit,
                offset
        );
    }
}

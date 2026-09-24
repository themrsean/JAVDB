package service;

import java.util.List;
import java.util.Objects;

public record ReviewQueuePage(
        ReviewQueueFilter filter,
        List<ReviewQueueItem> items,
        List<ReviewDetails> details,
        int basePageSize) {

    public ReviewQueuePage {
        Objects.requireNonNull(filter, "Filter must not be null");
        items = List.copyOf(Objects.requireNonNull(
                items,
                "Items must not be null"
        ));
        details = List.copyOf(Objects.requireNonNull(
                details,
                "Details must not be null"
        ));
    }
}

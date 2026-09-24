package service;

import repository.MediaLibraryFilter;

import java.util.List;
import java.util.Objects;

public record MediaLibraryPage(
        MediaLibraryFilter filter,
        List<MediaLibraryRow> rows,
        boolean hasNextPage) {

    public MediaLibraryPage {
        Objects.requireNonNull(filter, "Filter must not be null");
        rows = List.copyOf(Objects.requireNonNull(rows, "Rows must not be null"));
    }
}

package service;

import java.util.List;
import java.util.Objects;

public record MediaRenameBatchResult(List<MediaRenameResult> results) {
    public MediaRenameBatchResult {
        results = List.copyOf(Objects.requireNonNull(
                results,
                "Results must not be null"
        ));
    }
}

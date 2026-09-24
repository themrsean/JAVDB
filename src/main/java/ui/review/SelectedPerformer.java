package ui.review;

import java.util.Objects;
import java.util.UUID;

public record SelectedPerformer(UUID id, String displayName) {
    public SelectedPerformer {
        Objects.requireNonNull(id, "Performer ID must not be null");
        displayName = displayName == null ? "" : displayName;
    }
}

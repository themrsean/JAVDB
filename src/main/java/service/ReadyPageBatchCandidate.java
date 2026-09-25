package service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public record ReadyPageBatchCandidate(
        UUID mediaId,
        Path path) {

    public ReadyPageBatchCandidate {
        Objects.requireNonNull(mediaId, "Media ID must not be null");
        Objects.requireNonNull(path, "Media path must not be null");
    }
}

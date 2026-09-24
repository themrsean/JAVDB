package service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MediaRenameResult(
        UUID mediaId,
        UUID sceneId,
        Path originalPath,
        Path finalPath,
        MediaRenameStatus status,
        List<String> warnings,
        String error) {

    public MediaRenameResult {
        Objects.requireNonNull(mediaId, "Media ID must not be null");
        Objects.requireNonNull(sceneId, "Scene ID must not be null");
        Objects.requireNonNull(originalPath, "Original path must not be null");
        Objects.requireNonNull(finalPath, "Final path must not be null");
        Objects.requireNonNull(status, "Status must not be null");
        warnings = List.copyOf(Objects.requireNonNull(
                warnings,
                "Warnings must not be null"
        ));
        error = Objects.requireNonNull(error, "Error must not be null");
    }
}

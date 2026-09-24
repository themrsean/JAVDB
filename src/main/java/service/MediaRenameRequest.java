package service;

import java.util.Objects;
import java.util.UUID;

public record MediaRenameRequest(
        UUID mediaFileId,
        UUID sceneId,
        UUID movieOverrideId) {

    public MediaRenameRequest {
        Objects.requireNonNull(
                mediaFileId,
                "Media file ID must not be null"
        );
    }
}

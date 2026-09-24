package service;

import java.nio.file.Path;
import java.util.UUID;

public record BatchSceneCreationFileResult(
        int position,
        UUID mediaFileId,
        Path path,
        String title,
        UUID sceneId,
        BatchSceneCreationStatus status,
        String error) {
}

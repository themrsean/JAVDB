package service;

import java.nio.file.Path;
import java.util.UUID;

public record AutoIndexFileResult(
        AutoIndexStatus status,
        UUID mediaFileId,
        Path path,
        UUID sceneId,
        String title,
        String warnings,
        String error) {
}

package service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

public record MediaScanFileResult(
        MediaScanStatus status,
        Path path,
        UUID mediaId,
        long fileSize,
        long lastModifiedMillis,
        int width,
        int height,
        Duration duration,
        String contentHash,
        UUID duplicateMediaId,
        Path duplicatePath,
        String error) {
}

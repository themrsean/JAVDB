package service;

import java.nio.file.Path;
import java.util.UUID;

public record MediaVerificationFileResult(
        MediaVerificationStatus status,
        UUID mediaId,
        Path path,
        long storedFileSize,
        long actualFileSize,
        long storedLastModifiedMillis,
        long actualLastModifiedMillis,
        String error) {
}

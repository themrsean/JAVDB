package model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MediaLocation(
        UUID id,
        Path path,
        boolean enabled,
        boolean recursive,
        Instant createdAt,
        Instant updatedAt,
        Instant lastScanStartedAt,
        Instant lastScanCompletedAt,
        MediaLocationScanStatus lastScanStatus,
        String lastScanMessage,
        int lastDiscoveredCount,
        int lastNewCount,
        int lastUpdatedCount,
        int lastUnchangedCount,
        int lastMissingCount,
        int lastFailedCount) {

    public MediaLocation {
        Objects.requireNonNull(id, "Media location ID must not be null");
        Objects.requireNonNull(path, "Media location path must not be null");
        Objects.requireNonNull(createdAt, "Created timestamp must not be null");
        Objects.requireNonNull(updatedAt, "Updated timestamp must not be null");
        Objects.requireNonNull(
                lastScanStatus,
                "Last scan status must not be null"
        );

        path = path.toAbsolutePath().normalize();
    }
}

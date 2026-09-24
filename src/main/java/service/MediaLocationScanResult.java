package service;

import model.MediaLocationScanStatus;

import java.time.Instant;
import java.util.Objects;

public record MediaLocationScanResult(
        Instant startedAt,
        Instant completedAt,
        MediaLocationScanStatus status,
        String message,
        int discoveredCount,
        int newCount,
        int updatedCount,
        int unchangedCount,
        int missingCount,
        int failedCount) {

    public MediaLocationScanResult {
        Objects.requireNonNull(status, "Scan status must not be null");
    }
}

package service;

import java.nio.file.Path;
import java.util.UUID;

public record MediaLocationScanProgress(
        MediaLocationScanPhase phase,
        UUID locationId,
        Path locationPath,
        Path currentFile,
        int locationIndex,
        int totalLocations,
        int discoveredFiles,
        int processedFiles,
        int newFiles,
        int updatedFiles,
        int unchangedFiles,
        int missingFiles,
        int failedFiles) {
}

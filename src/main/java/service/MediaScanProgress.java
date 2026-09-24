package service;

import java.nio.file.Path;

public record MediaScanProgress(
        MediaLocationScanPhase phase,
        Path currentFile,
        MediaScanSummary summary) {
}

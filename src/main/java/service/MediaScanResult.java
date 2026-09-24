package service;

import java.util.List;

public record MediaScanResult(
        List<MediaScanFileResult> files,
        MediaScanSummary summary) {
}

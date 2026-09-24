package service;

import java.util.List;

public record MediaLocationBatchScanResult(
        List<MediaLocationScanResult> locationResults) {

    public int newCount() {
        return locationResults.stream()
                .mapToInt(MediaLocationScanResult::newCount)
                .sum();
    }

    public int updatedCount() {
        return locationResults.stream()
                .mapToInt(MediaLocationScanResult::updatedCount)
                .sum();
    }

    public int unchangedCount() {
        return locationResults.stream()
                .mapToInt(MediaLocationScanResult::unchangedCount)
                .sum();
    }

    public int failedCount() {
        return locationResults.stream()
                .mapToInt(MediaLocationScanResult::failedCount)
                .sum();
    }
}

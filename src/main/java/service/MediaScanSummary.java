package service;

public record MediaScanSummary(
        int filesDiscovered,
        int added,
        int updated,
        int unchanged,
        int duplicateContentFiles,
        int failed,
        int dryRunAdditions,
        int dryRunUpdates) {
}

package service;

import java.util.Set;

public record MediaLocationScanOptions(
        boolean hashingEnabled,
        boolean dryRun,
        boolean failFast,
        Set<String> additionalExtensions) {

    public static MediaLocationScanOptions guiDefaults() {
        return new MediaLocationScanOptions(false, false, false, Set.of());
    }
}

package service;

public record MediaVerificationRequest(
        boolean refreshChangedFiles,
        boolean dryRun,
        boolean failFast) {
}

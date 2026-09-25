package service;

import java.util.List;
import java.util.Objects;

public record ReadyPageBatchResult(
        ReadyPageBatchPreflight preflight,
        List<ReadyPageBatchRowResult> rows) {

    public ReadyPageBatchResult {
        Objects.requireNonNull(preflight, "Preflight must not be null");
        rows = List.copyOf(Objects.requireNonNull(rows, "Rows must not be null"));
    }

    public int createdWithoutRename() {
        return count(ReadyPageBatchOutcome.CREATED_WITHOUT_RENAME);
    }

    public int createdAndRenamed() {
        return count(ReadyPageBatchOutcome.CREATED_AND_RENAMED);
    }

    public int renameFailures() {
        return count(ReadyPageBatchOutcome.CREATED_RENAME_FAILED_NEEDS_REVIEW);
    }

    public int skippedNoLongerReady() {
        return count(ReadyPageBatchOutcome.SKIPPED_NO_LONGER_READY);
    }

    public int skippedAlreadyAssigned() {
        return count(ReadyPageBatchOutcome.SKIPPED_ALREADY_ASSIGNED);
    }

    public int missingMedia() {
        return count(ReadyPageBatchOutcome.SKIPPED_MEDIA_MISSING);
    }

    public int otherFailures() {
        return count(ReadyPageBatchOutcome.FAILED);
    }

    private int count(ReadyPageBatchOutcome outcome) {
        return (int) rows.stream()
                .filter(row -> row.outcome() == outcome)
                .count();
    }
}

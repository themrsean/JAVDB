package service;

import java.util.List;
import java.util.Objects;

public record ReadyPageBatchPreflight(
        int displayedRows,
        int initialReadyCandidates,
        List<ReadyPageBatchCandidate> eligibleCandidates,
        List<ReadyPageBatchRowResult> excludedRows) {

    public ReadyPageBatchPreflight {
        eligibleCandidates = List.copyOf(Objects.requireNonNull(
                eligibleCandidates,
                "Eligible candidates must not be null"
        ));
        excludedRows = List.copyOf(Objects.requireNonNull(
                excludedRows,
                "Excluded rows must not be null"
        ));
    }

    public int eligibleCount() {
        return eligibleCandidates.size();
    }

    public long excludedNoLongerReady() {
        return count(ReadyPageBatchOutcome.SKIPPED_NO_LONGER_READY);
    }

    public long excludedAlreadyAssigned() {
        return count(ReadyPageBatchOutcome.SKIPPED_ALREADY_ASSIGNED);
    }

    public long excludedMissing() {
        return count(ReadyPageBatchOutcome.SKIPPED_MEDIA_MISSING);
    }

    public long preflightFailures() {
        return count(ReadyPageBatchOutcome.FAILED);
    }

    private long count(ReadyPageBatchOutcome outcome) {
        return excludedRows.stream()
                .filter(row -> row.outcome() == outcome)
                .count();
    }
}

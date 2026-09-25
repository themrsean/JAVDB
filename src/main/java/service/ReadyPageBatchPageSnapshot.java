package service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ReadyPageBatchPageSnapshot(
        int displayedRows,
        List<ReadyPageBatchCandidate> readyCandidates) {

    public ReadyPageBatchPageSnapshot {
        if (displayedRows < 0) {
            throw new IllegalArgumentException(
                    "Displayed row count must not be negative."
            );
        }
        final List<ReadyPageBatchCandidate> supplied = Objects.requireNonNull(
                readyCandidates,
                "READY candidates must not be null"
        );
        final Map<UUID, ReadyPageBatchCandidate> unique = new LinkedHashMap<>();
        for (ReadyPageBatchCandidate candidate : supplied) {
            unique.putIfAbsent(candidate.mediaId(), candidate);
        }
        readyCandidates = List.copyOf(unique.values());
    }

    public static ReadyPageBatchPageSnapshot from(
            List<ReviewQueueItem> displayedRows) {

        Objects.requireNonNull(displayedRows, "Displayed rows must not be null");
        final Map<UUID, ReadyPageBatchCandidate> candidates =
                new LinkedHashMap<>();

        for (ReviewQueueItem row : displayedRows) {
            if (row != null && row.matchStatus() == FilenameMatchStatus.READY) {
                candidates.putIfAbsent(
                        row.mediaId(),
                        new ReadyPageBatchCandidate(row.mediaId(), row.path())
                );
            }
        }

        return new ReadyPageBatchPageSnapshot(
                displayedRows.size(),
                List.copyOf(candidates.values())
        );
    }

    public int initialReadyCandidates() {
        return readyCandidates.size();
    }
}

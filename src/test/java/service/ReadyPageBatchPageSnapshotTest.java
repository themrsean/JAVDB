package service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class ReadyPageBatchPageSnapshotTest {
    private static final UUID READY_ID = UUID.fromString(
            "11111111-2020-1111-2020-111111111111"
    );
    private static final UUID HIDDEN_ID = UUID.fromString(
            "22222222-2020-2222-2020-222222222222"
    );

    @Test
    @DisplayName("Snapshot keeps only displayed READY IDs and deduplicates them")
    void snapshotKeepsOnlyDisplayedReadyIdsAndDeduplicatesThem() {
        final ReviewQueueItem ready = row(READY_ID, FilenameMatchStatus.READY);
        final ReadyPageBatchPageSnapshot snapshot =
                ReadyPageBatchPageSnapshot.from(List.of(
                        ready,
                        row(HIDDEN_ID, FilenameMatchStatus.UNRESOLVED),
                        ready
                ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(3, snapshot.displayedRows()),
                () -> Assertions.assertEquals(
                        1,
                        snapshot.initialReadyCandidates()
                ),
                () -> Assertions.assertEquals(
                        List.of(READY_ID),
                        snapshot.readyCandidates().stream()
                                .map(ReadyPageBatchCandidate::mediaId)
                                .toList()
                )
        );
    }

    private ReviewQueueItem row(UUID id, FilenameMatchStatus status) {
        return new ReviewQueueItem(
                id, Path.of(id + ".mp4"), id + ".mp4", "", status,
                "Title", "", "", "", "", "", "", "",
                "", "", 0
        );
    }
}

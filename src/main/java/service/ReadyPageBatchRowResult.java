package service;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public record ReadyPageBatchRowResult(
        UUID mediaId,
        Path path,
        ReadyPageBatchOutcome outcome,
        SceneReviewSaveStatus saveStatus,
        String message) {

    public ReadyPageBatchRowResult {
        Objects.requireNonNull(mediaId, "Media ID must not be null");
        Objects.requireNonNull(path, "Media path must not be null");
        Objects.requireNonNull(outcome, "Outcome must not be null");
        message = message == null ? "" : message;
    }

    public boolean successful() {
        return outcome == ReadyPageBatchOutcome.CREATED_WITHOUT_RENAME
                || outcome == ReadyPageBatchOutcome.CREATED_AND_RENAMED;
    }
}

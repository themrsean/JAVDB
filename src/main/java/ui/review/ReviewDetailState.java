package ui.review;

import service.ReviewDetails;
import service.SceneReviewQueueItem;

import java.nio.file.Path;
import java.util.UUID;

record ReviewDetailState(
        UUID mediaId,
        Path mediaPath,
        String parseStatus,
        String matchStatus) {

    private static final String EXISTING_SCENE_STATUS =
            "N/A — existing Scene";

    static ReviewDetailState empty() {
        return new ReviewDetailState(null, null, "", "");
    }

    static ReviewDetailState fromUnassigned(ReviewDetails details) {
        return details == null ? empty() : new ReviewDetailState(
                details.mediaId(),
                details.path(),
                details.parseStatus().name(),
                details.matchStatus().name()
        );
    }

    static ReviewDetailState fromExistingScene(SceneReviewQueueItem item) {
        return item == null ? empty() : new ReviewDetailState(
                item.mediaId(),
                item.mediaPath(),
                EXISTING_SCENE_STATUS,
                EXISTING_SCENE_STATUS
        );
    }

    String mediaIdText() {
        return mediaId == null ? "" : mediaId.toString();
    }

    String mediaPathText() {
        return mediaPath == null ? "" : mediaPath.toString();
    }

    boolean emptyMedia() {
        return mediaId == null && mediaPath == null;
    }
}

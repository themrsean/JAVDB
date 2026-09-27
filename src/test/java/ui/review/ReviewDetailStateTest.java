package ui.review;

import media.FilenameParseStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.CanonicalRenameDisplay;
import service.FilenameMatchStatus;
import service.ReviewDetails;
import service.SceneReviewQueueItem;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class ReviewDetailStateTest {
    private static final UUID UNASSIGNED_MEDIA_ID =
            UUID.fromString("11111111-eeee-1111-eeee-111111111111");
    private static final UUID SCENE_ID =
            UUID.fromString("22222222-eeee-2222-eeee-222222222222");
    private static final UUID SCENE_MEDIA_ID =
            UUID.fromString("33333333-eeee-3333-eeee-333333333333");
    private static final Path UNASSIGNED_PATH = Path.of("/video/file-a.mp4");
    private static final Path SCENE_PATH = Path.of("/video/scene-b.mp4");

    @Test
    @DisplayName("Active detail source follows review tab and restores selection")
    void activeDetailSourceFollowsReviewTab() {
        final ReviewDetailState unassigned = ReviewDetailState.fromUnassigned(
                unassignedDetails()
        );
        final ReviewDetailState existing = ReviewDetailState.fromExistingScene(
                sceneItem(SCENE_MEDIA_ID, SCENE_PATH)
        );
        final ReviewDetailState restored = ReviewDetailState.fromUnassigned(
                unassignedDetails()
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        UNASSIGNED_MEDIA_ID.toString(),
                        unassigned.mediaIdText()
                ),
                () -> Assertions.assertEquals(
                        FilenameParseStatus.VALID.name(),
                        unassigned.parseStatus()
                ),
                () -> Assertions.assertEquals(
                        SCENE_MEDIA_ID.toString(),
                        existing.mediaIdText()
                ),
                () -> Assertions.assertEquals(
                        SCENE_PATH.toString(),
                        existing.mediaPathText()
                ),
                () -> Assertions.assertNotEquals(
                        UNASSIGNED_MEDIA_ID.toString(),
                        existing.mediaIdText()
                ),
                () -> Assertions.assertEquals(
                        "N/A — existing Scene",
                        existing.parseStatus()
                ),
                () -> Assertions.assertEquals(
                        "N/A — existing Scene",
                        existing.matchStatus()
                ),
                () -> Assertions.assertEquals(
                        UNASSIGNED_MEDIA_ID.toString(),
                        restored.mediaIdText()
                )
        );
    }

    @Test
    @DisplayName("Missing selections and missing Scene media clear active details")
    void missingSelectionsAndMediaClearDetails() {
        final ReviewDetailState noSelection =
                ReviewDetailState.fromExistingScene(null);
        final ReviewDetailState noMedia = ReviewDetailState.fromExistingScene(
                sceneItem(null, null)
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(noSelection.emptyMedia()),
                () -> Assertions.assertEquals("", noSelection.parseStatus()),
                () -> Assertions.assertTrue(noMedia.emptyMedia()),
                () -> Assertions.assertEquals("", noMedia.mediaIdText()),
                () -> Assertions.assertEquals("", noMedia.mediaPathText())
        );
    }

    private SceneReviewQueueItem sceneItem(UUID mediaId, Path mediaPath) {
        return new SceneReviewQueueItem(
                SCENE_ID,
                "Scene B",
                model.VerificationStatus.NEEDS_REVIEW,
                null,
                "Brazzers",
                "",
                mediaId,
                mediaPath,
                mediaPath == null ? "(no media associated)"
                        : mediaPath.getFileName().toString()
        );
    }

    private ReviewDetails unassignedDetails() {
        return new ReviewDetails(
                UNASSIGNED_MEDIA_ID,
                UNASSIGNED_PATH,
                "file-a.mp4",
                "/video",
                10L,
                1L,
                1280,
                720,
                "PT1S",
                "",
                FilenameParseStatus.VALID,
                null,
                List.of(),
                "Scene A",
                "",
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                "",
                "",
                "",
                "",
                "",
                "",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                FilenameMatchStatus.READY,
                new CanonicalRenameDisplay(
                        "READY",
                        "file-a.mp4",
                        "scene-a.mp4",
                        Path.of("/video/scene-a.mp4"),
                        false,
                        false,
                        false,
                        List.of(),
                        ""
                )
        );
    }
}

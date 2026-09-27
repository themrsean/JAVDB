package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
import model.Publisher;
import model.Scene;
import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaFileRepository;
import repository.PublisherRepository;
import repository.SceneRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

class SceneReviewQueueServiceTest {
    private static final String DATABASE_FILE_NAME =
            "scene-review-queue-service-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-7777-1111-7777-111111111111");
    private static final int PAGE_LIMIT = 10;
    private static final int PAGE_OFFSET = 0;
    private static final UUID MEDIA_ID =
            UUID.fromString("22222222-7777-2222-7777-222222222222");

    @TempDir
    Path temporaryDirectory;

    private SceneRepository sceneRepository;
    private MediaFileRepository mediaFileRepository;
    private SceneReviewQueueService service;
    private Publisher publisher;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        sceneRepository = new SceneRepository(databaseManager);
        mediaFileRepository = new MediaFileRepository(databaseManager);
        service = new SceneReviewQueueService(sceneRepository);
        publisher = new Publisher(PUBLISHER_ID, "Publisher", List.of());
        new PublisherRepository(databaseManager).insert(publisher);
    }

    @Test
    @DisplayName("Queue row exposes its single media identity and filename")
    void rowExposesSingleMediaIdentityAndFilename() throws Exception {
        final Path mediaPath = temporaryDirectory.resolve("scene-video.mp4");
        final MediaFile media = new MediaFile(
                MEDIA_ID,
                mediaPath,
                10L,
                null,
                Duration.ofSeconds(1),
                1280,
                720
        );
        mediaFileRepository.insert(media);
        sceneRepository.insert(scene(
                "With Media",
                VerificationStatus.UNVERIFIED,
                List.of(media)
        ));

        final SceneReviewQueueItem item = service.loadReviewScenes(
                PAGE_LIMIT,
                PAGE_OFFSET
        ).getFirst();

        Assertions.assertAll(
                () -> Assertions.assertEquals(MEDIA_ID, item.mediaId()),
                () -> Assertions.assertEquals(mediaPath, item.mediaPath()),
                () -> Assertions.assertEquals("scene-video.mp4", item.filename())
        );
    }

    @Test
    @DisplayName("Queue row handles a diagnostic scene with no media")
    void rowHandlesSceneWithNoMedia() throws Exception {
        sceneRepository.insert(scene("No Media", VerificationStatus.NEEDS_REVIEW));

        final SceneReviewQueueItem item = service.loadReviewScenes(
                PAGE_LIMIT,
                PAGE_OFFSET
        ).getFirst();

        Assertions.assertAll(
                () -> Assertions.assertNull(item.mediaId()),
                () -> Assertions.assertNull(item.mediaPath()),
                () -> Assertions.assertEquals(
                        "(no media associated)",
                        item.filename()
                )
        );
    }

    @Test
    @DisplayName("Returns unverified and needs-review scenes")
    void returnsUnverifiedAndNeedsReviewScenes() throws Exception {
        sceneRepository.insert(scene("Unverified", VerificationStatus.UNVERIFIED));
        sceneRepository.insert(scene("Needs Review", VerificationStatus.NEEDS_REVIEW));
        sceneRepository.insert(scene("Verified", VerificationStatus.VERIFIED));

        final List<SceneReviewQueueItem> items =
                service.loadReviewScenes(PAGE_LIMIT, PAGE_OFFSET);

        Assertions.assertEquals(
                List.of("Needs Review", "Unverified"),
                items.stream().map(SceneReviewQueueItem::title).toList()
        );
    }

    @Test
    @DisplayName("Constructor rejects null repository")
    void constructorRejectsNullRepository() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new SceneReviewQueueService(null)
        );
    }

    private Scene scene(String title, VerificationStatus status) {
        return scene(title, status, List.of());
    }

    private Scene scene(
            String title,
            VerificationStatus status,
            List<MediaFile> files) {

        return new Scene(
                UUID.randomUUID(),
                title,
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                files,
                status
        );
    }
}

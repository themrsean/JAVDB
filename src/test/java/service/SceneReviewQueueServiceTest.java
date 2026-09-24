package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.Publisher;
import model.Scene;
import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.PublisherRepository;
import repository.SceneRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class SceneReviewQueueServiceTest {
    private static final String DATABASE_FILE_NAME =
            "scene-review-queue-service-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-7777-1111-7777-111111111111");
    private static final int PAGE_LIMIT = 10;
    private static final int PAGE_OFFSET = 0;

    @TempDir
    Path temporaryDirectory;

    private SceneRepository sceneRepository;
    private SceneReviewQueueService service;
    private Publisher publisher;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        sceneRepository = new SceneRepository(databaseManager);
        service = new SceneReviewQueueService(sceneRepository);
        publisher = new Publisher(PUBLISHER_ID, "Publisher", List.of());
        new PublisherRepository(databaseManager).insert(publisher);
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
                List.of(),
                status
        );
    }
}

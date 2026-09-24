package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class SceneReviewWorkflowServiceTest {
    private static final String DATABASE_FILE_NAME =
            "scene-review-workflow-service-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-9999-1111-9999-111111111111");
    private static final UUID SERIES_ID =
            UUID.fromString("22222222-9999-2222-9999-222222222222");
    private static final UUID PERFORMER_ID =
            UUID.fromString("33333333-9999-3333-9999-333333333333");
    private static final UUID SECOND_PERFORMER_ID =
            UUID.fromString("44444444-9999-4444-9999-444444444444");
    private static final UUID MEDIA_ID =
            UUID.fromString("55555555-9999-5555-9999-555555555555");
    private static final UUID SECOND_MEDIA_ID =
            UUID.fromString("66666666-9999-6666-9999-666666666666");
    private static final UUID MOVIE_ID =
            UUID.fromString("77777777-9999-7777-9999-777777777777");
    private static final UUID EXISTING_SCENE_ID =
            UUID.fromString("88888888-9999-8888-9999-888888888888");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final long FILE_SIZE = 5L;
    private static final int WIDTH = 1_920;
    private static final int HEIGHT = 1_080;
    private static final long LAST_MODIFIED_MILLIS = 123L;

    @TempDir
    Path temporaryDirectory;

    private MediaFileRepository mediaFileRepository;
    private SceneRepository sceneRepository;
    private MovieRepository movieRepository;
    private MediaAssignmentRepository assignmentRepository;
    private SceneReviewWorkflowService service;
    private Path mediaPath;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        mediaFileRepository = new MediaFileRepository(databaseManager);
        sceneRepository = new SceneRepository(databaseManager);
        movieRepository = new MovieRepository(databaseManager);
        assignmentRepository = new MediaAssignmentRepository(databaseManager);
        final CatalogService catalogService = new CatalogService(
                new PublisherRepository(databaseManager),
                new PerformerRepository(databaseManager),
                new SeriesRepository(databaseManager),
                mediaFileRepository,
                sceneRepository,
                new SearchRepository(databaseManager),
                movieRepository
        );
        final MediaRenameService renameService = new MediaRenameService(
                mediaFileRepository,
                sceneRepository,
                assignmentRepository,
                new OriginalMovieSelector(movieRepository)
        );
        service = new SceneReviewWorkflowService(
                catalogService,
                assignmentRepository,
                sceneRepository,
                movieRepository,
                renameService
        );

        final Publisher publisher =
                new Publisher(PUBLISHER_ID, "Publisher", List.of());
        final Series series = new Series(SERIES_ID, "Series", publisher);
        final Performer performer = performer(PERFORMER_ID, "Alice");
        final Performer secondPerformer =
                performer(SECOND_PERFORMER_ID, "Bea");
        mediaPath = temporaryDirectory.resolve("old.mp4");
        final Path secondMediaPath = temporaryDirectory.resolve("second.mp4");

        Files.writeString(mediaPath, "video");
        Files.writeString(secondMediaPath, "video");
        new PublisherRepository(databaseManager).insert(publisher);
        new SeriesRepository(databaseManager).insert(series);
        new PerformerRepository(databaseManager).insert(performer);
        new PerformerRepository(databaseManager).insert(secondPerformer);
        mediaFileRepository.insert(mediaFile(MEDIA_ID, mediaPath));
        mediaFileRepository.insert(mediaFile(SECOND_MEDIA_ID, secondMediaPath));

        final Scene existingScene = new Scene(
                EXISTING_SCENE_ID,
                "Existing",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(performer),
                List.of(),
                VerificationStatus.UNVERIFIED
        );
        sceneRepository.insert(existingScene);
        movieRepository.insert(new Movie(
                MOVIE_ID,
                "Movie",
                LocalDate.of(2020, 1, 1),
                publisher,
                List.of(existingScene),
                false,
                List.of()
        ));
    }

    @Test
    @DisplayName("Constructor rejects null dependencies")
    void constructorRejectsNullDependencies() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new SceneReviewWorkflowService(
                        null,
                        assignmentRepository,
                        sceneRepository,
                        movieRepository,
                        successfulRenameService()
                )
        );
    }

    @Test
    @DisplayName("New scene saves without rename and becomes verified")
    void newSceneSavesWithoutRenameAndBecomesVerified() throws Exception {
        final SceneReviewSaveResult result = service.save(
                createRequest(SceneReviewRenameChoice.DO_NOT_RENAME)
        );

        final Scene saved = sceneRepository.findById(result.sceneId())
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewSaveStatus.CREATED,
                        result.status()
                ),
                () -> Assertions.assertEquals(
                        VerificationStatus.VERIFIED,
                        saved.getVerificationStatus()
                ),
                () -> Assertions.assertEquals(
                        List.of(PERFORMER_ID),
                        saved.getPerformers().stream()
                                .map(Performer::getId)
                                .toList()
                ),
                () -> Assertions.assertEquals(
                        SERIES_ID,
                        saved.getSeries().getId()
                ),
                () -> Assertions.assertFalse(
                        assignmentRepository.isUnassigned(MEDIA_ID)
                )
        );
    }

    @Test
    @DisplayName("Movie selection appends new scene and preserves existing order")
    void movieSelectionAppendsNewSceneAndPreservesExistingOrder()
            throws Exception {

        final SceneReviewSaveResult result = service.save(
                createRequest(SceneReviewRenameChoice.DO_NOT_RENAME)
        );

        final Movie movie = movieRepository.findById(MOVIE_ID).orElseThrow();

        Assertions.assertEquals(
                List.of(EXISTING_SCENE_ID, result.sceneId()),
                movie.getScenes().stream().map(Scene::getId).toList()
        );
    }

    @Test
    @DisplayName("Existing scene metadata and performers can be updated")
    void existingSceneMetadataAndPerformersCanBeUpdated() throws Exception {
        final SceneReviewSaveResult result = service.save(new SceneReviewSaveRequest(
                new SceneReviewDraft(
                        SceneReviewMode.EDIT_EXISTING_SCENE,
                        EXISTING_SCENE_ID,
                        List.of(),
                        "Updated",
                        LocalDate.of(2026, 7, 16),
                        "ABC-001",
                        "1",
                        "2",
                        PUBLISHER_ID,
                        SERIES_ID,
                        null,
                        null,
                        List.of(SECOND_PERFORMER_ID),
                        VerificationStatus.NEEDS_REVIEW,
                        List.of()
                ),
                SceneReviewRenameChoice.DO_NOT_RENAME
        ));

        final Scene scene = sceneRepository.findById(EXISTING_SCENE_ID)
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewSaveStatus.UPDATED,
                        result.status()
                ),
                () -> Assertions.assertEquals("Updated", scene.getTitle()),
                () -> Assertions.assertEquals(
                        VerificationStatus.NEEDS_REVIEW,
                        scene.getVerificationStatus()
                ),
                () -> Assertions.assertEquals(
                        List.of(SECOND_PERFORMER_ID),
                        scene.getPerformers().stream()
                                .map(Performer::getId)
                                .toList()
                )
        );
    }

    @Test
    @DisplayName("Assigned media cannot create another scene")
    void assignedMediaCannotCreateAnotherScene() throws Exception {
        service.save(createRequest(SceneReviewRenameChoice.DO_NOT_RENAME));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.save(createRequest(
                        SceneReviewRenameChoice.DO_NOT_RENAME
                ))
        );
    }

    @Test
    @DisplayName("Unknown related records are rejected")
    void unknownRelatedRecordsAreRejected() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.save(new SceneReviewSaveRequest(
                        new SceneReviewDraft(
                                SceneReviewMode.CREATE_FROM_MEDIA,
                                null,
                                List.of(MEDIA_ID),
                                "Scene Title",
                                null,
                                null,
                                null,
                                null,
                                UNKNOWN_ID,
                                SERIES_ID,
                                null,
                                null,
                                List.of(PERFORMER_ID),
                                VerificationStatus.VERIFIED,
                                List.of()
                        ),
                        SceneReviewRenameChoice.DO_NOT_RENAME
                ))
        );
    }

    @Test
    @DisplayName("Save and rename updates stored media path")
    void saveAndRenameUpdatesStoredMediaPath() throws Exception {
        final SceneReviewSaveResult result = service.save(
                createRequest(SceneReviewRenameChoice.RENAME)
        );

        final MediaFile mediaFile = mediaFileRepository.findById(MEDIA_ID)
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewSaveStatus.CREATED_AND_RENAMED,
                        result.status()
                ),
                () -> Assertions.assertNotEquals(mediaPath, mediaFile.getPath()),
                () -> Assertions.assertTrue(Files.exists(mediaFile.getPath()))
        );
    }

    @Test
    @DisplayName("Rename failure preserves scene and forces needs review")
    void renameFailurePreservesSceneAndForcesNeedsReview() throws Exception {
        final SceneReviewWorkflowService failingService =
                new SceneReviewWorkflowService(
                        service.catalogService(),
                        assignmentRepository,
                        sceneRepository,
                        movieRepository,
                        failingRenameService()
                );

        final SceneReviewSaveResult result = failingService.save(
                createRequest(SceneReviewRenameChoice.RENAME)
        );

        final Scene scene = sceneRepository.findById(result.sceneId())
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewSaveStatus.CREATED_RENAME_FAILED_NEEDS_REVIEW,
                        result.status()
                ),
                () -> Assertions.assertTrue(result.databasePersisted()),
                () -> Assertions.assertFalse(result.physicalRenameSucceeded()),
                () -> Assertions.assertEquals(
                        VerificationStatus.NEEDS_REVIEW,
                        scene.getVerificationStatus()
                ),
                () -> Assertions.assertFalse(
                        assignmentRepository.isUnassigned(MEDIA_ID)
                )
        );
    }

    private SceneReviewSaveRequest createRequest(
            SceneReviewRenameChoice renameChoice) {

        return new SceneReviewSaveRequest(
                new SceneReviewDraft(
                        SceneReviewMode.CREATE_FROM_MEDIA,
                        null,
                        List.of(MEDIA_ID),
                        "Scene Title",
                        LocalDate.of(2026, 1, 15),
                        null,
                        null,
                        null,
                        PUBLISHER_ID,
                        SERIES_ID,
                        MOVIE_ID,
                        MOVIE_ID,
                        List.of(PERFORMER_ID),
                        VerificationStatus.VERIFIED,
                        List.of()
                ),
                renameChoice
        );
    }

    private MediaRenameService successfulRenameService() {
        return service == null ? null : service.renameService();
    }

    private MediaRenameService failingRenameService() {
        return new MediaRenameService(
                mediaFileRepository,
                sceneRepository,
                assignmentRepository,
                new OriginalMovieSelector(movieRepository),
                Files::move,
                (mediaFile, newPath) -> {
                    throw new SQLException("forced update failure");
                }
        );
    }

    private MediaFile mediaFile(UUID id, Path path) {
        return new MediaFile(
                id,
                path,
                FILE_SIZE,
                null,
                Duration.ofMillis(1_000L),
                WIDTH,
                HEIGHT,
                LAST_MODIFIED_MILLIS
        );
    }

    private Performer performer(UUID id, String name) {
        return new Performer(id, name, List.of(), PerformerCategory.UNKNOWN);
    }
}

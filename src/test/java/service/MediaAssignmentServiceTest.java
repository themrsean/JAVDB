package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
import model.Movie;
import model.Publisher;
import model.Scene;
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
import repository.UnassignedMediaFilter;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

class MediaAssignmentServiceTest {
    private static final String DATABASE_FILE_NAME =
            "media-assignment-service-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-bbbb-1111-bbbb-111111111111");
    private static final UUID SCENE_ID =
            UUID.fromString("22222222-bbbb-2222-bbbb-222222222222");
    private static final UUID SECOND_SCENE_ID =
            UUID.fromString("33333333-bbbb-3333-bbbb-333333333333");
    private static final UUID MOVIE_ID =
            UUID.fromString("44444444-bbbb-4444-bbbb-444444444444");
    private static final UUID MEDIA_ONE_ID =
            UUID.fromString("55555555-bbbb-5555-bbbb-555555555555");
    private static final UUID MEDIA_TWO_ID =
            UUID.fromString("66666666-bbbb-6666-bbbb-666666666666");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-bbbb-9999-bbbb-999999999999");

    @TempDir
    Path temporaryDirectory;

    private MediaFileRepository mediaFileRepository;
    private SceneRepository sceneRepository;
    private MovieRepository movieRepository;
    private MediaAssignmentRepository assignmentRepository;
    private MediaAssignmentService service;
    private CatalogService catalogService;
    private MediaFile mediaOne;
    private MediaFile mediaTwo;

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
        catalogService = new CatalogService(
                new PublisherRepository(databaseManager),
                new PerformerRepository(databaseManager),
                new SeriesRepository(databaseManager),
                mediaFileRepository,
                sceneRepository,
                new SearchRepository(databaseManager),
                movieRepository
        );
        service = new MediaAssignmentService(
                assignmentRepository,
                mediaFileRepository,
                sceneRepository,
                movieRepository,
                catalogService,
                new MediaTitleDeriver()
        );

        final Publisher publisher =
                new Publisher(PUBLISHER_ID, "Publisher", List.of());
        final Scene scene = new Scene(
                SCENE_ID,
                "Scene",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        final Scene secondScene = new Scene(
                SECOND_SCENE_ID,
                "Second Scene",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        final Movie movie = new Movie(
                MOVIE_ID,
                "Movie",
                null,
                publisher,
                List.of(scene, secondScene),
                false,
                List.of()
        );
        mediaOne = mediaFile(MEDIA_ONE_ID, "one.mp4");
        mediaTwo = mediaFile(MEDIA_TWO_ID, "two.mp4");

        new PublisherRepository(databaseManager).insert(publisher);
        sceneRepository.insert(scene);
        sceneRepository.insert(secondScene);
        movieRepository.insert(movie);
        mediaFileRepository.insert(mediaOne);
        mediaFileRepository.insert(mediaTwo);
    }

    @Test
    @DisplayName("Constructor rejects null dependencies")
    void constructorRejectsNullDependencies() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new MediaAssignmentService(
                                null,
                                mediaFileRepository,
                                sceneRepository,
                                movieRepository,
                                catalogService,
                                new MediaTitleDeriver()
                        )
                ),
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new MediaAssignmentService(
                                assignmentRepository,
                                null,
                                sceneRepository,
                                movieRepository,
                                catalogService,
                                new MediaTitleDeriver()
                        )
                )
        );
    }

    @Test
    @DisplayName("Unassigned media can be attached to a scene")
    void unassignedMediaCanBeAttachedToScene() throws Exception {
        final Scene updated = service.attachToScene(MEDIA_ONE_ID, SCENE_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, updated.getFiles().size()),
                () -> Assertions.assertEquals(
                        MEDIA_ONE_ID,
                        updated.getFiles().getFirst().getId()
                ),
                () -> Assertions.assertFalse(
                        assignmentRepository.isUnassigned(MEDIA_ONE_ID)
                )
        );
    }

    @Test
    @DisplayName("Existing scene media remain attached")
    void existingSceneMediaRemainAttached() throws Exception {
        service.attachToScene(MEDIA_ONE_ID, SCENE_ID);
        final Scene updated = service.attachToScene(MEDIA_TWO_ID, SCENE_ID);

        Assertions.assertEquals(
                List.of(MEDIA_ONE_ID, MEDIA_TWO_ID),
                updated.getFiles().stream().map(MediaFile::getId).toList()
        );
    }

    @Test
    @DisplayName("Scene attachment rejects assignment conflicts")
    void sceneAttachmentRejectsAssignmentConflicts() throws Exception {
        service.attachToScene(MEDIA_ONE_ID, SCENE_ID);

        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.attachToScene(MEDIA_ONE_ID, SCENE_ID)
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.attachToScene(
                                MEDIA_ONE_ID,
                                SECOND_SCENE_ID
                        )
                )
        );
    }

    @Test
    @DisplayName("Unassigned media can be attached to a movie")
    void unassignedMediaCanBeAttachedToMovie() throws Exception {
        final Movie updated = service.attachToMovie(MEDIA_ONE_ID, MOVIE_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, updated.getFiles().size()),
                () -> Assertions.assertEquals(
                        MEDIA_ONE_ID,
                        updated.getFiles().getFirst().getId()
                ),
                () -> Assertions.assertEquals(
                        List.of(SCENE_ID, SECOND_SCENE_ID),
                        updated.getScenes().stream().map(Scene::getId).toList()
                )
        );
    }

    @Test
    @DisplayName("Movie attachment rejects scene-assigned media")
    void movieAttachmentRejectsSceneAssignedMedia() throws Exception {
        service.attachToScene(MEDIA_ONE_ID, SCENE_ID);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.attachToMovie(MEDIA_ONE_ID, MOVIE_ID)
        );
    }

    @Test
    @DisplayName("Unknown IDs are rejected without altering relationships")
    void unknownIdsAreRejectedWithoutAlteringRelationships() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.attachToScene(UNKNOWN_ID, SCENE_ID)
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.attachToScene(MEDIA_ONE_ID, UNKNOWN_ID)
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.attachToMovie(MEDIA_ONE_ID, UNKNOWN_ID)
                )
        );
    }

    @Test
    @DisplayName("Find unassigned delegates filtered query")
    void findUnassignedDelegatesFilteredQuery() throws Exception {
        Assertions.assertEquals(
                List.of(MEDIA_ONE_ID, MEDIA_TWO_ID),
                service.findUnassigned(UnassignedMediaFilter.firstPage())
                        .stream()
                        .map(MediaFile::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Create scene from media uses explicit title and attaches media")
    void createSceneFromMediaUsesExplicitTitleAndAttachesMedia()
            throws Exception {

        final Scene scene = service.createSceneFromMedia(
                new CreateSceneFromMediaRequest(
                        MEDIA_ONE_ID,
                        " Explicit Title ",
                        " CODE-1 ",
                        null,
                        PUBLISHER_ID,
                        null,
                        " 1 ",
                        " 2 ",
                        List.of()
                )
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals("Explicit Title",
                        scene.getTitle()),
                () -> Assertions.assertEquals("CODE-1", scene.getCode()),
                () -> Assertions.assertEquals("1", scene.getSeason()),
                () -> Assertions.assertEquals("2", scene.getEpisode()),
                () -> Assertions.assertEquals(MEDIA_ONE_ID,
                        scene.getFiles().getFirst().getId()),
                () -> Assertions.assertFalse(
                        assignmentRepository.isUnassigned(MEDIA_ONE_ID)
                )
        );
    }

    @Test
    @DisplayName("Create scene from media derives missing title")
    void createSceneFromMediaDerivesMissingTitle() throws Exception {
        final Scene scene = service.createSceneFromMedia(
                new CreateSceneFromMediaRequest(
                        MEDIA_TWO_ID,
                        " ",
                        null,
                        null,
                        PUBLISHER_ID,
                        null,
                        null,
                        null,
                        List.of()
                )
        );

        Assertions.assertEquals("two", scene.getTitle());
    }

    @Test
    @DisplayName("Create scene from media rejects unknown or assigned media")
    void createSceneFromMediaRejectsUnknownOrAssignedMedia() throws Exception {
        service.attachToScene(MEDIA_ONE_ID, SCENE_ID);

        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createSceneFromMedia(
                                new CreateSceneFromMediaRequest(
                                        UNKNOWN_ID,
                                        null,
                                        null,
                                        null,
                                        PUBLISHER_ID,
                                        null,
                                        null,
                                        null,
                                        List.of()
                                )
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createSceneFromMedia(
                                new CreateSceneFromMediaRequest(
                                        MEDIA_ONE_ID,
                                        null,
                                        null,
                                        null,
                                        PUBLISHER_ID,
                                        null,
                                        null,
                                        null,
                                        List.of()
                                )
                        )
                )
        );
    }

    @Test
    @DisplayName("Batch create creates one scene per media in input order")
    void batchCreateCreatesOneScenePerMediaInInputOrder() throws Exception {
        final BatchSceneCreationResult result = service.createScenesFromMedia(
                new BatchSceneCreationRequest(
                        List.of(MEDIA_TWO_ID, MEDIA_ONE_ID),
                        PUBLISHER_ID,
                        null,
                        null,
                        "1",
                        "A",
                        List.of(),
                        false,
                        false
                )
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(2,
                        result.summary().created()),
                () -> Assertions.assertEquals(MEDIA_TWO_ID,
                        result.files().getFirst().mediaFileId()),
                () -> Assertions.assertEquals("two",
                        result.files().getFirst().title()),
                () -> Assertions.assertEquals(MEDIA_ONE_ID,
                        result.files().get(1).mediaFileId()),
                () -> Assertions.assertEquals("one",
                        result.files().get(1).title()),
                () -> Assertions.assertFalse(
                        assignmentRepository.isUnassigned(MEDIA_ONE_ID)
                ),
                () -> Assertions.assertFalse(
                        assignmentRepository.isUnassigned(MEDIA_TWO_ID)
                )
        );
    }

    @Test
    @DisplayName("Batch dry run reports titles without creating scenes")
    void batchDryRunReportsTitlesWithoutCreatingScenes() throws Exception {
        final BatchSceneCreationResult result = service.createScenesFromMedia(
                new BatchSceneCreationRequest(
                        List.of(MEDIA_ONE_ID),
                        PUBLISHER_ID,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        true,
                        false
                )
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        BatchSceneCreationStatus.WOULD_CREATE,
                        result.files().getFirst().status()
                ),
                () -> Assertions.assertEquals(1,
                        result.summary().wouldCreate()),
                () -> Assertions.assertTrue(
                        assignmentRepository.isUnassigned(MEDIA_ONE_ID)
                )
        );
    }

    @Test
    @DisplayName("Batch reports already assigned and continues")
    void batchReportsAlreadyAssignedAndContinues() throws Exception {
        service.attachToScene(MEDIA_ONE_ID, SCENE_ID);

        final BatchSceneCreationResult result = service.createScenesFromMedia(
                new BatchSceneCreationRequest(
                        List.of(MEDIA_ONE_ID, MEDIA_TWO_ID),
                        PUBLISHER_ID,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        false,
                        false
                )
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        BatchSceneCreationStatus.ALREADY_ASSIGNED,
                        result.files().getFirst().status()
                ),
                () -> Assertions.assertEquals(
                        BatchSceneCreationStatus.CREATED,
                        result.files().get(1).status()
                ),
                () -> Assertions.assertEquals(1,
                        result.summary().alreadyAssigned()),
                () -> Assertions.assertEquals(1,
                        result.summary().created())
        );
    }

    @Test
    @DisplayName("Batch duplicate input media IDs are rejected before writes")
    void batchDuplicateInputMediaIdsAreRejectedBeforeWrites() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.createScenesFromMedia(
                        new BatchSceneCreationRequest(
                                List.of(MEDIA_ONE_ID, MEDIA_ONE_ID),
                                PUBLISHER_ID,
                                null,
                                null,
                                null,
                                null,
                                List.of(),
                                false,
                                false
                        )
                )
        );
    }

    private MediaFile mediaFile(UUID id, String fileName) {
        return new MediaFile(
                id,
                temporaryDirectory.resolve(fileName).toAbsolutePath()
                        .normalize(),
                1_000L,
                "hash-" + fileName,
                Duration.ofMillis(2_000L),
                1920,
                1080,
                1_700_000_000_000L
        );
    }
}

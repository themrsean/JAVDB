package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.EntitySuggestionRepository;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

class ReadyPageBatchServiceTest {
    private static final UUID PUBLISHER_ID = UUID.fromString(
            "11111111-4040-1111-4040-111111111111"
    );
    private static final UUID PERFORMER_ID = UUID.fromString(
            "22222222-4040-2222-4040-222222222222"
    );
    private static final UUID FIRST_ID = UUID.fromString(
            "33333333-4040-3333-4040-333333333333"
    );
    private static final UUID SECOND_ID = UUID.fromString(
            "44444444-4040-4444-4040-444444444444"
    );
    private static final UUID MISSING_FILE_ID = UUID.fromString(
            "55555555-4040-5555-4040-555555555555"
    );
    private static final UUID MOVIE_MEDIA_ID = UUID.fromString(
            "66666666-4040-6666-4040-666666666666"
    );
    private static final UUID MOVIE_ID = UUID.fromString(
            "77777777-4040-7777-4040-777777777777"
    );
    private static final UUID UNKNOWN_ID = UUID.fromString(
            "99999999-4040-9999-4040-999999999999"
    );

    @TempDir
    Path temporaryDirectory;

    private MediaFileRepository mediaFileRepository;
    private MediaAssignmentRepository assignmentRepository;
    private SceneRepository sceneRepository;
    private MovieRepository movieRepository;
    private PerformerRepository performerRepository;
    private MediaFilenameIndexingService indexingService;
    private SceneReviewDraftFactory draftFactory;
    private SceneReviewWorkflowService workflowService;
    private Publisher publisher;
    private Performer performer;
    private Path firstPath;
    private Path secondPath;
    private Path missingPhysicalPath;
    private Path moviePath;

    @BeforeEach
    void initialize() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve("ready-page-batch.db")
        );
        new SchemaManager(databaseManager).initialize();
        mediaFileRepository = new MediaFileRepository(databaseManager);
        assignmentRepository = new MediaAssignmentRepository(databaseManager);
        sceneRepository = new SceneRepository(databaseManager);
        movieRepository = new MovieRepository(databaseManager);
        performerRepository = new PerformerRepository(databaseManager);
        final PublisherRepository publisherRepository =
                new PublisherRepository(databaseManager);
        final CatalogService catalogService = new CatalogService(
                publisherRepository,
                performerRepository,
                new SeriesRepository(databaseManager),
                mediaFileRepository,
                sceneRepository,
                new SearchRepository(databaseManager),
                movieRepository
        );
        publisher = new Publisher(
                PUBLISHER_ID,
                "Studio",
                List.of("Studio Alias")
        );
        performer = new Performer(
                PERFORMER_ID,
                "Performer One",
                List.of(),
                PerformerCategory.UNKNOWN
        );
        publisherRepository.insert(publisher);
        performerRepository.insert(performer);
        movieRepository.insert(new Movie(
                MOVIE_ID,
                "Movie Title",
                null,
                publisher,
                List.of(),
                false,
                List.of()
        ));
        firstPath = path(
                "(25.01.02) Studio - Scene One - Performer One.mp4"
        );
        secondPath = path(
                "(25.01.03) Studio Alias - Scene Two - Performer One.mp4"
        );
        missingPhysicalPath = path(
                "(25.01.04) Studio - Missing File - Performer One.mp4"
        );
        moviePath = path(
                "(25.01.05) Studio - Movie Title - Movie Scene - "
                        + "Performer One.mp4"
        );
        Files.writeString(firstPath, "first");
        Files.writeString(secondPath, "second");
        Files.writeString(moviePath, "movie");
        mediaFileRepository.insert(media(FIRST_ID, firstPath));
        mediaFileRepository.insert(media(SECOND_ID, secondPath));
        mediaFileRepository.insert(media(MISSING_FILE_ID, missingPhysicalPath));
        mediaFileRepository.insert(media(MOVIE_MEDIA_ID, moviePath));
        indexingService = new MediaFilenameIndexingService(
                mediaFileRepository,
                assignmentRepository,
                new MediaFilenameParser(),
                new FilenameMetadataMatcher(
                        new EntitySuggestionRepository(databaseManager),
                        new PublisherRepository(databaseManager)
                )
        );
        draftFactory = new SceneReviewDraftFactory(
                sceneRepository,
                movieRepository,
                new OriginalMovieSelector(movieRepository)
        );
        workflowService = new SceneReviewWorkflowService(
                catalogService,
                assignmentRepository,
                sceneRepository,
                movieRepository,
                new MediaRenameService(
                        mediaFileRepository,
                        sceneRepository,
                        assignmentRepository,
                        new OriginalMovieSelector(movieRepository)
                )
        );
    }

    @Test
    @DisplayName("Preflight classifies ready assigned missing and non-ready rows")
    void preflightClassifiesRows() throws Exception {
        assign(SECOND_ID, "Already assigned");
        final ReadyPageBatchPageSnapshot snapshot = new ReadyPageBatchPageSnapshot(
                5,
                List.of(
                        candidate(FIRST_ID, firstPath),
                        candidate(SECOND_ID, secondPath),
                        candidate(UNKNOWN_ID, path("missing-record.mp4")),
                        candidate(FIRST_ID, firstPath)
                )
        );

        final ReadyPageBatchPreflight preflight = service(workflowService)
                .preflight(snapshot);

        Assertions.assertAll(
                () -> Assertions.assertEquals(5, preflight.displayedRows()),
                () -> Assertions.assertEquals(3,
                        preflight.initialReadyCandidates()),
                () -> Assertions.assertEquals(1, preflight.eligibleCount()),
                () -> Assertions.assertEquals(1,
                        preflight.excludedAlreadyAssigned()),
                () -> Assertions.assertEquals(1,
                        preflight.excludedMissing())
        );
    }

    @Test
    @DisplayName("Execution revalidates READY and assignment state")
    void executionRevalidatesReadyAndAssignmentState() throws Exception {
        final ReadyPageBatchService service = service(workflowService);
        final ReadyPageBatchPreflight readyPreflight = service.preflight(
                snapshot(FIRST_ID, firstPath)
        );
        performerRepository.delete(PERFORMER_ID);
        final ReadyPageBatchResult noLongerReady = service.execute(
                readyPreflight,
                ReadyPageBatchMode.CREATE_WITHOUT_RENAMING
        );

        performerRepository.insert(performer);
        final ReadyPageBatchPreflight assignmentPreflight = service.preflight(
                snapshot(SECOND_ID, secondPath)
        );
        assign(SECOND_ID, "Assigned after preflight");
        final ReadyPageBatchResult assigned = service.execute(
                assignmentPreflight,
                ReadyPageBatchMode.CREATE_WITHOUT_RENAMING
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(1,
                        noLongerReady.skippedNoLongerReady()),
                () -> Assertions.assertEquals(1,
                        assigned.skippedAlreadyAssigned()),
                () -> Assertions.assertEquals(0,
                        noLongerReady.createdWithoutRename()),
                () -> Assertions.assertEquals(0,
                        assigned.createdWithoutRename())
        );
    }

    @Test
    @DisplayName("READY display row that changes before preflight is excluded")
    void changedBeforePreflightIsExcluded() throws Exception {
        performerRepository.delete(PERFORMER_ID);

        final ReadyPageBatchPreflight preflight = service(workflowService)
                .preflight(snapshot(FIRST_ID, firstPath));

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, preflight.eligibleCount()),
                () -> Assertions.assertEquals(1,
                        preflight.excludedNoLongerReady()),
                () -> Assertions.assertTrue(sceneRepository.findAll().isEmpty())
        );
    }

    @Test
    @DisplayName("Both confirmation modes map to reviewed save choices")
    void confirmationModesMapToReviewedSaveChoices() {
        final CapturingSaver saver = new CapturingSaver();
        final ReadyPageBatchService service = service(saver);

        service.execute(
                service.preflight(snapshot(FIRST_ID, firstPath)),
                ReadyPageBatchMode.CREATE_WITHOUT_RENAMING
        );
        service.execute(
                service.preflight(snapshot(SECOND_ID, secondPath)),
                ReadyPageBatchMode.CREATE_AND_RENAME
        );

        Assertions.assertEquals(
                List.of(
                        SceneReviewRenameChoice.DO_NOT_RENAME,
                        SceneReviewRenameChoice.RENAME
                ),
                saver.renameChoices
        );
    }

    @Test
    @DisplayName("Multiple rows continue after an individual failure")
    void multipleRowsContinueAfterIndividualFailure() {
        final SceneReviewSaver saver = new SceneReviewSaver() {
            private int calls;

            @Override
            public SceneReviewSaveResult save(SceneReviewSaveRequest request)
                    throws IOException {

                calls++;
                if (calls == 1) {
                    throw new IOException("one row failed");
                }
                return saved(request, SceneReviewSaveStatus.CREATED);
            }
        };
        final ReadyPageBatchService service = service(saver);
        final ReadyPageBatchPreflight preflight = service.preflight(
                new ReadyPageBatchPageSnapshot(2, List.of(
                        candidate(FIRST_ID, firstPath),
                        candidate(SECOND_ID, secondPath)
                ))
        );

        final ReadyPageBatchResult result = service.execute(
                preflight,
                ReadyPageBatchMode.CREATE_WITHOUT_RENAMING
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.otherFailures()),
                () -> Assertions.assertEquals(1,
                        result.createdWithoutRename()),
                () -> Assertions.assertEquals(2, result.rows().size())
        );
    }

    @Test
    @DisplayName("Reviewed save creates verified scene and appends matched movie")
    void reviewedSaveCreatesVerifiedSceneAndAppendsMatchedMovie()
            throws Exception {

        final ReadyPageBatchResult result = service(workflowService).execute(
                service(workflowService).preflight(
                        snapshot(MOVIE_MEDIA_ID, moviePath)
                ),
                ReadyPageBatchMode.CREATE_WITHOUT_RENAMING
        );
        final Scene scene = sceneRepository.findAll().stream()
                .filter(item -> item.getFiles().stream()
                        .anyMatch(file -> file.getId().equals(MOVIE_MEDIA_ID)))
                .findFirst()
                .orElseThrow();
        final Movie movie = movieRepository.findById(MOVIE_ID).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1,
                        result.createdWithoutRename()),
                () -> Assertions.assertEquals(
                        VerificationStatus.VERIFIED,
                        scene.getVerificationStatus()
                ),
                () -> Assertions.assertEquals(
                        List.of(scene.getId()),
                        movie.getScenes().stream().map(Scene::getId).toList()
                )
        );
    }

    @Test
    @DisplayName("Create and rename updates stored and physical path")
    void createAndRenameUpdatesStoredAndPhysicalPath() throws Exception {
        final ReadyPageBatchService service = service(workflowService);
        final ReadyPageBatchResult result = service.execute(
                service.preflight(snapshot(SECOND_ID, secondPath)),
                ReadyPageBatchMode.CREATE_AND_RENAME
        );
        final Path renamedPath = mediaFileRepository.findById(SECOND_ID)
                .orElseThrow()
                .getPath();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1,
                        result.createdAndRenamed()),
                () -> Assertions.assertNotEquals(secondPath, renamedPath),
                () -> Assertions.assertFalse(Files.exists(secondPath)),
                () -> Assertions.assertTrue(Files.exists(renamedPath))
        );
    }

    @Test
    @DisplayName("Rename failure keeps created scene as needs review")
    void renameFailureKeepsCreatedSceneAsNeedsReview() throws Exception {
        final ReadyPageBatchService service = service(workflowService);
        final ReadyPageBatchResult result = service.execute(
                service.preflight(snapshot(MISSING_FILE_ID, missingPhysicalPath)),
                ReadyPageBatchMode.CREATE_AND_RENAME
        );
        final Scene scene = sceneRepository.findAll().stream()
                .filter(item -> item.getFiles().stream().anyMatch(file ->
                        file.getId().equals(MISSING_FILE_ID)))
                .findFirst()
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.renameFailures()),
                () -> Assertions.assertEquals(
                        VerificationStatus.NEEDS_REVIEW,
                        scene.getVerificationStatus()
                ),
                () -> Assertions.assertFalse(
                        assignmentRepository.isUnassigned(MISSING_FILE_ID)
                )
        );
    }

    @Test
    @DisplayName("Assignment race at save is reported without duplicate creation")
    void assignmentRaceAtSaveIsReportedWithoutDuplicateCreation() {
        final SceneReviewSaver racingSaver = request -> {
            assign(request.draft().mediaFileIds().getFirst(), "Racing scene");
            throw new IllegalArgumentException("Media file is already assigned");
        };
        final ReadyPageBatchService service = service(racingSaver);

        final ReadyPageBatchResult result = service.execute(
                service.preflight(snapshot(FIRST_ID, firstPath)),
                ReadyPageBatchMode.CREATE_WITHOUT_RENAMING
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(1,
                        result.skippedAlreadyAssigned()),
                () -> Assertions.assertEquals(1,
                        sceneRepository.findAll().size())
        );
    }

    private ReadyPageBatchService service(SceneReviewSaver saver) {
        return new ReadyPageBatchService(
                mediaFileRepository,
                assignmentRepository,
                indexingService,
                draftFactory,
                saver
        );
    }

    private ReadyPageBatchPageSnapshot snapshot(UUID mediaId, Path path) {
        return new ReadyPageBatchPageSnapshot(
                1,
                List.of(candidate(mediaId, path))
        );
    }

    private ReadyPageBatchCandidate candidate(UUID mediaId, Path path) {
        return new ReadyPageBatchCandidate(mediaId, path);
    }

    private void assign(UUID mediaId, String title) throws SQLException {
        final MediaFile media = mediaFileRepository.findById(mediaId)
                .orElseThrow();
        sceneRepository.insert(new Scene(
                UUID.randomUUID(), title, publisher, null, null, null,
                null, null, List.of(performer), List.of(media),
                VerificationStatus.VERIFIED
        ));
    }

    private SceneReviewSaveResult saved(
            SceneReviewSaveRequest request,
            SceneReviewSaveStatus status) {

        return new SceneReviewSaveResult(
                status,
                UUID.randomUUID(),
                request.draft().mediaFileIds(),
                VerificationStatus.VERIFIED,
                List.of(),
                List.of(),
                null,
                true,
                status == SceneReviewSaveStatus.CREATED_AND_RENAMED
        );
    }

    private Path path(String filename) {
        return temporaryDirectory.resolve(filename).toAbsolutePath().normalize();
    }

    private MediaFile media(UUID id, Path path) {
        return new MediaFile(
                id,
                path,
                5L,
                null,
                Duration.ofSeconds(1),
                1920,
                1080,
                1_700_000_000_000L
        );
    }

    private final class CapturingSaver implements SceneReviewSaver {
        private final java.util.ArrayList<SceneReviewRenameChoice> renameChoices =
                new java.util.ArrayList<>();

        @Override
        public SceneReviewSaveResult save(SceneReviewSaveRequest request) {
            renameChoices.add(request.renameChoice());
            final SceneReviewSaveStatus status = request.renameChoice()
                    == SceneReviewRenameChoice.RENAME
                    ? SceneReviewSaveStatus.CREATED_AND_RENAMED
                    : SceneReviewSaveStatus.CREATED;
            return saved(request, status);
        }
    }
}

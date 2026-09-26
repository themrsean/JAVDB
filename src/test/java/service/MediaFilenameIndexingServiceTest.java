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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.EntitySuggestionRepository;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.UnassignedMediaFilter;
import repository.MovieRepository;
import repository.SeriesRepository;
import repository.SearchRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

class MediaFilenameIndexingServiceTest {
    private static final String DATABASE_FILE_NAME =
            "media-filename-indexing-service-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-babe-1111-babe-111111111111");
    private static final UUID PERFORMER_ID =
            UUID.fromString("22222222-babe-2222-babe-222222222222");
    private static final UUID MEDIA_READY_ID =
            UUID.fromString("33333333-babe-3333-babe-333333333333");
    private static final UUID MEDIA_INVALID_ID =
            UUID.fromString("44444444-babe-4444-babe-444444444444");
    private static final UUID MEDIA_ASSIGNED_ID =
            UUID.fromString("55555555-babe-5555-babe-555555555555");
    private static final UUID SCENE_ID =
            UUID.fromString("66666666-babe-6666-babe-666666666666");
    private static final UUID MOVIE_ID =
            UUID.fromString("77777777-babe-7777-babe-777777777777");
    private static final UUID MEDIA_MOVIE_ID =
            UUID.fromString("88888888-babe-8888-babe-888888888888");
    private static final UUID UNKNOWN_MEDIA_ID =
            UUID.fromString("99999999-babe-9999-babe-999999999999");

    @TempDir
    Path temporaryDirectory;

    private MediaFilenameIndexingService service;
    private MediaFileRepository mediaFileRepository;
    private MediaAssignmentRepository assignmentRepository;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        mediaFileRepository = new MediaFileRepository(databaseManager);
        assignmentRepository = new MediaAssignmentRepository(databaseManager);
        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);
        service = new MediaFilenameIndexingService(
                mediaFileRepository,
                assignmentRepository,
                new MediaFilenameParser(),
                new FilenameMetadataMatcher(
                        new EntitySuggestionRepository(databaseManager),
                        new PublisherRepository(databaseManager)
                ),
                new CatalogService(
                        new PublisherRepository(databaseManager),
                        new PerformerRepository(databaseManager),
                        new SeriesRepository(databaseManager),
                        mediaFileRepository,
                        sceneRepository,
                        new SearchRepository(databaseManager),
                        movieRepository
                ),
                sceneRepository,
                movieRepository
        );

        final Publisher publisher =
                new Publisher(PUBLISHER_ID, "Studio", List.of());
        final Performer performer = new Performer(
                PERFORMER_ID,
                "Performer One",
                List.of(),
                PerformerCategory.ACTOR
        );
        new PublisherRepository(databaseManager).insert(publisher);
        new PerformerRepository(databaseManager).insert(performer);

        final MediaFile ready = mediaFile(
                MEDIA_READY_ID,
                "(25.01.02) Studio - Scene Title - Performer One.mp4"
        );
        final MediaFile invalid = mediaFile(
                MEDIA_INVALID_ID,
                "Studio - Scene Title - Performer One.mp4"
        );
        final MediaFile assigned = mediaFile(
                MEDIA_ASSIGNED_ID,
                "(25.01.03) Studio - Assigned Title - Performer One.mp4"
        );
        final MediaFile movieReady = mediaFile(
                MEDIA_MOVIE_ID,
                "(25.01.04) Studio - Movie Title - Scene Title - Performer One.mp4"
        );
        mediaFileRepository.insert(ready);
        mediaFileRepository.insert(invalid);
        mediaFileRepository.insert(assigned);
        mediaFileRepository.insert(movieReady);
        new SceneRepository(databaseManager).insert(new Scene(
                SCENE_ID,
                "Assigned",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(assigned)
        ));
        movieRepository.insert(new Movie(
                MOVIE_ID,
                "Movie Title",
                null,
                publisher,
                List.of(),
                false,
                List.of()
        ));
    }

    @Test
    @DisplayName("Preview by media UUID reports ready and assigned states")
    void previewByMediaUuidReportsReadyAndAssignedStates() throws Exception {
        final FilenamePreview ready = service.preview(MEDIA_READY_ID);
        final FilenamePreview assigned = service.preview(MEDIA_ASSIGNED_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        ready.matchResult().status()),
                () -> Assertions.assertFalse(ready.assigned()),
                () -> Assertions.assertTrue(assigned.assigned())
        );
    }

    @Test
    @DisplayName("Preview unassigned page returns deterministic results")
    void previewUnassignedPageReturnsDeterministicResults() throws Exception {
        final List<FilenamePreview> previews = service.previewUnassigned(
                new UnassignedMediaFilter(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        UnassignedMediaFilter.DEFAULT_LIMIT,
                        0
                )
        );

        Assertions.assertEquals(
                List.of(MEDIA_READY_ID, MEDIA_MOVIE_ID, MEDIA_INVALID_ID),
                previews.stream().map(FilenamePreview::mediaFileId).toList()
        );
    }

    @Test
    @DisplayName("Preview invalid filename performs no writes")
    void previewInvalidFilenamePerformsNoWrites() throws Exception {
        final FilenamePreview preview = service.preview(MEDIA_INVALID_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        FilenameMatchStatus.INVALID_FILENAME,
                        preview.matchResult().status()
                ),
                () -> Assertions.assertTrue(mediaFileRepository.findById(
                        MEDIA_INVALID_ID
                ).isPresent())
        );
    }

    @Test
    @DisplayName("Auto index creates scenes only for ready rows")
    void autoIndexCreatesScenesOnlyForReadyRows() throws Exception {
        final AutoIndexResult result = service.autoIndex(new AutoIndexRequest(
                List.of(MEDIA_READY_ID, MEDIA_INVALID_ID),
                false,
                false
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.summary().created()),
                () -> Assertions.assertEquals(1, result.summary().invalid()),
                () -> Assertions.assertEquals(AutoIndexStatus.CREATED_VERIFY,
                        result.files().getFirst().status()),
                () -> Assertions.assertEquals(
                        "VERIFY",
                        result.files().getFirst().warnings()
                ),
                () -> Assertions.assertFalse(assignmentRepository.isUnassigned(
                        MEDIA_READY_ID
                )),
                () -> Assertions.assertTrue(assignmentRepository.isUnassigned(
                        MEDIA_INVALID_ID
                ))
        );
    }

    @Test
    @DisplayName("Auto index dry run creates nothing")
    void autoIndexDryRunCreatesNothing() throws Exception {
        final AutoIndexResult result = service.autoIndex(new AutoIndexRequest(
                List.of(MEDIA_READY_ID),
                true,
                false
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(1,
                        result.summary().wouldCreate()),
                () -> Assertions.assertEquals(AutoIndexStatus.WOULD_CREATE,
                        result.files().getFirst().status()),
                () -> Assertions.assertTrue(assignmentRepository.isUnassigned(
                        MEDIA_READY_ID
                ))
        );
    }

    @Test
    @DisplayName("Auto index continues after row failure")
    void autoIndexContinuesAfterRowFailure() throws Exception {
        final AutoIndexResult result = service.autoIndex(new AutoIndexRequest(
                List.of(UNKNOWN_MEDIA_ID, MEDIA_READY_ID),
                false,
                false
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.summary().failed()),
                () -> Assertions.assertEquals(1, result.summary().created()),
                () -> Assertions.assertEquals(AutoIndexStatus.FAILED,
                        result.files().getFirst().status()),
                () -> Assertions.assertEquals(AutoIndexStatus.CREATED_VERIFY,
                        result.files().get(1).status())
        );
    }

    @Test
    @DisplayName("Auto index fail fast stops after row failure")
    void autoIndexFailFastStopsAfterRowFailure() throws Exception {
        final AutoIndexResult result = service.autoIndex(new AutoIndexRequest(
                List.of(UNKNOWN_MEDIA_ID, MEDIA_READY_ID),
                false,
                true
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.summary().failed()),
                () -> Assertions.assertEquals(0, result.summary().created()),
                () -> Assertions.assertEquals(1, result.files().size())
        );
    }

    @Test
    @DisplayName("Auto index appends created scene to matched movie")
    void autoIndexAppendsCreatedSceneToMatchedMovie() throws Exception {
        final AutoIndexResult result = service.autoIndex(new AutoIndexRequest(
                List.of(MEDIA_MOVIE_ID),
                false,
                false
        ));

        final Movie movie = new MovieRepository(
                new DatabaseManager(temporaryDirectory.resolve(
                        DATABASE_FILE_NAME
                ))
        ).findById(MOVIE_ID).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.summary().created()),
                () -> Assertions.assertEquals(1, movie.getScenes().size()),
                () -> Assertions.assertEquals(
                        "Scene Title",
                        movie.getScenes().getFirst().getTitle()
                ),
                () -> Assertions.assertTrue(result.files().getFirst()
                        .warnings().contains("VERIFY"))
        );
    }

    private MediaFile mediaFile(UUID id, String filename) {
        return new MediaFile(
                id,
                temporaryDirectory.resolve(filename).toAbsolutePath()
                        .normalize(),
                1_000L,
                null,
                Duration.ofMillis(1_000L),
                1920,
                1080,
                1_700_000_000_000L
        );
    }
}

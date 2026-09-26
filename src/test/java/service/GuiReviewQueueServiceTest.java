package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.FilenameGenerationStatus;
import media.MediaFilenameParser;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
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
import repository.SeriesRepository;
import repository.UnassignedMediaFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class GuiReviewQueueServiceTest {
    private static final String DATABASE_FILE_NAME =
            "gui-review-queue-service-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-fafa-1111-fafa-111111111111");
    private static final UUID PERFORMER_ID =
            UUID.fromString("22222222-fafa-2222-fafa-222222222222");
    private static final UUID READY_MEDIA_ID =
            UUID.fromString("33333333-fafa-3333-fafa-333333333333");
    private static final UUID INVALID_MEDIA_ID =
            UUID.fromString("44444444-fafa-4444-fafa-444444444444");
    private static final UUID ASSIGNED_MEDIA_ID =
            UUID.fromString("55555555-fafa-5555-fafa-555555555555");
    private static final UUID CONFLICT_MEDIA_ID =
            UUID.fromString("66666666-fafa-6666-fafa-666666666666");
    private static final UUID NO_CONTEXT_MEDIA_ID =
            UUID.fromString("88888888-fafa-8888-fafa-888888888888");
    private static final UUID UNRESOLVED_MEDIA_ID =
            UUID.fromString("99999999-fafa-9999-fafa-999999999999");
    private static final UUID SERIES_MEDIA_ID =
            UUID.fromString("aaaaaaaa-fafa-aaaa-fafa-aaaaaaaaaaaa");
    private static final UUID MOVIE_MEDIA_ID =
            UUID.fromString("bbbbbbbb-fafa-bbbb-fafa-bbbbbbbbbbbb");
    private static final UUID PARTIAL_MOVIE_MEDIA_ID =
            UUID.fromString("eeeeeeee-fafa-eeee-fafa-eeeeeeeeeeee");
    private static final UUID SCENE_ID =
            UUID.fromString("77777777-fafa-7777-fafa-777777777777");
    private static final long FILE_SIZE = 1_234L;
    private static final int WIDTH = 1_920;
    private static final int HEIGHT = 1_080;
    private static final long LAST_MODIFIED = 9_876L;

    @TempDir
    Path temporaryDirectory;

    private MediaFileRepository mediaFileRepository;
    private GuiReviewQueueService service;
    private Path mediaDirectory;
    private Publisher publisher;
    private SeriesRepository seriesRepository;
    private MovieRepository movieRepository;
    private PublisherRepository publisherRepository;
    private PerformerRepository performerRepository;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        mediaDirectory = temporaryDirectory.resolve("incoming");
        Files.createDirectories(mediaDirectory);
        mediaFileRepository = new MediaFileRepository(databaseManager);
        final MediaAssignmentRepository assignmentRepository =
                new MediaAssignmentRepository(databaseManager);
        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        seriesRepository = new SeriesRepository(databaseManager);
        movieRepository = new MovieRepository(databaseManager);
        publisherRepository = new PublisherRepository(databaseManager);
        performerRepository = new PerformerRepository(databaseManager);
        service = new GuiReviewQueueService(
                new MediaFilenameIndexingService(
                        mediaFileRepository,
                        assignmentRepository,
                        new MediaFilenameParser(),
                        new FilenameMetadataMatcher(
                                new EntitySuggestionRepository(databaseManager),
                                publisherRepository
                        )
                ),
                mediaFileRepository
        );

        publisher = new Publisher(PUBLISHER_ID, "Studio", List.of("Alias"));
        final Performer performer = new Performer(
                PERFORMER_ID,
                "Performer One",
                List.of("P Alias"),
                PerformerCategory.ACTOR
        );
        publisherRepository.insert(publisher);
        performerRepository.insert(performer);

        final MediaFile ready = mediaFile(
                READY_MEDIA_ID,
                mediaDirectory.resolve(
                        "(25.01.02) Alias - Scene Title - Performer One.mp4"
                ),
                WIDTH,
                HEIGHT
        );
        final MediaFile invalid = mediaFile(
                INVALID_MEDIA_ID,
                mediaDirectory.resolve(
                        "Studio - Scene Title - Performer One.mkv"
                ),
                640,
                360
        );
        final MediaFile assigned = mediaFile(
                ASSIGNED_MEDIA_ID,
                mediaDirectory.resolve(
                        "(25.01.03) Studio - Assigned Title - Performer One.mp4"
                ),
                WIDTH,
                HEIGHT
        );
        final MediaFile conflict = mediaFile(
                CONFLICT_MEDIA_ID,
                mediaDirectory.resolve(
                        "(25.01.02) Studio - Scene Title - Performer One.mp4"
                ),
                WIDTH,
                HEIGHT
        );
        mediaFileRepository.insert(ready);
        mediaFileRepository.insert(invalid);
        mediaFileRepository.insert(assigned);
        mediaFileRepository.insert(conflict);
        sceneRepository.insert(new Scene(
                SCENE_ID,
                "Assigned",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(performer),
                List.of(assigned)
        ));

        Files.writeString(ready.getPath(), "ready");
        Files.writeString(conflict.getPath(), "conflict");
    }

    @Test
    @DisplayName("Unassigned queue excludes assigned media")
    void unassignedQueueExcludesAssignedMedia() throws Exception {
        final ReviewQueuePage page = service.loadPage(
                ReviewQueueFilter.firstPage()
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(page.items().stream()
                        .anyMatch(item -> READY_MEDIA_ID.equals(item.mediaId()))),
                () -> Assertions.assertFalse(page.items().stream()
                        .anyMatch(item -> ASSIGNED_MEDIA_ID.equals(item.mediaId())))
        );
    }

    @Test
    @DisplayName("Filters and paging are applied")
    void filtersAndPagingAreApplied() throws Exception {
        final ReviewQueuePage pathPage = service.loadPage(new ReviewQueueFilter(
                "Scene Title",
                null,
                null,
                null,
                null,
                null,
                UnassignedMediaFilter.DEFAULT_LIMIT,
                0,
                ReviewMatchStatusFilter.ALL
        ));
        final ReviewQueuePage dimensionPage = service.loadPage(
                new ReviewQueueFilter(
                        null,
                        mediaDirectory,
                        WIDTH,
                        HEIGHT,
                        WIDTH,
                        HEIGHT,
                        1,
                        1,
                        ReviewMatchStatusFilter.ALL
                )
        );

        Assertions.assertAll(
                () -> Assertions.assertFalse(pathPage.items().isEmpty()),
                () -> Assertions.assertEquals(1, dimensionPage.items().size())
        );
    }

    @Test
    @DisplayName("Queue item contains summary fields")
    void queueItemContainsSummaryFields() throws Exception {
        final ReviewQueueItem item = service.loadPage(
                ReviewQueueFilter.firstPage()
        ).items().stream()
                .filter(row -> READY_MEDIA_ID.equals(row.mediaId()))
                .findFirst()
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals("Performer One",
                        item.performers()),
                () -> Assertions.assertEquals("1920x1080",
                        item.resolution()),
                () -> Assertions.assertEquals("Scene Title",
                        item.proposedTitle()),
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        item.matchStatus())
        );
    }

    @Test
    @DisplayName("Details contain media metadata and parse data")
    void detailsContainMediaMetadataAndParseData() throws Exception {
        final ReviewDetails details = service.loadPage(
                ReviewQueueFilter.firstPage()
        ).details().stream()
                .filter(detail -> READY_MEDIA_ID.equals(detail.mediaId()))
                .findFirst()
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(FILE_SIZE, details.fileSize()),
                () -> Assertions.assertEquals(LAST_MODIFIED,
                        details.lastModifiedMillis()),
                () -> Assertions.assertEquals("Scene Title",
                        details.proposedTitle()),
                () -> Assertions.assertEquals(List.of("Performer One"),
                        details.performerCandidates()),
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        details.matchStatus()),
                () -> Assertions.assertFalse(
                        details.canonicalRename().proposedFilename().isBlank()
                )
        );
    }

    @Test
    @DisplayName("Series-derived publisher produces a complete READY queue preview")
    void seriesDerivedPublisherProducesCompleteReadyQueuePreview()
            throws Exception {
        seriesRepository.insert(new Series(UUID.fromString(
                "cccccccc-fafa-cccc-fafa-cccccccccccc"), "Unique Series", publisher));
        mediaFileRepository.insert(mediaFile(SERIES_MEDIA_ID,
                mediaDirectory.resolve(
                        "(25.01.02) Unique Series - Scene Title - P Alias.mp4"),
                WIDTH, HEIGHT));

        final ReviewQueuePage page = service.loadPage(ReviewQueueFilter.firstPage());
        final ReviewQueueItem item = item(page, SERIES_MEDIA_ID);
        final ReviewDetails details = details(page, SERIES_MEDIA_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        item.matchStatus()),
                () -> Assertions.assertEquals("Studio", item.publisherName()),
                () -> Assertions.assertEquals("Studio", details.publisherResolution()),
                () -> Assertions.assertFalse(
                        details.canonicalRename().proposedFilename().isBlank()),
                () -> Assertions.assertTrue(details.canonicalRename()
                        .proposedFilename().contains("Studio"))
        );
    }

    @Test
    @DisplayName("Movie-derived publisher produces a complete READY queue preview")
    void movieDerivedPublisherProducesCompleteReadyQueuePreview()
            throws Exception {
        movieRepository.insert(new Movie(UUID.fromString(
                "dddddddd-fafa-dddd-fafa-dddddddddddd"), "Unique Movie", null,
                publisher, List.of(), false, List.of()));
        mediaFileRepository.insert(mediaFile(MOVIE_MEDIA_ID,
                mediaDirectory.resolve(
                        "(25.01.02) Unique Movie - Scene Title - P Alias.mp4"),
                WIDTH, HEIGHT));

        final ReviewQueuePage page = service.loadPage(ReviewQueueFilter.firstPage());
        final ReviewQueueItem item = item(page, MOVIE_MEDIA_ID);
        final ReviewDetails details = details(page, MOVIE_MEDIA_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        item.matchStatus()),
                () -> Assertions.assertEquals("Studio", item.publisherName()),
                () -> Assertions.assertEquals("Studio", details.publisherResolution()),
                () -> Assertions.assertFalse(
                        details.canonicalRename().proposedFilename().isBlank()),
                () -> Assertions.assertTrue(details.canonicalRename()
                        .proposedFilename().contains("Studio"))
        );
    }

    @Test
    @DisplayName("Strong partial movie reaches review queue without canonical rename")
    void strongPartialMovieReachesReviewQueue() throws Exception {
        final UUID brazzersId = UUID.fromString(
                "10101010-fafa-1010-fafa-101010101010"
        );
        final UUID seriesId = UUID.fromString(
                "12121212-fafa-1212-fafa-121212121212"
        );
        final UUID performerId = UUID.fromString(
                "34343434-fafa-3434-fafa-343434343434"
        );
        final Publisher brazzers = new Publisher(
                brazzersId, "Brazzers", List.of()
        );
        publisherRepository.insert(brazzers);
        seriesRepository.insert(new Series(
                seriesId, "BigWetButts", brazzers
        ));
        performerRepository.insert(new Performer(
                performerId, "Abella Danger", List.of(),
                PerformerCategory.ACTRESS
        ));
        mediaFileRepository.insert(mediaFile(
                PARTIAL_MOVIE_MEDIA_ID,
                mediaDirectory.resolve(
                        "(15.02.08) Brazzers - BigWetButts - Asspirations 2 - "
                                + "Abella's Ass Is In Danger - Abella Danger.mp4"
                ),
                WIDTH,
                HEIGHT
        ));

        final ReviewDetails details = details(
                service.loadPage(ReviewQueueFilter.firstPage()),
                PARTIAL_MOVIE_MEDIA_ID
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.UNRESOLVED,
                        details.matchStatus()),
                () -> Assertions.assertEquals("Brazzers",
                        details.publisherResolution()),
                () -> Assertions.assertEquals("BigWetButts",
                        details.seriesResolution()),
                () -> Assertions.assertEquals("Asspirations 2",
                        details.movieCandidate()),
                () -> Assertions.assertTrue(details.movieResolution().isBlank()),
                () -> Assertions.assertEquals(List.of("Asspirations 2"),
                        details.unresolvedSegments()),
                () -> Assertions.assertEquals(
                        FilenameGenerationStatus.REVIEW_REQUIRED.name(),
                        details.canonicalRename().status())
        );
    }

    @Test
    @DisplayName("Invalid filename does not receive guessed canonical filename")
    void invalidFilenameDoesNotReceiveGuessedCanonicalFilename()
            throws Exception {

        final ReviewDetails details = service.loadPage(
                ReviewQueueFilter.firstPage()
        ).details().stream()
                .filter(detail -> INVALID_MEDIA_ID.equals(detail.mediaId()))
                .findFirst()
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        FilenameMatchStatus.INVALID_FILENAME,
                        details.matchStatus()
                ),
                () -> Assertions.assertEquals(
                        FilenameGenerationStatus.REVIEW_REQUIRED.name(),
                        details.canonicalRename().status()
                )
        );
    }

    @Test
    @DisplayName("No-context filename stays review-required without crashing")
    void noContextFilenameStaysReviewRequiredWithoutCrashing()
            throws Exception {

        mediaFileRepository.insert(mediaFile(
                NO_CONTEXT_MEDIA_ID,
                mediaDirectory.resolve(
                        "(25.01.02) Scene Title - Performer One.mp4"
                ),
                WIDTH,
                HEIGHT
        ));

        final ReviewDetails details = service.loadPage(
                ReviewQueueFilter.firstPage()
        ).details().stream().filter(detail -> NO_CONTEXT_MEDIA_ID.equals(
                detail.mediaId())).findFirst().orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        FilenameMatchStatus.REVIEW_REQUIRED,
                        details.matchStatus()
                ),
                () -> Assertions.assertEquals(
                        FilenameGenerationStatus.REVIEW_REQUIRED.name(),
                        details.canonicalRename().status()
                )
        );
    }

    @Test
    @DisplayName("Unresolved valid filename still exposes title and release date")
    void unresolvedFilenameExposesStructuralDraftFields() throws Exception {
        mediaFileRepository.insert(mediaFile(UNRESOLVED_MEDIA_ID,
                mediaDirectory.resolve(
                        "(14.07.12) EvilAngel - Raw 20 - Abella Danger.mp4"),
                WIDTH, HEIGHT));

        final ReviewDetails details = service.loadPage(
                ReviewQueueFilter.firstPage()).details().stream()
                .filter(detail -> UNRESOLVED_MEDIA_ID.equals(detail.mediaId()))
                .findFirst().orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals("Raw 20", details.proposedTitle()),
                () -> Assertions.assertEquals(LocalDate.of(2014, 7, 12),
                        details.releaseDate()),
                () -> Assertions.assertEquals(media.FilenameParseStatus.VALID,
                        details.parseStatus()),
                () -> Assertions.assertEquals(FilenameMatchStatus.UNRESOLVED,
                        details.matchStatus()),
                () -> Assertions.assertTrue(details.publisherResolution().isBlank()),
                () -> Assertions.assertTrue(details.seriesResolution().isBlank()),
                () -> Assertions.assertTrue(details.movieResolution().isBlank()),
                () -> Assertions.assertTrue(details.resolvedPerformerNames().isEmpty())
        );
    }

    @Test
    @DisplayName("Destination physical and database conflicts are detected")
    void destinationPhysicalAndDatabaseConflictsAreDetected() throws Exception {
        final ReviewDetails details = service.loadPage(
                ReviewQueueFilter.firstPage()
        ).details().stream()
                .filter(detail -> READY_MEDIA_ID.equals(detail.mediaId()))
                .findFirst()
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        details.canonicalRename().physicalDestinationExists()
                ),
                () -> Assertions.assertTrue(
                        details.canonicalRename().databasePathConflict()
                )
        );
    }

    @Test
    @DisplayName("Service does not modify media records")
    void serviceDoesNotModifyMediaRecords() throws Exception {
        final MediaFile before =
                mediaFileRepository.findById(READY_MEDIA_ID).orElseThrow();

        service.loadPage(ReviewQueueFilter.firstPage());

        final MediaFile after =
                mediaFileRepository.findById(READY_MEDIA_ID).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(before.getPath(), after.getPath()),
                () -> Assertions.assertEquals(before.getFileSize(),
                        after.getFileSize()),
                () -> Assertions.assertEquals(before.getLastModifiedMillis(),
                        after.getLastModifiedMillis())
        );
    }

    private MediaFile mediaFile(UUID id, Path path, int width, int height) {
        return new MediaFile(
                id,
                path,
                FILE_SIZE,
                null,
                Duration.ofMillis(1_000L),
                width,
                height,
                LAST_MODIFIED
        );
    }

    private ReviewQueueItem item(ReviewQueuePage page, UUID mediaId) {
        return page.items().stream().filter(item -> mediaId.equals(item.mediaId()))
                .findFirst().orElseThrow();
    }

    private ReviewDetails details(ReviewQueuePage page, UUID mediaId) {
        return page.details().stream()
                .filter(details -> mediaId.equals(details.mediaId()))
                .findFirst().orElseThrow();
    }
}

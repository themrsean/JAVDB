package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.FilenameParseStatus;
import media.ParsedMediaFilename;
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
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class SceneReviewDraftFactoryTest {
    private static final String DATABASE_FILE_NAME =
            "scene-review-draft-factory-test.db";
    private static final UUID MEDIA_ID =
            UUID.fromString("11111111-aaaa-1111-aaaa-111111111111");
    private static final UUID PUBLISHER_ID =
            UUID.fromString("22222222-aaaa-2222-aaaa-222222222222");
    private static final UUID PERFORMER_ID =
            UUID.fromString("33333333-aaaa-3333-aaaa-333333333333");
    private static final UUID MOVIE_ID =
            UUID.fromString("44444444-aaaa-4444-aaaa-444444444444");
    private static final UUID SCENE_ID =
            UUID.fromString("55555555-aaaa-5555-aaaa-555555555555");
    private static final long FILE_SIZE = 10L;
    private static final int WIDTH = 1_280;
    private static final int HEIGHT = 720;
    private static final long LAST_MODIFIED_MILLIS = 321L;

    @TempDir
    Path temporaryDirectory;

    private SceneRepository sceneRepository;
    private MovieRepository movieRepository;
    private SceneReviewDraftFactory factory;
    private Publisher publisher;
    private Performer performer;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        sceneRepository = new SceneRepository(databaseManager);
        movieRepository = new MovieRepository(databaseManager);
        factory = new SceneReviewDraftFactory(
                sceneRepository,
                movieRepository,
                new OriginalMovieSelector(movieRepository)
        );
        publisher = new Publisher(PUBLISHER_ID, "Publisher", List.of());
        performer = new Performer(
                PERFORMER_ID,
                "Alice",
                List.of("Ally"),
                PerformerCategory.UNKNOWN
        );

        new PublisherRepository(databaseManager).insert(publisher);
        new PerformerRepository(databaseManager).insert(performer);
        new MediaFileRepository(databaseManager).insert(mediaFile(MEDIA_ID));
    }

    @Test
    @DisplayName("Ready unassigned preview creates populated draft")
    void readyUnassignedPreviewCreatesPopulatedDraft() throws Exception {
        final EditableSceneReviewDraft editable =
                factory.fromUnassignedPreview(readyPreview());

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewMode.CREATE_FROM_MEDIA,
                        editable.draft().mode()
                ),
                () -> Assertions.assertEquals(
                        "Scene Title",
                        editable.draft().title()
                ),
                () -> Assertions.assertEquals(
                        PUBLISHER_ID,
                        editable.draft().publisherId()
                ),
                () -> Assertions.assertEquals(
                        List.of(PERFORMER_ID),
                        editable.draft().performerIds()
                ),
                () -> Assertions.assertTrue(editable.unmatchedPerformers().isEmpty())
        );
    }

    @Test
    @DisplayName("Review queue details create populated create-mode draft")
    void reviewQueueDetailsCreatePopulatedCreateModeDraft() {
        final FilenameInterpretation interpretation = interpretation(
                new EntityMatch(
                        PUBLISHER_ID,
                        "Publisher",
                        "Publisher",
                        MatchSource.EXPLICIT_PRIMARY_NAME,
                        null
                ),
                List.of(new EntityMatch(
                        PERFORMER_ID,
                        "Alice",
                        "Alice",
                        MatchSource.EXPLICIT_PRIMARY_NAME,
                        null
                )),
                List.of()
        );
        final ReviewDetails details = new ReviewDetails(
                MEDIA_ID,
                temporaryDirectory.resolve("media.mp4"),
                "media.mp4",
                temporaryDirectory.toString(),
                FILE_SIZE,
                LAST_MODIFIED_MILLIS,
                WIDTH,
                HEIGHT,
                "PT1S",
                "",
                FilenameParseStatus.VALID,
                LocalDate.of(2026, 1, 15),
                List.of("Publisher"),
                "Scene Title",
                "ABC-001",
                "1",
                "2",
                List.of("Alice"),
                List.of("Alice"),
                List.of(),
                "Publisher",
                "Publisher",
                "",
                "",
                "",
                "",
                interpretation,
                List.of(interpretation),
                List.of(),
                List.of(),
                List.of("parser warning"),
                List.of("matcher warning"),
                FilenameMatchStatus.READY,
                new CanonicalRenameDisplay(
                        "READY",
                        "media.mp4",
                        "(26.01.15) Publisher - Scene Title - Alice.mp4",
                        temporaryDirectory.resolve(
                                "(26.01.15) Publisher - Scene Title - Alice.mp4"
                        ),
                        false,
                        false,
                        false,
                        List.of("rename warning"),
                        ""
                )
        );

        final EditableSceneReviewDraft editable =
                factory.fromReviewDetails(details);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewMode.CREATE_FROM_MEDIA,
                        editable.draft().mode()
                ),
                () -> Assertions.assertEquals(
                        "Scene Title",
                        editable.draft().title()
                ),
                () -> Assertions.assertEquals(
                        LocalDate.of(2026, 1, 15),
                        editable.draft().releaseDate()
                ),
                () -> Assertions.assertEquals(
                        "ABC-001",
                        editable.draft().code()
                ),
                () -> Assertions.assertEquals(
                        PUBLISHER_ID,
                        editable.draft().publisherId()
                ),
                () -> Assertions.assertEquals(
                        List.of(PERFORMER_ID),
                        editable.draft().performerIds()
                ),
                () -> Assertions.assertTrue(
                        editable.warnings().contains("matcher warning")
                )
        );
    }

    @Test
    @DisplayName("Ambiguous preview preserves alternatives without selecting context")
    void ambiguousPreviewPreservesAlternativesWithoutSelectingContext()
            throws Exception {

        final EditableSceneReviewDraft editable =
                factory.fromUnassignedPreview(ambiguousPreview());

        Assertions.assertAll(
                () -> Assertions.assertNull(editable.draft().publisherId()),
                () -> Assertions.assertEquals(2, editable.alternatives().size()),
                () -> Assertions.assertEquals(
                        FilenameMatchStatus.AMBIGUOUS,
                        editable.matchStatus()
                )
        );
    }

    @Test
    @DisplayName("Existing scene draft uses persisted metadata and status")
    void existingSceneDraftUsesPersistedMetadataAndStatus() throws Exception {
        final Scene scene = scene();
        sceneRepository.insert(scene);
        movieRepository.insert(new Movie(
                MOVIE_ID,
                "Movie",
                LocalDate.of(2020, 1, 1),
                publisher,
                List.of(scene),
                false,
                List.of()
        ));

        final EditableSceneReviewDraft editable =
                factory.fromExistingScene(SCENE_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewMode.EDIT_EXISTING_SCENE,
                        editable.draft().mode()
                ),
                () -> Assertions.assertEquals("Persisted", editable.draft().title()),
                () -> Assertions.assertEquals(
                        VerificationStatus.NEEDS_REVIEW,
                        editable.draft().verificationStatus()
                ),
                () -> Assertions.assertEquals(
                        MOVIE_ID,
                        editable.draft().selectedMovieId()
                ),
                () -> Assertions.assertEquals(
                        List.of(MEDIA_ID),
                        editable.draft().mediaFileIds()
                )
        );
    }

    @Test
    @DisplayName("Applying alternative updates matched fields without persisting")
    void applyingAlternativeUpdatesMatchedFieldsWithoutPersisting()
            throws Exception {

        final EditableSceneReviewDraft editable =
                factory.fromUnassignedPreview(ambiguousPreview());
        final EditableSceneReviewDraft applied =
                factory.applyInterpretation(editable, editable.alternatives().getFirst());

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        PUBLISHER_ID,
                        applied.draft().publisherId()
                ),
                () -> Assertions.assertTrue(sceneRepository.findAll().isEmpty())
        );
    }

    private FilenamePreview readyPreview() {
        final ParsedMediaFilename parsed = parsed("Scene Title");
        final FilenameInterpretation interpretation = interpretation(
                new EntityMatch(
                        PUBLISHER_ID,
                        "Publisher",
                        "Publisher",
                        MatchSource.EXPLICIT_PRIMARY_NAME,
                        null
                ),
                List.of(new EntityMatch(
                        PERFORMER_ID,
                        "Alice",
                        "Alice",
                        MatchSource.EXPLICIT_PRIMARY_NAME,
                        null
                )),
                List.of()
        );

        return preview(parsed, FilenameMatchStatus.READY, interpretation,
                List.of(interpretation));
    }

    private FilenamePreview ambiguousPreview() {
        final ParsedMediaFilename parsed = parsed("Scene Title");
        final FilenameInterpretation first = interpretation(
                new EntityMatch(
                        PUBLISHER_ID,
                        "Publisher",
                        "Publisher",
                        MatchSource.EXPLICIT_PRIMARY_NAME,
                        null
                ),
                List.of(),
                List.of()
        );
        final FilenameInterpretation second = interpretation(
                null,
                List.of(),
                List.of("Publisher")
        );

        return preview(parsed, FilenameMatchStatus.AMBIGUOUS, first,
                List.of(first, second));
    }

    private FilenamePreview preview(
            ParsedMediaFilename parsed,
            FilenameMatchStatus status,
            FilenameInterpretation best,
            List<FilenameInterpretation> interpretations) {

        return new FilenamePreview(
                MEDIA_ID,
                temporaryDirectory.resolve("media.mp4"),
                false,
                parsed,
                new FilenameMatchResult(
                        parsed,
                        status,
                        best,
                        interpretations,
                        List.of("warning"),
                        List.of()
                )
        );
    }

    private FilenameInterpretation interpretation(
            EntityMatch publisher,
            List<EntityMatch> performers,
            List<String> unresolvedSegments) {

        return new FilenameInterpretation(
                publisher,
                null,
                null,
                performers,
                unresolvedSegments,
                1
        );
    }

    private ParsedMediaFilename parsed(String title) {
        return new ParsedMediaFilename(
                temporaryDirectory.resolve("media.mp4"),
                "media",
                ".mp4",
                LocalDate.of(2026, 1, 15),
                List.of("Publisher"),
                null,
                null,
                null,
                title,
                List.of("Alice"),
                List.of(),
                FilenameParseStatus.VALID,
                List.of(),
                List.of()
        );
    }

    private Scene scene() {
        return new Scene(
                SCENE_ID,
                "Persisted",
                publisher,
                LocalDate.of(2026, 1, 15),
                "ABC-001",
                null,
                "1",
                "2",
                List.of(performer),
                List.of(mediaFile(MEDIA_ID)),
                VerificationStatus.NEEDS_REVIEW
        );
    }

    private MediaFile mediaFile(UUID id) {
        return new MediaFile(
                id,
                temporaryDirectory.resolve(id + ".mp4"),
                FILE_SIZE,
                null,
                Duration.ofMillis(1_000L),
                WIDTH,
                HEIGHT,
                LAST_MODIFIED_MILLIS
        );
    }
}

package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.Movie;
import model.Publisher;
import model.Scene;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MovieRepository;
import repository.PublisherRepository;
import repository.SceneRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class OriginalMovieSelectorTest {
    private static final String DATABASE_FILE_NAME =
            "original-movie-selector-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-cccc-1111-cccc-111111111111");
    private static final UUID SCENE_ID =
            UUID.fromString("22222222-cccc-2222-cccc-222222222222");
    private static final UUID OTHER_SCENE_ID =
            UUID.fromString("33333333-cccc-3333-cccc-333333333333");
    private static final UUID MOVIE_ONE_ID =
            UUID.fromString("44444444-cccc-4444-cccc-444444444444");
    private static final UUID MOVIE_TWO_ID =
            UUID.fromString("55555555-cccc-5555-cccc-555555555555");
    private static final UUID MOVIE_THREE_ID =
            UUID.fromString("66666666-cccc-6666-cccc-666666666666");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-cccc-9999-cccc-999999999999");
    private static final LocalDate EARLY_DATE = LocalDate.of(2021, 4, 10);
    private static final LocalDate LATER_DATE = LocalDate.of(2023, 5, 11);
    private static final LocalDate LATEST_DATE = LocalDate.of(2025, 6, 12);

    @TempDir
    Path temporaryDirectory;

    private MovieRepository movieRepository;
    private Scene scene;
    private Scene otherScene;
    private Publisher publisher;
    private OriginalMovieSelector selector;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        movieRepository = new MovieRepository(databaseManager);
        selector = new OriginalMovieSelector(movieRepository);
        publisher = new Publisher(PUBLISHER_ID, "Publisher", List.of());
        scene = scene(SCENE_ID, "Scene");
        otherScene = scene(OTHER_SCENE_ID, "Other Scene");

        new PublisherRepository(databaseManager).insert(publisher);
        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        sceneRepository.insert(scene);
        sceneRepository.insert(otherScene);
    }

    @Test
    @DisplayName("Constructor rejects null movie repository")
    void constructorRejectsNullMovieRepository() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new OriginalMovieSelector(null)
        );
    }

    @Test
    @DisplayName("No movies produces none")
    void noMoviesProducesNone() throws Exception {
        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, null);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MovieSelectionStatus.NONE,
                        result.status()
                ),
                () -> Assertions.assertTrue(result.selectedMovie().isEmpty()),
                () -> Assertions.assertTrue(result.candidateMovies().isEmpty())
        );
    }

    @Test
    @DisplayName("One dated movie is selected")
    void oneDatedMovieIsSelected() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Original", EARLY_DATE, false, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, null);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MovieSelectionStatus.SELECTED,
                        result.status()
                ),
                () -> Assertions.assertEquals(
                        MOVIE_ONE_ID,
                        result.selectedMovie().orElseThrow().getId()
                )
        );
    }

    @Test
    @DisplayName("One undated movie is selected")
    void oneUndatedMovieIsSelected() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Undated", null, false, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, null);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MovieSelectionStatus.SELECTED,
                        result.status()
                ),
                () -> Assertions.assertEquals(
                        MOVIE_ONE_ID,
                        result.selectedMovie().orElseThrow().getId()
                )
        );
    }

    @Test
    @DisplayName("Earliest movie is selected from multiple dated movies")
    void earliestMovieIsSelectedFromMultipleDatedMovies() throws Exception {
        insertMovie(MOVIE_TWO_ID, "Compilation", LATER_DATE, true, scene);
        insertMovie(MOVIE_ONE_ID, "Original", EARLY_DATE, false, scene);
        insertMovie(MOVIE_THREE_ID, "Rerelease", LATEST_DATE, true, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, null);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MovieSelectionStatus.SELECTED,
                        result.status()
                ),
                () -> Assertions.assertEquals(
                        MOVIE_ONE_ID,
                        result.selectedMovie().orElseThrow().getId()
                ),
                () -> Assertions.assertEquals(
                        List.of(MOVIE_ONE_ID, MOVIE_TWO_ID, MOVIE_THREE_ID),
                        result.candidateMovies().stream()
                                .map(Movie::getId)
                                .toList()
                )
        );
    }

    @Test
    @DisplayName("Missing date in multiple movies requires review")
    void missingDateInMultipleMoviesRequiresReview() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Original", EARLY_DATE, false, scene);
        insertMovie(MOVIE_TWO_ID, "Unknown Date", null, false, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, null);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MovieSelectionStatus.REVIEW_REQUIRED,
                        result.status()
                ),
                () -> Assertions.assertTrue(result.selectedMovie().isEmpty())
        );
    }

    @Test
    @DisplayName("All missing dates require review")
    void allMissingDatesRequireReview() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Unknown One", null, false, scene);
        insertMovie(MOVIE_TWO_ID, "Unknown Two", null, false, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, null);

        Assertions.assertEquals(
                MovieSelectionStatus.REVIEW_REQUIRED,
                result.status()
        );
    }

    @Test
    @DisplayName("Earliest date tie requires review")
    void earliestDateTieRequiresReview() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Tie One", EARLY_DATE, false, scene);
        insertMovie(MOVIE_TWO_ID, "Tie Two", EARLY_DATE, true, scene);
        insertMovie(MOVIE_THREE_ID, "Later", LATER_DATE, true, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, null);

        Assertions.assertEquals(
                MovieSelectionStatus.REVIEW_REQUIRED,
                result.status()
        );
    }

    @Test
    @DisplayName("Valid override resolves tied dates")
    void validOverrideResolvesTiedDates() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Tie One", EARLY_DATE, false, scene);
        insertMovie(MOVIE_TWO_ID, "Tie Two", EARLY_DATE, true, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, MOVIE_TWO_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MovieSelectionStatus.OVERRIDE_SELECTED,
                        result.status()
                ),
                () -> Assertions.assertEquals(
                        MOVIE_TWO_ID,
                        result.selectedMovie().orElseThrow().getId()
                )
        );
    }

    @Test
    @DisplayName("Valid override resolves missing date ambiguity")
    void validOverrideResolvesMissingDateAmbiguity() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Dated", EARLY_DATE, false, scene);
        insertMovie(MOVIE_TWO_ID, "Undated", null, false, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, MOVIE_TWO_ID);

        Assertions.assertEquals(
                MovieSelectionStatus.OVERRIDE_SELECTED,
                result.status()
        );
    }

    @Test
    @DisplayName("Unknown override is rejected")
    void unknownOverrideIsRejected() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Original", EARLY_DATE, false, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, UNKNOWN_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MovieSelectionStatus.INVALID_OVERRIDE,
                        result.status()
                ),
                () -> Assertions.assertTrue(result.selectedMovie().isEmpty())
        );
    }

    @Test
    @DisplayName("Unrelated movie override is rejected")
    void unrelatedMovieOverrideIsRejected() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Original", EARLY_DATE, false, scene);
        insertMovie(MOVIE_TWO_ID, "Unrelated", EARLY_DATE, false, otherScene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, MOVIE_TWO_ID);

        Assertions.assertEquals(
                MovieSelectionStatus.INVALID_OVERRIDE,
                result.status()
        );
    }

    @Test
    @DisplayName("Candidate ordering is deterministic")
    void candidateOrderingIsDeterministic() throws Exception {
        insertMovie(MOVIE_TWO_ID, "Zulu", LATER_DATE, false, scene);
        insertMovie(MOVIE_ONE_ID, "Alpha", LATER_DATE, false, scene);

        final MovieSelectionResult result =
                selector.selectOriginalMovie(scene, null);

        Assertions.assertEquals(
                List.of(MOVIE_ONE_ID, MOVIE_TWO_ID),
                result.candidateMovies().stream()
                        .map(Movie::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Selection does not modify the database")
    void selectionDoesNotModifyDatabase() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Original", EARLY_DATE, false, scene);

        selector.selectOriginalMovie(scene, null);

        final Movie storedMovie =
                movieRepository.findById(MOVIE_ONE_ID).orElseThrow();
        Assertions.assertEquals(
                List.of(SCENE_ID),
                storedMovie.getScenes().stream()
                        .map(Scene::getId)
                        .toList()
        );
    }

    private Scene scene(UUID id, String title) {
        return new Scene(
                id,
                title,
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }

    private void insertMovie(
            UUID id,
            String title,
            LocalDate releaseDate,
            boolean compilation,
            Scene movieScene) throws Exception {

        movieRepository.insert(new Movie(
                id,
                title,
                releaseDate,
                publisher,
                List.of(movieScene),
                compilation,
                List.of()
        ));
    }
}

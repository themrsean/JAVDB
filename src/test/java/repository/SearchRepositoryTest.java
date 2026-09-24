package repository;

import database.DatabaseManager;
import database.SchemaManager;
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

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class SearchRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "search-repository-test.db";
    private static final UUID PERFORMER_A_ID =
            UUID.fromString("aaaaaaaa-ffff-1111-ffff-111111111111");
    private static final UUID PERFORMER_B_ID =
            UUID.fromString("bbbbbbbb-ffff-2222-ffff-222222222222");
    private static final UUID PERFORMER_C_ID =
            UUID.fromString("cccccccc-ffff-3333-ffff-333333333333");
    private static final UUID PUBLISHER_ONE_ID =
            UUID.fromString("11111111-ffff-1111-ffff-111111111111");
    private static final UUID PUBLISHER_TWO_ID =
            UUID.fromString("22222222-ffff-2222-ffff-222222222222");
    private static final UUID SERIES_ONE_ID =
            UUID.fromString("33333333-ffff-1111-ffff-111111111111");
    private static final UUID SERIES_TWO_ID =
            UUID.fromString("44444444-ffff-2222-ffff-222222222222");
    private static final UUID SCENE_ONE_ID =
            UUID.fromString("55555555-ffff-1111-ffff-111111111111");
    private static final UUID SCENE_TWO_ID =
            UUID.fromString("66666666-ffff-2222-ffff-222222222222");
    private static final UUID SCENE_THREE_ID =
            UUID.fromString("77777777-ffff-3333-ffff-333333333333");
    private static final UUID MOVIE_ONE_ID =
            UUID.fromString("88888888-ffff-1111-ffff-111111111111");
    private static final UUID MOVIE_TWO_ID =
            UUID.fromString("99999999-ffff-2222-ffff-222222222222");
    private static final LocalDate SCENE_ONE_DATE =
            LocalDate.of(2024, 1, 1);
    private static final LocalDate SCENE_TWO_DATE =
            LocalDate.of(2024, 2, 1);
    private static final LocalDate SCENE_THREE_DATE =
            LocalDate.of(2025, 1, 1);
    private static final LocalDate MOVIE_ONE_DATE =
            LocalDate.of(2024, 3, 1);
    private static final LocalDate MOVIE_TWO_DATE =
            LocalDate.of(2025, 3, 1);

    @TempDir
    Path temporaryDirectory;

    private SearchRepository repository;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        repository = new SearchRepository(databaseManager);
        createFixture(databaseManager);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new SearchRepository(null)
        );
    }

    @Test
    @DisplayName("Scene lookup by performer works")
    void sceneLookupByPerformerWorks() throws Exception {
        Assertions.assertEquals(
                List.of(SCENE_ONE_ID, SCENE_TWO_ID),
                sceneIds(repository.findScenesByPerformer(PERFORMER_A_ID))
        );
    }

    @Test
    @DisplayName("Shared-scene lookup works")
    void sharedSceneLookupWorks() throws Exception {
        Assertions.assertEquals(
                List.of(SCENE_ONE_ID),
                sceneIds(repository.findScenesSharedByPerformers(
                        PERFORMER_A_ID,
                        PERFORMER_B_ID
                ))
        );
    }

    @Test
    @DisplayName("Scene lookup requiring all selected performers works")
    void sceneLookupRequiringAllSelectedPerformersWorks()
            throws Exception {

        Assertions.assertEquals(
                List.of(SCENE_TWO_ID),
                sceneIds(repository.findScenesByAllPerformers(
                        List.of(PERFORMER_A_ID, PERFORMER_C_ID)
                ))
        );
    }

    @Test
    @DisplayName("Movie lookup by performer works through scenes")
    void movieLookupByPerformerWorksThroughScenes() throws Exception {
        Assertions.assertEquals(
                List.of(MOVIE_ONE_ID),
                movieIds(repository.findMoviesByPerformer(PERFORMER_B_ID))
        );
    }

    @Test
    @DisplayName("Shared-movie lookup works")
    void sharedMovieLookupWorks() throws Exception {
        Assertions.assertEquals(
                List.of(MOVIE_ONE_ID),
                movieIds(repository.findMoviesSharedByPerformers(
                        PERFORMER_A_ID,
                        PERFORMER_C_ID
                ))
        );
    }

    @Test
    @DisplayName("Movie lookup requiring all selected performers works")
    void movieLookupRequiringAllSelectedPerformersWorks()
            throws Exception {

        Assertions.assertEquals(
                List.of(MOVIE_ONE_ID),
                movieIds(repository.findMoviesByAllPerformers(
                        List.of(PERFORMER_A_ID, PERFORMER_B_ID, PERFORMER_C_ID)
                ))
        );
    }

    @Test
    @DisplayName("Co-performer lookup excludes selected performer")
    void coPerformerLookupExcludesSelectedPerformer() throws Exception {
        Assertions.assertFalse(
                performerIds(repository.findCoPerformers(PERFORMER_A_ID))
                        .contains(PERFORMER_A_ID)
        );
    }

    @Test
    @DisplayName("Co-performer results contain no duplicates")
    void coPerformerResultsContainNoDuplicates() throws Exception {
        Assertions.assertEquals(
                List.of(PERFORMER_B_ID, PERFORMER_C_ID),
                performerIds(repository.findCoPerformers(PERFORMER_A_ID))
        );
    }

    @Test
    @DisplayName("Scene-title search is case-insensitive and partial")
    void sceneTitleSearchIsCaseInsensitiveAndPartial() throws Exception {
        Assertions.assertEquals(
                List.of(SCENE_ONE_ID),
                sceneIds(repository.searchScenesByTitle("alpha"))
        );
    }

    @Test
    @DisplayName("Movie-title search is case-insensitive and partial")
    void movieTitleSearchIsCaseInsensitiveAndPartial() throws Exception {
        Assertions.assertEquals(
                List.of(MOVIE_TWO_ID),
                movieIds(repository.searchMoviesByTitle("BETA"))
        );
    }

    @Test
    @DisplayName("Scene-code search works")
    void sceneCodeSearchWorks() throws Exception {
        Assertions.assertEquals(
                List.of(SCENE_TWO_ID),
                sceneIds(repository.searchScenesByCode("002"))
        );
    }

    @Test
    @DisplayName("Scene release-date range filtering is inclusive")
    void sceneReleaseDateRangeFilteringIsInclusive() throws Exception {
        Assertions.assertEquals(
                List.of(SCENE_ONE_ID, SCENE_TWO_ID),
                sceneIds(repository.findScenesByReleaseDateRange(
                        SCENE_ONE_DATE,
                        SCENE_TWO_DATE
                ))
        );
    }

    @Test
    @DisplayName("Movie release-date range filtering is inclusive")
    void movieReleaseDateRangeFilteringIsInclusive() throws Exception {
        Assertions.assertEquals(
                List.of(MOVIE_ONE_ID, MOVIE_TWO_ID),
                movieIds(repository.findMoviesByReleaseDateRange(
                        MOVIE_ONE_DATE,
                        MOVIE_TWO_DATE
                ))
        );
    }

    @Test
    @DisplayName("Publisher filtering works")
    void publisherFilteringWorks() throws Exception {
        Assertions.assertEquals(
                List.of(SCENE_ONE_ID, SCENE_TWO_ID),
                sceneIds(repository.findScenesByPublisher(PUBLISHER_ONE_ID))
        );
    }

    @Test
    @DisplayName("Series filtering works")
    void seriesFilteringWorks() throws Exception {
        Assertions.assertEquals(
                List.of(SCENE_THREE_ID),
                sceneIds(repository.findScenesBySeries(SERIES_TWO_ID))
        );
    }

    @Test
    @DisplayName("Empty performer-input behavior returns empty results")
    void emptyPerformerInputBehaviorReturnsEmptyResults()
            throws Exception {

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        repository.findScenesByAllPerformers(List.of())
                                .isEmpty()
                ),
                () -> Assertions.assertTrue(
                        repository.findMoviesByAllPerformers(List.of())
                                .isEmpty()
                )
        );
    }

    @Test
    @DisplayName("Returned collections use deterministic ordering")
    void returnedCollectionsUseDeterministicOrdering() throws Exception {
        Assertions.assertEquals(
                List.of(SCENE_ONE_ID, SCENE_TWO_ID, SCENE_THREE_ID),
                sceneIds(repository.searchScenesByTitle("scene"))
        );
    }

    private void createFixture(DatabaseManager databaseManager)
            throws Exception {

        final Publisher publisherOne = new Publisher(
                PUBLISHER_ONE_ID,
                "Publisher One",
                List.of()
        );
        final Publisher publisherTwo = new Publisher(
                PUBLISHER_TWO_ID,
                "Publisher Two",
                List.of()
        );
        final Performer performerA = new Performer(
                PERFORMER_A_ID,
                "Alice",
                List.of("A One"),
                PerformerCategory.ACTRESS
        );
        final Performer performerB = new Performer(
                PERFORMER_B_ID,
                "Bob",
                List.of(),
                PerformerCategory.ACTOR
        );
        final Performer performerC = new Performer(
                PERFORMER_C_ID,
                "Cara",
                List.of(),
                PerformerCategory.OTHER
        );
        final Series seriesOne = new Series(
                SERIES_ONE_ID,
                "Series One",
                publisherOne
        );
        final Series seriesTwo = new Series(
                SERIES_TWO_ID,
                "Series Two",
                publisherTwo
        );
        final Scene sceneOne = new Scene(
                SCENE_ONE_ID,
                "Alpha Scene",
                publisherOne,
                SCENE_ONE_DATE,
                "A-001",
                seriesOne,
                null,
                null,
                List.of(performerA, performerB),
                List.of()
        );
        final Scene sceneTwo = new Scene(
                SCENE_TWO_ID,
                "Beta Scene",
                publisherOne,
                SCENE_TWO_DATE,
                "B-002",
                seriesOne,
                null,
                null,
                List.of(performerA, performerC),
                List.of()
        );
        final Scene sceneThree = new Scene(
                SCENE_THREE_ID,
                "Gamma Scene",
                publisherTwo,
                SCENE_THREE_DATE,
                "G-003",
                seriesTwo,
                null,
                null,
                List.of(performerC),
                List.of()
        );

        new PublisherRepository(databaseManager).insert(publisherOne);
        new PublisherRepository(databaseManager).insert(publisherTwo);
        new PerformerRepository(databaseManager).insert(performerA);
        new PerformerRepository(databaseManager).insert(performerB);
        new PerformerRepository(databaseManager).insert(performerC);
        new SeriesRepository(databaseManager).insert(seriesOne);
        new SeriesRepository(databaseManager).insert(seriesTwo);
        new SceneRepository(databaseManager).insert(sceneOne);
        new SceneRepository(databaseManager).insert(sceneTwo);
        new SceneRepository(databaseManager).insert(sceneThree);
        new MovieRepository(databaseManager).insert(new Movie(
                MOVIE_ONE_ID,
                "Alpha Movie",
                MOVIE_ONE_DATE,
                publisherOne,
                List.of(sceneOne, sceneTwo),
                true,
                List.of()
        ));
        new MovieRepository(databaseManager).insert(new Movie(
                MOVIE_TWO_ID,
                "Beta Movie",
                MOVIE_TWO_DATE,
                publisherTwo,
                List.of(sceneThree),
                false,
                List.of()
        ));
    }

    private List<UUID> sceneIds(List<Scene> scenes) {
        return scenes.stream().map(Scene::getId).toList();
    }

    private List<UUID> movieIds(List<Movie> movies) {
        return movies.stream().map(Movie::getId).toList();
    }

    private List<UUID> performerIds(List<Performer> performers) {
        return performers.stream().map(Performer::getId).toList();
    }
}

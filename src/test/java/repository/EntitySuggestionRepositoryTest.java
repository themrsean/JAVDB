package repository;

import database.DatabaseManager;
import database.SchemaManager;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Series;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class EntitySuggestionRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "entity-suggestion-repository-test.db";
    private static final int DEFAULT_LIMIT =
            SuggestionQuery.DEFAULT_LIMIT;
    private static final UUID PUBLISHER_ONE_ID =
            UUID.fromString("11111111-cafe-1111-cafe-111111111111");
    private static final UUID PUBLISHER_TWO_ID =
            UUID.fromString("22222222-cafe-2222-cafe-222222222222");
    private static final UUID PERFORMER_ONE_ID =
            UUID.fromString("33333333-cafe-3333-cafe-333333333333");
    private static final UUID PERFORMER_TWO_ID =
            UUID.fromString("44444444-cafe-4444-cafe-444444444444");
    private static final UUID SERIES_ONE_ID =
            UUID.fromString("55555555-cafe-5555-cafe-555555555555");
    private static final UUID SERIES_TWO_ID =
            UUID.fromString("66666666-cafe-6666-cafe-666666666666");
    private static final UUID MOVIE_ONE_ID =
            UUID.fromString("77777777-cafe-7777-cafe-777777777777");
    private static final UUID MOVIE_TWO_ID =
            UUID.fromString("88888888-cafe-8888-cafe-888888888888");

    @TempDir
    Path temporaryDirectory;

    private EntitySuggestionRepository repository;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        repository = new EntitySuggestionRepository(databaseManager);

        final Publisher publisherOne = new Publisher(
                PUBLISHER_ONE_ID,
                "Alpha Studio",
                List.of("A Studio")
        );
        final Publisher publisherTwo = new Publisher(
                PUBLISHER_TWO_ID,
                "Beta Studio",
                List.of("B Studio")
        );
        final Performer performerOne = new Performer(
                PERFORMER_ONE_ID,
                "Alex Performer",
                List.of("A Performer", "Shared Alias"),
                PerformerCategory.ACTOR
        );
        final Performer performerTwo = new Performer(
                PERFORMER_TWO_ID,
                "Blair Performer",
                List.of("Shared Alias"),
                PerformerCategory.ACTRESS
        );
        final Series seriesOne = new Series(
                SERIES_ONE_ID,
                "Alpha Series",
                publisherOne
        );
        final Series seriesTwo = new Series(
                SERIES_TWO_ID,
                "Alpha Series",
                publisherTwo
        );
        final Movie movieOne = new Movie(
                MOVIE_ONE_ID,
                "Alpha Movie",
                null,
                publisherOne,
                List.of(),
                false,
                List.of()
        );
        final Movie movieTwo = new Movie(
                MOVIE_TWO_ID,
                "Alpha Movie",
                null,
                publisherTwo,
                List.of(),
                false,
                List.of()
        );

        new PublisherRepository(databaseManager).insert(publisherOne);
        new PublisherRepository(databaseManager).insert(publisherTwo);
        new PerformerRepository(databaseManager).insert(performerOne);
        new PerformerRepository(databaseManager).insert(performerTwo);
        new SeriesRepository(databaseManager).insert(seriesOne);
        new SeriesRepository(databaseManager).insert(seriesTwo);
        new MovieRepository(databaseManager).insert(movieOne);
        new MovieRepository(databaseManager).insert(movieTwo);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new EntitySuggestionRepository(null)
        );
    }

    @Test
    @DisplayName("Performer suggestions rank exact main, alias, prefix, and substring")
    void performerSuggestionsRankMatches() throws Exception {
        final List<EntitySuggestion> exact =
                repository.suggestPerformers("alex performer", DEFAULT_LIMIT);
        final List<EntitySuggestion> alias =
                repository.suggestPerformers("A Performer", DEFAULT_LIMIT);
        final List<EntitySuggestion> prefix =
                repository.suggestPerformers("Alex", DEFAULT_LIMIT);
        final List<EntitySuggestion> substring =
                repository.suggestPerformers("Perform", DEFAULT_LIMIT);

        Assertions.assertAll(
                () -> Assertions.assertEquals(PERFORMER_ONE_ID,
                        exact.getFirst().id()),
                () -> Assertions.assertEquals(MatchField.PRIMARY,
                        exact.getFirst().matchField()),
                () -> Assertions.assertEquals(PERFORMER_ONE_ID,
                        alias.getFirst().id()),
                () -> Assertions.assertEquals(MatchField.ALIAS,
                        alias.getFirst().matchField()),
                () -> Assertions.assertEquals(MatchRank.PRIMARY_PREFIX,
                        prefix.getFirst().rank()),
                () -> Assertions.assertEquals(MatchRank.PRIMARY_SUBSTRING,
                        substring.getFirst().rank())
        );
    }

    @Test
    @DisplayName("Performer matching through multiple aliases appears once")
    void performerMatchingThroughMultipleAliasesAppearsOnce() throws Exception {
        final List<EntitySuggestion> suggestions =
                repository.suggestPerformers("Shared Alias", DEFAULT_LIMIT);

        Assertions.assertEquals(
                List.of(PERFORMER_ONE_ID, PERFORMER_TWO_ID),
                suggestions.stream().map(EntitySuggestion::id).toList()
        );
    }

    @Test
    @DisplayName("Publisher suggestions include aliases and are case insensitive")
    void publisherSuggestionsIncludeAliasesAndAreCaseInsensitive()
            throws Exception {

        final List<EntitySuggestion> suggestions =
                repository.suggestPublishers("a studio", DEFAULT_LIMIT);

        Assertions.assertAll(
                () -> Assertions.assertEquals(PUBLISHER_ONE_ID,
                        suggestions.getFirst().id()),
                () -> Assertions.assertEquals(MatchField.ALIAS,
                        suggestions.getFirst().matchField())
        );
    }

    @Test
    @DisplayName("Series and movie suggestions can be filtered by publisher")
    void seriesAndMovieSuggestionsCanBeFilteredByPublisher()
            throws Exception {

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(SERIES_ONE_ID),
                        repository.suggestSeries(
                                "Alpha Series",
                                PUBLISHER_ONE_ID,
                                DEFAULT_LIMIT
                        ).stream().map(EntitySuggestion::id).toList()
                ),
                () -> Assertions.assertEquals(
                        List.of(MOVIE_TWO_ID),
                        repository.suggestMovies(
                                "Alpha Movie",
                                PUBLISHER_TWO_ID,
                                DEFAULT_LIMIT
                        ).stream().map(EntitySuggestion::id).toList()
                )
        );
    }

    @Test
    @DisplayName("Suggestions enforce limit and invalid limits")
    void suggestionsEnforceLimitAndInvalidLimits() throws Exception {
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        1,
                        repository.suggestPerformers("performer", 1).size()
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> repository.suggestPerformers("performer", 0)
                )
        );
    }

    @Test
    @DisplayName("Unknown and blank query behavior is deterministic")
    void unknownAndBlankQueryBehaviorIsDeterministic() throws Exception {
        Assertions.assertAll(
                () -> Assertions.assertTrue(repository.suggestPublishers(
                        "not found",
                        DEFAULT_LIMIT
                ).isEmpty()),
                () -> Assertions.assertEquals(
                        List.of(PUBLISHER_ONE_ID, PUBLISHER_TWO_ID),
                        repository.suggestPublishers(
                                " ",
                                DEFAULT_LIMIT
                        ).stream().map(EntitySuggestion::id).toList()
                )
        );
    }
}

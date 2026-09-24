package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
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
import repository.EntitySuggestionRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SeriesRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class FilenameMetadataMatcherTest {
    private static final String DATABASE_FILE_NAME =
            "filename-metadata-matcher-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-face-1111-face-111111111111");
    private static final UUID OTHER_PUBLISHER_ID =
            UUID.fromString("22222222-face-2222-face-222222222222");
    private static final UUID SERIES_ID =
            UUID.fromString("33333333-face-3333-face-333333333333");
    private static final UUID UNIQUE_SERIES_ID =
            UUID.fromString("33333333-face-4444-face-333333333333");
    private static final UUID MOVIE_ID =
            UUID.fromString("44444444-face-4444-face-444444444444");
    private static final UUID PERFORMER_ID =
            UUID.fromString("55555555-face-5555-face-555555555555");
    private static final UUID OTHER_PERFORMER_ID =
            UUID.fromString("66666666-face-6666-face-666666666666");

    private FilenameMetadataMatcher matcher;
    private MediaFilenameParser parser;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        matcher = new FilenameMetadataMatcher(
                new EntitySuggestionRepository(databaseManager)
        );
        parser = new MediaFilenameParser();

        final Publisher publisher = new Publisher(
                PUBLISHER_ID,
                "Studio",
                List.of("Studio Alias")
        );
        final Publisher otherPublisher = new Publisher(
                OTHER_PUBLISHER_ID,
                "Series",
                List.of()
        );
        final Series series = new Series(SERIES_ID, "Series", publisher);
        final Series uniqueSeries = new Series(
                UNIQUE_SERIES_ID,
                "Unique Series",
                publisher
        );
        final Movie movie = new Movie(
                MOVIE_ID,
                "Movie",
                null,
                publisher,
                List.of(),
                false,
                List.of()
        );
        final Performer performer = new Performer(
                PERFORMER_ID,
                "Performer One",
                List.of("P One"),
                PerformerCategory.ACTOR
        );
        final Performer otherPerformer = new Performer(
                OTHER_PERFORMER_ID,
                "Other Performer",
                List.of("Shared Name"),
                PerformerCategory.ACTRESS
        );

        new PublisherRepository(databaseManager).insert(publisher);
        new PublisherRepository(databaseManager).insert(otherPublisher);
        new SeriesRepository(databaseManager).insert(series);
        new SeriesRepository(databaseManager).insert(uniqueSeries);
        new MovieRepository(databaseManager).insert(movie);
        new PerformerRepository(databaseManager).insert(performer);
        new PerformerRepository(databaseManager).insert(otherPerformer);
    }

    @Test
    @DisplayName("Ready interpretation resolves publisher series movie and performer")
    void readyInterpretationResolvesPublisherSeriesMovieAndPerformer()
            throws Exception {

        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Studio - Series - Movie - Scene Title - Performer One.mp4"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        result.status()),
                () -> Assertions.assertEquals(PUBLISHER_ID,
                        result.bestInterpretation().publisher().id()),
                () -> Assertions.assertEquals(SERIES_ID,
                        result.bestInterpretation().series().id()),
                () -> Assertions.assertEquals(MOVIE_ID,
                        result.bestInterpretation().movie().id()),
                () -> Assertions.assertEquals(List.of(PERFORMER_ID),
                        result.bestInterpretation().performers()
                                .stream().map(EntityMatch::id).toList())
        );
    }

    @Test
    @DisplayName("Publisher can be inferred from exact series")
    void publisherCanBeInferredFromExactSeries() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Unique Series - Scene Title - P One.mp4"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        result.status()),
                () -> Assertions.assertEquals(MatchSource.INFERRED_FROM_SERIES,
                        result.bestInterpretation().publisher().source()),
                () -> Assertions.assertEquals(MatchSource.EXPLICIT_ALIAS,
                        result.bestInterpretation().performers()
                                .getFirst().source())
        );
    }

    @Test
    @DisplayName("Ambiguous context is retained")
    void ambiguousContextIsRetained() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Series - Scene Title - Performer One.mp4"
        )));

        Assertions.assertEquals(FilenameMatchStatus.AMBIGUOUS,
                result.status());
        Assertions.assertTrue(result.interpretations().size() > 1);
    }

    @Test
    @DisplayName("Unknown performer is unresolved")
    void unknownPerformerIsUnresolved() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Studio - Scene Title - Missing Performer.mp4"
        )));

        Assertions.assertEquals(FilenameMatchStatus.UNRESOLVED,
                result.status());
    }

    @Test
    @DisplayName("Invalid filename remains invalid")
    void invalidFilenameRemainsInvalid() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "Studio - Scene Title - Performer One.mp4"
        )));

        Assertions.assertEquals(FilenameMatchStatus.INVALID_FILENAME,
                result.status());
    }
}

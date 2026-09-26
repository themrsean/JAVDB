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
    private static final UUID OTHER_MOVIE_ID =
            UUID.fromString("44444444-face-5555-face-444444444444");
    private static final UUID PERFORMER_ID =
            UUID.fromString("55555555-face-5555-face-555555555555");
    private static final UUID OTHER_PERFORMER_ID =
            UUID.fromString("66666666-face-6666-face-666666666666");
    private static final UUID BRAZZERS_ID =
            UUID.fromString("77777777-face-7777-face-777777777777");
    private static final UUID BIG_WET_BUTTS_ID =
            UUID.fromString("88888888-face-8888-face-888888888888");
    private static final UUID ABELLA_DANGER_ID =
            UUID.fromString("99999999-face-9999-face-999999999999");
    private static final UUID ASSPIRATIONS_ID =
            UUID.fromString("aaaaaaaa-face-aaaa-face-aaaaaaaaaaaa");

    private FilenameMetadataMatcher matcher;
    private MediaFilenameParser parser;
    private MovieRepository movieRepository;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        matcher = new FilenameMetadataMatcher(
                new EntitySuggestionRepository(databaseManager),
                new PublisherRepository(databaseManager)
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
        movieRepository = new MovieRepository(databaseManager);
        movieRepository.insert(movie);
        new PerformerRepository(databaseManager).insert(performer);
        new PerformerRepository(databaseManager).insert(otherPerformer);
        final Publisher brazzers = new Publisher(
                BRAZZERS_ID, "Brazzers", List.of()
        );
        new PublisherRepository(databaseManager).insert(brazzers);
        new SeriesRepository(databaseManager).insert(new Series(
                BIG_WET_BUTTS_ID, "BigWetButts", brazzers
        ));
        new PerformerRepository(databaseManager).insert(new Performer(
                ABELLA_DANGER_ID,
                "Abella Danger",
                List.of(),
                PerformerCategory.ACTRESS
        ));
    }

    @Test
    @DisplayName("Strong partial retains unresolved movie and resolves after creation")
    void strongPartialRetainsUnresolvedMovieAndResolvesAfterCreation()
            throws Exception {

        final Path path = Path.of(
                "(15.02.08) Brazzers - BigWetButts - Asspirations 2 - "
                        + "Abella's Ass Is In Danger - Abella Danger"
        );
        final FilenameMatchResult partial = matcher.match(parser.parse(path));
        final FilenameInterpretation interpretation =
                partial.bestInterpretation();

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.UNRESOLVED,
                        partial.status()),
                () -> Assertions.assertNotNull(interpretation),
                () -> Assertions.assertEquals(BRAZZERS_ID,
                        interpretation.publisher().id()),
                () -> Assertions.assertEquals("Brazzers",
                        interpretation.publisher().name()),
                () -> Assertions.assertEquals(BIG_WET_BUTTS_ID,
                        interpretation.series().id()),
                () -> Assertions.assertEquals("BigWetButts",
                        interpretation.series().name()),
                () -> Assertions.assertNull(interpretation.movie().id()),
                () -> Assertions.assertEquals("Asspirations 2",
                        interpretation.movie().candidateText()),
                () -> Assertions.assertEquals(MatchSource.UNMATCHED,
                        interpretation.movie().source()),
                () -> Assertions.assertEquals(List.of("Asspirations 2"),
                        interpretation.unresolvedSegments())
        );

        movieRepository.insert(new Movie(
                ASSPIRATIONS_ID,
                "Asspirations 2",
                null,
                new Publisher(BRAZZERS_ID, "Brazzers", List.of()),
                List.of(),
                false,
                List.of()
        ));
        final FilenameMatchResult resolved = matcher.match(parser.parse(path));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        resolved.status()),
                () -> Assertions.assertEquals(ASSPIRATIONS_ID,
                        resolved.bestInterpretation().movie().id())
        );
    }

    @Test
    @DisplayName("Publisher plus unknown context retains tied series and movie candidates")
    void publisherPlusUnknownContextRetainsRoleAmbiguity() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(15.02.08) Brazzers - Unknown Name - Scene Title - Abella Danger"
        )));
        final List<FilenameInterpretation> top = result.interpretations().stream()
                .filter(value -> value.score()
                        == result.bestInterpretation().score())
                .toList();

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.AMBIGUOUS,
                        result.status()),
                () -> Assertions.assertEquals(2, top.size()),
                () -> Assertions.assertTrue(top.stream().anyMatch(value ->
                        "Unknown Name".equals(value.series().candidateText())
                                && value.series().source()
                                == MatchSource.UNMATCHED
                                && value.movie().source() == MatchSource.ABSENT)),
                () -> Assertions.assertTrue(top.stream().anyMatch(value ->
                        "Unknown Name".equals(value.movie().candidateText())
                                && value.movie().source()
                                == MatchSource.UNMATCHED
                                && value.series().source() == MatchSource.ABSENT))
        );
    }

    @Test
    @DisplayName("Publisher plus exact series prefers complete series interpretation")
    void publisherPlusExactSeriesIsReady() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(15.02.08) Brazzers - BigWetButts - Scene Title - Abella Danger"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        result.status()),
                () -> Assertions.assertEquals(BIG_WET_BUTTS_ID,
                        result.bestInterpretation().series().id()),
                () -> Assertions.assertEquals(MatchSource.ABSENT,
                        result.bestInterpretation().movie().source())
        );
    }

    @Test
    @DisplayName("Publisher plus exact movie prefers complete movie interpretation")
    void publisherPlusExactMovieIsReady() throws Exception {
        movieRepository.insert(new Movie(ASSPIRATIONS_ID, "Asspirations 2", null,
                new Publisher(BRAZZERS_ID, "Brazzers", List.of()), List.of(),
                false, List.of()));

        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(15.02.08) Brazzers - Asspirations 2 - Scene Title - Abella Danger"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        result.status()),
                () -> Assertions.assertEquals(ASSPIRATIONS_ID,
                        result.bestInterpretation().movie().id()),
                () -> Assertions.assertEquals(MatchSource.ABSENT,
                        result.bestInterpretation().series().source())
        );
    }

    @Test
    @DisplayName("Series plus unknown context infers publisher and types movie candidate")
    void seriesPlusUnknownContextInfersPublisherAndTypesMovie() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(15.02.08) BigWetButts - Asspirations 2 - Scene Title - Abella Danger"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.UNRESOLVED,
                        result.status()),
                () -> Assertions.assertEquals(BRAZZERS_ID,
                        result.bestInterpretation().publisher().id()),
                () -> Assertions.assertEquals(MatchSource.INFERRED_FROM_SERIES,
                        result.bestInterpretation().publisher().source()),
                () -> Assertions.assertEquals(BIG_WET_BUTTS_ID,
                        result.bestInterpretation().series().id()),
                () -> Assertions.assertEquals("Asspirations 2",
                        result.bestInterpretation().movie().candidateText()),
                () -> Assertions.assertEquals(List.of("Asspirations 2"),
                        result.bestInterpretation().unresolvedSegments())
        );
    }

    @Test
    @DisplayName("Fully unknown context does not create speculative interpretations")
    void fullyUnknownContextDoesNotCreateSpeculativeInterpretations()
            throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(15.02.08) Unknown One - Unknown Two - Scene Title - Abella Danger"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.UNRESOLVED,
                        result.status()),
                () -> Assertions.assertNull(result.bestInterpretation()),
                () -> Assertions.assertTrue(result.interpretations().isEmpty())
        );
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
                () -> Assertions.assertEquals("Studio",
                        result.bestInterpretation().publisher().name()),
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
                () -> Assertions.assertEquals(PUBLISHER_ID,
                        result.bestInterpretation().publisher().id()),
                () -> Assertions.assertEquals("Studio",
                        result.bestInterpretation().publisher().name()),
                () -> Assertions.assertEquals(MatchSource.INFERRED_FROM_SERIES,
                        result.bestInterpretation().publisher().source()),
                () -> Assertions.assertEquals(MatchSource.EXPLICIT_ALIAS,
                        result.bestInterpretation().performers()
                                .getFirst().source())
        );
    }

    @Test
    @DisplayName("Publisher can be inferred from exact movie")
    void publisherCanBeInferredFromExactMovie() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Movie - Scene Title - P One.mp4"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        result.status()),
                () -> Assertions.assertEquals(PUBLISHER_ID,
                        result.bestInterpretation().publisher().id()),
                () -> Assertions.assertEquals("Studio",
                        result.bestInterpretation().publisher().name()),
                () -> Assertions.assertEquals(MatchSource.INFERRED_FROM_MOVIE,
                        result.bestInterpretation().publisher().source())
        );
    }

    @Test
    @DisplayName("Series and movie sharing a publisher infer its real name")
    void seriesAndMovieWithSharedPublisherInferNamedPublisher() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Unique Series - Movie - Scene Title - P One.mp4"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.READY,
                        result.status()),
                () -> Assertions.assertEquals(PUBLISHER_ID,
                        result.bestInterpretation().publisher().id()),
                () -> Assertions.assertEquals("Studio",
                        result.bestInterpretation().publisher().name()),
                () -> Assertions.assertEquals(MatchSource.INFERRED_FROM_SERIES,
                        result.bestInterpretation().publisher().source())
        );
    }

    @Test
    @DisplayName("Mismatched series and movie publisher relationships remain rejected")
    void mismatchedSeriesAndMoviePublisherRelationshipsRemainRejected()
            throws Exception {
        movieRepository.insert(new Movie(OTHER_MOVIE_ID, "Other Movie", null,
                new Publisher(OTHER_PUBLISHER_ID, "Series", List.of()), List.of(),
                false, List.of()));

        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Unique Series - Other Movie - Scene Title - P One.mp4"
        )));

        Assertions.assertEquals(FilenameMatchStatus.UNRESOLVED, result.status());
    }

    @Test
    @DisplayName("Explicit publisher conflict cannot resolve known foreign series")
    void explicitPublisherConflictRejectsForeignSeries() throws Exception {
        final UUID foreignSeriesId = UUID.fromString(
                "abababab-face-abab-face-abababababab"
        );
        new SeriesRepository(new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        )).insert(new Series(
                foreignSeriesId,
                "Foreign Series",
                new Publisher(OTHER_PUBLISHER_ID, "Series", List.of())
        ));

        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Studio - Foreign Series - Scene Title - Performer One.mp4"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameMatchStatus.UNRESOLVED,
                        result.status()),
                () -> Assertions.assertTrue(result.interpretations().stream()
                        .noneMatch(value -> foreignSeriesId.equals(
                                value.series().id())))
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
    @DisplayName("No-context filename requires review instead of READY")
    void noContextFilenameRequiresReviewInsteadOfReady() throws Exception {
        final FilenameMatchResult result = matcher.match(parser.parse(Path.of(
                "(25.01.02) Scene Title - Performer One.mp4"
        )));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        FilenameMatchStatus.REVIEW_REQUIRED,
                        result.status()
                ),
                () -> Assertions.assertNull(result.bestInterpretation())
        );
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

package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import model.Movie;
import model.Publisher;
import model.Series;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.EntitySuggestionRepository;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PublisherRepository;
import repository.SeriesRepository;
import repository.UnassignedMediaPathRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

class ContextCandidateReviewServiceTest {
    @TempDir Path temporaryDirectory;
    private MediaFileRepository mediaFiles;
    private PublisherRepository publishers;
    private SeriesRepository series;
    private MovieRepository movies;
    private ContextCandidateReviewService service;

    @BeforeEach
    void setUp() throws Exception {
        final DatabaseManager database = new DatabaseManager(
                temporaryDirectory.resolve("context.db"));
        new SchemaManager(database).initialize();
        mediaFiles = new MediaFileRepository(database);
        publishers = new PublisherRepository(database);
        series = new SeriesRepository(database);
        movies = new MovieRepository(database);
        service = new ContextCandidateReviewService(
                new UnassignedMediaPathRepository(database),
                new MediaFilenameParser(),
                new EntitySuggestionRepository(database), publishers);
    }

    @Test
    void aggregatesValidSegmentsWithPositionEvidenceAndNoPerFileDuplicates()
            throws Exception {
        add("(25.01.01) Studio - studio - Title - Alice.mp4");
        add("(25.01.02) studio - Series - Title - Alice.mp4");
        add("not a valid filename.mp4");

        final ContextCandidate studio = candidate("Studio");
        Assertions.assertAll(
                () -> Assertions.assertEquals(2, studio.occurrences()),
                () -> Assertions.assertEquals(2, studio.positionCounts().get(1)),
                () -> Assertions.assertEquals(2, studio.contextLengthCounts().get(2)),
                () -> Assertions.assertEquals(ContextCandidateStatus.UNRESOLVED,
                        studio.status()),
                () -> Assertions.assertEquals(2, studio.representativePaths().size()),
                () -> Assertions.assertEquals(2, service.loadCandidates().size())
        );
    }

    @Test
    void reportsExactPublisherSeriesMovieAndMultipleMatches() throws Exception {
        final Publisher publisher = new Publisher(UUID.randomUUID(), "Publisher", List.of());
        publishers.insert(publisher);
        series.insert(new Series(UUID.randomUUID(), "Series", publisher));
        movies.insert(new Movie(UUID.randomUUID(), "Movie", null, publisher,
                List.of(), false, List.of()));
        series.insert(new Series(UUID.randomUUID(), "Shared", publisher));
        movies.insert(new Movie(UUID.randomUUID(), "Shared", null, publisher,
                List.of(), false, List.of()));
        add("(25.01.01) Publisher - Series - Movie - Shared - Title - Alice.mp4");

        final ContextCandidate seriesMatch = candidate("Series");
        final ContextCandidate movieMatch = candidate("Movie");
        Assertions.assertAll(
                () -> Assertions.assertEquals(ContextCandidateStatus.PUBLISHER_MATCH,
                        candidate("Publisher").status()),
                () -> Assertions.assertEquals(ContextCandidateStatus.SERIES_MATCH,
                        seriesMatch.status()),
                () -> Assertions.assertTrue(seriesMatch.matches().getFirst()
                        .displayText().contains("Publisher: Publisher")),
                () -> Assertions.assertEquals(ContextCandidateStatus.MOVIE_MATCH,
                        movieMatch.status()),
                () -> Assertions.assertEquals(ContextCandidateStatus.MULTIPLE_ROLE_MATCHES,
                        candidate("Shared").status())
        );
    }

    @Test
    void ordersAttentionRowsByImpactThenName() throws Exception {
        add("(25.01.01) Beta - Title - Alice.mp4");
        add("(25.01.02) alpha - Title - Alice.mp4");
        add("(25.01.03) Beta - Title - Alice.mp4");
        final List<ContextCandidate> rows = service.loadCandidates();
        Assertions.assertEquals(List.of("Beta", "alpha"),
                rows.stream().map(ContextCandidate::text).toList());
    }

    @Test
    void readOnlyLoadingDoesNotCreateCatalogRecordsAndEmptyInputIsEmpty()
            throws Exception {
        add("invalid filename.mp4");
        Assertions.assertAll(
                () -> Assertions.assertTrue(service.loadCandidates().isEmpty()),
                () -> Assertions.assertTrue(publishers.findAll().isEmpty()),
                () -> Assertions.assertTrue(series.findAll().isEmpty()),
                () -> Assertions.assertTrue(movies.findAll().isEmpty())
        );
    }

    @Test
    void aggregatesExactPublisherContextOncePerFileWithRelativePositions()
            throws Exception {
        final Publisher publisher = publisher("Publisher");
        add("(25.01.01) Publisher - Unknown - Publisher - Title - Alice.mp4");
        add("(25.01.02) Publisher - Unknown - Title - Alice.mp4");
        add("(25.01.03) Unknown - Publisher - Title - Alice.mp4");

        final ContextPublisherEvidence evidence = evidence(candidate("Unknown"), publisher);
        Assertions.assertAll(
                () -> Assertions.assertEquals(ContextCandidateStatus.UNRESOLVED,
                        candidate("Unknown").status()),
                () -> Assertions.assertEquals(3, evidence.affectedFiles()),
                () -> Assertions.assertEquals(2,
                        evidence.candidateBeforePublisherFiles()),
                () -> Assertions.assertEquals(2,
                        evidence.candidateAfterPublisherFiles()),
                () -> Assertions.assertEquals(1,
                        evidence.publisherOnBothSidesFiles()),
                () -> Assertions.assertEquals(3, evidence.directPublisherFiles()),
                () -> Assertions.assertEquals(0, evidence.seriesPublisherFiles()),
                () -> Assertions.assertEquals(0, evidence.moviePublisherFiles())
        );
    }

    @Test
    void aggregatesPublisherAliasesAndSeriesAndMoviePublisherContext()
            throws Exception {
        final Publisher publisher = new Publisher(UUID.randomUUID(), "Canonical",
                List.of("Publisher Alias"));
        publishers.insert(publisher);
        series.insert(new Series(UUID.randomUUID(), "Series Context", publisher));
        movies.insert(new Movie(UUID.randomUUID(), "Movie Context", null, publisher,
                List.of(), false, List.of()));
        add("(25.01.01) Publisher Alias - Unknown - Title - Alice.mp4");
        add("(25.01.02) Series Context - Unknown - Title - Alice.mp4");
        add("(25.01.03) Movie Context - Unknown - Title - Alice.mp4");

        final ContextPublisherEvidence evidence = evidence(candidate("Unknown"), publisher);
        Assertions.assertAll(
                () -> Assertions.assertEquals(3, evidence.affectedFiles()),
                () -> Assertions.assertEquals(1, evidence.directPublisherFiles()),
                () -> Assertions.assertEquals(1, evidence.seriesPublisherFiles()),
                () -> Assertions.assertEquals(1, evidence.moviePublisherFiles()),
                () -> Assertions.assertEquals(3,
                        evidence.candidateAfterPublisherFiles())
        );
    }

    @Test
    void ignoresPrefixSuggestionsAndSortsMultiplePublisherEvidenceDeterministically()
            throws Exception {
        final Publisher alpha = publisher("Alpha Publisher");
        final Publisher beta = publisher("Beta Publisher");
        publisher("Publisher");
        add("(25.01.01) Beta Publisher - Unknown - Title - Alice.mp4");
        add("(25.01.02) Alpha Publisher - Unknown - Title - Alice.mp4");
        add("(25.01.03) Pub - Prefix Only - Title - Alice.mp4");

        final List<ContextPublisherEvidence> evidence = candidate("Unknown")
                .publisherEvidence();
        Assertions.assertAll(
                () -> Assertions.assertEquals(List.of(alpha.getName(), beta.getName()),
                        evidence.stream().map(ContextPublisherEvidence::publisherName)
                                .toList()),
                () -> Assertions.assertTrue(candidate("Prefix Only")
                        .publisherEvidence().isEmpty())
        );
    }

    private ContextCandidate candidate(String text) throws Exception {
        return service.loadCandidates().stream()
                .filter(candidate -> candidate.text().equalsIgnoreCase(text))
                .findFirst().orElseThrow();
    }

    private ContextPublisherEvidence evidence(ContextCandidate candidate,
            Publisher publisher) {
        return candidate.publisherEvidence().stream()
                .filter(evidence -> evidence.publisherId().equals(publisher.getId()))
                .findFirst().orElseThrow();
    }

    private Publisher publisher(String name) throws Exception {
        final Publisher publisher = new Publisher(UUID.randomUUID(), name, List.of());
        publishers.insert(publisher);
        return publisher;
    }

    private void add(String name) throws Exception {
        mediaFiles.insert(new MediaFile(UUID.randomUUID(),
                temporaryDirectory.resolve(name), 1, null,
                Duration.ofSeconds(1), 1, 1, 1));
    }
}

package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import model.Publisher;
import model.Series;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.EntitySuggestionRepository;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;
import repository.UnassignedMediaPathRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

class ContextSeriesResolutionServiceTest {
    @TempDir Path temporaryDirectory;
    private PublisherRepository publishers;
    private SeriesRepository series;
    private MovieRepository movies;
    private SceneRepository scenes;
    private UnassignedMediaPathRepository unassignedPaths;
    private ContextCandidateReviewService review;
    private ContextSeriesResolutionService actions;
    private EntityManagementService entityManagement;

    @BeforeEach
    void setUp() throws Exception {
        final DatabaseManager database = new DatabaseManager(
                temporaryDirectory.resolve("context-series.db"));
        new SchemaManager(database).initialize();
        final MediaFileRepository media = new MediaFileRepository(database);
        publishers = new PublisherRepository(database);
        series = new SeriesRepository(database);
        movies = new MovieRepository(database);
        scenes = new SceneRepository(database);
        unassignedPaths = new UnassignedMediaPathRepository(database);
        final PerformerRepository performers = new PerformerRepository(database);
        final CatalogService catalog = new CatalogService(publishers, performers,
                series, media, scenes, new SearchRepository(database), movies);
        entityManagement = new EntityManagementService(catalog, publishers, performers);
        review = new ContextCandidateReviewService(unassignedPaths,
                new MediaFilenameParser(), new EntitySuggestionRepository(database),
                publishers);
        actions = new ContextSeriesResolutionService(review, entityManagement);
        final Path mediaPath = temporaryDirectory.resolve(
                "(25.01.01) Series Candidate - Title - Alice.mp4");
        Files.writeString(mediaPath, "fixture");
        media.insert(new MediaFile(UUID.randomUUID(), mediaPath,
                1, null, Duration.ofSeconds(1), 1, 1, 1));
    }

    @Test
    void explicitCreationRequiresTheSelectedPublisherAndShowsSeriesEvidence()
            throws Exception {
        final Publisher publisher = entityManagement.createPublisher(
                "Explicit Publisher", List.of());
        final Series created = actions.createSeries("Series Candidate",
                "Series Candidate", publisher.getId());
        final ContextCandidate candidate = review.loadCandidates().stream()
                .filter(value -> value.text().equals("Series Candidate"))
                .findFirst().orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(publisher.getId(),
                        created.getPublisher().getId()),
                () -> Assertions.assertEquals(ContextCandidateStatus.SERIES_MATCH,
                        candidate.status()),
                () -> Assertions.assertEquals("Explicit Publisher",
                        candidate.matches().getFirst().publisherName())
        );
    }

    @Test
    void noPublisherIsInferredOrCreated() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> actions.createSeries("Series Candidate", "Series Candidate", null));
        Assertions.assertAll(
                () -> Assertions.assertTrue(publishers.findAll().isEmpty()),
                () -> Assertions.assertTrue(series.findAll().isEmpty())
        );
    }

    @Test
    void staleCandidateIsRejectedBeforeAnotherSeriesWrite() throws Exception {
        final Publisher publisher = entityManagement.createPublisher("Publisher", List.of());
        entityManagement.createSeries("Series Candidate", publisher.getId());

        Assertions.assertAll(
                () -> Assertions.assertThrows(ContextCandidateResolvedException.class,
                        () -> actions.createSeries("Series Candidate",
                                "Series Candidate", publisher.getId())),
                () -> Assertions.assertEquals(1, series.findAll().size())
        );
    }

    @Test
    void seriesActionDoesNotCreateMoviesScenesOrIndexMedia() throws Exception {
        final Publisher publisher = entityManagement.createPublisher("Publisher", List.of());
        actions.createSeries("Series Candidate", "Series Candidate", publisher.getId());

        Assertions.assertAll(
                () -> Assertions.assertTrue(movies.findAll().isEmpty()),
                () -> Assertions.assertTrue(scenes.findAll().isEmpty()),
                () -> Assertions.assertEquals(1, unassignedPaths.findAll().size())
        );
    }
}

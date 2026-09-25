package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import model.Movie;
import model.Publisher;
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

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class ContextMovieResolutionServiceTest {
    @TempDir Path temporaryDirectory;
    private PublisherRepository publishers;
    private SeriesRepository series;
    private MovieRepository movies;
    private SceneRepository scenes;
    private UnassignedMediaPathRepository unassignedPaths;
    private ContextCandidateReviewService review;
    private ContextMovieResolutionService actions;
    private EntityManagementService entityManagement;

    @BeforeEach
    void setUp() throws Exception {
        final DatabaseManager database = new DatabaseManager(
                temporaryDirectory.resolve("context-movie.db"));
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
        actions = new ContextMovieResolutionService(review, entityManagement);
        media.insert(new MediaFile(UUID.randomUUID(), temporaryDirectory.resolve(
                "(25.01.01) Movie Candidate - Title - Alice.mp4"),
                1, null, Duration.ofSeconds(1), 1, 1, 1));
    }

    @Test
    void explicitCreationUsesSelectedPublisherAndShowsMovieEvidence()
            throws Exception {
        final Publisher publisher = entityManagement.createPublisher(
                "Explicit Publisher", List.of());
        final Movie created = actions.createMovie("Movie Candidate",
                "Movie Candidate", null, publisher.getId(), false);
        final ContextCandidate candidate = review.loadCandidates().stream()
                .filter(value -> value.text().equals("Movie Candidate"))
                .findFirst().orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(publisher.getId(),
                        created.getPublisher().getId()),
                () -> Assertions.assertEquals(ContextCandidateStatus.MOVIE_MATCH,
                        candidate.status()),
                () -> Assertions.assertEquals("Explicit Publisher",
                        candidate.matches().getFirst().publisherName()),
                () -> Assertions.assertNull(created.getReleaseDate()),
                () -> Assertions.assertFalse(created.isCompilation()),
                () -> Assertions.assertTrue(created.getScenes().isEmpty()),
                () -> Assertions.assertTrue(created.getFiles().isEmpty())
        );
    }

    @Test
    void explicitReleaseDateAndCompilationArePreserved() throws Exception {
        final Publisher publisher = entityManagement.createPublisher("Publisher", List.of());
        final Movie created = actions.createMovie("Movie Candidate",
                "Movie Candidate", LocalDate.of(2020, 2, 3), publisher.getId(), true);

        Assertions.assertAll(
                () -> Assertions.assertEquals(LocalDate.of(2020, 2, 3),
                        created.getReleaseDate()),
                () -> Assertions.assertTrue(created.isCompilation())
        );
    }

    @Test
    void noPublisherIsInferredOrCreated() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> actions.createMovie("Movie Candidate", "Movie Candidate",
                        null, null, false));
        Assertions.assertAll(
                () -> Assertions.assertTrue(publishers.findAll().isEmpty()),
                () -> Assertions.assertTrue(movies.findAll().isEmpty())
        );
    }

    @Test
    void staleCandidateIsRejectedBeforeAnotherMovieWrite() throws Exception {
        final Publisher publisher = entityManagement.createPublisher("Publisher", List.of());
        entityManagement.createMovie("Movie Candidate", null, publisher.getId(), false);

        Assertions.assertAll(
                () -> Assertions.assertThrows(ContextCandidateResolvedException.class,
                        () -> actions.createMovie("Movie Candidate", "Movie Candidate",
                                null, publisher.getId(), false)),
                () -> Assertions.assertEquals(1, movies.findAll().size())
        );
    }

    @Test
    void movieActionDoesNotCreateSeriesScenesOrIndexMedia() throws Exception {
        final Publisher publisher = entityManagement.createPublisher("Publisher", List.of());
        actions.createMovie("Movie Candidate", "Movie Candidate", null,
                publisher.getId(), false);

        Assertions.assertAll(
                () -> Assertions.assertTrue(series.findAll().isEmpty()),
                () -> Assertions.assertTrue(scenes.findAll().isEmpty()),
                () -> Assertions.assertEquals(1, unassignedPaths.findAll().size())
        );
    }
}

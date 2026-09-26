package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Series;
import model.Movie;
import model.MediaFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaFileRepository;
import repository.MediaAssignmentRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class EntityManagementServiceTest {
    private static final String DATABASE_FILE_NAME =
            "entity-management-service-test.db";

    @TempDir
    Path temporaryDirectory;

    private PublisherRepository publisherRepository;
    private PerformerRepository performerRepository;
    private SeriesRepository seriesRepository;
    private MovieRepository movieRepository;
    private MediaFileRepository mediaFileRepository;
    private MediaAssignmentRepository mediaAssignmentRepository;
    private EntityManagementService service;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        publisherRepository = new PublisherRepository(databaseManager);
        performerRepository = new PerformerRepository(databaseManager);
        seriesRepository = new SeriesRepository(databaseManager);
        movieRepository = new MovieRepository(databaseManager);
        mediaFileRepository = new MediaFileRepository(databaseManager);
        mediaAssignmentRepository = new MediaAssignmentRepository(databaseManager);
        final CatalogService catalogService = new CatalogService(
                publisherRepository,
                performerRepository,
                seriesRepository,
                mediaFileRepository,
                new SceneRepository(databaseManager),
                new SearchRepository(databaseManager),
                movieRepository
        );
        service = new EntityManagementService(
                catalogService,
                publisherRepository,
                performerRepository
        );
    }

    @Test
    @DisplayName("Creates series through catalog rules")
    void createsSeriesThroughCatalogRules() throws Exception {
        final Publisher publisher =
                service.createPublisher("Publisher", List.of());

        final Series series =
                service.createSeries(" Series ", publisher.getId());

        Assertions.assertAll(
                () -> Assertions.assertEquals("Series", series.getTitle()),
                () -> Assertions.assertEquals(
                        publisher.getId(),
                        seriesRepository.findById(series.getId())
                                .orElseThrow()
                                .getPublisher()
                                .getId()
                )
        );
    }

    @Test
    @DisplayName("Creates movie through catalog rules")
    void createsMovieThroughCatalogRules() throws Exception {
        final Publisher publisher =
                service.createPublisher("Publisher", List.of());

        final Movie movie = service.createMovie(
                " Movie ",
                LocalDate.of(2026, 7, 16),
                publisher.getId(),
                true
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals("Movie", movie.getTitle()),
                () -> Assertions.assertEquals(
                        LocalDate.of(2026, 7, 16),
                        movieRepository.findById(movie.getId())
                                .orElseThrow()
                                .getReleaseDate()
                ),
                () -> Assertions.assertTrue(movie.isCompilation())
        );
    }

    @Test
    @DisplayName("Creating movie persists only catalog data and never changes media")
    void creatingMovieDoesNotAssociateOrRenameMedia() throws Exception {
        final Publisher publisher =
                service.createPublisher("Brazzers", List.of());
        final UUID mediaId = UUID.randomUUID();
        final Path mediaPath = temporaryDirectory.resolve("original-name.mp4");
        Files.writeString(mediaPath, "media");
        mediaFileRepository.insert(new MediaFile(
                mediaId, mediaPath, Files.size(mediaPath), null,
                Duration.ofSeconds(1), 1280, 720,
                Files.getLastModifiedTime(mediaPath).toMillis()
        ));

        final Movie created = service.createMovie(
                "Asspirations 2", null, publisher.getId(), false
        );
        final Movie persisted = movieRepository.findById(created.getId())
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(publisher.getId(),
                        persisted.getPublisher().getId()),
                () -> Assertions.assertNull(persisted.getReleaseDate()),
                () -> Assertions.assertFalse(persisted.isCompilation()),
                () -> Assertions.assertTrue(persisted.getScenes().isEmpty()),
                () -> Assertions.assertTrue(persisted.getFiles().isEmpty()),
                () -> Assertions.assertTrue(mediaAssignmentRepository
                        .findAssignment(mediaId).scenes().isEmpty()),
                () -> Assertions.assertTrue(mediaAssignmentRepository
                        .findAssignment(mediaId).movies().isEmpty()),
                () -> Assertions.assertEquals(mediaPath,
                        mediaFileRepository.findById(mediaId).orElseThrow()
                                .getPath()),
                () -> Assertions.assertTrue(Files.exists(mediaPath))
        );
    }

    @Test
    @DisplayName("Creates publisher through catalog rules")
    void createsPublisherThroughCatalogRules() throws Exception {
        final Publisher publisher =
                service.createPublisher(" Publisher ", List.of(" Alias "));

        Assertions.assertAll(
                () -> Assertions.assertEquals("Publisher", publisher.getName()),
                () -> Assertions.assertEquals(
                        List.of("Alias"),
                        publisherRepository.findById(publisher.getId())
                                .orElseThrow()
                                .getAliases()
                )
        );
    }

    @Test
    @DisplayName("Creates performer through catalog rules")
    void createsPerformerThroughCatalogRules() throws Exception {
        final Performer performer = service.createPerformer(
                " Alice ",
                List.of(" Ally "),
                PerformerCategory.UNKNOWN
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals("Alice", performer.getMainName()),
                () -> Assertions.assertEquals(
                        List.of("Ally"),
                        performerRepository.findById(performer.getId())
                                .orElseThrow()
                                .getAliases()
                )
        );
    }

    @Test
    @DisplayName("Adds publisher alias explicitly")
    void addsPublisherAliasExplicitly() throws Exception {
        final Publisher publisher =
                service.createPublisher("Publisher", List.of());

        final Publisher updated =
                service.addPublisherAlias(publisher.getId(), " Alias ");

        Assertions.assertEquals(List.of("Alias"), updated.getAliases());
    }

    @Test
    @DisplayName("Adds performer alias explicitly")
    void addsPerformerAliasExplicitly() throws Exception {
        final Performer performer = service.createPerformer(
                "Alice",
                List.of(),
                PerformerCategory.UNKNOWN
        );

        final Performer updated =
                service.addPerformerAlias(performer.getId(), " Ally ");

        Assertions.assertEquals(List.of("Ally"), updated.getAliases());
    }

    @Test
    @DisplayName("Duplicate aliases are rejected")
    void duplicateAliasesAreRejected() throws Exception {
        final Publisher publisher =
                service.createPublisher("Publisher", List.of("Alias"));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.addPublisherAlias(publisher.getId(), "alias")
        );
    }
}

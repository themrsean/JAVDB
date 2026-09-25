package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import model.PerformerCategory;
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
import java.util.List;
import java.util.UUID;

class ContextPublisherResolutionServiceTest {
    @TempDir Path temporaryDirectory;
    private PublisherRepository publishers;
    private ContextCandidateReviewService review;
    private ContextPublisherResolutionService actions;

    @BeforeEach
    void setUp() throws Exception {
        final DatabaseManager database = new DatabaseManager(
                temporaryDirectory.resolve("context-actions.db"));
        new SchemaManager(database).initialize();
        final MediaFileRepository media = new MediaFileRepository(database);
        publishers = new PublisherRepository(database);
        final PerformerRepository performers = new PerformerRepository(database);
        final CatalogService catalog = new CatalogService(publishers, performers,
                new SeriesRepository(database), media, new SceneRepository(database),
                new SearchRepository(database), new MovieRepository(database));
        review = new ContextCandidateReviewService(
                new UnassignedMediaPathRepository(database), new MediaFilenameParser(),
                new EntitySuggestionRepository(database), publishers);
        actions = new ContextPublisherResolutionService(review,
                new EntityManagementService(catalog, publishers, performers));
        media.insert(new MediaFile(UUID.randomUUID(),
                temporaryDirectory.resolve("(25.01.01) Studio - Title - Alice.mp4"),
                1, null, Duration.ofSeconds(1), 1, 1, 1));
    }

    @Test
    void explicitCreateMakesTheCandidateAnExactPublisherMatch() throws Exception {
        final Publisher created = actions.createPublisher("Studio", "Studio",
                List.of("Studio Alias"));

        Assertions.assertAll(
                () -> Assertions.assertEquals("Studio", created.getName()),
                () -> Assertions.assertEquals(ContextCandidateStatus.PUBLISHER_MATCH,
                        review.currentStatus("Studio")),
                () -> Assertions.assertTrue(publishers.findById(created.getId())
                        .orElseThrow().getAliases().contains("Studio Alias"))
        );
    }

    @Test
    void explicitAliasMappingUsesPublisherValidationAndMakesAnExactMatch()
            throws Exception {
        final Publisher publisher = new Publisher(UUID.randomUUID(), "Known", List.of());
        publishers.insert(publisher);
        actions.mapPublisherAlias("Studio", publisher.getId());

        Assertions.assertAll(
                () -> Assertions.assertEquals(ContextCandidateStatus.PUBLISHER_MATCH,
                        review.currentStatus("Studio")),
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> actions.mapPublisherAlias("studio", publisher.getId()))
        );
    }

    @Test
    void staleCandidatesAreRejectedBeforeCreateOrAliasWithoutOtherWrites()
            throws Exception {
        final Publisher existing = new Publisher(UUID.randomUUID(), "Studio", List.of());
        final Publisher target = new Publisher(UUID.randomUUID(), "Target", List.of());
        publishers.insert(existing);
        publishers.insert(target);

        Assertions.assertAll(
                () -> Assertions.assertThrows(ContextCandidateResolvedException.class,
                        () -> actions.createPublisher("Studio", "Studio", List.of())),
                () -> Assertions.assertThrows(ContextCandidateResolvedException.class,
                        () -> actions.mapPublisherAlias("Studio", target.getId())),
                () -> Assertions.assertTrue(publishers.findById(target.getId())
                        .orElseThrow().getAliases().isEmpty()),
                () -> Assertions.assertEquals(2, publishers.findAll().size())
        );
    }

    @Test
    void publisherActionsDoNotCreateSeriesMoviesScenesOrIndexMedia()
            throws Exception {
        actions.createPublisher("Studio", "Studio", List.of());
        final Publisher publisher = publishers.findAll().getFirst();
        Assertions.assertAll(
                () -> Assertions.assertTrue(new SeriesRepository(new DatabaseManager(
                        temporaryDirectory.resolve("context-actions.db"))).findAll().isEmpty()),
                () -> Assertions.assertTrue(new MovieRepository(new DatabaseManager(
                        temporaryDirectory.resolve("context-actions.db"))).findAll().isEmpty()),
                () -> Assertions.assertTrue(new SceneRepository(new DatabaseManager(
                        temporaryDirectory.resolve("context-actions.db"))).findAll().isEmpty()),
                () -> Assertions.assertEquals("Studio", publisher.getName())
        );
    }
}

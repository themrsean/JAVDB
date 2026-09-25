package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import model.Performer;
import model.PerformerCategory;
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

class PerformerCandidateReviewServiceTest {
    @TempDir Path temporaryDirectory;
    private PerformerCandidateReviewService service;
    private PerformerRepository performers;
    private MediaFileRepository mediaFiles;

    @BeforeEach
    void setUp() throws Exception {
        final DatabaseManager database = new DatabaseManager(
                temporaryDirectory.resolve("test.db"));
        new SchemaManager(database).initialize();
        mediaFiles = new MediaFileRepository(database);
        performers = new PerformerRepository(database);
        final CatalogService catalog = new CatalogService(
                new PublisherRepository(database), performers,
                new SeriesRepository(database), mediaFiles,
                new SceneRepository(database), new SearchRepository(database),
                new MovieRepository(database));
        service = new PerformerCandidateReviewService(
                new UnassignedMediaPathRepository(database),
                new MediaFilenameParser(), new EntitySuggestionRepository(database),
                new EntityManagementService(catalog, new PublisherRepository(database),
                        performers));
        add("(25.01.01) Title - Alice, Bob.mp4");
        add("(25.01.02) Other - alice.mp4");
        add("bad filename.mp4");
    }

    @Test
    void aggregatesValidCandidatesAndResolvesPrimaryAlias() throws Exception {
        performers.insert(new Performer(UUID.randomUUID(), "Alice Prime",
                List.of("Alice"), PerformerCategory.UNKNOWN));

        final List<PerformerCandidate> rows = service.loadCandidates();
        final PerformerCandidate alice = candidate(rows, "Alice");
        final PerformerCandidate bob = candidate(rows, "Bob");

        Assertions.assertAll(
                () -> Assertions.assertEquals(2, rows.size()),
                () -> Assertions.assertEquals(2, alice.mediaCount()),
                () -> Assertions.assertEquals(PerformerCandidateResolution.ALIAS_MATCH,
                        alice.resolution()),
                () -> Assertions.assertEquals(PerformerCandidateResolution.UNRESOLVED,
                        bob.resolution())
        );
    }

    @Test
    void explicitCreateAndAliasMappingRefreshResolutionAndRejectDuplicates()
            throws Exception {
        final Performer created = service.createPerformer("Bob",
                PerformerCategory.ACTOR);

        Assertions.assertEquals(PerformerCandidateResolution.PRIMARY_MATCH,
                candidate(service.loadCandidates(), "Bob").resolution());
        service.mapAlias("Alice", created.getId());

        Assertions.assertAll(
                () -> Assertions.assertEquals(PerformerCategory.ACTOR,
                        performers.findById(created.getId()).orElseThrow().getCategory()),
                () -> Assertions.assertThrows(IllegalArgumentException.class,
                        () -> service.mapAlias("alice", created.getId()))
        );
    }

    @Test
    void selectedCategoryIsPreservedForEverySingleCreation() throws Exception {
        for (PerformerCategory category : PerformerCategory.values()) {
            final String name = "Candidate " + category;
            final Performer created = service.createPerformer(name, category);
            Assertions.assertEquals(category,
                    performers.findById(created.getId()).orElseThrow().getCategory());
        }
    }

    @Test
    void batchCreatesIndependentlyWithTheSelectedCategoryAndSkipsResolved()
            throws Exception {
        service.createPerformer("Alice", PerformerCategory.OTHER);

        final PerformerCandidateBatchResult batch = service.createPerformers(
                List.of("Alice", "Bob"), PerformerCategory.ACTRESS);

        Assertions.assertAll(
                () -> Assertions.assertEquals(2, batch.selected()),
                () -> Assertions.assertEquals(1, batch.created()),
                () -> Assertions.assertEquals(1, batch.skipped()),
                () -> Assertions.assertTrue(batch.failures().isEmpty()),
                () -> Assertions.assertEquals(PerformerCategory.OTHER,
                        performerNamed("Alice").getCategory()),
                () -> Assertions.assertEquals(PerformerCategory.ACTRESS,
                        performerNamed("Bob").getCategory())
        );
    }

    @Test
    void nullCategoryIsRejectedBeforeAWrite() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(NullPointerException.class,
                        () -> service.createPerformer("Alice", null)),
                () -> Assertions.assertThrows(NullPointerException.class,
                        () -> service.createPerformers(List.of("Alice"), null))
        );
    }

    private PerformerCandidate candidate(List<PerformerCandidate> rows, String text) {
        return rows.stream().filter(row -> row.text().equals(text)).findFirst()
                .orElseThrow();
    }

    private Performer performerNamed(String name) throws Exception {
        return performers.findAll().stream()
                .filter(performer -> performer.getMainName().equals(name))
                .findFirst().orElseThrow();
    }

    private void add(String name) throws Exception {
        mediaFiles.insert(new MediaFile(UUID.randomUUID(),
                temporaryDirectory.resolve(name), 1, null,
                Duration.ofSeconds(1), 1, 1, 1));
    }
}

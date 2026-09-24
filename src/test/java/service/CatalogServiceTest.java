package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaFileRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

class CatalogServiceTest {
    private static final String DATABASE_FILE_NAME =
            "catalog-service-test.db";

    private static final String SELECT_PUBLISHER_NAME_SQL = """
            SELECT name
            FROM publisher
            WHERE id = ?
            """;
    private static final String SELECT_PERFORMER_SQL = """
            SELECT main_name, category
            FROM performer
            WHERE id = ?
            """;
    private static final String SELECT_SCENE_SQL = """
            SELECT title, code, release_date, publisher_id, series_id,
                   season, episode
            FROM scene
            WHERE id = ?
            """;
    private static final String SELECT_PUBLISHER_ALIASES_SQL = """
            SELECT alias
            FROM publisher_alias
            WHERE publisher_id = ?
            ORDER BY alias COLLATE NOCASE
            """;
    private static final String SELECT_PERFORMER_ALIASES_SQL = """
            SELECT alias
            FROM performer_alias
            WHERE performer_id = ?
            ORDER BY alias COLLATE NOCASE
            """;
    private static final String COUNT_SCENE_SQL = """
            SELECT COUNT(*)
            FROM scene
            """;
    private static final String COUNT_SCENE_PERFORMER_SQL = """
            SELECT COUNT(*)
            FROM scene_performer
            WHERE scene_id = ?
            """;
    private static final String COUNT_SCENE_MEDIA_FILE_SQL = """
            SELECT COUNT(*)
            FROM scene_media_file
            WHERE scene_id = ?
            """;
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int FIRST_COLUMN_INDEX = 1;
    private static final int SECOND_COLUMN_INDEX = 2;
    private static final int THIRD_COLUMN_INDEX = 3;
    private static final int FOURTH_COLUMN_INDEX = 4;
    private static final int FIFTH_COLUMN_INDEX = 5;
    private static final int SIXTH_COLUMN_INDEX = 6;
    private static final int SEVENTH_COLUMN_INDEX = 7;
    private static final long MEDIA_FILE_SIZE = 1_024L;
    private static final int MEDIA_WIDTH = 1920;
    private static final int MEDIA_HEIGHT = 1080;

    private static final String PUBLISHER_NAME = "Zen Publisher";
    private static final String PERFORMER_NAME = "Zen Performer";
    private static final String SCENE_TITLE = "Zen Scene";
    private static final String SCENE_CODE = "ZEN-001";
    private static final String SEASON = "1";
    private static final String EPISODE = "2";
    private static final LocalDate RELEASE_DATE =
            LocalDate.of(2024, 1, 2);
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-1111-9999-1111-999999999999");

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private PublisherRepository publisherRepository;
    private PerformerRepository performerRepository;
    private SeriesRepository seriesRepository;
    private MediaFileRepository mediaFileRepository;
    private SceneRepository sceneRepository;
    private SearchRepository searchRepository;
    private CatalogService service;

    @BeforeEach
    void initializeDatabase() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        publisherRepository = new PublisherRepository(databaseManager);
        performerRepository = new PerformerRepository(databaseManager);
        seriesRepository = new SeriesRepository(databaseManager);
        mediaFileRepository = new MediaFileRepository(databaseManager);
        sceneRepository = new SceneRepository(databaseManager);
        searchRepository = new SearchRepository(databaseManager);
        service = createService();
    }

    @Test
    @DisplayName("Constructor rejects null repository dependencies")
    void constructorRejectsNullRepositoryDependencies() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new CatalogService(
                                null,
                                performerRepository,
                                seriesRepository,
                                mediaFileRepository,
                                sceneRepository,
                                searchRepository
                        )
                ),
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new CatalogService(
                                publisherRepository,
                                null,
                                seriesRepository,
                                mediaFileRepository,
                                sceneRepository,
                                searchRepository
                        )
                ),
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new CatalogService(
                                publisherRepository,
                                performerRepository,
                                null,
                                mediaFileRepository,
                                sceneRepository,
                                searchRepository
                        )
                ),
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new CatalogService(
                                publisherRepository,
                                performerRepository,
                                seriesRepository,
                                null,
                                sceneRepository,
                                searchRepository
                        )
                ),
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new CatalogService(
                                publisherRepository,
                                performerRepository,
                                seriesRepository,
                                mediaFileRepository,
                                null,
                                searchRepository
                        )
                ),
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new CatalogService(
                                publisherRepository,
                                performerRepository,
                                seriesRepository,
                                mediaFileRepository,
                                sceneRepository,
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("Creating a publisher persists it")
    void creatingPublisherPersistsIt() throws Exception {
        final Publisher publisher = service.createPublisher(
                PUBLISHER_NAME,
                List.of()
        );

        Assertions.assertEquals(
                PUBLISHER_NAME,
                findSingleString(SELECT_PUBLISHER_NAME_SQL, publisher.getId())
        );
    }

    @Test
    @DisplayName("Creating a publisher persists normalized aliases")
    void creatingPublisherPersistsNormalizedAliases() throws Exception {
        final Publisher publisher = service.createPublisher(
                "  " + PUBLISHER_NAME + "  ",
                List.of(" Beta ", " ", "alpha", "ALPHA")
        );

        Assertions.assertEquals(PUBLISHER_NAME, publisher.getName());
        Assertions.assertIterableEquals(
                List.of("alpha", "Beta"),
                findStrings(SELECT_PUBLISHER_ALIASES_SQL, publisher.getId())
        );
    }

    @Test
    @DisplayName("Blank publisher names are rejected")
    void blankPublisherNamesAreRejected() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.createPublisher("   ", List.of())
        );
    }

    @Test
    @DisplayName("Creating a performer persists name category and aliases")
    void creatingPerformerPersistsNameCategoryAndAliases()
            throws Exception {

        final Performer performer = service.createPerformer(
                "  " + PERFORMER_NAME + "  ",
                List.of(" Beta ", "", "alpha", "ALPHA"),
                PerformerCategory.ACTOR
        );

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SELECT_PERFORMER_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    performer.getId().toString()
            );

            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(
                        PERFORMER_NAME,
                        resultSet.getString(FIRST_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        PerformerCategory.ACTOR.name(),
                        resultSet.getString(SECOND_COLUMN_INDEX)
                );
            }
        }

        Assertions.assertIterableEquals(
                List.of("alpha", "Beta"),
                findStrings(SELECT_PERFORMER_ALIASES_SQL, performer.getId())
        );
    }

    @Test
    @DisplayName("Blank performer names and null categories are rejected")
    void blankPerformerNamesAndNullCategoriesAreRejected() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createPerformer(
                                " ",
                                List.of(),
                                PerformerCategory.ACTOR
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createPerformer(
                                PERFORMER_NAME,
                                List.of(),
                                null
                        )
                )
        );
    }

    @Test
    @DisplayName("Creating a scene persists scalar fields and relationships")
    void creatingScenePersistsScalarFieldsAndRelationships()
            throws Exception {

        final Publisher publisher = service.createPublisher(
                PUBLISHER_NAME,
                List.of()
        );
        final Performer firstPerformer = service.createPerformer(
                "First Performer",
                List.of(),
                PerformerCategory.ACTOR
        );
        final Performer secondPerformer = service.createPerformer(
                "Second Performer",
                List.of(),
                PerformerCategory.ACTRESS
        );
        final Series series = createSeries(publisher);
        final MediaFile mediaFile = createMediaFile();

        final Scene scene = service.createScene(
                "  " + SCENE_TITLE + "  ",
                "  " + SCENE_CODE + "  ",
                RELEASE_DATE,
                publisher.getId(),
                series.getId(),
                "  " + SEASON + "  ",
                "  " + EPISODE + "  ",
                List.of(firstPerformer.getId(), secondPerformer.getId()),
                List.of(mediaFile.getId())
        );

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SELECT_SCENE_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    scene.getId().toString()
            );

            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(
                        SCENE_TITLE,
                        resultSet.getString(FIRST_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        SCENE_CODE,
                        resultSet.getString(SECOND_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        RELEASE_DATE.toString(),
                        resultSet.getString(THIRD_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        publisher.getId().toString(),
                        resultSet.getString(FOURTH_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        series.getId().toString(),
                        resultSet.getString(FIFTH_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        SEASON,
                        resultSet.getString(SIXTH_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        EPISODE,
                        resultSet.getString(SEVENTH_COLUMN_INDEX)
                );
            }
        }

        Assertions.assertEquals(
                2,
                countBySceneId(COUNT_SCENE_PERFORMER_SQL, scene.getId())
        );
        Assertions.assertEquals(
                1,
                countBySceneId(COUNT_SCENE_MEDIA_FILE_SQL, scene.getId())
        );
    }

    @Test
    @DisplayName("Optional scene fields and blank strings are normalized")
    void optionalSceneFieldsAndBlankStringsAreNormalized()
            throws Exception {

        final Publisher publisher = service.createPublisher(
                PUBLISHER_NAME,
                List.of()
        );

        final Scene scene = service.createScene(
                SCENE_TITLE,
                " ",
                null,
                publisher.getId(),
                null,
                " ",
                "",
                null,
                null
        );

        final Scene found = sceneRepository.findById(scene.getId())
                .orElseThrow();
        Assertions.assertNull(found.getCode());
        Assertions.assertNull(found.getReleaseDate());
        Assertions.assertNull(found.getSeries());
        Assertions.assertNull(found.getSeason());
        Assertions.assertNull(found.getEpisode());
        Assertions.assertTrue(found.getPerformers().isEmpty());
        Assertions.assertTrue(found.getFiles().isEmpty());
    }

    @Test
    @DisplayName("Invalid scene inputs are rejected")
    void invalidSceneInputsAreRejected() throws Exception {
        final Publisher publisher = service.createPublisher(
                PUBLISHER_NAME,
                List.of()
        );
        final Performer performer = service.createPerformer(
                PERFORMER_NAME,
                List.of(),
                PerformerCategory.ACTOR
        );
        final MediaFile mediaFile = createMediaFile();

        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createScene(
                                " ",
                                null,
                                null,
                                publisher.getId(),
                                null,
                                null,
                                null,
                                List.of(),
                                List.of()
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createScene(
                                SCENE_TITLE,
                                null,
                                null,
                                UNKNOWN_ID,
                                null,
                                null,
                                null,
                                List.of(),
                                List.of()
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createScene(
                                SCENE_TITLE,
                                null,
                                null,
                                publisher.getId(),
                                UNKNOWN_ID,
                                null,
                                null,
                                List.of(),
                                List.of()
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createScene(
                                SCENE_TITLE,
                                null,
                                null,
                                publisher.getId(),
                                null,
                                null,
                                null,
                                List.of(UNKNOWN_ID),
                                List.of()
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createScene(
                                SCENE_TITLE,
                                null,
                                null,
                                publisher.getId(),
                                null,
                                null,
                                null,
                                List.of(performer.getId()),
                                List.of(UNKNOWN_ID)
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createScene(
                                SCENE_TITLE,
                                null,
                                null,
                                publisher.getId(),
                                null,
                                null,
                                null,
                                Arrays.asList(performer.getId(), null),
                                List.of()
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createScene(
                                SCENE_TITLE,
                                null,
                                null,
                                publisher.getId(),
                                null,
                                null,
                                null,
                                List.of(performer.getId(), performer.getId()),
                                List.of()
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createScene(
                                SCENE_TITLE,
                                null,
                                null,
                                publisher.getId(),
                                null,
                                null,
                                null,
                                List.of(),
                                List.of(mediaFile.getId(), mediaFile.getId())
                        )
                )
        );

        Assertions.assertEquals(0, countScenes());
    }

    @Test
    @DisplayName("Created scenes can be found by performer")
    void createdScenesCanBeFoundByPerformer() throws Exception {
        final Publisher publisher = service.createPublisher(
                PUBLISHER_NAME,
                List.of()
        );
        final Performer performer = service.createPerformer(
                PERFORMER_NAME,
                List.of(),
                PerformerCategory.ACTOR
        );

        final Scene scene = service.createScene(
                SCENE_TITLE,
                null,
                null,
                publisher.getId(),
                null,
                null,
                null,
                List.of(performer.getId()),
                List.of()
        );

        Assertions.assertEquals(
                List.of(scene.getId()),
                service.findScenesByPerformer(performer.getId())
                        .stream()
                        .map(Scene::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Scene lookup handles performers with no scenes and unknown performers")
    void sceneLookupHandlesNoScenesAndUnknownPerformers()
            throws Exception {

        final Performer performer = service.createPerformer(
                PERFORMER_NAME,
                List.of(),
                PerformerCategory.ACTOR
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        service.findScenesByPerformer(performer.getId())
                                .isEmpty()
                ),
                () -> Assertions.assertTrue(
                        service.findScenesByPerformer(UNKNOWN_ID).isEmpty()
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.findScenesByPerformer(null)
                )
        );
    }

    @Test
    @DisplayName("Multiple matching scenes use deterministic repository order")
    void multipleMatchingScenesUseDeterministicRepositoryOrder()
            throws Exception {

        final Publisher publisher = service.createPublisher(
                PUBLISHER_NAME,
                List.of()
        );
        final Performer performer = service.createPerformer(
                PERFORMER_NAME,
                List.of(),
                PerformerCategory.ACTOR
        );

        final Scene zScene = service.createScene(
                "Zulu Scene",
                null,
                null,
                publisher.getId(),
                null,
                null,
                null,
                List.of(performer.getId()),
                List.of()
        );
        final Scene aScene = service.createScene(
                "Alpha Scene",
                null,
                null,
                publisher.getId(),
                null,
                null,
                null,
                List.of(performer.getId()),
                List.of()
        );

        Assertions.assertEquals(
                List.of(aScene.getId(), zScene.getId()),
                service.findScenesByPerformer(performer.getId())
                        .stream()
                        .map(Scene::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Scene verification status transitions preserve relationships")
    void sceneVerificationStatusTransitionsPreserveRelationships()
            throws Exception {

        final Publisher publisher = service.createPublisher(
                PUBLISHER_NAME,
                List.of()
        );
        final Performer performer = service.createPerformer(
                PERFORMER_NAME,
                List.of(),
                PerformerCategory.ACTOR
        );
        final Scene scene = service.createScene(
                SCENE_TITLE,
                null,
                null,
                publisher.getId(),
                null,
                null,
                null,
                List.of(performer.getId()),
                List.of()
        );

        final Scene verified = service.markSceneVerified(scene.getId());
        final Scene needsReview = service.markSceneNeedsReview(scene.getId());
        final Scene unverified = service.markSceneUnverified(scene.getId());

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        VerificationStatus.VERIFIED,
                        verified.getVerificationStatus()
                ),
                () -> Assertions.assertEquals(
                        VerificationStatus.NEEDS_REVIEW,
                        needsReview.getVerificationStatus()
                ),
                () -> Assertions.assertEquals(
                        VerificationStatus.UNVERIFIED,
                        unverified.getVerificationStatus()
                ),
                () -> Assertions.assertEquals(
                        List.of(performer.getId()),
                        unverified.getPerformers()
                                .stream()
                                .map(Performer::getId)
                                .toList()
                )
        );
    }

    @Test
    @DisplayName("Find scenes by verification status filters and pages")
    void findScenesByVerificationStatusFiltersAndPages() throws Exception {
        final Publisher publisher = service.createPublisher(
                PUBLISHER_NAME,
                List.of()
        );
        final Scene zScene = service.createScene(
                "Zulu",
                null,
                null,
                publisher.getId(),
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        final Scene aScene = service.createScene(
                "Alpha",
                null,
                null,
                publisher.getId(),
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        service.markSceneNeedsReview(zScene.getId());
        service.markSceneNeedsReview(aScene.getId());

        Assertions.assertEquals(
                List.of(zScene.getId()),
                service.findScenesByVerificationStatus(
                        VerificationStatus.NEEDS_REVIEW,
                        1,
                        1
                ).stream().map(Scene::getId).toList()
        );
    }

    @Test
    @DisplayName("Unknown scene verification update is rejected")
    void unknownSceneVerificationUpdateIsRejected() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.markSceneVerified(UNKNOWN_ID)
        );
    }

    @Test
    @DisplayName("End-to-end catalog workflow returns complete scene")
    void endToEndCatalogWorkflowReturnsCompleteScene() throws Exception {
        final Publisher publisher = service.createPublisher(
                " Workflow Publisher ",
                List.of("WP")
        );
        final Performer firstPerformer = service.createPerformer(
                " First Performer ",
                List.of("First"),
                PerformerCategory.ACTOR
        );
        final Performer secondPerformer = service.createPerformer(
                " Second Performer ",
                List.of("Second"),
                PerformerCategory.ACTRESS
        );

        final Scene scene = service.createScene(
                " Workflow Scene ",
                " WF-001 ",
                RELEASE_DATE,
                publisher.getId(),
                null,
                null,
                null,
                List.of(firstPerformer.getId(), secondPerformer.getId()),
                List.of()
        );

        final Scene found = service.findScenesByPerformer(
                secondPerformer.getId()
        ).getFirst();

        Assertions.assertEquals(scene.getId(), found.getId());
        Assertions.assertEquals(publisher.getId(), found.getPublisher().getId());
        Assertions.assertEquals(
                List.of(firstPerformer.getId(), secondPerformer.getId()),
                found.getPerformers()
                        .stream()
                        .map(Performer::getId)
                        .toList()
        );
    }

    private CatalogService createService() {
        return new CatalogService(
                publisherRepository,
                performerRepository,
                seriesRepository,
                mediaFileRepository,
                sceneRepository,
                searchRepository
        );
    }

    private Series createSeries(Publisher publisher) throws Exception {
        final Series series = new Series(
                UUID.randomUUID(),
                "Series",
                publisher
        );
        seriesRepository.insert(series);
        return series;
    }

    private MediaFile createMediaFile() throws Exception {
        final MediaFile mediaFile = new MediaFile(
                UUID.randomUUID(),
                Path.of("/video/" + UUID.randomUUID() + ".mp4"),
                MEDIA_FILE_SIZE,
                "hash-" + UUID.randomUUID(),
                Duration.ofMillis(MEDIA_FILE_SIZE),
                MEDIA_WIDTH,
                MEDIA_HEIGHT
        );
        mediaFileRepository.insert(mediaFile);
        return mediaFile;
    }

    private String findSingleString(String sql, UUID id) throws Exception {
        String value = null;

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    value = resultSet.getString(FIRST_COLUMN_INDEX);
                }
            }
        }

        return value;
    }

    private List<String> findStrings(String sql, UUID id) throws Exception {
        final java.util.ArrayList<String> values =
                new java.util.ArrayList<>();

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getString(FIRST_COLUMN_INDEX));
                }
            }
        }

        return values;
    }

    private int countBySceneId(String sql, UUID sceneId) throws Exception {
        int count = 0;

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    sceneId.toString()
            );

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    count = resultSet.getInt(FIRST_COLUMN_INDEX);
                }
            }
        }

        return count;
    }

    private int countScenes() throws Exception {
        int count = 0;

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(COUNT_SCENE_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                count = resultSet.getInt(FIRST_COLUMN_INDEX);
            }
        }

        return count;
    }
}

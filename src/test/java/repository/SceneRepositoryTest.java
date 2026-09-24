package repository;

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

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SceneRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "scene-repository-test.db";
    private static final String SELECT_SCENE_SQL = """
            SELECT title, code, release_date, publisher_id, series_id,
                   season, episode, verification_status
            FROM scene
            WHERE id = ?
            """;
    private static final String COUNT_SCENE_SQL = """
            SELECT COUNT(*)
            FROM scene
            WHERE id = ?
            """;
    private static final String COUNT_SCENE_PERFORMERS_SQL = """
            SELECT COUNT(*)
            FROM scene_performer
            WHERE scene_id = ?
            """;
    private static final String COUNT_SCENE_FILES_SQL = """
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
    private static final int EIGHTH_COLUMN_INDEX = 8;

    private static final UUID SCENE_ID =
            UUID.fromString("11111111-dddd-1111-dddd-111111111111");
    private static final UUID SECOND_SCENE_ID =
            UUID.fromString("22222222-dddd-2222-dddd-222222222222");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-dddd-9999-dddd-999999999999");
    private static final UUID PUBLISHER_ID =
            UUID.fromString("aaaaaaaa-dddd-1111-dddd-111111111111");
    private static final UUID SERIES_ID =
            UUID.fromString("bbbbbbbb-dddd-1111-dddd-111111111111");
    private static final UUID PERFORMER_ID =
            UUID.fromString("cccccccc-dddd-1111-dddd-111111111111");
    private static final UUID SECOND_PERFORMER_ID =
            UUID.fromString("dddddddd-dddd-2222-dddd-222222222222");
    private static final UUID MEDIA_FILE_ID =
            UUID.fromString("eeeeeeee-dddd-1111-dddd-111111111111");
    private static final UUID SECOND_MEDIA_FILE_ID =
            UUID.fromString("ffffffff-dddd-2222-dddd-222222222222");
    private static final String TITLE = "Zen Scene";
    private static final String SECOND_TITLE = "alpha Scene";
    private static final String UPDATED_TITLE = "Zen Scene Updated";
    private static final String CODE = "ZEN-001";
    private static final String UPDATED_CODE = "ZEN-002";
    private static final String SEASON = "1";
    private static final String EPISODE = "2";
    private static final LocalDate RELEASE_DATE =
            LocalDate.of(2024, 1, 2);
    private static final LocalDate UPDATED_RELEASE_DATE =
            LocalDate.of(2025, 3, 4);

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private SceneRepository repository;
    private Publisher publisher;
    private Series series;
    private Performer performer;
    private Performer secondPerformer;
    private MediaFile mediaFile;
    private MediaFile secondMediaFile;

    @BeforeEach
    void initializeDatabase() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        repository = new SceneRepository(databaseManager);

        publisher = new Publisher(PUBLISHER_ID, "Publisher", List.of());
        series = new Series(SERIES_ID, "Series", publisher);
        performer = new Performer(
                PERFORMER_ID,
                "Performer",
                List.of(),
                PerformerCategory.ACTOR
        );
        secondPerformer = new Performer(
                SECOND_PERFORMER_ID,
                "Second Performer",
                List.of(),
                PerformerCategory.ACTRESS
        );
        mediaFile = new MediaFile(
                MEDIA_FILE_ID,
                Path.of("/video/scene.mp4"),
                1_000L,
                "hash-one",
                Duration.ofMillis(2_000L),
                1920,
                1080
        );
        secondMediaFile = new MediaFile(
                SECOND_MEDIA_FILE_ID,
                Path.of("/video/scene-two.mp4"),
                2_000L,
                "hash-two",
                Duration.ofMillis(3_000L),
                1280,
                720
        );

        new PublisherRepository(databaseManager).insert(publisher);
        new SeriesRepository(databaseManager).insert(series);
        new PerformerRepository(databaseManager).insert(performer);
        new PerformerRepository(databaseManager).insert(secondPerformer);
        new MediaFileRepository(databaseManager).insert(mediaFile);
        new MediaFileRepository(databaseManager).insert(secondMediaFile);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new SceneRepository(null)
        );
    }

    @Test
    @DisplayName("Insert stores every scalar scene field")
    void insertStoresEveryScalarSceneField() throws Exception {
        repository.insert(createScene());

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SELECT_SCENE_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    SCENE_ID.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(
                        TITLE,
                        resultSet.getString(FIRST_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        CODE,
                        resultSet.getString(SECOND_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        RELEASE_DATE.toString(),
                        resultSet.getString(THIRD_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        PUBLISHER_ID.toString(),
                        resultSet.getString(FOURTH_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        SERIES_ID.toString(),
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
                Assertions.assertEquals(
                        VerificationStatus.UNVERIFIED.name(),
                        resultSet.getString(EIGHTH_COLUMN_INDEX)
                );
            }
        }
    }

    @Test
    @DisplayName("Insert stores every valid verification status")
    void insertStoresEveryValidVerificationStatus() throws Exception {
        for (VerificationStatus status : VerificationStatus.values()) {
            final UUID sceneId = UUID.nameUUIDFromBytes(status.name()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            repository.insert(new Scene(
                    sceneId,
                    TITLE + status.name(),
                    publisher,
                    RELEASE_DATE,
                    CODE,
                    series,
                    SEASON,
                    EPISODE,
                    List.of(),
                    List.of(),
                    status
            ));

            Assertions.assertEquals(
                    status,
                    repository.findById(sceneId).orElseThrow()
                            .getVerificationStatus()
            );
        }
    }

    @Test
    @DisplayName("Nullable scalar values and series behave by schema")
    void nullableScalarValuesAndSeriesBehaveBySchema() throws Exception {
        final Scene scene = new Scene(
                SCENE_ID,
                TITLE,
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );

        repository.insert(scene);

        final Scene found = repository.findById(SCENE_ID).orElseThrow();
        Assertions.assertNull(found.getCode());
        Assertions.assertNull(found.getReleaseDate());
        Assertions.assertNull(found.getSeries());
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new Scene(
                        SECOND_SCENE_ID,
                        SECOND_TITLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of()
                )
        );
    }

    @Test
    @DisplayName("Insert stores all performer relationships")
    void insertStoresAllPerformerRelationships() throws Exception {
        repository.insert(createScene());

        Assertions.assertEquals(
                2,
                countBySceneId(COUNT_SCENE_PERFORMERS_SQL)
        );
    }

    @Test
    @DisplayName("Insert stores all media-file relationships")
    void insertStoresAllMediaFileRelationships() throws Exception {
        repository.insert(createScene());

        Assertions.assertEquals(
                2,
                countBySceneId(COUNT_SCENE_FILES_SQL)
        );
    }

    @Test
    @DisplayName("Find by ID reconstructs scalar fields and relationships")
    void findByIdReconstructsScalarFieldsAndRelationships()
            throws Exception {

        repository.insert(createScene());

        final Scene found = repository.findById(SCENE_ID).orElseThrow();

        Assertions.assertEquals(TITLE, found.getTitle());
        Assertions.assertEquals(CODE, found.getCode());
        Assertions.assertEquals(RELEASE_DATE, found.getReleaseDate());
        Assertions.assertEquals(
                VerificationStatus.UNVERIFIED,
                found.getVerificationStatus()
        );
        Assertions.assertEquals(PUBLISHER_ID, found.getPublisher().getId());
        Assertions.assertEquals(SERIES_ID, found.getSeries().getId());
        Assertions.assertEquals(
                List.of(PERFORMER_ID, SECOND_PERFORMER_ID),
                found.getPerformers()
                        .stream()
                        .map(Performer::getId)
                        .toList()
        );
        Assertions.assertEquals(
                List.of(SECOND_MEDIA_FILE_ID, MEDIA_FILE_ID),
                found.getFiles()
                        .stream()
                        .map(MediaFile::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Find by ID returns empty for unknown UUID")
    void findByIdReturnsEmptyForUnknownUuid() throws Exception {
        Assertions.assertTrue(repository.findById(UNKNOWN_ID).isEmpty());
    }

    @Test
    @DisplayName("Find all returns deterministic results")
    void findAllReturnsDeterministicResults() throws Exception {
        repository.insert(createScene());
        repository.insert(new Scene(
                SECOND_SCENE_ID,
                SECOND_TITLE,
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        ));

        Assertions.assertEquals(
                List.of(SECOND_SCENE_ID, SCENE_ID),
                repository.findAll()
                        .stream()
                        .map(Scene::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Find by verification status filters and pages deterministically")
    void findByVerificationStatusFiltersAndPagesDeterministically()
            throws Exception {

        repository.insert(new Scene(
                SCENE_ID,
                "Bravo",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                VerificationStatus.NEEDS_REVIEW
        ));
        repository.insert(new Scene(
                SECOND_SCENE_ID,
                "Alpha",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                VerificationStatus.NEEDS_REVIEW
        ));

        Assertions.assertEquals(
                List.of(SCENE_ID),
                repository.findByVerificationStatus(
                        VerificationStatus.NEEDS_REVIEW,
                        1,
                        1
                ).stream().map(Scene::getId).toList()
        );
    }

    @Test
    @DisplayName("Update changes scalar fields")
    void updateChangesScalarFields() throws Exception {
        repository.insert(createScene());

        repository.update(new Scene(
                SCENE_ID,
                UPDATED_TITLE,
                publisher,
                UPDATED_RELEASE_DATE,
                UPDATED_CODE,
                null,
                null,
                null,
                List.of(performer),
                List.of(mediaFile)
                ,
                VerificationStatus.VERIFIED
        ));

        final Scene found = repository.findById(SCENE_ID).orElseThrow();
        Assertions.assertEquals(UPDATED_TITLE, found.getTitle());
        Assertions.assertEquals(UPDATED_CODE, found.getCode());
        Assertions.assertEquals(
                UPDATED_RELEASE_DATE,
                found.getReleaseDate()
        );
        Assertions.assertNull(found.getSeries());
        Assertions.assertEquals(
                VerificationStatus.VERIFIED,
                found.getVerificationStatus()
        );
    }

    @Test
    @DisplayName("Update replaces performers and removes stale relationships")
    void updateReplacesPerformersAndRemovesStaleRelationships()
            throws Exception {

        repository.insert(createScene());
        repository.update(new Scene(
                SCENE_ID,
                TITLE,
                publisher,
                RELEASE_DATE,
                CODE,
                series,
                SEASON,
                EPISODE,
                List.of(secondPerformer),
                List.of(mediaFile)
        ));

        Assertions.assertEquals(
                List.of(SECOND_PERFORMER_ID),
                repository.findById(SCENE_ID).orElseThrow()
                        .getPerformers()
                        .stream()
                        .map(Performer::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Update replaces media files and removes stale relationships")
    void updateReplacesMediaFilesAndRemovesStaleRelationships()
            throws Exception {

        repository.insert(createScene());
        repository.update(new Scene(
                SCENE_ID,
                TITLE,
                publisher,
                RELEASE_DATE,
                CODE,
                series,
                SEASON,
                EPISODE,
                List.of(performer),
                List.of(secondMediaFile)
        ));

        Assertions.assertEquals(
                List.of(SECOND_MEDIA_FILE_ID),
                repository.findById(SCENE_ID).orElseThrow()
                        .getFiles()
                        .stream()
                        .map(MediaFile::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Unknown related UUID fails")
    void unknownRelatedUuidFails() {
        final Performer unknownPerformer = new Performer(
                UNKNOWN_ID,
                "Unknown",
                List.of(),
                PerformerCategory.UNKNOWN
        );

        Assertions.assertThrows(
                SQLException.class,
                () -> repository.insert(new Scene(
                        SCENE_ID,
                        TITLE,
                        publisher,
                        RELEASE_DATE,
                        CODE,
                        series,
                        SEASON,
                        EPISODE,
                        List.of(unknownPerformer),
                        List.of()
                ))
        );
    }

    @Test
    @DisplayName("Failed multi-table insert rolls back completely")
    void failedMultiTableInsertRollsBackCompletely() {
        final Scene scene = new Scene(
                SCENE_ID,
                TITLE,
                publisher,
                RELEASE_DATE,
                CODE,
                series,
                SEASON,
                EPISODE,
                List.of(performer, performer),
                List.of(mediaFile)
        );

        Assertions.assertThrows(
                SQLException.class,
                () -> repository.insert(scene)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, countSceneById()),
                () -> Assertions.assertEquals(
                        0,
                        countBySceneId(COUNT_SCENE_PERFORMERS_SQL)
                ),
                () -> Assertions.assertEquals(
                        0,
                        countBySceneId(COUNT_SCENE_FILES_SQL)
                )
        );
    }

    @Test
    @DisplayName("Delete removes the scene")
    void deleteRemovesScene() throws Exception {
        repository.insert(createScene());

        Assertions.assertTrue(repository.delete(SCENE_ID));
        Assertions.assertEquals(0, countSceneById());
    }

    @Test
    @DisplayName("Delete removes scene-performer rows")
    void deleteRemovesScenePerformerRows() throws Exception {
        repository.insert(createScene());

        repository.delete(SCENE_ID);

        Assertions.assertEquals(
                0,
                countBySceneId(COUNT_SCENE_PERFORMERS_SQL)
        );
    }

    @Test
    @DisplayName("Delete removes scene-media-file rows")
    void deleteRemovesSceneMediaFileRows() throws Exception {
        repository.insert(createScene());

        repository.delete(SCENE_ID);

        Assertions.assertEquals(
                0,
                countBySceneId(COUNT_SCENE_FILES_SQL)
        );
    }

    @Test
    @DisplayName("Delete reports false for unknown UUID")
    void deleteReportsFalseForUnknownUuid() throws Exception {
        Assertions.assertFalse(repository.delete(UNKNOWN_ID));
    }

    private Scene createScene() {
        return new Scene(
                SCENE_ID,
                TITLE,
                publisher,
                RELEASE_DATE,
                CODE,
                series,
                SEASON,
                EPISODE,
                List.of(performer, secondPerformer),
                List.of(mediaFile, secondMediaFile)
        );
    }

    private int countSceneById() throws Exception {
        return countBySql(COUNT_SCENE_SQL);
    }

    private int countBySceneId(String sql) throws Exception {
        return countBySql(sql);
    }

    private int countBySql(String sql) throws Exception {
        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    SCENE_ID.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    count = resultSet.getInt(FIRST_COLUMN_INDEX);
                }
            }
        }

        return count;
    }
}

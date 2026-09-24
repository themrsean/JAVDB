package repository;

import database.DatabaseManager;
import database.SchemaManager;
import model.Publisher;
import model.Series;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class SeriesRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "series-repository-test.db";
    private static final String SELECT_SERIES_SQL = """
            SELECT title, publisher_id
            FROM series
            WHERE id = ?
            """;
    private static final String COUNT_SERIES_BY_ID_SQL = """
            SELECT COUNT(*)
            FROM series
            WHERE id = ?
            """;
    private static final String INSERT_SCENE_SQL = """
            INSERT INTO scene(
                id,
                title,
                publisher_id,
                series_id
            )
            VALUES (?, ?, ?, ?)
            """;
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int THIRD_PARAMETER_INDEX = 3;
    private static final int FOURTH_PARAMETER_INDEX = 4;
    private static final int FIRST_COLUMN_INDEX = 1;
    private static final int SECOND_COLUMN_INDEX = 2;

    private static final UUID SERIES_ID =
            UUID.fromString("11111111-bbbb-1111-bbbb-111111111111");
    private static final UUID SECOND_SERIES_ID =
            UUID.fromString("22222222-bbbb-2222-bbbb-222222222222");
    private static final UUID UNKNOWN_SERIES_ID =
            UUID.fromString("99999999-bbbb-9999-bbbb-999999999999");
    private static final UUID PUBLISHER_ID =
            UUID.fromString("aaaaaaaa-bbbb-1111-bbbb-111111111111");
    private static final UUID SECOND_PUBLISHER_ID =
            UUID.fromString("bbbbbbbb-bbbb-2222-bbbb-222222222222");
    private static final UUID UNKNOWN_PUBLISHER_ID =
            UUID.fromString("cccccccc-bbbb-9999-bbbb-999999999999");
    private static final UUID SCENE_ID =
            UUID.fromString("dddddddd-bbbb-1111-bbbb-111111111111");
    private static final String TITLE = "Zen Series";
    private static final String UPDATED_TITLE = "Zen Saga";
    private static final String SECOND_TITLE = "alpha Series";
    private static final String PUBLISHER_NAME = "Zen Publisher";
    private static final String SECOND_PUBLISHER_NAME =
            "Second Publisher";
    private static final String SCENE_TITLE = "Referenced Scene";
    private static final String SEARCH_TEXT = "saga";

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private PublisherRepository publisherRepository;
    private SeriesRepository repository;
    private Publisher publisher;
    private Publisher secondPublisher;

    @BeforeEach
    void initializeDatabase() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        publisherRepository = new PublisherRepository(databaseManager);
        repository = new SeriesRepository(databaseManager);
        publisher = new Publisher(PUBLISHER_ID, PUBLISHER_NAME, List.of());
        secondPublisher = new Publisher(
                SECOND_PUBLISHER_ID,
                SECOND_PUBLISHER_NAME,
                List.of()
        );
        publisherRepository.insert(publisher);
        publisherRepository.insert(secondPublisher);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new SeriesRepository(null)
        );
    }

    @Test
    @DisplayName("Insert stores the title and publisher relationship")
    void insertStoresTitleAndPublisherRelationship() throws Exception {
        repository.insert(createSeries(SERIES_ID, TITLE, publisher));

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SELECT_SERIES_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    SERIES_ID.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(
                        TITLE,
                        resultSet.getString(FIRST_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        PUBLISHER_ID.toString(),
                        resultSet.getString(SECOND_COLUMN_INDEX)
                );
            }
        }
    }

    @Test
    @DisplayName("Find by ID reconstructs the series correctly")
    void findByIdReconstructsSeriesCorrectly() throws Exception {
        repository.insert(createSeries(SERIES_ID, TITLE, publisher));

        final Optional<Series> found = repository.findById(SERIES_ID);

        Assertions.assertTrue(found.isPresent());
        assertSeries(found.get(), SERIES_ID, TITLE, PUBLISHER_ID);
    }

    @Test
    @DisplayName("Find by ID returns empty for unknown UUID")
    void findByIdReturnsEmptyForUnknownUuid() throws Exception {
        Assertions.assertTrue(
                repository.findById(UNKNOWN_SERIES_ID).isEmpty()
        );
    }

    @Test
    @DisplayName("Find all returns deterministic results")
    void findAllReturnsDeterministicResults() throws Exception {
        repository.insert(createSeries(SERIES_ID, TITLE, publisher));
        repository.insert(createSeries(
                SECOND_SERIES_ID,
                SECOND_TITLE,
                publisher
        ));

        Assertions.assertEquals(
                List.of(SECOND_SERIES_ID, SERIES_ID),
                repository.findAll()
                        .stream()
                        .map(Series::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Find by publisher returns only matching series")
    void findByPublisherReturnsOnlyMatchingSeries() throws Exception {
        repository.insert(createSeries(SERIES_ID, TITLE, publisher));
        repository.insert(createSeries(
                SECOND_SERIES_ID,
                SECOND_TITLE,
                secondPublisher
        ));

        Assertions.assertEquals(
                List.of(SERIES_ID),
                repository.findByPublisher(PUBLISHER_ID)
                        .stream()
                        .map(Series::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Title search is case-insensitive")
    void titleSearchIsCaseInsensitive() throws Exception {
        repository.insert(createSeries(SERIES_ID, UPDATED_TITLE, publisher));

        Assertions.assertEquals(
                List.of(SERIES_ID),
                repository.searchByTitle(SEARCH_TEXT.toUpperCase())
                        .stream()
                        .map(Series::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Update changes the title")
    void updateChangesTitle() throws Exception {
        repository.insert(createSeries(SERIES_ID, TITLE, publisher));

        repository.update(createSeries(
                SERIES_ID,
                UPDATED_TITLE,
                publisher
        ));

        Assertions.assertEquals(
                UPDATED_TITLE,
                repository.findById(SERIES_ID).orElseThrow()
                        .getTitle()
        );
    }

    @Test
    @DisplayName("Update changes the publisher relationship")
    void updateChangesPublisherRelationship() throws Exception {
        repository.insert(createSeries(SERIES_ID, TITLE, publisher));

        repository.update(createSeries(
                SERIES_ID,
                TITLE,
                secondPublisher
        ));

        Assertions.assertEquals(
                SECOND_PUBLISHER_ID,
                repository.findById(SERIES_ID).orElseThrow()
                        .getPublisher()
                        .getId()
        );
    }

    @Test
    @DisplayName("Unknown publisher fails through foreign keys")
    void unknownPublisherFailsThroughForeignKeys() {
        final Publisher unknownPublisher = new Publisher(
                UNKNOWN_PUBLISHER_ID,
                "Unknown",
                List.of()
        );

        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        SQLException.class,
                        () -> repository.insert(createSeries(
                                SERIES_ID,
                                TITLE,
                                unknownPublisher
                        ))
                ),
                () -> {
                    repository.insert(createSeries(
                            SERIES_ID,
                            TITLE,
                            publisher
                    ));
                    Assertions.assertThrows(
                            SQLException.class,
                            () -> repository.update(createSeries(
                                    SERIES_ID,
                                    TITLE,
                                    unknownPublisher
                            ))
                    );
                }
        );
    }

    @Test
    @DisplayName("Delete removes the series when permitted")
    void deleteRemovesSeriesWhenPermitted() throws Exception {
        repository.insert(createSeries(SERIES_ID, TITLE, publisher));

        Assertions.assertTrue(repository.delete(SERIES_ID));
        Assertions.assertEquals(
                0,
                countSeriesById(SERIES_ID)
        );
    }

    @Test
    @DisplayName("Delete reports false for unknown UUID")
    void deleteReportsFalseForUnknownUuid() throws Exception {
        Assertions.assertFalse(repository.delete(UNKNOWN_SERIES_ID));
    }

    @Test
    @DisplayName("Referenced series restrictions are respected")
    void referencedSeriesRestrictionsAreRespected() throws Exception {
        repository.insert(createSeries(SERIES_ID, TITLE, publisher));
        insertReferencingScene();

        Assertions.assertThrows(
                SQLException.class,
                () -> repository.delete(SERIES_ID)
        );
        Assertions.assertEquals(
                1,
                countSeriesById(SERIES_ID)
        );
    }

    private Series createSeries(
            UUID id,
            String title,
            Publisher publisher) {

        return new Series(id, title, publisher);
    }

    private void assertSeries(
            Series series,
            UUID id,
            String title,
            UUID publisherId) {

        Assertions.assertEquals(id, series.getId());
        Assertions.assertEquals(title, series.getTitle());
        Assertions.assertEquals(publisherId, series.getPublisher().getId());
    }

    private int countSeriesById(UUID id) throws Exception {
        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             COUNT_SERIES_BY_ID_SQL
                     )) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    count = resultSet.getInt(FIRST_COLUMN_INDEX);
                }
            }
        }

        return count;
    }

    private void insertReferencingScene() throws Exception {
        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SCENE_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    SCENE_ID.toString()
            );
            statement.setString(SECOND_PARAMETER_INDEX, SCENE_TITLE);
            statement.setString(
                    THIRD_PARAMETER_INDEX,
                    PUBLISHER_ID.toString()
            );
            statement.setString(
                    FOURTH_PARAMETER_INDEX,
                    SERIES_ID.toString()
            );
            statement.executeUpdate();
        }
    }
}

package repository;

import database.DatabaseManager;
import database.SchemaManager;
import model.Publisher;
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

class PublisherRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "publisher-repository-test.db";

    private static final String PUBLISHER_COUNT_SQL = """
            SELECT COUNT(*)
            FROM publisher
            """;

    private static final String PUBLISHER_COUNT_BY_ID_SQL = """
            SELECT COUNT(*)
            FROM publisher
            WHERE id = ?
            """;

    private static final String ALIAS_COUNT_BY_PUBLISHER_ID_SQL = """
            SELECT COUNT(*)
            FROM publisher_alias
            WHERE publisher_id = ?
            """;

    private static final String NAME_BY_ID_SQL = """
            SELECT name
            FROM publisher
            WHERE id = ?
            """;

    private static final String ALIAS_LIST_BY_PUBLISHER_ID_SQL = """
            SELECT alias
            FROM publisher_alias
            WHERE publisher_id = ?
            ORDER BY alias COLLATE NOCASE
            """;

    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int FIRST_RESULT_COLUMN_INDEX = 1;

    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_PUBLISHER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID UNKNOWN_PUBLISHER_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final String PUBLISHER_NAME = "Zenith Video";
    private static final String UPDATED_PUBLISHER_NAME =
            "Zenith Studios";
    private static final String SECOND_PUBLISHER_NAME = "alpha Media";
    private static final String FIRST_ALIAS = "Zenith";
    private static final String SECOND_ALIAS = "ZV";
    private static final String UPDATED_ALIAS = "ZS";
    private static final String DUPLICATE_ALIAS = "Alias";
    private static final String DUPLICATE_ALIAS_DIFFERENT_CASE =
            "alias";

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private PublisherRepository repository;

    @BeforeEach
    void initializeDatabase() throws Exception {
        databaseManager = createDatabaseManager();

        final SchemaManager schemaManager =
                new SchemaManager(databaseManager);
        schemaManager.initialize();

        repository = new PublisherRepository(databaseManager);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new PublisherRepository(null)
        );
    }

    @Test
    @DisplayName("Insert stores the publisher")
    void insertStoresPublisher() throws Exception {
        final Publisher publisher =
                createPublisher(PUBLISHER_ID, PUBLISHER_NAME);

        repository.insert(publisher);

        Assertions.assertEquals(
                PUBLISHER_NAME,
                findPublisherName(PUBLISHER_ID)
        );
    }

    @Test
    @DisplayName("Insert stores all aliases")
    void insertStoresAllAliases() throws Exception {
        final Publisher publisher = createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME,
                FIRST_ALIAS,
                SECOND_ALIAS
        );

        repository.insert(publisher);

        Assertions.assertIterableEquals(
                List.of(FIRST_ALIAS, SECOND_ALIAS),
                findAliases(PUBLISHER_ID)
        );
    }

    @Test
    @DisplayName("Find by ID returns the publisher and aliases")
    void findByIdReturnsPublisherAndAliases() throws Exception {
        final Publisher publisher = createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME,
                FIRST_ALIAS,
                SECOND_ALIAS
        );
        repository.insert(publisher);

        final Optional<Publisher> found =
                repository.findById(PUBLISHER_ID);

        Assertions.assertTrue(found.isPresent());
        assertPublisher(
                found.get(),
                PUBLISHER_ID,
                PUBLISHER_NAME,
                List.of(FIRST_ALIAS, SECOND_ALIAS)
        );
    }

    @Test
    @DisplayName("Find by ID returns empty for an unknown UUID")
    void findByIdReturnsEmptyForUnknownUuid() throws Exception {
        final Optional<Publisher> found =
                repository.findById(UNKNOWN_PUBLISHER_ID);

        Assertions.assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Find all returns every publisher in deterministic order")
    void findAllReturnsEveryPublisherInDeterministicOrder()
            throws Exception {

        repository.insert(createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME,
                FIRST_ALIAS
        ));
        repository.insert(createPublisher(
                SECOND_PUBLISHER_ID,
                SECOND_PUBLISHER_NAME,
                SECOND_ALIAS
        ));

        final List<Publisher> publishers = repository.findAll();

        Assertions.assertEquals(
                List.of(SECOND_PUBLISHER_ID, PUBLISHER_ID),
                publishers.stream()
                        .map(Publisher::getId)
                        .toList()
        );
        Assertions.assertEquals(
                List.of(SECOND_ALIAS),
                publishers.getFirst().getAliases()
        );
        Assertions.assertEquals(
                List.of(FIRST_ALIAS),
                publishers.getLast().getAliases()
        );
    }

    @Test
    @DisplayName("Update changes the main name")
    void updateChangesMainName() throws Exception {
        repository.insert(createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME,
                FIRST_ALIAS
        ));

        repository.update(createPublisher(
                PUBLISHER_ID,
                UPDATED_PUBLISHER_NAME,
                FIRST_ALIAS
        ));

        Assertions.assertEquals(
                UPDATED_PUBLISHER_NAME,
                findPublisherName(PUBLISHER_ID)
        );
    }

    @Test
    @DisplayName("Update replaces aliases correctly")
    void updateReplacesAliasesCorrectly() throws Exception {
        repository.insert(createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME,
                FIRST_ALIAS,
                SECOND_ALIAS
        ));

        repository.update(createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME,
                SECOND_ALIAS,
                UPDATED_ALIAS
        ));

        Assertions.assertIterableEquals(
                List.of(UPDATED_ALIAS, SECOND_ALIAS),
                findAliases(PUBLISHER_ID)
        );
    }

    @Test
    @DisplayName("Delete removes the publisher")
    void deleteRemovesPublisher() throws Exception {
        repository.insert(createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME
        ));

        final boolean deleted = repository.delete(PUBLISHER_ID);

        Assertions.assertTrue(deleted);
        Assertions.assertEquals(
                0,
                countPublishersById(PUBLISHER_ID)
        );
    }

    @Test
    @DisplayName("Delete removes associated alias rows")
    void deleteRemovesAssociatedAliasRows() throws Exception {
        repository.insert(createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME,
                FIRST_ALIAS,
                SECOND_ALIAS
        ));

        repository.delete(PUBLISHER_ID);

        Assertions.assertEquals(
                0,
                countAliasesByPublisherId(PUBLISHER_ID)
        );
    }

    @Test
    @DisplayName("Delete returns false for an unknown UUID")
    void deleteReturnsFalseForUnknownUuid() throws Exception {
        final boolean deleted =
                repository.delete(UNKNOWN_PUBLISHER_ID);

        Assertions.assertFalse(deleted);
    }

    @Test
    @DisplayName("Failed multi-table write rolls back completely")
    void failedMultiTableWriteRollsBackCompletely() {
        final Publisher publisher = createPublisher(
                PUBLISHER_ID,
                PUBLISHER_NAME,
                DUPLICATE_ALIAS,
                DUPLICATE_ALIAS_DIFFERENT_CASE
        );

        Assertions.assertThrows(
                SQLException.class,
                () -> repository.insert(publisher)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        0,
                        countPublishers()
                ),
                () -> Assertions.assertEquals(
                        0,
                        countAliasesByPublisherId(PUBLISHER_ID)
                )
        );
    }

    private DatabaseManager createDatabaseManager() {
        final Path databasePath =
                temporaryDirectory.resolve(DATABASE_FILE_NAME);

        return new DatabaseManager(databasePath);
    }

    private Publisher createPublisher(
            UUID id,
            String name,
            String... aliases) {

        return new Publisher(id, name, List.of(aliases));
    }

    private void assertPublisher(
            Publisher publisher,
            UUID expectedId,
            String expectedName,
            List<String> expectedAliases) {

        Assertions.assertEquals(
                expectedId,
                publisher.getId()
        );
        Assertions.assertEquals(
                expectedName,
                publisher.getName()
        );
        Assertions.assertIterableEquals(
                expectedAliases,
                publisher.getAliases()
        );
    }

    private String findPublisherName(UUID id) throws Exception {
        String name = null;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(NAME_BY_ID_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    id.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    name = resultSet.getString(
                            FIRST_RESULT_COLUMN_INDEX
                    );
                }
            }
        }

        return name;
    }

    private List<String> findAliases(UUID publisherId)
            throws Exception {

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             ALIAS_LIST_BY_PUBLISHER_ID_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    publisherId.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                final java.util.ArrayList<String> aliases =
                        new java.util.ArrayList<>();

                while (resultSet.next()) {
                    aliases.add(resultSet.getString(
                            FIRST_RESULT_COLUMN_INDEX
                    ));
                }

                return aliases;
            }
        }
    }

    private int countPublishers() throws Exception {
        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             PUBLISHER_COUNT_SQL
                     );
             ResultSet resultSet =
                     statement.executeQuery()) {

            if (resultSet.next()) {
                count = resultSet.getInt(
                        FIRST_RESULT_COLUMN_INDEX
                );
            }
        }

        return count;
    }

    private int countPublishersById(UUID publisherId)
            throws Exception {

        return countByPublisherId(
                PUBLISHER_COUNT_BY_ID_SQL,
                publisherId
        );
    }

    private int countAliasesByPublisherId(UUID publisherId)
            throws Exception {

        return countByPublisherId(
                ALIAS_COUNT_BY_PUBLISHER_ID_SQL,
                publisherId
        );
    }

    private int countByPublisherId(
            String sql,
            UUID publisherId) throws Exception {

        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    publisherId.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    count = resultSet.getInt(
                            FIRST_RESULT_COLUMN_INDEX
                    );
                }
            }
        }

        return count;
    }
}

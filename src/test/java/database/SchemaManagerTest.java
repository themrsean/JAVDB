package database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;

class SchemaManagerTest {
    private static final String DATABASE_FILE_NAME =
            "schema-manager-test.db";

    private static final String SCHEMA_VERSION_KEY =
            "schema_version";
    private static final String VERSION_ONE =
            "1";
    private static final String VERSION_TWO =
            "2";
    private static final String CURRENT_SCHEMA_VERSION =
            "3";
    private static final String UNSUPPORTED_SCHEMA_VERSION =
            "999";

    private static final String CUSTOM_METADATA_KEY =
            "test_key";
    private static final String CUSTOM_METADATA_VALUE =
            "test_value";

    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int FIRST_RESULT_COLUMN_INDEX = 1;

    private static final Set<String> EXPECTED_TABLE_NAMES = Set.of(
            "app_metadata",
            "performer",
            "performer_alias",
            "publisher",
            "publisher_alias",
            "series",
            "scene",
            "movie",
            "media_file",
            "media_location",
            "scene_performer",
            "movie_scene",
            "scene_media_file",
            "movie_media_file"
    );

    private static final String TABLE_EXISTS_SQL = """
            SELECT COUNT(*)
            FROM sqlite_master
            WHERE type = 'table'
              AND name = ?
            """;

    private static final String SELECT_METADATA_SQL = """
            SELECT metadata_value
            FROM app_metadata
            WHERE metadata_key = ?
            """;

    private static final String INSERT_METADATA_SQL = """
            INSERT INTO app_metadata(metadata_key, metadata_value)
            VALUES (?, ?)
            """;

    private static final String UPDATE_SCHEMA_VERSION_SQL = """
            UPDATE app_metadata
            SET metadata_value = ?
            WHERE metadata_key = ?
            """;

    private static final String CREATE_MALFORMED_METADATA_TABLE_SQL = """
            CREATE TABLE app_metadata (
                placeholder TEXT
            )
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new SchemaManager(null)
        );
    }

    @Test
    @DisplayName("Initialization creates all expected tables")
    void initializeCreatesAllExpectedTables() throws Exception {
        final DatabaseManager databaseManager =
                createDatabaseManager();
        final SchemaManager schemaManager =
                new SchemaManager(databaseManager);

        schemaManager.initialize();

        try (Connection connection =
                     databaseManager.openConnection()) {

            for (String tableName : EXPECTED_TABLE_NAMES) {
                Assertions.assertTrue(
                        tableExists(connection, tableName),
                        "Expected table was not created: "
                                + tableName
                );
            }
        }
    }

    @Test
    @DisplayName("Initialization records current schema version")
    void initializeRecordsCurrentSchemaVersion() throws Exception {
        final DatabaseManager databaseManager =
                createDatabaseManager();
        final SchemaManager schemaManager =
                new SchemaManager(databaseManager);

        schemaManager.initialize();

        try (Connection connection =
                     databaseManager.openConnection()) {

            final String schemaVersion = readMetadataValue(
                    connection,
                    SCHEMA_VERSION_KEY
            );

            Assertions.assertEquals(
                    CURRENT_SCHEMA_VERSION,
                    schemaVersion
            );
        }
    }

    @Test
    @DisplayName("New database contains scene verification status column")
    void newDatabaseContainsSceneVerificationStatusColumn() throws Exception {
        final DatabaseManager databaseManager = createDatabaseManager();
        new SchemaManager(databaseManager).initialize();

        try (Connection connection = databaseManager.openConnection()) {
            Assertions.assertTrue(
                    columnExists(connection, "scene", "verification_status")
            );
        }
    }

    @Test
    @DisplayName("New database contains media location table")
    void newDatabaseContainsMediaLocationTable() throws Exception {
        final DatabaseManager databaseManager = createDatabaseManager();
        new SchemaManager(databaseManager).initialize();

        try (Connection connection = databaseManager.openConnection()) {
            Assertions.assertAll(
                    () -> Assertions.assertTrue(
                            tableExists(connection, "media_location")
                    ),
                    () -> Assertions.assertTrue(
                            columnExists(connection, "media_location", "path")
                    ),
                    () -> Assertions.assertTrue(
                            columnExists(
                                    connection,
                                    "media_location",
                                    "last_scan_status"
                            )
                    )
            );
        }
    }

    @Test
    @DisplayName("Version one database migrates scene verification status")
    void versionOneDatabaseMigratesSceneVerificationStatus() throws Exception {
        final DatabaseManager databaseManager = createDatabaseManager();
        createVersionOneDatabase(databaseManager);

        new SchemaManager(databaseManager).initialize();

        try (Connection connection = databaseManager.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT verification_status
                     FROM scene
                     WHERE id = 'scene-id'
                     """)) {

            Assertions.assertAll(
                    () -> Assertions.assertEquals(
                            CURRENT_SCHEMA_VERSION,
                            readMetadataValue(connection, SCHEMA_VERSION_KEY)
                    ),
                    () -> Assertions.assertTrue(resultSet.next()),
                    () -> Assertions.assertEquals(
                            "UNVERIFIED",
                            resultSet.getString(FIRST_RESULT_COLUMN_INDEX)
                    )
            );
        }
    }

    @Test
    @DisplayName("Version one migration is idempotent")
    void versionOneMigrationIsIdempotent() throws Exception {
        final DatabaseManager databaseManager = createDatabaseManager();
        createVersionOneDatabase(databaseManager);
        final SchemaManager schemaManager = new SchemaManager(databaseManager);

        schemaManager.initialize();

        Assertions.assertDoesNotThrow(schemaManager::initialize);
    }

    @Test
    @DisplayName("Version two database migrates media locations")
    void versionTwoDatabaseMigratesMediaLocations() throws Exception {
        final DatabaseManager databaseManager = createDatabaseManager();
        createVersionTwoDatabase(databaseManager);

        new SchemaManager(databaseManager).initialize();

        try (Connection connection = databaseManager.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT path
                     FROM media_file
                     WHERE id = 'media-id'
                     """)) {

            Assertions.assertAll(
                    () -> Assertions.assertEquals(
                            CURRENT_SCHEMA_VERSION,
                            readMetadataValue(connection, SCHEMA_VERSION_KEY)
                    ),
                    () -> Assertions.assertTrue(
                            tableExists(connection, "media_location")
                    ),
                    () -> Assertions.assertTrue(resultSet.next()),
                    () -> Assertions.assertEquals(
                            "/tmp/example.mp4",
                            resultSet.getString(FIRST_RESULT_COLUMN_INDEX)
                    )
            );
        }
    }

    @Test
    @DisplayName("Media location status constraint rejects invalid status")
    void mediaLocationStatusConstraintRejectsInvalidStatus()
            throws Exception {

        final DatabaseManager databaseManager = createDatabaseManager();
        new SchemaManager(databaseManager).initialize();

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO media_location(
                         id,
                         path,
                         enabled,
                         recursive,
                         created_at,
                         updated_at,
                         last_scan_status
                     )
                     VALUES (?, ?, TRUE, TRUE, ?, ?, ?)
                     """)) {

            statement.setString(FIRST_PARAMETER_INDEX, "location-id");
            statement.setString(SECOND_PARAMETER_INDEX, "/tmp/media");
            statement.setString(3, "2026-01-01T00:00:00Z");
            statement.setString(4, "2026-01-01T00:00:00Z");
            statement.setString(5, "NOT_A_STATUS");

            Assertions.assertThrows(SQLException.class, statement::executeUpdate);
        }
    }

    @Test
    @DisplayName("Media location path rejects null and duplicate values")
    void mediaLocationPathRejectsNullAndDuplicateValues() throws Exception {
        final DatabaseManager databaseManager = createDatabaseManager();
        new SchemaManager(databaseManager).initialize();

        try (Connection connection = databaseManager.openConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    INSERT INTO media_location(
                        id,
                        path,
                        enabled,
                        recursive,
                        created_at,
                        updated_at,
                        last_scan_status
                    )
                    VALUES (
                        'location-one',
                        '/tmp/media',
                        TRUE,
                        TRUE,
                        '2026-01-01T00:00:00Z',
                        '2026-01-01T00:00:00Z',
                        'NEVER_SCANNED'
                    )
                    """);

            Assertions.assertAll(
                    () -> Assertions.assertThrows(
                            SQLException.class,
                            () -> statement.execute("""
                                    INSERT INTO media_location(
                                        id,
                                        path,
                                        enabled,
                                        recursive,
                                        created_at,
                                        updated_at,
                                        last_scan_status
                                    )
                                    VALUES (
                                        'location-two',
                                        NULL,
                                        TRUE,
                                        TRUE,
                                        '2026-01-01T00:00:00Z',
                                        '2026-01-01T00:00:00Z',
                                        'NEVER_SCANNED'
                                    )
                                    """)
                    ),
                    () -> Assertions.assertThrows(
                            SQLException.class,
                            () -> statement.execute("""
                                    INSERT INTO media_location(
                                        id,
                                        path,
                                        enabled,
                                        recursive,
                                        created_at,
                                        updated_at,
                                        last_scan_status
                                    )
                                    VALUES (
                                        'location-three',
                                        '/tmp/media',
                                        TRUE,
                                        TRUE,
                                        '2026-01-01T00:00:00Z',
                                        '2026-01-01T00:00:00Z',
                                        'NEVER_SCANNED'
                                    )
                                    """)
                    )
            );
        }
    }

    @Test
    @DisplayName("Initialization can run more than once")
    void initializeCanRunMoreThanOnce() throws Exception {
        final DatabaseManager databaseManager =
                createDatabaseManager();
        final SchemaManager schemaManager =
                new SchemaManager(databaseManager);

        schemaManager.initialize();

        Assertions.assertDoesNotThrow(
                schemaManager::initialize
        );
    }

    @Test
    @DisplayName("Repeated initialization preserves existing data")
    void repeatedInitializationPreservesExistingData()
            throws Exception {

        final DatabaseManager databaseManager =
                createDatabaseManager();
        final SchemaManager schemaManager =
                new SchemaManager(databaseManager);

        schemaManager.initialize();

        try (Connection connection =
                     databaseManager.openConnection()) {

            insertMetadata(
                    connection,
                    CUSTOM_METADATA_KEY,
                    CUSTOM_METADATA_VALUE
            );
        }

        schemaManager.initialize();

        try (Connection connection =
                     databaseManager.openConnection()) {

            final String storedValue = readMetadataValue(
                    connection,
                    CUSTOM_METADATA_KEY
            );

            Assertions.assertEquals(
                    CUSTOM_METADATA_VALUE,
                    storedValue
            );
        }
    }

    @Test
    @DisplayName("Initialization rejects an unsupported schema version")
    void initializeRejectsUnsupportedSchemaVersion()
            throws Exception {

        final DatabaseManager databaseManager =
                createDatabaseManager();
        final SchemaManager schemaManager =
                new SchemaManager(databaseManager);

        schemaManager.initialize();

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             UPDATE_SCHEMA_VERSION_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    UNSUPPORTED_SCHEMA_VERSION
            );

            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    SCHEMA_VERSION_KEY
            );

            statement.executeUpdate();
        }

        final SQLException exception =
                Assertions.assertThrows(
                        SQLException.class,
                        schemaManager::initialize
                );

        Assertions.assertTrue(
                exception.getMessage().contains(
                        UNSUPPORTED_SCHEMA_VERSION
                )
        );
    }

    @Test
    @DisplayName("Initialization rolls back schema changes after failure")
    void initializeRollsBackSchemaChangesAfterFailure()
            throws Exception {

        final DatabaseManager databaseManager =
                createDatabaseManager();

        createMalformedMetadataTable(databaseManager);

        final SchemaManager schemaManager =
                new SchemaManager(databaseManager);

        Assertions.assertThrows(
                SQLException.class,
                schemaManager::initialize
        );

        try (Connection connection =
                     databaseManager.openConnection()) {

            for (String tableName : EXPECTED_TABLE_NAMES) {
                final boolean isMalformedMetadataTable =
                        "app_metadata".equals(tableName);

                if (!isMalformedMetadataTable) {
                    Assertions.assertFalse(
                            tableExists(connection, tableName),
                            "Table should have been rolled back: "
                                    + tableName
                    );
                }
            }

            Assertions.assertTrue(
                    tableExists(connection, "app_metadata"),
                    "The preexisting malformed table should remain."
            );
        }
    }

    private DatabaseManager createDatabaseManager() {
        final Path databasePath =
                temporaryDirectory.resolve(
                        DATABASE_FILE_NAME
                );

        return new DatabaseManager(databasePath);
    }

    private boolean tableExists(
            Connection connection,
            String tableName) throws SQLException {

        boolean exists = false;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             TABLE_EXISTS_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    tableName
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                final boolean resultAvailable =
                        resultSet.next();

                if (resultAvailable) {
                    final int matchingTableCount =
                            resultSet.getInt(
                                    FIRST_RESULT_COLUMN_INDEX
                            );

                    exists = matchingTableCount > 0;
                }
            }
        }

        return exists;
    }

    private boolean columnExists(
            Connection connection,
            String tableName,
            String columnName) throws SQLException {

        boolean exists = false;

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "PRAGMA table_info(" + tableName + ")"
             )) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    exists = true;
                }
            }
        }

        return exists;
    }

    private String readMetadataValue(
            Connection connection,
            String metadataKey) throws SQLException {

        String metadataValue = null;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             SELECT_METADATA_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    metadataKey
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    metadataValue = resultSet.getString(
                            FIRST_RESULT_COLUMN_INDEX
                    );
                }
            }
        }

        return metadataValue;
    }

    private void insertMetadata(
            Connection connection,
            String metadataKey,
            String metadataValue) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_METADATA_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    metadataKey
            );

            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    metadataValue
            );

            statement.executeUpdate();
        }
    }

    private void createMalformedMetadataTable(
            DatabaseManager databaseManager)
            throws Exception {

        try (Connection connection =
                     databaseManager.openConnection();
             Statement statement =
                     connection.createStatement()) {

            statement.execute(
                    CREATE_MALFORMED_METADATA_TABLE_SQL
            );
        }
    }

    private void createVersionOneDatabase(DatabaseManager databaseManager)
            throws Exception {

        try (Connection connection = databaseManager.openConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE app_metadata (
                        metadata_key TEXT PRIMARY KEY,
                        metadata_value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO app_metadata(metadata_key, metadata_value)
                    VALUES ('schema_version', '""" + VERSION_ONE + """
                    ')
                    """);
            statement.execute("""
                    CREATE TABLE publisher (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL COLLATE NOCASE
                    )
                    """);
            statement.execute("""
                    INSERT INTO publisher(id, name)
                    VALUES ('publisher-id', 'Publisher')
                    """);
            statement.execute("""
                    CREATE TABLE scene (
                        id TEXT PRIMARY KEY,
                        title TEXT NOT NULL COLLATE NOCASE,
                        code TEXT COLLATE NOCASE,
                        release_date TEXT,
                        publisher_id TEXT NOT NULL,
                        series_id TEXT,
                        season TEXT,
                        episode TEXT,
                        FOREIGN KEY (publisher_id) REFERENCES publisher(id)
                    )
                    """);
            statement.execute("""
                    INSERT INTO scene(
                        id, title, code, release_date, publisher_id,
                        series_id, season, episode
                    )
                    VALUES (
                        'scene-id', 'Scene', NULL, NULL, 'publisher-id',
                        NULL, NULL, NULL
                    )
                    """);
        }
    }

    private void createVersionTwoDatabase(DatabaseManager databaseManager)
            throws Exception {

        try (Connection connection = databaseManager.openConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("""
                    CREATE TABLE app_metadata (
                        metadata_key TEXT PRIMARY KEY,
                        metadata_value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO app_metadata(metadata_key, metadata_value)
                    VALUES ('schema_version', '""" + VERSION_TWO + """
                    ')
                    """);
            statement.execute("""
                    CREATE TABLE media_file (
                        id TEXT PRIMARY KEY,
                        path TEXT NOT NULL UNIQUE,
                        file_size INTEGER,
                        duration_millis INTEGER,
                        width INTEGER,
                        height INTEGER,
                        content_hash TEXT,
                        last_modified_millis INTEGER
                    )
                    """);
            statement.execute("""
                    INSERT INTO media_file(
                        id,
                        path,
                        file_size,
                        duration_millis,
                        width,
                        height,
                        content_hash,
                        last_modified_millis
                    )
                    VALUES (
                        'media-id',
                        '/tmp/example.mp4',
                        100,
                        1000,
                        1920,
                        1080,
                        NULL,
                        1700000000000
                    )
                    """);
        }
    }
}

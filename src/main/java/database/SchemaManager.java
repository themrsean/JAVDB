package database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SchemaManager {
    private static final String SCHEMA_RESOURCE = "/db/schema.sql";
    private static final String SQL_STATEMENT_SEPARATOR = ";";
    private static final String SQL_COMMENT_PREFIX = "--";

    private static final String SCHEMA_VERSION_SELECT_SQL = """
            SELECT metadata_value
            FROM app_metadata
            WHERE metadata_key = ?
            """;

    private static final String METADATA_INSERT_SQL = """
            INSERT INTO app_metadata(metadata_key, metadata_value)
            VALUES (?, ?)
            """;
    private static final String METADATA_UPDATE_SQL = """
            UPDATE app_metadata
            SET metadata_value = ?
            WHERE metadata_key = ?
            """;
    private static final String ADD_SCENE_VERIFICATION_STATUS_SQL = """
            ALTER TABLE scene
            ADD COLUMN verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED'
            CHECK (
                verification_status IN (
                    'UNVERIFIED', 'VERIFIED', 'NEEDS_REVIEW'
                )
            )
            """;
    private static final String CREATE_MEDIA_LOCATION_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS media_location (
                id TEXT PRIMARY KEY,
                path TEXT NOT NULL UNIQUE,
                enabled INTEGER NOT NULL CHECK (enabled IN (FALSE, TRUE)),
                recursive INTEGER NOT NULL CHECK (recursive IN (FALSE, TRUE)),
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                last_scan_started_at TEXT,
                last_scan_completed_at TEXT,
                last_scan_status TEXT NOT NULL DEFAULT 'NEVER_SCANNED' CHECK (
                    last_scan_status IN (
                        'NEVER_SCANNED',
                        'RUNNING',
                        'COMPLETED',
                        'COMPLETED_WITH_ERRORS',
                        'CANCELLED',
                        'FAILED',
                        'DIRECTORY_UNAVAILABLE'
                    )
                ),
                last_scan_message TEXT,
                last_discovered_count INTEGER NOT NULL DEFAULT 0,
                last_new_count INTEGER NOT NULL DEFAULT 0,
                last_updated_count INTEGER NOT NULL DEFAULT 0,
                last_unchanged_count INTEGER NOT NULL DEFAULT 0,
                last_missing_count INTEGER NOT NULL DEFAULT 0,
                last_failed_count INTEGER NOT NULL DEFAULT 0
            )
            """;

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final String VERSION_ONE = "1";
    private static final String VERSION_TWO = "2";
    private static final String CURRENT_SCHEMA_VERSION = "3";

    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int METADATA_VALUE_COLUMN_INDEX = 1;

    private final DatabaseManager databaseManager;

    public SchemaManager(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public void initialize() throws IOException, SQLException {
        final String schema = readSchema();
        final List<String> statements = parseStatements(schema);

        try (Connection connection =
                     databaseManager.openConnection()) {
            connection.setAutoCommit(false);

            try {
                executeStatements(connection, statements);
                validateOrRecordSchemaVersion(connection);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    private String readSchema() throws IOException {
        final InputStream inputStream =
                SchemaManager.class.getResourceAsStream(
                        SCHEMA_RESOURCE
                );

        if (inputStream == null) {
            throw new IOException(
                    "Schema resource not found: "
                            + SCHEMA_RESOURCE
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        inputStream,
                        StandardCharsets.UTF_8
                ))) {

            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line ->
                            !line.startsWith(SQL_COMMENT_PREFIX))
                    .collect(Collectors.joining(
                            System.lineSeparator()
                    ));
        }
    }

    private List<String> parseStatements(String schema) {
        final String separatorExpression =
                Pattern.quote(SQL_STATEMENT_SEPARATOR);

        return Arrays.stream(
                        schema.split(separatorExpression)
                )
                .map(String::trim)
                .filter(statement -> !statement.isBlank())
                .toList();
    }

    private void executeStatements(
            Connection connection,
            List<String> statements) throws SQLException {

        try (Statement statement =
                     connection.createStatement()) {

            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private void validateOrRecordSchemaVersion(
            Connection connection) throws SQLException {

        final Optional<String> existingVersion =
                findSchemaVersion(connection);

        if (existingVersion.isEmpty()) {
            recordCurrentSchemaVersion(connection);
        } else if (VERSION_ONE.equals(existingVersion.get())) {
            migrateFromVersionOneToTwo(connection);
            migrateFromVersionTwoToThree(connection);
        } else if (VERSION_TWO.equals(existingVersion.get())) {
            migrateFromVersionTwoToThree(connection);
        } else if (!CURRENT_SCHEMA_VERSION.equals(existingVersion.get())) {
            throw new SQLException(
                    "Unsupported database schema version: "
                            + existingVersion.get()
                            + ". Expected version: "
                            + CURRENT_SCHEMA_VERSION
            );
        }
    }

    private Optional<String> findSchemaVersion(
            Connection connection) throws SQLException {

        Optional<String> schemaVersion = Optional.empty();

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             SCHEMA_VERSION_SELECT_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    SCHEMA_VERSION_KEY
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    schemaVersion = Optional.ofNullable(
                            resultSet.getString(
                                    METADATA_VALUE_COLUMN_INDEX
                            )
                    );
                }
            }
        }

        return schemaVersion;
    }

    private void recordCurrentSchemaVersion(
            Connection connection) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             METADATA_INSERT_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    SCHEMA_VERSION_KEY
            );

            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    CURRENT_SCHEMA_VERSION
            );

            statement.executeUpdate();
        }
    }

    private void migrateFromVersionOneToTwo(Connection connection)
            throws SQLException {

        try (Statement statement = connection.createStatement()) {
            statement.execute(ADD_SCENE_VERIFICATION_STATUS_SQL);
        }

        updateSchemaVersion(connection, VERSION_TWO);
    }

    private void migrateFromVersionTwoToThree(Connection connection)
            throws SQLException {

        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_MEDIA_LOCATION_TABLE_SQL);
        }

        updateSchemaVersion(connection, CURRENT_SCHEMA_VERSION);
    }

    private void updateSchemaVersion(Connection connection, String version)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(METADATA_UPDATE_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, version);
            statement.setString(SECOND_PARAMETER_INDEX, SCHEMA_VERSION_KEY);
            statement.executeUpdate();
        }
    }

    private void rollback(
            Connection connection,
            SQLException originalException) {

        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(
                    rollbackException
            );
        }
    }
}

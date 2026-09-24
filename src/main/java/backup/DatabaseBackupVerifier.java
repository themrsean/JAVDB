package backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DatabaseBackupVerifier {
    public static final String SUPPORTED_SCHEMA_VERSION = "3";

    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final String OK = "ok";
    private static final int FIRST_COLUMN_INDEX = 1;
    private static final List<String> REQUIRED_TABLES = List.of(
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

    public BackupVerificationResult verify(
            Path databasePath,
            BackupVerificationLevel level) throws IOException, SQLException {

        Objects.requireNonNull(databasePath, "Database path must not be null");
        final BackupVerificationLevel effectiveLevel = level == null
                ? BackupVerificationLevel.QUICK
                : level;
        final Path path = databasePath.toAbsolutePath().normalize();
        final List<String> messages = new ArrayList<>();
        final List<String> integrityMessages = new ArrayList<>();
        final List<String> foreignKeyViolations = new ArrayList<>();
        final List<String> missingTables = new ArrayList<>();
        String schemaVersion = null;
        String error = null;
        long fileSize = 0L;

        if (!Files.exists(path)) {
            error = "Database file does not exist.";
        } else if (!Files.isRegularFile(path)) {
            error = "Database path is not a regular file.";
        } else {
            fileSize = Files.size(path);

            if (fileSize == 0L) {
                error = "Database file is empty.";
            } else {
                try (Connection connection =
                             DriverManager.getConnection(JDBC_PREFIX + path);
                     Statement statement = connection.createStatement()) {

                    statement.execute("PRAGMA query_only = ON");
                    integrityMessages.addAll(readSingleColumnRows(
                            statement,
                            effectiveLevel == BackupVerificationLevel.FULL
                                    ? "PRAGMA integrity_check"
                                    : "PRAGMA quick_check"
                    ));
                    foreignKeyViolations.addAll(readForeignKeyViolations(
                            statement
                    ));
                    missingTables.addAll(missingTables(statement));
                    schemaVersion = readSchemaVersion(statement);
                } catch (SQLException exception) {
                    error = concise(exception);
                }
            }
        }

        if (error == null) {
            if (integrityMessages.stream().anyMatch(
                    message -> !OK.equalsIgnoreCase(message)
            )) {
                messages.add("Integrity check failed.");
            }

            if (!foreignKeyViolations.isEmpty()) {
                messages.add("Foreign-key check failed.");
            }

            if (!missingTables.isEmpty()) {
                messages.add("Required tables are missing.");
            }

            if (schemaVersion == null) {
                messages.add("Schema version is missing.");
            } else if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
                messages.add("Unsupported schema version: " + schemaVersion);
            }
        } else {
            messages.add(error);
        }

        final BackupVerificationStatus status =
                error == null
                        && messages.isEmpty()
                        && missingTables.isEmpty()
                        && foreignKeyViolations.isEmpty()
                        ? BackupVerificationStatus.VALID
                        : BackupVerificationStatus.INVALID;

        return new BackupVerificationResult(
                status,
                effectiveLevel,
                path,
                fileSize,
                schemaVersion,
                List.copyOf(integrityMessages),
                List.copyOf(foreignKeyViolations),
                List.copyOf(missingTables),
                List.copyOf(messages),
                error
        );
    }

    private List<String> readSingleColumnRows(
            Statement statement,
            String sql) throws SQLException {

        final List<String> rows = new ArrayList<>();

        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                rows.add(resultSet.getString(FIRST_COLUMN_INDEX));
            }
        }

        return rows;
    }

    private List<String> readForeignKeyViolations(Statement statement)
            throws SQLException {

        final List<String> violations = new ArrayList<>();

        try (ResultSet resultSet =
                     statement.executeQuery("PRAGMA foreign_key_check")) {
            while (resultSet.next()) {
                violations.add(resultSet.getString(FIRST_COLUMN_INDEX));
            }
        }

        return violations;
    }

    private List<String> missingTables(Statement statement)
            throws SQLException {

        final List<String> missingTables = new ArrayList<>();

        for (String tableName : REQUIRED_TABLES) {
            if (!tableExists(statement, tableName)) {
                missingTables.add(tableName);
            }
        }

        return missingTables;
    }

    private boolean tableExists(Statement statement, String tableName)
            throws SQLException {

        boolean exists = false;

        try (ResultSet resultSet = statement.executeQuery(
                "SELECT COUNT(*) FROM sqlite_master "
                        + "WHERE type = 'table' AND name = "
                        + sqlLiteral(tableName)
        )) {
            if (resultSet.next()) {
                exists = resultSet.getInt(FIRST_COLUMN_INDEX) > 0;
            }
        }

        return exists;
    }

    private String readSchemaVersion(Statement statement)
            throws SQLException {

        String schemaVersion = null;

        if (tableExists(statement, "app_metadata")) {
            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT metadata_value
                    FROM app_metadata
                    WHERE metadata_key = 'schema_version'
                    """)) {
                if (resultSet.next()) {
                    schemaVersion = resultSet.getString(FIRST_COLUMN_INDEX);
                }
            }
        }

        return schemaVersion;
    }

    private String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private String concise(SQLException exception) {
        final String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }
}

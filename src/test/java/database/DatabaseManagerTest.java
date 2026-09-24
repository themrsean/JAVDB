package database;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

class DatabaseManagerTest {
    private static final String DATABASE_FILE_NAME = "javdb-test.db";
    private static final String FOREIGN_KEYS_SQL =
            "PRAGMA foreign_keys";
    private static final String FOREIGN_KEYS_COLUMN =
            "foreign_keys";
    private static final int FOREIGN_KEYS_ENABLED_VALUE = 1;

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Constructor stores an absolute normalized database path")
    void constructorStoresAbsoluteNormalizedPath() {
        final Path suppliedPath = temporaryDirectory
                .resolve("temporary")
                .resolve("..")
                .resolve("database")
                .resolve(DATABASE_FILE_NAME);

        final Path expectedPath =
                suppliedPath.toAbsolutePath().normalize();

        final DatabaseManager databaseManager =
                new DatabaseManager(suppliedPath);

        Assertions.assertEquals(
                expectedPath,
                databaseManager.getDatabasePath()
        );
    }

    @Test
    @DisplayName("Opening a connection creates missing parent directories")
    void openConnectionCreatesMissingParentDirectories()
            throws Exception {
        final Path databaseDirectory = temporaryDirectory
                .resolve("nested")
                .resolve("database");

        final Path databasePath =
                databaseDirectory.resolve(DATABASE_FILE_NAME);

        final DatabaseManager databaseManager =
                new DatabaseManager(databasePath);

        Assertions.assertFalse(Files.exists(databaseDirectory));

        try (Connection connection =
                     databaseManager.openConnection()) {
            Assertions.assertNotNull(connection);
        }

        Assertions.assertTrue(Files.isDirectory(databaseDirectory));
        Assertions.assertTrue(Files.isRegularFile(databasePath));
    }

    @Test
    @DisplayName("Opening a connection returns an open JDBC connection")
    void openConnectionReturnsOpenConnection() throws Exception {
        final Path databasePath =
                temporaryDirectory.resolve(DATABASE_FILE_NAME);

        final DatabaseManager databaseManager =
                new DatabaseManager(databasePath);

        try (Connection connection =
                     databaseManager.openConnection()) {
            Assertions.assertNotNull(connection);
            Assertions.assertFalse(connection.isClosed());
        }
    }

    @Test
    @DisplayName("Every connection has SQLite foreign keys enabled")
    void openConnectionEnablesForeignKeys() throws Exception {
        final Path databasePath =
                temporaryDirectory.resolve(DATABASE_FILE_NAME);

        final DatabaseManager databaseManager =
                new DatabaseManager(databasePath);

        try (Connection connection =
                     databaseManager.openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet =
                     statement.executeQuery(FOREIGN_KEYS_SQL)) {

            final boolean resultAvailable = resultSet.next();

            Assertions.assertTrue(
                    resultAvailable,
                    "The foreign-key PRAGMA did not return a result."
            );

            final int foreignKeysValue =
                    resultSet.getInt(FOREIGN_KEYS_COLUMN);

            Assertions.assertEquals(
                    FOREIGN_KEYS_ENABLED_VALUE,
                    foreignKeysValue,
                    "SQLite foreign-key enforcement was not enabled."
            );
        }
    }
}
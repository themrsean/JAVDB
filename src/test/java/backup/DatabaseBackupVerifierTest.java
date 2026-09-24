package backup;

import database.DatabaseManager;
import database.SchemaManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

class DatabaseBackupVerifierTest {
    private static final String DATABASE_FILE_NAME = "verify.db";
    private static final String UNSUPPORTED_VERSION = "999";

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Valid initialized database passes quick verification")
    void validInitializedDatabasePassesQuickVerification() throws Exception {
        final Path database = initializedDatabase(DATABASE_FILE_NAME);
        final DatabaseBackupVerifier verifier = new DatabaseBackupVerifier();

        final BackupVerificationResult result = verifier.verify(
                database,
                BackupVerificationLevel.QUICK
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        BackupVerificationStatus.VALID,
                        result.status()
                ),
                () -> Assertions.assertEquals("3", result.schemaVersion()),
                () -> Assertions.assertTrue(result.fileSize() > 0L)
        );
    }

    @Test
    @DisplayName("Valid initialized database passes full verification")
    void validInitializedDatabasePassesFullVerification() throws Exception {
        final Path database = initializedDatabase(DATABASE_FILE_NAME);
        final DatabaseBackupVerifier verifier = new DatabaseBackupVerifier();

        final BackupVerificationResult result = verifier.verify(
                database,
                BackupVerificationLevel.FULL
        );

        Assertions.assertEquals(BackupVerificationStatus.VALID,
                result.status());
    }

    @Test
    @DisplayName("Missing file fails and is not created")
    void missingFileFailsAndIsNotCreated() throws Exception {
        final Path database = temporaryDirectory.resolve("missing.db");
        final DatabaseBackupVerifier verifier = new DatabaseBackupVerifier();

        final BackupVerificationResult result = verifier.verify(
                database,
                BackupVerificationLevel.QUICK
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        BackupVerificationStatus.INVALID,
                        result.status()
                ),
                () -> Assertions.assertFalse(Files.exists(database))
        );
    }

    @Test
    @DisplayName("Empty file fails verification")
    void emptyFileFailsVerification() throws Exception {
        final Path database = temporaryDirectory.resolve("empty.db");
        Files.write(database, new byte[0]);
        final DatabaseBackupVerifier verifier = new DatabaseBackupVerifier();

        final BackupVerificationResult result = verifier.verify(
                database,
                BackupVerificationLevel.QUICK
        );

        Assertions.assertEquals(BackupVerificationStatus.INVALID,
                result.status());
    }

    @Test
    @DisplayName("Non SQLite file fails verification")
    void nonSqliteFileFailsVerification() throws Exception {
        final Path database = temporaryDirectory.resolve("not-sqlite.db");
        Files.writeString(database, "not sqlite");
        final DatabaseBackupVerifier verifier = new DatabaseBackupVerifier();

        final BackupVerificationResult result = verifier.verify(
                database,
                BackupVerificationLevel.QUICK
        );

        Assertions.assertEquals(BackupVerificationStatus.INVALID,
                result.status());
    }

    @Test
    @DisplayName("Unsupported schema version fails")
    void unsupportedSchemaVersionFails() throws Exception {
        final Path database = initializedDatabase(DATABASE_FILE_NAME);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE app_metadata
                    SET metadata_value = '""" + UNSUPPORTED_VERSION + """
                    '
                    WHERE metadata_key = 'schema_version'
                    """);
        }

        final BackupVerificationResult result =
                new DatabaseBackupVerifier().verify(
                        database,
                        BackupVerificationLevel.QUICK
                );

        Assertions.assertEquals(BackupVerificationStatus.INVALID,
                result.status());
    }

    @Test
    @DisplayName("Missing app metadata fails")
    void missingAppMetadataFails() throws Exception {
        final Path database = initializedDatabase(DATABASE_FILE_NAME);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE app_metadata");
        }

        final BackupVerificationResult result =
                new DatabaseBackupVerifier().verify(
                        database,
                        BackupVerificationLevel.QUICK
                );

        Assertions.assertEquals(BackupVerificationStatus.INVALID,
                result.status());
    }

    @Test
    @DisplayName("Missing required core table fails")
    void missingRequiredCoreTableFails() throws Exception {
        final Path database = initializedDatabase(DATABASE_FILE_NAME);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE scene_performer");
        }

        final BackupVerificationResult result =
                new DatabaseBackupVerifier().verify(
                        database,
                        BackupVerificationLevel.QUICK
                );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        BackupVerificationStatus.INVALID,
                        result.status()
                ),
                () -> Assertions.assertTrue(result.missingTables()
                        .contains("scene_performer"))
        );
    }

    @Test
    @DisplayName("Verification does not modify file metadata")
    void verificationDoesNotModifyFileMetadata() throws Exception {
        final Path database = initializedDatabase(DATABASE_FILE_NAME);
        final long size = Files.size(database);
        final long modified = Files.getLastModifiedTime(database).toMillis();

        new DatabaseBackupVerifier().verify(
                database,
                BackupVerificationLevel.FULL
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(size, Files.size(database)),
                () -> Assertions.assertEquals(
                        modified,
                        Files.getLastModifiedTime(database).toMillis()
                )
        );
    }

    @Test
    @DisplayName("Special character paths verify")
    void specialCharacterPathsVerify() throws Exception {
        final Path database = initializedDatabase(
                "spaces'apostrophe,comma-å.db"
        );

        final BackupVerificationResult result =
                new DatabaseBackupVerifier().verify(
                        database,
                        BackupVerificationLevel.QUICK
                );

        Assertions.assertEquals(BackupVerificationStatus.VALID,
                result.status());
    }

    private Path initializedDatabase(String fileName) throws Exception {
        final Path database = temporaryDirectory.resolve(fileName);
        final DatabaseManager databaseManager = new DatabaseManager(database);
        new SchemaManager(databaseManager).initialize();
        return database;
    }
}

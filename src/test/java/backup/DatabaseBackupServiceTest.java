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
import java.sql.ResultSet;
import java.sql.Statement;

class DatabaseBackupServiceTest {
    private static final String SOURCE_FILE_NAME = "source.db";
    private static final String BACKUP_FILE_NAME = "backup.db";

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Backup file is created and verified")
    void backupFileIsCreatedAndVerified() throws Exception {
        final Path source = initializedDatabase(SOURCE_FILE_NAME);
        insertPublisher(source, "Publisher");
        final Path destination = temporaryDirectory.resolve(BACKUP_FILE_NAME);

        final BackupResult result = new DatabaseBackupService().createBackup(
                request(source, destination, false)
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(Files.isRegularFile(destination)),
                () -> Assertions.assertEquals(BackupStatus.CREATED,
                        result.status()),
                () -> Assertions.assertEquals(BackupVerificationStatus.VALID,
                        result.verification().status()),
                () -> Assertions.assertEquals(1,
                        countRows(destination, "publisher")),
                () -> Assertions.assertEquals(1,
                        countRows(source, "publisher"))
        );
    }

    @Test
    @DisplayName("Backup preserves WAL committed rows without WAL beside backup")
    void backupPreservesWalCommittedRowsWithoutWalBesideBackup()
            throws Exception {

        final Path source = initializedDatabase(SOURCE_FILE_NAME);

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + source
        );
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.executeUpdate("""
                    INSERT INTO publisher(id, name)
                    VALUES ('publisher-id', 'Publisher')
                    """);
        }

        final Path destination = temporaryDirectory.resolve(BACKUP_FILE_NAME);
        new DatabaseBackupService().createBackup(request(
                source,
                destination,
                false
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(1,
                        countRows(destination, "publisher")),
                () -> Assertions.assertFalse(Files.exists(Path.of(
                        destination + "-wal"
                ))),
                () -> Assertions.assertFalse(Files.exists(Path.of(
                        destination + "-shm"
                )))
        );
    }

    @Test
    @DisplayName("Existing destination is refused by default")
    void existingDestinationIsRefusedByDefault() throws Exception {
        final Path source = initializedDatabase(SOURCE_FILE_NAME);
        final Path destination = temporaryDirectory.resolve(BACKUP_FILE_NAME);
        Files.writeString(destination, "old");

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseBackupService().createBackup(request(
                        source,
                        destination,
                        false
                ))
        );
    }

    @Test
    @DisplayName("Overwrite replaces old backup only after verification")
    void overwriteReplacesOldBackupOnlyAfterVerification() throws Exception {
        final Path source = initializedDatabase(SOURCE_FILE_NAME);
        insertPublisher(source, "New Publisher");
        final Path oldSource = initializedDatabase("old-source.db");
        insertPublisher(oldSource, "Old Publisher");
        final Path destination = temporaryDirectory.resolve(BACKUP_FILE_NAME);
        new DatabaseBackupService().createBackup(request(
                oldSource,
                destination,
                false
        ));

        final BackupResult result = new DatabaseBackupService().createBackup(
                request(source, destination, true)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(BackupStatus.CREATED,
                        result.status()),
                () -> Assertions.assertEquals(
                        "New Publisher",
                        firstPublisherName(destination)
                )
        );
    }

    @Test
    @DisplayName("Failed overwrite preserves old backup")
    void failedOverwritePreservesOldBackup() throws Exception {
        final Path oldSource = initializedDatabase("old-source.db");
        insertPublisher(oldSource, "Old Publisher");
        final Path destination = temporaryDirectory.resolve(BACKUP_FILE_NAME);
        new DatabaseBackupService().createBackup(request(
                oldSource,
                destination,
                false
        ));
        final Path invalidSource = temporaryDirectory.resolve("invalid.db");
        Files.writeString(invalidSource, "not sqlite");

        Assertions.assertThrows(
                Exception.class,
                () -> new DatabaseBackupService().createBackup(request(
                        invalidSource,
                        destination,
                        true
                ))
        );

        Assertions.assertEquals("Old Publisher", firstPublisherName(
                destination
        ));
    }

    @Test
    @DisplayName("Special character destination path is backed up")
    void specialCharacterDestinationPathIsBackedUp() throws Exception {
        final Path source = initializedDatabase("source spaces.db");
        final Path destination = temporaryDirectory.resolve(
                "backup spaces'apostrophe,comma-å.db"
        );

        final BackupResult result = new DatabaseBackupService().createBackup(
                request(source, destination, false)
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(Files.isRegularFile(destination)),
                () -> Assertions.assertEquals(destination.toAbsolutePath()
                        .normalize(), result.destination())
        );
    }

    private BackupRequest request(
            Path source,
            Path destination,
            boolean overwrite) {

        return new BackupRequest(
                source,
                destination,
                overwrite,
                BackupVerificationLevel.QUICK
        );
    }

    private Path initializedDatabase(String fileName) throws Exception {
        final Path database = temporaryDirectory.resolve(fileName);
        final DatabaseManager databaseManager = new DatabaseManager(database);
        new SchemaManager(databaseManager).initialize();
        return database;
    }

    private void insertPublisher(Path database, String name) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO publisher(id, name)
                    VALUES ('""" + name + """
                    ', '""" + name + """
                    ')
                    """);
        }
    }

    private int countRows(Path database, String tableName) throws Exception {
        int count = 0;

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM " + tableName
             )) {
            if (resultSet.next()) {
                count = resultSet.getInt(1);
            }
        }

        return count;
    }

    private String firstPublisherName(Path database) throws Exception {
        String name = null;

        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + database
        );
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT name
                     FROM publisher
                     ORDER BY name
                     """)) {
            if (resultSet.next()) {
                name = resultSet.getString(1);
            }
        }

        return name;
    }
}

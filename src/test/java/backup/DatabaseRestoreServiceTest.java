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

class DatabaseRestoreServiceTest {
    private static final String SOURCE_FILE_NAME = "source.db";
    private static final String BACKUP_FILE_NAME = "backup.db";
    private static final String RESTORE_FILE_NAME = "restored.db";

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Valid backup restores to new file")
    void validBackupRestoresToNewFile() throws Exception {
        final Path source = initializedDatabase(SOURCE_FILE_NAME);
        insertPublisher(source, "Publisher");
        final Path backup = createBackup(source);
        final Path destination = temporaryDirectory.resolve(RESTORE_FILE_NAME);

        final RestoreResult result = new DatabaseRestoreService().restore(
                request(backup, destination, false)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(RestoreStatus.RESTORED,
                        result.status()),
                () -> Assertions.assertTrue(Files.isRegularFile(destination)),
                () -> Assertions.assertEquals(1,
                        countRows(destination, "publisher")),
                () -> Assertions.assertEquals(BackupVerificationStatus.VALID,
                        result.verification().status())
        );
    }

    @Test
    @DisplayName("Corrupt input fails before output creation")
    void corruptInputFailsBeforeOutputCreation() throws Exception {
        final Path backup = temporaryDirectory.resolve(BACKUP_FILE_NAME);
        Files.writeString(backup, "not sqlite");
        final Path destination = temporaryDirectory.resolve(RESTORE_FILE_NAME);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseRestoreService().restore(request(
                        backup,
                        destination,
                        false
                ))
        );
        Assertions.assertFalse(Files.exists(destination));
    }

    @Test
    @DisplayName("Active database target is refused")
    void activeDatabaseTargetIsRefused() throws Exception {
        final Path source = initializedDatabase(SOURCE_FILE_NAME);
        final Path backup = createBackup(source);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseRestoreService().restore(new RestoreRequest(
                        backup,
                        source,
                        true,
                        BackupVerificationLevel.QUICK,
                        source
                ))
        );
    }

    @Test
    @DisplayName("Existing target without overwrite is refused")
    void existingTargetWithoutOverwriteIsRefused() throws Exception {
        final Path source = initializedDatabase(SOURCE_FILE_NAME);
        final Path backup = createBackup(source);
        final Path destination = initializedDatabase(RESTORE_FILE_NAME);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new DatabaseRestoreService().restore(request(
                        backup,
                        destination,
                        false
                ))
        );
    }

    @Test
    @DisplayName("Existing target with overwrite is replaced")
    void existingTargetWithOverwriteIsReplaced() throws Exception {
        final Path source = initializedDatabase(SOURCE_FILE_NAME);
        insertPublisher(source, "Publisher");
        final Path backup = createBackup(source);
        final Path destination = initializedDatabase(RESTORE_FILE_NAME);

        new DatabaseRestoreService().restore(request(
                backup,
                destination,
                true
        ));

        Assertions.assertEquals(1, countRows(destination, "publisher"));
    }

    private RestoreRequest request(
            Path input,
            Path destination,
            boolean overwrite) {

        return new RestoreRequest(
                input,
                destination,
                overwrite,
                BackupVerificationLevel.QUICK,
                temporaryDirectory.resolve("active.db")
        );
    }

    private Path createBackup(Path source) throws Exception {
        final Path backup = temporaryDirectory.resolve(BACKUP_FILE_NAME);
        new DatabaseBackupService().createBackup(new BackupRequest(
                source,
                backup,
                false,
                BackupVerificationLevel.QUICK
        ));
        return backup;
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
                    VALUES ('publisher-id', '""" + name + """
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
}

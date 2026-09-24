package backup;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class BackupPathValidationTest {
    private static final String SOURCE_FILE_NAME = "source.db";
    private static final String DESTINATION_FILE_NAME = "backup.db";

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Backup request rejects null source")
    void backupRequestRejectsNullSource() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new BackupRequest(
                        null,
                        temporaryDirectory.resolve(DESTINATION_FILE_NAME),
                        false,
                        BackupVerificationLevel.QUICK
                )
        );
    }

    @Test
    @DisplayName("Backup request rejects null destination")
    void backupRequestRejectsNullDestination() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new BackupRequest(
                        temporaryDirectory.resolve(SOURCE_FILE_NAME),
                        null,
                        false,
                        BackupVerificationLevel.QUICK
                )
        );
    }

    @Test
    @DisplayName("Backup path validator rejects missing source")
    void backupPathValidatorRejectsMissingSource() {
        final BackupPathValidator validator = new BackupPathValidator();
        final BackupRequest request = request(
                temporaryDirectory.resolve(SOURCE_FILE_NAME),
                temporaryDirectory.resolve(DESTINATION_FILE_NAME),
                false
        );

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateBackup(request)
        );
    }

    @Test
    @DisplayName("Backup path validator rejects source directory")
    void backupPathValidatorRejectsSourceDirectory() throws Exception {
        final BackupPathValidator validator = new BackupPathValidator();
        final BackupRequest request = request(
                temporaryDirectory,
                temporaryDirectory.resolve(DESTINATION_FILE_NAME),
                false
        );

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateBackup(request)
        );
    }

    @Test
    @DisplayName("Backup path validator rejects destination directory")
    void backupPathValidatorRejectsDestinationDirectory() throws Exception {
        final Path source = createSource();
        final BackupPathValidator validator = new BackupPathValidator();
        final BackupRequest request = request(source, temporaryDirectory, false);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateBackup(request)
        );
    }

    @Test
    @DisplayName("Backup path validator rejects same source and destination")
    void backupPathValidatorRejectsSameSourceAndDestination() throws Exception {
        final Path source = createSource();
        final BackupPathValidator validator = new BackupPathValidator();
        final BackupRequest request = request(source, source, true);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateBackup(request)
        );
    }

    @Test
    @DisplayName("Backup path validator rejects equivalent normalized paths")
    void backupPathValidatorRejectsEquivalentNormalizedPaths()
            throws Exception {

        final Path source = createSource();
        final BackupPathValidator validator = new BackupPathValidator();
        final BackupRequest request = request(
                source,
                source.getParent().resolve(".").resolve(source.getFileName()),
                true
        );

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateBackup(request)
        );
    }

    @Test
    @DisplayName("Backup path validator rejects existing destination without overwrite")
    void backupPathValidatorRejectsExistingDestinationWithoutOverwrite()
            throws Exception {

        final Path source = createSource();
        final Path destination = temporaryDirectory.resolve(
                DESTINATION_FILE_NAME
        );
        Files.writeString(destination, "existing");
        final BackupPathValidator validator = new BackupPathValidator();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateBackup(request(
                        source,
                        destination,
                        false
                ))
        );
    }

    @Test
    @DisplayName("Backup path validator accepts existing destination with overwrite")
    void backupPathValidatorAcceptsExistingDestinationWithOverwrite()
            throws Exception {

        final Path source = createSource();
        final Path destination = temporaryDirectory.resolve(
                DESTINATION_FILE_NAME
        );
        Files.writeString(destination, "existing");
        final BackupPathValidator validator = new BackupPathValidator();

        final ValidatedBackupPaths paths = validator.validateBackup(request(
                source,
                destination,
                true
        ));

        Assertions.assertEquals(
                destination.toAbsolutePath().normalize(),
                paths.destination()
        );
    }

    @Test
    @DisplayName("Backup path validator creates missing destination parent")
    void backupPathValidatorCreatesMissingDestinationParent()
            throws Exception {

        final Path source = createSource();
        final Path destination = temporaryDirectory.resolve("missing")
                .resolve(DESTINATION_FILE_NAME);
        final BackupPathValidator validator = new BackupPathValidator();

        validator.validateBackup(request(source, destination, false));

        Assertions.assertTrue(Files.isDirectory(destination.getParent()));
    }

    @Test
    @DisplayName("Backup path validator accepts special character paths")
    void backupPathValidatorAcceptsSpecialCharacterPaths() throws Exception {
        final Path source = temporaryDirectory.resolve(
                "source spaces'apostrophe,comma-å.db"
        );
        Files.writeString(source, "source");
        final Path destination = temporaryDirectory.resolve("dest folder")
                .resolve("backup spaces'apostrophe,comma-å.db");
        final BackupPathValidator validator = new BackupPathValidator();

        final ValidatedBackupPaths paths = validator.validateBackup(request(
                source,
                destination,
                false
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        source.toAbsolutePath().normalize(),
                        paths.source()
                ),
                () -> Assertions.assertEquals(
                        destination.toAbsolutePath().normalize(),
                        paths.destination()
                )
        );
    }

    @Test
    @DisplayName("Restore path validator rejects same input and output")
    void restorePathValidatorRejectsSameInputAndOutput() throws Exception {
        final Path backup = createSource();
        final BackupPathValidator validator = new BackupPathValidator();

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRestore(new RestoreRequest(
                        backup,
                        backup,
                        false,
                        BackupVerificationLevel.QUICK,
                        temporaryDirectory.resolve("active.db")
                ))
        );
    }

    private Path createSource() throws Exception {
        final Path source = temporaryDirectory.resolve(SOURCE_FILE_NAME);
        Files.writeString(source, "source");
        return source;
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
}

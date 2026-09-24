package backup;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Objects;

public final class DatabaseBackupService {
    private static final String JDBC_PREFIX = "jdbc:sqlite:";
    private static final String TEMP_PREFIX = ".javdb-backup-";
    private static final String TEMP_SUFFIX = ".tmp";

    private final BackupPathValidator pathValidator;
    private final DatabaseBackupVerifier verifier;

    public DatabaseBackupService() {
        this(new BackupPathValidator(), new DatabaseBackupVerifier());
    }

    public DatabaseBackupService(
            BackupPathValidator pathValidator,
            DatabaseBackupVerifier verifier) {

        this.pathValidator = Objects.requireNonNull(
                pathValidator,
                "Backup path validator must not be null"
        );
        this.verifier = Objects.requireNonNull(
                verifier,
                "Backup verifier must not be null"
        );
    }

    public BackupResult createBackup(BackupRequest request)
            throws IOException, SQLException {

        final ValidatedBackupPaths paths = pathValidator.validateBackup(
                request
        );
        final Path temporaryBackup = Files.createTempFile(
                paths.destination().getParent(),
                TEMP_PREFIX,
                TEMP_SUFFIX
        );
        boolean complete = false;

        try {
            Files.deleteIfExists(temporaryBackup);
            vacuumInto(paths.source(), temporaryBackup);
            final BackupVerificationResult verification = verifier.verify(
                    temporaryBackup,
                    request.verificationLevel()
            );

            if (!verification.valid()) {
                throw new SQLException(
                        "Backup verification failed: "
                                + String.join("; ", verification.messages())
                );
            }

            moveIntoPlace(temporaryBackup, paths.destination());
            complete = true;
            final BackupVerificationResult finalVerification =
                    verifier.verify(
                            paths.destination(),
                            request.verificationLevel()
                    );

            return new BackupResult(
                    BackupStatus.CREATED,
                    paths.source(),
                    paths.destination(),
                    Files.size(paths.destination()),
                    finalVerification.schemaVersion(),
                    Instant.now(),
                    finalVerification,
                    null
            );
        } finally {
            if (!complete) {
                Files.deleteIfExists(temporaryBackup);
            }
        }
    }

    private void vacuumInto(Path source, Path destination)
            throws SQLException {

        try (Connection connection =
                     DriverManager.getConnection(JDBC_PREFIX + source);
             Statement statement = connection.createStatement()) {

            statement.execute("VACUUM INTO "
                    + SqlitePathLiteral.quote(destination));
        }
    }

    private void moveIntoPlace(Path source, Path destination)
            throws IOException {

        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
}

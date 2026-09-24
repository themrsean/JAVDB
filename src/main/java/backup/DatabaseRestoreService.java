package backup;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseRestoreService {
    private static final String TEMP_PREFIX = ".javdb-restore-";
    private static final String TEMP_SUFFIX = ".tmp";

    private final BackupPathValidator pathValidator;
    private final DatabaseBackupVerifier verifier;

    public DatabaseRestoreService() {
        this(new BackupPathValidator(), new DatabaseBackupVerifier());
    }

    public DatabaseRestoreService(
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

    public RestoreResult restore(RestoreRequest request)
            throws IOException, SQLException {

        final ValidatedRestorePaths paths = pathValidator.validateRestore(
                request
        );
        final BackupVerificationResult inputVerification = verifier.verify(
                paths.input(),
                request.verificationLevel()
        );

        if (!inputVerification.valid()) {
            throw new IllegalArgumentException(
                    "Restore input verification failed: "
                            + String.join("; ", inputVerification.messages())
            );
        }

        final Path temporaryRestore = Files.createTempFile(
                paths.destination().getParent(),
                TEMP_PREFIX,
                TEMP_SUFFIX
        );
        boolean complete = false;

        try {
            Files.copy(
                    paths.input(),
                    temporaryRestore,
                    StandardCopyOption.REPLACE_EXISTING
            );
            final BackupVerificationResult outputVerification =
                    verifier.verify(
                            temporaryRestore,
                            request.verificationLevel()
                    );

            if (!outputVerification.valid()) {
                throw new SQLException(
                        "Restored database verification failed: "
                                + String.join(
                                        "; ",
                                        outputVerification.messages()
                                )
                );
            }

            moveIntoPlace(temporaryRestore, paths.destination());
            complete = true;
            final BackupVerificationResult finalVerification =
                    verifier.verify(
                            paths.destination(),
                            request.verificationLevel()
                    );

            return new RestoreResult(
                    RestoreStatus.RESTORED,
                    paths.input(),
                    paths.destination(),
                    Files.size(paths.destination()),
                    finalVerification.schemaVersion(),
                    finalVerification,
                    null
            );
        } finally {
            if (!complete) {
                Files.deleteIfExists(temporaryRestore);
            }
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

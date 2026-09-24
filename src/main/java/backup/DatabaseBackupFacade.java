package backup;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;

public final class DatabaseBackupFacade {
    private final DatabaseBackupService backupService;
    private final DatabaseBackupVerifier verifier;
    private final DatabaseRestoreService restoreService;
    private final Path activeDatabasePath;

    public DatabaseBackupFacade(Path activeDatabasePath) {
        this(
                new DatabaseBackupService(),
                new DatabaseBackupVerifier(),
                new DatabaseRestoreService(),
                activeDatabasePath
        );
    }

    public DatabaseBackupFacade(
            DatabaseBackupService backupService,
            DatabaseBackupVerifier verifier,
            DatabaseRestoreService restoreService,
            Path activeDatabasePath) {

        this.backupService = Objects.requireNonNull(
                backupService,
                "Backup service must not be null"
        );
        this.verifier = Objects.requireNonNull(
                verifier,
                "Backup verifier must not be null"
        );
        this.restoreService = Objects.requireNonNull(
                restoreService,
                "Restore service must not be null"
        );
        this.activeDatabasePath = Objects.requireNonNull(
                activeDatabasePath,
                "Active database path must not be null"
        ).toAbsolutePath().normalize();
    }

    public BackupResult createBackup(
            Path destination,
            boolean overwrite,
            BackupVerificationLevel level) throws IOException, SQLException {

        return backupService.createBackup(new BackupRequest(
                activeDatabasePath,
                destination,
                overwrite,
                level
        ));
    }

    public BackupVerificationResult verify(
            Path input,
            BackupVerificationLevel level) throws IOException, SQLException {

        return verifier.verify(input, level);
    }

    public RestoreResult restore(
            Path input,
            Path destination,
            boolean overwrite,
            BackupVerificationLevel level) throws IOException, SQLException {

        return restoreService.restore(new RestoreRequest(
                input,
                destination,
                overwrite,
                level,
                activeDatabasePath
        ));
    }
}

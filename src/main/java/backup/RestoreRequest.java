package backup;

import java.nio.file.Path;
import java.util.Objects;

public record RestoreRequest(
        Path input,
        Path destination,
        boolean overwrite,
        BackupVerificationLevel verificationLevel,
        Path activeDatabasePath) {

    public RestoreRequest {
        Objects.requireNonNull(input, "Restore input must not be null");
        Objects.requireNonNull(
                destination,
                "Restore destination must not be null"
        );
        verificationLevel = verificationLevel == null
                ? BackupVerificationLevel.QUICK
                : verificationLevel;
        activeDatabasePath = activeDatabasePath == null
                ? null
                : activeDatabasePath.toAbsolutePath().normalize();
    }
}

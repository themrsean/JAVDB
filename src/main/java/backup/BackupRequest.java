package backup;

import java.nio.file.Path;
import java.util.Objects;

public record BackupRequest(
        Path source,
        Path destination,
        boolean overwrite,
        BackupVerificationLevel verificationLevel) {

    public BackupRequest {
        Objects.requireNonNull(source, "Backup source must not be null");
        Objects.requireNonNull(
                destination,
                "Backup destination must not be null"
        );
        verificationLevel = verificationLevel == null
                ? BackupVerificationLevel.QUICK
                : verificationLevel;
    }
}

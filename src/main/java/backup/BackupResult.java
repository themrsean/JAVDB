package backup;

import java.nio.file.Path;
import java.time.Instant;

public record BackupResult(
        BackupStatus status,
        Path source,
        Path destination,
        long fileSize,
        String schemaVersion,
        Instant createdAt,
        BackupVerificationResult verification,
        String error) {
}

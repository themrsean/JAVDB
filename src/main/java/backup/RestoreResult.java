package backup;

import java.nio.file.Path;

public record RestoreResult(
        RestoreStatus status,
        Path input,
        Path destination,
        long fileSize,
        String schemaVersion,
        BackupVerificationResult verification,
        String error) {
}

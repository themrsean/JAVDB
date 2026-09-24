package backup;

import java.nio.file.Path;
import java.util.List;

public record BackupVerificationResult(
        BackupVerificationStatus status,
        BackupVerificationLevel level,
        Path path,
        long fileSize,
        String schemaVersion,
        List<String> integrityMessages,
        List<String> foreignKeyViolations,
        List<String> missingTables,
        List<String> messages,
        String error) {

    public boolean valid() {
        return status == BackupVerificationStatus.VALID;
    }
}

package backup;

import java.nio.file.Path;

public record ValidatedBackupPaths(
        Path source,
        Path destination) {
}

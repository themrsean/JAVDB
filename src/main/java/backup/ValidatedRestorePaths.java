package backup;

import java.nio.file.Path;

public record ValidatedRestorePaths(
        Path input,
        Path destination) {
}

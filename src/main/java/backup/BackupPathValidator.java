package backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class BackupPathValidator {
    public ValidatedBackupPaths validateBackup(BackupRequest request)
            throws IOException {

        Objects.requireNonNull(request, "Backup request must not be null");
        final Path source = normalize(request.source());
        final Path destination = normalize(request.destination());

        validateExistingRegularFile(source, "Backup source");
        validateDestination(destination, request.overwrite());

        if (source.equals(destination)) {
            throw new IllegalArgumentException(
                    "Backup source and destination must be different."
            );
        }

        return new ValidatedBackupPaths(source, destination);
    }

    public ValidatedRestorePaths validateRestore(RestoreRequest request)
            throws IOException {

        Objects.requireNonNull(request, "Restore request must not be null");
        final Path input = normalize(request.input());
        final Path destination = normalize(request.destination());

        validateExistingRegularFile(input, "Restore input");
        validateDestination(destination, request.overwrite());

        if (input.equals(destination)) {
            throw new IllegalArgumentException(
                    "Restore input and destination must be different."
            );
        }

        if (request.activeDatabasePath() != null
                && destination.equals(normalize(request.activeDatabasePath()))) {
            throw new IllegalArgumentException(
                    "Restore destination must not be the active database."
            );
        }

        return new ValidatedRestorePaths(input, destination);
    }

    private Path normalize(Path path) {
        return Objects.requireNonNull(
                path,
                "Path must not be null"
        ).toAbsolutePath().normalize();
    }

    private void validateExistingRegularFile(Path path, String label) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(label + " does not exist: "
                    + path);
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(label
                    + " must be a regular file: " + path);
        }
    }

    private void validateDestination(Path destination, boolean overwrite)
            throws IOException {

        final Path parent = destination.getParent();

        if (Files.exists(destination) && Files.isDirectory(destination)) {
            throw new IllegalArgumentException(
                    "Destination must not be a directory: " + destination
            );
        }

        if (Files.exists(destination) && !overwrite) {
            throw new IllegalArgumentException(
                    "Destination already exists: " + destination
            );
        }

        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}

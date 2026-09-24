package service;

import model.MediaLocation;
import model.MediaLocationScanStatus;
import repository.MediaLocationRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MediaLocationService {
    private final MediaLocationRepository repository;

    public MediaLocationService(MediaLocationRepository repository) {
        this.repository = Objects.requireNonNull(
                repository,
                "Media location repository must not be null"
        );
    }

    public MediaLocation addLocation(
            Path path,
            boolean enabled,
            boolean recursive) throws SQLException {

        Objects.requireNonNull(path, "Media location path must not be null");
        final Path normalizedPath = path.toAbsolutePath().normalize();
        validateDirectory(normalizedPath);

        if (repository.findByPath(normalizedPath).isPresent()) {
            throw new IllegalArgumentException(
                    "Media location already exists: " + normalizedPath
            );
        }

        final Instant now = Instant.now();
        final MediaLocation location = new MediaLocation(
                UUID.randomUUID(),
                normalizedPath,
                enabled,
                recursive,
                now,
                now,
                null,
                null,
                MediaLocationScanStatus.NEVER_SCANNED,
                null,
                0,
                0,
                0,
                0,
                0,
                0
        );
        repository.insert(location);

        return location;
    }

    public Optional<MediaLocation> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Media location ID must not be null");
        return repository.findById(id);
    }

    public Optional<MediaLocation> findByPath(Path path) throws SQLException {
        Objects.requireNonNull(path, "Media location path must not be null");
        return repository.findByPath(path.toAbsolutePath().normalize());
    }

    public List<MediaLocation> findAll() throws SQLException {
        return repository.findAll();
    }

    public List<MediaLocation> findEnabled() throws SQLException {
        return repository.findEnabled();
    }

    public MediaLocation updateSettings(
            UUID id,
            boolean enabled,
            boolean recursive) throws SQLException {

        Objects.requireNonNull(id, "Media location ID must not be null");
        final boolean updated = repository.updateSettings(
                id,
                enabled,
                recursive,
                Instant.now()
        );
        ensureUpdated(updated, "Unknown media location: " + id);

        return repository.findById(id).orElseThrow();
    }

    public MediaLocation updateScanResult(
            UUID id,
            MediaLocationScanResult result) throws SQLException {

        Objects.requireNonNull(id, "Media location ID must not be null");
        Objects.requireNonNull(result, "Scan result must not be null");
        final boolean updated = repository.updateScanResult(
                id,
                result,
                Instant.now()
        );
        ensureUpdated(updated, "Unknown media location: " + id);

        return repository.findById(id).orElseThrow();
    }

    public void removeLocation(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Media location ID must not be null");
        final boolean deleted = repository.delete(id);
        ensureUpdated(deleted, "Unknown media location: " + id);
    }

    private void validateDirectory(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(
                    "Media location does not exist: " + path
            );
        }

        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(
                    "Media location is not a directory: " + path
            );
        }

        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(
                    "Media location is not readable: " + path
            );
        }
    }

    private void ensureUpdated(boolean updated, String message) {
        if (!updated) {
            throw new IllegalArgumentException(message);
        }
    }
}

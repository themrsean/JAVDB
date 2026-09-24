package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
import model.MediaLocation;
import model.MediaLocationScanStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaFileRepository;
import repository.MediaLocationRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

class MediaLocationServiceTest {
    private static final String DATABASE_FILE_NAME =
            "media-location-service-test.db";
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final long FILE_SIZE = 25L;
    private static final long LAST_MODIFIED = 1_700_000_000_000L;
    private static final int DISCOVERED_COUNT = 8;
    private static final int NEW_COUNT = 2;
    private static final int UPDATED_COUNT = 1;
    private static final int UNCHANGED_COUNT = 4;
    private static final int MISSING_COUNT = 0;
    private static final int FAILED_COUNT = 1;

    @TempDir
    Path temporaryDirectory;

    private MediaLocationService service;
    private MediaFileRepository mediaFileRepository;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        service = new MediaLocationService(
                new MediaLocationRepository(databaseManager)
        );
        mediaFileRepository = new MediaFileRepository(databaseManager);
    }

    @Test
    @DisplayName("Valid directory is added normalized and discoverable")
    void validDirectoryIsAddedNormalizedAndDiscoverable() throws Exception {
        final Path directory = Files.createDirectories(
                temporaryDirectory.resolve("media").resolve("..")
                        .resolve("media")
        );

        final MediaLocation location = service.addLocation(
                directory,
                true,
                false
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        directory.toAbsolutePath().normalize(),
                        location.path()
                ),
                () -> Assertions.assertTrue(location.enabled()),
                () -> Assertions.assertFalse(location.recursive()),
                () -> Assertions.assertEquals(
                        MediaLocationScanStatus.NEVER_SCANNED,
                        location.lastScanStatus()
                ),
                () -> Assertions.assertEquals(
                        location,
                        service.findById(location.id()).orElseThrow()
                ),
                () -> Assertions.assertEquals(
                        location,
                        service.findByPath(directory).orElseThrow()
                )
        );
    }

    @Test
    @DisplayName("Add rejects null missing file and duplicate paths")
    void addRejectsNullMissingFileAndDuplicatePaths() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final Path file = Files.writeString(
                temporaryDirectory.resolve("not-directory.mp4"),
                "media"
        );
        service.addLocation(directory, true, true);

        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> service.addLocation(null, true, true)
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.addLocation(
                                temporaryDirectory.resolve("missing"),
                                true,
                                true
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.addLocation(file, true, true)
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.addLocation(
                                directory.resolve("."),
                                true,
                                true
                        )
                )
        );
    }

    @Test
    @DisplayName("Locations are listed deterministically and filtered by enabled")
    void locationsAreListedDeterministicallyAndFilteredByEnabled()
            throws Exception {

        final Path zeta = Files.createDirectory(
                temporaryDirectory.resolve("zeta")
        );
        final Path alpha = Files.createDirectory(
                temporaryDirectory.resolve("Alpha")
        );
        final MediaLocation second = service.addLocation(zeta, false, true);
        final MediaLocation first = service.addLocation(alpha, true, true);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(first, second),
                        service.findAll()
                ),
                () -> Assertions.assertEquals(
                        List.of(first),
                        service.findEnabled()
                )
        );
    }

    @Test
    @DisplayName("Enabled recursive and scan result fields update")
    void enabledRecursiveAndScanResultFieldsUpdate() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final MediaLocation location = service.addLocation(
                directory,
                true,
                true
        );
        final MediaLocation settings = service.updateSettings(
                location.id(),
                false,
                false
        );
        final Instant started = Instant.parse("2026-01-01T00:00:00Z");
        final Instant completed = Instant.parse("2026-01-01T00:00:10Z");

        final MediaLocation updated = service.updateScanResult(
                location.id(),
                new MediaLocationScanResult(
                        started,
                        completed,
                        MediaLocationScanStatus.COMPLETED_WITH_ERRORS,
                        "One file failed",
                        DISCOVERED_COUNT,
                        NEW_COUNT,
                        UPDATED_COUNT,
                        UNCHANGED_COUNT,
                        MISSING_COUNT,
                        FAILED_COUNT
                )
        );

        Assertions.assertAll(
                () -> Assertions.assertFalse(settings.enabled()),
                () -> Assertions.assertFalse(settings.recursive()),
                () -> Assertions.assertEquals(started,
                        updated.lastScanStartedAt()),
                () -> Assertions.assertEquals(completed,
                        updated.lastScanCompletedAt()),
                () -> Assertions.assertEquals(
                        MediaLocationScanStatus.COMPLETED_WITH_ERRORS,
                        updated.lastScanStatus()
                ),
                () -> Assertions.assertEquals("One file failed",
                        updated.lastScanMessage()),
                () -> Assertions.assertEquals(DISCOVERED_COUNT,
                        updated.lastDiscoveredCount()),
                () -> Assertions.assertEquals(NEW_COUNT,
                        updated.lastNewCount()),
                () -> Assertions.assertEquals(UPDATED_COUNT,
                        updated.lastUpdatedCount()),
                () -> Assertions.assertEquals(UNCHANGED_COUNT,
                        updated.lastUnchangedCount()),
                () -> Assertions.assertEquals(MISSING_COUNT,
                        updated.lastMissingCount()),
                () -> Assertions.assertEquals(FAILED_COUNT,
                        updated.lastFailedCount())
        );
    }

    @Test
    @DisplayName("Every valid scan status persists")
    void everyValidScanStatusPersists() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final MediaLocation location = service.addLocation(
                directory,
                true,
                true
        );

        for (MediaLocationScanStatus status
                : MediaLocationScanStatus.values()) {
            final MediaLocation updated = service.updateScanResult(
                    location.id(),
                    new MediaLocationScanResult(
                            Instant.parse("2026-01-01T00:00:00Z"),
                            Instant.parse("2026-01-01T00:00:01Z"),
                            status,
                            status.name(),
                            0,
                            0,
                            0,
                            0,
                            0,
                            0
                    )
            );

            Assertions.assertEquals(status, updated.lastScanStatus());
        }
    }

    @Test
    @DisplayName("Remove deletes only configuration and preserves media")
    void removeDeletesOnlyConfigurationAndPreservesMedia() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final Path mediaPath = Files.writeString(
                directory.resolve("video.mp4"),
                "fixture"
        );
        final MediaLocation location = service.addLocation(
                directory,
                true,
                true
        );
        mediaFileRepository.insert(new MediaFile(
                UUID.randomUUID(),
                mediaPath.toAbsolutePath().normalize(),
                FILE_SIZE,
                null,
                Duration.ofSeconds(1),
                WIDTH,
                HEIGHT,
                LAST_MODIFIED
        ));

        service.removeLocation(location.id());

        Assertions.assertAll(
                () -> Assertions.assertTrue(service.findById(location.id())
                        .isEmpty()),
                () -> Assertions.assertEquals(1,
                        mediaFileRepository.findAll().size()),
                () -> Assertions.assertTrue(Files.exists(mediaPath))
        );
    }

    @Test
    @DisplayName("Unknown ID operations fail clearly")
    void unknownIdOperationsFailClearly() {
        final UUID unknownId = UUID.randomUUID();

        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.updateSettings(unknownId, true, true)
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.updateScanResult(
                                unknownId,
                                new MediaLocationScanResult(
                                        Instant.parse("2026-01-01T00:00:00Z"),
                                        Instant.parse("2026-01-01T00:00:01Z"),
                                        MediaLocationScanStatus.FAILED,
                                        "Missing",
                                        0,
                                        0,
                                        0,
                                        0,
                                        0,
                                        0
                                )
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.removeLocation(unknownId)
                )
        );
    }
}

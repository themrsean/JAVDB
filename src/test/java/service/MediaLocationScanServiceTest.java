package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaHashProvider;
import media.MediaMetadata;
import media.MediaMetadataProbe;
import media.MediaProbeException;
import media.VideoFileDiscovery;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class MediaLocationScanServiceTest {
    private static final String DATABASE_FILE_NAME =
            "media-location-scan-service-test.db";
    private static final long FIRST_MODIFIED = 1_700_000_000_000L;
    private static final long SECOND_MODIFIED = 1_800_000_000_000L;
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int DURATION_MILLIS = 2_000;

    @TempDir
    Path temporaryDirectory;

    private MediaLocationService locationService;
    private MediaFileRepository mediaFileRepository;
    private MediaLocationScanService scanService;
    private RecordingProgressListener progressListener;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        mediaFileRepository = new MediaFileRepository(databaseManager);
        locationService = new MediaLocationService(
                new MediaLocationRepository(databaseManager)
        );
        scanService = new MediaLocationScanService(
                locationService,
                new MediaScanService(
                        mediaFileRepository,
                        new VideoFileDiscovery(),
                        new FakeProbe(),
                        new FakeHasher()
                )
        );
        progressListener = new RecordingProgressListener();
    }

    @Test
    @DisplayName("Scan selected location adds media and stores completed status")
    void scanSelectedLocationAddsMediaAndStoresCompletedStatus()
            throws Exception {

        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final Path mediaPath = createVideo(directory, "a.mp4");
        final MediaLocation location = locationService.addLocation(
                directory,
                true,
                true
        );

        final MediaLocationScanResult result = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progressListener,
                new ScanCancellationToken()
        );

        final MediaLocation stored = locationService.findById(
                location.id()
        ).orElseThrow();
        final MediaFile mediaFile = mediaFileRepository.findByPath(
                mediaPath.toAbsolutePath().normalize()
        ).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaLocationScanStatus.COMPLETED,
                        result.status()
                ),
                () -> Assertions.assertEquals(
                        MediaLocationScanStatus.COMPLETED,
                        stored.lastScanStatus()
                ),
                () -> Assertions.assertEquals(1, result.newCount()),
                () -> Assertions.assertEquals(WIDTH, mediaFile.getWidth()),
                () -> Assertions.assertTrue(progressListener.hasPhase(
                        MediaLocationScanPhase.DISCOVERING
                )),
                () -> Assertions.assertTrue(progressListener.hasPhase(
                        MediaLocationScanPhase.COMPLETED
                ))
        );
    }

    @Test
    @DisplayName("Scan respects recursive setting")
    void scanRespectsRecursiveSetting() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final Path nested = Files.createDirectory(directory.resolve("nested"));
        createVideo(directory, "root.mp4");
        createVideo(nested, "nested.mp4");
        final MediaLocation location = locationService.addLocation(
                directory,
                true,
                false
        );

        final MediaLocationScanResult first = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progressListener,
                new ScanCancellationToken()
        );
        locationService.updateSettings(location.id(), true, true);
        final MediaLocationScanResult second = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progressListener,
                new ScanCancellationToken()
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, first.discoveredCount()),
                () -> Assertions.assertEquals(2, second.discoveredCount()),
                () -> Assertions.assertEquals(2,
                        mediaFileRepository.findAll().size())
        );
    }

    @Test
    @DisplayName("Changed and unchanged files are counted")
    void changedAndUnchangedFilesAreCounted() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final Path mediaPath = createVideo(directory, "a.mp4");
        final MediaLocation location = locationService.addLocation(
                directory,
                true,
                true
        );
        scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progressListener,
                new ScanCancellationToken()
        );
        final MediaLocationScanResult unchanged = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progressListener,
                new ScanCancellationToken()
        );
        Files.setLastModifiedTime(
                mediaPath,
                FileTime.fromMillis(SECOND_MODIFIED)
        );
        final MediaLocationScanResult changed = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progressListener,
                new ScanCancellationToken()
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, unchanged.unchangedCount()),
                () -> Assertions.assertEquals(1, changed.updatedCount()),
                () -> Assertions.assertEquals(1,
                        mediaFileRepository.findAll().size())
        );
    }

    @Test
    @DisplayName("Missing directory records unavailable status")
    void missingDirectoryRecordsUnavailableStatus() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final MediaLocation location = locationService.addLocation(
                directory,
                true,
                true
        );
        Files.delete(directory);

        final MediaLocationScanResult result = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progressListener,
                new ScanCancellationToken()
        );

        Assertions.assertEquals(
                MediaLocationScanStatus.DIRECTORY_UNAVAILABLE,
                result.status()
        );
    }

    @Test
    @DisplayName("Scan all includes enabled excludes disabled and dedupes overlap")
    void scanAllIncludesEnabledExcludesDisabledAndDedupesOverlap()
            throws Exception {

        final Path parent = Files.createDirectory(
                temporaryDirectory.resolve("parent")
        );
        final Path child = Files.createDirectory(parent.resolve("child"));
        final Path disabled = Files.createDirectory(
                temporaryDirectory.resolve("disabled")
        );
        createVideo(parent, "root.mp4");
        createVideo(child, "nested.mp4");
        createVideo(disabled, "disabled.mp4");
        locationService.addLocation(parent, true, true);
        locationService.addLocation(child, true, true);
        locationService.addLocation(disabled, false, true);

        final MediaLocationBatchScanResult result =
                scanService.scanEnabledLocations(
                        MediaLocationScanOptions.guiDefaults(),
                        progressListener,
                        new ScanCancellationToken()
                );

        Assertions.assertAll(
                () -> Assertions.assertEquals(2,
                        result.locationResults().size()),
                () -> Assertions.assertEquals(2, result.newCount()),
                () -> Assertions.assertEquals(2,
                        mediaFileRepository.findAll().size())
        );
    }

    @Test
    @DisplayName("Cancellation before work stores cancelled status")
    void cancellationBeforeWorkStoresCancelledStatus() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        createVideo(directory, "a.mp4");
        final MediaLocation location = locationService.addLocation(
                directory,
                true,
                true
        );
        final ScanCancellationToken token = new ScanCancellationToken();
        token.cancel();

        final MediaLocationScanResult result = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progressListener,
                token
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaLocationScanStatus.CANCELLED,
                        result.status()
                ),
                () -> Assertions.assertEquals(0,
                        mediaFileRepository.findAll().size())
        );
    }

    @Test
    @DisplayName("Location scan progress reports actual current file")
    void locationScanProgressReportsActualCurrentFile() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final Path mediaPath = createVideo(directory, "current.mp4");
        final MediaLocation location = locationService.addLocation(
                directory,
                true,
                true
        );
        final List<MediaLocationScanProgress> progress = new ArrayList<>();

        scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progress::add,
                new ScanCancellationToken()
        );

        Assertions.assertTrue(progress.stream().anyMatch(item ->
                item.phase() == MediaLocationScanPhase.PROCESSING_FILE
                        && mediaPath.toAbsolutePath().normalize()
                        .equals(item.currentFile())
        ));
    }

    @Test
    @DisplayName("Cancellation between files preserves completed records")
    void cancellationBetweenFilesPreservesCompletedRecords() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        createVideo(directory, "a.mp4");
        createVideo(directory, "b.mp4");
        final MediaLocation location = locationService.addLocation(
                directory,
                true,
                true
        );
        final ScanCancellationToken token = new ScanCancellationToken();

        final MediaLocationScanResult result = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progress -> {
                    if (progress.phase() == MediaLocationScanPhase.FILE_COMPLETED) {
                        token.cancel();
                    }
                },
                token
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaLocationScanStatus.CANCELLED,
                        result.status()
                ),
                () -> Assertions.assertEquals(1, result.newCount()),
                () -> Assertions.assertEquals(1,
                        mediaFileRepository.findAll().size())
        );
    }

    private Path createVideo(Path directory, String fileName)
            throws IOException {

        final Path file = Files.writeString(directory.resolve(fileName), "x");
        Files.setLastModifiedTime(
                file,
                FileTime.fromMillis(FIRST_MODIFIED)
        );
        return file;
    }

    private static final class RecordingProgressListener
            implements MediaLocationScanProgressListener {
        private final List<MediaLocationScanPhase> phases = new ArrayList<>();

        @Override
        public void onProgress(MediaLocationScanProgress progress) {
            phases.add(progress.phase());
        }

        boolean hasPhase(MediaLocationScanPhase phase) {
            return phases.contains(phase);
        }
    }

    private static final class FakeProbe implements MediaMetadataProbe {
        @Override
        public MediaMetadata probe(Path mediaPath)
                throws IOException, MediaProbeException {

            return new MediaMetadata(
                    Duration.ofMillis(DURATION_MILLIS),
                    WIDTH,
                    HEIGHT
            );
        }
    }

    private static final class FakeHasher implements MediaHashProvider {
        @Override
        public String hash(Path mediaPath) throws IOException {
            return null;
        }
    }
}

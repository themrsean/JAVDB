package service;

import database.DatabaseManager;
import database.SchemaManager;
import javafx.collections.ObservableList;
import media.MediaFilenameParser;
import media.MediaHashProvider;
import media.MediaMetadata;
import media.MediaMetadataProbe;
import media.MediaProbeException;
import media.VideoFileDiscovery;
import model.MediaLocation;
import model.MediaLocationScanStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.EntitySuggestionRepository;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MediaLocationRepository;
import ui.media.MediaLocationsViewModel;
import ui.review.ReviewQueueViewModel;
import ui.review.ScanRefreshCoordinator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

class MediaLocationsAcceptanceTest {
    private static final String DATABASE_FILE_NAME =
            "media-locations-acceptance-test.db";
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int DURATION_MILLIS = 2_000;
    private static final long FIRST_MODIFIED = 1_700_000_000_000L;
    private static final long SECOND_MODIFIED = 1_800_000_000_000L;

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private MediaFileRepository mediaFileRepository;
    private MediaLocationService locationService;
    private MediaLocationScanService scanService;
    private FakeProbe probe;
    private Path mediaDirectory;

    @BeforeEach
    void initialize() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        mediaFileRepository = new MediaFileRepository(databaseManager);
        locationService = new MediaLocationService(
                new MediaLocationRepository(databaseManager)
        );
        probe = new FakeProbe();
        scanService = new MediaLocationScanService(
                locationService,
                new MediaScanService(
                        mediaFileRepository,
                        new VideoFileDiscovery(),
                        probe,
                        new FakeHasher()
                )
        );
        mediaDirectory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
    }

    @Test
    @DisplayName("Workflow A clean queue refresh shows scanned media")
    void cleanQueueRefreshShowsScannedMedia() throws Exception {
        final Path mediaPath = createVideo(mediaDirectory, "clean.mp4");
        final ReviewQueueViewModel queueViewModel = queueViewModel();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                () -> false,
                () -> true,
                queueViewModel::load
        );
        final MediaLocationsViewModel locationsViewModel =
                mediaLocationsViewModel(coordinator);
        addAndSelectLocation(locationsViewModel, mediaDirectory);

        locationsViewModel.scanSelected();

        Assertions.assertAll(
                () -> Assertions.assertTrue(mediaFileRepository.findByPath(
                        mediaPath.toAbsolutePath().normalize()
                ).isPresent()),
                () -> Assertions.assertEquals(1, queueViewModel.rows().size()),
                () -> Assertions.assertEquals(
                        mediaPath.toAbsolutePath().normalize(),
                        queueViewModel.rows().getFirst().path()
                ),
                () -> Assertions.assertFalse(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Workflow B dirty queue protection defers refresh")
    void dirtyQueueProtectionDefersRefresh() throws Exception {
        final Path initialPath = createVideo(mediaDirectory, "initial.mp4");
        final MediaLocation location = locationService.addLocation(
                mediaDirectory,
                true,
                true
        );
        scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                null,
                new ScanCancellationToken()
        );
        final ReviewQueueViewModel queueViewModel = queueViewModel();
        queueViewModel.load();
        queueViewModel.filenameFilter().set("initial");
        queueViewModel.applyFilters();
        final MutableBoolean dirty = new MutableBoolean(true);
        final MutableBoolean discard = new MutableBoolean(false);
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                discard::value,
                queueViewModel::load
        );
        final MediaLocationsViewModel locationsViewModel =
                mediaLocationsViewModel(coordinator);
        locationsViewModel.loadLocations();
        locationsViewModel.selectedLocationProperty().set(
                locationsViewModel.locations().getFirst()
        );
        createVideo(mediaDirectory, "new-media.mp4");

        locationsViewModel.scanSelected();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, queueViewModel.rows().size()),
                () -> Assertions.assertEquals(
                        initialPath.toAbsolutePath().normalize(),
                        queueViewModel.selectedRow().get().path()
                ),
                () -> Assertions.assertEquals("initial",
                        queueViewModel.filenameFilter().get()),
                () -> Assertions.assertTrue(
                        coordinator.pendingScanResultsProperty().get()
                )
        );

        coordinator.refreshPendingScanResults();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, queueViewModel.rows().size()),
                () -> Assertions.assertTrue(
                        coordinator.pendingScanResultsProperty().get()
                )
        );

        queueViewModel.filenameFilter().set("");
        dirty.setValue(true);
        discard.setValue(true);
        coordinator.refreshPendingScanResults();

        Assertions.assertAll(
                () -> Assertions.assertEquals(2, queueViewModel.rows().size()),
                () -> Assertions.assertFalse(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Workflow C file-level progress reports actual filenames")
    void fileLevelProgressReportsActualFilenames() throws Exception {
        createVideo(mediaDirectory, "one.mp4");
        createVideo(mediaDirectory, "two.mp4");
        final MediaLocation location = locationService.addLocation(
                mediaDirectory,
                true,
                true
        );
        final List<MediaLocationScanProgress> progress = new ArrayList<>();

        final MediaLocationScanResult result = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                progress::add,
                new ScanCancellationToken()
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(2, result.newCount()),
                () -> Assertions.assertTrue(hasCurrentFile(progress,
                        "one.mp4")),
                () -> Assertions.assertTrue(hasCurrentFile(progress,
                        "two.mp4")),
                () -> Assertions.assertEquals(2,
                        progress.getLast().processedFiles()),
                () -> Assertions.assertEquals(2,
                        mediaFileRepository.findAll().size())
        );
    }

    @Test
    @DisplayName("Workflow D cancellation preserves partial changes")
    void cancellationPreservesPartialChanges() throws Exception {
        createVideo(mediaDirectory, "first.mp4");
        createVideo(mediaDirectory, "second.mp4");
        createVideo(mediaDirectory, "third.mp4");
        final MediaLocation location = locationService.addLocation(
                mediaDirectory,
                true,
                true
        );
        final ScanCancellationToken token = new ScanCancellationToken();
        final List<MediaLocationScanProgress> progress = new ArrayList<>();

        final MediaLocationScanResult result = scanService.scanLocation(
                location.id(),
                MediaLocationScanOptions.guiDefaults(),
                item -> {
                    progress.add(item);
                    if (item.phase() == MediaLocationScanPhase.FILE_COMPLETED
                            && item.processedFiles() == 1) {
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
                        mediaFileRepository.findAll().size()),
                () -> Assertions.assertEquals(
                        MediaLocationScanPhase.CANCELLED,
                        progress.getLast().phase()
                )
        );
    }

    @Test
    @DisplayName("Workflow E scan all overlap processes each path once")
    void scanAllOverlapProcessesEachPathOnce() throws Exception {
        final Path child = Files.createDirectory(mediaDirectory.resolve("child"));
        createVideo(mediaDirectory, "parent.mp4");
        createVideo(child, "child.mp4");
        locationService.addLocation(mediaDirectory, true, true);
        locationService.addLocation(child, true, true);
        final List<MediaLocationScanProgress> progress = new ArrayList<>();

        final MediaLocationBatchScanResult result =
                scanService.scanEnabledLocations(
                        MediaLocationScanOptions.guiDefaults(),
                        progress::add,
                        new ScanCancellationToken()
                );

        Assertions.assertAll(
                () -> Assertions.assertEquals(2,
                        result.locationResults().size()),
                () -> Assertions.assertEquals(2, result.newCount()),
                () -> Assertions.assertEquals(2,
                        mediaFileRepository.findAll().size()),
                () -> Assertions.assertEquals(2,
                        progress.getLast().totalLocations())
        );
    }

    @Test
    @DisplayName("Workflow F unchanged scan does not request dirty refresh")
    void unchangedScanDoesNotRequestDirtyRefresh() throws Exception {
        createVideo(mediaDirectory, "same.mp4");
        final MutableBoolean dirty = new MutableBoolean(true);
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> true,
                () -> {
                }
        );
        final MediaLocationsViewModel locationsViewModel =
                mediaLocationsViewModel(coordinator);
        addAndSelectLocation(locationsViewModel, mediaDirectory);

        locationsViewModel.scanSelected();
        coordinator.consumePendingAfterExternalRefresh();
        locationsViewModel.scanSelected();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1,
                        locationsViewModel.unchangedCountProperty().get()),
                () -> Assertions.assertFalse(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    private ReviewQueueViewModel queueViewModel() {
        return new ReviewQueueViewModel(
                new GuiReviewQueueService(
                        new MediaFilenameIndexingService(
                                mediaFileRepository,
                                new MediaAssignmentRepository(databaseManager),
                                new MediaFilenameParser(),
                                new FilenameMetadataMatcher(
                                        new EntitySuggestionRepository(
                                                databaseManager
                                        )
                                )
                        ),
                        mediaFileRepository
                ),
                directExecutor(),
                directExecutor()
        );
    }

    private MediaLocationsViewModel mediaLocationsViewModel(
            ScanRefreshCoordinator coordinator) {

        return new MediaLocationsViewModel(
                locationService,
                scanService,
                directExecutor(),
                directExecutor(),
                coordinator::requestScanRefresh
        );
    }

    private Executor directExecutor() {
        return Runnable::run;
    }

    private void addAndSelectLocation(
            MediaLocationsViewModel locationsViewModel,
            Path directory) throws Exception {

        locationService.addLocation(directory, true, true);
        locationsViewModel.loadLocations();
        locationsViewModel.selectedLocationProperty().set(
                locationsViewModel.locations().getFirst()
        );
    }

    private Path createVideo(Path directory, String fileName)
            throws IOException {

        final Path path = Files.writeString(directory.resolve(fileName), "x");
        Files.setLastModifiedTime(path, FileTime.fromMillis(FIRST_MODIFIED));
        return path;
    }

    private boolean hasCurrentFile(
            List<MediaLocationScanProgress> progress,
            String fileName) {

        return progress.stream().anyMatch(item ->
                item.currentFile() != null
                        && fileName.equals(
                        item.currentFile().getFileName().toString()
                )
        );
    }

    private static final class MutableBoolean {
        private boolean value;

        private MutableBoolean(boolean value) {
            this.value = value;
        }

        private boolean value() {
            return value;
        }

        private void setValue(boolean value) {
            this.value = value;
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
        public String hash(Path mediaPath) {
            return mediaPath.getFileName().toString();
        }
    }
}

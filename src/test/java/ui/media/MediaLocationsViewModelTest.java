package ui.media;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaHashProvider;
import media.MediaMetadata;
import media.MediaMetadataProbe;
import media.MediaProbeException;
import media.VideoFileDiscovery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaFileRepository;
import repository.MediaLocationRepository;
import service.MediaLocationScanService;
import service.MediaLocationService;
import service.MediaScanService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

class MediaLocationsViewModelTest {
    private static final String DATABASE_FILE_NAME =
            "media-locations-view-model-test.db";
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int DURATION_MILLIS = 3_000;
    private static final long MODIFIED_MILLIS = 1_700_000_000_000L;

    @TempDir
    Path temporaryDirectory;

    private MediaLocationService locationService;
    private MediaFileRepository mediaFileRepository;
    private FakeProbe probe;
    private RecordingRefreshCallback refreshCallback;
    private MediaLocationsViewModel viewModel;

    @BeforeEach
    void initialize() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        mediaFileRepository = new MediaFileRepository(databaseManager);
        locationService = new MediaLocationService(
                new MediaLocationRepository(databaseManager)
        );
        probe = new FakeProbe();
        refreshCallback = new RecordingRefreshCallback();
        final MediaLocationScanService scanService =
                new MediaLocationScanService(
                        locationService,
                        new MediaScanService(
                                mediaFileRepository,
                                new VideoFileDiscovery(),
                                probe,
                                new FakeHasher()
                        )
                );
        final Executor directExecutor = Runnable::run;
        viewModel = new MediaLocationsViewModel(
                locationService,
                scanService,
                directExecutor,
                directExecutor,
                refreshCallback::accept
        );
    }

    @Test
    @DisplayName("Scan selected reports filename counts summary and refresh")
    void scanSelectedReportsFilenameCountsSummaryAndRefresh()
            throws Exception {

        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        final Path mediaPath = createVideo(directory, "visible.mp4");
        addAndSelectLocation(directory);

        viewModel.scanSelected();

        Assertions.assertAll(
                () -> Assertions.assertEquals("",
                        viewModel.currentFileProperty().get()),
                () -> Assertions.assertEquals("",
                        viewModel.currentFilePathProperty().get()),
                () -> Assertions.assertEquals(1,
                        viewModel.discoveredCountProperty().get()),
                () -> Assertions.assertEquals(1,
                        viewModel.processedCountProperty().get()),
                () -> Assertions.assertEquals(1,
                        viewModel.newCountProperty().get()),
                () -> Assertions.assertTrue(
                        viewModel.scanSummaryProperty().get()
                                .contains("COMPLETED")
                ),
                () -> Assertions.assertEquals(
                        List.of(true),
                        refreshCallback.values
                ),
                () -> Assertions.assertTrue(mediaFileRepository.findByPath(
                        mediaPath.toAbsolutePath().normalize()
                ).isPresent())
        );
    }

    @Test
    @DisplayName("No-change scan does not request queue refresh")
    void noChangeScanDoesNotRequestQueueRefresh() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        createVideo(directory, "unchanged.mp4");
        addAndSelectLocation(directory);

        viewModel.scanSelected();
        refreshCallback.values.clear();
        viewModel.scanSelected();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1,
                        viewModel.unchangedCountProperty().get()),
                () -> Assertions.assertEquals(
                        List.of(false),
                        refreshCallback.values
                )
        );
    }

    @Test
    @DisplayName("Cancellation final state remains cancelled")
    void cancellationFinalStateRemainsCancelled() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        createVideo(directory, "first.mp4");
        createVideo(directory, "second.mp4");
        addAndSelectLocation(directory);
        probe.afterSecondProbe = viewModel::cancelScan;

        viewModel.scanSelected();

        Assertions.assertAll(
                () -> Assertions.assertFalse(
                        viewModel.scanRunningProperty().get()
                ),
                () -> Assertions.assertEquals(
                        "CANCELLED",
                        viewModel.currentPhaseProperty().get()
                ),
                () -> Assertions.assertTrue(
                        viewModel.scanSummaryProperty().get()
                                .contains("CANCELLED")
                ),
                () -> Assertions.assertEquals(1,
                        mediaFileRepository.findAll().size()),
                () -> Assertions.assertEquals(
                        List.of(true),
                        refreshCallback.values
                )
        );
    }

    @Test
    @DisplayName("Cancel request disables repeated cancellation")
    void cancelRequestDisablesRepeatedCancellation() throws Exception {
        final Path directory = Files.createDirectory(
                temporaryDirectory.resolve("media")
        );
        createVideo(directory, "pending.mp4");
        addAndSelectLocation(directory);

        viewModel.cancelScan();

        Assertions.assertFalse(viewModel.cancelRequestedProperty().get());
    }

    private void addAndSelectLocation(Path directory) throws Exception {
        locationService.addLocation(directory, true, true);
        viewModel.loadLocations();
        viewModel.selectedLocationProperty().set(
                viewModel.locations().getFirst()
        );
    }

    private Path createVideo(Path directory, String fileName)
            throws IOException {

        final Path path = Files.writeString(directory.resolve(fileName), "x");
        Files.setLastModifiedTime(path, FileTime.fromMillis(MODIFIED_MILLIS));
        return path;
    }

    private static final class RecordingRefreshCallback {
        private final List<Boolean> values = new ArrayList<>();

        private void accept(boolean value) {
            values.add(value);
        }
    }

    private static final class FakeProbe implements MediaMetadataProbe {
        private int calls;
        private Runnable afterSecondProbe;

        @Override
        public MediaMetadata probe(Path mediaPath)
                throws IOException, MediaProbeException {

            calls++;

            if (calls == 2 && afterSecondProbe != null) {
                afterSecondProbe.run();
            }

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
            return null;
        }
    }
}

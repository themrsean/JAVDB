package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaMetadata;
import media.MediaMetadataProbe;
import media.MediaProbeException;
import media.MediaHashProvider;
import media.VideoFileDiscovery;
import model.MediaFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaFileRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

class MediaScanServiceTest {
    private static final String DATABASE_FILE_NAME =
            "media-scan-service-test.db";
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final long DURATION_MILLIS = 1_234L;
    private static final long FIRST_MODIFIED = 1_700_000_000_000L;
    private static final long SECOND_MODIFIED = 1_800_000_000_000L;

    @TempDir
    Path temporaryDirectory;

    private MediaFileRepository repository;
    private FakeProbe probe;
    private FakeHasher hasher;
    private MediaScanService service;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        repository = new MediaFileRepository(databaseManager);
        probe = new FakeProbe();
        hasher = new FakeHasher();
        service = new MediaScanService(
                repository,
                new VideoFileDiscovery(),
                probe,
                hasher
        );
    }

    @Test
    @DisplayName("Progress reports actual current files and final counts")
    void progressReportsActualCurrentFilesAndFinalCounts() throws Exception {
        final Path first = createVideo("a.mp4", "abc", FIRST_MODIFIED);
        final Path second = createVideo("b.mp4", "abc", FIRST_MODIFIED);
        final List<MediaScanProgress> progress = new ArrayList<>();

        final MediaScanResult result = service.scan(
                new MediaScanRequest(
                        temporaryDirectory,
                        false,
                        false,
                        false,
                        Set.of()
                ),
                Set.of(),
                progress::add,
                new ScanCancellationToken()
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(progress.stream().anyMatch(item ->
                        item.phase() == MediaLocationScanPhase.PROCESSING_FILE
                                && item.currentFile().equals(
                                first.toAbsolutePath().normalize()
                        )
                )),
                () -> Assertions.assertTrue(progress.stream().anyMatch(item ->
                        item.phase() == MediaLocationScanPhase.PROCESSING_FILE
                                && item.currentFile().equals(
                                second.toAbsolutePath().normalize()
                        )
                )),
                () -> Assertions.assertEquals(2, result.summary().added()),
                () -> Assertions.assertEquals(
                        2,
                        progress.getLast().summary().added()
                )
        );
    }

    @Test
    @DisplayName("Discovery completion reports discovered file count")
    void discoveryCompletionReportsDiscoveredFileCount() throws Exception {
        createVideo("a.mp4", "abc", FIRST_MODIFIED);
        createVideo("b.mp4", "abc", FIRST_MODIFIED);
        final List<MediaScanProgress> progress = new ArrayList<>();

        service.scan(
                new MediaScanRequest(
                        temporaryDirectory,
                        false,
                        false,
                        false,
                        Set.of()
                ),
                Set.of(),
                progress::add,
                new ScanCancellationToken()
        );

        final MediaScanProgress discoveryCompleted = progress.stream()
                .filter(item -> item.phase()
                        == MediaLocationScanPhase.DISCOVERY_COMPLETED)
                .findFirst()
                .orElseThrow();

        Assertions.assertEquals(
                2,
                discoveryCompleted.summary().filesDiscovered()
        );
    }

    @Test
    @DisplayName("Every processed file produces completion progress")
    void everyProcessedFileProducesCompletionProgress() throws Exception {
        createVideo("a.mp4", "abc", FIRST_MODIFIED);
        createVideo("b.mp4", "abc", FIRST_MODIFIED);
        createVideo("c.mp4", "abc", FIRST_MODIFIED);
        final List<MediaScanProgress> progress = new ArrayList<>();

        service.scan(
                new MediaScanRequest(
                        temporaryDirectory,
                        false,
                        false,
                        false,
                        Set.of()
                ),
                Set.of(),
                progress::add,
                new ScanCancellationToken()
        );

        final long completedFiles = progress.stream()
                .filter(item -> item.phase()
                        == MediaLocationScanPhase.FILE_COMPLETED)
                .count();

        Assertions.assertEquals(3L, completedFiles);
    }

    @Test
    @DisplayName("Cancellation before first file preserves no records")
    void cancellationBeforeFirstFilePreservesNoRecords() throws Exception {
        createVideo("a.mp4", "abc", FIRST_MODIFIED);
        final ScanCancellationToken token = new ScanCancellationToken();
        final List<MediaScanProgress> progress = new ArrayList<>();
        token.cancel();

        final MediaScanResult result = service.scan(
                new MediaScanRequest(
                        temporaryDirectory,
                        false,
                        false,
                        false,
                        Set.of()
                ),
                Set.of(),
                progress::add,
                token
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, result.summary().added()),
                () -> Assertions.assertTrue(repository.findAll().isEmpty()),
                () -> Assertions.assertEquals(
                        MediaLocationScanPhase.CANCELLED,
                        progress.getLast().phase()
                )
        );
    }

    @Test
    @DisplayName("Cancellation after probe prevents persistence")
    void cancellationAfterProbePreventsPersistence() throws Exception {
        createVideo("a.mp4", "abc", FIRST_MODIFIED);
        final ScanCancellationToken token = new ScanCancellationToken();
        probe.afterProbe = token::cancel;
        final List<MediaScanProgress> progress = new ArrayList<>();

        final MediaScanResult result = service.scan(
                new MediaScanRequest(
                        temporaryDirectory,
                        false,
                        false,
                        false,
                        Set.of()
                ),
                Set.of(),
                progress::add,
                token
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, result.summary().added()),
                () -> Assertions.assertEquals(1, result.summary().failed()),
                () -> Assertions.assertTrue(repository.findAll().isEmpty()),
                () -> Assertions.assertEquals(
                        MediaLocationScanPhase.CANCELLED,
                        progress.getLast().phase()
                )
        );
    }

    @Test
    @DisplayName("CLI-style scanning works without progress listener")
    void cliStyleScanningWorksWithoutProgressListener() throws Exception {
        createVideo("a.mp4", "abc", FIRST_MODIFIED);

        final MediaScanResult result = service.scan(
                new MediaScanRequest(
                        temporaryDirectory,
                        false,
                        false,
                        false,
                        Set.of()
                ),
                Set.of(),
                null,
                null
        );

        Assertions.assertEquals(1, result.summary().added());
    }

    @Test
    @DisplayName("New file is added with filesystem and probe metadata")
    void newFileIsAddedWithFilesystemAndProbeMetadata() throws Exception {
        final Path file = createVideo("a.mp4", "abc", FIRST_MODIFIED);

        final MediaScanResult result = service.scan(new MediaScanRequest(
                temporaryDirectory,
                false,
                false,
                false,
                Set.of()
        ));

        final MediaFile stored = repository.findByPath(
                file.toAbsolutePath().normalize()
        ).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.summary().added()),
                () -> Assertions.assertEquals(WIDTH, stored.getWidth()),
                () -> Assertions.assertEquals(HEIGHT, stored.getHeight()),
                () -> Assertions.assertEquals(
                        DURATION_MILLIS,
                        stored.getDuration().toMillis()
                ),
                () -> Assertions.assertEquals(FIRST_MODIFIED,
                        stored.getLastModifiedMillis()),
                () -> Assertions.assertEquals(Files.size(file),
                        stored.getFileSize())
        );
    }

    @Test
    @DisplayName("Unchanged file skips probe and hashing")
    void unchangedFileSkipsProbeAndHashing() throws Exception {
        final Path file = createVideo("a.mp4", "abc", FIRST_MODIFIED);
        service.scan(new MediaScanRequest(
                temporaryDirectory,
                true,
                false,
                false,
                Set.of()
        ));
        probe.calls = 0;
        hasher.calls = 0;

        final MediaScanResult result = service.scan(new MediaScanRequest(
                temporaryDirectory,
                true,
                false,
                false,
                Set.of()
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.summary().unchanged()),
                () -> Assertions.assertEquals(0, probe.calls),
                () -> Assertions.assertEquals(0, hasher.calls)
        );
    }

    @Test
    @DisplayName("Changed last-modified refreshes existing UUID")
    void changedLastModifiedRefreshesExistingUuid() throws Exception {
        final Path file = createVideo("a.mp4", "abc", FIRST_MODIFIED);
        service.scan(new MediaScanRequest(
                temporaryDirectory,
                false,
                false,
                false,
                Set.of()
        ));
        final MediaFile before = repository.findByPath(
                file.toAbsolutePath().normalize()
        ).orElseThrow();
        Files.setLastModifiedTime(file, FileTime.fromMillis(SECOND_MODIFIED));

        final MediaScanResult result = service.scan(new MediaScanRequest(
                temporaryDirectory,
                false,
                false,
                false,
                Set.of()
        ));
        final MediaFile after = repository.findByPath(
                file.toAbsolutePath().normalize()
        ).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.summary().updated()),
                () -> Assertions.assertEquals(before.getId(), after.getId()),
                () -> Assertions.assertEquals(
                        SECOND_MODIFIED,
                        after.getLastModifiedMillis()
                )
        );
    }

    @Test
    @DisplayName("Dry run reports add without persisting")
    void dryRunReportsAddWithoutPersisting() throws Exception {
        final Path file = createVideo("a.mp4", "abc", FIRST_MODIFIED);

        final MediaScanResult result = service.scan(new MediaScanRequest(
                temporaryDirectory,
                false,
                true,
                false,
                Set.of()
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        1,
                        result.summary().dryRunAdditions()
                ),
                () -> Assertions.assertTrue(repository.findByPath(
                        file.toAbsolutePath().normalize()
                ).isEmpty())
        );
    }

    @Test
    @DisplayName("Probe failure affects row and continues")
    void probeFailureAffectsRowAndContinues() throws Exception {
        createVideo("a.mp4", "abc", FIRST_MODIFIED);
        createVideo("b.mp4", "abc", FIRST_MODIFIED);
        probe.failPathSuffix = "a.mp4";

        final MediaScanResult result = service.scan(new MediaScanRequest(
                temporaryDirectory,
                false,
                false,
                false,
                Set.of()
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.summary().failed()),
                () -> Assertions.assertEquals(1, result.summary().added())
        );
    }

    @Test
    @DisplayName("Hashing detects duplicate content and stores both")
    void hashingDetectsDuplicateContentAndStoresBoth() throws Exception {
        createVideo("a.mp4", "same", FIRST_MODIFIED);
        createVideo("b.mp4", "same", FIRST_MODIFIED);

        final MediaScanResult result = service.scan(new MediaScanRequest(
                temporaryDirectory,
                true,
                false,
                false,
                Set.of()
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        1,
                        result.summary().duplicateContentFiles()
                ),
                () -> Assertions.assertEquals(2, repository.findAll().size())
        );
    }

    @Test
    @DisplayName("Verification reports present, changed, and missing files")
    void verificationReportsPresentChangedAndMissingFiles() throws Exception {
        final Path present = createVideo("present.mp4", "abc", FIRST_MODIFIED);
        final Path changed = createVideo("changed.mp4", "abc", FIRST_MODIFIED);
        final Path missing = temporaryDirectory.resolve("missing.mp4");
        insertMedia(present, FIRST_MODIFIED);
        insertMedia(changed, FIRST_MODIFIED);
        repository.insert(new MediaFile(
                java.util.UUID.randomUUID(),
                missing.toAbsolutePath().normalize(),
                3L,
                null,
                Duration.ofMillis(DURATION_MILLIS),
                WIDTH,
                HEIGHT,
                FIRST_MODIFIED
        ));
        Files.setLastModifiedTime(changed, FileTime.fromMillis(
                SECOND_MODIFIED
        ));

        final MediaVerificationResult result = service.verify(
                new MediaVerificationRequest(false, false, false)
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(hasStatus(
                        result,
                        MediaVerificationStatus.PRESENT
                )),
                () -> Assertions.assertTrue(hasStatus(
                        result,
                        MediaVerificationStatus.CHANGED
                )),
                () -> Assertions.assertTrue(hasStatus(
                        result,
                        MediaVerificationStatus.MISSING
                ))
        );
    }

    @Test
    @DisplayName("Verification refresh updates changed metadata and preserves UUID")
    void verificationRefreshUpdatesChangedMetadataAndPreservesUuid()
            throws Exception {

        final Path file = createVideo("refresh.mp4", "abc", FIRST_MODIFIED);
        final MediaFile inserted = insertMedia(file, FIRST_MODIFIED);
        Files.setLastModifiedTime(file, FileTime.fromMillis(SECOND_MODIFIED));

        final MediaVerificationResult result = service.verify(
                new MediaVerificationRequest(true, false, false)
        );
        final MediaFile updated = repository.findById(
                inserted.getId()
        ).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertTrue(hasStatus(
                        result,
                        MediaVerificationStatus.REFRESHED
                )),
                () -> Assertions.assertEquals(inserted.getId(),
                        updated.getId()),
                () -> Assertions.assertEquals(SECOND_MODIFIED,
                        updated.getLastModifiedMillis())
        );
    }

    @Test
    @DisplayName("Dry-run verification refresh does not update changed metadata")
    void dryRunVerificationRefreshDoesNotUpdateChangedMetadata()
            throws Exception {

        final Path file = createVideo("dry-refresh.mp4", "abc",
                FIRST_MODIFIED);
        final MediaFile inserted = insertMedia(file, FIRST_MODIFIED);
        Files.setLastModifiedTime(file, FileTime.fromMillis(SECOND_MODIFIED));

        final MediaVerificationResult result = service.verify(
                new MediaVerificationRequest(true, true, false)
        );
        final MediaFile stored = repository.findById(
                inserted.getId()
        ).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertTrue(hasStatus(
                        result,
                        MediaVerificationStatus.WOULD_REFRESH
                )),
                () -> Assertions.assertEquals(FIRST_MODIFIED,
                        stored.getLastModifiedMillis())
        );
    }

    private Path createVideo(
            String name,
            String content,
            long lastModifiedMillis) throws IOException {

        final Path file = Files.writeString(
                temporaryDirectory.resolve(name),
                content
        );
        Files.setLastModifiedTime(file, FileTime.fromMillis(lastModifiedMillis));
        return file;
    }

    private MediaFile insertMedia(Path file, long lastModifiedMillis)
            throws Exception {

        final MediaFile mediaFile = new MediaFile(
                java.util.UUID.randomUUID(),
                file.toAbsolutePath().normalize(),
                Files.size(file),
                null,
                Duration.ofMillis(DURATION_MILLIS),
                WIDTH,
                HEIGHT,
                lastModifiedMillis
        );
        repository.insert(mediaFile);
        return mediaFile;
    }

    private boolean hasStatus(
            MediaVerificationResult result,
            MediaVerificationStatus status) {

        return result.files()
                .stream()
                .anyMatch(fileResult -> fileResult.status() == status);
    }

    private static final class FakeProbe implements MediaMetadataProbe {
        private int calls;
        private String failPathSuffix;
        private Runnable afterProbe;

        @Override
        public MediaMetadata probe(Path mediaPath)
                throws IOException, MediaProbeException {

            calls++;

            if (failPathSuffix != null
                    && mediaPath.toString().endsWith(failPathSuffix)) {
                throw new MediaProbeException("probe failed");
            }

            if (afterProbe != null) {
                afterProbe.run();
            }

            return new MediaMetadata(
                    Duration.ofMillis(DURATION_MILLIS),
                    WIDTH,
                    HEIGHT
            );
        }
    }

    private static final class FakeHasher implements MediaHashProvider {
        private int calls;

        @Override
        public String hash(Path path) {
            calls++;
            return "hash-" + path.getFileName().toString().replace(
                    "b.mp4",
                    "a.mp4"
            );
        }
    }
}

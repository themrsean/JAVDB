package service;

import model.MediaLocation;
import model.MediaLocationScanStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MediaLocationScanService {
    private final MediaLocationService locationService;
    private final MediaScanService mediaScanService;

    public MediaLocationScanService(
            MediaLocationService locationService,
            MediaScanService mediaScanService) {

        this.locationService = Objects.requireNonNull(
                locationService,
                "Media location service must not be null"
        );
        this.mediaScanService = Objects.requireNonNull(
                mediaScanService,
                "Media scan service must not be null"
        );
    }

    public MediaLocationScanResult scanLocation(
            UUID locationId,
            MediaLocationScanOptions options,
            MediaLocationScanProgressListener listener,
            ScanCancellationToken cancellationToken)
            throws SQLException {

        Objects.requireNonNull(
                locationId,
                "Media location ID must not be null"
        );
        final MediaLocation location = locationService.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown media location: " + locationId
                ));

        return scanLocation(
                location,
                1,
                1,
                effectiveOptions(options),
                listener,
                effectiveCancellationToken(cancellationToken),
                new HashSet<>()
        );
    }

    public MediaLocationBatchScanResult scanEnabledLocations(
            MediaLocationScanOptions options,
            MediaLocationScanProgressListener listener,
            ScanCancellationToken cancellationToken)
            throws SQLException {

        final List<MediaLocation> locations = locationService.findEnabled();
        final List<MediaLocationScanResult> results = new ArrayList<>();
        final Set<Path> alreadyProcessedPaths = new HashSet<>();
        final MediaLocationScanOptions effectiveOptions =
                effectiveOptions(options);
        final ScanCancellationToken effectiveCancellationToken =
                effectiveCancellationToken(cancellationToken);
        int locationIndex = 0;
        boolean keepScanning = true;

        while (locationIndex < locations.size() && keepScanning) {
            final MediaLocation location = locations.get(locationIndex);
            final MediaLocationScanResult result = scanLocation(
                    location,
                    locationIndex + 1,
                    locations.size(),
                    effectiveOptions,
                    listener,
                    effectiveCancellationToken,
                    alreadyProcessedPaths
            );
            results.add(result);
            keepScanning = !effectiveCancellationToken
                    .cancellationRequested();
            locationIndex++;
        }

        return new MediaLocationBatchScanResult(List.copyOf(results));
    }

    private MediaLocationScanResult scanLocation(
            MediaLocation location,
            int locationIndex,
            int totalLocations,
            MediaLocationScanOptions options,
            MediaLocationScanProgressListener listener,
            ScanCancellationToken cancellationToken,
            Set<Path> alreadyProcessedPaths) throws SQLException {

        final Instant startedAt = Instant.now();
        publish(
                listener,
                MediaLocationScanPhase.VALIDATING_LOCATION,
                location,
                locationIndex,
                totalLocations,
                null,
                emptySummary()
        );

        locationService.updateScanResult(
                location.id(),
                new MediaLocationScanResult(
                        startedAt,
                        null,
                        MediaLocationScanStatus.RUNNING,
                        null,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                )
        );

        final MediaLocationScanResult result;

        if (cancellationToken.cancellationRequested()) {
            result = cancelled(startedAt, "Scan cancelled before work.");
        } else if (!Files.exists(location.path())
                || !Files.isDirectory(location.path())
                || !Files.isReadable(location.path())) {
            result = unavailable(startedAt, "Directory unavailable.");
        } else {
            result = scanAvailableLocation(
                    location,
                    locationIndex,
                    totalLocations,
                    options,
                    listener,
                    cancellationToken,
                    alreadyProcessedPaths,
                    startedAt
            );
        }

        locationService.updateScanResult(location.id(), result);
        publishFinal(listener, location, locationIndex, totalLocations, result);

        return result;
    }

    private MediaLocationScanResult scanAvailableLocation(
            MediaLocation location,
            int locationIndex,
            int totalLocations,
            MediaLocationScanOptions options,
            MediaLocationScanProgressListener listener,
            ScanCancellationToken cancellationToken,
            Set<Path> alreadyProcessedPaths,
            Instant startedAt) {

        MediaLocationScanResult result;

        try {
            publish(
                    listener,
                    MediaLocationScanPhase.DISCOVERING,
                    location,
                    locationIndex,
                    totalLocations,
                    null,
                    emptySummary()
            );
            final MediaScanResult scanResult = mediaScanService.scan(
                    new MediaScanRequest(
                            location.path(),
                            options.hashingEnabled(),
                            options.dryRun(),
                            options.failFast(),
                            options.additionalExtensions(),
                            location.recursive()
                    ),
                    alreadyProcessedPaths,
                    progress -> publish(
                            listener,
                            progress.phase(),
                            location,
                            locationIndex,
                            totalLocations,
                            progress.currentFile(),
                            progress.summary()
                    ),
                    cancellationToken
            );
            addProcessedPaths(alreadyProcessedPaths, scanResult);
            result = completedResult(startedAt, scanResult);

            if (cancellationToken.cancellationRequested()) {
                result = cancelled(
                        startedAt,
                        "Scan cancelled.",
                        scanResult.summary()
                );
            }
        } catch (IOException exception) {
            result = failed(startedAt, exception.getMessage());
        }

        return result;
    }

    private void addProcessedPaths(
            Set<Path> alreadyProcessedPaths,
            MediaScanResult scanResult) {

        for (MediaScanFileResult fileResult : scanResult.files()) {
            alreadyProcessedPaths.add(fileResult.path());
        }
    }

    private MediaLocationScanResult completedResult(
            Instant startedAt,
            MediaScanResult scanResult) {

        final MediaScanSummary summary = scanResult.summary();
        final MediaLocationScanStatus status = summary.failed() > 0
                ? MediaLocationScanStatus.COMPLETED_WITH_ERRORS
                : MediaLocationScanStatus.COMPLETED;

        return new MediaLocationScanResult(
                startedAt,
                Instant.now(),
                status,
                status == MediaLocationScanStatus.COMPLETED
                        ? null
                        : "Some files failed.",
                summary.filesDiscovered(),
                summary.added(),
                summary.updated(),
                summary.unchanged(),
                0,
                summary.failed()
        );
    }

    private MediaLocationScanResult cancelled(
            Instant startedAt,
            String message) {

        return cancelled(startedAt, message, emptySummary());
    }

    private MediaLocationScanResult cancelled(
            Instant startedAt,
            String message,
            MediaScanSummary summary) {

        return new MediaLocationScanResult(
                startedAt,
                Instant.now(),
                MediaLocationScanStatus.CANCELLED,
                message,
                summary.filesDiscovered(),
                summary.added(),
                summary.updated(),
                summary.unchanged(),
                0,
                summary.failed()
        );
    }

    private MediaLocationScanResult unavailable(
            Instant startedAt,
            String message) {

        return new MediaLocationScanResult(
                startedAt,
                Instant.now(),
                MediaLocationScanStatus.DIRECTORY_UNAVAILABLE,
                message,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    private MediaLocationScanResult failed(Instant startedAt, String message) {
        return new MediaLocationScanResult(
                startedAt,
                Instant.now(),
                MediaLocationScanStatus.FAILED,
                message,
                0,
                0,
                0,
                0,
                0,
                1
        );
    }

    private MediaScanSummary emptySummary() {
        return new MediaScanSummary(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private void publishFinal(
            MediaLocationScanProgressListener listener,
            MediaLocation location,
            int locationIndex,
            int totalLocations,
            MediaLocationScanResult result) {

        final MediaLocationScanPhase phase =
                switch (result.status()) {
                    case CANCELLED -> MediaLocationScanPhase.CANCELLED;
                    case FAILED, DIRECTORY_UNAVAILABLE ->
                            MediaLocationScanPhase.FAILED;
                    default -> MediaLocationScanPhase.COMPLETED;
                };
        publish(
                listener,
                phase,
                location,
                locationIndex,
                totalLocations,
                null,
                new MediaScanSummary(
                        result.discoveredCount(),
                        result.newCount(),
                        result.updatedCount(),
                        result.unchangedCount(),
                        0,
                        result.failedCount(),
                        0,
                        0
                )
        );
    }

    private void publish(
            MediaLocationScanProgressListener listener,
            MediaLocationScanPhase phase,
            MediaLocation location,
            int locationIndex,
            int totalLocations,
            Path currentFile,
            MediaScanSummary summary) {

        if (listener != null) {
            listener.onProgress(new MediaLocationScanProgress(
                    phase,
                    location.id(),
                    location.path(),
                    currentFile,
                    locationIndex,
                    totalLocations,
                    summary.filesDiscovered(),
                    summary.added()
                            + summary.updated()
                            + summary.unchanged()
                            + summary.failed(),
                    summary.added(),
                    summary.updated(),
                    summary.unchanged(),
                    0,
                    summary.failed()
            ));
        }
    }

    private MediaLocationScanOptions effectiveOptions(
            MediaLocationScanOptions options) {

        return options == null ? MediaLocationScanOptions.guiDefaults()
                : options;
    }

    private ScanCancellationToken effectiveCancellationToken(
            ScanCancellationToken cancellationToken) {

        return cancellationToken == null ? new ScanCancellationToken()
                : cancellationToken;
    }
}

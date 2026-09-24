package ui.media;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.MediaLocation;
import service.MediaLocationBatchScanResult;
import service.MediaLocationScanOptions;
import service.MediaLocationScanPhase;
import service.MediaLocationScanProgress;
import service.MediaLocationScanResult;
import service.MediaLocationScanService;
import service.MediaLocationService;
import service.ScanCancellationToken;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class MediaLocationsViewModel {
    private final MediaLocationService locationService;
    private final MediaLocationScanService scanService;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final Consumer<Boolean> queueRefreshCallback;
    private final ObservableList<MediaLocation> locations =
            FXCollections.observableArrayList();
    private final ObjectProperty<MediaLocation> selectedLocation =
            new SimpleObjectProperty<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty scanRunning =
            new SimpleBooleanProperty(false);
    private final BooleanProperty cancelRequested =
            new SimpleBooleanProperty(false);
    private final StringProperty currentPhase = new SimpleStringProperty("");
    private final StringProperty currentLocation = new SimpleStringProperty("");
    private final StringProperty currentFile = new SimpleStringProperty("");
    private final StringProperty currentFilePath = new SimpleStringProperty("");
    private final StringProperty scanSummary = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final IntegerProperty discoveredCount = new SimpleIntegerProperty();
    private final IntegerProperty processedCount = new SimpleIntegerProperty();
    private final IntegerProperty newCount = new SimpleIntegerProperty();
    private final IntegerProperty updatedCount = new SimpleIntegerProperty();
    private final IntegerProperty unchangedCount = new SimpleIntegerProperty();
    private final IntegerProperty missingCount = new SimpleIntegerProperty();
    private final IntegerProperty failedCount = new SimpleIntegerProperty();
    private final IntegerProperty locationIndex = new SimpleIntegerProperty();
    private final IntegerProperty totalLocations = new SimpleIntegerProperty();
    private final DoubleProperty progress = new SimpleDoubleProperty(-1.0);
    private volatile boolean disposed;
    private volatile ScanCancellationToken activeCancellationToken;

    public MediaLocationsViewModel(
            MediaLocationService locationService,
            MediaLocationScanService scanService,
            Executor backgroundExecutor,
            Executor uiExecutor,
            Consumer<Boolean> queueRefreshCallback) {

        this.locationService = Objects.requireNonNull(
                locationService,
                "Media location service must not be null"
        );
        this.scanService = Objects.requireNonNull(
                scanService,
                "Media location scan service must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
        this.queueRefreshCallback = Objects.requireNonNull(
                queueRefreshCallback,
                "Queue refresh callback must not be null"
        );
    }

    public ObservableList<MediaLocation> locations() {
        return locations;
    }

    public ObjectProperty<MediaLocation> selectedLocationProperty() {
        return selectedLocation;
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading;
    }

    public ReadOnlyBooleanProperty scanRunningProperty() {
        return scanRunning;
    }

    public ReadOnlyBooleanProperty cancelRequestedProperty() {
        return cancelRequested;
    }

    public ReadOnlyStringProperty currentPhaseProperty() {
        return currentPhase;
    }

    public ReadOnlyStringProperty currentLocationProperty() {
        return currentLocation;
    }

    public ReadOnlyStringProperty currentFileProperty() {
        return currentFile;
    }

    public ReadOnlyStringProperty currentFilePathProperty() {
        return currentFilePath;
    }

    public ReadOnlyStringProperty scanSummaryProperty() {
        return scanSummary;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessage;
    }

    public ReadOnlyIntegerProperty discoveredCountProperty() {
        return discoveredCount;
    }

    public ReadOnlyIntegerProperty processedCountProperty() {
        return processedCount;
    }

    public ReadOnlyIntegerProperty newCountProperty() {
        return newCount;
    }

    public ReadOnlyIntegerProperty updatedCountProperty() {
        return updatedCount;
    }

    public ReadOnlyIntegerProperty unchangedCountProperty() {
        return unchangedCount;
    }

    public ReadOnlyIntegerProperty missingCountProperty() {
        return missingCount;
    }

    public ReadOnlyIntegerProperty failedCountProperty() {
        return failedCount;
    }

    public ReadOnlyIntegerProperty locationIndexProperty() {
        return locationIndex;
    }

    public ReadOnlyIntegerProperty totalLocationsProperty() {
        return totalLocations;
    }

    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    public void loadLocations() {
        if (!loading.get() && !disposed) {
            loading.set(true);
            backgroundExecutor.execute(() -> {
                try {
                    final List<MediaLocation> loaded =
                            locationService.findAll();
                    uiExecutor.execute(() -> applyLocations(loaded));
                } catch (Exception exception) {
                    uiExecutor.execute(() -> applyError(exception));
                }
            });
        }
    }

    public void addLocation(Path path) {
        if (!scanRunning.get() && !disposed) {
            backgroundExecutor.execute(() -> {
                try {
                    locationService.addLocation(path, true, true);
                    final List<MediaLocation> loaded =
                            locationService.findAll();
                    uiExecutor.execute(() -> applyLocations(loaded));
                } catch (Exception exception) {
                    uiExecutor.execute(() -> applyError(exception));
                }
            });
        }
    }

    public void removeSelectedLocation() {
        final MediaLocation selected = selectedLocation.get();

        if (selected != null && !scanRunning.get() && !disposed) {
            backgroundExecutor.execute(() -> {
                try {
                    locationService.removeLocation(selected.id());
                    final List<MediaLocation> loaded =
                            locationService.findAll();
                    uiExecutor.execute(() -> applyLocations(loaded));
                } catch (Exception exception) {
                    uiExecutor.execute(() -> applyError(exception));
                }
            });
        }
    }

    public void updateSettings(
            MediaLocation location,
            boolean enabled,
            boolean recursive) {

        if (location != null && !scanRunning.get() && !disposed) {
            backgroundExecutor.execute(() -> {
                try {
                    locationService.updateSettings(
                            location.id(),
                            enabled,
                            recursive
                    );
                    final List<MediaLocation> loaded =
                            locationService.findAll();
                    uiExecutor.execute(() -> applyLocations(loaded));
                } catch (Exception exception) {
                    uiExecutor.execute(() -> applyError(exception));
                }
            });
        }
    }

    public void scanSelected() {
        final MediaLocation selected = selectedLocation.get();

        if (selected != null && !scanRunning.get() && !disposed) {
            beginScan();
            backgroundExecutor.execute(() -> {
                try {
                    final MediaLocationScanResult result =
                            scanService.scanLocation(
                                    selected.id(),
                                    MediaLocationScanOptions.guiDefaults(),
                                    this::publishProgress,
                                    activeCancellationToken
                            );
                    finishScan(result);
                } catch (Exception exception) {
                    uiExecutor.execute(() -> applyError(exception));
                }
            });
        }
    }

    public void scanAllEnabled() {
        if (!scanRunning.get() && !disposed) {
            beginScan();
            backgroundExecutor.execute(() -> {
                try {
                    final MediaLocationBatchScanResult result =
                            scanService.scanEnabledLocations(
                                    MediaLocationScanOptions.guiDefaults(),
                                    this::publishProgress,
                                    activeCancellationToken
                            );
                    finishBatchScan(result);
                } catch (Exception exception) {
                    uiExecutor.execute(() -> applyError(exception));
                }
            });
        }
    }

    public void cancelScan() {
        final ScanCancellationToken token = activeCancellationToken;

        if (token != null && scanRunning.get()) {
            cancelRequested.set(true);
            currentPhase.set("Cancellation requested...");
            token.cancel();
        }
    }

    public void clearResultMessage() {
        scanSummary.set("");
        errorMessage.set("");
    }

    public void dispose() {
        disposed = true;
        cancelScan();
    }

    private void beginScan() {
        activeCancellationToken = new ScanCancellationToken();
        scanRunning.set(true);
        cancelRequested.set(false);
        errorMessage.set("");
        scanSummary.set("");
    }

    private void publishProgress(MediaLocationScanProgress progress) {
        uiExecutor.execute(() -> {
            if (!disposed) {
                currentPhase.set(progress.phase().name());
                currentLocation.set(progress.locationPath().toString());
                currentFile.set(progress.currentFile() == null
                        ? ""
                        : progress.currentFile().getFileName().toString());
                currentFilePath.set(progress.currentFile() == null
                        ? ""
                        : progress.currentFile().toString());
                locationIndex.set(progress.locationIndex());
                totalLocations.set(progress.totalLocations());
                discoveredCount.set(progress.discoveredFiles());
                processedCount.set(progress.processedFiles());
                newCount.set(progress.newFiles());
                updatedCount.set(progress.updatedFiles());
                unchangedCount.set(progress.unchangedFiles());
                missingCount.set(progress.missingFiles());
                failedCount.set(progress.failedFiles());
                if (progress.discoveredFiles() > 0) {
                    this.progress.set((double) progress.processedFiles()
                            / (double) progress.discoveredFiles());
                } else {
                    this.progress.set(-1.0);
                }
            }
        });
    }

    private void finishScan(MediaLocationScanResult result) {
        uiExecutor.execute(() -> {
            if (!disposed) {
                applyScanFinished(summaryText(result), result.status());
                queueRefreshCallback.accept(scanChangedMedia(result));
                loadLocations();
            }
        });
    }

    private void finishBatchScan(MediaLocationBatchScanResult result) {
        uiExecutor.execute(() -> {
            if (!disposed) {
                applyScanFinished(
                        batchSummaryText(result),
                        batchStatus(result)
                );
                queueRefreshCallback.accept(batchScanChangedMedia(result));
                loadLocations();
            }
        });
    }

    private void applyScanFinished(
            String message,
            model.MediaLocationScanStatus status) {

        scanRunning.set(false);
        cancelRequested.set(false);
        activeCancellationToken = null;
        currentPhase.set(status.name());
        currentFile.set("");
        currentFilePath.set("");
        if (status == model.MediaLocationScanStatus.COMPLETED
                || status == model.MediaLocationScanStatus
                .COMPLETED_WITH_ERRORS) {
            progress.set(1.0);
        }
        scanSummary.set(message);
    }

    private String summaryText(MediaLocationScanResult result) {
        return "Scan " + result.status().name()
                + ". Discovered: " + result.discoveredCount()
                + ", Processed: " + processedCount(result)
                + ", New: " + result.newCount()
                + ", Updated: " + result.updatedCount()
                + ", Unchanged: " + result.unchangedCount()
                + ", Missing: " + result.missingCount()
                + ", Failed: " + result.failedCount();
    }

    private String batchSummaryText(MediaLocationBatchScanResult result) {
        return "Scan " + batchStatus(result).name()
                + ". Locations: " + result.locationResults().size()
                + ", Discovered: " + batchDiscoveredCount(result)
                + ", Processed: " + batchProcessedCount(result)
                + ", New: " + result.newCount()
                + ", Updated: " + result.updatedCount()
                + ", Unchanged: " + result.unchangedCount()
                + ", Missing: " + batchMissingCount(result)
                + ", Failed: " + result.failedCount();
    }

    private model.MediaLocationScanStatus batchStatus(
            MediaLocationBatchScanResult result) {

        model.MediaLocationScanStatus status =
                model.MediaLocationScanStatus.COMPLETED;

        if (result.locationResults().stream().anyMatch(item ->
                item.status() == model.MediaLocationScanStatus.CANCELLED)) {
            status = model.MediaLocationScanStatus.CANCELLED;
        } else if (result.locationResults().stream().anyMatch(item ->
                item.status() == model.MediaLocationScanStatus.FAILED
                        || item.status() == model.MediaLocationScanStatus
                        .DIRECTORY_UNAVAILABLE)) {
            status = model.MediaLocationScanStatus.FAILED;
        } else if (result.failedCount() > 0) {
            status = model.MediaLocationScanStatus.COMPLETED_WITH_ERRORS;
        }

        return status;
    }

    private int processedCount(MediaLocationScanResult result) {
        return result.newCount()
                + result.updatedCount()
                + result.unchangedCount()
                + result.missingCount()
                + result.failedCount();
    }

    private int batchDiscoveredCount(MediaLocationBatchScanResult result) {
        return result.locationResults()
                .stream()
                .mapToInt(MediaLocationScanResult::discoveredCount)
                .sum();
    }

    private int batchProcessedCount(MediaLocationBatchScanResult result) {
        return result.locationResults()
                .stream()
                .mapToInt(this::processedCount)
                .sum();
    }

    private int batchMissingCount(MediaLocationBatchScanResult result) {
        return result.locationResults()
                .stream()
                .mapToInt(MediaLocationScanResult::missingCount)
                .sum();
    }

    private boolean scanChangedMedia(MediaLocationScanResult result) {
        return result.newCount() > 0 || result.updatedCount() > 0;
    }

    private boolean batchScanChangedMedia(MediaLocationBatchScanResult result) {
        return result.newCount() > 0 || result.updatedCount() > 0;
    }

    private void applyLocations(List<MediaLocation> loaded) {
        if (!disposed) {
            locations.setAll(loaded);
            loading.set(false);
        }
    }

    private void applyError(Exception exception) {
        if (!disposed) {
            loading.set(false);
            scanRunning.set(false);
            cancelRequested.set(false);
            errorMessage.set(exception.getMessage());
        }
    }
}

package ui.review;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import service.ReadyPageBatchMode;
import service.ReadyPageBatchOperations;
import service.ReadyPageBatchPageSnapshot;
import service.ReadyPageBatchPreflight;
import service.ReadyPageBatchResult;
import service.ReadyPageBatchRowResult;
import service.ReviewQueueItem;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class ReadyPageBatchViewModel {
    private final ReadyPageBatchOperations service;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final Runnable completionRefresh;
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ObjectProperty<ReadyPageBatchPreflight> pendingPreflight =
            new SimpleObjectProperty<>();
    private final ObjectProperty<ReadyPageBatchResult> lastResult =
            new SimpleObjectProperty<>();
    private final ObservableList<String> rowResults =
            FXCollections.observableArrayList();
    private final AtomicLong generation = new AtomicLong();

    private boolean disposed;

    public ReadyPageBatchViewModel(
            ReadyPageBatchOperations service,
            Executor backgroundExecutor,
            Executor uiExecutor,
            Runnable completionRefresh) {

        this.service = Objects.requireNonNull(
                service,
                "READY-page batch service must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
        this.completionRefresh = Objects.requireNonNull(
                completionRefresh,
                "Completion refresh must not be null"
        );
    }

    public boolean startPreflight(List<ReviewQueueItem> displayedRows) {
        Objects.requireNonNull(displayedRows, "Displayed rows must not be null");
        boolean started = false;

        if (!disposed && !busy.get()) {
            final long requestGeneration = generation.incrementAndGet();
            final ReadyPageBatchPageSnapshot snapshot =
                    ReadyPageBatchPageSnapshot.from(List.copyOf(displayedRows));
            busy.set(true);
            statusMessage.set("Checking current-page READY rows...");
            rowResults.clear();
            lastResult.set(null);
            pendingPreflight.set(null);

            try {
                backgroundExecutor.execute(() -> preflightInBackground(
                        requestGeneration,
                        snapshot
                ));
                started = true;
            } catch (RejectedExecutionException exception) {
                busy.set(false);
                statusMessage.set("Could not start the READY-page batch.");
            }
        }

        return started;
    }

    public void execute(ReadyPageBatchMode mode) {
        Objects.requireNonNull(mode, "Batch mode must not be null");
        final ReadyPageBatchPreflight preflight = pendingPreflight.get();

        if (!disposed && busy.get() && preflight != null) {
            final long requestGeneration = generation.get();
            pendingPreflight.set(null);
            statusMessage.set("Processing eligible READY rows...");

            try {
                backgroundExecutor.execute(() -> executeInBackground(
                        requestGeneration,
                        preflight,
                        mode
                ));
            } catch (RejectedExecutionException exception) {
                busy.set(false);
                statusMessage.set("Could not start READY-page processing.");
            }
        }
    }

    public void cancel() {
        if (pendingPreflight.get() != null) {
            generation.incrementAndGet();
            pendingPreflight.set(null);
            busy.set(false);
            statusMessage.set("Canceled. No changes were made.");
        }
    }

    public void dispose() {
        disposed = true;
        generation.incrementAndGet();
    }

    public BooleanProperty busyProperty() {
        return busy;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public ObjectProperty<ReadyPageBatchPreflight> pendingPreflightProperty() {
        return pendingPreflight;
    }

    public ObjectProperty<ReadyPageBatchResult> lastResultProperty() {
        return lastResult;
    }

    public ObservableList<String> rowResults() {
        return rowResults;
    }

    private void preflightInBackground(
            long requestGeneration,
            ReadyPageBatchPageSnapshot snapshot) {

        try {
            final ReadyPageBatchPreflight preflight = service.preflight(snapshot);
            uiExecutor.execute(() -> applyPreflight(
                    requestGeneration,
                    preflight
            ));
        } catch (RuntimeException exception) {
            uiExecutor.execute(() -> applyFailure(requestGeneration));
        }
    }

    private void applyPreflight(
            long requestGeneration,
            ReadyPageBatchPreflight preflight) {

        if (!disposed && requestGeneration == generation.get()) {
            if (preflight.eligibleCount() == 0) {
                final ReadyPageBatchResult result = new ReadyPageBatchResult(
                        preflight,
                        preflight.excludedRows()
                );
                applyResult(result, false);
            } else {
                statusMessage.set(preflightSummary(preflight));
                pendingPreflight.set(preflight);
            }
        }
    }

    private void executeInBackground(
            long requestGeneration,
            ReadyPageBatchPreflight preflight,
            ReadyPageBatchMode mode) {

        try {
            final ReadyPageBatchResult result = service.execute(preflight, mode);
            uiExecutor.execute(() -> {
                if (!disposed && requestGeneration == generation.get()) {
                    applyResult(result, true);
                }
            });
        } catch (RuntimeException exception) {
            uiExecutor.execute(() -> applyFailure(requestGeneration));
        }
    }

    private void applyFailure(long requestGeneration) {
        if (!disposed && requestGeneration == generation.get()) {
            pendingPreflight.set(null);
            busy.set(false);
            statusMessage.set("READY-page processing failed before completion.");
        }
    }

    private void applyResult(ReadyPageBatchResult result, boolean refresh) {
        lastResult.set(result);
        rowResults.setAll(result.rows().stream()
                .filter(row -> !row.successful())
                .map(this::rowText)
                .toList());
        statusMessage.set(resultSummary(result));
        busy.set(false);

        if (refresh) {
            completionRefresh.run();
        }
    }

    private String preflightSummary(ReadyPageBatchPreflight preflight) {
        return "Preflight: " + preflight.eligibleCount()
                + " of " + preflight.initialReadyCandidates()
                + " displayed READY rows remain eligible.";
    }

    private String resultSummary(ReadyPageBatchResult result) {
        return "READY-page result: displayed "
                + result.preflight().displayedRows()
                + ", initial READY "
                + result.preflight().initialReadyCandidates()
                + ", eligible after preflight "
                + result.preflight().eligibleCount()
                + ", created without rename "
                + result.createdWithoutRename()
                + ", created and renamed " + result.createdAndRenamed()
                + ", rename failures " + result.renameFailures()
                + ", skipped no longer READY "
                + result.skippedNoLongerReady()
                + ", skipped already assigned "
                + result.skippedAlreadyAssigned()
                + ", missing media " + result.missingMedia()
                + ", failed " + result.otherFailures() + ".";
    }

    private String rowText(ReadyPageBatchRowResult row) {
        final String detail = row.message().isBlank()
                ? ""
                : " — " + row.message();
        return row.path().getFileName() + " — " + row.outcome() + detail;
    }
}

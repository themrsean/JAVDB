package ui.review;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class ScanRefreshCoordinator {
    private static final String PENDING_MESSAGE =
            "New media scan results are available.";

    private final BooleanSupplier dirtySupplier;
    private final ReviewNavigationGuard navigationGuard;
    private final Runnable refreshAction;
    private final Runnable readOnlyRefreshAction;
    private final BooleanProperty pendingScanResults =
            new SimpleBooleanProperty(false);
    private final StringProperty pendingScanResultsMessage =
            new SimpleStringProperty("");

    public ScanRefreshCoordinator(
            BooleanSupplier dirtySupplier,
            BooleanSupplier discardConfirmation,
            Runnable refreshAction) {

        this(dirtySupplier, discardConfirmation, refreshAction, () -> { });
    }

    public ScanRefreshCoordinator(
            BooleanSupplier dirtySupplier,
            BooleanSupplier discardConfirmation,
            Runnable refreshAction,
            Runnable readOnlyRefreshAction) {

        this.dirtySupplier = Objects.requireNonNull(
                dirtySupplier,
                "Dirty supplier must not be null"
        );
        this.navigationGuard = new ReviewNavigationGuard(discardConfirmation);
        this.refreshAction = Objects.requireNonNull(
                refreshAction,
                "Refresh action must not be null"
        );
        this.readOnlyRefreshAction = Objects.requireNonNull(
                readOnlyRefreshAction,
                "Read-only refresh action must not be null"
        );
    }

    public ReadOnlyBooleanProperty pendingScanResultsProperty() {
        return pendingScanResults;
    }

    public ReadOnlyStringProperty pendingScanResultsMessageProperty() {
        return pendingScanResultsMessage;
    }

    public void requestScanRefresh(boolean dataChanged) {
        if (dataChanged) {
            readOnlyRefreshAction.run();
            if (dirtySupplier.getAsBoolean()) {
                pendingScanResults.set(true);
                pendingScanResultsMessage.set(PENDING_MESSAGE);
            } else {
                refreshAction.run();
                clearPending();
            }
        }
    }

    public void refreshPendingScanResults() {
        if (pendingScanResults.get()
                && navigationGuard.mayNavigateAway(
                        dirtySupplier.getAsBoolean()
                )) {
            refreshAction.run();
            clearPending();
        }
    }

    public void consumePendingAfterExternalRefresh() {
        clearPending();
    }

    private void clearPending() {
        pendingScanResults.set(false);
        pendingScanResultsMessage.set("");
    }
}

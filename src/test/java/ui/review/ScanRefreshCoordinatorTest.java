package ui.review;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScanRefreshCoordinatorTest {
    @Test
    @DisplayName("Clean editor refreshes immediately after changed scan")
    void cleanEditorRefreshesImmediatelyAfterChangedScan() {
        final MutableBoolean dirty = new MutableBoolean(false);
        final Counter refreshes = new Counter();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> true,
                refreshes::increment
        );

        coordinator.requestScanRefresh(true);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, refreshes.count),
                () -> Assertions.assertFalse(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Dirty editor records one pending refresh")
    void dirtyEditorRecordsOnePendingRefresh() {
        final MutableBoolean dirty = new MutableBoolean(true);
        final Counter refreshes = new Counter();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> true,
                refreshes::increment
        );

        coordinator.requestScanRefresh(true);
        coordinator.requestScanRefresh(true);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, refreshes.count),
                () -> Assertions.assertTrue(
                        coordinator.pendingScanResultsProperty().get()
                ),
                () -> Assertions.assertEquals(
                        "New media scan results are available.",
                        coordinator.pendingScanResultsMessageProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Keep editing leaves pending refresh and rows unchanged")
    void keepEditingLeavesPendingRefreshAndRowsUnchanged() {
        final MutableBoolean dirty = new MutableBoolean(true);
        final Counter refreshes = new Counter();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> false,
                refreshes::increment
        );
        coordinator.requestScanRefresh(true);

        coordinator.refreshPendingScanResults();

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, refreshes.count),
                () -> Assertions.assertTrue(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Discard and refresh consumes pending refresh")
    void discardAndRefreshConsumesPendingRefresh() {
        final MutableBoolean dirty = new MutableBoolean(true);
        final Counter refreshes = new Counter();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> true,
                refreshes::increment
        );
        coordinator.requestScanRefresh(true);

        coordinator.refreshPendingScanResults();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, refreshes.count),
                () -> Assertions.assertFalse(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Successful save refresh can consume pending scan refresh")
    void successfulSaveRefreshCanConsumePendingScanRefresh() {
        final MutableBoolean dirty = new MutableBoolean(true);
        final Counter refreshes = new Counter();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> true,
                refreshes::increment
        );
        coordinator.requestScanRefresh(true);

        coordinator.consumePendingAfterExternalRefresh();

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, refreshes.count),
                () -> Assertions.assertFalse(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Clean transition does not silently consume pending refresh")
    void cleanTransitionDoesNotSilentlyConsumePendingRefresh() {
        final MutableBoolean dirty = new MutableBoolean(true);
        final Counter refreshes = new Counter();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> true,
                refreshes::increment
        );
        coordinator.requestScanRefresh(true);

        dirty.setValue(false);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, refreshes.count),
                () -> Assertions.assertTrue(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    @Test
    @DisplayName("No-change scan does not request refresh")
    void noChangeScanDoesNotRequestRefresh() {
        final MutableBoolean dirty = new MutableBoolean(true);
        final Counter refreshes = new Counter();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> true,
                refreshes::increment
        );

        coordinator.requestScanRefresh(false);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, refreshes.count),
                () -> Assertions.assertFalse(
                        coordinator.pendingScanResultsProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Changed scan refreshes read-only library without discarding draft")
    void changedScanRefreshesLibraryWhileProtectingDirtyDraft() {
        final MutableBoolean dirty = new MutableBoolean(true);
        final Counter reviewRefreshes = new Counter();
        final Counter libraryRefreshes = new Counter();
        final ScanRefreshCoordinator coordinator = new ScanRefreshCoordinator(
                dirty::value,
                () -> false,
                reviewRefreshes::increment,
                libraryRefreshes::increment
        );

        coordinator.requestScanRefresh(true);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, reviewRefreshes.count),
                () -> Assertions.assertEquals(1, libraryRefreshes.count),
                () -> Assertions.assertTrue(
                        coordinator.pendingScanResultsProperty().get())
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

    private static final class Counter {
        private int count;

        private void increment() {
            count++;
        }
    }
}

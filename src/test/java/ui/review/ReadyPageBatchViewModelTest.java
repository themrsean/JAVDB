package ui.review;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.FilenameMatchStatus;
import service.ReadyPageBatchMode;
import service.ReadyPageBatchOperations;
import service.ReadyPageBatchPageSnapshot;
import service.ReadyPageBatchPreflight;
import service.ReadyPageBatchResult;
import service.ReviewQueueItem;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

class ReadyPageBatchViewModelTest {
    private static final UUID MEDIA_ID = UUID.fromString(
            "11111111-3030-1111-3030-111111111111"
    );

    @Test
    @DisplayName("Preflight and execution are asynchronous and refresh once")
    void preflightAndExecutionAreAsynchronousAndRefreshOnce() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final AtomicInteger refreshes = new AtomicInteger();
        final FakeOperations operations = new FakeOperations();
        final ReadyPageBatchViewModel viewModel = new ReadyPageBatchViewModel(
                operations, background, ui, refreshes::incrementAndGet
        );

        Assertions.assertTrue(viewModel.startPreflight(List.of(readyRow())));
        Assertions.assertTrue(viewModel.busyProperty().get());
        background.runNext();
        ui.runNext();
        Assertions.assertNotNull(
                viewModel.pendingPreflightProperty().get()
        );

        viewModel.execute(ReadyPageBatchMode.CREATE_WITHOUT_RENAMING);
        background.runNext();
        ui.runNext();

        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.busyProperty().get()),
                () -> Assertions.assertEquals(1, refreshes.get()),
                () -> Assertions.assertNotNull(
                        viewModel.lastResultProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Concurrent start is rejected and cancel performs no execution")
    void concurrentStartIsRejectedAndCancelPerformsNoExecution() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final FakeOperations operations = new FakeOperations();
        final ReadyPageBatchViewModel viewModel = new ReadyPageBatchViewModel(
                operations, background, ui, () -> { }
        );

        Assertions.assertTrue(viewModel.startPreflight(List.of(readyRow())));
        Assertions.assertFalse(viewModel.startPreflight(List.of(readyRow())));
        background.runNext();
        ui.runNext();
        viewModel.cancel();

        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.busyProperty().get()),
                () -> Assertions.assertEquals(0, operations.executions),
                () -> Assertions.assertTrue(
                        viewModel.statusMessageProperty().get()
                                .contains("No changes")
                )
        );
    }

    @Test
    @DisplayName("Background failure clears busy state with concise error")
    void backgroundFailureClearsBusyStateWithConciseError() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final ReadyPageBatchViewModel viewModel = new ReadyPageBatchViewModel(
                new FailingOperations(), background, ui, () -> { }
        );

        viewModel.startPreflight(List.of(readyRow()));
        background.runNext();
        ui.runNext();

        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.busyProperty().get()),
                () -> Assertions.assertTrue(
                        viewModel.statusMessageProperty().get()
                                .contains("failed")
                )
        );
    }

    @Test
    @DisplayName("Disposed view model suppresses stale preflight result")
    void disposedViewModelSuppressesStalePreflightResult() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final ReadyPageBatchViewModel viewModel = new ReadyPageBatchViewModel(
                new FakeOperations(), background, ui, () -> { }
        );

        viewModel.startPreflight(List.of(readyRow()));
        background.runNext();
        viewModel.dispose();
        ui.runNext();

        Assertions.assertNull(viewModel.pendingPreflightProperty().get());
    }

    private ReviewQueueItem readyRow() {
        return new ReviewQueueItem(
                MEDIA_ID, Path.of("ready.mp4"), "ready.mp4", "",
                FilenameMatchStatus.READY, "Title", "", "", "", "",
                "", "", "", "", "", 0
        );
    }

    private static final class FakeOperations
            implements ReadyPageBatchOperations {
        private int executions;

        @Override
        public ReadyPageBatchPreflight preflight(
                ReadyPageBatchPageSnapshot snapshot) {

            return new ReadyPageBatchPreflight(
                    snapshot.displayedRows(),
                    snapshot.initialReadyCandidates(),
                    snapshot.readyCandidates(),
                    List.of()
            );
        }

        @Override
        public ReadyPageBatchResult execute(
                ReadyPageBatchPreflight preflight,
                ReadyPageBatchMode mode) {

            executions++;
            return new ReadyPageBatchResult(preflight, List.of());
        }
    }

    private static final class FailingOperations
            implements ReadyPageBatchOperations {
        @Override
        public ReadyPageBatchPreflight preflight(
                ReadyPageBatchPageSnapshot snapshot) {

            throw new IllegalStateException("raw database details");
        }

        @Override
        public ReadyPageBatchResult execute(
                ReadyPageBatchPreflight preflight,
                ReadyPageBatchMode mode) {

            throw new UnsupportedOperationException();
        }
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            tasks.removeFirst().run();
        }
    }
}

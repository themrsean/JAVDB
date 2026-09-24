package ui.library;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.MediaLibraryFilter;
import service.MediaLibraryDataSource;
import service.MediaLibraryDetails;
import service.MediaLibraryPage;
import service.MediaLibraryRow;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

class MediaLibraryViewModelTest {
    private static final UUID FIRST = UUID.fromString(
            "20000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");

    @Test
    @DisplayName("Page and detail loading both run asynchronously")
    void loadsPageAndDetailsAsynchronously() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final MediaLibraryViewModel viewModel = new MediaLibraryViewModel(
                source(page(FIRST), details(FIRST)), background, ui);

        viewModel.load();
        Assertions.assertTrue(viewModel.loadingProperty().get());
        background.runNext();
        ui.runNext();
        Assertions.assertEquals(FIRST,
                viewModel.selectedRow().get().mediaId());
        Assertions.assertTrue(viewModel.detailLoadingProperty().get());
        background.runNext();
        ui.runNext();

        Assertions.assertAll(
                () -> Assertions.assertFalse(
                        viewModel.detailLoadingProperty().get()),
                () -> Assertions.assertEquals(FIRST,
                        viewModel.selectedDetails().get().mediaId())
        );
    }

    @Test
    @DisplayName("Superseded page results are ignored")
    void ignoresStalePageResults() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final ArrayDeque<MediaLibraryPage> pages = new ArrayDeque<>(
                List.of(page(FIRST), page(SECOND)));
        final MediaLibraryDataSource source = new MediaLibraryDataSource() {
            @Override
            public MediaLibraryPage loadPage(MediaLibraryFilter filter) {
                return pages.removeFirst();
            }

            @Override
            public MediaLibraryDetails loadDetails(UUID mediaId) {
                return details(mediaId);
            }
        };
        final MediaLibraryViewModel viewModel = new MediaLibraryViewModel(
                source, background, ui);

        viewModel.load();
        viewModel.load();
        background.runNext();
        background.runNext();
        ui.runNext();
        ui.runNext();

        Assertions.assertEquals(SECOND,
                viewModel.rows().getFirst().mediaId());
    }

    @Test
    @DisplayName("Manual refresh reloads and empty states distinguish filters")
    void refreshesAndReportsEmptyStates() {
        final Counter counter = new Counter();
        final MediaLibraryDataSource source = new MediaLibraryDataSource() {
            @Override
            public MediaLibraryPage loadPage(MediaLibraryFilter filter) {
                counter.value++;
                return new MediaLibraryPage(filter, List.of(), false);
            }

            @Override
            public MediaLibraryDetails loadDetails(UUID mediaId) {
                throw new AssertionError();
            }
        };
        final MediaLibraryViewModel viewModel = new MediaLibraryViewModel(
                source, Runnable::run, Runnable::run);

        viewModel.load();
        Assertions.assertEquals("No media records have been scanned.",
                viewModel.emptyMessageProperty().get());
        viewModel.containsProperty().set("missing");
        viewModel.applyFilters();

        Assertions.assertAll(
                () -> Assertions.assertEquals(2, counter.value),
                () -> Assertions.assertEquals(
                        "No media records match the current filters.",
                        viewModel.emptyMessageProperty().get())
        );
    }

    @Test
    @DisplayName("Database errors expose a safe message and clear rows")
    void handlesDatabaseErrors() {
        final MediaLibraryViewModel viewModel = new MediaLibraryViewModel(
                new MediaLibraryDataSource() {
                    @Override
                    public MediaLibraryPage loadPage(MediaLibraryFilter filter)
                            throws SQLException {
                        throw new SQLException("SELECT secret");
                    }

                    @Override
                    public MediaLibraryDetails loadDetails(UUID mediaId) {
                        throw new AssertionError();
                    }
                }, Runnable::run, Runnable::run);

        viewModel.load();

        Assertions.assertAll(
                () -> Assertions.assertTrue(viewModel.rows().isEmpty()),
                () -> Assertions.assertEquals(
                        "Unable to load the media library.",
                        viewModel.errorMessageProperty().get()),
                () -> Assertions.assertFalse(viewModel.loadingProperty().get())
        );
    }

    private MediaLibraryDataSource source(
            MediaLibraryPage page, MediaLibraryDetails details) {
        return new MediaLibraryDataSource() {
            @Override
            public MediaLibraryPage loadPage(MediaLibraryFilter filter) {
                return page;
            }

            @Override
            public MediaLibraryDetails loadDetails(UUID mediaId) {
                return details;
            }
        };
    }

    private MediaLibraryPage page(UUID id) {
        return new MediaLibraryPage(MediaLibraryFilter.firstPage(),
                List.of(new MediaLibraryRow(id, Path.of("/tmp/a.mp4"),
                        "a.mp4", "/tmp", "640x480", "0:00:01",
                        "1 B", "2026-01-01", "Unassigned")), false);
    }

    private MediaLibraryDetails details(UUID id) {
        return new MediaLibraryDetails(id, Path.of("/tmp/a.mp4"), false,
                "1 B", "2026-01-01", "640x480", "0:00:01", "",
                List.of(), List.of(), media.FilenameParseStatus.INVALID,
                service.FilenameMatchStatus.INVALID_FILENAME,
                "No interpretation available", List.of());
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.addLast(command);
        }

        private void runNext() {
            tasks.removeFirst().run();
        }
    }

    private static final class Counter {
        private int value;
    }
}

package ui.review;

import javafx.collections.ObservableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.CanonicalRenameDisplay;
import service.EditableSceneReviewDraft;
import service.FilenameMatchStatus;
import service.ReviewDetails;
import service.ReviewMatchStatusFilter;
import service.ReviewQueueFilter;
import service.ReviewQueueItem;
import service.ReviewQueuePage;
import service.SceneReviewDraft;
import service.SceneReviewMode;
import model.VerificationStatus;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

class ReviewQueueViewModelTest {
    private static final UUID FIRST_ID =
            UUID.fromString("11111111-abcd-1111-abcd-111111111111");
    private static final UUID SECOND_ID =
            UUID.fromString("22222222-abcd-2222-abcd-222222222222");
    private static final int PAGE_SIZE = 2;

    @Test
    @DisplayName("Initial state is empty and ready")
    void initialStateIsEmptyAndReady() {
        final ReviewQueueViewModel viewModel = viewModel(
                page(List.of(), ReviewQueueFilter.firstPage(), 0)
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(viewModel.rows().isEmpty()),
                () -> Assertions.assertFalse(viewModel.loadingProperty().get()),
                () -> Assertions.assertTrue(viewModel.errorMessage().isBlank()),
                () -> Assertions.assertFalse(viewModel.previousAvailable().get())
        );
    }

    @Test
    @DisplayName("Initial load runs in background and populates rows")
    void initialLoadRunsInBackgroundAndPopulatesRows() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final ReviewQueueViewModel viewModel = viewModel(
                new FakeService(page(
                        List.of(item(FIRST_ID), item(SECOND_ID)),
                        ReviewQueueFilter.firstPage(),
                        PAGE_SIZE
                )),
                background,
                ui
        );

        viewModel.load();

        Assertions.assertTrue(viewModel.loadingProperty().get());
        background.runNext();
        Assertions.assertTrue(viewModel.loadingProperty().get());
        ui.runNext();

        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.loadingProperty().get()),
                () -> Assertions.assertEquals(
                        List.of(FIRST_ID, SECOND_ID),
                        ids(viewModel.rows())
                ),
                () -> Assertions.assertEquals(
                        FIRST_ID,
                        viewModel.selectedRow().get().mediaId()
                ),
                () -> Assertions.assertEquals(
                        FIRST_ID,
                        viewModel.selectedDetails().get().mediaId()
                )
        );
    }

    @Test
    @DisplayName("Initial and restored selection load matching editor draft once")
    void initialAndRestoredSelectionLoadMatchingEditorDraftOnce() {
        final ReviewQueueViewModel viewModel = viewModel(new SequencedService(
                rawPage("Raw 20", LocalDate.of(2014, 7, 12)),
                rawPage("Raw 21", LocalDate.of(2014, 7, 13))));
        final SceneReviewEditorViewModel editor = new SceneReviewEditorViewModel(
                request -> null, rawDraftLoader(), Runnable::run, Runnable::run);
        final java.util.concurrent.atomic.AtomicInteger draftLoads =
                new java.util.concurrent.atomic.AtomicInteger();
        viewModel.selectedDetails().addListener((observable, oldValue, details) -> {
            if (details != null) {
                draftLoads.incrementAndGet();
                editor.loadUnassignedDetails(details);
            }
        });

        viewModel.load();

        Assertions.assertAll(
                () -> Assertions.assertEquals(FIRST_ID,
                        viewModel.selectedRow().get().mediaId()),
                () -> Assertions.assertEquals(FIRST_ID,
                        viewModel.selectedDetails().get().mediaId()),
                () -> Assertions.assertEquals(FIRST_ID,
                        editor.currentDraft().draft().mediaFileIds().getFirst()),
                () -> Assertions.assertEquals("Raw 20", editor.titleProperty().get()),
                () -> Assertions.assertEquals("2014-07-12",
                        editor.releaseDateTextProperty().get()),
                () -> Assertions.assertEquals(1, draftLoads.get())
        );

        viewModel.load();

        Assertions.assertAll(
                () -> Assertions.assertEquals(FIRST_ID,
                        viewModel.selectedRow().get().mediaId()),
                () -> Assertions.assertEquals("Raw 21", editor.titleProperty().get()),
                () -> Assertions.assertEquals("2014-07-13",
                        editor.releaseDateTextProperty().get()),
                () -> Assertions.assertEquals(2, draftLoads.get())
        );
    }

    @Test
    @DisplayName("Selection exposes details and clearing selection clears details")
    void selectionExposesDetailsAndClearingSelectionClearsDetails() {
        final ReviewQueueViewModel viewModel = viewModel(
                page(
                        List.of(item(FIRST_ID), item(SECOND_ID)),
                        ReviewQueueFilter.firstPage(),
                        PAGE_SIZE
                )
        );
        viewModel.load();

        viewModel.selectedRow().set(viewModel.rows().get(1));

        Assertions.assertEquals(
                SECOND_ID,
                viewModel.selectedDetails().get().mediaId()
        );

        viewModel.selectedRow().set(null);

        Assertions.assertNull(viewModel.selectedDetails().get());
    }

    @Test
    @DisplayName("Errors clear loading state and expose concise message")
    void errorsClearLoadingStateAndExposeConciseMessage() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final ReviewQueueViewModel viewModel = viewModel(
                filter -> {
                    throw new java.sql.SQLException("database down");
                },
                background,
                ui
        );

        viewModel.load();
        background.runNext();
        ui.runNext();

        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.loadingProperty().get()),
                () -> Assertions.assertTrue(
                        viewModel.errorMessage().contains("database down")
                ),
                () -> Assertions.assertTrue(viewModel.rows().isEmpty())
        );
    }

    @Test
    @DisplayName("Invalid numeric filters fail without querying")
    void invalidNumericFiltersFailWithoutQuerying() {
        final ReviewQueueViewModel viewModel =
                viewModel(new CapturingService());
        viewModel.widthFilter().set("-1");

        Assertions.assertThrows(
                IllegalArgumentException.class,
                viewModel::applyFilters
        );
    }

    @Test
    @DisplayName("Applying filters resets offset")
    void applyingFiltersResetsOffset() {
        final CapturingService service = new CapturingService();
        final ReviewQueueViewModel viewModel = viewModel(service);
        viewModel.pageSize().set(PAGE_SIZE);
        viewModel.nextPage();
        viewModel.filenameFilter().set("needle");

        viewModel.applyFilters();

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, viewModel.offset().get()),
                () -> Assertions.assertEquals("needle",
                        service.lastFilter().contains())
        );
    }

    @Test
    @DisplayName("Previous page never goes below zero")
    void previousPageNeverGoesBelowZero() {
        final ReviewQueueViewModel viewModel = viewModel(
                page(List.of(), ReviewQueueFilter.firstPage(), 0)
        );

        viewModel.previousPage();

        Assertions.assertEquals(0, viewModel.offset().get());
    }

    @Test
    @DisplayName("Next availability uses unfiltered base page size")
    void nextAvailabilityUsesUnfilteredBasePageSize() {
        final ReviewQueueViewModel viewModel = viewModel(
                page(
                        List.of(item(FIRST_ID)),
                        new ReviewQueueFilter(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                PAGE_SIZE,
                                0,
                                ReviewMatchStatusFilter.READY
                        ),
                        PAGE_SIZE
                )
        );
        viewModel.pageSize().set(PAGE_SIZE);
        viewModel.statusFilter().set(ReviewMatchStatusFilter.READY);
        viewModel.load();

        Assertions.assertTrue(viewModel.nextAvailable().get());
    }

    @Test
    @DisplayName("Page-size change resets offset")
    void pageSizeChangeResetsOffset() {
        final ReviewQueueViewModel viewModel = viewModel(
                page(List.of(), ReviewQueueFilter.firstPage(), 0)
        );
        viewModel.offset().set(PAGE_SIZE);

        viewModel.pageSize().set(5);

        Assertions.assertEquals(0, viewModel.offset().get());
    }

    @Test
    @DisplayName("Selection is preserved when row remains visible")
    void selectionIsPreservedWhenRowRemainsVisible() {
        final FakeService service = new FakeService(
                page(
                        List.of(item(FIRST_ID), item(SECOND_ID)),
                        ReviewQueueFilter.firstPage(),
                        PAGE_SIZE
                )
        );
        final ReviewQueueViewModel viewModel = viewModel(service);
        viewModel.load();
        viewModel.selectedRow().set(viewModel.rows().get(1));

        service.page = page(
                List.of(item(SECOND_ID)),
                ReviewQueueFilter.firstPage(),
                1
        );
        viewModel.load();

        Assertions.assertEquals(
                SECOND_ID,
                viewModel.selectedRow().get().mediaId()
        );
    }

    @Test
    @DisplayName("Refresh preserves filters page size offset and selection")
    void refreshPreservesFiltersPageSizeOffsetAndSelection() {
        final CapturingService service = new CapturingService();
        final ReviewQueueViewModel viewModel = viewModel(service);
        viewModel.filenameFilter().set("needle");
        viewModel.directoryFilter().set("/tmp");
        viewModel.widthFilter().set("1920");
        viewModel.heightFilter().set("1080");
        viewModel.minWidthFilter().set("1280");
        viewModel.minHeightFilter().set("720");
        viewModel.statusFilter().set(ReviewMatchStatusFilter.READY);
        viewModel.pageSize().set(PAGE_SIZE);
        viewModel.offset().set(PAGE_SIZE);
        service.page = page(
                List.of(item(FIRST_ID), item(SECOND_ID)),
                ReviewQueueFilter.firstPage(),
                PAGE_SIZE
        );
        viewModel.load();
        viewModel.selectedRow().set(viewModel.rows().get(1));

        service.page = page(
                List.of(item(SECOND_ID)),
                ReviewQueueFilter.firstPage(),
                1
        );
        viewModel.load();

        Assertions.assertAll(
                () -> Assertions.assertEquals("needle",
                        service.lastFilter().contains()),
                () -> Assertions.assertEquals(Path.of("/tmp"),
                        service.lastFilter().directory()),
                () -> Assertions.assertEquals(1920,
                        service.lastFilter().width()),
                () -> Assertions.assertEquals(1080,
                        service.lastFilter().height()),
                () -> Assertions.assertEquals(1280,
                        service.lastFilter().minWidth()),
                () -> Assertions.assertEquals(720,
                        service.lastFilter().minHeight()),
                () -> Assertions.assertEquals(ReviewMatchStatusFilter.READY,
                        service.lastFilter().statusFilter()),
                () -> Assertions.assertEquals(PAGE_SIZE,
                        service.lastFilter().limit()),
                () -> Assertions.assertEquals(PAGE_SIZE,
                        service.lastFilter().offset()),
                () -> Assertions.assertEquals(SECOND_ID,
                        viewModel.selectedRow().get().mediaId())
        );
    }

    @Test
    @DisplayName("Stale request results are ignored")
    void staleRequestResultsAreIgnored() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final SequencedService service = new SequencedService(
                page(List.of(item(FIRST_ID)), ReviewQueueFilter.firstPage(), 1),
                page(List.of(item(SECOND_ID)), ReviewQueueFilter.firstPage(), 1)
        );
        final ReviewQueueViewModel viewModel =
                viewModel(service, background, ui);

        viewModel.load();
        viewModel.load();
        background.runNext();
        background.runNext();
        ui.runNext();
        ui.runNext();

        Assertions.assertEquals(
                List.of(SECOND_ID),
                ids(viewModel.rows())
        );
    }

    @Test
    @DisplayName("Dispose prevents later work")
    void disposePreventsLaterWork() {
        final QueuedExecutor background = new QueuedExecutor();
        final ReviewQueueViewModel viewModel = viewModel(
                page(List.of(item(FIRST_ID)), ReviewQueueFilter.firstPage(), 1),
                background,
                new QueuedExecutor()
        );

        viewModel.dispose();
        viewModel.load();

        Assertions.assertEquals(0, background.pendingCount());
    }

    private ReviewQueueViewModel viewModel(ReviewQueuePage page) {
        return viewModel(new FakeService(page));
    }

    private ReviewQueueViewModel viewModel(ReviewQueueDataSource service) {
        return viewModel(service, Runnable::run, Runnable::run);
    }

    private ReviewQueueViewModel viewModel(
            ReviewQueuePage page,
            QueuedExecutor background,
            QueuedExecutor ui) {

        return viewModel(new FakeService(page), background, ui);
    }

    private ReviewQueueViewModel viewModel(
            ReviewQueueDataSource service,
            java.util.concurrent.Executor background,
            java.util.concurrent.Executor ui) {

        return new ReviewQueueViewModel(service, background, ui);
    }

    private ReviewQueuePage page(
            List<ReviewQueueItem> items,
            ReviewQueueFilter filter,
            int basePageSize) {

        return new ReviewQueuePage(
                filter,
                items,
                items.stream().map(item -> detail(item.mediaId())).toList(),
                basePageSize
        );
    }

    private ReviewQueueItem item(UUID id) {
        return new ReviewQueueItem(
                id,
                Path.of("/tmp", id + ".mp4"),
                id + ".mp4",
                "/tmp",
                FilenameMatchStatus.READY,
                "Title",
                "Publisher",
                "EXPLICIT_PRIMARY_NAME",
                "",
                "",
                "",
                "",
                "Performer",
                "1920x1080",
                "1000",
                0
        );
    }

    private ReviewDetails detail(UUID id) {
        return new ReviewDetails(
                id,
                Path.of("/tmp", id + ".mp4"),
                id + ".mp4",
                "/tmp",
                1L,
                2L,
                1920,
                1080,
                "1000",
                "",
                media.FilenameParseStatus.VALID,
                null,
                List.of(),
                "Title",
                "",
                "",
                "",
                List.of(),
                List.of("Performer"),
                List.of(),
                "",
                "",
                "",
                "",
                "",
                "",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                FilenameMatchStatus.READY,
                new CanonicalRenameDisplay(
                        "READY",
                        "old.mp4",
                        "new.mp4",
                        Path.of("/tmp/new.mp4"),
                        false,
                        false,
                        false,
                        List.of(),
                        ""
                )
        );
    }

    private ReviewQueuePage rawPage(String title, LocalDate releaseDate) {
        final Path path = Path.of(
                "/media/(14.07.12) EvilAngel - Raw 20 - Abella Danger.mp4");
        final ReviewQueueItem item = new ReviewQueueItem(FIRST_ID, path,
                path.getFileName().toString(), "/media", FilenameMatchStatus.UNRESOLVED,
                title, "", "", "", "", "", "", "", "", "00:01", 0);
        final ReviewDetails details = new ReviewDetails(FIRST_ID, path,
                item.filename(), item.directory(), 1L, 1L, 1920, 1080, "00:01", "",
                media.FilenameParseStatus.VALID, releaseDate, List.of("EvilAngel"),
                title, "", "", "", List.of("Abella Danger"), List.of(),
                List.of("Abella Danger"), "", "", "", "", "", "", null,
                List.of(), List.of("EvilAngel"), List.of(), List.of(), List.of(),
                FilenameMatchStatus.UNRESOLVED, new CanonicalRenameDisplay(
                        "REVIEW_REQUIRED", item.filename(), "", path, false, false,
                        false, List.of(), "Filename interpretation is not ready."));
        return new ReviewQueuePage(ReviewQueueFilter.firstPage(), List.of(item),
                List.of(details), 1);
    }

    private SceneReviewDraftLoader rawDraftLoader() {
        return new SceneReviewDraftLoader() {
            @Override
            public EditableSceneReviewDraft fromReviewDetails(ReviewDetails details) {
                return new EditableSceneReviewDraft(new SceneReviewDraft(
                        SceneReviewMode.CREATE_FROM_MEDIA, null, List.of(details.mediaId()),
                        details.proposedTitle(), details.releaseDate(), details.code(),
                        details.season(), details.episode(), null, null, null, null,
                        List.of(), VerificationStatus.VERIFIED, List.of()), details.path(), null,
                        details.matchStatus(), List.of(), details.performerCandidates(),
                        details.unmatchedPerformers(), null, List.of());
            }

            @Override
            public EditableSceneReviewDraft fromExistingScene(UUID sceneId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public EditableSceneReviewDraft applyInterpretation(
                    EditableSceneReviewDraft editable,
                    service.FilenameInterpretation interpretation) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private List<UUID> ids(ObservableList<ReviewQueueItem> rows) {
        return rows.stream().map(ReviewQueueItem::mediaId).toList();
    }

    private static final class FakeService implements ReviewQueueDataSource {
        private ReviewQueuePage page;

        private FakeService(ReviewQueuePage page) {
            this.page = page;
        }

        @Override
        public ReviewQueuePage loadPage(ReviewQueueFilter filter) {
            return page;
        }
    }

    private static final class CapturingService
            implements ReviewQueueDataSource {
        private ReviewQueueFilter lastFilter = ReviewQueueFilter.firstPage();
        private ReviewQueuePage page =
                new ReviewQueuePage(
                        ReviewQueueFilter.firstPage(),
                        List.of(),
                        List.of(),
                        0
                );

        @Override
        public ReviewQueuePage loadPage(ReviewQueueFilter filter) {
            lastFilter = filter;
            return new ReviewQueuePage(
                    filter,
                    page.items(),
                    page.details(),
                    page.basePageSize()
            );
        }

        private ReviewQueueFilter lastFilter() {
            return lastFilter;
        }
    }

    private static final class SequencedService
            implements ReviewQueueDataSource {
        private final ArrayDeque<ReviewQueuePage> pages = new ArrayDeque<>();

        private SequencedService(ReviewQueuePage first, ReviewQueuePage second) {
            pages.add(first);
            pages.add(second);
        }

        @Override
        public ReviewQueuePage loadPage(ReviewQueueFilter filter) {
            return pages.removeFirst();
        }
    }

    private static final class QueuedExecutor
            implements java.util.concurrent.Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private void runNext() {
            tasks.removeFirst().run();
        }

        private int pendingCount() {
            return tasks.size();
        }
    }
}

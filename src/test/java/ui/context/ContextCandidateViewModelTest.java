package ui.context;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.ContextCandidate;
import service.ContextCandidateSource;
import service.ContextCandidateStatus;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

class ContextCandidateViewModelTest {
    @Test
    void loadingAndStaleResultsAreHandledOffTheUiExecutor() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final ContextCandidateViewModel viewModel = new ContextCandidateViewModel(
                new SequencedSource(row("first"), row("second")), background, ui);
        viewModel.load();
        viewModel.load();
        Assertions.assertTrue(viewModel.loadingProperty().get());
        background.runNext(); background.runNext(); ui.runNext(); ui.runNext();
        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.loadingProperty().get()),
                () -> Assertions.assertEquals("second", viewModel.candidates().getFirst().text())
        );
    }

    @Test
    void loadErrorIsConcise() {
        final ContextCandidateViewModel viewModel = new ContextCandidateViewModel(
                () -> { throw new SQLException("database unavailable"); },
                Runnable::run, Runnable::run);
        viewModel.load();
        Assertions.assertEquals("Unable to load context candidates.",
                viewModel.errorMessageProperty().get());
    }

    @Test
    void textAndAttentionFiltersUseOnlyLoadedCandidates() {
        final ContextCandidate unresolved = row("Alpha Context");
        final ContextCandidate resolved = new ContextCandidate("Beta Context", 1,
                Map.of(1, 1), Map.of(1, 1),
                ContextCandidateStatus.PUBLISHER_MATCH, List.of(), List.of());
        Assertions.assertAll(
                () -> Assertions.assertTrue(ContextCandidateController.matchesFilter(
                        unresolved, "alpha", true)),
                () -> Assertions.assertFalse(ContextCandidateController.matchesFilter(
                        resolved, "beta", true)),
                () -> Assertions.assertTrue(ContextCandidateController.matchesFilter(
                        resolved, "beta", false)),
                () -> Assertions.assertFalse(ContextCandidateController.matchesFilter(
                        unresolved, "beta", false))
        );
    }

    @Test
    void publisherActionsAreAvailableOnlyForUnresolvedCandidates() {
        Assertions.assertAll(
                () -> Assertions.assertTrue(ContextCandidateController.canResolveCandidate(
                        row("unresolved"))),
                () -> Assertions.assertFalse(ContextCandidateController.canResolveCandidate(
                        new ContextCandidate("publisher", 1, Map.of(), Map.of(),
                                ContextCandidateStatus.PUBLISHER_MATCH,
                                List.of(), List.of()))),
                () -> Assertions.assertFalse(ContextCandidateController.canResolveCandidate(
                        new ContextCandidate("multiple", 1, Map.of(), Map.of(),
                                ContextCandidateStatus.MULTIPLE_ROLE_MATCHES,
                                List.of(), List.of())))
        );
    }

    @Test
    void seriesActionsAreAvailableOnlyForUnresolvedCandidates() {
        Assertions.assertAll(
                () -> Assertions.assertTrue(ContextCandidateController.canResolveCandidate(
                        row("unresolved"))),
                () -> Assertions.assertFalse(ContextCandidateController.canResolveCandidate(
                        new ContextCandidate("series", 1, Map.of(), Map.of(),
                                ContextCandidateStatus.SERIES_MATCH,
                                List.of(), List.of()))),
                () -> Assertions.assertFalse(ContextCandidateController.canResolveCandidate(
                        new ContextCandidate("multiple", 1, Map.of(), Map.of(),
                                ContextCandidateStatus.MULTIPLE_ROLE_MATCHES,
                                List.of(), List.of())))
        );
    }

    @Test
    void publisherCreationRefreshesCandidatesAndCatalogConsumers() {
        final AtomicInteger loads = new AtomicInteger();
        final AtomicInteger catalogRefreshes = new AtomicInteger();
        final ContextCandidateViewModel viewModel = new ContextCandidateViewModel(
                () -> { loads.incrementAndGet(); return List.of(); }, null,
                Runnable::run, Runnable::run, catalogRefreshes::incrementAndGet);

        viewModel.publisherCreated();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, catalogRefreshes.get()),
                () -> Assertions.assertEquals(1, loads.get()),
                () -> Assertions.assertEquals("Publisher created.",
                        viewModel.resultMessageProperty().get())
        );
    }

    @Test
    void seriesCreationRefreshesCandidatesAndCatalogConsumers() {
        final AtomicInteger loads = new AtomicInteger();
        final AtomicInteger catalogRefreshes = new AtomicInteger();
        final ContextCandidateViewModel viewModel = new ContextCandidateViewModel(
                () -> { loads.incrementAndGet(); return List.of(); }, null,
                Runnable::run, Runnable::run, catalogRefreshes::incrementAndGet);

        viewModel.seriesCreated();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, catalogRefreshes.get()),
                () -> Assertions.assertEquals(1, loads.get()),
                () -> Assertions.assertEquals("Series created.",
                        viewModel.resultMessageProperty().get())
        );
    }

    @Test
    void movieCreationRefreshesCandidatesAndCatalogConsumers() {
        final AtomicInteger loads = new AtomicInteger();
        final AtomicInteger catalogRefreshes = new AtomicInteger();
        final ContextCandidateViewModel viewModel = new ContextCandidateViewModel(
                () -> { loads.incrementAndGet(); return List.of(); }, null,
                Runnable::run, Runnable::run, catalogRefreshes::incrementAndGet);

        viewModel.movieCreated();

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, catalogRefreshes.get()),
                () -> Assertions.assertEquals(1, loads.get()),
                () -> Assertions.assertEquals("Movie created.",
                        viewModel.resultMessageProperty().get())
        );
    }

    private static ContextCandidate row(String text) {
        return new ContextCandidate(text, 1, Map.of(1, 1), Map.of(1, 1),
                ContextCandidateStatus.UNRESOLVED, List.of(), List.of(Path.of(text)));
    }

    private static final class SequencedSource implements ContextCandidateSource {
        private final ArrayDeque<List<ContextCandidate>> rows = new ArrayDeque<>();
        private SequencedSource(ContextCandidate... candidates) {
            for (ContextCandidate candidate : candidates) rows.add(List.of(candidate));
        }
        @Override public List<ContextCandidate> loadCandidates() { return rows.removeFirst(); }
    }

    private static final class QueuedExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();
        @Override public void execute(Runnable command) { work.add(command); }
        private void runNext() { work.removeFirst().run(); }
    }
}

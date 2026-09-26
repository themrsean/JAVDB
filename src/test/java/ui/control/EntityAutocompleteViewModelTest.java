package ui.control;

import javafx.collections.ObservableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import repository.EntitySuggestion;
import repository.MatchField;
import repository.MatchRank;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

class EntityAutocompleteViewModelTest {
    private static final UUID FIRST_ID =
            UUID.fromString("11111111-cccc-1111-cccc-111111111111");
    private static final UUID SECOND_ID =
            UUID.fromString("22222222-cccc-2222-cccc-222222222222");
    private static final int LIMIT = 5;

    @Test
    @DisplayName("Search populates suggestions outside caller flow")
    void searchPopulatesSuggestionsOutsideCallerFlow() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final EntityAutocompleteViewModel viewModel =
                viewModel(new FakeProvider(List.of(suggestion(FIRST_ID))),
                        background,
                        ui);
        viewModel.searchTextProperty().set("ali");

        viewModel.search();

        Assertions.assertTrue(viewModel.loadingProperty().get());
        background.runNext();
        ui.runNext();

        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.loadingProperty().get()),
                () -> Assertions.assertEquals(
                        List.of(FIRST_ID),
                        ids(viewModel.suggestions())
                ),
                () -> Assertions.assertEquals("Alice",
                        viewModel.suggestions().getFirst().displayName())
        );
    }

    @Test
    @DisplayName("Selecting suggestion updates selected entity")
    void selectingSuggestionUpdatesSelectedEntity() {
        final EntityAutocompleteViewModel viewModel =
                viewModel(new FakeProvider(List.of(suggestion(FIRST_ID))));
        viewModel.searchTextProperty().set("ali");
        viewModel.search();

        viewModel.select(viewModel.suggestions().getFirst());

        Assertions.assertEquals(
                FIRST_ID,
                viewModel.selectedSuggestionProperty().get().id()
        );
    }

    @Test
    @DisplayName("Stale search results are ignored")
    void staleSearchResultsAreIgnored() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final SequencedProvider provider = new SequencedProvider(
                List.of(suggestion(FIRST_ID)),
                List.of(suggestion(SECOND_ID))
        );
        final EntityAutocompleteViewModel viewModel =
                viewModel(provider, background, ui);

        viewModel.searchTextProperty().set("first");
        viewModel.search();
        viewModel.searchTextProperty().set("second");
        viewModel.search();
        background.runNext();
        background.runNext();
        ui.runNext();
        ui.runNext();

        Assertions.assertEquals(
                List.of(SECOND_ID),
                ids(viewModel.suggestions())
        );
    }

    @Test
    @DisplayName("Clear invalidates delayed results and resets all transient state")
    void clearInvalidatesDelayedResultsAndResetsState() {
        final QueuedExecutor background = new QueuedExecutor();
        final QueuedExecutor ui = new QueuedExecutor();
        final EntityAutocompleteViewModel viewModel = viewModel(
                new FakeProvider(List.of(suggestion(FIRST_ID))),
                background,
                ui
        );
        viewModel.searchTextProperty().set("old row");
        viewModel.select(suggestion(SECOND_ID));
        viewModel.suggestions().add(suggestion(SECOND_ID));
        viewModel.search();

        viewModel.clear();
        background.runNext();
        ui.runNext();

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        viewModel.searchTextProperty().get().isBlank()),
                () -> Assertions.assertTrue(viewModel.suggestions().isEmpty()),
                () -> Assertions.assertNull(
                        viewModel.selectedSuggestionProperty().get()),
                () -> Assertions.assertTrue(
                        viewModel.errorMessageProperty().get().isBlank()),
                () -> Assertions.assertFalse(viewModel.loadingProperty().get())
        );
    }

    @Test
    @DisplayName("Errors clear after successful search")
    void errorsClearAfterSuccessfulSearch() {
        final SequencedProvider provider = new SequencedProvider(
                new SQLException("database down"),
                List.of(suggestion(FIRST_ID))
        );
        final EntityAutocompleteViewModel viewModel = viewModel(provider);

        viewModel.search();
        Assertions.assertFalse(viewModel.errorMessageProperty().get().isBlank());

        viewModel.search();

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        viewModel.errorMessageProperty().get().isBlank()
                ),
                () -> Assertions.assertEquals(1, viewModel.suggestions().size())
        );
    }

    @Test
    @DisplayName("Dispose prevents further work")
    void disposePreventsFurtherWork() {
        final QueuedExecutor background = new QueuedExecutor();
        final EntityAutocompleteViewModel viewModel =
                viewModel(new FakeProvider(List.of(suggestion(FIRST_ID))),
                        background,
                        new QueuedExecutor());

        viewModel.dispose();
        viewModel.search();

        Assertions.assertEquals(0, background.pendingCount());
    }

    private EntityAutocompleteViewModel viewModel(
            EntityAutocompleteViewModel.SuggestionProvider provider) {

        return viewModel(provider, Runnable::run, Runnable::run);
    }

    private EntityAutocompleteViewModel viewModel(
            EntityAutocompleteViewModel.SuggestionProvider provider,
            java.util.concurrent.Executor background,
            java.util.concurrent.Executor ui) {

        return new EntityAutocompleteViewModel(provider, LIMIT, background, ui);
    }

    private EntitySuggestionDisplay suggestion(UUID id) {
        return EntitySuggestionDisplay.from(new EntitySuggestion(
                id,
                id.equals(FIRST_ID) ? "Alice" : "Bea",
                id.equals(FIRST_ID) ? "Ally" : "Bee",
                MatchField.ALIAS,
                MatchRank.ALIAS_EXACT,
                null
        ));
    }

    private List<UUID> ids(ObservableList<EntitySuggestionDisplay> suggestions) {
        return suggestions.stream()
                .map(EntitySuggestionDisplay::id)
                .toList();
    }

    private static final class FakeProvider
            implements EntityAutocompleteViewModel.SuggestionProvider {
        private final List<EntitySuggestionDisplay> suggestions;

        private FakeProvider(List<EntitySuggestionDisplay> suggestions) {
            this.suggestions = suggestions;
        }

        @Override
        public List<EntitySuggestionDisplay> suggest(String query, int limit) {
            return suggestions;
        }
    }

    private static final class SequencedProvider
            implements EntityAutocompleteViewModel.SuggestionProvider {
        private final ArrayDeque<Object> results = new ArrayDeque<>();

        private SequencedProvider(Object first, Object second) {
            results.add(first);
            results.add(second);
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<EntitySuggestionDisplay> suggest(String query, int limit)
                throws SQLException {

            final Object result = results.removeFirst();

            if (result instanceof SQLException exception) {
                throw exception;
            }

            return (List<EntitySuggestionDisplay>) result;
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

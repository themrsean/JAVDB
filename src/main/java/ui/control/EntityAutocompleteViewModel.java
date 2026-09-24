package ui.control;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class EntityAutocompleteViewModel {
    public interface SuggestionProvider {
        List<EntitySuggestionDisplay> suggest(String query, int limit)
                throws SQLException;
    }

    public static final long DEBOUNCE_MILLIS = 200L;

    private final SuggestionProvider suggestionProvider;
    private final int limit;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final ObservableList<EntitySuggestionDisplay> suggestions =
            FXCollections.observableArrayList();
    private final StringProperty searchText = new SimpleStringProperty("");
    private final ObjectProperty<EntitySuggestionDisplay> selectedSuggestion =
            new SimpleObjectProperty<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final AtomicLong generation = new AtomicLong();

    private boolean disposed;

    public EntityAutocompleteViewModel(
            SuggestionProvider suggestionProvider,
            int limit,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this.suggestionProvider = Objects.requireNonNull(
                suggestionProvider,
                "Suggestion provider must not be null"
        );
        this.limit = limit;
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
    }

    public void search() {
        if (!disposed) {
            final long requestGeneration = generation.incrementAndGet();
            final String query = searchText.get();
            loading.set(true);
            errorMessage.set("");

            try {
                backgroundExecutor.execute(() -> searchInBackground(
                        requestGeneration,
                        query
                ));
            } catch (RejectedExecutionException exception) {
                loading.set(false);
                errorMessage.set(exception.getMessage());
            }
        }
    }

    public void select(EntitySuggestionDisplay suggestion) {
        selectedSuggestion.set(suggestion);
    }

    public void clear() {
        searchText.set("");
        suggestions.clear();
        selectedSuggestion.set(null);
        errorMessage.set("");
    }

    public void dispose() {
        disposed = true;
        generation.incrementAndGet();
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public ObservableList<EntitySuggestionDisplay> suggestions() {
        return suggestions;
    }

    public ObjectProperty<EntitySuggestionDisplay> selectedSuggestionProperty() {
        return selectedSuggestion;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    private void searchInBackground(long requestGeneration, String query) {
        try {
            final List<EntitySuggestionDisplay> result =
                    suggestionProvider.suggest(query, limit);
            uiExecutor.execute(() -> applyResult(requestGeneration, result));
        } catch (SQLException exception) {
            uiExecutor.execute(() -> applyFailure(
                    requestGeneration,
                    exception
            ));
        }
    }

    private void applyResult(
            long requestGeneration,
            List<EntitySuggestionDisplay> result) {

        if (!disposed && requestGeneration == generation.get()) {
            suggestions.setAll(result);
            loading.set(false);
            errorMessage.set("");
        }
    }

    private void applyFailure(long requestGeneration, SQLException exception) {
        if (!disposed && requestGeneration == generation.get()) {
            suggestions.clear();
            loading.set(false);
            errorMessage.set(exception.getMessage());
        }
    }
}

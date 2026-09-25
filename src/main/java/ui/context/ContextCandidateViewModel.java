package ui.context;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import service.ContextCandidate;
import service.ContextCandidateSource;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/** Background loading state for the read-only context-candidate window. */
public final class ContextCandidateViewModel {
    private final ContextCandidateSource source;
    private final Executor background;
    private final Executor ui;
    private final ObservableList<ContextCandidate> candidates =
            FXCollections.observableArrayList();
    private final BooleanProperty attentionOnly = new SimpleBooleanProperty(true);
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final AtomicLong generation = new AtomicLong();
    private boolean disposed;

    public ContextCandidateViewModel(ContextCandidateSource source,
            Executor background, Executor ui) {
        this.source = Objects.requireNonNull(source);
        this.background = Objects.requireNonNull(background);
        this.ui = Objects.requireNonNull(ui);
    }

    public void load() {
        if (disposed) return;
        final long request = generation.incrementAndGet();
        loading.set(true);
        errorMessage.set("");
        background.execute(() -> {
            try {
                final List<ContextCandidate> rows = source.loadCandidates();
                ui.execute(() -> apply(request, rows));
            } catch (SQLException | RuntimeException exception) {
                ui.execute(() -> fail(request));
            }
        });
    }

    private void apply(long request, List<ContextCandidate> rows) {
        if (!disposed && request == generation.get()) {
            candidates.setAll(rows);
            loading.set(false);
        }
    }

    private void fail(long request) {
        if (!disposed && request == generation.get()) {
            errorMessage.set("Unable to load context candidates.");
            loading.set(false);
        }
    }

    public void dispose() { disposed = true; generation.incrementAndGet(); }
    public ObservableList<ContextCandidate> candidates() { return candidates; }
    public BooleanProperty attentionOnlyProperty() { return attentionOnly; }
    public BooleanProperty loadingProperty() { return loading; }
    public StringProperty errorMessageProperty() { return errorMessage; }
}

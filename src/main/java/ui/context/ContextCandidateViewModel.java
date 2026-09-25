package ui.context;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import service.ContextCandidate;
import service.ContextCandidateSource;
import service.ContextCandidateResolvedException;
import service.ContextPublisherResolutionService;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/** Background loading state for the read-only context-candidate window. */
public final class ContextCandidateViewModel {
    private final ContextCandidateSource source;
    private final Executor background;
    private final Executor ui;
    private final ContextPublisherResolutionService publisherResolutionService;
    private final Runnable catalogRefresh;
    private final ObservableList<ContextCandidate> candidates =
            FXCollections.observableArrayList();
    private final BooleanProperty attentionOnly = new SimpleBooleanProperty(true);
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty resultMessage = new SimpleStringProperty("");
    private final AtomicLong generation = new AtomicLong();
    private boolean disposed;

    public ContextCandidateViewModel(ContextCandidateSource source,
            Executor background, Executor ui) {
        this(source, null, background, ui, () -> { });
    }

    public ContextCandidateViewModel(ContextCandidateSource source,
            ContextPublisherResolutionService publisherResolutionService,
            Executor background, Executor ui, Runnable catalogRefresh) {
        this.source = Objects.requireNonNull(source);
        this.publisherResolutionService = publisherResolutionService;
        this.background = Objects.requireNonNull(background);
        this.ui = Objects.requireNonNull(ui);
        this.catalogRefresh = Objects.requireNonNull(catalogRefresh);
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

    public void mapPublisherAlias(String candidate, UUID publisherId) {
        if (publisherResolutionService == null || disposed) return;
        final long request = generation.incrementAndGet();
        loading.set(true);
        errorMessage.set("");
        resultMessage.set("");
        background.execute(() -> {
            try {
                publisherResolutionService.mapPublisherAlias(candidate, publisherId);
                ui.execute(() -> completePublisherAction(request,
                        "Publisher alias added."));
            } catch (ContextCandidateResolvedException exception) {
                ui.execute(() -> stale(request, exception));
            } catch (SQLException | IllegalArgumentException exception) {
                ui.execute(() -> actionFailure(request, exception));
            }
        });
    }

    public void publisherCreated() {
        catalogChanged("Publisher created.");
    }

    public void seriesCreated() {
        catalogChanged("Series created.");
    }

    private void catalogChanged(String message) {
        if (disposed) return;
        resultMessage.set(message);
        catalogRefresh.run();
        load();
    }

    private void completePublisherAction(long request, String message) {
        if (!disposed && request == generation.get()) {
            resultMessage.set(message);
            catalogRefresh.run();
            load();
        }
    }

    private void stale(long request, ContextCandidateResolvedException exception) {
        if (!disposed && request == generation.get()) {
            resultMessage.set("Candidate became resolved: " + exception.status() + ".");
            load();
        }
    }

    private void actionFailure(long request, Exception exception) {
        if (!disposed && request == generation.get()) {
            errorMessage.set(exception.getMessage());
            loading.set(false);
        }
    }

    public void dispose() { disposed = true; generation.incrementAndGet(); }
    public ObservableList<ContextCandidate> candidates() { return candidates; }
    public BooleanProperty attentionOnlyProperty() { return attentionOnly; }
    public BooleanProperty loadingProperty() { return loading; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public StringProperty resultMessageProperty() { return resultMessage; }
}

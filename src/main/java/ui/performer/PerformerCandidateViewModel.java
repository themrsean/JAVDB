package ui.performer;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import service.PerformerCandidate;
import service.PerformerCandidateReviewService;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class PerformerCandidateViewModel {
    private final PerformerCandidateReviewService service;
    private final Executor background;
    private final Executor ui;
    private final Runnable reviewRefresh;
    private final ObservableList<PerformerCandidate> candidates = FXCollections.observableArrayList();
    private final BooleanProperty unresolvedOnly = new SimpleBooleanProperty(true);
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final StringProperty error = new SimpleStringProperty("");
    private final StringProperty result = new SimpleStringProperty("");
    private final AtomicLong generation = new AtomicLong();
    private boolean disposed;

    public PerformerCandidateViewModel(PerformerCandidateReviewService service,
            Executor background, Executor ui, Runnable reviewRefresh) {
        this.service = Objects.requireNonNull(service); this.background = Objects.requireNonNull(background);
        this.ui = Objects.requireNonNull(ui); this.reviewRefresh = Objects.requireNonNull(reviewRefresh);
    }
    public void load() {
        if (disposed) return;
        long request = generation.incrementAndGet(); loading.set(true); error.set("");
        background.execute(() -> { try { List<PerformerCandidate> rows = service.loadCandidates(); ui.execute(() -> apply(request, rows)); }
            catch (SQLException exception) { ui.execute(() -> fail(request)); } });
    }
    public void create(String candidate) { act(candidate, null, true); }
    public void mapAlias(String candidate, UUID performerId) { act(candidate, performerId, false); }
    private void act(String candidate, UUID performerId, boolean create) {
        long request = generation.incrementAndGet(); loading.set(true); error.set("");
        background.execute(() -> { try { if (create) service.createPerformer(candidate); else service.mapAlias(candidate, performerId);
            ui.execute(() -> { if (!disposed && request == generation.get()) { result.set(create ? "Performer created." : "Alias added."); reviewRefresh.run(); load(); } });
        } catch (SQLException | IllegalArgumentException exception) { ui.execute(() -> { if (!disposed && request == generation.get()) { error.set(exception.getMessage()); loading.set(false); } }); } });
    }
    private void apply(long request, List<PerformerCandidate> rows) { if (!disposed && request == generation.get()) { candidates.setAll(rows); loading.set(false); } }
    private void fail(long request) { if (!disposed && request == generation.get()) { error.set("Unable to load performer candidates."); loading.set(false); } }
    public void dispose() { disposed = true; generation.incrementAndGet(); }
    public ObservableList<PerformerCandidate> candidates() { return candidates; }
    public BooleanProperty unresolvedOnlyProperty() { return unresolvedOnly; }
    public BooleanProperty loadingProperty() { return loading; }
    public StringProperty errorMessageProperty() { return error; }
    public StringProperty resultMessageProperty() { return result; }
}

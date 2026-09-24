package ui.review;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import service.SceneReviewQueueItem;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

public final class SceneReviewQueueViewModel {
    public interface DataSource {
        List<SceneReviewQueueItem> loadReviewScenes(int limit, int offset)
                throws SQLException;
    }

    private static final int DEFAULT_LIMIT = 100;
    private static final int DEFAULT_OFFSET = 0;

    private final DataSource dataSource;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final ObservableList<SceneReviewQueueItem> rows =
            FXCollections.observableArrayList();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final AtomicLong generation = new AtomicLong();

    private boolean disposed;

    public SceneReviewQueueViewModel(
            DataSource dataSource,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this.dataSource = Objects.requireNonNull(
                dataSource,
                "Scene review queue data source must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
    }

    public void load() {
        if (!disposed) {
            final long requestGeneration = generation.incrementAndGet();
            backgroundExecutor.execute(() -> loadInBackground(requestGeneration));
        }
    }

    public void dispose() {
        disposed = true;
        generation.incrementAndGet();
    }

    public ObservableList<SceneReviewQueueItem> rows() {
        return rows;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    private void loadInBackground(long requestGeneration) {
        try {
            final List<SceneReviewQueueItem> loaded =
                    dataSource.loadReviewScenes(DEFAULT_LIMIT, DEFAULT_OFFSET);
            uiExecutor.execute(() -> applyRows(requestGeneration, loaded));
        } catch (SQLException exception) {
            uiExecutor.execute(() -> applyError(
                    requestGeneration,
                    exception
            ));
        }
    }

    private void applyRows(
            long requestGeneration,
            List<SceneReviewQueueItem> loaded) {

        if (!disposed && requestGeneration == generation.get()) {
            rows.setAll(loaded);
            errorMessage.set("");
        }
    }

    private void applyError(long requestGeneration, SQLException exception) {
        if (!disposed && requestGeneration == generation.get()) {
            rows.clear();
            errorMessage.set(exception.getMessage());
        }
    }
}

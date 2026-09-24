package ui;

import ui.review.ReviewQueueController;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public final class GuiApplicationContext implements AutoCloseable {
    private final Path databasePath;
    private final ReviewQueueController controller;
    private final ExecutorService backgroundExecutor;

    public GuiApplicationContext(
            Path databasePath,
            ReviewQueueController controller,
            ExecutorService backgroundExecutor) {

        this.databasePath = Objects.requireNonNull(
                databasePath,
                "Database path must not be null"
        );
        this.controller = Objects.requireNonNull(
                controller,
                "Review queue controller must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
    }

    public Path databasePath() {
        return databasePath;
    }

    public ReviewQueueController controller() {
        return controller;
    }

    public ExecutorService backgroundExecutor() {
        return backgroundExecutor;
    }

    @Override
    public void close() {
        controller.dispose();
        backgroundExecutor.shutdownNow();
    }
}

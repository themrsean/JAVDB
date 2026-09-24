package ui.review;

import javafx.collections.ObservableList;
import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.SceneReviewQueueItem;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

class SceneReviewQueueViewModelTest {
    private static final UUID SCENE_ID =
            UUID.fromString("11111111-6666-1111-6666-111111111111");

    @Test
    @DisplayName("Load populates scene review rows")
    void loadPopulatesSceneReviewRows() {
        final SceneReviewQueueViewModel viewModel =
                new SceneReviewQueueViewModel(
                        (limit, offset) -> List.of(item()),
                        Runnable::run,
                        Runnable::run
                );

        viewModel.load();

        Assertions.assertEquals(List.of(SCENE_ID), ids(viewModel.rows()));
    }

    @Test
    @DisplayName("Errors are exposed")
    void errorsAreExposed() {
        final SceneReviewQueueViewModel viewModel =
                new SceneReviewQueueViewModel(
                        (limit, offset) -> {
                            throw new SQLException("database down");
                        },
                        Runnable::run,
                        Runnable::run
                );

        viewModel.load();

        Assertions.assertTrue(
                viewModel.errorMessageProperty().get().contains("database down")
        );
    }

    @Test
    @DisplayName("Dispose prevents work")
    void disposePreventsWork() {
        final QueuedExecutor background = new QueuedExecutor();
        final SceneReviewQueueViewModel viewModel =
                new SceneReviewQueueViewModel(
                        (limit, offset) -> List.of(item()),
                        background,
                        Runnable::run
                );

        viewModel.dispose();
        viewModel.load();

        Assertions.assertEquals(0, background.pendingCount());
    }

    private SceneReviewQueueItem item() {
        return new SceneReviewQueueItem(
                SCENE_ID,
                "Scene",
                VerificationStatus.UNVERIFIED,
                null,
                "Publisher",
                "",
                0
        );
    }

    private List<UUID> ids(ObservableList<SceneReviewQueueItem> rows) {
        return rows.stream().map(SceneReviewQueueItem::sceneId).toList();
    }

    private static final class QueuedExecutor
            implements java.util.concurrent.Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private int pendingCount() {
            return tasks.size();
        }
    }
}

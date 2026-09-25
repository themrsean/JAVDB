package ui.control;

import javafx.scene.control.TableColumn;
import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.ContextCandidate;
import service.ContextCandidateStatus;
import service.FilenameMatchStatus;
import service.MediaLibraryRow;
import service.PerformerCandidate;
import service.PerformerCandidateResolution;
import service.ReviewQueueItem;
import service.SceneReviewQueueItem;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class RecordTableCellValuesTest {
    @Test
    void rendersReviewQueueRecordFieldsWithoutBeanReflection() {
        final ReviewQueueItem row = new ReviewQueueItem(UUID.randomUUID(),
                Path.of("/media/example.mp4"), "example.mp4", "/media",
                FilenameMatchStatus.UNRESOLVED, "Raw 20", "", "", "", "",
                "", "", "", "1920x1080", "00:10", 2);

        Assertions.assertAll(
                () -> Assertions.assertEquals("example.mp4", string(
                        RecordTableCellValues.string(ReviewQueueItem::filename), row)),
                () -> Assertions.assertEquals("Raw 20", string(
                        RecordTableCellValues.string(ReviewQueueItem::proposedTitle), row)),
                () -> Assertions.assertEquals("1920x1080", string(
                        RecordTableCellValues.string(ReviewQueueItem::resolution), row)),
                () -> Assertions.assertEquals(2, object(
                        RecordTableCellValues.object(ReviewQueueItem::warningCount), row))
        );
    }

    @Test
    void rendersMediaLibraryAndSceneReviewRecordFields() {
        final MediaLibraryRow media = new MediaLibraryRow(UUID.randomUUID(),
                Path.of("/media/example.mp4"), "example.mp4", "/media",
                "1920x1080", "00:10", "10 MB", "2026-09-25", "Unassigned");
        final SceneReviewQueueItem scene = new SceneReviewQueueItem(UUID.randomUUID(),
                "Scene", VerificationStatus.NEEDS_REVIEW,
                LocalDate.of(2014, 7, 12), "Publisher", "Series", 3);

        Assertions.assertAll(
                () -> Assertions.assertEquals("example.mp4", string(
                        RecordTableCellValues.string(MediaLibraryRow::filename), media)),
                () -> Assertions.assertEquals("/media", string(
                        RecordTableCellValues.string(MediaLibraryRow::directory), media)),
                () -> Assertions.assertEquals("Unassigned", string(
                        RecordTableCellValues.string(MediaLibraryRow::assignmentSummary), media)),
                () -> Assertions.assertEquals("Scene", string(
                        RecordTableCellValues.string(SceneReviewQueueItem::title), scene)),
                () -> Assertions.assertEquals(3, object(
                        RecordTableCellValues.object(SceneReviewQueueItem::mediaCount), scene))
        );
    }

    @Test
    void rendersPerformerAndContextCandidateRecordFields() {
        final PerformerCandidate performer = new PerformerCandidate("Abella Danger", 4,
                PerformerCandidateResolution.UNRESOLVED, null, "", List.of());
        final ContextCandidate context = new ContextCandidate("EvilAngel", 2,
                Map.of(1, 2), Map.of(2, 2), ContextCandidateStatus.UNRESOLVED,
                List.of(), List.of(), List.of());

        Assertions.assertAll(
                () -> Assertions.assertEquals("Abella Danger", string(
                        RecordTableCellValues.string(PerformerCandidate::text), performer)),
                () -> Assertions.assertEquals(4, object(
                        RecordTableCellValues.object(PerformerCandidate::mediaCount), performer)),
                () -> Assertions.assertEquals("EvilAngel", string(
                        RecordTableCellValues.string(ContextCandidate::text), context)),
                () -> Assertions.assertEquals(2, object(
                        RecordTableCellValues.object(ContextCandidate::occurrences), context))
        );
    }

    @Test
    void javaFxControllersNoLongerUsePropertyValueFactoryForRecords()
            throws Exception {
        for (Path source : List.of(
                Path.of("src/main/java/ui/review/ReviewQueueController.java"),
                Path.of("src/main/java/ui/performer/PerformerCandidateController.java"),
                Path.of("src/main/java/ui/context/ContextCandidateController.java"))) {
            Assertions.assertFalse(java.nio.file.Files.readString(source)
                    .contains("PropertyValueFactory"), source + " uses reflection");
        }
    }

    private <R> String string(javafx.util.Callback<TableColumn.CellDataFeatures<R, String>,
            javafx.beans.value.ObservableValue<String>> factory, R row) {
        return factory.call(new TableColumn.CellDataFeatures<>(null,
                new TableColumn<>(), row)).getValue();
    }

    private <R, V> V object(javafx.util.Callback<TableColumn.CellDataFeatures<R, V>,
            javafx.beans.value.ObservableValue<V>> factory, R row) {
        return factory.call(new TableColumn.CellDataFeatures<>(null,
                new TableColumn<>(), row)).getValue();
    }
}

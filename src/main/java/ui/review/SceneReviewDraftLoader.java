package ui.review;

import service.EditableSceneReviewDraft;
import service.FilenameInterpretation;
import service.ReviewDetails;

import java.sql.SQLException;
import java.util.UUID;

public interface SceneReviewDraftLoader {
    EditableSceneReviewDraft fromReviewDetails(ReviewDetails details);

    EditableSceneReviewDraft fromExistingScene(UUID sceneId) throws SQLException;

    EditableSceneReviewDraft applyInterpretation(
            EditableSceneReviewDraft editable,
            FilenameInterpretation interpretation);
}

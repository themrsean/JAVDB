package ui.review;

import service.EditableSceneReviewDraft;
import service.FilenameInterpretation;
import service.ReviewDetails;
import service.SceneReviewDraftFactory;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class DefaultSceneReviewDraftLoader
        implements SceneReviewDraftLoader {
    private final SceneReviewDraftFactory draftFactory;

    public DefaultSceneReviewDraftLoader(SceneReviewDraftFactory draftFactory) {
        this.draftFactory = Objects.requireNonNull(
                draftFactory,
                "Scene review draft factory must not be null"
        );
    }

    @Override
    public EditableSceneReviewDraft fromReviewDetails(ReviewDetails details) {
        return draftFactory.fromReviewDetails(details);
    }

    @Override
    public EditableSceneReviewDraft fromExistingScene(UUID sceneId)
            throws SQLException {

        return draftFactory.fromExistingScene(sceneId);
    }

    @Override
    public EditableSceneReviewDraft applyInterpretation(
            EditableSceneReviewDraft editable,
            FilenameInterpretation interpretation) {

        return draftFactory.applyInterpretation(editable, interpretation);
    }
}

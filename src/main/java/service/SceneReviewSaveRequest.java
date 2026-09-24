package service;

import java.util.Objects;

public record SceneReviewSaveRequest(
        SceneReviewDraft draft,
        SceneReviewRenameChoice renameChoice) {

    public SceneReviewSaveRequest {
        Objects.requireNonNull(draft, "Scene review draft must not be null");
        Objects.requireNonNull(
                renameChoice,
                "Scene review rename choice must not be null"
        );
    }
}

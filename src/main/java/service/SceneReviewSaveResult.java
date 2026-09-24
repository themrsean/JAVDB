package service;

import model.VerificationStatus;

import java.util.List;
import java.util.UUID;

public record SceneReviewSaveResult(
        SceneReviewSaveStatus status,
        UUID sceneId,
        List<UUID> mediaFileIds,
        VerificationStatus finalVerificationStatus,
        List<MediaRenameResult> renameResults,
        List<String> warnings,
        String error,
        boolean databasePersisted,
        boolean physicalRenameSucceeded) {

    public SceneReviewSaveResult {
        mediaFileIds = mediaFileIds == null ? List.of() : List.copyOf(mediaFileIds);
        renameResults = renameResults == null ? List.of() : List.copyOf(renameResults);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}

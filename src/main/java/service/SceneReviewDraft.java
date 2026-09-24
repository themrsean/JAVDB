package service;

import model.VerificationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SceneReviewDraft(
        SceneReviewMode mode,
        UUID sceneId,
        List<UUID> mediaFileIds,
        String title,
        LocalDate releaseDate,
        String code,
        String season,
        String episode,
        UUID publisherId,
        UUID seriesId,
        UUID selectedMovieId,
        UUID explicitOriginalMovieOverrideId,
        List<UUID> performerIds,
        VerificationStatus verificationStatus,
        List<String> warnings) {

    public SceneReviewDraft {
        mediaFileIds = mediaFileIds == null ? List.of() : List.copyOf(mediaFileIds);
        performerIds = performerIds == null ? List.of() : List.copyOf(performerIds);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}

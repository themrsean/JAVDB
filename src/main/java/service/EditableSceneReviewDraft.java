package service;

import media.ParsedMediaFilename;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record EditableSceneReviewDraft(
        SceneReviewDraft draft,
        Path mediaPath,
        ParsedMediaFilename parsedFilename,
        FilenameMatchStatus matchStatus,
        List<FilenameInterpretation> alternatives,
        List<String> performerCandidates,
        List<String> unmatchedPerformers,
        MovieSelectionResult originalMovieSelection,
        List<String> warnings) {

    public EditableSceneReviewDraft {
        Objects.requireNonNull(draft, "Scene review draft must not be null");
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        performerCandidates = performerCandidates == null
                ? List.of()
                : List.copyOf(performerCandidates);
        unmatchedPerformers = unmatchedPerformers == null
                ? List.of()
                : List.copyOf(unmatchedPerformers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}

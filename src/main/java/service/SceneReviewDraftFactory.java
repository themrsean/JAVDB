package service;

import media.ParsedMediaFilename;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.Scene;
import repository.MovieRepository;
import repository.SceneRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SceneReviewDraftFactory {
    private final SceneRepository sceneRepository;
    private final MovieRepository movieRepository;
    private final OriginalMovieSelector originalMovieSelector;

    public SceneReviewDraftFactory(
            SceneRepository sceneRepository,
            MovieRepository movieRepository,
            OriginalMovieSelector originalMovieSelector) {

        this.sceneRepository = Objects.requireNonNull(
                sceneRepository,
                "Scene repository must not be null"
        );
        this.movieRepository = Objects.requireNonNull(
                movieRepository,
                "Movie repository must not be null"
        );
        this.originalMovieSelector = Objects.requireNonNull(
                originalMovieSelector,
                "Original movie selector must not be null"
        );
    }

    public EditableSceneReviewDraft fromUnassignedPreview(
            FilenamePreview preview) {

        Objects.requireNonNull(preview, "Filename preview must not be null");
        final ParsedMediaFilename parsed = preview.parsedFilename();
        final FilenameMatchResult matchResult = preview.matchResult();
        final FilenameInterpretation best = matchResult.bestInterpretation();
        final FilenameInterpretation initial = FilenameInterpretationConsensus
                .initial(matchResult.status(), best,
                        matchResult.interpretations());
        final SceneReviewDraft draft = draftFromPreview(preview, initial);

        return new EditableSceneReviewDraft(
                draft,
                preview.path(),
                parsed,
                matchResult.status(),
                matchResult.interpretations(),
                parsed.performerCandidates(),
                unmatchedPerformers(parsed, initial),
                null,
                combinedWarnings(parsed.warnings(), matchResult.warnings())
        );
    }

    public EditableSceneReviewDraft fromReviewDetails(ReviewDetails details) {
        Objects.requireNonNull(details, "Review details must not be null");

        final FilenameInterpretation best = FilenameInterpretationConsensus
                .initial(details.matchStatus(), details.bestInterpretation(),
                        details.alternativeInterpretations());
        final SceneReviewDraft draft = new SceneReviewDraft(
                SceneReviewMode.CREATE_FROM_MEDIA,
                null,
                List.of(details.mediaId()),
                details.proposedTitle(),
                details.releaseDate(),
                blankToNull(details.code()),
                blankToNull(details.season()),
                blankToNull(details.episode()),
                id(best == null ? null : best.publisher()),
                id(best == null ? null : best.series()),
                id(best == null ? null : best.movie()),
                id(best == null ? null : best.movie()),
                best == null ? List.of() : performerIds(best.performers()),
                model.VerificationStatus.VERIFIED,
                combinedWarnings(
                        combinedWarnings(
                                details.parserWarnings(),
                                details.matcherWarnings()
                        ),
                        details.canonicalRename().warnings()
                )
        );

        return new EditableSceneReviewDraft(
                draft,
                details.path(),
                null,
                details.matchStatus(),
                details.alternativeInterpretations(),
                details.performerCandidates(),
                details.unmatchedPerformers(),
                null,
                draft.warnings()
        );
    }

    public EditableSceneReviewDraft fromExistingScene(UUID sceneId)
            throws SQLException {

        Objects.requireNonNull(sceneId, "Scene ID must not be null");

        final Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Scene ID does not reference an existing scene: "
                                + sceneId
                ));
        final MovieSelectionResult movieSelection =
                originalMovieSelector.selectOriginalMovie(scene, null);
        final UUID selectedMovieId = movieSelection.selectedMovie()
                .map(Movie::getId)
                .orElse(null);

        return new EditableSceneReviewDraft(
                new SceneReviewDraft(
                        SceneReviewMode.EDIT_EXISTING_SCENE,
                        scene.getId(),
                        scene.getFiles().stream().map(MediaFile::getId).toList(),
                        scene.getTitle(),
                        scene.getReleaseDate(),
                        scene.getCode(),
                        scene.getSeason(),
                        scene.getEpisode(),
                        scene.getPublisher().getId(),
                        scene.getSeries() == null
                                ? null
                                : scene.getSeries().getId(),
                        selectedMovieId,
                        selectedMovieId,
                        scene.getPerformers().stream()
                                .map(Performer::getId)
                                .toList(),
                        scene.getVerificationStatus(),
                        List.of()
                ),
                scene.getFiles().isEmpty()
                        ? null
                        : scene.getFiles().getFirst().getPath(),
                null,
                FilenameMatchStatus.READY,
                List.of(),
                List.of(),
                List.of(),
                movieSelection,
                List.of(movieSelection.reason())
        );
    }

    public EditableSceneReviewDraft applyInterpretation(
            EditableSceneReviewDraft editable,
            FilenameInterpretation interpretation) {

        Objects.requireNonNull(
                editable,
                "Editable scene review draft must not be null"
        );
        Objects.requireNonNull(
                interpretation,
                "Filename interpretation must not be null"
        );

        final SceneReviewDraft current = editable.draft();
        final SceneReviewDraft applied = new SceneReviewDraft(
                current.mode(),
                current.sceneId(),
                current.mediaFileIds(),
                current.title(),
                current.releaseDate(),
                current.code(),
                current.season(),
                current.episode(),
                id(interpretation.publisher()),
                id(interpretation.series()),
                id(interpretation.movie()),
                id(interpretation.movie()),
                performerIdsOrCurrent(
                        interpretation.performers(),
                        current.performerIds()
                ),
                current.verificationStatus(),
                current.warnings()
        );

        return new EditableSceneReviewDraft(
                applied,
                editable.mediaPath(),
                editable.parsedFilename(),
                editable.matchStatus(),
                editable.alternatives(),
                editable.performerCandidates(),
                unmatchedPerformers(editable.parsedFilename(), interpretation),
                editable.originalMovieSelection(),
                editable.warnings()
        );
    }

    private SceneReviewDraft draftFromPreview(
            FilenamePreview preview,
            FilenameInterpretation best) {

        final ParsedMediaFilename parsed = preview.parsedFilename();

        return new SceneReviewDraft(
                SceneReviewMode.CREATE_FROM_MEDIA,
                null,
                List.of(preview.mediaFileId()),
                parsed.titleCandidate(),
                parsed.releaseDate(),
                parsed.codeCandidate(),
                parsed.season(),
                parsed.episode(),
                best == null ? null : id(best.publisher()),
                best == null ? null : id(best.series()),
                best == null ? null : id(best.movie()),
                best == null ? null : id(best.movie()),
                best == null ? List.of() : performerIds(best.performers()),
                model.VerificationStatus.VERIFIED,
                combinedWarnings(
                        parsed.warnings(),
                        preview.matchResult().warnings()
                )
        );
    }

    private UUID id(EntityMatch match) {
        return match == null ? null : match.id();
    }

    private List<UUID> performerIds(List<EntityMatch> performers) {
        return performers == null
                ? List.of()
                : performers.stream().map(EntityMatch::id)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<UUID> performerIdsOrCurrent(
            List<EntityMatch> performers,
            List<UUID> currentIds) {

        final List<UUID> resolvedIds = performerIds(performers);
        return resolvedIds.isEmpty() ? currentIds : resolvedIds;
    }

    private List<String> unmatchedPerformers(
            ParsedMediaFilename parsed,
            FilenameInterpretation interpretation) {

        final List<String> unmatched = new ArrayList<>();

        if (parsed != null) {
            final List<String> matched = interpretation == null
                    ? List.of()
                    : interpretation.performers()
                    .stream()
                    .filter(match -> match.id() != null)
                    .map(EntityMatch::candidateText)
                    .toList();

            for (String candidate : parsed.performerCandidates()) {
                if (!matched.contains(candidate)) {
                    unmatched.add(candidate);
                }
            }
        }

        return List.copyOf(unmatched);
    }

    private List<String> combinedWarnings(
            List<String> first,
            List<String> second) {

        final List<String> warnings = new ArrayList<>();

        if (first != null) {
            warnings.addAll(first);
        }

        if (second != null) {
            warnings.addAll(second);
        }

        return List.copyOf(warnings);
    }

    private String blankToNull(String value) {
        String normalized = null;

        if (value != null && !value.trim().isEmpty()) {
            normalized = value.trim();
        }

        return normalized;
    }
}

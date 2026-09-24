package service;

import model.MediaFile;
import model.Movie;
import model.Performer;
import model.Scene;
import model.VerificationStatus;
import repository.MediaAssignmentRepository;
import repository.MovieRepository;
import repository.SceneRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class SceneReviewWorkflowService implements SceneReviewSaver {
    private final CatalogService catalogService;
    private final MediaAssignmentRepository assignmentRepository;
    private final SceneRepository sceneRepository;
    private final MovieRepository movieRepository;
    private final MediaRenameService renameService;

    public SceneReviewWorkflowService(
            CatalogService catalogService,
            MediaAssignmentRepository assignmentRepository,
            SceneRepository sceneRepository,
            MovieRepository movieRepository,
            MediaRenameService renameService) {

        this.catalogService = Objects.requireNonNull(
                catalogService,
                "Catalog service must not be null"
        );
        this.assignmentRepository = Objects.requireNonNull(
                assignmentRepository,
                "Media assignment repository must not be null"
        );
        this.sceneRepository = Objects.requireNonNull(
                sceneRepository,
                "Scene repository must not be null"
        );
        this.movieRepository = Objects.requireNonNull(
                movieRepository,
                "Movie repository must not be null"
        );
        this.renameService = Objects.requireNonNull(
                renameService,
                "Media rename service must not be null"
        );
    }

    @Override
    public SceneReviewSaveResult save(SceneReviewSaveRequest request)
            throws IOException, SQLException {

        Objects.requireNonNull(request, "Scene review save request must not be null");
        validateDraft(request.draft());

        final SceneReviewSaveResult result =
                request.draft().mode() == SceneReviewMode.CREATE_FROM_MEDIA
                        ? createFromMedia(request)
                        : updateExistingScene(request);

        return result;
    }

    CatalogService catalogService() {
        return catalogService;
    }

    MediaRenameService renameService() {
        return renameService;
    }

    private SceneReviewSaveResult createFromMedia(SceneReviewSaveRequest request)
            throws IOException, SQLException {

        final SceneReviewDraft draft = request.draft();
        final UUID mediaFileId = singleMediaId(draft);

        if (!assignmentRepository.isUnassigned(mediaFileId)) {
            throw new IllegalArgumentException(
                    "Media file is already assigned: " + mediaFileId
            );
        }

        final Scene scene = catalogService.createScene(
                draft.title(),
                draft.code(),
                draft.releaseDate(),
                draft.publisherId(),
                draft.seriesId(),
                draft.season(),
                draft.episode(),
                draft.performerIds(),
                draft.mediaFileIds()
        );
        scene.setVerificationStatus(draft.verificationStatus());
        sceneRepository.update(scene);
        appendMovieIfSelected(draft.selectedMovieId(), scene);

        return finishSave(
                request,
                scene.getId(),
                true,
                SceneReviewSaveStatus.CREATED,
                SceneReviewSaveStatus.CREATED_AND_RENAMED,
                SceneReviewSaveStatus.CREATED_RENAME_FAILED_NEEDS_REVIEW
        );
    }

    private SceneReviewSaveResult updateExistingScene(SceneReviewSaveRequest request)
            throws IOException, SQLException {

        final SceneReviewDraft draft = request.draft();
        final Scene scene = sceneRepository.findById(draft.sceneId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Scene ID does not reference an existing scene: "
                                + draft.sceneId()
                ));

        final Scene updated = new Scene(
                scene.getId(),
                draft.title(),
                catalogService.findPublisherById(draft.publisherId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Publisher ID does not reference an existing publisher: "
                                        + draft.publisherId()
                        )),
                draft.releaseDate(),
                normalizeOptionalText(draft.code()),
                draft.seriesId() == null
                        ? null
                        : catalogService.findSeriesById(draft.seriesId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Series ID does not reference an existing series: "
                                        + draft.seriesId()
                        )),
                normalizeOptionalText(draft.season()),
                normalizeOptionalText(draft.episode()),
                performers(draft.performerIds()),
                scene.getFiles(),
                draft.verificationStatus()
        );
        sceneRepository.update(updated);
        appendMovieIfSelected(draft.selectedMovieId(), updated);

        return finishSave(
                request,
                updated.getId(),
                false,
                SceneReviewSaveStatus.UPDATED,
                SceneReviewSaveStatus.UPDATED_AND_RENAMED,
                SceneReviewSaveStatus.UPDATED_RENAME_FAILED_NEEDS_REVIEW
        );
    }

    private SceneReviewSaveResult finishSave(
            SceneReviewSaveRequest request,
            UUID sceneId,
            boolean created,
            SceneReviewSaveStatus noRenameStatus,
            SceneReviewSaveStatus renamedStatus,
            SceneReviewSaveStatus renameFailedStatus)
            throws IOException, SQLException {

        SceneReviewSaveResult result;

        if (request.renameChoice() == SceneReviewRenameChoice.DO_NOT_RENAME) {
            result = new SceneReviewSaveResult(
                    noRenameStatus,
                    sceneId,
                    request.draft().mediaFileIds(),
                    request.draft().verificationStatus(),
                    List.of(),
                    request.draft().warnings(),
                    null,
                    true,
                    false
            );
        } else {
            result = renameSceneMedia(
                    request,
                    sceneId,
                    created,
                    renamedStatus,
                    renameFailedStatus
            );
        }

        return result;
    }

    private SceneReviewSaveResult renameSceneMedia(
            SceneReviewSaveRequest request,
            UUID sceneId,
            boolean created,
            SceneReviewSaveStatus renamedStatus,
            SceneReviewSaveStatus renameFailedStatus)
            throws IOException, SQLException {

        SceneReviewSaveResult result;

        try {
            final MediaRenameBatchResult batchResult =
                    renameService.renameSceneMedia(
                            sceneId,
                            request.draft().explicitOriginalMovieOverrideId(),
                            false,
                            true
                    );
            final boolean renamed = batchResult.results()
                    .stream()
                    .allMatch(renameResult ->
                            renameResult.status() == MediaRenameStatus.RENAMED
                                    || renameResult.status()
                                    == MediaRenameStatus.UNCHANGED);

            if (renamed) {
                result = new SceneReviewSaveResult(
                        renamedStatus,
                        sceneId,
                        request.draft().mediaFileIds(),
                        request.draft().verificationStatus(),
                        batchResult.results(),
                        request.draft().warnings(),
                        null,
                        true,
                        true
                );
            } else {
                forceNeedsReview(sceneId);
                result = new SceneReviewSaveResult(
                        renameFailedStatus,
                        sceneId,
                        request.draft().mediaFileIds(),
                        VerificationStatus.NEEDS_REVIEW,
                        batchResult.results(),
                        request.draft().warnings(),
                        "Rename did not complete.",
                        true,
                        false
                );
            }
        } catch (IOException | SQLException exception) {
            forceNeedsReview(sceneId);
            result = new SceneReviewSaveResult(
                    renameFailedStatus,
                    sceneId,
                    request.draft().mediaFileIds(),
                    VerificationStatus.NEEDS_REVIEW,
                    List.of(),
                    request.draft().warnings(),
                    exception.getMessage(),
                    true,
                    false
            );
        }

        return result;
    }

    private void appendMovieIfSelected(UUID movieId, Scene scene)
            throws SQLException {

        if (movieId != null) {
            final Movie movie = movieRepository.findById(movieId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Movie ID does not reference an existing movie: "
                                    + movieId
                    ));
            final List<Scene> scenes = new ArrayList<>(movie.getScenes());
            final boolean alreadyPresent = scenes.stream()
                    .anyMatch(existing -> existing.getId().equals(scene.getId()));

            if (!alreadyPresent) {
                scenes.add(scene);
                movie.setScenes(scenes);
                movieRepository.update(movie);
            }
        }
    }

    private void forceNeedsReview(UUID sceneId) throws SQLException {
        final Scene scene = sceneRepository.findById(sceneId).orElseThrow();
        scene.setVerificationStatus(VerificationStatus.NEEDS_REVIEW);
        sceneRepository.update(scene);
    }

    private void validateDraft(SceneReviewDraft draft) throws SQLException {
        Objects.requireNonNull(draft, "Scene review draft must not be null");
        Objects.requireNonNull(draft.mode(), "Scene review mode must not be null");
        Objects.requireNonNull(
                draft.verificationStatus(),
                "Verification status must not be null"
        );

        if (draft.title() == null || draft.title().trim().isEmpty()) {
            throw new IllegalArgumentException("Scene title must not be blank.");
        }

        validateIds(draft.mediaFileIds(), "Media file ID");
        validateIds(draft.performerIds(), "Performer ID");

        if (draft.mode() == SceneReviewMode.CREATE_FROM_MEDIA) {
            singleMediaId(draft);
        } else if (draft.sceneId() == null) {
            throw new IllegalArgumentException("Scene ID must not be null.");
        }

        if (draft.publisherId() == null) {
            throw new IllegalArgumentException("Publisher ID must not be null.");
        }

        if (draft.selectedMovieId() != null) {
            movieRepository.findById(draft.selectedMovieId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Movie ID does not reference an existing movie: "
                                    + draft.selectedMovieId()
                    ));
        }
    }

    private UUID singleMediaId(SceneReviewDraft draft) {
        if (draft.mediaFileIds().size() != 1) {
            throw new IllegalArgumentException(
                    "Exactly one media file is required for scene review creation."
            );
        }

        return draft.mediaFileIds().getFirst();
    }

    private List<Performer> performers(List<UUID> performerIds)
            throws SQLException {

        final List<Performer> performers = new ArrayList<>();

        for (UUID performerId : performerIds) {
            performers.add(catalogService.findPerformerById(performerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Performer ID does not reference an existing performer: "
                                    + performerId
                    )));
        }

        return performers;
    }

    private void validateIds(List<UUID> ids, String fieldName) {
        final Set<UUID> seenIds = new LinkedHashSet<>();

        for (UUID id : ids) {
            if (id == null) {
                throw new IllegalArgumentException(
                        fieldName + " collection must not contain null IDs."
                );
            }

            if (!seenIds.add(id)) {
                throw new IllegalArgumentException(
                        fieldName + " collection must not contain duplicate IDs."
                );
            }
        }
    }

    private String normalizeOptionalText(String value) {
        String normalized = null;

        if (value != null && !value.trim().isEmpty()) {
            normalized = value.trim();
        }

        return normalized;
    }
}

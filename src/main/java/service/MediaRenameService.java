package service;

import media.CanonicalFilenameRequest;
import media.CanonicalFilenameResult;
import media.CanonicalMediaFilenameGenerator;
import media.FilenameGenerationStatus;
import model.MediaFile;
import model.Scene;
import repository.MediaAssignment;
import repository.MediaAssignmentReference;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.SceneRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MediaRenameService {
    private static final Path UNKNOWN_PATH = Path.of("");
    private static final long ZERO_UUID_BITS = 0L;
    private static final long NO_PROPOSED_PATH_COUNT = 0L;
    private static final long COLLISION_PATH_COUNT = 1L;
    private static final UUID UNKNOWN_SCENE_ID =
            new UUID(ZERO_UUID_BITS, ZERO_UUID_BITS);
    private static final String MEDIA_NOT_FOUND =
            "Media file does not exist.";
    private static final String SCENE_NOT_FOUND =
            "Media file is not attached to the selected scene.";
    private static final String AMBIGUOUS_SCENE =
            "Media file is attached to multiple scenes; select a scene.";
    private static final String NO_SCENE =
            "Media file is not attached to a scene.";
    private static final String SOURCE_MISSING =
            "Source media file does not exist.";
    private static final String DESTINATION_EXISTS =
            "Destination file already exists.";
    private static final String DATABASE_PATH_CONFLICT =
            "Another media record already uses the proposed path.";

    private final MediaFileRepository mediaFileRepository;
    private final SceneRepository sceneRepository;
    private final MediaAssignmentRepository assignmentRepository;
    private final OriginalMovieSelector originalMovieSelector;
    private final CanonicalMediaFilenameGenerator filenameGenerator;
    private final MediaFileMover mediaFileMover;
    private final MediaPathUpdater mediaPathUpdater;

    public MediaRenameService(
            MediaFileRepository mediaFileRepository,
            SceneRepository sceneRepository,
            MediaAssignmentRepository assignmentRepository,
            OriginalMovieSelector originalMovieSelector) {

        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        this.sceneRepository = Objects.requireNonNull(
                sceneRepository,
                "Scene repository must not be null"
        );
        this.assignmentRepository = Objects.requireNonNull(
                assignmentRepository,
                "Assignment repository must not be null"
        );
        this.originalMovieSelector = Objects.requireNonNull(
                originalMovieSelector,
                "Original movie selector must not be null"
        );
        mediaFileMover = new DefaultMediaFileMover();
        mediaPathUpdater = new RepositoryMediaPathUpdater(mediaFileRepository);
        filenameGenerator = new CanonicalMediaFilenameGenerator();
    }

    MediaRenameService(
            MediaFileRepository mediaFileRepository,
            SceneRepository sceneRepository,
            MediaAssignmentRepository assignmentRepository,
            OriginalMovieSelector originalMovieSelector,
            MediaFileMover mediaFileMover,
            MediaPathUpdater mediaPathUpdater) {

        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        this.sceneRepository = Objects.requireNonNull(
                sceneRepository,
                "Scene repository must not be null"
        );
        this.assignmentRepository = Objects.requireNonNull(
                assignmentRepository,
                "Assignment repository must not be null"
        );
        this.originalMovieSelector = Objects.requireNonNull(
                originalMovieSelector,
                "Original movie selector must not be null"
        );
        this.mediaFileMover = Objects.requireNonNull(
                mediaFileMover,
                "Media file mover must not be null"
        );
        this.mediaPathUpdater = Objects.requireNonNull(
                mediaPathUpdater,
                "Media path updater must not be null"
        );
        filenameGenerator = new CanonicalMediaFilenameGenerator();
    }

    public MediaRenamePreview preview(MediaRenameRequest request)
            throws SQLException {

        Objects.requireNonNull(request, "Rename request must not be null");

        final Optional<MediaFile> mediaFile =
                mediaFileRepository.findById(request.mediaFileId());
        MediaRenamePreview preview;

        if (mediaFile.isEmpty()) {
            preview = invalidPreview(
                    request.mediaFileId(),
                    UNKNOWN_SCENE_ID,
                    UNKNOWN_PATH,
                    MEDIA_NOT_FOUND
            );
        } else {
            preview = previewExistingMedia(request, mediaFile.orElseThrow());
        }

        return preview;
    }

    public MediaRenameResult rename(MediaRenameRequest request)
            throws IOException, SQLException {

        final MediaRenamePreview preview = preview(request);
        MediaRenameResult result;

        if (preview.status() == MediaRenameStatus.UNCHANGED) {
            result = resultFromPreview(preview, MediaRenameStatus.UNCHANGED);
        } else if (preview.status() != MediaRenameStatus.READY) {
            result = resultFromPreview(preview, preview.status());
        } else {
            result = renameReadyMedia(request, preview);
        }

        return result;
    }

    public List<MediaRenamePreview> previewSceneMedia(
            UUID sceneId,
            UUID movieOverrideId) throws SQLException {

        Objects.requireNonNull(sceneId, "Scene ID must not be null");

        final Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Scene ID does not reference an existing scene: "
                                + sceneId
                ));
        final List<MediaRenamePreview> previews = new ArrayList<>();
        final List<MediaFile> sortedMediaFiles = scene.getFiles()
                .stream()
                .sorted((left, right) -> {
                    final int pathComparison = left.getPath()
                            .toString()
                            .compareToIgnoreCase(right.getPath().toString());
                    int comparison = pathComparison;

                    if (comparison == 0) {
                        comparison = left.getId()
                                .toString()
                                .compareTo(right.getId().toString());
                    }

                    return comparison;
                })
                .toList();

        for (MediaFile mediaFile : sortedMediaFiles) {
            previews.add(preview(new MediaRenameRequest(
                    mediaFile.getId(),
                    sceneId,
                    movieOverrideId
            )));
        }

        return markBatchCollisions(previews);
    }

    public MediaRenameBatchResult renameSceneMedia(
            UUID sceneId,
            UUID movieOverrideId,
            boolean dryRun,
            boolean failFast) throws IOException, SQLException {

        final List<MediaRenamePreview> previews =
                previewSceneMedia(sceneId, movieOverrideId);
        final List<MediaRenameResult> results = new ArrayList<>();
        boolean processing = true;

        for (int index = 0; index < previews.size() && processing; index++) {
            final MediaRenamePreview preview = previews.get(index);
            final MediaRenameResult result = dryRun
                    ? resultFromPreview(preview, preview.status())
                    : renamePreview(preview, movieOverrideId);
            results.add(result);

            if (failFast && isFailedRenameResult(result)) {
                processing = false;
            }
        }

        return new MediaRenameBatchResult(results);
    }

    private List<MediaRenamePreview> markBatchCollisions(
            List<MediaRenamePreview> previews) {

        final Map<Path, Long> proposedPathCounts = previews.stream()
                .filter(preview -> preview.status() == MediaRenameStatus.READY)
                .collect(Collectors.groupingBy(
                        MediaRenamePreview::proposedPath,
                        Collectors.counting()
                ));

        return previews.stream()
                .map(preview -> proposedPathCounts.getOrDefault(
                        preview.proposedPath(),
                        NO_PROPOSED_PATH_COUNT
                ) > COLLISION_PATH_COUNT
                        ? batchCollisionPreview(preview)
                        : preview)
                .toList();
    }

    private MediaRenamePreview batchCollisionPreview(
            MediaRenamePreview preview) {

        return new MediaRenamePreview(
                preview.mediaId(),
                preview.sceneId(),
                preview.originalPath(),
                preview.proposedPath(),
                preview.selectedMovie(),
                preview.movieSelectionStatus(),
                MediaRenameStatus.REVIEW_REQUIRED,
                preview.warnings(),
                "Multiple scene media files propose the same destination path."
        );
    }

    private MediaRenameResult renamePreview(
            MediaRenamePreview preview,
            UUID movieOverrideId) throws IOException, SQLException {

        MediaRenameResult result;

        if (preview.status() == MediaRenameStatus.READY) {
            result = rename(new MediaRenameRequest(
                    preview.mediaId(),
                    preview.sceneId(),
                    movieOverrideId
            ));
        } else {
            result = resultFromPreview(preview, preview.status());
        }

        return result;
    }

    private boolean isFailedRenameResult(MediaRenameResult result) {
        return result.status() != MediaRenameStatus.RENAMED
                && result.status() != MediaRenameStatus.UNCHANGED
                && result.status() != MediaRenameStatus.READY;
    }

    private MediaRenameResult renameReadyMedia(
            MediaRenameRequest request,
            MediaRenamePreview preview) throws IOException, SQLException {

        MediaRenameResult result;

        try {
            mediaFileMover.move(preview.originalPath(), preview.proposedPath());
            updateDatabasePath(request.mediaFileId(), preview);
            result = resultFromPreview(preview, MediaRenameStatus.RENAMED);
        } catch (IOException exception) {
            result = new MediaRenameResult(
                    preview.mediaId(),
                    preview.sceneId(),
                    preview.originalPath(),
                    preview.originalPath(),
                    MediaRenameStatus.FILESYSTEM_FAILURE,
                    preview.warnings(),
                    exception.getMessage()
            );
        }

        return result;
    }

    private void updateDatabasePath(
            UUID mediaFileId,
            MediaRenamePreview preview) throws SQLException, IOException {

        final MediaFile mediaFile =
                mediaFileRepository.findById(mediaFileId).orElseThrow();

        try {
            mediaPathUpdater.updatePath(mediaFile, preview.proposedPath());
        } catch (SQLException exception) {
            moveBackAfterDatabaseFailure(preview, exception);
            throw exception;
        }
    }

    private void moveBackAfterDatabaseFailure(
            MediaRenamePreview preview,
            SQLException originalException) {

        try {
            mediaFileMover.move(preview.proposedPath(), preview.originalPath());
        } catch (IOException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }

    private MediaRenameResult resultFromPreview(
            MediaRenamePreview preview,
            MediaRenameStatus status) {

        final Path finalPath = status == MediaRenameStatus.RENAMED
                || status == MediaRenameStatus.READY
                ? preview.proposedPath()
                : preview.originalPath();

        return new MediaRenameResult(
                preview.mediaId(),
                preview.sceneId(),
                preview.originalPath(),
                finalPath,
                status,
                preview.warnings(),
                preview.error()
        );
    }

    private MediaRenamePreview previewExistingMedia(
            MediaRenameRequest request,
            MediaFile mediaFile) throws SQLException {

        final SceneResolution sceneResolution = resolveScene(request);
        MediaRenamePreview preview;

        if (sceneResolution.scene().isEmpty()) {
            preview = invalidPreview(
                    mediaFile.getId(),
                    sceneResolution.sceneId(),
                    mediaFile.getPath(),
                    sceneResolution.error()
            );
        } else {
            preview = previewResolvedScene(
                    request,
                    mediaFile,
                    sceneResolution.scene().orElseThrow()
            );
        }

        return preview;
    }

    private MediaRenamePreview previewResolvedScene(
            MediaRenameRequest request,
            MediaFile mediaFile,
            Scene scene) throws SQLException {

        final MovieSelectionResult movieSelection =
                originalMovieSelector.selectOriginalMovie(
                        scene,
                        request.movieOverrideId()
                );
        final CanonicalFilenameResult filenameResult =
                filenameGenerator.generate(new CanonicalFilenameRequest(
                        scene,
                        mediaFile,
                        movieSelection
                ));
        MediaRenameStatus status = mapStatus(filenameResult.status());
        String error = filenameResult.error();

        if ((status == MediaRenameStatus.READY
                || status == MediaRenameStatus.UNCHANGED)
                && !Files.exists(mediaFile.getPath())) {
            status = MediaRenameStatus.SOURCE_MISSING;
            error = SOURCE_MISSING;
        }

        if (status == MediaRenameStatus.READY
                && Files.exists(filenameResult.proposedPath())) {
            status = MediaRenameStatus.DESTINATION_EXISTS;
            error = DESTINATION_EXISTS;
        }

        if (status == MediaRenameStatus.READY
                && hasDatabasePathConflict(
                        mediaFile.getId(),
                        filenameResult.proposedPath()
                )) {
            status = MediaRenameStatus.DATABASE_PATH_CONFLICT;
            error = DATABASE_PATH_CONFLICT;
        }

        return new MediaRenamePreview(
                mediaFile.getId(),
                scene.getId(),
                mediaFile.getPath(),
                filenameResult.proposedPath(),
                movieSelection.selectedMovie(),
                movieSelection.status(),
                status,
                filenameResult.warnings(),
                error
        );
    }

    private SceneResolution resolveScene(MediaRenameRequest request)
            throws SQLException {

        final MediaAssignment assignment =
                assignmentRepository.findAssignment(request.mediaFileId());
        SceneResolution resolution;

        if (request.sceneId() != null) {
            resolution = resolveExplicitScene(request.sceneId(), assignment);
        } else if (assignment.scenes().isEmpty()) {
            resolution = new SceneResolution(
                    Optional.empty(),
                    UNKNOWN_SCENE_ID,
                    NO_SCENE
            );
        } else if (assignment.scenes().size() == 1) {
            final UUID sceneId = assignment.scenes().getFirst().id();
            resolution = new SceneResolution(
                    sceneRepository.findById(sceneId),
                    sceneId,
                    EMPTY_ERROR
            );
        } else {
            resolution = new SceneResolution(
                    Optional.empty(),
                    UNKNOWN_SCENE_ID,
                    AMBIGUOUS_SCENE
            );
        }

        return resolution;
    }

    private SceneResolution resolveExplicitScene(
            UUID sceneId,
            MediaAssignment assignment) throws SQLException {

        final boolean mediaIsAttachedToScene = assignment.scenes()
                .stream()
                .map(MediaAssignmentReference::id)
                .anyMatch(sceneId::equals);
        SceneResolution resolution;

        if (mediaIsAttachedToScene) {
            resolution = new SceneResolution(
                    sceneRepository.findById(sceneId),
                    sceneId,
                    EMPTY_ERROR
            );
        } else {
            resolution = new SceneResolution(
                    Optional.empty(),
                    sceneId,
                    SCENE_NOT_FOUND
            );
        }

        return resolution;
    }

    private boolean hasDatabasePathConflict(UUID mediaId, Path proposedPath)
            throws SQLException {

        final Optional<MediaFile> existingMedia =
                mediaFileRepository.findByPath(proposedPath);
        return existingMedia.isPresent()
                && !mediaId.equals(existingMedia.orElseThrow().getId());
    }

    private MediaRenameStatus mapStatus(FilenameGenerationStatus status) {
        return switch (status) {
            case READY -> MediaRenameStatus.READY;
            case UNCHANGED -> MediaRenameStatus.UNCHANGED;
            case REVIEW_REQUIRED -> MediaRenameStatus.REVIEW_REQUIRED;
            case INVALID_METADATA -> MediaRenameStatus.INVALID_METADATA;
            case TOO_LONG -> MediaRenameStatus.TOO_LONG;
        };
    }

    private MediaRenamePreview invalidPreview(
            UUID mediaId,
            UUID sceneId,
            Path path,
            String error) {

        return new MediaRenamePreview(
                mediaId,
                sceneId,
                path,
                path,
                Optional.empty(),
                MovieSelectionStatus.NONE,
                MediaRenameStatus.INVALID_ASSIGNMENT,
                List.of(),
                error
        );
    }

    private static final String EMPTY_ERROR = "";

    private record SceneResolution(
            Optional<Scene> scene,
            UUID sceneId,
            String error) {
    }
}

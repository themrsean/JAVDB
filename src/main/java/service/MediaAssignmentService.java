package service;

import model.MediaFile;
import model.Movie;
import model.Scene;
import repository.MediaAssignment;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.SceneRepository;
import repository.UnassignedMediaFilter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MediaAssignmentService {
    private final MediaAssignmentRepository mediaAssignmentRepository;
    private final MediaFileRepository mediaFileRepository;
    private final SceneRepository sceneRepository;
    private final MovieRepository movieRepository;
    private final CatalogService catalogService;
    private final MediaTitleDeriver titleDeriver;

    public MediaAssignmentService(
            MediaAssignmentRepository mediaAssignmentRepository,
            MediaFileRepository mediaFileRepository,
            SceneRepository sceneRepository,
            MovieRepository movieRepository) {

        this(
                mediaAssignmentRepository,
                mediaFileRepository,
                sceneRepository,
                movieRepository,
                null,
                new MediaTitleDeriver()
        );
    }

    public MediaAssignmentService(
            MediaAssignmentRepository mediaAssignmentRepository,
            MediaFileRepository mediaFileRepository,
            SceneRepository sceneRepository,
            MovieRepository movieRepository,
            CatalogService catalogService,
            MediaTitleDeriver titleDeriver) {

        this.mediaAssignmentRepository = Objects.requireNonNull(
                mediaAssignmentRepository,
                "Media assignment repository must not be null"
        );
        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        this.sceneRepository = Objects.requireNonNull(
                sceneRepository,
                "Scene repository must not be null"
        );
        this.movieRepository = Objects.requireNonNull(
                movieRepository,
                "Movie repository must not be null"
        );
        this.catalogService = catalogService;
        this.titleDeriver = Objects.requireNonNull(
                titleDeriver,
                "Media title deriver must not be null"
        );
    }

    public List<MediaFile> findUnassigned(UnassignedMediaFilter filter)
            throws SQLException {

        return mediaAssignmentRepository.findUnassigned(filter);
    }

    public MediaAssignment findAssignment(UUID mediaFileId)
            throws SQLException {

        requireId(mediaFileId, "Media file ID");
        return mediaAssignmentRepository.findAssignment(mediaFileId);
    }

    public Scene attachToScene(UUID mediaFileId, UUID sceneId)
            throws SQLException {

        final MediaFile mediaFile = findRequiredMediaFile(mediaFileId);
        final Scene scene = sceneRepository.findById(requireId(
                sceneId,
                "Scene ID"
        )).orElseThrow(() -> new IllegalArgumentException(
                "Scene ID does not reference an existing scene: " + sceneId
        ));
        ensureUnassigned(mediaFileId);

        final List<MediaFile> mediaFiles = new ArrayList<>(scene.getFiles());
        mediaFiles.add(mediaFile);
        scene.setFiles(mediaFiles);
        sceneRepository.update(scene);

        return sceneRepository.findById(sceneId).orElseThrow();
    }

    public Movie attachToMovie(UUID mediaFileId, UUID movieId)
            throws SQLException {

        final MediaFile mediaFile = findRequiredMediaFile(mediaFileId);
        final Movie movie = movieRepository.findById(requireId(
                movieId,
                "Movie ID"
        )).orElseThrow(() -> new IllegalArgumentException(
                "Movie ID does not reference an existing movie: " + movieId
        ));
        ensureUnassigned(mediaFileId);

        final List<MediaFile> mediaFiles = new ArrayList<>(movie.getFiles());
        mediaFiles.add(mediaFile);
        movie.setFiles(mediaFiles);
        movieRepository.update(movie);

        return movieRepository.findById(movieId).orElseThrow();
    }

    public Scene createSceneFromMedia(CreateSceneFromMediaRequest request)
            throws SQLException {

        Objects.requireNonNull(
                request,
                "Create scene from media request must not be null"
        );

        if (catalogService == null) {
            throw new IllegalStateException(
                    "Catalog service is required for scene creation."
            );
        }

        final MediaFile mediaFile = findRequiredMediaFile(
                request.mediaFileId()
        );
        ensureUnassigned(request.mediaFileId());
        final String title = title(request.title(), mediaFile);

        return catalogService.createScene(
                title,
                request.code(),
                request.releaseDate(),
                request.publisherId(),
                request.seriesId(),
                request.season(),
                request.episode(),
                request.performerIds(),
                List.of(request.mediaFileId())
        );
    }

    public BatchSceneCreationResult createScenesFromMedia(
            BatchSceneCreationRequest request) throws SQLException {

        Objects.requireNonNull(
                request,
                "Batch scene creation request must not be null"
        );
        final List<UUID> mediaFileIds = validateBatchMediaIds(
                request.mediaFileIds()
        );
        final List<BatchSceneCreationFileResult> results = new ArrayList<>();
        boolean processing = true;
        int mediaFileIndex = 0;

        while (mediaFileIndex < mediaFileIds.size() && processing) {
            final UUID mediaFileId = mediaFileIds.get(mediaFileIndex);
            final BatchSceneCreationFileResult result = createSceneFromMediaRow(
                    mediaFileIndex,
                    mediaFileId,
                    request
            );
            results.add(result);

            if (request.failFast()
                    && result.status() != BatchSceneCreationStatus.CREATED
                    && result.status()
                    != BatchSceneCreationStatus.WOULD_CREATE) {
                processing = false;
            }

            mediaFileIndex++;
        }

        return new BatchSceneCreationResult(
                List.copyOf(results),
                summarizeBatch(results)
        );
    }

    private MediaFile findRequiredMediaFile(UUID mediaFileId)
            throws SQLException {

        return mediaFileRepository.findById(requireId(
                mediaFileId,
                "Media file ID"
        )).orElseThrow(() -> new IllegalArgumentException(
                "Media file ID does not reference an existing media file: "
                        + mediaFileId
        ));
    }

    private void ensureUnassigned(UUID mediaFileId) throws SQLException {
        if (!mediaAssignmentRepository.isUnassigned(mediaFileId)) {
            throw new IllegalArgumentException(
                    "Media file is already assigned: " + mediaFileId
            );
        }
    }

    private UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " must not be null.");
        }

        return id;
    }

    private List<UUID> validateBatchMediaIds(List<UUID> mediaFileIds) {
        final List<UUID> ids =
                mediaFileIds == null ? List.of() : mediaFileIds;
        final Set<UUID> seenIds = new LinkedHashSet<>();

        for (UUID mediaFileId : ids) {
            if (mediaFileId == null) {
                throw new IllegalArgumentException(
                        "Media file ID collection must not contain null IDs."
                );
            }

            if (!seenIds.add(mediaFileId)) {
                throw new IllegalArgumentException(
                        "Media file ID collection must not contain duplicate IDs."
                );
            }
        }

        return List.copyOf(ids);
    }

    private BatchSceneCreationFileResult createSceneFromMediaRow(
            int mediaFileIndex,
            UUID mediaFileId,
            BatchSceneCreationRequest request) throws SQLException {

        final int position = mediaFileIndex + 1;
        BatchSceneCreationFileResult result;
        final Optional<MediaFile> mediaFile =
                mediaFileRepository.findById(mediaFileId);

        if (mediaFile.isEmpty()) {
            result = new BatchSceneCreationFileResult(
                    position,
                    mediaFileId,
                    null,
                    null,
                    null,
                    BatchSceneCreationStatus.MEDIA_NOT_FOUND,
                    "Media file not found."
            );
        } else if (!mediaAssignmentRepository.isUnassigned(mediaFileId)) {
            result = new BatchSceneCreationFileResult(
                    position,
                    mediaFileId,
                    mediaFile.orElseThrow().getPath(),
                    titleDeriver.derive(mediaFile.orElseThrow().getPath()),
                    null,
                    BatchSceneCreationStatus.ALREADY_ASSIGNED,
                    "Media file is already assigned."
            );
        } else if (request.dryRun()) {
            result = new BatchSceneCreationFileResult(
                    position,
                    mediaFileId,
                    mediaFile.orElseThrow().getPath(),
                    titleDeriver.derive(mediaFile.orElseThrow().getPath()),
                    null,
                    BatchSceneCreationStatus.WOULD_CREATE,
                    null
            );
        } else {
            result = createPersistedBatchRow(
                    position,
                    mediaFile.orElseThrow(),
                    request
            );
        }

        return result;
    }

    private BatchSceneCreationFileResult createPersistedBatchRow(
            int position,
            MediaFile mediaFile,
            BatchSceneCreationRequest request) throws SQLException {

        BatchSceneCreationFileResult result;
        final String title = titleDeriver.derive(mediaFile.getPath());

        try {
            final Scene scene = createSceneFromMedia(
                    new CreateSceneFromMediaRequest(
                            mediaFile.getId(),
                            title,
                            null,
                            request.releaseDate(),
                            request.publisherId(),
                            request.seriesId(),
                            request.season(),
                            request.episode(),
                            request.performerIds()
                    )
            );
            result = new BatchSceneCreationFileResult(
                    position,
                    mediaFile.getId(),
                    mediaFile.getPath(),
                    title,
                    scene.getId(),
                    BatchSceneCreationStatus.CREATED,
                    null
            );
        } catch (IllegalArgumentException exception) {
            result = new BatchSceneCreationFileResult(
                    position,
                    mediaFile.getId(),
                    mediaFile.getPath(),
                    title,
                    null,
                    BatchSceneCreationStatus.FAILED,
                    exception.getMessage()
            );
        }

        return result;
    }

    private BatchSceneCreationSummary summarizeBatch(
            List<BatchSceneCreationFileResult> results) {

        int created = 0;
        int wouldCreate = 0;
        int alreadyAssigned = 0;
        int missing = 0;
        int failed = 0;

        for (BatchSceneCreationFileResult result : results) {
            if (result.status() == BatchSceneCreationStatus.CREATED) {
                created++;
            } else if (result.status()
                    == BatchSceneCreationStatus.WOULD_CREATE) {
                wouldCreate++;
            } else if (result.status()
                    == BatchSceneCreationStatus.ALREADY_ASSIGNED) {
                alreadyAssigned++;
            } else if (result.status()
                    == BatchSceneCreationStatus.MEDIA_NOT_FOUND) {
                missing++;
            } else if (result.status() == BatchSceneCreationStatus.FAILED) {
                failed++;
            }
        }

        return new BatchSceneCreationSummary(
                results.size(),
                created,
                wouldCreate,
                alreadyAssigned,
                missing,
                failed
        );
    }

    private String title(String explicitTitle, MediaFile mediaFile) {
        String title = explicitTitle;

        if (title == null || title.isBlank()) {
            title = titleDeriver.derive(mediaFile.getPath());
        }

        return title;
    }
}

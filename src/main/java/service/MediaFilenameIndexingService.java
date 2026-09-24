package service;

import media.MediaFilenameParser;
import media.ParsedMediaFilename;
import model.MediaFile;
import model.Movie;
import model.Scene;
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
import java.util.Set;
import java.util.UUID;

public final class MediaFilenameIndexingService {
    private final MediaFileRepository mediaFileRepository;
    private final MediaAssignmentRepository mediaAssignmentRepository;
    private final MediaFilenameParser filenameParser;
    private final FilenameMetadataMatcher metadataMatcher;
    private final CatalogService catalogService;
    private final SceneRepository sceneRepository;
    private final MovieRepository movieRepository;

    public MediaFilenameIndexingService(
            MediaFileRepository mediaFileRepository,
            MediaAssignmentRepository mediaAssignmentRepository,
            MediaFilenameParser filenameParser,
            FilenameMetadataMatcher metadataMatcher) {

        this(
                mediaFileRepository,
                mediaAssignmentRepository,
                filenameParser,
                metadataMatcher,
                null,
                null,
                null
        );
    }

    public MediaFilenameIndexingService(
            MediaFileRepository mediaFileRepository,
            MediaAssignmentRepository mediaAssignmentRepository,
            MediaFilenameParser filenameParser,
            FilenameMetadataMatcher metadataMatcher,
            CatalogService catalogService,
            SceneRepository sceneRepository,
            MovieRepository movieRepository) {

        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        this.mediaAssignmentRepository = Objects.requireNonNull(
                mediaAssignmentRepository,
                "Media assignment repository must not be null"
        );
        this.filenameParser = Objects.requireNonNull(
                filenameParser,
                "Filename parser must not be null"
        );
        this.metadataMatcher = Objects.requireNonNull(
                metadataMatcher,
                "Filename metadata matcher must not be null"
        );
        this.catalogService = catalogService;
        this.sceneRepository = sceneRepository;
        this.movieRepository = movieRepository;
    }

    public FilenamePreview preview(UUID mediaFileId) throws SQLException {
        Objects.requireNonNull(mediaFileId, "Media file ID must not be null");

        final MediaFile mediaFile = mediaFileRepository.findById(mediaFileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media file not found: " + mediaFileId
                ));

        return preview(mediaFile);
    }

    public List<FilenamePreview> previewUnassigned(
            UnassignedMediaFilter filter) throws SQLException {

        final List<FilenamePreview> previews = new ArrayList<>();

        for (MediaFile mediaFile
                : mediaAssignmentRepository.findUnassigned(filter)) {
            previews.add(preview(mediaFile));
        }

        return previews;
    }

    public AutoIndexResult autoIndex(AutoIndexRequest request)
            throws SQLException {

        Objects.requireNonNull(request, "Auto index request must not be null");
        ensureAutoIndexDependencies();
        final List<UUID> mediaFileIds = validateMediaIds(
                request.mediaFileIds()
        );
        final List<AutoIndexFileResult> results = new ArrayList<>();
        boolean processing = true;
        int index = 0;

        while (index < mediaFileIds.size() && processing) {
            final AutoIndexFileResult result = autoIndexOne(
                    mediaFileIds.get(index),
                    request.dryRun()
            );
            results.add(result);

            if (request.failFast()
                    && result.status() != AutoIndexStatus.CREATED_VERIFY
                    && result.status() != AutoIndexStatus.WOULD_CREATE) {
                processing = false;
            }

            index++;
        }

        return new AutoIndexResult(List.copyOf(results), summarize(results));
    }

    private FilenamePreview preview(MediaFile mediaFile) throws SQLException {
        final ParsedMediaFilename parsed =
                filenameParser.parse(mediaFile.getPath());
        final FilenameMatchResult matchResult =
                metadataMatcher.match(parsed);

        return new FilenamePreview(
                mediaFile.getId(),
                mediaFile.getPath(),
                !mediaAssignmentRepository.isUnassigned(mediaFile.getId()),
                parsed,
                matchResult
        );
    }

    private AutoIndexFileResult autoIndexOne(UUID mediaFileId, boolean dryRun)
            throws SQLException {

        AutoIndexFileResult result;
        final MediaFile mediaFile = mediaFileRepository.findById(mediaFileId)
                .orElse(null);

        if (mediaFile == null) {
            result = new AutoIndexFileResult(
                    AutoIndexStatus.FAILED,
                    mediaFileId,
                    null,
                    null,
                    null,
                    null,
                    "Media file not found: " + mediaFileId
            );
        } else if (!mediaAssignmentRepository.isUnassigned(mediaFileId)) {
            result = new AutoIndexFileResult(
                    AutoIndexStatus.ALREADY_ASSIGNED,
                    mediaFileId,
                    mediaFile.getPath(),
                    null,
                    null,
                    null,
                    "Media file is already assigned."
            );
        } else {
            final FilenamePreview preview = preview(mediaFile);
            final FilenameMatchStatus status = preview.matchResult().status();

            if (status == FilenameMatchStatus.READY) {
                result = dryRun
                        ? wouldCreate(preview)
                        : createScene(preview);
            } else {
                result = nonReadyResult(preview, status);
            }
        }

        return result;
    }

    private AutoIndexFileResult wouldCreate(FilenamePreview preview) {
        return new AutoIndexFileResult(
                AutoIndexStatus.WOULD_CREATE,
                preview.mediaFileId(),
                preview.path(),
                null,
                preview.parsedFilename().titleCandidate(),
                null,
                null
        );
    }

    private AutoIndexFileResult createScene(FilenamePreview preview)
            throws SQLException {

        final FilenameInterpretation interpretation =
                preview.matchResult().bestInterpretation();
        final List<UUID> performerIds = interpretation.performers().stream()
                .map(EntityMatch::id)
                .toList();
        final Scene scene = catalogService.createScene(
                preview.parsedFilename().titleCandidate(),
                preview.parsedFilename().codeCandidate(),
                preview.parsedFilename().releaseDate(),
                interpretation.publisher().id(),
                interpretation.series().id(),
                preview.parsedFilename().season(),
                preview.parsedFilename().episode(),
                performerIds,
                List.of(preview.mediaFileId())
        );
        appendSceneToMovieIfMatched(interpretation, scene);

        return new AutoIndexFileResult(
                AutoIndexStatus.CREATED_VERIFY,
                preview.mediaFileId(),
                preview.path(),
                scene.getId(),
                scene.getTitle(),
                !hasResolvedMovie(interpretation)
                        ? "VERIFY"
                        : "VERIFY;movie scene appended",
                null
        );
    }

    private boolean hasResolvedMovie(FilenameInterpretation interpretation) {
        return interpretation.movie() != null
                && interpretation.movie().id() != null;
    }

    private void appendSceneToMovieIfMatched(
            FilenameInterpretation interpretation,
            Scene scene) throws SQLException {

        if (hasResolvedMovie(interpretation)) {
            try {
                final Movie movie = movieRepository.findById(
                        interpretation.movie().id()
                ).orElseThrow(() -> new IllegalArgumentException(
                        "Movie not found: " + interpretation.movie().id()
                ));
                final List<Scene> scenes = new ArrayList<>(
                        movie.getScenes()
                );
                scenes.add(scene);
                movie.setScenes(scenes);
                movieRepository.update(movie);
            } catch (SQLException exception) {
                compensateCreatedScene(scene, exception);
                throw exception;
            } catch (IllegalArgumentException exception) {
                compensateCreatedScene(scene, exception);
                throw exception;
            }
        }
    }

    private void compensateCreatedScene(Scene scene, Exception original)
            throws SQLException {

        try {
            sceneRepository.delete(scene.getId());
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private AutoIndexFileResult nonReadyResult(
            FilenamePreview preview,
            FilenameMatchStatus status) {

        final AutoIndexStatus autoStatus = switch (status) {
            case REVIEW_REQUIRED -> AutoIndexStatus.REVIEW_REQUIRED;
            case AMBIGUOUS -> AutoIndexStatus.AMBIGUOUS;
            case UNRESOLVED -> AutoIndexStatus.UNRESOLVED;
            case INVALID_FILENAME -> AutoIndexStatus.INVALID_FILENAME;
            case READY -> AutoIndexStatus.FAILED;
        };

        return new AutoIndexFileResult(
                autoStatus,
                preview.mediaFileId(),
                preview.path(),
                null,
                preview.parsedFilename().titleCandidate(),
                null,
                null
        );
    }

    private List<UUID> validateMediaIds(List<UUID> mediaFileIds) {
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

    private AutoIndexSummary summarize(List<AutoIndexFileResult> results) {
        int created = 0;
        int wouldCreate = 0;
        int reviewRequired = 0;
        int ambiguous = 0;
        int unresolved = 0;
        int invalid = 0;
        int alreadyAssigned = 0;
        int failed = 0;

        for (AutoIndexFileResult result : results) {
            if (result.status() == AutoIndexStatus.CREATED_VERIFY) {
                created++;
            } else if (result.status() == AutoIndexStatus.WOULD_CREATE) {
                wouldCreate++;
            } else if (result.status() == AutoIndexStatus.REVIEW_REQUIRED) {
                reviewRequired++;
            } else if (result.status() == AutoIndexStatus.AMBIGUOUS) {
                ambiguous++;
            } else if (result.status() == AutoIndexStatus.UNRESOLVED) {
                unresolved++;
            } else if (result.status() == AutoIndexStatus.INVALID_FILENAME) {
                invalid++;
            } else if (result.status() == AutoIndexStatus.ALREADY_ASSIGNED) {
                alreadyAssigned++;
            } else if (result.status() == AutoIndexStatus.FAILED) {
                failed++;
            }
        }

        return new AutoIndexSummary(
                results.size(),
                created,
                wouldCreate,
                reviewRequired,
                ambiguous,
                unresolved,
                invalid,
                alreadyAssigned,
                failed
        );
    }

    private void ensureAutoIndexDependencies() {
        if (catalogService == null
                || sceneRepository == null
                || movieRepository == null) {
            throw new IllegalStateException(
                    "Auto-index dependencies are not available."
            );
        }
    }
}

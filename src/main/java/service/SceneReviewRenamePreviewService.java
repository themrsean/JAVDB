package service;

import media.CanonicalFilenameRequest;
import media.CanonicalFilenameResult;
import media.CanonicalMediaFilenameGenerator;
import media.FilenameGenerationStatus;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.Publisher;
import model.Scene;
import model.Series;
import repository.MediaFileRepository;

import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SceneReviewRenamePreviewService {
    private static final UUID PREVIEW_SCENE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String EMPTY_TEXT = "";

    private final CatalogService catalogService;
    private final MediaFileRepository mediaFileRepository;
    private final OriginalMovieSelector originalMovieSelector;
    private final CanonicalMediaFilenameGenerator filenameGenerator;

    public SceneReviewRenamePreviewService(
            CatalogService catalogService,
            MediaFileRepository mediaFileRepository,
            OriginalMovieSelector originalMovieSelector) {

        this.catalogService = Objects.requireNonNull(
                catalogService,
                "Catalog service must not be null"
        );
        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        this.originalMovieSelector = Objects.requireNonNull(
                originalMovieSelector,
                "Original movie selector must not be null"
        );
        filenameGenerator = new CanonicalMediaFilenameGenerator();
    }

    public CanonicalRenameDisplay preview(SceneReviewDraft draft)
            throws SQLException {

        Objects.requireNonNull(draft, "Scene review draft must not be null");

        final MediaFile mediaFile = mediaFile(draft);
        CanonicalRenameDisplay display;

        if (draft.publisherId() == null) {
            display = invalidDisplay(
                    mediaFile,
                    FilenameGenerationStatus.INVALID_METADATA.name(),
                    "Publisher is required for canonical filename preview."
            );
        } else {
            final Scene scene = transientScene(draft, mediaFile);
            final MovieSelectionResult movieSelection = movieSelection(
                    draft,
                    scene
            );
            final CanonicalFilenameResult result =
                    filenameGenerator.generate(new CanonicalFilenameRequest(
                            scene,
                            mediaFile,
                            movieSelection
                    ));
            final Optional<MediaFile> existing =
                    mediaFileRepository.findByPath(result.proposedPath());
            final boolean databaseConflict = existing.isPresent()
                    && !mediaFile.getId().equals(existing.orElseThrow().getId());

            display = new CanonicalRenameDisplay(
                    result.status().name(),
                    mediaFile.getPath().getFileName().toString(),
                    result.proposedFilename(),
                    result.proposedPath(),
                    result.status() == FilenameGenerationStatus.UNCHANGED,
                    Files.exists(result.proposedPath())
                            && !mediaFile.getPath().equals(result.proposedPath()),
                    databaseConflict,
                    result.warnings(),
                    result.error()
            );
        }

        return display;
    }

    private MediaFile mediaFile(SceneReviewDraft draft) throws SQLException {
        if (draft.mediaFileIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one media file is required for rename preview."
            );
        }

        return mediaFileRepository.findById(draft.mediaFileIds().getFirst())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media file not found: "
                                + draft.mediaFileIds().getFirst()
                ));
    }

    private Scene transientScene(
            SceneReviewDraft draft,
            MediaFile mediaFile) throws SQLException {

        final Publisher publisher = catalogService.findPublisherById(
                draft.publisherId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Publisher ID does not reference an existing publisher: "
                        + draft.publisherId()
        ));
        final Series series = draft.seriesId() == null
                ? null
                : catalogService.findSeriesById(draft.seriesId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Series ID does not reference an existing series: "
                                + draft.seriesId()
                ));
        final List<Performer> performers = performers(draft.performerIds());

        return new Scene(
                draft.sceneId() == null ? PREVIEW_SCENE_ID : draft.sceneId(),
                draft.title(),
                publisher,
                draft.releaseDate(),
                draft.code(),
                series,
                draft.season(),
                draft.episode(),
                performers,
                List.of(mediaFile),
                draft.verificationStatus()
        );
    }

    private List<Performer> performers(List<UUID> performerIds)
            throws SQLException {

        return performerIds.stream()
                .map(this::findPerformerUnchecked)
                .toList();
    }

    private Performer findPerformerUnchecked(UUID performerId) {
        try {
            return catalogService.findPerformerById(performerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Performer ID does not reference an existing performer: "
                                    + performerId
                    ));
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private MovieSelectionResult movieSelection(
            SceneReviewDraft draft,
            Scene scene) throws SQLException {

        MovieSelectionResult result;

        if (draft.sceneId() != null) {
            result = originalMovieSelector.selectOriginalMovie(
                    scene,
                    draft.explicitOriginalMovieOverrideId()
            );
        } else if (draft.selectedMovieId() != null) {
            final Movie movie = catalogService.findMovieById(
                    draft.selectedMovieId()
            ).orElseThrow(() -> new IllegalArgumentException(
                    "Movie ID does not reference an existing movie: "
                            + draft.selectedMovieId()
            ));
            result = new MovieSelectionResult(
                    scene.getId(),
                    MovieSelectionStatus.SELECTED,
                    Optional.of(movie),
                    List.of(movie),
                    "Selected movie from review draft."
            );
        } else {
            result = new MovieSelectionResult(
                    scene.getId(),
                    MovieSelectionStatus.NONE,
                    Optional.empty(),
                    List.of(),
                    "No movie selected."
            );
        }

        return result;
    }

    private CanonicalRenameDisplay invalidDisplay(
            MediaFile mediaFile,
            String status,
            String error) {

        return new CanonicalRenameDisplay(
                status,
                mediaFile.getPath().getFileName().toString(),
                EMPTY_TEXT,
                mediaFile.getPath(),
                false,
                false,
                false,
                List.of(),
                error
        );
    }
}

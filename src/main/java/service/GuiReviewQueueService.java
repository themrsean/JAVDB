package service;

import media.CanonicalFilenameRequest;
import media.CanonicalFilenameResult;
import media.CanonicalMediaFilenameGenerator;
import media.FilenameGenerationStatus;
import media.ParsedMediaFilename;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import repository.MediaFileRepository;
import ui.review.ReviewDisplayFormatter;
import ui.review.ReviewQueueDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GuiReviewQueueService implements ReviewQueueDataSource {
    private static final UUID PREVIEW_SCENE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PREVIEW_MOVIE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String EMPTY_TEXT = "";
    private static final String UNKNOWN_CATEGORY_NAME = "UNKNOWN";

    private final MediaFilenameIndexingService indexingService;
    private final MediaFileRepository mediaFileRepository;
    private final CanonicalMediaFilenameGenerator filenameGenerator;

    public GuiReviewQueueService(
            MediaFilenameIndexingService indexingService,
            MediaFileRepository mediaFileRepository) {

        this.indexingService = Objects.requireNonNull(
                indexingService,
                "Filename indexing service must not be null"
        );
        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        filenameGenerator = new CanonicalMediaFilenameGenerator();
    }

    @Override
    public ReviewQueuePage loadPage(ReviewQueueFilter filter)
            throws SQLException {

        final ReviewQueueFilter effectiveFilter =
                filter == null ? ReviewQueueFilter.firstPage() : filter;
        final List<FilenamePreview> basePreviews =
                indexingService.previewUnassigned(
                        effectiveFilter.toUnassignedMediaFilter()
                );
        final List<ReviewQueueItem> items = new ArrayList<>();
        final List<ReviewDetails> details = new ArrayList<>();

        for (FilenamePreview preview : basePreviews) {
            if (matchesStatusFilter(preview, effectiveFilter.statusFilter())) {
                final MediaFile mediaFile = mediaFileRepository.findById(
                        preview.mediaFileId()
                ).orElseThrow(() -> new IllegalArgumentException(
                        "Media file not found: " + preview.mediaFileId()
                ));
                final ReviewDetails detail = buildDetails(preview, mediaFile);
                details.add(detail);
                items.add(buildItem(preview, mediaFile, detail));
            }
        }

        return new ReviewQueuePage(
                effectiveFilter,
                items,
                details,
                basePreviews.size()
        );
    }

    private boolean matchesStatusFilter(
            FilenamePreview preview,
            ReviewMatchStatusFilter statusFilter) {

        return statusFilter == ReviewMatchStatusFilter.ALL
                || statusFilter.name().equals(
                        preview.matchResult().status().name()
                );
    }

    private ReviewQueueItem buildItem(
            FilenamePreview preview,
            MediaFile mediaFile,
            ReviewDetails details) {

        final FilenameInterpretation interpretation =
                preview.matchResult().bestInterpretation();
        return new ReviewQueueItem(
                preview.mediaFileId(),
                preview.path(),
                filename(preview.path()),
                directory(preview.path()),
                preview.matchResult().status(),
                nullToEmpty(preview.parsedFilename().titleCandidate()),
                entityName(interpretation == null
                        ? null
                        : interpretation.publisher()),
                entitySource(interpretation == null
                        ? null
                        : interpretation.publisher()),
                entityName(interpretation == null
                        ? null
                        : interpretation.series()),
                entitySource(interpretation == null
                        ? null
                        : interpretation.series()),
                entityName(interpretation == null
                        ? null
                        : interpretation.movie()),
                entitySource(interpretation == null
                        ? null
                        : interpretation.movie()),
                String.join(", ", details.resolvedPerformerNames()),
                resolution(mediaFile),
                duration(mediaFile.getDuration()),
                details.parserWarnings().size()
                        + details.matcherWarnings().size()
                        + details.canonicalRename().warnings().size()
        );
    }

    private ReviewDetails buildDetails(
            FilenamePreview preview,
            MediaFile mediaFile) throws SQLException {

        final ParsedMediaFilename parsed = preview.parsedFilename();
        final FilenameMatchResult matchResult = preview.matchResult();
        final FilenameInterpretation interpretation =
                matchResult.bestInterpretation();
        final List<String> resolvedPerformers =
                resolvedPerformers(interpretation);
        final List<String> unmatchedPerformers =
                unmatchedPerformers(interpretation);

        return new ReviewDetails(
                preview.mediaFileId(),
                mediaFile.getPath(),
                filename(mediaFile.getPath()),
                directory(mediaFile.getPath()),
                mediaFile.getFileSize(),
                mediaFile.getLastModifiedMillis(),
                mediaFile.getWidth(),
                mediaFile.getHeight(),
                duration(mediaFile.getDuration()),
                nullToEmpty(mediaFile.getContentHash()),
                parsed.status(),
                parsed.releaseDate(),
                parsed.contextSegments(),
                nullToEmpty(parsed.titleCandidate()),
                nullToEmpty(parsed.codeCandidate()),
                nullToEmpty(parsed.season()),
                nullToEmpty(parsed.episode()),
                parsed.performerCandidates(),
                resolvedPerformers,
                unmatchedPerformers,
                entityCandidate(interpretation == null
                        ? null
                        : interpretation.publisher()),
                entityName(interpretation == null
                        ? null
                        : interpretation.publisher()),
                entityCandidate(interpretation == null
                        ? null
                        : interpretation.series()),
                entityName(interpretation == null
                        ? null
                        : interpretation.series()),
                entityCandidate(interpretation == null
                        ? null
                        : interpretation.movie()),
                entityName(interpretation == null
                        ? null
                        : interpretation.movie()),
                interpretation,
                matchResult.interpretations(),
                interpretation == null
                        ? parsed.unresolvedSegments()
                        : interpretation.unresolvedSegments(),
                parsed.issues(),
                parsed.warnings(),
                matchResult.warnings(),
                matchResult.status(),
                canonicalRename(preview, mediaFile)
        );
    }

    private CanonicalRenameDisplay canonicalRename(
            FilenamePreview preview,
            MediaFile mediaFile) throws SQLException {

        CanonicalRenameDisplay display;

        if (preview.matchResult().status() == FilenameMatchStatus.READY) {
            final CanonicalFilenameResult result =
                    filenameGenerator.generate(new CanonicalFilenameRequest(
                            transientScene(preview),
                            mediaFile,
                            movieSelection(preview)
                    ));
            final Optional<MediaFile> existing =
                    mediaFileRepository.findByPath(result.proposedPath());
            final boolean databaseConflict = existing.isPresent()
                    && !mediaFile.getId().equals(existing.orElseThrow().getId());
            display = new CanonicalRenameDisplay(
                    result.status().name(),
                    filename(mediaFile.getPath()),
                    result.proposedFilename(),
                    result.proposedPath(),
                    result.status() == FilenameGenerationStatus.UNCHANGED,
                    Files.exists(result.proposedPath())
                            && !mediaFile.getPath().equals(result.proposedPath()),
                    databaseConflict,
                    result.warnings(),
                    result.error()
            );
        } else {
            display = new CanonicalRenameDisplay(
                    FilenameGenerationStatus.REVIEW_REQUIRED.name(),
                    filename(mediaFile.getPath()),
                    EMPTY_TEXT,
                    mediaFile.getPath(),
                    false,
                    false,
                    false,
                    List.of(),
                    "Filename interpretation is not ready."
            );
        }

        return display;
    }

    private Scene transientScene(FilenamePreview preview) {
        final FilenameInterpretation interpretation =
                preview.matchResult().bestInterpretation();
        final Publisher publisher = publisher(interpretation.publisher());
        return new Scene(
                PREVIEW_SCENE_ID,
                preview.parsedFilename().titleCandidate(),
                publisher,
                preview.parsedFilename().releaseDate(),
                preview.parsedFilename().codeCandidate(),
                series(interpretation.series(), publisher),
                preview.parsedFilename().season(),
                preview.parsedFilename().episode(),
                performers(interpretation.performers()),
                List.of()
        );
    }

    private MovieSelectionResult movieSelection(FilenamePreview preview) {
        final FilenameInterpretation interpretation =
                preview.matchResult().bestInterpretation();
        final EntityMatch movieMatch = interpretation.movie();
        final Optional<Movie> selectedMovie = movieMatch == null
                || movieMatch.id() == null
                ? Optional.empty()
                : Optional.of(new Movie(
                        movieMatch.id(),
                        movieMatch.name(),
                        null,
                        publisher(interpretation.publisher()),
                        List.of(),
                        false,
                        List.of()
                ));
        return new MovieSelectionResult(
                PREVIEW_SCENE_ID,
                selectedMovie.isPresent()
                        ? MovieSelectionStatus.SELECTED
                        : MovieSelectionStatus.NONE,
                selectedMovie,
                selectedMovie.stream().toList(),
                selectedMovie.isPresent()
                        ? "Matched from filename interpretation."
                        : "No movie selected."
        );
    }

    private Publisher publisher(EntityMatch match) {
        return new Publisher(
                match.id(),
                match.name(),
                List.of()
        );
    }

    private Series series(EntityMatch match, Publisher publisher) {
        Series series = null;

        if (match != null && match.id() != null) {
            series = new Series(match.id(), match.name(), publisher);
        }

        return series;
    }

    private List<Performer> performers(List<EntityMatch> matches) {
        return matches.stream()
                .filter(match -> match.id() != null)
                .map(match -> new Performer(
                        match.id(),
                        match.name(),
                        List.of(),
                        PerformerCategory.valueOf(UNKNOWN_CATEGORY_NAME)
                ))
                .toList();
    }

    private List<String> resolvedPerformers(
            FilenameInterpretation interpretation) {

        List<String> performers = List.of();

        if (interpretation != null) {
            performers = interpretation.performers()
                    .stream()
                    .filter(match -> match.id() != null)
                    .map(EntityMatch::name)
                    .toList();
        }

        return performers;
    }

    private List<String> unmatchedPerformers(
            FilenameInterpretation interpretation) {

        List<String> performers = List.of();

        if (interpretation != null) {
            performers = interpretation.performers()
                    .stream()
                    .filter(match -> match.id() == null)
                    .map(EntityMatch::candidateText)
                    .toList();
        }

        return performers;
    }

    private String entityName(EntityMatch match) {
        return match == null || match.name() == null
                ? EMPTY_TEXT
                : match.name();
    }

    private String entitySource(EntityMatch match) {
        return match == null || match.source() == null
                ? EMPTY_TEXT
                : match.source().name();
    }

    private String entityCandidate(EntityMatch match) {
        return match == null || match.candidateText() == null
                ? EMPTY_TEXT
                : match.candidateText();
    }

    private String filename(Path path) {
        return path.getFileName().toString();
    }

    private String directory(Path path) {
        final Path parent = path.getParent();
        return parent == null ? EMPTY_TEXT : parent.toString();
    }

    private String resolution(MediaFile mediaFile) {
        return ReviewDisplayFormatter.resolution(
                mediaFile.getWidth(),
                mediaFile.getHeight()
        );
    }

    private String duration(Duration duration) {
        return ReviewDisplayFormatter.duration(duration);
    }

    private String nullToEmpty(String value) {
        return value == null ? EMPTY_TEXT : value;
    }
}

package media;

import model.Movie;
import model.Performer;
import model.Scene;
import service.MovieSelectionStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CanonicalMediaFilenameGenerator {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("'('yy'.'MM'.'dd')'");
    private static final int MAXIMUM_FILENAME_BYTES = 240;
    private static final String SEGMENT_SEPARATOR = " - ";
    private static final String PERFORMER_SEPARATOR = ", ";
    private static final String EMPTY_TEXT = "";
    private static final String UNSAFE_SEPARATOR_REGEX = "[/\\\\]+";
    private static final String REPEATED_WHITESPACE_REGEX = "\\s+";
    private static final String TRAILING_PORTABILITY_REGEX = "[ .]+$";
    private static final char NUL_CHARACTER = '\0';

    public CanonicalFilenameResult generate(
            CanonicalFilenameRequest request) {

        final Scene scene = request.scene();
        final String originalFilename = request.mediaFile()
                .getPath()
                .getFileName()
                .toString();
        final List<String> warnings = new ArrayList<>();
        CanonicalFilenameResult result;

        if (requiresMovieReview(request)) {
            result = result(
                    FilenameGenerationStatus.REVIEW_REQUIRED,
                    originalFilename,
                    originalFilename,
                    request.mediaFile().getPath(),
                    warnings,
                    "Original movie selection requires review.",
                    request.movieSelection().selectedMovie()
            );
        } else {
            result = generateFilename(request, scene, originalFilename, warnings);
        }

        return result;
    }

    private CanonicalFilenameResult generateFilename(
            CanonicalFilenameRequest request,
            Scene scene,
            String originalFilename,
            List<String> warnings) {

        final List<String> segments = new ArrayList<>();
        FilenameGenerationStatus status = FilenameGenerationStatus.READY;
        String error = EMPTY_TEXT;

        final String datePrefix = scene.getReleaseDate() == null
                ? EMPTY_TEXT
                : scene.getReleaseDate().format(DATE_FORMATTER) + " ";

        final Component publisherComponent =
                sanitizeComponent(scene.getPublisher().getName());
        final Component titleComponent = sanitizeComponent(scene.getTitle());

        if (!publisherComponent.valid()) {
            status = FilenameGenerationStatus.INVALID_METADATA;
            error = "Publisher name is not valid for a filename.";
        } else if (!titleComponent.valid()) {
            status = FilenameGenerationStatus.INVALID_METADATA;
            error = "Scene title is not valid for a filename.";
        } else {
            segments.add(publisherComponent.value());
            addSeries(scene, segments);
            addEpisodeOrCode(scene, segments, warnings);
            addMovie(request, segments, warnings);
            segments.add(titleComponent.value());
            addPerformers(scene, segments, warnings);
        }

        final String extension = extension(originalFilename);
        final String proposedFilename =
                status == FilenameGenerationStatus.INVALID_METADATA
                        ? originalFilename
                        : datePrefix
                                + String.join(SEGMENT_SEPARATOR, segments)
                                + extension;
        final Path proposedPath = proposedPath(
                request.mediaFile().getPath(),
                proposedFilename
        );

        if (status == FilenameGenerationStatus.READY
                && filenameByteLength(proposedFilename)
                        > MAXIMUM_FILENAME_BYTES) {
            status = FilenameGenerationStatus.TOO_LONG;
            error = "Generated filename exceeds "
                    + MAXIMUM_FILENAME_BYTES
                    + " UTF-8 bytes.";
        }

        if (status == FilenameGenerationStatus.READY
                && originalFilename.equals(proposedFilename)) {
            status = FilenameGenerationStatus.UNCHANGED;
        }

        return result(
                status,
                originalFilename,
                proposedFilename,
                proposedPath,
                warnings,
                error,
                request.movieSelection().selectedMovie()
        );
    }

    private boolean requiresMovieReview(CanonicalFilenameRequest request) {
        final MovieSelectionStatus status = request.movieSelection().status();
        return status == MovieSelectionStatus.REVIEW_REQUIRED
                || status == MovieSelectionStatus.INVALID_OVERRIDE;
    }

    private void addSeries(
            Scene scene,
            List<String> segments) {

        if (scene.getSeries() != null) {
            final Component seriesComponent =
                    sanitizeComponent(scene.getSeries().getTitle());

            if (seriesComponent.valid()) {
                segments.add(seriesComponent.value());
            }
        }
    }

    private void addEpisodeOrCode(
            Scene scene,
            List<String> segments,
            List<String> warnings) {

        final String season = normalizeText(scene.getSeason());
        final String episode = normalizeText(scene.getEpisode());
        final String code = normalizeText(scene.getCode());

        if (season != null && episode != null) {
            segments.add("S" + season + "E" + episode);

            if (code != null) {
                warnings.add(
                        "Scene has both season/episode and code; code was omitted."
                );
            }
        } else if (code != null) {
            final Component codeComponent = sanitizeComponent(code);

            if (codeComponent.valid()) {
                segments.add(codeComponent.value());
            }
        }
    }

    private void addMovie(
            CanonicalFilenameRequest request,
            List<String> segments,
            List<String> warnings) {

        final Optional<Movie> selectedMovie =
                request.movieSelection().selectedMovie();

        if (selectedMovie.isPresent()) {
            final Component movieComponent =
                    sanitizeComponent(selectedMovie.orElseThrow().getTitle());

            if (movieComponent.valid()) {
                segments.add(movieComponent.value());
            } else {
                warnings.add("Selected movie title was omitted.");
            }
        }
    }

    private void addPerformers(
            Scene scene,
            List<String> segments,
            List<String> warnings) {

        final List<String> performerNames = scene.getPerformers()
                .stream()
                .map(Performer::getMainName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(this::sanitizeComponent)
                .filter(Component::valid)
                .map(Component::value)
                .toList();

        if (performerNames.isEmpty()) {
            warnings.add("Scene has no performers.");
        } else {
            segments.add(String.join(PERFORMER_SEPARATOR, performerNames));
        }
    }

    private Component sanitizeComponent(String value) {
        Component component;

        if (value == null || value.indexOf(NUL_CHARACTER) >= 0) {
            component = new Component(false, EMPTY_TEXT);
        } else {
            final String sanitized = value
                    .replaceAll(UNSAFE_SEPARATOR_REGEX, " ")
                    .replaceAll(REPEATED_WHITESPACE_REGEX, " ")
                    .trim()
                    .replaceAll(TRAILING_PORTABILITY_REGEX, EMPTY_TEXT);
            component = new Component(
                    !sanitized.isBlank()
                            && !".".equals(sanitized)
                            && !"..".equals(sanitized),
                    sanitized
            );
        }

        return component;
    }

    private String normalizeText(String value) {
        String normalized = null;

        if (value != null) {
            final String trimmed = value.trim();

            if (!trimmed.isEmpty()) {
                normalized = trimmed;
            }
        }

        return normalized;
    }

    private String extension(String filename) {
        final int finalDotIndex = filename.lastIndexOf('.');
        String extension = EMPTY_TEXT;

        if (finalDotIndex > 0 && finalDotIndex < filename.length() - 1) {
            extension = filename.substring(finalDotIndex);
        }

        return extension;
    }

    private Path proposedPath(Path originalPath, String proposedFilename) {
        final Path parent = originalPath.getParent();
        Path proposedPath = Path.of(proposedFilename);

        if (parent != null) {
            proposedPath = parent.resolve(proposedFilename);
        }

        return proposedPath;
    }

    private int filenameByteLength(String filename) {
        return filename.getBytes(StandardCharsets.UTF_8).length;
    }

    private CanonicalFilenameResult result(
            FilenameGenerationStatus status,
            String originalFilename,
            String proposedFilename,
            Path proposedPath,
            List<String> warnings,
            String error,
            Optional<Movie> selectedMovie) {

        return new CanonicalFilenameResult(
                status,
                originalFilename,
                proposedFilename,
                proposedPath,
                warnings,
                error,
                selectedMovie
        );
    }

    private record Component(boolean valid, String value) {
    }
}

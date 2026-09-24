package media;

import model.Movie;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CanonicalFilenameResult(
        FilenameGenerationStatus status,
        String originalFilename,
        String proposedFilename,
        Path proposedPath,
        List<String> warnings,
        String error,
        Optional<Movie> selectedMovie) {

    public CanonicalFilenameResult {
        Objects.requireNonNull(status, "Status must not be null");
        Objects.requireNonNull(
                originalFilename,
                "Original filename must not be null"
        );
        Objects.requireNonNull(
                proposedFilename,
                "Proposed filename must not be null"
        );
        Objects.requireNonNull(proposedPath, "Proposed path must not be null");
        warnings = List.copyOf(Objects.requireNonNull(
                warnings,
                "Warnings must not be null"
        ));
        error = Objects.requireNonNull(error, "Error must not be null");
        selectedMovie = Objects.requireNonNull(
                selectedMovie,
                "Selected movie must not be null"
        );
    }
}

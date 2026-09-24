package service;

import model.Movie;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MediaRenamePreview(
        UUID mediaId,
        UUID sceneId,
        Path originalPath,
        Path proposedPath,
        Optional<Movie> selectedMovie,
        MovieSelectionStatus movieSelectionStatus,
        MediaRenameStatus status,
        List<String> warnings,
        String error) {

    public MediaRenamePreview {
        Objects.requireNonNull(mediaId, "Media ID must not be null");
        Objects.requireNonNull(sceneId, "Scene ID must not be null");
        Objects.requireNonNull(originalPath, "Original path must not be null");
        Objects.requireNonNull(proposedPath, "Proposed path must not be null");
        selectedMovie = Objects.requireNonNull(
                selectedMovie,
                "Selected movie must not be null"
        );
        Objects.requireNonNull(
                movieSelectionStatus,
                "Movie selection status must not be null"
        );
        Objects.requireNonNull(status, "Status must not be null");
        warnings = List.copyOf(Objects.requireNonNull(
                warnings,
                "Warnings must not be null"
        ));
        error = Objects.requireNonNull(error, "Error must not be null");
    }
}

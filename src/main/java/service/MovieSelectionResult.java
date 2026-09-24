package service;

import model.Movie;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MovieSelectionResult(
        UUID sceneId,
        MovieSelectionStatus status,
        Optional<Movie> selectedMovie,
        List<Movie> candidateMovies,
        String reason) {

    public MovieSelectionResult {
        Objects.requireNonNull(sceneId, "Scene ID must not be null");
        Objects.requireNonNull(status, "Status must not be null");
        selectedMovie = Objects.requireNonNull(
                selectedMovie,
                "Selected movie must not be null"
        );
        candidateMovies = List.copyOf(Objects.requireNonNull(
                candidateMovies,
                "Candidate movies must not be null"
        ));
        reason = Objects.requireNonNull(reason, "Reason must not be null");
    }
}

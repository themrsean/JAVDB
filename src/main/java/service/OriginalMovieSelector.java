package service;

import model.Movie;
import model.Scene;
import repository.MovieRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OriginalMovieSelector {
    private static final String NO_MOVIES_REASON =
            "Scene is not associated with any movies.";
    private static final String SINGLE_MOVIE_REASON =
            "Scene is associated with one movie.";
    private static final String EARLIEST_MOVIE_REASON =
            "Selected the uniquely earliest released associated movie.";
    private static final String MISSING_DATE_REASON =
            "At least one associated movie has no release date.";
    private static final String EARLIEST_TIE_REASON =
            "Multiple associated movies share the earliest release date.";
    private static final String UNKNOWN_OVERRIDE_REASON =
            "Movie override does not reference an existing movie.";
    private static final String UNRELATED_OVERRIDE_REASON =
            "Movie override is not associated with the scene.";
    private static final String OVERRIDE_REASON =
            "Selected explicit associated movie override.";

    private final MovieRepository movieRepository;

    public OriginalMovieSelector(MovieRepository movieRepository) {
        this.movieRepository = Objects.requireNonNull(
                movieRepository,
                "Movie repository must not be null"
        );
    }

    public MovieSelectionResult selectOriginalMovie(
            Scene scene,
            UUID explicitMovieOverride) throws SQLException {

        Objects.requireNonNull(scene, "Scene must not be null");

        final List<Movie> candidates = findAssociatedMovies(scene.getId());
        MovieSelectionResult result;

        if (explicitMovieOverride != null) {
            result = selectOverride(scene.getId(), explicitMovieOverride, candidates);
        } else {
            result = selectAutomatically(scene.getId(), candidates);
        }

        return result;
    }

    private MovieSelectionResult selectOverride(
            UUID sceneId,
            UUID explicitMovieOverride,
            List<Movie> candidates) throws SQLException {

        final Optional<Movie> overrideMovie =
                movieRepository.findById(explicitMovieOverride);
        MovieSelectionResult result;

        if (overrideMovie.isEmpty()) {
            result = new MovieSelectionResult(
                    sceneId,
                    MovieSelectionStatus.INVALID_OVERRIDE,
                    Optional.empty(),
                    candidates,
                    UNKNOWN_OVERRIDE_REASON
            );
        } else if (!containsScene(overrideMovie.orElseThrow(), sceneId)) {
            result = new MovieSelectionResult(
                    sceneId,
                    MovieSelectionStatus.INVALID_OVERRIDE,
                    Optional.empty(),
                    candidates,
                    UNRELATED_OVERRIDE_REASON
            );
        } else {
            result = new MovieSelectionResult(
                    sceneId,
                    MovieSelectionStatus.OVERRIDE_SELECTED,
                    overrideMovie,
                    candidates,
                    OVERRIDE_REASON
            );
        }

        return result;
    }

    private MovieSelectionResult selectAutomatically(
            UUID sceneId,
            List<Movie> candidates) {

        MovieSelectionResult result;

        if (candidates.isEmpty()) {
            result = new MovieSelectionResult(
                    sceneId,
                    MovieSelectionStatus.NONE,
                    Optional.empty(),
                    candidates,
                    NO_MOVIES_REASON
            );
        } else if (candidates.size() == 1) {
            result = new MovieSelectionResult(
                    sceneId,
                    MovieSelectionStatus.SELECTED,
                    Optional.of(candidates.getFirst()),
                    candidates,
                    SINGLE_MOVIE_REASON
            );
        } else if (hasMissingReleaseDate(candidates)) {
            result = new MovieSelectionResult(
                    sceneId,
                    MovieSelectionStatus.REVIEW_REQUIRED,
                    Optional.empty(),
                    candidates,
                    MISSING_DATE_REASON
            );
        } else {
            result = selectEarliestDatedMovie(sceneId, candidates);
        }

        return result;
    }

    private MovieSelectionResult selectEarliestDatedMovie(
            UUID sceneId,
            List<Movie> candidates) {

        final LocalDate earliestDate =
                candidates.getFirst().getReleaseDate();
        final List<Movie> earliestMovies = candidates.stream()
                .filter(movie -> earliestDate.equals(movie.getReleaseDate()))
                .toList();
        MovieSelectionResult result;

        if (earliestMovies.size() == 1) {
            result = new MovieSelectionResult(
                    sceneId,
                    MovieSelectionStatus.SELECTED,
                    Optional.of(earliestMovies.getFirst()),
                    candidates,
                    EARLIEST_MOVIE_REASON
            );
        } else {
            result = new MovieSelectionResult(
                    sceneId,
                    MovieSelectionStatus.REVIEW_REQUIRED,
                    Optional.empty(),
                    candidates,
                    EARLIEST_TIE_REASON
            );
        }

        return result;
    }

    private List<Movie> findAssociatedMovies(UUID sceneId) throws SQLException {
        return movieRepository.findAll()
                .stream()
                .filter(movie -> containsScene(movie, sceneId))
                .sorted(movieComparator())
                .toList();
    }

    private boolean containsScene(Movie movie, UUID sceneId) {
        return movie.getScenes()
                .stream()
                .anyMatch(movieScene -> sceneId.equals(movieScene.getId()));
    }

    private boolean hasMissingReleaseDate(List<Movie> candidates) {
        return candidates.stream()
                .anyMatch(movie -> movie.getReleaseDate() == null);
    }

    private Comparator<Movie> movieComparator() {
        return Comparator
                .comparing(
                        Movie::getReleaseDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(
                        Movie::getTitle,
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(movie -> movie.getId().toString());
    }
}

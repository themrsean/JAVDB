package service;

import model.Movie;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Explicit Movie creation for a context candidate; never infers any metadata. */
public final class ContextMovieResolutionService {
    private final ContextCandidateReviewService reviewService;
    private final EntityManagementService entityManagementService;

    public ContextMovieResolutionService(
            ContextCandidateReviewService reviewService,
            EntityManagementService entityManagementService) {
        this.reviewService = Objects.requireNonNull(reviewService);
        this.entityManagementService = Objects.requireNonNull(entityManagementService);
    }

    public Movie createMovie(String candidate, String title,
            LocalDate releaseDate, UUID publisherId, boolean compilation)
            throws SQLException {
        requireUnresolved(candidate);
        return entityManagementService.createMovie(title, releaseDate,
                publisherId, compilation);
    }

    private void requireUnresolved(String candidate) throws SQLException {
        final ContextCandidateStatus status = reviewService.currentStatus(candidate);
        if (status != ContextCandidateStatus.UNRESOLVED) {
            throw new ContextCandidateResolvedException(status);
        }
    }
}

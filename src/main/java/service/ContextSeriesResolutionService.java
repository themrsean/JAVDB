package service;

import model.Series;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Explicit Series creation for a context candidate; never infers its Publisher. */
public final class ContextSeriesResolutionService {
    private final ContextCandidateReviewService reviewService;
    private final EntityManagementService entityManagementService;

    public ContextSeriesResolutionService(
            ContextCandidateReviewService reviewService,
            EntityManagementService entityManagementService) {
        this.reviewService = Objects.requireNonNull(reviewService);
        this.entityManagementService = Objects.requireNonNull(entityManagementService);
    }

    public Series createSeries(String candidate, String title, UUID publisherId)
            throws SQLException {
        requireUnresolved(candidate);
        return entityManagementService.createSeries(title, publisherId);
    }

    private void requireUnresolved(String candidate) throws SQLException {
        final ContextCandidateStatus status = reviewService.currentStatus(candidate);
        if (status != ContextCandidateStatus.UNRESOLVED) {
            throw new ContextCandidateResolvedException(status);
        }
    }
}

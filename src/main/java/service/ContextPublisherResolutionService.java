package service;

import model.Publisher;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Explicit Publisher actions for a context candidate; never infers its role. */
public final class ContextPublisherResolutionService {
    private final ContextCandidateReviewService reviewService;
    private final EntityManagementService entityManagementService;

    public ContextPublisherResolutionService(
            ContextCandidateReviewService reviewService,
            EntityManagementService entityManagementService) {
        this.reviewService = Objects.requireNonNull(reviewService);
        this.entityManagementService = Objects.requireNonNull(entityManagementService);
    }

    public Publisher createPublisher(String candidate, String name,
            List<String> aliases) throws SQLException {
        requireUnresolved(candidate);
        return entityManagementService.createPublisher(name, aliases);
    }

    public Publisher mapPublisherAlias(String candidate, UUID publisherId)
            throws SQLException {
        requireUnresolved(candidate);
        return entityManagementService.addPublisherAlias(publisherId, candidate);
    }

    private void requireUnresolved(String candidate) throws SQLException {
        final ContextCandidateStatus status = reviewService.currentStatus(candidate);
        if (status != ContextCandidateStatus.UNRESOLVED) {
            throw new ContextCandidateResolvedException(status);
        }
    }
}

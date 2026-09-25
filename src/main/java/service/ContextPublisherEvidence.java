package service;

import java.util.Objects;
import java.util.UUID;

/**
 * Exact catalog-backed Publisher context observed alongside one context
 * candidate. It is evidence only and never classifies the candidate's role.
 */
public record ContextPublisherEvidence(
        UUID publisherId,
        String publisherName,
        int affectedFiles,
        int candidateBeforePublisherFiles,
        int candidateAfterPublisherFiles,
        int publisherOnBothSidesFiles,
        int directPublisherFiles,
        int seriesPublisherFiles,
        int moviePublisherFiles) {

    public ContextPublisherEvidence {
        Objects.requireNonNull(publisherId, "Publisher ID must not be null");
        Objects.requireNonNull(publisherName, "Publisher name must not be null");
    }
}

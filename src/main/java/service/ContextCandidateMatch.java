package service;

import java.util.Objects;
import java.util.UUID;

/** An exact catalog match displayed as evidence; it does not classify a segment. */
public record ContextCandidateMatch(
        ContextCandidateRole role,
        UUID entityId,
        String entityName,
        UUID publisherId,
        String publisherName) {
    public ContextCandidateMatch {
        Objects.requireNonNull(role, "Role must not be null");
        Objects.requireNonNull(entityId, "Entity ID must not be null");
        Objects.requireNonNull(entityName, "Entity name must not be null");
    }
    public String displayText() {
        if (publisherName == null || publisherName.isBlank()) {
            return role.displayName() + ": " + entityName;
        }
        return role.displayName() + ": " + entityName
                + " (Publisher: " + publisherName + ")";
    }
}

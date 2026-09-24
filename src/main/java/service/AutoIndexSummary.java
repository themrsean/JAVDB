package service;

public record AutoIndexSummary(
        int requested,
        int created,
        int wouldCreate,
        int reviewRequired,
        int ambiguous,
        int unresolved,
        int invalid,
        int alreadyAssigned,
        int failed) {
}

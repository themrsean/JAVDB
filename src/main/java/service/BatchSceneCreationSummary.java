package service;

public record BatchSceneCreationSummary(
        int requested,
        int created,
        int wouldCreate,
        int alreadyAssigned,
        int missing,
        int failed) {
}

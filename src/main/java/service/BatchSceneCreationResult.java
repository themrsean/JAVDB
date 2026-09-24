package service;

import java.util.List;

public record BatchSceneCreationResult(
        List<BatchSceneCreationFileResult> files,
        BatchSceneCreationSummary summary) {
}

package service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Read-only evidence collected for one role-neutral filename context segment. */
public record ContextCandidate(
        String text,
        int occurrences,
        Map<Integer, Integer> positionCounts,
        Map<Integer, Integer> contextLengthCounts,
        ContextCandidateStatus status,
        List<ContextCandidateMatch> matches,
        List<Path> representativePaths) {

    public ContextCandidate {
        positionCounts = Map.copyOf(positionCounts);
        contextLengthCounts = Map.copyOf(contextLengthCounts);
        matches = List.copyOf(matches);
        representativePaths = List.copyOf(representativePaths);
    }
}

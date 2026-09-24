package service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public record PerformerCandidate(
        String text,
        int mediaCount,
        PerformerCandidateResolution resolution,
        UUID performerId,
        String performerName,
        List<Path> representativePaths) {
    public PerformerCandidate { representativePaths = List.copyOf(representativePaths); }
}

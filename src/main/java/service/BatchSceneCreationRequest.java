package service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BatchSceneCreationRequest(
        List<UUID> mediaFileIds,
        UUID publisherId,
        UUID seriesId,
        LocalDate releaseDate,
        String season,
        String episode,
        List<UUID> performerIds,
        boolean dryRun,
        boolean failFast) {
}

package service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateSceneFromMediaRequest(
        UUID mediaFileId,
        String title,
        String code,
        LocalDate releaseDate,
        UUID publisherId,
        UUID seriesId,
        String season,
        String episode,
        List<UUID> performerIds) {
}

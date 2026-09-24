package service;

import java.nio.file.Path;
import java.util.UUID;

public record ReviewQueueItem(
        UUID mediaId,
        Path path,
        String filename,
        String directory,
        FilenameMatchStatus matchStatus,
        String proposedTitle,
        String publisherName,
        String publisherSource,
        String seriesTitle,
        String seriesSource,
        String movieTitle,
        String movieSource,
        String performers,
        String resolution,
        String duration,
        int warningCount) {
}

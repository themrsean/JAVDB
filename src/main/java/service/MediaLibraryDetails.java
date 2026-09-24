package service;

import media.FilenameParseStatus;
import repository.MediaAssignmentReference;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public record MediaLibraryDetails(
        UUID mediaId,
        Path path,
        boolean exists,
        String fileSize,
        String lastModified,
        String resolution,
        String duration,
        String contentHash,
        List<MediaAssignmentReference> scenes,
        List<MediaAssignmentReference> movies,
        FilenameParseStatus parseStatus,
        FilenameMatchStatus matchStatus,
        String bestInterpretation,
        List<String> warnings) {

    public MediaLibraryDetails {
        scenes = List.copyOf(scenes);
        movies = List.copyOf(movies);
        warnings = List.copyOf(warnings);
    }
}

package service;

import java.nio.file.Path;
import java.util.UUID;

public record MediaLibraryRow(
        UUID mediaId,
        Path path,
        String filename,
        String directory,
        String resolution,
        String duration,
        String fileSize,
        String lastModified,
        String assignmentSummary) {
}

package service;

import media.ParsedMediaFilename;

import java.nio.file.Path;
import java.util.UUID;

public record FilenamePreview(
        UUID mediaFileId,
        Path path,
        boolean assigned,
        ParsedMediaFilename parsedFilename,
        FilenameMatchResult matchResult) {
}

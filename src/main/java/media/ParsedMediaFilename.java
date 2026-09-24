package media;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public record ParsedMediaFilename(
        Path originalPath,
        String extensionlessFilename,
        String extension,
        LocalDate releaseDate,
        List<String> contextSegments,
        String season,
        String episode,
        String codeCandidate,
        String titleCandidate,
        List<String> performerCandidates,
        List<String> unresolvedSegments,
        FilenameParseStatus status,
        List<FilenameParseIssue> issues,
        List<String> warnings) {
}

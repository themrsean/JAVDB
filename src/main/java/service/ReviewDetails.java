package service;

import media.FilenameParseIssue;
import media.FilenameParseStatus;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ReviewDetails(
        UUID mediaId,
        Path path,
        String filename,
        String directory,
        long fileSize,
        long lastModifiedMillis,
        int width,
        int height,
        String duration,
        String contentHash,
        FilenameParseStatus parseStatus,
        LocalDate releaseDate,
        List<String> contextSegments,
        String proposedTitle,
        String code,
        String season,
        String episode,
        List<String> performerCandidates,
        List<String> resolvedPerformerNames,
        List<String> unmatchedPerformers,
        String publisherCandidate,
        String publisherResolution,
        String seriesCandidate,
        String seriesResolution,
        String movieCandidate,
        String movieResolution,
        FilenameInterpretation bestInterpretation,
        List<FilenameInterpretation> alternativeInterpretations,
        List<String> unresolvedSegments,
        List<FilenameParseIssue> parserIssues,
        List<String> parserWarnings,
        List<String> matcherWarnings,
        FilenameMatchStatus matchStatus,
        CanonicalRenameDisplay canonicalRename) {

    public ReviewDetails {
        contextSegments = List.copyOf(contextSegments);
        performerCandidates = List.copyOf(performerCandidates);
        resolvedPerformerNames = List.copyOf(resolvedPerformerNames);
        unmatchedPerformers = List.copyOf(unmatchedPerformers);
        alternativeInterpretations = List.copyOf(alternativeInterpretations);
        unresolvedSegments = List.copyOf(unresolvedSegments);
        parserIssues = List.copyOf(parserIssues);
        parserWarnings = List.copyOf(parserWarnings);
        matcherWarnings = List.copyOf(matcherWarnings);
    }
}

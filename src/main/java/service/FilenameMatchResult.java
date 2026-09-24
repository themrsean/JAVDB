package service;

import media.ParsedMediaFilename;

import java.util.List;

public record FilenameMatchResult(
        ParsedMediaFilename parsedFilename,
        FilenameMatchStatus status,
        FilenameInterpretation bestInterpretation,
        List<FilenameInterpretation> interpretations,
        List<String> warnings,
        List<String> errors) {
}

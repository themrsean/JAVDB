package service;

import java.util.List;

public record FilenameInterpretation(
        EntityMatch publisher,
        EntityMatch series,
        EntityMatch movie,
        List<EntityMatch> performers,
        List<String> unresolvedSegments,
        int score) {
}

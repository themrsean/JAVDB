package service;

import java.util.UUID;

public record EntityMatch(
        UUID id,
        String name,
        String candidateText,
        MatchSource source,
        UUID publisherId) {
}

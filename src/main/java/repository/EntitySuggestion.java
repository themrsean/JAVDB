package repository;

import java.util.UUID;

public record EntitySuggestion(
        UUID id,
        String displayName,
        String matchedText,
        MatchField matchField,
        MatchRank rank,
        UUID publisherId) {
}

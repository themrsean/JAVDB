package ui.control;

import repository.EntitySuggestion;
import repository.MatchField;
import repository.MatchRank;

import java.util.Objects;
import java.util.UUID;

public record EntitySuggestionDisplay(
        UUID id,
        String displayName,
        String matchedText,
        MatchField matchField,
        MatchRank rank,
        UUID publisherId) {

    public static EntitySuggestionDisplay from(EntitySuggestion suggestion) {
        Objects.requireNonNull(
                suggestion,
                "Entity suggestion must not be null"
        );

        return new EntitySuggestionDisplay(
                suggestion.id(),
                suggestion.displayName(),
                suggestion.matchedText(),
                suggestion.matchField(),
                suggestion.rank(),
                suggestion.publisherId()
        );
    }
}

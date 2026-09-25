package ui.performer;

import service.PerformerCandidate;
import service.PerformerCandidateResolution;

import java.util.Locale;

final class PerformerCandidateFilterState {
    static final String INVALID_MINIMUM_MESSAGE =
            "Minimum occurrences must be a nonnegative integer.";

    private int minimumOccurrences;
    private String validationMessage = "";

    boolean updateMinimumOccurrences(String text) {
        final String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            minimumOccurrences = 0;
            validationMessage = "";
            return true;
        }
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed >= 0) {
                minimumOccurrences = parsed;
                validationMessage = "";
                return true;
            }
        } catch (NumberFormatException ignored) {
            // The existing visible predicate remains in effect.
        }
        validationMessage = INVALID_MINIMUM_MESSAGE;
        return false;
    }

    int minimumOccurrences() {
        return minimumOccurrences;
    }

    String validationMessage() {
        return validationMessage;
    }

    boolean matches(PerformerCandidate candidate, String candidateText,
            boolean unresolvedOnly) {
        final String query = candidateText == null ? ""
                : candidateText.toLowerCase(Locale.ROOT);
        return (!unresolvedOnly || candidate.resolution()
                == PerformerCandidateResolution.UNRESOLVED)
                && candidate.mediaCount() >= minimumOccurrences
                && candidate.text().toLowerCase(Locale.ROOT).contains(query);
    }
}

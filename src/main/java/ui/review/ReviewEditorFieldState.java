package ui.review;

import service.EntityMatch;
import service.FilenameInterpretation;
import service.FilenameInterpretationConsensus;
import service.MatchSource;
import service.ReviewDetails;

import java.util.List;

record ReviewEditorFieldState(
        String publisher,
        String series,
        String movie,
        List<String> performers) {

    static ReviewEditorFieldState from(ReviewDetails details) {
        final FilenameInterpretation interpretation =
                FilenameInterpretationConsensus.initial(
                        details.matchStatus(),
                        details.bestInterpretation(),
                        details.alternativeInterpretations()
                );
        return new ReviewEditorFieldState(
                fieldText(interpretation == null
                        ? null : interpretation.publisher()),
                fieldText(interpretation == null
                        ? null : interpretation.series()),
                fieldText(interpretation == null
                        ? null : interpretation.movie()),
                details.resolvedPerformerNames()
        );
    }

    static ReviewEditorFieldState from(FilenameInterpretation interpretation) {
        return new ReviewEditorFieldState(
                fieldText(interpretation.publisher()),
                fieldText(interpretation.series()),
                fieldText(interpretation.movie()),
                interpretation.performers().stream()
                        .filter(match -> match.id() != null)
                        .map(EntityMatch::name)
                        .toList()
        );
    }

    static String alternativeText(FilenameInterpretation interpretation) {
        return "Publisher=" + alternativeEntityText(interpretation.publisher())
                + ", Series=" + alternativeEntityText(interpretation.series())
                + ", Movie=" + alternativeEntityText(interpretation.movie());
    }

    private static String fieldText(EntityMatch match) {
        if (match == null) {
            return "";
        }
        if (match.id() != null && match.name() != null) {
            return match.name();
        }
        return match.source() == MatchSource.UNMATCHED
                && match.candidateText() != null
                ? match.candidateText()
                : "";
    }

    private static String alternativeEntityText(EntityMatch match) {
        final String text = fieldText(match);
        return match != null && match.source() == MatchSource.UNMATCHED
                && !text.isBlank() ? text + " (unresolved)" : text;
    }
}

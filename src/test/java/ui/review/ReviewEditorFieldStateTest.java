package ui.review;

import media.FilenameParseStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.CanonicalRenameDisplay;
import service.EntityMatch;
import service.FilenameInterpretation;
import service.FilenameMatchStatus;
import service.MatchSource;
import service.ReviewDetails;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

class ReviewEditorFieldStateTest {
    private static final UUID PUBLISHER_ID = UUID.fromString(
            "11111111-abcd-1111-abcd-111111111111"
    );
    private static final UUID SERIES_ID = UUID.fromString(
            "22222222-abcd-2222-abcd-222222222222"
    );

    @Test
    @DisplayName("Row field state never carries absent series or movie values")
    void rowFieldStateClearsAbsentValues() {
        final FilenameInterpretation first = interpretation(
                resolved(PUBLISHER_ID, "Brazzers"),
                resolved(SERIES_ID, "BigWetButts"),
                unmatched("Asspirations 2"),
                250
        );
        final FilenameInterpretation second = interpretation(
                resolved(PUBLISHER_ID, "EvilAngel"),
                absent(),
                absent(),
                100
        );

        final ReviewEditorFieldState rowA = ReviewEditorFieldState.from(
                details(first, FilenameMatchStatus.UNRESOLVED, List.of(first))
        );
        final ReviewEditorFieldState rowB = ReviewEditorFieldState.from(
                details(second, FilenameMatchStatus.READY, List.of(second))
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals("Brazzers", rowA.publisher()),
                () -> Assertions.assertEquals("BigWetButts", rowA.series()),
                () -> Assertions.assertEquals("Asspirations 2", rowA.movie()),
                () -> Assertions.assertEquals("EvilAngel", rowB.publisher()),
                () -> Assertions.assertTrue(rowB.series().isBlank()),
                () -> Assertions.assertTrue(rowB.movie().isBlank())
        );
    }

    @Test
    @DisplayName("Ambiguous role candidates leave only common publisher populated")
    void ambiguousRoleCandidatesUseConsensus() {
        final FilenameInterpretation series = interpretation(
                resolved(PUBLISHER_ID, "Brazzers"),
                unmatched("Unknown Name"),
                absent(),
                100
        );
        final FilenameInterpretation movie = interpretation(
                resolved(PUBLISHER_ID, "Brazzers"),
                absent(),
                unmatched("Unknown Name"),
                100
        );

        final ReviewEditorFieldState state = ReviewEditorFieldState.from(
                details(series, FilenameMatchStatus.AMBIGUOUS,
                        List.of(series, movie))
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals("Brazzers", state.publisher()),
                () -> Assertions.assertTrue(state.series().isBlank()),
                () -> Assertions.assertTrue(state.movie().isBlank()),
                () -> Assertions.assertNotEquals(
                        ReviewEditorFieldState.alternativeText(series),
                        ReviewEditorFieldState.alternativeText(movie)
                ),
                () -> Assertions.assertTrue(
                        ReviewEditorFieldState.alternativeText(series)
                                .contains("Series=Unknown Name (unresolved)")),
                () -> Assertions.assertTrue(
                        ReviewEditorFieldState.alternativeText(movie)
                                .contains("Movie=Unknown Name (unresolved)"))
        );
    }

    private FilenameInterpretation interpretation(EntityMatch publisher,
            EntityMatch series, EntityMatch movie, int score) {
        return new FilenameInterpretation(publisher, series, movie, List.of(),
                List.of(), score);
    }

    private EntityMatch resolved(UUID id, String name) {
        return new EntityMatch(id, name, name,
                MatchSource.EXPLICIT_PRIMARY_NAME, null);
    }

    private EntityMatch unmatched(String text) {
        return new EntityMatch(null, null, text, MatchSource.UNMATCHED,
                PUBLISHER_ID);
    }

    private EntityMatch absent() {
        return new EntityMatch(null, null, null, MatchSource.ABSENT, null);
    }

    private ReviewDetails details(FilenameInterpretation best,
            FilenameMatchStatus status,
            List<FilenameInterpretation> alternatives) {
        return new ReviewDetails(
                UUID.randomUUID(), Path.of("media.mp4"), "media.mp4", "",
                1L, 1L, 1, 1, "", "", FilenameParseStatus.VALID,
                null, List.of(), "Scene", "", "", "", List.of(),
                List.of(), List.of(), "", "", "", "", "", "", best,
                alternatives, List.of(), List.of(), List.of(), List.of(), status,
                new CanonicalRenameDisplay("REVIEW_REQUIRED", "media.mp4", "",
                        Path.of("media.mp4"), false, false, false, List.of(), "")
        );
    }
}

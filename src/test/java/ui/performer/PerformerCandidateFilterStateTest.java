package ui.performer;

import model.PerformerCategory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import service.PerformerCandidate;
import service.PerformerCandidateResolution;

import java.util.List;

class PerformerCandidateFilterStateTest {
    @Test
    void blankAndZeroAreValidMinimumOccurrences() {
        final PerformerCandidateFilterState state = new PerformerCandidateFilterState();

        Assertions.assertAll(
                () -> Assertions.assertTrue(state.updateMinimumOccurrences("")),
                () -> Assertions.assertEquals(0, state.minimumOccurrences()),
                () -> Assertions.assertTrue(state.updateMinimumOccurrences("0")),
                () -> Assertions.assertEquals(0, state.minimumOccurrences())
        );
    }

    @Test
    void validMinimumFiltersAtTheExactOccurrenceBoundary() {
        final PerformerCandidateFilterState state = new PerformerCandidateFilterState();
        state.updateMinimumOccurrences("10");

        Assertions.assertAll(
                () -> Assertions.assertFalse(state.matches(candidate("Nine", 9), "", false)),
                () -> Assertions.assertTrue(state.matches(candidate("Ten", 10), "", false))
        );
    }

    @Test
    void invalidMinimumKeepsTheLastValidPredicateUntilCorrected() {
        final PerformerCandidateFilterState state = new PerformerCandidateFilterState();
        state.updateMinimumOccurrences("10");

        Assertions.assertAll(
                () -> Assertions.assertFalse(state.updateMinimumOccurrences("abc")),
                () -> Assertions.assertEquals(10, state.minimumOccurrences()),
                () -> Assertions.assertEquals(
                        PerformerCandidateFilterState.INVALID_MINIMUM_MESSAGE,
                        state.validationMessage()),
                () -> Assertions.assertFalse(state.matches(candidate("Nine", 9), "", false)),
                () -> Assertions.assertFalse(state.updateMinimumOccurrences("1.5")),
                () -> Assertions.assertFalse(state.updateMinimumOccurrences("-1")),
                () -> Assertions.assertTrue(state.updateMinimumOccurrences("1")),
                () -> Assertions.assertTrue(state.matches(candidate("Nine", 9), "", false))
        );
    }

    @Test
    void unresolvedOnlyAndCandidateTextStillFilterTheLoadedList() {
        final PerformerCandidateFilterState state = new PerformerCandidateFilterState();

        Assertions.assertAll(
                () -> Assertions.assertTrue(state.matches(candidate("Alice", 1), "ali", true)),
                () -> Assertions.assertFalse(state.matches(resolvedCandidate("Alice"), "ali", true)),
                () -> Assertions.assertTrue(state.matches(resolvedCandidate("Alice"), "ali", false)),
                () -> Assertions.assertFalse(state.matches(candidate("Alice", 1), "bob", false))
        );
    }

    @Test
    void categoryChoicesDefaultToUnknownAndExposeEveryEnumValue() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(PerformerCategory.UNKNOWN,
                        PerformerCandidateController.defaultCategory()),
                () -> Assertions.assertEquals(List.of(PerformerCategory.values()),
                        PerformerCandidateController.availableCategories())
        );
    }

    private PerformerCandidate candidate(String text, int occurrences) {
        return new PerformerCandidate(text, occurrences,
                PerformerCandidateResolution.UNRESOLVED, null, "", List.of());
    }

    private PerformerCandidate resolvedCandidate(String text) {
        return new PerformerCandidate(text, 1,
                PerformerCandidateResolution.PRIMARY_MATCH, null, text, List.of());
    }
}

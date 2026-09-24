package ui.review;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewNavigationGuardTest {
    @Test
    @DisplayName("Clean editor allows navigation")
    void cleanEditorAllowsNavigation() {
        final ReviewNavigationGuard guard =
                new ReviewNavigationGuard(() -> false);

        Assertions.assertTrue(guard.mayNavigateAway(false));
    }

    @Test
    @DisplayName("Dirty editor can keep editing")
    void dirtyEditorCanKeepEditing() {
        final ReviewNavigationGuard guard =
                new ReviewNavigationGuard(() -> false);

        Assertions.assertFalse(guard.mayNavigateAway(true));
    }

    @Test
    @DisplayName("Dirty editor can discard changes")
    void dirtyEditorCanDiscardChanges() {
        final ReviewNavigationGuard guard =
                new ReviewNavigationGuard(() -> true);

        Assertions.assertTrue(guard.mayNavigateAway(true));
    }

    @Test
    @DisplayName("Next index preserves current position when possible")
    void nextIndexPreservesCurrentPositionWhenPossible() {
        Assertions.assertEquals(
                1,
                ReviewNavigationGuard.nextIndexAfterRemoval(1, 3)
        );
    }

    @Test
    @DisplayName("Next index selects previous final row at page end")
    void nextIndexSelectsPreviousFinalRowAtPageEnd() {
        Assertions.assertEquals(
                1,
                ReviewNavigationGuard.nextIndexAfterRemoval(2, 3)
        );
    }

    @Test
    @DisplayName("Next index reports no selection when page becomes empty")
    void nextIndexReportsNoSelectionWhenPageBecomesEmpty() {
        Assertions.assertEquals(
                ReviewNavigationGuard.NO_SELECTION,
                ReviewNavigationGuard.nextIndexAfterRemoval(0, 1)
        );
    }
}

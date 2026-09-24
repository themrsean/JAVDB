package ui.review;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class ReviewNavigationGuard {
    public static final int NO_SELECTION = -1;
    private static final int FIRST_INDEX = 0;
    private static final int ONE_REMOVED = 1;

    private final BooleanSupplier discardConfirmation;

    public ReviewNavigationGuard(BooleanSupplier discardConfirmation) {
        this.discardConfirmation = Objects.requireNonNull(
                discardConfirmation,
                "Discard confirmation must not be null"
        );
    }

    public boolean mayNavigateAway(boolean dirty) {
        return !dirty || discardConfirmation.getAsBoolean();
    }

    public static int nextIndexAfterRemoval(
            int removedIndex,
            int previousSize) {

        int nextIndex = NO_SELECTION;
        final int remainingSize = previousSize - ONE_REMOVED;

        if (remainingSize > 0) {
            nextIndex = Math.min(removedIndex, remainingSize - ONE_REMOVED);
            nextIndex = Math.max(FIRST_INDEX, nextIndex);
        }

        return nextIndex;
    }
}

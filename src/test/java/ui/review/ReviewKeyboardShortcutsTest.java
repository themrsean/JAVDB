package ui.review;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewKeyboardShortcutsTest {
    @Test
    @DisplayName("Save shortcuts are registered")
    void saveShortcutsAreRegistered() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        KeyCode.S,
                        ReviewKeyboardShortcuts.SAVE_WITHOUT_RENAME
                                .getCode()
                ),
                () -> Assertions.assertEquals(
                        KeyCombination.ModifierValue.UP,
                        ReviewKeyboardShortcuts.SAVE_WITHOUT_RENAME
                                .getShift()
                ),
                () -> Assertions.assertEquals(
                        KeyCombination.ModifierValue.DOWN,
                        ReviewKeyboardShortcuts.SAVE_AND_RENAME
                                .getShift()
                ),
                () -> Assertions.assertEquals(
                        KeyCombination.ModifierValue.DOWN,
                        ReviewKeyboardShortcuts.SAVE_AS_NEEDS_REVIEW
                                .getAlt()
                )
        );
    }

    @Test
    @DisplayName("Navigation shortcuts are registered")
    void navigationShortcutsAreRegistered() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        KeyCode.DOWN,
                        ReviewKeyboardShortcuts.NEXT_ITEM.getCode()
                ),
                () -> Assertions.assertEquals(
                        KeyCode.UP,
                        ReviewKeyboardShortcuts.PREVIOUS_ITEM.getCode()
                )
        );
    }
}

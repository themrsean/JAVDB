package ui.review;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public final class ReviewKeyboardShortcuts {
    public static final KeyCodeCombination SAVE_WITHOUT_RENAME =
            new KeyCodeCombination(
                    KeyCode.S,
                    KeyCombination.SHORTCUT_DOWN
            );
    public static final KeyCodeCombination SAVE_AND_RENAME =
            new KeyCodeCombination(
                    KeyCode.S,
                    KeyCombination.SHORTCUT_DOWN,
                    KeyCombination.SHIFT_DOWN
            );
    public static final KeyCodeCombination SAVE_AS_NEEDS_REVIEW =
            new KeyCodeCombination(
                    KeyCode.S,
                    KeyCombination.SHORTCUT_DOWN,
                    KeyCombination.ALT_DOWN
            );
    public static final KeyCodeCombination NEXT_ITEM =
            new KeyCodeCombination(
                    KeyCode.DOWN,
                    KeyCombination.SHORTCUT_DOWN
            );
    public static final KeyCodeCombination PREVIOUS_ITEM =
            new KeyCodeCombination(
                    KeyCode.UP,
                    KeyCombination.SHORTCUT_DOWN
            );

    private ReviewKeyboardShortcuts() {
    }
}

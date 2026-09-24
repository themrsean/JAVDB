package media;

import java.time.Duration;

public record MediaMetadata(Duration duration, int width, int height) {
    public MediaMetadata {
        if (duration != null && duration.isNegative()) {
            throw new IllegalArgumentException(
                    "Duration must not be negative."
            );
        }

        if (width < 0) {
            throw new IllegalArgumentException(
                    "Width must not be negative."
            );
        }

        if (height < 0) {
            throw new IllegalArgumentException(
                    "Height must not be negative."
            );
        }
    }
}

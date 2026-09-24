package ui.review;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

public final class ReviewDisplayFormatter {
    private static final String EMPTY_TEXT = "";
    private static final String RESOLUTION_SEPARATOR = "x";
    private static final String BYTE_SUFFIX = " bytes";
    private static final String MILLISECOND_SUFFIX = " ms";
    private static final int ABSENT_DIMENSION_VALUE = 0;

    private ReviewDisplayFormatter() {
    }

    public static String resolution(int width, int height) {
        String text = EMPTY_TEXT;

        if (width > ABSENT_DIMENSION_VALUE && height > ABSENT_DIMENSION_VALUE) {
            text = width + RESOLUTION_SEPARATOR + height;
        }

        return text;
    }

    public static String duration(Duration duration) {
        return duration == null
                ? EMPTY_TEXT
                : duration.toMillis() + MILLISECOND_SUFFIX;
    }

    public static String fileSize(long bytes) {
        return bytes + BYTE_SUFFIX;
    }

    public static String optional(String value) {
        return value == null ? EMPTY_TEXT : value;
    }

    public static String date(LocalDate date) {
        return date == null ? EMPTY_TEXT : date.toString();
    }

    public static String uuid(UUID id) {
        return id == null ? EMPTY_TEXT : id.toString();
    }
}

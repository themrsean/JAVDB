package ui.review;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

class ReviewDisplayFormatterTest {
    private static final long BYTE_COUNT = 1_536L;
    private static final int WIDTH = 1_920;
    private static final int HEIGHT = 1_080;
    private static final long DURATION_MILLIS = 90_000L;

    @Test
    @DisplayName("Formats resolution deterministically")
    void formatsResolutionDeterministically() {
        Assertions.assertEquals(
                "1920x1080",
                ReviewDisplayFormatter.resolution(WIDTH, HEIGHT)
        );
    }

    @Test
    @DisplayName("Formats absent resolution as empty text")
    void formatsAbsentResolutionAsEmptyText() {
        Assertions.assertEquals(
                "",
                ReviewDisplayFormatter.resolution(0, HEIGHT)
        );
    }

    @Test
    @DisplayName("Formats duration in milliseconds")
    void formatsDurationInMilliseconds() {
        Assertions.assertEquals(
                "90000 ms",
                ReviewDisplayFormatter.duration(
                        Duration.ofMillis(DURATION_MILLIS)
                )
        );
    }

    @Test
    @DisplayName("Formats absent duration as empty text")
    void formatsAbsentDurationAsEmptyText() {
        Assertions.assertEquals("", ReviewDisplayFormatter.duration(null));
    }

    @Test
    @DisplayName("Formats byte counts with exact bytes")
    void formatsByteCountsWithExactBytes() {
        Assertions.assertEquals(
                "1536 bytes",
                ReviewDisplayFormatter.fileSize(BYTE_COUNT)
        );
    }

    @Test
    @DisplayName("Formats optional values")
    void formatsOptionalValues() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "",
                        ReviewDisplayFormatter.optional(null)
                ),
                () -> Assertions.assertEquals(
                        "value",
                        ReviewDisplayFormatter.optional("value")
                )
        );
    }

    @Test
    @DisplayName("Formats dates and UUIDs as data values")
    void formatsDatesAndUuidsAsDataValues() {
        final UUID id = UUID.fromString(
                "11111111-1111-1111-1111-111111111111"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "2026-07-16",
                        ReviewDisplayFormatter.date(
                                LocalDate.of(2026, 7, 16)
                        )
                ),
                () -> Assertions.assertEquals(
                        id.toString(),
                        ReviewDisplayFormatter.uuid(id)
                )
        );
    }
}

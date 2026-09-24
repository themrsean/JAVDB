package ui.library;

import model.MediaFile;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MediaLibraryDisplayFormatter {
    private static final long KIBIBYTE = 1_024L;
    private static final long MEBIBYTE = KIBIBYTE * KIBIBYTE;
    private static final long GIBIBYTE = MEBIBYTE * KIBIBYTE;
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MediaLibraryDisplayFormatter() {
    }

    public static String resolution(MediaFile mediaFile) {
        return mediaFile.getWidth() > 0 && mediaFile.getHeight() > 0
                ? mediaFile.getWidth() + "x" + mediaFile.getHeight()
                : "Missing";
    }

    public static String duration(Duration duration) {
        if (duration == null) {
            return "Missing";
        }
        final long seconds = duration.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d:%02d",
                seconds / 3_600, (seconds % 3_600) / 60, seconds % 60);
    }

    public static String fileSize(long bytes) {
        if (bytes >= GIBIBYTE) {
            return decimal(bytes, GIBIBYTE, "GiB");
        }
        if (bytes >= MEBIBYTE) {
            return decimal(bytes, MEBIBYTE, "MiB");
        }
        if (bytes >= KIBIBYTE) {
            return decimal(bytes, KIBIBYTE, "KiB");
        }
        return bytes + " B";
    }

    public static String lastModified(long millis) {
        return millis <= 0 ? "Missing" : DATE_TIME.format(
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        );
    }

    private static String decimal(long bytes, long unit, String suffix) {
        return String.format(Locale.ROOT, "%.1f %s", (double) bytes / unit, suffix);
    }
}

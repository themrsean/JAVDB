package media;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FfprobeMediaMetadataProbe
        implements MediaMetadataProbe {
    private static final String DEFAULT_EXECUTABLE = "ffprobe";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int SUCCESS_EXIT_STATUS = 0;
    private static final int KEY_VALUE_PART_COUNT = 2;
    private static final int KEY_INDEX = 0;
    private static final int VALUE_INDEX = 1;
    private static final long MILLIS_PER_SECOND = 1_000L;

    private final String executable;
    private final MediaProcessRunner processRunner;

    public FfprobeMediaMetadataProbe() {
        this(
                DEFAULT_EXECUTABLE,
                DEFAULT_TIMEOUT,
                new ProcessBuilderMediaProcessRunner(DEFAULT_TIMEOUT)
        );
    }

    public FfprobeMediaMetadataProbe(MediaProcessRunner processRunner) {
        this(DEFAULT_EXECUTABLE, DEFAULT_TIMEOUT, processRunner);
    }

    public FfprobeMediaMetadataProbe(
            String executable,
            Duration timeout,
            MediaProcessRunner processRunner) {

        this.executable = Objects.requireNonNull(
                executable,
                "Executable must not be null"
        );
        Objects.requireNonNull(timeout, "Timeout must not be null");
        this.processRunner = Objects.requireNonNull(
                processRunner,
                "Process runner must not be null"
        );
    }

    @Override
    public MediaMetadata probe(Path mediaPath)
            throws IOException, MediaProbeException {

        Objects.requireNonNull(mediaPath, "Media path must not be null");

        final ProcessResult result;

        try {
            result = processRunner.run(command(mediaPath));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaProbeException(
                    "ffprobe was interrupted.",
                    exception
            );
        } catch (IOException exception) {
            throw new MediaProbeException(
                    "Could not start ffprobe: " + exception.getMessage(),
                    exception
            );
        }

        if (result.timedOut()) {
            throw new MediaProbeException("ffprobe timed out.");
        }

        if (result.exitStatus() != SUCCESS_EXIT_STATUS) {
            throw new MediaProbeException(
                    "ffprobe failed: " + result.error()
            );
        }

        return parse(result.output());
    }

    private List<String> command(Path mediaPath) {
        return List.of(
                executable,
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=width,height",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=0",
                mediaPath.toString()
        );
    }

    private MediaMetadata parse(String output) throws MediaProbeException {
        final Map<String, String> values = new HashMap<>();

        for (String line : output.split("\\R")) {
            final String[] parts = line.split("=", KEY_VALUE_PART_COUNT);

            if (parts.length == KEY_VALUE_PART_COUNT) {
                values.put(parts[KEY_INDEX], parts[VALUE_INDEX]);
            }
        }

        final int width = parseRequiredInt(values, "width");
        final int height = parseRequiredInt(values, "height");
        final Duration duration = parseRequiredDuration(values);

        return new MediaMetadata(duration, width, height);
    }

    private int parseRequiredInt(
            Map<String, String> values,
            String key) throws MediaProbeException {

        final String value = values.get(key);

        if (value == null || value.isBlank()) {
            throw new MediaProbeException("ffprobe output missing " + key);
        }

        final int parsedValue;

        try {
            parsedValue = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new MediaProbeException(
                    "Invalid ffprobe " + key + ": " + value,
                    exception
            );
        }

        if (parsedValue < 0) {
            throw new MediaProbeException(
                    "Invalid ffprobe " + key + ": " + value
            );
        }

        return parsedValue;
    }

    private Duration parseRequiredDuration(
            Map<String, String> values) throws MediaProbeException {

        final String value = values.get("duration");

        if (value == null || value.isBlank()) {
            throw new MediaProbeException(
                    "ffprobe output missing duration"
            );
        }

        final long millis;

        try {
            millis = new BigDecimal(value)
                    .multiply(BigDecimal.valueOf(MILLIS_PER_SECOND))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new MediaProbeException(
                    "Invalid ffprobe duration: " + value,
                    exception
            );
        }

        if (millis < 0) {
            throw new MediaProbeException(
                    "Invalid ffprobe duration: " + value
            );
        }

        return Duration.ofMillis(millis);
    }
}

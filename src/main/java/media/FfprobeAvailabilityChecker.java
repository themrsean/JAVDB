package media;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class FfprobeAvailabilityChecker {
    private static final String DEFAULT_EXECUTABLE = "ffprobe";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int SUCCESS_EXIT_STATUS = 0;

    private final MediaProcessRunner processRunner;

    public FfprobeAvailabilityChecker() {
        this(new ProcessBuilderMediaProcessRunner(DEFAULT_TIMEOUT));
    }

    public FfprobeAvailabilityChecker(MediaProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(
                processRunner,
                "Process runner must not be null"
        );
    }

    public String check(String executable) throws MediaProbeException {
        final String effectiveExecutable =
                executable == null || executable.isBlank()
                        ? DEFAULT_EXECUTABLE
                        : executable;
        final ProcessResult result;

        try {
            result = processRunner.run(List.of(effectiveExecutable, "-version"));
        } catch (IOException exception) {
            throw new MediaProbeException(
                    "Could not start ffprobe: " + exception.getMessage(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MediaProbeException(
                    "ffprobe check was interrupted.",
                    exception
            );
        }

        if (result.timedOut()) {
            throw new MediaProbeException("ffprobe check timed out.");
        }

        if (result.exitStatus() != SUCCESS_EXIT_STATUS) {
            throw new MediaProbeException(
                    "ffprobe check failed: " + result.error()
            );
        }

        return result.output().strip();
    }
}

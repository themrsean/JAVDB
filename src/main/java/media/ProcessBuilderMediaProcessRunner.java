package media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ProcessBuilderMediaProcessRunner
        implements MediaProcessRunner {
    private static final int SUCCESS_EXIT_STATUS = 0;

    private final Duration timeout;

    public ProcessBuilderMediaProcessRunner(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public ProcessResult run(List<String> command)
            throws IOException, InterruptedException {

        final Process process = new ProcessBuilder(command).start();
        final boolean finished = process.waitFor(
                timeout.toMillis(),
                TimeUnit.MILLISECONDS
        );

        ProcessResult result;

        if (finished) {
            final String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            final String error = new String(
                    process.getErrorStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            result = new ProcessResult(
                    process.exitValue(),
                    output,
                    error,
                    false
            );
        } else {
            process.destroyForcibly();
            result = new ProcessResult(
                    SUCCESS_EXIT_STATUS,
                    "",
                    "",
                    true
            );
        }

        return result;
    }
}

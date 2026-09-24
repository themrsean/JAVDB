package media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

class FfprobeMediaMetadataProbeTest {
    private static final Path MEDIA_PATH =
            Path.of("/tmp/video with spaces.mp4");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final long DURATION_MILLIS = 1_234L;

    @Test
    @DisplayName("Probe builds ffprobe command with override and path")
    void probeBuildsFfprobeCommandWithOverrideAndPath()
            throws Exception {

        final FakeProcessRunner runner = new FakeProcessRunner(
                new ProcessResult(
                        0,
                        "width=1920\nheight=1080\nduration=1.234\n",
                        "",
                        false
                )
        );
        final FfprobeMediaMetadataProbe probe =
                new FfprobeMediaMetadataProbe(
                        "/opt/ffprobe",
                        TIMEOUT,
                        runner
                );

        final MediaMetadata metadata = probe.probe(MEDIA_PATH);

        Assertions.assertAll(
                () -> Assertions.assertEquals(WIDTH, metadata.width()),
                () -> Assertions.assertEquals(HEIGHT, metadata.height()),
                () -> Assertions.assertEquals(
                        DURATION_MILLIS,
                        metadata.duration().toMillis()
                ),
                () -> Assertions.assertEquals(
                        "/opt/ffprobe",
                        runner.command.getFirst()
                ),
                () -> Assertions.assertEquals(
                        MEDIA_PATH.toString(),
                        runner.command.getLast()
                )
        );
    }

    @Test
    @DisplayName("Probe parses fields in unexpected order with extra output")
    void probeParsesFieldsInUnexpectedOrderWithExtraOutput()
            throws Exception {

        final FfprobeMediaMetadataProbe probe =
                new FfprobeMediaMetadataProbe(new FakeProcessRunner(
                        new ProcessResult(
                                0,
                                "duration=1.234\njunk=value\nheight=1080\nwidth=1920\n",
                                "",
                                false
                        )
                ));

        final MediaMetadata metadata = probe.probe(MEDIA_PATH);

        Assertions.assertEquals(WIDTH, metadata.width());
        Assertions.assertEquals(HEIGHT, metadata.height());
    }

    @Test
    @DisplayName("Probe rejects missing and invalid values")
    void probeRejectsMissingAndInvalidValues() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        MediaProbeException.class,
                        () -> new FfprobeMediaMetadataProbe(
                                new FakeProcessRunner(new ProcessResult(
                                        0,
                                        "height=1080\nduration=1.0\n",
                                        "",
                                        false
                                ))
                        ).probe(MEDIA_PATH)
                ),
                () -> Assertions.assertThrows(
                        MediaProbeException.class,
                        () -> new FfprobeMediaMetadataProbe(
                                new FakeProcessRunner(new ProcessResult(
                                        0,
                                        "width=1920\nduration=1.0\n",
                                        "",
                                        false
                                ))
                        ).probe(MEDIA_PATH)
                ),
                () -> Assertions.assertThrows(
                        MediaProbeException.class,
                        () -> new FfprobeMediaMetadataProbe(
                                new FakeProcessRunner(new ProcessResult(
                                        0,
                                        "width=abc\nheight=1080\nduration=1.0\n",
                                        "",
                                        false
                                ))
                        ).probe(MEDIA_PATH)
                ),
                () -> Assertions.assertThrows(
                        MediaProbeException.class,
                        () -> new FfprobeMediaMetadataProbe(
                                new FakeProcessRunner(new ProcessResult(
                                        0,
                                        "width=-1\nheight=1080\nduration=1.0\n",
                                        "",
                                        false
                                ))
                        ).probe(MEDIA_PATH)
                )
        );
    }

    @Test
    @DisplayName("Probe reports process failures and timeout")
    void probeReportsProcessFailuresAndTimeout() {
        final MediaProbeException nonzero = Assertions.assertThrows(
                MediaProbeException.class,
                () -> new FfprobeMediaMetadataProbe(
                        new FakeProcessRunner(new ProcessResult(
                                1,
                                "",
                                "bad file",
                                false
                        ))
                ).probe(MEDIA_PATH)
        );
        final MediaProbeException timeout = Assertions.assertThrows(
                MediaProbeException.class,
                () -> new FfprobeMediaMetadataProbe(
                        new FakeProcessRunner(new ProcessResult(
                                0,
                                "",
                                "",
                                true
                        ))
                ).probe(MEDIA_PATH)
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        nonzero.getMessage().contains("bad file")
                ),
                () -> Assertions.assertTrue(
                        timeout.getMessage().contains("timed out")
                )
        );
    }

    @Test
    @DisplayName("Probe reports start failure")
    void probeReportsStartFailure() {
        final MediaProbeException exception = Assertions.assertThrows(
                MediaProbeException.class,
                () -> new FfprobeMediaMetadataProbe(command -> {
                    throw new IOException("cannot start");
                }).probe(MEDIA_PATH)
        );

        Assertions.assertTrue(exception.getMessage().contains("cannot start"));
    }

    private static final class FakeProcessRunner
            implements MediaProcessRunner {
        private final ProcessResult result;
        private List<String> command;

        private FakeProcessRunner(ProcessResult result) {
            this.result = result;
        }

        @Override
        public ProcessResult run(List<String> command)
                throws IOException, InterruptedException {

            this.command = command;
            return result;
        }
    }
}

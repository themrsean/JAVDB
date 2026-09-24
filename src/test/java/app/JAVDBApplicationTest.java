package app;

import cli.CommandResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

class JAVDBApplicationTest {
    private static final int SUCCESS_STATUS = 0;

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Application initializes the selected database")
    void applicationInitializesSelectedDatabase() {
        final Path databasePath = temporaryDirectory.resolve("app.db");
        final TestRun run = run(
                "--database", databasePath.toString(),
                "init"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        run.status()
                ),
                () -> Assertions.assertTrue(Files.exists(databasePath)),
                () -> Assertions.assertTrue(
                        run.output().contains(databasePath.toAbsolutePath()
                                .normalize()
                                .toString())
                )
        );
    }

    @Test
    @DisplayName("Help succeeds without creating production database")
    void helpSucceedsWithoutCreatingProductionDatabase() {
        final Path productionPath = Path.of("data", "javdb.db");
        final boolean existedBefore = Files.exists(productionPath);

        final TestRun run = run("help");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        run.status()
                ),
                () -> Assertions.assertEquals(
                        existedBefore,
                        Files.exists(productionPath)
                )
        );
    }

    @Test
    @DisplayName("Runner returns nonzero for invalid commands")
    void runnerReturnsNonzeroForInvalidCommands() {
        final Path databasePath = temporaryDirectory.resolve("invalid.db");
        final TestRun run = run(
                "--database", databasePath.toString(),
                "not-a-command"
        );

        Assertions.assertNotEquals(
                CommandResult.SUCCESS,
                run.status()
        );
    }

    @Test
    @DisplayName("Application runner supports media scan with selected database")
    void applicationRunnerSupportsMediaScanWithSelectedDatabase()
            throws Exception {

        final Path databasePath = temporaryDirectory.resolve("scan-app.db");
        final Path mediaPath = temporaryDirectory.resolve("scan.mp4");
        final Path executable = temporaryDirectory.resolve("fake-ffprobe");
        Files.writeString(mediaPath, "fixture");
        Files.writeString(executable, """
                #!/bin/sh
                echo width=640
                echo height=360
                echo duration=1.250
                """);
        executable.toFile().setExecutable(true);

        final TestRun run = run(
                "--database", databasePath.toString(),
                "media", "scan",
                "--root", temporaryDirectory.toString(),
                "--ffprobe", executable.toString(),
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(SUCCESS_STATUS, run.status()),
                () -> Assertions.assertTrue(run.output().contains("ADDED")),
                () -> Assertions.assertTrue(Files.exists(databasePath))
        );
    }

    @Test
    @DisplayName("GUI command passes selected database to launcher")
    void guiCommandPassesSelectedDatabaseToLauncher() {
        final Path databasePath = temporaryDirectory.resolve("gui.db");
        final CapturingGuiLauncher launcher = new CapturingGuiLauncher(
                CommandResult.SUCCESS
        );

        final TestRun run = runWithLauncher(
                launcher,
                "--database", databasePath.toString(),
                "gui"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(SUCCESS_STATUS, run.status()),
                () -> Assertions.assertEquals(
                        databasePath.toAbsolutePath().normalize(),
                        launcher.databasePath().orElseThrow()
                ),
                () -> Assertions.assertFalse(Files.exists(databasePath))
        );
    }

    @Test
    @DisplayName("GUI command uses default database path")
    void guiCommandUsesDefaultDatabasePath() {
        final CapturingGuiLauncher launcher = new CapturingGuiLauncher(
                CommandResult.SUCCESS
        );

        final TestRun run = runWithLauncher(launcher, "gui");

        Assertions.assertAll(
                () -> Assertions.assertEquals(SUCCESS_STATUS, run.status()),
                () -> Assertions.assertEquals(
                        Path.of("data", "javdb.db")
                                .toAbsolutePath()
                                .normalize(),
                        launcher.databasePath().orElseThrow()
                )
        );
    }

    @Test
    @DisplayName("GUI help does not launch JavaFX")
    void guiHelpDoesNotLaunchJavaFx() {
        final CapturingGuiLauncher launcher = new CapturingGuiLauncher(
                CommandResult.SUCCESS
        );

        final TestRun run = runWithLauncher(launcher, "gui", "--help");

        Assertions.assertAll(
                () -> Assertions.assertEquals(SUCCESS_STATUS, run.status()),
                () -> Assertions.assertTrue(run.output().contains("gui")),
                () -> Assertions.assertTrue(launcher.databasePath().isEmpty())
        );
    }

    @Test
    @DisplayName("Unknown GUI option returns usage error")
    void unknownGuiOptionReturnsUsageError() {
        final CapturingGuiLauncher launcher = new CapturingGuiLauncher(
                CommandResult.SUCCESS
        );

        final TestRun run = runWithLauncher(launcher, "gui", "--bad", "x");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        CommandResult.USAGE_ERROR,
                        run.status()
                ),
                () -> Assertions.assertTrue(launcher.databasePath().isEmpty())
        );
    }

    @Test
    @DisplayName("GUI launch failure returns nonzero status")
    void guiLaunchFailureReturnsNonzeroStatus() {
        final CapturingGuiLauncher launcher = new CapturingGuiLauncher(
                CommandResult.IO_ERROR
        );

        final TestRun run = runWithLauncher(launcher, "gui");

        Assertions.assertEquals(CommandResult.IO_ERROR, run.status());
    }

    private TestRun run(String... args) {
        return runWithLauncher(new CapturingGuiLauncher(CommandResult.SUCCESS), args);
    }

    private TestRun runWithLauncher(
            GuiLauncher guiLauncher,
            String... args) {

        final StringWriter output = new StringWriter();
        final StringWriter error = new StringWriter();
        final int status = JAVDBApplication.run(
                args,
                new StringReader("15\n"),
                new PrintWriter(output),
                new PrintWriter(error),
                guiLauncher
        );

        return new TestRun(status, output.toString(), error.toString());
    }

    private record TestRun(int status, String output, String error) {
    }

    private static final class CapturingGuiLauncher implements GuiLauncher {
        private final int status;
        private Path databasePath;

        private CapturingGuiLauncher(int status) {
            this.status = status;
        }

        @Override
        public int launch(Path selectedDatabasePath) {
            databasePath = selectedDatabasePath;
            return status;
        }

        private Optional<Path> databasePath() {
            return Optional.ofNullable(databasePath);
        }
    }
}

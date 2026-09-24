package cli;

import model.PerformerCategory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

class CliFoundationTest {
    private static final int SUCCESS_STATUS = 0;
    private static final int USAGE_ERROR_STATUS = 2;
    private static final int VALIDATION_ERROR_STATUS = 3;

    @Test
    @DisplayName("Command result exposes stable success and failure statuses")
    void commandResultExposesStableSuccessAndFailureStatuses() {
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        CommandResult.success().exitStatus()
                ),
                () -> Assertions.assertTrue(CommandResult.success().succeeded()),
                () -> Assertions.assertEquals(
                        USAGE_ERROR_STATUS,
                        CommandResult.usageError("bad").exitStatus()
                ),
                () -> Assertions.assertFalse(
                        CommandResult.usageError("bad").succeeded()
                )
        );
    }

    @Test
    @DisplayName("Console IO keeps output and error streams separate")
    void consoleIoKeepsOutputAndErrorStreamsSeparate() {
        final StringWriter output = new StringWriter();
        final StringWriter error = new StringWriter();
        final ConsoleIO consoleIO = new ConsoleIO(
                new StringReader(""),
                new PrintWriter(output),
                new PrintWriter(error)
        );

        consoleIO.println("normal");
        consoleIO.error("problem");

        Assertions.assertAll(
                () -> Assertions.assertTrue(output.toString().contains("normal")),
                () -> Assertions.assertFalse(output.toString().contains("problem")),
                () -> Assertions.assertTrue(error.toString().contains("problem")),
                () -> Assertions.assertFalse(error.toString().contains("normal"))
        );
    }

    @Test
    @DisplayName("No arguments select interactive mode")
    void noArgumentsSelectInteractiveMode() {
        final FakeCatalogService catalogService = new FakeCatalogService();
        final CommandLineInterface cli =
                new CommandLineInterface(catalogService);
        final TestConsole console = new TestConsole("15\n");

        final CommandResult result = cli.run(new String[0], console.io());

        Assertions.assertAll(
                () -> Assertions.assertTrue(result.succeeded()),
                () -> Assertions.assertTrue(catalogService.interactiveStarted),
                () -> Assertions.assertFalse(catalogService.oneShotStarted)
        );
    }

    @Test
    @DisplayName("Arguments select one-shot mode")
    void argumentsSelectOneShotMode() {
        final FakeCatalogService catalogService = new FakeCatalogService();
        final CommandLineInterface cli =
                new CommandLineInterface(catalogService);
        final TestConsole console = new TestConsole("");

        final CommandResult result = cli.run(
                new String[]{"help"},
                console.io()
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(result.succeeded()),
                () -> Assertions.assertFalse(catalogService.interactiveStarted),
                () -> Assertions.assertTrue(catalogService.oneShotStarted)
        );
    }

    @Test
    @DisplayName("End of input exits interactive mode cleanly")
    void endOfInputExitsInteractiveModeCleanly() {
        final CommandLineInterface cli =
                new CommandLineInterface(new FakeCatalogService());
        final TestConsole console = new TestConsole("");

        final CommandResult result = cli.run(new String[0], console.io());

        Assertions.assertTrue(result.succeeded());
    }

    @Test
    @DisplayName("Invalid menu choices do not terminate interactive mode")
    void invalidMenuChoicesDoNotTerminateInteractiveMode() {
        final CommandLineInterface cli =
                new CommandLineInterface(new FakeCatalogService());
        final TestConsole console = new TestConsole("bad\n15\n");

        final CommandResult result = cli.run(new String[0], console.io());

        Assertions.assertAll(
                () -> Assertions.assertTrue(result.succeeded()),
                () -> Assertions.assertTrue(
                        console.error().contains("Invalid menu choice")
                )
        );
    }

    @Test
    @DisplayName("Parser handles repeated options and typed values")
    void parserHandlesRepeatedOptionsAndTypedValues() {
        final ParsedCommand command = CommandParser.parse(new String[]{
                "--database", "build/test.db",
                "performer", "add",
                "--alias", "A",
                "--alias", "B",
                "--category", "actor",
                "--output", "tsv"
        });

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of("performer", "add"),
                        command.path()
                ),
                () -> Assertions.assertEquals(
                        List.of("A", "B"),
                        command.values("alias")
                ),
                () -> Assertions.assertEquals(
                        PerformerCategory.ACTOR,
                        command.requiredCategory("category")
                ),
                () -> Assertions.assertEquals("tsv", command.outputMode()),
                () -> Assertions.assertEquals(
                        "build/test.db",
                        command.databasePath()
                )
        );
    }

    @Test
    @DisplayName("Parser reports missing option values")
    void parserReportsMissingOptionValues() {
        final IllegalArgumentException exception =
                Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> CommandParser.parse(new String[]{
                                "publisher", "add", "--name"
                        })
                );

        Assertions.assertTrue(exception.getMessage().contains("Missing value"));
    }

    private static final class TestConsole {
        private final StringWriter output = new StringWriter();
        private final StringWriter error = new StringWriter();
        private final ConsoleIO io;

        private TestConsole(String input) {
            io = new ConsoleIO(
                    new StringReader(input),
                    new PrintWriter(output),
                    new PrintWriter(error)
            );
        }

        private ConsoleIO io() {
            return io;
        }

        private String error() {
            return error.toString();
        }
    }

    private static final class FakeCatalogService implements CliBackend {
        private boolean interactiveStarted;
        private boolean oneShotStarted;

        @Override
        public void markInteractiveStarted() {
            interactiveStarted = true;
        }

        @Override
        public void markOneShotStarted() {
            oneShotStarted = true;
        }
    }
}

package app;

import cli.CommandLineInterface;
import cli.CommandParser;
import cli.CommandResult;
import cli.ConsoleIO;
import cli.ParsedCommand;
import backup.DatabaseBackupFacade;
import database.DatabaseManager;
import database.SchemaManager;
import media.FfprobeMediaMetadataProbe;
import media.FileHasher;
import media.MediaFilenameParser;
import media.VideoFileDiscovery;
import repository.EntitySuggestionRepository;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;
import service.CatalogService;
import service.MediaAssignmentService;
import service.FilenameMetadataMatcher;
import service.MediaFilenameIndexingService;
import service.MediaRenameService;
import service.MediaScanService;
import service.MediaTitleDeriver;
import service.OriginalMovieSelector;

import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JAVDBApplication {
    private static final String DATA_DIRECTORY = "data";
    private static final String DATABASE_FILE_NAME = "javdb.db";
    private static final String DATABASE_OPTION = "--database";
    private static final int OPTION_VALUE_OFFSET = 1;
    private static final String BACKUP_COMMAND = "backup";
    private static final String GUI_COMMAND = "gui";

    private JAVDBApplication() {
    }

    public static void main(String[] args) {
        final int status = run(
                args,
                new InputStreamReader(
                        System.in,
                        StandardCharsets.UTF_8
                ),
                new PrintWriter(System.out),
                new PrintWriter(System.err),
                new JavaFxGuiLauncher()
        );

        System.exit(status);
    }

    public static int run(
            String[] args,
            Reader input,
            PrintWriter output,
            PrintWriter error) {

        return run(args, input, output, error, new JavaFxGuiLauncher());
    }

    public static int run(
            String[] args,
            Reader input,
            PrintWriter output,
            PrintWriter error,
            GuiLauncher guiLauncher) {

        CommandResult result;

        try {
            final ParsedCommand parsedCommand = CommandParser.parse(args);

            if (parsedCommand.help()) {
                final CommandLineInterface cli =
                        new CommandLineInterface(new HelpOnlyBackend());
                result = cli.run(args, new ConsoleIO(input, output, error));
            } else {
                final Path databasePath =
                        resolveDatabasePath(parsedCommand);

                if (isGuiCommand(parsedCommand)) {
                    result = launchGui(parsedCommand, databasePath, guiLauncher);
                } else {
                    final String[] commandArgs = stripDatabaseOption(args);
                    final CommandLineInterface cli =
                            isBackupCommand(parsedCommand)
                            ? new CommandLineInterface(
                                    new DatabaseBackupFacade(databasePath)
                            )
                            : createFullCli(databasePath);
                    result = cli.run(
                            commandArgs,
                            new ConsoleIO(input, output, error)
                    );
                }
            }
        } catch (java.io.IOException | SQLException exception) {
            result = CommandResult.databaseError(
                    "Unable to initialize JAVDB: "
                            + exception.getMessage()
            );
            error.println(result.message());
            error.flush();
        } catch (IllegalArgumentException exception) {
            result = CommandResult.usageError(exception.getMessage());
            error.println(result.message());
            error.flush();
        }

        return result.exitStatus();
    }

    private static CommandResult launchGui(
            ParsedCommand command,
            Path databasePath,
            GuiLauncher guiLauncher) {

        if (!command.options().isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown option for command: --"
                            + command.options().keySet().iterator().next()
            );
        }

        final int status = guiLauncher.launch(databasePath);
        CommandResult result = CommandResult.success();

        if (status != CommandResult.SUCCESS) {
            result = CommandResult.ioError("GUI launch failed.");
        }

        return result;
    }

    private static CommandLineInterface createFullCli(Path databasePath)
            throws java.io.IOException, SQLException {

        final DatabaseManager databaseManager =
                new DatabaseManager(databasePath);
        final SchemaManager schemaManager =
                new SchemaManager(databaseManager);
        schemaManager.initialize();
        final MediaFileRepository mediaFileRepository =
                new MediaFileRepository(databaseManager);
        final CatalogService catalogService = createCatalogService(
                databaseManager,
                mediaFileRepository
        );

        return new CommandLineInterface(
                catalogService,
                databaseManager.getDatabasePath(),
                createMediaScanService(mediaFileRepository),
                createMediaAssignmentService(
                        databaseManager,
                        mediaFileRepository,
                        catalogService
                ),
                createFilenameIndexingService(
                        databaseManager,
                        mediaFileRepository,
                        catalogService
                ),
                new DatabaseBackupFacade(
                        databaseManager.getDatabasePath()
                ),
                createMediaRenameService(databaseManager, mediaFileRepository)
        );
    }

    private static CatalogService createCatalogService(
            DatabaseManager databaseManager,
            MediaFileRepository mediaFileRepository) {

        return new CatalogService(
                new PublisherRepository(databaseManager),
                new PerformerRepository(databaseManager),
                new SeriesRepository(databaseManager),
                mediaFileRepository,
                new SceneRepository(databaseManager),
                new SearchRepository(databaseManager),
                new MovieRepository(databaseManager)
        );
    }

    private static MediaScanService createMediaScanService(
            MediaFileRepository mediaFileRepository) {

        return new MediaScanService(
                mediaFileRepository,
                new VideoFileDiscovery(),
                new FfprobeMediaMetadataProbe(),
                new FileHasher()
        );
    }

    private static MediaAssignmentService createMediaAssignmentService(
            DatabaseManager databaseManager,
            MediaFileRepository mediaFileRepository,
            CatalogService catalogService) {

        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);

        return new MediaAssignmentService(
                new MediaAssignmentRepository(databaseManager),
                mediaFileRepository,
                sceneRepository,
                movieRepository,
                catalogService,
                new MediaTitleDeriver()
        );
    }

    private static MediaFilenameIndexingService createFilenameIndexingService(
            DatabaseManager databaseManager,
            MediaFileRepository mediaFileRepository,
            CatalogService catalogService) {

        final EntitySuggestionRepository suggestionRepository =
                new EntitySuggestionRepository(databaseManager);
        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);
        return new MediaFilenameIndexingService(
                mediaFileRepository,
                new MediaAssignmentRepository(databaseManager),
                new MediaFilenameParser(),
                new FilenameMetadataMatcher(suggestionRepository),
                catalogService,
                sceneRepository,
                movieRepository
        );
    }

    private static MediaRenameService createMediaRenameService(
            DatabaseManager databaseManager,
            MediaFileRepository mediaFileRepository) {

        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);
        return new MediaRenameService(
                mediaFileRepository,
                sceneRepository,
                new MediaAssignmentRepository(databaseManager),
                new OriginalMovieSelector(movieRepository)
        );
    }

    private static Path resolveDatabasePath(ParsedCommand command) {
        Path databasePath = Path.of(DATA_DIRECTORY, DATABASE_FILE_NAME);

        if (command.databasePath() != null) {
            databasePath = Path.of(command.databasePath());
        }

        return databasePath.toAbsolutePath().normalize();
    }

    private static String[] stripDatabaseOption(String[] args) {
        final List<String> stripped = new ArrayList<>();
        int index = 0;

        while (index < args.length) {
            final String argument = args[index];

            if (DATABASE_OPTION.equals(argument)
                    && index + OPTION_VALUE_OFFSET < args.length) {
                index = index + OPTION_VALUE_OFFSET + OPTION_VALUE_OFFSET;
            } else {
                stripped.add(argument);
                index++;
            }
        }

        return stripped.toArray(String[]::new);
    }

    private static boolean isBackupCommand(ParsedCommand command) {
        return !command.path().isEmpty()
                && BACKUP_COMMAND.equals(command.path().getFirst());
    }

    private static boolean isGuiCommand(ParsedCommand command) {
        return command.path().equals(List.of(GUI_COMMAND));
    }

    private static final class HelpOnlyBackend implements cli.CliBackend {
    }
}

package cli;

import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import backup.BackupResult;
import backup.BackupVerificationLevel;
import backup.BackupVerificationResult;
import backup.DatabaseBackupFacade;
import backup.RestoreResult;
import media.FfprobeAvailabilityChecker;
import media.FfprobeMediaMetadataProbe;
import media.MediaProbeException;
import media.ProcessBuilderMediaProcessRunner;
import service.BatchSceneCreationFileResult;
import service.BatchSceneCreationRequest;
import service.BatchSceneCreationResult;
import service.BatchSceneCreationSummary;
import service.BatchSceneCreationStatus;
import service.AutoIndexFileResult;
import service.AutoIndexRequest;
import service.AutoIndexResult;
import service.CatalogService;
import service.CreateSceneFromMediaRequest;
import service.FilenamePreview;
import service.MediaAssignmentService;
import service.MediaFilenameIndexingService;
import service.MediaRenameBatchResult;
import service.MediaRenamePreview;
import service.MediaRenameRequest;
import service.MediaRenameResult;
import service.MediaRenameService;
import service.MediaRenameStatus;
import service.MediaScanRequest;
import service.MediaScanResult;
import service.MediaScanService;
import service.MediaScanStatus;
import service.MediaVerificationRequest;
import service.MediaVerificationResult;
import service.MediaVerificationStatus;
import model.VerificationStatus;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class CommandLineInterface {
    private static final String EXIT_CHOICE = "15";
    private static final String INIT = "init";
    private static final String PUBLISHER = "publisher";
    private static final String PERFORMER = "performer";
    private static final String SERIES = "series";
    private static final String MEDIA = "media";
    private static final String SCENE = "scene";
    private static final String MOVIE = "movie";
    private static final String BACKUP = "backup";
    private static final String ADD = "add";
    private static final String LIST = "list";
    private static final String SHOW = "show";
    private static final String IMPORT = "import";
    private static final String SCAN = "scan";
    private static final String VERIFY = "verify";
    private static final String VERIFICATION = "verification";
    private static final String SET = "set";
    private static final String CREATE = "create";
    private static final String RESTORE = "restore";
    private static final String PROBE_CHECK = "probe-check";
    private static final String UNASSIGNED = "unassigned";
    private static final String ASSIGNMENT = "assignment";
    private static final String ATTACH_MEDIA = "attach-media";
    private static final String CREATE_FROM_MEDIA = "create-from-media";
    private static final String CREATE_FROM_MEDIA_BATCH =
            "create-from-media-batch";
    private static final String AUTO_INDEX = "auto-index";
    private static final String INDEX_REVIEW = "index-review";
    private static final String PARSE_PREVIEW = "parse-preview";
    private static final String RENAME_PREVIEW = "rename-preview";
    private static final String RENAME = "rename";
    private static final String RENAME_MEDIA = "rename-media";
    private static final String ALL_UNASSIGNED = "all-unassigned";
    private static final String SEARCH_BY_PERFORMER = "search-by-performer";
    private static final String DASH = "-";
    private static final String PATH_COLUMN = "path";
    private static final String FILE_SIZE_COLUMN = "file_size";
    private static final String DURATION_COLUMN = "duration";
    private static final String DURATION_MILLIS_COLUMN = "duration_millis";
    private static final String WIDTH_COLUMN = "width";
    private static final String HEIGHT_COLUMN = "height";
    private static final String HASH_COLUMN = "content_hash";
    private static final String LAST_MODIFIED_COLUMN = "last_modified_millis";
    private static final int FIRST_DATA_ROW_NUMBER = 2;
    private static final int FIRST_CSV_INDEX = 0;
    private static final long DEFAULT_FILE_SIZE = 0L;
    private static final int DEFAULT_DIMENSION = 0;
    private static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final Set<String> SCENE_MANIFEST_COLUMNS = Set.of(
            "media_id",
            "title",
            "code",
            "release_date",
            "publisher_id",
            "series_id",
            "season",
            "episode",
            "performer_ids"
    );

    private final CliBackend backend;
    private final CatalogService catalogService;
    private final Path databasePath;
    private final MediaScanService mediaScanService;
    private final MediaAssignmentService mediaAssignmentService;
    private final MediaFilenameIndexingService mediaFilenameIndexingService;
    private final DatabaseBackupFacade databaseBackupFacade;
    private final MediaRenameService mediaRenameService;

    public CommandLineInterface(CliBackend backend) {
        this.backend = Objects.requireNonNull(
                backend,
                "CLI backend must not be null"
        );
        catalogService = null;
        databasePath = null;
        mediaScanService = null;
        mediaAssignmentService = null;
        mediaFilenameIndexingService = null;
        databaseBackupFacade = null;
        mediaRenameService = null;
    }

    public CommandLineInterface(CatalogService catalogService) {
        this(catalogService, null, null);
    }

    public CommandLineInterface(DatabaseBackupFacade databaseBackupFacade) {
        this.catalogService = null;
        databasePath = null;
        mediaScanService = null;
        mediaAssignmentService = null;
        mediaFilenameIndexingService = null;
        this.databaseBackupFacade = Objects.requireNonNull(
                databaseBackupFacade,
                "Database backup facade must not be null"
        );
        mediaRenameService = null;
        backend = new ServiceBackend();
    }

    public CommandLineInterface(
            CatalogService catalogService,
            Path databasePath) {

        this(catalogService, databasePath, null);
    }

    public CommandLineInterface(
            CatalogService catalogService,
            Path databasePath,
            MediaScanService mediaScanService) {

        this(catalogService, databasePath, mediaScanService, null);
    }

    public CommandLineInterface(
            CatalogService catalogService,
            Path databasePath,
            MediaScanService mediaScanService,
            MediaAssignmentService mediaAssignmentService) {

        this(
                catalogService,
                databasePath,
                mediaScanService,
                mediaAssignmentService,
                null,
                null,
                null
        );
    }

    public CommandLineInterface(
            CatalogService catalogService,
            Path databasePath,
            MediaScanService mediaScanService,
            MediaAssignmentService mediaAssignmentService,
            MediaFilenameIndexingService mediaFilenameIndexingService) {

        this(
                catalogService,
                databasePath,
                mediaScanService,
                mediaAssignmentService,
                mediaFilenameIndexingService,
                null,
                null
        );
    }

    public CommandLineInterface(
            CatalogService catalogService,
            Path databasePath,
            MediaScanService mediaScanService,
            MediaAssignmentService mediaAssignmentService,
            MediaFilenameIndexingService mediaFilenameIndexingService,
            DatabaseBackupFacade databaseBackupFacade) {

        this(
                catalogService,
                databasePath,
                mediaScanService,
                mediaAssignmentService,
                mediaFilenameIndexingService,
                databaseBackupFacade,
                null
        );
    }

    public CommandLineInterface(
            CatalogService catalogService,
            Path databasePath,
            MediaScanService mediaScanService,
            MediaAssignmentService mediaAssignmentService,
            MediaFilenameIndexingService mediaFilenameIndexingService,
            DatabaseBackupFacade databaseBackupFacade,
            MediaRenameService mediaRenameService) {

        this.catalogService = Objects.requireNonNull(
                catalogService,
                "Catalog service must not be null"
        );
        this.databasePath = databasePath;
        this.mediaScanService = mediaScanService;
        this.mediaAssignmentService = mediaAssignmentService;
        this.mediaFilenameIndexingService = mediaFilenameIndexingService;
        this.databaseBackupFacade = databaseBackupFacade;
        this.mediaRenameService = mediaRenameService;
        backend = new ServiceBackend();
    }

    public CommandResult run(String[] args, ConsoleIO consoleIO) {
        Objects.requireNonNull(args, "Arguments must not be null");
        Objects.requireNonNull(consoleIO, "Console IO must not be null");

        final CommandResult result;

        if (args.length == 0) {
            result = runInteractive(consoleIO);
        } else {
            result = runOneShot(args, consoleIO);
        }

        return result;
    }

    private CommandResult runInteractive(ConsoleIO consoleIO) {
        backend.markInteractiveStarted();

        boolean running = true;
        CommandResult result = CommandResult.success();

        while (running) {
            printMenu(consoleIO);

            try {
                final String choice = consoleIO.readLine();

                if (choice == null) {
                    running = false;
                } else if (EXIT_CHOICE.equals(choice.trim())) {
                    running = false;
                } else if (catalogService == null) {
                    consoleIO.error("Invalid menu choice: " + choice);
                } else {
                    result = handleInteractiveChoice(choice.trim(), consoleIO);

                    if (!result.succeeded()) {
                        consoleIO.error(result.message());
                    }
                }
            } catch (IOException exception) {
                result = CommandResult.ioError(exception.getMessage());
                consoleIO.error("Input error: " + exception.getMessage());
                running = false;
            }
        }

        return result;
    }

    private CommandResult runOneShot(String[] args, ConsoleIO consoleIO) {
        backend.markOneShotStarted();
        CommandResult result = CommandResult.success();

        try {
            final ParsedCommand command = CommandParser.parse(args);

            if (command.help()) {
                consoleIO.println(HelpText.text());
            } else if (catalogService == null
                    && !isInit(command)
                    && !isBackupCommand(command)) {
                result = CommandResult.usageError(
                        "Command handling is not available."
                );
                consoleIO.error(result.message());
            } else {
                result = execute(command, consoleIO);
            }
        } catch (IllegalArgumentException exception) {
            result = CommandResult.validationError(exception.getMessage());
            consoleIO.error(exception.getMessage());
        } catch (SQLException exception) {
            result = CommandResult.databaseError("Database error: "
                    + exception.getMessage());
            consoleIO.error(result.message());
        } catch (IOException exception) {
            result = CommandResult.ioError("File error: "
                    + exception.getMessage());
            consoleIO.error(result.message());
        } catch (MediaProbeException exception) {
            result = CommandResult.ioError("Media probe error: "
                    + exception.getMessage());
            consoleIO.error(result.message());
        }

        return result;
    }

    private CommandResult execute(ParsedCommand command, ConsoleIO consoleIO)
            throws SQLException, IOException, MediaProbeException {

        CommandResult result;

        if (isInit(command)) {
            consoleIO.println("Database initialized: "
                    + (databasePath == null ? "" : databasePath));
            result = CommandResult.success();
        } else if (matches(command, PUBLISHER, ADD)) {
            requireOnlyOptions(command, Set.of("name", "alias"));
            result = addPublisher(command, consoleIO);
        } else if (matches(command, PUBLISHER, LIST)) {
            requireOnlyOptions(command, Set.of());
            print(consoleIO, OutputFormatter.publishers(
                    catalogService.listPublishers(),
                    OutputFormatter.isTsv(command)
            ));
            result = CommandResult.success();
        } else if (matches(command, PERFORMER, ADD)) {
            requireOnlyOptions(command, Set.of("name", "category", "alias"));
            result = addPerformer(command, consoleIO);
        } else if (matches(command, PERFORMER, LIST)) {
            requireOnlyOptions(command, Set.of());
            print(consoleIO, OutputFormatter.performers(
                    catalogService.listPerformers(),
                    OutputFormatter.isTsv(command)
            ));
            result = CommandResult.success();
        } else if (matches(command, SERIES, ADD)) {
            requireOnlyOptions(command, Set.of("title", "publisher"));
            result = addSeries(command, consoleIO);
        } else if (matches(command, SERIES, LIST)) {
            requireOnlyOptions(command, Set.of());
            print(consoleIO, OutputFormatter.series(
                    catalogService.listSeries(),
                    OutputFormatter.isTsv(command)
            ));
            result = CommandResult.success();
        } else if (matches(command, MEDIA, ADD)) {
            requireOnlyOptions(command, Set.of(
                    "path",
                    "file-size",
                    "duration",
                    "width",
                    "height",
                    "hash",
                    "last-modified"
            ));
            result = addMedia(command, consoleIO);
        } else if (matches(command, MEDIA, LIST)) {
            requireOnlyOptions(command, Set.of());
            print(consoleIO, OutputFormatter.mediaFiles(
                    catalogService.listMediaFiles(),
                    OutputFormatter.isTsv(command)
            ));
            result = CommandResult.success();
        } else if (matches(command, MEDIA, SHOW)) {
            requireOnlyOptions(command, Set.of("id"));
            result = showMedia(command, consoleIO);
        } else if (matches(command, MEDIA, IMPORT)) {
            requireOnlyOptions(command, Set.of("input", "fail-fast"));
            result = importMedia(command, consoleIO);
        } else if (matches(command, MEDIA, SCAN)) {
            requireOnlyOptions(command, Set.of(
                    "root",
                    "ffprobe",
                    "hash",
                    "dry-run",
                    "fail-fast",
                    "extension"
            ));
            result = scanMedia(command, consoleIO);
        } else if (matches(command, MEDIA, VERIFY)) {
            requireOnlyOptions(command, Set.of(
                    "ffprobe",
                    "refresh",
                    "dry-run",
                    "fail-fast"
            ));
            result = verifyMedia(command, consoleIO);
        } else if (matches(command, MEDIA, PROBE_CHECK)) {
            requireOnlyOptions(command, Set.of("ffprobe"));
            result = checkProbe(command, consoleIO);
        } else if (matches(command, MEDIA, UNASSIGNED)) {
            requireOnlyOptions(command, Set.of(
                    "contains",
                    "directory",
                    "width",
                    "height",
                    "min-width",
                    "min-height",
                    "limit",
                    "offset"
            ));
            result = listUnassignedMedia(command, consoleIO);
        } else if (matches(command, MEDIA, ASSIGNMENT)) {
            requireOnlyOptions(command, Set.of("media"));
            result = showMediaAssignment(command, consoleIO);
        } else if (matches(command, MEDIA, PARSE_PREVIEW)) {
            requireOnlyOptions(command, Set.of(
                    "media",
                    ALL_UNASSIGNED,
                    "contains",
                    "directory",
                    "width",
                    "height",
                    "min-width",
                    "min-height",
                    "limit",
                    "offset"
            ));
            result = previewFilenames(command, consoleIO);
        } else if (matches(command, MEDIA, RENAME_PREVIEW)) {
            requireOnlyOptions(command, Set.of("media", "scene", "movie"));
            result = previewMediaRename(command, consoleIO);
        } else if (matches(command, MEDIA, RENAME)) {
            requireOnlyOptions(command, Set.of(
                    "media",
                    "scene",
                    "movie",
                    "dry-run"
            ));
            result = renameMedia(command, consoleIO);
        } else if (matches(command, SCENE, ADD)) {
            requireOnlyOptions(command, Set.of(
                    "title",
                    "code",
                    "release-date",
                    "publisher",
                    "series",
                    "season",
                    "episode",
                    "performer",
                    "media"
            ));
            result = addScene(command, consoleIO);
        } else if (matches(command, SCENE, LIST)) {
            requireOnlyOptions(command, Set.of());
            print(consoleIO, OutputFormatter.scenes(
                    catalogService.listScenes(),
                    OutputFormatter.isTsv(command)
            ));
            result = CommandResult.success();
        } else if (matches(command, SCENE, SHOW)) {
            requireOnlyOptions(command, Set.of("id"));
            result = showScene(command, consoleIO);
        } else if (matches(command, SCENE, SEARCH_BY_PERFORMER)) {
            requireOnlyOptions(command, Set.of("performer"));
            result = searchScenesByPerformer(command, consoleIO);
        } else if (matches(command, SCENE, ATTACH_MEDIA)) {
            requireOnlyOptions(command, Set.of("scene", "media"));
            result = attachMediaToScene(command, consoleIO);
        } else if (matches(command, SCENE, CREATE_FROM_MEDIA)) {
            requireOnlyOptions(command, Set.of(
                    "media",
                    "title",
                    "code",
                    "release-date",
                    "publisher",
                    "series",
                    "season",
                    "episode",
                    "performer",
                    "dry-run",
                    "fail-fast"
            ));
            result = createSceneFromMedia(command, consoleIO);
        } else if (matches(command, SCENE, CREATE_FROM_MEDIA_BATCH)) {
            requireOnlyOptions(command, Set.of(
                    "input",
                    "dry-run",
                    "fail-fast"
            ));
            result = createSceneFromMediaBatch(command, consoleIO);
        } else if (matches(command, SCENE, AUTO_INDEX)) {
            requireOnlyOptions(command, Set.of(
                    "media",
                    ALL_UNASSIGNED,
                    "contains",
                    "directory",
                    "limit",
                    "offset",
                    "dry-run",
                    "fail-fast"
            ));
            result = autoIndexScenes(command, consoleIO);
        } else if (matches(command, SCENE, INDEX_REVIEW)) {
            requireOnlyOptions(command, Set.of(
                    "media",
                    ALL_UNASSIGNED,
                    "contains",
                    "directory",
                    "limit",
                    "offset"
            ));
            result = reviewFilenameIndex(command, consoleIO);
        } else if (matches(command, SCENE, VERIFICATION, LIST)) {
            requireOnlyOptions(command, Set.of("status", "limit", "offset"));
            result = listSceneVerification(command, consoleIO);
        } else if (matches(command, SCENE, VERIFICATION, SET)) {
            requireOnlyOptions(command, Set.of("scene", "status"));
            result = setSceneVerification(command, consoleIO);
        } else if (matches(command, SCENE, RENAME_MEDIA)) {
            requireOnlyOptions(command, Set.of(
                    "scene",
                    "movie",
                    "dry-run",
                    "fail-fast"
            ));
            result = renameSceneMedia(command, consoleIO);
        } else if (matches(command, MOVIE, ADD)) {
            requireOnlyOptions(command, Set.of(
                    "title",
                    "release-date",
                    "publisher",
                    "compilation",
                    "scene",
                    "media"
            ));
            result = addMovie(command, consoleIO);
        } else if (matches(command, MOVIE, LIST)) {
            requireOnlyOptions(command, Set.of());
            print(consoleIO, OutputFormatter.movies(
                    catalogService.listMovies(),
                    OutputFormatter.isTsv(command)
            ));
            result = CommandResult.success();
        } else if (matches(command, MOVIE, SHOW)) {
            requireOnlyOptions(command, Set.of("id"));
            result = showMovie(command, consoleIO);
        } else if (matches(command, MOVIE, ATTACH_MEDIA)) {
            requireOnlyOptions(command, Set.of("movie", "media"));
            result = attachMediaToMovie(command, consoleIO);
        } else if (matches(command, BACKUP, CREATE)) {
            requireOnlyOptions(command, Set.of(
                    "destination",
                    "overwrite",
                    "verify"
            ));
            result = createBackup(command, consoleIO);
        } else if (matches(command, BACKUP, VERIFY)) {
            requireOnlyOptions(command, Set.of("input", "level"));
            result = verifyBackup(command, consoleIO);
        } else if (matches(command, BACKUP, RESTORE)) {
            requireOnlyOptions(command, Set.of(
                    "input",
                    "destination",
                    "overwrite",
                    "level"
            ));
            result = restoreBackup(command, consoleIO);
        } else {
            result = CommandResult.usageError(
                    "Unknown command. Run javdb help for usage."
            );
            consoleIO.error(result.message());
        }

        return result;
    }

    private CommandResult addPublisher(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Publisher publisher = catalogService.createPublisher(
                command.requiredValue("name"),
                command.values("alias")
        );
        printCreatedId(consoleIO, command, publisher.getId());
        return CommandResult.success();
    }

    private CommandResult addPerformer(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Performer performer = catalogService.createPerformer(
                command.requiredValue("name"),
                command.values("alias"),
                command.requiredCategory("category")
        );
        printCreatedId(consoleIO, command, performer.getId());
        return CommandResult.success();
    }

    private CommandResult addSeries(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Series series = catalogService.createSeries(
                command.requiredValue("title"),
                command.requiredUuid("publisher")
        );
        printCreatedId(consoleIO, command, series.getId());
        return CommandResult.success();
    }

    private CommandResult addMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException, IOException {

        final Path path = normalizePath(command.requiredValue("path"));
        final long fileSize = fileSize(command, path);
        final MediaFile mediaFile = catalogService.createMediaFile(
                path,
                fileSize,
                optionalDuration(command),
                command.optionalInt("width", DEFAULT_DIMENSION),
                command.optionalInt("height", DEFAULT_DIMENSION),
                command.optionalValue("hash"),
                lastModified(command, path)
        );
        printCreatedId(consoleIO, command, mediaFile.getId());
        return CommandResult.success();
    }

    private CommandResult showMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final MediaFile mediaFile = catalogService
                .findMediaFileById(command.requiredUuid("id"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media file not found."
                ));
        print(consoleIO, OutputFormatter.mediaFile(
                mediaFile,
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult addScene(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Scene scene = catalogService.createScene(
                command.requiredValue("title"),
                command.optionalValue("code"),
                command.optionalDate("release-date"),
                command.requiredUuid("publisher"),
                command.optionalUuid("series"),
                command.optionalValue("season"),
                command.optionalValue("episode"),
                command.uuids("performer"),
                command.uuids("media")
        );
        printCreatedId(consoleIO, command, scene.getId());
        return CommandResult.success();
    }

    private CommandResult showScene(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Scene scene = catalogService
                .findSceneById(command.requiredUuid("id"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Scene not found."
                ));
        print(consoleIO, OutputFormatter.scene(
                scene,
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult searchScenesByPerformer(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        print(consoleIO, OutputFormatter.scenes(
                catalogService.findScenesByPerformer(
                        command.requiredUuid("performer")
                ),
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult listSceneVerification(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final List<Scene> scenes;

        if (command.hasOption("status")) {
            scenes = catalogService.findScenesByVerificationStatus(
                    verificationStatus(command.requiredValue("status")),
                    command.optionalInt("limit", 100),
                    command.optionalInt("offset", 0)
            );
        } else {
            scenes = catalogService.listScenes();
        }

        print(consoleIO, OutputFormatter.sceneVerification(
                scenes,
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult setSceneVerification(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final UUID sceneId = command.requiredUuid("scene");
        final VerificationStatus status =
                verificationStatus(command.requiredValue("status"));
        final Scene scene;

        if (status == VerificationStatus.VERIFIED) {
            scene = catalogService.markSceneVerified(sceneId);
        } else if (status == VerificationStatus.NEEDS_REVIEW) {
            scene = catalogService.markSceneNeedsReview(sceneId);
        } else {
            scene = catalogService.markSceneUnverified(sceneId);
        }

        print(consoleIO, OutputFormatter.sceneVerification(
                List.of(scene),
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult previewMediaRename(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final MediaRenamePreview preview = renameService().preview(
                new MediaRenameRequest(
                        command.requiredUuid("media"),
                        command.optionalUuid("scene"),
                        command.optionalUuid("movie")
                )
        );
        print(consoleIO, OutputFormatter.mediaRenamePreview(
                preview,
                OutputFormatter.isTsv(command)
        ));
        return commandResultForRenameStatus(preview.status());
    }

    private CommandResult renameMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException, IOException {

        final MediaRenameRequest request = new MediaRenameRequest(
                command.requiredUuid("media"),
                command.optionalUuid("scene"),
                command.optionalUuid("movie")
        );
        final CommandResult result;

        if (command.hasOption("dry-run")) {
            final MediaRenamePreview preview = renameService().preview(request);
            print(consoleIO, OutputFormatter.mediaRenamePreview(
                    preview,
                    OutputFormatter.isTsv(command)
            ));
            result = commandResultForRenameStatus(preview.status());
        } else {
            final MediaRenameResult renameResult =
                    renameService().rename(request);
            print(consoleIO, OutputFormatter.mediaRenameResults(
                    List.of(renameResult),
                    OutputFormatter.isTsv(command)
            ));
            result = commandResultForRenameStatus(renameResult.status());
        }

        return result;
    }

    private CommandResult renameSceneMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException, IOException {

        final UUID sceneId = command.requiredUuid("scene");
        final UUID movieOverrideId = command.optionalUuid("movie");
        final CommandResult result;

        if (command.hasOption("dry-run")) {
            final List<MediaRenamePreview> previews =
                    renameService().previewSceneMedia(sceneId, movieOverrideId);
            final List<MediaRenameResult> rows = previews.stream()
                    .map(preview -> new MediaRenameResult(
                            preview.mediaId(),
                            preview.sceneId(),
                            preview.originalPath(),
                            preview.proposedPath(),
                            preview.status(),
                            preview.warnings(),
                            preview.error()
                    ))
                    .toList();
            print(consoleIO, OutputFormatter.mediaRenameResults(
                    rows,
                    OutputFormatter.isTsv(command)
            ));
            result = commandResultForRenameResults(rows);
        } else {
            final MediaRenameBatchResult batchResult =
                    renameService().renameSceneMedia(
                            sceneId,
                            movieOverrideId,
                            false,
                            command.hasOption("fail-fast")
                    );
            print(consoleIO, OutputFormatter.mediaRenameResults(
                    batchResult.results(),
                    OutputFormatter.isTsv(command)
            ));
            result = commandResultForRenameResults(batchResult.results());
        }

        return result;
    }

    private CommandResult addMovie(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Movie movie = catalogService.createMovie(
                command.requiredValue("title"),
                command.optionalDate("release-date"),
                command.requiredUuid("publisher"),
                command.optionalBoolean("compilation", false),
                command.uuids("scene"),
                command.uuids("media")
        );
        printCreatedId(consoleIO, command, movie.getId());
        return CommandResult.success();
    }

    private CommandResult showMovie(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Movie movie = catalogService
                .findMovieById(command.requiredUuid("id"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Movie not found."
                ));
        print(consoleIO, OutputFormatter.movie(
                movie,
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult importMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws IOException {

        final String input = command.requiredValue("input");
        final String csvText;

        if (DASH.equals(input)) {
            csvText = readRemainingInput(consoleIO);
        } else {
            csvText = Files.readString(Path.of(input));
        }

        final CsvImportSummary summary =
                importMediaRows(command, consoleIO, csvText);

        final CommandResult result;

        if (summary.failedRows() == 0) {
            result = CommandResult.success();
        } else {
            result = CommandResult.partialFailure(
                    "One or more rows failed."
            );
        }

        return result;
    }

    private CommandResult scanMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws IOException, MediaProbeException {

        final MediaScanService service = scanService(command);
        final MediaScanResult result = service.scan(new MediaScanRequest(
                Path.of(command.requiredValue("root")),
                command.hasOption("hash"),
                command.hasOption("dry-run"),
                command.hasOption("fail-fast"),
                Set.copyOf(command.values("extension"))
        ));
        print(consoleIO, OutputFormatter.mediaScan(
                result,
                OutputFormatter.isTsv(command)
        ));

        final CommandResult commandResult;

        if (result.summary().failed() == 0) {
            commandResult = CommandResult.success();
        } else {
            commandResult = CommandResult.partialFailure(
                    "One or more media files failed."
            );
        }

        return commandResult;
    }

    private CommandResult verifyMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws MediaProbeException {

        final MediaScanService service = scanService(command);
        final MediaVerificationResult result = service.verify(
                new MediaVerificationRequest(
                        command.hasOption("refresh"),
                        command.hasOption("dry-run"),
                        command.hasOption("fail-fast")
                )
        );
        print(consoleIO, OutputFormatter.mediaVerification(
                result,
                OutputFormatter.isTsv(command)
        ));

        CommandResult commandResult = CommandResult.success();

        for (service.MediaVerificationFileResult fileResult
                : result.files()) {
            if (fileResult.status() == MediaVerificationStatus.MISSING
                    || fileResult.status() == MediaVerificationStatus.CHANGED
                    || fileResult.status()
                    == MediaVerificationStatus.REFRESH_FAILED) {
                commandResult = CommandResult.partialFailure(
                        "One or more media files need attention."
                );
            }
        }

        return commandResult;
    }

    private CommandResult checkProbe(
            ParsedCommand command,
            ConsoleIO consoleIO) throws MediaProbeException {

        final String output = new FfprobeAvailabilityChecker().check(
                command.optionalValue("ffprobe")
        );
        consoleIO.println(output);
        return CommandResult.success();
    }

    private CommandResult createBackup(
            ParsedCommand command,
            ConsoleIO consoleIO) throws IOException, SQLException {

        final BackupResult result = backupFacade().createBackup(
                Path.of(command.requiredValue("destination")),
                command.hasOption("overwrite"),
                BackupVerificationLevel.parse(command.optionalValue("verify"))
        );
        print(consoleIO, OutputFormatter.backup(
                result,
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult verifyBackup(
            ParsedCommand command,
            ConsoleIO consoleIO) throws IOException, SQLException {

        final BackupVerificationResult result = backupFacade().verify(
                Path.of(command.requiredValue("input")),
                BackupVerificationLevel.parse(command.optionalValue("level"))
        );
        print(consoleIO, OutputFormatter.backupVerification(
                result,
                OutputFormatter.isTsv(command)
        ));
        return result.valid()
                ? CommandResult.success()
                : CommandResult.validationError("Backup verification failed.");
    }

    private CommandResult restoreBackup(
            ParsedCommand command,
            ConsoleIO consoleIO) throws IOException, SQLException {

        final RestoreResult result = backupFacade().restore(
                Path.of(command.requiredValue("input")),
                Path.of(command.requiredValue("destination")),
                command.hasOption("overwrite"),
                BackupVerificationLevel.parse(command.optionalValue("level"))
        );
        print(consoleIO, OutputFormatter.restore(
                result,
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult listUnassignedMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        print(consoleIO, OutputFormatter.unassignedMediaFiles(
                assignmentService().findUnassigned(
                        new repository.UnassignedMediaFilter(
                                command.optionalValue("contains"),
                                optionalPath(command, "directory"),
                                optionalInteger(command, "width"),
                                optionalInteger(command, "height"),
                                optionalInteger(command, "min-width"),
                                optionalInteger(command, "min-height"),
                                command.optionalInt(
                                        "limit",
                                        repository.UnassignedMediaFilter
                                                .DEFAULT_LIMIT
                                ),
                                command.optionalInt("offset", 0)
                        )
                ),
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult showMediaAssignment(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        print(consoleIO, OutputFormatter.mediaAssignment(
                assignmentService().findAssignment(
                        command.requiredUuid("media")
                ),
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult previewFilenames(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final List<FilenamePreview> previews = new ArrayList<>();

        if (command.values("media").isEmpty()
                && !command.hasOption(ALL_UNASSIGNED)) {
            throw new IllegalArgumentException(
                    "Use --media or --all-unassigned for filename preview."
            );
        }

        for (UUID mediaId : command.uuids("media")) {
            previews.add(indexingService().preview(mediaId));
        }

        if (command.hasOption(ALL_UNASSIGNED)) {
            previews.addAll(indexingService().previewUnassigned(
                    new repository.UnassignedMediaFilter(
                            command.optionalValue("contains"),
                            optionalPath(command, "directory"),
                            optionalInteger(command, "width"),
                            optionalInteger(command, "height"),
                            optionalInteger(command, "min-width"),
                            optionalInteger(command, "min-height"),
                            command.optionalInt(
                                    "limit",
                                    repository.UnassignedMediaFilter
                                            .DEFAULT_LIMIT
                            ),
                            command.optionalInt("offset", 0)
                    )
            ));
        }

        print(consoleIO, OutputFormatter.filenamePreviews(
                previews,
                OutputFormatter.isTsv(command)
        ));
        return CommandResult.success();
    }

    private CommandResult attachMediaToScene(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Scene scene = assignmentService().attachToScene(
                command.requiredUuid("media"),
                command.requiredUuid("scene")
        );
        consoleIO.println("scene_id\tmedia_id");
        consoleIO.println(scene.getId() + "\t"
                + command.requiredUuid("media"));
        return CommandResult.success();
    }

    private CommandResult attachMediaToMovie(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final Movie movie = assignmentService().attachToMovie(
                command.requiredUuid("media"),
                command.requiredUuid("movie")
        );
        consoleIO.println("movie_id\tmedia_id");
        consoleIO.println(movie.getId() + "\t"
                + command.requiredUuid("media"));
        return CommandResult.success();
    }

    private CommandResult createSceneFromMedia(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final List<UUID> mediaIds = command.uuids("media");
        final CommandResult result;

        if (command.hasOption("dry-run") || mediaIds.size() > 1) {
            final BatchSceneCreationResult batchResult =
                    assignmentService().createScenesFromMedia(
                            new BatchSceneCreationRequest(
                                    mediaIds,
                                    command.optionalUuid("publisher"),
                                    command.optionalUuid("series"),
                                    command.optionalDate("release-date"),
                                    command.optionalValue("season"),
                                    command.optionalValue("episode"),
                                    command.uuids("performer"),
                                    command.hasOption("dry-run"),
                                    command.hasOption("fail-fast")
                            )
                    );
            print(consoleIO, OutputFormatter.batchSceneCreation(
                    batchResult,
                    OutputFormatter.isTsv(command)
            ));
            result = batchResultSuccess(batchResult)
                    ? CommandResult.success()
                    : CommandResult.partialFailure(
                            "One or more media files failed."
                    );
        } else {
            final Scene scene = assignmentService().createSceneFromMedia(
                    new CreateSceneFromMediaRequest(
                            command.requiredUuid("media"),
                            command.optionalValue("title"),
                            command.optionalValue("code"),
                            command.optionalDate("release-date"),
                            command.requiredUuid("publisher"),
                            command.optionalUuid("series"),
                            command.optionalValue("season"),
                            command.optionalValue("episode"),
                            command.uuids("performer")
                    )
            );

            if (OutputFormatter.isTsv(command)) {
                consoleIO.println("scene_id\tmedia_id\ttitle");
                consoleIO.println(scene.getId() + "\t"
                        + command.requiredUuid("media") + "\t"
                        + OutputFormatter.escape(scene.getTitle()));
            } else {
                consoleIO.println("Created scene " + scene.getId()
                        + " " + scene.getTitle());
            }

            result = CommandResult.success();
        }

        return result;
    }

    private CommandResult createSceneFromMediaBatch(
            ParsedCommand command,
            ConsoleIO consoleIO) throws IOException, SQLException {

        final String input = command.requiredValue("input");
        final String csvText = DASH.equals(input)
                ? readRemainingInput(consoleIO)
                : Files.readString(Path.of(input));
        final BatchSceneCreationResult result =
                createSceneFromManifest(command, csvText);
        print(consoleIO, OutputFormatter.batchSceneCreation(
                result,
                OutputFormatter.isTsv(command)
        ));
        return batchResultSuccess(result)
                ? CommandResult.success()
                : CommandResult.partialFailure(
                        "One or more manifest rows failed."
                );
    }

    private CommandResult autoIndexScenes(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException {

        final List<UUID> mediaIds = autoIndexMediaIds(command);
        final AutoIndexResult result = indexingService().autoIndex(
                new AutoIndexRequest(
                        mediaIds,
                        command.hasOption("dry-run"),
                        command.hasOption("fail-fast")
                )
        );
        print(consoleIO, OutputFormatter.autoIndex(
                result,
                OutputFormatter.isTsv(command)
        ));

        return autoIndexSuccess(result)
                ? CommandResult.success()
                : CommandResult.partialFailure(
                        "One or more filename rows need attention."
                );
    }

    private List<UUID> autoIndexMediaIds(ParsedCommand command)
            throws SQLException {

        if (command.values("media").isEmpty()
                && !command.hasOption(ALL_UNASSIGNED)) {
            throw new IllegalArgumentException(
                    "Use --media or --all-unassigned for auto-index."
            );
        }

        final List<UUID> mediaIds = new ArrayList<>(command.uuids("media"));

        if (command.hasOption(ALL_UNASSIGNED)) {
            for (FilenamePreview preview
                    : indexingService().previewUnassigned(
                            new repository.UnassignedMediaFilter(
                                    command.optionalValue("contains"),
                                    optionalPath(command, "directory"),
                                    null,
                                    null,
                                    null,
                                    null,
                                    command.optionalInt(
                                            "limit",
                                            repository.UnassignedMediaFilter
                                                    .DEFAULT_LIMIT
                                    ),
                                    command.optionalInt("offset", 0)
                            )
                    )) {
                mediaIds.add(preview.mediaFileId());
            }
        }

        return List.copyOf(mediaIds);
    }

    private CommandResult reviewFilenameIndex(
            ParsedCommand command,
            ConsoleIO consoleIO) throws SQLException, IOException {

        final List<UUID> mediaIds = autoIndexMediaIds(command);
        CommandResult result = CommandResult.success();
        boolean reviewing = true;
        int index = 0;

        while (index < mediaIds.size() && reviewing) {
            final UUID mediaId = mediaIds.get(index);
            final ReviewOutcome outcome = reviewFilenameIndexRow(
                    mediaId,
                    consoleIO
            );

            if (outcome.quit()) {
                reviewing = false;
            }

            if (!outcome.succeeded()) {
                result = CommandResult.partialFailure(
                        "One or more review rows need attention."
                );
            }

            index++;
        }

        return result;
    }

    private ReviewOutcome reviewFilenameIndexRow(
            UUID mediaId,
            ConsoleIO consoleIO) throws SQLException, IOException {

        boolean reviewingRow = true;
        boolean quit = false;
        boolean succeeded = true;

        while (reviewingRow) {
            final FilenamePreview preview = indexingService().preview(mediaId);
            consoleIO.println("Media: " + mediaId);
            consoleIO.println("Path: " + preview.path());
            consoleIO.println("Status: "
                    + preview.matchResult().status());
            consoleIO.println("Title: "
                    + preview.parsedFilename().titleCandidate());

            if (preview.matchResult().status()
                    == service.FilenameMatchStatus.READY) {
                final ReviewOutcome readyOutcome = reviewReadyFilenameRow(
                        mediaId,
                        consoleIO
                );
                quit = readyOutcome.quit();
                succeeded = readyOutcome.succeeded();
                reviewingRow = false;
            } else if (preview.matchResult().status()
                    == service.FilenameMatchStatus.UNRESOLVED
                    && !preview.parsedFilename().performerCandidates()
                    .isEmpty()) {
                consoleIO.print("Create missing performers? y/N/q: ");
                final String answer = consoleIO.readLine();

                if (answer == null || "q".equalsIgnoreCase(answer.trim())) {
                    quit = true;
                    succeeded = false;
                    reviewingRow = false;
                } else if (isYes(answer)) {
                    createReviewPerformers(preview, consoleIO);
                } else {
                    consoleIO.println("Skipped " + mediaId);
                    succeeded = false;
                    reviewingRow = false;
                }
            } else {
                consoleIO.println("Skipped " + mediaId
                        + " because it is not READY.");
                succeeded = false;
                reviewingRow = false;
            }
        }

        return new ReviewOutcome(quit, succeeded);
    }

    private ReviewOutcome reviewReadyFilenameRow(
            UUID mediaId,
            ConsoleIO consoleIO) throws IOException, SQLException {

        boolean quit = false;
        boolean succeeded = true;
        consoleIO.print("Accept best interpretation? y/N/q: ");
        final String answer = consoleIO.readLine();

        if (answer == null || "q".equalsIgnoreCase(answer.trim())) {
            quit = true;
            succeeded = false;
        } else if (isYes(answer)) {
            final AutoIndexResult autoIndexResult =
                    indexingService().autoIndex(new AutoIndexRequest(
                            List.of(mediaId),
                            false,
                            true
                    ));
            final AutoIndexFileResult fileResult =
                    autoIndexResult.files().getFirst();
            if (fileResult.sceneId() == null) {
                succeeded = false;
                consoleIO.error(fileResult.status().toString());
            } else {
                consoleIO.println("Created scene "
                        + fileResult.sceneId());
            }
        } else {
            consoleIO.println("Skipped " + mediaId);
            succeeded = false;
        }

        return new ReviewOutcome(quit, succeeded);
    }

    private void createReviewPerformers(
            FilenamePreview preview,
            ConsoleIO consoleIO) throws IOException, SQLException {

        for (String performerName
                : preview.parsedFilename().performerCandidates()) {
            consoleIO.print("Category for " + performerName + ": ");
            final PerformerCategory category =
                    parsePerformerCategory(consoleIO.readLine());
            final Performer performer = catalogService.createPerformer(
                    performerName,
                    List.of(),
                    category
            );
            consoleIO.println("Created performer " + performer.getId());
        }
    }

    private PerformerCategory parsePerformerCategory(String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Performer category must not be null."
            );
        }

        PerformerCategory category = null;

        for (PerformerCategory candidate : PerformerCategory.values()) {
            if (candidate.name().equalsIgnoreCase(value.trim())) {
                category = candidate;
            }
        }

        if (category == null) {
            throw new IllegalArgumentException(
                    "Invalid performer category: " + value
            );
        }

        return category;
    }

    private MediaScanService scanService(ParsedCommand command)
            throws MediaProbeException {

        if (mediaScanService == null) {
            throw new MediaProbeException(
                    "Media scanning is not available."
            );
        }

        MediaScanService service = mediaScanService;
        final String executable = command.optionalValue("ffprobe");

        if (executable != null) {
            service = mediaScanService.withProbe(new FfprobeMediaMetadataProbe(
                    executable,
                    DEFAULT_PROBE_TIMEOUT,
                    new ProcessBuilderMediaProcessRunner(
                            DEFAULT_PROBE_TIMEOUT
                    )
            ));
        }

        return service;
    }

    private MediaAssignmentService assignmentService() {
        if (mediaAssignmentService == null) {
            throw new IllegalStateException(
                    "Media assignment handling is not available."
            );
        }

        return mediaAssignmentService;
    }

    private MediaFilenameIndexingService indexingService() {
        if (mediaFilenameIndexingService == null) {
            throw new IllegalStateException(
                    "Filename indexing is not available."
            );
        }

        return mediaFilenameIndexingService;
    }

    private DatabaseBackupFacade backupFacade() {
        if (databaseBackupFacade == null) {
            throw new IllegalStateException(
                    "Backup handling is not available."
            );
        }

        return databaseBackupFacade;
    }

    private Path optionalPath(ParsedCommand command, String name) {
        Path path = null;
        final String value = command.optionalValue(name);

        if (value != null) {
            path = Path.of(value).toAbsolutePath().normalize();
        }

        return path;
    }

    private Integer optionalInteger(ParsedCommand command, String name) {
        Integer value = null;
        final String text = command.optionalValue(name);

        if (text != null) {
            value = command.optionalInt(name, 0);
        }

        return value;
    }

    private VerificationStatus verificationStatus(String value) {
        final String normalized = value.trim()
                .replace('-', '_')
                .toUpperCase(java.util.Locale.ROOT);
        VerificationStatus status;

        try {
            status = VerificationStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid verification status: " + value,
                    exception
            );
        }

        return status;
    }

    private MediaRenameService renameService() {
        if (mediaRenameService == null) {
            throw new IllegalStateException(
                    "Media rename service is required for rename operations."
            );
        }

        return mediaRenameService;
    }

    private CommandResult commandResultForRenameResults(
            List<MediaRenameResult> results) {

        final boolean allSuccessful = results.stream()
                .allMatch(result -> isSuccessfulRenameStatus(result.status()));
        final CommandResult commandResult;

        if (allSuccessful) {
            commandResult = CommandResult.success();
        } else {
            commandResult = CommandResult.validationError(
                    "One or more media rename rows require attention."
            );
        }

        return commandResult;
    }

    private CommandResult commandResultForRenameStatus(
            MediaRenameStatus status) {

        final CommandResult result;

        if (isSuccessfulRenameStatus(status)) {
            result = CommandResult.success();
        } else {
            result = CommandResult.validationError(
                    "Media rename is not ready: " + status
            );
        }

        return result;
    }

    private boolean isSuccessfulRenameStatus(MediaRenameStatus status) {
        return status == MediaRenameStatus.READY
                || status == MediaRenameStatus.UNCHANGED
                || status == MediaRenameStatus.RENAMED;
    }

    private BatchSceneCreationResult createSceneFromManifest(
            ParsedCommand command,
            String csvText) throws IOException, SQLException {

        final List<List<String>> rows =
                new CsvReader(new StringReader(csvText)).readAll();

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV input is empty.");
        }

        final Map<String, Integer> header = CsvSchema.header(
                rows.getFirst(),
                SCENE_MANIFEST_COLUMNS,
                Set.of("media_id")
        );
        final List<BatchSceneCreationFileResult> results = new ArrayList<>();
        boolean processing = true;
        int rowIndex = 1;

        while (rowIndex < rows.size() && processing) {
            final int rowNumber = rowIndex + FIRST_DATA_ROW_NUMBER - 1;
            final BatchSceneCreationFileResult result =
                    createSceneFromManifestRow(
                            command,
                            header,
                            rows.get(rowIndex),
                            rowNumber
                    );
            results.add(result);

            if (command.hasOption("fail-fast")
                    && result.status()
                    != BatchSceneCreationStatus.CREATED
                    && result.status()
                    != BatchSceneCreationStatus.WOULD_CREATE) {
                processing = false;
            }

            rowIndex++;
        }

        return new BatchSceneCreationResult(
                results,
                summarizeManifest(results)
        );
    }

    private BatchSceneCreationFileResult createSceneFromManifestRow(
            ParsedCommand command,
            Map<String, Integer> header,
            List<String> row,
            int rowNumber) throws SQLException {

        BatchSceneCreationFileResult result;

        try {
            final UUID mediaId = UUID.fromString(CsvSchema.value(
                    header,
                    row,
                    "media_id"
            ));
            final Scene scene;

            if (command.hasOption("dry-run")) {
                final BatchSceneCreationResult batchResult =
                        assignmentService().createScenesFromMedia(
                                new BatchSceneCreationRequest(
                                        List.of(mediaId),
                                        optionalCsvUuid(
                                                header,
                                                row,
                                                "publisher_id"
                                        ),
                                        optionalCsvUuid(
                                                header,
                                                row,
                                                "series_id"
                                        ),
                                        optionalCsvDate(
                                                header,
                                                row,
                                                "release_date"
                                        ),
                                        CsvSchema.optionalValue(
                                                header,
                                                row,
                                                "season"
                                        ),
                                        CsvSchema.optionalValue(
                                                header,
                                                row,
                                                "episode"
                                        ),
                                        csvPerformerIds(header, row),
                                        true,
                                        command.hasOption("fail-fast")
                                )
                        );
                result = withRowNumber(batchResult.files().getFirst(), rowNumber);
            } else {
                scene = assignmentService().createSceneFromMedia(
                        new CreateSceneFromMediaRequest(
                                mediaId,
                                CsvSchema.optionalValue(header, row, "title"),
                                CsvSchema.optionalValue(header, row, "code"),
                                optionalCsvDate(header, row, "release_date"),
                                optionalCsvUuid(header, row, "publisher_id"),
                                optionalCsvUuid(header, row, "series_id"),
                                CsvSchema.optionalValue(
                                        header,
                                        row,
                                        "season"
                                ),
                                CsvSchema.optionalValue(
                                        header,
                                        row,
                                        "episode"
                                ),
                                csvPerformerIds(header, row)
                        )
                );
                result = new BatchSceneCreationFileResult(
                        rowNumber,
                        mediaId,
                        scene.getFiles().getFirst().getPath(),
                        scene.getTitle(),
                        scene.getId(),
                        BatchSceneCreationStatus.CREATED,
                        null
                );
            }
        } catch (IllegalArgumentException exception) {
            result = new BatchSceneCreationFileResult(
                    rowNumber,
                    null,
                    null,
                    null,
                    null,
                    BatchSceneCreationStatus.FAILED,
                    exception.getMessage()
            );
        }

        return result;
    }

    private BatchSceneCreationFileResult withRowNumber(
            BatchSceneCreationFileResult result,
            int rowNumber) {

        return new BatchSceneCreationFileResult(
                rowNumber,
                result.mediaFileId(),
                result.path(),
                result.title(),
                result.sceneId(),
                result.status(),
                result.error()
        );
    }

    private BatchSceneCreationSummary summarizeManifest(
            List<BatchSceneCreationFileResult> results) {

        int created = 0;
        int wouldCreate = 0;
        int alreadyAssigned = 0;
        int missing = 0;
        int failed = 0;

        for (BatchSceneCreationFileResult result : results) {
            if (result.status()
                    == BatchSceneCreationStatus.CREATED) {
                created++;
            } else if (result.status()
                    == BatchSceneCreationStatus.WOULD_CREATE) {
                wouldCreate++;
            } else if (result.status()
                    == BatchSceneCreationStatus.ALREADY_ASSIGNED) {
                alreadyAssigned++;
            } else if (result.status()
                    == BatchSceneCreationStatus.MEDIA_NOT_FOUND) {
                missing++;
            } else if (result.status()
                    == BatchSceneCreationStatus.FAILED) {
                failed++;
            }
        }

        return new BatchSceneCreationSummary(
                results.size(),
                created,
                wouldCreate,
                alreadyAssigned,
                missing,
                failed
        );
    }

    private UUID optionalCsvUuid(
            Map<String, Integer> header,
            List<String> row,
            String column) {

        UUID id = null;
        final String value = CsvSchema.optionalValue(header, row, column);

        if (value != null) {
            id = UUID.fromString(value);
        }

        return id;
    }

    private LocalDate optionalCsvDate(
            Map<String, Integer> header,
            List<String> row,
            String column) {

        LocalDate date = null;
        final String value = CsvSchema.optionalValue(header, row, column);

        if (value != null) {
            date = LocalDate.parse(value);
        }

        return date;
    }

    private List<UUID> csvPerformerIds(
            Map<String, Integer> header,
            List<String> row) {

        final List<UUID> ids = new ArrayList<>();
        final String value = CsvSchema.optionalValue(
                header,
                row,
                "performer_ids"
        );

        if (value != null) {
            for (String idText : value.split(";")) {
                if (!idText.isBlank()) {
                    ids.add(UUID.fromString(idText.trim()));
                }
            }
        }

        return ids;
    }

    private boolean batchResultSuccess(BatchSceneCreationResult result) {
        return result.summary().alreadyAssigned() == 0
                && result.summary().missing() == 0
                && result.summary().failed() == 0;
    }

    private boolean autoIndexSuccess(AutoIndexResult result) {
        return result.summary().reviewRequired() == 0
                && result.summary().ambiguous() == 0
                && result.summary().unresolved() == 0
                && result.summary().invalid() == 0
                && result.summary().alreadyAssigned() == 0
                && result.summary().failed() == 0;
    }

    private CsvImportSummary importMediaRows(
            ParsedCommand command,
            ConsoleIO consoleIO,
            String csvText) throws IOException {

        final List<List<String>> rows =
                new CsvReader(new StringReader(csvText)).readAll();
        int rowsRead = 0;
        int rowsImported = 0;
        int rowsFailed = 0;
        boolean processing = true;

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV input is empty.");
        }

        final Map<String, Integer> header = CsvSchema.header(rows.getFirst());
        int rowIndex = 1;

        while (rowIndex < rows.size() && processing) {
            rowsRead++;
            final int inputRowNumber = rowIndex + FIRST_DATA_ROW_NUMBER - 1;

            try {
                final MediaFile mediaFile =
                        createMediaFileFromCsvRow(header, rows.get(rowIndex));
                rowsImported++;

                if (OutputFormatter.isTsv(command)) {
                    consoleIO.println("record\t" + inputRowNumber + "\t"
                            + mediaFile.getId() + "\t"
                            + OutputFormatter.escape(
                            mediaFile.getPath().toString()
                    ));
                } else {
                    consoleIO.println("row " + inputRowNumber
                            + " created " + mediaFile.getId()
                            + " " + mediaFile.getPath());
                }
            } catch (IllegalArgumentException | SQLException exception) {
                rowsFailed++;
                consoleIO.error("row " + inputRowNumber + ": "
                        + exception.getMessage());

                if (command.hasOption("fail-fast")) {
                    processing = false;
                }
            }

            rowIndex++;
        }

        if (OutputFormatter.isTsv(command)) {
            consoleIO.error("summary\trows_read\t" + rowsRead
                    + "\trows_imported\t" + rowsImported
                    + "\trows_failed\t" + rowsFailed);
        } else {
            consoleIO.println("Rows read: " + rowsRead);
            consoleIO.println("Rows imported: " + rowsImported);
            consoleIO.println("Rows failed: " + rowsFailed);
        }

        return new CsvImportSummary(rowsRead, rowsImported, rowsFailed);
    }

    private MediaFile createMediaFileFromCsvRow(
            Map<String, Integer> header,
            List<String> row) throws SQLException, IOException {

        final String pathText = CsvSchema.value(header, row, PATH_COLUMN);
        final Path path = normalizePath(pathText);
        final long fileSize = CsvSchema.optionalLong(
                header,
                row,
                FILE_SIZE_COLUMN,
                existingFileSize(path)
        );
        final long durationMillis = CsvSchema.optionalLong(
                header,
                row,
                DURATION_MILLIS_COLUMN,
                CsvSchema.optionalLong(
                        header,
                        row,
                        DURATION_COLUMN,
                        0L
                )
        );
        final Duration duration =
                durationMillis == 0L ? null : Duration.ofMillis(durationMillis);

        return catalogService.createMediaFile(
                path,
                fileSize,
                duration,
                CsvSchema.optionalInt(
                        header,
                        row,
                        WIDTH_COLUMN,
                        DEFAULT_DIMENSION
                ),
                CsvSchema.optionalInt(
                        header,
                        row,
                        HEIGHT_COLUMN,
                        DEFAULT_DIMENSION
                ),
                CsvSchema.optionalValue(header, row, HASH_COLUMN),
                CsvSchema.optionalLong(
                        header,
                        row,
                        LAST_MODIFIED_COLUMN,
                        existingLastModified(path)
                )
        );
    }

    private CommandResult handleInteractiveChoice(
            String choice,
            ConsoleIO consoleIO) throws IOException {

        CommandResult result = CommandResult.success();

        try {
            if ("1".equals(choice)) {
                consoleIO.print("Name: ");
                final Publisher publisher = catalogService.createPublisher(
                        consoleIO.readLine(),
                        List.of()
                );
                consoleIO.println("Created publisher " + publisher.getId());
            } else if ("2".equals(choice)) {
                consoleIO.print("Name: ");
                final String name = consoleIO.readLine();
                consoleIO.print("Category: ");
                final PerformerCategory category =
                        PerformerCategory.valueOf(
                                consoleIO.readLine().trim().toUpperCase(
                                        java.util.Locale.ROOT
                                )
                        );
                final Performer performer = catalogService.createPerformer(
                        name,
                        List.of(),
                        category
                );
                consoleIO.println("Created performer " + performer.getId());
            } else if ("5".equals(choice)) {
                consoleIO.print("Title: ");
                final String title = consoleIO.readLine();
                consoleIO.print("Publisher UUID: ");
                final UUID publisherId =
                        UUID.fromString(consoleIO.readLine().trim());
                consoleIO.print("Performer UUIDs comma-separated: ");
                final List<UUID> performerIds =
                        parseCommaSeparatedUuids(consoleIO.readLine());
                final Scene scene = catalogService.createScene(
                        title,
                        null,
                        null,
                        publisherId,
                        null,
                        null,
                        null,
                        performerIds,
                        List.of()
                );
                consoleIO.println("Created scene " + scene.getId());
            } else if ("13".equals(choice)) {
                consoleIO.print("Performer UUID: ");
                final UUID performerId =
                        UUID.fromString(consoleIO.readLine().trim());
                print(consoleIO, OutputFormatter.scenes(
                        catalogService.findScenesByPerformer(performerId),
                        false
                ));
            } else if ("16".equals(choice)) {
                consoleIO.print("Root directory: ");
                final String root = consoleIO.readLine();
                consoleIO.print("Hash files? y/N: ");
                final boolean hash = isYes(consoleIO.readLine());
                consoleIO.print("Dry run? y/N: ");
                final boolean dryRun = isYes(consoleIO.readLine());
                consoleIO.print("Fail fast? y/N: ");
                final boolean failFast = isYes(consoleIO.readLine());
                final MediaScanResult scanResult = scanService(
                        new ParsedCommand(
                                List.of(MEDIA, SCAN),
                                Map.of(),
                                null,
                                "human",
                                false,
                                false
                        )
                ).scan(new MediaScanRequest(
                        Path.of(root),
                        hash,
                        dryRun,
                        failFast,
                        Set.of()
                ));
                print(consoleIO, OutputFormatter.mediaScan(scanResult, false));
            } else if ("17".equals(choice)) {
                consoleIO.print("Refresh changed files? y/N: ");
                final boolean refresh = isYes(consoleIO.readLine());
                consoleIO.print("Dry run? y/N: ");
                final boolean dryRun = isYes(consoleIO.readLine());
                consoleIO.print("Fail fast? y/N: ");
                final boolean failFast = isYes(consoleIO.readLine());
                final MediaVerificationResult verificationResult =
                        scanService(new ParsedCommand(
                                List.of(MEDIA, VERIFY),
                                Map.of(),
                                null,
                                "human",
                                false,
                                false
                        )).verify(new MediaVerificationRequest(
                                refresh,
                                dryRun,
                                failFast
                        ));
                print(consoleIO, OutputFormatter.mediaVerification(
                        verificationResult,
                        false
                ));
            } else if ("18".equals(choice)) {
                print(consoleIO, OutputFormatter.unassignedMediaFiles(
                        assignmentService().findUnassigned(
                                repository.UnassignedMediaFilter.firstPage()
                        ),
                        false
                ));
            } else if ("19".equals(choice)) {
                consoleIO.print("Media UUID: ");
                final UUID mediaId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                print(consoleIO, OutputFormatter.mediaAssignment(
                        assignmentService().findAssignment(mediaId),
                        false
                ));
            } else if ("20".equals(choice)) {
                consoleIO.print("Scene UUID: ");
                final UUID sceneId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                consoleIO.print("Media UUID: ");
                final UUID mediaId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                final Scene scene = assignmentService().attachToScene(
                        mediaId,
                        sceneId
                );
                consoleIO.println("Attached media to scene " + scene.getId());
            } else if ("21".equals(choice)) {
                consoleIO.print("Movie UUID: ");
                final UUID movieId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                consoleIO.print("Media UUID: ");
                final UUID mediaId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                final Movie movie = assignmentService().attachToMovie(
                        mediaId,
                        movieId
                );
                consoleIO.println("Attached media to movie " + movie.getId());
            } else if ("22".equals(choice)) {
                consoleIO.print("Media UUID: ");
                final UUID mediaId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                consoleIO.print("Publisher UUID: ");
                final UUID publisherId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                consoleIO.print("Title blank to derive: ");
                final String title = consoleIO.readLine();
                final Scene scene = assignmentService().createSceneFromMedia(
                        new CreateSceneFromMediaRequest(
                                mediaId,
                                title,
                                null,
                                null,
                                publisherId,
                                null,
                                null,
                                null,
                                List.of()
                        )
                );
                consoleIO.println("Created scene " + scene.getId());
            } else if ("23".equals(choice)) {
                print(consoleIO, OutputFormatter.filenamePreviews(
                        indexingService().previewUnassigned(
                                repository.UnassignedMediaFilter.firstPage()
                        ),
                        false
                ));
            } else if ("24".equals(choice)) {
                consoleIO.print("Dry run? y/N: ");
                final boolean dryRun = isYes(consoleIO.readLine());
                final List<UUID> mediaIds = indexingService()
                        .previewUnassigned(
                                repository.UnassignedMediaFilter.firstPage()
                        )
                        .stream()
                        .map(FilenamePreview::mediaFileId)
                        .toList();
                print(consoleIO, OutputFormatter.autoIndex(
                        indexingService().autoIndex(new AutoIndexRequest(
                                mediaIds,
                                dryRun,
                                false
                        )),
                        false
                ));
            } else if ("25".equals(choice)) {
                final ParsedCommand command = new ParsedCommand(
                        List.of(SCENE, INDEX_REVIEW),
                        Map.of(ALL_UNASSIGNED, List.of()),
                        null,
                        "human",
                        false,
                        false
                );
                result = reviewFilenameIndex(command, consoleIO);
            } else if ("26".equals(choice)) {
                consoleIO.print("Destination: ");
                final Path destination = Path.of(consoleIO.readLine());
                consoleIO.print("Overwrite? y/N: ");
                final boolean overwrite = isYes(consoleIO.readLine());
                consoleIO.print("Verification level quick/full: ");
                final BackupVerificationLevel level =
                        BackupVerificationLevel.parse(consoleIO.readLine());
                print(consoleIO, OutputFormatter.backup(
                        backupFacade().createBackup(
                                destination,
                                overwrite,
                                level
                        ),
                        false
                ));
            } else if ("27".equals(choice)) {
                consoleIO.print("Backup path: ");
                final Path input = Path.of(consoleIO.readLine());
                consoleIO.print("Verification level quick/full: ");
                final BackupVerificationLevel level =
                        BackupVerificationLevel.parse(consoleIO.readLine());
                print(consoleIO, OutputFormatter.backupVerification(
                        backupFacade().verify(input, level),
                        false
                ));
            } else if ("28".equals(choice)) {
                consoleIO.print("Backup path: ");
                final Path input = Path.of(consoleIO.readLine());
                consoleIO.print("New destination path: ");
                final Path destination = Path.of(consoleIO.readLine());
                consoleIO.print("Overwrite? y/N: ");
                final boolean overwrite = isYes(consoleIO.readLine());
                consoleIO.print("Verification level quick/full: ");
                final BackupVerificationLevel level =
                        BackupVerificationLevel.parse(consoleIO.readLine());
                print(consoleIO, OutputFormatter.restore(
                        backupFacade().restore(
                                input,
                                destination,
                                overwrite,
                                level
                        ),
                        false
                ));
            } else if ("29".equals(choice)) {
                consoleIO.print("Verification status: ");
                final VerificationStatus status =
                        verificationStatus(consoleIO.readLine());
                print(consoleIO, OutputFormatter.sceneVerification(
                        catalogService.findScenesByVerificationStatus(
                                status,
                                100,
                                0
                        ),
                        false
                ));
            } else if ("30".equals(choice)) {
                consoleIO.print("Scene UUID: ");
                final UUID sceneId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                consoleIO.print("Verification status: ");
                final VerificationStatus status =
                        verificationStatus(consoleIO.readLine());
                final Scene scene;

                if (status == VerificationStatus.VERIFIED) {
                    scene = catalogService.markSceneVerified(sceneId);
                } else if (status == VerificationStatus.NEEDS_REVIEW) {
                    scene = catalogService.markSceneNeedsReview(sceneId);
                } else {
                    scene = catalogService.markSceneUnverified(sceneId);
                }

                print(consoleIO, OutputFormatter.sceneVerificationRow(
                        scene,
                        false
                ));
            } else if ("31".equals(choice)) {
                consoleIO.print("Media UUID: ");
                final UUID mediaId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                print(consoleIO, OutputFormatter.mediaRenamePreview(
                        renameService().preview(new MediaRenameRequest(
                                mediaId,
                                null,
                                null
                        )),
                        false
                ));
            } else if ("32".equals(choice)) {
                consoleIO.print("Media UUID: ");
                final UUID mediaId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                final MediaRenamePreview preview =
                        renameService().preview(new MediaRenameRequest(
                                mediaId,
                                null,
                                null
                        ));
                print(consoleIO, OutputFormatter.mediaRenamePreview(
                        preview,
                        false
                ));
                consoleIO.print("Rename file? y/N: ");

                if (isYes(consoleIO.readLine())) {
                    print(consoleIO, OutputFormatter.mediaRenameResults(
                            List.of(renameService().rename(
                                    new MediaRenameRequest(
                                            mediaId,
                                            null,
                                            null
                                    )
                            )),
                            false
                    ));
                }
            } else if ("33".equals(choice)) {
                consoleIO.print("Scene UUID: ");
                final UUID sceneId = UUID.fromString(
                        consoleIO.readLine().trim()
                );
                consoleIO.print("Dry run? y/N: ");
                final boolean dryRun = isYes(consoleIO.readLine());
                final MediaRenameBatchResult batchResult =
                        renameService().renameSceneMedia(
                                sceneId,
                                null,
                                dryRun,
                                false
                        );
                print(consoleIO, OutputFormatter.mediaRenameResults(
                        batchResult.results(),
                        false
                ));
            } else {
                consoleIO.error("Invalid menu choice: " + choice);
            }
        } catch (IllegalArgumentException | SQLException
                 | MediaProbeException exception) {
            result = CommandResult.validationError(exception.getMessage());
        }

        return result;
    }

    private List<UUID> parseCommaSeparatedUuids(String text) {
        final List<UUID> ids = new ArrayList<>();

        if (text != null && !text.isBlank()) {
            for (String value : text.split(",")) {
                ids.add(UUID.fromString(value.trim()));
            }
        }

        return ids;
    }

    private void printCreatedId(
            ConsoleIO consoleIO,
            ParsedCommand command,
            UUID id) {

        if (OutputFormatter.isTsv(command)) {
            consoleIO.println("id");
        }

        consoleIO.println(id.toString());
    }

    private void print(ConsoleIO consoleIO, String text) {
        if (!text.isEmpty()) {
            consoleIO.print(text);
        }
    }

    private boolean matches(
            ParsedCommand command,
            String first,
            String second) {

        return command.path().equals(List.of(first, second));
    }

    private boolean matches(
            ParsedCommand command,
            String first,
            String second,
            String third) {

        return command.path().equals(List.of(first, second, third));
    }

    private boolean isInit(ParsedCommand command) {
        return command.path().equals(List.of(INIT));
    }

    private boolean isBackupCommand(ParsedCommand command) {
        return command.path().size() == 2
                && BACKUP.equals(command.path().getFirst());
    }

    private void requireOnlyOptions(
            ParsedCommand command,
            Set<String> allowedOptions) {

        for (String option : command.options().keySet()) {
            if (!allowedOptions.contains(option)) {
                throw new IllegalArgumentException(
                        "Unknown option for command: --" + option
                );
            }
        }
    }

    private Path normalizePath(String pathText) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalArgumentException(
                    "Media path must not be blank."
            );
        }

        return Path.of(pathText).toAbsolutePath().normalize();
    }

    private long fileSize(ParsedCommand command, Path path)
            throws IOException {

        long fileSize = command.optionalLong(
                "file-size",
                DEFAULT_FILE_SIZE
        );

        if (fileSize == DEFAULT_FILE_SIZE && Files.isRegularFile(path)) {
            fileSize = Files.size(path);
        }

        return fileSize;
    }

    private long existingFileSize(Path path) throws IOException {
        long fileSize = DEFAULT_FILE_SIZE;

        if (Files.isRegularFile(path)) {
            fileSize = Files.size(path);
        }

        return fileSize;
    }

    private long lastModified(ParsedCommand command, Path path)
            throws IOException {

        long lastModifiedMillis = command.optionalLong(
                "last-modified",
                0L
        );

        if (lastModifiedMillis == 0L && Files.isRegularFile(path)) {
            lastModifiedMillis =
                    Files.getLastModifiedTime(path).toMillis();
        }

        return lastModifiedMillis;
    }

    private long existingLastModified(Path path) throws IOException {
        long lastModifiedMillis = 0L;

        if (Files.isRegularFile(path)) {
            lastModifiedMillis =
                    Files.getLastModifiedTime(path).toMillis();
        }

        return lastModifiedMillis;
    }

    private Duration optionalDuration(ParsedCommand command) {
        Duration duration = null;
        final long durationMillis =
                command.optionalLong("duration", DEFAULT_FILE_SIZE);

        if (durationMillis > DEFAULT_FILE_SIZE) {
            duration = Duration.ofMillis(durationMillis);
        }

        return duration;
    }

    private String readRemainingInput(ConsoleIO consoleIO)
            throws IOException {

        final StringBuilder builder = new StringBuilder();
        String line = consoleIO.readLine();

        while (line != null) {
            builder.append(line).append(System.lineSeparator());
            line = consoleIO.readLine();
        }

        return builder.toString();
    }

    private void printMenu(ConsoleIO consoleIO) {
        consoleIO.println("JAVDB");
        consoleIO.println("1. Add publisher");
        consoleIO.println("2. Add performer");
        consoleIO.println("3. Add series");
        consoleIO.println("4. Add media file");
        consoleIO.println("5. Add scene");
        consoleIO.println("6. Add movie");
        consoleIO.println("7. List publishers");
        consoleIO.println("8. List performers");
        consoleIO.println("9. List series");
        consoleIO.println("10. List media files");
        consoleIO.println("11. List scenes");
        consoleIO.println("12. List movies");
        consoleIO.println("13. Search scenes by performer");
        consoleIO.println("14. View a record by ID");
        consoleIO.println("15. Exit");
        consoleIO.println("16. Scan a directory");
        consoleIO.println("17. Verify media paths");
        consoleIO.println("18. List unassigned media");
        consoleIO.println("19. Show media assignment");
        consoleIO.println("20. Attach media to scene");
        consoleIO.println("21. Attach media to movie");
        consoleIO.println("22. Create scene from media");
        consoleIO.println("23. Preview parsed filenames");
        consoleIO.println("24. Automatically index READY files");
        consoleIO.println("25. Review ambiguous or unresolved files");
        consoleIO.println("26. Create database backup");
        consoleIO.println("27. Verify database backup");
        consoleIO.println("28. Restore backup to new database file");
        consoleIO.println("29. List scenes by verification status");
        consoleIO.println("30. Change scene verification status");
        consoleIO.println("31. Preview a media rename");
        consoleIO.println("32. Rename one media file");
        consoleIO.println("33. Preview or rename media for one scene");
        consoleIO.print("> ");
    }

    private boolean isYes(String text) {
        return "y".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private record CsvImportSummary(
            int rowsRead,
            int importedRows,
            int failedRows) {
    }

    private record ReviewOutcome(
            boolean quit,
            boolean succeeded) {
    }

    private static final class ServiceBackend implements CliBackend {
        @Override
        public void markInteractiveStarted() {
        }

        @Override
        public void markOneShotStarted() {
        }
    }
}

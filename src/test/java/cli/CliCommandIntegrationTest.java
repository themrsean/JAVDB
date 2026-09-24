package cli;

import database.DatabaseManager;
import database.SchemaManager;
import backup.DatabaseBackupFacade;
import media.FileHasher;
import media.MediaMetadata;
import media.MediaMetadataProbe;
import media.MediaProbeException;
import media.MediaFilenameParser;
import media.VideoFileDiscovery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaFileRepository;
import repository.MediaAssignmentRepository;
import repository.EntitySuggestionRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;
import service.CatalogService;
import service.FilenameMetadataMatcher;
import service.MediaAssignmentService;
import service.MediaFilenameIndexingService;
import service.MediaRenameService;
import service.MediaScanService;
import service.MediaTitleDeriver;
import service.OriginalMovieSelector;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

class CliCommandIntegrationTest {
    private static final String DATABASE_FILE_NAME =
            "cli-command-integration-test.db";
    private static final int SUCCESS_STATUS = 0;
    private static final int FAILURE_STATUS = 0;
    private static final long LAST_MODIFIED_MILLIS =
            1_701_111_222_333L;
    private static final int PROBE_WIDTH = 1920;
    private static final int PROBE_HEIGHT = 1080;
    private static final long PROBE_DURATION_MILLIS = 1_234L;

    @TempDir
    Path temporaryDirectory;

    private CommandLineInterface cli;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        final MediaFileRepository mediaFileRepository =
                new MediaFileRepository(databaseManager);
        final PublisherRepository publisherRepository =
                new PublisherRepository(databaseManager);
        final PerformerRepository performerRepository =
                new PerformerRepository(databaseManager);
        final SeriesRepository seriesRepository =
                new SeriesRepository(databaseManager);
        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final SearchRepository searchRepository =
                new SearchRepository(databaseManager);
        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);
        final CatalogService catalogService = new CatalogService(
                publisherRepository,
                performerRepository,
                seriesRepository,
                mediaFileRepository,
                sceneRepository,
                searchRepository,
                movieRepository
        );
        cli = new CommandLineInterface(catalogService, null, new MediaScanService(
                mediaFileRepository,
                new VideoFileDiscovery(),
                new FakeProbe(),
                new FileHasher()
        ), new MediaAssignmentService(
                new MediaAssignmentRepository(databaseManager),
                mediaFileRepository,
                sceneRepository,
                movieRepository,
                catalogService,
                new MediaTitleDeriver()
        ), new MediaFilenameIndexingService(
                mediaFileRepository,
                new MediaAssignmentRepository(databaseManager),
                new MediaFilenameParser(),
                new FilenameMetadataMatcher(
                        new EntitySuggestionRepository(databaseManager)
                ),
                catalogService,
                sceneRepository,
                movieRepository
        ), new DatabaseBackupFacade(databaseManager.getDatabasePath()),
                new MediaRenameService(
                        mediaFileRepository,
                        sceneRepository,
                        new MediaAssignmentRepository(databaseManager),
                        new OriginalMovieSelector(movieRepository)
                ));
    }

    @Test
    @DisplayName("Publisher add prints UUID and list shows created publisher")
    void publisherAddPrintsUuidAndListShowsCreatedPublisher() {
        final TestConsole addConsole = run(
                "publisher", "add",
                "--name", "Publisher",
                "--alias", "Pub"
        );
        final UUID publisherId = UUID.fromString(addConsole.output().trim());
        final TestConsole listConsole = run("publisher", "list");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        addConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(
                        listConsole.output().contains(publisherId.toString())
                ),
                () -> Assertions.assertTrue(
                        listConsole.output().contains("Publisher")
                )
        );
    }

    @Test
    @DisplayName("Scene workflow supports performer search")
    void sceneWorkflowSupportsPerformerSearch() {
        final UUID publisherId = addPublisher();
        final UUID performerId = addPerformer();
        final UUID sceneId = UUID.fromString(run(
                "scene", "add",
                "--title", "Scene",
                "--publisher", publisherId.toString(),
                "--performer", performerId.toString()
        ).output().trim());

        final TestConsole searchConsole = run(
                "scene", "search-by-performer",
                "--performer", performerId.toString()
        );

        Assertions.assertTrue(searchConsole.output().contains(sceneId.toString()));
    }

    @Test
    @DisplayName("Media add list and show work with TSV output")
    void mediaAddListAndShowWorkWithTsvOutput() {
        final Path mediaPath = temporaryDirectory.resolve("a.mp4");
        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", mediaPath.toString(),
                "--width", "1920",
                "--height", "1080",
                "--last-modified", String.valueOf(LAST_MODIFIED_MILLIS),
                "--output", "tsv"
        ).output().lines().skip(1).findFirst().orElseThrow().split("\t")[0]);

        final TestConsole listConsole = run(
                "media", "list",
                "--output", "tsv"
        );
        final TestConsole showConsole = run(
                "media", "show",
                "--id", mediaId.toString()
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        listConsole.output().startsWith(
                                "id\tpath\tfile_size\tduration_millis\twidth\theight\tcontent_hash\tlast_modified_millis"
                        )
                ),
                () -> Assertions.assertTrue(
                        listConsole.output().contains(mediaId.toString())
                ),
                () -> Assertions.assertTrue(
                        showConsole.output().contains(mediaId.toString())
                ),
                () -> Assertions.assertTrue(
                        listConsole.output().contains(
                                String.valueOf(LAST_MODIFIED_MILLIS)
                        )
                )
        );
    }

    @Test
    @DisplayName("Scene verification set and list support TSV output")
    void sceneVerificationSetAndListSupportTsvOutput() throws Exception {
        final UUID publisherId = addPublisher();
        final UUID performerId = addPerformer();
        final UUID sceneId = UUID.fromString(run(
                "scene", "add",
                "--title", "Verify Me",
                "--publisher", publisherId.toString(),
                "--performer", performerId.toString()
        ).output().trim());

        final TestConsole setConsole = run(
                "scene", "verification", "set",
                "--scene", sceneId.toString(),
                "--status", "verified",
                "--output", "tsv"
        );
        final TestConsole listConsole = run(
                "scene", "verification", "list",
                "--status", "verified",
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        setConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(
                        setConsole.output().contains("VERIFIED")
                ),
                () -> Assertions.assertTrue(
                        listConsole.output().startsWith(
                                "scene_id\tverification_status"
                        )
                ),
                () -> Assertions.assertTrue(
                        listConsole.output().contains(sceneId.toString())
                )
        );
    }

    @Test
    @DisplayName("Media rename preview and dry run support TSV output")
    void mediaRenamePreviewAndDryRunSupportTsvOutput() throws Exception {
        final UUID publisherId = addPublisher();
        final UUID performerId = addPerformer();
        final Path mediaPath = temporaryDirectory.resolve("old.mp4");
        Files.writeString(mediaPath, "video");
        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", mediaPath.toString(),
                "--width", "1920",
                "--height", "1080"
        ).output().trim());
        final UUID sceneId = UUID.fromString(run(
                "scene", "add",
                "--title", "Rename Scene",
                "--release-date", "2026-01-15",
                "--publisher", publisherId.toString(),
                "--performer", performerId.toString(),
                "--media", mediaId.toString()
        ).output().trim());

        final TestConsole previewConsole = run(
                "media", "rename-preview",
                "--media", mediaId.toString(),
                "--output", "tsv"
        );
        final TestConsole dryRunConsole = run(
                "scene", "rename-media",
                "--scene", sceneId.toString(),
                "--dry-run",
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        previewConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(
                        previewConsole.output().startsWith(
                                "status\tmedia_id\tscene_id"
                        )
                ),
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        dryRunConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(Files.exists(mediaPath))
        );
    }

    @Test
    @DisplayName("Media add derives file size and last modified from filesystem")
    void mediaAddDerivesFileSizeAndLastModifiedFromFilesystem()
            throws Exception {

        final Path mediaPath = temporaryDirectory.resolve("derived.mp4");
        Files.writeString(mediaPath, "fixture");
        final long actualLastModified =
                Files.getLastModifiedTime(mediaPath).toMillis();

        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", mediaPath.toString(),
                "--output", "tsv"
        ).output().lines().skip(1).findFirst().orElseThrow().split("\t")[0]);
        final TestConsole showConsole = run(
                "media", "show",
                "--id", mediaId.toString(),
                "--output", "tsv"
        );

        Assertions.assertTrue(
                showConsole.output().contains(
                        String.valueOf(actualLastModified)
                )
        );
    }

    @Test
    @DisplayName("Series and movie commands preserve movie scene order")
    void seriesAndMovieCommandsPreserveMovieSceneOrder() {
        final UUID publisherId = addPublisher();
        final UUID performerId = addPerformer();
        final UUID firstSceneId = UUID.fromString(run(
                "scene", "add",
                "--title", "First",
                "--publisher", publisherId.toString(),
                "--performer", performerId.toString()
        ).output().trim());
        final UUID secondSceneId = UUID.fromString(run(
                "scene", "add",
                "--title", "Second",
                "--publisher", publisherId.toString(),
                "--performer", performerId.toString()
        ).output().trim());
        final UUID seriesId = UUID.fromString(run(
                "series", "add",
                "--title", "Series",
                "--publisher", publisherId.toString()
        ).output().trim());
        final UUID movieId = UUID.fromString(run(
                "movie", "add",
                "--title", "Movie",
                "--publisher", publisherId.toString(),
                "--scene", secondSceneId.toString(),
                "--scene", firstSceneId.toString()
        ).output().trim());

        final TestConsole movieConsole = run(
                "movie", "show",
                "--id", movieId.toString()
        );

        Assertions.assertAll(
                () -> Assertions.assertNotNull(seriesId),
                () -> Assertions.assertTrue(
                        movieConsole.output().indexOf(secondSceneId.toString())
                                < movieConsole.output().indexOf(firstSceneId.toString())
                )
        );
    }

    @Test
    @DisplayName("Unknown related IDs produce nonzero status")
    void unknownRelatedIdsProduceNonzeroStatus() {
        final TestConsole console = run(
                "scene", "add",
                "--title", "Scene",
                "--publisher", UUID.randomUUID().toString()
        );

        Assertions.assertAll(
                () -> Assertions.assertNotEquals(
                        FAILURE_STATUS,
                        console.result().exitStatus()
                ),
                () -> Assertions.assertFalse(console.error().isBlank())
        );
    }

    @Test
    @DisplayName("CSV media import from file and stdin creates records")
    void csvMediaImportFromFileAndStdinCreatesRecords() throws Exception {
        final Path csvPath = temporaryDirectory.resolve("media.csv");
        Files.writeString(
                csvPath,
                "path,width,height,last_modified_millis\n\"/video/a,one.mp4\",1920,1080,"
                        + LAST_MODIFIED_MILLIS + "\n"
        );

        final TestConsole fileConsole = run(
                "media", "import",
                "--input", csvPath.toString()
        );
        final TestConsole stdinConsole = runWithInput(
                "path,width,height\n/video/b.mp4,1280,720\n",
                "media", "import",
                "--input", "-"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        fileConsole.result().exitStatus()
                ),
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        stdinConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(
                        fileConsole.output().contains("Rows read: 1")
                ),
                () -> Assertions.assertTrue(
                        stdinConsole.output().contains("Rows imported: 1")
                )
        );
    }

    @Test
    @DisplayName("Media scan adds discovered files with TSV output")
    void mediaScanAddsDiscoveredFilesWithTsvOutput() throws Exception {
        final Path mediaPath = temporaryDirectory.resolve("scan.mp4");
        Files.writeString(mediaPath, "fixture");

        final TestConsole console = run(
                "media", "scan",
                "--root", temporaryDirectory.toString(),
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        console.result().exitStatus()
                ),
                () -> Assertions.assertTrue(console.output().startsWith(
                        "status\tmedia_id\tpath\tfile_size\tlast_modified_millis\twidth\theight\tduration_millis"
                )),
                () -> Assertions.assertTrue(console.output().contains("ADDED")),
                () -> Assertions.assertTrue(console.output().contains(
                        mediaPath.toAbsolutePath().normalize().toString()
                ))
        );
    }

    @Test
    @DisplayName("Media scan dry run does not persist discovered files")
    void mediaScanDryRunDoesNotPersistDiscoveredFiles() throws Exception {
        Files.writeString(temporaryDirectory.resolve("dry.mp4"), "fixture");

        final TestConsole scanConsole = run(
                "media", "scan",
                "--root", temporaryDirectory.toString(),
                "--dry-run"
        );
        final TestConsole listConsole = run("media", "list");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        scanConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(scanConsole.output().contains(
                        "WOULD_ADD"
                )),
                () -> Assertions.assertTrue(listConsole.output().isBlank())
        );
    }

    @Test
    @DisplayName("Media verify reports missing files with nonzero status")
    void mediaVerifyReportsMissingFilesWithNonzeroStatus() throws Exception {
        final Path mediaPath = temporaryDirectory.resolve("missing.mp4");
        Files.writeString(mediaPath, "fixture");
        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", mediaPath.toString()
        ).output().trim());
        Files.delete(mediaPath);

        final TestConsole console = run(
                "media", "verify",
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertNotEquals(
                        SUCCESS_STATUS,
                        console.result().exitStatus()
                ),
                () -> Assertions.assertTrue(console.output().contains(
                        mediaId.toString()
                )),
                () -> Assertions.assertTrue(console.output().contains(
                        "MISSING"
                ))
        );
    }

    @Test
    @DisplayName("Media probe-check uses executable override")
    void mediaProbeCheckUsesExecutableOverride() throws Exception {
        final Path executable = temporaryDirectory.resolve("fake-ffprobe");
        Files.writeString(executable, """
                #!/bin/sh
                echo ffprobe fake
                """);
        executable.toFile().setExecutable(true);

        final TestConsole console = run(
                "media", "probe-check",
                "--ffprobe", executable.toString()
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        console.result().exitStatus()
                ),
                () -> Assertions.assertTrue(console.output().contains(
                        "ffprobe fake"
                ))
        );
    }

    @Test
    @DisplayName("Unassigned media can be listed and assigned to a scene")
    void unassignedMediaCanBeListedAndAssignedToScene() {
        final UUID publisherId = addPublisher();
        final UUID mediaId = addMedia("unassigned-one.mp4");
        final UUID sceneId = UUID.fromString(run(
                "scene", "add",
                "--title", "Existing Scene",
                "--publisher", publisherId.toString()
        ).output().trim());

        final TestConsole listConsole = run(
                "media", "unassigned",
                "--output", "tsv"
        );
        final TestConsole attachConsole = run(
                "scene", "attach-media",
                "--scene", sceneId.toString(),
                "--media", mediaId.toString()
        );
        final TestConsole assignmentConsole = run(
                "media", "assignment",
                "--media", mediaId.toString(),
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(listConsole.output().contains(
                        mediaId.toString()
                )),
                () -> Assertions.assertEquals(SUCCESS_STATUS,
                        attachConsole.result().exitStatus()),
                () -> Assertions.assertTrue(assignmentConsole.output()
                        .contains("SCENE")),
                () -> Assertions.assertTrue(assignmentConsole.output()
                        .contains(sceneId.toString()))
        );
    }

    @Test
    @DisplayName("Scene create-from-media derives title and removes unassigned row")
    void sceneCreateFromMediaDerivesTitleAndRemovesUnassignedRow() {
        final UUID publisherId = addPublisher();
        final UUID mediaId = addMedia("Derived.Scene.mp4");

        final TestConsole createConsole = run(
                "scene", "create-from-media",
                "--media", mediaId.toString(),
                "--publisher", publisherId.toString(),
                "--output", "tsv"
        );
        final TestConsole listConsole = run(
                "media", "unassigned",
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(SUCCESS_STATUS,
                        createConsole.result().exitStatus()),
                () -> Assertions.assertTrue(createConsole.output().contains(
                        "Derived Scene"
                )),
                () -> Assertions.assertFalse(listConsole.output().contains(
                        mediaId.toString()
                ))
        );
    }

    @Test
    @DisplayName("Scene create-from-media batch supports stdin TSV dry run")
    void sceneCreateFromMediaBatchSupportsStdinTsvDryRun() {
        final UUID publisherId = addPublisher();
        final UUID mediaId = addMedia("Batch.One.mp4");
        final TestConsole console = runWithInput(
                "media_id,publisher_id\n" + mediaId + "," + publisherId + "\n",
                "scene", "create-from-media-batch",
                "--input", "-",
                "--dry-run",
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(SUCCESS_STATUS,
                        console.result().exitStatus()),
                () -> Assertions.assertTrue(console.output().startsWith(
                        "row\tstatus\tmedia_id\tpath\ttitle\tscene_id\terror"
                )),
                () -> Assertions.assertTrue(console.output().contains(
                        "WOULD_CREATE"
                ))
        );
    }

    @Test
    @DisplayName("Media parse-preview supports TSV for explicit media")
    void mediaParsePreviewSupportsTsvForExplicitMedia() {
        final UUID publisherId = addPublisher();
        addPerformer();
        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", temporaryDirectory.resolve(
                        "(25.01.02) Publisher - Scene Title - Performer.mp4"
                ).toString()
        ).output().trim());

        final TestConsole console = run(
                "media", "parse-preview",
                "--media", mediaId.toString(),
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertNotNull(publisherId),
                () -> Assertions.assertEquals(SUCCESS_STATUS,
                        console.result().exitStatus()),
                () -> Assertions.assertTrue(console.output().startsWith(
                        "status\tmedia_id\tpath\trelease_date\ttitle"
                )),
                () -> Assertions.assertTrue(console.output().contains(
                        mediaId.toString()
                ))
        );
    }

    @Test
    @DisplayName("Scene auto-index creates READY rows and prints TSV verification")
    void sceneAutoIndexCreatesReadyRowsAndPrintsTsvVerification() {
        addPublisher();
        addPerformer();
        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", temporaryDirectory.resolve(
                        "(25.01.02) Publisher - Scene Title - Performer.mp4"
                ).toString()
        ).output().trim());

        final TestConsole autoIndexConsole = run(
                "scene", "auto-index",
                "--media", mediaId.toString(),
                "--output", "tsv"
        );
        final TestConsole unassignedConsole = run(
                "media", "unassigned",
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        autoIndexConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(autoIndexConsole.output()
                        .startsWith("status\tmedia_id\tpath\tscene_id\ttitle")),
                () -> Assertions.assertTrue(autoIndexConsole.output()
                        .contains("CREATED_VERIFY")),
                () -> Assertions.assertTrue(autoIndexConsole.output()
                        .contains(mediaId.toString())),
                () -> Assertions.assertFalse(unassignedConsole.output()
                        .contains(mediaId.toString()))
        );
    }

    @Test
    @DisplayName("Scene index-review accepts READY interpretation interactively")
    void sceneIndexReviewAcceptsReadyInterpretationInteractively() {
        addPublisher();
        addPerformer();
        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", temporaryDirectory.resolve(
                        "(25.01.02) Publisher - Review Title - Performer.mp4"
                ).toString()
        ).output().trim());

        final TestConsole reviewConsole = runWithInput(
                "y\n",
                "scene", "index-review",
                "--media", mediaId.toString()
        );
        final TestConsole unassignedConsole = run(
                "media", "unassigned",
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        reviewConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(reviewConsole.output()
                        .contains("Created scene")),
                () -> Assertions.assertFalse(unassignedConsole.output()
                        .contains(mediaId.toString()))
        );
    }

    @Test
    @DisplayName("Scene index-review can explicitly create a missing performer")
    void sceneIndexReviewCanExplicitlyCreateMissingPerformer() {
        addPublisher();
        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", temporaryDirectory.resolve(
                        "(25.01.02) Publisher - Review Create - New Performer.mp4"
                ).toString()
        ).output().trim());

        final TestConsole reviewConsole = runWithInput(
                "y\nactor\ny\n",
                "scene", "index-review",
                "--media", mediaId.toString()
        );
        final TestConsole performerConsole = run(
                "performer", "list"
        );
        final TestConsole unassignedConsole = run(
                "media", "unassigned",
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        reviewConsole.result().exitStatus()
                ),
                () -> Assertions.assertTrue(reviewConsole.output()
                        .contains("Created performer")),
                () -> Assertions.assertTrue(performerConsole.output()
                        .contains("New Performer")),
                () -> Assertions.assertFalse(unassignedConsole.output()
                        .contains(mediaId.toString()))
        );
    }

    @Test
    @DisplayName("Interactive menu can preview parsed filenames")
    void interactiveMenuCanPreviewParsedFilenames() {
        addPublisher();
        addPerformer();
        final UUID mediaId = UUID.fromString(run(
                "media", "add",
                "--path", temporaryDirectory.resolve(
                        "(25.01.02) Publisher - Menu Preview - Performer.mp4"
                ).toString()
        ).output().trim());

        final TestConsole console = runWithInput("23\n15\n");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SUCCESS_STATUS,
                        console.result().exitStatus()
                ),
                () -> Assertions.assertTrue(console.output().contains(
                        "Preview parsed filenames"
                )),
                () -> Assertions.assertTrue(console.output().contains(
                        mediaId.toString()
                )),
                () -> Assertions.assertTrue(console.output().contains("READY"))
        );
    }

    @Test
    @DisplayName("Backup create verify and restore commands work")
    void backupCreateVerifyAndRestoreCommandsWork() {
        addPublisher();
        final Path backupPath = temporaryDirectory.resolve("backup.db");
        final Path restorePath = temporaryDirectory.resolve("restored.db");

        final TestConsole createConsole = run(
                "backup", "create",
                "--destination", backupPath.toString(),
                "--output", "tsv"
        );
        final TestConsole verifyConsole = run(
                "backup", "verify",
                "--input", backupPath.toString(),
                "--level", "full",
                "--output", "tsv"
        );
        final TestConsole restoreConsole = run(
                "backup", "restore",
                "--input", backupPath.toString(),
                "--destination", restorePath.toString(),
                "--output", "tsv"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(SUCCESS_STATUS,
                        createConsole.result().exitStatus()),
                () -> Assertions.assertEquals(SUCCESS_STATUS,
                        verifyConsole.result().exitStatus()),
                () -> Assertions.assertEquals(SUCCESS_STATUS,
                        restoreConsole.result().exitStatus()),
                () -> Assertions.assertTrue(createConsole.output().startsWith(
                        "status\tsource\tdestination\tfile_size"
                )),
                () -> Assertions.assertTrue(verifyConsole.output().startsWith(
                        "status\tpath\tfile_size\tschema_version"
                )),
                () -> Assertions.assertTrue(restoreConsole.output().startsWith(
                        "status\tinput\tdestination\tfile_size"
                )),
                () -> Assertions.assertTrue(Files.isRegularFile(backupPath)),
                () -> Assertions.assertTrue(Files.isRegularFile(restorePath))
        );
    }

    @Test
    @DisplayName("Backup restore refuses active database destination")
    void backupRestoreRefusesActiveDatabaseDestination() {
        final Path backupPath = temporaryDirectory.resolve("backup.db");
        run(
                "backup", "create",
                "--destination", backupPath.toString()
        );

        final TestConsole restoreConsole = run(
                "backup", "restore",
                "--input", backupPath.toString(),
                "--destination",
                temporaryDirectory.resolve(DATABASE_FILE_NAME).toString(),
                "--overwrite"
        );

        Assertions.assertAll(
                () -> Assertions.assertNotEquals(SUCCESS_STATUS,
                        restoreConsole.result().exitStatus()),
                () -> Assertions.assertTrue(restoreConsole.error()
                        .contains("active database"))
        );
    }

    @Test
    @DisplayName("Interactive menu can create backup")
    void interactiveMenuCanCreateBackup() {
        final Path backupPath = temporaryDirectory.resolve(
                "interactive-backup.db"
        );

        final TestConsole console = runWithInput(
                "26\n" + backupPath + "\nn\nquick\n15\n"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(SUCCESS_STATUS,
                        console.result().exitStatus()),
                () -> Assertions.assertTrue(Files.isRegularFile(backupPath)),
                () -> Assertions.assertTrue(console.output()
                        .contains("Create database backup"))
        );
    }

    private UUID addPublisher() {
        return UUID.fromString(run(
                "publisher", "add",
                "--name", "Publisher"
        ).output().trim());
    }

    private UUID addPerformer() {
        return UUID.fromString(run(
                "performer", "add",
                "--name", "Performer",
                "--category", "actor"
        ).output().trim());
    }

    private UUID addMedia(String fileName) {
        return UUID.fromString(run(
                "media", "add",
                "--path", temporaryDirectory.resolve(fileName).toString()
        ).output().trim());
    }

    private TestConsole run(String... args) {
        return runWithInput("", args);
    }

    private TestConsole runWithInput(String input, String... args) {
        final TestConsole console = new TestConsole(input);
        console.result = cli.run(args, console.io);
        return console;
    }

    private static final class TestConsole {
        private final StringWriter output = new StringWriter();
        private final StringWriter error = new StringWriter();
        private final ConsoleIO io;
        private CommandResult result;

        private TestConsole(String input) {
            io = new ConsoleIO(
                    new StringReader(input),
                    new PrintWriter(output),
                    new PrintWriter(error)
            );
        }

        private String output() {
            return output.toString();
        }

        private String error() {
            return error.toString();
        }

        private CommandResult result() {
            return result;
        }
    }

    private static final class FakeProbe implements MediaMetadataProbe {
        @Override
        public MediaMetadata probe(Path mediaPath)
                throws IOException, MediaProbeException {

            return new MediaMetadata(
                    Duration.ofMillis(PROBE_DURATION_MILLIS),
                    PROBE_WIDTH,
                    PROBE_HEIGHT
            );
        }
    }
}

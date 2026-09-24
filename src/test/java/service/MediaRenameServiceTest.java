package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class MediaRenameServiceTest {
    private static final String DATABASE_FILE_NAME =
            "media-rename-service-test.db";
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-eeee-1111-eeee-111111111111");
    private static final UUID SCENE_ID =
            UUID.fromString("22222222-eeee-2222-eeee-222222222222");
    private static final UUID SECOND_SCENE_ID =
            UUID.fromString("33333333-eeee-3333-eeee-333333333333");
    private static final UUID MEDIA_ID =
            UUID.fromString("44444444-eeee-4444-eeee-444444444444");
    private static final UUID CONFLICT_MEDIA_ID =
            UUID.fromString("55555555-eeee-5555-eeee-555555555555");
    private static final UUID MOVIE_ONE_ID =
            UUID.fromString("66666666-eeee-6666-eeee-666666666666");
    private static final UUID MOVIE_TWO_ID =
            UUID.fromString("77777777-eeee-7777-eeee-777777777777");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-eeee-9999-eeee-999999999999");
    private static final long FILE_SIZE = 10L;
    private static final int WIDTH = 1_920;
    private static final int HEIGHT = 1_080;
    private static final long LAST_MODIFIED_MILLIS = 123L;

    @TempDir
    Path temporaryDirectory;

    private MediaFileRepository mediaFileRepository;
    private SceneRepository sceneRepository;
    private MovieRepository movieRepository;
    private MediaRenameService service;
    private Publisher publisher;
    private Performer performer;
    private Path mediaPath;
    private Path proposedPath;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();

        mediaFileRepository = new MediaFileRepository(databaseManager);
        sceneRepository = new SceneRepository(databaseManager);
        movieRepository = new MovieRepository(databaseManager);
        service = new MediaRenameService(
                mediaFileRepository,
                sceneRepository,
                new MediaAssignmentRepository(databaseManager),
                new OriginalMovieSelector(movieRepository)
        );
        publisher = new Publisher(PUBLISHER_ID, "Publisher", List.of());
        performer = new Performer(
                UUID.fromString("88888888-eeee-8888-eeee-888888888888"),
                "Alice",
                List.of("Alias"),
                PerformerCategory.UNKNOWN
        );
        mediaPath = temporaryDirectory.resolve("old name.mp4");
        proposedPath = temporaryDirectory.resolve(
                "(26.01.15) Publisher - Scene Title - Alice.mp4"
        );

        Files.writeString(mediaPath, "video");
        new PublisherRepository(databaseManager).insert(publisher);
        new PerformerRepository(databaseManager).insert(performer);
        mediaFileRepository.insert(mediaFile(MEDIA_ID, mediaPath));
        sceneRepository.insert(scene(SCENE_ID, "Scene Title", MEDIA_ID));
    }

    @Test
    @DisplayName("Constructor rejects null dependencies")
    void constructorRejectsNullDependencies() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new MediaRenameService(
                                null,
                                sceneRepository,
                                new MediaAssignmentRepository(
                                        new DatabaseManager(
                                                temporaryDirectory.resolve(
                                                        "unused.db"
                                                )
                                        )
                                ),
                                new OriginalMovieSelector(movieRepository)
                        )
                ),
                () -> Assertions.assertThrows(
                        NullPointerException.class,
                        () -> new MediaRenameService(
                                mediaFileRepository,
                                sceneRepository,
                                null,
                                new OriginalMovieSelector(movieRepository)
                        )
                )
        );
    }

    @Test
    @DisplayName("Ready rename preview proposes canonical path")
    void readyRenamePreviewProposesCanonicalPath() throws Exception {
        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaRenameStatus.READY,
                        preview.status()
                ),
                () -> Assertions.assertEquals(MEDIA_ID, preview.mediaId()),
                () -> Assertions.assertEquals(SCENE_ID, preview.sceneId()),
                () -> Assertions.assertEquals(mediaPath, preview.originalPath()),
                () -> Assertions.assertEquals(proposedPath, preview.proposedPath())
        );
    }

    @Test
    @DisplayName("Unchanged path is reported")
    void unchangedPathIsReported() throws Exception {
        final UUID mediaId =
                UUID.fromString("aaaaaaaa-eeee-aaaa-eeee-aaaaaaaaaaaa");
        Files.writeString(proposedPath, "video");
        mediaFileRepository.insert(mediaFile(mediaId, proposedPath));
        sceneRepository.insert(scene(
                UUID.fromString("bbbbbbbb-eeee-bbbb-eeee-bbbbbbbbbbbb"),
                "Scene Title",
                mediaId
        ));

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(mediaId, null, null)
        );

        Assertions.assertEquals(MediaRenameStatus.UNCHANGED, preview.status());
    }

    @Test
    @DisplayName("Missing source file is reported")
    void missingSourceFileIsReported() throws Exception {
        Files.delete(mediaPath);

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertEquals(
                MediaRenameStatus.SOURCE_MISSING,
                preview.status()
        );
    }

    @Test
    @DisplayName("Existing filesystem destination is reported")
    void existingFilesystemDestinationIsReported() throws Exception {
        Files.writeString(proposedPath, "collision");

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertEquals(
                MediaRenameStatus.DESTINATION_EXISTS,
                preview.status()
        );
    }

    @Test
    @DisplayName("Existing database destination path is reported")
    void existingDatabaseDestinationPathIsReported() throws Exception {
        mediaFileRepository.insert(mediaFile(CONFLICT_MEDIA_ID, proposedPath));

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertEquals(
                MediaRenameStatus.DATABASE_PATH_CONFLICT,
                preview.status()
        );
    }

    @Test
    @DisplayName("Ambiguous scene assignment requires explicit scene")
    void ambiguousSceneAssignmentRequiresExplicitScene() throws Exception {
        sceneRepository.insert(scene(SECOND_SCENE_ID, "Second Scene", MEDIA_ID));

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertEquals(
                MediaRenameStatus.INVALID_ASSIGNMENT,
                preview.status()
        );
    }

    @Test
    @DisplayName("Explicit valid scene selection is used")
    void explicitValidSceneSelectionIsUsed() throws Exception {
        sceneRepository.insert(scene(SECOND_SCENE_ID, "Second Scene", MEDIA_ID));

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, SCENE_ID, null)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaRenameStatus.READY,
                        preview.status()
                ),
                () -> Assertions.assertEquals(SCENE_ID, preview.sceneId())
        );
    }

    @Test
    @DisplayName("Invalid scene selection is reported")
    void invalidSceneSelectionIsReported() throws Exception {
        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, UNKNOWN_ID, null)
        );

        Assertions.assertEquals(
                MediaRenameStatus.INVALID_ASSIGNMENT,
                preview.status()
        );
    }

    @Test
    @DisplayName("Original movie ambiguity requires review")
    void originalMovieAmbiguityRequiresReview() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Movie One", SCENE_ID);
        insertMovie(MOVIE_TWO_ID, "Movie Two", SCENE_ID);

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertEquals(
                MediaRenameStatus.REVIEW_REQUIRED,
                preview.status()
        );
    }

    @Test
    @DisplayName("Movie override resolves ambiguity")
    void movieOverrideResolvesAmbiguity() throws Exception {
        insertMovie(MOVIE_ONE_ID, "Movie One", SCENE_ID);
        insertMovie(MOVIE_TWO_ID, "Movie Two", SCENE_ID);

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(MEDIA_ID, null, MOVIE_TWO_ID)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaRenameStatus.READY,
                        preview.status()
                ),
                () -> Assertions.assertEquals(
                        MOVIE_TWO_ID,
                        preview.selectedMovie().orElseThrow().getId()
                )
        );
    }

    @Test
    @DisplayName("Preview makes no filesystem or database changes")
    void previewMakesNoFilesystemOrDatabaseChanges() throws Exception {
        service.preview(new MediaRenameRequest(MEDIA_ID, null, null));

        Assertions.assertAll(
                () -> Assertions.assertTrue(Files.exists(mediaPath)),
                () -> Assertions.assertFalse(Files.exists(proposedPath)),
                () -> Assertions.assertEquals(
                        mediaPath,
                        mediaFileRepository.findById(MEDIA_ID)
                                .orElseThrow()
                                .getPath()
                )
        );
    }

    @Test
    @DisplayName("Special-character paths are previewed")
    void specialCharacterPathsArePreviewed() throws Exception {
        final Path specialPath =
                temporaryDirectory.resolve("old, name's copy.mp4");
        Files.writeString(specialPath, "video");
        final UUID mediaId =
                UUID.fromString("cccccccc-eeee-cccc-eeee-cccccccccccc");
        mediaFileRepository.insert(mediaFile(mediaId, specialPath));
        sceneRepository.insert(scene(
                UUID.fromString("dddddddd-eeee-dddd-eeee-dddddddddddd"),
                "Special Scene",
                mediaId
        ));

        final MediaRenamePreview preview = service.preview(
                new MediaRenameRequest(mediaId, null, null)
        );

        Assertions.assertEquals(MediaRenameStatus.READY, preview.status());
    }

    @Test
    @DisplayName("Successful rename moves the file and updates the database")
    void successfulRenameMovesFileAndUpdatesDatabase() throws Exception {
        final MediaRenameResult result = service.rename(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        final MediaFile stored =
                mediaFileRepository.findById(MEDIA_ID).orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaRenameStatus.RENAMED,
                        result.status()
                ),
                () -> Assertions.assertFalse(Files.exists(mediaPath)),
                () -> Assertions.assertTrue(Files.exists(proposedPath)),
                () -> Assertions.assertEquals(proposedPath, stored.getPath()),
                () -> Assertions.assertEquals(FILE_SIZE, stored.getFileSize()),
                () -> Assertions.assertEquals(WIDTH, stored.getWidth()),
                () -> Assertions.assertEquals(HEIGHT, stored.getHeight()),
                () -> Assertions.assertEquals(
                        LAST_MODIFIED_MILLIS,
                        stored.getLastModifiedMillis()
                ),
                () -> Assertions.assertEquals(
                        List.of(MEDIA_ID),
                        sceneRepository.findById(SCENE_ID)
                                .orElseThrow()
                                .getFiles()
                                .stream()
                                .map(MediaFile::getId)
                                .toList()
                )
        );
    }

    @Test
    @DisplayName("Unchanged filename performs no writes")
    void unchangedFilenamePerformsNoWrites() throws Exception {
        final UUID mediaId =
                UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        Files.writeString(proposedPath, "video");
        mediaFileRepository.insert(mediaFile(mediaId, proposedPath));
        sceneRepository.insert(scene(
                UUID.fromString("ffffffff-eeee-ffff-eeee-ffffffffffff"),
                "Scene Title",
                mediaId
        ));

        final MediaRenameResult result = service.rename(
                new MediaRenameRequest(mediaId, null, null)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaRenameStatus.UNCHANGED,
                        result.status()
                ),
                () -> Assertions.assertTrue(Files.exists(proposedPath)),
                () -> Assertions.assertEquals(
                        proposedPath,
                        mediaFileRepository.findById(mediaId)
                                .orElseThrow()
                                .getPath()
                )
        );
    }

    @Test
    @DisplayName("Source missing performs no writes")
    void sourceMissingPerformsNoWrites() throws Exception {
        Files.delete(mediaPath);

        final MediaRenameResult result = service.rename(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaRenameStatus.SOURCE_MISSING,
                        result.status()
                ),
                () -> Assertions.assertFalse(Files.exists(proposedPath)),
                () -> Assertions.assertEquals(
                        mediaPath,
                        mediaFileRepository.findById(MEDIA_ID)
                                .orElseThrow()
                                .getPath()
                )
        );
    }

    @Test
    @DisplayName("Destination collision performs no writes")
    void destinationCollisionPerformsNoWrites() throws Exception {
        Files.writeString(proposedPath, "collision");

        final MediaRenameResult result = service.rename(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaRenameStatus.DESTINATION_EXISTS,
                        result.status()
                ),
                () -> Assertions.assertTrue(Files.exists(mediaPath)),
                () -> Assertions.assertEquals(
                        mediaPath,
                        mediaFileRepository.findById(MEDIA_ID)
                                .orElseThrow()
                                .getPath()
                )
        );
    }

    @Test
    @DisplayName("Filesystem move failure leaves database unchanged")
    void filesystemMoveFailureLeavesDatabaseUnchanged() throws Exception {
        final MediaRenameService failingService = new MediaRenameService(
                mediaFileRepository,
                sceneRepository,
                new MediaAssignmentRepository(
                        new DatabaseManager(
                                temporaryDirectory.resolve(DATABASE_FILE_NAME)
                        )
                ),
                new OriginalMovieSelector(movieRepository),
                new FailingMover(),
                new RepositoryMediaPathUpdater(mediaFileRepository)
        );

        final MediaRenameResult result = failingService.rename(
                new MediaRenameRequest(MEDIA_ID, null, null)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MediaRenameStatus.FILESYSTEM_FAILURE,
                        result.status()
                ),
                () -> Assertions.assertTrue(Files.exists(mediaPath)),
                () -> Assertions.assertFalse(Files.exists(proposedPath)),
                () -> Assertions.assertEquals(
                        mediaPath,
                        mediaFileRepository.findById(MEDIA_ID)
                                .orElseThrow()
                                .getPath()
                )
        );
    }

    @Test
    @DisplayName("Database update failure moves the file back")
    void databaseUpdateFailureMovesFileBack() throws Exception {
        final MediaRenameService failingService = new MediaRenameService(
                mediaFileRepository,
                sceneRepository,
                new MediaAssignmentRepository(
                        new DatabaseManager(
                                temporaryDirectory.resolve(DATABASE_FILE_NAME)
                        )
                ),
                new OriginalMovieSelector(movieRepository),
                new DefaultMediaFileMover(),
                (mediaFile, newPath) -> {
                    throw new SQLException("Forced update failure");
                }
        );

        final SQLException exception = Assertions.assertThrows(
                SQLException.class,
                () -> failingService.rename(
                        new MediaRenameRequest(MEDIA_ID, null, null)
                )
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        exception.getMessage().contains("Forced update failure")
                ),
                () -> Assertions.assertTrue(Files.exists(mediaPath)),
                () -> Assertions.assertFalse(Files.exists(proposedPath)),
                () -> Assertions.assertEquals(
                        mediaPath,
                        mediaFileRepository.findById(MEDIA_ID)
                                .orElseThrow()
                                .getPath()
                )
        );
    }

    @Test
    @DisplayName("Scene media preview returns media in deterministic path order")
    void sceneMediaPreviewReturnsMediaInDeterministicPathOrder()
            throws Exception {

        final UUID secondMediaId =
                UUID.fromString("12121212-eeee-1212-eeee-121212121212");
        final Path secondPath = temporaryDirectory.resolve("another.mkv");
        Files.writeString(secondPath, "video");
        mediaFileRepository.insert(mediaFile(secondMediaId, secondPath));
        replaceSceneMedia(SCENE_ID, List.of(MEDIA_ID, secondMediaId));

        final List<MediaRenamePreview> previews =
                service.previewSceneMedia(SCENE_ID, null);

        Assertions.assertEquals(
                List.of(secondMediaId, MEDIA_ID),
                previews.stream()
                        .map(MediaRenamePreview::mediaId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Scene media dry run performs no writes")
    void sceneMediaDryRunPerformsNoWrites() throws Exception {
        final MediaRenameBatchResult result =
                service.renameSceneMedia(SCENE_ID, null, true, false);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, result.results().size()),
                () -> Assertions.assertEquals(
                        MediaRenameStatus.READY,
                        result.results().getFirst().status()
                ),
                () -> Assertions.assertTrue(Files.exists(mediaPath)),
                () -> Assertions.assertFalse(Files.exists(proposedPath)),
                () -> Assertions.assertEquals(
                        mediaPath,
                        mediaFileRepository.findById(MEDIA_ID)
                                .orElseThrow()
                                .getPath()
                )
        );
    }

    @Test
    @DisplayName("Scene media rename renames every ready file")
    void sceneMediaRenameRenamesEveryReadyFile() throws Exception {
        final UUID secondMediaId =
                UUID.fromString("34343434-eeee-3434-eeee-343434343434");
        final Path secondPath = temporaryDirectory.resolve("another.mkv");
        final Path secondProposedPath = temporaryDirectory.resolve(
                "(26.01.15) Publisher - Scene Title - Alice.mkv"
        );
        Files.writeString(secondPath, "video");
        mediaFileRepository.insert(mediaFile(secondMediaId, secondPath));
        replaceSceneMedia(SCENE_ID, List.of(MEDIA_ID, secondMediaId));

        final MediaRenameBatchResult result =
                service.renameSceneMedia(SCENE_ID, null, false, false);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(
                                MediaRenameStatus.RENAMED,
                                MediaRenameStatus.RENAMED
                        ),
                        result.results().stream()
                                .map(MediaRenameResult::status)
                                .toList()
                ),
                () -> Assertions.assertTrue(Files.exists(proposedPath)),
                () -> Assertions.assertTrue(Files.exists(secondProposedPath))
        );
    }

    @Test
    @DisplayName("Scene media duplicate extension collision requires review")
    void sceneMediaDuplicateExtensionCollisionRequiresReview()
            throws Exception {

        final UUID secondMediaId =
                UUID.fromString("56565656-eeee-5656-eeee-565656565656");
        final Path secondPath = temporaryDirectory.resolve("another.mp4");
        Files.writeString(secondPath, "video");
        mediaFileRepository.insert(mediaFile(secondMediaId, secondPath));
        replaceSceneMedia(SCENE_ID, List.of(MEDIA_ID, secondMediaId));

        final List<MediaRenamePreview> previews =
                service.previewSceneMedia(SCENE_ID, null);

        Assertions.assertEquals(
                List.of(
                        MediaRenameStatus.REVIEW_REQUIRED,
                        MediaRenameStatus.REVIEW_REQUIRED
                ),
                previews.stream()
                        .map(MediaRenamePreview::status)
                        .toList()
        );
    }

    @Test
    @DisplayName("Scene media fail-fast stops after first failed file")
    void sceneMediaFailFastStopsAfterFirstFailedFile() throws Exception {
        final UUID secondMediaId =
                UUID.fromString("78787878-eeee-7878-eeee-787878787878");
        final Path secondPath = temporaryDirectory.resolve("zzz.mkv");
        Files.writeString(secondPath, "video");
        mediaFileRepository.insert(mediaFile(secondMediaId, secondPath));
        replaceSceneMedia(SCENE_ID, List.of(MEDIA_ID, secondMediaId));
        Files.delete(mediaPath);

        final MediaRenameBatchResult result =
                service.renameSceneMedia(SCENE_ID, null, false, true);

        Assertions.assertEquals(1, result.results().size());
    }

    private MediaFile mediaFile(UUID id, Path path) {
        return new MediaFile(
                id,
                path,
                FILE_SIZE,
                null,
                Duration.ofMinutes(1),
                WIDTH,
                HEIGHT,
                LAST_MODIFIED_MILLIS
        );
    }

    private Scene scene(UUID id, String title, UUID mediaId) throws Exception {
        return new Scene(
                id,
                title,
                publisher,
                LocalDate.of(2026, 1, 15),
                null,
                null,
                null,
                null,
                List.of(performer),
                List.of(mediaFileRepository.findById(mediaId).orElseThrow())
        );
    }

    private void insertMovie(UUID id, String title, UUID sceneId)
            throws Exception {

        movieRepository.insert(new Movie(
                id,
                title,
                LocalDate.of(2026, 1, 15),
                publisher,
                List.of(sceneRepository.findById(sceneId).orElseThrow()),
                false,
                List.of()
        ));
    }

    private void replaceSceneMedia(UUID sceneId, List<UUID> mediaIds)
            throws Exception {

        final Scene existingScene = sceneRepository.findById(sceneId)
                .orElseThrow();
        final List<MediaFile> files = mediaIds.stream()
                .map(mediaId -> {
                    try {
                        return mediaFileRepository.findById(mediaId)
                                .orElseThrow();
                    } catch (SQLException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toList();
        sceneRepository.update(new Scene(
                existingScene.getId(),
                existingScene.getTitle(),
                existingScene.getPublisher(),
                existingScene.getReleaseDate(),
                existingScene.getCode(),
                existingScene.getSeries(),
                existingScene.getSeason(),
                existingScene.getEpisode(),
                existingScene.getPerformers(),
                files,
                existingScene.getVerificationStatus()
        ));
    }

    private static final class FailingMover implements MediaFileMover {
        @Override
        public void move(Path source, Path destination)
                throws java.io.IOException {

            throw new java.io.IOException("Forced move failure");
        }
    }
}

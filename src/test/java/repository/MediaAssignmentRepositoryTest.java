package repository;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
import model.Movie;
import model.Publisher;
import model.Scene;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

class MediaAssignmentRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "media-assignment-repository-test.db";
    private static final String INSERT_SCENE_MEDIA_SQL = """
            INSERT INTO scene_media_file(scene_id, media_file_id)
            VALUES (?, ?)
            """;
    private static final String INSERT_MOVIE_MEDIA_SQL = """
            INSERT INTO movie_media_file(movie_id, media_file_id)
            VALUES (?, ?)
            """;
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int DEFAULT_LIMIT =
            UnassignedMediaFilter.DEFAULT_LIMIT;

    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-aaaa-1111-aaaa-111111111111");
    private static final UUID SCENE_ID =
            UUID.fromString("22222222-aaaa-2222-aaaa-222222222222");
    private static final UUID MOVIE_ID =
            UUID.fromString("33333333-aaaa-3333-aaaa-333333333333");
    private static final UUID MEDIA_ONE_ID =
            UUID.fromString("44444444-aaaa-4444-aaaa-444444444444");
    private static final UUID MEDIA_TWO_ID =
            UUID.fromString("55555555-aaaa-5555-aaaa-555555555555");
    private static final UUID MEDIA_THREE_ID =
            UUID.fromString("66666666-aaaa-6666-aaaa-666666666666");
    private static final UUID MEDIA_FOUR_ID =
            UUID.fromString("77777777-aaaa-7777-aaaa-777777777777");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-aaaa-9999-aaaa-999999999999");

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private MediaAssignmentRepository repository;
    private MediaFile mediaOne;
    private MediaFile mediaTwo;
    private MediaFile mediaThree;
    private MediaFile mediaFour;

    @BeforeEach
    void initializeDatabase() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        repository = new MediaAssignmentRepository(databaseManager);

        final Publisher publisher =
                new Publisher(PUBLISHER_ID, "Publisher", List.of());
        final Scene scene = new Scene(
                SCENE_ID,
                "Scene Title",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        final Movie movie = new Movie(
                MOVIE_ID,
                "Movie Title",
                null,
                publisher,
                List.of(scene),
                false,
                List.of()
        );
        mediaOne = mediaFile(MEDIA_ONE_ID, "Alpha One.mp4", 1920, 1080);
        mediaTwo = mediaFile(MEDIA_TWO_ID, "beta_two.mkv", 1280, 720);
        mediaThree = mediaFile(MEDIA_THREE_ID, "Gamma.Three.mov", 1920, 720);
        mediaFour = mediaFile(MEDIA_FOUR_ID, "nested/Delta Four.mp4",
                3840, 2160);

        new PublisherRepository(databaseManager).insert(publisher);
        new SceneRepository(databaseManager).insert(scene);
        new MovieRepository(databaseManager).insert(movie);
        final MediaFileRepository mediaFileRepository =
                new MediaFileRepository(databaseManager);
        mediaFileRepository.insert(mediaOne);
        mediaFileRepository.insert(mediaTwo);
        mediaFileRepository.insert(mediaThree);
        mediaFileRepository.insert(mediaFour);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new MediaAssignmentRepository(null)
        );
    }

    @Test
    @DisplayName("Unassigned state reflects scene and movie usage")
    void unassignedStateReflectsSceneAndMovieUsage() throws Exception {
        attach(INSERT_SCENE_MEDIA_SQL, SCENE_ID, MEDIA_TWO_ID);
        attach(INSERT_MOVIE_MEDIA_SQL, MOVIE_ID, MEDIA_THREE_ID);

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        repository.isUnassigned(MEDIA_ONE_ID)
                ),
                () -> Assertions.assertFalse(
                        repository.isUnassigned(MEDIA_TWO_ID)
                ),
                () -> Assertions.assertFalse(
                        repository.isUnassigned(MEDIA_THREE_ID)
                )
        );
    }

    @Test
    @DisplayName("Assignment lookup reports scene and movie references")
    void assignmentLookupReportsSceneAndMovieReferences() throws Exception {
        attach(INSERT_SCENE_MEDIA_SQL, SCENE_ID, MEDIA_TWO_ID);
        attach(INSERT_MOVIE_MEDIA_SQL, MOVIE_ID, MEDIA_TWO_ID);

        final MediaAssignment assignment =
                repository.findAssignment(MEDIA_TWO_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(MEDIA_TWO_ID,
                        assignment.mediaFileId()),
                () -> Assertions.assertEquals(1,
                        assignment.scenes().size()),
                () -> Assertions.assertEquals(SCENE_ID,
                        assignment.scenes().getFirst().id()),
                () -> Assertions.assertEquals("Scene Title",
                        assignment.scenes().getFirst().title()),
                () -> Assertions.assertEquals(1,
                        assignment.movies().size()),
                () -> Assertions.assertEquals(MOVIE_ID,
                        assignment.movies().getFirst().id()),
                () -> Assertions.assertEquals("Movie Title",
                        assignment.movies().getFirst().title())
        );
    }

    @Test
    @DisplayName("Unknown media assignment is empty and not unassigned")
    void unknownMediaAssignmentIsEmptyAndNotUnassigned() throws Exception {
        final MediaAssignment assignment = repository.findAssignment(
                UNKNOWN_ID
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(UNKNOWN_ID,
                        assignment.mediaFileId()),
                () -> Assertions.assertTrue(assignment.scenes().isEmpty()),
                () -> Assertions.assertTrue(assignment.movies().isEmpty()),
                () -> Assertions.assertFalse(repository.isUnassigned(
                        UNKNOWN_ID
                ))
        );
    }

    @Test
    @DisplayName("Find unassigned supports filters and deterministic paging")
    void findUnassignedSupportsFiltersAndDeterministicPaging()
            throws Exception {

        attach(INSERT_SCENE_MEDIA_SQL, SCENE_ID, MEDIA_TWO_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(MEDIA_ONE_ID),
                        ids(repository.findUnassigned(new UnassignedMediaFilter(
                                "alpha",
                                null,
                                null,
                                null,
                                null,
                                null,
                                DEFAULT_LIMIT,
                                0
                        )))
                ),
                () -> Assertions.assertEquals(
                        List.of(MEDIA_THREE_ID),
                        ids(repository.findUnassigned(new UnassignedMediaFilter(
                                "three",
                                null,
                                null,
                                null,
                                null,
                                null,
                                DEFAULT_LIMIT,
                                0
                        )))
                ),
                () -> Assertions.assertEquals(
                        List.of(MEDIA_FOUR_ID),
                        ids(repository.findUnassigned(new UnassignedMediaFilter(
                                null,
                                temporaryDirectory.resolve("nested"),
                                null,
                                null,
                                null,
                                null,
                                DEFAULT_LIMIT,
                                0
                        )))
                ),
                () -> Assertions.assertEquals(
                        List.of(MEDIA_ONE_ID, MEDIA_THREE_ID),
                        ids(repository.findUnassigned(new UnassignedMediaFilter(
                                null,
                                null,
                                1920,
                                null,
                                null,
                                null,
                                DEFAULT_LIMIT,
                                0
                        )))
                ),
                () -> Assertions.assertEquals(
                        List.of(MEDIA_FOUR_ID),
                        ids(repository.findUnassigned(new UnassignedMediaFilter(
                                null,
                                null,
                                null,
                                2160,
                                3000,
                                2000,
                                DEFAULT_LIMIT,
                                0
                        )))
                ),
                () -> Assertions.assertEquals(
                        List.of(MEDIA_THREE_ID),
                        ids(repository.findUnassigned(new UnassignedMediaFilter(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                1,
                                1
                        )))
                )
        );
    }

    @Test
    @DisplayName("Invalid paging values are rejected")
    void invalidPagingValuesAreRejected() {
        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> new UnassignedMediaFilter(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0,
                                0
                        )
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> new UnassignedMediaFilter(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                DEFAULT_LIMIT,
                                -1
                        )
                )
        );
    }

    private MediaFile mediaFile(
            UUID id,
            String name,
            int width,
            int height) {

        return new MediaFile(
                id,
                temporaryDirectory.resolve(name).toAbsolutePath().normalize(),
                1_000L,
                null,
                Duration.ofMillis(2_000L),
                width,
                height,
                1_700_000_000_000L
        );
    }

    private void attach(String sql, UUID targetId, UUID mediaId)
            throws Exception {

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, targetId.toString());
            statement.setString(SECOND_PARAMETER_INDEX, mediaId.toString());
            statement.executeUpdate();
        }
    }

    private List<UUID> ids(List<MediaFile> mediaFiles) {
        return mediaFiles.stream()
                .map(MediaFile::getId)
                .toList();
    }
}

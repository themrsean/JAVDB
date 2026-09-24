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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class MovieRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "movie-repository-test.db";
    private static final String SELECT_MOVIE_SQL = """
            SELECT title, release_date, publisher_id, compilation
            FROM movie
            WHERE id = ?
            """;
    private static final String SELECT_SCENE_ORDER_SQL = """
            SELECT scene_id
            FROM movie_scene
            WHERE movie_id = ?
            ORDER BY scene_order
            """;
    private static final String COUNT_MOVIE_SQL = """
            SELECT COUNT(*)
            FROM movie
            WHERE id = ?
            """;
    private static final String COUNT_MOVIE_SCENES_SQL = """
            SELECT COUNT(*)
            FROM movie_scene
            WHERE movie_id = ?
            """;
    private static final String COUNT_MOVIE_FILES_SQL = """
            SELECT COUNT(*)
            FROM movie_media_file
            WHERE movie_id = ?
            """;
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int FIRST_COLUMN_INDEX = 1;
    private static final int SECOND_COLUMN_INDEX = 2;
    private static final int THIRD_COLUMN_INDEX = 3;
    private static final int FOURTH_COLUMN_INDEX = 4;

    private static final UUID MOVIE_ID =
            UUID.fromString("11111111-eeee-1111-eeee-111111111111");
    private static final UUID SECOND_MOVIE_ID =
            UUID.fromString("22222222-eeee-2222-eeee-222222222222");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-eeee-9999-eeee-999999999999");
    private static final UUID PUBLISHER_ID =
            UUID.fromString("aaaaaaaa-eeee-1111-eeee-111111111111");
    private static final UUID SCENE_ID =
            UUID.fromString("bbbbbbbb-eeee-1111-eeee-111111111111");
    private static final UUID SECOND_SCENE_ID =
            UUID.fromString("cccccccc-eeee-2222-eeee-222222222222");
    private static final UUID MEDIA_FILE_ID =
            UUID.fromString("dddddddd-eeee-1111-eeee-111111111111");
    private static final UUID SECOND_MEDIA_FILE_ID =
            UUID.fromString("eeeeeeee-eeee-2222-eeee-222222222222");
    private static final String TITLE = "Zen Movie";
    private static final String SECOND_TITLE = "alpha Movie";
    private static final String UPDATED_TITLE = "Zen Movie Updated";
    private static final LocalDate RELEASE_DATE =
            LocalDate.of(2024, 5, 6);
    private static final LocalDate UPDATED_RELEASE_DATE =
            LocalDate.of(2025, 6, 7);

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private MovieRepository repository;
    private Publisher publisher;
    private Scene scene;
    private Scene secondScene;
    private MediaFile mediaFile;
    private MediaFile secondMediaFile;

    @BeforeEach
    void initializeDatabase() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        repository = new MovieRepository(databaseManager);

        publisher = new Publisher(PUBLISHER_ID, "Publisher", List.of());
        scene = new Scene(
                SCENE_ID,
                "Scene",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        secondScene = new Scene(
                SECOND_SCENE_ID,
                "Second Scene",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        mediaFile = new MediaFile(
                MEDIA_FILE_ID,
                Path.of("/video/movie.mp4"),
                1_000L,
                "movie-hash",
                Duration.ofMillis(1_000L),
                1920,
                1080
        );
        secondMediaFile = new MediaFile(
                SECOND_MEDIA_FILE_ID,
                Path.of("/video/movie-two.mp4"),
                2_000L,
                "movie-hash-two",
                Duration.ofMillis(2_000L),
                1280,
                720
        );

        new PublisherRepository(databaseManager).insert(publisher);
        new SceneRepository(databaseManager).insert(scene);
        new SceneRepository(databaseManager).insert(secondScene);
        new MediaFileRepository(databaseManager).insert(mediaFile);
        new MediaFileRepository(databaseManager).insert(secondMediaFile);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new MovieRepository(null)
        );
    }

    @Test
    @DisplayName("Insert stores all scalar movie fields")
    void insertStoresAllScalarMovieFields() throws Exception {
        repository.insert(createMovie());

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SELECT_MOVIE_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    MOVIE_ID.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(
                        TITLE,
                        resultSet.getString(FIRST_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        RELEASE_DATE.toString(),
                        resultSet.getString(SECOND_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        PUBLISHER_ID.toString(),
                        resultSet.getString(THIRD_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        1,
                        resultSet.getInt(FOURTH_COLUMN_INDEX)
                );
            }
        }
    }

    @Test
    @DisplayName("Insert stores scene membership")
    void insertStoresSceneMembership() throws Exception {
        repository.insert(createMovie());

        Assertions.assertEquals(
                2,
                countByMovieId(COUNT_MOVIE_SCENES_SQL)
        );
    }

    @Test
    @DisplayName("Insert preserves scene order")
    void insertPreservesSceneOrder() throws Exception {
        repository.insert(createMovie());

        Assertions.assertEquals(
                List.of(SCENE_ID.toString(), SECOND_SCENE_ID.toString()),
                findSceneOrder()
        );
    }

    @Test
    @DisplayName("Insert stores media-file relationships")
    void insertStoresMediaFileRelationships() throws Exception {
        repository.insert(createMovie());

        Assertions.assertEquals(
                2,
                countByMovieId(COUNT_MOVIE_FILES_SQL)
        );
    }

    @Test
    @DisplayName("Find by ID reconstructs ordered scenes and media files")
    void findByIdReconstructsOrderedScenesAndMediaFiles()
            throws Exception {

        repository.insert(createMovie());

        final Movie found = repository.findById(MOVIE_ID).orElseThrow();

        Assertions.assertEquals(
                List.of(SCENE_ID, SECOND_SCENE_ID),
                found.getScenes()
                        .stream()
                        .map(Scene::getId)
                        .toList()
        );
        Assertions.assertEquals(
                List.of(SECOND_MEDIA_FILE_ID, MEDIA_FILE_ID),
                found.getFiles()
                        .stream()
                        .map(MediaFile::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Find by ID returns empty for unknown UUID")
    void findByIdReturnsEmptyForUnknownUuid() throws Exception {
        Assertions.assertTrue(repository.findById(UNKNOWN_ID).isEmpty());
    }

    @Test
    @DisplayName("Find all returns deterministic results")
    void findAllReturnsDeterministicResults() throws Exception {
        repository.insert(createMovie());
        repository.insert(new Movie(
                SECOND_MOVIE_ID,
                SECOND_TITLE,
                null,
                publisher,
                List.of(),
                false,
                List.of()
        ));

        Assertions.assertEquals(
                List.of(SECOND_MOVIE_ID, MOVIE_ID),
                repository.findAll()
                        .stream()
                        .map(Movie::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Update changes scalar fields")
    void updateChangesScalarFields() throws Exception {
        repository.insert(createMovie());

        repository.update(new Movie(
                MOVIE_ID,
                UPDATED_TITLE,
                UPDATED_RELEASE_DATE,
                publisher,
                List.of(scene),
                false,
                List.of(mediaFile)
        ));

        final Movie found = repository.findById(MOVIE_ID).orElseThrow();
        Assertions.assertEquals(UPDATED_TITLE, found.getTitle());
        Assertions.assertEquals(UPDATED_RELEASE_DATE, found.getReleaseDate());
        Assertions.assertFalse(found.isCompilation());
    }

    @Test
    @DisplayName("Update can add and remove scenes")
    void updateCanAddAndRemoveScenes() throws Exception {
        repository.insert(new Movie(
                MOVIE_ID,
                TITLE,
                RELEASE_DATE,
                publisher,
                List.of(scene),
                true,
                List.of()
        ));

        repository.update(createMovie());

        Assertions.assertEquals(
                List.of(SCENE_ID, SECOND_SCENE_ID),
                repository.findById(MOVIE_ID).orElseThrow()
                        .getScenes()
                        .stream()
                        .map(Scene::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Update can reorder scenes")
    void updateCanReorderScenes() throws Exception {
        repository.insert(createMovie());

        repository.update(new Movie(
                MOVIE_ID,
                TITLE,
                RELEASE_DATE,
                publisher,
                List.of(secondScene, scene),
                true,
                List.of()
        ));

        Assertions.assertEquals(
                List.of(SECOND_SCENE_ID, SCENE_ID),
                repository.findById(MOVIE_ID).orElseThrow()
                        .getScenes()
                        .stream()
                        .map(Scene::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Update replaces media files")
    void updateReplacesMediaFiles() throws Exception {
        repository.insert(createMovie());

        repository.update(new Movie(
                MOVIE_ID,
                TITLE,
                RELEASE_DATE,
                publisher,
                List.of(scene),
                true,
                List.of(secondMediaFile)
        ));

        Assertions.assertEquals(
                List.of(SECOND_MEDIA_FILE_ID),
                repository.findById(MOVIE_ID).orElseThrow()
                        .getFiles()
                        .stream()
                        .map(MediaFile::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Unknown references fail through foreign keys")
    void unknownReferencesFailThroughForeignKeys() {
        final Scene unknownScene = new Scene(
                UNKNOWN_ID,
                "Unknown",
                publisher,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );

        Assertions.assertThrows(
                SQLException.class,
                () -> repository.insert(new Movie(
                        MOVIE_ID,
                        TITLE,
                        RELEASE_DATE,
                        publisher,
                        List.of(unknownScene),
                        true,
                        List.of()
                ))
        );
    }

    @Test
    @DisplayName("Failed multi-table write rolls back movie and relationships")
    void failedMultiTableWriteRollsBackMovieAndRelationships() {
        final Movie movie = new Movie(
                MOVIE_ID,
                TITLE,
                RELEASE_DATE,
                publisher,
                List.of(scene, scene),
                true,
                List.of(mediaFile)
        );

        Assertions.assertThrows(
                SQLException.class,
                () -> repository.insert(movie)
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, countByMovieId(COUNT_MOVIE_SQL)),
                () -> Assertions.assertEquals(
                        0,
                        countByMovieId(COUNT_MOVIE_SCENES_SQL)
                ),
                () -> Assertions.assertEquals(
                        0,
                        countByMovieId(COUNT_MOVIE_FILES_SQL)
                )
        );
    }

    @Test
    @DisplayName("Delete removes the movie")
    void deleteRemovesMovie() throws Exception {
        repository.insert(createMovie());

        Assertions.assertTrue(repository.delete(MOVIE_ID));
        Assertions.assertEquals(0, countByMovieId(COUNT_MOVIE_SQL));
    }

    @Test
    @DisplayName("Delete removes movie-scene rows")
    void deleteRemovesMovieSceneRows() throws Exception {
        repository.insert(createMovie());

        repository.delete(MOVIE_ID);

        Assertions.assertEquals(
                0,
                countByMovieId(COUNT_MOVIE_SCENES_SQL)
        );
    }

    @Test
    @DisplayName("Delete removes movie-media-file rows")
    void deleteRemovesMovieMediaFileRows() throws Exception {
        repository.insert(createMovie());

        repository.delete(MOVIE_ID);

        Assertions.assertEquals(
                0,
                countByMovieId(COUNT_MOVIE_FILES_SQL)
        );
    }

    @Test
    @DisplayName("Delete reports false for unknown UUID")
    void deleteReportsFalseForUnknownUuid() throws Exception {
        Assertions.assertFalse(repository.delete(UNKNOWN_ID));
    }

    private Movie createMovie() {
        return new Movie(
                MOVIE_ID,
                TITLE,
                RELEASE_DATE,
                publisher,
                List.of(scene, secondScene),
                true,
                List.of(mediaFile, secondMediaFile)
        );
    }

    private List<String> findSceneOrder() throws Exception {
        final java.util.ArrayList<String> sceneIds =
                new java.util.ArrayList<>();

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             SELECT_SCENE_ORDER_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    MOVIE_ID.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    sceneIds.add(resultSet.getString(FIRST_COLUMN_INDEX));
                }
            }
        }

        return sceneIds;
    }

    private int countByMovieId(String sql) throws Exception {
        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    MOVIE_ID.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    count = resultSet.getInt(FIRST_COLUMN_INDEX);
                }
            }
        }

        return count;
    }
}

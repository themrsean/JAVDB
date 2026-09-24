package repository;

import database.DatabaseManager;
import database.SchemaManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

class MediaLibraryRepositoryTest {
    private static final UUID ALPHA = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID BETA = UUID.fromString(
            "10000000-0000-0000-0000-000000000002");
    private static final UUID GAMMA = UUID.fromString(
            "10000000-0000-0000-0000-000000000003");
    private static final UUID DELTA = UUID.fromString(
            "10000000-0000-0000-0000-000000000004");

    @TempDir
    Path temporaryDirectory;
    private DatabaseManager databaseManager;
    private MediaLibraryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve("library.db"));
        new SchemaManager(databaseManager).initialize();
        repository = new MediaLibraryRepository(databaseManager);
        insertCatalogFixtures();
    }

    @Test
    @DisplayName("All-media query pages in deterministic path order")
    void allMediaQueryPagesDeterministically() throws Exception {
        final List<MediaLibraryRecord> first = repository.findPage(filter(
                null, null, MediaLibraryAssignmentState.ALL,
                null, null, null, null,
                MediaLibraryMetadataQuality.ALL, 2, 0));
        final List<MediaLibraryRecord> second = repository.findPage(filter(
                null, null, MediaLibraryAssignmentState.ALL,
                null, null, null, null,
                MediaLibraryMetadataQuality.ALL, 2, 2));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(ALPHA, BETA, DELTA), ids(first)),
                () -> Assertions.assertEquals(
                        List.of(DELTA, GAMMA), ids(second))
        );
    }

    @Test
    @DisplayName("Path directory and dimension filters are SQL backed")
    void supportsPathDirectoryAndDimensionFilters() throws Exception {
        Assertions.assertAll(
                () -> Assertions.assertEquals(List.of(BETA), ids(
                        repository.findPage(filter("BETA", null,
                                MediaLibraryAssignmentState.ALL,
                                null, null, null, null,
                                MediaLibraryMetadataQuality.ALL, 100, 0)))),
                () -> Assertions.assertEquals(List.of(DELTA, GAMMA), ids(
                        repository.findPage(filter(null,
                                temporaryDirectory.resolve("nested"),
                                MediaLibraryAssignmentState.ALL,
                                null, null, null, null,
                                MediaLibraryMetadataQuality.ALL, 100, 0)))),
                () -> Assertions.assertEquals(List.of(ALPHA), ids(
                        repository.findPage(filter(null, null,
                                MediaLibraryAssignmentState.ALL,
                                1920, 1080, 1900, 1000,
                                MediaLibraryMetadataQuality.ALL, 100, 0))))
        );
    }

    @Test
    @DisplayName("Assignment state classification covers all four states")
    void classifiesAssignmentStates() throws Exception {
        final List<MediaLibraryRecord> records = repository.findPage(
                MediaLibraryFilter.firstPage());

        Assertions.assertEquals(
                List.of(
                        MediaLibraryAssignmentState.UNASSIGNED,
                        MediaLibraryAssignmentState.SCENE,
                        MediaLibraryAssignmentState.SCENE_AND_MOVIE,
                        MediaLibraryAssignmentState.MOVIE
                ),
                records.stream().map(MediaLibraryRecord::assignmentState)
                        .toList()
        );
        Assertions.assertEquals(List.of(DELTA), ids(repository.findPage(
                filter(null, null, MediaLibraryAssignmentState.SCENE_AND_MOVIE,
                        null, null, null, null,
                        MediaLibraryMetadataQuality.ALL, 100, 0))));
    }

    @Test
    @DisplayName("Missing metadata filters find dimensions and duration")
    void filtersMissingMetadata() throws Exception {
        Assertions.assertAll(
                () -> Assertions.assertEquals(List.of(BETA), ids(
                        repository.findPage(filter(null, null,
                                MediaLibraryAssignmentState.ALL,
                                null, null, null, null,
                                MediaLibraryMetadataQuality.MISSING_DIMENSIONS,
                                100, 0)))),
                () -> Assertions.assertEquals(List.of(GAMMA), ids(
                        repository.findPage(filter(null, null,
                                MediaLibraryAssignmentState.ALL,
                                null, null, null, null,
                                MediaLibraryMetadataQuality.MISSING_DURATION,
                                100, 0))))
        );
    }

    private void insertCatalogFixtures() throws Exception {
        execute("INSERT INTO publisher(id, name) VALUES ('p', 'Publisher')");
        execute("INSERT INTO scene(id, title, publisher_id)"
                + " VALUES ('s', 'Scene', 'p')");
        execute("INSERT INTO movie(id, title, publisher_id)"
                + " VALUES ('m', 'Movie', 'p')");
        insertMedia(ALPHA, "Alpha.mp4", 1920, 1080, 1_000L);
        insertMedia(BETA, "beta.mp4", 0, 0, 2_000L);
        insertMedia(GAMMA, "nested/Gamma.mp4", 1280, 720, null);
        insertMedia(DELTA, "nested/delta.mp4", 3840, 2160, 4_000L);
        execute("INSERT INTO scene_media_file VALUES ('s', '" + BETA + "')");
        execute("INSERT INTO movie_media_file VALUES ('m', '" + GAMMA + "')");
        execute("INSERT INTO scene_media_file VALUES ('s', '" + DELTA + "')");
        execute("INSERT INTO movie_media_file VALUES ('m', '" + DELTA + "')");
    }

    private void insertMedia(UUID id, String name, int width, int height,
                             Long duration) throws Exception {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO media_file(
                         id, path, file_size, duration_millis, width, height,
                         content_hash, last_modified_millis)
                     VALUES (?, ?, 1024, ?, ?, ?, NULL, 1000)
                     """)) {
            statement.setString(1, id.toString());
            statement.setString(2, temporaryDirectory.resolve(name).toString());
            if (duration == null) {
                statement.setNull(3, java.sql.Types.INTEGER);
            } else {
                statement.setLong(3, duration);
            }
            statement.setInt(4, width);
            statement.setInt(5, height);
            statement.executeUpdate();
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    private MediaLibraryFilter filter(
            String contains, Path directory,
            MediaLibraryAssignmentState assignment,
            Integer width, Integer height, Integer minWidth, Integer minHeight,
            MediaLibraryMetadataQuality quality, int limit, int offset) {
        return new MediaLibraryFilter(contains, directory, assignment,
                width, height, minWidth, minHeight, quality, limit, offset);
    }

    private List<UUID> ids(List<MediaLibraryRecord> records) {
        return records.stream().map(record -> record.mediaFile().getId()).toList();
    }
}

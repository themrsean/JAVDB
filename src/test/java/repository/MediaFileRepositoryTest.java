package repository;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class MediaFileRepositoryTest {
    private static final String DATABASE_FILE_NAME =
            "media-file-repository-test.db";
    private static final String SELECT_MEDIA_FILE_SQL = """
            SELECT path,
                   file_size,
                   duration_millis,
                   width,
                   height,
                   content_hash,
                   last_modified_millis
            FROM media_file
            WHERE id = ?
            """;
    private static final String COUNT_BY_ID_SQL = """
            SELECT COUNT(*)
            FROM media_file
            WHERE id = ?
            """;
    private static final String COUNT_ALL_SQL = """
            SELECT COUNT(*)
            FROM media_file
            """;
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int FIRST_COLUMN_INDEX = 1;
    private static final int SECOND_COLUMN_INDEX = 2;
    private static final int THIRD_COLUMN_INDEX = 3;
    private static final int FOURTH_COLUMN_INDEX = 4;
    private static final int FIFTH_COLUMN_INDEX = 5;
    private static final int SIXTH_COLUMN_INDEX = 6;
    private static final int SEVENTH_COLUMN_INDEX = 7;

    private static final UUID MEDIA_FILE_ID =
            UUID.fromString("11111111-cccc-1111-cccc-111111111111");
    private static final UUID SECOND_MEDIA_FILE_ID =
            UUID.fromString("22222222-cccc-2222-cccc-222222222222");
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-cccc-9999-cccc-999999999999");
    private static final Path FILE_PATH =
            Path.of("/video/zen.mp4");
    private static final Path UPDATED_FILE_PATH =
            Path.of("/video/zen-updated.mp4");
    private static final Path SECOND_FILE_PATH =
            Path.of("/archive/alpha.mp4");
    private static final long FILE_SIZE = 123_456L;
    private static final long UPDATED_FILE_SIZE = 654_321L;
    private static final String CONTENT_HASH = "abc123";
    private static final String SECOND_CONTENT_HASH = "abc123";
    private static final String UPDATED_CONTENT_HASH = "def456";
    private static final Duration DURATION =
            Duration.ofMillis(987_654L);
    private static final Duration UPDATED_DURATION =
            Duration.ofMillis(456_789L);
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int UPDATED_WIDTH = 1280;
    private static final int UPDATED_HEIGHT = 720;
    private static final long DURATION_MILLIS = 987_654L;
    private static final long LAST_MODIFIED_MILLIS =
            1_701_234_567_890L;
    private static final long UPDATED_LAST_MODIFIED_MILLIS =
            1_801_234_567_890L;

    @TempDir
    Path temporaryDirectory;

    private DatabaseManager databaseManager;
    private MediaFileRepository repository;

    @BeforeEach
    void initializeDatabase() throws Exception {
        databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        repository = new MediaFileRepository(databaseManager);
    }

    @Test
    @DisplayName("Constructor rejects a null database manager")
    void constructorRejectsNullDatabaseManager() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new MediaFileRepository(null)
        );
    }

    @Test
    @DisplayName("Insert stores all required fields")
    void insertStoresAllRequiredFields() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        ));

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             SELECT_MEDIA_FILE_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    MEDIA_FILE_ID.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(
                        FILE_PATH.toString(),
                        resultSet.getString(FIRST_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        FILE_SIZE,
                        resultSet.getLong(SECOND_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        DURATION_MILLIS,
                        resultSet.getLong(THIRD_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        WIDTH,
                        resultSet.getInt(FOURTH_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        HEIGHT,
                        resultSet.getInt(FIFTH_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        CONTENT_HASH,
                        resultSet.getString(SIXTH_COLUMN_INDEX)
                );
                Assertions.assertEquals(
                        LAST_MODIFIED_MILLIS,
                        resultSet.getLong(SEVENTH_COLUMN_INDEX)
                );
            }
        }
    }

    @Test
    @DisplayName("Optional metadata can be null")
    void optionalMetadataCanBeNull() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                null,
                null,
                WIDTH,
                HEIGHT
        ));

        final MediaFile mediaFile =
                repository.findById(MEDIA_FILE_ID).orElseThrow();

        Assertions.assertNull(mediaFile.getContentHash());
        Assertions.assertNull(mediaFile.getDuration());
    }

    @Test
    @DisplayName("Find by ID reconstructs all metadata")
    void findByIdReconstructsAllMetadata() throws Exception {
        final MediaFile mediaFile = createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        );
        repository.insert(mediaFile);

        assertMediaFile(repository.findById(MEDIA_FILE_ID).orElseThrow(), mediaFile);
    }

    @Test
    @DisplayName("Find by ID returns empty for unknown UUID")
    void findByIdReturnsEmptyForUnknownUuid() throws Exception {
        Assertions.assertTrue(repository.findById(UNKNOWN_ID).isEmpty());
    }

    @Test
    @DisplayName("Find by path returns the correct record")
    void findByPathReturnsCorrectRecord() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        ));

        Assertions.assertEquals(
                MEDIA_FILE_ID,
                repository.findByPath(FILE_PATH).orElseThrow().getId()
        );
    }

    @Test
    @DisplayName("Hash lookup returns matching records")
    void hashLookupReturnsMatchingRecords() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        ));
        repository.insert(createMediaFile(
                SECOND_MEDIA_FILE_ID,
                SECOND_FILE_PATH,
                SECOND_CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        ));

        Assertions.assertEquals(
                List.of(SECOND_MEDIA_FILE_ID, MEDIA_FILE_ID),
                repository.findByContentHash(CONTENT_HASH)
                        .stream()
                        .map(MediaFile::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Find all uses deterministic ordering")
    void findAllUsesDeterministicOrdering() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        ));
        repository.insert(createMediaFile(
                SECOND_MEDIA_FILE_ID,
                SECOND_FILE_PATH,
                null,
                null,
                WIDTH,
                HEIGHT
        ));

        Assertions.assertEquals(
                List.of(SECOND_MEDIA_FILE_ID, MEDIA_FILE_ID),
                repository.findAll()
                        .stream()
                        .map(MediaFile::getId)
                        .toList()
        );
    }

    @Test
    @DisplayName("Update changes path and metadata")
    void updateChangesPathAndMetadata() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        ));

        final MediaFile updated = new MediaFile(
                MEDIA_FILE_ID,
                UPDATED_FILE_PATH,
                UPDATED_FILE_SIZE,
                UPDATED_CONTENT_HASH,
                UPDATED_DURATION,
                UPDATED_WIDTH,
                UPDATED_HEIGHT,
                UPDATED_LAST_MODIFIED_MILLIS
        );
        repository.update(updated);

        assertMediaFile(repository.findById(MEDIA_FILE_ID).orElseThrow(), updated);
    }

    @Test
    @DisplayName("Width and height are preserved accurately")
    void widthAndHeightArePreservedAccurately() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                UPDATED_WIDTH,
                UPDATED_HEIGHT
        ));

        final MediaFile found =
                repository.findById(MEDIA_FILE_ID).orElseThrow();

        Assertions.assertEquals(UPDATED_WIDTH, found.getWidth());
        Assertions.assertEquals(UPDATED_HEIGHT, found.getHeight());
    }

    @Test
    @DisplayName("Delete removes the media file")
    void deleteRemovesMediaFile() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        ));

        Assertions.assertTrue(repository.delete(MEDIA_FILE_ID));
        Assertions.assertEquals(0, countById(MEDIA_FILE_ID));
    }

    @Test
    @DisplayName("Delete reports false for unknown UUID")
    void deleteReportsFalseForUnknownUuid() throws Exception {
        Assertions.assertFalse(repository.delete(UNKNOWN_ID));
    }

    @Test
    @DisplayName("Path uniqueness constraints are enforced")
    void pathUniquenessConstraintsAreEnforced() throws Exception {
        repository.insert(createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        ));

        Assertions.assertThrows(
                SQLException.class,
                () -> repository.insert(createMediaFile(
                        SECOND_MEDIA_FILE_ID,
                        FILE_PATH,
                        null,
                        null,
                        WIDTH,
                        HEIGHT
                ))
        );
    }

    @Test
    @DisplayName("Failed writes leave no partial data")
    void failedWritesLeaveNoPartialData() {
        final MediaFile first = createMediaFile(
                MEDIA_FILE_ID,
                FILE_PATH,
                CONTENT_HASH,
                DURATION,
                WIDTH,
                HEIGHT
        );
        final MediaFile duplicate = createMediaFile(
                SECOND_MEDIA_FILE_ID,
                FILE_PATH,
                null,
                null,
                WIDTH,
                HEIGHT
        );

        Assertions.assertAll(
                () -> repository.insert(first),
                () -> Assertions.assertThrows(
                        SQLException.class,
                        () -> repository.insert(duplicate)
                ),
                () -> Assertions.assertEquals(1, countAll())
        );
    }

    private MediaFile createMediaFile(
            UUID id,
            Path path,
            String contentHash,
            Duration duration,
            int width,
            int height) {

        return new MediaFile(
                id,
                path,
                FILE_SIZE,
                contentHash,
                duration,
                width,
                height,
                LAST_MODIFIED_MILLIS
        );
    }

    private void assertMediaFile(
            MediaFile actual,
            MediaFile expected) {

        Assertions.assertEquals(expected.getId(), actual.getId());
        Assertions.assertEquals(expected.getPath(), actual.getPath());
        Assertions.assertEquals(
                expected.getFileSize(),
                actual.getFileSize()
        );
        Assertions.assertEquals(
                expected.getContentHash(),
                actual.getContentHash()
        );
        Assertions.assertEquals(
                expected.getDuration(),
                actual.getDuration()
        );
        Assertions.assertEquals(expected.getWidth(), actual.getWidth());
        Assertions.assertEquals(expected.getHeight(), actual.getHeight());
        Assertions.assertEquals(
                expected.getLastModifiedMillis(),
                actual.getLastModifiedMillis()
        );
    }

    private int countById(UUID id) throws Exception {
        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(COUNT_BY_ID_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    count = resultSet.getInt(FIRST_COLUMN_INDEX);
                }
            }
        }

        return count;
    }

    private int countAll() throws Exception {
        int count = 0;

        try (Connection connection =
                     databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(COUNT_ALL_SQL);
             ResultSet resultSet =
                     statement.executeQuery()) {

            if (resultSet.next()) {
                count = resultSet.getInt(FIRST_COLUMN_INDEX);
            }
        }

        return count;
    }
}

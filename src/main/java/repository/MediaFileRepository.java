package repository;

import database.DatabaseManager;
import model.MediaFile;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MediaFileRepository {
    private static final String INSERT_SQL = """
            INSERT INTO media_file(
                id,
                path,
                file_size,
                duration_millis,
                width,
                height,
                content_hash,
                last_modified_millis
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BASE_SQL = """
            SELECT id,
                   path,
                   file_size,
                   duration_millis,
                   width,
                   height,
                   content_hash,
                   last_modified_millis
            FROM media_file
            """;
    private static final String FIND_BY_ID_SQL = SELECT_BASE_SQL + """
            WHERE id = ?
            """;
    private static final String FIND_BY_PATH_SQL = SELECT_BASE_SQL + """
            WHERE path = ?
            """;
    private static final String FIND_BY_HASH_SQL = SELECT_BASE_SQL + """
            WHERE content_hash = ?
            ORDER BY path COLLATE NOCASE, id
            """;
    private static final String FIND_ALL_SQL = SELECT_BASE_SQL + """
            ORDER BY path COLLATE NOCASE, id
            """;
    private static final String UPDATE_SQL = """
            UPDATE media_file
            SET path = ?,
                file_size = ?,
                duration_millis = ?,
                width = ?,
                height = ?,
                content_hash = ?,
                last_modified_millis = ?
            WHERE id = ?
            """;
    private static final String DELETE_SQL = """
            DELETE FROM media_file
            WHERE id = ?
            """;

    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int THIRD_PARAMETER_INDEX = 3;
    private static final int FOURTH_PARAMETER_INDEX = 4;
    private static final int FIFTH_PARAMETER_INDEX = 5;
    private static final int SIXTH_PARAMETER_INDEX = 6;
    private static final int SEVENTH_PARAMETER_INDEX = 7;
    private static final int EIGHTH_PARAMETER_INDEX = 8;
    private static final int ID_COLUMN_INDEX = 1;
    private static final int PATH_COLUMN_INDEX = 2;
    private static final int FILE_SIZE_COLUMN_INDEX = 3;
    private static final int DURATION_COLUMN_INDEX = 4;
    private static final int WIDTH_COLUMN_INDEX = 5;
    private static final int HEIGHT_COLUMN_INDEX = 6;
    private static final int HASH_COLUMN_INDEX = 7;
    private static final int LAST_MODIFIED_COLUMN_INDEX = 8;
    private static final int ONE_ROW_CHANGED = 1;

    private final DatabaseManager databaseManager;

    public MediaFileRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public void insert(MediaFile mediaFile) throws SQLException {
        Objects.requireNonNull(
                mediaFile,
                "Media file must not be null"
        );

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            bindInsert(statement, mediaFile);
            statement.executeUpdate();
        }
    }

    public Optional<MediaFile> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Media file ID must not be null");

        return findOne(FIND_BY_ID_SQL, id.toString());
    }

    public Optional<MediaFile> findByPath(Path path) throws SQLException {
        Objects.requireNonNull(path, "Media file path must not be null");

        return findOne(FIND_BY_PATH_SQL, path.toString());
    }

    public List<MediaFile> findByContentHash(String contentHash)
            throws SQLException {

        Objects.requireNonNull(
                contentHash,
                "Content hash must not be null"
        );

        return findMany(FIND_BY_HASH_SQL, contentHash);
    }

    public List<MediaFile> findAll() throws SQLException {
        final List<MediaFile> mediaFiles = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                mediaFiles.add(readMediaFile(resultSet));
            }
        }

        return mediaFiles;
    }

    public void update(MediaFile mediaFile) throws SQLException {
        Objects.requireNonNull(
                mediaFile,
                "Media file must not be null"
        );

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    mediaFile.getPath().toString()
            );
            statement.setLong(
                    SECOND_PARAMETER_INDEX,
                    mediaFile.getFileSize()
            );
            bindDuration(
                    statement,
                    THIRD_PARAMETER_INDEX,
                    mediaFile.getDuration()
            );
            statement.setInt(FOURTH_PARAMETER_INDEX, mediaFile.getWidth());
            statement.setInt(FIFTH_PARAMETER_INDEX, mediaFile.getHeight());
            statement.setString(
                    SIXTH_PARAMETER_INDEX,
                    mediaFile.getContentHash()
            );
            statement.setLong(
                    SEVENTH_PARAMETER_INDEX,
                    mediaFile.getLastModifiedMillis()
            );
            statement.setString(
                    EIGHTH_PARAMETER_INDEX,
                    mediaFile.getId().toString()
            );
            statement.executeUpdate();
        }
    }

    public boolean delete(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Media file ID must not be null");

        boolean deleted;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(DELETE_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());
            deleted = statement.executeUpdate() == ONE_ROW_CHANGED;
        }

        return deleted;
    }

    private Connection openConnection() throws SQLException {
        try {
            return databaseManager.openConnection();
        } catch (IOException exception) {
            throw new SQLException(
                    "Could not open database connection.",
                    exception
            );
        }
    }

    private Optional<MediaFile> findOne(String sql, String value)
            throws SQLException {

        Optional<MediaFile> mediaFile = Optional.empty();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    mediaFile = Optional.of(readMediaFile(resultSet));
                }
            }
        }

        return mediaFile;
    }

    private List<MediaFile> findMany(String sql, String value)
            throws SQLException {

        final List<MediaFile> mediaFiles = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    mediaFiles.add(readMediaFile(resultSet));
                }
            }
        }

        return mediaFiles;
    }

    private void bindInsert(
            PreparedStatement statement,
            MediaFile mediaFile) throws SQLException {

        statement.setString(
                FIRST_PARAMETER_INDEX,
                mediaFile.getId().toString()
        );
        statement.setString(
                SECOND_PARAMETER_INDEX,
                mediaFile.getPath().toString()
        );
        statement.setLong(
                THIRD_PARAMETER_INDEX,
                mediaFile.getFileSize()
        );
        bindDuration(
                statement,
                FOURTH_PARAMETER_INDEX,
                mediaFile.getDuration()
        );
        statement.setInt(FIFTH_PARAMETER_INDEX, mediaFile.getWidth());
        statement.setInt(SIXTH_PARAMETER_INDEX, mediaFile.getHeight());
        statement.setString(
                SEVENTH_PARAMETER_INDEX,
                mediaFile.getContentHash()
        );
        statement.setLong(
                EIGHTH_PARAMETER_INDEX,
                mediaFile.getLastModifiedMillis()
        );
    }

    private void bindDuration(
            PreparedStatement statement,
            int parameterIndex,
            Duration duration) throws SQLException {

        if (duration == null) {
            statement.setNull(parameterIndex, Types.INTEGER);
        } else {
            statement.setLong(parameterIndex, duration.toMillis());
        }
    }

    private MediaFile readMediaFile(ResultSet resultSet)
            throws SQLException {

        final long durationMillis =
                resultSet.getLong(DURATION_COLUMN_INDEX);
        Duration duration = Duration.ofMillis(durationMillis);

        if (resultSet.wasNull()) {
            duration = null;
        }

        return new MediaFile(
                UUID.fromString(resultSet.getString(ID_COLUMN_INDEX)),
                Path.of(resultSet.getString(PATH_COLUMN_INDEX)),
                resultSet.getLong(FILE_SIZE_COLUMN_INDEX),
                resultSet.getString(HASH_COLUMN_INDEX),
                duration,
                resultSet.getInt(WIDTH_COLUMN_INDEX),
                resultSet.getInt(HEIGHT_COLUMN_INDEX),
                resultSet.getLong(LAST_MODIFIED_COLUMN_INDEX)
        );
    }
}

package repository;

import database.DatabaseManager;
import model.MediaFile;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MediaAssignmentRepository {
    private static final String MEDIA_EXISTS_SQL = """
            SELECT 1
            FROM media_file
            WHERE id = ?
            """;
    private static final String FIND_SCENE_ASSIGNMENTS_SQL = """
            SELECT DISTINCT s.id, s.title
            FROM scene s
            JOIN scene_media_file smf ON smf.scene_id = s.id
            WHERE smf.media_file_id = ?
            ORDER BY s.title COLLATE NOCASE, s.id
            """;
    private static final String FIND_MOVIE_ASSIGNMENTS_SQL = """
            SELECT DISTINCT m.id, m.title
            FROM movie m
            JOIN movie_media_file mmf ON mmf.movie_id = m.id
            WHERE mmf.media_file_id = ?
            ORDER BY m.title COLLATE NOCASE, m.id
            """;
    private static final String FIND_UNASSIGNED_BASE_SQL = """
            SELECT mf.id, mf.path, mf.file_size, mf.duration_millis,
                   mf.width, mf.height, mf.content_hash,
                   mf.last_modified_millis
            FROM media_file mf
            WHERE NOT EXISTS (
                SELECT 1
                FROM scene_media_file smf
                WHERE smf.media_file_id = mf.id
            )
            AND NOT EXISTS (
                SELECT 1
                FROM movie_media_file mmf
                WHERE mmf.media_file_id = mf.id
            )
            """;
    private static final String ORDER_AND_PAGE_SQL = """
            ORDER BY mf.path COLLATE NOCASE, mf.id
            LIMIT ? OFFSET ?
            """;
    private static final String PATH_FILTER_SQL = """
            AND LOWER(mf.path) LIKE LOWER(?)
            """;
    private static final String DIRECTORY_FILTER_SQL = """
            AND mf.path LIKE ?
            """;
    private static final String WIDTH_FILTER_SQL = """
            AND mf.width = ?
            """;
    private static final String HEIGHT_FILTER_SQL = """
            AND mf.height = ?
            """;
    private static final String MIN_WIDTH_FILTER_SQL = """
            AND mf.width >= ?
            """;
    private static final String MIN_HEIGHT_FILTER_SQL = """
            AND mf.height >= ?
            """;
    private static final String LIKE_WILDCARD = "%";
    private static final String DIRECTORY_SEPARATOR = "/";
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int ID_COLUMN_INDEX = 1;
    private static final int PATH_COLUMN_INDEX = 2;
    private static final int FILE_SIZE_COLUMN_INDEX = 3;
    private static final int DURATION_COLUMN_INDEX = 4;
    private static final int WIDTH_COLUMN_INDEX = 5;
    private static final int HEIGHT_COLUMN_INDEX = 6;
    private static final int HASH_COLUMN_INDEX = 7;
    private static final int LAST_MODIFIED_COLUMN_INDEX = 8;

    private final DatabaseManager databaseManager;

    public MediaAssignmentRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public MediaAssignment findAssignment(UUID mediaFileId)
            throws SQLException {

        Objects.requireNonNull(mediaFileId, "Media file ID must not be null");

        try (Connection connection = openConnection()) {
            return new MediaAssignment(
                    mediaFileId,
                    findReferences(
                            connection,
                            FIND_SCENE_ASSIGNMENTS_SQL,
                            mediaFileId
                    ),
                    findReferences(
                            connection,
                            FIND_MOVIE_ASSIGNMENTS_SQL,
                            mediaFileId
                    )
            );
        }
    }

    public boolean isUnassigned(UUID mediaFileId) throws SQLException {
        Objects.requireNonNull(mediaFileId, "Media file ID must not be null");

        final MediaAssignment assignment = findAssignment(mediaFileId);
        return mediaExists(mediaFileId)
                && assignment.scenes().isEmpty()
                && assignment.movies().isEmpty();
    }

    public List<MediaFile> findUnassigned(UnassignedMediaFilter filter)
            throws SQLException {

        final UnassignedMediaFilter effectiveFilter =
                filter == null ? UnassignedMediaFilter.firstPage() : filter;
        final StringBuilder sql = new StringBuilder(FIND_UNASSIGNED_BASE_SQL);
        final List<Object> values = new ArrayList<>();

        appendFilters(sql, values, effectiveFilter);
        sql.append(ORDER_AND_PAGE_SQL);
        values.add(effectiveFilter.limit());
        values.add(effectiveFilter.offset());

        final List<MediaFile> mediaFiles = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {

            bindValues(statement, values);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    mediaFiles.add(readMediaFile(resultSet));
                }
            }
        }

        return mediaFiles;
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

    private boolean mediaExists(UUID mediaFileId) throws SQLException {
        boolean exists = false;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(MEDIA_EXISTS_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, mediaFileId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                exists = resultSet.next();
            }
        }

        return exists;
    }

    private List<MediaAssignmentReference> findReferences(
            Connection connection,
            String sql,
            UUID mediaFileId) throws SQLException {

        final List<MediaAssignmentReference> references = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(FIRST_PARAMETER_INDEX, mediaFileId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    references.add(new MediaAssignmentReference(
                            UUID.fromString(
                                    resultSet.getString(ID_COLUMN_INDEX)
                            ),
                            resultSet.getString(SECOND_PARAMETER_INDEX)
                    ));
                }
            }
        }

        return references;
    }

    private void appendFilters(
            StringBuilder sql,
            List<Object> values,
            UnassignedMediaFilter filter) {

        if (filter.contains() != null) {
            sql.append(PATH_FILTER_SQL);
            values.add(LIKE_WILDCARD + filter.contains() + LIKE_WILDCARD);
        }

        if (filter.directory() != null) {
            sql.append(DIRECTORY_FILTER_SQL);
            values.add(filter.directory().toString() + DIRECTORY_SEPARATOR
                    + LIKE_WILDCARD);
        }

        if (filter.width() != null) {
            sql.append(WIDTH_FILTER_SQL);
            values.add(filter.width());
        }

        if (filter.height() != null) {
            sql.append(HEIGHT_FILTER_SQL);
            values.add(filter.height());
        }

        if (filter.minWidth() != null) {
            sql.append(MIN_WIDTH_FILTER_SQL);
            values.add(filter.minWidth());
        }

        if (filter.minHeight() != null) {
            sql.append(MIN_HEIGHT_FILTER_SQL);
            values.add(filter.minHeight());
        }
    }

    private void bindValues(PreparedStatement statement, List<Object> values)
            throws SQLException {

        int index = 0;

        while (index < values.size()) {
            final Object value = values.get(index);
            final int parameterIndex = index + FIRST_PARAMETER_INDEX;

            if (value instanceof Integer integerValue) {
                statement.setInt(parameterIndex, integerValue);
            } else {
                statement.setString(parameterIndex, value.toString());
            }

            index++;
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

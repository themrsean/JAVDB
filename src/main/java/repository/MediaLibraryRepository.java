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

public final class MediaLibraryRepository {
    private static final String SELECT_SQL = """
            SELECT mf.id, mf.path, mf.file_size, mf.duration_millis,
                   mf.width, mf.height, mf.content_hash,
                   mf.last_modified_millis,
                   EXISTS (SELECT 1 FROM scene_media_file smf
                           WHERE smf.media_file_id = mf.id),
                   EXISTS (SELECT 1 FROM movie_media_file mmf
                           WHERE mmf.media_file_id = mf.id)
            FROM media_file mf
            WHERE 1 = 1
            """;
    private static final String SCENE_EXISTS = """
            EXISTS (SELECT 1 FROM scene_media_file smf
                    WHERE smf.media_file_id = mf.id)
            """;
    private static final String MOVIE_EXISTS = """
            EXISTS (SELECT 1 FROM movie_media_file mmf
                    WHERE mmf.media_file_id = mf.id)
            """;

    private final DatabaseManager databaseManager;

    public MediaLibraryRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager, "Database manager must not be null"
        );
    }

    public List<MediaLibraryRecord> findPage(MediaLibraryFilter filter)
            throws SQLException {

        final MediaLibraryFilter effective = filter == null
                ? MediaLibraryFilter.firstPage() : filter;
        final StringBuilder sql = new StringBuilder(SELECT_SQL);
        final List<Object> values = new ArrayList<>();
        appendFilters(sql, values, effective);
        sql.append(" ORDER BY mf.path COLLATE NOCASE, mf.path, mf.id");
        sql.append(" LIMIT ? OFFSET ?");
        values.add(effective.limit() + 1);
        values.add(effective.offset());

        final List<MediaLibraryRecord> records = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql.toString())) {
            bind(statement, values);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(readRecord(resultSet));
                }
            }
        }
        return records;
    }

    private void appendFilters(
            StringBuilder sql,
            List<Object> values,
            MediaLibraryFilter filter) {

        if (filter.contains() != null) {
            sql.append(" AND instr(lower(mf.path), lower(?)) > 0");
            values.add(filter.contains());
        }
        if (filter.directory() != null) {
            final String directory = filter.directory().toString();
            sql.append(" AND (mf.path = ? OR mf.path LIKE ?)");
            values.add(directory);
            values.add(directory + "/%");
        }
        appendAssignmentFilter(sql, filter.assignmentState());
        appendIntegerFilter(sql, values, "mf.width = ?", filter.width());
        appendIntegerFilter(sql, values, "mf.height = ?", filter.height());
        appendIntegerFilter(sql, values, "mf.width >= ?", filter.minWidth());
        appendIntegerFilter(sql, values, "mf.height >= ?", filter.minHeight());
        if (filter.metadataQuality()
                == MediaLibraryMetadataQuality.MISSING_DIMENSIONS) {
            sql.append(" AND (mf.width IS NULL OR mf.width <= 0");
            sql.append(" OR mf.height IS NULL OR mf.height <= 0)");
        } else if (filter.metadataQuality()
                == MediaLibraryMetadataQuality.MISSING_DURATION) {
            sql.append(" AND mf.duration_millis IS NULL");
        }
    }

    private void appendAssignmentFilter(
            StringBuilder sql,
            MediaLibraryAssignmentState state) {

        switch (state) {
            case UNASSIGNED -> sql.append(" AND NOT ").append(SCENE_EXISTS)
                    .append(" AND NOT ").append(MOVIE_EXISTS);
            case SCENE -> sql.append(" AND ").append(SCENE_EXISTS)
                    .append(" AND NOT ").append(MOVIE_EXISTS);
            case MOVIE -> sql.append(" AND NOT ").append(SCENE_EXISTS)
                    .append(" AND ").append(MOVIE_EXISTS);
            case SCENE_AND_MOVIE -> sql.append(" AND ").append(SCENE_EXISTS)
                    .append(" AND ").append(MOVIE_EXISTS);
            case ALL -> { }
        }
    }

    private void appendIntegerFilter(
            StringBuilder sql,
            List<Object> values,
            String expression,
            Integer value) {
        if (value != null) {
            sql.append(" AND ").append(expression);
            values.add(value);
        }
    }

    private void bind(PreparedStatement statement, List<Object> values)
            throws SQLException {
        for (int index = 0; index < values.size(); index++) {
            final Object value = values.get(index);
            if (value instanceof Integer integer) {
                statement.setInt(index + 1, integer);
            } else {
                statement.setString(index + 1, value.toString());
            }
        }
    }

    private MediaLibraryRecord readRecord(ResultSet resultSet)
            throws SQLException {
        final long durationMillis = resultSet.getLong(4);
        Duration duration = Duration.ofMillis(durationMillis);
        if (resultSet.wasNull()) {
            duration = null;
        }
        final MediaFile mediaFile = new MediaFile(
                UUID.fromString(resultSet.getString(1)),
                Path.of(resultSet.getString(2)),
                resultSet.getLong(3),
                resultSet.getString(7),
                duration,
                resultSet.getInt(5),
                resultSet.getInt(6),
                resultSet.getLong(8)
        );
        return new MediaLibraryRecord(
                mediaFile,
                resultSet.getBoolean(9),
                resultSet.getBoolean(10)
        );
    }

    private Connection openConnection() throws SQLException {
        try {
            return databaseManager.openConnection();
        } catch (IOException exception) {
            throw new SQLException("Could not open database connection.", exception);
        }
    }
}

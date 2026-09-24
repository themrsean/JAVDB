package repository;

import database.DatabaseManager;
import model.MediaLocation;
import model.MediaLocationScanStatus;
import service.MediaLocationScanResult;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MediaLocationRepository {
    private static final String INSERT_SQL = """
            INSERT INTO media_location(
                id,
                path,
                enabled,
                recursive,
                created_at,
                updated_at,
                last_scan_status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_BASE_SQL = """
            SELECT id,
                   path,
                   enabled,
                   recursive,
                   created_at,
                   updated_at,
                   last_scan_started_at,
                   last_scan_completed_at,
                   last_scan_status,
                   last_scan_message,
                   last_discovered_count,
                   last_new_count,
                   last_updated_count,
                   last_unchanged_count,
                   last_missing_count,
                   last_failed_count
            FROM media_location
            """;
    private static final String FIND_BY_ID_SQL = SELECT_BASE_SQL + """
            WHERE id = ?
            """;
    private static final String FIND_BY_PATH_SQL = SELECT_BASE_SQL + """
            WHERE path = ?
            """;
    private static final String FIND_ALL_SQL = SELECT_BASE_SQL + """
            ORDER BY path COLLATE NOCASE, id
            """;
    private static final String FIND_ENABLED_SQL = SELECT_BASE_SQL + """
            WHERE enabled = TRUE
            ORDER BY path COLLATE NOCASE, id
            """;
    private static final String UPDATE_SETTINGS_SQL = """
            UPDATE media_location
            SET enabled = ?,
                recursive = ?,
                updated_at = ?
            WHERE id = ?
            """;
    private static final String UPDATE_SCAN_RESULT_SQL = """
            UPDATE media_location
            SET updated_at = ?,
                last_scan_started_at = ?,
                last_scan_completed_at = ?,
                last_scan_status = ?,
                last_scan_message = ?,
                last_discovered_count = ?,
                last_new_count = ?,
                last_updated_count = ?,
                last_unchanged_count = ?,
                last_missing_count = ?,
                last_failed_count = ?
            WHERE id = ?
            """;
    private static final String DELETE_SQL = """
            DELETE FROM media_location
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
    private static final int NINTH_PARAMETER_INDEX = 9;
    private static final int TENTH_PARAMETER_INDEX = 10;
    private static final int ELEVENTH_PARAMETER_INDEX = 11;
    private static final int TWELFTH_PARAMETER_INDEX = 12;
    private static final int ONE_ROW_CHANGED = 1;

    private final DatabaseManager databaseManager;

    public MediaLocationRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public void insert(MediaLocation location) throws SQLException {
        Objects.requireNonNull(
                location,
                "Media location must not be null"
        );

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    location.id().toString()
            );
            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    location.path().toString()
            );
            statement.setBoolean(THIRD_PARAMETER_INDEX, location.enabled());
            statement.setBoolean(FOURTH_PARAMETER_INDEX, location.recursive());
            statement.setString(
                    FIFTH_PARAMETER_INDEX,
                    location.createdAt().toString()
            );
            statement.setString(
                    SIXTH_PARAMETER_INDEX,
                    location.updatedAt().toString()
            );
            statement.setString(
                    SEVENTH_PARAMETER_INDEX,
                    location.lastScanStatus().name()
            );
            statement.executeUpdate();
        }
    }

    public Optional<MediaLocation> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Media location ID must not be null");
        return findOne(FIND_BY_ID_SQL, id.toString());
    }

    public Optional<MediaLocation> findByPath(Path path) throws SQLException {
        Objects.requireNonNull(path, "Media location path must not be null");
        return findOne(FIND_BY_PATH_SQL, path.toString());
    }

    public List<MediaLocation> findAll() throws SQLException {
        return findMany(FIND_ALL_SQL);
    }

    public List<MediaLocation> findEnabled() throws SQLException {
        return findMany(FIND_ENABLED_SQL);
    }

    public boolean updateSettings(
            UUID id,
            boolean enabled,
            boolean recursive,
            Instant updatedAt) throws SQLException {

        Objects.requireNonNull(id, "Media location ID must not be null");
        Objects.requireNonNull(updatedAt, "Updated timestamp must not be null");
        boolean updated;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(UPDATE_SETTINGS_SQL)) {

            statement.setBoolean(FIRST_PARAMETER_INDEX, enabled);
            statement.setBoolean(SECOND_PARAMETER_INDEX, recursive);
            statement.setString(THIRD_PARAMETER_INDEX, updatedAt.toString());
            statement.setString(FOURTH_PARAMETER_INDEX, id.toString());
            updated = statement.executeUpdate() == ONE_ROW_CHANGED;
        }

        return updated;
    }

    public boolean updateScanResult(
            UUID id,
            MediaLocationScanResult result,
            Instant updatedAt) throws SQLException {

        Objects.requireNonNull(id, "Media location ID must not be null");
        Objects.requireNonNull(result, "Scan result must not be null");
        Objects.requireNonNull(updatedAt, "Updated timestamp must not be null");
        boolean updated;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(UPDATE_SCAN_RESULT_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, updatedAt.toString());
            bindInstant(statement, SECOND_PARAMETER_INDEX, result.startedAt());
            bindInstant(
                    statement,
                    THIRD_PARAMETER_INDEX,
                    result.completedAt()
            );
            statement.setString(FOURTH_PARAMETER_INDEX, result.status().name());
            statement.setString(FIFTH_PARAMETER_INDEX, result.message());
            statement.setInt(SIXTH_PARAMETER_INDEX, result.discoveredCount());
            statement.setInt(SEVENTH_PARAMETER_INDEX, result.newCount());
            statement.setInt(EIGHTH_PARAMETER_INDEX, result.updatedCount());
            statement.setInt(NINTH_PARAMETER_INDEX, result.unchangedCount());
            statement.setInt(TENTH_PARAMETER_INDEX, result.missingCount());
            statement.setInt(ELEVENTH_PARAMETER_INDEX, result.failedCount());
            statement.setString(TWELFTH_PARAMETER_INDEX, id.toString());
            updated = statement.executeUpdate() == ONE_ROW_CHANGED;
        }

        return updated;
    }

    public boolean delete(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Media location ID must not be null");
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

    private Optional<MediaLocation> findOne(String sql, String value)
            throws SQLException {

        Optional<MediaLocation> location = Optional.empty();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    location = Optional.of(readLocation(resultSet));
                }
            }
        }

        return location;
    }

    private List<MediaLocation> findMany(String sql) throws SQLException {
        final List<MediaLocation> locations = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                locations.add(readLocation(resultSet));
            }
        }

        return locations;
    }

    private void bindInstant(
            PreparedStatement statement,
            int parameterIndex,
            Instant instant) throws SQLException {

        if (instant == null) {
            statement.setNull(parameterIndex, Types.VARCHAR);
        } else {
            statement.setString(parameterIndex, instant.toString());
        }
    }

    private MediaLocation readLocation(ResultSet resultSet)
            throws SQLException {

        return new MediaLocation(
                UUID.fromString(resultSet.getString("id")),
                Path.of(resultSet.getString("path")),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("recursive"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at")),
                readOptionalInstant(resultSet, "last_scan_started_at"),
                readOptionalInstant(resultSet, "last_scan_completed_at"),
                MediaLocationScanStatus.valueOf(
                        resultSet.getString("last_scan_status")
                ),
                resultSet.getString("last_scan_message"),
                resultSet.getInt("last_discovered_count"),
                resultSet.getInt("last_new_count"),
                resultSet.getInt("last_updated_count"),
                resultSet.getInt("last_unchanged_count"),
                resultSet.getInt("last_missing_count"),
                resultSet.getInt("last_failed_count")
        );
    }

    private Instant readOptionalInstant(ResultSet resultSet, String columnName)
            throws SQLException {

        final String value = resultSet.getString(columnName);
        Instant instant = null;

        if (value != null) {
            instant = Instant.parse(value);
        }

        return instant;
    }
}

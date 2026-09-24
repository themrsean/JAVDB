package repository;

import database.DatabaseManager;
import model.MediaFile;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import model.VerificationStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SceneRepository {
    private static final String INSERT_SCENE_SQL = """
            INSERT INTO scene(
                id, title, code, release_date, publisher_id, series_id,
                season, episode, verification_status
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SELECT_SCENE_SQL = """
            SELECT id, title, code, release_date, publisher_id, series_id,
                   season, episode, verification_status
            FROM scene
            """;
    private static final String FIND_BY_ID_SQL = SELECT_SCENE_SQL + """
            WHERE id = ?
            """;
    private static final String FIND_ALL_SQL = SELECT_SCENE_SQL + """
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String FIND_BY_VERIFICATION_STATUS_SQL =
            SELECT_SCENE_SQL + """
            WHERE verification_status = ?
            ORDER BY title COLLATE NOCASE, id
            LIMIT ? OFFSET ?
            """;
    private static final String UPDATE_SCENE_SQL = """
            UPDATE scene
            SET title = ?, code = ?, release_date = ?, publisher_id = ?,
                series_id = ?, season = ?, episode = ?,
                verification_status = ?
            WHERE id = ?
            """;
    private static final String DELETE_SCENE_SQL = """
            DELETE FROM scene
            WHERE id = ?
            """;
    private static final String INSERT_SCENE_PERFORMER_SQL = """
            INSERT INTO scene_performer(scene_id, performer_id)
            VALUES (?, ?)
            """;
    private static final String INSERT_SCENE_FILE_SQL = """
            INSERT INTO scene_media_file(scene_id, media_file_id)
            VALUES (?, ?)
            """;
    private static final String DELETE_SCENE_PERFORMERS_SQL = """
            DELETE FROM scene_performer
            WHERE scene_id = ?
            """;
    private static final String DELETE_SCENE_FILES_SQL = """
            DELETE FROM scene_media_file
            WHERE scene_id = ?
            """;
    private static final String FIND_PUBLISHER_SQL = """
            SELECT id, name
            FROM publisher
            WHERE id = ?
            """;
    private static final String FIND_SERIES_SQL = """
            SELECT id, title, publisher_id
            FROM series
            WHERE id = ?
            """;
    private static final String FIND_PERFORMERS_SQL = """
            SELECT p.id, p.main_name, p.category
            FROM performer p
            JOIN scene_performer sp ON sp.performer_id = p.id
            WHERE sp.scene_id = ?
            ORDER BY p.main_name COLLATE NOCASE, p.id
            """;
    private static final String FIND_MEDIA_FILES_SQL = """
            SELECT mf.id, mf.path, mf.file_size, mf.duration_millis,
                   mf.width, mf.height, mf.content_hash,
                   mf.last_modified_millis
            FROM media_file mf
            JOIN scene_media_file smf ON smf.media_file_id = mf.id
            WHERE smf.scene_id = ?
            ORDER BY mf.path COLLATE NOCASE, mf.id
            """;
    private static final String FIND_PERFORMER_ALIASES_SQL = """
            SELECT alias
            FROM performer_alias
            WHERE performer_id = ?
            ORDER BY alias COLLATE NOCASE
            """;
    private static final String FIND_PUBLISHER_ALIASES_SQL = """
            SELECT alias
            FROM publisher_alias
            WHERE publisher_id = ?
            ORDER BY alias COLLATE NOCASE
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
    private static final int ID_COLUMN_INDEX = 1;
    private static final int TITLE_COLUMN_INDEX = 2;
    private static final int CODE_COLUMN_INDEX = 3;
    private static final int RELEASE_DATE_COLUMN_INDEX = 4;
    private static final int PUBLISHER_ID_COLUMN_INDEX = 5;
    private static final int SERIES_ID_COLUMN_INDEX = 6;
    private static final int SEASON_COLUMN_INDEX = 7;
    private static final int EPISODE_COLUMN_INDEX = 8;
    private static final int VERIFICATION_STATUS_COLUMN_INDEX = 9;
    private static final int SECOND_COLUMN_INDEX = 2;
    private static final int THIRD_COLUMN_INDEX = 3;
    private static final int FOURTH_COLUMN_INDEX = 4;
    private static final int FIFTH_COLUMN_INDEX = 5;
    private static final int SIXTH_COLUMN_INDEX = 6;
    private static final int SEVENTH_COLUMN_INDEX = 7;
    private static final int EIGHTH_COLUMN_INDEX = 8;
    private static final int ONE_ROW_CHANGED = 1;
    private static final int MAXIMUM_PAGE_LIMIT = 1_000;

    private final DatabaseManager databaseManager;

    public SceneRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public void insert(Scene scene) throws SQLException {
        Objects.requireNonNull(scene, "Scene must not be null");

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                insertScene(connection, scene);
                insertPerformers(connection, scene);
                insertFiles(connection, scene);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public Optional<Scene> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Scene ID must not be null");

        Optional<Scene> scene = Optional.empty();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_BY_ID_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    scene = Optional.of(readScene(connection, resultSet));
                }
            }
        }

        return scene;
    }

    public List<Scene> findAll() throws SQLException {
        final List<Scene> scenes = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                scenes.add(readScene(connection, resultSet));
            }
        }

        return scenes;
    }

    public List<Scene> findByVerificationStatus(
            VerificationStatus status,
            int limit,
            int offset) throws SQLException {

        Objects.requireNonNull(status, "Verification status must not be null");
        validatePage(limit, offset);

        final List<Scene> scenes = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     FIND_BY_VERIFICATION_STATUS_SQL
             )) {

            statement.setString(FIRST_PARAMETER_INDEX, status.name());
            statement.setInt(SECOND_PARAMETER_INDEX, limit);
            statement.setInt(THIRD_PARAMETER_INDEX, offset);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    scenes.add(readScene(connection, resultSet));
                }
            }
        }

        return scenes;
    }

    public void update(Scene scene) throws SQLException {
        Objects.requireNonNull(scene, "Scene must not be null");

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                updateScene(connection, scene);
                deleteScenePerformers(connection, scene.getId());
                deleteSceneFiles(connection, scene.getId());
                insertPerformers(connection, scene);
                insertFiles(connection, scene);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public boolean delete(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Scene ID must not be null");

        boolean deleted;

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 DELETE_SCENE_SQL
                         )) {

                statement.setString(
                        FIRST_PARAMETER_INDEX,
                        id.toString()
                );
                deleted =
                        statement.executeUpdate() == ONE_ROW_CHANGED;
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
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

    private void insertScene(Connection connection, Scene scene)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT_SCENE_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, scene.getId().toString());
            statement.setString(SECOND_PARAMETER_INDEX, scene.getTitle());
            statement.setString(THIRD_PARAMETER_INDEX, scene.getCode());
            bindDate(statement, FOURTH_PARAMETER_INDEX, scene.getReleaseDate());
            statement.setString(
                    FIFTH_PARAMETER_INDEX,
                    scene.getPublisher().getId().toString()
            );
            bindUuid(
                    statement,
                    SIXTH_PARAMETER_INDEX,
                    scene.getSeries() == null
                            ? null
                            : scene.getSeries().getId()
            );
            statement.setString(SEVENTH_PARAMETER_INDEX, scene.getSeason());
            statement.setString(EIGHTH_PARAMETER_INDEX, scene.getEpisode());
            statement.setString(
                    NINTH_PARAMETER_INDEX,
                    scene.getVerificationStatus().name()
            );
            statement.executeUpdate();
        }
    }

    private void updateScene(Connection connection, Scene scene)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(UPDATE_SCENE_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, scene.getTitle());
            statement.setString(SECOND_PARAMETER_INDEX, scene.getCode());
            bindDate(statement, THIRD_PARAMETER_INDEX, scene.getReleaseDate());
            statement.setString(
                    FOURTH_PARAMETER_INDEX,
                    scene.getPublisher().getId().toString()
            );
            bindUuid(
                    statement,
                    FIFTH_PARAMETER_INDEX,
                    scene.getSeries() == null
                            ? null
                            : scene.getSeries().getId()
            );
            statement.setString(SIXTH_PARAMETER_INDEX, scene.getSeason());
            statement.setString(SEVENTH_PARAMETER_INDEX, scene.getEpisode());
            statement.setString(
                    EIGHTH_PARAMETER_INDEX,
                    scene.getVerificationStatus().name()
            );
            statement.setString(NINTH_PARAMETER_INDEX, scene.getId().toString());
            statement.executeUpdate();
        }
    }

    private void insertPerformers(Connection connection, Scene scene)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_SCENE_PERFORMER_SQL
                     )) {

            for (Performer performer : scene.getPerformers()) {
                statement.setString(
                        FIRST_PARAMETER_INDEX,
                        scene.getId().toString()
                );
                statement.setString(
                        SECOND_PARAMETER_INDEX,
                        performer.getId().toString()
                );
                statement.executeUpdate();
            }
        }
    }

    private void insertFiles(Connection connection, Scene scene)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_SCENE_FILE_SQL
                     )) {

            for (MediaFile file : scene.getFiles()) {
                statement.setString(
                        FIRST_PARAMETER_INDEX,
                        scene.getId().toString()
                );
                statement.setString(
                        SECOND_PARAMETER_INDEX,
                        file.getId().toString()
                );
                statement.executeUpdate();
            }
        }
    }

    private Scene readScene(Connection connection, ResultSet resultSet)
            throws SQLException {

        final UUID id = UUID.fromString(
                resultSet.getString(ID_COLUMN_INDEX)
        );
        final UUID publisherId = UUID.fromString(
                resultSet.getString(PUBLISHER_ID_COLUMN_INDEX)
        );
        final String releaseDateText =
                resultSet.getString(RELEASE_DATE_COLUMN_INDEX);
        final String seriesIdText =
                resultSet.getString(SERIES_ID_COLUMN_INDEX);
        Series series = null;

        if (seriesIdText != null) {
            series = findSeries(
                    connection,
                    UUID.fromString(seriesIdText)
            );
        }

        return new Scene(
                id,
                resultSet.getString(TITLE_COLUMN_INDEX),
                findPublisher(connection, publisherId),
                releaseDateText == null
                        ? null
                        : LocalDate.parse(releaseDateText),
                resultSet.getString(CODE_COLUMN_INDEX),
                series,
                resultSet.getString(SEASON_COLUMN_INDEX),
                resultSet.getString(EPISODE_COLUMN_INDEX),
                findPerformers(connection, id),
                findMediaFiles(connection, id),
                readVerificationStatus(resultSet)
        );
    }

    private VerificationStatus readVerificationStatus(ResultSet resultSet)
            throws SQLException {

        final String statusText = resultSet.getString(
                VERIFICATION_STATUS_COLUMN_INDEX
        );

        try {
            return VerificationStatus.valueOf(statusText);
        } catch (IllegalArgumentException exception) {
            throw new SQLException(
                    "Invalid scene verification status: " + statusText,
                    exception
            );
        }
    }

    private Publisher findPublisher(Connection connection, UUID id)
            throws SQLException {

        Publisher publisher = null;

        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_PUBLISHER_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    publisher = new Publisher(
                            id,
                            resultSet.getString(SECOND_COLUMN_INDEX),
                            findAliases(
                                    connection,
                                    FIND_PUBLISHER_ALIASES_SQL,
                                    id
                            )
                    );
                }
            }
        }

        return publisher;
    }

    private Series findSeries(Connection connection, UUID id)
            throws SQLException {

        Series series = null;

        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_SERIES_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    final UUID publisherId = UUID.fromString(
                            resultSet.getString(THIRD_COLUMN_INDEX)
                    );
                    series = new Series(
                            id,
                            resultSet.getString(SECOND_COLUMN_INDEX),
                            findPublisher(connection, publisherId)
                    );
                }
            }
        }

        return series;
    }

    private List<Performer> findPerformers(
            Connection connection,
            UUID sceneId) throws SQLException {

        final List<Performer> performers = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_PERFORMERS_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, sceneId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID performerId = UUID.fromString(
                            resultSet.getString(ID_COLUMN_INDEX)
                    );
                    performers.add(new Performer(
                            performerId,
                            resultSet.getString(SECOND_COLUMN_INDEX),
                            findAliases(
                                    connection,
                                    FIND_PERFORMER_ALIASES_SQL,
                                    performerId
                            ),
                            PerformerCategory.valueOf(
                                    resultSet.getString(
                                            THIRD_COLUMN_INDEX
                                    )
                            )
                    ));
                }
            }
        }

        return performers;
    }

    private List<MediaFile> findMediaFiles(
            Connection connection,
            UUID sceneId) throws SQLException {

        final List<MediaFile> mediaFiles = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_MEDIA_FILES_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, sceneId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final long durationMillis =
                            resultSet.getLong(FOURTH_COLUMN_INDEX);
                    Duration duration =
                            Duration.ofMillis(durationMillis);

                    if (resultSet.wasNull()) {
                        duration = null;
                    }

                    mediaFiles.add(new MediaFile(
                            UUID.fromString(
                                    resultSet.getString(ID_COLUMN_INDEX)
                            ),
                            Path.of(resultSet.getString(SECOND_COLUMN_INDEX)),
                            resultSet.getLong(THIRD_COLUMN_INDEX),
                            resultSet.getString(SEVENTH_COLUMN_INDEX),
                            duration,
                            resultSet.getInt(FIFTH_COLUMN_INDEX),
                            resultSet.getInt(SIXTH_COLUMN_INDEX),
                            resultSet.getLong(EIGHTH_COLUMN_INDEX)
                    ));
                }
            }
        }

        return mediaFiles;
    }

    private List<String> findAliases(
            Connection connection,
            String sql,
            UUID ownerId) throws SQLException {

        final List<String> aliases = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    ownerId.toString()
            );

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    aliases.add(resultSet.getString(ID_COLUMN_INDEX));
                }
            }
        }

        return aliases;
    }

    private void deleteScenePerformers(
            Connection connection,
            UUID sceneId) throws SQLException {

        deleteRelationships(connection, DELETE_SCENE_PERFORMERS_SQL, sceneId);
    }

    private void deleteSceneFiles(Connection connection, UUID sceneId)
            throws SQLException {

        deleteRelationships(connection, DELETE_SCENE_FILES_SQL, sceneId);
    }

    private void deleteRelationships(
            Connection connection,
            String sql,
            UUID sceneId) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, sceneId.toString());
            statement.executeUpdate();
        }
    }

    private void bindDate(
            PreparedStatement statement,
            int parameterIndex,
            LocalDate date) throws SQLException {

        if (date == null) {
            statement.setNull(parameterIndex, Types.VARCHAR);
        } else {
            statement.setString(parameterIndex, date.toString());
        }
    }

    private void bindUuid(
            PreparedStatement statement,
            int parameterIndex,
            UUID id) throws SQLException {

        if (id == null) {
            statement.setNull(parameterIndex, Types.VARCHAR);
        } else {
            statement.setString(parameterIndex, id.toString());
        }
    }

    private void validatePage(int limit, int offset) {
        if (limit <= 0 || limit > MAXIMUM_PAGE_LIMIT) {
            throw new IllegalArgumentException(
                    "Limit must be between 1 and " + MAXIMUM_PAGE_LIMIT + "."
            );
        }

        if (offset < 0) {
            throw new IllegalArgumentException(
                    "Offset must not be negative."
            );
        }
    }

    private void rollback(
            Connection connection,
            SQLException originalException) {

        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }
}

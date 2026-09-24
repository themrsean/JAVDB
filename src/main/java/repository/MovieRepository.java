package repository;

import database.DatabaseManager;
import model.MediaFile;
import model.Movie;
import model.Publisher;
import model.Scene;

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

public final class MovieRepository {
    private static final String INSERT_MOVIE_SQL = """
            INSERT INTO movie(
                id, title, release_date, publisher_id, compilation
            )
            VALUES (?, ?, ?, ?, ?)
            """;
    private static final String SELECT_MOVIE_SQL = """
            SELECT id, title, release_date, publisher_id, compilation
            FROM movie
            """;
    private static final String FIND_BY_ID_SQL = SELECT_MOVIE_SQL + """
            WHERE id = ?
            """;
    private static final String FIND_ALL_SQL = SELECT_MOVIE_SQL + """
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String UPDATE_MOVIE_SQL = """
            UPDATE movie
            SET title = ?, release_date = ?, publisher_id = ?,
                compilation = ?
            WHERE id = ?
            """;
    private static final String DELETE_MOVIE_SQL = """
            DELETE FROM movie
            WHERE id = ?
            """;
    private static final String INSERT_MOVIE_SCENE_SQL = """
            INSERT INTO movie_scene(movie_id, scene_id, scene_order)
            VALUES (?, ?, ?)
            """;
    private static final String INSERT_MOVIE_FILE_SQL = """
            INSERT INTO movie_media_file(movie_id, media_file_id)
            VALUES (?, ?)
            """;
    private static final String DELETE_MOVIE_SCENES_SQL = """
            DELETE FROM movie_scene
            WHERE movie_id = ?
            """;
    private static final String DELETE_MOVIE_FILES_SQL = """
            DELETE FROM movie_media_file
            WHERE movie_id = ?
            """;
    private static final String FIND_PUBLISHER_SQL = """
            SELECT id, name
            FROM publisher
            WHERE id = ?
            """;
    private static final String FIND_SCENES_SQL = """
            SELECT s.id, s.title, s.code, s.release_date, s.publisher_id,
                   s.series_id, s.season, s.episode
            FROM scene s
            JOIN movie_scene ms ON ms.scene_id = s.id
            WHERE ms.movie_id = ?
            ORDER BY ms.scene_order
            """;
    private static final String FIND_MEDIA_FILES_SQL = """
            SELECT mf.id, mf.path, mf.file_size, mf.duration_millis,
                   mf.width, mf.height, mf.content_hash,
                   mf.last_modified_millis
            FROM media_file mf
            JOIN movie_media_file mmf ON mmf.media_file_id = mf.id
            WHERE mmf.movie_id = ?
            ORDER BY mf.path COLLATE NOCASE, mf.id
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
    private static final int ID_COLUMN_INDEX = 1;
    private static final int TITLE_COLUMN_INDEX = 2;
    private static final int RELEASE_DATE_COLUMN_INDEX = 3;
    private static final int PUBLISHER_ID_COLUMN_INDEX = 4;
    private static final int COMPILATION_COLUMN_INDEX = 5;
    private static final int MEDIA_FILE_DURATION_COLUMN_INDEX = 4;
    private static final int MEDIA_FILE_WIDTH_COLUMN_INDEX = 5;
    private static final int MEDIA_FILE_HEIGHT_COLUMN_INDEX = 6;
    private static final int MEDIA_FILE_HASH_COLUMN_INDEX = 7;
    private static final int MEDIA_FILE_LAST_MODIFIED_COLUMN_INDEX = 8;
    private static final int SCENE_CODE_COLUMN_INDEX = 3;
    private static final int SCENE_RELEASE_DATE_COLUMN_INDEX = 4;
    private static final int SCENE_PUBLISHER_ID_COLUMN_INDEX = 5;
    private static final int SCENE_SEASON_COLUMN_INDEX = 7;
    private static final int SCENE_EPISODE_COLUMN_INDEX = 8;
    private static final int ONE_ROW_CHANGED = 1;

    private final DatabaseManager databaseManager;

    public MovieRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public void insert(Movie movie) throws SQLException {
        Objects.requireNonNull(movie, "Movie must not be null");

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                insertMovie(connection, movie);
                insertScenes(connection, movie);
                insertFiles(connection, movie);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public Optional<Movie> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Movie ID must not be null");

        Optional<Movie> movie = Optional.empty();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_BY_ID_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    movie = Optional.of(readMovie(connection, resultSet));
                }
            }
        }

        return movie;
    }

    public List<Movie> findAll() throws SQLException {
        final List<Movie> movies = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                movies.add(readMovie(connection, resultSet));
            }
        }

        return movies;
    }

    public void update(Movie movie) throws SQLException {
        Objects.requireNonNull(movie, "Movie must not be null");

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                updateMovie(connection, movie);
                deleteRelationships(
                        connection,
                        DELETE_MOVIE_SCENES_SQL,
                        movie.getId()
                );
                deleteRelationships(
                        connection,
                        DELETE_MOVIE_FILES_SQL,
                        movie.getId()
                );
                insertScenes(connection, movie);
                insertFiles(connection, movie);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public boolean delete(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Movie ID must not be null");

        boolean deleted;

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement statement =
                         connection.prepareStatement(DELETE_MOVIE_SQL)) {

                statement.setString(FIRST_PARAMETER_INDEX, id.toString());
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

    private void insertMovie(Connection connection, Movie movie)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT_MOVIE_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, movie.getId().toString());
            statement.setString(SECOND_PARAMETER_INDEX, movie.getTitle());
            bindDate(statement, THIRD_PARAMETER_INDEX, movie.getReleaseDate());
            statement.setString(
                    FOURTH_PARAMETER_INDEX,
                    movie.getPublisher().getId().toString()
            );
            statement.setBoolean(FIFTH_PARAMETER_INDEX, movie.isCompilation());
            statement.executeUpdate();
        }
    }

    private void updateMovie(Connection connection, Movie movie)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(UPDATE_MOVIE_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, movie.getTitle());
            bindDate(statement, SECOND_PARAMETER_INDEX, movie.getReleaseDate());
            statement.setString(
                    THIRD_PARAMETER_INDEX,
                    movie.getPublisher().getId().toString()
            );
            statement.setBoolean(FOURTH_PARAMETER_INDEX, movie.isCompilation());
            statement.setString(FIFTH_PARAMETER_INDEX, movie.getId().toString());
            statement.executeUpdate();
        }
    }

    private void insertScenes(Connection connection, Movie movie)
            throws SQLException {

        int sceneOrder = 0;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_MOVIE_SCENE_SQL
                     )) {

            for (Scene scene : movie.getScenes()) {
                statement.setString(
                        FIRST_PARAMETER_INDEX,
                        movie.getId().toString()
                );
                statement.setString(
                        SECOND_PARAMETER_INDEX,
                        scene.getId().toString()
                );
                statement.setInt(THIRD_PARAMETER_INDEX, sceneOrder);
                statement.executeUpdate();
                sceneOrder++;
            }
        }
    }

    private void insertFiles(Connection connection, Movie movie)
            throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_MOVIE_FILE_SQL
                     )) {

            for (MediaFile file : movie.getFiles()) {
                statement.setString(
                        FIRST_PARAMETER_INDEX,
                        movie.getId().toString()
                );
                statement.setString(
                        SECOND_PARAMETER_INDEX,
                        file.getId().toString()
                );
                statement.executeUpdate();
            }
        }
    }

    private Movie readMovie(Connection connection, ResultSet resultSet)
            throws SQLException {

        final UUID id = UUID.fromString(
                resultSet.getString(ID_COLUMN_INDEX)
        );
        final UUID publisherId = UUID.fromString(
                resultSet.getString(PUBLISHER_ID_COLUMN_INDEX)
        );
        final String releaseDateText =
                resultSet.getString(RELEASE_DATE_COLUMN_INDEX);

        return new Movie(
                id,
                resultSet.getString(TITLE_COLUMN_INDEX),
                releaseDateText == null
                        ? null
                        : LocalDate.parse(releaseDateText),
                findPublisher(connection, publisherId),
                findScenes(connection, id),
                resultSet.getBoolean(COMPILATION_COLUMN_INDEX),
                findMediaFiles(connection, id)
        );
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
                            resultSet.getString(TITLE_COLUMN_INDEX),
                            findPublisherAliases(connection, id)
                    );
                }
            }
        }

        return publisher;
    }

    private List<String> findPublisherAliases(
            Connection connection,
            UUID publisherId) throws SQLException {

        final List<String> aliases = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             FIND_PUBLISHER_ALIASES_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    publisherId.toString()
            );

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    aliases.add(resultSet.getString(ID_COLUMN_INDEX));
                }
            }
        }

        return aliases;
    }

    private List<Scene> findScenes(Connection connection, UUID movieId)
            throws SQLException {

        final List<Scene> scenes = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_SCENES_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, movieId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final UUID publisherId = UUID.fromString(
                            resultSet.getString(
                                    SCENE_PUBLISHER_ID_COLUMN_INDEX
                            )
                    );
                    final String releaseDateText =
                            resultSet.getString(
                                    SCENE_RELEASE_DATE_COLUMN_INDEX
                            );
                    scenes.add(new Scene(
                            UUID.fromString(
                                    resultSet.getString(ID_COLUMN_INDEX)
                            ),
                            resultSet.getString(TITLE_COLUMN_INDEX),
                            findPublisher(connection, publisherId),
                            releaseDateText == null
                                    ? null
                                    : LocalDate.parse(releaseDateText),
                            resultSet.getString(SCENE_CODE_COLUMN_INDEX),
                            null,
                            resultSet.getString(SCENE_SEASON_COLUMN_INDEX),
                            resultSet.getString(SCENE_EPISODE_COLUMN_INDEX),
                            List.of(),
                            List.of()
                    ));
                }
            }
        }

        return scenes;
    }

    private List<MediaFile> findMediaFiles(
            Connection connection,
            UUID movieId) throws SQLException {

        final List<MediaFile> mediaFiles = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_MEDIA_FILES_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, movieId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final long durationMillis =
                            resultSet.getLong(
                                    MEDIA_FILE_DURATION_COLUMN_INDEX
                            );
                    Duration duration =
                            Duration.ofMillis(durationMillis);

                    if (resultSet.wasNull()) {
                        duration = null;
                    }

                    mediaFiles.add(new MediaFile(
                            UUID.fromString(
                                    resultSet.getString(ID_COLUMN_INDEX)
                            ),
                            Path.of(resultSet.getString(TITLE_COLUMN_INDEX)),
                            resultSet.getLong(RELEASE_DATE_COLUMN_INDEX),
                            resultSet.getString(
                                    MEDIA_FILE_HASH_COLUMN_INDEX
                            ),
                            duration,
                            resultSet.getInt(
                                    MEDIA_FILE_WIDTH_COLUMN_INDEX
                            ),
                            resultSet.getInt(
                                    MEDIA_FILE_HEIGHT_COLUMN_INDEX
                            ),
                            resultSet.getLong(
                                    MEDIA_FILE_LAST_MODIFIED_COLUMN_INDEX
                            )
                    ));
                }
            }
        }

        return mediaFiles;
    }

    private void deleteRelationships(
            Connection connection,
            String sql,
            UUID movieId) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, movieId.toString());
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

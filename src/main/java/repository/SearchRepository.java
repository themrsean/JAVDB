package repository;

import database.DatabaseManager;
import model.Movie;
import model.Performer;
import model.Scene;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SearchRepository {
    private static final String SCENES_BY_PERFORMER_SQL = """
            SELECT s.id
            FROM scene s
            JOIN scene_performer sp ON sp.scene_id = s.id
            WHERE sp.performer_id = ?
            ORDER BY s.title COLLATE NOCASE, s.id
            """;
    private static final String MOVIES_BY_PERFORMER_SQL = """
            SELECT DISTINCT m.id, m.title
            FROM movie m
            JOIN movie_scene ms ON ms.movie_id = m.id
            JOIN scene_performer sp ON sp.scene_id = ms.scene_id
            WHERE sp.performer_id = ?
            ORDER BY m.title COLLATE NOCASE, m.id
            """;
    private static final String CO_PERFORMERS_SQL = """
            SELECT DISTINCT p.id, p.main_name
            FROM performer p
            JOIN scene_performer sp_other ON sp_other.performer_id = p.id
            JOIN scene_performer sp_selected
              ON sp_selected.scene_id = sp_other.scene_id
            WHERE sp_selected.performer_id = ?
              AND p.id <> ?
            ORDER BY p.main_name COLLATE NOCASE, p.id
            """;
    private static final String SCENES_BY_TITLE_SQL = """
            SELECT id
            FROM scene
            WHERE title LIKE ? COLLATE NOCASE
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String MOVIES_BY_TITLE_SQL = """
            SELECT id
            FROM movie
            WHERE title LIKE ? COLLATE NOCASE
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String SCENES_BY_CODE_SQL = """
            SELECT id
            FROM scene
            WHERE code LIKE ? COLLATE NOCASE
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String SCENES_BY_DATE_SQL = """
            SELECT id
            FROM scene
            WHERE release_date >= ?
              AND release_date <= ?
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String MOVIES_BY_DATE_SQL = """
            SELECT id
            FROM movie
            WHERE release_date >= ?
              AND release_date <= ?
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String SCENES_BY_PUBLISHER_SQL = """
            SELECT id
            FROM scene
            WHERE publisher_id = ?
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String SCENES_BY_SERIES_SQL = """
            SELECT id
            FROM scene
            WHERE series_id = ?
            ORDER BY title COLLATE NOCASE, id
            """;
    private static final String SCENES_BY_ALL_PREFIX = """
            SELECT s.id
            FROM scene s
            JOIN scene_performer sp ON sp.scene_id = s.id
            WHERE sp.performer_id IN (
            """;
    private static final String SCENES_BY_ALL_SUFFIX = """
            )
            GROUP BY s.id
            HAVING COUNT(DISTINCT sp.performer_id) = ?
            ORDER BY s.title COLLATE NOCASE, s.id
            """;
    private static final String MOVIES_BY_ALL_PREFIX = """
            SELECT m.id
            FROM movie m
            JOIN movie_scene ms ON ms.movie_id = m.id
            JOIN scene_performer sp ON sp.scene_id = ms.scene_id
            WHERE sp.performer_id IN (
            """;
    private static final String MOVIES_BY_ALL_SUFFIX = """
            )
            GROUP BY m.id
            HAVING COUNT(DISTINCT sp.performer_id) = ?
            ORDER BY m.title COLLATE NOCASE, m.id
            """;
    private static final String LIKE_WILDCARD = "%";
    private static final String PLACEHOLDER = "?";
    private static final String PLACEHOLDER_SEPARATOR = ", ";

    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int FIRST_COLUMN_INDEX = 1;

    private final DatabaseManager databaseManager;

    public SearchRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public List<Scene> findScenesByPerformer(UUID performerId)
            throws SQLException {

        Objects.requireNonNull(
                performerId,
                "Performer ID must not be null"
        );

        return findScenes(SCENES_BY_PERFORMER_SQL, performerId.toString());
    }

    public List<Scene> findScenesByAllPerformers(
            List<UUID> performerIds) throws SQLException {

        return findScenesByAll(performerIds);
    }

    public List<Scene> findScenesSharedByPerformers(
            UUID firstPerformerId,
            UUID secondPerformerId) throws SQLException {

        return findScenesByAllPerformers(
                List.of(firstPerformerId, secondPerformerId)
        );
    }

    public List<Movie> findMoviesByPerformer(UUID performerId)
            throws SQLException {

        Objects.requireNonNull(
                performerId,
                "Performer ID must not be null"
        );

        return findMovies(MOVIES_BY_PERFORMER_SQL, performerId.toString());
    }

    public List<Movie> findMoviesByAllPerformers(
            List<UUID> performerIds) throws SQLException {

        return findMoviesByAll(performerIds);
    }

    public List<Movie> findMoviesSharedByPerformers(
            UUID firstPerformerId,
            UUID secondPerformerId) throws SQLException {

        return findMoviesByAllPerformers(
                List.of(firstPerformerId, secondPerformerId)
        );
    }

    public List<Performer> findCoPerformers(UUID performerId)
            throws SQLException {

        Objects.requireNonNull(
                performerId,
                "Performer ID must not be null"
        );

        final List<UUID> ids = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(CO_PERFORMERS_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    performerId.toString()
            );
            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    performerId.toString()
            );

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(UUID.fromString(
                            resultSet.getString(FIRST_COLUMN_INDEX)
                    ));
                }
            }
        }

        final PerformerRepository performerRepository =
                new PerformerRepository(databaseManager);
        final List<Performer> performers = new ArrayList<>();

        for (UUID id : ids) {
            performerRepository.findById(id).ifPresent(performers::add);
        }

        return performers;
    }

    public List<Scene> searchScenesByTitle(String searchText)
            throws SQLException {

        return findScenes(
                SCENES_BY_TITLE_SQL,
                pattern(searchText)
        );
    }

    public List<Movie> searchMoviesByTitle(String searchText)
            throws SQLException {

        return findMovies(
                MOVIES_BY_TITLE_SQL,
                pattern(searchText)
        );
    }

    public List<Scene> searchScenesByCode(String searchText)
            throws SQLException {

        return findScenes(
                SCENES_BY_CODE_SQL,
                pattern(searchText)
        );
    }

    public List<Scene> findScenesByReleaseDateRange(
            LocalDate startDate,
            LocalDate endDate) throws SQLException {

        Objects.requireNonNull(startDate, "Start date must not be null");
        Objects.requireNonNull(endDate, "End date must not be null");

        return findScenes(
                SCENES_BY_DATE_SQL,
                startDate.toString(),
                endDate.toString()
        );
    }

    public List<Movie> findMoviesByReleaseDateRange(
            LocalDate startDate,
            LocalDate endDate) throws SQLException {

        Objects.requireNonNull(startDate, "Start date must not be null");
        Objects.requireNonNull(endDate, "End date must not be null");

        return findMovies(
                MOVIES_BY_DATE_SQL,
                startDate.toString(),
                endDate.toString()
        );
    }

    public List<Scene> findScenesByPublisher(UUID publisherId)
            throws SQLException {

        Objects.requireNonNull(
                publisherId,
                "Publisher ID must not be null"
        );

        return findScenes(SCENES_BY_PUBLISHER_SQL, publisherId.toString());
    }

    public List<Scene> findScenesBySeries(UUID seriesId)
            throws SQLException {

        Objects.requireNonNull(seriesId, "Series ID must not be null");

        return findScenes(SCENES_BY_SERIES_SQL, seriesId.toString());
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

    private List<Scene> findScenes(String sql, String... values)
            throws SQLException {

        final List<UUID> ids = findIds(sql, values);
        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final List<Scene> scenes = new ArrayList<>();

        for (UUID id : ids) {
            sceneRepository.findById(id).ifPresent(scenes::add);
        }

        return scenes;
    }

    private List<Movie> findMovies(String sql, String... values)
            throws SQLException {

        final List<UUID> ids = findIds(sql, values);
        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);
        final List<Movie> movies = new ArrayList<>();

        for (UUID id : ids) {
            movieRepository.findById(id).ifPresent(movies::add);
        }

        return movies;
    }

    private List<Scene> findScenesByAll(List<UUID> performerIds)
            throws SQLException {

        validatePerformerIds(performerIds);

        final List<Scene> scenes;

        if (performerIds.isEmpty()) {
            scenes = List.of();
        } else {
            scenes = findScenes(
                    SCENES_BY_ALL_PREFIX
                            + placeholders(performerIds.size())
                            + SCENES_BY_ALL_SUFFIX,
                    findIdsByAll(
                            SCENES_BY_ALL_PREFIX
                                    + placeholders(performerIds.size())
                                    + SCENES_BY_ALL_SUFFIX,
                            performerIds
                    )
            );
        }

        return scenes;
    }

    private List<Movie> findMoviesByAll(List<UUID> performerIds)
            throws SQLException {

        validatePerformerIds(performerIds);

        final List<Movie> movies;

        if (performerIds.isEmpty()) {
            movies = List.of();
        } else {
            movies = findMovies(
                    MOVIES_BY_ALL_PREFIX
                            + placeholders(performerIds.size())
                            + MOVIES_BY_ALL_SUFFIX,
                    findIdsByAll(
                            MOVIES_BY_ALL_PREFIX
                                    + placeholders(performerIds.size())
                                    + MOVIES_BY_ALL_SUFFIX,
                            performerIds
                    )
            );
        }

        return movies;
    }

    private List<Scene> findScenes(String sql, List<UUID> ids)
            throws SQLException {

        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final List<Scene> scenes = new ArrayList<>();

        for (UUID id : ids) {
            sceneRepository.findById(id).ifPresent(scenes::add);
        }

        return scenes;
    }

    private List<Movie> findMovies(String sql, List<UUID> ids)
            throws SQLException {

        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);
        final List<Movie> movies = new ArrayList<>();

        for (UUID id : ids) {
            movieRepository.findById(id).ifPresent(movies::add);
        }

        return movies;
    }

    private List<UUID> findIds(String sql, String... values)
            throws SQLException {

        final List<UUID> ids = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            for (int index = 0; index < values.length; index++) {
                statement.setString(index + FIRST_PARAMETER_INDEX, values[index]);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(UUID.fromString(
                            resultSet.getString(FIRST_COLUMN_INDEX)
                    ));
                }
            }
        }

        return ids;
    }

    private List<UUID> findIdsByAll(
            String sql,
            List<UUID> performerIds) throws SQLException {

        final List<UUID> ids = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            for (int index = 0; index < performerIds.size(); index++) {
                statement.setString(
                        index + FIRST_PARAMETER_INDEX,
                        performerIds.get(index).toString()
                );
            }

            statement.setInt(
                    performerIds.size() + FIRST_PARAMETER_INDEX,
                    performerIds.size()
            );

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(UUID.fromString(
                            resultSet.getString(FIRST_COLUMN_INDEX)
                    ));
                }
            }
        }

        return ids;
    }

    private String pattern(String searchText) {
        Objects.requireNonNull(
                searchText,
                "Search text must not be null"
        );

        return LIKE_WILDCARD + searchText + LIKE_WILDCARD;
    }

    private void validatePerformerIds(List<UUID> performerIds) {
        Objects.requireNonNull(
                performerIds,
                "Performer IDs must not be null"
        );

        for (UUID performerId : performerIds) {
            Objects.requireNonNull(
                    performerId,
                    "Performer ID must not be null"
            );
        }
    }

    private String placeholders(int count) {
        return String.join(
                PLACEHOLDER_SEPARATOR,
                java.util.Collections.nCopies(count, PLACEHOLDER)
        );
    }

}

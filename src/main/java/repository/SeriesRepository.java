package repository;

import database.DatabaseManager;
import model.Publisher;
import model.Series;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SeriesRepository {
    private static final String INSERT_SQL = """
            INSERT INTO series(id, title, publisher_id)
            VALUES (?, ?, ?)
            """;
    private static final String SELECT_BASE_SQL = """
            SELECT s.id, s.title, p.id, p.name
            FROM series s
            JOIN publisher p ON p.id = s.publisher_id
            """;
    private static final String FIND_BY_ID_SQL = SELECT_BASE_SQL + """
            WHERE s.id = ?
            """;
    private static final String FIND_ALL_SQL = SELECT_BASE_SQL + """
            ORDER BY s.title COLLATE NOCASE, s.id
            """;
    private static final String FIND_BY_PUBLISHER_SQL = SELECT_BASE_SQL + """
            WHERE s.publisher_id = ?
            ORDER BY s.title COLLATE NOCASE, s.id
            """;
    private static final String SEARCH_BY_TITLE_SQL = SELECT_BASE_SQL + """
            WHERE s.title LIKE ? COLLATE NOCASE
            ORDER BY s.title COLLATE NOCASE, s.id
            """;
    private static final String FIND_PUBLISHER_ALIASES_SQL = """
            SELECT alias
            FROM publisher_alias
            WHERE publisher_id = ?
            ORDER BY alias COLLATE NOCASE
            """;
    private static final String UPDATE_SQL = """
            UPDATE series
            SET title = ?, publisher_id = ?
            WHERE id = ?
            """;
    private static final String DELETE_SQL = """
            DELETE FROM series
            WHERE id = ?
            """;
    private static final String LIKE_WILDCARD = "%";

    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int THIRD_PARAMETER_INDEX = 3;
    private static final int SERIES_ID_COLUMN_INDEX = 1;
    private static final int TITLE_COLUMN_INDEX = 2;
    private static final int PUBLISHER_ID_COLUMN_INDEX = 3;
    private static final int PUBLISHER_NAME_COLUMN_INDEX = 4;
    private static final int ALIAS_COLUMN_INDEX = 1;
    private static final int ONE_ROW_CHANGED = 1;

    private final DatabaseManager databaseManager;

    public SeriesRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public void insert(Series series) throws SQLException {
        Objects.requireNonNull(series, "Series must not be null");

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    series.getId().toString()
            );
            statement.setString(SECOND_PARAMETER_INDEX, series.getTitle());
            statement.setString(
                    THIRD_PARAMETER_INDEX,
                    series.getPublisher().getId().toString()
            );
            statement.executeUpdate();
        }
    }

    public Optional<Series> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Series ID must not be null");

        Optional<Series> series = Optional.empty();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_BY_ID_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    series = Optional.of(readSeries(
                            connection,
                            resultSet
                    ));
                }
            }
        }

        return series;
    }

    public List<Series> findAll() throws SQLException {
        return findSeries(FIND_ALL_SQL);
    }

    public List<Series> findByPublisher(UUID publisherId)
            throws SQLException {

        Objects.requireNonNull(
                publisherId,
                "Publisher ID must not be null"
        );

        return findSeries(FIND_BY_PUBLISHER_SQL, publisherId.toString());
    }

    public List<Series> searchByTitle(String searchText)
            throws SQLException {

        Objects.requireNonNull(
                searchText,
                "Search text must not be null"
        );

        return findSeries(
                SEARCH_BY_TITLE_SQL,
                LIKE_WILDCARD + searchText + LIKE_WILDCARD
        );
    }

    public void update(Series series) throws SQLException {
        Objects.requireNonNull(series, "Series must not be null");

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, series.getTitle());
            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    series.getPublisher().getId().toString()
            );
            statement.setString(
                    THIRD_PARAMETER_INDEX,
                    series.getId().toString()
            );
            statement.executeUpdate();
        }
    }

    public boolean delete(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Series ID must not be null");

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

    private List<Series> findSeries(String sql) throws SQLException {
        final List<Series> series = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                series.add(readSeries(connection, resultSet));
            }
        }

        return series;
    }

    private List<Series> findSeries(String sql, String value)
            throws SQLException {

        final List<Series> series = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(FIRST_PARAMETER_INDEX, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    series.add(readSeries(connection, resultSet));
                }
            }
        }

        return series;
    }

    private Series readSeries(
            Connection connection,
            ResultSet resultSet) throws SQLException {

        final UUID publisherId = UUID.fromString(
                resultSet.getString(PUBLISHER_ID_COLUMN_INDEX)
        );
        final Publisher publisher = new Publisher(
                publisherId,
                resultSet.getString(PUBLISHER_NAME_COLUMN_INDEX),
                findPublisherAliases(connection, publisherId)
        );

        return new Series(
                UUID.fromString(
                        resultSet.getString(SERIES_ID_COLUMN_INDEX)
                ),
                resultSet.getString(TITLE_COLUMN_INDEX),
                publisher
        );
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
                    aliases.add(resultSet.getString(
                            ALIAS_COLUMN_INDEX
                    ));
                }
            }
        }

        return aliases;
    }
}

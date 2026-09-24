package repository;

import database.DatabaseManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class EntitySuggestionRepository {
    private static final String PERFORMER_SQL = """
            SELECT id, display_name, matched_text, match_field,
                   MIN(rank_order) AS rank_order, NULL AS publisher_id
            FROM (
                SELECT p.id, p.main_name AS display_name,
                       p.main_name AS matched_text,
                       'PRIMARY' AS match_field,
                       CASE
                           WHEN p.main_name = ? COLLATE NOCASE THEN ?
                           WHEN p.main_name LIKE ? COLLATE NOCASE THEN ?
                           ELSE ?
                       END AS rank_order
                FROM performer p
                WHERE p.main_name LIKE ? COLLATE NOCASE
                UNION ALL
                SELECT p.id, p.main_name AS display_name,
                       pa.alias AS matched_text,
                       'ALIAS' AS match_field,
                       CASE
                           WHEN pa.alias = ? COLLATE NOCASE THEN ?
                           WHEN pa.alias LIKE ? COLLATE NOCASE THEN ?
                           ELSE ?
                       END AS rank_order
                FROM performer p
                JOIN performer_alias pa ON pa.performer_id = p.id
                WHERE pa.alias LIKE ? COLLATE NOCASE
            )
            GROUP BY id
            ORDER BY rank_order, display_name COLLATE NOCASE, id
            LIMIT ?
            """;
    private static final String PUBLISHER_SQL = """
            SELECT id, display_name, matched_text, match_field,
                   MIN(rank_order) AS rank_order, NULL AS publisher_id
            FROM (
                SELECT p.id, p.name AS display_name,
                       p.name AS matched_text,
                       'PRIMARY' AS match_field,
                       CASE
                           WHEN p.name = ? COLLATE NOCASE THEN ?
                           WHEN p.name LIKE ? COLLATE NOCASE THEN ?
                           ELSE ?
                       END AS rank_order
                FROM publisher p
                WHERE p.name LIKE ? COLLATE NOCASE
                UNION ALL
                SELECT p.id, p.name AS display_name,
                       pa.alias AS matched_text,
                       'ALIAS' AS match_field,
                       CASE
                           WHEN pa.alias = ? COLLATE NOCASE THEN ?
                           WHEN pa.alias LIKE ? COLLATE NOCASE THEN ?
                           ELSE ?
                       END AS rank_order
                FROM publisher p
                JOIN publisher_alias pa ON pa.publisher_id = p.id
                WHERE pa.alias LIKE ? COLLATE NOCASE
            )
            GROUP BY id
            ORDER BY rank_order, display_name COLLATE NOCASE, id
            LIMIT ?
            """;
    private static final String SERIES_SQL = """
            SELECT s.id, s.title AS display_name, s.title AS matched_text,
                   'PRIMARY' AS match_field,
                   CASE
                       WHEN s.title = ? COLLATE NOCASE THEN ?
                       WHEN s.title LIKE ? COLLATE NOCASE THEN ?
                       ELSE ?
                   END AS rank_order,
                   s.publisher_id
            FROM series s
            WHERE s.title LIKE ? COLLATE NOCASE
              AND (? IS NULL OR s.publisher_id = ?)
            ORDER BY rank_order, s.title COLLATE NOCASE, s.id
            LIMIT ?
            """;
    private static final String MOVIE_SQL = """
            SELECT m.id, m.title AS display_name, m.title AS matched_text,
                   'PRIMARY' AS match_field,
                   CASE
                       WHEN m.title = ? COLLATE NOCASE THEN ?
                       WHEN m.title LIKE ? COLLATE NOCASE THEN ?
                       ELSE ?
                   END AS rank_order,
                   m.publisher_id
            FROM movie m
            WHERE m.title LIKE ? COLLATE NOCASE
              AND (? IS NULL OR m.publisher_id = ?)
            ORDER BY rank_order, m.title COLLATE NOCASE, m.id
            LIMIT ?
            """;
    private static final String WILDCARD = "%";
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int ID_COLUMN_INDEX = 1;
    private static final int DISPLAY_COLUMN_INDEX = 2;
    private static final int MATCHED_COLUMN_INDEX = 3;
    private static final int MATCH_FIELD_COLUMN_INDEX = 4;
    private static final int RANK_COLUMN_INDEX = 5;
    private static final int PUBLISHER_COLUMN_INDEX = 6;

    private final DatabaseManager databaseManager;

    public EntitySuggestionRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public List<EntitySuggestion> suggestPerformers(String query, int limit)
            throws SQLException {

        return suggestWithAliases(PERFORMER_SQL, query, limit);
    }

    public List<EntitySuggestion> suggestPublishers(String query, int limit)
            throws SQLException {

        return suggestWithAliases(PUBLISHER_SQL, query, limit);
    }

    public List<EntitySuggestion> suggestSeries(
            String query,
            UUID publisherId,
            int limit) throws SQLException {

        return suggestTitled(SERIES_SQL, query, publisherId, limit);
    }

    public List<EntitySuggestion> suggestMovies(
            String query,
            UUID publisherId,
            int limit) throws SQLException {

        return suggestTitled(MOVIE_SQL, query, publisherId, limit);
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

    private List<EntitySuggestion> suggestWithAliases(
            String sql,
            String query,
            int limit) throws SQLException {

        final String normalizedQuery = normalizeQuery(query);
        final String prefixPattern = normalizedQuery + WILDCARD;
        final String substringPattern =
                WILDCARD + normalizedQuery + WILDCARD;
        final List<EntitySuggestion> suggestions = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            int parameterIndex = FIRST_PARAMETER_INDEX;
            statement.setString(parameterIndex, normalizedQuery);
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.PRIMARY_EXACT.orderValue());
            parameterIndex++;
            statement.setString(parameterIndex, prefixPattern);
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.PRIMARY_PREFIX.orderValue());
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.PRIMARY_SUBSTRING.orderValue());
            parameterIndex++;
            statement.setString(parameterIndex, substringPattern);
            parameterIndex++;
            statement.setString(parameterIndex, normalizedQuery);
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.ALIAS_EXACT.orderValue());
            parameterIndex++;
            statement.setString(parameterIndex, prefixPattern);
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.ALIAS_PREFIX.orderValue());
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.ALIAS_SUBSTRING.orderValue());
            parameterIndex++;
            statement.setString(parameterIndex, substringPattern);
            parameterIndex++;
            statement.setInt(parameterIndex, SuggestionQuery.validateLimit(limit));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    suggestions.add(readSuggestion(resultSet));
                }
            }
        }

        return suggestions;
    }

    private List<EntitySuggestion> suggestTitled(
            String sql,
            String query,
            UUID publisherId,
            int limit) throws SQLException {

        final String normalizedQuery = normalizeQuery(query);
        final String prefixPattern = normalizedQuery + WILDCARD;
        final String substringPattern =
                WILDCARD + normalizedQuery + WILDCARD;
        final List<EntitySuggestion> suggestions = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            int parameterIndex = FIRST_PARAMETER_INDEX;
            statement.setString(parameterIndex, normalizedQuery);
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.PRIMARY_EXACT.orderValue());
            parameterIndex++;
            statement.setString(parameterIndex, prefixPattern);
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.PRIMARY_PREFIX.orderValue());
            parameterIndex++;
            statement.setInt(parameterIndex, MatchRank.PRIMARY_SUBSTRING.orderValue());
            parameterIndex++;
            statement.setString(parameterIndex, substringPattern);
            parameterIndex++;
            statement.setString(
                    parameterIndex,
                    publisherId == null ? null : publisherId.toString()
            );
            parameterIndex++;
            statement.setString(
                    parameterIndex,
                    publisherId == null ? null : publisherId.toString()
            );
            parameterIndex++;
            statement.setInt(parameterIndex, SuggestionQuery.validateLimit(limit));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    suggestions.add(readSuggestion(resultSet));
                }
            }
        }

        return suggestions;
    }

    private String normalizeQuery(String query) {
        String normalized = "";

        if (query != null) {
            normalized = query.trim();
        }

        return normalized;
    }

    private EntitySuggestion readSuggestion(ResultSet resultSet)
            throws SQLException {

        final String publisherText =
                resultSet.getString(PUBLISHER_COLUMN_INDEX);
        return new EntitySuggestion(
                UUID.fromString(resultSet.getString(ID_COLUMN_INDEX)),
                resultSet.getString(DISPLAY_COLUMN_INDEX),
                resultSet.getString(MATCHED_COLUMN_INDEX),
                MatchField.valueOf(resultSet.getString(MATCH_FIELD_COLUMN_INDEX)),
                rankFor(resultSet.getInt(RANK_COLUMN_INDEX)),
                publisherText == null ? null : UUID.fromString(publisherText)
        );
    }

    private MatchRank rankFor(int orderValue) {
        MatchRank rank = MatchRank.PRIMARY_SUBSTRING;

        for (MatchRank candidate : MatchRank.values()) {
            if (candidate.orderValue() == orderValue) {
                rank = candidate;
            }
        }

        return rank;
    }
}

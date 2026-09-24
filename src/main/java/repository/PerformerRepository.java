package repository;

import database.DatabaseManager;
import model.Performer;
import model.PerformerCategory;

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

public final class PerformerRepository {
    private static final String INSERT_PERFORMER_SQL = """
            INSERT INTO performer(id, main_name, category)
            VALUES (?, ?, ?)
            """;
    private static final String INSERT_ALIAS_SQL = """
            INSERT INTO performer_alias(performer_id, alias)
            VALUES (?, ?)
            """;
    private static final String FIND_BY_ID_SQL = """
            SELECT id, main_name, category
            FROM performer
            WHERE id = ?
            """;
    private static final String FIND_ALL_SQL = """
            SELECT id, main_name, category
            FROM performer
            ORDER BY main_name COLLATE NOCASE, id
            """;
    private static final String SEARCH_BY_NAME_SQL = """
            SELECT DISTINCT p.id, p.main_name, p.category
            FROM performer p
            LEFT JOIN performer_alias pa ON pa.performer_id = p.id
            WHERE p.main_name LIKE ? COLLATE NOCASE
               OR pa.alias LIKE ? COLLATE NOCASE
            ORDER BY p.main_name COLLATE NOCASE, p.id
            """;
    private static final String FIND_ALIASES_SQL = """
            SELECT alias
            FROM performer_alias
            WHERE performer_id = ?
            ORDER BY alias COLLATE NOCASE
            """;
    private static final String UPDATE_PERFORMER_SQL = """
            UPDATE performer
            SET main_name = ?, category = ?
            WHERE id = ?
            """;
    private static final String DELETE_ALIASES_SQL = """
            DELETE FROM performer_alias
            WHERE performer_id = ?
            """;
    private static final String DELETE_PERFORMER_SQL = """
            DELETE FROM performer
            WHERE id = ?
            """;
    private static final String LIKE_WILDCARD = "%";

    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int THIRD_PARAMETER_INDEX = 3;
    private static final int ID_COLUMN_INDEX = 1;
    private static final int MAIN_NAME_COLUMN_INDEX = 2;
    private static final int CATEGORY_COLUMN_INDEX = 3;
    private static final int ALIAS_COLUMN_INDEX = 1;
    private static final int ONE_ROW_CHANGED = 1;

    private final DatabaseManager databaseManager;

    public PerformerRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public void insert(Performer performer) throws SQLException {
        Objects.requireNonNull(performer, "Performer must not be null");

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                insertPerformer(connection, performer);
                insertAliases(connection, performer);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public Optional<Performer> findById(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Performer ID must not be null");

        Optional<Performer> performer = Optional.empty();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_BY_ID_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, id.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    performer = Optional.of(
                            readPerformer(connection, resultSet)
                    );
                }
            }
        }

        return performer;
    }

    public List<Performer> findAll() throws SQLException {
        final List<Performer> performers = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                performers.add(readPerformer(connection, resultSet));
            }
        }

        return performers;
    }

    public List<Performer> searchByName(String searchText)
            throws SQLException {

        Objects.requireNonNull(
                searchText,
                "Search text must not be null"
        );

        final List<Performer> performers = new ArrayList<>();
        final String pattern =
                LIKE_WILDCARD + searchText + LIKE_WILDCARD;

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SEARCH_BY_NAME_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, pattern);
            statement.setString(SECOND_PARAMETER_INDEX, pattern);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    performers.add(readPerformer(
                            connection,
                            resultSet
                    ));
                }
            }
        }

        return performers;
    }

    public void update(Performer performer) throws SQLException {
        Objects.requireNonNull(performer, "Performer must not be null");

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                updatePerformer(connection, performer);
                deleteAliases(connection, performer.getId());
                insertAliases(connection, performer);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public boolean delete(UUID id) throws SQLException {
        Objects.requireNonNull(id, "Performer ID must not be null");

        boolean deleted;

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 DELETE_PERFORMER_SQL
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

    private void insertPerformer(
            Connection connection,
            Performer performer) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_PERFORMER_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    performer.getId().toString()
            );
            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    performer.getMainName()
            );
            statement.setString(
                    THIRD_PARAMETER_INDEX,
                    performer.getCategory().name()
            );
            statement.executeUpdate();
        }
    }

    private void insertAliases(
            Connection connection,
            Performer performer) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(INSERT_ALIAS_SQL)) {

            for (String alias : performer.getAliases()) {
                statement.setString(
                        FIRST_PARAMETER_INDEX,
                        performer.getId().toString()
                );
                statement.setString(SECOND_PARAMETER_INDEX, alias);
                statement.executeUpdate();
            }
        }
    }

    private Performer readPerformer(
            Connection connection,
            ResultSet resultSet) throws SQLException {

        final UUID id = UUID.fromString(
                resultSet.getString(ID_COLUMN_INDEX)
        );
        final String mainName =
                resultSet.getString(MAIN_NAME_COLUMN_INDEX);
        final PerformerCategory category = PerformerCategory.valueOf(
                resultSet.getString(CATEGORY_COLUMN_INDEX)
        );
        final List<String> aliases = findAliases(connection, id);

        return new Performer(id, mainName, aliases, category);
    }

    private List<String> findAliases(
            Connection connection,
            UUID performerId) throws SQLException {

        final List<String> aliases = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(FIND_ALIASES_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    performerId.toString()
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

    private void updatePerformer(
            Connection connection,
            Performer performer) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             UPDATE_PERFORMER_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    performer.getMainName()
            );
            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    performer.getCategory().name()
            );
            statement.setString(
                    THIRD_PARAMETER_INDEX,
                    performer.getId().toString()
            );
            statement.executeUpdate();
        }
    }

    private void deleteAliases(
            Connection connection,
            UUID performerId) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(DELETE_ALIASES_SQL)) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    performerId.toString()
            );
            statement.executeUpdate();
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

package repository;

import database.DatabaseManager;
import model.Publisher;

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

public final class PublisherRepository {
    private static final String INSERT_PUBLISHER_SQL = """
            INSERT INTO publisher(id, name)
            VALUES (?, ?)
            """;

    private static final String INSERT_ALIAS_SQL = """
            INSERT INTO publisher_alias(publisher_id, alias)
            VALUES (?, ?)
            """;

    private static final String FIND_PUBLISHER_BY_ID_SQL = """
            SELECT id, name
            FROM publisher
            WHERE id = ?
            """;

    private static final String FIND_ALL_PUBLISHERS_SQL = """
            SELECT id, name
            FROM publisher
            ORDER BY name COLLATE NOCASE, id
            """;

    private static final String FIND_ALIASES_BY_PUBLISHER_ID_SQL = """
            SELECT alias
            FROM publisher_alias
            WHERE publisher_id = ?
            ORDER BY alias COLLATE NOCASE
            """;

    private static final String UPDATE_PUBLISHER_SQL = """
            UPDATE publisher
            SET name = ?
            WHERE id = ?
            """;

    private static final String DELETE_ALIASES_SQL = """
            DELETE FROM publisher_alias
            WHERE publisher_id = ?
            """;

    private static final String DELETE_PUBLISHER_SQL = """
            DELETE FROM publisher
            WHERE id = ?
            """;

    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int SECOND_PARAMETER_INDEX = 2;
    private static final int ID_COLUMN_INDEX = 1;
    private static final int NAME_COLUMN_INDEX = 2;
    private static final int ALIAS_COLUMN_INDEX = 1;
    private static final int ONE_ROW_CHANGED = 1;

    private final DatabaseManager databaseManager;

    public PublisherRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(
                databaseManager,
                "Database manager must not be null"
        );
    }

    public void insert(Publisher publisher) throws SQLException {
        Objects.requireNonNull(
                publisher,
                "Publisher must not be null"
        );

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                insertPublisher(connection, publisher);
                insertAliases(connection, publisher);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public Optional<Publisher> findById(UUID id) throws SQLException {
        Objects.requireNonNull(
                id,
                "Publisher ID must not be null"
        );

        Optional<Publisher> publisher = Optional.empty();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             FIND_PUBLISHER_BY_ID_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    id.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    publisher = Optional.of(
                            readPublisher(connection, resultSet)
                    );
                }
            }
        }

        return publisher;
    }

    public List<Publisher> findAll() throws SQLException {
        final List<Publisher> publishers = new ArrayList<>();

        try (Connection connection = openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(
                             FIND_ALL_PUBLISHERS_SQL
                     );
             ResultSet resultSet =
                     statement.executeQuery()) {

            while (resultSet.next()) {
                publishers.add(readPublisher(
                        connection,
                        resultSet
                ));
            }
        }

        return publishers;
    }

    public void update(Publisher publisher) throws SQLException {
        Objects.requireNonNull(
                publisher,
                "Publisher must not be null"
        );

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try {
                updatePublisher(connection, publisher);
                deleteAliases(connection, publisher.getId());
                insertAliases(connection, publisher);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public boolean delete(UUID id) throws SQLException {
        Objects.requireNonNull(
                id,
                "Publisher ID must not be null"
        );

        boolean deleted;

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement statement =
                         connection.prepareStatement(
                                 DELETE_PUBLISHER_SQL
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

    private void insertPublisher(
            Connection connection,
            Publisher publisher) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_PUBLISHER_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    publisher.getId().toString()
            );
            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    publisher.getName()
            );

            statement.executeUpdate();
        }
    }

    private void insertAliases(
            Connection connection,
            Publisher publisher) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             INSERT_ALIAS_SQL
                     )) {

            for (String alias : publisher.getAliases()) {
                statement.setString(
                        FIRST_PARAMETER_INDEX,
                        publisher.getId().toString()
                );
                statement.setString(
                        SECOND_PARAMETER_INDEX,
                        alias
                );

                statement.executeUpdate();
            }
        }
    }

    private Publisher readPublisher(
            Connection connection,
            ResultSet resultSet) throws SQLException {

        final UUID id = UUID.fromString(
                resultSet.getString(ID_COLUMN_INDEX)
        );
        final String name = resultSet.getString(
                NAME_COLUMN_INDEX
        );
        final List<String> aliases =
                findAliases(connection, id);

        return new Publisher(id, name, aliases);
    }

    private List<String> findAliases(
            Connection connection,
            UUID publisherId) throws SQLException {

        final List<String> aliases = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             FIND_ALIASES_BY_PUBLISHER_ID_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    publisherId.toString()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    aliases.add(resultSet.getString(
                            ALIAS_COLUMN_INDEX
                    ));
                }
            }
        }

        return aliases;
    }

    private void updatePublisher(
            Connection connection,
            Publisher publisher) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             UPDATE_PUBLISHER_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    publisher.getName()
            );
            statement.setString(
                    SECOND_PARAMETER_INDEX,
                    publisher.getId().toString()
            );

            statement.executeUpdate();
        }
    }

    private void deleteAliases(
            Connection connection,
            UUID publisherId) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             DELETE_ALIASES_SQL
                     )) {

            statement.setString(
                    FIRST_PARAMETER_INDEX,
                    publisherId.toString()
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
            originalException.addSuppressed(
                    rollbackException
            );
        }
    }
}

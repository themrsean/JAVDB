/*
 * Course: None
 * JAVDB
 * DatabaseManager
 */
package database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Creates and configures connections to the JAVDB SQLite database.
 */
public final class DatabaseManager {
    private static final String JDBC_URL_PREFIX = "jdbc:sqlite:";
    private static final String ENABLE_FOREIGN_KEYS_SQL =
            "PRAGMA foreign_keys = ON";
    private static final String VERIFY_FOREIGN_KEYS_SQL =
            "PRAGMA foreign_keys";
    private static final String FOREIGN_KEYS_RESULT_COLUMN =
            "foreign_keys";
    private static final int FOREIGN_KEYS_ENABLED_VALUE = 1;

    private final Path databasePath;

    /**
     * Creates a database manager for the specified database file.
     *
     * @param databasePath path to the SQLite database file
     */
    public DatabaseManager(Path databasePath) {
        this.databasePath = Objects.requireNonNull(
                databasePath,
                "Database path must not be null"
        ).toAbsolutePath().normalize();
    }

    /**
     * Opens a configured connection to the database.
     *
     * <p>The caller is responsible for closing the returned connection,
     * preferably with a try-with-resources statement.</p>
     *
     * @return configured database connection
     * @throws IOException if the database directory cannot be created
     * @throws SQLException if the connection cannot be opened or configured
     */
    public Connection openConnection() throws IOException, SQLException {
        createDatabaseDirectory();

        final String databaseUrl =
                JDBC_URL_PREFIX + databasePath;
        final Connection connection =
                DriverManager.getConnection(databaseUrl);

        try {
            configureConnection(connection);
        } catch (SQLException exception) {
            closeAfterConfigurationFailure(connection, exception);
            throw exception;
        }

        return connection;
    }

    /**
     * Returns the absolute, normalized database path.
     *
     * @return database file path
     */
    public Path getDatabasePath() {
        return databasePath;
    }

    private void createDatabaseDirectory() throws IOException {
        final Path parentDirectory = databasePath.getParent();

        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }
    }

    private void configureConnection(Connection connection)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(ENABLE_FOREIGN_KEYS_SQL);

            try (ResultSet resultSet =
                         statement.executeQuery(VERIFY_FOREIGN_KEYS_SQL)) {
                final boolean resultAvailable = resultSet.next();
                final boolean foreignKeysEnabled =
                        resultAvailable
                                && resultSet.getInt(
                                FOREIGN_KEYS_RESULT_COLUMN
                        ) == FOREIGN_KEYS_ENABLED_VALUE;

                if (!foreignKeysEnabled) {
                    throw new SQLException(
                            "SQLite foreign-key enforcement could not be enabled."
                    );
                }
            }
        }
    }

    private void closeAfterConfigurationFailure(
            Connection connection,
            SQLException originalException) {
        try {
            connection.close();
        } catch (SQLException closeException) {
            originalException.addSuppressed(closeException);
        }
    }
}
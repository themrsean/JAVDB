package ui;

import database.DatabaseManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

class GuiApplicationFactoryTest {
    private static final String DATABASE_FILE_NAME =
            "gui-application-factory-test.db";
    private static final String SCHEMA_VERSION_KEY =
            "schema_version";
    private static final String CURRENT_SCHEMA_VERSION =
            "3";
    private static final int FIRST_PARAMETER_INDEX = 1;
    private static final int FIRST_RESULT_COLUMN_INDEX = 1;

    private static final String SELECT_METADATA_SQL = """
            SELECT metadata_value
            FROM app_metadata
            WHERE metadata_key = ?
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Factory initializes schema and exposes selected database path")
    void factoryInitializesSchemaAndExposesSelectedDatabasePath()
            throws Exception {

        final Path databasePath =
                temporaryDirectory.resolve(DATABASE_FILE_NAME);
        final GuiApplicationFactory factory =
                new GuiApplicationFactory();

        try (GuiApplicationContext context =
                     factory.create(databasePath)) {

            Assertions.assertEquals(
                    databasePath.toAbsolutePath().normalize(),
                    context.databasePath()
            );
            Assertions.assertNotNull(context.controller());
            Assertions.assertEquals(
                    CURRENT_SCHEMA_VERSION,
                    schemaVersion(databasePath)
            );
        }
    }

    @Test
    @DisplayName("Closing context shuts down GUI background resources")
    void closingContextShutsDownGuiBackgroundResources()
            throws Exception {

        final GuiApplicationFactory factory =
                new GuiApplicationFactory();
        final GuiApplicationContext context =
                factory.create(temporaryDirectory.resolve(DATABASE_FILE_NAME));

        context.close();

        Assertions.assertTrue(context.backgroundExecutor().isShutdown());
    }

    private String schemaVersion(Path databasePath) throws Exception {
        final DatabaseManager databaseManager =
                new DatabaseManager(databasePath);
        String version = "";

        try (Connection connection = databaseManager.openConnection();
             PreparedStatement statement =
                     connection.prepareStatement(SELECT_METADATA_SQL)) {

            statement.setString(FIRST_PARAMETER_INDEX, SCHEMA_VERSION_KEY);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    version = resultSet.getString(
                            FIRST_RESULT_COLUMN_INDEX
                    );
                }
            }
        }

        return version;
    }
}

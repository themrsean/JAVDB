package repository;

import database.DatabaseManager;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Focused SQL source for background filename-review aggregation. */
public final class UnassignedMediaPathRepository {
    private static final String SQL = """
            SELECT mf.path FROM media_file mf
            WHERE NOT EXISTS (SELECT 1 FROM scene_media_file smf
                              WHERE smf.media_file_id = mf.id)
              AND NOT EXISTS (SELECT 1 FROM movie_media_file mmf
                              WHERE mmf.media_file_id = mf.id)
            ORDER BY mf.path COLLATE NOCASE, mf.id
            """;
    private final DatabaseManager databaseManager;

    public UnassignedMediaPathRepository(DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager);
    }

    public List<Path> findAll() throws SQLException {
        final List<Path> paths = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(SQL);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                paths.add(Path.of(results.getString(1)));
            }
        }
        return paths;
    }

    private Connection openConnection() throws SQLException {
        try {
            return databaseManager.openConnection();
        } catch (IOException exception) {
            throw new SQLException("Could not open database connection.", exception);
        }
    }
}

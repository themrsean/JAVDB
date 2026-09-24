package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.EntitySuggestionRepository;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MediaLibraryFilter;
import repository.MediaLibraryRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

class MediaLibraryServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Detail lookup reuses filename preview and reports filesystem state")
    void detailLookupReusesFilenamePreview() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve("details.db"));
        new SchemaManager(databaseManager).initialize();
        final MediaFileRepository mediaRepository =
                new MediaFileRepository(databaseManager);
        final MediaAssignmentRepository assignmentRepository =
                new MediaAssignmentRepository(databaseManager);
        final MediaFilenameIndexingService indexingService =
                new MediaFilenameIndexingService(
                        mediaRepository,
                        assignmentRepository,
                        new MediaFilenameParser(),
                        new FilenameMetadataMatcher(
                                new EntitySuggestionRepository(databaseManager))
                );
        final Path path = temporaryDirectory.resolve(
                "(26.01.02) Unknown - Example - Nobody.mp4");
        Files.writeString(path, "fixture");
        final UUID id = UUID.randomUUID();
        mediaRepository.insert(new MediaFile(
                id, path, Files.size(path), "abc123",
                Duration.ofSeconds(65), 1920, 1080,
                Files.getLastModifiedTime(path).toMillis()
        ));
        execute(databaseManager, "INSERT INTO publisher(id, name) VALUES "
                + "('30000000-0000-0000-0000-000000000001', 'Publisher')");
        execute(databaseManager, "INSERT INTO scene(id, title, publisher_id) "
                + "VALUES ('30000000-0000-0000-0000-000000000002', "
                + "'Assigned Scene', '30000000-0000-0000-0000-000000000001')");
        execute(databaseManager, "INSERT INTO movie(id, title, publisher_id) "
                + "VALUES ('30000000-0000-0000-0000-000000000003', "
                + "'Assigned Movie', '30000000-0000-0000-0000-000000000001')");
        execute(databaseManager, "INSERT INTO scene_media_file VALUES "
                + "('30000000-0000-0000-0000-000000000002', '" + id + "')");
        execute(databaseManager, "INSERT INTO movie_media_file VALUES "
                + "('30000000-0000-0000-0000-000000000003', '" + id + "')");
        final MediaLibraryService service = new MediaLibraryService(
                new MediaLibraryRepository(databaseManager), mediaRepository,
                assignmentRepository, indexingService);

        final MediaLibraryDetails details = service.loadDetails(id);
        final FilenamePreview preview = indexingService.preview(id);

        Assertions.assertAll(
                () -> Assertions.assertTrue(details.exists()),
                () -> Assertions.assertEquals("abc123", details.contentHash()),
                () -> Assertions.assertEquals("1920x1080", details.resolution()),
                () -> Assertions.assertEquals("0:01:05", details.duration()),
                () -> Assertions.assertEquals(preview.parsedFilename().status(),
                        details.parseStatus()),
                () -> Assertions.assertEquals(preview.matchResult().status(),
                        details.matchStatus()),
                () -> Assertions.assertEquals("Assigned Scene",
                        details.scenes().getFirst().title()),
                () -> Assertions.assertEquals("Assigned Movie",
                        details.movies().getFirst().title())
        );

        Files.delete(path);
        Assertions.assertFalse(service.loadDetails(id).exists());

        final UUID invalidId = UUID.randomUUID();
        mediaRepository.insert(new MediaFile(
                invalidId, temporaryDirectory.resolve("bad.mp4"), 1, null,
                null, 0, 0, 0));
        final MediaLibraryDetails invalid = service.loadDetails(invalidId);
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        media.FilenameParseStatus.INVALID,
                        invalid.parseStatus()),
                () -> Assertions.assertEquals(
                        FilenameMatchStatus.INVALID_FILENAME,
                        invalid.matchStatus()),
                () -> Assertions.assertFalse(invalid.warnings().isEmpty())
        );
    }

    @Test
    @DisplayName("Page maps readable values and next-page sentinel")
    void pageMapsReadableValuesAndSentinel() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve("page.db"));
        new SchemaManager(databaseManager).initialize();
        final MediaFileRepository mediaRepository =
                new MediaFileRepository(databaseManager);
        final MediaAssignmentRepository assignmentRepository =
                new MediaAssignmentRepository(databaseManager);
        final MediaFilenameIndexingService indexingService =
                new MediaFilenameIndexingService(
                        mediaRepository, assignmentRepository,
                        new MediaFilenameParser(),
                        new FilenameMetadataMatcher(
                                new EntitySuggestionRepository(databaseManager)));
        mediaRepository.insert(media("a.mp4"));
        mediaRepository.insert(media("b.mp4"));
        final MediaLibraryService service = new MediaLibraryService(
                new MediaLibraryRepository(databaseManager), mediaRepository,
                assignmentRepository, indexingService);
        final MediaLibraryFilter first = new MediaLibraryFilter(
                null, null, null, null, null, null, null, null, 1, 0);

        final MediaLibraryPage page = service.loadPage(first);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, page.rows().size()),
                () -> Assertions.assertTrue(page.hasNextPage()),
                () -> Assertions.assertEquals("Unassigned",
                        page.rows().getFirst().assignmentSummary()),
                () -> Assertions.assertEquals("1.0 KiB",
                        page.rows().getFirst().fileSize())
        );
    }

    private MediaFile media(String name) {
        return new MediaFile(UUID.randomUUID(), temporaryDirectory.resolve(name),
                1024, null, Duration.ofSeconds(1), 640, 480, 1_000);
    }

    private void execute(DatabaseManager databaseManager, String sql)
            throws Exception {
        try (java.sql.Connection connection = databaseManager.openConnection();
             java.sql.PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}

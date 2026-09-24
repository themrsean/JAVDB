package service;

import database.DatabaseManager;
import database.SchemaManager;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class CatalogServiceCliExtensionTest {
    private static final String DATABASE_FILE_NAME =
            "catalog-service-cli-extension-test.db";
    private static final String PUBLISHER_NAME = "Publisher";
    private static final String PERFORMER_NAME = "Performer";
    private static final String SERIES_TITLE = "Series";
    private static final String SCENE_TITLE = "Scene";
    private static final String MOVIE_TITLE = "Movie";
    private static final Path MEDIA_PATH = Path.of("/video/media.mp4");
    private static final long FILE_SIZE = 12_345L;
    private static final long DURATION_MILLIS = 67_890L;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final UUID UNKNOWN_ID =
            UUID.fromString("99999999-aaaa-bbbb-cccc-999999999999");

    @TempDir
    Path temporaryDirectory;

    private CatalogService service;

    @BeforeEach
    void initializeDatabase() throws Exception {
        final DatabaseManager databaseManager = new DatabaseManager(
                temporaryDirectory.resolve(DATABASE_FILE_NAME)
        );
        new SchemaManager(databaseManager).initialize();
        service = new CatalogService(
                new PublisherRepository(databaseManager),
                new PerformerRepository(databaseManager),
                new SeriesRepository(databaseManager),
                new MediaFileRepository(databaseManager),
                new SceneRepository(databaseManager),
                new SearchRepository(databaseManager),
                new MovieRepository(databaseManager)
        );
    }

    @Test
    @DisplayName("Service lists and finds publishers performers and scenes")
    void serviceListsAndFindsPublishersPerformersAndScenes()
            throws Exception {

        final Publisher publisher =
                service.createPublisher(PUBLISHER_NAME, List.of());
        final Performer performer = service.createPerformer(
                PERFORMER_NAME,
                List.of(),
                PerformerCategory.ACTOR
        );
        final Scene scene = service.createScene(
                SCENE_TITLE,
                null,
                LocalDate.of(2024, 1, 1),
                publisher.getId(),
                null,
                null,
                null,
                List.of(performer.getId()),
                List.of()
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(publisher.getId()),
                        service.listPublishers()
                                .stream()
                                .map(Publisher::getId)
                                .toList()
                ),
                () -> Assertions.assertEquals(
                        Optional.of(publisher).map(Publisher::getId),
                        service.findPublisherById(publisher.getId())
                                .map(Publisher::getId)
                ),
                () -> Assertions.assertEquals(
                        List.of(performer.getId()),
                        service.listPerformers()
                                .stream()
                                .map(Performer::getId)
                                .toList()
                ),
                () -> Assertions.assertEquals(
                        Optional.of(performer).map(Performer::getId),
                        service.findPerformerById(performer.getId())
                                .map(Performer::getId)
                ),
                () -> Assertions.assertEquals(
                        List.of(scene.getId()),
                        service.listScenes()
                                .stream()
                                .map(Scene::getId)
                                .toList()
                ),
                () -> Assertions.assertEquals(
                        Optional.of(scene).map(Scene::getId),
                        service.findSceneById(scene.getId())
                                .map(Scene::getId)
                )
        );
    }

    @Test
    @DisplayName("Service creates lists and finds series")
    void serviceCreatesListsAndFindsSeries() throws Exception {
        final Publisher publisher =
                service.createPublisher(PUBLISHER_NAME, List.of());

        final Series series =
                service.createSeries(SERIES_TITLE, publisher.getId());

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(series.getId()),
                        service.listSeries()
                                .stream()
                                .map(Series::getId)
                                .toList()
                ),
                () -> Assertions.assertEquals(
                        Optional.of(series).map(Series::getId),
                        service.findSeriesById(series.getId())
                                .map(Series::getId)
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createSeries(SERIES_TITLE, UNKNOWN_ID)
                )
        );
    }

    @Test
    @DisplayName("Service creates lists and finds media files")
    void serviceCreatesListsAndFindsMediaFiles() throws Exception {
        final MediaFile mediaFile = service.createMediaFile(
                MEDIA_PATH,
                FILE_SIZE,
                Duration.ofMillis(DURATION_MILLIS),
                WIDTH,
                HEIGHT,
                "hash"
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        MEDIA_PATH,
                        mediaFile.getPath()
                ),
                () -> Assertions.assertEquals(
                        List.of(mediaFile.getId()),
                        service.listMediaFiles()
                                .stream()
                                .map(MediaFile::getId)
                                .toList()
                ),
                () -> Assertions.assertEquals(
                        Optional.of(mediaFile).map(MediaFile::getId),
                        service.findMediaFileById(mediaFile.getId())
                                .map(MediaFile::getId)
                )
        );
    }

    @Test
    @DisplayName("Service creates lists and finds movies")
    void serviceCreatesListsAndFindsMovies() throws Exception {
        final Publisher publisher =
                service.createPublisher(PUBLISHER_NAME, List.of());
        final Performer performer = service.createPerformer(
                PERFORMER_NAME,
                List.of(),
                PerformerCategory.ACTOR
        );
        final Scene firstScene = service.createScene(
                "Zulu Scene",
                null,
                null,
                publisher.getId(),
                null,
                null,
                null,
                List.of(performer.getId()),
                List.of()
        );
        final Scene secondScene = service.createScene(
                "Alpha Scene",
                null,
                null,
                publisher.getId(),
                null,
                null,
                null,
                List.of(performer.getId()),
                List.of()
        );
        final MediaFile mediaFile = service.createMediaFile(
                MEDIA_PATH,
                FILE_SIZE,
                null,
                WIDTH,
                HEIGHT,
                null
        );

        final Movie movie = service.createMovie(
                MOVIE_TITLE,
                LocalDate.of(2024, 2, 3),
                publisher.getId(),
                true,
                List.of(firstScene.getId(), secondScene.getId()),
                List.of(mediaFile.getId())
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        List.of(movie.getId()),
                        service.listMovies()
                                .stream()
                                .map(Movie::getId)
                                .toList()
                ),
                () -> Assertions.assertEquals(
                        List.of(firstScene.getId(), secondScene.getId()),
                        service.findMovieById(movie.getId()).orElseThrow()
                                .getScenes()
                                .stream()
                                .map(Scene::getId)
                                .toList()
                ),
                () -> Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> service.createMovie(
                                MOVIE_TITLE,
                                null,
                                UNKNOWN_ID,
                                false,
                                List.of(),
                                List.of()
                        )
                )
        );
    }
}

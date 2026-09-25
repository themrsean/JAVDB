package ui;

import database.DatabaseManager;
import database.SchemaManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import media.FfprobeMediaMetadataProbe;
import media.FileHasher;
import media.MediaFilenameParser;
import media.VideoFileDiscovery;
import repository.EntitySuggestionRepository;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MediaLocationRepository;
import repository.MediaLibraryRepository;
import repository.UnassignedMediaPathRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;
import service.CatalogService;
import service.EntitySuggestionService;
import service.FilenameMetadataMatcher;
import service.GuiReviewQueueService;
import service.MediaFilenameIndexingService;
import service.MediaLocationScanService;
import service.MediaLocationService;
import service.MediaLibraryService;
import service.MediaScanService;
import service.MediaRenameService;
import service.OriginalMovieSelector;
import service.EntityManagementService;
import service.SceneReviewDraftFactory;
import service.SceneReviewRenamePreviewService;
import service.SceneReviewWorkflowService;
import ui.review.DefaultSceneReviewDraftLoader;
import ui.review.JavaFxEntityDialogLauncher;
import ui.review.ReviewNavigationGuard;
import ui.review.ReviewQueueController;
import ui.review.ReviewQueueViewModel;
import ui.review.ScanRefreshCoordinator;
import ui.review.SceneReviewEditorViewModel;
import ui.review.SceneReviewQueueViewModel;
import ui.control.EntityAutocompleteViewModel;
import ui.control.EntitySuggestionDisplay;
import ui.media.JavaFxMediaLocationsWindowLauncher;
import ui.media.MediaLocationsViewModel;
import ui.library.MediaLibraryViewModel;
import ui.performer.JavaFxPerformerCandidateWindowLauncher;
import ui.performer.PerformerCandidateViewModel;
import ui.performer.PerformerCandidateWindowLauncher;
import ui.context.ContextCandidateWindowLauncher;
import ui.context.ContextCandidateViewModel;
import ui.context.JavaFxContextCandidateWindowLauncher;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GuiApplicationFactory {
    private static final String BACKGROUND_THREAD_NAME =
            "javdb-gui-review-loader";
    private static final int AUTOCOMPLETE_LIMIT = 10;

    public GuiApplicationContext create(Path databasePath)
            throws IOException, SQLException {

        final DatabaseManager databaseManager =
                new DatabaseManager(databasePath);
        new SchemaManager(databaseManager).initialize();

        final MediaFileRepository mediaFileRepository =
                new MediaFileRepository(databaseManager);
        final CatalogService catalogService = createCatalogService(
                databaseManager,
                mediaFileRepository
        );
        final MediaFilenameIndexingService indexingService =
                createFilenameIndexingService(
                        databaseManager,
                        mediaFileRepository,
                        catalogService
                );
        final GuiReviewQueueService reviewQueueService =
                new GuiReviewQueueService(
                        indexingService,
                        mediaFileRepository
                );
        final ExecutorService backgroundExecutor =
                Executors.newSingleThreadExecutor(this::backgroundThread);
        final EntityManagementService entityManagementService =
                new EntityManagementService(
                        catalogService,
                        new PublisherRepository(databaseManager),
                        new PerformerRepository(databaseManager)
                );
        final ReviewQueueViewModel viewModel =
                new ReviewQueueViewModel(
                        reviewQueueService,
                        backgroundExecutor,
                        Platform::runLater
                );
        final MediaLibraryViewModel mediaLibraryViewModel =
                new MediaLibraryViewModel(
                        new MediaLibraryService(
                                new MediaLibraryRepository(databaseManager),
                                mediaFileRepository,
                                new MediaAssignmentRepository(databaseManager),
                                indexingService
                        ),
                        backgroundExecutor,
                        Platform::runLater
                );
        final SceneReviewEditorViewModel editorViewModel =
                new SceneReviewEditorViewModel(
                        createSceneReviewWorkflowService(
                                databaseManager,
                                catalogService,
                                mediaFileRepository
                        ),
                        new DefaultSceneReviewDraftLoader(
                                new SceneReviewDraftFactory(
                                        new SceneRepository(databaseManager),
                                        new MovieRepository(databaseManager),
                                        new OriginalMovieSelector(
                                                new MovieRepository(
                                                        databaseManager
                                                )
                                        )
                                )
                        ),
                        this::confirmAliasCreation,
                        new SceneReviewEditorViewModel.AliasPersistence() {
                            @Override
                            public void addPerformerAlias(
                                    java.util.UUID performerId,
                                    String alias) throws SQLException {

                                entityManagementService.addPerformerAlias(
                                        performerId,
                                        alias
                                );
                            }

                            @Override
                            public void addPublisherAlias(
                                    java.util.UUID publisherId,
                                    String alias) throws SQLException {

                                entityManagementService.addPublisherAlias(
                                        publisherId,
                                        alias
                                );
                            }
                        },
                        new SceneReviewRenamePreviewService(
                                catalogService,
                                mediaFileRepository,
                                new OriginalMovieSelector(
                                        new MovieRepository(databaseManager)
                                )
                        )::preview,
                        backgroundExecutor,
                        Platform::runLater
                );
        final SceneReviewQueueViewModel sceneQueueViewModel =
                new SceneReviewQueueViewModel(
                        new service.SceneReviewQueueService(
                                new SceneRepository(databaseManager)
                        )::loadReviewScenes,
                        backgroundExecutor,
                        Platform::runLater
                );
        final ScanRefreshCoordinator scanRefreshCoordinator =
                new ScanRefreshCoordinator(
                        () -> editorViewModel.dirtyProperty().get(),
                        this::confirmDiscardChanges,
                        viewModel::load,
                        mediaLibraryViewModel::load
                );
        final MediaLocationService mediaLocationService =
                new MediaLocationService(
                        new MediaLocationRepository(databaseManager)
                );
        final MediaLocationScanService mediaLocationScanService =
                new MediaLocationScanService(
                        mediaLocationService,
                        new MediaScanService(
                                mediaFileRepository,
                                new VideoFileDiscovery(),
                                new FfprobeMediaMetadataProbe(),
                                new FileHasher()
                        )
                );
        final MediaLocationsViewModel mediaLocationsViewModel =
                new MediaLocationsViewModel(
                        mediaLocationService,
                        mediaLocationScanService,
                        backgroundExecutor,
                        Platform::runLater,
                        scanRefreshCoordinator::requestScanRefresh
                );
        final EntitySuggestionService suggestionService =
                new EntitySuggestionService(
                        new EntitySuggestionRepository(databaseManager)
                );
        final PerformerCandidateWindowLauncher performerCandidateLauncher =
                new JavaFxPerformerCandidateWindowLauncher(
                        new PerformerCandidateViewModel(
                                new service.PerformerCandidateReviewService(
                                        new UnassignedMediaPathRepository(databaseManager),
                                        new MediaFilenameParser(),
                                        new EntitySuggestionRepository(databaseManager),
                                        entityManagementService
                                ),
                                backgroundExecutor,
                                Platform::runLater,
                                scanRefreshCoordinator::requestCatalogRefresh
                        ),
                        autocomplete(
                                (query, limit) -> suggestionService
                                        .suggestPerformers(query, limit).stream()
                                        .map(EntitySuggestionDisplay::from).toList(),
                                backgroundExecutor
                        )
                );
        final ContextCandidateWindowLauncher contextCandidateLauncher =
                new JavaFxContextCandidateWindowLauncher(
                        new ContextCandidateViewModel(
                                new service.ContextCandidateReviewService(
                                        new UnassignedMediaPathRepository(databaseManager),
                                        new MediaFilenameParser(),
                                        new EntitySuggestionRepository(databaseManager),
                                        new PublisherRepository(databaseManager)
                                ),
                                backgroundExecutor,
                                Platform::runLater
                        )
                );
        final ReviewQueueController controller =
                new ReviewQueueController(
                        viewModel,
                        editorViewModel,
                        sceneQueueViewModel,
                        new JavaFxEntityDialogLauncher(
                                entityManagementService,
                                suggestionService,
                                backgroundExecutor
                        ),
                        new ReviewNavigationGuard(this::confirmDiscardChanges),
                        autocomplete(
                                (query, limit) -> suggestionService
                                        .suggestPublishers(query, limit)
                                        .stream()
                                        .map(EntitySuggestionDisplay::from)
                                        .toList(),
                                backgroundExecutor
                        ),
                        autocomplete(
                                (query, limit) -> suggestionService
                                        .suggestSeries(
                                                query,
                                                editorViewModel
                                                        .selectedPublisherIdProperty()
                                                        .get(),
                                                limit
                                        )
                                        .stream()
                                        .map(EntitySuggestionDisplay::from)
                                        .toList(),
                                backgroundExecutor
                        ),
                        autocomplete(
                                (query, limit) -> suggestionService
                                        .suggestMovies(
                                                query,
                                                editorViewModel
                                                        .selectedPublisherIdProperty()
                                                        .get(),
                                                limit
                                        )
                                        .stream()
                                        .map(EntitySuggestionDisplay::from)
                                        .toList(),
                                backgroundExecutor
                        ),
                        autocomplete(
                                (query, limit) -> suggestionService
                                        .suggestPerformers(query, limit)
                                        .stream()
                                        .map(EntitySuggestionDisplay::from)
                                        .toList(),
                                backgroundExecutor
                        ),
                        new JavaFxMediaLocationsWindowLauncher(
                                mediaLocationsViewModel
                        ),
                        scanRefreshCoordinator,
                        mediaLibraryViewModel,
                        performerCandidateLauncher,
                        contextCandidateLauncher,
                        databaseManager.getDatabasePath()
                );

        return new GuiApplicationContext(
                databaseManager.getDatabasePath(),
                controller,
                backgroundExecutor
        );
    }

    private CatalogService createCatalogService(
            DatabaseManager databaseManager,
            MediaFileRepository mediaFileRepository) {

        return new CatalogService(
                new PublisherRepository(databaseManager),
                new PerformerRepository(databaseManager),
                new SeriesRepository(databaseManager),
                mediaFileRepository,
                new SceneRepository(databaseManager),
                new SearchRepository(databaseManager),
                new MovieRepository(databaseManager)
        );
    }

    private MediaFilenameIndexingService createFilenameIndexingService(
            DatabaseManager databaseManager,
            MediaFileRepository mediaFileRepository,
            CatalogService catalogService) {

        final EntitySuggestionRepository suggestionRepository =
                new EntitySuggestionRepository(databaseManager);
        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);

        return new MediaFilenameIndexingService(
                mediaFileRepository,
                new MediaAssignmentRepository(databaseManager),
                new MediaFilenameParser(),
                new FilenameMetadataMatcher(suggestionRepository),
                catalogService,
                sceneRepository,
                movieRepository
        );
    }

    private SceneReviewWorkflowService createSceneReviewWorkflowService(
            DatabaseManager databaseManager,
            CatalogService catalogService,
            MediaFileRepository mediaFileRepository) {

        final SceneRepository sceneRepository =
                new SceneRepository(databaseManager);
        final MovieRepository movieRepository =
                new MovieRepository(databaseManager);
        final MediaAssignmentRepository assignmentRepository =
                new MediaAssignmentRepository(databaseManager);
        final MediaRenameService renameService = new MediaRenameService(
                mediaFileRepository,
                sceneRepository,
                assignmentRepository,
                new OriginalMovieSelector(movieRepository)
        );

        return new SceneReviewWorkflowService(
                catalogService,
                assignmentRepository,
                sceneRepository,
                movieRepository,
                renameService
        );
    }

    private Thread backgroundThread(Runnable runnable) {
        final Thread thread = new Thread(runnable, BACKGROUND_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    }

    private boolean confirmDiscardChanges() {
        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Discard changes?");
        alert.setContentText("Choose Cancel to keep editing.");
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        return alert.showAndWait()
                .filter(ButtonType.OK::equals)
                .isPresent();
    }

    private boolean confirmAliasCreation(
            String candidateText,
            String primaryName) {

        final ButtonType addAlias = new ButtonType(
                "Add Alias",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE
        );
        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Add Alias");
        alert.setHeaderText(
                "Add \"" + candidateText + "\" as an alias for \""
                        + primaryName + "\"?"
        );
        alert.setContentText("Choose No to use this mapping only here.");
        alert.getButtonTypes().setAll(addAlias, ButtonType.CANCEL);

        return alert.showAndWait()
                .filter(addAlias::equals)
                .isPresent();
    }

    private EntityAutocompleteViewModel autocomplete(
            EntityAutocompleteViewModel.SuggestionProvider provider,
            ExecutorService backgroundExecutor) {

        return new EntityAutocompleteViewModel(
                provider,
                AUTOCOMPLETE_LIMIT,
                backgroundExecutor,
                Platform::runLater
        );
    }
}

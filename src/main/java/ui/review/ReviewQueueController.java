package ui.review;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Movie;
import model.Performer;
import model.Publisher;
import model.Series;
import service.EntityMatch;
import service.FilenameInterpretation;
import service.FilenameMatchStatus;
import service.ReviewDetails;
import service.ReviewMatchStatusFilter;
import service.ReviewQueueItem;
import service.ReadyPageBatchMode;
import service.ReadyPageBatchPreflight;
import service.ReadyPageBatchResult;
import service.SceneReviewSaveResult;
import service.SceneReviewSaveStatus;
import service.SceneReviewQueueItem;
import ui.control.EntityAutocompleteViewModel;
import ui.control.EntitySuggestionDisplay;
import ui.media.MediaLocationsWindowLauncher;
import ui.library.MediaLibraryViewModel;
import repository.MediaAssignmentReference;
import repository.MediaLibraryAssignmentState;
import repository.MediaLibraryMetadataQuality;
import service.MediaLibraryDetails;
import service.MediaLibraryRow;
import ui.performer.PerformerCandidateWindowLauncher;
import ui.context.ContextCandidateWindowLauncher;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ReviewQueueController {
    private static final int PAGE_SIZE_SMALL = 25;
    private static final int PAGE_SIZE_MEDIUM = 50;
    private static final int PAGE_SIZE_LARGE = 100;
    private static final int PAGE_SIZE_EXTRA_LARGE = 250;

    private final ReviewQueueViewModel viewModel;
    private final SceneReviewEditorViewModel editorViewModel;
    private final SceneReviewQueueViewModel sceneQueueViewModel;
    private final ReadyPageBatchViewModel readyPageBatchViewModel;
    private final EntityDialogLauncher entityDialogLauncher;
    private final ReviewNavigationGuard navigationGuard;
    private final EntityAutocompleteViewModel publisherAutocomplete;
    private final EntityAutocompleteViewModel seriesAutocomplete;
    private final EntityAutocompleteViewModel movieAutocomplete;
    private final EntityAutocompleteViewModel performerAutocomplete;
    private final MediaLocationsWindowLauncher mediaLocationsWindowLauncher;
    private final ScanRefreshCoordinator scanRefreshCoordinator;
    private final MediaLibraryViewModel mediaLibraryViewModel;
    private final PerformerCandidateWindowLauncher performerCandidateWindowLauncher;
    private final ContextCandidateWindowLauncher contextCandidateWindowLauncher;
    private final Path databasePath;
    private boolean restoringSelection;

    @FXML
    private TextField pathFilterField;
    @FXML
    private TextField directoryFilterField;
    @FXML
    private TextField widthFilterField;
    @FXML
    private TextField heightFilterField;
    @FXML
    private TextField minWidthFilterField;
    @FXML
    private TextField minHeightFilterField;
    @FXML
    private Button applyFiltersButton;
    @FXML
    private Button clearFiltersButton;
    @FXML
    private ComboBox<ReviewMatchStatusFilter> statusFilterComboBox;
    @FXML
    private Button previousButton;
    @FXML
    private Button nextButton;
    @FXML
    private ComboBox<Integer> pageSizeComboBox;
    @FXML
    private Button refreshButton;
    @FXML
    private Button processReadyPageButton;
    @FXML
    private Label readyPageBatchStatusLabel;
    @FXML
    private ListView<String> readyPageBatchResultsList;
    @FXML
    private VBox unassignedMediaPane;
    @FXML
    private TableView<ReviewQueueItem> queueTable;
    @FXML
    private TableColumn<ReviewQueueItem, String> filenameColumn;
    @FXML
    private TableColumn<ReviewQueueItem, String> matchStatusColumn;
    @FXML
    private TableColumn<ReviewQueueItem, String> titleColumn;
    @FXML
    private TableColumn<ReviewQueueItem, String> publisherColumn;
    @FXML
    private TableColumn<ReviewQueueItem, String> seriesColumn;
    @FXML
    private TableColumn<ReviewQueueItem, String> movieColumn;
    @FXML
    private TableColumn<ReviewQueueItem, String> performersColumn;
    @FXML
    private TableColumn<ReviewQueueItem, String> resolutionColumn;
    @FXML
    private TableColumn<ReviewQueueItem, String> durationColumn;
    @FXML
    private TableColumn<ReviewQueueItem, Integer> warningCountColumn;
    @FXML
    private Label detailsPlaceholderLabel;
    @FXML
    private Label mediaIdLabel;
    @FXML
    private Label fullPathLabel;
    @FXML
    private Label parseStatusLabel;
    @FXML
    private Label matchStatusLabel;
    @FXML
    private Label canonicalStatusLabel;
    @FXML
    private Label proposedFilenameLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private ProgressIndicator loadingIndicator;
    @FXML
    private Label databasePathLabel;
    @FXML
    private MenuItem mediaLocationsMenuItem;
    @FXML
    private MenuItem scanAllMediaLocationsMenuItem;
    @FXML
    private MenuItem performerCandidatesMenuItem;
    @FXML
    private MenuItem contextCandidatesMenuItem;
    @FXML
    private Button scanMediaButton;
    @FXML
    private HBox scanResultsNotificationBox;
    @FXML
    private Label scanResultsNotificationLabel;
    @FXML
    private Button refreshMediaQueueButton;
    @FXML
    private TabPane reviewTabs;
    @FXML
    private TextField titleEditorField;
    @FXML
    private TextField releaseDateEditorField;
    @FXML
    private TextField codeEditorField;
    @FXML
    private TextField seasonEditorField;
    @FXML
    private TextField episodeEditorField;
    @FXML
    private TextField publisherSearchField;
    @FXML
    private ListView<EntitySuggestionDisplay> publisherSuggestionsList;
    @FXML
    private TextField seriesSearchField;
    @FXML
    private ListView<EntitySuggestionDisplay> seriesSuggestionsList;
    @FXML
    private TextField movieSearchField;
    @FXML
    private ListView<EntitySuggestionDisplay> movieSuggestionsList;
    @FXML
    private TextField performerSearchField;
    @FXML
    private ListView<EntitySuggestionDisplay> performerSuggestionsList;
    @FXML
    private ListView<String> selectedPerformersList;
    @FXML
    private ListView<String> alternativesList;
    @FXML
    private Button applyInterpretationButton;
    @FXML
    private Button createPublisherButton;
    @FXML
    private Button createSeriesButton;
    @FXML
    private Button createMovieButton;
    @FXML
    private Button createPerformerButton;
    @FXML
    private Button saveWithoutRenameButton;
    @FXML
    private Button saveAndRenameButton;
    @FXML
    private Button saveNeedsReviewButton;
    @FXML
    private Button skipButton;
    @FXML
    private Button resetChangesButton;
    @FXML
    private Label editorValidationLabel;
    @FXML
    private Label editorResultLabel;
    @FXML
    private TableView<SceneReviewQueueItem> unverifiedScenesTable;
    @FXML
    private TableColumn<SceneReviewQueueItem, String> sceneTitleColumn;
    @FXML
    private TableColumn<SceneReviewQueueItem, String> sceneVerificationStatusColumn;
    @FXML
    private Tab mediaLibraryTab;
    @FXML
    private VBox reviewDetailsPane;
    @FXML
    private VBox mediaLibraryDetailsPane;
    @FXML
    private TextField libraryContainsField;
    @FXML
    private TextField libraryDirectoryField;
    @FXML
    private TextField libraryWidthField;
    @FXML
    private TextField libraryHeightField;
    @FXML
    private TextField libraryMinWidthField;
    @FXML
    private TextField libraryMinHeightField;
    @FXML
    private ComboBox<MediaLibraryAssignmentState> libraryAssignmentComboBox;
    @FXML
    private ComboBox<MediaLibraryMetadataQuality> libraryQualityComboBox;
    @FXML
    private ComboBox<Integer> libraryPageSizeComboBox;
    @FXML
    private Button libraryPreviousButton;
    @FXML
    private Button libraryNextButton;
    @FXML
    private Button libraryApplyButton;
    @FXML
    private Button libraryClearButton;
    @FXML
    private Button libraryRefreshButton;
    @FXML
    private TableView<MediaLibraryRow> mediaLibraryTable;
    @FXML
    private TableColumn<MediaLibraryRow, String> libraryFilenameColumn;
    @FXML
    private TableColumn<MediaLibraryRow, String> libraryDirectoryColumn;
    @FXML
    private TableColumn<MediaLibraryRow, String> libraryResolutionColumn;
    @FXML
    private TableColumn<MediaLibraryRow, String> libraryDurationColumn;
    @FXML
    private TableColumn<MediaLibraryRow, String> libraryFileSizeColumn;
    @FXML
    private TableColumn<MediaLibraryRow, String> libraryModifiedColumn;
    @FXML
    private TableColumn<MediaLibraryRow, String> libraryAssignmentColumn;
    @FXML
    private Label libraryEmptyLabel;
    @FXML
    private Label libraryErrorLabel;
    @FXML
    private Label libraryDetailErrorLabel;
    @FXML
    private Label libraryMediaIdLabel;
    @FXML
    private Label libraryFullPathLabel;
    @FXML
    private Label libraryExistsLabel;
    @FXML
    private Label libraryFileSizeLabel;
    @FXML
    private Label libraryModifiedLabel;
    @FXML
    private Label libraryResolutionLabel;
    @FXML
    private Label libraryDurationLabel;
    @FXML
    private Label libraryHashLabel;
    @FXML
    private Label libraryParseStatusLabel;
    @FXML
    private Label libraryMatchStatusLabel;
    @FXML
    private Label libraryInterpretationLabel;
    @FXML
    private ListView<String> librarySceneAssignmentsList;
    @FXML
    private ListView<String> libraryMovieAssignmentsList;
    @FXML
    private ListView<String> libraryWarningsList;

    public ReviewQueueController(
            ReviewQueueViewModel viewModel,
            SceneReviewEditorViewModel editorViewModel,
            SceneReviewQueueViewModel sceneQueueViewModel,
            ReadyPageBatchViewModel readyPageBatchViewModel,
            EntityDialogLauncher entityDialogLauncher,
            ReviewNavigationGuard navigationGuard,
            EntityAutocompleteViewModel publisherAutocomplete,
            EntityAutocompleteViewModel seriesAutocomplete,
            EntityAutocompleteViewModel movieAutocomplete,
            EntityAutocompleteViewModel performerAutocomplete,
            MediaLocationsWindowLauncher mediaLocationsWindowLauncher,
            ScanRefreshCoordinator scanRefreshCoordinator,
            MediaLibraryViewModel mediaLibraryViewModel,
            PerformerCandidateWindowLauncher performerCandidateWindowLauncher,
            ContextCandidateWindowLauncher contextCandidateWindowLauncher,
            Path databasePath) {

        this.viewModel = Objects.requireNonNull(
                viewModel,
                "Review queue view model must not be null"
        );
        this.editorViewModel = Objects.requireNonNull(
                editorViewModel,
                "Scene review editor view model must not be null"
        );
        this.sceneQueueViewModel = Objects.requireNonNull(
                sceneQueueViewModel,
                "Scene review queue view model must not be null"
        );
        this.readyPageBatchViewModel = Objects.requireNonNull(
                readyPageBatchViewModel,
                "READY-page batch view model must not be null"
        );
        this.entityDialogLauncher = Objects.requireNonNull(
                entityDialogLauncher,
                "Entity dialog launcher must not be null"
        );
        this.navigationGuard = Objects.requireNonNull(
                navigationGuard,
                "Review navigation guard must not be null"
        );
        this.publisherAutocomplete = Objects.requireNonNull(
                publisherAutocomplete,
                "Publisher autocomplete must not be null"
        );
        this.seriesAutocomplete = Objects.requireNonNull(
                seriesAutocomplete,
                "Series autocomplete must not be null"
        );
        this.movieAutocomplete = Objects.requireNonNull(
                movieAutocomplete,
                "Movie autocomplete must not be null"
        );
        this.performerAutocomplete = Objects.requireNonNull(
                performerAutocomplete,
                "Performer autocomplete must not be null"
        );
        this.mediaLocationsWindowLauncher = Objects.requireNonNull(
                mediaLocationsWindowLauncher,
                "Media locations window launcher must not be null"
        );
        this.scanRefreshCoordinator = Objects.requireNonNull(
                scanRefreshCoordinator,
                "Scan refresh coordinator must not be null"
        );
        this.mediaLibraryViewModel = Objects.requireNonNull(
                mediaLibraryViewModel,
                "Media library view model must not be null"
        );
        this.performerCandidateWindowLauncher = Objects.requireNonNull(
                performerCandidateWindowLauncher,
                "Performer candidate window launcher must not be null"
        );
        this.contextCandidateWindowLauncher = Objects.requireNonNull(
                contextCandidateWindowLauncher,
                "Context candidate window launcher must not be null"
        );
        this.databasePath = Objects.requireNonNull(
                databasePath,
                "Database path must not be null"
        );
    }

    @FXML
    private void initialize() {
        configureTable();
        bindFilters();
        bindControls();
        bindDetails();
        bindEditor();
        bindAutocomplete();
        configureMediaLibrary();
        installKeyboardShortcutsWhenReady();
    }

    public void loadInitialPage() {
        viewModel.load();
        sceneQueueViewModel.load();
        mediaLibraryViewModel.load();
    }

    public void dispose() {
        viewModel.dispose();
        sceneQueueViewModel.dispose();
        readyPageBatchViewModel.dispose();
        mediaLibraryViewModel.dispose();
        publisherAutocomplete.dispose();
        seriesAutocomplete.dispose();
        movieAutocomplete.dispose();
        performerAutocomplete.dispose();
    }

    public boolean mayClose() {
        return navigationGuard.mayNavigateAway(
                editorViewModel.dirtyProperty().get()
        );
    }

    private void configureTable() {
        filenameColumn.setCellValueFactory(
                new PropertyValueFactory<>("filename")
        );
        matchStatusColumn.setCellValueFactory(
                new PropertyValueFactory<>("matchStatus")
        );
        titleColumn.setCellValueFactory(
                new PropertyValueFactory<>("proposedTitle")
        );
        publisherColumn.setCellValueFactory(
                new PropertyValueFactory<>("publisherName")
        );
        seriesColumn.setCellValueFactory(
                new PropertyValueFactory<>("seriesTitle")
        );
        movieColumn.setCellValueFactory(
                new PropertyValueFactory<>("movieTitle")
        );
        performersColumn.setCellValueFactory(
                new PropertyValueFactory<>("performers")
        );
        resolutionColumn.setCellValueFactory(
                new PropertyValueFactory<>("resolution")
        );
        durationColumn.setCellValueFactory(
                new PropertyValueFactory<>("duration")
        );
        warningCountColumn.setCellValueFactory(
                new PropertyValueFactory<>("warningCount")
        );
        sceneTitleColumn.setCellValueFactory(
                new PropertyValueFactory<>("title")
        );
        sceneVerificationStatusColumn.setCellValueFactory(
                new PropertyValueFactory<>("verificationStatus")
        );
        queueTable.setItems(viewModel.rows());
        unverifiedScenesTable.setItems(sceneQueueViewModel.rows());
        queueTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) ->
                        selectUnassignedRow(oldValue, newValue));
        viewModel.selectedRow().addListener((observable, oldValue, newValue) ->
                queueTable.getSelectionModel().select(newValue));
        viewModel.selectedDetails().addListener((observable, oldValue, newValue) ->
                loadEditorDraft(newValue));
        unverifiedScenesTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) ->
                        selectSceneRow(oldValue, newValue));
    }

    private void bindFilters() {
        pathFilterField.textProperty()
                .bindBidirectional(viewModel.filenameFilter());
        directoryFilterField.textProperty()
                .bindBidirectional(viewModel.directoryFilter());
        widthFilterField.textProperty()
                .bindBidirectional(viewModel.widthFilter());
        heightFilterField.textProperty()
                .bindBidirectional(viewModel.heightFilter());
        minWidthFilterField.textProperty()
                .bindBidirectional(viewModel.minWidthFilter());
        minHeightFilterField.textProperty()
                .bindBidirectional(viewModel.minHeightFilter());
        statusFilterComboBox.getItems()
                .setAll(ReviewMatchStatusFilter.values());
        statusFilterComboBox.valueProperty()
                .bindBidirectional(viewModel.statusFilter());
        pageSizeComboBox.getItems().setAll(
                PAGE_SIZE_SMALL,
                PAGE_SIZE_MEDIUM,
                PAGE_SIZE_LARGE,
                PAGE_SIZE_EXTRA_LARGE
        );
        pageSizeComboBox.setValue(viewModel.pageSize().get());
        pageSizeComboBox.valueProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        viewModel.pageSize().set(newValue);
                    }
                });
        viewModel.pageSize().addListener((observable, oldValue, newValue) ->
                pageSizeComboBox.setValue(newValue.intValue()));
    }

    private void bindControls() {
        databasePathLabel.setText(databasePath.toString());
        applyFiltersButton.setOnAction(event ->
                guardedNavigation(viewModel::applyFilters));
        clearFiltersButton.setOnAction(event ->
                guardedNavigation(viewModel::clearFilters));
        refreshButton.setOnAction(event -> guardedNavigation(viewModel::load));
        processReadyPageButton.setOnAction(event -> startReadyPageBatch());
        processReadyPageButton.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> viewModel.loadingProperty().get()
                                || readyPageBatchViewModel.busyProperty().get()
                                || viewModel.rows().stream().noneMatch(row ->
                                row.matchStatus() == FilenameMatchStatus.READY),
                        viewModel.loadingProperty(),
                        readyPageBatchViewModel.busyProperty(),
                        viewModel.rows()
                )
        );
        readyPageBatchStatusLabel.textProperty().bind(
                readyPageBatchViewModel.statusMessageProperty()
        );
        readyPageBatchResultsList.setItems(
                readyPageBatchViewModel.rowResults()
        );
        readyPageBatchResultsList.visibleProperty().bind(
                Bindings.isNotEmpty(readyPageBatchViewModel.rowResults())
        );
        readyPageBatchResultsList.managedProperty().bind(
                readyPageBatchResultsList.visibleProperty()
        );
        unassignedMediaPane.disableProperty().bind(
                readyPageBatchViewModel.busyProperty()
        );
        reviewDetailsPane.disableProperty().bind(
                readyPageBatchViewModel.busyProperty()
        );
        readyPageBatchViewModel.pendingPreflightProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        confirmReadyPageBatch(newValue);
                    }
                }
        );
        readyPageBatchViewModel.lastResultProperty().addListener(
                (observable, oldValue, newValue) -> showBatchOutcome(newValue)
        );
        mediaLocationsMenuItem.setOnAction(event ->
                openMediaLocationsWindow());
        scanAllMediaLocationsMenuItem.setOnAction(event ->
                openMediaLocationsWindow());
        performerCandidatesMenuItem.setOnAction(event ->
                performerCandidateWindowLauncher.open(ownerWindow()));
        contextCandidatesMenuItem.setOnAction(event ->
                contextCandidateWindowLauncher.open(ownerWindow()));
        scanMediaButton.setOnAction(event -> openMediaLocationsWindow());
        refreshMediaQueueButton.setOnAction(event ->
                scanRefreshCoordinator.refreshPendingScanResults());
        scanResultsNotificationBox.visibleProperty().bind(
                scanRefreshCoordinator.pendingScanResultsProperty()
        );
        scanResultsNotificationBox.managedProperty().bind(
                scanResultsNotificationBox.visibleProperty()
        );
        scanResultsNotificationLabel.textProperty().bind(
                scanRefreshCoordinator.pendingScanResultsMessageProperty()
        );
        previousButton.setOnAction(event ->
                guardedNavigation(viewModel::previousPage));
        nextButton.setOnAction(event ->
                guardedNavigation(viewModel::nextPage));
        previousButton.disableProperty().bind(
                viewModel.previousAvailable().not()
                        .or(viewModel.loadingProperty())
        );
        nextButton.disableProperty().bind(
                viewModel.nextAvailable().not()
                        .or(viewModel.loadingProperty())
        );
        loadingIndicator.visibleProperty()
                .bind(viewModel.loadingProperty());
        errorLabel.textProperty()
                .bind(viewModel.errorMessageProperty());
    }

    private void bindDetails() {
        detailsPlaceholderLabel.visibleProperty().bind(
                viewModel.selectedDetails().isNull()
        );
        mediaIdLabel.textProperty().bind(Bindings.createStringBinding(
                () -> detailsText(""),
                viewModel.selectedDetails()
        ));
        fullPathLabel.textProperty().bind(Bindings.createStringBinding(
                () -> detailsText("path"),
                viewModel.selectedDetails()
        ));
        parseStatusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> detailsText("parse"),
                viewModel.selectedDetails()
        ));
        matchStatusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> detailsText("match"),
                viewModel.selectedDetails()
        ));
        canonicalStatusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> editorViewModel.previewStatusProperty().get(),
                editorViewModel.previewStatusProperty()
        ));
        proposedFilenameLabel.textProperty().bind(Bindings.createStringBinding(
                () -> editorViewModel.previewProposedFilenameProperty().get(),
                editorViewModel.previewProposedFilenameProperty()
        ));
    }

    private void bindEditor() {
        titleEditorField.textProperty()
                .bindBidirectional(editorViewModel.titleProperty());
        releaseDateEditorField.textProperty()
                .bindBidirectional(editorViewModel.releaseDateTextProperty());
        codeEditorField.textProperty()
                .bindBidirectional(editorViewModel.codeProperty());
        seasonEditorField.textProperty()
                .bindBidirectional(editorViewModel.seasonProperty());
        episodeEditorField.textProperty()
                .bindBidirectional(editorViewModel.episodeProperty());
        saveWithoutRenameButton.disableProperty()
                .bind(editorViewModel.saveEnabledProperty().not());
        saveAndRenameButton.disableProperty()
                .bind(editorViewModel.saveAndRenameEnabledProperty().not());
        saveNeedsReviewButton.disableProperty()
                .bind(editorViewModel.saveEnabledProperty().not());
        editorValidationLabel.textProperty()
                .bind(editorViewModel.validationMessageProperty());
        editorResultLabel.textProperty()
                .bind(editorViewModel.saveMessageProperty());
        saveWithoutRenameButton.setOnAction(event ->
                editorViewModel.saveWithoutRename());
        saveAndRenameButton.setOnAction(event ->
                editorViewModel.saveAndRename());
        saveNeedsReviewButton.setOnAction(event ->
                editorViewModel.saveAsNeedsReview());
        resetChangesButton.setOnAction(event -> editorViewModel.resetChanges());
        skipButton.setOnAction(event -> selectNextVisibleRow());
        createPublisherButton.setOnAction(event -> createPublisher());
        createSeriesButton.setOnAction(event -> createSeries());
        createMovieButton.setOnAction(event -> createMovie());
        createPerformerButton.setOnAction(event -> createPerformer());
        applyInterpretationButton.setOnAction(event -> applyInterpretation());
        editorViewModel.lastSaveResultProperty().addListener(
                (observable, oldValue, newValue) -> handleSaveResult(newValue)
        );
    }

    private void bindAutocomplete() {
        bindAutocomplete(
                publisherSearchField,
                publisherSuggestionsList,
                publisherAutocomplete,
                this::selectPublisherSuggestion
        );
        bindAutocomplete(
                seriesSearchField,
                seriesSuggestionsList,
                seriesAutocomplete,
                this::selectSeriesSuggestion
        );
        bindAutocomplete(
                movieSearchField,
                movieSuggestionsList,
                movieAutocomplete,
                this::selectMovieSuggestion
        );
        bindAutocomplete(
                performerSearchField,
                performerSuggestionsList,
                performerAutocomplete,
                this::selectPerformerSuggestion
        );
    }

    private void configureMediaLibrary() {
        libraryFilenameColumn.setCellValueFactory(
                new PropertyValueFactory<>("filename"));
        libraryDirectoryColumn.setCellValueFactory(
                new PropertyValueFactory<>("directory"));
        libraryResolutionColumn.setCellValueFactory(
                new PropertyValueFactory<>("resolution"));
        libraryDurationColumn.setCellValueFactory(
                new PropertyValueFactory<>("duration"));
        libraryFileSizeColumn.setCellValueFactory(
                new PropertyValueFactory<>("fileSize"));
        libraryModifiedColumn.setCellValueFactory(
                new PropertyValueFactory<>("lastModified"));
        libraryAssignmentColumn.setCellValueFactory(
                new PropertyValueFactory<>("assignmentSummary"));
        mediaLibraryTable.setItems(mediaLibraryViewModel.rows());
        mediaLibraryTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) ->
                        mediaLibraryViewModel.selectedRow().set(newValue));
        mediaLibraryViewModel.selectedRow().addListener(
                (observable, oldValue, newValue) ->
                        mediaLibraryTable.getSelectionModel().select(newValue));

        libraryContainsField.textProperty().bindBidirectional(
                mediaLibraryViewModel.containsProperty());
        libraryDirectoryField.textProperty().bindBidirectional(
                mediaLibraryViewModel.directoryProperty());
        libraryWidthField.textProperty().bindBidirectional(
                mediaLibraryViewModel.widthProperty());
        libraryHeightField.textProperty().bindBidirectional(
                mediaLibraryViewModel.heightProperty());
        libraryMinWidthField.textProperty().bindBidirectional(
                mediaLibraryViewModel.minWidthProperty());
        libraryMinHeightField.textProperty().bindBidirectional(
                mediaLibraryViewModel.minHeightProperty());
        libraryAssignmentComboBox.getItems().setAll(
                MediaLibraryAssignmentState.values());
        libraryAssignmentComboBox.valueProperty().bindBidirectional(
                mediaLibraryViewModel.assignmentProperty());
        libraryQualityComboBox.getItems().setAll(
                MediaLibraryMetadataQuality.values());
        libraryQualityComboBox.valueProperty().bindBidirectional(
                mediaLibraryViewModel.qualityProperty());
        libraryPageSizeComboBox.getItems().setAll(
                PAGE_SIZE_SMALL, PAGE_SIZE_MEDIUM,
                PAGE_SIZE_LARGE, PAGE_SIZE_EXTRA_LARGE);
        libraryPageSizeComboBox.valueProperty().bindBidirectional(
                mediaLibraryViewModel.pageSizeProperty().asObject());

        libraryApplyButton.setOnAction(event -> libraryAction(
                mediaLibraryViewModel::applyFilters));
        libraryClearButton.setOnAction(event -> libraryAction(
                mediaLibraryViewModel::clearFilters));
        libraryRefreshButton.setOnAction(event -> libraryAction(
                mediaLibraryViewModel::load));
        libraryPreviousButton.setOnAction(event ->
                mediaLibraryViewModel.previousPage());
        libraryNextButton.setOnAction(event ->
                mediaLibraryViewModel.nextPage());
        libraryPreviousButton.disableProperty().bind(
                mediaLibraryViewModel.previousAvailableProperty().not()
                        .or(mediaLibraryViewModel.loadingProperty()));
        libraryNextButton.disableProperty().bind(
                mediaLibraryViewModel.nextAvailableProperty().not()
                        .or(mediaLibraryViewModel.loadingProperty()));
        libraryErrorLabel.textProperty().bind(
                mediaLibraryViewModel.errorMessageProperty());
        libraryEmptyLabel.textProperty().bind(
                mediaLibraryViewModel.emptyMessageProperty());
        libraryDetailErrorLabel.textProperty().bind(
                mediaLibraryViewModel.detailErrorMessageProperty());

        bindMediaLibraryDetails();
        updateDetailsPane(reviewTabs.getSelectionModel().getSelectedItem());
        reviewTabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> updateDetailsPane(newValue));
    }

    private void libraryAction(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            mediaLibraryViewModel.errorMessageProperty().set(
                    "Dimension filters must be positive whole numbers."
            );
        }
    }

    private void bindMediaLibraryDetails() {
        libraryMediaIdLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("id"),
                mediaLibraryViewModel.selectedDetails()));
        libraryFullPathLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("path"),
                mediaLibraryViewModel.selectedDetails()));
        libraryExistsLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("exists"),
                mediaLibraryViewModel.selectedDetails()));
        libraryFileSizeLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("size"),
                mediaLibraryViewModel.selectedDetails()));
        libraryModifiedLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("modified"),
                mediaLibraryViewModel.selectedDetails()));
        libraryResolutionLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("resolution"),
                mediaLibraryViewModel.selectedDetails()));
        libraryDurationLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("duration"),
                mediaLibraryViewModel.selectedDetails()));
        libraryHashLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("hash"),
                mediaLibraryViewModel.selectedDetails()));
        libraryParseStatusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("parse"),
                mediaLibraryViewModel.selectedDetails()));
        libraryMatchStatusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> libraryDetailText("match"),
                mediaLibraryViewModel.selectedDetails()));
        libraryInterpretationLabel.textProperty().bind(
                Bindings.createStringBinding(
                        () -> libraryDetailText("interpretation"),
                        mediaLibraryViewModel.selectedDetails()));
        mediaLibraryViewModel.selectedDetails().addListener(
                (observable, oldValue, newValue) -> {
                    librarySceneAssignmentsList.getItems().setAll(
                            assignmentText(newValue == null
                                    ? java.util.List.of() : newValue.scenes()));
                    libraryMovieAssignmentsList.getItems().setAll(
                            assignmentText(newValue == null
                                    ? java.util.List.of() : newValue.movies()));
                    libraryWarningsList.getItems().setAll(newValue == null
                            ? java.util.List.of() : newValue.warnings());
                });
    }

    private java.util.List<String> assignmentText(
            java.util.List<MediaAssignmentReference> references) {
        return references.isEmpty()
                ? java.util.List.of("None")
                : references.stream()
                        .map(reference -> reference.title() + " — "
                                + reference.id())
                        .toList();
    }

    private String libraryDetailText(String field) {
        final MediaLibraryDetails details =
                mediaLibraryViewModel.selectedDetails().get();
        if (details == null) {
            return "";
        }
        return switch (field) {
            case "id" -> details.mediaId().toString();
            case "path" -> details.path().toString();
            case "exists" -> details.exists() ? "Present" : "Missing from disk";
            case "size" -> details.fileSize();
            case "modified" -> details.lastModified();
            case "resolution" -> details.resolution();
            case "duration" -> details.duration();
            case "hash" -> details.contentHash().isBlank()
                    ? "Not stored" : details.contentHash();
            case "parse" -> details.parseStatus().toString();
            case "match" -> details.matchStatus().toString();
            case "interpretation" -> details.bestInterpretation();
            default -> "";
        };
    }

    private void updateDetailsPane(Tab selectedTab) {
        final boolean librarySelected = mediaLibraryTab.equals(selectedTab);
        reviewDetailsPane.setVisible(!librarySelected);
        reviewDetailsPane.setManaged(!librarySelected);
        mediaLibraryDetailsPane.setVisible(librarySelected);
        mediaLibraryDetailsPane.setManaged(librarySelected);
    }

    private void installKeyboardShortcutsWhenReady() {
        titleEditorField.sceneProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        installKeyboardShortcuts(newValue);
                    }
                });
    }

    private void installKeyboardShortcuts(javafx.scene.Scene scene) {
        scene.getAccelerators().put(
                ReviewKeyboardShortcuts.SAVE_WITHOUT_RENAME,
                () -> {
                    if (!saveWithoutRenameButton.isDisabled()) {
                        editorViewModel.saveWithoutRename();
                    }
                }
        );
        scene.getAccelerators().put(
                ReviewKeyboardShortcuts.SAVE_AND_RENAME,
                () -> {
                    if (!saveAndRenameButton.isDisabled()) {
                        editorViewModel.saveAndRename();
                    }
                }
        );
        scene.getAccelerators().put(
                ReviewKeyboardShortcuts.SAVE_AS_NEEDS_REVIEW,
                () -> {
                    if (!saveNeedsReviewButton.isDisabled()) {
                        editorViewModel.saveAsNeedsReview();
                    }
                }
        );
        scene.getAccelerators().put(
                ReviewKeyboardShortcuts.NEXT_ITEM,
                this::selectNextVisibleRow
        );
        scene.getAccelerators().put(
                ReviewKeyboardShortcuts.PREVIOUS_ITEM,
                this::selectPreviousVisibleRow
        );
    }

    private void bindAutocomplete(
            TextField searchField,
            ListView<EntitySuggestionDisplay> suggestionsList,
            EntityAutocompleteViewModel autocomplete,
            java.util.function.Consumer<EntitySuggestionDisplay> selection) {

        searchField.textProperty()
                .bindBidirectional(autocomplete.searchTextProperty());
        suggestionsList.setItems(autocomplete.suggestions());
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> autocomplete.search()
        );
        suggestionsList.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        autocomplete.select(newValue);
                        selection.accept(newValue);
                    }
                });
    }

    private void selectUnassignedRow(
            ReviewQueueItem oldValue,
            ReviewQueueItem newValue) {

        if (!restoringSelection) {
            if (navigationGuard.mayNavigateAway(
                    editorViewModel.dirtyProperty().get()
            )) {
                viewModel.selectedRow().set(newValue);
            } else {
                restoreUnassignedSelection(oldValue);
            }
        }
    }

    private void selectSceneRow(
            SceneReviewQueueItem oldValue,
            SceneReviewQueueItem newValue) {

        if (!restoringSelection) {
            if (navigationGuard.mayNavigateAway(
                    editorViewModel.dirtyProperty().get()
            )) {
                loadExistingSceneDraft(newValue);
            } else {
                restoreSceneSelection(oldValue);
            }
        }
    }

    private void guardedNavigation(Runnable action) {
        if (navigationGuard.mayNavigateAway(
                editorViewModel.dirtyProperty().get()
        )) {
            action.run();
        }
    }

    private void startReadyPageBatch() {
        final List<ReviewQueueItem> pageSnapshot =
                List.copyOf(viewModel.rows());

        if (navigationGuard.mayNavigateAway(
                editorViewModel.dirtyProperty().get()
        )) {
            readyPageBatchViewModel.startPreflight(pageSnapshot);
        }
    }

    private void confirmReadyPageBatch(ReadyPageBatchPreflight preflight) {
        final ButtonType withoutRename = new ButtonType(
                "Create Without Renaming",
                ButtonBar.ButtonData.OTHER
        );
        final ButtonType withRename = new ButtonType(
                "Create and Rename",
                ButtonBar.ButtonData.OTHER
        );
        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(ownerWindow());
        alert.setTitle("Process Current-Page READY Rows");
        alert.setHeaderText("Choose how to create Scenes for "
                + preflight.eligibleCount() + " eligible rows.");
        alert.setContentText(preflightText(preflight));
        alert.getButtonTypes().setAll(
                withoutRename,
                withRename,
                ButtonType.CANCEL
        );
        final Button cancelButton = (Button) alert.getDialogPane()
                .lookupButton(ButtonType.CANCEL);
        cancelButton.setDefaultButton(true);

        final ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);

        if (choice.equals(withoutRename)) {
            editorViewModel.resetChanges();
            readyPageBatchViewModel.execute(
                    ReadyPageBatchMode.CREATE_WITHOUT_RENAMING
            );
        } else if (choice.equals(withRename)) {
            editorViewModel.resetChanges();
            readyPageBatchViewModel.execute(
                    ReadyPageBatchMode.CREATE_AND_RENAME
            );
        } else {
            readyPageBatchViewModel.cancel();
        }
    }

    private String preflightText(ReadyPageBatchPreflight preflight) {
        return "Only READY rows captured from the currently displayed page "
                + "are affected.\n\nDisplayed rows considered: "
                + preflight.displayedRows()
                + "\nDisplayed READY candidates: "
                + preflight.initialReadyCandidates()
                + "\nCurrently eligible: " + preflight.eligibleCount()
                + "\nNo longer READY: "
                + preflight.excludedNoLongerReady()
                + "\nAlready assigned: "
                + preflight.excludedAlreadyAssigned()
                + "\nMissing media records: " + preflight.excludedMissing()
                + "\nPreflight failures: " + preflight.preflightFailures();
    }

    private void showBatchOutcome(ReadyPageBatchResult result) {
        if (result != null && result.renameFailures() > 0) {
            reviewTabs.getSelectionModel().select(1);
        }
    }

    private void openMediaLocationsWindow() {
        mediaLocationsWindowLauncher.open(ownerWindow());
    }

    private void restoreUnassignedSelection(ReviewQueueItem oldValue) {
        restoringSelection = true;
        queueTable.getSelectionModel().select(oldValue);
        restoringSelection = false;
    }

    private void restoreSceneSelection(SceneReviewQueueItem oldValue) {
        restoringSelection = true;
        unverifiedScenesTable.getSelectionModel().select(oldValue);
        restoringSelection = false;
    }

    private void loadEditorDraft(ReviewDetails details) {
        if (details != null) {
            selectedPerformersList.getItems()
                    .setAll(details.resolvedPerformerNames());
            alternativesList.getItems()
                    .setAll(details.alternativeInterpretations()
                            .stream()
                            .map(this::alternativeText)
                            .toList());
            editorViewModel.loadUnassignedDetails(details);
            refreshSelectedPerformers();
        } else {
            selectedPerformersList.getItems().clear();
            alternativesList.getItems().clear();
        }
    }

    private void loadExistingSceneDraft(SceneReviewQueueItem item) {
        if (item != null) {
            selectedPerformersList.getItems().clear();
            alternativesList.getItems().clear();
            editorViewModel.loadExistingScene(item.sceneId());
            refreshSelectedPerformers();
        }
    }

    private void createPublisher() {
        entityDialogLauncher.createPublisher(ownerWindow())
                .ifPresent(this::selectPublisher);
    }

    private void applyInterpretation() {
        final int selectedIndex = alternativesList.getSelectionModel()
                .getSelectedIndex();
        final ReviewDetails details = viewModel.selectedDetails().get();

        if (details != null
                && selectedIndex >= 0
                && selectedIndex < details.alternativeInterpretations().size()) {
            editorViewModel.applyAlternative(
                    details.alternativeInterpretations().get(selectedIndex)
            );
            refreshSelectedPerformers();
        }
    }

    private void createSeries() {
        entityDialogLauncher.createSeries(ownerWindow())
                .ifPresent(this::selectSeries);
    }

    private void createMovie() {
        entityDialogLauncher.createMovie(ownerWindow())
                .ifPresent(this::selectMovie);
    }

    private void createPerformer() {
        entityDialogLauncher.createPerformer(ownerWindow())
                .ifPresent(this::selectPerformer);
    }

    private void selectPublisher(Publisher publisher) {
        editorViewModel.selectedPublisherIdProperty().set(publisher.getId());
        publisherSearchField.setText(publisher.getName());
    }

    private void selectSeries(Series series) {
        editorViewModel.selectedSeriesIdProperty().set(series.getId());
        editorViewModel.selectedPublisherIdProperty().set(
                series.getPublisher().getId()
        );
        seriesSearchField.setText(series.getTitle());
        publisherSearchField.setText(series.getPublisher().getName());
    }

    private void selectMovie(Movie movie) {
        editorViewModel.selectedMovieIdProperty().set(movie.getId());
        editorViewModel.explicitOriginalMovieOverrideIdProperty()
                .set(movie.getId());
        movieSearchField.setText(movie.getTitle());
    }

    private void selectPerformer(Performer performer) {
        editorViewModel.addPerformer(
                performer.getId(),
                performer.getMainName()
        );
        refreshSelectedPerformers();
    }

    private void selectPublisherSuggestion(EntitySuggestionDisplay suggestion) {
        final ReviewDetails details = viewModel.selectedDetails().get();

        if (details != null
                && details.publisherCandidate() != null
                && !details.publisherCandidate().isBlank()) {
            editorViewModel.resolvePublisherCandidate(
                    details.publisherCandidate(),
                    suggestion.id(),
                    suggestion.displayName()
            );
        } else {
            editorViewModel.selectedPublisherIdProperty().set(suggestion.id());
        }

        publisherSearchField.setText(suggestion.displayName());
        seriesAutocomplete.search();
    }

    private void selectSeriesSuggestion(EntitySuggestionDisplay suggestion) {
        editorViewModel.selectedSeriesIdProperty().set(suggestion.id());

        if (suggestion.publisherId() != null) {
            editorViewModel.selectedPublisherIdProperty()
                    .set(suggestion.publisherId());
        }

        seriesSearchField.setText(suggestion.displayName());
    }

    private void selectMovieSuggestion(EntitySuggestionDisplay suggestion) {
        editorViewModel.selectedMovieIdProperty().set(suggestion.id());
        editorViewModel.explicitOriginalMovieOverrideIdProperty()
                .set(suggestion.id());
        movieSearchField.setText(suggestion.displayName());
    }

    private void selectPerformerSuggestion(EntitySuggestionDisplay suggestion) {
        final String candidate = firstUnmatchedPerformerCandidate();

        if (candidate.isBlank()) {
            editorViewModel.addPerformer(
                    suggestion.id(),
                    suggestion.displayName()
            );
        } else {
            editorViewModel.resolvePerformerCandidate(
                    candidate,
                    suggestion.id(),
                    suggestion.displayName()
            );
        }

        performerSearchField.setText("");
        refreshSelectedPerformers();
    }

    private String firstUnmatchedPerformerCandidate() {
        final service.EditableSceneReviewDraft draft =
                editorViewModel.currentDraft();
        String candidate = "";

        if (draft != null && !draft.unmatchedPerformers().isEmpty()) {
            candidate = draft.unmatchedPerformers().getFirst();
        }

        return candidate;
    }

    private void refreshSelectedPerformers() {
        selectedPerformersList.getItems().setAll(
                editorViewModel.selectedPerformers()
                        .stream()
                        .map(SelectedPerformer::displayName)
                        .toList()
        );
    }

    private javafx.stage.Window ownerWindow() {
        return titleEditorField.getScene() == null
                ? null
                : titleEditorField.getScene().getWindow();
    }

    private void selectNextVisibleRow() {
        final int selectedIndex =
                queueTable.getSelectionModel().getSelectedIndex();
        final int nextIndex = selectedIndex + 1;

        if (nextIndex < queueTable.getItems().size()) {
            queueTable.getSelectionModel().select(nextIndex);
        }
    }

    private void selectPreviousVisibleRow() {
        final int selectedIndex =
                queueTable.getSelectionModel().getSelectedIndex();
        final int previousIndex = selectedIndex - 1;

        if (previousIndex >= 0) {
            queueTable.getSelectionModel().select(previousIndex);
        }
    }

    private void handleSaveResult(SceneReviewSaveResult result) {
        if (result != null && result.databasePersisted()) {
            final boolean unassignedTabSelected =
                    reviewTabs.getSelectionModel().getSelectedIndex() == 0;
            final boolean partialRenameFailure =
                    result.status()
                            == SceneReviewSaveStatus.CREATED_RENAME_FAILED_NEEDS_REVIEW
                            || result.status()
                            == SceneReviewSaveStatus.UPDATED_RENAME_FAILED_NEEDS_REVIEW;

            if (unassignedTabSelected) {
                removeSelectedUnassignedRow();
            } else if (result.finalVerificationStatus()
                    == model.VerificationStatus.VERIFIED) {
                removeSelectedSceneRow();
            }

            if (partialRenameFailure) {
                reviewTabs.getSelectionModel().select(1);
            }

            viewModel.load();
            sceneQueueViewModel.load();
            scanRefreshCoordinator.consumePendingAfterExternalRefresh();
        }
    }

    private void removeSelectedUnassignedRow() {
        final int selectedIndex =
                queueTable.getSelectionModel().getSelectedIndex();
        final int previousSize = queueTable.getItems().size();

        if (selectedIndex >= 0) {
            queueTable.getItems().remove(selectedIndex);
            selectIndexAfterRemoval(
                    queueTable,
                    selectedIndex,
                    previousSize
            );
        }
    }

    private void removeSelectedSceneRow() {
        final int selectedIndex =
                unverifiedScenesTable.getSelectionModel().getSelectedIndex();
        final int previousSize = unverifiedScenesTable.getItems().size();

        if (selectedIndex >= 0) {
            unverifiedScenesTable.getItems().remove(selectedIndex);
            selectIndexAfterRemoval(
                    unverifiedScenesTable,
                    selectedIndex,
                    previousSize
            );
        }
    }

    private <T> void selectIndexAfterRemoval(
            TableView<T> table,
            int selectedIndex,
            int previousSize) {

        final int nextIndex = ReviewNavigationGuard.nextIndexAfterRemoval(
                selectedIndex,
                previousSize
        );

        if (nextIndex == ReviewNavigationGuard.NO_SELECTION) {
            table.getSelectionModel().clearSelection();
        } else {
            table.getSelectionModel().select(nextIndex);
        }
    }

    private String alternativeText(FilenameInterpretation interpretation) {
        return "Publisher="
                + entityName(interpretation.publisher())
                + ", Series="
                + entityName(interpretation.series())
                + ", Movie="
                + entityName(interpretation.movie());
    }

    private String entityName(EntityMatch match) {
        return match == null || match.name() == null ? "" : match.name();
    }

    private String detailsText(String field) {
        final ReviewDetails details = viewModel.selectedDetails().get();
        String text = "";

        if (details != null) {
            if ("path".equals(field)) {
                text = details.path().toString();
            } else if ("parse".equals(field)) {
                text = details.parseStatus().name();
            } else if ("match".equals(field)) {
                text = details.matchStatus().name();
            } else if ("canonical".equals(field)) {
                text = details.canonicalRename().status();
            } else if ("filename".equals(field)) {
                text = details.canonicalRename().proposedFilename();
            } else {
                text = details.mediaId().toString();
            }
        }

        return text;
    }
}

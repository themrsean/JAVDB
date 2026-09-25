package ui.context;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import service.ContextCandidate;
import service.ContextCandidateStatus;
import service.ContextPublisherEvidence;
import service.ContextPublisherResolutionService;
import service.ContextSeriesResolutionService;
import service.ContextMovieResolutionService;
import ui.control.EntityAutocompleteViewModel;
import ui.control.EntitySuggestionDisplay;
import ui.review.EntityDialogLauncher;

import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ContextCandidateController {
    private final ContextCandidateViewModel viewModel;
    private final ContextPublisherResolutionService publisherResolutionService;
    private final ContextSeriesResolutionService seriesResolutionService;
    private final ContextMovieResolutionService movieResolutionService;
    private final EntityDialogLauncher entityDialogLauncher;
    private final EntityAutocompleteViewModel publisherAutocomplete;
    private final FilteredList<ContextCandidate> visible;

    @FXML private TableView<ContextCandidate> candidatesTable;
    @FXML private TableColumn<ContextCandidate, String> candidateColumn;
    @FXML private TableColumn<ContextCandidate, Integer> countColumn;
    @FXML private TableColumn<ContextCandidate, String> positionColumn;
    @FXML private TableColumn<ContextCandidate, String> statusColumn;
    @FXML private TableColumn<ContextCandidate, String> matchesColumn;
    @FXML private TableColumn<ContextCandidate, String> examplesColumn;
    @FXML private CheckBox attentionOnlyCheckBox;
    @FXML private TextField candidateFilterField;
    @FXML private Button refreshButton;
    @FXML private Button createPublisherButton;
    @FXML private Button mapPublisherAliasButton;
    @FXML private Button createSeriesButton;
    @FXML private Button createMovieButton;
    @FXML private TextField publisherSearchField;
    @FXML private ListView<EntitySuggestionDisplay> publisherSuggestionsList;
    @FXML private ListView<String> publisherEvidenceList;
    @FXML private Label publisherEvidenceLabel;
    @FXML private Label emptyLabel;
    @FXML private Label errorLabel;
    @FXML private Label resultLabel;
    private EntitySuggestionDisplay selectedPublisher;

    public ContextCandidateController(ContextCandidateViewModel viewModel) {
        this(viewModel, null, null, null, null, null);
    }

    public ContextCandidateController(ContextCandidateViewModel viewModel,
            ContextPublisherResolutionService publisherResolutionService,
            ContextSeriesResolutionService seriesResolutionService,
            ContextMovieResolutionService movieResolutionService,
            EntityDialogLauncher entityDialogLauncher,
            EntityAutocompleteViewModel publisherAutocomplete) {
        this.viewModel = Objects.requireNonNull(viewModel);
        this.publisherResolutionService = publisherResolutionService;
        this.seriesResolutionService = seriesResolutionService;
        this.movieResolutionService = movieResolutionService;
        this.entityDialogLauncher = entityDialogLauncher;
        this.publisherAutocomplete = publisherAutocomplete;
        visible = new FilteredList<>(viewModel.candidates());
    }

    @FXML
    private void initialize() {
        candidateColumn.setCellValueFactory(new PropertyValueFactory<>("text"));
        countColumn.setCellValueFactory(new PropertyValueFactory<>("occurrences"));
        positionColumn.setCellValueFactory(value -> new SimpleStringProperty(
                formatCounts(value.getValue().positionCounts())));
        statusColumn.setCellValueFactory(value -> new SimpleStringProperty(
                value.getValue().status().toString()));
        matchesColumn.setCellValueFactory(value -> new SimpleStringProperty(value
                .getValue().matches().stream().map(match -> match.displayText())
                .collect(Collectors.joining(" | "))));
        examplesColumn.setCellValueFactory(value -> new SimpleStringProperty(value
                .getValue().representativePaths().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.joining(" | "))));
        candidatesTable.setItems(visible);
        candidatesTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, selected) -> showPublisherEvidence(selected));
        attentionOnlyCheckBox.selectedProperty()
                .bindBidirectional(viewModel.attentionOnlyProperty());
        attentionOnlyCheckBox.selectedProperty().addListener((o, oldValue, newValue) -> filter());
        candidateFilterField.textProperty().addListener((o, oldValue, newValue) -> filter());
        refreshButton.setOnAction(event -> viewModel.load());
        createPublisherButton.setOnAction(event -> createPublisher());
        mapPublisherAliasButton.setOnAction(event -> mapPublisherAlias());
        createSeriesButton.setOnAction(event -> createSeries());
        createMovieButton.setOnAction(event -> createMovie());
        configurePublisherAutocomplete();
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        resultLabel.textProperty().bind(viewModel.resultMessageProperty());
        emptyLabel.visibleProperty().bind(Bindings.isEmpty(visible)
                .and(viewModel.loadingProperty().not()));
        filter();
        showPublisherEvidence(null);
    }

    private void configurePublisherAutocomplete() {
        if (publisherAutocomplete == null) return;
        publisherSearchField.textProperty()
                .bindBidirectional(publisherAutocomplete.searchTextProperty());
        publisherSearchField.textProperty().addListener((o, oldValue, newValue) ->
                publisherAutocomplete.search());
        publisherSuggestionsList.setItems(publisherAutocomplete.suggestions());
        publisherSuggestionsList.getSelectionModel().selectedItemProperty()
                .addListener((o, oldValue, newValue) -> selectedPublisher = newValue);
        createPublisherButton.disableProperty().bind(
                candidatesTable.getSelectionModel().selectedItemProperty().isNull()
                        .or(javafx.beans.binding.Bindings.createBooleanBinding(
                                () -> !isUnresolvedSelection(),
                                candidatesTable.getSelectionModel()
                                        .selectedItemProperty())));
        mapPublisherAliasButton.disableProperty().bind(
                createPublisherButton.disableProperty().or(
                        publisherSuggestionsList.getSelectionModel()
                                .selectedItemProperty().isNull()));
        createSeriesButton.disableProperty().bind(
                candidatesTable.getSelectionModel().selectedItemProperty().isNull()
                        .or(javafx.beans.binding.Bindings.createBooleanBinding(
                                () -> !isUnresolvedSelection(),
                                candidatesTable.getSelectionModel()
                                        .selectedItemProperty())));
        createMovieButton.disableProperty().bind(
                candidatesTable.getSelectionModel().selectedItemProperty().isNull()
                        .or(javafx.beans.binding.Bindings.createBooleanBinding(
                                () -> !isUnresolvedSelection(),
                                candidatesTable.getSelectionModel()
                                        .selectedItemProperty())));
    }

    private boolean isUnresolvedSelection() {
        final ContextCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        return canResolveCandidate(candidate);
    }

    static boolean canResolveCandidate(ContextCandidate candidate) {
        return candidate != null
                && candidate.status() == ContextCandidateStatus.UNRESOLVED;
    }

    private void createPublisher() {
        final ContextCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        if (!isUnresolvedSelection() || publisherResolutionService == null
                || entityDialogLauncher == null) return;
        entityDialogLauncher.createPublisher(ownerWindow(), candidate.text(),
                        (name, aliases) -> publisherResolutionService
                                .createPublisher(candidate.text(), name, aliases))
                .ifPresent(publisher -> viewModel.publisherCreated());
        viewModel.load();
    }

    private void mapPublisherAlias() {
        final ContextCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        if (!isUnresolvedSelection() || selectedPublisher == null) return;
        if (confirm("Add publisher alias", "Add '" + candidate.text()
                + "' as an alias for '" + selectedPublisher.displayName()
                + "'?")) {
            viewModel.mapPublisherAlias(candidate.text(), selectedPublisher.id());
        }
    }

    private void createSeries() {
        final ContextCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        if (!canResolveCandidate(candidate) || seriesResolutionService == null
                || entityDialogLauncher == null) return;
        entityDialogLauncher.createSeries(ownerWindow(), candidate.text(),
                        (title, publisherId) -> seriesResolutionService
                                .createSeries(candidate.text(), title, publisherId))
                .ifPresent(series -> viewModel.seriesCreated());
        viewModel.load();
    }

    private void createMovie() {
        final ContextCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        if (!canResolveCandidate(candidate) || movieResolutionService == null
                || entityDialogLauncher == null) return;
        entityDialogLauncher.createMovie(ownerWindow(), candidate.text(),
                        (title, releaseDate, publisherId, compilation) ->
                                movieResolutionService.createMovie(candidate.text(),
                                        title, releaseDate, publisherId, compilation))
                .ifPresent(movie -> viewModel.movieCreated());
        viewModel.load();
    }

    private boolean confirm(String title, String text) {
        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text,
                javafx.scene.control.ButtonType.CANCEL,
                javafx.scene.control.ButtonType.OK);
        alert.setTitle(title);
        return alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                == javafx.scene.control.ButtonType.OK;
    }

    private javafx.stage.Window ownerWindow() {
        return candidatesTable.getScene() == null ? null
                : candidatesTable.getScene().getWindow();
    }

    private void filter() {
        final String text = candidateFilterField.getText() == null ? ""
                : candidateFilterField.getText().toLowerCase(Locale.ROOT);
        visible.setPredicate(candidate -> matchesFilter(candidate, text,
                viewModel.attentionOnlyProperty().get()));
    }

    private void showPublisherEvidence(ContextCandidate candidate) {
        publisherEvidenceList.getItems().clear();
        if (candidate == null) {
            publisherEvidenceLabel.setText(
                    "Known Publisher context: select a candidate to inspect evidence.");
            return;
        }
        if (candidate.publisherEvidence().isEmpty()) {
            publisherEvidenceLabel.setText(
                    "Known Publisher context: no exact Publisher context is currently known.");
            return;
        }
        publisherEvidenceLabel.setText("Known Publisher context (exact catalog evidence):");
        publisherEvidenceList.getItems().setAll(candidate.publisherEvidence().stream()
                .map(ContextCandidateController::formatPublisherEvidence).toList());
    }

    static String formatPublisherEvidence(ContextPublisherEvidence evidence) {
        final List<String> parts = new java.util.ArrayList<>();
        if (evidence.candidateBeforePublisherFiles() > 0) {
            parts.add("candidate before: " + evidence.candidateBeforePublisherFiles());
        }
        if (evidence.candidateAfterPublisherFiles() > 0) {
            parts.add("candidate after: " + evidence.candidateAfterPublisherFiles());
        }
        if (evidence.publisherOnBothSidesFiles() > 0) {
            parts.add("both sides: " + evidence.publisherOnBothSidesFiles());
        }
        if (evidence.directPublisherFiles() > 0) {
            parts.add("exact Publisher: " + evidence.directPublisherFiles());
        }
        if (evidence.seriesPublisherFiles() > 0) {
            parts.add("exact Series: " + evidence.seriesPublisherFiles());
        }
        if (evidence.moviePublisherFiles() > 0) {
            parts.add("exact Movie: " + evidence.moviePublisherFiles());
        }
        return evidence.publisherName() + " — " + evidence.affectedFiles()
                + " files (" + String.join(", ", parts) + ")";
    }

    static boolean matchesFilter(ContextCandidate candidate, String text,
            boolean attentionOnly) {
        return (!attentionOnly || candidate.status().needsAttention())
                && candidate.text().toLowerCase(Locale.ROOT).contains(text);
    }

    private String formatCounts(java.util.Map<Integer, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}

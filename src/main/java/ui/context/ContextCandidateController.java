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
import service.ContextPublisherResolutionService;
import ui.control.EntityAutocompleteViewModel;
import ui.control.EntitySuggestionDisplay;
import ui.review.EntityDialogLauncher;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ContextCandidateController {
    private final ContextCandidateViewModel viewModel;
    private final ContextPublisherResolutionService publisherResolutionService;
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
    @FXML private TextField publisherSearchField;
    @FXML private ListView<EntitySuggestionDisplay> publisherSuggestionsList;
    @FXML private Label emptyLabel;
    @FXML private Label errorLabel;
    @FXML private Label resultLabel;
    private EntitySuggestionDisplay selectedPublisher;

    public ContextCandidateController(ContextCandidateViewModel viewModel) {
        this(viewModel, null, null, null);
    }

    public ContextCandidateController(ContextCandidateViewModel viewModel,
            ContextPublisherResolutionService publisherResolutionService,
            EntityDialogLauncher entityDialogLauncher,
            EntityAutocompleteViewModel publisherAutocomplete) {
        this.viewModel = Objects.requireNonNull(viewModel);
        this.publisherResolutionService = publisherResolutionService;
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
        attentionOnlyCheckBox.selectedProperty()
                .bindBidirectional(viewModel.attentionOnlyProperty());
        attentionOnlyCheckBox.selectedProperty().addListener((o, oldValue, newValue) -> filter());
        candidateFilterField.textProperty().addListener((o, oldValue, newValue) -> filter());
        refreshButton.setOnAction(event -> viewModel.load());
        createPublisherButton.setOnAction(event -> createPublisher());
        mapPublisherAliasButton.setOnAction(event -> mapPublisherAlias());
        configurePublisherAutocomplete();
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        resultLabel.textProperty().bind(viewModel.resultMessageProperty());
        emptyLabel.visibleProperty().bind(Bindings.isEmpty(visible)
                .and(viewModel.loadingProperty().not()));
        filter();
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
    }

    private boolean isUnresolvedSelection() {
        final ContextCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        return canResolvePublisher(candidate);
    }

    static boolean canResolvePublisher(ContextCandidate candidate) {
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

package ui.performer;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import model.PerformerCategory;
import service.PerformerCandidate;
import service.PerformerCandidateResolution;
import ui.control.EntityAutocompleteViewModel;
import ui.control.EntitySuggestionDisplay;
import ui.control.RecordTableCellValues;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class PerformerCandidateController {
    private final PerformerCandidateViewModel viewModel;
    private final EntityAutocompleteViewModel autocomplete;
    private final FilteredList<PerformerCandidate> visible;
    private final PerformerCandidateFilterState filterState =
            new PerformerCandidateFilterState();

    @FXML private TableView<PerformerCandidate> candidatesTable;
    @FXML private TableColumn<PerformerCandidate, String> candidateColumn;
    @FXML private TableColumn<PerformerCandidate, Integer> countColumn;
    @FXML private TableColumn<PerformerCandidate, String> statusColumn;
    @FXML private TableColumn<PerformerCandidate, String> performerColumn;
    @FXML private TableColumn<PerformerCandidate, String> examplesColumn;
    @FXML private CheckBox unresolvedOnlyCheckBox;
    @FXML private ComboBox<PerformerCategory> categoryComboBox;
    @FXML private Button refreshButton;
    @FXML private Button createButton;
    @FXML private Button mapAliasButton;
    @FXML private Button createSelectedButton;
    @FXML private TextField candidateFilterField;
    @FXML private TextField minimumCountField;
    @FXML private TextField performerSearchField;
    @FXML private ListView<EntitySuggestionDisplay> performerSuggestionsList;
    @FXML private Label minimumCountValidationLabel;
    @FXML private Label emptyLabel;
    @FXML private Label errorLabel;
    @FXML private Label resultLabel;

    private EntitySuggestionDisplay selectedPerformer;

    public PerformerCandidateController(PerformerCandidateViewModel viewModel,
            EntityAutocompleteViewModel autocomplete) {
        this.viewModel = Objects.requireNonNull(viewModel);
        this.autocomplete = Objects.requireNonNull(autocomplete);
        visible = new FilteredList<>(viewModel.candidates());
    }

    @FXML
    private void initialize() {
        configureTable();
        configureFilters();
        configureCategoryAndActions();
        configureAutocomplete();
        configureMessages();
        applyFilterIfValid();
    }

    private void configureTable() {
        candidateColumn.setCellValueFactory(
                RecordTableCellValues.string(PerformerCandidate::text));
        countColumn.setCellValueFactory(
                RecordTableCellValues.object(PerformerCandidate::mediaCount));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().resolution().toString()));
        performerColumn.setCellValueFactory(
                RecordTableCellValues.string(PerformerCandidate::performerName));
        examplesColumn.setCellValueFactory(data -> new SimpleStringProperty(data
                .getValue().representativePaths().stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.joining(" | "))));
        candidatesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        candidatesTable.setItems(visible);
    }

    private void configureFilters() {
        unresolvedOnlyCheckBox.selectedProperty()
                .bindBidirectional(viewModel.unresolvedOnlyProperty());
        unresolvedOnlyCheckBox.selectedProperty().addListener(
                (observable, previous, selected) -> applyFilterIfValid());
        candidateFilterField.textProperty().addListener(
                (observable, previous, text) -> applyFilterIfValid());
        minimumCountField.textProperty().addListener(
                (observable, previous, text) -> applyFilterIfValid());
    }

    private void configureCategoryAndActions() {
        categoryComboBox.setItems(FXCollections.observableArrayList(
                availableCategories()));
        categoryComboBox.setValue(defaultCategory());
        refreshButton.setOnAction(event -> viewModel.load());
        createButton.setOnAction(event -> create());
        createSelectedButton.setOnAction(event -> createSelected());
        mapAliasButton.setOnAction(event -> mapAlias());
        createButton.disableProperty().bind(candidatesTable.getSelectionModel()
                .selectedItemProperty().isNull()
                .or(Bindings.createBooleanBinding(() -> !isUnresolvedSelection(),
                        candidatesTable.getSelectionModel().selectedItemProperty()))
                .or(categoryComboBox.valueProperty().isNull()));
        mapAliasButton.disableProperty().bind(candidatesTable.getSelectionModel()
                .selectedItemProperty().isNull()
                .or(Bindings.createBooleanBinding(() -> !isUnresolvedSelection(),
                        candidatesTable.getSelectionModel().selectedItemProperty()))
                .or(performerSuggestionsList.getSelectionModel()
                        .selectedItemProperty().isNull()));
        createSelectedButton.disableProperty().bind(categoryComboBox.valueProperty()
                .isNull().or(candidatesTable.getSelectionModel()
                        .selectedItemProperty().isNull()));
    }

    private void configureAutocomplete() {
        performerSearchField.textProperty()
                .bindBidirectional(autocomplete.searchTextProperty());
        performerSearchField.textProperty().addListener(
                (observable, previous, text) -> autocomplete.search());
        performerSuggestionsList.setItems(autocomplete.suggestions());
        performerSuggestionsList.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, selected) ->
                        selectedPerformer = selected);
    }

    private void configureMessages() {
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        resultLabel.textProperty().bind(viewModel.resultMessageProperty());
        emptyLabel.visibleProperty().bind(Bindings.isEmpty(visible)
                .and(viewModel.loadingProperty().not()));
    }

    private void applyFilterIfValid() {
        if (filterState.updateMinimumOccurrences(minimumCountField.getText())) {
            minimumCountValidationLabel.setText("");
            visible.setPredicate(candidate -> filterState.matches(candidate,
                    candidateFilterField.getText(),
                    viewModel.unresolvedOnlyProperty().get()));
        } else {
            minimumCountValidationLabel.setText(filterState.validationMessage());
        }
    }

    private boolean isUnresolvedSelection() {
        final PerformerCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        return candidate != null && candidate.resolution()
                == PerformerCandidateResolution.UNRESOLVED;
    }

    private void create() {
        final PerformerCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        final PerformerCategory category = categoryComboBox.getValue();
        if (isUnresolvedSelection() && category != null && confirm(
                "Create performer", "Create performer '" + candidate.text()
                        + "' with category " + category + "?")) {
            viewModel.create(candidate.text(), category);
        }
    }

    private void mapAlias() {
        final PerformerCandidate candidate = candidatesTable.getSelectionModel()
                .getSelectedItem();
        if (isUnresolvedSelection() && selectedPerformer != null && confirm(
                "Add performer alias", "Add '" + candidate.text()
                        + "' as an alias for '" + selectedPerformer.displayName()
                        + "'?")) {
            viewModel.mapAlias(candidate.text(), selectedPerformer.id());
        }
    }

    private void createSelected() {
        final PerformerCategory category = categoryComboBox.getValue();
        final List<String> names = candidatesTable.getSelectionModel()
                .getSelectedItems().stream()
                .filter(candidate -> candidate.resolution()
                        == PerformerCandidateResolution.UNRESOLVED)
                .map(PerformerCandidate::text)
                .toList();
        if (category != null && !names.isEmpty() && confirm(
                "Create selected performers", "Create " + names.size()
                        + " performers with category " + category + "?")) {
            viewModel.createSelected(names, category);
        }
    }

    private boolean confirm(String title, String text) {
        final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text,
                ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    static List<PerformerCategory> availableCategories() {
        return List.of(PerformerCategory.values());
    }

    static PerformerCategory defaultCategory() {
        return PerformerCategory.UNKNOWN;
    }
}

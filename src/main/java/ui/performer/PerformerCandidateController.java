package ui.performer;

import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import service.PerformerCandidate;
import ui.control.EntityAutocompleteViewModel;
import ui.control.EntitySuggestionDisplay;
import ui.control.RecordTableCellValues;
import java.util.Objects;

public final class PerformerCandidateController {
    private final PerformerCandidateViewModel viewModel;
    private final EntityAutocompleteViewModel autocomplete;
    private final FilteredList<PerformerCandidate> visible;
    @FXML private TableView<PerformerCandidate> candidatesTable;
    @FXML private TableColumn<PerformerCandidate,String> candidateColumn;
    @FXML private TableColumn<PerformerCandidate,Integer> countColumn;
    @FXML private TableColumn<PerformerCandidate,String> statusColumn;
    @FXML private TableColumn<PerformerCandidate,String> performerColumn;
    @FXML private TableColumn<PerformerCandidate,String> examplesColumn;
    @FXML private CheckBox unresolvedOnlyCheckBox;
    @FXML private Button refreshButton; @FXML private Button createButton; @FXML private Button mapAliasButton; @FXML private Button createSelectedButton;
    @FXML private TextField candidateFilterField; @FXML private TextField minimumCountField;
    @FXML private TextField performerSearchField; @FXML private ListView<EntitySuggestionDisplay> performerSuggestionsList;
    @FXML private Label emptyLabel; @FXML private Label errorLabel; @FXML private Label resultLabel;
    private EntitySuggestionDisplay selectedPerformer;
    public PerformerCandidateController(PerformerCandidateViewModel viewModel, EntityAutocompleteViewModel autocomplete) { this.viewModel=Objects.requireNonNull(viewModel);this.autocomplete=Objects.requireNonNull(autocomplete);visible=new FilteredList<>(viewModel.candidates()); }
    @FXML private void initialize() {
        candidateColumn.setCellValueFactory(
                RecordTableCellValues.string(PerformerCandidate::text));
        countColumn.setCellValueFactory(
                RecordTableCellValues.object(PerformerCandidate::mediaCount));
        statusColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(
                        data.getValue().resolution().toString()));
        performerColumn.setCellValueFactory(
                RecordTableCellValues.string(PerformerCandidate::performerName));
        examplesColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleStringProperty(data.getValue()
                        .representativePaths().stream()
                        .map(path -> path.getFileName().toString())
                        .collect(java.util.stream.Collectors.joining(" | "))));
        candidatesTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE); candidatesTable.setItems(visible); unresolvedOnlyCheckBox.selectedProperty().bindBidirectional(viewModel.unresolvedOnlyProperty()); unresolvedOnlyCheckBox.selectedProperty().addListener((o,a,b)->filter()); candidateFilterField.textProperty().addListener((o,a,b)->filter());minimumCountField.textProperty().addListener((o,a,b)->filter()); filter();
        refreshButton.setOnAction(e->viewModel.load()); createButton.setOnAction(e->create()); mapAliasButton.setOnAction(e->map());createSelectedButton.setOnAction(e->createSelected());
        performerSearchField.textProperty().bindBidirectional(autocomplete.searchTextProperty()); performerSearchField.textProperty().addListener((o,a,b)->autocomplete.search()); performerSuggestionsList.setItems(autocomplete.suggestions()); performerSuggestionsList.getSelectionModel().selectedItemProperty().addListener((o,a,b)->selectedPerformer=b);
        errorLabel.textProperty().bind(viewModel.errorMessageProperty()); resultLabel.textProperty().bind(viewModel.resultMessageProperty()); emptyLabel.visibleProperty().bind(javafx.beans.binding.Bindings.isEmpty(visible)); createButton.disableProperty().bind(candidatesTable.getSelectionModel().selectedItemProperty().isNull()); mapAliasButton.disableProperty().bind(candidatesTable.getSelectionModel().selectedItemProperty().isNull());
    }
    private void filter(){int min=0;try{min=minimumCountField.getText().isBlank()?0:Integer.parseInt(minimumCountField.getText());}catch(NumberFormatException ignored){}final int minimum=min;final String text=candidateFilterField.getText().toLowerCase(java.util.Locale.ROOT);visible.setPredicate(value->(!viewModel.unresolvedOnlyProperty().get()||value.resolution()==service.PerformerCandidateResolution.UNRESOLVED)&&value.mediaCount()>=minimum&&value.text().toLowerCase(java.util.Locale.ROOT).contains(text));}
    private void create(){var row=candidatesTable.getSelectionModel().getSelectedItem();if(row!=null&&row.resolution()==service.PerformerCandidateResolution.UNRESOLVED&&confirm("Create performer", "Create performer '"+row.text()+"' with category UNKNOWN?"))viewModel.create(row.text());}
    private void map(){var row=candidatesTable.getSelectionModel().getSelectedItem();if(row!=null&&selectedPerformer!=null&&row.resolution()==service.PerformerCandidateResolution.UNRESOLVED&&confirm("Add performer alias", "Add '"+row.text()+"' as an alias for '"+selectedPerformer.displayName()+"'?"))viewModel.mapAlias(row.text(),selectedPerformer.id());}
    private void createSelected(){var names=candidatesTable.getSelectionModel().getSelectedItems().stream().filter(row->row.resolution()==service.PerformerCandidateResolution.UNRESOLVED).map(PerformerCandidate::text).toList();if(!names.isEmpty()&&confirm("Create selected performers","Create "+names.size()+" performers with category UNKNOWN?"))viewModel.createSelected(names);}
    private boolean confirm(String title,String text){var alert=new Alert(Alert.AlertType.CONFIRMATION,text,javafx.scene.control.ButtonType.CANCEL,javafx.scene.control.ButtonType.OK);alert.setTitle(title);return alert.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)==javafx.scene.control.ButtonType.OK;}
}

package ui.context;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import service.ContextCandidate;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** Controller deliberately has no catalog mutation actions. */
public final class ContextCandidateController {
    private final ContextCandidateViewModel viewModel;
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
    @FXML private Label emptyLabel;
    @FXML private Label errorLabel;

    public ContextCandidateController(ContextCandidateViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel);
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
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        emptyLabel.visibleProperty().bind(Bindings.isEmpty(visible)
                .and(viewModel.loadingProperty().not()));
        filter();
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

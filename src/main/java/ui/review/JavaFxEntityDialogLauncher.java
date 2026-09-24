package ui.review;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Series;
import service.EntityManagementService;
import service.EntitySuggestionService;
import ui.control.EntityAutocompleteViewModel;
import ui.control.EntitySuggestionDisplay;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class JavaFxEntityDialogLauncher implements EntityDialogLauncher {
    private static final int DIALOG_PADDING = 12;
    private static final int DIALOG_GAP = 8;
    private static final int ALIAS_ROW_COUNT = 3;
    private static final int ALIAS_COLUMN_COUNT = 32;

    private final EntityManagementService entityManagementService;
    private final EntitySuggestionService entitySuggestionService;
    private final Executor backgroundExecutor;

    public JavaFxEntityDialogLauncher(
            EntityManagementService entityManagementService,
            EntitySuggestionService entitySuggestionService,
            Executor backgroundExecutor) {

        this.entityManagementService = Objects.requireNonNull(
                entityManagementService,
                "Entity management service must not be null"
        );
        this.entitySuggestionService = Objects.requireNonNull(
                entitySuggestionService,
                "Entity suggestion service must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
    }

    @Override
    public Optional<Publisher> createPublisher(Window owner) {
        final PublisherEditorDialogViewModel viewModel =
                new PublisherEditorDialogViewModel(
                        entityManagementService::createPublisher,
                        backgroundExecutor,
                        Platform::runLater
                );
        final TextField nameField = new TextField();
        final TextArea aliasArea = aliasArea();
        final Dialog<Publisher> dialog = dialog(
                owner,
                "Create Publisher",
                grid(
                        "Primary name",
                        nameField,
                        "Aliases",
                        aliasArea,
                        null,
                        null
                )
        );

        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        aliasArea.textProperty()
                .bindBidirectional(viewModel.aliasesTextProperty());
        wireDialogSave(dialog, viewModel::save,
                viewModel.saveEnabledProperty(),
                viewModel.savingProperty(),
                viewModel.errorMessageProperty(),
                viewModel.resultProperty());

        return dialog.showAndWait();
    }

    @Override
    public Optional<Performer> createPerformer(Window owner) {
        final PerformerEditorDialogViewModel viewModel =
                new PerformerEditorDialogViewModel(
                        entityManagementService::createPerformer,
                        backgroundExecutor,
                        Platform::runLater
                );
        final TextField nameField = new TextField();
        final TextArea aliasArea = aliasArea();
        final ComboBox<PerformerCategory> categoryBox = new ComboBox<>();
        categoryBox.getItems().setAll(PerformerCategory.values());
        categoryBox.setValue(PerformerCategory.UNKNOWN);

        final Dialog<Performer> dialog = dialog(
                owner,
                "Create Performer",
                grid(
                        "Main name",
                        nameField,
                        "Category",
                        categoryBox,
                        "Aliases",
                        aliasArea
                )
        );

        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        aliasArea.textProperty()
                .bindBidirectional(viewModel.aliasesTextProperty());
        categoryBox.valueProperty()
                .bindBidirectional(viewModel.categoryProperty());
        wireDialogSave(dialog, viewModel::save,
                viewModel.saveEnabledProperty(),
                viewModel.savingProperty(),
                viewModel.errorMessageProperty(),
                viewModel.resultProperty());

        return dialog.showAndWait();
    }

    @Override
    public Optional<Series> createSeries(Window owner) {
        final SeriesEditorDialogViewModel viewModel =
                new SeriesEditorDialogViewModel(
                        entityManagementService::createSeries,
                        backgroundExecutor,
                        Platform::runLater
                );
        final TextField titleField = new TextField();
        final PublisherSelector publisherSelector = publisherSelector();
        final Dialog<Series> dialog = dialog(
                owner,
                "Create Series",
                grid(
                        "Title",
                        titleField,
                        "Publisher",
                        publisherSelector.container(),
                        null,
                        null
                )
        );

        titleField.textProperty().bindBidirectional(viewModel.titleProperty());
        publisherSelector.selectedPublisherId().addListener(
                (observable, oldValue, newValue) ->
                        viewModel.publisherIdProperty().set(newValue)
        );
        wireDialogSave(dialog, viewModel::save,
                viewModel.saveEnabledProperty(),
                viewModel.savingProperty(),
                viewModel.errorMessageProperty(),
                viewModel.resultProperty());

        return dialog.showAndWait();
    }

    @Override
    public Optional<Movie> createMovie(Window owner) {
        final MovieEditorDialogViewModel viewModel =
                new MovieEditorDialogViewModel(
                        entityManagementService::createMovie,
                        backgroundExecutor,
                        Platform::runLater
                );
        final TextField titleField = new TextField();
        final TextField releaseDateField = new TextField();
        final PublisherSelector publisherSelector = publisherSelector();
        final CheckBox compilationBox = new CheckBox();
        final Dialog<Movie> dialog = dialog(
                owner,
                "Create Movie",
                grid(
                        "Title",
                        titleField,
                        "Release date",
                        releaseDateField,
                        "Publisher",
                        publisherSelector.container(),
                        "Compilation",
                        compilationBox
                )
        );

        titleField.textProperty().bindBidirectional(viewModel.titleProperty());
        releaseDateField.textProperty()
                .bindBidirectional(viewModel.releaseDateTextProperty());
        publisherSelector.selectedPublisherId().addListener(
                (observable, oldValue, newValue) ->
                        viewModel.publisherIdProperty().set(newValue)
        );
        compilationBox.selectedProperty()
                .bindBidirectional(viewModel.compilationProperty());
        wireDialogSave(dialog, viewModel::save,
                viewModel.saveEnabledProperty(),
                viewModel.savingProperty(),
                viewModel.errorMessageProperty(),
                viewModel.resultProperty());

        return dialog.showAndWait();
    }

    private <T> Dialog<T> dialog(Window owner, String title, GridPane content) {
        final Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(
                new ButtonType("Save", ButtonBar.ButtonData.OK_DONE),
                ButtonType.CANCEL
        );
        return dialog;
    }

    private <T> void wireDialogSave(
            Dialog<T> dialog,
            Runnable saveAction,
            javafx.beans.value.ObservableBooleanValue saveEnabled,
            javafx.beans.value.ObservableBooleanValue saving,
            javafx.beans.value.ObservableStringValue errorMessage,
            javafx.beans.value.ObservableValue<T> result) {

        final Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error-label");
        errorLabel.textProperty().bind(errorMessage);
        ((GridPane) dialog.getDialogPane().getContent()).add(
                errorLabel,
                0,
                ((GridPane) dialog.getDialogPane().getContent()).getRowCount(),
                2,
                1
        );
        final Button saveButton = (Button) dialog.getDialogPane()
                .lookupButton(dialog.getDialogPane().getButtonTypes()
                        .getFirst());
        saveButton.disableProperty().bind(
                Bindings.not(saveEnabled).or(saving)
        );
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            saveAction.run();
        });
        result.addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                dialog.setResult(newValue);
                dialog.close();
            }
        });
    }

    private GridPane grid(Object... labelControlPairs) {
        final GridPane grid = new GridPane();
        grid.setPadding(new Insets(DIALOG_PADDING));
        grid.setHgap(DIALOG_GAP);
        grid.setVgap(DIALOG_GAP);
        int row = 0;

        for (int index = 0; index < labelControlPairs.length; index += 2) {
            final Object label = labelControlPairs[index];
            final Object control = labelControlPairs[index + 1];

            if (label != null && control instanceof javafx.scene.Node node) {
                grid.add(new Label(label.toString()), 0, row);
                grid.add(node, 1, row);
                row++;
            }

        }

        return grid;
    }

    private TextArea aliasArea() {
        final TextArea textArea = new TextArea();
        textArea.setPrefColumnCount(ALIAS_COLUMN_COUNT);
        textArea.setPrefRowCount(ALIAS_ROW_COUNT);
        return textArea;
    }

    private PublisherSelector publisherSelector() {
        final TextField searchField = new TextField();
        final ListView<EntitySuggestionDisplay> suggestions = new ListView<>();
        suggestions.setPrefHeight(70.0);
        final Label selectedLabel = new Label();
        final javafx.beans.property.ObjectProperty<UUID> selectedPublisherId =
                new javafx.beans.property.SimpleObjectProperty<>();
        final EntityAutocompleteViewModel autocomplete =
                new EntityAutocompleteViewModel(
                        (query, limit) -> entitySuggestionService
                                .suggestPublishers(query, limit)
                                .stream()
                                .map(EntitySuggestionDisplay::from)
                                .toList(),
                        10,
                        backgroundExecutor,
                        Platform::runLater
                );
        final VBox container = new VBox(4.0);
        container.getChildren().setAll(searchField, suggestions, selectedLabel);
        searchField.textProperty()
                .bindBidirectional(autocomplete.searchTextProperty());
        searchField.textProperty().addListener(
                (observable, oldValue, newValue) -> autocomplete.search()
        );
        suggestions.setItems(autocomplete.suggestions());
        suggestions.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        selectedPublisherId.set(newValue.id());
                        selectedLabel.setText(newValue.displayName());
                        searchField.setText(newValue.displayName());
                    }
                });

        return new PublisherSelector(container, selectedPublisherId);
    }

    private record PublisherSelector(
            VBox container,
            javafx.beans.property.ObjectProperty<UUID> selectedPublisherId) {
    }
}

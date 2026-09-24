package ui.media;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import model.MediaLocation;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public final class MediaLocationsController {
    private static final String EMPTY_STATE =
            "No media locations configured.\n"
                    + "Add a folder to begin scanning media into JAVDB.";
    private final MediaLocationsViewModel viewModel;
    private Path lastChosenParent;

    @FXML
    private TableView<MediaLocation> locationsTable;
    @FXML
    private TableColumn<MediaLocation, String> directoryColumn;
    @FXML
    private TableColumn<MediaLocation, Boolean> enabledColumn;
    @FXML
    private TableColumn<MediaLocation, Boolean> recursiveColumn;
    @FXML
    private TableColumn<MediaLocation, String> lastScanColumn;
    @FXML
    private TableColumn<MediaLocation, String> statusColumn;
    @FXML
    private TableColumn<MediaLocation, String> newColumn;
    @FXML
    private TableColumn<MediaLocation, String> updatedColumn;
    @FXML
    private TableColumn<MediaLocation, String> unchangedColumn;
    @FXML
    private TableColumn<MediaLocation, String> missingColumn;
    @FXML
    private TableColumn<MediaLocation, String> failedColumn;
    @FXML
    private Button addFolderButton;
    @FXML
    private Button emptyAddFolderButton;
    @FXML
    private Button removeButton;
    @FXML
    private Button scanSelectedButton;
    @FXML
    private Button scanAllButton;
    @FXML
    private Button cancelScanButton;
    @FXML
    private Button closeButton;
    @FXML
    private Label emptyStateLabel;
    @FXML
    private Label phaseLabel;
    @FXML
    private Label currentDirectoryLabel;
    @FXML
    private Label currentFileLabel;
    @FXML
    private Label currentFilePathLabel;
    @FXML
    private Label locationPositionLabel;
    @FXML
    private Label discoveredCountLabel;
    @FXML
    private Label processedCountLabel;
    @FXML
    private Label newCountLabel;
    @FXML
    private Label updatedCountLabel;
    @FXML
    private Label unchangedCountLabel;
    @FXML
    private Label missingCountLabel;
    @FXML
    private Label failedCountLabel;
    @FXML
    private Label summaryLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private ProgressIndicator scanProgressIndicator;

    public MediaLocationsController(MediaLocationsViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(
                viewModel,
                "Media locations view model must not be null"
        );
    }

    @FXML
    private void initialize() {
        configureTable();
        bindControls();
        viewModel.loadLocations();
    }

    public boolean requestClose() {
        boolean mayClose = true;

        if (viewModel.scanRunningProperty().get()) {
            final ButtonType keepOpen = new ButtonType(
                    "Continue scan and keep window open",
                    ButtonBar.ButtonData.CANCEL_CLOSE
            );
            final ButtonType cancelScan = new ButtonType(
                    "Cancel scan",
                    ButtonBar.ButtonData.OK_DONE
            );
            final Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "A scan is running.",
                    keepOpen,
                    cancelScan
            );
            alert.setTitle("Media Locations");
            alert.setHeaderText("Close Media Locations?");
            final ButtonType result = alert.showAndWait().orElse(keepOpen);

            if (result == cancelScan) {
                viewModel.cancelScan();
            }

            mayClose = false;
        }

        return mayClose;
    }

    private void configureTable() {
        locationsTable.setItems(viewModel.locations());
        locationsTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) ->
                        viewModel.selectedLocationProperty().set(newValue));
        directoryColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().path().toString()));
        directoryColumn.setCellFactory(column -> new javafx.scene.control
                .TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? "" : item);
                setTooltip(empty ? null : new Tooltip(item));
            }
        });
        enabledColumn.setCellValueFactory(data ->
                new SimpleBooleanProperty(data.getValue().enabled()));
        recursiveColumn.setCellValueFactory(data ->
                new SimpleBooleanProperty(data.getValue().recursive()));
        enabledColumn.setCellFactory(column -> editableBooleanCell(true));
        recursiveColumn.setCellFactory(column -> editableBooleanCell(false));
        lastScanColumn.setCellValueFactory(data ->
                new SimpleStringProperty(formatInstant(
                        data.getValue().lastScanCompletedAt()
                )));
        statusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().lastScanStatus().name()
                ));
        newColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Integer.toString(data.getValue().lastNewCount())
        ));
        updatedColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Integer.toString(data.getValue().lastUpdatedCount())
        ));
        unchangedColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Integer.toString(data.getValue().lastUnchangedCount())
        ));
        missingColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Integer.toString(data.getValue().lastMissingCount())
        ));
        failedColumn.setCellValueFactory(data -> new SimpleStringProperty(
                Integer.toString(data.getValue().lastFailedCount())
        ));
    }

    private javafx.scene.control.TableCell<MediaLocation, Boolean>
            editableBooleanCell(boolean enabledColumnCell) {

        final javafx.scene.control.cell.CheckBoxTableCell<MediaLocation, Boolean>
                cell = new javafx.scene.control.cell.CheckBoxTableCell<>();
        cell.setSelectedStateCallback(index -> {
            final MediaLocation location = locationsTable.getItems().get(index);
            final SimpleBooleanProperty property =
                    new SimpleBooleanProperty(enabledColumnCell
                            ? location.enabled()
                            : location.recursive());
            property.addListener((observable, oldValue, newValue) ->
                    updateLocationSetting(
                            location,
                            enabledColumnCell,
                            newValue
                    ));
            return property;
        });
        return cell;
    }

    private void bindControls() {
        emptyStateLabel.setText(EMPTY_STATE);
        emptyStateLabel.visibleProperty().bind(
                javafx.beans.binding.Bindings.isEmpty(viewModel.locations())
        );
        emptyAddFolderButton.visibleProperty().bind(
                emptyStateLabel.visibleProperty()
        );
        addFolderButton.setOnAction(event -> chooseAndAddFolder());
        emptyAddFolderButton.setOnAction(event -> chooseAndAddFolder());
        removeButton.setOnAction(event -> removeSelected());
        scanSelectedButton.setOnAction(event -> viewModel.scanSelected());
        scanAllButton.setOnAction(event -> viewModel.scanAllEnabled());
        cancelScanButton.setOnAction(event -> viewModel.cancelScan());
        closeButton.setOnAction(event -> {
            if (requestClose()) {
                closeButton.getScene().getWindow().hide();
            }
        });
        removeButton.disableProperty().bind(
                viewModel.selectedLocationProperty().isNull()
                        .or(viewModel.scanRunningProperty())
        );
        scanSelectedButton.disableProperty().bind(
                viewModel.selectedLocationProperty().isNull()
                        .or(viewModel.scanRunningProperty())
        );
        scanAllButton.disableProperty().bind(viewModel.scanRunningProperty());
        cancelScanButton.disableProperty().bind(
                viewModel.scanRunningProperty().not()
                        .or(viewModel.cancelRequestedProperty())
        );
        scanProgressIndicator.visibleProperty().bind(
                viewModel.scanRunningProperty()
        );
        phaseLabel.textProperty().bind(viewModel.currentPhaseProperty());
        currentDirectoryLabel.textProperty()
                .bind(viewModel.currentLocationProperty());
        currentFileLabel.textProperty().bind(viewModel.currentFileProperty());
        currentFilePathLabel.textProperty()
                .bind(viewModel.currentFilePathProperty());
        scanProgressIndicator.progressProperty()
                .bind(viewModel.progressProperty());
        locationPositionLabel.textProperty().bind(Bindings.createStringBinding(
                () -> viewModel.locationIndexProperty().get()
                        + " / "
                        + viewModel.totalLocationsProperty().get(),
                viewModel.locationIndexProperty(),
                viewModel.totalLocationsProperty()
        ));
        discoveredCountLabel.textProperty()
                .bind(viewModel.discoveredCountProperty().asString());
        processedCountLabel.textProperty()
                .bind(viewModel.processedCountProperty().asString());
        newCountLabel.textProperty()
                .bind(viewModel.newCountProperty().asString());
        updatedCountLabel.textProperty()
                .bind(viewModel.updatedCountProperty().asString());
        unchangedCountLabel.textProperty()
                .bind(viewModel.unchangedCountProperty().asString());
        missingCountLabel.textProperty()
                .bind(viewModel.missingCountProperty().asString());
        failedCountLabel.textProperty()
                .bind(viewModel.failedCountProperty().asString());
        summaryLabel.textProperty().bind(viewModel.scanSummaryProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
    }

    private void chooseAndAddFolder() {
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Add Media Location");

        if (lastChosenParent != null) {
            chooser.setInitialDirectory(lastChosenParent.toFile());
        }

        final File selected = chooser.showDialog(ownerWindow());

        if (selected != null) {
            final Path selectedPath = selected.toPath()
                    .toAbsolutePath()
                    .normalize();
            lastChosenParent = selectedPath.getParent();
            viewModel.addLocation(selectedPath);
        }
    }

    private void removeSelected() {
        final Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Physical files will not be deleted. Existing media records "
                        + "will remain.",
                ButtonType.CANCEL,
                ButtonType.OK
        );
        alert.setTitle("Remove Media Location");
        alert.setHeaderText("Remove this media location from JAVDB?");
        final ButtonType result = alert.showAndWait().orElse(ButtonType.CANCEL);

        if (result == ButtonType.OK) {
            viewModel.removeSelectedLocation();
        }
    }

    private void updateLocationSetting(
            MediaLocation location,
            boolean enabledColumnCell,
            boolean value) {

        if (enabledColumnCell) {
            viewModel.updateSettings(location, value, location.recursive());
        } else {
            viewModel.updateSettings(location, location.enabled(), value);
        }
    }

    private Window ownerWindow() {
        return addFolderButton.getScene() == null
                ? null
                : addFolderButton.getScene().getWindow();
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }
}

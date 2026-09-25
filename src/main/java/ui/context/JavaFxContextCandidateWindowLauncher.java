package ui.context;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import service.ContextPublisherResolutionService;
import service.ContextSeriesResolutionService;
import ui.control.EntityAutocompleteViewModel;
import ui.review.EntityDialogLauncher;

import java.io.IOException;
import java.util.Objects;

public final class JavaFxContextCandidateWindowLauncher
        implements ContextCandidateWindowLauncher {
    private final ContextCandidateViewModel viewModel;
    private final ContextPublisherResolutionService publisherResolutionService;
    private final ContextSeriesResolutionService seriesResolutionService;
    private final EntityDialogLauncher entityDialogLauncher;
    private final EntityAutocompleteViewModel publisherAutocomplete;
    private Stage stage;

    public JavaFxContextCandidateWindowLauncher(ContextCandidateViewModel viewModel,
            ContextPublisherResolutionService publisherResolutionService,
            ContextSeriesResolutionService seriesResolutionService,
            EntityDialogLauncher entityDialogLauncher,
            EntityAutocompleteViewModel publisherAutocomplete) {
        this.viewModel = Objects.requireNonNull(viewModel);
        this.publisherResolutionService = Objects.requireNonNull(publisherResolutionService);
        this.seriesResolutionService = Objects.requireNonNull(seriesResolutionService);
        this.entityDialogLauncher = Objects.requireNonNull(entityDialogLauncher);
        this.publisherAutocomplete = Objects.requireNonNull(publisherAutocomplete);
    }

    @Override
    public void open(Window owner) {
        try {
            if (stage == null || !stage.isShowing()) create(owner);
            stage.show();
            stage.toFront();
            viewModel.load();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to open Context Candidate Review.", exception);
        }
    }

    private void create(Window owner) throws IOException {
        final var resource = getClass().getResource("/ui/context-candidates.fxml");
        if (resource == null) throw new IOException("GUI resource not found");
        final FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(type -> {
            if (type.equals(ContextCandidateController.class)) {
                return new ContextCandidateController(viewModel,
                        publisherResolutionService, seriesResolutionService,
                        entityDialogLauncher,
                        publisherAutocomplete);
            }
            throw new IllegalArgumentException("Unexpected FXML controller");
        });
        final javafx.scene.Parent root = loader.load();
        stage = new Stage();
        stage.setTitle("Context Candidate Review");
        stage.initModality(Modality.NONE);
        if (owner != null) stage.initOwner(owner);
        stage.setScene(new Scene(root, 1100, 620));
    }
}

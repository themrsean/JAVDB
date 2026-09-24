package ui.media;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.util.Objects;

public final class JavaFxMediaLocationsWindowLauncher
        implements MediaLocationsWindowLauncher {
    private static final String VIEW_RESOURCE = "/ui/media-locations.fxml";
    private static final String WINDOW_TITLE = "Media Locations";
    private static final double WIDTH = 1_050.0;
    private static final double HEIGHT = 560.0;

    private final MediaLocationsViewModel viewModel;
    private Stage stage;
    private MediaLocationsController controller;

    public JavaFxMediaLocationsWindowLauncher(
            MediaLocationsViewModel viewModel) {

        this.viewModel = Objects.requireNonNull(
                viewModel,
                "Media locations view model must not be null"
        );
    }

    @Override
    public void open(Window owner) {
        try {
            if (stage == null || !stage.isShowing()) {
                createStage(owner);
            }

            stage.show();
            stage.toFront();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to open Media Locations.",
                    exception
            );
        }
    }

    private void createStage(Window owner) throws IOException {
        final URL resource =
                JavaFxMediaLocationsWindowLauncher.class.getResource(
                        VIEW_RESOURCE
                );

        if (resource == null) {
            throw new IOException(
                    "GUI resource not found: " + VIEW_RESOURCE
            );
        }

        final FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(type -> {
            if (MediaLocationsController.class.equals(type)) {
                controller = new MediaLocationsController(viewModel);
                return controller;
            }

            throw new IllegalArgumentException(
                    "Unsupported media locations controller: "
                            + type.getName()
            );
        });
        final Parent root = loader.load();
        stage = new Stage();
        stage.setTitle(WINDOW_TITLE);
        stage.initModality(Modality.NONE);

        if (owner != null) {
            stage.initOwner(owner);
        }

        stage.setScene(new Scene(root, WIDTH, HEIGHT));
        stage.setOnCloseRequest(event -> {
            if (!controller.requestClose()) {
                event.consume();
            }
        });
    }
}

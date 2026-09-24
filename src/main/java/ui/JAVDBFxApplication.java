package ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ui.review.ReviewQueueController;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.SQLException;

public final class JAVDBFxApplication extends Application {
    private static final String MAIN_VIEW_RESOURCE =
            "/ui/review-queue.fxml";
    private static final String STYLESHEET_RESOURCE =
            "/ui/javdb.css";
    private static final String WINDOW_TITLE = "JAVDB";
    private static final double INITIAL_WIDTH = 1_200.0;
    private static final double INITIAL_HEIGHT = 800.0;
    private static final double MINIMUM_WIDTH = 900.0;
    private static final double MINIMUM_HEIGHT = 600.0;
    private static final int FIRST_PARAMETER_INDEX = 0;

    private GuiApplicationContext context;

    @Override
    public void start(Stage stage) {
        GuiApplicationContext newContext = null;

        try {
            final Path databasePath = selectedDatabasePath();
            newContext = new GuiApplicationFactory().create(databasePath);
            context = newContext;
            final Parent root = loadMainView(newContext);
            final Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
            addStylesheet(scene);

            stage.setTitle(WINDOW_TITLE);
            stage.setMinWidth(MINIMUM_WIDTH);
            stage.setMinHeight(MINIMUM_HEIGHT);
            stage.setScene(scene);
            stage.setOnShown(event -> context.controller().loadInitialPage());
            stage.setOnCloseRequest(event -> {
                if (context.controller().mayClose()) {
                    context.close();
                } else {
                    event.consume();
                }
            });
            stage.show();
        } catch (IOException | SQLException exception) {
            if (newContext != null) {
                newContext.close();
            }
            System.err.println(
                    "Unable to start JAVDB GUI: " + exception.getMessage()
            );
            throw new IllegalStateException(
                    "Unable to start JAVDB GUI.",
                    exception
            );
        }
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    private Parent loadMainView(GuiApplicationContext newContext)
            throws IOException {

        final URL resource =
                JAVDBFxApplication.class.getResource(MAIN_VIEW_RESOURCE);

        if (resource == null) {
            throw new IOException(
                    "GUI resource not found: " + MAIN_VIEW_RESOURCE
            );
        }

        final FXMLLoader loader = new FXMLLoader(resource);
        loader.setControllerFactory(type -> {
            if (ReviewQueueController.class.equals(type)) {
                return newContext.controller();
            }

            throw new IllegalArgumentException(
                    "Unsupported GUI controller: " + type.getName()
            );
        });

        return loader.load();
    }

    private void addStylesheet(Scene scene) {
        final URL stylesheet =
                JAVDBFxApplication.class.getResource(STYLESHEET_RESOURCE);

        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
    }

    private Path selectedDatabasePath() {
        final String pathText = getParameters().getRaw()
                .get(FIRST_PARAMETER_INDEX);
        return Path.of(pathText).toAbsolutePath().normalize();
    }
}

package app;

import cli.CommandResult;
import javafx.application.Application;
import ui.JAVDBFxApplication;

import java.nio.file.Path;
import java.util.Objects;

public final class JavaFxGuiLauncher implements GuiLauncher {
    @Override
    public int launch(Path databasePath) {
        Objects.requireNonNull(databasePath, "Database path must not be null");
        Application.launch(
                JAVDBFxApplication.class,
                databasePath.toAbsolutePath().normalize().toString()
        );
        return CommandResult.SUCCESS;
    }
}

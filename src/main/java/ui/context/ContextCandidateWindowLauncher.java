package ui.context;

import javafx.stage.Window;

@FunctionalInterface
public interface ContextCandidateWindowLauncher {
    void open(Window owner);
}

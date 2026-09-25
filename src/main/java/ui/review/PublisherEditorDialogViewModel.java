package ui.review;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.Publisher;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class PublisherEditorDialogViewModel {
    public interface Creator {
        Publisher create(String name, List<String> aliases)
                throws SQLException;
    }

    private final Creator creator;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty aliasesText = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
    private final ObjectProperty<Publisher> result = new SimpleObjectProperty<>();

    public PublisherEditorDialogViewModel(
            Creator creator,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this(creator, backgroundExecutor, uiExecutor, "");
    }

    public PublisherEditorDialogViewModel(
            Creator creator,
            Executor backgroundExecutor,
            Executor uiExecutor,
            String initialName) {

        this.creator = Objects.requireNonNull(
                creator,
                "Publisher creator must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
        name.set(initialName == null ? "" : initialName);
        name.addListener((observable, oldValue, newValue) -> validate());
        validate();
    }

    public void save() {
        if (saveEnabled.get() && !saving.get()) {
            saving.set(true);
            errorMessage.set("");
            final String requestedName = name.get();
            final List<String> requestedAliases = aliases();

            try {
                backgroundExecutor.execute(() -> saveInBackground(
                        requestedName,
                        requestedAliases
                ));
            } catch (RejectedExecutionException exception) {
                saving.set(false);
                errorMessage.set(exception.getMessage());
            }
        }
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty aliasesTextProperty() {
        return aliasesText;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public BooleanProperty savingProperty() {
        return saving;
    }

    public BooleanProperty saveEnabledProperty() {
        return saveEnabled;
    }

    public ObjectProperty<Publisher> resultProperty() {
        return result;
    }

    private void saveInBackground(String requestedName, List<String> aliases) {
        try {
            final Publisher publisher = creator.create(requestedName, aliases);
            uiExecutor.execute(() -> {
                result.set(publisher);
                saving.set(false);
            });
        } catch (SQLException | IllegalArgumentException exception) {
            uiExecutor.execute(() -> {
                errorMessage.set(exception.getMessage());
                saving.set(false);
            });
        }
    }

    private void validate() {
        saveEnabled.set(name.get() != null && !name.get().trim().isEmpty());
    }

    private List<String> aliases() {
        return Arrays.stream(aliasesText.get().split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}

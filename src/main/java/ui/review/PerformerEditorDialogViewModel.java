package ui.review;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.Performer;
import model.PerformerCategory;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class PerformerEditorDialogViewModel {
    public interface Creator {
        Performer create(
                String name,
                List<String> aliases,
                PerformerCategory category) throws SQLException;
    }

    private final Creator creator;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty aliasesText = new SimpleStringProperty("");
    private final ObjectProperty<PerformerCategory> category =
            new SimpleObjectProperty<>(PerformerCategory.UNKNOWN);
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
    private final ObjectProperty<Performer> result = new SimpleObjectProperty<>();

    public PerformerEditorDialogViewModel(
            Creator creator,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this.creator = Objects.requireNonNull(
                creator,
                "Performer creator must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
        name.addListener((observable, oldValue, newValue) -> validate());
        category.addListener((observable, oldValue, newValue) -> validate());
        validate();
    }

    public void save() {
        if (saveEnabled.get() && !saving.get()) {
            saving.set(true);
            errorMessage.set("");
            final String requestedName = name.get();
            final List<String> requestedAliases = aliases();
            final PerformerCategory requestedCategory = category.get();

            try {
                backgroundExecutor.execute(() -> saveInBackground(
                        requestedName,
                        requestedAliases,
                        requestedCategory
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

    public ObjectProperty<PerformerCategory> categoryProperty() {
        return category;
    }

    public BooleanProperty saveEnabledProperty() {
        return saveEnabled;
    }

    public BooleanProperty savingProperty() {
        return saving;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public ObjectProperty<Performer> resultProperty() {
        return result;
    }

    private void saveInBackground(
            String requestedName,
            List<String> aliases,
            PerformerCategory requestedCategory) {

        try {
            final Performer performer = creator.create(
                    requestedName,
                    aliases,
                    requestedCategory
            );
            uiExecutor.execute(() -> {
                result.set(performer);
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
        saveEnabled.set(name.get() != null
                && !name.get().trim().isEmpty()
                && category.get() != null);
    }

    private List<String> aliases() {
        return Arrays.stream(aliasesText.get().split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}

package ui.review;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.Series;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class SeriesEditorDialogViewModel {
    public interface Creator {
        Series create(String title, UUID publisherId) throws SQLException;
    }

    private final Creator creator;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final StringProperty title = new SimpleStringProperty("");
    private final ObjectProperty<UUID> publisherId = new SimpleObjectProperty<>();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
    private final ObjectProperty<Series> result = new SimpleObjectProperty<>();

    public SeriesEditorDialogViewModel(
            Creator creator,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this(creator, backgroundExecutor, uiExecutor, "");
    }

    public SeriesEditorDialogViewModel(
            Creator creator,
            Executor backgroundExecutor,
            Executor uiExecutor,
            String initialTitle) {

        this.creator = Objects.requireNonNull(
                creator,
                "Series creator must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
        title.set(initialTitle == null ? "" : initialTitle);
        title.addListener((observable, oldValue, newValue) -> validate());
        publisherId.addListener((observable, oldValue, newValue) -> validate());
        validate();
    }

    public void save() {
        if (saveEnabled.get() && !saving.get()) {
            saving.set(true);
            errorMessage.set("");
            final String requestedTitle = title.get();
            final UUID requestedPublisherId = publisherId.get();

            try {
                backgroundExecutor.execute(() -> saveInBackground(
                        requestedTitle,
                        requestedPublisherId
                ));
            } catch (RejectedExecutionException exception) {
                saving.set(false);
                errorMessage.set(exception.getMessage());
            }
        }
    }

    public StringProperty titleProperty() {
        return title;
    }

    public ObjectProperty<UUID> publisherIdProperty() {
        return publisherId;
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

    public ObjectProperty<Series> resultProperty() {
        return result;
    }

    private void saveInBackground(String requestedTitle, UUID requestedPublisherId) {
        try {
            final Series series = creator.create(
                    requestedTitle,
                    requestedPublisherId
            );
            uiExecutor.execute(() -> {
                result.set(series);
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
        saveEnabled.set(title.get() != null
                && !title.get().trim().isEmpty()
                && publisherId.get() != null);
    }
}

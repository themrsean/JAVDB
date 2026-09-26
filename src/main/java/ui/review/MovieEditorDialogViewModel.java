package ui.review;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.Movie;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class MovieEditorDialogViewModel {
    public interface Creator {
        Movie create(
                String title,
                LocalDate releaseDate,
                UUID publisherId,
                boolean compilation) throws SQLException;
    }

    private final Creator creator;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty releaseDateText = new SimpleStringProperty("");
    private final ObjectProperty<UUID> publisherId = new SimpleObjectProperty<>();
    private final BooleanProperty compilation = new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
    private final ObjectProperty<Movie> result = new SimpleObjectProperty<>();

    public MovieEditorDialogViewModel(
            Creator creator,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this(creator, backgroundExecutor, uiExecutor, "");
    }

    public MovieEditorDialogViewModel(
            Creator creator,
            Executor backgroundExecutor,
            Executor uiExecutor,
            String initialTitle) {

        this(creator, backgroundExecutor, uiExecutor, initialTitle, null);
    }

    public MovieEditorDialogViewModel(
            Creator creator,
            Executor backgroundExecutor,
            Executor uiExecutor,
            String initialTitle,
            UUID initialPublisherId) {

        this.creator = Objects.requireNonNull(
                creator,
                "Movie creator must not be null"
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
        publisherId.set(initialPublisherId);
        title.addListener((observable, oldValue, newValue) -> validate());
        releaseDateText.addListener((observable, oldValue, newValue) ->
                validate());
        publisherId.addListener((observable, oldValue, newValue) -> validate());
        validate();
    }

    public void save() {
        if (saveEnabled.get() && !saving.get()) {
            saving.set(true);
            errorMessage.set("");
            final String requestedTitle = title.get();
            final LocalDate requestedReleaseDate = parsedReleaseDate();
            final UUID requestedPublisherId = publisherId.get();
            final boolean requestedCompilation = compilation.get();

            try {
                backgroundExecutor.execute(() -> saveInBackground(
                        requestedTitle,
                        requestedReleaseDate,
                        requestedPublisherId,
                        requestedCompilation
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

    public StringProperty releaseDateTextProperty() {
        return releaseDateText;
    }

    public ObjectProperty<UUID> publisherIdProperty() {
        return publisherId;
    }

    public BooleanProperty compilationProperty() {
        return compilation;
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

    public ObjectProperty<Movie> resultProperty() {
        return result;
    }

    private void saveInBackground(
            String requestedTitle,
            LocalDate requestedReleaseDate,
            UUID requestedPublisherId,
            boolean requestedCompilation) {

        try {
            final Movie movie = creator.create(
                    requestedTitle,
                    requestedReleaseDate,
                    requestedPublisherId,
                    requestedCompilation
            );
            uiExecutor.execute(() -> {
                result.set(movie);
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
                && publisherId.get() != null
                && releaseDateValid());
    }

    private boolean releaseDateValid() {
        return releaseDateText.get() == null
                || releaseDateText.get().trim().isEmpty()
                || parsedReleaseDate() != null;
    }

    private LocalDate parsedReleaseDate() {
        LocalDate date = null;

        if (releaseDateText.get() != null
                && !releaseDateText.get().trim().isEmpty()) {
            try {
                date = LocalDate.parse(releaseDateText.get().trim());
            } catch (DateTimeParseException exception) {
                date = null;
            }
        }

        return date;
    }
}

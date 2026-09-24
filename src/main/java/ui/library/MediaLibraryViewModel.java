package ui.library;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import repository.MediaLibraryAssignmentState;
import repository.MediaLibraryFilter;
import repository.MediaLibraryMetadataQuality;
import service.MediaLibraryDataSource;
import service.MediaLibraryDetails;
import service.MediaLibraryPage;
import service.MediaLibraryRow;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class MediaLibraryViewModel {
    private static final int FIRST_OFFSET = 0;

    private final MediaLibraryDataSource dataSource;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final ObservableList<MediaLibraryRow> rows =
            FXCollections.observableArrayList();
    private final ObjectProperty<MediaLibraryRow> selectedRow =
            new SimpleObjectProperty<>();
    private final ObjectProperty<MediaLibraryDetails> selectedDetails =
            new SimpleObjectProperty<>();
    private final StringProperty contains = new SimpleStringProperty("");
    private final StringProperty directory = new SimpleStringProperty("");
    private final StringProperty width = new SimpleStringProperty("");
    private final StringProperty height = new SimpleStringProperty("");
    private final StringProperty minWidth = new SimpleStringProperty("");
    private final StringProperty minHeight = new SimpleStringProperty("");
    private final ObjectProperty<MediaLibraryAssignmentState> assignment =
            new SimpleObjectProperty<>(MediaLibraryAssignmentState.ALL);
    private final ObjectProperty<MediaLibraryMetadataQuality> quality =
            new SimpleObjectProperty<>(MediaLibraryMetadataQuality.ALL);
    private final IntegerProperty pageSize =
            new SimpleIntegerProperty(MediaLibraryFilter.DEFAULT_LIMIT);
    private final IntegerProperty offset = new SimpleIntegerProperty(0);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty detailLoading = new SimpleBooleanProperty(false);
    private final BooleanProperty previousAvailable =
            new SimpleBooleanProperty(false);
    private final BooleanProperty nextAvailable =
            new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty detailErrorMessage = new SimpleStringProperty("");
    private final StringProperty emptyMessage = new SimpleStringProperty("");
    private final AtomicLong pageGeneration = new AtomicLong();
    private final AtomicLong detailGeneration = new AtomicLong();
    private boolean disposed;

    public MediaLibraryViewModel(
            MediaLibraryDataSource dataSource,
            Executor backgroundExecutor,
            Executor uiExecutor) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor);
        this.uiExecutor = Objects.requireNonNull(uiExecutor);
        selectedRow.addListener((observable, oldValue, newValue) ->
                loadSelectedDetails(newValue));
        pageSize.addListener((observable, oldValue, newValue) ->
                offset.set(FIRST_OFFSET));
    }

    public void load() {
        if (disposed) {
            return;
        }
        final MediaLibraryFilter filter = currentFilter();
        final long generation = pageGeneration.incrementAndGet();
        loading.set(true);
        errorMessage.set("");
        try {
            backgroundExecutor.execute(() -> loadPage(generation, filter));
        } catch (RejectedExecutionException exception) {
            applyPageFailure(generation);
        }
    }

    public void applyFilters() {
        offset.set(FIRST_OFFSET);
        load();
    }

    public void clearFilters() {
        contains.set("");
        directory.set("");
        width.set("");
        height.set("");
        minWidth.set("");
        minHeight.set("");
        assignment.set(MediaLibraryAssignmentState.ALL);
        quality.set(MediaLibraryMetadataQuality.ALL);
        offset.set(FIRST_OFFSET);
        load();
    }

    public void nextPage() {
        offset.set(offset.get() + pageSize.get());
        load();
    }

    public void previousPage() {
        offset.set(Math.max(FIRST_OFFSET, offset.get() - pageSize.get()));
        load();
    }

    public void dispose() {
        disposed = true;
        pageGeneration.incrementAndGet();
        detailGeneration.incrementAndGet();
    }

    private void loadPage(long generation, MediaLibraryFilter filter) {
        try {
            final MediaLibraryPage page = dataSource.loadPage(filter);
            uiExecutor.execute(() -> applyPage(generation, page));
        } catch (SQLException | IllegalArgumentException exception) {
            uiExecutor.execute(() -> applyPageFailure(generation));
        }
    }

    private void applyPage(long generation, MediaLibraryPage page) {
        if (disposed || generation != pageGeneration.get()) {
            return;
        }
        final java.util.UUID selectedId = selectedRow.get() == null
                ? null : selectedRow.get().mediaId();
        rows.setAll(page.rows());
        previousAvailable.set(offset.get() > FIRST_OFFSET);
        nextAvailable.set(page.hasNextPage());
        emptyMessage.set(rows.isEmpty()
                ? emptyText(page.filter()) : "");
        MediaLibraryRow selection = rows.stream()
                .filter(row -> row.mediaId().equals(selectedId))
                .findFirst().orElse(rows.isEmpty() ? null : rows.getFirst());
        selectedRow.set(selection);
        if (selection != null && selectedId != null
                && selection.mediaId().equals(selectedId)) {
            loadSelectedDetails(selection);
        }
        loading.set(false);
    }

    private void applyPageFailure(long generation) {
        if (!disposed && generation == pageGeneration.get()) {
            rows.clear();
            selectedRow.set(null);
            errorMessage.set("Unable to load the media library.");
            loading.set(false);
        }
    }

    private void loadSelectedDetails(MediaLibraryRow row) {
        final long generation = detailGeneration.incrementAndGet();
        selectedDetails.set(null);
        detailErrorMessage.set("");
        detailLoading.set(row != null);
        if (row != null && !disposed) {
            try {
                backgroundExecutor.execute(() -> loadDetails(
                        generation, row.mediaId()
                ));
            } catch (RejectedExecutionException exception) {
                applyDetailFailure(generation);
            }
        }
    }

    private void loadDetails(long generation, java.util.UUID mediaId) {
        try {
            final MediaLibraryDetails details = dataSource.loadDetails(mediaId);
            uiExecutor.execute(() -> applyDetails(generation, details));
        } catch (SQLException | IllegalArgumentException exception) {
            uiExecutor.execute(() -> applyDetailFailure(generation));
        }
    }

    private void applyDetails(long generation, MediaLibraryDetails details) {
        if (!disposed && generation == detailGeneration.get()) {
            selectedDetails.set(details);
            detailLoading.set(false);
        }
    }

    private void applyDetailFailure(long generation) {
        if (!disposed && generation == detailGeneration.get()) {
            selectedDetails.set(null);
            detailErrorMessage.set("Unable to load media details.");
            detailLoading.set(false);
        }
    }

    private String emptyText(MediaLibraryFilter filter) {
        final boolean filtered = filter.contains() != null
                || filter.directory() != null || filter.width() != null
                || filter.height() != null || filter.minWidth() != null
                || filter.minHeight() != null
                || filter.assignmentState() != MediaLibraryAssignmentState.ALL
                || filter.metadataQuality() != MediaLibraryMetadataQuality.ALL;
        return filtered ? "No media records match the current filters."
                : "No media records have been scanned.";
    }

    private MediaLibraryFilter currentFilter() {
        return new MediaLibraryFilter(
                blankToNull(contains.get()), pathOrNull(directory.get()),
                assignment.get(), positiveInteger(width.get()),
                positiveInteger(height.get()), positiveInteger(minWidth.get()),
                positiveInteger(minHeight.get()), quality.get(),
                pageSize.get(), offset.get()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Path pathOrNull(String value) {
        final String text = blankToNull(value);
        return text == null ? null : Path.of(text).toAbsolutePath().normalize();
    }

    private Integer positiveInteger(String value) {
        final String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            final int number = Integer.parseInt(text);
            if (number < 1) {
                throw new IllegalArgumentException();
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Dimension filters must be positive whole numbers."
            );
        }
    }

    public ObservableList<MediaLibraryRow> rows() { return rows; }
    public ObjectProperty<MediaLibraryRow> selectedRow() { return selectedRow; }
    public ObjectProperty<MediaLibraryDetails> selectedDetails() {
        return selectedDetails;
    }
    public StringProperty containsProperty() { return contains; }
    public StringProperty directoryProperty() { return directory; }
    public StringProperty widthProperty() { return width; }
    public StringProperty heightProperty() { return height; }
    public StringProperty minWidthProperty() { return minWidth; }
    public StringProperty minHeightProperty() { return minHeight; }
    public ObjectProperty<MediaLibraryAssignmentState> assignmentProperty() {
        return assignment;
    }
    public ObjectProperty<MediaLibraryMetadataQuality> qualityProperty() {
        return quality;
    }
    public IntegerProperty pageSizeProperty() { return pageSize; }
    public IntegerProperty offsetProperty() { return offset; }
    public BooleanProperty loadingProperty() { return loading; }
    public BooleanProperty detailLoadingProperty() { return detailLoading; }
    public BooleanProperty previousAvailableProperty() { return previousAvailable; }
    public BooleanProperty nextAvailableProperty() { return nextAvailable; }
    public StringProperty errorMessageProperty() { return errorMessage; }
    public StringProperty detailErrorMessageProperty() { return detailErrorMessage; }
    public StringProperty emptyMessageProperty() { return emptyMessage; }
}

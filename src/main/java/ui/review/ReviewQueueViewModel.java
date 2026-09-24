package ui.review;

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
import service.ReviewDetails;
import service.ReviewMatchStatusFilter;
import service.ReviewQueueFilter;
import service.ReviewQueueItem;
import service.ReviewQueuePage;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class ReviewQueueViewModel {
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int DEFAULT_OFFSET = 0;
    private static final int MINIMUM_FILTER_VALUE = 1;
    private static final int FIRST_ROW_INDEX = 0;

    private final ReviewQueueDataSource dataSource;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final ObservableList<ReviewQueueItem> rows =
            FXCollections.observableArrayList();
    private final ObjectProperty<ReviewQueueItem> selectedRow =
            new SimpleObjectProperty<>();
    private final ObjectProperty<ReviewDetails> selectedDetails =
            new SimpleObjectProperty<>();
    private final StringProperty filenameFilter = new SimpleStringProperty("");
    private final StringProperty directoryFilter = new SimpleStringProperty("");
    private final StringProperty widthFilter = new SimpleStringProperty("");
    private final StringProperty heightFilter = new SimpleStringProperty("");
    private final StringProperty minWidthFilter = new SimpleStringProperty("");
    private final StringProperty minHeightFilter = new SimpleStringProperty("");
    private final ObjectProperty<ReviewMatchStatusFilter> statusFilter =
            new SimpleObjectProperty<>(ReviewMatchStatusFilter.ALL);
    private final IntegerProperty pageSize =
            new SimpleIntegerProperty(DEFAULT_PAGE_SIZE);
    private final IntegerProperty offset =
            new SimpleIntegerProperty(DEFAULT_OFFSET);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty previousAvailable =
            new SimpleBooleanProperty(false);
    private final BooleanProperty nextAvailable =
            new SimpleBooleanProperty(false);
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty emptyMessage = new SimpleStringProperty("");
    private final IntegerProperty basePageSize = new SimpleIntegerProperty(0);
    private final AtomicLong generation = new AtomicLong();
    private final Map<UUID, ReviewDetails> detailByMediaId = new HashMap<>();

    private boolean disposed;

    public ReviewQueueViewModel(
            ReviewQueueDataSource dataSource,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this.dataSource = Objects.requireNonNull(
                dataSource,
                "Review queue data source must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
        selectedRow.addListener((observable, oldValue, newValue) ->
                updateSelectedDetails(newValue));
        pageSize.addListener((observable, oldValue, newValue) ->
                offset.set(DEFAULT_OFFSET));
    }

    public void load() {
        if (!disposed) {
            final long requestGeneration = generation.incrementAndGet();
            final ReviewQueueFilter filter = currentFilter();
            loading.set(true);
            errorMessage.set("");

            try {
                backgroundExecutor.execute(() -> loadInBackground(
                        requestGeneration,
                        filter
                ));
            } catch (RejectedExecutionException exception) {
                loading.set(false);
                errorMessage.set(exception.getMessage());
            }
        }
    }

    public void applyFilters() {
        offset.set(DEFAULT_OFFSET);
        load();
    }

    public void clearFilters() {
        filenameFilter.set("");
        directoryFilter.set("");
        widthFilter.set("");
        heightFilter.set("");
        minWidthFilter.set("");
        minHeightFilter.set("");
        statusFilter.set(ReviewMatchStatusFilter.ALL);
        offset.set(DEFAULT_OFFSET);
        load();
    }

    public void nextPage() {
        offset.set(offset.get() + pageSize.get());
        load();
    }

    public void previousPage() {
        offset.set(Math.max(DEFAULT_OFFSET, offset.get() - pageSize.get()));
        load();
    }

    public void dispose() {
        disposed = true;
        generation.incrementAndGet();
    }

    public ObservableList<ReviewQueueItem> rows() {
        return rows;
    }

    public ObjectProperty<ReviewQueueItem> selectedRow() {
        return selectedRow;
    }

    public ObjectProperty<ReviewDetails> selectedDetails() {
        return selectedDetails;
    }

    public StringProperty filenameFilter() {
        return filenameFilter;
    }

    public StringProperty directoryFilter() {
        return directoryFilter;
    }

    public StringProperty widthFilter() {
        return widthFilter;
    }

    public StringProperty heightFilter() {
        return heightFilter;
    }

    public StringProperty minWidthFilter() {
        return minWidthFilter;
    }

    public StringProperty minHeightFilter() {
        return minHeightFilter;
    }

    public ObjectProperty<ReviewMatchStatusFilter> statusFilter() {
        return statusFilter;
    }

    public IntegerProperty pageSize() {
        return pageSize;
    }

    public IntegerProperty offset() {
        return offset;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty previousAvailable() {
        return previousAvailable;
    }

    public BooleanProperty nextAvailable() {
        return nextAvailable;
    }

    public String errorMessage() {
        return errorMessage.get();
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty emptyMessageProperty() {
        return emptyMessage;
    }

    public IntegerProperty basePageSize() {
        return basePageSize;
    }

    private void loadInBackground(
            long requestGeneration,
            ReviewQueueFilter filter) {

        try {
            final ReviewQueuePage page = dataSource.loadPage(filter);
            uiExecutor.execute(() -> applyPage(requestGeneration, page));
        } catch (SQLException | IllegalArgumentException exception) {
            uiExecutor.execute(() -> applyFailure(
                    requestGeneration,
                    exception
            ));
        }
    }

    private void applyPage(long requestGeneration, ReviewQueuePage page) {
        if (!disposed && requestGeneration == generation.get()) {
            final UUID selectedMediaId = selectedRow.get() == null
                    ? null
                    : selectedRow.get().mediaId();
            rows.setAll(page.items());
            detailByMediaId.clear();

            for (ReviewDetails detail : page.details()) {
                detailByMediaId.put(detail.mediaId(), detail);
            }

            basePageSize.set(page.basePageSize());
            previousAvailable.set(offset.get() > DEFAULT_OFFSET);
            nextAvailable.set(page.basePageSize() >= pageSize.get());
            emptyMessage.set(rows.isEmpty() ? "No unassigned media." : "");
            restoreSelection(selectedMediaId);
            loading.set(false);
        }
    }

    private void applyFailure(long requestGeneration, Exception exception) {
        if (!disposed && requestGeneration == generation.get()) {
            rows.clear();
            detailByMediaId.clear();
            selectedRow.set(null);
            selectedDetails.set(null);
            errorMessage.set(exception.getMessage());
            loading.set(false);
        }
    }

    private void restoreSelection(UUID selectedMediaId) {
        ReviewQueueItem nextSelection = null;

        if (selectedMediaId != null) {
            nextSelection = rows.stream()
                    .filter(row -> selectedMediaId.equals(row.mediaId()))
                    .findFirst()
                    .orElse(null);
        }

        if (nextSelection == null && !rows.isEmpty()) {
            nextSelection = rows.get(FIRST_ROW_INDEX);
        }

        selectedRow.set(nextSelection);
    }

    private void updateSelectedDetails(ReviewQueueItem item) {
        if (item == null) {
            selectedDetails.set(null);
        } else {
            selectedDetails.set(detailByMediaId.get(item.mediaId()));
        }
    }

    private ReviewQueueFilter currentFilter() {
        return new ReviewQueueFilter(
                blankToNull(filenameFilter.get()),
                pathOrNull(directoryFilter.get()),
                integerOrNull(widthFilter.get()),
                integerOrNull(heightFilter.get()),
                integerOrNull(minWidthFilter.get()),
                integerOrNull(minHeightFilter.get()),
                pageSize.get(),
                offset.get(),
                statusFilter.get()
        );
    }

    private Path pathOrNull(String value) {
        final String normalized = blankToNull(value);
        return normalized == null ? null : Path.of(normalized);
    }

    private Integer integerOrNull(String value) {
        Integer integer = null;
        final String normalized = blankToNull(value);

        if (normalized != null) {
            integer = Integer.valueOf(normalized);

            if (integer < MINIMUM_FILTER_VALUE) {
                throw new IllegalArgumentException(
                        "Numeric filters must be positive."
                );
            }
        }

        return integer;
    }

    private String blankToNull(String value) {
        String normalized = null;

        if (value != null && !value.isBlank()) {
            normalized = value.trim();
        }

        return normalized;
    }
}

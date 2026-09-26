package ui.review;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import model.VerificationStatus;
import service.CanonicalRenameDisplay;
import service.EditableSceneReviewDraft;
import service.FilenameInterpretation;
import service.FilenameInterpretationConsensus;
import service.MatchSource;
import service.SceneReviewDraft;
import service.SceneReviewRenameChoice;
import service.SceneReviewSaveRequest;
import service.SceneReviewSaveResult;
import service.SceneReviewSaveStatus;
import service.SceneReviewSaver;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

public final class SceneReviewEditorViewModel {
    public interface RenamePreviewProvider {
        CanonicalRenameDisplay preview(SceneReviewDraft draft)
                throws SQLException;
    }

    public interface AliasConfirmation {
        boolean confirm(String candidateText, String primaryName);
    }

    public interface AliasPersistence {
        void addPerformerAlias(UUID performerId, String alias)
                throws SQLException;

        void addPublisherAlias(UUID publisherId, String alias)
                throws SQLException;
    }

    private static final String TITLE_REQUIRED_MESSAGE =
            "Scene title is required.";
    private static final String DATE_INVALID_MESSAGE =
            "Release date must use yyyy-MM-dd.";
    private static final String RENAME_FAILED_TEXT =
            "Saved, but rename failed. Scene needs review.";
    private static final String APPLY_INTERPRETATION_MESSAGE =
            "Apply an interpretation before saving as verified.";

    private final SceneReviewSaver saver;
    private final SceneReviewDraftLoader draftLoader;
    private final AliasConfirmation aliasConfirmation;
    private final AliasPersistence aliasPersistence;
    private final RenamePreviewProvider renamePreviewProvider;
    private final Executor backgroundExecutor;
    private final Executor uiExecutor;
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty validationMessage = new SimpleStringProperty("");
    private final StringProperty saveMessage = new SimpleStringProperty("");
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final BooleanProperty saving = new SimpleBooleanProperty(false);
    private final BooleanProperty saveEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty verifiedSaveEnabled =
            new SimpleBooleanProperty(false);
    private final BooleanProperty saveAndRenameEnabled =
            new SimpleBooleanProperty(false);
    private final BooleanProperty previewLoading = new SimpleBooleanProperty(false);
    private final ObjectProperty<SceneReviewSaveResult> lastSaveResult =
            new SimpleObjectProperty<>();
    private final StringProperty previewStatus = new SimpleStringProperty("");
    private final StringProperty previewCurrentFilename =
            new SimpleStringProperty("");
    private final StringProperty previewProposedFilename =
            new SimpleStringProperty("");
    private final StringProperty previewProposedPath =
            new SimpleStringProperty("");
    private final StringProperty previewError = new SimpleStringProperty("");
    private final StringProperty releaseDateText =
            new SimpleStringProperty("");
    private final StringProperty code = new SimpleStringProperty("");
    private final StringProperty season = new SimpleStringProperty("");
    private final StringProperty episode = new SimpleStringProperty("");
    private final ObjectProperty<UUID> selectedPublisherId =
            new SimpleObjectProperty<>();
    private final ObjectProperty<UUID> selectedSeriesId =
            new SimpleObjectProperty<>();
    private final ObjectProperty<UUID> selectedMovieId =
            new SimpleObjectProperty<>();
    private final ObjectProperty<UUID> explicitOriginalMovieOverrideId =
            new SimpleObjectProperty<>();
    private final ObservableList<SelectedPerformer> selectedPerformers =
            FXCollections.observableArrayList();

    private EditableSceneReviewDraft editableDraft;
    private boolean loadingDraft;
    private boolean interpretationSelectionRequired;
    private boolean publisherResolutionRequired;
    private boolean seriesResolutionRequired;
    private boolean movieResolutionRequired;
    private final AtomicLong draftGeneration = new AtomicLong();
    private final AtomicLong previewGeneration = new AtomicLong();

    public SceneReviewEditorViewModel(
            SceneReviewSaver saver,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this(saver, null, backgroundExecutor, uiExecutor);
    }

    public SceneReviewEditorViewModel(
            SceneReviewSaver saver,
            SceneReviewDraftLoader draftLoader,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this(
                saver,
                draftLoader,
                (candidateText, primaryName) -> false,
                new AliasPersistence() {
                    @Override
                    public void addPerformerAlias(UUID performerId, String alias) {
                    }

                    @Override
                    public void addPublisherAlias(UUID publisherId, String alias) {
                    }
                },
                backgroundExecutor,
                uiExecutor
        );
    }

    public SceneReviewEditorViewModel(
            SceneReviewSaver saver,
            SceneReviewDraftLoader draftLoader,
            AliasConfirmation aliasConfirmation,
            AliasPersistence aliasPersistence,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this(
                saver,
                draftLoader,
                aliasConfirmation,
                aliasPersistence,
                draft -> new CanonicalRenameDisplay(
                        "REVIEW_REQUIRED",
                        "",
                        "",
                        java.nio.file.Path.of(""),
                        false,
                        false,
                        false,
                        java.util.List.of(),
                        "Rename preview is not configured."
                ),
                backgroundExecutor,
                uiExecutor
        );
    }

    public SceneReviewEditorViewModel(
            SceneReviewSaver saver,
            SceneReviewDraftLoader draftLoader,
            AliasConfirmation aliasConfirmation,
            AliasPersistence aliasPersistence,
            RenamePreviewProvider renamePreviewProvider,
            Executor backgroundExecutor,
            Executor uiExecutor) {

        this.saver = Objects.requireNonNull(
                saver,
                "Scene review saver must not be null"
        );
        this.draftLoader = draftLoader;
        this.aliasConfirmation = Objects.requireNonNull(
                aliasConfirmation,
                "Alias confirmation must not be null"
        );
        this.aliasPersistence = Objects.requireNonNull(
                aliasPersistence,
                "Alias persistence must not be null"
        );
        this.renamePreviewProvider = Objects.requireNonNull(
                renamePreviewProvider,
                "Rename preview provider must not be null"
        );
        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor,
                "Background executor must not be null"
        );
        this.uiExecutor = Objects.requireNonNull(
                uiExecutor,
                "UI executor must not be null"
        );
        title.addListener((observable, oldValue, newValue) -> {
            updateDirty();
            validate();
            schedulePreviewIfEditing();
        });
        releaseDateText.addListener((observable, oldValue, newValue) -> {
            updateDirty();
            validate();
            schedulePreviewIfEditing();
        });
        code.addListener((observable, oldValue, newValue) -> {
            updateDirty();
            schedulePreviewIfEditing();
        });
        season.addListener((observable, oldValue, newValue) -> {
            updateDirty();
            schedulePreviewIfEditing();
        });
        episode.addListener((observable, oldValue, newValue) -> {
            updateDirty();
            schedulePreviewIfEditing();
        });
        selectedPublisherId.addListener((observable, oldValue, newValue) ->
                updateDirtyAndPreview());
        selectedSeriesId.addListener((observable, oldValue, newValue) ->
                updateDirtyAndPreview());
        selectedMovieId.addListener((observable, oldValue, newValue) ->
                updateDirtyAndPreview());
        explicitOriginalMovieOverrideId.addListener(
                (observable, oldValue, newValue) -> updateDirtyAndPreview()
        );
        selectedPerformers.addListener(
                (javafx.collections.ListChangeListener<SelectedPerformer>)
                        change -> updateDirtyAndPreview()
        );
        validate();
    }

    public void loadUnassignedDetails(service.ReviewDetails details) {
        if (draftLoader == null) {
            throw new IllegalStateException("Draft loader is not configured.");
        }

        loadDraft(draftLoader.fromReviewDetails(details));
    }

    public void loadExistingScene(java.util.UUID sceneId) {
        if (draftLoader == null) {
            throw new IllegalStateException("Draft loader is not configured.");
        }

        saving.set(true);
        saveMessage.set("");
        final long generation = draftGeneration.incrementAndGet();

        try {
            backgroundExecutor.execute(() -> loadExistingSceneInBackground(
                    sceneId,
                    generation
            ));
        } catch (RejectedExecutionException exception) {
            saving.set(false);
            saveMessage.set(exception.getMessage());
        }
    }

    public EditableSceneReviewDraft currentDraft() {
        return editableDraft;
    }

    public void loadDraft(EditableSceneReviewDraft draft) {
        draftGeneration.incrementAndGet();
        loadingDraft = true;
        editableDraft = Objects.requireNonNull(
                draft,
                "Editable scene review draft must not be null"
        );
        title.set(draft.draft().title());
        releaseDateText.set(draft.draft().releaseDate() == null
                ? ""
                : draft.draft().releaseDate().toString());
        code.set(nullToEmpty(draft.draft().code()));
        season.set(nullToEmpty(draft.draft().season()));
        episode.set(nullToEmpty(draft.draft().episode()));
        selectedPublisherId.set(draft.draft().publisherId());
        selectedSeriesId.set(draft.draft().seriesId());
        selectedMovieId.set(draft.draft().selectedMovieId());
        explicitOriginalMovieOverrideId.set(
                draft.draft().explicitOriginalMovieOverrideId()
        );
        selectedPerformers.setAll(draft.draft().performerIds()
                .stream()
                .map(id -> new SelectedPerformer(id, id.toString()))
                .toList());
        dirty.set(false);
        saveMessage.set("");
        lastSaveResult.set(null);
        loadingDraft = false;
        configureInterpretationRequirements(
                FilenameInterpretationConsensus.initial(
                        draft.matchStatus(),
                        draft.alternatives().isEmpty()
                                ? null : draft.alternatives().getFirst(),
                        draft.alternatives()
                ),
                draft.matchStatus() == service.FilenameMatchStatus.AMBIGUOUS
        );
        validate();
        schedulePreview();
    }

    public void clearDraft() {
        loadingDraft = true;
        editableDraft = null;
        draftGeneration.incrementAndGet();
        previewGeneration.incrementAndGet();
        saving.set(false);
        title.set("");
        releaseDateText.set("");
        code.set("");
        season.set("");
        episode.set("");
        selectedPublisherId.set(null);
        selectedSeriesId.set(null);
        selectedMovieId.set(null);
        explicitOriginalMovieOverrideId.set(null);
        selectedPerformers.clear();
        previewStatus.set("");
        previewCurrentFilename.set("");
        previewProposedFilename.set("");
        previewProposedPath.set("");
        previewError.set("");
        previewLoading.set(false);
        saveAndRenameEnabled.set(false);
        saveMessage.set("");
        lastSaveResult.set(null);
        dirty.set(false);
        configureInterpretationRequirements(null, false);
        loadingDraft = false;
        validate();
    }

    public void setSelectedPerformerDisplayNames(List<String> displayNames) {
        final List<UUID> ids = selectedPerformers.stream()
                .map(SelectedPerformer::id)
                .toList();
        selectedPerformers.setAll(java.util.stream.IntStream.range(0, ids.size())
                .mapToObj(index -> new SelectedPerformer(
                        ids.get(index),
                        index < displayNames.size()
                                ? displayNames.get(index)
                                : ids.get(index).toString()
                ))
                .toList());
    }

    public void resetChanges() {
        if (editableDraft != null) {
            loadDraft(editableDraft);
        }
    }

    public void applyAlternative(FilenameInterpretation interpretation) {
        if (editableDraft != null && draftLoader != null
                && interpretation != null) {
            final EditableSceneReviewDraft applied =
                    draftLoader.applyInterpretation(
                            editableDraft,
                            interpretation
                    );
            loadDraft(applied);
            configureInterpretationRequirements(interpretation, false);
            validate();
            dirty.set(true);
        }
    }

    public void saveWithoutRename() {
        submitSave(
                SceneReviewRenameChoice.DO_NOT_RENAME,
                VerificationStatus.VERIFIED
        );
    }

    public void saveAndRename() {
        submitSave(
                SceneReviewRenameChoice.RENAME,
                VerificationStatus.VERIFIED
        );
    }

    public void saveAsNeedsReview() {
        submitSave(
                SceneReviewRenameChoice.DO_NOT_RENAME,
                VerificationStatus.NEEDS_REVIEW
        );
    }

    public void addPerformer(UUID performerId, String displayName) {
        Objects.requireNonNull(performerId, "Performer ID must not be null");
        final boolean alreadySelected = selectedPerformers.stream()
                .anyMatch(performer -> performer.id().equals(performerId));

        if (!alreadySelected) {
            selectedPerformers.add(new SelectedPerformer(
                    performerId,
                    displayName
            ));
        }
    }

    public void removePerformer(UUID performerId) {
        Objects.requireNonNull(performerId, "Performer ID must not be null");
        selectedPerformers.removeIf(
                performer -> performer.id().equals(performerId)
        );
    }

    public void resolvePerformerCandidate(
            String candidateText,
            UUID performerId,
            String primaryName) {

        addPerformer(performerId, primaryName);
        maybePersistAlias(
                candidateText,
                primaryName,
                () -> aliasPersistence.addPerformerAlias(
                        performerId,
                        candidateText
                )
        );
    }

    public void resolvePublisherCandidate(
            String candidateText,
            UUID publisherId,
            String primaryName) {

        selectedPublisherId.set(Objects.requireNonNull(
                publisherId,
                "Publisher ID must not be null"
        ));
        maybePersistAlias(
                candidateText,
                primaryName,
                () -> aliasPersistence.addPublisherAlias(
                        publisherId,
                        candidateText
                )
        );
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty releaseDateTextProperty() {
        return releaseDateText;
    }

    public StringProperty codeProperty() {
        return code;
    }

    public StringProperty seasonProperty() {
        return season;
    }

    public StringProperty episodeProperty() {
        return episode;
    }

    public ObjectProperty<UUID> selectedPublisherIdProperty() {
        return selectedPublisherId;
    }

    public ObjectProperty<UUID> selectedSeriesIdProperty() {
        return selectedSeriesId;
    }

    public ObjectProperty<UUID> selectedMovieIdProperty() {
        return selectedMovieId;
    }

    public ObjectProperty<UUID> explicitOriginalMovieOverrideIdProperty() {
        return explicitOriginalMovieOverrideId;
    }

    public ObservableList<SelectedPerformer> selectedPerformers() {
        return selectedPerformers;
    }

    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    public BooleanProperty saveEnabledProperty() {
        return saveEnabled;
    }

    public BooleanProperty verifiedSaveEnabledProperty() {
        return verifiedSaveEnabled;
    }

    public BooleanProperty saveAndRenameEnabledProperty() {
        return saveAndRenameEnabled;
    }

    public BooleanProperty previewLoadingProperty() {
        return previewLoading;
    }

    public StringProperty previewStatusProperty() {
        return previewStatus;
    }

    public StringProperty previewCurrentFilenameProperty() {
        return previewCurrentFilename;
    }

    public StringProperty previewProposedFilenameProperty() {
        return previewProposedFilename;
    }

    public StringProperty previewProposedPathProperty() {
        return previewProposedPath;
    }

    public StringProperty previewErrorProperty() {
        return previewError;
    }

    public StringProperty validationMessageProperty() {
        return validationMessage;
    }

    public StringProperty saveMessageProperty() {
        return saveMessage;
    }

    public ObjectProperty<SceneReviewSaveResult> lastSaveResultProperty() {
        return lastSaveResult;
    }

    private void submitSave(
            SceneReviewRenameChoice renameChoice,
            VerificationStatus verificationStatus) {

        final boolean enabled = verificationStatus == VerificationStatus.VERIFIED
                ? verifiedSaveEnabled.get()
                : saveEnabled.get();
        if (enabled && !saving.get()) {
            final SceneReviewSaveRequest request = new SceneReviewSaveRequest(
                    draftWithCurrentFields(verificationStatus),
                    renameChoice
            );
            saving.set(true);
            validate();
            saveMessage.set("");

            try {
                backgroundExecutor.execute(() -> saveInBackground(request));
            } catch (RejectedExecutionException exception) {
                saving.set(false);
                saveMessage.set(exception.getMessage());
            }
        }
    }

    private void saveInBackground(SceneReviewSaveRequest request) {
        try {
            final SceneReviewSaveResult result = saver.save(request);
            uiExecutor.execute(() -> applySaveResult(result));
        } catch (IOException | SQLException | IllegalArgumentException exception) {
            uiExecutor.execute(() -> applySaveFailure(exception));
        }
    }

    private void maybePersistAlias(
            String candidateText,
            String primaryName,
            AliasWrite aliasWrite) {

        if (aliasConfirmation.confirm(candidateText, primaryName)) {
            try {
                backgroundExecutor.execute(() -> persistAlias(aliasWrite));
            } catch (RejectedExecutionException exception) {
                saveMessage.set(exception.getMessage());
            }
        }
    }

    private void persistAlias(AliasWrite aliasWrite) {
        try {
            aliasWrite.persist();
            uiExecutor.execute(() -> saveMessage.set("Alias created."));
        } catch (SQLException | IllegalArgumentException exception) {
            uiExecutor.execute(() -> saveMessage.set(exception.getMessage()));
        }
    }

    private void loadExistingSceneInBackground(
            java.util.UUID sceneId,
            long generation) {
        try {
            final EditableSceneReviewDraft draft =
                    draftLoader.fromExistingScene(sceneId);
            uiExecutor.execute(() -> {
                if (generation == draftGeneration.get()) {
                    saving.set(false);
                    loadDraft(draft);
                }
            });
        } catch (SQLException | IllegalArgumentException exception) {
            uiExecutor.execute(() -> {
                if (generation == draftGeneration.get()) {
                    saving.set(false);
                    saveMessage.set(exception.getMessage());
                }
            });
        }
    }

    private void applySaveResult(SceneReviewSaveResult result) {
        lastSaveResult.set(result);
        saving.set(false);
        validate();

        if (result.status() == SceneReviewSaveStatus.CREATED_RENAME_FAILED_NEEDS_REVIEW
                || result.status()
                == SceneReviewSaveStatus.UPDATED_RENAME_FAILED_NEEDS_REVIEW) {
            saveMessage.set(RENAME_FAILED_TEXT);
        } else {
            saveMessage.set(result.status().name());
            dirty.set(false);
        }
    }

    private void applySaveFailure(Exception exception) {
        saving.set(false);
        validate();
        saveMessage.set(exception.getMessage());
    }

    private void schedulePreviewIfEditing() {
        if (!loadingDraft) {
            schedulePreview();
        }
    }

    private void updateDirtyAndPreview() {
        updateDirty();
        validate();
        schedulePreviewIfEditing();
    }

    private void schedulePreview() {
        if (editableDraft != null) {
            final long generation = previewGeneration.incrementAndGet();
            final SceneReviewDraft previewDraft = draftWithCurrentFields(
                    verificationTarget()
            );
            previewLoading.set(true);
            saveAndRenameEnabled.set(false);

            try {
                backgroundExecutor.execute(() -> previewInBackground(
                        generation,
                        previewDraft
                ));
            } catch (RejectedExecutionException exception) {
                previewLoading.set(false);
                previewError.set(exception.getMessage());
            }
        }
    }

    private void previewInBackground(long generation, SceneReviewDraft draft) {
        try {
            final CanonicalRenameDisplay display =
                    renamePreviewProvider.preview(draft);
            uiExecutor.execute(() -> applyPreview(generation, display));
        } catch (SQLException | IllegalArgumentException exception) {
            uiExecutor.execute(() -> applyPreviewFailure(
                    generation,
                    exception
            ));
        }
    }

    private void applyPreview(
            long generation,
            CanonicalRenameDisplay display) {

        if (generation == previewGeneration.get()) {
            previewStatus.set(display.status());
            previewCurrentFilename.set(display.currentFilename());
            previewProposedFilename.set(display.proposedFilename());
            previewProposedPath.set(display.proposedPath().toString());
            previewError.set(display.error());
            previewLoading.set(false);
            saveAndRenameEnabled.set(renameReady(display));
        }
    }

    private void applyPreviewFailure(long generation, Exception exception) {
        if (generation == previewGeneration.get()) {
            previewLoading.set(false);
            previewError.set(exception.getMessage());
            saveAndRenameEnabled.set(false);
        }
    }

    private boolean renameReady(CanonicalRenameDisplay display) {
        return verifiedSaveEnabled.get()
                && !previewLoading.get()
                && "READY".equals(display.status())
                && !display.unchanged()
                && !display.physicalDestinationExists()
                && !display.databasePathConflict()
                && display.error().isBlank();
    }

    private SceneReviewDraft draftWithCurrentFields(
            VerificationStatus verificationStatus) {

        final SceneReviewDraft draft = editableDraft.draft();

        return new SceneReviewDraft(
                draft.mode(),
                draft.sceneId(),
                draft.mediaFileIds(),
                title.get(),
                parsedReleaseDate(),
                blankToNull(code.get()),
                blankToNull(season.get()),
                blankToNull(episode.get()),
                selectedPublisherId.get(),
                selectedSeriesId.get(),
                selectedMovieId.get(),
                explicitOriginalMovieOverrideId.get(),
                selectedPerformers.stream()
                        .map(SelectedPerformer::id)
                        .toList(),
                verificationStatus,
                draft.warnings()
        );
    }

    private VerificationStatus verificationTarget() {
        return editableDraft == null
                ? VerificationStatus.VERIFIED
                : editableDraft.draft().verificationStatus();
    }

    private void validate() {
        final boolean titleBlank = title.get() == null || title.get().isBlank();

        if (editableDraft == null) {
            validationMessage.set("");
            saveEnabled.set(false);
        } else if (titleBlank) {
            validationMessage.set(TITLE_REQUIRED_MESSAGE);
            saveEnabled.set(false);
        } else if (!releaseDateText.get().isBlank()
                && parsedReleaseDate() == null) {
            validationMessage.set(DATE_INVALID_MESSAGE);
            saveEnabled.set(false);
        } else {
            validationMessage.set("");
            saveEnabled.set(!saving.get());
        }

        verifiedSaveEnabled.set(saveEnabled.get()
                && verifiedContextResolved());
        if (saveEnabled.get() && !verifiedSaveEnabled.get()) {
            validationMessage.set(verifiedContextMessage());
        }

        if (!verifiedSaveEnabled.get()) {
            saveAndRenameEnabled.set(false);
        }
    }

    private void configureInterpretationRequirements(
            FilenameInterpretation interpretation,
            boolean selectionRequired) {

        interpretationSelectionRequired = selectionRequired;
        publisherResolutionRequired = isUnmatched(
                interpretation == null ? null : interpretation.publisher()
        );
        seriesResolutionRequired = isUnmatched(
                interpretation == null ? null : interpretation.series()
        );
        movieResolutionRequired = isUnmatched(
                interpretation == null ? null : interpretation.movie()
        );
    }

    private boolean isUnmatched(service.EntityMatch match) {
        return match != null && match.source() == MatchSource.UNMATCHED;
    }

    private boolean verifiedContextResolved() {
        return !interpretationSelectionRequired
                && (!publisherResolutionRequired
                        || selectedPublisherId.get() != null)
                && (!seriesResolutionRequired || selectedSeriesId.get() != null)
                && (!movieResolutionRequired || selectedMovieId.get() != null);
    }

    private String verifiedContextMessage() {
        if (interpretationSelectionRequired) {
            return APPLY_INTERPRETATION_MESSAGE;
        }
        if (publisherResolutionRequired && selectedPublisherId.get() == null) {
            return "Select or create the Publisher candidate before saving as verified.";
        }
        if (seriesResolutionRequired && selectedSeriesId.get() == null) {
            return "Select or create the Series candidate before saving as verified.";
        }
        return "Select or create the Movie candidate before saving as verified.";
    }

    private void updateDirty() {
        if (!loadingDraft && editableDraft != null) {
            final SceneReviewDraft draft = editableDraft.draft();
            dirty.set(!Objects.equals(draft.title(), title.get())
                    || !Objects.equals(
                    draft.releaseDate() == null
                            ? ""
                            : draft.releaseDate().toString(),
                    releaseDateText.get()
            )
                    || !Objects.equals(nullToEmpty(draft.code()), code.get())
                    || !Objects.equals(nullToEmpty(draft.season()), season.get())
                    || !Objects.equals(nullToEmpty(draft.episode()), episode.get())
                    || !Objects.equals(
                    draft.publisherId(),
                    selectedPublisherId.get()
            )
                    || !Objects.equals(
                    draft.seriesId(),
                    selectedSeriesId.get()
            )
                    || !Objects.equals(
                    draft.selectedMovieId(),
                    selectedMovieId.get()
            )
                    || !Objects.equals(
                    draft.explicitOriginalMovieOverrideId(),
                    explicitOriginalMovieOverrideId.get()
            )
                    || !Objects.equals(
                    draft.performerIds(),
                    selectedPerformers.stream()
                            .map(SelectedPerformer::id)
                            .toList()
            ));
        }
    }

    private LocalDate parsedReleaseDate() {
        LocalDate date = null;

        if (!releaseDateText.get().isBlank()) {
            try {
                date = LocalDate.parse(releaseDateText.get().trim());
            } catch (DateTimeParseException exception) {
                date = null;
            }
        }

        return date;
    }

    private String blankToNull(String value) {
        String normalized = null;

        if (value != null && !value.trim().isEmpty()) {
            normalized = value.trim();
        }

        return normalized;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @FunctionalInterface
    private interface AliasWrite {
        void persist() throws SQLException;
    }
}

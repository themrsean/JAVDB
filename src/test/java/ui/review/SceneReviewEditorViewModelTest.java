package ui.review;

import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.EditableSceneReviewDraft;
import service.FilenameMatchStatus;
import service.FilenameInterpretation;
import service.EntityMatch;
import service.MatchSource;
import service.SceneReviewDraft;
import service.SceneReviewMode;
import service.SceneReviewRenameChoice;
import service.SceneReviewSaveRequest;
import service.SceneReviewSaveResult;
import service.SceneReviewSaveStatus;
import service.SceneReviewSaver;
import service.CanonicalRenameDisplay;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class SceneReviewEditorViewModelTest {
    private static final UUID MEDIA_ID =
            UUID.fromString("11111111-dddd-1111-dddd-111111111111");
    private static final UUID SCENE_ID =
            UUID.fromString("22222222-dddd-2222-dddd-222222222222");
    private static final UUID PUBLISHER_ID =
            UUID.fromString("33333333-dddd-3333-dddd-333333333333");
    private static final UUID SERIES_ID =
            UUID.fromString("44444444-dddd-4444-dddd-444444444444");
    private static final UUID MOVIE_ID =
            UUID.fromString("55555555-dddd-5555-dddd-555555555555");
    private static final UUID PERFORMER_ID =
            UUID.fromString("66666666-dddd-6666-dddd-666666666666");

    @Test
    @DisplayName("Initial state has no editable draft")
    void initialStateHasNoEditableDraft() {
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow());

        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.dirtyProperty().get()),
                () -> Assertions.assertFalse(viewModel.saveEnabledProperty().get()),
                () -> Assertions.assertTrue(viewModel.titleProperty().get().isBlank())
        );
    }

    @Test
    @DisplayName("Loading draft populates state and clears dirty")
    void loadingDraftPopulatesStateAndClearsDirty() {
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow());

        viewModel.loadDraft(editableDraft("Title"));

        Assertions.assertAll(
                () -> Assertions.assertEquals("Title",
                        viewModel.titleProperty().get()),
                () -> Assertions.assertFalse(viewModel.dirtyProperty().get()),
                () -> Assertions.assertTrue(viewModel.saveEnabledProperty().get())
        );
    }

    @Test
    @DisplayName("Existing scene draft loads through draft loader")
    void existingSceneDraftLoadsThroughDraftLoader() {
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow(), new CapturingDraftLoader());

        viewModel.loadExistingScene(SCENE_ID);

        Assertions.assertAll(
                () -> Assertions.assertEquals("Existing Scene",
                        viewModel.titleProperty().get()),
                () -> Assertions.assertEquals(
                        SceneReviewMode.EDIT_EXISTING_SCENE,
                        viewModel.currentDraft().draft().mode()
                ),
                () -> Assertions.assertFalse(viewModel.dirtyProperty().get())
        );
    }

    @Test
    @DisplayName("Title editing marks dirty and blank title disables save")
    void titleEditingMarksDirtyAndBlankTitleDisablesSave() {
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow());
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.titleProperty().set(" ");

        Assertions.assertAll(
                () -> Assertions.assertTrue(viewModel.dirtyProperty().get()),
                () -> Assertions.assertFalse(viewModel.saveEnabledProperty().get()),
                () -> Assertions.assertTrue(
                        viewModel.validationMessageProperty().get()
                                .contains("title")
                )
        );
    }

    @Test
    @DisplayName("Scalar editor fields are included in save request")
    void scalarEditorFieldsAreIncludedInSaveRequest() {
        final CapturingWorkflow workflow = new CapturingWorkflow();
        final SceneReviewEditorViewModel viewModel = viewModel(workflow);
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.releaseDateTextProperty().set("2026-07-16");
        viewModel.codeProperty().set("ABC-001");
        viewModel.seasonProperty().set("1");
        viewModel.episodeProperty().set("2");
        viewModel.saveWithoutRename();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        java.time.LocalDate.of(2026, 7, 16),
                        workflow.lastRequest.draft().releaseDate()
                ),
                () -> Assertions.assertEquals(
                        "ABC-001",
                        workflow.lastRequest.draft().code()
                ),
                () -> Assertions.assertEquals(
                        "1",
                        workflow.lastRequest.draft().season()
                ),
                () -> Assertions.assertEquals(
                        "2",
                        workflow.lastRequest.draft().episode()
                )
        );
    }

    @Test
    @DisplayName("Selected entities and performers are included in save request")
    void selectedEntitiesAndPerformersAreIncludedInSaveRequest() {
        final CapturingWorkflow workflow = new CapturingWorkflow();
        final SceneReviewEditorViewModel viewModel = viewModel(workflow);
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.selectedPublisherIdProperty().set(PUBLISHER_ID);
        viewModel.selectedSeriesIdProperty().set(SERIES_ID);
        viewModel.selectedMovieIdProperty().set(MOVIE_ID);
        viewModel.explicitOriginalMovieOverrideIdProperty().set(MOVIE_ID);
        viewModel.addPerformer(PERFORMER_ID, "Alice");
        viewModel.saveWithoutRename();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        PUBLISHER_ID,
                        workflow.lastRequest.draft().publisherId()
                ),
                () -> Assertions.assertEquals(
                        SERIES_ID,
                        workflow.lastRequest.draft().seriesId()
                ),
                () -> Assertions.assertEquals(
                        MOVIE_ID,
                        workflow.lastRequest.draft().selectedMovieId()
                ),
                () -> Assertions.assertEquals(
                        MOVIE_ID,
                        workflow.lastRequest.draft()
                                .explicitOriginalMovieOverrideId()
                ),
                () -> Assertions.assertEquals(
                        List.of(PERFORMER_ID),
                        workflow.lastRequest.draft().performerIds()
                )
        );
    }

    @Test
    @DisplayName("Duplicate performer add is ignored")
    void duplicatePerformerAddIsIgnored() {
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow());
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.addPerformer(PERFORMER_ID, "Alice");
        viewModel.addPerformer(PERFORMER_ID, "Alice Again");

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        1,
                        viewModel.selectedPerformers().size()
                ),
                () -> Assertions.assertTrue(viewModel.dirtyProperty().get())
        );
    }

    @Test
    @DisplayName("Removing an original performer marks dirty")
    void removingOriginalPerformerMarksDirty() {
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow());
        viewModel.loadDraft(editableDraftWithPerformers("Title"));

        viewModel.removePerformer(PERFORMER_ID);

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        viewModel.selectedPerformers().isEmpty()
                ),
                () -> Assertions.assertTrue(viewModel.dirtyProperty().get())
        );
    }

    @Test
    @DisplayName("Applying alternative replaces current draft through loader")
    void applyingAlternativeReplacesCurrentDraftThroughLoader() {
        final CapturingDraftLoader loader = new CapturingDraftLoader();
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow(), loader);
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.applyAlternative(new FilenameInterpretation(
                new EntityMatch(
                        PUBLISHER_ID,
                        "Publisher",
                        "Publisher",
                        MatchSource.EXPLICIT_PRIMARY_NAME,
                        null
                ),
                null,
                null,
                List.of(),
                List.of(),
                1
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        PUBLISHER_ID,
                        viewModel.selectedPublisherIdProperty().get()
                ),
                () -> Assertions.assertTrue(viewModel.dirtyProperty().get())
        );
    }

    @Test
    @DisplayName("Resolving performer candidate defaults to no alias")
    void resolvingPerformerCandidateDefaultsToNoAlias() {
        final CapturingAliasPersistence aliases = new CapturingAliasPersistence();
        final SceneReviewEditorViewModel viewModel = viewModel(
                new CapturingWorkflow(),
                null,
                (candidate, primaryName) -> false,
                aliases
        );
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.resolvePerformerCandidate("Ally", PERFORMER_ID, "Alice");

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, aliases.performerAliases.size()),
                () -> Assertions.assertEquals(
                        List.of(PERFORMER_ID),
                        viewModel.selectedPerformers()
                                .stream()
                                .map(SelectedPerformer::id)
                                .toList()
                )
        );
    }

    @Test
    @DisplayName("Resolving performer candidate persists alias after confirmation")
    void resolvingPerformerCandidatePersistsAliasAfterConfirmation() {
        final CapturingAliasPersistence aliases = new CapturingAliasPersistence();
        final SceneReviewEditorViewModel viewModel = viewModel(
                new CapturingWorkflow(),
                null,
                (candidate, primaryName) -> true,
                aliases
        );
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.resolvePerformerCandidate("Ally", PERFORMER_ID, "Alice");

        Assertions.assertEquals(List.of("Ally"), aliases.performerAliases);
    }

    @Test
    @DisplayName("Resolving publisher candidate persists alias after confirmation")
    void resolvingPublisherCandidatePersistsAliasAfterConfirmation() {
        final CapturingAliasPersistence aliases = new CapturingAliasPersistence();
        final SceneReviewEditorViewModel viewModel = viewModel(
                new CapturingWorkflow(),
                null,
                (candidate, primaryName) -> true,
                aliases
        );
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.resolvePublisherCandidate("Pub Alias", PUBLISHER_ID, "Publisher");

        Assertions.assertAll(
                () -> Assertions.assertEquals(PUBLISHER_ID,
                        viewModel.selectedPublisherIdProperty().get()),
                () -> Assertions.assertEquals(
                        List.of("Pub Alias"),
                        aliases.publisherAliases
                )
        );
    }

    @Test
    @DisplayName("Invalid date disables save")
    void invalidDateDisablesSave() {
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow());
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.releaseDateTextProperty().set("not-a-date");

        Assertions.assertAll(
                () -> Assertions.assertFalse(viewModel.saveEnabledProperty().get()),
                () -> Assertions.assertTrue(
                        viewModel.validationMessageProperty().get()
                                .contains("date")
                )
        );
    }

    @Test
    @DisplayName("Save without rename routes request and clears dirty")
    void saveWithoutRenameRoutesRequestAndClearsDirty() {
        final CapturingWorkflow workflow = new CapturingWorkflow();
        final SceneReviewEditorViewModel viewModel = viewModel(workflow);
        viewModel.loadDraft(editableDraft("Title"));
        viewModel.titleProperty().set("Edited");

        viewModel.saveWithoutRename();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewRenameChoice.DO_NOT_RENAME,
                        workflow.lastRequest.renameChoice()
                ),
                () -> Assertions.assertEquals(
                        "Edited",
                        workflow.lastRequest.draft().title()
                ),
                () -> Assertions.assertFalse(viewModel.dirtyProperty().get()),
                () -> Assertions.assertEquals(
                        SceneReviewSaveStatus.CREATED,
                        viewModel.lastSaveResultProperty().get().status()
                )
        );
    }

    @Test
    @DisplayName("Save and rename routes rename request")
    void saveAndRenameRoutesRenameRequest() {
        final CapturingWorkflow workflow = new CapturingWorkflow();
        final SceneReviewEditorViewModel viewModel = viewModel(workflow);
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.saveAndRename();

        Assertions.assertEquals(
                SceneReviewRenameChoice.RENAME,
                workflow.lastRequest.renameChoice()
        );
    }

    @Test
    @DisplayName("Save as needs review changes verification target")
    void saveAsNeedsReviewChangesVerificationTarget() {
        final CapturingWorkflow workflow = new CapturingWorkflow();
        final SceneReviewEditorViewModel viewModel = viewModel(workflow);
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.saveAsNeedsReview();

        Assertions.assertEquals(
                VerificationStatus.NEEDS_REVIEW,
                workflow.lastRequest.draft().verificationStatus()
        );
    }

    @Test
    @DisplayName("Reset restores loaded draft")
    void resetRestoresLoadedDraft() {
        final SceneReviewEditorViewModel viewModel =
                viewModel(new CapturingWorkflow());
        viewModel.loadDraft(editableDraft("Title"));
        viewModel.titleProperty().set("Edited");

        viewModel.resetChanges();

        Assertions.assertAll(
                () -> Assertions.assertEquals("Title",
                        viewModel.titleProperty().get()),
                () -> Assertions.assertFalse(viewModel.dirtyProperty().get())
        );
    }

    @Test
    @DisplayName("Duplicate save submission is prevented")
    void duplicateSaveSubmissionIsPrevented() {
        final QueuedExecutor background = new QueuedExecutor();
        final SceneReviewEditorViewModel viewModel =
                new SceneReviewEditorViewModel(
                        new CapturingWorkflow(),
                        background,
                        new QueuedExecutor()
                );
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.saveWithoutRename();
        viewModel.saveWithoutRename();

        Assertions.assertEquals(2, background.pendingCount());
    }

    @Test
    @DisplayName("Partial rename failure is displayed")
    void partialRenameFailureIsDisplayed() {
        final CapturingWorkflow workflow = new CapturingWorkflow(
                result(SceneReviewSaveStatus.CREATED_RENAME_FAILED_NEEDS_REVIEW)
        );
        final SceneReviewEditorViewModel viewModel = viewModel(workflow);
        viewModel.loadDraft(editableDraft("Title"));

        viewModel.saveAndRename();

        Assertions.assertTrue(
                viewModel.saveMessageProperty().get().contains("rename failed")
        );
    }

    @Test
    @DisplayName("Draft load triggers rename preview")
    void draftLoadTriggersRenamePreview() {
        final SceneReviewEditorViewModel viewModel = viewModel(
                new CapturingWorkflow(),
                null,
                (candidate, primaryName) -> false,
                new CapturingAliasPersistence(),
                draft -> new CanonicalRenameDisplay(
                        "READY",
                        "old.mp4",
                        draft.title() + ".mp4",
                        Path.of("/tmp/" + draft.title() + ".mp4"),
                        false,
                        false,
                        false,
                        List.of(),
                        ""
                )
        );

        viewModel.loadDraft(editableDraft("Preview Title"));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Preview Title.mp4",
                        viewModel.previewProposedFilenameProperty().get()
                ),
                () -> Assertions.assertTrue(
                        viewModel.saveAndRenameEnabledProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Collision disables only save and rename")
    void collisionDisablesOnlySaveAndRename() {
        final SceneReviewEditorViewModel viewModel = viewModel(
                new CapturingWorkflow(),
                null,
                (candidate, primaryName) -> false,
                new CapturingAliasPersistence(),
                draft -> new CanonicalRenameDisplay(
                        "READY",
                        "old.mp4",
                        "new.mp4",
                        Path.of("/tmp/new.mp4"),
                        false,
                        true,
                        false,
                        List.of(),
                        ""
                )
        );

        viewModel.loadDraft(editableDraft("Title"));

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        viewModel.saveEnabledProperty().get()
                ),
                () -> Assertions.assertFalse(
                        viewModel.saveAndRenameEnabledProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Stale preview result is ignored")
    void stalePreviewResultIsIgnored() {
        final QueuedExecutor backgroundExecutor = new QueuedExecutor();
        final SceneReviewEditorViewModel viewModel =
                new SceneReviewEditorViewModel(
                        new CapturingWorkflow(),
                        null,
                        (candidate, primaryName) -> false,
                        new CapturingAliasPersistence(),
                        draft -> new CanonicalRenameDisplay(
                                "READY",
                                "old.mp4",
                                draft.title() + ".mp4",
                                Path.of("/tmp/" + draft.title() + ".mp4"),
                                false,
                                false,
                                false,
                                List.of(),
                                ""
                        ),
                        backgroundExecutor,
                        Runnable::run
                );

        viewModel.loadDraft(editableDraft("First"));
        viewModel.titleProperty().set("Second");

        Assertions.assertEquals(2, backgroundExecutor.pendingCount());

        backgroundExecutor.runLast();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Second.mp4",
                        viewModel.previewProposedFilenameProperty().get()
                ),
                () -> Assertions.assertTrue(
                        viewModel.saveAndRenameEnabledProperty().get()
                )
        );

        backgroundExecutor.runFirst();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Second.mp4",
                        viewModel.previewProposedFilenameProperty().get()
                ),
                () -> Assertions.assertTrue(
                        viewModel.saveAndRenameEnabledProperty().get()
                )
        );
    }

    private SceneReviewEditorViewModel viewModel(CapturingWorkflow workflow) {
        return viewModel(workflow, null);
    }

    private SceneReviewEditorViewModel viewModel(
            CapturingWorkflow workflow,
            SceneReviewDraftLoader draftLoader) {

        return viewModel(
                workflow,
                draftLoader,
                (candidate, primaryName) -> false,
                new CapturingAliasPersistence()
        );
    }

    private SceneReviewEditorViewModel viewModel(
            CapturingWorkflow workflow,
            SceneReviewDraftLoader draftLoader,
            SceneReviewEditorViewModel.AliasConfirmation aliasConfirmation,
            SceneReviewEditorViewModel.AliasPersistence aliasPersistence) {

        return viewModel(
                workflow,
                draftLoader,
                aliasConfirmation,
                aliasPersistence,
                draft -> new CanonicalRenameDisplay(
                        "READY",
                        "old.mp4",
                        "new.mp4",
                        Path.of("/tmp/new.mp4"),
                        false,
                        false,
                        false,
                        List.of(),
                        ""
                )
        );
    }

    private SceneReviewEditorViewModel viewModel(
            CapturingWorkflow workflow,
            SceneReviewDraftLoader draftLoader,
            SceneReviewEditorViewModel.AliasConfirmation aliasConfirmation,
            SceneReviewEditorViewModel.AliasPersistence aliasPersistence,
            SceneReviewEditorViewModel.RenamePreviewProvider previewProvider) {

        return new SceneReviewEditorViewModel(
                workflow,
                draftLoader,
                aliasConfirmation,
                aliasPersistence,
                previewProvider,
                Runnable::run,
                Runnable::run
        );
    }

    private EditableSceneReviewDraft editableDraft(String title) {
        return editableDraft(title, List.of());
    }

    private EditableSceneReviewDraft editableDraftWithPerformers(String title) {
        return editableDraft(title, List.of(PERFORMER_ID));
    }

    private EditableSceneReviewDraft editableDraft(
            String title,
            List<UUID> performerIds) {

        return new EditableSceneReviewDraft(
                new SceneReviewDraft(
                        SceneReviewMode.CREATE_FROM_MEDIA,
                        null,
                        List.of(MEDIA_ID),
                        title,
                        null,
                        null,
                        null,
                        null,
                        PUBLISHER_ID,
                        null,
                        null,
                        null,
                        performerIds,
                        VerificationStatus.VERIFIED,
                        List.of()
                ),
                Path.of("/tmp/media.mp4"),
                null,
                FilenameMatchStatus.READY,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of()
        );
    }

    private static final class CapturingDraftLoader
            implements SceneReviewDraftLoader {
        @Override
        public EditableSceneReviewDraft fromReviewDetails(
                service.ReviewDetails details) {

            return null;
        }

        @Override
        public EditableSceneReviewDraft fromExistingScene(UUID sceneId)
                throws SQLException {

            return new EditableSceneReviewDraft(
                    new SceneReviewDraft(
                            SceneReviewMode.EDIT_EXISTING_SCENE,
                            sceneId,
                            List.of(MEDIA_ID),
                            "Existing Scene",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            List.of(),
                            VerificationStatus.NEEDS_REVIEW,
                            List.of()
                    ),
                    Path.of("/tmp/existing.mp4"),
                    null,
                    FilenameMatchStatus.READY,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    List.of()
            );
        }

        @Override
        public EditableSceneReviewDraft applyInterpretation(
                EditableSceneReviewDraft editable,
                FilenameInterpretation interpretation) {

            return new EditableSceneReviewDraft(
                    new SceneReviewDraft(
                            editable.draft().mode(),
                            editable.draft().sceneId(),
                            editable.draft().mediaFileIds(),
                            editable.draft().title(),
                            editable.draft().releaseDate(),
                            editable.draft().code(),
                            editable.draft().season(),
                            editable.draft().episode(),
                            interpretation.publisher().id(),
                            editable.draft().seriesId(),
                            editable.draft().selectedMovieId(),
                            editable.draft()
                                    .explicitOriginalMovieOverrideId(),
                            editable.draft().performerIds(),
                            editable.draft().verificationStatus(),
                            editable.draft().warnings()
                    ),
                    editable.mediaPath(),
                    editable.parsedFilename(),
                    editable.matchStatus(),
                    editable.alternatives(),
                    editable.performerCandidates(),
                    editable.unmatchedPerformers(),
                    editable.originalMovieSelection(),
                    editable.warnings()
            );
        }
    }

    private static SceneReviewSaveResult result(
            SceneReviewSaveStatus status) {

        return new SceneReviewSaveResult(
                status,
                SCENE_ID,
                List.of(MEDIA_ID),
                status == SceneReviewSaveStatus.CREATED_RENAME_FAILED_NEEDS_REVIEW
                        ? VerificationStatus.NEEDS_REVIEW
                        : VerificationStatus.VERIFIED,
                List.of(),
                List.of(),
                null,
                true,
                status != SceneReviewSaveStatus.CREATED_RENAME_FAILED_NEEDS_REVIEW
        );
    }

    private static final class CapturingWorkflow
            implements SceneReviewSaver {
        private final SceneReviewSaveResult result;
        private SceneReviewSaveRequest lastRequest;

        private CapturingWorkflow() {
            this(result(SceneReviewSaveStatus.CREATED));
        }

        private CapturingWorkflow(SceneReviewSaveResult result) {
            this.result = result;
        }

        @Override
        public SceneReviewSaveResult save(SceneReviewSaveRequest request) {
            lastRequest = request;
            return result;
        }
    }

    private static final class CapturingAliasPersistence
            implements SceneReviewEditorViewModel.AliasPersistence {
        private final List<String> performerAliases = new ArrayList<>();
        private final List<String> publisherAliases = new ArrayList<>();

        @Override
        public void addPerformerAlias(UUID performerId, String alias) {
            performerAliases.add(alias);
        }

        @Override
        public void addPublisherAlias(UUID publisherId, String alias) {
            publisherAliases.add(alias);
        }
    }

    private static final class QueuedExecutor
            implements java.util.concurrent.Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        private int pendingCount() {
            return tasks.size();
        }

        private void runFirst() {
            tasks.removeFirst().run();
        }

        private void runLast() {
            tasks.removeLast().run();
        }
    }
}

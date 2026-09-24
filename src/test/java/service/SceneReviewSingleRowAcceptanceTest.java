package service;

import database.DatabaseManager;
import database.SchemaManager;
import media.MediaFilenameParser;
import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import model.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import repository.EntitySuggestionRepository;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;
import ui.review.DefaultSceneReviewDraftLoader;
import ui.review.ReviewQueueViewModel;
import ui.review.SceneReviewEditorViewModel;
import ui.review.SceneReviewQueueViewModel;
import ui.review.SelectedPerformer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

class SceneReviewSingleRowAcceptanceTest {
    private static final String DATABASE_FILE_NAME =
            "scene-review-single-row-acceptance.db";
    private static final long LAST_MODIFIED_MILLIS = 100L;
    private static final long FILE_SIZE = 5L;
    private static final int WIDTH = 1_920;
    private static final int HEIGHT = 1_080;
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @TempDir
    Path temporaryDirectory;

    private AcceptanceFixture fixture;

    @BeforeEach
    void initializeFixture() throws Exception {
        fixture = new AcceptanceFixture(temporaryDirectory);
        fixture.initialize();
    }

    @Test
    @DisplayName("Connected metadata-only workflow creates verified scene")
    void connectedMetadataOnlyWorkflowCreatesVerifiedScene() throws Exception {
        final SceneReviewEditorViewModel editor =
                fixture.editor(fixture.successfulWorkflowService());
        final ReviewDetails details = fixture.unassignedDetails(
                fixture.readyMediaId
        );

        editor.loadUnassignedDetails(details);
        editor.titleProperty().set("Edited Title");
        editor.releaseDateTextProperty().set("2026-07-16");
        editor.selectedPublisherIdProperty().set(fixture.publisher.getId());
        editor.selectedSeriesIdProperty().set(fixture.series.getId());
        editor.selectedMovieIdProperty().set(fixture.originalMovie.getId());
        editor.explicitOriginalMovieOverrideIdProperty().set(
                fixture.originalMovie.getId()
        );
        editor.addPerformer(
                fixture.secondPerformer.getId(),
                fixture.secondPerformer.getMainName()
        );
        editor.removePerformer(fixture.performer.getId());
        editor.addPerformer(
                fixture.performer.getId(),
                fixture.performer.getMainName()
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(editor.saveEnabledProperty().get()),
                () -> Assertions.assertEquals(
                        "READY",
                        editor.previewStatusProperty().get()
                ),
                () -> Assertions.assertTrue(
                        editor.previewProposedFilenameProperty()
                                .get()
                                .contains("Edited Title")
                ),
                () -> Assertions.assertTrue(
                        editor.previewProposedFilenameProperty()
                                .get()
                                .contains("Original Movie")
                )
        );

        final Path originalPath = fixture.readyMediaPath;
        editor.saveWithoutRename();
        final SceneReviewSaveResult result =
                editor.lastSaveResultProperty().get();
        fixture.unassignedQueueViewModel.load();
        fixture.sceneQueueViewModel.load();

        final Scene saved = fixture.sceneRepository
                .findById(result.sceneId())
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewSaveStatus.CREATED,
                        result.status()
                ),
                () -> Assertions.assertEquals(
                        VerificationStatus.VERIFIED,
                        saved.getVerificationStatus()
                ),
                () -> Assertions.assertEquals("Edited Title", saved.getTitle()),
                () -> Assertions.assertEquals(
                        fixture.publisher.getId(),
                        saved.getPublisher().getId()
                ),
                () -> Assertions.assertEquals(
                        fixture.series.getId(),
                        saved.getSeries().getId()
                ),
                () -> Assertions.assertTrue(
                        saved.getPerformers()
                                .stream()
                                .map(Performer::getId)
                                .toList()
                                .containsAll(List.of(
                                        fixture.secondPerformer.getId(),
                                        fixture.performer.getId()
                                ))
                ),
                () -> Assertions.assertFalse(
                        fixture.assignmentRepository.isUnassigned(
                                fixture.readyMediaId
                        )
                ),
                () -> Assertions.assertEquals(
                        originalPath,
                        fixture.mediaFileRepository
                                .findById(fixture.readyMediaId)
                                .orElseThrow()
                                .getPath()
                ),
                () -> Assertions.assertTrue(Files.exists(originalPath)),
                () -> Assertions.assertFalse(editor.dirtyProperty().get()),
                () -> Assertions.assertTrue(
                        fixture.unassignedQueueViewModel.rows()
                                .stream()
                                .noneMatch(row -> row.mediaId()
                                        .equals(fixture.readyMediaId))
                ),
                () -> Assertions.assertNotNull(
                        fixture.unassignedQueueViewModel.selectedRow().get()
                ),
                () -> Assertions.assertTrue(
                        fixture.sceneQueueViewModel.rows()
                                .stream()
                                .noneMatch(row -> row.sceneId()
                                        .equals(result.sceneId()))
                )
        );
    }

    @Test
    @DisplayName("Connected save-and-rename workflow moves file and path")
    void connectedSaveAndRenameWorkflowMovesFileAndPath() throws Exception {
        final SceneReviewEditorViewModel editor =
                fixture.editor(fixture.successfulWorkflowService());
        final ReviewDetails details = fixture.unassignedDetails(
                fixture.secondReadyMediaId
        );
        final byte[] originalBytes = Files.readAllBytes(
                fixture.secondReadyMediaPath
        );

        editor.loadUnassignedDetails(details);
        editor.titleProperty().set("Renamed Scene");
        editor.releaseDateTextProperty().set("2026-08-17");
        editor.selectedPublisherIdProperty().set(fixture.publisher.getId());
        editor.selectedSeriesIdProperty().set(fixture.series.getId());
        editor.selectedMovieIdProperty().set(fixture.originalMovie.getId());
        editor.explicitOriginalMovieOverrideIdProperty().set(
                fixture.originalMovie.getId()
        );

        final Path proposedPath = Path.of(
                editor.previewProposedPathProperty().get()
        );
        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "READY",
                        editor.previewStatusProperty().get()
                ),
                () -> Assertions.assertTrue(
                        editor.saveAndRenameEnabledProperty().get()
                )
        );

        editor.saveAndRename();
        final SceneReviewSaveResult result =
                editor.lastSaveResultProperty().get();
        fixture.unassignedQueueViewModel.load();
        fixture.sceneQueueViewModel.load();

        final MediaFile renamed = fixture.mediaFileRepository
                .findById(fixture.secondReadyMediaId)
                .orElseThrow();
        final Scene saved = fixture.sceneRepository
                .findById(result.sceneId())
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewSaveStatus.CREATED_AND_RENAMED,
                        result.status()
                ),
                () -> Assertions.assertFalse(Files.exists(
                        fixture.secondReadyMediaPath
                )),
                () -> Assertions.assertTrue(Files.exists(proposedPath)),
                () -> Assertions.assertArrayEquals(
                        originalBytes,
                        Files.readAllBytes(proposedPath)
                ),
                () -> Assertions.assertEquals(proposedPath, renamed.getPath()),
                () -> Assertions.assertEquals(
                        fixture.secondReadyMediaId,
                        renamed.getId()
                ),
                () -> Assertions.assertEquals(FILE_SIZE, renamed.getFileSize()),
                () -> Assertions.assertEquals(WIDTH, renamed.getWidth()),
                () -> Assertions.assertEquals(HEIGHT, renamed.getHeight()),
                () -> Assertions.assertEquals(
                        VerificationStatus.VERIFIED,
                        saved.getVerificationStatus()
                ),
                () -> Assertions.assertFalse(
                        fixture.assignmentRepository.isUnassigned(
                                fixture.secondReadyMediaId
                        )
                ),
                () -> Assertions.assertTrue(
                        fixture.unassignedQueueViewModel.rows()
                                .stream()
                                .noneMatch(row -> row.mediaId()
                                        .equals(fixture.secondReadyMediaId))
                ),
                () -> Assertions.assertNotNull(
                        fixture.unassignedQueueViewModel.selectedRow().get()
                )
        );
    }

    @Test
    @DisplayName("Rename restrictions disable only rename save")
    void renameRestrictionsDisableOnlyRenameSave() throws Exception {
        final SceneReviewEditorViewModel collisionEditor =
                fixture.editor(fixture.successfulWorkflowService());
        collisionEditor.loadUnassignedDetails(fixture.unassignedDetails(
                fixture.collisionMediaId
        ));
        collisionEditor.titleProperty().set("Collision Scene");
        final Path collisionPath = Path.of(
                collisionEditor.previewProposedPathProperty().get()
        );
        Files.writeString(collisionPath, "existing");
        collisionEditor.titleProperty().set("Collision Scene ");

        final SceneReviewEditorViewModel unchangedEditor =
                fixture.editor(fixture.successfulWorkflowService());
        unchangedEditor.loadUnassignedDetails(fixture.unassignedDetails(
                fixture.canonicalMediaId
        ));

        final SceneReviewEditorViewModel missingEditor =
                fixture.editor(fixture.successfulWorkflowService());
        missingEditor.loadUnassignedDetails(fixture.unassignedDetails(
                fixture.missingSourceMediaId
        ));

        final SceneReviewEditorViewModel longEditor =
                fixture.editor(fixture.successfulWorkflowService());
        longEditor.loadUnassignedDetails(fixture.unassignedDetails(
                fixture.longNameMediaId
        ));
        longEditor.titleProperty().set("A".repeat(230));

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        collisionEditor.saveEnabledProperty().get()
                ),
                () -> Assertions.assertFalse(
                        collisionEditor.saveAndRenameEnabledProperty().get()
                ),
                () -> Assertions.assertTrue(
                        collisionEditor.previewProposedFilenameProperty()
                                .get()
                                .contains("Collision Scene")
                ),
                () -> Assertions.assertTrue(
                        unchangedEditor.saveEnabledProperty().get()
                ),
                () -> Assertions.assertEquals(
                        "UNCHANGED",
                        unchangedEditor.previewStatusProperty().get()
                ),
                () -> Assertions.assertFalse(
                        unchangedEditor.saveAndRenameEnabledProperty().get()
                ),
                () -> Assertions.assertTrue(
                        missingEditor.saveEnabledProperty().get()
                ),
                () -> Assertions.assertFalse(
                        missingEditor.saveAndRenameEnabledProperty().get()
                ),
                () -> Assertions.assertTrue(
                        longEditor.saveEnabledProperty().get()
                ),
                () -> Assertions.assertEquals(
                        "TOO_LONG",
                        longEditor.previewStatusProperty().get()
                ),
                () -> Assertions.assertFalse(
                        longEditor.saveAndRenameEnabledProperty().get()
                )
        );

        collisionEditor.saveWithoutRename();
        Assertions.assertEquals(
                "existing",
                Files.readString(collisionPath)
        );
    }

    @Test
    @DisplayName("Existing scene movie ambiguity requires override")
    void existingSceneMovieAmbiguityRequiresOverride() throws Exception {
        final SceneReviewEditorViewModel editor =
                fixture.editor(fixture.successfulWorkflowService());

        editor.loadExistingScene(fixture.tiedMovieSceneId);

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "REVIEW_REQUIRED",
                        editor.previewStatusProperty().get()
                ),
                () -> Assertions.assertFalse(
                        editor.saveAndRenameEnabledProperty().get()
                ),
                () -> Assertions.assertTrue(editor.saveEnabledProperty().get())
        );

        editor.explicitOriginalMovieOverrideIdProperty()
                .set(fixture.tiedMovieOne.getId());

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "READY",
                        editor.previewStatusProperty().get()
                ),
                () -> Assertions.assertTrue(
                        editor.previewProposedFilenameProperty()
                                .get()
                                .contains(fixture.tiedMovieOne.getTitle())
                ),
                () -> Assertions.assertTrue(
                        editor.saveAndRenameEnabledProperty().get()
                )
        );
    }

    @Test
    @DisplayName("Alias workflows require explicit confirmation")
    void aliasWorkflowsRequireExplicitConfirmation() throws Exception {
        final CapturingAliasConfirmation aliasDenied =
                new CapturingAliasConfirmation(false);
        final SceneReviewEditorViewModel noAliasEditor =
                fixture.editor(
                        fixture.successfulWorkflowService(),
                        aliasDenied
                );
        noAliasEditor.loadUnassignedDetails(fixture.unassignedDetails(
                fixture.unresolvedMediaId
        ));
        noAliasEditor.titleProperty().set("Manual Unmatched Title");
        noAliasEditor.resolvePerformerCandidate(
                "Mystery Performer",
                fixture.performer.getId(),
                fixture.performer.getMainName()
        );
        noAliasEditor.resolvePublisherCandidate(
                "Mystery Publisher",
                fixture.publisher.getId(),
                fixture.publisher.getName()
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(2, aliasDenied.prompts.size()),
                () -> Assertions.assertTrue(
                        noAliasEditor.selectedPerformers()
                                .stream()
                                .map(SelectedPerformer::id)
                                .toList()
                                .contains(fixture.performer.getId())
                ),
                () -> Assertions.assertEquals(
                        fixture.publisher.getId(),
                        noAliasEditor.selectedPublisherIdProperty().get()
                ),
                () -> Assertions.assertFalse(
                        fixture.performerRepository
                                .findById(fixture.performer.getId())
                                .orElseThrow()
                                .getAliases()
                                .contains("Mystery Performer")
                ),
                () -> Assertions.assertFalse(
                        fixture.publisherRepository
                                .findById(fixture.publisher.getId())
                                .orElseThrow()
                                .getAliases()
                                .contains("Mystery Publisher")
                ),
                () -> Assertions.assertEquals(
                        "Manual Unmatched Title",
                        noAliasEditor.titleProperty().get()
                )
        );

        final CapturingAliasConfirmation aliasAccepted =
                new CapturingAliasConfirmation(true);
        final SceneReviewEditorViewModel aliasEditor =
                fixture.editor(
                        fixture.successfulWorkflowService(),
                        aliasAccepted
                );
        aliasEditor.loadUnassignedDetails(fixture.unassignedDetails(
                fixture.unresolvedMediaId
        ));
        aliasEditor.titleProperty().set("Preserved Manual Title");
        aliasEditor.resolvePerformerCandidate(
                "Mystery Performer",
                fixture.performer.getId(),
                fixture.performer.getMainName()
        );
        aliasEditor.resolvePublisherCandidate(
                "Mystery Publisher",
                fixture.publisher.getId(),
                fixture.publisher.getName()
        );

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        fixture.performerRepository
                                .findById(fixture.performer.getId())
                                .orElseThrow()
                                .getAliases()
                                .contains("Mystery Performer")
                ),
                () -> Assertions.assertTrue(
                        fixture.publisherRepository
                                .findById(fixture.publisher.getId())
                                .orElseThrow()
                                .getAliases()
                                .contains("Mystery Publisher")
                ),
                () -> Assertions.assertEquals(
                        "Preserved Manual Title",
                        aliasEditor.titleProperty().get()
                ),
                () -> Assertions.assertTrue(
                        fixture.queueService
                                .loadPage(ReviewQueueFilter.firstPage())
                                .details()
                                .stream()
                                .filter(detail -> detail.mediaId()
                                        .equals(fixture.unresolvedMediaId))
                                .findFirst()
                                .orElseThrow()
                                .resolvedPerformerNames()
                                .contains(fixture.performer.getMainName())
                )
        );
    }

    @Test
    @DisplayName("Existing scene review updates or retains queue membership")
    void existingSceneReviewUpdatesOrRetainsQueueMembership() throws Exception {
        final SceneReviewEditorViewModel verifyEditor =
                fixture.editor(fixture.successfulWorkflowService());
        verifyEditor.loadExistingScene(fixture.unverifiedSceneId);
        verifyEditor.titleProperty().set("Verified Existing");
        verifyEditor.releaseDateTextProperty().set("2026-09-18");
        verifyEditor.selectedMovieIdProperty().set(
                fixture.originalMovie.getId()
        );
        verifyEditor.saveWithoutRename();
        fixture.sceneQueueViewModel.load();

        final Scene verified = fixture.sceneRepository
                .findById(fixture.unverifiedSceneId)
                .orElseThrow();

        final SceneReviewEditorViewModel needsReviewEditor =
                fixture.editor(fixture.successfulWorkflowService());
        needsReviewEditor.loadExistingScene(fixture.needsReviewSceneId);
        needsReviewEditor.titleProperty().set("Still Needs Review");
        needsReviewEditor.saveAsNeedsReview();
        fixture.sceneQueueViewModel.load();

        final Scene needsReview = fixture.sceneRepository
                .findById(fixture.needsReviewSceneId)
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        VerificationStatus.VERIFIED,
                        verified.getVerificationStatus()
                ),
                () -> Assertions.assertTrue(
                        fixture.sceneQueueViewModel.rows()
                                .stream()
                                .noneMatch(row -> row.sceneId()
                                        .equals(fixture.unverifiedSceneId))
                ),
                () -> Assertions.assertEquals(
                        VerificationStatus.NEEDS_REVIEW,
                        needsReview.getVerificationStatus()
                ),
                () -> Assertions.assertEquals(
                        "Still Needs Review",
                        needsReview.getTitle()
                ),
                () -> Assertions.assertTrue(
                        fixture.sceneQueueViewModel.rows()
                                .stream()
                                .anyMatch(row -> row.sceneId()
                                        .equals(fixture.needsReviewSceneId))
                )
        );
    }

    @Test
    @DisplayName("Partial rename failure preserves scene as needs review")
    void partialRenameFailurePreservesSceneAsNeedsReview() throws Exception {
        final SceneReviewEditorViewModel editor =
                fixture.editor(fixture.failingWorkflowService());
        editor.loadUnassignedDetails(fixture.unassignedDetails(
                fixture.renameFailureMediaId
        ));
        editor.titleProperty().set("Rollback Scene");
        editor.selectedPublisherIdProperty().set(fixture.publisher.getId());
        editor.selectedSeriesIdProperty().set(fixture.series.getId());
        editor.selectedMovieIdProperty().set(fixture.originalMovie.getId());
        editor.explicitOriginalMovieOverrideIdProperty().set(
                fixture.originalMovie.getId()
        );

        final Path originalPath = fixture.renameFailureMediaPath;
        editor.saveAndRename();
        final SceneReviewSaveResult result =
                editor.lastSaveResultProperty().get();
        fixture.unassignedQueueViewModel.load();
        fixture.sceneQueueViewModel.load();

        final Scene scene = fixture.sceneRepository
                .findById(result.sceneId())
                .orElseThrow();
        final MediaFile mediaFile = fixture.mediaFileRepository
                .findById(fixture.renameFailureMediaId)
                .orElseThrow();

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        SceneReviewSaveStatus.CREATED_RENAME_FAILED_NEEDS_REVIEW,
                        result.status()
                ),
                () -> Assertions.assertTrue(result.databasePersisted()),
                () -> Assertions.assertFalse(result.physicalRenameSucceeded()),
                () -> Assertions.assertEquals(
                        VerificationStatus.NEEDS_REVIEW,
                        scene.getVerificationStatus()
                ),
                () -> Assertions.assertFalse(
                        fixture.assignmentRepository.isUnassigned(
                                fixture.renameFailureMediaId
                        )
                ),
                () -> Assertions.assertEquals(originalPath, mediaFile.getPath()),
                () -> Assertions.assertTrue(Files.exists(originalPath)),
                () -> Assertions.assertTrue(
                        editor.saveMessageProperty()
                                .get()
                                .contains("rename failed")
                ),
                () -> Assertions.assertTrue(
                        fixture.sceneQueueViewModel.rows()
                                .stream()
                                .anyMatch(row -> row.sceneId()
                                        .equals(result.sceneId()))
                )
        );
    }

    @Test
    @DisplayName("Entity creation workflow selects records and refreshes preview")
    void entityCreationWorkflowSelectsRecordsAndRefreshesPreview()
            throws Exception {

        final SceneReviewEditorViewModel editor =
                fixture.editor(fixture.successfulWorkflowService());
        editor.loadUnassignedDetails(fixture.unassignedDetails(
                fixture.readyMediaId
        ));

        final Publisher createdPublisher =
                fixture.entityManagementService.createPublisher(
                        "Created Publisher",
                        List.of("Created Pub Alias")
                );
        final UUID suggestedPublisherId = fixture.suggestionService
                .suggestPublishers("Created Pub Alias", 5)
                .getFirst()
                .id();
        final Series createdSeries =
                fixture.entityManagementService.createSeries(
                        "Created Series",
                        suggestedPublisherId
                );
        final Performer createdPerformer =
                fixture.entityManagementService.createPerformer(
                        "Created Performer",
                        List.of(),
                        PerformerCategory.ACTOR
                );
        final Movie createdMovie = fixture.entityManagementService.createMovie(
                "Created Movie",
                LocalDate.of(2019, 2, 3),
                suggestedPublisherId,
                false
        );

        editor.selectedPublisherIdProperty().set(createdPublisher.getId());
        editor.selectedSeriesIdProperty().set(createdSeries.getId());
        editor.selectedMovieIdProperty().set(createdMovie.getId());
        editor.explicitOriginalMovieOverrideIdProperty().set(
                createdMovie.getId()
        );
        editor.addPerformer(
                createdPerformer.getId(),
                createdPerformer.getMainName()
        );

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        createdPublisher.getId(),
                        editor.selectedPublisherIdProperty().get()
                ),
                () -> Assertions.assertEquals(
                        createdSeries.getId(),
                        editor.selectedSeriesIdProperty().get()
                ),
                () -> Assertions.assertEquals(
                        createdMovie.getId(),
                        editor.selectedMovieIdProperty().get()
                ),
                () -> Assertions.assertTrue(
                        editor.selectedPerformers()
                                .stream()
                                .map(SelectedPerformer::id)
                                .toList()
                                .contains(createdPerformer.getId())
                ),
                () -> Assertions.assertEquals(
                        "READY",
                        editor.previewStatusProperty().get()
                ),
                () -> Assertions.assertTrue(
                        editor.previewProposedFilenameProperty()
                                .get()
                                .contains("Created Publisher")
                ),
                () -> Assertions.assertTrue(
                        editor.previewProposedFilenameProperty()
                                .get()
                                .contains("Created Series")
                ),
                () -> Assertions.assertTrue(
                        editor.previewProposedFilenameProperty()
                                .get()
                                .contains("Created Movie")
                ),
                () -> Assertions.assertTrue(
                        editor.previewProposedFilenameProperty()
                                .get()
                                .contains("Created Performer")
                )
        );
    }

    private static final class CapturingAliasConfirmation
            implements SceneReviewEditorViewModel.AliasConfirmation {
        private final boolean confirmed;
        private final List<String> prompts = new ArrayList<>();

        private CapturingAliasConfirmation(boolean confirmed) {
            this.confirmed = confirmed;
        }

        @Override
        public boolean confirm(String candidateText, String primaryName) {
            prompts.add(candidateText + " -> " + primaryName);
            return confirmed;
        }
    }

    private static final class AcceptanceFixture {
        private final Path temporaryDirectory;
        private DatabaseManager databaseManager;
        private PublisherRepository publisherRepository;
        private PerformerRepository performerRepository;
        private SeriesRepository seriesRepository;
        private MediaFileRepository mediaFileRepository;
        private SceneRepository sceneRepository;
        private MovieRepository movieRepository;
        private MediaAssignmentRepository assignmentRepository;
        private CatalogService catalogService;
        private EntityManagementService entityManagementService;
        private EntitySuggestionService suggestionService;
        private GuiReviewQueueService queueService;
        private ReviewQueueViewModel unassignedQueueViewModel;
        private SceneReviewQueueViewModel sceneQueueViewModel;
        private DefaultSceneReviewDraftLoader draftLoader;
        private SceneReviewRenamePreviewService previewService;
        private Publisher publisher;
        private Publisher secondPublisher;
        private Performer performer;
        private Performer secondPerformer;
        private Series series;
        private Movie originalMovie;
        private Movie compilationMovie;
        private Movie tiedMovieOne;
        private Movie tiedMovieTwo;
        private UUID readyMediaId;
        private UUID secondReadyMediaId;
        private UUID collisionMediaId;
        private UUID canonicalMediaId;
        private UUID missingSourceMediaId;
        private UUID longNameMediaId;
        private UUID unresolvedMediaId;
        private UUID renameFailureMediaId;
        private UUID unverifiedSceneId;
        private UUID needsReviewSceneId;
        private UUID tiedMovieSceneId;
        private Path readyMediaPath;
        private Path secondReadyMediaPath;
        private Path renameFailureMediaPath;

        private AcceptanceFixture(Path temporaryDirectory) {
            this.temporaryDirectory = temporaryDirectory;
        }

        private void initialize() throws Exception {
            databaseManager = new DatabaseManager(
                    temporaryDirectory.resolve(DATABASE_FILE_NAME)
            );
            new SchemaManager(databaseManager).initialize();

            publisherRepository = new PublisherRepository(databaseManager);
            performerRepository = new PerformerRepository(databaseManager);
            seriesRepository = new SeriesRepository(databaseManager);
            mediaFileRepository = new MediaFileRepository(databaseManager);
            sceneRepository = new SceneRepository(databaseManager);
            movieRepository = new MovieRepository(databaseManager);
            assignmentRepository =
                    new MediaAssignmentRepository(databaseManager);
            catalogService = new CatalogService(
                    publisherRepository,
                    performerRepository,
                    seriesRepository,
                    mediaFileRepository,
                    sceneRepository,
                    new SearchRepository(databaseManager),
                    movieRepository
            );
            entityManagementService = new EntityManagementService(
                    catalogService,
                    publisherRepository,
                    performerRepository
            );
            suggestionService = new EntitySuggestionService(
                    new EntitySuggestionRepository(databaseManager)
            );
            final MediaFilenameIndexingService indexingService =
                    new MediaFilenameIndexingService(
                            mediaFileRepository,
                            assignmentRepository,
                            new MediaFilenameParser(),
                            new FilenameMetadataMatcher(
                                    new EntitySuggestionRepository(
                                            databaseManager
                                    )
                            ),
                            catalogService,
                            sceneRepository,
                            movieRepository
                    );
            queueService = new GuiReviewQueueService(
                    indexingService,
                    mediaFileRepository
            );
            unassignedQueueViewModel = new ReviewQueueViewModel(
                    queueService,
                    DIRECT_EXECUTOR,
                    DIRECT_EXECUTOR
            );
            sceneQueueViewModel = new SceneReviewQueueViewModel(
                    new SceneReviewQueueService(sceneRepository)
                            ::loadReviewScenes,
                    DIRECT_EXECUTOR,
                    DIRECT_EXECUTOR
            );
            final OriginalMovieSelector originalMovieSelector =
                    new OriginalMovieSelector(movieRepository);
            draftLoader = new DefaultSceneReviewDraftLoader(
                    new SceneReviewDraftFactory(
                            sceneRepository,
                            movieRepository,
                            originalMovieSelector
                    )
            );
            previewService = new SceneReviewRenamePreviewService(
                    catalogService,
                    mediaFileRepository,
                    originalMovieSelector
            );

            createEntities();
            createMediaRows();
            createSceneRows();
            unassignedQueueViewModel.load();
            sceneQueueViewModel.load();
        }

        private SceneReviewEditorViewModel editor(
                SceneReviewWorkflowService workflowService) {

            return editor(workflowService, (candidate, primary) -> false);
        }

        private SceneReviewEditorViewModel editor(
                SceneReviewWorkflowService workflowService,
                SceneReviewEditorViewModel.AliasConfirmation confirmation) {

            return new SceneReviewEditorViewModel(
                    workflowService,
                    draftLoader,
                    confirmation,
                    new SceneReviewEditorViewModel.AliasPersistence() {
                        @Override
                        public void addPerformerAlias(
                                UUID performerId,
                                String alias) throws SQLException {

                            entityManagementService.addPerformerAlias(
                                    performerId,
                                    alias
                            );
                        }

                        @Override
                        public void addPublisherAlias(
                                UUID publisherId,
                                String alias) throws SQLException {

                            entityManagementService.addPublisherAlias(
                                    publisherId,
                                    alias
                            );
                        }
                    },
                    previewService::preview,
                    DIRECT_EXECUTOR,
                    DIRECT_EXECUTOR
            );
        }

        private SceneReviewWorkflowService successfulWorkflowService() {
            return workflowService(mediaRenameService(
                    new DefaultMediaFileMover(),
                    new RepositoryMediaPathUpdater(mediaFileRepository)
            ));
        }

        private SceneReviewWorkflowService failingWorkflowService() {
            return workflowService(mediaRenameService(
                    new DefaultMediaFileMover(),
                    (mediaFile, newPath) -> {
                        throw new SQLException("forced path update failure");
                    }
            ));
        }

        private SceneReviewWorkflowService workflowService(
                MediaRenameService renameService) {

            return new SceneReviewWorkflowService(
                    catalogService,
                    assignmentRepository,
                    sceneRepository,
                    movieRepository,
                    renameService
            );
        }

        private MediaRenameService mediaRenameService(
                MediaFileMover mover,
                MediaPathUpdater pathUpdater) {

            return new MediaRenameService(
                    mediaFileRepository,
                    sceneRepository,
                    assignmentRepository,
                    new OriginalMovieSelector(movieRepository),
                    mover,
                    pathUpdater
            );
        }

        private ReviewDetails unassignedDetails(UUID mediaId)
                throws SQLException {

            return queueService.loadPage(ReviewQueueFilter.firstPage())
                    .details()
                    .stream()
                    .filter(detail -> detail.mediaId().equals(mediaId))
                    .findFirst()
                    .orElseThrow();
        }

        private void createEntities() throws SQLException {
            publisher = catalogService.createPublisher(
                    "Studio Alpha",
                    List.of("Alpha Alias")
            );
            secondPublisher = catalogService.createPublisher(
                    "Studio Beta",
                    List.of()
            );
            performer = catalogService.createPerformer(
                    "Alice Actor",
                    List.of("Alice Alias"),
                    PerformerCategory.ACTOR
            );
            secondPerformer = catalogService.createPerformer(
                    "Bea Actor",
                    List.of(),
                    PerformerCategory.ACTOR
            );
            series = catalogService.createSeries(
                    "Series Prime",
                    publisher.getId()
            );
            originalMovie = catalogService.createMovie(
                    "Original Movie",
                    LocalDate.of(2020, 1, 1),
                    publisher.getId(),
                    false,
                    List.of(),
                    List.of()
            );
            compilationMovie = catalogService.createMovie(
                    "Compilation Movie",
                    LocalDate.of(2024, 1, 1),
                    publisher.getId(),
                    true,
                    List.of(),
                    List.of()
            );
            tiedMovieOne = catalogService.createMovie(
                    "Tied Movie One",
                    LocalDate.of(2021, 4, 10),
                    publisher.getId(),
                    false,
                    List.of(),
                    List.of()
            );
            tiedMovieTwo = catalogService.createMovie(
                    "Tied Movie Two",
                    LocalDate.of(2021, 4, 10),
                    publisher.getId(),
                    false,
                    List.of(),
                    List.of()
            );
            catalogService.createMovie(
                    "Undated Movie",
                    null,
                    publisher.getId(),
                    false,
                    List.of(),
                    List.of()
            );
        }

        private void createMediaRows() throws Exception {
            readyMediaPath = createMediaFile(
                    "(26.01.02) Studio Alpha - Ready Scene - Alice Actor.mp4"
            );
            readyMediaId = insertMedia(readyMediaPath);
            secondReadyMediaPath = createMediaFile(
                    "(26.01.03) Studio Alpha - Second Ready - Alice Actor.mp4"
            );
            secondReadyMediaId = insertMedia(secondReadyMediaPath);
            final Path collisionPath = createMediaFile(
                    "(26.01.04) Studio Alpha - Collision Start - Alice Actor.mp4"
            );
            collisionMediaId = insertMedia(collisionPath);
            final Path canonicalPath = createMediaFile(
                    "(26.01.05) Studio Alpha - Original Movie - Canonical Scene - Alice Actor.mp4"
            );
            canonicalMediaId = insertMedia(canonicalPath);
            final Path missingSourcePath = temporaryDirectory.resolve(
                    "(26.01.06) Studio Alpha - Missing Source - Alice Actor.mp4"
            );
            missingSourceMediaId = insertMedia(missingSourcePath);
            final Path longPath = createMediaFile(
                    "(26.01.07) Studio Alpha - Long Start - Alice Actor.mp4"
            );
            longNameMediaId = insertMedia(longPath);
            final Path unresolvedPath = createMediaFile(
                    "(26.01.08) Mystery Publisher - Unknown Scene - Mystery Performer.mp4"
            );
            unresolvedMediaId = insertMedia(unresolvedPath);
            renameFailureMediaPath = createMediaFile(
                    "(26.01.09) Studio Alpha - Rollback Start - Alice Actor.mp4"
            );
            renameFailureMediaId = insertMedia(renameFailureMediaPath);
        }

        private void createSceneRows() throws SQLException {
            final Path unverifiedPath = temporaryDirectory.resolve(
                    "unverified.mp4"
            );
            final Path needsReviewPath = temporaryDirectory.resolve(
                    "needs-review.mp4"
            );
            final Path tiedPath = temporaryDirectory.resolve("tied.mp4");
            final MediaFile unverifiedMedia = mediaFile(
                    UUID.randomUUID(),
                    unverifiedPath
            );
            final MediaFile needsReviewMedia = mediaFile(
                    UUID.randomUUID(),
                    needsReviewPath
            );
            final MediaFile tiedMedia = mediaFile(UUID.randomUUID(), tiedPath);
            mediaFileRepository.insert(unverifiedMedia);
            mediaFileRepository.insert(needsReviewMedia);
            mediaFileRepository.insert(tiedMedia);

            final Scene unverified = scene(
                    "Unverified Scene",
                    List.of(unverifiedMedia),
                    VerificationStatus.UNVERIFIED
            );
            final Scene needsReview = scene(
                    "Needs Review Scene",
                    List.of(needsReviewMedia),
                    VerificationStatus.NEEDS_REVIEW
            );
            final Scene tied = scene(
                    "Tied Scene",
                    List.of(tiedMedia),
                    VerificationStatus.UNVERIFIED
            );
            unverifiedSceneId = unverified.getId();
            needsReviewSceneId = needsReview.getId();
            tiedMovieSceneId = tied.getId();
            sceneRepository.insert(unverified);
            sceneRepository.insert(needsReview);
            sceneRepository.insert(tied);
            addSceneToMovie(originalMovie.getId(), unverified);
            addSceneToMovie(compilationMovie.getId(), unverified);
            addSceneToMovie(tiedMovieOne.getId(), tied);
            addSceneToMovie(tiedMovieTwo.getId(), tied);
        }

        private Path createMediaFile(String filename) throws IOException {
            final Path path = temporaryDirectory.resolve(filename);
            Files.writeString(path, "video");
            return path;
        }

        private UUID insertMedia(Path path) throws SQLException {
            final UUID id = UUID.randomUUID();
            mediaFileRepository.insert(mediaFile(id, path));
            return id;
        }

        private MediaFile mediaFile(UUID id, Path path) {
            return new MediaFile(
                    id,
                    path,
                    FILE_SIZE,
                    null,
                    Duration.ofMillis(1_000L),
                    WIDTH,
                    HEIGHT,
                    LAST_MODIFIED_MILLIS
            );
        }

        private Scene scene(
                String title,
                List<MediaFile> files,
                VerificationStatus status) {

            return new Scene(
                    UUID.randomUUID(),
                    title,
                    publisher,
                    LocalDate.of(2026, 1, 1),
                    null,
                    series,
                    null,
                    null,
                    List.of(performer),
                    files,
                    status
            );
        }

        private void addSceneToMovie(UUID movieId, Scene scene)
                throws SQLException {

            final Movie movie = movieRepository.findById(movieId).orElseThrow();
            final List<Scene> scenes = new ArrayList<>(movie.getScenes());
            scenes.add(scene);
            movie.setScenes(scenes);
            movieRepository.update(movie);
        }
    }
}

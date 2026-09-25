package service;

import model.VerificationStatus;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ReadyPageBatchService implements ReadyPageBatchOperations {
    private static final String GENERIC_FAILURE =
            "Could not process this media row.";

    private final MediaFileRepository mediaFileRepository;
    private final MediaAssignmentRepository assignmentRepository;
    private final MediaFilenameIndexingService indexingService;
    private final SceneReviewDraftFactory draftFactory;
    private final SceneReviewSaver reviewSaver;

    public ReadyPageBatchService(
            MediaFileRepository mediaFileRepository,
            MediaAssignmentRepository assignmentRepository,
            MediaFilenameIndexingService indexingService,
            SceneReviewDraftFactory draftFactory,
            SceneReviewSaver reviewSaver) {

        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        this.assignmentRepository = Objects.requireNonNull(
                assignmentRepository,
                "Assignment repository must not be null"
        );
        this.indexingService = Objects.requireNonNull(
                indexingService,
                "Filename indexing service must not be null"
        );
        this.draftFactory = Objects.requireNonNull(
                draftFactory,
                "Scene review draft factory must not be null"
        );
        this.reviewSaver = Objects.requireNonNull(
                reviewSaver,
                "Scene review saver must not be null"
        );
    }

    @Override
    public ReadyPageBatchPreflight preflight(
            ReadyPageBatchPageSnapshot snapshot) {

        Objects.requireNonNull(snapshot, "Page snapshot must not be null");
        final List<ReadyPageBatchCandidate> eligible = new ArrayList<>();
        final List<ReadyPageBatchRowResult> excluded = new ArrayList<>();

        for (ReadyPageBatchCandidate candidate : snapshot.readyCandidates()) {
            final Validation validation = validate(candidate);

            if (validation.draft() == null) {
                excluded.add(validation.result());
            } else {
                eligible.add(candidate);
            }
        }

        return new ReadyPageBatchPreflight(
                snapshot.displayedRows(),
                snapshot.initialReadyCandidates(),
                eligible,
                excluded
        );
    }

    @Override
    public ReadyPageBatchResult execute(
            ReadyPageBatchPreflight preflight,
            ReadyPageBatchMode mode) {

        Objects.requireNonNull(preflight, "Preflight must not be null");
        Objects.requireNonNull(mode, "Batch mode must not be null");
        final List<ReadyPageBatchRowResult> results =
                new ArrayList<>(preflight.excludedRows());

        for (ReadyPageBatchCandidate candidate
                : preflight.eligibleCandidates()) {
            results.add(process(candidate, mode));
        }

        return new ReadyPageBatchResult(preflight, results);
    }

    private ReadyPageBatchRowResult process(
            ReadyPageBatchCandidate candidate,
            ReadyPageBatchMode mode) {

        final Validation validation = validate(candidate);
        ReadyPageBatchRowResult result;

        if (validation.draft() == null) {
            result = validation.result();
        } else {
            result = save(candidate, validation.draft(), mode);
        }

        return result;
    }

    private ReadyPageBatchRowResult save(
            ReadyPageBatchCandidate candidate,
            SceneReviewDraft draft,
            ReadyPageBatchMode mode) {

        ReadyPageBatchRowResult result;

        try {
            final SceneReviewSaveResult saveResult = reviewSaver.save(
                    new SceneReviewSaveRequest(draft, renameChoice(mode))
            );
            result = savedResult(candidate, saveResult);
        } catch (IllegalArgumentException exception) {
            result = classifyLateConflict(candidate);
        } catch (IOException | SQLException | RuntimeException exception) {
            result = failed(candidate);
        }

        return result;
    }

    private Validation validate(ReadyPageBatchCandidate candidate) {
        Validation validation;

        try {
            if (mediaFileRepository.findById(candidate.mediaId()).isEmpty()) {
                validation = invalid(candidate,
                        ReadyPageBatchOutcome.SKIPPED_MEDIA_MISSING,
                        "Media record no longer exists.");
            } else if (!assignmentRepository.isUnassigned(candidate.mediaId())) {
                validation = invalid(candidate,
                        ReadyPageBatchOutcome.SKIPPED_ALREADY_ASSIGNED,
                        "Media is already assigned.");
            } else {
                validation = validatePreview(candidate);
            }
        } catch (SQLException | RuntimeException exception) {
            validation = invalid(candidate, ReadyPageBatchOutcome.FAILED,
                    GENERIC_FAILURE);
        }

        return validation;
    }

    private Validation validatePreview(ReadyPageBatchCandidate candidate)
            throws SQLException {

        final FilenamePreview preview = indexingService.preview(
                candidate.mediaId()
        );
        final boolean ready = !preview.assigned()
                && preview.matchResult().status() == FilenameMatchStatus.READY
                && preview.matchResult().bestInterpretation() != null;
        Validation validation;

        if (!ready) {
            validation = preview.assigned()
                    ? invalid(candidate,
                            ReadyPageBatchOutcome.SKIPPED_ALREADY_ASSIGNED,
                            "Media is already assigned.")
                    : invalid(candidate,
                            ReadyPageBatchOutcome.SKIPPED_NO_LONGER_READY,
                            "Filename match is no longer READY.");
        } else {
            final EditableSceneReviewDraft editable =
                    draftFactory.fromUnassignedPreview(preview);
            final SceneReviewDraft draft = editable.draft();
            final boolean validDraft = editable.matchStatus()
                    == FilenameMatchStatus.READY
                    && draft.mode() == SceneReviewMode.CREATE_FROM_MEDIA
                    && draft.verificationStatus() == VerificationStatus.VERIFIED
                    && draft.mediaFileIds().equals(List.of(candidate.mediaId()));

            validation = validDraft
                    ? new Validation(draft, null)
                    : invalid(candidate,
                            ReadyPageBatchOutcome.SKIPPED_NO_LONGER_READY,
                            "Fresh READY interpretation could not be drafted.");
        }

        return validation;
    }

    private ReadyPageBatchRowResult classifyLateConflict(
            ReadyPageBatchCandidate candidate) {

        ReadyPageBatchRowResult result = failed(candidate);

        try {
            if (mediaFileRepository.findById(candidate.mediaId()).isEmpty()) {
                result = row(candidate,
                        ReadyPageBatchOutcome.SKIPPED_MEDIA_MISSING,
                        null, "Media record no longer exists.");
            } else if (!assignmentRepository.isUnassigned(candidate.mediaId())) {
                result = row(candidate,
                        ReadyPageBatchOutcome.SKIPPED_ALREADY_ASSIGNED,
                        SceneReviewSaveStatus.ASSIGNMENT_CONFLICT,
                        "Media became assigned before save.");
            }
        } catch (SQLException exception) {
            result = failed(candidate);
        }

        return result;
    }

    private ReadyPageBatchRowResult savedResult(
            ReadyPageBatchCandidate candidate,
            SceneReviewSaveResult result) {

        final ReadyPageBatchOutcome outcome = switch (result.status()) {
            case CREATED -> ReadyPageBatchOutcome.CREATED_WITHOUT_RENAME;
            case CREATED_AND_RENAMED ->
                    ReadyPageBatchOutcome.CREATED_AND_RENAMED;
            case CREATED_RENAME_FAILED_NEEDS_REVIEW ->
                    ReadyPageBatchOutcome.CREATED_RENAME_FAILED_NEEDS_REVIEW;
            case ASSIGNMENT_CONFLICT ->
                    ReadyPageBatchOutcome.SKIPPED_ALREADY_ASSIGNED;
            default -> ReadyPageBatchOutcome.FAILED;
        };
        final String message = switch (result.status()) {
            case CREATED_RENAME_FAILED_NEEDS_REVIEW ->
                    "Scene was created, but rename failed; review is required.";
            case CREATED, CREATED_AND_RENAMED -> "";
            default -> GENERIC_FAILURE;
        };
        return row(candidate, outcome, result.status(), message);
    }

    private SceneReviewRenameChoice renameChoice(ReadyPageBatchMode mode) {
        return mode == ReadyPageBatchMode.CREATE_AND_RENAME
                ? SceneReviewRenameChoice.RENAME
                : SceneReviewRenameChoice.DO_NOT_RENAME;
    }

    private Validation invalid(
            ReadyPageBatchCandidate candidate,
            ReadyPageBatchOutcome outcome,
            String message) {

        return new Validation(null, row(candidate, outcome, null, message));
    }

    private ReadyPageBatchRowResult failed(
            ReadyPageBatchCandidate candidate) {

        return row(candidate, ReadyPageBatchOutcome.FAILED, null,
                GENERIC_FAILURE);
    }

    private ReadyPageBatchRowResult row(
            ReadyPageBatchCandidate candidate,
            ReadyPageBatchOutcome outcome,
            SceneReviewSaveStatus saveStatus,
            String message) {

        return new ReadyPageBatchRowResult(
                candidate.mediaId(), candidate.path(), outcome,
                saveStatus, message
        );
    }

    private record Validation(
            SceneReviewDraft draft,
            ReadyPageBatchRowResult result) {
    }
}

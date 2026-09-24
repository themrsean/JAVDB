package service;

import media.FilenameParseIssue;
import model.MediaFile;
import repository.MediaAssignment;
import repository.MediaAssignmentRepository;
import repository.MediaFileRepository;
import repository.MediaLibraryFilter;
import repository.MediaLibraryRecord;
import repository.MediaLibraryRepository;
import ui.library.MediaLibraryDisplayFormatter;

import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MediaLibraryService implements MediaLibraryDataSource {
    private final MediaLibraryRepository libraryRepository;
    private final MediaFileRepository mediaFileRepository;
    private final MediaAssignmentRepository assignmentRepository;
    private final MediaFilenameIndexingService indexingService;

    public MediaLibraryService(
            MediaLibraryRepository libraryRepository,
            MediaFileRepository mediaFileRepository,
            MediaAssignmentRepository assignmentRepository,
            MediaFilenameIndexingService indexingService) {
        this.libraryRepository = Objects.requireNonNull(libraryRepository);
        this.mediaFileRepository = Objects.requireNonNull(mediaFileRepository);
        this.assignmentRepository = Objects.requireNonNull(assignmentRepository);
        this.indexingService = Objects.requireNonNull(indexingService);
    }

    @Override
    public MediaLibraryPage loadPage(MediaLibraryFilter filter)
            throws SQLException {
        final MediaLibraryFilter effective = filter == null
                ? MediaLibraryFilter.firstPage() : filter;
        final List<MediaLibraryRecord> records =
                libraryRepository.findPage(effective);
        final boolean hasNext = records.size() > effective.limit();
        final int rowCount = Math.min(records.size(), effective.limit());
        final List<MediaLibraryRow> rows = records.subList(0, rowCount)
                .stream().map(this::row).toList();
        return new MediaLibraryPage(effective, rows, hasNext);
    }

    @Override
    public MediaLibraryDetails loadDetails(UUID mediaId) throws SQLException {
        Objects.requireNonNull(mediaId, "Media file ID must not be null");
        final MediaFile mediaFile = mediaFileRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media file is no longer available."
                ));
        final MediaAssignment assignment =
                assignmentRepository.findAssignment(mediaId);
        final FilenamePreview preview = indexingService.preview(mediaId);
        final List<String> warnings = new ArrayList<>();
        warnings.addAll(preview.parsedFilename().issues().stream()
                .map(FilenameParseIssue::name).toList());
        warnings.addAll(preview.parsedFilename().warnings());
        warnings.addAll(preview.matchResult().warnings());
        warnings.addAll(preview.matchResult().errors());

        return new MediaLibraryDetails(
                mediaId,
                mediaFile.getPath(),
                Files.exists(mediaFile.getPath()),
                MediaLibraryDisplayFormatter.fileSize(mediaFile.getFileSize()),
                MediaLibraryDisplayFormatter.lastModified(
                        mediaFile.getLastModifiedMillis()),
                MediaLibraryDisplayFormatter.resolution(mediaFile),
                MediaLibraryDisplayFormatter.duration(mediaFile.getDuration()),
                blankIfNull(mediaFile.getContentHash()),
                assignment.scenes(),
                assignment.movies(),
                preview.parsedFilename().status(),
                preview.matchResult().status(),
                interpretation(preview.matchResult().bestInterpretation()),
                warnings
        );
    }

    private MediaLibraryRow row(MediaLibraryRecord record) {
        final MediaFile mediaFile = record.mediaFile();
        final String filename = mediaFile.getPath().getFileName() == null
                ? mediaFile.getPath().toString()
                : mediaFile.getPath().getFileName().toString();
        final String directory = mediaFile.getPath().getParent() == null
                ? "" : mediaFile.getPath().getParent().toString();
        return new MediaLibraryRow(
                mediaFile.getId(), mediaFile.getPath(), filename, directory,
                MediaLibraryDisplayFormatter.resolution(mediaFile),
                MediaLibraryDisplayFormatter.duration(mediaFile.getDuration()),
                MediaLibraryDisplayFormatter.fileSize(mediaFile.getFileSize()),
                MediaLibraryDisplayFormatter.lastModified(
                        mediaFile.getLastModifiedMillis()),
                record.assignmentState().toString()
        );
    }

    private String interpretation(FilenameInterpretation interpretation) {
        if (interpretation == null) {
            return "No interpretation available";
        }
        final List<String> parts = new ArrayList<>();
        addMatch(parts, "Publisher", interpretation.publisher());
        addMatch(parts, "Series", interpretation.series());
        addMatch(parts, "Movie", interpretation.movie());
        if (!interpretation.performers().isEmpty()) {
            parts.add("Performers: " + String.join(", ",
                    interpretation.performers().stream()
                            .map(this::matchText).toList()));
        }
        if (!interpretation.unresolvedSegments().isEmpty()) {
            parts.add("Unresolved: "
                    + String.join(", ", interpretation.unresolvedSegments()));
        }
        return parts.isEmpty() ? "No matched entities" : String.join("; ", parts);
    }

    private void addMatch(List<String> parts, String label, EntityMatch match) {
        if (match != null) {
            parts.add(label + ": " + matchText(match));
        }
    }

    private String matchText(EntityMatch match) {
        return blankIfNull(match.name()).isBlank()
                ? blankIfNull(match.candidateText()) : match.name();
    }

    private String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}

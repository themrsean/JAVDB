package cli;

import model.MediaFile;
import model.Movie;
import model.Performer;
import model.Publisher;
import model.Scene;
import model.Series;
import backup.BackupResult;
import backup.BackupVerificationResult;
import backup.RestoreResult;
import repository.MediaAssignment;
import repository.MediaAssignmentReference;
import service.BatchSceneCreationFileResult;
import service.BatchSceneCreationResult;
import service.AutoIndexFileResult;
import service.AutoIndexResult;
import service.MediaScanFileResult;
import service.MediaScanResult;
import service.MediaVerificationFileResult;
import service.MediaVerificationResult;
import service.FilenamePreview;
import service.MediaRenamePreview;
import service.MediaRenameResult;

import java.util.List;

final class OutputFormatter {
    private static final String TSV_MODE = "tsv";
    private static final String TAB = "\t";
    private static final String NEWLINE_ESCAPE = "\\n";
    private static final String TAB_ESCAPE = "\\t";

    private OutputFormatter() {
    }

    static boolean isTsv(ParsedCommand command) {
        return TSV_MODE.equalsIgnoreCase(command.outputMode());
    }

    static String publishers(List<Publisher> publishers, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("id\tname\taliases\n");
        }

        for (Publisher publisher : publishers) {
            if (tsv) {
                builder.append(escape(publisher.getId().toString()))
                        .append(TAB)
                        .append(escape(publisher.getName()))
                        .append(TAB)
                        .append(escape(String.join(",", publisher.getAliases())))
                        .append(System.lineSeparator());
            } else {
                builder.append(publisher.getId())
                        .append("  ")
                        .append(publisher.getName())
                        .append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    static String performers(List<Performer> performers, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("id\tmain_name\tcategory\taliases\n");
        }

        for (Performer performer : performers) {
            if (tsv) {
                builder.append(escape(performer.getId().toString()))
                        .append(TAB)
                        .append(escape(performer.getMainName()))
                        .append(TAB)
                        .append(performer.getCategory().name())
                        .append(TAB)
                        .append(escape(String.join(",", performer.getAliases())))
                        .append(System.lineSeparator());
            } else {
                builder.append(performer.getId())
                        .append("  ")
                        .append(performer.getMainName())
                        .append("  ")
                        .append(performer.getCategory())
                        .append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    static String series(List<Series> series, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("id\ttitle\tpublisher_id\n");
        }

        for (Series item : series) {
            if (tsv) {
                builder.append(escape(item.getId().toString()))
                        .append(TAB)
                        .append(escape(item.getTitle()))
                        .append(TAB)
                        .append(item.getPublisher().getId())
                        .append(System.lineSeparator());
            } else {
                builder.append(item.getId())
                        .append("  ")
                        .append(item.getTitle())
                        .append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    static String mediaFiles(List<MediaFile> mediaFiles, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("id\tpath\tfile_size\tduration_millis\twidth\theight\tcontent_hash\tlast_modified_millis\n");
        }

        for (MediaFile mediaFile : mediaFiles) {
            builder.append(mediaFile(mediaFile, tsv));
        }

        return builder.toString();
    }

    static String mediaFile(MediaFile mediaFile, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append(escape(mediaFile.getId().toString()))
                    .append(TAB)
                    .append(escape(mediaFile.getPath().toString()))
                    .append(TAB)
                    .append(mediaFile.getFileSize())
                    .append(TAB)
                    .append(mediaFile.getDuration() == null
                            ? ""
                            : mediaFile.getDuration().toMillis())
                    .append(TAB)
                    .append(mediaFile.getWidth())
                    .append(TAB)
                    .append(mediaFile.getHeight())
                    .append(TAB)
                    .append(escape(mediaFile.getContentHash()))
                    .append(TAB)
                    .append(mediaFile.getLastModifiedMillis())
                    .append(System.lineSeparator());
        } else {
            builder.append(mediaFile.getId())
                    .append("  ")
                    .append(mediaFile.getPath())
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    static String scenes(List<Scene> scenes, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("id\ttitle\tpublisher_id\tperformer_ids\n");
        }

        for (Scene scene : scenes) {
            builder.append(scene(scene, tsv));
        }

        return builder.toString();
    }

    static String scene(Scene scene, boolean tsv) {
        final String performerIds = scene.getPerformers()
                .stream()
                .map(performer -> performer.getId().toString())
                .collect(java.util.stream.Collectors.joining(","));

        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append(escape(scene.getId().toString()))
                    .append(TAB)
                    .append(escape(scene.getTitle()))
                    .append(TAB)
                    .append(scene.getPublisher().getId())
                    .append(TAB)
                    .append(escape(performerIds))
                    .append(System.lineSeparator());
        } else {
            builder.append(scene.getId())
                    .append("  ")
                    .append(scene.getTitle())
                    .append("  publisher=")
                    .append(scene.getPublisher().getId())
                    .append("  performers=")
                    .append(performerIds)
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    static String sceneVerification(List<Scene> scenes, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("scene_id\tverification_status\ttitle\trelease_date\tpublisher_id\tpublisher_name\tseries_id\tseries_title\tmedia_count\n");
        }

        for (Scene scene : scenes) {
            builder.append(sceneVerificationRow(scene, tsv));
        }

        return builder.toString();
    }

    static String sceneVerificationRow(Scene scene, boolean tsv) {
        final String seriesId = scene.getSeries() == null
                ? ""
                : scene.getSeries().getId().toString();
        final String seriesTitle = scene.getSeries() == null
                ? ""
                : scene.getSeries().getTitle();
        final String releaseDate = scene.getReleaseDate() == null
                ? ""
                : scene.getReleaseDate().toString();
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append(escape(scene.getId().toString()))
                    .append(TAB)
                    .append(scene.getVerificationStatus().name())
                    .append(TAB)
                    .append(escape(scene.getTitle()))
                    .append(TAB)
                    .append(releaseDate)
                    .append(TAB)
                    .append(scene.getPublisher().getId())
                    .append(TAB)
                    .append(escape(scene.getPublisher().getName()))
                    .append(TAB)
                    .append(escape(seriesId))
                    .append(TAB)
                    .append(escape(seriesTitle))
                    .append(TAB)
                    .append(scene.getFiles().size())
                    .append(System.lineSeparator());
        } else {
            builder.append(scene.getId())
                    .append("  ")
                    .append(scene.getVerificationStatus())
                    .append("  ")
                    .append(scene.getTitle())
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    static String movies(List<Movie> movies, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("id\ttitle\tpublisher_id\tcompilation\tscene_ids\n");
        }

        for (Movie movie : movies) {
            builder.append(movie(movie, tsv));
        }

        return builder.toString();
    }

    static String movie(Movie movie, boolean tsv) {
        final String sceneIds = movie.getScenes()
                .stream()
                .map(scene -> scene.getId().toString())
                .collect(java.util.stream.Collectors.joining(","));
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append(escape(movie.getId().toString()))
                    .append(TAB)
                    .append(escape(movie.getTitle()))
                    .append(TAB)
                    .append(movie.getPublisher().getId())
                    .append(TAB)
                    .append(movie.isCompilation())
                    .append(TAB)
                    .append(escape(sceneIds))
                    .append(System.lineSeparator());
        } else {
            builder.append(movie.getId())
                    .append("  ")
                    .append(movie.getTitle())
                    .append("  scenes=")
                    .append(sceneIds)
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    static String mediaRenamePreview(MediaRenamePreview preview, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tmedia_id\tscene_id\toriginal_path\tproposed_path\tselected_movie_id\tselected_movie_title\tmovie_selection_source\tverification_status\twarnings\terror\n");
        }

        appendRenameRow(
                builder,
                preview.status().name(),
                preview.mediaId().toString(),
                preview.sceneId().toString(),
                preview.originalPath().toString(),
                preview.proposedPath().toString(),
                preview.selectedMovie()
                        .map(movie -> movie.getId().toString())
                        .orElse(""),
                preview.selectedMovie()
                        .map(Movie::getTitle)
                        .orElse(""),
                preview.movieSelectionStatus().name(),
                "",
                String.join(";", preview.warnings()),
                preview.error(),
                tsv
        );

        return builder.toString();
    }

    static String mediaRenameResults(
            List<MediaRenameResult> results,
            boolean tsv) {

        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tmedia_id\tscene_id\toriginal_path\tproposed_path\tselected_movie_id\tselected_movie_title\tmovie_selection_source\tverification_status\twarnings\terror\n");
        }

        for (MediaRenameResult result : results) {
            appendRenameRow(
                    builder,
                    result.status().name(),
                    result.mediaId().toString(),
                    result.sceneId().toString(),
                    result.originalPath().toString(),
                    result.finalPath().toString(),
                    "",
                    "",
                    "",
                    "",
                    String.join(";", result.warnings()),
                    result.error(),
                    tsv
            );
        }

        return builder.toString();
    }

    static String mediaScan(MediaScanResult result, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tmedia_id\tpath\tfile_size\tlast_modified_millis\twidth\theight\tduration_millis\tcontent_hash\tduplicate_media_id\tduplicate_path\terror\n");
        }

        for (MediaScanFileResult fileResult : result.files()) {
            if (tsv) {
                builder.append(fileResult.status())
                        .append(TAB)
                        .append(escape(fileResult.mediaId() == null
                                ? null
                                : fileResult.mediaId().toString()))
                        .append(TAB)
                        .append(escape(fileResult.path().toString()))
                        .append(TAB)
                        .append(fileResult.fileSize())
                        .append(TAB)
                        .append(fileResult.lastModifiedMillis())
                        .append(TAB)
                        .append(fileResult.width())
                        .append(TAB)
                        .append(fileResult.height())
                        .append(TAB)
                        .append(fileResult.duration() == null
                                ? ""
                                : fileResult.duration().toMillis())
                        .append(TAB)
                        .append(escape(fileResult.contentHash()))
                        .append(TAB)
                        .append(escape(fileResult.duplicateMediaId() == null
                                ? null
                                : fileResult.duplicateMediaId().toString()))
                        .append(TAB)
                        .append(escape(fileResult.duplicatePath() == null
                                ? null
                                : fileResult.duplicatePath().toString()))
                        .append(TAB)
                        .append(escape(fileResult.error()))
                        .append(System.lineSeparator());
            } else {
                builder.append(fileResult.status())
                        .append("  ")
                        .append(fileResult.path())
                        .append("  ")
                        .append(fileResult.width())
                        .append("x")
                        .append(fileResult.height())
                        .append("  duration=")
                        .append(fileResult.duration() == null
                                ? ""
                                : fileResult.duration().toMillis())
                        .append(System.lineSeparator());
            }
        }

        if (!tsv) {
            builder.append("Files discovered: ")
                    .append(result.summary().filesDiscovered())
                    .append(System.lineSeparator())
                    .append("Added: ")
                    .append(result.summary().added())
                    .append(System.lineSeparator())
                    .append("Updated: ")
                    .append(result.summary().updated())
                    .append(System.lineSeparator())
                    .append("Unchanged: ")
                    .append(result.summary().unchanged())
                    .append(System.lineSeparator())
                    .append("Failed: ")
                    .append(result.summary().failed())
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    static String mediaVerification(
            MediaVerificationResult result,
            boolean tsv) {

        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tmedia_id\tpath\tstored_file_size\tactual_file_size\tstored_last_modified_millis\tactual_last_modified_millis\terror\n");
        }

        for (MediaVerificationFileResult fileResult : result.files()) {
            if (tsv) {
                builder.append(fileResult.status())
                        .append(TAB)
                        .append(escape(fileResult.mediaId() == null
                                ? null
                                : fileResult.mediaId().toString()))
                        .append(TAB)
                        .append(escape(fileResult.path() == null
                                ? null
                                : fileResult.path().toString()))
                        .append(TAB)
                        .append(fileResult.storedFileSize())
                        .append(TAB)
                        .append(fileResult.actualFileSize())
                        .append(TAB)
                        .append(fileResult.storedLastModifiedMillis())
                        .append(TAB)
                        .append(fileResult.actualLastModifiedMillis())
                        .append(TAB)
                        .append(escape(fileResult.error()))
                        .append(System.lineSeparator());
            } else {
                builder.append(fileResult.status())
                        .append("  ")
                        .append(fileResult.path())
                        .append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    static String unassignedMediaFiles(
            List<MediaFile> mediaFiles,
            boolean tsv) {

        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("media_id\tpath\tfile_size\tlast_modified_millis\twidth\theight\tduration\tcontent_hash\n");
        }

        for (MediaFile mediaFile : mediaFiles) {
            if (tsv) {
                builder.append(escape(mediaFile.getId().toString()))
                        .append(TAB)
                        .append(escape(mediaFile.getPath().toString()))
                        .append(TAB)
                        .append(mediaFile.getFileSize())
                        .append(TAB)
                        .append(mediaFile.getLastModifiedMillis())
                        .append(TAB)
                        .append(mediaFile.getWidth())
                        .append(TAB)
                        .append(mediaFile.getHeight())
                        .append(TAB)
                        .append(mediaFile.getDuration() == null
                                ? ""
                                : mediaFile.getDuration().toMillis())
                        .append(TAB)
                        .append(escape(mediaFile.getContentHash()))
                        .append(System.lineSeparator());
            } else {
                builder.append(mediaFile.getId())
                        .append("  ")
                        .append(mediaFile.getPath())
                        .append("  ")
                        .append(mediaFile.getWidth())
                        .append("x")
                        .append(mediaFile.getHeight())
                        .append("  size=")
                        .append(mediaFile.getFileSize())
                        .append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    static String mediaAssignment(MediaAssignment assignment, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("media_id\tassignment_type\ttarget_id\ttarget_title\n");
        }

        if (assignment.scenes().isEmpty() && assignment.movies().isEmpty()) {
            appendAssignmentRow(
                    builder,
                    assignment.mediaFileId().toString(),
                    "UNASSIGNED",
                    "",
                    "",
                    tsv
            );
        }

        for (MediaAssignmentReference reference : assignment.scenes()) {
            appendAssignmentRow(
                    builder,
                    assignment.mediaFileId().toString(),
                    "SCENE",
                    reference.id().toString(),
                    reference.title(),
                    tsv
            );
        }

        for (MediaAssignmentReference reference : assignment.movies()) {
            appendAssignmentRow(
                    builder,
                    assignment.mediaFileId().toString(),
                    "MOVIE",
                    reference.id().toString(),
                    reference.title(),
                    tsv
            );
        }

        return builder.toString();
    }

    static String batchSceneCreation(
            BatchSceneCreationResult result,
            boolean tsv) {

        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("row\tstatus\tmedia_id\tpath\ttitle\tscene_id\terror\n");
        }

        for (BatchSceneCreationFileResult fileResult : result.files()) {
            if (tsv) {
                builder.append(fileResult.position())
                        .append(TAB)
                        .append(fileResult.status())
                        .append(TAB)
                        .append(escape(fileResult.mediaFileId() == null
                                ? null
                                : fileResult.mediaFileId().toString()))
                        .append(TAB)
                        .append(escape(fileResult.path() == null
                                ? null
                                : fileResult.path().toString()))
                        .append(TAB)
                        .append(escape(fileResult.title()))
                        .append(TAB)
                        .append(escape(fileResult.sceneId() == null
                                ? null
                                : fileResult.sceneId().toString()))
                        .append(TAB)
                        .append(escape(fileResult.error()))
                        .append(System.lineSeparator());
            } else {
                builder.append("row ")
                        .append(fileResult.position())
                        .append("  ")
                        .append(fileResult.status())
                        .append("  ")
                        .append(fileResult.mediaFileId())
                        .append("  ")
                        .append(fileResult.title())
                        .append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    static String filenamePreviews(List<FilenamePreview> previews, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tmedia_id\tpath\trelease_date\ttitle\tpublisher_id\tpublisher_name\tseries_id\tseries_title\tmovie_id\tmovie_title\tperformer_ids\tperformer_names\tunresolved_segments\talternative_count\twarnings\terror\n");
        }

        for (FilenamePreview preview : previews) {
            final service.FilenameInterpretation interpretation =
                    preview.matchResult().bestInterpretation();
            if (tsv) {
                builder.append(preview.matchResult().status())
                        .append(TAB)
                        .append(preview.mediaFileId())
                        .append(TAB)
                        .append(escape(preview.path().toString()))
                        .append(TAB)
                        .append(preview.parsedFilename().releaseDate() == null
                                ? ""
                                : preview.parsedFilename().releaseDate())
                        .append(TAB)
                        .append(escape(preview.parsedFilename()
                                .titleCandidate()))
                        .append(TAB)
                        .append(escape(entityId(interpretation == null
                                ? null
                                : interpretation.publisher())))
                        .append(TAB)
                        .append(escape(entityName(interpretation == null
                                ? null
                                : interpretation.publisher())))
                        .append(TAB)
                        .append(escape(entityId(interpretation == null
                                ? null
                                : interpretation.series())))
                        .append(TAB)
                        .append(escape(entityName(interpretation == null
                                ? null
                                : interpretation.series())))
                        .append(TAB)
                        .append(escape(entityId(interpretation == null
                                ? null
                                : interpretation.movie())))
                        .append(TAB)
                        .append(escape(entityName(interpretation == null
                                ? null
                                : interpretation.movie())))
                        .append(TAB)
                        .append(escape(performerIds(interpretation)))
                        .append(TAB)
                        .append(escape(performerNames(interpretation)))
                        .append(TAB)
                        .append(escape(String.join(
                                ";",
                                preview.parsedFilename().unresolvedSegments()
                        )))
                        .append(TAB)
                        .append(preview.matchResult().interpretations().size())
                        .append(TAB)
                        .append(escape(String.join(
                                ";",
                                preview.matchResult().warnings()
                        )))
                        .append(TAB)
                        .append(escape(String.join(
                                ";",
                                preview.matchResult().errors()
                        )))
                        .append(System.lineSeparator());
            } else {
                builder.append(preview.matchResult().status())
                        .append("  ")
                        .append(preview.mediaFileId())
                        .append("  ")
                        .append(preview.path())
                        .append("  ")
                        .append(preview.parsedFilename().titleCandidate())
                        .append(System.lineSeparator());
            }
        }

        return builder.toString();
    }

    static String autoIndex(AutoIndexResult result, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tmedia_id\tpath\tscene_id\ttitle\trelease_date\tpublisher_id\tpublisher_name\tseries_id\tseries_title\tmovie_id\tmovie_title\tmovie_scene_order\tperformer_ids\tperformer_names\tcode\tseason\tepisode\twarnings\terror\n");
        }

        for (AutoIndexFileResult fileResult : result.files()) {
            if (tsv) {
                builder.append(fileResult.status())
                        .append(TAB)
                        .append(escape(fileResult.mediaFileId() == null
                                ? null
                                : fileResult.mediaFileId().toString()))
                        .append(TAB)
                        .append(escape(fileResult.path() == null
                                ? null
                                : fileResult.path().toString()))
                        .append(TAB)
                        .append(escape(fileResult.sceneId() == null
                                ? null
                                : fileResult.sceneId().toString()))
                        .append(TAB)
                        .append(escape(fileResult.title()))
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(TAB)
                        .append(escape(fileResult.warnings()))
                        .append(TAB)
                        .append(escape(fileResult.error()))
                        .append(System.lineSeparator());
            } else {
                builder.append(fileResult.status())
                        .append("  ")
                        .append(fileResult.mediaFileId())
                        .append("  ")
                        .append(fileResult.title())
                        .append("  scene=")
                        .append(fileResult.sceneId() == null
                                ? ""
                                : fileResult.sceneId())
                        .append(System.lineSeparator());
            }
        }

        if (!tsv) {
            builder.append("Requested: ")
                    .append(result.summary().requested())
                    .append(System.lineSeparator())
                    .append("Created: ")
                    .append(result.summary().created())
                    .append(System.lineSeparator())
                    .append("Would create: ")
                    .append(result.summary().wouldCreate())
                    .append(System.lineSeparator())
                    .append("Review required: ")
                    .append(result.summary().reviewRequired())
                    .append(System.lineSeparator())
                    .append("Ambiguous: ")
                    .append(result.summary().ambiguous())
                    .append(System.lineSeparator())
                    .append("Unresolved: ")
                    .append(result.summary().unresolved())
                    .append(System.lineSeparator())
                    .append("Invalid: ")
                    .append(result.summary().invalid())
                    .append(System.lineSeparator())
                    .append("Already assigned: ")
                    .append(result.summary().alreadyAssigned())
                    .append(System.lineSeparator())
                    .append("Failed: ")
                    .append(result.summary().failed())
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    static String backup(BackupResult result, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tsource\tdestination\tfile_size\tschema_version\tverification_level\tverification_status\tcreated_at\terror\n");
            builder.append(result.status())
                    .append(TAB)
                    .append(escape(result.source().toString()))
                    .append(TAB)
                    .append(escape(result.destination().toString()))
                    .append(TAB)
                    .append(result.fileSize())
                    .append(TAB)
                    .append(escape(result.schemaVersion()))
                    .append(TAB)
                    .append(result.verification().level())
                    .append(TAB)
                    .append(result.verification().status())
                    .append(TAB)
                    .append(result.createdAt())
                    .append(TAB)
                    .append(escape(result.error()))
                    .append(System.lineSeparator());
        } else {
            builder.append("Status: ")
                    .append(result.status())
                    .append(System.lineSeparator())
                    .append("Source: ")
                    .append(result.source())
                    .append(System.lineSeparator())
                    .append("Destination: ")
                    .append(result.destination())
                    .append(System.lineSeparator())
                    .append("Size: ")
                    .append(result.fileSize())
                    .append(System.lineSeparator())
                    .append("Schema version: ")
                    .append(result.schemaVersion())
                    .append(System.lineSeparator())
                    .append("Verification: ")
                    .append(result.verification().status())
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    static String backupVerification(
            BackupVerificationResult result,
            boolean tsv) {

        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tpath\tfile_size\tschema_version\tverification_level\tintegrity_status\tforeign_key_status\tmissing_tables\tmessages\terror\n");
            builder.append(result.status())
                    .append(TAB)
                    .append(escape(result.path().toString()))
                    .append(TAB)
                    .append(result.fileSize())
                    .append(TAB)
                    .append(escape(result.schemaVersion()))
                    .append(TAB)
                    .append(result.level())
                    .append(TAB)
                    .append(result.integrityMessages().stream().allMatch(
                            "ok"::equalsIgnoreCase
                    ) ? "OK" : "FAILED")
                    .append(TAB)
                    .append(result.foreignKeyViolations().isEmpty()
                            ? "OK"
                            : "FAILED")
                    .append(TAB)
                    .append(escape(String.join(
                            ";",
                            result.missingTables()
                    )))
                    .append(TAB)
                    .append(escape(String.join(";", result.messages())))
                    .append(TAB)
                    .append(escape(result.error()))
                    .append(System.lineSeparator());
        } else {
            builder.append("Status: ")
                    .append(result.status())
                    .append(System.lineSeparator())
                    .append("Path: ")
                    .append(result.path())
                    .append(System.lineSeparator())
                    .append("Size: ")
                    .append(result.fileSize())
                    .append(System.lineSeparator())
                    .append("Schema version: ")
                    .append(result.schemaVersion())
                    .append(System.lineSeparator())
                    .append("Messages: ")
                    .append(String.join("; ", result.messages()))
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    static String restore(RestoreResult result, boolean tsv) {
        final StringBuilder builder = new StringBuilder();

        if (tsv) {
            builder.append("status\tinput\tdestination\tfile_size\tschema_version\tverification_level\tverification_status\terror\n");
            builder.append(result.status())
                    .append(TAB)
                    .append(escape(result.input().toString()))
                    .append(TAB)
                    .append(escape(result.destination().toString()))
                    .append(TAB)
                    .append(result.fileSize())
                    .append(TAB)
                    .append(escape(result.schemaVersion()))
                    .append(TAB)
                    .append(result.verification().level())
                    .append(TAB)
                    .append(result.verification().status())
                    .append(TAB)
                    .append(escape(result.error()))
                    .append(System.lineSeparator());
        } else {
            builder.append("Status: ")
                    .append(result.status())
                    .append(System.lineSeparator())
                    .append("Input: ")
                    .append(result.input())
                    .append(System.lineSeparator())
                    .append("Destination: ")
                    .append(result.destination())
                    .append(System.lineSeparator())
                    .append("Verification: ")
                    .append(result.verification().status())
                    .append(System.lineSeparator());
        }

        return builder.toString();
    }

    private static String entityId(service.EntityMatch match) {
        return match == null || match.id() == null ? "" : match.id().toString();
    }

    private static String entityName(service.EntityMatch match) {
        return match == null || match.name() == null ? "" : match.name();
    }

    private static String performerIds(service.FilenameInterpretation interpretation) {
        String value = "";

        if (interpretation != null) {
            value = interpretation.performers().stream()
                    .filter(match -> match.id() != null)
                    .map(match -> match.id().toString())
                    .collect(java.util.stream.Collectors.joining(";"));
        }

        return value;
    }

    private static String performerNames(service.FilenameInterpretation interpretation) {
        String value = "";

        if (interpretation != null) {
            value = interpretation.performers().stream()
                    .filter(match -> match.name() != null)
                    .map(service.EntityMatch::name)
                    .collect(java.util.stream.Collectors.joining(";"));
        }

        return value;
    }

    private static void appendRenameRow(
            StringBuilder builder,
            String status,
            String mediaId,
            String sceneId,
            String originalPath,
            String proposedPath,
            String selectedMovieId,
            String selectedMovieTitle,
            String movieSelectionSource,
            String verificationStatus,
            String warnings,
            String error,
            boolean tsv) {

        if (tsv) {
            builder.append(escape(status))
                    .append(TAB)
                    .append(escape(mediaId))
                    .append(TAB)
                    .append(escape(sceneId))
                    .append(TAB)
                    .append(escape(originalPath))
                    .append(TAB)
                    .append(escape(proposedPath))
                    .append(TAB)
                    .append(escape(selectedMovieId))
                    .append(TAB)
                    .append(escape(selectedMovieTitle))
                    .append(TAB)
                    .append(escape(movieSelectionSource))
                    .append(TAB)
                    .append(escape(verificationStatus))
                    .append(TAB)
                    .append(escape(warnings))
                    .append(TAB)
                    .append(escape(error))
                    .append(System.lineSeparator());
        } else {
            builder.append(status)
                    .append("  media=")
                    .append(mediaId)
                    .append("  scene=")
                    .append(sceneId)
                    .append("  ")
                    .append(originalPath)
                    .append(" -> ")
                    .append(proposedPath);

            if (!error.isBlank()) {
                builder.append("  error=")
                        .append(error);
            }

            builder.append(System.lineSeparator());
        }
    }

    private static void appendAssignmentRow(
            StringBuilder builder,
            String mediaId,
            String assignmentType,
            String targetId,
            String targetTitle,
            boolean tsv) {

        if (tsv) {
            builder.append(escape(mediaId))
                    .append(TAB)
                    .append(assignmentType)
                    .append(TAB)
                    .append(escape(targetId))
                    .append(TAB)
                    .append(escape(targetTitle))
                    .append(System.lineSeparator());
        } else {
            builder.append(mediaId)
                    .append("  ")
                    .append(assignmentType)
                    .append("  ")
                    .append(targetId)
                    .append("  ")
                    .append(targetTitle)
                    .append(System.lineSeparator());
        }
    }

    static String escape(String value) {
        String escaped = "";

        if (value != null) {
            escaped = value
                    .replace("\t", TAB_ESCAPE)
                    .replace("\n", NEWLINE_ESCAPE)
                    .replace("\r", NEWLINE_ESCAPE);
        }

        return escaped;
    }
}

package service;

import media.MediaHashProvider;
import media.MediaMetadata;
import media.MediaMetadataProbe;
import media.MediaProbeException;
import media.VideoFileDiscovery;
import model.MediaFile;
import repository.MediaFileRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MediaScanService {
    private final MediaFileRepository mediaFileRepository;
    private final VideoFileDiscovery videoFileDiscovery;
    private final MediaMetadataProbe mediaMetadataProbe;
    private final MediaHashProvider mediaHashProvider;

    public MediaScanService(
            MediaFileRepository mediaFileRepository,
            VideoFileDiscovery videoFileDiscovery,
            MediaMetadataProbe mediaMetadataProbe,
            MediaHashProvider mediaHashProvider) {

        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        this.videoFileDiscovery = Objects.requireNonNull(
                videoFileDiscovery,
                "Video file discovery must not be null"
        );
        this.mediaMetadataProbe = Objects.requireNonNull(
                mediaMetadataProbe,
                "Media metadata probe must not be null"
        );
        this.mediaHashProvider = Objects.requireNonNull(
                mediaHashProvider,
                "Media hash provider must not be null"
        );
    }

    public MediaScanResult scan(MediaScanRequest request) throws IOException {
        return scan(request, Set.of());
    }

    public MediaScanResult scan(
            MediaScanRequest request,
            Set<Path> skippedPaths) throws IOException {

        return scan(
                request,
                skippedPaths,
                null,
                new ScanCancellationToken()
        );
    }

    public MediaScanResult scan(
            MediaScanRequest request,
            Set<Path> skippedPaths,
            MediaScanProgressListener progressListener,
            ScanCancellationToken cancellationToken) throws IOException {

        Objects.requireNonNull(request, "Media scan request must not be null");
        final Set<Path> effectiveSkippedPaths = skippedPaths == null
                ? Set.of()
                : skippedPaths;
        final ScanCancellationToken effectiveCancellationToken =
                cancellationToken == null
                        ? new ScanCancellationToken()
                        : cancellationToken;

        final Set<String> additionalExtensions =
                request.additionalExtensions() == null
                        ? Set.of()
                        : request.additionalExtensions();
        publishProgress(
                progressListener,
                MediaLocationScanPhase.DISCOVERING,
                null,
                List.of(),
                0
        );
        final List<Path> discoveredPaths = videoFileDiscovery.discover(
                request.rootDirectory(),
                additionalExtensions,
                request.recursive()
        ).stream()
                .filter(path -> !effectiveSkippedPaths.contains(path))
                .toList();
        publishProgress(
                progressListener,
                MediaLocationScanPhase.DISCOVERY_COMPLETED,
                null,
                List.of(),
                discoveredPaths.size()
        );
        final List<MediaScanFileResult> fileResults = new ArrayList<>();
        boolean shouldKeepScanning = true;
        int pathIndex = 0;

        while (pathIndex < discoveredPaths.size() && shouldKeepScanning) {
            final Path discoveredPath = discoveredPaths.get(pathIndex);
            if (effectiveCancellationToken.cancellationRequested()) {
                shouldKeepScanning = false;
            } else {
                publishProgress(
                        progressListener,
                        MediaLocationScanPhase.PROCESSING_FILE,
                        discoveredPath,
                        fileResults,
                        discoveredPaths.size()
                );
                final MediaScanFileResult fileResult = scanFile(
                        discoveredPath,
                        request.hashingEnabled(),
                        request.dryRun(),
                        effectiveCancellationToken
                );
                fileResults.add(fileResult);
                publishProgress(
                        progressListener,
                        MediaLocationScanPhase.FILE_COMPLETED,
                        discoveredPath,
                        fileResults,
                        discoveredPaths.size()
                );

                if (request.failFast()
                        && fileResult.status() == MediaScanStatus.FAILED) {
                    shouldKeepScanning = false;
                }
            }

            pathIndex++;
        }

        if (effectiveCancellationToken.cancellationRequested()) {
            publishProgress(
                    progressListener,
                    MediaLocationScanPhase.CANCELLED,
                    null,
                    fileResults,
                    discoveredPaths.size()
            );
        } else {
            publishProgress(
                    progressListener,
                    MediaLocationScanPhase.COMPLETED,
                    null,
                    fileResults,
                    discoveredPaths.size()
            );
        }

        return new MediaScanResult(
                fileResults,
                summarize(fileResults, discoveredPaths.size())
        );
    }

    public MediaScanService withProbe(MediaMetadataProbe probe) {
        return new MediaScanService(
                mediaFileRepository,
                videoFileDiscovery,
                probe,
                mediaHashProvider
        );
    }

    public MediaVerificationResult verify(MediaVerificationRequest request) {
        Objects.requireNonNull(
                request,
                "Media verification request must not be null"
        );

        final List<MediaVerificationFileResult> fileResults =
                new ArrayList<>();
        boolean shouldKeepVerifying = true;

        try {
            final List<MediaFile> mediaFiles = mediaFileRepository.findAll();
            int mediaFileIndex = 0;

            while (mediaFileIndex < mediaFiles.size()
                    && shouldKeepVerifying) {
                final MediaVerificationFileResult fileResult =
                        verifyFile(mediaFiles.get(mediaFileIndex), request);
                fileResults.add(fileResult);

                if (request.failFast()
                        && fileResult.status()
                        == MediaVerificationStatus.REFRESH_FAILED) {
                    shouldKeepVerifying = false;
                }

                mediaFileIndex++;
            }
        } catch (SQLException exception) {
            fileResults.add(new MediaVerificationFileResult(
                    MediaVerificationStatus.REFRESH_FAILED,
                    null,
                    null,
                    0L,
                    0L,
                    0L,
                    0L,
                    exception.getMessage()
            ));
        }

        return new MediaVerificationResult(List.copyOf(fileResults));
    }

    private MediaVerificationFileResult verifyFile(
            MediaFile mediaFile,
            MediaVerificationRequest request) {

        MediaVerificationFileResult result;
        final Path path = mediaFile.getPath().toAbsolutePath().normalize();

        if (!Files.exists(path)) {
            result = new MediaVerificationFileResult(
                    MediaVerificationStatus.MISSING,
                    mediaFile.getId(),
                    path,
                    mediaFile.getFileSize(),
                    0L,
                    mediaFile.getLastModifiedMillis(),
                    0L,
                    null
            );
        } else {
            result = verifyExistingFile(mediaFile, path, request);
        }

        return result;
    }

    private MediaVerificationFileResult verifyExistingFile(
            MediaFile mediaFile,
            Path path,
            MediaVerificationRequest request) {

        MediaVerificationFileResult result;

        try {
            final long actualFileSize = Files.size(path);
            final long actualLastModifiedMillis =
                    Files.getLastModifiedTime(path).toMillis();
            final boolean changed = mediaFile.getFileSize() != actualFileSize
                    || mediaFile.getLastModifiedMillis()
                    != actualLastModifiedMillis;

            if (!changed) {
                result = new MediaVerificationFileResult(
                        MediaVerificationStatus.PRESENT,
                        mediaFile.getId(),
                        path,
                        mediaFile.getFileSize(),
                        actualFileSize,
                        mediaFile.getLastModifiedMillis(),
                        actualLastModifiedMillis,
                        null
                );
            } else if (!request.refreshChangedFiles()) {
                result = new MediaVerificationFileResult(
                        MediaVerificationStatus.CHANGED,
                        mediaFile.getId(),
                        path,
                        mediaFile.getFileSize(),
                        actualFileSize,
                        mediaFile.getLastModifiedMillis(),
                        actualLastModifiedMillis,
                        null
                );
            } else {
                result = refreshChangedFile(
                        mediaFile,
                        path,
                        actualFileSize,
                        actualLastModifiedMillis,
                        request.dryRun()
                );
            }
        } catch (IOException | SQLException | MediaProbeException exception) {
            result = new MediaVerificationFileResult(
                    MediaVerificationStatus.REFRESH_FAILED,
                    mediaFile.getId(),
                    path,
                    mediaFile.getFileSize(),
                    0L,
                    mediaFile.getLastModifiedMillis(),
                    0L,
                    exception.getMessage()
            );
        }

        return result;
    }

    private MediaVerificationFileResult refreshChangedFile(
            MediaFile mediaFile,
            Path path,
            long actualFileSize,
            long actualLastModifiedMillis,
            boolean dryRun)
            throws IOException, SQLException, MediaProbeException {

        final MediaVerificationStatus status;

        if (dryRun) {
            status = MediaVerificationStatus.WOULD_REFRESH;
        } else {
            final MediaMetadata metadata = mediaMetadataProbe.probe(path);
            mediaFileRepository.update(new MediaFile(
                    mediaFile.getId(),
                    path,
                    actualFileSize,
                    mediaFile.getContentHash(),
                    metadata.duration(),
                    metadata.width(),
                    metadata.height(),
                    actualLastModifiedMillis
            ));
            status = MediaVerificationStatus.REFRESHED;
        }

        return new MediaVerificationFileResult(
                status,
                mediaFile.getId(),
                path,
                mediaFile.getFileSize(),
                actualFileSize,
                mediaFile.getLastModifiedMillis(),
                actualLastModifiedMillis,
                null
        );
    }

    private MediaScanFileResult scanFile(
            Path path,
            boolean hashingEnabled,
            boolean dryRun,
            ScanCancellationToken cancellationToken) {

        MediaScanFileResult result;

        try {
            final Path normalizedPath = path.toAbsolutePath().normalize();
            final long fileSize = Files.size(normalizedPath);
            final long lastModifiedMillis =
                    Files.getLastModifiedTime(normalizedPath).toMillis();
            final Optional<MediaFile> existingMediaFile =
                    mediaFileRepository.findByPath(normalizedPath);

            if (isUnchanged(existingMediaFile, fileSize, lastModifiedMillis)) {
                final MediaFile mediaFile = existingMediaFile.orElseThrow();
                result = new MediaScanFileResult(
                        MediaScanStatus.UNCHANGED,
                        normalizedPath,
                        mediaFile.getId(),
                        fileSize,
                        lastModifiedMillis,
                        mediaFile.getWidth(),
                        mediaFile.getHeight(),
                        mediaFile.getDuration(),
                        mediaFile.getContentHash(),
                        null,
                        null,
                        null
                );
            } else {
                result = scanChangedFile(
                        normalizedPath,
                        fileSize,
                        lastModifiedMillis,
                        existingMediaFile,
                        hashingEnabled,
                        dryRun,
                        cancellationToken
                );
            }
        } catch (IOException | SQLException | MediaProbeException
                 | IllegalArgumentException exception) {
            result = new MediaScanFileResult(
                    MediaScanStatus.FAILED,
                    path.toAbsolutePath().normalize(),
                    null,
                    0L,
                    0L,
                    0,
                    0,
                    null,
                    null,
                    null,
                    null,
                    exception.getMessage()
            );
        }

        return result;
    }

    private MediaScanFileResult scanChangedFile(
            Path path,
            long fileSize,
            long lastModifiedMillis,
            Optional<MediaFile> existingMediaFile,
            boolean hashingEnabled,
            boolean dryRun,
            ScanCancellationToken cancellationToken)
            throws IOException, SQLException, MediaProbeException {

        final MediaMetadata metadata = mediaMetadataProbe.probe(path);
        if (cancellationToken.cancellationRequested()) {
            throw new MediaProbeException("Scan was cancelled.");
        }
        final String contentHash =
                hashingEnabled ? mediaHashProvider.hash(path) : null;
        if (cancellationToken.cancellationRequested()) {
            throw new MediaProbeException("Scan was cancelled.");
        }
        final Optional<MediaFile> duplicateMediaFile = findDuplicate(
                contentHash,
                existingMediaFile.map(MediaFile::getId)
        );
        final boolean duplicateContent = duplicateMediaFile.isPresent();
        final boolean update = existingMediaFile.isPresent();
        final UUID mediaId = existingMediaFile
                .map(MediaFile::getId)
                .orElseGet(UUID::randomUUID);
        final MediaScanStatus status = statusFor(
                update,
                dryRun,
                duplicateContent
        );

        if (!dryRun) {
            final MediaFile mediaFile = new MediaFile(
                    mediaId,
                    path,
                    fileSize,
                    contentHash,
                    metadata.duration(),
                    metadata.width(),
                    metadata.height(),
                    lastModifiedMillis
            );

            if (update) {
                mediaFileRepository.update(mediaFile);
            } else {
                mediaFileRepository.insert(mediaFile);
            }
        }

        return new MediaScanFileResult(
                status,
                path,
                mediaId,
                fileSize,
                lastModifiedMillis,
                metadata.width(),
                metadata.height(),
                metadata.duration(),
                contentHash,
                duplicateMediaFile.map(MediaFile::getId).orElse(null),
                duplicateMediaFile.map(MediaFile::getPath).orElse(null),
                null
        );
    }

    private Optional<MediaFile> findDuplicate(
            String contentHash,
            Optional<UUID> existingId) throws SQLException {

        Optional<MediaFile> duplicate = Optional.empty();

        if (contentHash != null) {
            final List<MediaFile> matchingMediaFiles =
                    mediaFileRepository.findByContentHash(contentHash);
            int mediaFileIndex = 0;

            while (mediaFileIndex < matchingMediaFiles.size()
                    && duplicate.isEmpty()) {
                final MediaFile mediaFile = matchingMediaFiles.get(
                        mediaFileIndex
                );

                if (existingId.isEmpty()
                        || !existingId.orElseThrow().equals(
                                mediaFile.getId()
                        )) {
                    duplicate = Optional.of(mediaFile);
                }

                mediaFileIndex++;
            }
        }

        return duplicate;
    }

    private boolean isUnchanged(
            Optional<MediaFile> existingMediaFile,
            long fileSize,
            long lastModifiedMillis) {

        boolean unchanged = false;

        if (existingMediaFile.isPresent()) {
            final MediaFile mediaFile = existingMediaFile.orElseThrow();
            unchanged = mediaFile.getFileSize() == fileSize
                    && mediaFile.getLastModifiedMillis()
                    == lastModifiedMillis;
        }

        return unchanged;
    }

    private MediaScanStatus statusFor(
            boolean update,
            boolean dryRun,
            boolean duplicateContent) {

        MediaScanStatus status;

        if (update && dryRun && duplicateContent) {
            status = MediaScanStatus.WOULD_UPDATE_DUPLICATE_CONTENT;
        } else if (update && dryRun) {
            status = MediaScanStatus.WOULD_UPDATE;
        } else if (update && duplicateContent) {
            status = MediaScanStatus.UPDATED_DUPLICATE_CONTENT;
        } else if (update) {
            status = MediaScanStatus.UPDATED;
        } else if (dryRun && duplicateContent) {
            status = MediaScanStatus.WOULD_ADD_DUPLICATE_CONTENT;
        } else if (dryRun) {
            status = MediaScanStatus.WOULD_ADD;
        } else if (duplicateContent) {
            status = MediaScanStatus.ADDED_DUPLICATE_CONTENT;
        } else {
            status = MediaScanStatus.ADDED;
        }

        return status;
    }

    private MediaScanSummary summarize(List<MediaScanFileResult> fileResults) {
        return summarize(fileResults, fileResults.size());
    }

    private MediaScanSummary summarize(
            List<MediaScanFileResult> fileResults,
            int discoveredCount) {

        int added = 0;
        int updated = 0;
        int unchanged = 0;
        int duplicateContentFiles = 0;
        int failed = 0;
        int dryRunAdditions = 0;
        int dryRunUpdates = 0;

        for (MediaScanFileResult fileResult : fileResults) {
            final MediaScanStatus status = fileResult.status();

            if (status == MediaScanStatus.ADDED
                    || status == MediaScanStatus.ADDED_DUPLICATE_CONTENT) {
                added++;
            }

            if (status == MediaScanStatus.UPDATED
                    || status == MediaScanStatus.UPDATED_DUPLICATE_CONTENT) {
                updated++;
            }

            if (status == MediaScanStatus.UNCHANGED) {
                unchanged++;
            }

            if (status == MediaScanStatus.ADDED_DUPLICATE_CONTENT
                    || status == MediaScanStatus.UPDATED_DUPLICATE_CONTENT
                    || status == MediaScanStatus.WOULD_ADD_DUPLICATE_CONTENT
                    || status
                    == MediaScanStatus.WOULD_UPDATE_DUPLICATE_CONTENT) {
                duplicateContentFiles++;
            }

            if (status == MediaScanStatus.FAILED) {
                failed++;
            }

            if (status == MediaScanStatus.WOULD_ADD
                    || status == MediaScanStatus.WOULD_ADD_DUPLICATE_CONTENT) {
                dryRunAdditions++;
            }

            if (status == MediaScanStatus.WOULD_UPDATE
                    || status
                    == MediaScanStatus.WOULD_UPDATE_DUPLICATE_CONTENT) {
                dryRunUpdates++;
            }
        }

        return new MediaScanSummary(
                discoveredCount,
                added,
                updated,
                unchanged,
                duplicateContentFiles,
                failed,
                dryRunAdditions,
                dryRunUpdates
        );
    }

    private void publishProgress(
            MediaScanProgressListener listener,
            MediaLocationScanPhase phase,
            Path currentFile,
            List<MediaScanFileResult> fileResults,
            int discoveredCount) {

        if (listener != null) {
            listener.onProgress(new MediaScanProgress(
                    phase,
                    currentFile == null
                            ? null
                            : currentFile.toAbsolutePath().normalize(),
                    summarize(fileResults, discoveredCount)
            ));
        }
    }
}

package service;

import model.MediaFile;
import repository.MediaFileRepository;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Objects;

final class RepositoryMediaPathUpdater implements MediaPathUpdater {
    private final MediaFileRepository mediaFileRepository;

    RepositoryMediaPathUpdater(MediaFileRepository mediaFileRepository) {
        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
    }

    @Override
    public void updatePath(MediaFile mediaFile, Path newPath)
            throws SQLException {

        mediaFileRepository.update(new MediaFile(
                mediaFile.getId(),
                newPath,
                mediaFile.getFileSize(),
                mediaFile.getContentHash(),
                mediaFile.getDuration(),
                mediaFile.getWidth(),
                mediaFile.getHeight(),
                mediaFile.getLastModifiedMillis()
        ));
    }
}

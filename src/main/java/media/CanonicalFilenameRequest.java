package media;

import model.MediaFile;
import model.Scene;
import service.MovieSelectionResult;

import java.util.Objects;

public record CanonicalFilenameRequest(
        Scene scene,
        MediaFile mediaFile,
        MovieSelectionResult movieSelection) {

    public CanonicalFilenameRequest {
        Objects.requireNonNull(scene, "Scene must not be null");
        Objects.requireNonNull(mediaFile, "Media file must not be null");
        Objects.requireNonNull(
                movieSelection,
                "Movie selection must not be null"
        );
    }
}

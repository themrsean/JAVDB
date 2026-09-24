package media;

import java.io.IOException;
import java.nio.file.Path;

public interface MediaMetadataProbe {
    MediaMetadata probe(Path mediaPath)
            throws IOException, MediaProbeException;
}

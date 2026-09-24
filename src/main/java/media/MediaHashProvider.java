package media;

import java.io.IOException;
import java.nio.file.Path;

public interface MediaHashProvider {
    String hash(Path path) throws IOException;
}

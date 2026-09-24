package media;

import java.io.IOException;
import java.util.List;

public interface MediaProcessRunner {
    ProcessResult run(List<String> command)
            throws IOException, InterruptedException;
}

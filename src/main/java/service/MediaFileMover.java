package service;

import java.io.IOException;
import java.nio.file.Path;

interface MediaFileMover {
    void move(Path source, Path destination) throws IOException;
}

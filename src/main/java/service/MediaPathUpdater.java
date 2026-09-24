package service;

import model.MediaFile;

import java.nio.file.Path;
import java.sql.SQLException;

interface MediaPathUpdater {
    void updatePath(MediaFile mediaFile, Path newPath) throws SQLException;
}

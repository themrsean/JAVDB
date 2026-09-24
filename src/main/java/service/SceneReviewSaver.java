package service;

import java.io.IOException;
import java.sql.SQLException;

public interface SceneReviewSaver {
    SceneReviewSaveResult save(SceneReviewSaveRequest request)
            throws IOException, SQLException;
}

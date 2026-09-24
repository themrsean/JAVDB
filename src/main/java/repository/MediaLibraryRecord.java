package repository;

import model.MediaFile;

import java.util.Objects;

public record MediaLibraryRecord(
        MediaFile mediaFile,
        boolean sceneAssigned,
        boolean movieAssigned) {

    public MediaLibraryRecord {
        Objects.requireNonNull(mediaFile, "Media file must not be null");
    }

    public MediaLibraryAssignmentState assignmentState() {
        if (sceneAssigned && movieAssigned) {
            return MediaLibraryAssignmentState.SCENE_AND_MOVIE;
        }
        if (sceneAssigned) {
            return MediaLibraryAssignmentState.SCENE;
        }
        if (movieAssigned) {
            return MediaLibraryAssignmentState.MOVIE;
        }
        return MediaLibraryAssignmentState.UNASSIGNED;
    }
}

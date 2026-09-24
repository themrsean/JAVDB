package repository;

public enum MediaLibraryAssignmentState {
    ALL("All"),
    UNASSIGNED("Unassigned"),
    SCENE("Scene"),
    MOVIE("Movie"),
    SCENE_AND_MOVIE("Scene + Movie");

    private final String displayName;

    MediaLibraryAssignmentState(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

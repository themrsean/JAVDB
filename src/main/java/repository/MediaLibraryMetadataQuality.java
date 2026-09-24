package repository;

public enum MediaLibraryMetadataQuality {
    ALL("All"),
    MISSING_DIMENSIONS("Missing dimensions"),
    MISSING_DURATION("Missing duration");

    private final String displayName;

    MediaLibraryMetadataQuality(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

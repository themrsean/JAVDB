package service;

public enum ContextCandidateRole {
    PUBLISHER("Publisher"), SERIES("Series"), MOVIE("Movie");
    private final String displayName;
    ContextCandidateRole(String displayName) { this.displayName = displayName; }
    public String displayName() { return displayName; }
}

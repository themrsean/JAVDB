package service;

public enum ContextCandidateStatus {
    UNRESOLVED("Unresolved"),
    PUBLISHER_MATCH("Exact publisher match"),
    SERIES_MATCH("Exact series match"),
    MOVIE_MATCH("Exact movie match"),
    MULTIPLE_ROLE_MATCHES("Multiple exact matches");

    private final String displayName;

    ContextCandidateStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean needsAttention() {
        return this == UNRESOLVED || this == MULTIPLE_ROLE_MATCHES;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

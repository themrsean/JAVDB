package service;

public enum PerformerCandidateResolution {
    PRIMARY_MATCH("Exact primary-name match"),
    ALIAS_MATCH("Exact alias match"),
    UNRESOLVED("Unresolved");

    private final String display;
    PerformerCandidateResolution(String display) { this.display = display; }
    @Override public String toString() { return display; }
}

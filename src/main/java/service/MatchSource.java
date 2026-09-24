package service;

public enum MatchSource {
    EXPLICIT_PRIMARY_NAME,
    EXPLICIT_ALIAS,
    INFERRED_FROM_SERIES,
    INFERRED_FROM_MOVIE,
    ABSENT,
    UNMATCHED
}

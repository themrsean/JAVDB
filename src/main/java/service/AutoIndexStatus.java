package service;

public enum AutoIndexStatus {
    CREATED_VERIFY,
    WOULD_CREATE,
    REVIEW_REQUIRED,
    AMBIGUOUS,
    UNRESOLVED,
    INVALID_FILENAME,
    ALREADY_ASSIGNED,
    FAILED
}

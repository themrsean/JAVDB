package service;

/** Signals that a context candidate changed after the review list was loaded. */
public final class ContextCandidateResolvedException
        extends IllegalArgumentException {
    private final ContextCandidateStatus status;

    public ContextCandidateResolvedException(ContextCandidateStatus status) {
        super("Candidate is no longer unresolved (" + status
                + "); no publisher was created or changed.");
        this.status = status;
    }

    public ContextCandidateStatus status() {
        return status;
    }
}

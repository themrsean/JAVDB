package service;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ScanCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean cancellationRequested() {
        return cancelled.get();
    }
}

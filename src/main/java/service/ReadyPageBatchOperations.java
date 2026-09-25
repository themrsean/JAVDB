package service;

public interface ReadyPageBatchOperations {
    ReadyPageBatchPreflight preflight(ReadyPageBatchPageSnapshot snapshot);

    ReadyPageBatchResult execute(
            ReadyPageBatchPreflight preflight,
            ReadyPageBatchMode mode);
}

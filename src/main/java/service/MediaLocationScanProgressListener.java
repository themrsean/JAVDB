package service;

@FunctionalInterface
public interface MediaLocationScanProgressListener {
    void onProgress(MediaLocationScanProgress progress);
}

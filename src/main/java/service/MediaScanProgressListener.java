package service;

@FunctionalInterface
public interface MediaScanProgressListener {
    void onProgress(MediaScanProgress progress);
}

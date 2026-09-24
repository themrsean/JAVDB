/*
 * Course: CSC-1120
 * ASSIGNMENT
 * CLASS
 * Name: Sean Jones
 * Last Updated:
 */
package model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public class MediaFile {
    private UUID id;
    private Path path;
    private long fileSize;
    private String contentHash;
    private Duration duration;
    private int width;
    private int height;
    private long lastModifiedMillis;

    public MediaFile(
            UUID id,
            Path path,
            long fileSize,
            String contentHash,
            Duration duration,
            int width,
            int height,
            long lastModifiedMillis) {

        setId(id);
        setPath(path);
        setFileSize(fileSize);
        setContentHash(contentHash);
        setDuration(duration);
        setWidth(width);
        setHeight(height);
        setLastModifiedMillis(lastModifiedMillis);
    }

    public MediaFile(
            UUID id,
            Path path,
            long fileSize,
            String contentHash,
            Duration duration,
            int width,
            int height) {

        this(
                id,
                path,
                fileSize,
                contentHash,
                duration,
                width,
                height,
                0L
        );
    }

    public MediaFile(Path path, long fileSize, String contentHash, Duration duration, int width,
                     int height) {
        this(UUID.randomUUID(), path, fileSize, contentHash, duration, width, height);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = Objects.requireNonNull(
                id,
                "Media file ID must not be null"
        );
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = Objects.requireNonNull(
                path,
                "Media file path must not be null"
        );
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getLastModifiedMillis() {
        return lastModifiedMillis;
    }

    public void setLastModifiedMillis(long lastModifiedMillis) {
        this.lastModifiedMillis = lastModifiedMillis;
    }
}

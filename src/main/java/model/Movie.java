/*
 * Course: CSC-1120
 * ASSIGNMENT
 * CLASS
 * Name: Sean Jones
 * Last Updated:
 */
package model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Movie {
    private UUID id;
    private String title;
    private LocalDate releaseDate;
    private Publisher publisher;
    private List<Scene> scenes;
    private boolean compilation;
    private List<MediaFile> files;

    public Movie() {
        scenes = List.of();
        files = List.of();
    }

    public Movie(
            UUID id,
            String title,
            LocalDate releaseDate,
            Publisher publisher,
            List<Scene> scenes,
            boolean compilation,
            List<MediaFile> files) {

        setId(id);
        setTitle(title);
        setReleaseDate(releaseDate);
        setPublisher(publisher);
        setScenes(scenes);
        setCompilation(compilation);
        setFiles(files);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = Objects.requireNonNull(
                id,
                "Movie ID must not be null"
        );
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(
                title,
                "Movie title must not be null"
        );
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = Objects.requireNonNull(
                publisher,
                "Movie publisher must not be null"
        );
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    public void setScenes(List<Scene> scenes) {
        this.scenes = List.copyOf(Objects.requireNonNull(
                scenes,
                "Movie scenes must not be null"
        ));
    }

    public boolean isCompilation() {
        return compilation;
    }

    public void setCompilation(boolean compilation) {
        this.compilation = compilation;
    }

    public List<MediaFile> getFiles() {
        return files;
    }

    public void setFiles(List<MediaFile> files) {
        this.files = List.copyOf(Objects.requireNonNull(
                files,
                "Movie files must not be null"
        ));
    }
}

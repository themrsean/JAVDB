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

public class Scene {
    private UUID id;
    private String title;
    private Publisher publisher;
    private LocalDate releaseDate;
    private String code;
    private Series series;
    private String season;
    private String episode;
    private List<Performer> performers;
    private List<MediaFile> files;
    private VerificationStatus verificationStatus;

    public Scene() {
        performers = List.of();
        files = List.of();
        verificationStatus = VerificationStatus.UNVERIFIED;
    }

    public Scene(
            UUID id,
            String title,
            Publisher publisher,
            LocalDate releaseDate,
            String code,
            Series series,
            String season,
            String episode,
            List<Performer> performers,
            List<MediaFile> files) {

        this(
                id,
                title,
                publisher,
                releaseDate,
                code,
                series,
                season,
                episode,
                performers,
                files,
                VerificationStatus.UNVERIFIED
        );
    }

    public Scene(
            UUID id,
            String title,
            Publisher publisher,
            LocalDate releaseDate,
            String code,
            Series series,
            String season,
            String episode,
            List<Performer> performers,
            List<MediaFile> files,
            VerificationStatus verificationStatus) {

        setId(id);
        setTitle(title);
        setPublisher(publisher);
        setReleaseDate(releaseDate);
        setCode(code);
        setSeries(series);
        setSeason(season);
        setEpisode(episode);
        setPerformers(performers);
        setFiles(files);
        setVerificationStatus(verificationStatus);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = Objects.requireNonNull(
                id,
                "Scene ID must not be null"
        );
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(
                title,
                "Scene title must not be null"
        );
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = Objects.requireNonNull(
                publisher,
                "Scene publisher must not be null"
        );
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Series getSeries() {
        return series;
    }

    public void setSeries(Series series) {
        this.series = series;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public String getEpisode() {
        return episode;
    }

    public void setEpisode(String episode) {
        this.episode = episode;
    }

    public List<Performer> getPerformers() {
        return performers;
    }

    public void setPerformers(List<Performer> performers) {
        this.performers = List.copyOf(Objects.requireNonNull(
                performers,
                "Scene performers must not be null"
        ));
    }

    public List<MediaFile> getFiles() {
        return files;
    }

    public void setFiles(List<MediaFile> files) {
        this.files = List.copyOf(Objects.requireNonNull(
                files,
                "Scene files must not be null"
        ));
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = Objects.requireNonNull(
                verificationStatus,
                "Scene verification status must not be null"
        );
    }
}

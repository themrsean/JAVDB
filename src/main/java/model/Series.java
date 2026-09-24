/*
 * Course: CSC-1120
 * ASSIGNMENT
 * CLASS
 * Name: Sean Jones
 * Last Updated:
 */
package model;

import java.util.Objects;
import java.util.UUID;

public class Series {
    private UUID id;
    private String title;
    private Publisher publisher;

    public Series() {
    }

    public Series(UUID id, String title, Publisher publisher) {
        setId(id);
        setTitle(title);
        setPublisher(publisher);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = Objects.requireNonNull(
                id,
                "Series ID must not be null"
        );
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Objects.requireNonNull(
                title,
                "Series title must not be null"
        );
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public void setPublisher(Publisher publisher) {
        this.publisher = Objects.requireNonNull(
                publisher,
                "Series publisher must not be null"
        );
    }
}

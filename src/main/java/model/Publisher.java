/*
 * Course: CSC-1120
 * ASSIGNMENT
 * CLASS
 * Name: Sean Jones
 * Last Updated:
 */
package model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Publisher {
    private UUID id;
    private String name;
    private List<String> aliases;

    public Publisher() {
        aliases = List.of();
    }

    public Publisher(
            UUID id,
            String name,
            List<String> aliases) {

        setId(id);
        setName(name);
        setAliases(aliases);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = Objects.requireNonNull(
                id,
                "Publisher ID must not be null"
        );
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(
                name,
                "Publisher name must not be null"
        );
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = List.copyOf(Objects.requireNonNull(
                aliases,
                "Publisher aliases must not be null"
        ));
    }
}

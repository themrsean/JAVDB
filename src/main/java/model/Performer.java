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

public class Performer {
    private UUID id;
    private String mainName;
    private List<String> aliases;
    private PerformerCategory category;

    public Performer() {
        aliases = List.of();
        category = PerformerCategory.UNKNOWN;
    }

    public Performer(
            UUID id,
            String mainName,
            List<String> aliases,
            PerformerCategory category) {

        setId(id);
        setMainName(mainName);
        setAliases(aliases);
        setCategory(category);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = Objects.requireNonNull(
                id,
                "Performer ID must not be null"
        );
    }

    public String getMainName() {
        return mainName;
    }

    public void setMainName(String mainName) {
        this.mainName = Objects.requireNonNull(
                mainName,
                "Performer main name must not be null"
        );
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = List.copyOf(Objects.requireNonNull(
                aliases,
                "Performer aliases must not be null"
        ));
    }

    public PerformerCategory getCategory() {
        return category;
    }

    public void setCategory(PerformerCategory category) {
        this.category = Objects.requireNonNull(
                category,
                "Performer category must not be null"
        );
    }
}

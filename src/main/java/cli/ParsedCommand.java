package cli;

import model.PerformerCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ParsedCommand(
        List<String> path,
        Map<String, List<String>> options,
        String databasePath,
        String outputMode,
        boolean help,
        boolean verbose) {

    public boolean hasOption(String name) {
        return options.containsKey(name);
    }

    public List<String> values(String name) {
        return options.getOrDefault(name, List.of());
    }

    public String optionalValue(String name) {
        String value = null;
        final List<String> values = values(name);

        if (!values.isEmpty()) {
            value = values.getLast();
        }

        return value;
    }

    public String requiredValue(String name) {
        final String value = optionalValue(name);

        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required option: --" + name
            );
        }

        return value;
    }

    public UUID requiredUuid(String name) {
        return parseUuid(requiredValue(name), name);
    }

    public UUID optionalUuid(String name) {
        UUID id = null;
        final String value = optionalValue(name);

        if (value != null) {
            id = parseUuid(value, name);
        }

        return id;
    }

    public List<UUID> uuids(String name) {
        return values(name).stream()
                .map(value -> parseUuid(value, name))
                .toList();
    }

    public LocalDate optionalDate(String name) {
        LocalDate date = null;
        final String value = optionalValue(name);

        if (value != null) {
            try {
                date = LocalDate.parse(value);
            } catch (java.time.format.DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "Invalid date for --" + name + ": " + value,
                        exception
                );
            }
        }

        return date;
    }

    public PerformerCategory requiredCategory(String name) {
        final String value = requiredValue(name);
        PerformerCategory category = null;

        for (PerformerCategory candidate : PerformerCategory.values()) {
            if (candidate.name().equalsIgnoreCase(value)) {
                category = candidate;
            }
        }

        if (category == null) {
            throw new IllegalArgumentException(
                    "Invalid performer category: " + value
            );
        }

        return category;
    }

    public boolean optionalBoolean(String name, boolean defaultValue) {
        boolean result = defaultValue;
        final String value = optionalValue(name);

        if (value != null) {
            if ("true".equalsIgnoreCase(value)) {
                result = true;
            } else if ("false".equalsIgnoreCase(value)) {
                result = false;
            } else {
                throw new IllegalArgumentException(
                        "Invalid boolean for --" + name + ": " + value
                );
            }
        }

        return result;
    }

    public long optionalLong(String name, long defaultValue) {
        long result = defaultValue;
        final String value = optionalValue(name);

        if (value != null && !value.isBlank()) {
            try {
                result = Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid integer for --" + name + ": " + value,
                        exception
                );
            }
        }

        return result;
    }

    public int optionalInt(String name, int defaultValue) {
        return Math.toIntExact(optionalLong(name, defaultValue));
    }

    private UUID parseUuid(String value, String optionName) {
        Objects.requireNonNull(value, "UUID value must not be null");

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Invalid UUID for --" + optionName + ": " + value,
                    exception
            );
        }
    }
}

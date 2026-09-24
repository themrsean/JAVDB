package cli;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class CsvSchema {
    private static final Set<String> ALLOWED_COLUMNS = Set.of(
            "path",
            "file_size",
            "duration",
            "duration_millis",
            "width",
            "height",
            "content_hash",
            "last_modified_millis"
    );

    private CsvSchema() {
    }

    static Map<String, Integer> header(List<String> row) {
        return header(row, ALLOWED_COLUMNS, Set.of("path"));
    }

    static Map<String, Integer> header(
            List<String> row,
            Set<String> allowedColumns,
            Set<String> requiredColumns) {

        final Map<String, Integer> header = new HashMap<>();
        int index = 0;

        for (String column : row) {
            final String normalized =
                    column.trim().toLowerCase(Locale.ROOT);

            if (!allowedColumns.contains(normalized)) {
                throw new IllegalArgumentException(
                        "Unknown CSV column: " + column
                );
            }

            header.put(normalized, index);
            index++;
        }

        for (String requiredColumn : requiredColumns) {
            if (!header.containsKey(requiredColumn)) {
                throw new IllegalArgumentException(
                        "CSV input must contain a " + requiredColumn
                                + " column."
                );
            }
        }

        return header;
    }

    static String value(
            Map<String, Integer> header,
            List<String> row,
            String column) {

        final String value = optionalValue(header, row, column);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required CSV column value: " + column
            );
        }

        return value;
    }

    static String optionalValue(
            Map<String, Integer> header,
            List<String> row,
            String column) {

        String value = null;
        final Integer index = header.get(column);

        if (index != null && index < row.size()) {
            final String rawValue = row.get(index);

            if (!rawValue.isBlank()) {
                value = rawValue;
            }
        }

        return value;
    }

    static long optionalLong(
            Map<String, Integer> header,
            List<String> row,
            String column,
            long defaultValue) {

        long result = defaultValue;
        final String value = optionalValue(header, row, column);

        if (value != null) {
            try {
                result = Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Invalid numeric CSV value for " + column + ": "
                                + value,
                        exception
                );
            }
        }

        return result;
    }

    static int optionalInt(
            Map<String, Integer> header,
            List<String> row,
            String column,
            int defaultValue) {

        return Math.toIntExact(optionalLong(
                header,
                row,
                column,
                defaultValue
        ));
    }
}

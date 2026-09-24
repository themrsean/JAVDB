package cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

final class CsvReader {
    private static final char COMMA = ',';
    private static final char QUOTE = '"';
    private static final int NEXT_CHARACTER_OFFSET = 1;

    private final BufferedReader reader;

    CsvReader(Reader reader) {
        this.reader = new BufferedReader(reader);
    }

    List<List<String>> readAll() throws IOException {
        final List<List<String>> rows = new ArrayList<>();
        String line = reader.readLine();

        while (line != null) {
            if (!line.isBlank()) {
                rows.add(parseLine(line));
            }

            line = reader.readLine();
        }

        return rows;
    }

    private List<String> parseLine(String line) {
        final List<String> values = new ArrayList<>();
        final StringBuilder value = new StringBuilder();
        boolean quoted = false;
        int index = 0;

        while (index < line.length()) {
            final char character = line.charAt(index);

            if (character == QUOTE) {
                final int nextIndex = index + NEXT_CHARACTER_OFFSET;
                final boolean escapedQuote =
                        quoted
                                && nextIndex < line.length()
                                && line.charAt(nextIndex) == QUOTE;

                if (escapedQuote) {
                    value.append(QUOTE);
                    index = nextIndex + NEXT_CHARACTER_OFFSET;
                } else {
                    quoted = !quoted;
                    index++;
                }
            } else if (character == COMMA && !quoted) {
                values.add(value.toString());
                value.setLength(0);
                index++;
            } else {
                value.append(character);
                index++;
            }
        }

        values.add(value.toString());

        return values;
    }
}

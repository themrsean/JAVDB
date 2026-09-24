package cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CommandParser {
    private static final String OPTION_PREFIX = "--";
    private static final String HELP_OPTION = "help";
    private static final String DATABASE_OPTION = "database";
    private static final String OUTPUT_OPTION = "output";
    private static final String VERBOSE_OPTION = "verbose";
    private static final String FAIL_FAST_OPTION = "fail-fast";
    private static final String HASH_OPTION = "hash";
    private static final String DRY_RUN_OPTION = "dry-run";
    private static final String REFRESH_OPTION = "refresh";
    private static final String ALL_UNASSIGNED_OPTION = "all-unassigned";
    private static final String OVERWRITE_OPTION = "overwrite";
    private static final int OPTION_PREFIX_LENGTH = 2;
    private static final int NEXT_ARGUMENT_OFFSET = 1;

    private CommandParser() {
    }

    public static ParsedCommand parse(String[] args) {
        final List<String> path = new ArrayList<>();
        final Map<String, List<String>> options = new LinkedHashMap<>();
        String databasePath = null;
        String outputMode = "human";
        boolean help = false;
        boolean verbose = false;
        int index = 0;

        while (index < args.length) {
            final String argument = args[index];

            if (argument.startsWith(OPTION_PREFIX)) {
                final String optionName =
                        argument.substring(OPTION_PREFIX_LENGTH);

                if (HELP_OPTION.equals(optionName)) {
                    help = true;
                    index++;
                } else if (VERBOSE_OPTION.equals(optionName)) {
                    verbose = true;
                    index++;
                } else if (isFlag(optionName)) {
                    options.computeIfAbsent(
                            optionName,
                            key -> new ArrayList<>()
                    );
                    index++;
                } else {
                    final int valueIndex = index + NEXT_ARGUMENT_OFFSET;

                    if (valueIndex >= args.length
                            || args[valueIndex].startsWith(OPTION_PREFIX)) {
                        throw new IllegalArgumentException(
                                "Missing value for option: " + argument
                        );
                    }

                    final String value = args[valueIndex];

                    if (DATABASE_OPTION.equals(optionName)) {
                        databasePath = value;
                    } else if (OUTPUT_OPTION.equals(optionName)) {
                        outputMode = value;
                    } else {
                        options.computeIfAbsent(
                                optionName,
                                key -> new ArrayList<>()
                        ).add(value);
                    }

                    index = valueIndex + NEXT_ARGUMENT_OFFSET;
                }
            } else {
                path.add(argument);
                index++;
            }
        }

        if (path.size() == 1 && HELP_OPTION.equals(path.getFirst())) {
            help = true;
        }

        return new ParsedCommand(
                List.copyOf(path),
                copyOptions(options),
                databasePath,
                outputMode,
                help,
                verbose
        );
    }

    private static boolean isFlag(String optionName) {
        return FAIL_FAST_OPTION.equals(optionName)
                || HASH_OPTION.equals(optionName)
                || DRY_RUN_OPTION.equals(optionName)
                || REFRESH_OPTION.equals(optionName)
                || ALL_UNASSIGNED_OPTION.equals(optionName)
                || OVERWRITE_OPTION.equals(optionName);
    }

    private static Map<String, List<String>> copyOptions(
            Map<String, List<String>> options) {

        final Map<String, List<String>> copy = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : options.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return Map.copyOf(copy);
    }
}

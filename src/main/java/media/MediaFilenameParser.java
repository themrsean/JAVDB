package media;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaFilenameParser {
    private static final Pattern DATE_PATTERN =
            Pattern.compile("^\\((\\d{2})\\.(\\d{2})\\.(\\d{2})\\)\\s+(.+)$");
    private static final Pattern SEASON_EPISODE_PATTERN =
            Pattern.compile("^S(\\d+)E(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_PATTERN =
            Pattern.compile("^[A-Za-z]+\\d{2,}$");
    private static final String SEPARATOR = " - ";
    private static final String COMMA = ",";
    private static final String DOT = ".";
    private static final int YEAR_BASE = 2_000;
    private static final int FIRST_DATE_GROUP = 1;
    private static final int SECOND_DATE_GROUP = 2;
    private static final int THIRD_DATE_GROUP = 3;
    private static final int REMAINDER_GROUP = 4;
    private static final int MINIMUM_SEGMENT_COUNT = 2;
    private static final int LAST_INDEX_OFFSET = 1;
    private static final int TITLE_OFFSET = 2;
    private static final int MAXIMUM_OPTION_FIELDS = 1;
    private static final Pattern EXTENSION_PATTERN =
            Pattern.compile("^[A-Za-z0-9]{1,8}$");

    public ParsedMediaFilename parse(Path mediaPath) {
        Objects.requireNonNull(mediaPath, "Media path must not be null");

        final String filename = filename(mediaPath);
        final String extension = extension(filename);
        final String extensionless = removeExtension(filename);
        final List<FilenameParseIssue> issues = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        LocalDate releaseDate = null;
        String parseText = extensionless;

        final Matcher dateMatcher = DATE_PATTERN.matcher(extensionless);

        if (dateMatcher.matches()) {
            try {
                releaseDate = LocalDate.of(
                        YEAR_BASE + Integer.parseInt(
                                dateMatcher.group(FIRST_DATE_GROUP)
                        ),
                        Integer.parseInt(
                                dateMatcher.group(SECOND_DATE_GROUP)
                        ),
                        Integer.parseInt(
                                dateMatcher.group(THIRD_DATE_GROUP)
                        )
                );
            } catch (DateTimeException exception) {
                issues.add(FilenameParseIssue.INVALID_DATE);
            }

            parseText = dateMatcher.group(REMAINDER_GROUP);
        } else {
            issues.add(FilenameParseIssue.MISSING_DATE);
        }

        final List<String> segments = trimmedSegments(parseText);
        String title = null;
        final List<String> performers = new ArrayList<>();
        final List<String> contextSegments = new ArrayList<>();
        String season = null;
        String episode = null;
        String codeCandidate = null;
        int optionFieldCount = 0;

        if (segments.size() < MINIMUM_SEGMENT_COUNT) {
            issues.add(FilenameParseIssue.MISSING_TITLE);
            issues.add(FilenameParseIssue.MISSING_PERFORMERS);
        } else {
            title = segments.get(segments.size() - TITLE_OFFSET);
            performers.addAll(performers(
                    segments.get(segments.size() - LAST_INDEX_OFFSET)
            ));

            if (title.isBlank()) {
                issues.add(FilenameParseIssue.MISSING_TITLE);
            }

            if (performers.isEmpty()) {
                issues.add(FilenameParseIssue.MISSING_PERFORMERS);
            }

            int index = 0;

            while (index < segments.size() - TITLE_OFFSET) {
                final String segment = segments.get(index);
                final Matcher seasonEpisodeMatcher =
                        SEASON_EPISODE_PATTERN.matcher(segment);

                if (seasonEpisodeMatcher.matches()) {
                    season = seasonEpisodeMatcher.group(FIRST_DATE_GROUP);
                    episode = seasonEpisodeMatcher.group(SECOND_DATE_GROUP);
                    optionFieldCount++;
                } else if (CODE_PATTERN.matcher(segment).matches()) {
                    codeCandidate = segment;
                    optionFieldCount++;
                } else {
                    contextSegments.add(segment);
                }

                index++;
            }
        }

        if (optionFieldCount > MAXIMUM_OPTION_FIELDS) {
            issues.add(FilenameParseIssue.TOO_MANY_OPTION_FIELDS);
        }

        return new ParsedMediaFilename(
                mediaPath,
                extensionless,
                extension,
                releaseDate,
                List.copyOf(contextSegments),
                season,
                episode,
                codeCandidate,
                title,
                List.copyOf(performers),
                List.of(),
                issues.isEmpty()
                        ? FilenameParseStatus.VALID
                        : FilenameParseStatus.INVALID,
                List.copyOf(issues),
                List.copyOf(warnings)
        );
    }

    private String filename(Path mediaPath) {
        final Path filenamePath = mediaPath.getFileName();

        if (filenamePath == null || filenamePath.toString().trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Media filename must not be blank."
            );
        }

        return filenamePath.toString().trim();
    }

    private String extension(String filename) {
        String extension = "";
        final int dotIndex = filename.lastIndexOf(DOT);

        if (hasExtension(filename, dotIndex)) {
            extension = filename.substring(dotIndex + LAST_INDEX_OFFSET);
        }

        return extension;
    }

    private String removeExtension(String filename) {
        String extensionless = filename;
        final int dotIndex = filename.lastIndexOf(DOT);

        if (hasExtension(filename, dotIndex)) {
            extensionless = filename.substring(0, dotIndex);
        }

        return extensionless.trim();
    }

    private boolean hasExtension(String filename, int dotIndex) {
        return dotIndex > 0
                && dotIndex < filename.length() - LAST_INDEX_OFFSET
                && EXTENSION_PATTERN.matcher(
                        filename.substring(dotIndex + LAST_INDEX_OFFSET)
                ).matches();
    }

    private List<String> trimmedSegments(String text) {
        final List<String> segments = new ArrayList<>();

        for (String segment : text.split(Pattern.quote(SEPARATOR))) {
            segments.add(segment.trim());
        }

        return segments;
    }

    private List<String> performers(String performerText) {
        final List<String> performers = new ArrayList<>();

        for (String performer : performerText.split(COMMA)) {
            final String trimmed = performer.trim();

            if (!trimmed.isEmpty()) {
                performers.add(trimmed);
            }
        }

        return performers;
    }
}

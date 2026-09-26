package media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

class MediaFilenameParserTest {
    private final MediaFilenameParser parser = new MediaFilenameParser();

    @Test
    @DisplayName("Parses representative structures")
    void parsesRepresentativeStructures() {
        final ParsedMediaFilename publisherOnly = parser.parse(Path.of(
                "(25.11.25) Publisher - Scene Title - Performer One, Performer Two.mp4"
        ));
        final ParsedMediaFilename seriesMovie = parser.parse(Path.of(
                "(25.12.26) Series - Movie Title - Scene Title - Performer One.mp4"
        ));
        final ParsedMediaFilename seasonEpisode = parser.parse(Path.of(
                "(26.01.19) Publisher - S13E7 - Scene Title - Performer One.mp4"
        ));
        final ParsedMediaFilename code = parser.parse(Path.of(
                "(24.07.18) Publisher - E1919 - Scene Title - Performer One"
        ));
        final ParsedMediaFilename full = parser.parse(Path.of(
                "(21.12.16) Publisher - Series - S4E4 - Movie Title - Scene Title - Performer One, Performer Two.mkv"
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(LocalDate.of(2025, 11, 25),
                        publisherOnly.releaseDate()),
                () -> Assertions.assertEquals(List.of("Publisher"),
                        publisherOnly.contextSegments()),
                () -> Assertions.assertEquals("Scene Title",
                        publisherOnly.titleCandidate()),
                () -> Assertions.assertEquals(
                        List.of("Performer One", "Performer Two"),
                        publisherOnly.performerCandidates()
                ),
                () -> Assertions.assertEquals(List.of("Series", "Movie Title"),
                        seriesMovie.contextSegments()),
                () -> Assertions.assertEquals("13",
                        seasonEpisode.season()),
                () -> Assertions.assertEquals("7",
                        seasonEpisode.episode()),
                () -> Assertions.assertEquals("E1919",
                        code.codeCandidate()),
                () -> Assertions.assertEquals(List.of(
                        "Publisher",
                        "Series",
                        "Movie Title"
                ), full.contextSegments())
        );
    }

    @Test
    @DisplayName("Parses Brazzers series movie scene structure")
    void parsesBrazzersSeriesMovieSceneStructure() {
        final ParsedMediaFilename parsed = parser.parse(Path.of(
                "(15.02.08) Brazzers - BigWetButts - Asspirations 2 - "
                        + "Abella's Ass Is In Danger - Abella Danger"
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(LocalDate.of(2015, 2, 8),
                        parsed.releaseDate()),
                () -> Assertions.assertEquals(List.of(
                        "Brazzers", "BigWetButts", "Asspirations 2"
                ), parsed.contextSegments()),
                () -> Assertions.assertEquals("Abella's Ass Is In Danger",
                        parsed.titleCandidate()),
                () -> Assertions.assertEquals(List.of("Abella Danger"),
                        parsed.performerCandidates()),
                () -> Assertions.assertEquals(FilenameParseStatus.VALID,
                        parsed.status())
        );
    }

    @Test
    @DisplayName("Preserves exact separator behavior and title punctuation")
    void preservesSeparatorBehaviorAndTitlePunctuation() {
        final ParsedMediaFilename parsed = parser.parse(Path.of(
                "(25.01.02) Publisher - Scene-Title, With Comma - Performer One.MP4"
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals("Scene-Title, With Comma",
                        parsed.titleCandidate()),
                () -> Assertions.assertEquals(List.of("Performer One"),
                        parsed.performerCandidates()),
                () -> Assertions.assertEquals("mp4", parsed.extension()
                        .toLowerCase(java.util.Locale.ROOT))
        );
    }

    @Test
    @DisplayName("Reports invalid filename issues")
    void reportsInvalidFilenameIssues() {
        final ParsedMediaFilename missingDate = parser.parse(Path.of(
                "Publisher - Scene - Performer.mp4"
        ));
        final ParsedMediaFilename missingTitle = parser.parse(Path.of(
                "(25.01.02) Performer.mp4"
        ));
        final ParsedMediaFilename invalidDate = parser.parse(Path.of(
                "(25.99.02) Publisher - Scene - Performer.mp4"
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(FilenameParseStatus.INVALID,
                        missingDate.status()),
                () -> Assertions.assertTrue(missingDate.issues().contains(
                        FilenameParseIssue.MISSING_DATE
                )),
                () -> Assertions.assertTrue(missingTitle.issues().contains(
                        FilenameParseIssue.MISSING_TITLE
                )),
                () -> Assertions.assertTrue(invalidDate.issues().contains(
                        FilenameParseIssue.INVALID_DATE
                ))
        );
    }

    @Test
    @DisplayName("Handles no extension, empty performers, and non-ASCII")
    void handlesNoExtensionEmptyPerformersAndNonAscii() {
        final ParsedMediaFilename parsed = parser.parse(Path.of(
                "(25.02.03) Série - Scène Étrange - Performer One, , Performer Two"
        ));

        Assertions.assertAll(
                () -> Assertions.assertEquals("", parsed.extension()),
                () -> Assertions.assertEquals("Scène Étrange",
                        parsed.titleCandidate()),
                () -> Assertions.assertEquals(
                        List.of("Performer One", "Performer Two"),
                        parsed.performerCandidates()
                ),
                () -> Assertions.assertEquals(FilenameParseStatus.VALID,
                        parsed.status())
        );
    }
}

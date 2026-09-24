package media;

import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.MovieSelectionResult;
import service.MovieSelectionStatus;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class CanonicalMediaFilenameGeneratorTest {
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-dddd-1111-dddd-111111111111");
    private static final UUID SERIES_ID =
            UUID.fromString("22222222-dddd-2222-dddd-222222222222");
    private static final UUID SCENE_ID =
            UUID.fromString("33333333-dddd-3333-dddd-333333333333");
    private static final UUID MEDIA_ID =
            UUID.fromString("44444444-dddd-4444-dddd-444444444444");
    private static final UUID MOVIE_ID =
            UUID.fromString("55555555-dddd-5555-dddd-555555555555");
    private static final UUID PERFORMER_ONE_ID =
            UUID.fromString("66666666-dddd-6666-dddd-666666666666");
    private static final UUID PERFORMER_TWO_ID =
            UUID.fromString("77777777-dddd-7777-dddd-777777777777");
    private static final long FILE_SIZE = 12_345L;
    private static final int WIDTH = 1_920;
    private static final int HEIGHT = 1_080;
    private static final long LAST_MODIFIED_MILLIS = 98_765L;

    private final CanonicalMediaFilenameGenerator generator =
            new CanonicalMediaFilenameGenerator();

    @Test
    @DisplayName("Complete metadata generates canonical filename")
    void completeMetadataGeneratesCanonicalFilename() {
        final Scene scene = scene(
                "Scene Title",
                LocalDate.of(2026, 1, 15),
                "ABC-001",
                series(),
                "4",
                "5",
                performers()
        );
        final Movie movie = movie("Original Movie", LocalDate.of(2025, 2, 3));
        final CanonicalFilenameResult result =
                generator.generate(new CanonicalFilenameRequest(
                        scene,
                        mediaFile("old.mp4"),
                        selection(MovieSelectionStatus.SELECTED, movie)
                ));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        FilenameGenerationStatus.READY,
                        result.status()
                ),
                () -> Assertions.assertEquals(
                        "(26.01.15) Publisher - Series - S4E5 - "
                                + "Original Movie - Scene Title - "
                                + "Alice, Bob.mp4",
                        result.proposedFilename()
                )
        );
    }

    @Test
    @DisplayName("Publisher only omits absent optional fields")
    void publisherOnlyOmitsAbsentOptionalFields() {
        final Scene scene = scene(
                "Publisher Scene",
                LocalDate.of(2025, 11, 25),
                null,
                null,
                null,
                null,
                performers()
        );

        final CanonicalFilenameResult result =
                generator.generate(request(scene, "old.mkv"));

        Assertions.assertEquals(
                "(25.11.25) Publisher - Publisher Scene - Alice, Bob.mkv",
                result.proposedFilename()
        );
    }

    @Test
    @DisplayName("Series only still includes scene publisher once")
    void seriesOnlyStillIncludesScenePublisherOnce() {
        final Scene scene = scene(
                "Series Scene",
                null,
                null,
                series(),
                null,
                null,
                performers()
        );

        final CanonicalFilenameResult result =
                generator.generate(request(scene, "old.avi"));

        Assertions.assertEquals(
                "Publisher - Series - Series Scene - Alice, Bob.avi",
                result.proposedFilename()
        );
    }

    @Test
    @DisplayName("Code is used when season and episode are absent")
    void codeIsUsedWhenSeasonAndEpisodeAreAbsent() {
        final Scene scene = scene(
                "Code Scene",
                null,
                "E1919",
                null,
                null,
                null,
                performers()
        );

        final CanonicalFilenameResult result =
                generator.generate(request(scene, "old.mov"));

        Assertions.assertEquals(
                "Publisher - E1919 - Code Scene - Alice, Bob.mov",
                result.proposedFilename()
        );
    }

    @Test
    @DisplayName("Season and episode are preferred over code")
    void seasonAndEpisodeArePreferredOverCode() {
        final Scene scene = scene(
                "Episode Scene",
                null,
                "E1919",
                null,
                "13",
                "7",
                performers()
        );

        final CanonicalFilenameResult result =
                generator.generate(request(scene, "old.mp4"));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Publisher - S13E7 - Episode Scene - Alice, Bob.mp4",
                        result.proposedFilename()
                ),
                () -> Assertions.assertFalse(result.warnings().isEmpty())
        );
    }

    @Test
    @DisplayName("Original movie is included and absent movie is omitted")
    void originalMovieIsIncludedAndAbsentMovieIsOmitted() {
        final Scene scene = scene(
                "Movie Scene",
                null,
                null,
                null,
                null,
                null,
                performers()
        );
        final Movie movie = movie("Original Movie", LocalDate.of(2021, 4, 10));

        final CanonicalFilenameResult withMovie =
                generator.generate(new CanonicalFilenameRequest(
                        scene,
                        mediaFile("old.mp4"),
                        selection(MovieSelectionStatus.SELECTED, movie)
                ));
        final CanonicalFilenameResult withoutMovie =
                generator.generate(request(scene, "old.mp4"));

        Assertions.assertAll(
                () -> Assertions.assertEquals(
                        "Publisher - Original Movie - Movie Scene - Alice, Bob.mp4",
                        withMovie.proposedFilename()
                ),
                () -> Assertions.assertEquals(
                        "Publisher - Movie Scene - Alice, Bob.mp4",
                        withoutMovie.proposedFilename()
                )
        );
    }

    @Test
    @DisplayName("Ambiguous movie selection requires review")
    void ambiguousMovieSelectionRequiresReview() {
        final CanonicalFilenameResult result =
                generator.generate(new CanonicalFilenameRequest(
                        scene(
                                "Scene",
                                null,
                                null,
                                null,
                                null,
                                null,
                                performers()
                        ),
                        mediaFile("old.mp4"),
                        new MovieSelectionResult(
                                SCENE_ID,
                                MovieSelectionStatus.REVIEW_REQUIRED,
                                Optional.empty(),
                                List.of(),
                                "Ambiguous movie"
                        )
                ));

        Assertions.assertEquals(
                FilenameGenerationStatus.REVIEW_REQUIRED,
                result.status()
        );
    }

    @Test
    @DisplayName("Primary performer names are sorted and aliases are ignored")
    void primaryPerformerNamesAreSortedAndAliasesAreIgnored() {
        final Scene scene = scene(
                "Cast Scene",
                null,
                null,
                null,
                null,
                null,
                List.of(
                        performer(PERFORMER_TWO_ID, "bob", "B Alias"),
                        performer(PERFORMER_ONE_ID, "Alice", "A Alias")
                )
        );

        final CanonicalFilenameResult result =
                generator.generate(request(scene, "old.MP4"));

        Assertions.assertEquals(
                "Publisher - Cast Scene - Alice, bob.MP4",
                result.proposedFilename()
        );
    }

    @Test
    @DisplayName("Files without extension remain without extension")
    void filesWithoutExtensionRemainWithoutExtension() {
        final CanonicalFilenameResult result =
                generator.generate(request(
                        scene(
                                "No Extension",
                                null,
                                null,
                                null,
                                null,
                                null,
                                performers()
                        ),
                        "old"
                ));

        Assertions.assertEquals(
                "Publisher - No Extension - Alice, Bob",
                result.proposedFilename()
        );
    }

    @Test
    @DisplayName("Missing date omits date prefix")
    void missingDateOmitsDatePrefix() {
        final CanonicalFilenameResult result =
                generator.generate(request(
                        scene(
                                "No Date",
                                null,
                                null,
                                null,
                                null,
                                null,
                                performers()
                        ),
                        "old.mp4"
                ));

        Assertions.assertFalse(result.proposedFilename().startsWith("("));
    }

    @Test
    @DisplayName("Unicode hyphens and commas are preserved")
    void unicodeHyphensAndCommasArePreserved() {
        final CanonicalFilenameResult result =
                generator.generate(request(
                        scene(
                                "Café Scene, Part-One",
                                null,
                                null,
                                null,
                                null,
                                null,
                                performers()
                        ),
                        "old.mp4"
                ));

        Assertions.assertEquals(
                "Publisher - Café Scene, Part-One - Alice, Bob.mp4",
                result.proposedFilename()
        );
    }

    @Test
    @DisplayName("Slashes are sanitized and repeated whitespace collapses")
    void slashesAreSanitizedAndRepeatedWhitespaceCollapses() {
        final CanonicalFilenameResult result =
                generator.generate(request(
                        scene(
                                "Bad / Path   Name.",
                                null,
                                null,
                                null,
                                null,
                                null,
                                performers()
                        ),
                        "old.mp4"
                ));

        Assertions.assertEquals(
                "Publisher - Bad Path Name - Alice, Bob.mp4",
                result.proposedFilename()
        );
    }

    @Test
    @DisplayName("NUL is invalid metadata")
    void nulIsInvalidMetadata() {
        final CanonicalFilenameResult result =
                generator.generate(request(
                        scene(
                                "Bad \0 Name",
                                null,
                                null,
                                null,
                                null,
                                null,
                                performers()
                        ),
                        "old.mp4"
                ));

        Assertions.assertEquals(
                FilenameGenerationStatus.INVALID_METADATA,
                result.status()
        );
    }

    @Test
    @DisplayName("Blank title is invalid metadata")
    void blankTitleIsInvalidMetadata() {
        final CanonicalFilenameResult result =
                generator.generate(request(
                        scene(
                                "   ",
                                null,
                                null,
                                null,
                                null,
                                null,
                                performers()
                        ),
                        "old.mp4"
                ));

        Assertions.assertEquals(
                FilenameGenerationStatus.INVALID_METADATA,
                result.status()
        );
    }

    @Test
    @DisplayName("Excessive filename length is reported")
    void excessiveFilenameLengthIsReported() {
        final String longTitle = "Title ".repeat(80);

        final CanonicalFilenameResult result =
                generator.generate(request(
                        scene(
                                longTitle,
                                null,
                                null,
                                null,
                                null,
                                null,
                                performers()
                        ),
                        "old.mp4"
                ));

        Assertions.assertEquals(
                FilenameGenerationStatus.TOO_LONG,
                result.status()
        );
    }

    @Test
    @DisplayName("Existing canonical filename returns unchanged")
    void existingCanonicalFilenameReturnsUnchanged() {
        final Scene scene = scene(
                "Scene",
                null,
                null,
                null,
                null,
                null,
                performers()
        );
        final CanonicalFilenameResult first =
                generator.generate(request(scene, "old.mp4"));
        final CanonicalFilenameResult second =
                generator.generate(request(scene, first.proposedFilename()));

        Assertions.assertEquals(
                FilenameGenerationStatus.UNCHANGED,
                second.status()
        );
    }

    private CanonicalFilenameRequest request(Scene scene, String filename) {
        return new CanonicalFilenameRequest(
                scene,
                mediaFile(filename),
                new MovieSelectionResult(
                        scene.getId(),
                        MovieSelectionStatus.NONE,
                        Optional.empty(),
                        List.of(),
                        "No movie"
                )
        );
    }

    private MediaFile mediaFile(String filename) {
        return new MediaFile(
                MEDIA_ID,
                Path.of("/tmp", filename),
                FILE_SIZE,
                null,
                Duration.ofMinutes(12),
                WIDTH,
                HEIGHT,
                LAST_MODIFIED_MILLIS
        );
    }

    private Scene scene(
            String title,
            LocalDate releaseDate,
            String code,
            Series sceneSeries,
            String season,
            String episode,
            List<Performer> performers) {

        return new Scene(
                SCENE_ID,
                title,
                publisher(),
                releaseDate,
                code,
                sceneSeries,
                season,
                episode,
                performers,
                List.of()
        );
    }

    private Publisher publisher() {
        return new Publisher(PUBLISHER_ID, "Publisher", List.of());
    }

    private Series series() {
        return new Series(SERIES_ID, "Series", publisher());
    }

    private List<Performer> performers() {
        return List.of(
                performer(PERFORMER_ONE_ID, "Alice", "A Alias"),
                performer(PERFORMER_TWO_ID, "Bob", "B Alias")
        );
    }

    private Performer performer(UUID id, String mainName, String alias) {
        return new Performer(
                id,
                mainName,
                List.of(alias),
                PerformerCategory.UNKNOWN
        );
    }

    private Movie movie(String title, LocalDate releaseDate) {
        return new Movie(
                MOVIE_ID,
                title,
                releaseDate,
                publisher(),
                List.of(),
                false,
                List.of()
        );
    }

    private MovieSelectionResult selection(
            MovieSelectionStatus status,
            Movie movie) {

        return new MovieSelectionResult(
                SCENE_ID,
                status,
                Optional.of(movie),
                List.of(movie),
                "Selected"
        );
    }
}

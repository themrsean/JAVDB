package media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

class VideoFileDiscoveryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Discovery recursively finds supported videos deterministically")
    void discoveryRecursivelyFindsSupportedVideosDeterministically()
            throws Exception {

        final Path nested = Files.createDirectories(
                temporaryDirectory.resolve("nested")
        );
        final Path alpha = Files.writeString(
                temporaryDirectory.resolve("alpha.MP4"),
                "a"
        );
        final Path zulu = Files.writeString(
                nested.resolve("zulu.mkv"),
                "z"
        );
        Files.writeString(temporaryDirectory.resolve("ignore.txt"), "x");

        final List<Path> paths = new VideoFileDiscovery()
                .discover(temporaryDirectory, Set.of());

        Assertions.assertEquals(
                List.of(
                        alpha.toAbsolutePath().normalize(),
                        zulu.toAbsolutePath().normalize()
                ),
                paths
        );
    }

    @Test
    @DisplayName("Discovery handles empty directories")
    void discoveryHandlesEmptyDirectories() throws Exception {
        Assertions.assertTrue(new VideoFileDiscovery()
                .discover(temporaryDirectory, Set.of())
                .isEmpty());
    }

    @Test
    @DisplayName("Discovery rejects missing and non-directory roots")
    void discoveryRejectsMissingAndNonDirectoryRoots() throws Exception {
        final Path file = Files.writeString(
                temporaryDirectory.resolve("file.mp4"),
                "x"
        );

        Assertions.assertAll(
                () -> Assertions.assertThrows(
                        java.io.IOException.class,
                        () -> new VideoFileDiscovery().discover(
                                temporaryDirectory.resolve("missing"),
                                Set.of()
                        )
                ),
                () -> Assertions.assertThrows(
                        java.io.IOException.class,
                        () -> new VideoFileDiscovery().discover(
                                file,
                                Set.of()
                        )
                )
        );
    }

    @Test
    @DisplayName("Discovery supports additional extensions")
    void discoverySupportsAdditionalExtensions() throws Exception {
        final Path custom = Files.writeString(
                temporaryDirectory.resolve("clip.custom"),
                "x"
        );

        Assertions.assertEquals(
                List.of(custom.toAbsolutePath().normalize()),
                new VideoFileDiscovery().discover(
                        temporaryDirectory,
                        Set.of("custom")
                )
        );
    }

    @Test
    @DisplayName("Discovery handles paths containing spaces and commas")
    void discoveryHandlesPathsContainingSpacesAndCommas()
            throws Exception {

        final Path file = Files.writeString(
                temporaryDirectory.resolve("a video, one.mov"),
                "x"
        );

        Assertions.assertEquals(
                List.of(file.toAbsolutePath().normalize()),
                new VideoFileDiscovery().discover(
                        temporaryDirectory,
                        Set.of()
                )
        );
    }
}

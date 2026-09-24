package media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class FileHasherTest {
    private static final String ABC_SHA_256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String EMPTY_SHA_256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final int LARGE_REPEAT_COUNT = 10_000;

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("Hasher returns known lowercase SHA-256")
    void hasherReturnsKnownLowercaseSha256() throws Exception {
        final Path file = temporaryDirectory.resolve("abc.txt");
        Files.writeString(file, "abc");

        final String hash = new FileHasher().sha256(file);

        Assertions.assertEquals(ABC_SHA_256, hash);
        Assertions.assertEquals(hash.toLowerCase(java.util.Locale.ROOT), hash);
    }

    @Test
    @DisplayName("Hasher returns empty-file SHA-256")
    void hasherReturnsEmptyFileSha256() throws Exception {
        final Path file = temporaryDirectory.resolve("empty.bin");
        Files.write(file, new byte[0]);

        Assertions.assertEquals(EMPTY_SHA_256, new FileHasher().sha256(file));
    }

    @Test
    @DisplayName("Different content produces different hashes")
    void differentContentProducesDifferentHashes() throws Exception {
        final Path first = temporaryDirectory.resolve("first.bin");
        final Path second = temporaryDirectory.resolve("second.bin");
        Files.writeString(first, "abc");
        Files.writeString(second, "abcd");

        Assertions.assertNotEquals(
                new FileHasher().sha256(first),
                new FileHasher().sha256(second)
        );
    }

    @Test
    @DisplayName("Large fixture is hashed")
    void largeFixtureIsHashed() throws Exception {
        final Path file = temporaryDirectory.resolve("large.bin");
        final String content = "0123456789".repeat(LARGE_REPEAT_COUNT);
        Files.writeString(file, content);

        Assertions.assertFalse(new FileHasher().sha256(file).isBlank());
    }

    @Test
    @DisplayName("Missing file produces IO failure")
    void missingFileProducesIoFailure() {
        Assertions.assertThrows(
                java.io.IOException.class,
                () -> new FileHasher().sha256(
                        temporaryDirectory.resolve("missing.bin")
                )
        );
    }
}

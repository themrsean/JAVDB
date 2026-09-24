package media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FileHasher implements MediaHashProvider {
    private static final String SHA_256 = "SHA-256";
    private static final int BUFFER_SIZE_BYTES = 64 * 1024;
    private static final String HEX_FORMAT = "%02x";

    @Override
    public String hash(Path path) throws IOException {
        return sha256(path);
    }

    public String sha256(Path path) throws IOException {
        final MessageDigest digest = createDigest();
        final byte[] buffer = new byte[BUFFER_SIZE_BYTES];

        try (InputStream inputStream = Files.newInputStream(path)) {
            int bytesRead = inputStream.read(buffer);

            while (bytesRead >= 0) {
                digest.update(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }
        }

        return toHex(digest.digest());
    }

    private MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 digest is not available.",
                    exception
            );
        }
    }

    private String toHex(byte[] bytes) {
        final StringBuilder builder = new StringBuilder();

        for (byte value : bytes) {
            builder.append(String.format(HEX_FORMAT, value));
        }

        return builder.toString();
    }
}

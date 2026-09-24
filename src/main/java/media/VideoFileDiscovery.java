package media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class VideoFileDiscovery {
    public static final Set<String> DEFAULT_EXTENSIONS = Set.of(
            ".mp4",
            ".mkv",
            ".avi",
            ".mov",
            ".m4v",
            ".webm",
            ".mpg",
            ".mpeg",
            ".wmv",
            ".flv",
            ".ts",
            ".m2ts"
    );

    public List<Path> discover(
            Path root,
            Collection<String> additionalExtensions) throws IOException {

        return discover(root, additionalExtensions, true);
    }

    public List<Path> discover(
            Path root,
            Collection<String> additionalExtensions,
            boolean recursive) throws IOException {

        if (!Files.exists(root)) {
            throw new IOException("Root directory does not exist: " + root);
        }

        if (!Files.isDirectory(root)) {
            throw new IOException("Root is not a directory: " + root);
        }

        final Set<String> extensions =
                normalizedExtensions(additionalExtensions);
        final int maximumDepth = recursive ? Integer.MAX_VALUE : 1;

        try (Stream<Path> paths = Files.walk(root, maximumDepth)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> hasSupportedExtension(path, extensions))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private Set<String> normalizedExtensions(
            Collection<String> additionalExtensions) {

        final Set<String> extensions = new HashSet<>(DEFAULT_EXTENSIONS);

        if (additionalExtensions != null) {
            for (String extension : additionalExtensions) {
                if (extension != null && !extension.isBlank()) {
                    String normalized =
                            extension.trim().toLowerCase(Locale.ROOT);

                    if (!normalized.startsWith(".")) {
                        normalized = "." + normalized;
                    }

                    extensions.add(normalized);
                }
            }
        }

        return extensions;
    }

    private boolean hasSupportedExtension(
            Path path,
            Set<String> extensions) {

        final String fileName = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);
        boolean supported = false;

        for (String extension : extensions) {
            if (fileName.endsWith(extension)) {
                supported = true;
            }
        }

        return supported;
    }
}

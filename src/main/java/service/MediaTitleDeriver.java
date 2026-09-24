package service;

import java.nio.file.Path;
import java.util.Objects;

public final class MediaTitleDeriver {
    private static final String DOT = ".";
    private static final String SPACE = " ";

    public String derive(Path mediaPath) {
        Objects.requireNonNull(mediaPath, "Media path must not be null");

        final Path fileNamePath = mediaPath.getFileName();

        if (fileNamePath == null || fileNamePath.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "Media filename must not be blank."
            );
        }

        final String filename = fileNamePath.toString();
        final String extensionless = removeFinalExtension(filename);
        final String leadingText = extensionless.startsWith(DOT) ? DOT : "";
        final String derivationText = extensionless.startsWith(DOT)
                ? extensionless.substring(DOT.length())
                : extensionless;
        String title = leadingText + derivationText
                .replace("_", SPACE)
                .replaceAll("\\.{2,}", SPACE)
                .replace(DOT, SPACE)
                .replaceAll("\\s+", SPACE)
                .trim();

        if (title.isBlank()) {
            title = extensionless;
        }

        if (title.isBlank()) {
            throw new IllegalArgumentException(
                    "Derived scene title must not be blank."
            );
        }

        return title;
    }

    private String removeFinalExtension(String filename) {
        String extensionless = filename;
        final int lastDotIndex = filename.lastIndexOf(DOT);

        if (lastDotIndex > 0) {
            extensionless = filename.substring(0, lastDotIndex);
        }

        return extensionless;
    }
}

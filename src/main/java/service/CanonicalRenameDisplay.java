package service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record CanonicalRenameDisplay(
        String status,
        String currentFilename,
        String proposedFilename,
        Path proposedPath,
        boolean unchanged,
        boolean physicalDestinationExists,
        boolean databasePathConflict,
        List<String> warnings,
        String error) {

    public CanonicalRenameDisplay {
        Objects.requireNonNull(status, "Status must not be null");
        Objects.requireNonNull(
                currentFilename,
                "Current filename must not be null"
        );
        Objects.requireNonNull(
                proposedFilename,
                "Proposed filename must not be null"
        );
        Objects.requireNonNull(proposedPath, "Proposed path must not be null");
        warnings = List.copyOf(Objects.requireNonNull(
                warnings,
                "Warnings must not be null"
        ));
        error = Objects.requireNonNull(error, "Error must not be null");
    }
}

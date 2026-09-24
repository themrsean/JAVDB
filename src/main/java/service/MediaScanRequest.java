package service;

import java.nio.file.Path;
import java.util.Set;

public record MediaScanRequest(
        Path rootDirectory,
        boolean hashingEnabled,
        boolean dryRun,
        boolean failFast,
        Set<String> additionalExtensions,
        boolean recursive) {

    public MediaScanRequest(
            Path rootDirectory,
            boolean hashingEnabled,
            boolean dryRun,
            boolean failFast,
            Set<String> additionalExtensions) {

        this(
                rootDirectory,
                hashingEnabled,
                dryRun,
                failFast,
                additionalExtensions,
                true
        );
    }
}

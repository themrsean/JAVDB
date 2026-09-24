package service;

import java.util.List;
import java.util.UUID;

public record AutoIndexRequest(
        List<UUID> mediaFileIds,
        boolean dryRun,
        boolean failFast) {
}

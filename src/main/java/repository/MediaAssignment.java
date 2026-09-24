package repository;

import java.util.List;
import java.util.UUID;

public record MediaAssignment(
        UUID mediaFileId,
        List<MediaAssignmentReference> scenes,
        List<MediaAssignmentReference> movies) {
}

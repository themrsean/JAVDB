package service;

import model.VerificationStatus;

import java.time.LocalDate;
import java.util.UUID;

public record SceneReviewQueueItem(
        UUID sceneId,
        String title,
        VerificationStatus verificationStatus,
        LocalDate releaseDate,
        String publisherName,
        String seriesTitle,
        int mediaCount) {
}

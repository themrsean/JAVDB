package service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public record SceneReviewDisplayData(
        UUID mediaId,
        Path mediaPath,
        String publisherName,
        String seriesTitle,
        String movieTitle,
        List<String> performerNames) {

    public SceneReviewDisplayData {
        publisherName = publisherName == null ? "" : publisherName;
        seriesTitle = seriesTitle == null ? "" : seriesTitle;
        movieTitle = movieTitle == null ? "" : movieTitle;
        performerNames = performerNames == null
                ? List.of()
                : List.copyOf(performerNames);
    }
}

package service;

import model.Scene;
import model.VerificationStatus;
import repository.SceneRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SceneReviewQueueService {
    private final SceneRepository sceneRepository;

    public SceneReviewQueueService(SceneRepository sceneRepository) {
        this.sceneRepository = Objects.requireNonNull(
                sceneRepository,
                "Scene repository must not be null"
        );
    }

    public List<SceneReviewQueueItem> loadReviewScenes(
            int limit,
            int offset) throws SQLException {

        final List<SceneReviewQueueItem> items = new ArrayList<>();
        final int perStatusLimit = limit + offset;

        addScenes(items, VerificationStatus.UNVERIFIED, perStatusLimit);
        addScenes(items, VerificationStatus.NEEDS_REVIEW, perStatusLimit);

        return items.stream()
                .sorted(Comparator
                        .comparing(
                                SceneReviewQueueItem::title,
                                String.CASE_INSENSITIVE_ORDER
                        )
                        .thenComparing(item -> item.sceneId().toString()))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    private void addScenes(
            List<SceneReviewQueueItem> items,
            VerificationStatus status,
            int limit) throws SQLException {

        for (Scene scene : sceneRepository.findByVerificationStatus(
                status,
                limit,
                0
        )) {
            items.add(new SceneReviewQueueItem(
                    scene.getId(),
                    scene.getTitle(),
                    scene.getVerificationStatus(),
                    scene.getReleaseDate(),
                    scene.getPublisher().getName(),
                    scene.getSeries() == null
                            ? ""
                            : scene.getSeries().getTitle(),
                    scene.getFiles().size()
            ));
        }
    }
}

package service;

import model.MediaFile;
import model.Movie;
import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Scene;
import model.Series;
import model.VerificationStatus;
import repository.MediaFileRepository;
import repository.MovieRepository;
import repository.PerformerRepository;
import repository.PublisherRepository;
import repository.SceneRepository;
import repository.SearchRepository;
import repository.SeriesRepository;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class CatalogService {
    private final PublisherRepository publisherRepository;
    private final PerformerRepository performerRepository;
    private final SeriesRepository seriesRepository;
    private final MediaFileRepository mediaFileRepository;
    private final SceneRepository sceneRepository;
    private final SearchRepository searchRepository;
    private final MovieRepository movieRepository;

    public CatalogService(
            PublisherRepository publisherRepository,
            PerformerRepository performerRepository,
            SeriesRepository seriesRepository,
            MediaFileRepository mediaFileRepository,
            SceneRepository sceneRepository,
            SearchRepository searchRepository) {

        this(
                publisherRepository,
                performerRepository,
                seriesRepository,
                mediaFileRepository,
                sceneRepository,
                searchRepository,
                null
        );
    }

    public CatalogService(
            PublisherRepository publisherRepository,
            PerformerRepository performerRepository,
            SeriesRepository seriesRepository,
            MediaFileRepository mediaFileRepository,
            SceneRepository sceneRepository,
            SearchRepository searchRepository,
            MovieRepository movieRepository) {

        this.publisherRepository = Objects.requireNonNull(
                publisherRepository,
                "Publisher repository must not be null"
        );
        this.performerRepository = Objects.requireNonNull(
                performerRepository,
                "Performer repository must not be null"
        );
        this.seriesRepository = Objects.requireNonNull(
                seriesRepository,
                "Series repository must not be null"
        );
        this.mediaFileRepository = Objects.requireNonNull(
                mediaFileRepository,
                "Media file repository must not be null"
        );
        this.sceneRepository = Objects.requireNonNull(
                sceneRepository,
                "Scene repository must not be null"
        );
        this.searchRepository = Objects.requireNonNull(
                searchRepository,
                "Search repository must not be null"
        );
        this.movieRepository = movieRepository;
    }

    public Publisher createPublisher(
            String name,
            List<String> aliases) throws SQLException {

        final Publisher publisher = new Publisher(
                UUID.randomUUID(),
                normalizeRequiredText(name, "Publisher name"),
                normalizeAliases(aliases)
        );

        publisherRepository.insert(publisher);

        return publisher;
    }

    public Performer createPerformer(
            String mainName,
            List<String> aliases,
            PerformerCategory category) throws SQLException {

        if (category == null) {
            throw new IllegalArgumentException(
                    "Performer category must not be null."
            );
        }

        final Performer performer = new Performer(
                UUID.randomUUID(),
                normalizeRequiredText(mainName, "Performer main name"),
                normalizeAliases(aliases),
                category
        );

        performerRepository.insert(performer);

        return performer;
    }

    public Scene createScene(
            String title,
            String code,
            LocalDate releaseDate,
            UUID publisherId,
            UUID seriesId,
            String season,
            String episode,
            List<UUID> performerIds,
            List<UUID> mediaFileIds) throws SQLException {

        final Publisher publisher =
                findRequiredPublisher(publisherId);
        final Series series = findOptionalSeries(seriesId);
        final List<Performer> performers =
                findPerformers(performerIds);
        final List<MediaFile> mediaFiles =
                findMediaFiles(mediaFileIds);

        final Scene scene = new Scene(
                UUID.randomUUID(),
                normalizeRequiredText(title, "Scene title"),
                publisher,
                releaseDate,
                normalizeOptionalText(code),
                series,
                normalizeOptionalText(season),
                normalizeOptionalText(episode),
                performers,
                mediaFiles
        );

        sceneRepository.insert(scene);

        return scene;
    }

    public List<Scene> findScenesByPerformer(UUID performerId)
            throws SQLException {

        if (performerId == null) {
            throw new IllegalArgumentException(
                    "Performer ID must not be null."
            );
        }

        return searchRepository.findScenesByPerformer(performerId);
    }

    public List<Publisher> listPublishers() throws SQLException {
        return publisherRepository.findAll();
    }

    public Optional<Publisher> findPublisherById(UUID id)
            throws SQLException {

        requireId(id, "Publisher ID");
        return publisherRepository.findById(id);
    }

    public List<Performer> listPerformers() throws SQLException {
        return performerRepository.findAll();
    }

    public Optional<Performer> findPerformerById(UUID id)
            throws SQLException {

        requireId(id, "Performer ID");
        return performerRepository.findById(id);
    }

    public List<Scene> listScenes() throws SQLException {
        return sceneRepository.findAll();
    }

    public Optional<Scene> findSceneById(UUID id) throws SQLException {
        requireId(id, "Scene ID");
        return sceneRepository.findById(id);
    }

    public Scene markSceneVerified(UUID sceneId) throws SQLException {
        return markSceneVerificationStatus(sceneId, VerificationStatus.VERIFIED);
    }

    public Scene markSceneUnverified(UUID sceneId) throws SQLException {
        return markSceneVerificationStatus(
                sceneId,
                VerificationStatus.UNVERIFIED
        );
    }

    public Scene markSceneNeedsReview(UUID sceneId) throws SQLException {
        return markSceneVerificationStatus(
                sceneId,
                VerificationStatus.NEEDS_REVIEW
        );
    }

    public List<Scene> findScenesByVerificationStatus(
            VerificationStatus status,
            int limit,
            int offset) throws SQLException {

        if (status == null) {
            throw new IllegalArgumentException(
                    "Verification status must not be null."
            );
        }

        return sceneRepository.findByVerificationStatus(status, limit, offset);
    }

    public Series createSeries(String title, UUID publisherId)
            throws SQLException {

        final Series series = new Series(
                UUID.randomUUID(),
                normalizeRequiredText(title, "Series title"),
                findRequiredPublisher(publisherId)
        );

        seriesRepository.insert(series);

        return series;
    }

    public List<Series> listSeries() throws SQLException {
        return seriesRepository.findAll();
    }

    public Optional<Series> findSeriesById(UUID id) throws SQLException {
        requireId(id, "Series ID");
        return seriesRepository.findById(id);
    }

    public MediaFile createMediaFile(
            Path path,
            long fileSize,
            Duration duration,
            int width,
            int height,
            String contentHash,
            long lastModifiedMillis) throws SQLException {

        if (path == null) {
            throw new IllegalArgumentException(
                    "Media file path must not be null."
            );
        }

        final MediaFile mediaFile = new MediaFile(
                UUID.randomUUID(),
                path,
                fileSize,
                contentHash,
                duration,
                width,
                height,
                lastModifiedMillis
        );

        mediaFileRepository.insert(mediaFile);

        return mediaFile;
    }

    public MediaFile createMediaFile(
            Path path,
            long fileSize,
            Duration duration,
            int width,
            int height,
            String contentHash) throws SQLException {

        return createMediaFile(
                path,
                fileSize,
                duration,
                width,
                height,
                contentHash,
                0L
        );
    }

    public List<MediaFile> listMediaFiles() throws SQLException {
        return mediaFileRepository.findAll();
    }

    public Optional<MediaFile> findMediaFileById(UUID id)
            throws SQLException {

        requireId(id, "Media file ID");
        return mediaFileRepository.findById(id);
    }

    public Movie createMovie(
            String title,
            LocalDate releaseDate,
            UUID publisherId,
            boolean compilation,
            List<UUID> sceneIds,
            List<UUID> mediaFileIds) throws SQLException {

        ensureMovieRepository();

        final Movie movie = new Movie(
                UUID.randomUUID(),
                normalizeRequiredText(title, "Movie title"),
                releaseDate,
                findRequiredPublisher(publisherId),
                findScenes(sceneIds),
                compilation,
                findMediaFiles(mediaFileIds)
        );

        movieRepository.insert(movie);

        return movie;
    }

    public List<Movie> listMovies() throws SQLException {
        ensureMovieRepository();
        return movieRepository.findAll();
    }

    public Optional<Movie> findMovieById(UUID id) throws SQLException {
        ensureMovieRepository();
        requireId(id, "Movie ID");
        return movieRepository.findById(id);
    }

    private String normalizeRequiredText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank."
            );
        }

        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        String normalized = null;

        if (value != null) {
            final String trimmed = value.trim();

            if (!trimmed.isEmpty()) {
                normalized = trimmed;
            }
        }

        return normalized;
    }

    private List<String> normalizeAliases(List<String> aliases) {
        final TreeMap<String, String> normalizedAliases = new TreeMap<>();

        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null) {
                    final String trimmedAlias = alias.trim();

                    if (!trimmedAlias.isEmpty()) {
                        normalizedAliases.putIfAbsent(
                                trimmedAlias.toLowerCase(Locale.ROOT),
                                trimmedAlias
                        );
                    }
                }
            }
        }

        return normalizedAliases.values()
                .stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Publisher findRequiredPublisher(UUID publisherId)
            throws SQLException {

        if (publisherId == null) {
            throw new IllegalArgumentException(
                    "Publisher ID must not be null."
            );
        }

        return publisherRepository.findById(publisherId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Publisher ID does not reference an existing publisher: "
                                + publisherId
                ));
    }

    private Series findOptionalSeries(UUID seriesId) throws SQLException {
        Series series = null;

        if (seriesId != null) {
            series = seriesRepository.findById(seriesId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Series ID does not reference an existing series: "
                                    + seriesId
                    ));
        }

        return series;
    }

    private List<Scene> findScenes(List<UUID> sceneIds)
            throws SQLException {

        final List<UUID> ids =
                validateRelationshipIds(sceneIds, "Scene ID");
        final List<Scene> scenes = new ArrayList<>();

        for (UUID sceneId : ids) {
            scenes.add(sceneRepository.findById(sceneId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Scene ID does not reference an existing scene: "
                                    + sceneId
                    )));
        }

        return scenes;
    }

    private List<Performer> findPerformers(List<UUID> performerIds)
            throws SQLException {

        final List<UUID> ids =
                validateRelationshipIds(performerIds, "Performer ID");
        final List<Performer> performers = new ArrayList<>();

        for (UUID performerId : ids) {
            performers.add(performerRepository.findById(performerId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Performer ID does not reference an existing performer: "
                                    + performerId
                    )));
        }

        return performers;
    }

    private List<MediaFile> findMediaFiles(List<UUID> mediaFileIds)
            throws SQLException {

        final List<UUID> ids =
                validateRelationshipIds(mediaFileIds, "Media file ID");
        final List<MediaFile> mediaFiles = new ArrayList<>();

        for (UUID mediaFileId : ids) {
            mediaFiles.add(mediaFileRepository.findById(mediaFileId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Media file ID does not reference an existing media file: "
                                    + mediaFileId
                    )));
        }

        return mediaFiles;
    }

    private Scene markSceneVerificationStatus(
            UUID sceneId,
            VerificationStatus status) throws SQLException {

        requireId(sceneId, "Scene ID");

        final Scene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Scene ID does not reference an existing scene: "
                                + sceneId
                ));
        scene.setVerificationStatus(status);
        sceneRepository.update(scene);

        return sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Scene ID does not reference an existing scene: "
                                + sceneId
                ));
    }

    private List<UUID> validateRelationshipIds(
            List<UUID> ids,
            String fieldName) {

        final List<UUID> normalizedIds = new ArrayList<>();
        final Set<UUID> seenIds = new LinkedHashSet<>();

        if (ids != null) {
            for (UUID id : ids) {
                if (id == null) {
                    throw new IllegalArgumentException(
                            fieldName + " collection must not contain null IDs."
                    );
                }

                final boolean added = seenIds.add(id);

                if (!added) {
                    throw new IllegalArgumentException(
                            fieldName + " collection must not contain duplicate IDs."
                    );
                }

                normalizedIds.add(id);
            }
        }

        return normalizedIds;
    }

    private void requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(
                    fieldName + " must not be null."
            );
        }
    }

    private void ensureMovieRepository() {
        if (movieRepository == null) {
            throw new IllegalStateException(
                    "Movie repository is required for movie operations."
            );
        }
    }
}

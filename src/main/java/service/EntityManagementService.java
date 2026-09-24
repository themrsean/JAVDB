package service;

import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Series;
import model.Movie;
import repository.PerformerRepository;
import repository.PublisherRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class EntityManagementService {
    private final CatalogService catalogService;
    private final PublisherRepository publisherRepository;
    private final PerformerRepository performerRepository;

    public EntityManagementService(
            CatalogService catalogService,
            PublisherRepository publisherRepository,
            PerformerRepository performerRepository) {

        this.catalogService = Objects.requireNonNull(
                catalogService,
                "Catalog service must not be null"
        );
        this.publisherRepository = Objects.requireNonNull(
                publisherRepository,
                "Publisher repository must not be null"
        );
        this.performerRepository = Objects.requireNonNull(
                performerRepository,
                "Performer repository must not be null"
        );
    }

    public Publisher createPublisher(String name, List<String> aliases)
            throws SQLException {

        return catalogService.createPublisher(name, aliases);
    }

    public Performer createPerformer(
            String mainName,
            List<String> aliases,
            PerformerCategory category) throws SQLException {

        return catalogService.createPerformer(mainName, aliases, category);
    }

    public Series createSeries(String title, UUID publisherId)
            throws SQLException {

        return catalogService.createSeries(title, publisherId);
    }

    public Movie createMovie(
            String title,
            LocalDate releaseDate,
            UUID publisherId,
            boolean compilation) throws SQLException {

        return catalogService.createMovie(
                title,
                releaseDate,
                publisherId,
                compilation,
                List.of(),
                List.of()
        );
    }

    public Publisher addPublisherAlias(UUID publisherId, String alias)
            throws SQLException {

        final Publisher publisher = publisherRepository.findById(
                requireId(publisherId, "Publisher ID")
        ).orElseThrow(() -> new IllegalArgumentException(
                "Publisher ID does not reference an existing publisher: "
                        + publisherId
        ));
        final List<String> aliases = aliasesWithAdded(
                publisher.getName(),
                publisher.getAliases(),
                alias
        );
        publisher.setAliases(aliases);
        publisherRepository.update(publisher);

        return publisherRepository.findById(publisherId).orElseThrow();
    }

    public Performer addPerformerAlias(UUID performerId, String alias)
            throws SQLException {

        final Performer performer = performerRepository.findById(
                requireId(performerId, "Performer ID")
        ).orElseThrow(() -> new IllegalArgumentException(
                "Performer ID does not reference an existing performer: "
                        + performerId
        ));
        final List<String> aliases = aliasesWithAdded(
                performer.getMainName(),
                performer.getAliases(),
                alias
        );
        performer.setAliases(aliases);
        performerRepository.update(performer);

        return performerRepository.findById(performerId).orElseThrow();
    }

    private List<String> aliasesWithAdded(
            String primaryName,
            List<String> existingAliases,
            String alias) {

        if (alias == null || alias.trim().isEmpty()) {
            throw new IllegalArgumentException("Alias must not be blank.");
        }

        final String normalizedAlias = alias.trim();

        if (primaryName.equalsIgnoreCase(normalizedAlias)) {
            throw new IllegalArgumentException(
                    "Alias must differ from the primary name."
            );
        }

        for (String existingAlias : existingAliases) {
            if (existingAlias.equalsIgnoreCase(normalizedAlias)) {
                throw new IllegalArgumentException(
                        "Alias already exists: " + normalizedAlias
                );
            }
        }

        final List<String> aliases = new ArrayList<>(existingAliases);
        aliases.add(normalizedAlias);
        aliases.sort(Comparator.comparing(
                value -> value.toLowerCase(Locale.ROOT)
        ));

        return List.copyOf(aliases);
    }

    private UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " must not be null.");
        }

        return id;
    }
}

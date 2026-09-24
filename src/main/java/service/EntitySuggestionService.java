package service;

import repository.EntitySuggestion;
import repository.EntitySuggestionRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class EntitySuggestionService {
    private final EntitySuggestionRepository repository;

    public EntitySuggestionService(EntitySuggestionRepository repository) {
        this.repository = Objects.requireNonNull(
                repository,
                "Entity suggestion repository must not be null"
        );
    }

    public List<EntitySuggestion> suggestPerformers(String query, int limit)
            throws SQLException {

        return repository.suggestPerformers(query, limit);
    }

    public List<EntitySuggestion> suggestPublishers(String query, int limit)
            throws SQLException {

        return repository.suggestPublishers(query, limit);
    }

    public List<EntitySuggestion> suggestSeries(
            String query,
            UUID publisherId,
            int limit) throws SQLException {

        return repository.suggestSeries(query, publisherId, limit);
    }

    public List<EntitySuggestion> suggestMovies(
            String query,
            UUID publisherId,
            int limit) throws SQLException {

        return repository.suggestMovies(query, publisherId, limit);
    }
}

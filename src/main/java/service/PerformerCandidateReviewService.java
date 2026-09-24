package service;

import media.FilenameParseStatus;
import media.MediaFilenameParser;
import model.Performer;
import model.PerformerCategory;
import repository.EntitySuggestionRepository;
import repository.MatchField;
import repository.MatchRank;
import repository.UnassignedMediaPathRepository;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PerformerCandidateReviewService {
    private static final int REPRESENTATIVE_LIMIT = 3;
    private final UnassignedMediaPathRepository pathRepository;
    private final MediaFilenameParser parser;
    private final EntitySuggestionRepository suggestionRepository;
    private final EntityManagementService entityManagementService;

    public PerformerCandidateReviewService(
            UnassignedMediaPathRepository pathRepository,
            MediaFilenameParser parser,
            EntitySuggestionRepository suggestionRepository,
            EntityManagementService entityManagementService) {
        this.pathRepository = Objects.requireNonNull(pathRepository);
        this.parser = Objects.requireNonNull(parser);
        this.suggestionRepository = Objects.requireNonNull(suggestionRepository);
        this.entityManagementService = Objects.requireNonNull(entityManagementService);
    }

    public List<PerformerCandidate> loadCandidates() throws SQLException {
        final Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (Path path : pathRepository.findAll()) {
            final var parsed = parser.parse(path);
            // Invalid filenames may expose partial segments; do not bootstrap from them.
            if (parsed.status() != FilenameParseStatus.VALID) continue;
            final java.util.Set<String> inFile = new java.util.HashSet<>();
            for (String candidate : parsed.performerCandidates()) {
                final String key = candidate.toLowerCase(Locale.ROOT);
                if (inFile.add(key)) {
                    aggregates.computeIfAbsent(key, ignored -> new Aggregate(candidate))
                            .add(path);
                }
            }
        }
        final List<PerformerCandidate> result = new ArrayList<>();
        for (Aggregate aggregate : aggregates.values()) result.add(resolve(aggregate));
        return result.stream().sorted(java.util.Comparator
                .comparing((PerformerCandidate value) -> value.resolution()
                        == PerformerCandidateResolution.UNRESOLVED ? 0 : 1)
                .thenComparing(PerformerCandidate::text,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Performer createPerformer(String candidate) throws SQLException {
        return entityManagementService.createPerformer(candidate, List.of(),
                PerformerCategory.UNKNOWN);
    }

    public Performer mapAlias(String candidate, UUID performerId) throws SQLException {
        return entityManagementService.addPerformerAlias(performerId, candidate);
    }

    private PerformerCandidate resolve(Aggregate aggregate) throws SQLException {
        final var suggestions = suggestionRepository.suggestPerformers(
                aggregate.text, repository.SuggestionQuery.MAXIMUM_LIMIT);
        final var exact = suggestions.stream().filter(item -> item.rank()
                == MatchRank.PRIMARY_EXACT || item.rank() == MatchRank.ALIAS_EXACT).toList();
        if (exact.size() == 1) {
            final var item = exact.getFirst();
            return new PerformerCandidate(aggregate.text, aggregate.count,
                    item.matchField() == MatchField.PRIMARY
                            ? PerformerCandidateResolution.PRIMARY_MATCH
                            : PerformerCandidateResolution.ALIAS_MATCH,
                    item.id(), item.displayName(), aggregate.paths);
        }
        return new PerformerCandidate(aggregate.text, aggregate.count,
                PerformerCandidateResolution.UNRESOLVED, null, "", aggregate.paths);
    }

    private static final class Aggregate {
        private final String text;
        private int count;
        private final List<Path> paths = new ArrayList<>();
        private Aggregate(String text) { this.text = text; }
        private void add(Path path) { count++; if (paths.size() < REPRESENTATIVE_LIMIT) paths.add(path); }
    }
}

package service;

import media.FilenameParseStatus;
import media.MediaFilenameParser;
import repository.EntitySuggestion;
import repository.EntitySuggestionRepository;
import repository.MatchRank;
import repository.PublisherRepository;
import repository.SuggestionQuery;
import repository.UnassignedMediaPathRepository;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Aggregates role-neutral context segments without writing to the catalog. */
public final class ContextCandidateReviewService implements ContextCandidateSource {
    private static final int REPRESENTATIVE_LIMIT = 3;

    private final UnassignedMediaPathRepository pathRepository;
    private final MediaFilenameParser parser;
    private final EntitySuggestionRepository suggestionRepository;
    private final PublisherRepository publisherRepository;

    public ContextCandidateReviewService(
            UnassignedMediaPathRepository pathRepository,
            MediaFilenameParser parser,
            EntitySuggestionRepository suggestionRepository,
            PublisherRepository publisherRepository) {
        this.pathRepository = Objects.requireNonNull(pathRepository);
        this.parser = Objects.requireNonNull(parser);
        this.suggestionRepository = Objects.requireNonNull(suggestionRepository);
        this.publisherRepository = Objects.requireNonNull(publisherRepository);
    }

    public List<ContextCandidate> loadCandidates() throws SQLException {
        final Map<String, Aggregate> aggregates = new LinkedHashMap<>();
        for (Path path : pathRepository.findAll()) {
            final var parsed = parser.parse(path);
            // A partial parse is deliberately not evidence for catalog work.
            if (parsed.status() != FilenameParseStatus.VALID) {
                continue;
            }
            final List<String> segments = parsed.contextSegments();
            final Set<String> candidatesInFile = new HashSet<>();
            for (int index = 0; index < segments.size(); index++) {
                final String text = segments.get(index);
                final String key = text.toLowerCase(Locale.ROOT);
                if (candidatesInFile.add(key)) {
                    aggregates.computeIfAbsent(key, ignored -> new Aggregate(text))
                            .add(index + 1, segments.size(), path);
                }
            }
        }

        final Map<UUID, String> publisherNames = new HashMap<>();
        final List<ContextCandidate> result = new ArrayList<>();
        for (Aggregate aggregate : aggregates.values()) {
            result.add(resolve(aggregate, publisherNames));
        }
        return result.stream().sorted(Comparator
                .comparing((ContextCandidate candidate) ->
                        candidate.status().needsAttention() ? 0 : 1)
                .thenComparing(ContextCandidate::occurrences,
                        Comparator.reverseOrder())
                .thenComparing(ContextCandidate::text,
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ContextCandidate::text))
                .toList();
    }

    /** Reuses the review's exact-match semantics immediately before an action. */
    public ContextCandidateStatus currentStatus(String candidate)
            throws SQLException {
        if (candidate == null || candidate.trim().isEmpty()) {
            throw new IllegalArgumentException("Context candidate must not be blank.");
        }
        return resolve(new Aggregate(candidate.trim()), new HashMap<>()).status();
    }

    private ContextCandidate resolve(
            Aggregate aggregate,
            Map<UUID, String> publisherNames) throws SQLException {
        final List<ContextCandidateMatch> matches = new ArrayList<>();
        addMatches(matches, ContextCandidateRole.PUBLISHER,
                exact(suggestionRepository.suggestPublishers(
                        aggregate.text, SuggestionQuery.MAXIMUM_LIMIT)),
                publisherNames);
        addMatches(matches, ContextCandidateRole.SERIES,
                exact(suggestionRepository.suggestSeries(
                        aggregate.text, null, SuggestionQuery.MAXIMUM_LIMIT)),
                publisherNames);
        addMatches(matches, ContextCandidateRole.MOVIE,
                exact(suggestionRepository.suggestMovies(
                        aggregate.text, null, SuggestionQuery.MAXIMUM_LIMIT)),
                publisherNames);
        return new ContextCandidate(aggregate.text, aggregate.occurrences,
                aggregate.positionCounts, aggregate.contextLengthCounts,
                statusFor(matches), matches, aggregate.paths);
    }

    private void addMatches(List<ContextCandidateMatch> matches,
            ContextCandidateRole role, List<EntitySuggestion> suggestions,
            Map<UUID, String> publisherNames) throws SQLException {
        for (EntitySuggestion suggestion : suggestions) {
            final UUID publisherId = suggestion.publisherId();
            String publisherName = null;
            if (publisherId != null) {
                if (!publisherNames.containsKey(publisherId)) {
                    publisherNames.put(publisherId, publisherRepository
                            .findById(publisherId).map(model.Publisher::getName)
                            .orElse("Unknown publisher"));
                }
                publisherName = publisherNames.get(publisherId);
            }
            matches.add(new ContextCandidateMatch(role, suggestion.id(),
                    suggestion.displayName(), publisherId, publisherName));
        }
    }

    private List<EntitySuggestion> exact(List<EntitySuggestion> suggestions) {
        return suggestions.stream().filter(suggestion ->
                suggestion.rank() == MatchRank.PRIMARY_EXACT
                        || suggestion.rank() == MatchRank.ALIAS_EXACT).toList();
    }

    private ContextCandidateStatus statusFor(
            List<ContextCandidateMatch> matches) {
        if (matches.isEmpty()) return ContextCandidateStatus.UNRESOLVED;
        if (matches.size() > 1) return ContextCandidateStatus.MULTIPLE_ROLE_MATCHES;
        return switch (matches.getFirst().role()) {
            case PUBLISHER -> ContextCandidateStatus.PUBLISHER_MATCH;
            case SERIES -> ContextCandidateStatus.SERIES_MATCH;
            case MOVIE -> ContextCandidateStatus.MOVIE_MATCH;
        };
    }

    private static final class Aggregate {
        private final String text;
        private int occurrences;
        private final Map<Integer, Integer> positionCounts = new LinkedHashMap<>();
        private final Map<Integer, Integer> contextLengthCounts = new LinkedHashMap<>();
        private final List<Path> paths = new ArrayList<>();
        private Aggregate(String text) { this.text = text; }
        private void add(int position, int contextLength, Path path) {
            occurrences++;
            positionCounts.merge(position, 1, Integer::sum);
            contextLengthCounts.merge(contextLength, 1, Integer::sum);
            if (paths.size() < REPRESENTATIVE_LIMIT) paths.add(path);
        }
    }
}

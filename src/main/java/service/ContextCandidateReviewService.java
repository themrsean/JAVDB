package service;

import media.FilenameParseStatus;
import media.MediaFilenameParser;
import repository.EntitySuggestion;
import repository.EntitySuggestionRepository;
import repository.MatchRank;
import repository.PublisherRepository;
import repository.SuggestionQuery;
import repository.UnassignedMediaPathRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
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
        final Map<String, ResolvedSegment> resolutionCache = new HashMap<>();
        final Map<UUID, String> publisherNames = new HashMap<>();
        for (Path path : pathRepository.findAll()) {
            if (!Files.exists(path)) {
                continue;
            }
            final var parsed = parser.parse(path);
            // A partial parse is deliberately not evidence for catalog work.
            if (parsed.status() != FilenameParseStatus.VALID) {
                continue;
            }
            final List<String> segments = parsed.contextSegments();
            final Map<String, Integer> candidatesInFile = new LinkedHashMap<>();
            for (int index = 0; index < segments.size(); index++) {
                final String text = segments.get(index);
                final String key = normalize(text);
                if (!candidatesInFile.containsKey(key)) {
                    candidatesInFile.put(key, index);
                    aggregates.computeIfAbsent(key, ignored -> new Aggregate(text))
                            .add(index + 1, segments.size(), path);
                }
            }
            final List<ResolvedSegment> resolvedSegments = new ArrayList<>();
            for (String segment : segments) {
                resolvedSegments.add(resolveSegment(segment, resolutionCache,
                        publisherNames));
            }
            for (Map.Entry<String, Integer> candidate : candidatesInFile.entrySet()) {
                final Map<UUID, RelationshipInFile> relationships = new HashMap<>();
                final int candidateIndex = candidate.getValue();
                for (int index = 0; index < segments.size(); index++) {
                    if (index == candidateIndex
                            || normalize(segments.get(index)).equals(candidate.getKey())) {
                        continue;
                    }
                    for (PublisherReference reference : resolvedSegments.get(index)
                            .publisherReferences()) {
                        relationships.computeIfAbsent(reference.publisherId(),
                                ignored -> new RelationshipInFile(reference.publisherName()))
                                .add(reference.role(), index > candidateIndex,
                                        index < candidateIndex);
                    }
                }
                aggregates.get(candidate.getKey()).addPublisherEvidence(relationships);
            }
        }

        final List<ContextCandidate> result = new ArrayList<>();
        for (Aggregate aggregate : aggregates.values()) {
            result.add(toCandidate(aggregate, resolveSegment(aggregate.text,
                    resolutionCache, publisherNames)));
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
        return resolveSegment(candidate.trim(), new HashMap<>(), new HashMap<>())
                .status();
    }

    private ContextCandidate toCandidate(Aggregate aggregate,
            ResolvedSegment resolved) {
        return new ContextCandidate(aggregate.text, aggregate.occurrences,
                aggregate.positionCounts, aggregate.contextLengthCounts,
                resolved.status(), resolved.matches(), aggregate.publisherEvidence(),
                aggregate.paths);
    }

    private ResolvedSegment resolveSegment(String text,
            Map<String, ResolvedSegment> resolutionCache,
            Map<UUID, String> publisherNames) throws SQLException {
        final String key = normalize(text);
        final ResolvedSegment cached = resolutionCache.get(key);
        if (cached != null) return cached;
        final List<ContextCandidateMatch> matches = new ArrayList<>();
        final List<PublisherReference> publisherReferences = new ArrayList<>();
        addMatches(matches, ContextCandidateRole.PUBLISHER,
                exact(suggestionRepository.suggestPublishers(
                        text, SuggestionQuery.MAXIMUM_LIMIT)), publisherNames,
                publisherReferences);
        addMatches(matches, ContextCandidateRole.SERIES,
                exact(suggestionRepository.suggestSeries(
                        text, null, SuggestionQuery.MAXIMUM_LIMIT)), publisherNames,
                publisherReferences);
        addMatches(matches, ContextCandidateRole.MOVIE,
                exact(suggestionRepository.suggestMovies(
                        text, null, SuggestionQuery.MAXIMUM_LIMIT)), publisherNames,
                publisherReferences);
        final ResolvedSegment resolved = new ResolvedSegment(statusFor(matches),
                matches, publisherReferences);
        resolutionCache.put(key, resolved);
        return resolved;
    }

    private void addMatches(List<ContextCandidateMatch> matches,
            ContextCandidateRole role, List<EntitySuggestion> suggestions,
            Map<UUID, String> publisherNames,
            List<PublisherReference> publisherReferences) throws SQLException {
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
            if (role == ContextCandidateRole.PUBLISHER) {
                publisherReferences.add(new PublisherReference(suggestion.id(),
                        suggestion.displayName(), role));
            } else if (publisherId != null) {
                publisherReferences.add(new PublisherReference(publisherId,
                        publisherName, role));
            }
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

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private record ResolvedSegment(ContextCandidateStatus status,
            List<ContextCandidateMatch> matches,
            List<PublisherReference> publisherReferences) {
        private ResolvedSegment {
            matches = List.copyOf(matches);
            publisherReferences = List.copyOf(publisherReferences);
        }
    }

    private record PublisherReference(UUID publisherId, String publisherName,
            ContextCandidateRole role) { }

    private static final class RelationshipInFile {
        private final String publisherName;
        private boolean candidateBeforePublisher;
        private boolean candidateAfterPublisher;
        private final Set<ContextCandidateRole> sourceRoles =
                EnumSet.noneOf(ContextCandidateRole.class);

        private RelationshipInFile(String publisherName) {
            this.publisherName = publisherName;
        }

        private void add(ContextCandidateRole role, boolean before, boolean after) {
            sourceRoles.add(role);
            candidateBeforePublisher |= before;
            candidateAfterPublisher |= after;
        }
    }

    private static final class Aggregate {
        private final String text;
        private int occurrences;
        private final Map<Integer, Integer> positionCounts = new LinkedHashMap<>();
        private final Map<Integer, Integer> contextLengthCounts = new LinkedHashMap<>();
        private final List<Path> paths = new ArrayList<>();
        private final Map<UUID, PublisherEvidenceAggregate> publisherEvidence =
                new HashMap<>();
        private Aggregate(String text) { this.text = text; }
        private void add(int position, int contextLength, Path path) {
            occurrences++;
            positionCounts.merge(position, 1, Integer::sum);
            contextLengthCounts.merge(contextLength, 1, Integer::sum);
            if (paths.size() < REPRESENTATIVE_LIMIT) paths.add(path);
        }
        private void addPublisherEvidence(
                Map<UUID, RelationshipInFile> relationships) {
            for (Map.Entry<UUID, RelationshipInFile> relationship
                    : relationships.entrySet()) {
                publisherEvidence.computeIfAbsent(relationship.getKey(), ignored ->
                        new PublisherEvidenceAggregate(relationship.getValue().publisherName))
                        .add(relationship.getValue());
            }
        }
        private List<ContextPublisherEvidence> publisherEvidence() {
            return publisherEvidence.entrySet().stream().map(entry ->
                    entry.getValue().toEvidence(entry.getKey())).sorted(Comparator
                    .comparing(ContextPublisherEvidence::affectedFiles,
                            Comparator.reverseOrder())
                    .thenComparing(ContextPublisherEvidence::publisherName,
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(ContextPublisherEvidence::publisherName)
                    .thenComparing(ContextPublisherEvidence::publisherId))
                    .toList();
        }
    }

    private static final class PublisherEvidenceAggregate {
        private final String publisherName;
        private int affectedFiles;
        private int candidateBeforePublisherFiles;
        private int candidateAfterPublisherFiles;
        private int publisherOnBothSidesFiles;
        private int directPublisherFiles;
        private int seriesPublisherFiles;
        private int moviePublisherFiles;

        private PublisherEvidenceAggregate(String publisherName) {
            this.publisherName = publisherName;
        }

        private void add(RelationshipInFile relationship) {
            affectedFiles++;
            if (relationship.candidateBeforePublisher) candidateBeforePublisherFiles++;
            if (relationship.candidateAfterPublisher) candidateAfterPublisherFiles++;
            if (relationship.candidateBeforePublisher
                    && relationship.candidateAfterPublisher) {
                publisherOnBothSidesFiles++;
            }
            if (relationship.sourceRoles.contains(ContextCandidateRole.PUBLISHER)) {
                directPublisherFiles++;
            }
            if (relationship.sourceRoles.contains(ContextCandidateRole.SERIES)) {
                seriesPublisherFiles++;
            }
            if (relationship.sourceRoles.contains(ContextCandidateRole.MOVIE)) {
                moviePublisherFiles++;
            }
        }

        private ContextPublisherEvidence toEvidence(UUID publisherId) {
            return new ContextPublisherEvidence(publisherId, publisherName,
                    affectedFiles, candidateBeforePublisherFiles,
                    candidateAfterPublisherFiles, publisherOnBothSidesFiles,
                    directPublisherFiles, seriesPublisherFiles, moviePublisherFiles);
        }
    }
}

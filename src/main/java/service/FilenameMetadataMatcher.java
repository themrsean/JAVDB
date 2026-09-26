package service;

import media.FilenameParseStatus;
import media.ParsedMediaFilename;
import repository.EntitySuggestion;
import repository.EntitySuggestionRepository;
import repository.MatchField;
import repository.MatchRank;
import repository.PublisherRepository;
import repository.SuggestionQuery;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class FilenameMetadataMatcher {
    private static final int ENTITY_EXACT_SCORE = 100;
    private static final int ALIAS_EXACT_SCORE = 90;
    private static final int RELATIONSHIP_SCORE = 50;
    private static final int INFERRED_PENALTY = 10;
    private static final int MAXIMUM_CONTEXT_SEGMENTS = 3;

    private final EntitySuggestionRepository suggestionRepository;
    private final PublisherRepository publisherRepository;

    public FilenameMetadataMatcher(
            EntitySuggestionRepository suggestionRepository,
            PublisherRepository publisherRepository) {

        this.suggestionRepository = Objects.requireNonNull(
                suggestionRepository,
                "Entity suggestion repository must not be null"
        );
        this.publisherRepository = Objects.requireNonNull(
                publisherRepository,
                "Publisher repository must not be null"
        );
    }

    public FilenameMatchResult match(ParsedMediaFilename parsed)
            throws SQLException {

        Objects.requireNonNull(parsed, "Parsed filename must not be null");

        final FilenameMatchResult result;

        if (parsed.status() == FilenameParseStatus.INVALID) {
            result = new FilenameMatchResult(
                    parsed,
                    FilenameMatchStatus.INVALID_FILENAME,
                    null,
                    List.of(),
                    parsed.warnings(),
                    parsed.issues().stream().map(Enum::name).toList()
            );
        } else {
            result = matchValid(parsed);
        }

        return result;
    }

    private FilenameMatchResult matchValid(ParsedMediaFilename parsed)
            throws SQLException {

        final List<EntityMatch> performerMatches =
                matchPerformers(parsed.performerCandidates());
        final List<FilenameInterpretation> interpretations =
                generateInterpretations(parsed, performerMatches, new HashMap<>());
        final List<FilenameInterpretation> sortedInterpretations =
                interpretations.stream()
                        .sorted(Comparator
                                .comparingInt(FilenameInterpretation::score)
                                .reversed()
                                .thenComparing(this::sortKey))
                        .toList();
        final FilenameInterpretation best = sortedInterpretations.isEmpty()
                ? null
                : sortedInterpretations.getFirst();
        final FilenameMatchStatus status = status(
                parsed,
                performerMatches,
                sortedInterpretations
        );

        return new FilenameMatchResult(
                parsed,
                status,
                best,
                sortedInterpretations,
                parsed.warnings(),
                List.of()
        );
    }

    private List<EntityMatch> matchPerformers(List<String> candidates)
            throws SQLException {

        final List<EntityMatch> matches = new ArrayList<>();

        for (String candidate : candidates) {
            final List<EntitySuggestion> exactSuggestions =
                    exactSuggestions(suggestionRepository.suggestPerformers(
                            candidate,
                            SuggestionQuery.MAXIMUM_LIMIT
                    ));

            if (exactSuggestions.size() == 1) {
                matches.add(toMatch(candidate, exactSuggestions.getFirst()));
            } else {
                matches.add(new EntityMatch(
                        null,
                        null,
                        candidate,
                        MatchSource.UNMATCHED,
                        null
                ));
            }
        }

        return matches;
    }

    private List<FilenameInterpretation> generateInterpretations(
            ParsedMediaFilename parsed,
            List<EntityMatch> performerMatches,
            Map<UUID, String> publisherNames) throws SQLException {

        final List<FilenameInterpretation> interpretations = new ArrayList<>();
        final List<String> context = parsed.contextSegments();

        if (context.size() <= MAXIMUM_CONTEXT_SEGMENTS) {
            addInterpretation(interpretations, parsed, performerMatches, publisherNames,
                    roleSet("PUBLISHER"));
            addInterpretation(interpretations, parsed, performerMatches, publisherNames,
                    roleSet("SERIES"));
            addInterpretation(interpretations, parsed, performerMatches, publisherNames,
                    roleSet("MOVIE"));
            addInterpretation(interpretations, parsed, performerMatches, publisherNames,
                    roleSet("PUBLISHER", "SERIES"));
            addInterpretation(interpretations, parsed, performerMatches, publisherNames,
                    roleSet("PUBLISHER", "MOVIE"));
            addInterpretation(interpretations, parsed, performerMatches, publisherNames,
                    roleSet("SERIES", "MOVIE"));
            addInterpretation(interpretations, parsed, performerMatches, publisherNames,
                    roleSet("PUBLISHER", "SERIES", "MOVIE"));
        }

        return List.copyOf(interpretations);
    }

    private List<String> roleSet(String... roles) {
        return List.of(roles);
    }

    private void addInterpretation(
            List<FilenameInterpretation> interpretations,
            ParsedMediaFilename parsed,
            List<EntityMatch> performerMatches,
            Map<UUID, String> publisherNames,
            List<String> roles) throws SQLException {

        if (roles.size() == parsed.contextSegments().size()) {
            EntityMatch publisher = absent();
            EntityMatch series = absent();
            EntityMatch movie = absent();
            int score = 0;
            boolean valid = true;
            boolean exactAnchor = false;

            int index = 0;

            while (index < roles.size() && valid) {
                final String role = roles.get(index);
                final String segment = parsed.contextSegments().get(index);

                if ("PUBLISHER".equals(role)) {
                    publisher = exactPublisher(segment);
                    exactAnchor = exactAnchor || publisher.id() != null;
                    score = score + score(publisher);
                } else if ("SERIES".equals(role)) {
                    series = exactSeries(segment, publisher.id());
                    exactAnchor = exactAnchor || series.id() != null;
                    score = score + score(series);
                } else if ("MOVIE".equals(role)) {
                    movie = exactMovie(segment, publisher.id());
                    exactAnchor = exactAnchor || movie.id() != null;
                    score = score + score(movie);
                }

                index++;
            }

            valid = valid && exactAnchor;

            if (valid) {
                if (publisher.source() == MatchSource.ABSENT
                        && series.id() != null
                        && series.publisherId() != null) {
                    publisher = inferredPublisher(series.publisherId(),
                            MatchSource.INFERRED_FROM_SERIES, publisherNames);
                    score = score - INFERRED_PENALTY;
                }

                if (publisher.source() == MatchSource.ABSENT
                        && movie.id() != null
                        && movie.publisherId() != null) {
                    publisher = inferredPublisher(movie.publisherId(),
                            MatchSource.INFERRED_FROM_MOVIE, publisherNames);
                    score = score - INFERRED_PENALTY;
                }

                if (publisher.id() != null && series.publisherId() != null
                        && publisher.id().equals(series.publisherId())) {
                    score = score + RELATIONSHIP_SCORE;
                } else if (publisher.id() != null
                        && series.publisherId() != null) {
                    valid = false;
                }

                if (publisher.id() != null && movie.publisherId() != null
                        && publisher.id().equals(movie.publisherId())) {
                    score = score + RELATIONSHIP_SCORE;
                } else if (publisher.id() != null
                        && movie.publisherId() != null) {
                    valid = false;
                }
            }

            if (valid) {
                final List<String> unresolvedSegments = List.of(
                                publisher,
                                series,
                                movie
                        ).stream()
                        .filter(match -> match.source() == MatchSource.UNMATCHED)
                        .map(EntityMatch::candidateText)
                        .filter(Objects::nonNull)
                        .toList();
                interpretations.add(new FilenameInterpretation(
                        publisher,
                        series,
                        movie,
                        performerMatches,
                        unresolvedSegments,
                        score
                ));
            }
        }
    }

    private EntityMatch inferredPublisher(UUID publisherId, MatchSource source,
            Map<UUID, String> publisherNames) throws SQLException {

        String name = publisherNames.get(publisherId);
        if (name == null) {
            name = publisherRepository.findById(publisherId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Publisher not found for inferred relationship: "
                                    + publisherId))
                    .getName();
            publisherNames.put(publisherId, name);
        }
        return new EntityMatch(publisherId, name, null, source, null);
    }

    private EntityMatch exactPublisher(String candidate) throws SQLException {
        return exactEntity(candidate, null,
                suggestionRepository.suggestPublishers(
                candidate,
                SuggestionQuery.MAXIMUM_LIMIT
        ));
    }

    private EntityMatch exactSeries(String candidate, UUID publisherId)
            throws SQLException {

        EntityMatch match = exactEntity(candidate, publisherId,
                suggestionRepository.suggestSeries(
                candidate,
                publisherId,
                SuggestionQuery.MAXIMUM_LIMIT
        ));
        if (publisherId != null && match.id() == null) {
            final EntityMatch global = exactEntity(candidate, null,
                    suggestionRepository.suggestSeries(
                            candidate,
                            null,
                            SuggestionQuery.MAXIMUM_LIMIT
                    ));
            if (global.id() != null) {
                match = global;
            }
        }
        return match;
    }

    private EntityMatch exactMovie(String candidate, UUID publisherId)
            throws SQLException {

        EntityMatch match = exactEntity(candidate, publisherId,
                suggestionRepository.suggestMovies(
                candidate,
                publisherId,
                SuggestionQuery.MAXIMUM_LIMIT
        ));
        if (publisherId != null && match.id() == null) {
            final EntityMatch global = exactEntity(candidate, null,
                    suggestionRepository.suggestMovies(
                            candidate,
                            null,
                            SuggestionQuery.MAXIMUM_LIMIT
                    ));
            if (global.id() != null) {
                match = global;
            }
        }
        return match;
    }

    private EntityMatch exactEntity(
            String candidate,
            UUID publisherId,
            List<EntitySuggestion> suggestions) {

        final List<EntitySuggestion> exactSuggestions =
                exactSuggestions(suggestions);
        final EntityMatch match;

        if (exactSuggestions.size() == 1) {
            match = toMatch(candidate, exactSuggestions.getFirst());
        } else {
            match = new EntityMatch(
                    null,
                    null,
                    candidate,
                    MatchSource.UNMATCHED,
                    publisherId
            );
        }

        return match;
    }

    private List<EntitySuggestion> exactSuggestions(
            List<EntitySuggestion> suggestions) {

        return suggestions.stream()
                .filter(suggestion -> suggestion.rank()
                        == MatchRank.PRIMARY_EXACT
                        || suggestion.rank() == MatchRank.ALIAS_EXACT)
                .toList();
    }

    private EntityMatch toMatch(
            String candidate,
            EntitySuggestion suggestion) {

        return new EntityMatch(
                suggestion.id(),
                suggestion.displayName(),
                candidate,
                suggestion.matchField() == MatchField.PRIMARY
                        ? MatchSource.EXPLICIT_PRIMARY_NAME
                        : MatchSource.EXPLICIT_ALIAS,
                suggestion.publisherId()
        );
    }

    private EntityMatch absent() {
        return new EntityMatch(null, null, null, MatchSource.ABSENT, null);
    }

    private int score(EntityMatch match) {
        int score = 0;

        if (match.source() == MatchSource.EXPLICIT_PRIMARY_NAME) {
            score = ENTITY_EXACT_SCORE;
        } else if (match.source() == MatchSource.EXPLICIT_ALIAS) {
            score = ALIAS_EXACT_SCORE;
        }

        return score;
    }

    private FilenameMatchStatus status(
            ParsedMediaFilename parsed,
            List<EntityMatch> performerMatches,
            List<FilenameInterpretation> interpretations) {

        FilenameMatchStatus status = FilenameMatchStatus.READY;

        if (performerMatches.stream().anyMatch(match -> match.id() == null)) {
            status = FilenameMatchStatus.UNRESOLVED;
        } else if (interpretations.isEmpty()) {
            status = parsed.contextSegments().isEmpty()
                    ? FilenameMatchStatus.REVIEW_REQUIRED
                    : FilenameMatchStatus.UNRESOLVED;
        } else if (hasCrossEntityAmbiguity(parsed)
                || hasMaterialTopTie(interpretations)) {
            status = FilenameMatchStatus.AMBIGUOUS;
        } else if (!interpretations.getFirst().unresolvedSegments().isEmpty()) {
            status = FilenameMatchStatus.UNRESOLVED;
        }

        return status;
    }

    private boolean hasMaterialTopTie(
            List<FilenameInterpretation> interpretations) {

        return interpretations.size() > 1
                && interpretations.getFirst().score()
                == interpretations.get(1).score()
                && !interpretationKey(interpretations.getFirst()).equals(
                        interpretationKey(interpretations.get(1))
                );
    }

    private String interpretationKey(FilenameInterpretation interpretation) {
        return matchKey(interpretation.publisher()) + "|"
                + matchKey(interpretation.series()) + "|"
                + matchKey(interpretation.movie());
    }

    private String matchKey(EntityMatch match) {
        return match.source() + ":" + match.id() + ":" + match.candidateText();
    }

    private boolean hasCrossEntityAmbiguity(ParsedMediaFilename parsed) {
        boolean ambiguous = false;

        if (parsed.contextSegments().size() == 1) {
            final String segment = parsed.contextSegments().getFirst();
            try {
                final boolean publisher = !exactSuggestions(
                        suggestionRepository.suggestPublishers(
                                segment,
                                SuggestionQuery.MAXIMUM_LIMIT
                        )
                ).isEmpty();
                final boolean series = !exactSuggestions(
                        suggestionRepository.suggestSeries(
                                segment,
                                null,
                                SuggestionQuery.MAXIMUM_LIMIT
                        )
                ).isEmpty();
                final boolean movie = !exactSuggestions(
                        suggestionRepository.suggestMovies(
                                segment,
                                null,
                                SuggestionQuery.MAXIMUM_LIMIT
                        )
                ).isEmpty();
                final int matches = (publisher ? 1 : 0)
                        + (series ? 1 : 0)
                        + (movie ? 1 : 0);
                ambiguous = matches > 1;
            } catch (SQLException exception) {
                ambiguous = false;
            }
        }

        return ambiguous;
    }

    private String sortKey(FilenameInterpretation interpretation) {
        return String.valueOf(interpretation.publisher().id())
                + interpretation.series().id()
                + interpretation.movie().id();
    }
}

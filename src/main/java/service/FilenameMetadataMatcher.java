package service;

import media.FilenameParseStatus;
import media.ParsedMediaFilename;
import repository.EntitySuggestion;
import repository.EntitySuggestionRepository;
import repository.MatchField;
import repository.MatchRank;
import repository.SuggestionQuery;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class FilenameMetadataMatcher {
    private static final int ENTITY_EXACT_SCORE = 100;
    private static final int ALIAS_EXACT_SCORE = 90;
    private static final int RELATIONSHIP_SCORE = 50;
    private static final int INFERRED_PENALTY = 10;
    private static final int MAXIMUM_CONTEXT_SEGMENTS = 3;

    private final EntitySuggestionRepository suggestionRepository;

    public FilenameMetadataMatcher(
            EntitySuggestionRepository suggestionRepository) {

        this.suggestionRepository = Objects.requireNonNull(
                suggestionRepository,
                "Entity suggestion repository must not be null"
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
                generateInterpretations(parsed, performerMatches);
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
            List<EntityMatch> performerMatches) throws SQLException {

        final List<FilenameInterpretation> interpretations = new ArrayList<>();
        final List<String> context = parsed.contextSegments();

        if (context.size() <= MAXIMUM_CONTEXT_SEGMENTS) {
            addInterpretation(interpretations, parsed, performerMatches,
                    roleSet("PUBLISHER"));
            addInterpretation(interpretations, parsed, performerMatches,
                    roleSet("SERIES"));
            addInterpretation(interpretations, parsed, performerMatches,
                    roleSet("MOVIE"));
            addInterpretation(interpretations, parsed, performerMatches,
                    roleSet("PUBLISHER", "SERIES"));
            addInterpretation(interpretations, parsed, performerMatches,
                    roleSet("PUBLISHER", "MOVIE"));
            addInterpretation(interpretations, parsed, performerMatches,
                    roleSet("SERIES", "MOVIE"));
            addInterpretation(interpretations, parsed, performerMatches,
                    roleSet("PUBLISHER", "SERIES", "MOVIE"));
        }

        return interpretations.stream()
                .filter(interpretation -> interpretation.unresolvedSegments()
                        .isEmpty())
                .toList();
    }

    private List<String> roleSet(String... roles) {
        return List.of(roles);
    }

    private void addInterpretation(
            List<FilenameInterpretation> interpretations,
            ParsedMediaFilename parsed,
            List<EntityMatch> performerMatches,
            List<String> roles) throws SQLException {

        if (roles.size() == parsed.contextSegments().size()) {
            EntityMatch publisher = absent();
            EntityMatch series = absent();
            EntityMatch movie = absent();
            int score = 0;
            boolean valid = true;

            int index = 0;

            while (index < roles.size() && valid) {
                final String role = roles.get(index);
                final String segment = parsed.contextSegments().get(index);

                if ("PUBLISHER".equals(role)) {
                    publisher = exactPublisher(segment);
                    valid = publisher.id() != null;
                    score = score + score(publisher);
                } else if ("SERIES".equals(role)) {
                    series = exactSeries(segment, null);
                    valid = series.id() != null;
                    score = score + score(series);
                } else if ("MOVIE".equals(role)) {
                    movie = exactMovie(segment, null);
                    valid = movie.id() != null;
                    score = score + score(movie);
                }

                index++;
            }

            if (valid) {
                if (publisher.id() == null && series.id() != null
                        && series.publisherId() != null) {
                    publisher = new EntityMatch(
                            series.publisherId(),
                            null,
                            null,
                            MatchSource.INFERRED_FROM_SERIES,
                            null
                    );
                    score = score - INFERRED_PENALTY;
                }

                if (publisher.id() == null && movie.id() != null
                        && movie.publisherId() != null) {
                    publisher = new EntityMatch(
                            movie.publisherId(),
                            null,
                            null,
                            MatchSource.INFERRED_FROM_MOVIE,
                            null
                    );
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
                interpretations.add(new FilenameInterpretation(
                        publisher,
                        series,
                        movie,
                        performerMatches,
                        List.of(),
                        score
                ));
            }
        }
    }

    private EntityMatch exactPublisher(String candidate) throws SQLException {
        return exactEntity(candidate, suggestionRepository.suggestPublishers(
                candidate,
                SuggestionQuery.MAXIMUM_LIMIT
        ));
    }

    private EntityMatch exactSeries(String candidate, UUID publisherId)
            throws SQLException {

        return exactEntity(candidate, suggestionRepository.suggestSeries(
                candidate,
                publisherId,
                SuggestionQuery.MAXIMUM_LIMIT
        ));
    }

    private EntityMatch exactMovie(String candidate, UUID publisherId)
            throws SQLException {

        return exactEntity(candidate, suggestionRepository.suggestMovies(
                candidate,
                publisherId,
                SuggestionQuery.MAXIMUM_LIMIT
        ));
    }

    private EntityMatch exactEntity(
            String candidate,
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
                    null
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
        } else if (interpretations.isEmpty()
                && !parsed.contextSegments().isEmpty()) {
            status = FilenameMatchStatus.UNRESOLVED;
        } else if (hasCrossEntityAmbiguity(parsed)
                || interpretations.size() > 1
                && interpretations.getFirst().score()
                == interpretations.get(1).score()) {
            status = FilenameMatchStatus.AMBIGUOUS;
        }

        return status;
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

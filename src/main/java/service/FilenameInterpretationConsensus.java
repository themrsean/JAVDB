package service;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Derives only metadata shared by every viable ambiguous interpretation. */
public final class FilenameInterpretationConsensus {
    private FilenameInterpretationConsensus() {
    }

    public static FilenameInterpretation initial(
            FilenameMatchStatus status,
            FilenameInterpretation best,
            List<FilenameInterpretation> interpretations) {

        if (status != FilenameMatchStatus.AMBIGUOUS || best == null) {
            return best;
        }

        return new FilenameInterpretation(
                commonMatch(interpretations, FilenameInterpretation::publisher),
                commonMatch(interpretations, FilenameInterpretation::series),
                commonMatch(interpretations, FilenameInterpretation::movie),
                commonPerformers(interpretations),
                commonUnresolvedSegments(interpretations),
                best.score()
        );
    }

    private static EntityMatch commonMatch(
            List<FilenameInterpretation> interpretations,
            Function<FilenameInterpretation, EntityMatch> accessor) {

        final EntityMatch first = accessor.apply(interpretations.getFirst());
        final boolean common = first != null && interpretations.stream()
                .map(accessor)
                .allMatch(value -> equivalent(first, value));
        return common ? first : absent();
    }

    private static boolean equivalent(EntityMatch first, EntityMatch second) {
        return second != null
                && Objects.equals(first.id(), second.id())
                && Objects.equals(first.candidateText(), second.candidateText())
                && first.source() == second.source();
    }

    private static List<EntityMatch> commonPerformers(
            List<FilenameInterpretation> interpretations) {

        final List<EntityMatch> first = interpretations.getFirst().performers();
        return interpretations.stream().allMatch(value ->
                value.performers().equals(first)) ? first : List.of();
    }

    private static List<String> commonUnresolvedSegments(
            List<FilenameInterpretation> interpretations) {

        return interpretations.getFirst().unresolvedSegments().stream()
                .filter(segment -> interpretations.stream().allMatch(value ->
                        value.unresolvedSegments().contains(segment)))
                .toList();
    }

    private static EntityMatch absent() {
        return new EntityMatch(null, null, null, MatchSource.ABSENT, null);
    }
}

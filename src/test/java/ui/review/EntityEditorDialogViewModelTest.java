package ui.review;

import model.Performer;
import model.PerformerCategory;
import model.Publisher;
import model.Series;
import model.Movie;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

class EntityEditorDialogViewModelTest {
    private static final UUID PUBLISHER_ID =
            UUID.fromString("11111111-eeee-1111-eeee-111111111111");

    @Test
    @DisplayName("Publisher dialog validates name and creates publisher")
    void publisherDialogValidatesNameAndCreatesPublisher() {
        final PublisherEditorDialogViewModel viewModel =
                new PublisherEditorDialogViewModel(
                        (name, aliases) -> new Publisher(
                                PUBLISHER_ID,
                                name,
                                aliases
                        ),
                        Runnable::run,
                        Runnable::run
                );

        viewModel.nameProperty().set(" Publisher ");
        viewModel.aliasesTextProperty().set("Alias One\nAlias Two");
        viewModel.save();

        Assertions.assertAll(
                () -> Assertions.assertNotNull(viewModel.resultProperty().get()),
                () -> Assertions.assertEquals(
                        List.of("Alias One", "Alias Two"),
                        viewModel.resultProperty().get().getAliases()
                ),
                () -> Assertions.assertFalse(viewModel.savingProperty().get())
        );
    }

    @Test
    @DisplayName("Blank publisher name disables save")
    void blankPublisherNameDisablesSave() {
        final PublisherEditorDialogViewModel viewModel =
                new PublisherEditorDialogViewModel(
                        (name, aliases) -> new Publisher(
                                PUBLISHER_ID,
                                name,
                                aliases
                        ),
                        Runnable::run,
                        Runnable::run
                );

        viewModel.nameProperty().set(" ");

        Assertions.assertFalse(viewModel.saveEnabledProperty().get());
    }

    @Test
    @DisplayName("Performer dialog creates performer")
    void performerDialogCreatesPerformer() {
        final PerformerEditorDialogViewModel viewModel =
                new PerformerEditorDialogViewModel(
                        (name, aliases, category) -> new Performer(
                                UUID.randomUUID(),
                                name,
                                aliases,
                                category
                        ),
                        Runnable::run,
                        Runnable::run
                );

        viewModel.nameProperty().set(" Alice ");
        viewModel.aliasesTextProperty().set("Ally");
        viewModel.categoryProperty().set(PerformerCategory.UNKNOWN);
        viewModel.save();

        Assertions.assertAll(
                () -> Assertions.assertNotNull(viewModel.resultProperty().get()),
                () -> Assertions.assertEquals(
                        PerformerCategory.UNKNOWN,
                        viewModel.resultProperty().get().getCategory()
                )
        );
    }

    @Test
    @DisplayName("Series dialog requires title and publisher")
    void seriesDialogRequiresTitleAndPublisher() {
        final SeriesEditorDialogViewModel viewModel =
                new SeriesEditorDialogViewModel(
                        (title, publisherId) -> new Series(
                                UUID.randomUUID(),
                                title,
                                new Publisher(publisherId, "Publisher", List.of())
                        ),
                        Runnable::run,
                        Runnable::run
                );

        viewModel.titleProperty().set("Series");

        Assertions.assertFalse(viewModel.saveEnabledProperty().get());

        viewModel.publisherIdProperty().set(PUBLISHER_ID);
        viewModel.save();

        Assertions.assertNotNull(viewModel.resultProperty().get());
    }

    @Test
    @DisplayName("Movie dialog creates movie")
    void movieDialogCreatesMovie() {
        final MovieEditorDialogViewModel viewModel =
                new MovieEditorDialogViewModel(
                        (title, releaseDate, publisherId, compilation) ->
                                new Movie(
                                        UUID.randomUUID(),
                                        title,
                                        releaseDate,
                                        new Publisher(
                                                publisherId,
                                                "Publisher",
                                                List.of()
                                        ),
                                        List.of(),
                                        compilation,
                                        List.of()
                                ),
                        Runnable::run,
                        Runnable::run
                );

        viewModel.titleProperty().set("Movie");
        viewModel.releaseDateTextProperty().set("2026-07-16");
        viewModel.publisherIdProperty().set(PUBLISHER_ID);
        viewModel.compilationProperty().set(true);
        viewModel.save();

        Assertions.assertAll(
                () -> Assertions.assertNotNull(viewModel.resultProperty().get()),
                () -> Assertions.assertEquals(
                        LocalDate.of(2026, 7, 16),
                        viewModel.resultProperty().get().getReleaseDate()
                ),
                () -> Assertions.assertTrue(
                        viewModel.resultProperty().get().isCompilation()
                )
        );
    }
}

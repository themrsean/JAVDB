package ui.review;

import javafx.stage.Window;
import model.Movie;
import model.Performer;
import model.Publisher;
import model.Series;

import java.util.Optional;

public interface EntityDialogLauncher {
    Optional<Publisher> createPublisher(Window owner);

    default Optional<Publisher> createPublisher(
            Window owner,
            String initialName,
            PublisherEditorDialogViewModel.Creator creator) {
        return createPublisher(owner);
    }

    Optional<Performer> createPerformer(Window owner);

    Optional<Series> createSeries(Window owner);

    default Optional<Series> createSeries(
            Window owner,
            String initialTitle,
            SeriesEditorDialogViewModel.Creator creator) {
        return createSeries(owner);
    }

    Optional<Movie> createMovie(Window owner);
}

package ui.control;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

import java.util.Objects;
import java.util.function.Function;

/** Type-safe table cell factories for immutable record-backed table rows. */
public final class RecordTableCellValues {
    private RecordTableCellValues() { }

    public static <R> Callback<TableColumn.CellDataFeatures<R, String>,
            ObservableValue<String>> string(Function<R, String> extractor) {
        Objects.requireNonNull(extractor, "Extractor must not be null");
        return cell -> new ReadOnlyStringWrapper(
                nullToEmpty(extractor.apply(cell.getValue()))
        );
    }

    public static <R, V> Callback<TableColumn.CellDataFeatures<R, V>,
            ObservableValue<V>> object(Function<R, V> extractor) {
        Objects.requireNonNull(extractor, "Extractor must not be null");
        return cell -> new ReadOnlyObjectWrapper<>(extractor.apply(cell.getValue()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package ui.performer;
import javafx.fxml.FXMLLoader; import javafx.scene.Scene; import javafx.stage.Modality; import javafx.stage.Stage; import javafx.stage.Window;
import java.io.IOException; import java.util.Objects;
public final class JavaFxPerformerCandidateWindowLauncher implements PerformerCandidateWindowLauncher {
 private final PerformerCandidateViewModel viewModel; private final ui.control.EntityAutocompleteViewModel autocomplete; private Stage stage;
 public JavaFxPerformerCandidateWindowLauncher(PerformerCandidateViewModel viewModel, ui.control.EntityAutocompleteViewModel autocomplete) { this.viewModel=Objects.requireNonNull(viewModel); this.autocomplete=Objects.requireNonNull(autocomplete); }
 public void open(Window owner) { try { if(stage==null||!stage.isShowing()) create(owner); stage.show(); stage.toFront(); } catch(IOException e){throw new IllegalStateException("Unable to open Performer Candidate Review.",e);} }
 private void create(Window owner)throws IOException { var url=getClass().getResource("/ui/performer-candidates.fxml"); if(url==null)throw new IOException("GUI resource not found"); var loader=new FXMLLoader(url); loader.setControllerFactory(type->{if(type.equals(PerformerCandidateController.class))return new PerformerCandidateController(viewModel, autocomplete);throw new IllegalArgumentException();}); javafx.scene.Parent root=loader.load(); stage=new Stage();stage.setTitle("Performer Candidate Review");stage.initModality(Modality.NONE);if(owner!=null)stage.initOwner(owner);stage.setScene(new Scene(root,1000,620));stage.setOnShown(e->viewModel.load()); }
}

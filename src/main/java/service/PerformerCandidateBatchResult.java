package service;
import java.util.List;
public record PerformerCandidateBatchResult(int selected, int created, int skipped, List<String> failures) {
 public PerformerCandidateBatchResult { failures=List.copyOf(failures); }
 public String summary(){return "Selected: "+selected+"; created: "+created+"; skipped already resolved: "+skipped+"; failed: "+failures.size()+".";}
}

package service;

import java.sql.SQLException;
import java.util.List;

@FunctionalInterface
public interface ContextCandidateSource {
    List<ContextCandidate> loadCandidates() throws SQLException;
}

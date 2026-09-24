package service;

import repository.MediaLibraryFilter;

import java.sql.SQLException;
import java.util.UUID;

public interface MediaLibraryDataSource {
    MediaLibraryPage loadPage(MediaLibraryFilter filter) throws SQLException;

    MediaLibraryDetails loadDetails(UUID mediaId) throws SQLException;
}

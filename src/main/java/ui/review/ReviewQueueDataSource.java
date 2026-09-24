package ui.review;

import service.ReviewQueueFilter;
import service.ReviewQueuePage;

import java.sql.SQLException;

public interface ReviewQueueDataSource {
    ReviewQueuePage loadPage(ReviewQueueFilter filter) throws SQLException;
}

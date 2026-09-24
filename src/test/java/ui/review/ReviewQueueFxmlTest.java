package ui.review;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Set;

class ReviewQueueFxmlTest {
    private static final String FXML_RESOURCE = "/ui/review-queue.fxml";
    private static final String FX_NAMESPACE =
            "http://javafx.com/fxml";

    @Test
    @DisplayName("FXML resource exists and declares controller")
    void fxmlResourceExistsAndDeclaresController() throws Exception {
        final Document document = document();

        Assertions.assertEquals(
                "ui.review.ReviewQueueController",
                document.getDocumentElement()
                        .getAttributeNS(FX_NAMESPACE, "controller")
        );
    }

    @Test
    @DisplayName("FXML contains required controls")
    void fxmlContainsRequiredControls() throws Exception {
        final Document document = document();
        final Set<String> expectedIds = Set.of(
                "pathFilterField",
                "directoryFilterField",
                "widthFilterField",
                "heightFilterField",
                "minWidthFilterField",
                "minHeightFilterField",
                "applyFiltersButton",
                "clearFiltersButton",
                "statusFilterComboBox",
                "previousButton",
                "nextButton",
                "pageSizeComboBox",
                "refreshButton",
                "queueTable",
                "filenameColumn",
                "matchStatusColumn",
                "titleColumn",
                "publisherColumn",
                "seriesColumn",
                "movieColumn",
                "performersColumn",
                "resolutionColumn",
                "durationColumn",
                "warningCountColumn",
                "detailsPlaceholderLabel",
                "mediaIdLabel",
                "fullPathLabel",
                "parseStatusLabel",
                "matchStatusLabel",
                "canonicalStatusLabel",
                "proposedFilenameLabel",
                "errorLabel",
                "loadingIndicator",
                "databasePathLabel",
                "mediaLocationsMenuItem",
                "scanAllMediaLocationsMenuItem",
                "scanMediaButton",
                "scanResultsNotificationBox",
                "scanResultsNotificationLabel",
                "refreshMediaQueueButton"
        );

        for (String expectedId : expectedIds) {
            Assertions.assertTrue(
                    hasFxId(document, expectedId),
                    "Missing fx:id " + expectedId
            );
        }
    }

    @Test
    @DisplayName("FXML contains editable review workflow controls")
    void fxmlContainsEditableReviewWorkflowControls() throws Exception {
        final Document document = document();
        final Set<String> expectedIds = Set.of(
                "reviewTabs",
                "unassignedMediaTab",
                "unverifiedScenesTab",
                "unverifiedScenesTable",
                "sceneTitleColumn",
                "sceneVerificationStatusColumn",
                "titleEditorField",
                "releaseDateEditorField",
                "codeEditorField",
                "seasonEditorField",
                "episodeEditorField",
                "publisherSearchField",
                "publisherSuggestionsList",
                "seriesSearchField",
                "seriesSuggestionsList",
                "movieSearchField",
                "movieSuggestionsList",
                "performerSearchField",
                "performerSuggestionsList",
                "selectedPerformersList",
                "alternativesList",
                "applyInterpretationButton",
                "createPublisherButton",
                "createSeriesButton",
                "createMovieButton",
                "createPerformerButton",
                "saveWithoutRenameButton",
                "saveAndRenameButton",
                "saveNeedsReviewButton",
                "skipButton",
                "resetChangesButton",
                "editorValidationLabel",
                "editorResultLabel"
        );

        for (String expectedId : expectedIds) {
            Assertions.assertTrue(
                    hasFxId(document, expectedId),
                    "Missing fx:id " + expectedId
            );
        }
    }

    private Document document() throws Exception {
        final InputStream input =
                ReviewQueueFxmlTest.class.getResourceAsStream(FXML_RESOURCE);
        Assertions.assertNotNull(input);

        try (input) {
            final DocumentBuilderFactory factory =
                    DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private boolean hasFxId(Document document, String fxId) {
        final NodeList nodes = document.getElementsByTagName("*");
        boolean found = false;

        for (int index = 0; index < nodes.getLength(); index++) {
            final Node idNode = nodes.item(index)
                    .getAttributes()
                    .getNamedItemNS(FX_NAMESPACE, "id");

            if (idNode != null && fxId.equals(idNode.getNodeValue())) {
                found = true;
            }
        }

        return found;
    }
}

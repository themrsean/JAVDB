package ui.media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Set;

class MediaLocationsFxmlTest {
    private static final String FXML_RESOURCE = "/ui/media-locations.fxml";
    private static final String FX_NAMESPACE =
            "http://javafx.com/fxml";

    @Test
    @DisplayName("FXML resource exists and declares controller")
    void fxmlResourceExistsAndDeclaresController() throws Exception {
        final Document document = document();

        Assertions.assertEquals(
                "ui.media.MediaLocationsController",
                document.getDocumentElement()
                        .getAttributeNS(FX_NAMESPACE, "controller")
        );
    }

    @Test
    @DisplayName("FXML contains required controls")
    void fxmlContainsRequiredControls() throws Exception {
        final Document document = document();
        final Set<String> expectedIds = Set.of(
                "locationsTable",
                "directoryColumn",
                "enabledColumn",
                "recursiveColumn",
                "lastScanColumn",
                "statusColumn",
                "newColumn",
                "updatedColumn",
                "unchangedColumn",
                "missingColumn",
                "failedColumn",
                "addFolderButton",
                "emptyAddFolderButton",
                "removeButton",
                "scanSelectedButton",
                "scanAllButton",
                "cancelScanButton",
                "closeButton",
                "emptyStateLabel",
                "phaseLabel",
                "currentDirectoryLabel",
                "currentFileLabel",
                "currentFilePathLabel",
                "locationPositionLabel",
                "discoveredCountLabel",
                "processedCountLabel",
                "newCountLabel",
                "updatedCountLabel",
                "unchangedCountLabel",
                "missingCountLabel",
                "failedCountLabel",
                "summaryLabel",
                "errorLabel",
                "scanProgressIndicator"
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
                MediaLocationsFxmlTest.class.getResourceAsStream(
                        FXML_RESOURCE
                );
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

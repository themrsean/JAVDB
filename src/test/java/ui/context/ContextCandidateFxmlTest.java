package ui.context;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.List;

class ContextCandidateFxmlTest {
    @Test
    void exposesReadOnlyCandidateEvidenceAndFilters() throws Exception {
        final Document document;
        try (InputStream input = getClass().getResourceAsStream(
                "/ui/context-candidates.fxml")) {
            Assertions.assertNotNull(input);
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            document = factory.newDocumentBuilder().parse(input);
        }
        for (String id : List.of("candidatesTable", "candidateColumn", "countColumn",
                "positionColumn", "statusColumn", "matchesColumn", "examplesColumn",
                "attentionOnlyCheckBox", "candidateFilterField", "refreshButton",
                "createPublisherButton", "createSeriesButton", "createMovieButton",
                "mapPublisherAliasButton",
                "publisherSearchField", "publisherSuggestionsList",
                "emptyLabel", "errorLabel", "resultLabel")) {
            Assertions.assertTrue(hasFxId(document, id), "Missing fx:id " + id);
        }
    }

    private boolean hasFxId(Document document, String expected) {
        final NodeList nodes = document.getElementsByTagName("*");
        for (int index = 0; index < nodes.getLength(); index++) {
            final var value = nodes.item(index).getAttributes()
                    .getNamedItemNS("http://javafx.com/fxml", "id");
            if (value != null && expected.equals(value.getNodeValue())) return true;
        }
        return false;
    }
}

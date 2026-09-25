package ui.performer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

class PerformerCandidateFxmlTest {
    private static final String FXML_RESOURCE = "/ui/performer-candidates.fxml";
    private static final String FX_NAMESPACE = "http://javafx.com/fxml";

    @Test
    void fxmlWiresCategoryValidationAndActionControls() throws Exception {
        final Document document = document();
        final Set<String> ids = Set.of("categoryComboBox", "minimumCountField",
                "minimumCountValidationLabel", "createButton",
                "createSelectedButton", "mapAliasButton", "performerSearchField");

        for (String id : ids) {
            Assertions.assertTrue(hasFxId(document, id), "Missing fx:id " + id);
        }
        Assertions.assertEquals("Create Performer", textFor(document, "createButton"));
    }

    @Test
    void fxmlKeepsFilteringActionsAndLookupOnThreeSeparateRows()
            throws Exception {
        final Document document = document();

        Assertions.assertAll(
                () -> Assertions.assertEquals(List.of("unresolvedOnlyCheckBox",
                        "candidateFilterField", "minimumCountField"),
                        childrenWithFxIds(document, "candidateFilterRow")),
                () -> Assertions.assertEquals(List.of("categoryComboBox",
                        "refreshButton", "createButton", "createSelectedButton",
                        "mapAliasButton"), childrenWithFxIds(document,
                                "categoryActionsRow")),
                () -> Assertions.assertEquals(List.of("performerSearchField"),
                        childrenWithFxIds(document, "performerLookupRow"))
        );
    }

    private Document document() throws Exception {
        final InputStream input = PerformerCandidateFxmlTest.class
                .getResourceAsStream(FXML_RESOURCE);
        Assertions.assertNotNull(input);
        try (input) {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private boolean hasFxId(Document document, String id) {
        return elementFor(document, id) != null;
    }

    private String textFor(Document document, String id) {
        return elementFor(document, id).getAttribute("text");
    }

    private List<String> childrenWithFxIds(Document document, String parentId) {
        final NodeList nodes = elementFor(document, parentId).getChildNodes();
        final java.util.ArrayList<String> ids = new java.util.ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            final Node node = nodes.item(index);
            if (node instanceof Element element) {
                final String id = element.getAttributeNS(FX_NAMESPACE, "id");
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private Element elementFor(Document document, String id) {
        final NodeList nodes = document.getElementsByTagName("*");
        Element result = null;
        for (int index = 0; index < nodes.getLength(); index++) {
            final Node node = nodes.item(index);
            if (node instanceof Element element && id.equals(
                    element.getAttributeNS(FX_NAMESPACE, "id"))) {
                result = element;
            }
        }
        return result;
    }
}

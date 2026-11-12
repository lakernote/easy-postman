package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.ViewportClippedTokenPainter;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class RequestBodyPanelTest extends AbstractSwingUiTest {

    @Test
    public void shouldUseViewportClippedTokenPainterForLargeRequestBodies() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP));

        Field field = RSyntaxTextArea.class.getDeclaredField("tokenPainter");
        field.setAccessible(true);

        assertTrue(holder[0].getBodyArea() instanceof FallbackAwareRSyntaxTextArea);
        assertEquals(field.get(holder[0].getBodyArea()).getClass(), ViewportClippedTokenPainter.class);
    }

    @Test
    public void shouldDisableMatchedBracketPopupForRequestEditor() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];

        SwingUtilities.invokeAndWait(() -> holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP));

        assertFalse(holder[0].getBodyArea().getShowMatchedBracketPopup());
    }

    @Test
    public void rawBodyShouldPreserveUserWhitespace() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].getBodyArea().setText("  {\"a\":1}\n");
        });

        assertEquals(holder[0].getRawBody(), "  {\"a\":1}\n");
    }

    @Test
    public void httpBodyTypeComboShouldOfferBinary() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];

        SwingUtilities.invokeAndWait(() -> holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP));

        assertTrue(hasBodyType(holder[0], RequestBodyPanel.BODY_TYPE_BINARY));
    }

    @Test
    public void webSocketBodyShouldExposeRawTypeForContentIndicators() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];
        String[] bodyType = new String[1];
        String[] bodyContent = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.WEBSOCKET);
            holder[0].setRawBodyText("请问答复");
            bodyType[0] = holder[0].getBodyType();
            bodyContent[0] = holder[0].getBodyContent(bodyType[0]);
        });

        assertEquals(bodyType[0], RequestBodyPanel.BODY_TYPE_RAW);
        assertEquals(bodyContent[0], "请问答复");
    }

    @Test
    public void binaryFileSelectionShouldShowFileNameSizeAndTooltip() throws Exception {
        Path file = Files.createTempFile("easy-postman-binary-", ".bin");
        Files.write(file, new byte[]{1, 2, 3, 4});
        RequestBodyPanel[] holder = new RequestBodyPanel[1];
        String[] summaryText = new String[1];
        String[] tooltipText = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].setBinaryFilePath(file.toString());
            summaryText[0] = holder[0].getBinaryFileSummaryLabel().getText();
            tooltipText[0] = holder[0].getBinaryFilePathField().getToolTipText();
        });

        assertEquals(holder[0].getBinaryFilePath(), file.toString());
        assertEquals(tooltipText[0], file.toString());
        assertTrue(summaryText[0].contains(file.getFileName().toString()));
        assertTrue(summaryText[0].contains("4 B"));
    }

    @Test
    public void binaryFileSelectionShouldShowDetectedMimeType() throws Exception {
        Path file = Files.createTempFile("easy-postman-binary-", ".png");
        Files.write(file, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        RequestBodyPanel[] holder = new RequestBodyPanel[1];
        String[] summaryText = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].setBinaryFilePath(file.toString());
            summaryText[0] = holder[0].getBinaryFileSummaryLabel().getText();
        });

        assertTrue(summaryText[0].contains("image/png"), "Binary file summary should show detected MIME type: " + summaryText[0]);
    }

    @Test
    public void binaryClearButtonShouldResetPathAndStatus() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];
        String[] summaryText = new String[1];
        boolean[] clearEnabled = new boolean[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].setBinaryFilePath("/tmp/example.bin");
            holder[0].getBinaryClearButton().doClick();
            summaryText[0] = holder[0].getBinaryFileSummaryLabel().getText();
            clearEnabled[0] = holder[0].getBinaryClearButton().isEnabled();
        });

        assertEquals(holder[0].getBinaryFilePath(), "");
        assertEquals(summaryText[0], I18nUtil.getMessage(MessageKeys.REQUEST_BODY_BINARY_NO_FILE));
        assertFalse(clearEnabled[0]);
    }

    @Test
    public void binaryFilePathFieldShouldNotStretchAcrossWideEditor() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];
        int[] fieldWidth = new int[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].getBodyTypeComboBox().setSelectedItem(RequestBodyPanel.BODY_TYPE_BINARY);
            holder[0].setSize(2048, 300);
            layoutTree(holder[0]);
            fieldWidth[0] = holder[0].getBinaryFilePathField().getWidth();
        });

        assertTrue(fieldWidth[0] <= 720, "Binary file path field width should stay compact but was " + fieldWidth[0]);
    }

    @Test
    public void binaryFilePathFieldShouldKeepUsableWidthInNarrowEditor() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];
        int[] fieldWidth = new int[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].getBodyTypeComboBox().setSelectedItem(RequestBodyPanel.BODY_TYPE_BINARY);
            holder[0].setSize(480, 200);
            layoutTree(holder[0]);
            fieldWidth[0] = holder[0].getBinaryFilePathField().getWidth();
        });

        assertTrue(fieldWidth[0] >= 160, "Binary file path field collapsed to " + fieldWidth[0]);
    }

    @Test
    public void binaryFilePathFieldShouldStartNearPickerLeftEdge() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];
        int[] fieldX = new int[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].getBodyTypeComboBox().setSelectedItem(RequestBodyPanel.BODY_TYPE_BINARY);
            holder[0].setSize(1200, 300);
            layoutTree(holder[0]);
            fieldX[0] = holder[0].getBinaryFilePathField().getX();
        });

        assertTrue(fieldX[0] <= 4, "Binary file path field should not be offset by a redundant label but x was " + fieldX[0]);
    }

    @Test
    public void programmaticRawBodyLoadShouldNotBeUndoable() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].setRawBodyText("{\"loaded\":true}");
            triggerUndo(holder[0]);
        });

        assertEquals(holder[0].getRawBody(), "{\"loaded\":true}");
    }

    @Test
    public void userEditAfterProgrammaticRawBodyLoadShouldRemainUndoable() throws Exception {
        RequestBodyPanel[] holder = new RequestBodyPanel[1];

        SwingUtilities.invokeAndWait(() -> {
            holder[0] = new RequestBodyPanel(RequestItemProtocolEnum.HTTP);
            holder[0].setRawBodyText("{\"loaded\":true}");
            holder[0].getBodyArea().append("\n");
            triggerUndo(holder[0]);
        });

        assertEquals(holder[0].getRawBody(), "{\"loaded\":true}");
    }

    private static void triggerUndo(RequestBodyPanel panel) {
        Action action = panel.getBodyArea().getActionMap().get("Undo");
        action.actionPerformed(new ActionEvent(panel.getBodyArea(), ActionEvent.ACTION_PERFORMED, "Undo"));
    }

    private static boolean hasBodyType(RequestBodyPanel panel, String bodyType) {
        ComboBoxModel<String> model = panel.getBodyTypeComboBox().getModel();
        for (int i = 0; i < model.getSize(); i++) {
            if (bodyType.equals(model.getElementAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void layoutTree(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }
}

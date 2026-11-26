package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.button.DownloadButton;
import com.laker.postman.common.component.button.WrapToggleButton;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.file.ResponseFileFixtures;
import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.util.UiI18n;
import com.laker.postman.util.UiMessageKeys;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.testng.annotations.Test;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.awt.*;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class ResponseBodyPanelTest extends AbstractSwingUiTest {

    @Test
    public void shouldNotAutoEnableLineWrapForSmallJsonResponseWithVeryLongLine() throws Exception {
        HttpResponse response = responseWithBody(longLineJson());

        ResponseBodyPanel panel = createPanelWithResponse(response);

        assertFalse(panel.getResponseBodyPane().getLineWrap());
    }

    @Test
    public void shouldKeepManualLineWrapSelectionAcrossResponses() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("{\"ok\":true}"));
        WrapToggleButton wrapButton = findWrapButton(panel);
        assertNotNull(wrapButton);

        SwingUtilities.invokeAndWait(() -> {
            wrapButton.doClick();
            panel.setBodyText(responseWithBody(longLineJson()));
            panel.setBodyText(responseWithBody("{\"ok\":true}"));
        });

        assertTrue(panel.getResponseBodyPane().getLineWrap());
    }

    @Test
    public void shouldUseViewportClippedTokenPainterForResponseRendering() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody(longLineJson()));

        assertTrue(panel.getResponseBodyPane() instanceof FallbackAwareRSyntaxTextArea);
        Object tokenPainter = getTokenPainter(panel);

        assertEquals(tokenPainter.getClass().getName(),
                "com.laker.postman.common.component.ViewportClippedTokenPainter");
    }

    @Test
    public void shouldDisableMatchedBracketPopupForResponseEditor() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody(longLineJson()));

        assertFalse(panel.getResponseBodyPane().getShowMatchedBracketPopup());
    }

    @Test
    public void shouldExposeJsonCopyActionsInResponseEditorPopupMenu() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("{\"data\":\"value\"}"));

        JPopupMenu popupMenu = panel.getResponseBodyPane().getPopupMenu();

        assertTrue(hasMenuItem(popupMenu, UiI18n.get(UiMessageKeys.EDITOR_POPUP_COPY_KEY)));
        assertTrue(hasMenuItem(popupMenu, UiI18n.get(UiMessageKeys.EDITOR_POPUP_COPY_VALUE)));
    }

    @Test
    public void shouldUseCompactLocalizedResponseEditorPopupMenu() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("{\"data\":\"value\"}"));

        JPopupMenu popupMenu = panel.getResponseBodyPane().getPopupMenu();

        assertFalse(hasAnyMenuItem(popupMenu, "Undo", "Can't Redo", "Cut", "Paste", "Delete",
                "撤销", "无法恢复", "剪切", "粘贴", "删除"));
        assertTrue(hasMenuItem(popupMenu, UiI18n.get(UiMessageKeys.EDITOR_POPUP_COPY_SELECTED)));
        assertTrue(hasMenuItem(popupMenu, UiI18n.get(UiMessageKeys.EDITOR_POPUP_COPY_ALL)));
        assertTrue(hasMenuItem(popupMenu, UiI18n.get(UiMessageKeys.EDITOR_POPUP_SELECT_ALL)));
        assertTrue(hasMenuItem(popupMenu, UiI18n.get(UiMessageKeys.EDITOR_POPUP_FOLDING)));
    }

    @Test
    public void shouldKeepResponseEditorCopyAllMenuEnabledWithoutSelection() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("{\"data\":\"value\"}"));
        JPopupMenu popupMenu = panel.getResponseBodyPane().getPopupMenu();

        firePopupWillBecomeVisible(popupMenu);

        JMenuItem copyItem = findMenuItem(popupMenu, UiI18n.get(UiMessageKeys.EDITOR_POPUP_COPY_ALL));
        assertNotNull(copyItem);
        assertTrue(copyItem.isEnabled());
    }

    @Test
    public void shouldKeepResponseEditorCopySelectedMenuEnabledWithSelection() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("{\"data\":\"value\"}"));
        JPopupMenu popupMenu = panel.getResponseBodyPane().getPopupMenu();

        SwingUtilities.invokeAndWait(() -> panel.getResponseBodyPane().select(1, 7));
        firePopupWillBecomeVisible(popupMenu);

        JMenuItem copyItem = findMenuItem(popupMenu, UiI18n.get(UiMessageKeys.EDITOR_POPUP_COPY_SELECTED));
        assertNotNull(copyItem);
        assertTrue(copyItem.isEnabled());
    }

    @Test
    public void shouldShowAudioPlayerForAudioResponseWithoutLoadingCodecOnEdt() throws Exception {
        Path audioFile = Files.createTempFile("easy-postman-audio-preview-", ".wav");
        try {
            HttpResponse response = new HttpResponse();
            response.body = "[Binary response saved to a temporary file]";
            response.bodySize = 128;
            response.filePath = audioFile.toString();
            response.fileName = "sample.wav";
            response.headers = Map.of("Content-Type", List.of("audio/wav"));

            ResponseBodyPanel panel = createPanelWithResponse(response);
            MediaResponsePanel mediaPanel = findComponent(panel, MediaResponsePanel.class);

            assertNotNull(mediaPanel);
            assertTrue(mediaPanel.isVisible());
        } finally {
            Files.deleteIfExists(audioFile);
        }
    }

    @Test
    public void shouldReturnToTextCardAfterAudioResponse() throws Exception {
        Path audioFile = Files.createTempFile("easy-postman-audio-preview-", ".wav");
        try {
            HttpResponse audioResponse = new HttpResponse();
            audioResponse.bodySize = 128;
            audioResponse.filePath = audioFile.toString();
            audioResponse.fileName = "sample.wav";
            audioResponse.headers = Map.of("Content-Type", List.of("audio/wav"));
            ResponseBodyPanel panel = createPanelWithResponse(audioResponse);

            SwingUtilities.invokeAndWait(() -> panel.setBodyText(responseWithBody("{\"ok\":true}")));

            MediaResponsePanel mediaPanel = findComponent(panel, MediaResponsePanel.class);
            assertNotNull(mediaPanel);
            assertFalse(mediaPanel.isVisible());
            assertTrue(panel.getResponseBodyPane().isVisible());
        } finally {
            Files.deleteIfExists(audioFile);
        }
    }

    @Test
    public void shouldShowSystemPlayerCardForVideoResponse() throws Exception {
        Path videoFile = Files.createTempFile("easy-postman-video-preview-", ".mp4");
        try {
            HttpResponse response = new HttpResponse();
            response.body = "[Binary response saved to a temporary file]";
            response.bodySize = 256;
            response.filePath = videoFile.toString();
            response.fileName = "sample.mp4";
            response.headers = Map.of("content-type", List.of("video/mp4; charset=binary"));

            ResponseBodyPanel panel = createPanelWithResponse(response);
            MediaResponsePanel mediaPanel = findComponent(panel, MediaResponsePanel.class);

            assertNotNull(mediaPanel);
            assertTrue(mediaPanel.isVisible());
            assertTrue(hasLabel(mediaPanel, I18nUtil.getMessage(MessageKeys.RESPONSE_VIDEO_TITLE)));
            JLabel videoStatusLabel = (JLabel) mediaPanel.getComponent(3);
            assertFalse(videoStatusLabel.isVisible());
            assertEquals(videoStatusLabel.getText(), "");
        } finally {
            Files.deleteIfExists(videoFile);
        }
    }

    @Test
    public void shouldKeepMediaStatusVisibleWhenResponseAreaIsNarrow() throws Exception {
        Path audioFile = Files.createTempFile("easy-postman-audio-layout-", ".wav");
        JLabel[] summaryHolder = new JLabel[1];
        JLabel[] statusHolder = new JLabel[1];

        try {
            SwingUtilities.invokeAndWait(() -> {
                MediaResponsePanel mediaPanel = new MediaResponsePanel();
                mediaPanel.setAudioSource(audioFile.toString(), "sample.wav", 128);
                mediaPanel.setSize(360, 220);
                mediaPanel.doLayout();
                summaryHolder[0] = (JLabel) mediaPanel.getComponent(1);
                statusHolder[0] = (JLabel) mediaPanel.getComponent(3);
            });
        } finally {
            Files.deleteIfExists(audioFile);
        }

        assertNotNull(summaryHolder[0]);
        assertNotNull(statusHolder[0]);
        assertTrue(summaryHolder[0].getWidth() > 0);
        assertTrue(summaryHolder[0].getHeight() > 0);
        assertTrue(statusHolder[0].getWidth() > 0);
        assertTrue(statusHolder[0].getHeight() > 0);
    }

    @Test
    public void shouldShowFileOverviewAndHideToolbarDownloadForDetectedFile() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(detectedXlsxResponse());

        FileResponsePanel filePanel = findComponent(panel, FileResponsePanel.class);
        assertNotNull(filePanel);
        assertTrue(filePanel.isVisible());
        assertFalse(findComponent(panel, DownloadButton.class).isVisible(), "File overview already provides a download action");

        SwingUtilities.invokeAndWait(() -> panel.setBodyText(responseWithBody("{\"ok\":true}")));

        assertFalse(filePanel.isVisible());
        assertTrue(findComponent(panel, DownloadButton.class).isVisible());
    }

    @Test
    public void shouldLoadAttachmentTextOnlyWhenViewingAsTextAndReturnToOverview() throws Exception {
        HttpResponse response = csvAttachmentResponse();
        ResponseBodyPanel panel = createPanelWithResponse(response);
        FileResponsePanel filePanel = findComponent(panel, FileResponsePanel.class);
        assertNotNull(filePanel);
        assertEquals(panel.getResponseBodyPane().getText(), "", "File text should not be rendered behind the overview");

        JButton viewAsText = findButton(filePanel, I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_VIEW_AS_TEXT));
        assertNotNull(viewAsText);
        assertTrue(viewAsText.isVisible());
        SwingUtilities.invokeAndWait(viewAsText::doClick);

        assertFalse(filePanel.isVisible());
        assertEquals(panel.getResponseBodyPane().getText(), response.body);
        assertTrue(findComponent(panel, DownloadButton.class).isVisible());

        JButton backToOverview = findButtonByTooltip(panel, I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_BACK_TO_OVERVIEW));
        assertNotNull(backToOverview);
        assertTrue(backToOverview.isVisible());
        SwingUtilities.invokeAndWait(backToOverview::doClick);

        assertTrue(filePanel.isVisible());
        assertFalse(backToOverview.isVisible());
    }

    @Test
    public void shouldExplainMismatchAndHideTextViewForDetectedBinaryFile() throws Exception {
        HttpResponse response = detectedXlsxResponse();
        response.headers = Map.of("Content-Type", List.of("application/json;charset=UTF-8"));
        ResponseBodyPanel panel = createPanelWithResponse(response);
        FileResponsePanel filePanel = findComponent(panel, FileResponsePanel.class);
        assertNotNull(filePanel);

        JButton viewAsText = findButton(filePanel, I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_VIEW_AS_TEXT));
        assertNotNull(viewAsText);
        assertFalse(viewAsText.isVisible(), "Binary formats only show garbled text");

        String expectedNotice = I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_MIME_MISMATCH, "application/json", "XLSX");
        assertNotNull(findLabel(filePanel, expectedNotice), "Mismatch notice should name declared and detected types");
        String expectedSummary = I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_SUMMARY,
                "XLSX", ResponseSizeCalculator.formatBytes(response.bodySize));
        assertNotNull(findLabel(filePanel, expectedSummary), "File size should match the response status bar format");
    }

    @Test
    public void shouldOfferOpenActionsAfterFileIsSaved() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(detectedXlsxResponse());
        FileResponsePanel filePanel = findComponent(panel, FileResponsePanel.class);
        assertNotNull(filePanel);
        File saved = new File(System.getProperty("java.io.tmpdir"), "orders.xlsx");
        String savedText = I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_SAVED_TO, saved.getAbsolutePath());
        assertNull(findLabel(filePanel, savedText));

        SwingUtilities.invokeAndWait(() -> filePanel.showSavedFile(saved));

        JLabel savedLabel = findLabel(filePanel, savedText);
        assertNotNull(savedLabel);
        assertTrue(savedLabel.isShowing() || savedLabel.getParent().isVisible());

        HttpResponse nextResponse = detectedXlsxResponse();
        SwingUtilities.invokeAndWait(() -> panel.setBodyText(nextResponse));
        assertFalse(savedLabel.getParent().isVisible(), "Saved hint belongs to the previous response");
    }

    @Test
    public void shouldShowFileInfoWithoutDownloadForExampleWithoutContent() throws Exception {
        HttpResponse response = new HttpResponse();
        response.body = "";
        response.bodySize = 8345;
        response.fileName = "orders.xlsx";
        response.detectedFileExtension = ".xlsx";
        response.fileContentOmitted = true;
        response.headers = Map.of("Content-Type", List.of("application/json"));

        ResponseBodyPanel panel = createPanelWithResponse(response);

        FileResponsePanel filePanel = findComponent(panel, FileResponsePanel.class);
        assertNotNull(filePanel);
        assertTrue(filePanel.isVisible());
        assertNotNull(findLabel(filePanel, I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_CONTENT_NOT_SAVED)));
        assertTrue(findLabel(filePanel, I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_CONTENT_NOT_SAVED)).isVisible());
        JButton download = findButton(filePanel, CommonI18n.get(CommonMessageKeys.BUTTON_DOWNLOAD));
        assertNotNull(download);
        assertFalse(download.getParent().isVisible(), "Example without content cannot be downloaded");
        assertFalse(findComponent(panel, DownloadButton.class).isVisible());

        SwingUtilities.invokeAndWait(() -> panel.setBodyText(responseWithBody("{\"ok\":true}")));
        assertTrue(findComponent(panel, DownloadButton.class).isVisible());
    }

    @Test
    public void shouldShowFileOverviewForTextAttachment() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(csvAttachmentResponse());

        FileResponsePanel filePanel = findComponent(panel, FileResponsePanel.class);
        assertNotNull(filePanel);
        assertTrue(filePanel.isVisible());
    }

    @Test
    public void shouldWriteOriginalBytesForDetectedFile() throws Exception {
        HttpResponse response = detectedXlsxResponse();
        ResponseBodyPanel panel = createPanelWithResponse(response);
        Path target = Files.createTempFile("easy-postman-response-", ".xlsx");
        try {
            panel.writeResponseBody(target.toFile());

            assertEquals(Files.readAllBytes(target), response.rawBodyBytes);
        } finally {
            Files.deleteIfExists(target);
        }
    }

    @Test
    public void shouldWriteResponseBodyInsteadOfFormattedEditorText() throws Exception {
        String json = "{\"b\":1,\"a\":\"中文\"}";
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody(json));
        SwingUtilities.invokeAndWait(() -> panel.getResponseBodyPane().setText("{\n  \"a\": \"中文\"\n}"));
        Path target = Files.createTempFile("easy-postman-response-", ".json");
        try {
            panel.writeResponseBody(target.toFile());

            assertEquals(Files.readAllBytes(target), json.getBytes(StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(target);
        }
    }

    @Test
    public void shouldCopyTempFileWhenResponseWasSavedToDisk() throws Exception {
        byte[] content = {0x00, 0x01, (byte) 0xFF, 0x10};
        Path source = Files.createTempFile("easy-postman-response-source-", ".bin");
        Path target = Files.createTempFile("easy-postman-response-target-", ".bin");
        try {
            Files.write(source, content);
            HttpResponse response = new HttpResponse();
            response.body = "binary";
            response.filePath = source.toString();
            response.fileName = "data.bin";
            response.bodySize = content.length;
            response.headers = Map.of("Content-Type", List.of("application/octet-stream"));
            ResponseBodyPanel panel = createPanelWithResponse(response);

            panel.writeResponseBody(target.toFile());

            assertEquals(Files.readAllBytes(target), content);
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(target);
        }
    }

    private HttpResponse csvAttachmentResponse() {
        byte[] csv = "id,name\n1,Alice\n".getBytes(StandardCharsets.UTF_8);
        HttpResponse response = new HttpResponse();
        response.body = new String(csv, StandardCharsets.UTF_8);
        response.rawBodyBytes = csv;
        response.bodySize = csv.length;
        response.fileName = "report.csv";
        response.fileAttachment = true;
        response.headers = Map.of(
                "Content-Type", List.of("text/csv"),
                "Content-Disposition", List.of("attachment; filename=\"report.csv\""));
        return response;
    }

    private JLabel findLabel(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (component instanceof Container child) {
                JLabel found = findLabel(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private HttpResponse detectedXlsxResponse() throws Exception {
        byte[] xlsx = ResponseFileFixtures.xlsx();
        HttpResponse response = new HttpResponse();
        response.body = new String(xlsx, StandardCharsets.UTF_8);
        response.rawBodyBytes = xlsx;
        response.detectedFileExtension = ".xlsx";
        response.bodySize = xlsx.length;
        response.fileName = "export.xlsx";
        response.headers = Map.of("Content-Type", List.of("application/json"));
        return response;
    }

    private JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                JButton found = findButton(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JButton findButtonByTooltip(Container container, String tooltip) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && tooltip.equals(button.getToolTipText())) {
                return button;
            }
            if (component instanceof Container child) {
                JButton found = findButtonByTooltip(child, tooltip);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void jsonPathFilterShouldShowMatchesAndRestoreOriginalBodyWhenClosed() throws Exception {
        String body = "{\"data\":[{\"id\":1,\"city\":\"Jazan\"},{\"id\":2,\"city\":\"Riyadh\"}]}";
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody(body));
        AbstractButton filterButton = findToggleByTooltip(panel,
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_TOOLTIP));
        ResponseJsonPathFilterBar filterBar = findComponent(panel, ResponseJsonPathFilterBar.class);
        assertNotNull(filterButton);
        assertNotNull(filterBar);
        String[] original = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            original[0] = panel.getResponseBodyPane().getText();
            filterButton.doClick();
            filterBar.setExpression("$.data[*].city");
            filterBar.getExpressionField().postActionEvent();
        });
        awaitJsonPathFilter(panel);

        assertTrue(filterBar.isVisible());
        assertTrue(com.laker.postman.util.JsonUtil.isStructurallyEqual(
                panel.getResponseBodyPane().getText(), "[\"Jazan\",\"Riyadh\"]"));

        SwingUtilities.invokeAndWait(filterButton::doClick);

        assertFalse(filterBar.isVisible());
        assertEquals(panel.getResponseBodyPane().getText(), original[0]);
    }

    @Test
    public void jsonPathFilterShouldReapplyToNewResponsesAndKeepLastResultOnInvalidPath() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("{\"id\":1}"));
        AbstractButton filterButton = findToggleByTooltip(panel,
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_TOOLTIP));
        ResponseJsonPathFilterBar filterBar = findComponent(panel, ResponseJsonPathFilterBar.class);

        SwingUtilities.invokeAndWait(() -> {
            filterButton.doClick();
            filterBar.setExpression("$.id");
            filterBar.getExpressionField().postActionEvent();
            panel.setBodyText(responseWithBody("{\"id\":42}"));
        });
        awaitJsonPathFilter(panel);
        assertEquals(panel.getResponseBodyPane().getText(), "42");

        SwingUtilities.invokeAndWait(() -> {
            filterBar.setExpression("$.items[?(@.x >");
            filterBar.getExpressionField().postActionEvent();
        });
        awaitJsonPathFilter(panel);
        assertEquals(panel.getResponseBodyPane().getText(), "42");
        assertNotNull(filterBar.getStatusText());

        SwingUtilities.invokeAndWait(() -> {
            filterBar.setExpression("$.missing");
            filterBar.getExpressionField().postActionEvent();
        });
        awaitJsonPathFilter(panel);
        assertEquals(panel.getResponseBodyPane().getText(), "");
        assertEquals(filterBar.getStatusText(),
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_NO_MATCH));
    }

    @Test
    public void jsonPathFilterShouldQueryRawBodyWithoutLosingPrecision() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(
                responseWithBody("{\"z\":1,\"price\":1.10,\"big\":0.1000000000000000055511151231257827}"));
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$.price");
        assertEquals(panel.getResponseBodyPane().getText(), "1.1");

        applyAndAwait(panel, filterBar, "$.big");
        assertEquals(panel.getResponseBodyPane().getText(), "0.1000000000000000055511151231257827");

        applyAndAwait(panel, filterBar, "$.*");
        assertEquals(panel.getResponseBodyPane().getText().replaceAll("\\s", ""),
                "[1,1.1,0.1000000000000000055511151231257827]");
    }

    @Test
    public void jsonPathFilterShouldAcceptNullJsonRoot() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("null"));
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$");

        assertEquals(panel.getResponseBodyPane().getText(), "null");
        assertNull(filterBar.getStatusText());
    }

    @Test
    public void jsonPathFilterShouldReadBodySavedToTempFile() throws Exception {
        File saved = File.createTempFile("easyPostman_jsonpath_", ".json");
        saved.deleteOnExit();
        Files.writeString(saved.toPath(), "{\"city\":\"\u062c\u0627\u0632\u0627\u0646\"}", StandardCharsets.UTF_8);
        HttpResponse response = responseWithBody("[Body exceeds 1KB, saved as temp file]");
        response.filePath = saved.getAbsolutePath();
        response.bodySize = saved.length();
        ResponseBodyPanel panel = createPanelWithResponse(response);
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$.city");

        assertEquals(panel.getResponseBodyPane().getText(), "\"\u062c\u0627\u0632\u0627\u0646\"");
        assertNull(filterBar.getStatusText());
    }

    @Test
    public void jsonPathFilterShouldReportIncompleteBodyInsteadOfInvalidJson() throws Exception {
        HttpResponse response = responseWithBody("Response body incomplete: unexpected end of stream");
        response.bodyIncomplete = true;
        ResponseBodyPanel panel = createPanelWithResponse(response);
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$.id");

        assertEquals(filterBar.getStatusText(),
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_UNAVAILABLE));
        assertEquals(panel.getResponseBodyPane().getText(), response.body);
    }

    @Test
    public void jsonPathFilterShouldReportOmittedBodyInsteadOfInvalidJson() throws Exception {
        HttpResponse response = responseWithBody("[Response body too large, skipped]");
        response.bodyOmitted = true;
        ResponseBodyPanel panel = createPanelWithResponse(response);
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$.id");

        assertEquals(filterBar.getStatusText(),
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_UNAVAILABLE));
        assertEquals(panel.getResponseBodyPane().getText(), response.body);
    }

    @Test
    public void jsonPathFilterShouldRejectSourceOverSizeLimit() throws Exception {
        File saved = File.createTempFile("easyPostman_jsonpath_", ".json");
        saved.deleteOnExit();
        Files.writeString(saved.toPath(), "{\"items\":[" + "1,".repeat(100) + "1]}", StandardCharsets.UTF_8);
        HttpResponse response = responseWithBody("[Body saved as temp file]");
        response.filePath = saved.getAbsolutePath();
        ResponseBodyPanel panel = createPanelWithResponse(response);
        SwingUtilities.invokeAndWait(() -> panel.setJsonPathFilterLimits(100, 1024));
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$.items[0]");

        assertEquals(filterBar.getStatusText(),
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_SOURCE_TOO_LARGE, "0"));
        assertEquals(panel.getResponseBodyPane().getText(), response.body);
    }

    @Test
    public void jsonPathFilterShouldRejectResultOverSizeLimit() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(
                responseWithBody("{\"id\":1,\"items\":[\"" + "x".repeat(200) + "\"]}"));
        SwingUtilities.invokeAndWait(() -> panel.setJsonPathFilterLimits(10_000, 100));
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$.id");
        assertEquals(panel.getResponseBodyPane().getText(), "1");

        applyAndAwait(panel, filterBar, "$.items");

        assertEquals(filterBar.getStatusText(),
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_RESULT_TOO_LARGE, "0"));
        assertEquals(panel.getResponseBodyPane().getText(), "1", "previous result should stay visible");
    }

    @Test
    public void parsedDocumentCacheShouldBeSharedAndReleasedOnlyByItsOwner() throws Exception {
        ResponseBodyPanel first = createPanelWithResponse(responseWithBody("{\"id\":1}"));
        ResponseBodyPanel second = createPanelWithResponse(responseWithBody("{\"id\":2}"));
        ResponseJsonPathFilterBar firstBar = openJsonPathFilter(first);
        ResponseJsonPathFilterBar secondBar = openJsonPathFilter(second);

        applyAndAwait(first, firstBar, "$.id");
        applyAndAwait(second, secondBar, "$.id");
        assertEquals(cachedFilterKey(), "{\"id\":2}", "only the latest parsed document should be retained");

        closeJsonPathFilter(first);
        assertEquals(cachedFilterKey(), "{\"id\":2}", "closing another panel must not drop this panel's cache");
        closeJsonPathFilter(second);
        assertNull(cachedFilterKey());
    }

    private void closeJsonPathFilter(ResponseBodyPanel panel) throws Exception {
        AbstractButton filterButton = findToggleByTooltip(panel,
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_TOOLTIP));
        SwingUtilities.invokeAndWait(filterButton::doClick);
    }

    private static String cachedFilterKey() throws ReflectiveOperationException {
        java.lang.reflect.Field field = ResponseBodyPanel.class.getDeclaredField("FILTER_DOCUMENT_CACHE");
        field.setAccessible(true);
        Object cached = ((java.util.concurrent.atomic.AtomicReference<?>) field.get(null)).get();
        if (cached == null) {
            return null;
        }
        java.lang.reflect.Method key = cached.getClass().getDeclaredMethod("key");
        key.setAccessible(true);
        return (String) key.invoke(cached);
    }

    @Test
    public void editingExpressionShouldDiscardInFlightQueryResult() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("{\"a\":\"old\",\"b\":\"new\"}"));
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);
        String[] original = new String[1];

        SwingUtilities.invokeAndWait(() -> {
            original[0] = panel.getResponseBodyPane().getText();
            filterBar.setExpression("$.a");
            filterBar.getExpressionField().postActionEvent();
            // 防抖尚未触发新查询时继续编辑：旧查询结果不能再覆盖预览
            filterBar.setExpression("$.b");
            filterBar.cancelPendingApply();
        });
        awaitJsonPathFilter(panel);

        assertEquals(panel.getResponseBodyPane().getText(), original[0]);
    }

    @Test
    public void jsonPathFilterShouldSwapDocumentsInsteadOfReinsertingText() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("{\"id\":7}"));
        javax.swing.text.Document[] original = new javax.swing.text.Document[1];
        SwingUtilities.invokeAndWait(() -> original[0] = panel.getResponseBodyPane().getDocument());
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$.id");
        assertEquals(panel.getResponseBodyPane().getText(), "7");
        assertTrue(panel.getResponseBodyPane().getDocument() != original[0]);

        applyAndAwait(panel, filterBar, "");
        assertTrue(panel.getResponseBodyPane().getDocument() == original[0]);
        assertEquals(panel.getResponseBodyPane().getSyntaxEditingStyle(), org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JSON);
    }

    @Test
    public void jsonPathFilterShouldAcceptScalarJsonRoot() throws Exception {
        ResponseBodyPanel panel = createPanelWithResponse(responseWithBody("\"hello\""));
        ResponseJsonPathFilterBar filterBar = openJsonPathFilter(panel);

        applyAndAwait(panel, filterBar, "$");

        assertEquals(panel.getResponseBodyPane().getText(), "\"hello\"");
        assertNull(filterBar.getStatusText());
    }

    @Test
    public void longJsonPathErrorShouldNotSqueezeExpressionField() throws Exception {
        String longError = "JSONPath error " + "x".repeat(300);
        ResponseJsonPathFilterBar[] holder = new ResponseJsonPathFilterBar[1];
        SwingUtilities.invokeAndWait(() -> {
            ResponseJsonPathFilterBar filterBar = new ResponseJsonPathFilterBar(expression -> {
            }, () -> {
            }, () -> {
            });
            filterBar.setSize(400, 32);
            filterBar.showError(longError);
            filterBar.doLayout();
            holder[0] = filterBar;
        });

        assertTrue(holder[0].getExpressionField().getWidth() >= 400 * 0.4,
                "expression field width: " + holder[0].getExpressionField().getWidth());
        assertEquals(holder[0].getStatusText(), longError);
    }

    private ResponseJsonPathFilterBar openJsonPathFilter(ResponseBodyPanel panel) throws Exception {
        AbstractButton filterButton = findToggleByTooltip(panel,
                I18nUtil.getMessage(MessageKeys.RESPONSE_BODY_JSONPATH_FILTER_TOOLTIP));
        ResponseJsonPathFilterBar filterBar = findComponent(panel, ResponseJsonPathFilterBar.class);
        assertNotNull(filterButton);
        assertNotNull(filterBar);
        SwingUtilities.invokeAndWait(filterButton::doClick);
        return filterBar;
    }

    private void applyAndAwait(ResponseBodyPanel panel, ResponseJsonPathFilterBar filterBar, String expression)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            filterBar.setExpression(expression);
            filterBar.getExpressionField().postActionEvent();
        });
        awaitJsonPathFilter(panel);
    }

    private void awaitJsonPathFilter(ResponseBodyPanel panel) throws Exception {
        java.util.concurrent.CompletableFuture<Void>[] pending = new java.util.concurrent.CompletableFuture[1];
        SwingUtilities.invokeAndWait(() -> pending[0] = panel.pendingJsonPathFilter());
        pending[0].get(10, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private AbstractButton findToggleByTooltip(Container container, String tooltip) {
        for (Component component : container.getComponents()) {
            if (component instanceof JToggleButton button && tooltip.equals(button.getToolTipText())) {
                return button;
            }
            if (component instanceof Container child) {
                AbstractButton found = findToggleByTooltip(child, tooltip);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ResponseBodyPanel createPanelWithResponse(HttpResponse response) throws Exception {
        ResponseBodyPanel[] holder = new ResponseBodyPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            ResponseBodyPanel panel = new ResponseBodyPanel(false);
            panel.setBodyText(response);
            holder[0] = panel;
        });
        return holder[0];
    }

    private HttpResponse responseWithBody(String body) {
        HttpResponse response = new HttpResponse();
        response.body = body;
        response.bodySize = response.body.getBytes(StandardCharsets.UTF_8).length;
        response.headers = Map.of("Content-Type", List.of("application/json"));
        return response;
    }

    private String longLineJson() {
        return """
                {
                    "choices": [
                        {
                            "message": {
                                "content": "%s"
                            }
                        }
                    ]
                }
                """.formatted("a".repeat(4_500));
    }

    private WrapToggleButton findWrapButton(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof WrapToggleButton wrapToggleButton) {
                return wrapToggleButton;
            }
            if (component instanceof Container child) {
                WrapToggleButton found = findWrapButton(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private <T extends Component> T findComponent(Container container, Class<T> type) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container child) {
                T found = findComponent(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean hasLabel(Container container, String text) {
        return findLabel(container, text) != null;
    }

    private Object getTokenPainter(ResponseBodyPanel panel) {
        try {
            Field field = RSyntaxTextArea.class.getDeclaredField("tokenPainter");
            field.setAccessible(true);
            return field.get(panel.getResponseBodyPane());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private boolean hasMenuItem(JPopupMenu popupMenu, String text) {
        return findMenuItem(popupMenu, text) != null;
    }

    private JMenuItem findMenuItem(JPopupMenu popupMenu, String text) {
        for (Component component : popupMenu.getComponents()) {
            if (component instanceof JMenuItem menuItem && text.equals(menuItem.getText())) {
                return menuItem;
            }
        }
        return null;
    }

    private boolean hasAnyMenuItem(JPopupMenu popupMenu, String... texts) {
        for (String text : texts) {
            if (hasMenuItem(popupMenu, text)) {
                return true;
            }
        }
        return false;
    }

    private void firePopupWillBecomeVisible(JPopupMenu popupMenu) {
        PopupMenuEvent event = new PopupMenuEvent(popupMenu);
        for (var listener : popupMenu.getPopupMenuListeners()) {
            listener.popupMenuWillBecomeVisible(event);
        }
    }
}

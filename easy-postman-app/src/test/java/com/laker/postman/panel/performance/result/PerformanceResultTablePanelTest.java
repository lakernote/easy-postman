package com.laker.postman.panel.performance.result;

import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.performance.model.ResultNodeInfo;
import com.laker.postman.performance.model.ResultTiming;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class PerformanceResultTablePanelTest extends AbstractSwingUiTest {

    @Test
    public void flushPendingResultsShouldImmediatelyPublishRowsToTable() throws Exception {
        AtomicReference<PerformanceResultTablePanel> panelRef = new AtomicReference<>();
        AtomicInteger rowCount = new AtomicInteger(-1);

        SwingUtilities.invokeAndWait(() -> {
            PerformanceResultTablePanel panel = new PerformanceResultTablePanel();
            panelRef.set(panel);
            panel.addResult(sampleResult());
            panel.flushPendingResults();
            rowCount.set(findTable(panel).getRowCount());
        });

        try {
            assertEquals(rowCount.get(), 1);
        } finally {
            PerformanceResultTablePanel panel = panelRef.get();
            if (panel != null) {
                SwingUtilities.invokeAndWait(panel::dispose);
            }
        }
    }

    @Test
    public void tableShouldRenderJmeterStyleTimingColumns() throws Exception {
        AtomicReference<PerformanceResultTablePanel> panelRef = new AtomicReference<>();
        AtomicReference<List<String>> renderedTexts = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            PerformanceResultTablePanel panel = new PerformanceResultTablePanel();
            panelRef.set(panel);
            HttpResponse response = new HttpResponse();
            response.code = 200;
            response.costMs = 20_500L;
            panel.addResult(new ResultNodeInfo("slow", null, new PreparedRequest(), response, List.of(), false, false,
                    null, ResultTiming.of(1_700_000_000_123L, 20_450L, ResultTiming.UNKNOWN)));
            panel.flushPendingResults();
            JTable table = findTable(panel);
            renderedTexts.set(List.of(
                    renderedText(table, PerformanceResultTablePanel.ResultTableModel.COL_START_TIME),
                    renderedText(table, PerformanceResultTablePanel.ResultTableModel.COL_LATENCY),
                    renderedText(table, PerformanceResultTablePanel.ResultTableModel.COL_CONNECT)
            ));
        });

        try {
            assertTrue(renderedTexts.get().get(0).matches("\\d{2}:\\d{2}:\\d{2}\\.123"), renderedTexts.get().get(0));
            assertEquals(renderedTexts.get().get(1), "20450");
            assertEquals(renderedTexts.get().get(2), "—");
        } finally {
            PerformanceResultTablePanel panel = panelRef.get();
            if (panel != null) {
                SwingUtilities.invokeAndWait(panel::dispose);
            }
        }
    }

    private static String renderedText(JTable table, int column) {
        JLabel label = (JLabel) table.prepareRenderer(table.getCellRenderer(0, column), 0, column);
        return label.getText();
    }

    @Test
    public void compactModeShouldRetainOnlyMostRecentRows() throws Exception {
        AtomicReference<PerformanceResultTablePanel> panelRef = new AtomicReference<>();
        AtomicInteger rowCount = new AtomicInteger(-1);
        AtomicReference<String> firstVisibleName = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            PerformanceResultTablePanel panel = new PerformanceResultTablePanel();
            panelRef.set(panel);
            int totalRows = PerformanceResultTablePanel.COMPACT_RESULT_ROW_LIMIT + 25;
            for (int i = 0; i < totalRows; i++) {
                panel.addResult(sampleResult("Sample-" + i), true);
            }
            panel.flushPendingResults();
            JTable table = findTable(panel);
            rowCount.set(table.getRowCount());
            firstVisibleName.set(String.valueOf(table.getValueAt(0, 1)));
        });

        try {
            assertEquals(rowCount.get(), PerformanceResultTablePanel.COMPACT_RESULT_ROW_LIMIT);
            assertEquals(firstVisibleName.get(), "Sample-25");
        } finally {
            PerformanceResultTablePanel panel = panelRef.get();
            if (panel != null) {
                SwingUtilities.invokeAndWait(panel::dispose);
            }
        }
    }

    @Test
    public void fullModeShouldRetainOnlyConfiguredMostRecentRows() throws Exception {
        Properties props = getSettingsProperties();
        Properties backup = new Properties();
        backup.putAll(props);
        AtomicReference<PerformanceResultTablePanel> panelRef = new AtomicReference<>();
        AtomicInteger rowCount = new AtomicInteger(-1);
        AtomicReference<String> firstVisibleName = new AtomicReference<>();

        try {
            props.setProperty("performance_result_row_limit", "120");
            SwingUtilities.invokeAndWait(() -> {
                PerformanceResultTablePanel panel = new PerformanceResultTablePanel();
                panelRef.set(panel);
                for (int i = 0; i < 145; i++) {
                    panel.addResult(sampleResult("Sample-" + i), false);
                }
                panel.flushPendingResults();
                JTable table = findTable(panel);
                rowCount.set(table.getRowCount());
                firstVisibleName.set(String.valueOf(table.getValueAt(0, 1)));
            });

            assertEquals(rowCount.get(), 120);
            assertEquals(firstVisibleName.get(), "Sample-25");
        } finally {
            PerformanceResultTablePanel panel = panelRef.get();
            if (panel != null) {
                SwingUtilities.invokeAndWait(panel::dispose);
            }
            props.clear();
            props.putAll(backup);
        }
    }

    @Test
    public void detailPanelShouldOnlyAppearWhileARowIsSelected() throws Exception {
        AtomicReference<PerformanceResultTablePanel> panelRef = new AtomicReference<>();
        AtomicReference<List<Boolean>> states = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            PerformanceResultTablePanel panel = new PerformanceResultTablePanel();
            panelRef.set(panel);
            panel.setSize(1200, 400);
            panel.addResult(sampleResult("a"));
            panel.addResult(sampleResult("b"));
            panel.flushPendingResults();
            JTable table = findTable(panel);
            boolean initial = panel.isDetailVisible();
            table.setRowSelectionInterval(1, 1);
            boolean afterSelect = panel.isDetailVisible();
            table.clearSelection();
            boolean afterClear = panel.isDetailVisible();
            table.setRowSelectionInterval(0, 0);
            panel.clearResults();
            states.set(List.of(initial, afterSelect, afterClear, panel.isDetailVisible()));
        });

        try {
            assertEquals(states.get(), List.of(false, true, false, false));
        } finally {
            SwingUtilities.invokeAndWait(panelRef.get()::dispose);
        }
    }

    @Test
    public void failedOnlyFilterShouldHideSuccessfulRows() throws Exception {
        AtomicReference<PerformanceResultTablePanel> panelRef = new AtomicReference<>();
        AtomicReference<List<Integer>> counts = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            PerformanceResultTablePanel panel = new PerformanceResultTablePanel();
            panelRef.set(panel);
            HttpResponse failed = new HttpResponse();
            failed.code = 500;
            panel.addResult(sampleResult("ok"));
            panel.addResult(new ResultNodeInfo("bad", "", new PreparedRequest(), failed, List.of(), false));
            panel.flushPendingResults();
            JTable table = findTable(panel);
            int before = table.getRowCount();
            findCheckBox(panel).doClick();
            int after = table.getRowCount();
            findCheckBox(panel).doClick();
            counts.set(List.of(before, after, table.getRowCount()));
        });

        try {
            assertEquals(counts.get(), List.of(2, 1, 2));
        } finally {
            SwingUtilities.invokeAndWait(panelRef.get()::dispose);
        }
    }

    @Test
    public void detailSummaryShouldShowTimingBreakdown() {
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.costMs = 300L;
        String summary = PerformanceResultTablePanel.buildDetailSummary(new ResultNodeInfo("s", null,
                new PreparedRequest(), response, List.of(), false, false, null,
                ResultTiming.of(1_700_000_000_123L, 250L, ResultTiming.UNKNOWN)));
        assertTrue(summary.contains("200"), summary);
        assertTrue(summary.contains("300 ms"), summary);
        assertTrue(summary.contains("250 ms"), summary);
        assertTrue(summary.contains("—"), summary);
    }

    private static JCheckBox findCheckBox(Container root) {
        JCheckBox box = findCheckBoxOrNull(root);
        if (box == null) {
            throw new AssertionError("JCheckBox not found");
        }
        return box;
    }

    private static JCheckBox findCheckBoxOrNull(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JCheckBox box) {
                return box;
            }
            if (c instanceof Container child) {
                JCheckBox found = findCheckBoxOrNull(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ResultNodeInfo sampleResult() {
        return sampleResult("Sample");
    }

    private static ResultNodeInfo sampleResult(String name) {
        HttpResponse response = new HttpResponse();
        response.code = 200;
        response.costMs = 12;
        return new ResultNodeInfo(
                name,
                "",
                new PreparedRequest(),
                response,
                List.of(),
                false
        );
    }

    private static JTable findTable(Container root) {
        try {
            java.lang.reflect.Field tableField = PerformanceResultTablePanel.class.getDeclaredField("table");
            tableField.setAccessible(true);
            return (JTable) tableField.get(root);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("JTable not found", e);
        }
    }

    private static Properties getSettingsProperties() throws Exception {
        Field propsField = SettingManager.class.getDeclaredField("props");
        propsField.setAccessible(true);
        return (Properties) propsField.get(null);
    }
}

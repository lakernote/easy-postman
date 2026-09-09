package com.laker.postman.common.component.table;

import com.laker.postman.model.Variable;
import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class AbstractTablePanelEditorNavigationTest extends AbstractSwingUiTest {

    @Test
    public void longValueEditorShouldUseVisibleTextAreaAndMoveWithTab() throws Exception {
        AtomicReference<EasyVariableTablePanel> panelRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            EasyVariableTablePanel panel = new EasyVariableTablePanel("Name", "Value", false, false);
            panel.setVariableList(List.of(
                    new Variable(true, "token", "Bearer " + "x".repeat(240)),
                    new Variable(true, "next", "value")
            ));

            JTable table = panel.getTable();
            table.setSize(640, 160);
            table.doLayout();
            dispatchMousePressed(table, cellCenter(table, 0, 2));
            panelRef.set(panel);
        });
        flushEdt();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = panelRef.get().getTable();
            assertTrue(table.isEditing());

            JTextArea textArea = findVisibleTextArea(table.getEditorComponent());
            assertNotNull(textArea, "长值应使用当前可见的 JTextArea 编辑");

            InputMap inputMap = textArea.getInputMap(JComponent.WHEN_FOCUSED);
            Object nextActionKey = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
            Object previousActionKey = inputMap.get(
                    KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK));
            assertNotNull(nextActionKey, "Tab 应绑定到表格单元格导航");
            assertNotNull(previousActionKey, "Shift+Tab 应绑定到表格单元格导航");

            Action nextAction = textArea.getActionMap().get(nextActionKey);
            assertNotNull(nextAction);
            nextAction.actionPerformed(new ActionEvent(textArea, ActionEvent.ACTION_PERFORMED, "tab"));
        });
        flushEdt();

        SwingUtilities.invokeAndWait(() -> {
            JTable table = panelRef.get().getTable();
            assertTrue(table.isEditing());
            assertEquals(table.getEditingRow(), 1);
            assertEquals(table.getEditingColumn(), 1);
        });
    }

    private static Point cellCenter(JTable table, int row, int column) {
        Rectangle rect = table.getCellRect(row, column, true);
        return new Point(rect.x + rect.width / 2, rect.y + rect.height / 2);
    }

    private static void dispatchMousePressed(JTable table, Point point) {
        table.dispatchEvent(new MouseEvent(
                table,
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                point.x,
                point.y,
                1,
                false,
                MouseEvent.BUTTON1
        ));
    }

    private static JTextArea findVisibleTextArea(Component root) {
        if (root instanceof JTextArea textArea && textArea.isVisible()) {
            return textArea;
        }
        if (root instanceof Container container && root.isVisible()) {
            for (Component child : container.getComponents()) {
                JTextArea found = findVisibleTextArea(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // Drain the editor-start/navigation callback.
        });
        SwingUtilities.invokeAndWait(() -> {
            // Drain the focus/layout callback.
        });
    }
}

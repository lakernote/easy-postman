package com.laker.postman.panel.collections;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.exception.CancelException;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

public class OpenedRequestTabSessionSaverUiTest extends AbstractSwingUiTest {

    @Test
    public void shouldCancelExitAndKeepFailedTabSelectedWhenSaveReturnsFalse() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RecordingRequestEditor editor = prepareEditor(1);
            JTabbedPane tabs = editor.getTabbedPane();

            expectThrows(CancelException.class,
                    () -> OpenedRequestTabSessionSaver.saveUnsavedTabs(editor, tabs, List.of(0, 1, 2)));

            assertEquals(editor.savedTabIndexes, List.of(0, 1));
            assertEquals(tabs.getSelectedIndex(), 1);
        });
    }

    @Test
    public void shouldSaveAllDirtyTabsWhenEverySaveSucceeds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RecordingRequestEditor editor = prepareEditor(-1);

            OpenedRequestTabSessionSaver.saveUnsavedTabs(editor, editor.getTabbedPane(), List.of(0, 2));

            assertEquals(editor.savedTabIndexes, List.of(0, 2));
        });
    }

    private static RecordingRequestEditor prepareEditor(int failedTabIndex) {
        RecordingRequestEditor editor = UiSingletonFactory.getInstance(RecordingRequestEditor.class);
        editor.failedTabIndex = failedTabIndex;
        editor.savedTabIndexes.clear();
        JTabbedPane tabs = editor.getTabbedPane();
        tabs.removeAll();
        for (int i = 0; i < 3; i++) {
            tabs.addTab("Request " + i, new JPanel());
        }
        tabs.setSelectedIndex(2);
        return editor;
    }

    public static final class RecordingRequestEditor extends RequestEditorPanel {
        private JTabbedPane tabs;
        private int failedTabIndex;
        private final List<Integer> savedTabIndexes = new ArrayList<>();

        @Override
        protected void initUI() {
            tabs = new JTabbedPane();
        }

        @Override
        protected void registerListeners() {
        }

        @Override
        public JTabbedPane getTabbedPane() {
            return tabs;
        }

        @Override
        public boolean saveCurrentRequest() {
            int selectedIndex = tabs.getSelectedIndex();
            savedTabIndexes.add(selectedIndex);
            return selectedIndex != failedTabIndex;
        }
    }
}

package com.laker.postman.panel.sidebar;

import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

public class SidebarTabbedPaneUiTest extends AbstractSwingUiTest {

    @Test
    public void collapsedTabComponentShouldNotMoveWhenSelectionChanges() throws Exception {
        AtomicInteger selectedX = new AtomicInteger();
        AtomicInteger unselectedX = new AtomicInteger();

        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane pane = new JTabbedPane(SwingConstants.LEFT, JTabbedPane.SCROLL_TAB_LAYOUT);
            pane.setUI(new SidebarTabbedPaneUi(
                    () -> false,
                    () -> 60,
                    () -> 36,
                    ignored -> 60,
                    () -> 40
            ));

            for (int i = 0; i < 3; i++) {
                pane.addTab("Tab " + i, new JPanel());
                pane.setTabComponentAt(i, fixedTabComponent());
            }

            pane.setSize(500, 300);
            pane.setSelectedIndex(0);
            pane.doLayout();
            selectedX.set(pane.getTabComponentAt(0).getX());

            pane.setSelectedIndex(1);
            pane.doLayout();
            unselectedX.set(pane.getTabComponentAt(0).getX());
        });

        assertEquals(selectedX.get(), unselectedX.get(),
                "Selecting a collapsed sidebar tab must not shift its icon horizontally");
    }

    private static JPanel fixedTabComponent() {
        return new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(36, 40);
            }
        };
    }
}

package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.*;
import java.awt.*;

import static org.testng.Assert.assertTrue;

public class ResponseTabNavigationControllerTest extends AbstractSwingUiTest {

    @Test
    public void shouldSelectEventStreamWhenSseResponseArrivesFromAnotherVisibleTab() {
        TabBarBuilder.TabConfig config = TabBarBuilder.createHttpTabs();
        JButton[] buttons = TabBarBuilder.createModernTabButtons(config.tabNames);
        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        TabBarBuilder.addButtonsToTabBar(tabBar, buttons, config.initialVisibility);
        JPanel cards = new JPanel(new CardLayout());
        for (int i = 0; i < config.tabNames.length; i++) {
            cards.add(new JPanel(), config.tabNames[i]);
        }
        JPanel topResponseBar = new JPanel(new BorderLayout());
        topResponseBar.add(tabBar, BorderLayout.WEST);

        ResponseTabNavigationController controller = new ResponseTabNavigationController(
                topResponseBar,
                tabBar,
                new JPanel(),
                cards,
                buttons,
                config.tabNames,
                config.indexOf(TabBarBuilder.Tab.RESPONSE_BODY),
                config.indexOf(TabBarBuilder.Tab.EVENT_STREAM)
        );
        controller.initializeFirstVisibleTab();
        controller.bindTabActions();
        controller.switchToTab(config.indexOf(TabBarBuilder.Tab.RESPONSE_HEADERS));

        controller.switchHttpResponseView(true);

        assertTrue(((ModernTabButton) buttons[config.indexOf(TabBarBuilder.Tab.EVENT_STREAM)]).isSelected());
        assertTrue(cards.getComponent(config.indexOf(TabBarBuilder.Tab.EVENT_STREAM)).isVisible());
    }
}

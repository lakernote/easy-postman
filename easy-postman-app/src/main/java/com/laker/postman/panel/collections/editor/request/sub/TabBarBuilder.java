package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import javax.swing.*;
import java.awt.*;
import java.util.function.IntConsumer;

/**
 * Tab栏构建器
 * 负责根据协议类型创建Tab按钮和面板
 */
@UtilityClass
public class TabBarBuilder {

    public enum Tab {
        RESPONSE_BODY,
        EVENT_STREAM,
        RESPONSE_HEADERS,
        TESTS,
        NETWORK_LOG,
        TIMING,
        WEBSOCKET_MESSAGES
    }

    /**
     * Tab配置信息
     */
    public static class TabConfig {
        public final String[] tabNames;
        public final boolean[] initialVisibility;
        private final Tab[] tabs;

        public TabConfig(Tab[] tabs, String[] tabNames, boolean[] initialVisibility) {
            this.tabs = tabs;
            this.tabNames = tabNames;
            this.initialVisibility = initialVisibility;
        }

        public int indexOf(Tab tab) {
            for (int i = 0; i < tabs.length; i++) {
                if (tabs[i] == tab) {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * 为WebSocket协议创建Tab配置
     */
    public static TabConfig createWebSocketTabs() {
        Tab[] tabs = new Tab[]{Tab.WEBSOCKET_MESSAGES, Tab.RESPONSE_HEADERS, Tab.NETWORK_LOG};
        String[] names = new String[]{
                I18nUtil.getMessage(MessageKeys.TAB_WEBSOCKET_MESSAGES),
                I18nUtil.getMessage(MessageKeys.TAB_RESPONSE_HEADERS),
                I18nUtil.getMessage(MessageKeys.TAB_NETWORK_LOG)
        };
        return createTabConfig(tabs, names, null);
    }

    /**
     * 为HTTP协议创建Tab配置
     */
    public static TabConfig createHttpTabs() {
        Tab[] tabs = new Tab[]{
                Tab.RESPONSE_BODY, Tab.EVENT_STREAM, Tab.RESPONSE_HEADERS,
                Tab.TESTS, Tab.NETWORK_LOG, Tab.TIMING
        };
        String[] names = new String[]{
                I18nUtil.getMessage(MessageKeys.TAB_RESPONSE_BODY),
                I18nUtil.getMessage(MessageKeys.TAB_SSE_EVENT_STREAM),
                I18nUtil.getMessage(MessageKeys.TAB_RESPONSE_HEADERS),
                I18nUtil.getMessage(MessageKeys.TAB_TESTS),
                I18nUtil.getMessage(MessageKeys.TAB_NETWORK_LOG),
                I18nUtil.getMessage(MessageKeys.TAB_TIMING)
        };

        // HTTP 模式下，默认隐藏事件流标签。
        boolean[] visibility = createAllVisibleArray(names.length);
        visibility[indexOf(tabs, Tab.EVENT_STREAM)] = false;

        return createTabConfig(tabs, names, visibility);
    }

    /**
     * 创建Tab配置
     *
     * @param names      tab名称数组
     * @param visibility 可见性数组，null表示全部可见
     * @return Tab配置对象
     */
    private static TabConfig createTabConfig(Tab[] tabs, String[] names, boolean[] visibility) {
        boolean[] vis = visibility != null ? visibility : createAllVisibleArray(names.length);
        return new TabConfig(tabs, names, vis);
    }

    private static int indexOf(Tab[] tabs, Tab tab) {
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] == tab) {
                return i;
            }
        }
        return -1;
    }

    public static JButton[] createModernTabButtons(String[] names) {
        JButton[] buttons = new JButton[names.length];
        for (int i = 0; i < names.length; i++) {
            buttons[i] = new ModernTabButton(names[i], i);
        }
        return buttons;
    }

    /**
     * 创建全部可见的布尔数组
     */
    private static boolean[] createAllVisibleArray(int length) {
        boolean[] result = new boolean[length];
        java.util.Arrays.fill(result, true);
        return result;
    }

    /**
     * 将Tab按钮添加到tabBar面板
     *
     * @param tabBar     目标面板
     * @param buttons    按钮数组
     * @param visibility 可见性数组
     */
    public static void addButtonsToTabBar(JPanel tabBar, JButton[] buttons, boolean[] visibility) {
        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] != null) {
                buttons[i].setVisible(visibility[i]);
                tabBar.add(buttons[i]);
            }
        }
    }

    /**
     * 为所有tab按钮绑定点击事件
     *
     * @param buttons       按钮数组
     * @param tabNames      tab名称数组
     * @param cardPanel     卡片面板
     * @param onTabSelected tab选中时的回调
     */
    public static void bindTabActions(JButton[] buttons, String[] tabNames,
                                      JPanel cardPanel, IntConsumer onTabSelected) {
        for (int i = 0; i < buttons.length; i++) {
            final int tabIndex = i;
            buttons[i].addActionListener(e -> {
                CardLayout cl = (CardLayout) cardPanel.getLayout();
                cl.show(cardPanel, tabNames[tabIndex]);
                onTabSelected.accept(tabIndex);
            });
        }
    }
}

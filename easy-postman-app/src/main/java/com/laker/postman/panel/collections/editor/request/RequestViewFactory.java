package com.laker.postman.panel.collections.editor.request;

import com.laker.postman.request.model.RequestItemProtocolEnum;


import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.MarkdownEditorPanel;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.tab.IndicatorTabComponent;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.http.request.AppRequestHeaderDefaults;
import com.laker.postman.panel.collections.editor.request.sub.*;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;
import java.awt.event.ActionListener;

@UtilityClass
class RequestViewFactory {

    static RequestViewComponents create(RequestItemProtocolEnum protocol,
                                        RequestEditSubPanelType panelType,
                                        ActionListener sendAction) {
        boolean requestOnlySnapshot = panelType == RequestEditSubPanelType.PERFORMANCE_SNAPSHOT;
        RequestLinePanel requestLinePanel = new RequestLinePanel(sendAction, protocol);
        JComboBox<String> methodBox = requestLinePanel.getMethodBox();
        JTextField urlField = requestLinePanel.getUrlField();

        JTabbedPane reqTabs = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        configureRequestTabs(reqTabs);

        MarkdownEditorPanel descriptionEditor = new MarkdownEditorPanel();
        EasyRequestParamsPanel pathVariablesPanel = EasyRequestParamsPanel.pathVariablesPanel();
        EasyRequestParamsPanel paramsPanel = EasyRequestParamsPanel.queryParamsPanel();
        RequestParamsPanel paramsTabPanel = new RequestParamsPanel(pathVariablesPanel, paramsPanel);
        IndicatorTabComponent paramsTabIndicator = new IndicatorTabComponent(I18nUtil.getMessage(MessageKeys.TAB_PARAMS));
        AuthTabPanel authTabPanel = new AuthTabPanel();
        IndicatorTabComponent authTabIndicator = new IndicatorTabComponent(I18nUtil.getMessage(MessageKeys.TAB_AUTHORIZATION));
        EasyRequestHttpHeadersPanel headersPanel =
                new EasyRequestHttpHeadersPanel(AppRequestHeaderDefaults.generatedHeaderPolicy());
        IndicatorTabComponent headersTabIndicator = new IndicatorTabComponent(I18nUtil.getMessage(MessageKeys.TAB_REQUEST_HEADERS));
        RequestBodyPanel requestBodyPanel = new RequestBodyPanel(protocol);
        IndicatorTabComponent bodyTabIndicator = new IndicatorTabComponent(I18nUtil.getMessage(MessageKeys.TAB_REQUEST_BODY));
        ScriptPanel scriptPanel = new ScriptPanel();
        IndicatorTabComponent scriptsTabIndicator = new IndicatorTabComponent(I18nUtil.getMessage(MessageKeys.TAB_SCRIPTS));
        RequestSettingsPanel requestSettingsPanel = new RequestSettingsPanel(protocol);
        IndicatorTabComponent settingsTabIndicator = new IndicatorTabComponent(I18nUtil.getMessage(MessageKeys.TAB_SETTINGS));

        boolean enableSaveButton = protocol.isHttpProtocol() && !requestOnlySnapshot;
        ResponsePanel responsePanel = requestOnlySnapshot ? null : new ResponsePanel(protocol, enableSaveButton);

        boolean isVertical = SettingManager.isLayoutVertical();
        int orientation = isVertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
        JSplitPane splitPane = null;
        JComponent editorContent = reqTabs;
        if (!requestOnlySnapshot) {
            splitPane = new JSplitPane(orientation, reqTabs, responsePanel);
            configureRequestResponseSplitPane(splitPane);
            editorContent = splitPane;
        }

        RequestViewComponents components = new RequestViewComponents(
                requestLinePanel,
                methodBox,
                urlField,
                reqTabs,
                descriptionEditor,
                paramsTabPanel,
                pathVariablesPanel,
                paramsPanel,
                paramsTabIndicator,
                authTabPanel,
                authTabIndicator,
                headersPanel,
                headersTabIndicator,
                requestBodyPanel,
                bodyTabIndicator,
                settingsTabIndicator,
                requestSettingsPanel,
                scriptPanel,
                scriptsTabIndicator,
                responsePanel,
                splitPane,
                editorContent
        );
        rebuildRequestTabs(components, protocol);
        return components;
    }

    private static void configureRequestTabs(JTabbedPane reqTabs) {
        reqTabs.setMinimumSize(new Dimension(0, 0));
        ToolWindowSurfaceStyle.applyTabbedPaneCard(reqTabs);
        reqTabs.putClientProperty(FlatClientProperties.TABBED_PANE_HAS_FULL_BORDER, false);
        reqTabs.putClientProperty(FlatClientProperties.TABBED_PANE_SHOW_CONTENT_SEPARATOR, false);
    }

    private static void configureRequestResponseSplitPane(JSplitPane splitPane) {
        splitPane.setUI(new EditorSplitPaneUi());
        splitPane.setDividerSize(4);
        splitPane.setOneTouchExpandable(false);
        splitPane.setContinuousLayout(true);
        ToolWindowSurfaceStyle.applyBackground(splitPane);
        splitPane.setBorder(new EmptyBorder(0, 0, 0, 0));
    }

    static void rebuildRequestTabs(RequestViewComponents components, RequestItemProtocolEnum protocol) {
        JTabbedPane tabs = components.reqTabs;
        tabs.removeAll();
        addTabIfVisible(
                tabs,
                I18nUtil.getMessage(MessageKeys.REQUEST_DOCS_TAB_TITLE),
                components.descriptionEditor,
                null,
                SettingManager.isRequestEditorTabVisible(SettingManager.REQUEST_EDITOR_TAB_DOCS)
        );
        addTabIfVisible(
                tabs,
                I18nUtil.getMessage(MessageKeys.TAB_PARAMS),
                components.paramsTabPanel,
                components.paramsTabIndicator,
                SettingManager.isRequestEditorTabVisible(SettingManager.REQUEST_EDITOR_TAB_PARAMS)
        );
        addTabIfVisible(
                tabs,
                I18nUtil.getMessage(MessageKeys.TAB_AUTHORIZATION),
                components.authTabPanel,
                components.authTabIndicator,
                protocol.isHttpProtocol()
                        && SettingManager.isRequestEditorTabVisible(SettingManager.REQUEST_EDITOR_TAB_AUTH)
        );
        addTabIfVisible(
                tabs,
                I18nUtil.getMessage(MessageKeys.TAB_REQUEST_HEADERS),
                components.headersPanel,
                components.headersTabIndicator,
                SettingManager.isRequestEditorTabVisible(SettingManager.REQUEST_EDITOR_TAB_HEADERS)
        );
        addTabIfVisible(
                tabs,
                I18nUtil.getMessage(MessageKeys.TAB_REQUEST_BODY),
                components.requestBodyPanel,
                components.bodyTabIndicator,
                SettingManager.isRequestEditorTabVisible(SettingManager.REQUEST_EDITOR_TAB_BODY)
        );
        addTabIfVisible(
                tabs,
                I18nUtil.getMessage(MessageKeys.TAB_SCRIPTS),
                components.scriptPanel,
                components.scriptsTabIndicator,
                SettingManager.isRequestEditorTabVisible(SettingManager.REQUEST_EDITOR_TAB_SCRIPTS)
        );
        addTabIfVisible(
                tabs,
                I18nUtil.getMessage(MessageKeys.TAB_SETTINGS),
                components.requestSettingsPanel,
                components.settingsTabIndicator,
                SettingManager.isRequestEditorTabVisible(SettingManager.REQUEST_EDITOR_TAB_SETTINGS)
        );
        if (tabs.getTabCount() == 0) {
            addTabIfVisible(
                    tabs,
                    I18nUtil.getMessage(MessageKeys.TAB_PARAMS),
                    components.paramsTabPanel,
                    components.paramsTabIndicator,
                    true
            );
        }
        applyTabContentSurfaces(tabs);
    }

    private static void addTabIfVisible(JTabbedPane tabs,
                                        String title,
                                        Component component,
                                        IndicatorTabComponent indicator,
                                        boolean visible) {
        if (!visible) {
            return;
        }

        tabs.addTab(title, component);
        if (indicator != null) {
            tabs.setTabComponentAt(tabs.getTabCount() - 1, indicator);
        }
    }

    private static void applyTabContentSurfaces(JTabbedPane tabs) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            Component component = tabs.getComponentAt(i);
            if (component instanceof JComponent jComponent) {
                ToolWindowSurfaceStyle.applyCard(jComponent);
            }
        }
    }

    private static final class EditorSplitPaneUi extends BasicSplitPaneUI {
        @Override
        public BasicSplitPaneDivider createDefaultDivider() {
            return new BasicSplitPaneDivider(this) {
                @Override
                public void setBorder(javax.swing.border.Border border) {
                    super.setBorder(new EmptyBorder(0, 0, 0, 0));
                }

                @Override
                public void paint(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        g2.setColor(ModernColors.getCardBackgroundColor());
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(ModernColors.getTabSeparatorColor());
                        JSplitPane splitPane = EditorSplitPaneUi.this.getSplitPane();
                        if (splitPane != null && splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT) {
                            int x = Math.max(0, getWidth() / 2);
                            g2.drawLine(x, 0, x, getHeight());
                        } else {
                            int y = Math.max(0, getHeight() / 2);
                            g2.drawLine(0, y, getWidth(), y);
                        }
                    } finally {
                        g2.dispose();
                    }
                }
            };
        }
    }
}

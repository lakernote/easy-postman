package com.laker.postman.panel.history;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.http.runtime.model.PreparedRequest;
import com.laker.postman.history.RequestHistoryItem;
import com.laker.postman.request.model.RequestItemProtocolEnum;
import com.laker.postman.request.model.HttpHeader;
import com.laker.postman.request.model.HttpParam;
import com.laker.postman.request.model.HttpFormData;
import com.laker.postman.request.model.HttpFormUrlencoded;
import com.laker.postman.request.model.HttpRequestItem;


import com.laker.postman.common.UiSingletonPanel;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.common.component.ToolWindowActionToolbar;
import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ClearButton;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.ioc.BeanFactory;
import com.laker.postman.panel.sidebar.SidebarTab;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.collections.editor.request.RequestEditSubPanel;
import com.laker.postman.panel.collections.editor.request.sub.RequestBodyPanel;
import com.laker.postman.panel.sidebar.SidebarTabPanel;
import com.laker.postman.service.HistoryPersistenceService;
import com.laker.postman.service.render.HttpHtmlRenderer;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.BreakIterator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 历史记录面板
 */
public class HistoryPanel extends UiSingletonPanel {
    private static final int HISTORY_SIDEBAR_WIDTH = AppToolWindowChrome.DEFAULT_SIDE_WIDTH;
    private static final int FILTER_DEBOUNCE_MS = 180;

    private JList<Object> historyList;
    private JTextPane requestPane;
    private JTextPane responsePane;
    private JTextPane timingPane;
    private JTextPane eventPane;
    private DefaultListModel<Object> historyListModel;
    private SearchTextField searchField;
    private JLabel detailTitleLabel;
    private JLabel detailMetaLabel;
    private JLabel endpointValueLabel;
    private JLabel endpointDetailLabel;
    private JLabel payloadValueLabel;
    private JLabel payloadDetailLabel;
    private JLabel structureValueLabel;
    private JLabel structureDetailLabel;
    private JLabel resultValueLabel;
    private JLabel resultDetailLabel;
    private JButton clearHistoryButton;

    private final List<RequestHistoryItem> allHistoryItems = new ArrayList<>();
    private final Set<String> collapsedGroups = new HashSet<>();
    private final List<AbstractButton> detailActionButtons = new ArrayList<>();
    private RequestHistoryItem currentSelectedItem;
    private volatile boolean isUpdating = false;
    private volatile boolean suppressSelectionSync = false;
    private int hoveredHistoryIndex = -1;
    private final SimpleDateFormat detailTimeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private long todayStartCache = 0;
    private long yesterdayStartCache = 0;
    private Timer filterDebounceTimer;
    private SwingWorker<HistoryListBuildResult, Void> historyListBuildWorker;
    private final AtomicInteger historyListBuildVersion = new AtomicInteger();

    private record HistoryVisualInfo(String title, String subtitle, String fullUrl) {
    }

    private record HistoryGroupHeader(String label, int count, boolean collapsed) {
    }

    private record OverviewCardLabels(JLabel valueLabel, JLabel detailLabel) {
    }

    private record HistoryListBuildResult(List<RequestHistoryItem> filteredItems,
                                          List<Object> displayItems) {
    }

    @Override
    protected void initUI() {
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyBackground(this);
        add(createWorkspaceSplitPane(), BorderLayout.CENTER);
        setMinimumSize(new Dimension(0, 120));
        filterDebounceTimer = new Timer(FILTER_DEBOUNCE_MS, e -> rebuildHistoryListModel(currentSelectedItem));
        filterDebounceTimer.setRepeats(false);

        SwingUtilities.invokeLater(this::loadPersistedHistory);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(headerPanel);

        JPanel titlePanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(titlePanel);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel title = new JLabel(I18nUtil.getMessage(MessageKeys.MENU_HISTORY));
        title.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, +1));

        clearHistoryButton = new ClearButton(IconUtil.SIZE_MEDIUM);
        clearHistoryButton.setToolTipText(I18nUtil.getMessage(MessageKeys.HISTORY_CLEAR));
        clearHistoryButton.getAccessibleContext().setAccessibleName(
                I18nUtil.getMessage(MessageKeys.HISTORY_CLEAR));
        clearHistoryButton.setFocusable(false);
        clearHistoryButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearHistoryButton.putClientProperty(
                FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON
        );
        clearHistoryButton.setEnabled(false);
        clearHistoryButton.addActionListener(e -> clearRequestHistory());
        JPanel btnPanel = ToolWindowActionToolbar.inlineRight(clearHistoryButton);

        titlePanel.add(title, BorderLayout.WEST);
        titlePanel.add(btnPanel, BorderLayout.EAST);

        JPanel filterPanel = new JPanel(new BorderLayout(10, 0));
        ToolWindowSurfaceStyle.applyCard(filterPanel);
        filterPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setOpaque(false);

        searchField = new SearchTextField();
        searchField.setPlaceholderText(I18nUtil.getMessage(MessageKeys.HISTORY_SEARCH_PLACEHOLDER));
        searchField.setPreferredSize(new Dimension(180, 28));
        searchField.setMinimumSize(new Dimension(50, 28));
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        searchPanel.add(searchField, BorderLayout.CENTER);

        filterPanel.add(searchPanel, BorderLayout.CENTER);

        headerPanel.add(titlePanel, BorderLayout.NORTH);
        headerPanel.add(filterPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    private JSplitPane createWorkspaceSplitPane() {
        JPanel sidebarPanel = createSidebarPanel();
        JPanel detailPanel = createHistoryDetailPanel();
        JSplitPane splitPane = createHistorySplitPane(sidebarPanel, detailPanel);
        splitPane.setResizeWeight(0.22);
        return splitPane;
    }

    private JPanel createSidebarPanel() {
        JPanel sidebarPanel = new JPanel(new BorderLayout());
        sidebarPanel.setOpaque(false);
        sidebarPanel.add(createHeaderPanel(), BorderLayout.NORTH);
        sidebarPanel.add(createHistoryListScrollPane(), BorderLayout.CENTER);
        sidebarPanel.setMinimumSize(new Dimension(260, 160));
        return sidebarPanel;
    }

    private JScrollPane createHistoryListScrollPane() {
        historyListModel = new DefaultListModel<>();
        historyList = new JList<>(historyListModel) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int index = locationToIndex(event.getPoint());
                Rectangle bounds = index >= 0 ? getCellBounds(index, index) : null;
                if (bounds == null || !bounds.contains(event.getPoint())) {
                    return null;
                }
                Object value = historyListModel.get(index);
                if (value instanceof RequestHistoryItem item) {
                    return item.getUrl();
                }
                return null;
            }

            @Override
            protected void processMouseEvent(MouseEvent event) {
                if (interceptDateGroupMouseEvent(event, this)) {
                    return;
                }
                super.processMouseEvent(event);
            }
        };
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setFixedCellHeight(-1);
        historyList.setCellRenderer(new OptimizedHistoryListCellRenderer());
        historyList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        ToolTipManager.sharedInstance().registerComponent(historyList);

        JScrollPane listScroll = new JScrollPane(historyList);
        listScroll.setPreferredSize(new Dimension(HISTORY_SIDEBAR_WIDTH, 240));
        listScroll.setMinimumSize(new Dimension(300, 240));
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ToolWindowSurfaceStyle.applyListScrollPaneCard(listScroll, historyList);

        return listScroll;
    }

    private JPanel createHistoryDetailPanel() {
        JPanel historyDetailPanel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(historyDetailPanel);
        historyDetailPanel.add(createDetailTopPanel(), BorderLayout.NORTH);
        historyDetailPanel.add(createDetailTabs(), BorderLayout.CENTER);

        clearDetailPanes();
        historyDetailPanel.setMinimumSize(new Dimension(420, 220));
        return historyDetailPanel;
    }

    static JSplitPane createHistorySplitPane(Component historyPanel, Component detailPanel) {
        return AppToolWindowChrome.createHorizontalCardSplitPane(
                historyPanel,
                detailPanel,
                HISTORY_SIDEBAR_WIDTH
        );
    }

    private JPanel createDetailTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(createDetailHeaderPanel(), BorderLayout.NORTH);
        topPanel.add(createDetailOverviewPanel(), BorderLayout.CENTER);
        return topPanel;
    }

    private JPanel createDetailHeaderPanel() {
        JPanel detailHeaderPanel = new JPanel(new BorderLayout(12, 0));
        ToolWindowSurfaceStyle.applyCard(detailHeaderPanel);
        detailHeaderPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel detailInfoPanel = new JPanel(new BorderLayout(0, 2));
        detailInfoPanel.setOpaque(false);

        detailTitleLabel = new JLabel(" ");
        detailTitleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, +1));

        detailMetaLabel = new JLabel(" ");
        detailMetaLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        detailMetaLabel.setForeground(ModernColors.getTextSecondary());

        detailInfoPanel.add(detailTitleLabel, BorderLayout.NORTH);
        detailInfoPanel.add(detailMetaLabel, BorderLayout.CENTER);

        JButton openRequestButton = createActionButton(
                I18nUtil.getMessage(MessageKeys.CREATE_NEW_REQUEST),
                "icons/request.svg"
        );
        openRequestButton.addActionListener(e -> openSelectedHistoryAsRequest());

        JButton deleteItemButton = createActionButton(
                I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_DELETE),
                "icons/delete.svg"
        );
        deleteItemButton.addActionListener(e -> deleteSelectedHistory());
        detailActionButtons.clear();
        detailActionButtons.add(openRequestButton);
        detailActionButtons.add(deleteItemButton);

        JPanel actionsPanel = ToolWindowActionToolbar.inlineRight(openRequestButton, deleteItemButton);

        detailHeaderPanel.add(detailInfoPanel, BorderLayout.CENTER);
        detailHeaderPanel.add(actionsPanel, BorderLayout.EAST);
        return detailHeaderPanel;
    }

    private JPanel createDetailOverviewPanel() {
        JPanel overviewPanel = new JPanel(new GridLayout(1, 4, 0, 0));
        ToolWindowSurfaceStyle.applyCard(overviewPanel);
        overviewPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));

        overviewPanel.add(createOverviewCard(I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_ENDPOINT_CARD), false, card -> {
            endpointValueLabel = card.valueLabel();
            endpointDetailLabel = card.detailLabel();
        }));
        overviewPanel.add(createOverviewCard(I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_PAYLOAD_CARD), true, card -> {
            payloadValueLabel = card.valueLabel();
            payloadDetailLabel = card.detailLabel();
        }));
        overviewPanel.add(createOverviewCard(I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_STRUCTURE_CARD), true, card -> {
            structureValueLabel = card.valueLabel();
            structureDetailLabel = card.detailLabel();
        }));
        overviewPanel.add(createOverviewCard(I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_RESULT_CARD), true, card -> {
            resultValueLabel = card.valueLabel();
            resultDetailLabel = card.detailLabel();
        }));
        return overviewPanel;
    }

    private JPanel createOverviewCard(String title, boolean withLeadingDivider,
                                      java.util.function.Consumer<OverviewCardLabels> binder) {
        JPanel cardPanel = new JPanel(new BorderLayout(0, 2));
        cardPanel.setOpaque(false);
        cardPanel.setBorder(BorderFactory.createEmptyBorder(6, withLeadingDivider ? 16 : 12, 10, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        titleLabel.setForeground(ModernColors.getTextHint());

        JLabel valueLabel = new JLabel(" ");
        valueLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        valueLabel.setForeground(ModernColors.getTextPrimary());

        JLabel detailLabel = new JLabel(" ");
        detailLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        detailLabel.setForeground(ModernColors.getTextSecondary());

        cardPanel.add(titleLabel, BorderLayout.NORTH);
        cardPanel.add(valueLabel, BorderLayout.CENTER);
        cardPanel.add(detailLabel, BorderLayout.SOUTH);
        binder.accept(new OverviewCardLabels(valueLabel, detailLabel));
        return cardPanel;
    }

    private JButton createActionButton(String text, String iconPath) {
        JButton button = new JButton(IconUtil.createThemed(iconPath, 16, 16));
        button.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        button.setFocusable(false);
        button.setMargin(new Insets(4, 4, 4, 4));
        button.setToolTipText(text);
        button.setPreferredSize(new Dimension(30, 30));
        button.setMinimumSize(new Dimension(30, 30));
        button.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return button;
    }

    private JTabbedPane createDetailTabs() {
        JTabbedPane historyDetailTabPane = new JTabbedPane();
        ToolWindowSurfaceStyle.applyTabbedPaneCard(historyDetailTabPane);
        historyDetailTabPane.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
        historyDetailTabPane.putClientProperty(FlatClientProperties.TABBED_PANE_HAS_FULL_BORDER, false);
        historyDetailTabPane.putClientProperty(FlatClientProperties.TABBED_PANE_SHOW_CONTENT_SEPARATOR, false);

        requestPane = createDetailPane();
        responsePane = createDetailPane();
        timingPane = createDetailPane();
        eventPane = createDetailPane();

        historyDetailTabPane.addTab(I18nUtil.getMessage(MessageKeys.TAB_REQUEST), new JScrollPane(requestPane));
        historyDetailTabPane.addTab(I18nUtil.getMessage(MessageKeys.TAB_RESPONSE), new JScrollPane(responsePane));
        historyDetailTabPane.addTab(I18nUtil.getMessage(MessageKeys.TAB_TIMING), new JScrollPane(timingPane));
        historyDetailTabPane.addTab(I18nUtil.getMessage(MessageKeys.TAB_EVENTS), new JScrollPane(eventPane));

        for (int i = 0; i < historyDetailTabPane.getTabCount(); i++) {
            JScrollPane scrollPane = (JScrollPane) historyDetailTabPane.getComponentAt(i);
            scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            ToolWindowSurfaceStyle.applyScrollPaneCard(scrollPane);
            scrollPane.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        }
        return historyDetailTabPane;
    }

    private JTextPane createDetailPane() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
        ToolWindowSurfaceStyle.applyTextComponentCard(pane);
        return pane;
    }

    /**
     * 列表项渲染器。
     * 分组显示日期，条目显示状态码、耗时和时间，避免历史列表只剩 URL。
     */
    private class OptimizedHistoryListCellRenderer implements ListCellRenderer<Object> {
        private final Font boldFont;
        private final Font plainFont;
        private final JPanel itemRootPanel;
        private final JPanel itemCardPanel;
        private final JLabel titleLabel;
        private final JLabel urlLabel;
        private final JLabel groupLabel;

        private OptimizedHistoryListCellRenderer() {
            Font baseFont = FontsUtil.getDefaultFont(Font.PLAIN);
            boldFont = baseFont.deriveFont(Font.BOLD);
            plainFont = baseFont;

            itemRootPanel = new JPanel(new BorderLayout());
            itemRootPanel.setOpaque(true);
            itemRootPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            itemCardPanel = new JPanel(new BorderLayout(0, 4));
            itemCardPanel.setOpaque(true);
            itemCardPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

            titleLabel = new JLabel();
            titleLabel.setFont(boldFont);
            titleLabel.setVerticalAlignment(SwingConstants.TOP);

            urlLabel = new JLabel();
            urlLabel.setFont(plainFont);
            urlLabel.setVerticalAlignment(SwingConstants.TOP);

            itemCardPanel.add(titleLabel, BorderLayout.NORTH);
            itemCardPanel.add(urlLabel, BorderLayout.CENTER);
            itemRootPanel.add(itemCardPanel, BorderLayout.CENTER);

            groupLabel = new JLabel();
            groupLabel.setFont(boldFont);
            groupLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
            groupLabel.setOpaque(true);
            groupLabel.setPreferredSize(new Dimension(10, 28));
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (value instanceof HistoryGroupHeader groupHeader) {
                String arrow = groupHeader.collapsed() ? "▸" : "▾";
                this.groupLabel.setText(arrow + " " + groupHeader.label() + "  (" + groupHeader.count() + ")");
                this.groupLabel.setForeground(HistoryTheme.groupHeaderForeground());
                this.groupLabel.setBackground(list.getBackground());
                return this.groupLabel;
            }

            if (value instanceof RequestHistoryItem item) {
                HistoryVisualInfo visualInfo = summarizeRequestTarget(item.getUrl());
                Color metaColor = ModernColors.getTextSecondary();
                Color statusColor = resolveStatusColor(item.getResponseCode());
                String statusText = item.getResponseCode() > 0 ? String.valueOf(item.getResponseCode()) : "-";
                String durationText = item.getResponse() != null ? formatDuration(item.getResponse().costMs) : "-";
                String timeText = formatTime(item.getRequestTime());
                String hostText = abbreviateMiddle(visualInfo.subtitle(), 52);
                boolean hovered = !isSelected && index == hoveredHistoryIndex;

                itemRootPanel.setBackground(list.getBackground());
                itemCardPanel.setBackground(isSelected
                        ? getSelectionBackground()
                        : hovered ? getHoverBackground() : ModernColors.getCardBackgroundColor());
                itemCardPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

                titleLabel.setForeground(isSelected ? HistoryTheme.selectedTitleForeground() : ModernColors.getTextPrimary());
                titleLabel.setText("<html>" + highlightMatches(abbreviateMiddle(visualInfo.title(), 56)) + "</html>");

                urlLabel.setForeground(metaColor);
                urlLabel.setText("<html><span style='color:" + toHex(metaColor) + ";'>"
                        + highlightMatches(item.getMethod())
                        + "  "
                        + highlightMatches(hostText)
                        + "  "
                        + "</span><span style='color:" + toHex(statusColor) + ";font-weight:bold;'>"
                        + highlightMatches(statusText)
                        + "</span><span style='color:" + toHex(metaColor) + ";'>  "
                        + escapeHtml(durationText) + "  " + escapeHtml(timeText)
                        + "</span></html>");
                return itemRootPanel;
            }
            return new JLabel("");
        }

        private static String formatDuration(long costMs) {
            return costMs > 0 ? costMs + " ms" : "-";
        }

        private static String formatTime(long timestamp) {
            return new SimpleDateFormat("HH:mm:ss").format(new Date(timestamp));
        }

        private static Color resolveStatusColor(int statusCode) {
            if (statusCode >= 200 && statusCode < 400) {
                return ModernColors.getSuccess();
            }
            if (statusCode >= 400) {
                return ModernColors.getError();
            }
            return ModernColors.getTextSecondary();
        }

        private static String toHex(Color color) {
            return ModernColors.toHtmlColor(color);
        }

        private static String escapeHtml(String text) {
            if (text == null) {
                return "";
            }
            return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
        }

        private static Color getSelectionBackground() {
            return HistoryTheme.selectionBackground();
        }

        private static Color getHoverBackground() {
            return HistoryTheme.hoverBackground();
        }
    }

    private void clearDetailPanes() {
        showEmptyDetailState(hasActiveFilters());
    }

    private void showEmptyDetailState(boolean filteredEmpty) {
        String emptyBodyHtml = filteredEmpty
                ? I18nUtil.getMessage(MessageKeys.HISTORY_EMPTY_FILTERED)
                : I18nUtil.getMessage(MessageKeys.HISTORY_EMPTY_BODY);
        requestPane.setText(emptyBodyHtml);
        responsePane.setText(emptyBodyHtml);
        timingPane.setText(emptyBodyHtml);
        eventPane.setText(emptyBodyHtml);
        detailTitleLabel.setText(" ");
        detailTitleLabel.setToolTipText(null);
        detailMetaLabel.setText(" ");
        setOverviewCardState(endpointValueLabel, endpointDetailLabel, " ", " ");
        setOverviewCardState(payloadValueLabel, payloadDetailLabel, " ", " ");
        setOverviewCardState(structureValueLabel, structureDetailLabel, " ", " ");
        setOverviewCardState(resultValueLabel, resultDetailLabel, " ", " ");
        currentSelectedItem = null;
        updateActionButtons(null);
    }

    private void updateDetailPanes(RequestHistoryItem item) {
        if (item == null) {
            clearDetailPanes();
            return;
        }

        if (item == currentSelectedItem) {
            return;
        }

        currentSelectedItem = item;
        updateDetailSummary(item);
        updateActionButtons(item);

        SwingWorker<Map<String, String>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<String, String> doInBackground() {
                Map<String, String> htmlMap = new LinkedHashMap<>();
                try {
                    htmlMap.put("request", HttpHtmlRenderer.renderRequest(item.getRequest()));
                    htmlMap.put("response", HttpHtmlRenderer.renderResponse(item.getResponse()));
                    htmlMap.put("timing", HttpHtmlRenderer.renderTimingInfo(item.getResponse()));
                    htmlMap.put("event", HttpHtmlRenderer.renderEventInfo(item.getResponse()));
                } catch (Exception e) {
                    String errorHtml = "<html><body style='font-family:monospace;font-size:9px;color:"
                            + ModernColors.toHtmlColor(ModernColors.getTextPrimary()) + ";'>"
                            + "<div style='color:" + ModernColors.toHtmlColor(ModernColors.getError())
                            + ";'>渲染详情时出错: " + escapeHtml(e.getMessage()) + "</div>"
                            + "</body></html>";
                    htmlMap.put("error", errorHtml);
                }
                return htmlMap;
            }

            @Override
            protected void done() {
                try {
                    if (currentSelectedItem != item) {
                        return;
                    }
                    Map<String, String> htmlMap = get();
                    if (htmlMap.containsKey("error")) {
                        String errorHtml = htmlMap.get("error");
                        requestPane.setText(errorHtml);
                        responsePane.setText(errorHtml);
                        timingPane.setText(errorHtml);
                        eventPane.setText(errorHtml);
                    } else {
                        requestPane.setText(htmlMap.get("request"));
                        requestPane.setCaretPosition(0);

                        responsePane.setText(htmlMap.get("response"));
                        responsePane.setCaretPosition(0);

                        timingPane.setText(htmlMap.get("timing"));
                        timingPane.setCaretPosition(0);

                        eventPane.setText(htmlMap.get("event"));
                        eventPane.setCaretPosition(0);
                    }
                } catch (Exception ignored) {
                    // Ignore if cancelled or interrupted
                }
            }
        };
        worker.execute();
    }

    private void updateDetailSummary(RequestHistoryItem item) {
        HistoryVisualInfo visualInfo = summarizeRequestTarget(item.getUrl());
        String protocol = resolveProtocol(item).getProtocol();
        String duration = item.getResponse() != null && item.getResponse().costMs > 0 ? item.getResponse().costMs + " ms" : "-";
        String status = item.getResponseCode() > 0 ? String.valueOf(item.getResponseCode()) : "-";
        String timestamp = detailTimeFormatter.format(new Date(item.getRequestTime()));
        String secondary = toHex(ModernColors.getTextSecondary());
        String statusColor = toHex(resolveStatusColor(item.getResponseCode()));

        detailTitleLabel.setText("<html>" + highlightMatches(abbreviateMiddle(visualInfo.title(), 120)) + "</html>");
        detailTitleLabel.setToolTipText(visualInfo.fullUrl());
        detailMetaLabel.setText("<html><span style='color:" + secondary + ";'>"
                + highlightMatches(item.getMethod()) + "  "
                + highlightMatches(visualInfo.subtitle()) + "  "
                + escapeHtml(protocol) + "  "
                + "<span style='color:" + statusColor + ";'>" + highlightMatches(status) + "</span>  "
                + escapeHtml(duration) + "  "
                + escapeHtml(timestamp)
                + "</span></html>");
        updateOverviewCards(item, visualInfo, protocol, duration, status, timestamp);
    }

    private void updateActionButtons(RequestHistoryItem item) {
        boolean enabled = item != null;
        detailActionButtons.forEach(button -> button.setEnabled(enabled));
    }

    @Override
    protected void registerListeners() {
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isUpdating && !suppressSelectionSync) {
                Object selectedValue = historyList.getSelectedValue();
                if (selectedValue instanceof HistoryGroupHeader) {
                    return;
                }
                updateDetailPanes(getSelectedHistoryItem());
            }
        });

        historyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowContextMenu(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoveredHistoryIndex != -1) {
                    hoveredHistoryIndex = -1;
                    historyList.repaint();
                }
            }
        });

        historyList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int hoveredIndex = historyList.locationToIndex(e.getPoint());
                Rectangle cellBounds = hoveredIndex >= 0 ? historyList.getCellBounds(hoveredIndex, hoveredIndex) : null;
                if (cellBounds == null || !cellBounds.contains(e.getPoint()) || historyListModel.get(hoveredIndex) instanceof HistoryGroupHeader) {
                    hoveredIndex = -1;
                }
                if (hoveredHistoryIndex != hoveredIndex) {
                    hoveredHistoryIndex = hoveredIndex;
                    historyList.repaint();
                }
            }
        });

        historyList.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openHistoryRequest");
        historyList.getActionMap().put("openHistoryRequest", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelectedHistoryAsRequest();
            }
        });

        historyList.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteHistoryRequest");
        historyList.getActionMap().put("deleteHistoryRequest", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                deleteSelectedHistory();
            }
        });

        int modifier = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        historyList.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, modifier), "openHistoryRequestWithShortcut");
        historyList.getActionMap().put("openHistoryRequestWithShortcut", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelectedHistoryAsRequest();
            }
        });

        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, modifier), "focusHistorySearch"
        );
        getActionMap().put("focusHistorySearch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });

        searchField.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearHistorySearch"
        );
        searchField.getActionMap().put("clearHistorySearch", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                searchField.setText("");
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleApplyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleApplyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleApplyFilters();
            }
        });
        searchField.addPropertyChangeListener("caseSensitive", e -> scheduleApplyFilters());
        searchField.addPropertyChangeListener("wholeWord", e -> scheduleApplyFilters());

    }

    public void addRequestHistory(PreparedRequest req, HttpResponse resp) {
        long requestTime = System.currentTimeMillis();
        RequestHistoryItem newItem = BeanFactory.getBean(HistoryPersistenceService.class)
                .addHistory(req, resp, requestTime);

        allHistoryItems.add(0, newItem);
        int maxCount = SettingManager.getMaxHistoryCount();
        while (allHistoryItems.size() > maxCount) {
            allHistoryItems.remove(allHistoryItems.size() - 1);
        }
        rebuildHistoryListModel(newItem);
    }

    private void applyFilters() {
        rebuildHistoryListModel(currentSelectedItem);
    }

    private void scheduleApplyFilters() {
        if (filterDebounceTimer == null) {
            applyFilters();
            return;
        }
        filterDebounceTimer.restart();
    }

    private void rebuildHistoryListModel(RequestHistoryItem preferredSelection) {
        if (historyListModel == null) {
            return;
        }
        if (clearHistoryButton != null) {
            clearHistoryButton.setEnabled(!allHistoryItems.isEmpty());
        }

        List<RequestHistoryItem> itemsSnapshot = new ArrayList<>(allHistoryItems);
        String keyword = searchField != null ? searchField.getText().trim() : "";
        boolean caseSensitive = searchField != null && searchField.isCaseSensitive();
        boolean wholeWord = searchField != null && searchField.isWholeWord();
        Set<String> collapsedGroupsSnapshot = new HashSet<>(collapsedGroups);
        updateDateCache();
        long todayStart = todayStartCache;
        long yesterdayStart = yesterdayStartCache;
        int buildVersion = historyListBuildVersion.incrementAndGet();

        if (historyListBuildWorker != null && !historyListBuildWorker.isDone()) {
            historyListBuildWorker.cancel(true);
        }

        historyListBuildWorker = new SwingWorker<>() {
            @Override
            protected HistoryListBuildResult doInBackground() {
                List<RequestHistoryItem> filteredItems = getFilteredHistoryItems(
                        itemsSnapshot, keyword, caseSensitive, wholeWord
                );
                List<Object> displayItems = buildDisplayItems(
                        filteredItems, collapsedGroupsSnapshot, todayStart, yesterdayStart
                );
                return new HistoryListBuildResult(filteredItems, displayItems);
            }

            @Override
            protected void done() {
                if (isCancelled() || buildVersion != historyListBuildVersion.get()) {
                    return;
                }
                try {
                    HistoryListBuildResult result = get();
                    isUpdating = true;
                    try {
                        historyListModel.clear();
                        for (Object displayItem : result.displayItems()) {
                            historyListModel.addElement(displayItem);
                        }
                    } finally {
                        isUpdating = false;
                    }

                    if (searchField != null) {
                        searchField.setNoResult(!keyword.isEmpty() && result.filteredItems().isEmpty());
                    }
                    restoreSelection(preferredSelection, result.filteredItems());
                } catch (Exception ignored) {
                    // Ignore if cancelled or interrupted
                }
            }
        };
        historyListBuildWorker.execute();
    }

    private List<RequestHistoryItem> getFilteredHistoryItems(List<RequestHistoryItem> sourceItems,
                                                             String keyword,
                                                             boolean caseSensitive,
                                                             boolean wholeWord) {
        List<RequestHistoryItem> filteredItems = new ArrayList<>();
        for (RequestHistoryItem item : sourceItems) {
            if (!keyword.isEmpty() && !matchesKeyword(item, keyword, caseSensitive, wholeWord)) {
                continue;
            }
            filteredItems.add(item);
        }
        return filteredItems;
    }

    private List<Object> buildDisplayItems(List<RequestHistoryItem> filteredItems,
                                           Set<String> collapsedGroupsSnapshot,
                                           long todayStart,
                                           long yesterdayStart) {
        Map<String, List<RequestHistoryItem>> dayMap = new LinkedHashMap<>();
        SimpleDateFormat groupDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        for (RequestHistoryItem item : filteredItems) {
            String groupLabel = buildDateGroupLabel(item.getRequestTime(), todayStart, yesterdayStart, groupDateFormatter);
            dayMap.computeIfAbsent(groupLabel, key -> new ArrayList<>()).add(item);
        }

        List<Object> result = new ArrayList<>();
        for (Map.Entry<String, List<RequestHistoryItem>> entry : dayMap.entrySet()) {
            boolean collapsed = collapsedGroupsSnapshot.contains(entry.getKey());
            result.add(new HistoryGroupHeader(entry.getKey(), entry.getValue().size(), collapsed));
            if (!collapsed) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    private void restoreSelection(RequestHistoryItem preferredSelection, List<RequestHistoryItem> filteredItems) {
        Object preferredDisplayObject = preferredSelection != null ? findDisplayObjectForItem(preferredSelection) : null;
        if (preferredDisplayObject != null && filteredItems.contains(preferredSelection)) {
            historyList.setSelectedValue(preferredDisplayObject, true);
            return;
        }
        Object firstSelectable = findFirstSelectableDisplayObject();
        if (firstSelectable != null) {
            historyList.setSelectedValue(firstSelectable, true);
            return;
        }
        historyList.clearSelection();
        showEmptyDetailState(hasActiveFilters());
    }

    private Object findDisplayObjectForItem(RequestHistoryItem item) {
        for (int i = 0; i < historyListModel.size(); i++) {
            Object value = historyListModel.get(i);
            if (value == item) {
                return value;
            }
        }
        return null;
    }

    private Object findFirstSelectableDisplayObject() {
        for (int i = 0; i < historyListModel.size(); i++) {
            Object value = historyListModel.get(i);
            if (value instanceof RequestHistoryItem) {
                return value;
            }
        }
        return null;
    }

    private boolean hasActiveFilters() {
        return searchField != null && !searchField.getText().trim().isEmpty();
    }

    private void maybeShowContextMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int index = historyList.locationToIndex(e.getPoint());
        Rectangle bounds = index >= 0 ? historyList.getCellBounds(index, index) : null;
        if (bounds == null || !bounds.contains(e.getPoint())) {
            return;
        }
        Object value = historyListModel.get(index);
        RequestHistoryItem item;
        if (value instanceof RequestHistoryItem requestHistoryItem) {
            item = requestHistoryItem;
        } else {
            return;
        }
        historyList.setSelectedValue(value, true);

        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);
        JMenuItem openItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.CREATE_NEW_REQUEST),
                IconUtil.createThemed("icons/request.svg", 14, 14));
        openItem.addActionListener(event -> openSelectedHistoryAsRequest());

        JMenuItem deleteItem = new JMenuItem(I18nUtil.getMessage(MessageKeys.COLLECTIONS_MENU_DELETE),
                IconUtil.createThemed("icons/delete.svg", 14, 14));
        deleteItem.addActionListener(event -> deleteSelectedHistory());

        menu.add(openItem);
        menu.addSeparator();
        menu.add(deleteItem);
        menu.show(historyList, e.getX(), e.getY());
    }

    private boolean interceptDateGroupMouseEvent(MouseEvent e, JList<?> list) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return false;
        }
        int index = list.locationToIndex(e.getPoint());
        Rectangle bounds = index >= 0 ? list.getCellBounds(index, index) : null;
        if (bounds == null || !bounds.contains(e.getPoint())) {
            return false;
        }
        Object value = historyListModel.get(index);
        if (!(value instanceof HistoryGroupHeader groupHeader)) {
            return false;
        }
        if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            toggleDateGroup(groupHeader.label());
        }
        e.consume();
        return true;
    }

    private void toggleDateGroup(String label) {
        RequestHistoryItem preferredSelection = currentSelectedItem;
        suppressSelectionSync = true;
        SwingUtilities.invokeLater(() -> {
            try {
                if (collapsedGroups.contains(label)) {
                    collapsedGroups.remove(label);
                } else {
                    collapsedGroups.add(label);
                }
                historyList.clearSelection();
                rebuildHistoryListModel(preferredSelection);
            } finally {
                suppressSelectionSync = false;
            }
        });
    }

    private boolean matchesKeyword(RequestHistoryItem item, String keyword, boolean caseSensitive, boolean wholeWord) {
        return contains(item.getMethod(), keyword, caseSensitive, wholeWord)
                || contains(item.getUrl(), keyword, caseSensitive, wholeWord)
                || contains(String.valueOf(item.getResponseCode()), keyword, caseSensitive, wholeWord)
                || contains(item.getRequest() != null ? item.getRequest().body : null, keyword, caseSensitive, wholeWord)
                || contains(item.getRequest() != null ? item.getRequest().sentRequestBody : null, keyword, caseSensitive, wholeWord)
                || contains(item.getResponse() != null ? item.getResponse().body : null, keyword, caseSensitive, wholeWord);
    }

    private boolean contains(String value, String keyword, boolean caseSensitive, boolean wholeWord) {
        if (value == null) {
            return false;
        }
        String candidate = caseSensitive ? value : value.toLowerCase(Locale.ROOT);
        String expected = caseSensitive ? keyword : keyword.toLowerCase(Locale.ROOT);
        if (!wholeWord) {
            return candidate.contains(expected);
        }
        return containsWholeWord(candidate, expected);
    }

    private boolean containsWholeWord(String text, String keyword) {
        BreakIterator iterator = BreakIterator.getWordInstance(Locale.getDefault());
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String token = text.substring(start, end);
            if (token.equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private RequestHistoryItem getSelectedHistoryItem() {
        Object selectedValue = historyList != null ? historyList.getSelectedValue() : null;
        if (selectedValue instanceof RequestHistoryItem item) {
            return item;
        }
        return null;
    }

    private void openSelectedHistoryAsRequest() {
        RequestHistoryItem item = getSelectedHistoryItem();
        if (item == null || item.getRequest() == null) {
            return;
        }

        SidebarTabPanel sidebarTabPanel = UiSingletonFactory.getInstance(SidebarTabPanel.class);
        if (!sidebarTabPanel.showTab(SidebarTab.COLLECTIONS)) {
            NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.HISTORY_OPEN_REQUEST_TAB_HIDDEN));
            return;
        }

        RequestItemProtocolEnum protocol = resolveProtocol(item);
        HttpRequestItem requestItem = createRequestItemFromHistory(item, protocol);
        RequestEditorPanel requestEditPanel = UiSingletonFactory.getInstance(RequestEditorPanel.class);
        RequestEditSubPanel subPanel = requestEditPanel.addNewTab(buildTabTitle(item), protocol);
        subPanel.initPanelData(requestItem);
    }

    private void deleteSelectedHistory() {
        RequestHistoryItem item = getSelectedHistoryItem();
        if (item == null) {
            return;
        }
        BeanFactory.getBean(HistoryPersistenceService.class).removeHistory(item);
        allHistoryItems.remove(item);
        rebuildHistoryListModel(null);
    }

    private HttpRequestItem createRequestItemFromHistory(RequestHistoryItem item, RequestItemProtocolEnum protocol) {
        PreparedRequest request = item.getRequest();
        HttpRequestItem requestItem = new HttpRequestItem();
        requestItem.setName("");
        requestItem.setProtocol(protocol);
        requestItem.setMethod(request.method != null ? request.method : item.getMethod());
        String historyUrl = request.url != null ? request.url : item.getUrl();
        requestItem.setUrl(com.laker.postman.request.util.HttpUrlUtil.decodeQueryForDisplay(historyUrl));
        requestItem.setHeadersList(copyHeaders(request));
        requestItem.setParamsList(copyParams(request.paramsList));
        requestItem.setFormDataList(copyFormData(request.formDataList));
        requestItem.setUrlencodedList(copyUrlencoded(request.urlencodedList));
        requestItem.setFollowRedirects(request.followRedirects);
        requestItem.setCookieJarEnabled(request.cookieJarEnabled);
        requestItem.setHttpVersion(request.httpVersion);
        requestItem.setRequestTimeoutMs(request.requestTimeoutMs > 0 ? request.requestTimeoutMs : null);
        requestItem.setWebSocketPingIntervalMs(request.webSocketPingIntervalMs);
        requestItem.setPrescript(request.prescript == null ? "" : request.prescript);
        requestItem.setPostscript(request.postscript == null ? "" : request.postscript);

        String requestBody = request.body != null ? request.body : request.sentRequestBody;
        requestItem.setBody(requestBody == null ? "" : requestBody);
        requestItem.setBodyType(resolveBodyType(request));
        return requestItem;
    }

    private List<HttpHeader> copyHeaders(PreparedRequest request) {
        List<HttpHeader> copiedHeaders = copyHeadersList(request.headersList);
        if (!copiedHeaders.isEmpty()) {
            return copiedHeaders;
        }
        if (request.sentHeadersList == null || request.sentHeadersList.isEmpty()) {
            return copiedHeaders;
        }
        for (HttpHeader header : request.sentHeadersList) {
            if (header != null) {
                copiedHeaders.add(new HttpHeader(header.isEnabled(), header.getKey(), header.getValue(), header.getDescription()));
            }
        }
        return copiedHeaders;
    }

    private List<HttpHeader> copyHeadersList(List<HttpHeader> source) {
        List<HttpHeader> copiedHeaders = new ArrayList<>();
        if (source == null) {
            return copiedHeaders;
        }
        for (HttpHeader header : source) {
            copiedHeaders.add(new HttpHeader(header.isEnabled(), header.getKey(), header.getValue(), header.getDescription()));
        }
        return copiedHeaders;
    }

    private List<HttpParam> copyParams(List<HttpParam> source) {
        List<HttpParam> copiedParams = new ArrayList<>();
        if (source == null) {
            return copiedParams;
        }
        for (HttpParam param : source) {
            copiedParams.add(new HttpParam(param.isEnabled(), param.getKey(), param.getValue(), param.getDescription()));
        }
        return copiedParams;
    }

    private List<HttpFormData> copyFormData(List<HttpFormData> source) {
        List<HttpFormData> copiedFormData = new ArrayList<>();
        if (source == null) {
            return copiedFormData;
        }
        for (HttpFormData formData : source) {
            copiedFormData.add(new HttpFormData(
                    formData.isEnabled(),
                    formData.getKey(),
                    formData.getType(),
                    formData.getValue(),
                    formData.getDescription()
            ));
        }
        return copiedFormData;
    }

    private List<HttpFormUrlencoded> copyUrlencoded(List<HttpFormUrlencoded> source) {
        List<HttpFormUrlencoded> copiedUrlencoded = new ArrayList<>();
        if (source == null) {
            return copiedUrlencoded;
        }
        for (HttpFormUrlencoded formUrlencoded : source) {
            copiedUrlencoded.add(new HttpFormUrlencoded(
                    formUrlencoded.isEnabled(),
                    formUrlencoded.getKey(),
                    formUrlencoded.getValue(),
                    formUrlencoded.getDescription()
            ));
        }
        return copiedUrlencoded;
    }

    private String resolveBodyType(PreparedRequest request) {
        if (request.formDataList != null && !request.formDataList.isEmpty()) {
            return RequestBodyPanel.BODY_TYPE_FORM_DATA;
        }
        if (request.urlencodedList != null && !request.urlencodedList.isEmpty()) {
            return RequestBodyPanel.BODY_TYPE_FORM_URLENCODED;
        }
        String bodyType = request.bodyType;
        if (bodyType == null || bodyType.isBlank()) {
            return (request.sentRequestBody != null && !request.sentRequestBody.isBlank())
                    || (request.body != null && !request.body.isBlank())
                    ? RequestBodyPanel.BODY_TYPE_RAW
                    : RequestBodyPanel.BODY_TYPE_NONE;
        }
        return bodyType;
    }

    private RequestItemProtocolEnum resolveProtocol(RequestHistoryItem item) {
        if (item.getResponse() != null && item.getResponse().isSse) {
            return RequestItemProtocolEnum.SSE;
        }
        String url = item.getUrl() != null ? item.getUrl().toLowerCase(Locale.ROOT) : "";
        if (url.startsWith("ws://") || url.startsWith("wss://")) {
            return RequestItemProtocolEnum.WEBSOCKET;
        }
        return RequestItemProtocolEnum.HTTP;
    }

    private static HistoryVisualInfo summarizeRequestTarget(String url) {
        if (url == null || url.isBlank()) {
            return new HistoryVisualInfo("-", "-", "");
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            String path = uri.getPath();
            String query = uri.getQuery();

            String title = (path != null && !path.isBlank() && !"/".equals(path))
                    ? path
                    : (host != null && !host.isBlank() ? host : url);
            if (query != null && !query.isBlank()) {
                title = title + "?" + query;
            }
            String subtitle = host != null && !host.isBlank()
                    ? host
                    : uri.getScheme() != null ? uri.getScheme() : url;
            return new HistoryVisualInfo(title, subtitle, url);
        } catch (Exception ignored) {
            return new HistoryVisualInfo(url, "-", url);
        }
    }

    private static String abbreviateMiddle(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        int head = Math.max(12, maxLength / 2);
        int tail = Math.max(10, maxLength - head - 3);
        return value.substring(0, head) + "..." + value.substring(value.length() - tail);
    }

    private String highlightMatches(String value) {
        if (value == null || value.isBlank() || searchField == null) {
            return escapeHtml(value);
        }
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            return escapeHtml(value);
        }
        boolean caseSensitive = searchField.isCaseSensitive();
        boolean wholeWord = searchField.isWholeWord();
        String source = caseSensitive ? value : value.toLowerCase(Locale.ROOT);
        String expected = caseSensitive ? keyword : keyword.toLowerCase(Locale.ROOT);
        String highlightColor = HistoryTheme.searchHighlightBackgroundHex();

        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int matchStart = findMatchStart(source, expected, cursor, wholeWord);
            if (matchStart < 0) {
                builder.append(escapeHtml(value.substring(cursor)));
                break;
            }
            int matchEnd = matchStart + expected.length();
            builder.append(escapeHtml(value.substring(cursor, matchStart)));
            builder.append("<span style='background:").append(highlightColor)
                    .append(";border-radius:3px;padding:0 1px;'>")
                    .append(escapeHtml(value.substring(matchStart, matchEnd)))
                    .append("</span>");
            cursor = matchEnd;
        }
        return builder.toString();
    }

    private int findMatchStart(String source, String expected, int fromIndex, boolean wholeWord) {
        int matchStart = source.indexOf(expected, fromIndex);
        if (!wholeWord) {
            return matchStart;
        }
        while (matchStart >= 0) {
            int before = matchStart - 1;
            int after = matchStart + expected.length();
            boolean leftBoundary = before < 0 || !Character.isLetterOrDigit(source.charAt(before));
            boolean rightBoundary = after >= source.length() || !Character.isLetterOrDigit(source.charAt(after));
            if (leftBoundary && rightBoundary) {
                return matchStart;
            }
            matchStart = source.indexOf(expected, matchStart + expected.length());
        }
        return -1;
    }

    private void updateOverviewCards(RequestHistoryItem item, HistoryVisualInfo visualInfo,
                                     String protocol, String duration, String status, String timestamp) {
        if (item == null || item.getRequest() == null) {
            return;
        }
        PreparedRequest request = item.getRequest();
        setOverviewCardState(endpointValueLabel, endpointDetailLabel,
                abbreviateMiddle(visualInfo.subtitle(), 30),
                abbreviateMiddle(visualInfo.title(), 42));
        setOverviewCardState(payloadValueLabel, payloadDetailLabel,
                resolveBodyTypeLabel(request),
                protocol);

        int params = countEnabledParams(request.paramsList);
        int headers = countEnabledHeaders(request);
        int formData = countEnabledFormData(request.formDataList);
        int urlencoded = countEnabledUrlencoded(request.urlencodedList);
        setOverviewCardState(structureValueLabel, structureDetailLabel,
                buildStructureSummary(params, headers, formData, urlencoded),
                buildScriptSummary(request));
        setOverviewCardState(resultValueLabel, resultDetailLabel,
                status + " · " + duration,
                timestamp);
    }

    private String buildStructureSummary(int params, int headers, int formData, int urlencoded) {
        return I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_PARAMS) + " " + params
                + " · " + I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_HEADERS_SHORT) + " " + headers
                + " · " + I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_FORM_DATA_SHORT) + " " + formData
                + " · " + I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_URLENCODED_SHORT) + " " + urlencoded;
    }

    private String buildScriptSummary(PreparedRequest request) {
        List<String> tags = new ArrayList<>();
        if (request.prescript != null && !request.prescript.isBlank()) {
            tags.add(I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_PRESCRIPT));
        }
        if (request.postscript != null && !request.postscript.isBlank()) {
            tags.add(I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_POSTSCRIPT));
        }
        return tags.isEmpty() ? "-" : String.join(" / ", tags);
    }

    private void setOverviewCardState(JLabel valueLabel, JLabel detailLabel, String value, String detail) {
        if (valueLabel != null) {
            valueLabel.setText(escapeHtml(value == null ? "" : value));
            valueLabel.setToolTipText(value);
        }
        if (detailLabel != null) {
            detailLabel.setText(escapeHtml(detail == null ? "" : detail));
            detailLabel.setToolTipText(detail);
        }
    }

    private String resolveBodyTypeLabel(PreparedRequest request) {
        String bodyType = resolveBodyType(request);
        return switch (bodyType) {
            case RequestBodyPanel.BODY_TYPE_FORM_DATA -> I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_BODY_FORM_DATA);
            case RequestBodyPanel.BODY_TYPE_FORM_URLENCODED -> I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_BODY_URLENCODED);
            case RequestBodyPanel.BODY_TYPE_RAW -> I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_BODY_RAW);
            default -> I18nUtil.getMessage(MessageKeys.HISTORY_OVERVIEW_BODY_NONE);
        };
    }

    private int countEnabledParams(List<HttpParam> paramsList) {
        if (paramsList == null) {
            return 0;
        }
        int count = 0;
        for (HttpParam param : paramsList) {
            if (param != null && param.isEnabled() && param.getKey() != null && !param.getKey().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private int countEnabledHeaders(PreparedRequest request) {
        List<HttpHeader> headersList = request.headersList;
        if (headersList != null && !headersList.isEmpty()) {
            int count = 0;
            for (HttpHeader header : headersList) {
                if (header != null && header.isEnabled() && header.getKey() != null && !header.getKey().isBlank()) {
                    count++;
                }
            }
            return count;
        }
        return request.sentHeadersList != null ? request.sentHeadersList.size() : 0;
    }

    private int countEnabledFormData(List<HttpFormData> formDataList) {
        if (formDataList == null) {
            return 0;
        }
        int count = 0;
        for (HttpFormData formData : formDataList) {
            if (formData != null && formData.isEnabled() && formData.getKey() != null && !formData.getKey().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private int countEnabledUrlencoded(List<HttpFormUrlencoded> urlencodedList) {
        if (urlencodedList == null) {
            return 0;
        }
        int count = 0;
        for (HttpFormUrlencoded urlencoded : urlencodedList) {
            if (urlencoded != null && urlencoded.isEnabled() && urlencoded.getKey() != null && !urlencoded.getKey().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private String buildTabTitle(RequestHistoryItem item) {
        try {
            URI uri = URI.create(item.getUrl());
            String path = uri.getPath();
            if (path != null && !path.isBlank() && !"/".equals(path)) {
                int slashIndex = path.lastIndexOf('/');
                String lastSegment = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
                if (!lastSegment.isBlank()) {
                    return lastSegment;
                }
                return path;
            }
            if (uri.getHost() != null && !uri.getHost().isBlank()) {
                return uri.getHost();
            }
        } catch (Exception ignored) {
            // Fall back to method-based title below.
        }
        return item.getMethod() != null && !item.getMethod().isBlank()
                ? item.getMethod().toUpperCase(Locale.ROOT)
                : I18nUtil.getMessage(MessageKeys.NEW_REQUEST);
    }

    private String buildDateGroupLabel(long timestamp,
                                       long todayStart,
                                       long yesterdayStart,
                                       SimpleDateFormat formatter) {
        if (timestamp >= todayStart) {
            return I18nUtil.getMessage(MessageKeys.HISTORY_TODAY);
        }
        if (timestamp >= yesterdayStart) {
            return I18nUtil.getMessage(MessageKeys.HISTORY_YESTERDAY);
        }
        return formatter.format(new Date(timestamp));
    }

    private void updateDateCache() {
        long now = System.currentTimeMillis();
        if (todayStartCache == 0 || now >= todayStartCache + 24 * 60 * 60 * 1000L) {
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            todayStartCache = today.getTimeInMillis();
            yesterdayStartCache = todayStartCache - 24 * 60 * 60 * 1000L;
        }
    }

    private void clearRequestHistory() {
        if (allHistoryItems.isEmpty()) {
            return;
        }
        int option = JOptionPane.showConfirmDialog(
                this,
                I18nUtil.getMessage(MessageKeys.HISTORY_CLEAR_CONFIRM, allHistoryItems.size()),
                I18nUtil.getMessage(MessageKeys.HISTORY_CLEAR_TITLE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        BeanFactory.getBean(HistoryPersistenceService.class).clearHistory();
        allHistoryItems.clear();
        rebuildHistoryListModel(null);
    }

    private void loadPersistedHistory() {
        if (historyListModel == null) {
            return;
        }

        SwingWorker<List<RequestHistoryItem>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<RequestHistoryItem> doInBackground() {
                return BeanFactory.getBean(HistoryPersistenceService.class).getHistory();
            }

            @Override
            protected void done() {
                try {
                    List<RequestHistoryItem> items = get();
                    allHistoryItems.clear();
                    allHistoryItems.addAll(items);
                    rebuildHistoryListModel(null);
                } catch (Exception ignored) {
                    // Ignore if cancelled or interrupted
                }
            }
        };
        worker.execute();
    }

    public void refreshHistory() {
        loadPersistedHistory();
    }

    private static Color resolveStatusColor(int statusCode) {
        if (statusCode >= 200 && statusCode < 400) {
            return ModernColors.getSuccess();
        }
        if (statusCode >= 400 || statusCode <= 0) {
            return ModernColors.getError();
        }
        return ModernColors.getTextSecondary();
    }

    private static String toHex(Color color) {
        return ModernColors.toHtmlColor(color);
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}

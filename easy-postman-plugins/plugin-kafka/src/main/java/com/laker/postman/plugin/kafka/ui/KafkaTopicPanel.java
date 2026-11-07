package com.laker.postman.plugin.kafka.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.laker.postman.plugin.kafka.MessageKeys;
import com.laker.postman.plugin.kafka.shared.ui.ThemedLabel;
import com.laker.postman.common.component.SearchTextField;
import com.laker.postman.common.component.ToolWindowSidebarHeader;
import com.laker.postman.common.component.ToolWindowSidebarToolbar;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.RefreshButton;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Supplier;
import java.util.function.Consumer;

import static com.laker.postman.plugin.kafka.KafkaI18n.t;

public class KafkaTopicPanel extends JPanel {

    public final SearchTextField topicSearchField;
    public final DefaultListModel<KafkaTopicItem> topicListModel;
    public final DefaultListModel<KafkaTopicItem> topicFilteredModel;
    public final JList<KafkaTopicItem> topicList;
    private final ThemedLabel statusLabel;
    private final RefreshButton refreshBtn;
    private boolean connected;
    private boolean loading;

    public KafkaTopicPanel(Runnable refreshAction, Consumer<String> topicSelectionAction) {
        super(new BorderLayout(0, 0));
        ToolWindowSurfaceStyle.applyCard(this);
        setPreferredSize(new Dimension(230, 0));

        refreshBtn = new RefreshButton();
        refreshBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_TOPIC_REFRESH));
        refreshBtn.addActionListener(e -> refreshAction.run());
        ToolWindowSidebarHeader titleBar = new ToolWindowSidebarHeader(
                t(MessageKeys.TOOLBOX_KAFKA_TOPIC_MANAGEMENT), refreshBtn);

        topicSearchField = new SearchTextField();
        topicSearchField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, null);
        topicSearchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_TOPIC_SEARCH_PLACEHOLDER));
        topicSearchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        topicListModel = new DefaultListModel<>();
        topicFilteredModel = new DefaultListModel<>();
        topicList = new JList<>(topicFilteredModel) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getModel().getSize() == 0) {
                    paintEmptyText(this, g, emptyText());
                }
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                int index = locationToIndex(event.getPoint());
                if (index < 0 || !getCellBounds(index, index).contains(event.getPoint())) {
                    return null;
                }
                KafkaTopicItem item = getModel().getElementAt(index);
                return t(MessageKeys.TOOLBOX_KAFKA_TOPIC_ITEM_TOOLTIP, item.name(), item.partitionCount());
            }
        };
        ToolTipManager.sharedInstance().registerComponent(topicList);
        topicList.setCellRenderer(new TopicCellRenderer());
        topicList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        topicList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            KafkaTopicItem selected = topicList.getSelectedValue();
            if (selected != null && !selected.name().isBlank()) {
                topicSelectionAction.accept(selected.name());
            }
        });
        topicList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    int index = topicList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        topicList.setSelectedIndex(index);
                        KafkaTopicItem selected = topicList.getSelectedValue();
                        if (selected != null) {
                            topicSelectionAction.accept(selected.name());
                        }
                    }
                }
            }
        });
        JScrollPane topicScroll = new JScrollPane(topicList);
        ToolWindowSurfaceStyle.applyListScrollPaneCard(topicScroll, topicList);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titleBar, BorderLayout.NORTH);
        top.add(new ToolWindowSidebarToolbar(null, topicSearchField), BorderLayout.CENTER);

        statusLabel = new ThemedLabel("", ModernColors::getTextSecondary);
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));
        statusLabel.setIconTextGap(6);
        setStatus(ModernColors::getTextSecondary, t(MessageKeys.TOOLBOX_KAFKA_STATUS_NOT_CONNECTED));

        add(top, BorderLayout.NORTH);
        add(topicScroll, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    /**
     * 侧栏底部的连接状态：彩色圆点 + 单行文本，完整内容放到 tooltip 中避免长错误撑开侧栏。
     */
    public void setStatus(Supplier<Color> color, String message) {
        statusLabel.setIcon(new StatusDotIcon(color));
        statusLabel.setForegroundSupplier(color);
        statusLabel.setText(message);
        statusLabel.setToolTipText(message);
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        topicList.repaint();
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        refreshBtn.setEnabled(!loading);
        topicList.repaint();
    }

    public boolean isLoading() {
        return loading;
    }

    boolean isRefreshEnabled() {
        return refreshBtn.isEnabled();
    }

    String emptyText() {
        if (loading) {
            return t(MessageKeys.TOOLBOX_KAFKA_STATUS_LOADING_TOPICS);
        }
        if (!connected) {
            return t(MessageKeys.TOOLBOX_KAFKA_TOPIC_EMPTY_NOT_CONNECTED);
        }
        if (topicListModel.isEmpty()) {
            return t(MessageKeys.TOOLBOX_KAFKA_TOPIC_EMPTY_NONE);
        }
        return t(MessageKeys.TOOLBOX_KAFKA_TOPIC_EMPTY_NO_MATCH);
    }

    private static void paintEmptyText(JComponent component, Graphics g, String text) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
            g2.setColor(ModernColors.getTextHint());
            FontMetrics fm = g2.getFontMetrics();
            int maxWidth = Math.max(40, component.getWidth() - 24);
            int y = 16 + fm.getAscent();
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch != '\n' && fm.stringWidth(line.toString() + ch) <= maxWidth) {
                    line.append(ch);
                    continue;
                }
                drawCentered(g2, fm, line.toString(), component.getWidth(), y);
                y += fm.getHeight();
                line.setLength(0);
                if (ch != '\n') {
                    line.append(ch);
                }
            }
            drawCentered(g2, fm, line.toString(), component.getWidth(), y);
        } finally {
            g2.dispose();
        }
    }

    private static void drawCentered(Graphics2D g2, FontMetrics fm, String line, int width, int y) {
        String trimmed = line.strip();
        g2.drawString(trimmed, Math.max(8, (width - fm.stringWidth(trimmed)) / 2), y);
    }

    /**
     * Topic 名称左对齐，分区数以次要颜色右对齐，窄侧栏下名称优先截断。
     */
    private static final class TopicCellRenderer extends JPanel implements ListCellRenderer<KafkaTopicItem> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel partitionLabel = new JLabel();

        TopicCellRenderer() {
            super(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(3, 8, 3, 8));
            partitionLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
            add(nameLabel, BorderLayout.CENTER);
            add(partitionLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends KafkaTopicItem> list, KafkaTopicItem value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value == null ? "" : value.name());
            nameLabel.setFont(list.getFont());
            partitionLabel.setText(value == null ? "" : t(MessageKeys.TOOLBOX_KAFKA_TOPIC_PARTITION_COUNT, value.partitionCount()));
            // FlatLaf 只会把渲染器根组件切换为失焦选中色，子标签需自行处理，否则亮色主题下失焦选中行会白字浅底看不清
            boolean focused = FlatUIUtils.isPermanentFocusOwner(list);
            Color selectionBg = focused ? list.getSelectionBackground()
                    : uiColor("List.selectionInactiveBackground", list.getSelectionBackground());
            Color selectionFg = focused ? list.getSelectionForeground()
                    : uiColor("List.selectionInactiveForeground", list.getForeground());
            setBackground(isSelected ? selectionBg : list.getBackground());
            nameLabel.setForeground(isSelected ? selectionFg : list.getForeground());
            partitionLabel.setForeground(isSelected ? selectionFg : ModernColors.getTextSecondary());
            setOpaque(true);
            return this;
        }
    }

    private static Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    private record StatusDotIcon(Supplier<Color> color) implements Icon {
        private static final int SIZE = 7;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color.get());
                g2.fillOval(x, y, SIZE, SIZE);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}

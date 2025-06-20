package com.laker.postman.panel.history;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.laker.postman.common.AbstractBasePanel;
import com.laker.postman.common.constants.Colors;
import com.laker.postman.model.RequestHistoryItem;
import com.laker.postman.util.FontUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 历史记录面板，原SidebarTabPanel.createHistoryPanel内容
 */
public class HistoryPanel extends AbstractBasePanel {
    private JList<RequestHistoryItem> historyList;
    private JPanel historyDetailPanel;
    private JTextPane historyDetailPane;
    private DefaultListModel<RequestHistoryItem> historyListModel;

    protected void initUI() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY));
        JPanel titlePanel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("History");
        title.setFont(FontUtil.getDefaultFont(Font.BOLD, 13));
        JButton clearBtn = new JButton(new FlatSVGIcon("icons/clear.svg"));
        clearBtn.setMargin(new Insets(0, 4, 0, 4));
        clearBtn.setBackground(Colors.PANEL_BACKGROUND);
        clearBtn.setBorder(BorderFactory.createEmptyBorder());
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> clearRequestHistory());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(clearBtn);
        titlePanel.add(title, BorderLayout.WEST);
        titlePanel.add(btnPanel, BorderLayout.EAST);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(titlePanel, BorderLayout.PAGE_START);

        // 历史列表
        historyListModel = new DefaultListModel<>();
        historyList = new JList<>(historyListModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof RequestHistoryItem item) {
                    String text = String.format("[%s] %s", item.method, item.url);
                    int maxWidth = list.getWidth() - 20;
                    if (maxWidth > 0) {
                        FontMetrics fm = label.getFontMetrics(label.getFont());
                        if (fm.stringWidth(text) > maxWidth) {
                            String ellipsis = "...";
                            int len = text.length();
                            while (len > 0 && fm.stringWidth(text.substring(0, len) + ellipsis) > maxWidth) {
                                len--;
                            }
                            if (len > 0) {
                                text = text.substring(0, len) + ellipsis;
                            } else {
                                text = ellipsis;
                            }
                        }
                    }
                    label.setText(text);
                }
                if (isSelected) {
                    label.setFont(label.getFont().deriveFont(Font.BOLD));
                    label.setBackground(new Color(180, 215, 255));
                } else {
                    label.setFont(label.getFont().deriveFont(Font.PLAIN));
                    label.setBackground(Color.WHITE);
                }
                return label;
            }
        });
        JScrollPane listScroll = new JScrollPane(historyList);
        listScroll.setPreferredSize(new Dimension(200, 240));
        listScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // 水平滚动条不需要，内容不会超出

        // 鼠标悬浮显示全文tip
        historyList.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = historyList.locationToIndex(e.getPoint());
                if (idx != -1) {
                    RequestHistoryItem item = historyListModel.get(idx);
                    historyList.setToolTipText(String.format("[%s] %s", item.method, item.url));
                } else {
                    historyList.setToolTipText(null);
                }
            }
        });

        // 详情区
        historyDetailPanel = new JPanel(new BorderLayout());
        historyDetailPane = new JTextPane();
        historyDetailPane.setEditable(false);
        historyDetailPane.setContentType("text/html");
        historyDetailPane.setFont(FontUtil.getDefaultFont(Font.PLAIN, 12));
        JScrollPane detailScroll = new JScrollPane(historyDetailPane);
        detailScroll.setPreferredSize(new Dimension(340, 240));
        detailScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        detailScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        historyDetailPanel.add(detailScroll, BorderLayout.CENTER);
        historyDetailPane.setText("<html><body>请选择一条历史记录</body></html>");
        historyDetailPanel.setVisible(true);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, historyDetailPanel);
        split.setDividerLocation(220);
        split.setDividerSize(1);
        add(split, BorderLayout.CENTER);
        setMinimumSize(new Dimension(0, 120));

        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = historyList.getSelectedIndex();
                if (idx == -1) {
                    historyDetailPane.setText("<html><body>请选择一条历史记录</body></html>");
                } else {
                    RequestHistoryItem item = historyListModel.get(idx);
                    historyDetailPane.setText(formatHistoryDetailPrettyHtml(item));
                    historyDetailPane.setCaretPosition(0);
                }
            }
        });
        historyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = historyList.locationToIndex(e.getPoint());
                    if (idx != -1) {
                        historyList.setSelectedIndex(idx);
                    }
                }
            }
        });
        SwingUtilities.invokeLater(() -> historyList.repaint());
    }

    @Override
    protected void registerListeners() {

    }

    private String formatHistoryDetailPrettyHtml(RequestHistoryItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:monospace;font-size:9px;'>");
        sb.append("<b>【方法】</b> <span style='color:#1976d2;'>").append(item.method).append("</span> ");
        sb.append("<b>【URL】</b> <span style='color:#388e3c;'>").append(item.url).append("</span><br><br>");
        sb.append("<b>【执行线程】</b> <span style='color:#d2691e;'>").append(item.threadName == null ? "(无)" : item.threadName).append("</span><br><br>");
        sb.append("<b>【请求头】</b><br><pre style='margin:0;'>")
          .append(item.requestHeaders == null || item.requestHeaders.isEmpty() ? "(无)" : escapeHtml(item.requestHeaders)).append("</pre><br>");
        sb.append("<b>【请求体】</b><br><pre style='margin:0;'>")
          .append(item.requestBody == null || item.requestBody.isEmpty() ? "(无)" : escapeHtml(item.requestBody)).append("</pre><br>");
        sb.append("<b>【响应状态】</b> <span style='color:#1976d2;'>").append(escapeHtml(item.responseStatus)).append("</span><br>");
        sb.append("<b>【响应头】</b><br><pre style='margin:0;'>")
          .append(item.responseHeaders == null || item.responseHeaders.isEmpty() ? "(无)" : escapeHtml(item.responseHeaders)).append("</pre><br>");
        sb.append("<b>【响应体】</b><br><pre style='margin:0;'>")
          .append(item.responseBody == null || item.responseBody.isEmpty() ? "(无)" : escapeHtml(item.responseBody)).append("</pre>");
        // 重定向链美化
        if (item.extra != null && !item.extra.isEmpty()) {
            sb.append("<br><b>【重定向链】</b><br>");
            sb.append(formatRedirectChainHtml(item.extra));
        }
        sb.append("</body></html>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String formatRedirectChainHtml(String chain) {
        // 按行分割，状态行高亮，Location高亮
        String[] lines = chain.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String l = escapeHtml(line);
            if (l.matches("\\[\\d+] \\S+ \\S+")) {
                sb.append("<span style='color:#1976d2;font-weight:bold;'>").append(l).append("</span><br>");
            } else if (l.trim().startsWith("Location:")) {
                sb.append("<span style='color:#388e3c;'>").append(l).append("</span><br>");
            } else if (l.trim().isEmpty()) {
                sb.append("<br>");
            } else {
                sb.append(l).append("<br>");
            }
        }
        return sb.toString();
    }

    private void clearRequestHistory() {
        historyListModel.clear();
        historyDetailPane.setText("<html><body>请选择一条历史记录</body></html>");
        historyDetailPanel.setVisible(true);
    }


    // 新增：支持带重定向链和线程名的历史记录
    public void addRequestHistory(String method, String url, String requestBody, String requestHeaders, String responseStatus, String responseHeaders, String responseBody, String redirectChain, String threadName) {
        RequestHistoryItem item = new RequestHistoryItem(
                method,
                url,
                requestBody,
                requestHeaders,
                responseStatus,
                responseHeaders,
                responseBody,
                System.currentTimeMillis(),
                threadName
        );
        item.extra = redirectChain;
        if (historyListModel != null) {
            historyListModel.add(0, item);
        }
    }
}

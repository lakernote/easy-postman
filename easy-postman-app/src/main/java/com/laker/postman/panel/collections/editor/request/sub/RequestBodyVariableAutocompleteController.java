package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.variable.VariableInfo;
import com.laker.postman.variable.VariableType;
import com.laker.postman.service.variable.VariableResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 请求体变量自动补全控制器。
 * <p>
 * 负责 {{...}} 触发、变量过滤、弹窗定位、键盘导航和插入变量，避免 RequestBodyPanel 承担弹窗状态。
 */
@Slf4j
@RequiredArgsConstructor
final class RequestBodyVariableAutocompleteController {
    private static final int POPUP_WIDTH = 400;
    private static final int LIST_CELL_WIDTH = 384;
    private static final int LIST_ITEM_HEIGHT = 32;
    private static final int POPUP_MAX_HEIGHT = 320;

    private final JComponent owner;
    private final RSyntaxTextArea bodyArea;

    private JWindow autocompleteWindow;
    private JList<VariableInfo> autocompleteList;
    private DefaultListModel<VariableInfo> autocompleteModel;

    void install() {
        Window parentWindow = SwingUtilities.getWindowAncestor(owner);
        if (parentWindow == null) {
            SwingUtilities.invokeLater(() -> {
                Window parent = SwingUtilities.getWindowAncestor(owner);
                if (parent != null) {
                    initAutocompleteWindow(parent);
                }
            });
        } else {
            initAutocompleteWindow(parentWindow);
        }

        bodyArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(RequestBodyVariableAutocompleteController.this::checkForAutocomplete);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(RequestBodyVariableAutocompleteController.this::checkForAutocomplete);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(RequestBodyVariableAutocompleteController.this::checkForAutocomplete);
            }
        });

        bodyArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleAutocompleteKey(e);
            }
        });
    }

    private void initAutocompleteWindow(Window parent) {
        autocompleteWindow = new JWindow(parent);
        autocompleteWindow.setFocusableWindowState(false);

        autocompleteModel = new DefaultListModel<>();
        autocompleteList = new JList<>(autocompleteModel);
        autocompleteList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        autocompleteList.setVisibleRowCount(10);
        autocompleteList.setFont(bodyArea.getFont());
        autocompleteList.setFixedCellWidth(LIST_CELL_WIDTH);
        autocompleteList.setCellRenderer(new VariableListCellRenderer(bodyArea));

        JScrollPane scrollPane = new JScrollPane(autocompleteList);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        autocompleteWindow.add(scrollPane);
        applyAutocompleteTheme(scrollPane);

        autocompleteList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 || e.getClickCount() == 1) {
                    insertSelectedVariable();
                }
            }
        });
    }

    private void handleAutocompleteKey(KeyEvent e) {
        if (autocompleteWindow == null || !autocompleteWindow.isVisible()) {
            return;
        }

        switch (e.getKeyCode()) {
            case KeyEvent.VK_DOWN -> {
                selectNext();
                e.consume();
            }
            case KeyEvent.VK_UP -> {
                selectPrevious();
                e.consume();
            }
            case KeyEvent.VK_ENTER, KeyEvent.VK_TAB -> {
                insertSelectedVariable();
                e.consume();
            }
            case KeyEvent.VK_ESCAPE -> {
                hideAutocomplete();
                e.consume();
            }
            default -> {
                // No action needed for other keys.
            }
        }
    }

    private void selectNext() {
        int currentIndex = autocompleteList.getSelectedIndex();
        int nextIndex = currentIndex + 1;
        if (nextIndex >= autocompleteModel.getSize()) {
            nextIndex = 0;
        }
        autocompleteList.setSelectedIndex(nextIndex);
        autocompleteList.ensureIndexIsVisible(nextIndex);
    }

    private void selectPrevious() {
        int currentIndex = autocompleteList.getSelectedIndex();
        int previousIndex = currentIndex - 1;
        if (previousIndex < 0) {
            previousIndex = autocompleteModel.getSize() - 1;
        }
        autocompleteList.setSelectedIndex(previousIndex);
        autocompleteList.ensureIndexIsVisible(previousIndex);
    }

    private void checkForAutocomplete() {
        if (bodyArea == null || autocompleteWindow == null) {
            return;
        }

        String text = bodyArea.getText();
        int caretPos = bodyArea.getCaretPosition();
        if (text == null || caretPos < 2) {
            hideAutocomplete();
            return;
        }

        int openBracePos = text.lastIndexOf("{{", caretPos - 1);
        if (openBracePos == -1) {
            hideAutocomplete();
            return;
        }

        int closeBracePos = text.indexOf("}}", openBracePos);
        if (closeBracePos != -1 && closeBracePos < caretPos) {
            hideAutocomplete();
            return;
        }

        String prefix = text.substring(openBracePos + 2, caretPos);
        java.util.List<VariableInfo> filteredVariables = VariableResolver.filterVariablesWithType(prefix);
        if (filteredVariables.isEmpty()) {
            hideAutocomplete();
            return;
        }

        autocompleteModel.clear();
        for (VariableInfo varInfo : filteredVariables) {
            autocompleteModel.addElement(varInfo);
        }

        autocompleteList.setSelectedIndex(0);
        autocompleteList.ensureIndexIsVisible(0);
        showAutocomplete();
    }

    private void showAutocomplete() {
        if (autocompleteWindow == null || autocompleteModel.getSize() == 0) {
            return;
        }

        try {
            applyAutocompleteTheme((JScrollPane) autocompleteWindow.getContentPane().getComponent(0));
            Rectangle rect = bodyArea.modelToView2D(bodyArea.getCaretPosition()).getBounds();
            Point screenPos = bodyArea.getLocationOnScreen();

            int popupHeight = Math.min(autocompleteModel.getSize() * LIST_ITEM_HEIGHT + 10, POPUP_MAX_HEIGHT);
            autocompleteWindow.setSize(POPUP_WIDTH, popupHeight);
            autocompleteWindow.setLocation(
                    screenPos.x + rect.x,
                    screenPos.y + rect.y + rect.height + 2
            );

            if (!autocompleteWindow.isVisible()) {
                autocompleteWindow.setVisible(true);
            }
        } catch (Exception e) {
            log.error("showAutocomplete error", e);
        }
    }

    private void hideAutocomplete() {
        if (autocompleteWindow != null) {
            autocompleteWindow.setVisible(false);
        }
    }

    private void applyAutocompleteTheme(JScrollPane scrollPane) {
        Color popupBackground = RequestBodyTheme.popupBackground();
        autocompleteList.setBackground(popupBackground);
        autocompleteList.setSelectionBackground(RequestBodyTheme.popupSelectionBackground());
        autocompleteList.setSelectionForeground(RequestBodyTheme.popupSelectionForeground());
        scrollPane.getViewport().setBackground(popupBackground);
        scrollPane.setBackground(popupBackground);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RequestBodyTheme.popupBorder(), 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
    }

    private void insertSelectedVariable() {
        if (autocompleteList == null || bodyArea == null) {
            return;
        }

        VariableInfo selected = autocompleteList.getSelectedValue();
        if (selected == null) {
            return;
        }

        try {
            String text = bodyArea.getText();
            int caretPos = bodyArea.getCaretPosition();
            int openBracePos = text.lastIndexOf("{{", caretPos - 1);
            if (openBracePos == -1) {
                hideAutocomplete();
                return;
            }

            String before = text.substring(0, openBracePos);
            String after = text.substring(caretPos);
            String varName = selected.getName();

            boolean hasClosingBraces = after.startsWith("}}");
            String newText;
            int newCaretPos;
            if (hasClosingBraces) {
                newText = before + "{{" + varName + after;
                newCaretPos = before.length() + varName.length() + 4;
            } else {
                newText = before + "{{" + varName + "}}" + after;
                newCaretPos = before.length() + varName.length() + 4;
            }

            bodyArea.setText(newText);
            bodyArea.setCaretPosition(newCaretPos);
        } catch (Exception e) {
            log.error("insertSelectedVariable error", e);
        }

        hideAutocomplete();
    }

    private static final class VariableListCellRenderer extends DefaultListCellRenderer {
        private final RSyntaxTextArea bodyArea;

        private VariableListCellRenderer(RSyntaxTextArea bodyArea) {
            this.bodyArea = bodyArea;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new BorderLayout(8, 0));
            panel.setOpaque(true);
            panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            panel.setPreferredSize(new Dimension(LIST_CELL_WIDTH, LIST_ITEM_HEIGHT));
            panel.setMaximumSize(new Dimension(LIST_CELL_WIDTH, LIST_ITEM_HEIGHT));
            panel.setBackground(isSelected
                    ? RequestBodyTheme.popupSelectionBackground()
                    : RequestBodyTheme.popupBackground());

            if (value instanceof VariableInfo varInfo) {
                installVariableContent(panel, varInfo);
            }
            return panel;
        }

        private void installVariableContent(JPanel panel, VariableInfo varInfo) {
            String varName = varInfo.getName();
            String varValue = varInfo.getValue();
            VariableType varType = varInfo.getType();
            Color labelColor = varType.getColor();

            panel.add(new VariableTypeIcon(varType), BorderLayout.WEST);
            panel.add(createContentPanel(varName, varValue, labelColor), BorderLayout.CENTER);
            panel.setToolTipText(RequestBodyVariableTooltipBuilder.listItemTooltip(varName, varValue, varType));
        }

        private JPanel createContentPanel(String varName, String varValue, Color labelColor) {
            JPanel contentPanel = new JPanel(new GridLayout(1, 2, 8, 0));
            contentPanel.setOpaque(false);
            contentPanel.add(createNamePanel(varName, labelColor));
            contentPanel.add(createValuePanel(varValue));
            return contentPanel;
        }

        private JPanel createNamePanel(String varName, Color labelColor) {
            JPanel namePanel = new JPanel(new BorderLayout());
            namePanel.setOpaque(false);

            String displayName = truncate(varName);
            JLabel nameLabel = new JLabel(displayName);
            nameLabel.setFont(bodyArea.getFont().deriveFont(Font.BOLD));
            nameLabel.setForeground(labelColor);
            if (!displayName.equals(varName)) {
                nameLabel.setToolTipText(varName);
            }
            namePanel.add(nameLabel, BorderLayout.WEST);
            return namePanel;
        }

        private JPanel createValuePanel(String varValue) {
            JPanel valuePanel = new JPanel(new BorderLayout());
            valuePanel.setOpaque(false);
            if (varValue == null || varValue.isEmpty()) {
                return valuePanel;
            }

            String displayValue = truncate(varValue);
            JLabel valueLabel = new JLabel(displayValue);
            valueLabel.setFont(bodyArea.getFont().deriveFont(Font.PLAIN, (float) (bodyArea.getFont().getSize() - 1)));
            valueLabel.setForeground(RequestBodyTheme.popupValueForeground());
            if (!displayValue.equals(varValue) || varValue.length() > 20) {
                valueLabel.setToolTipText(RequestBodyVariableTooltipBuilder.valueTooltip(varValue));
            }

            valuePanel.add(valueLabel, BorderLayout.WEST);
            return valuePanel;
        }

        private String truncate(String text) {
            int maxLength = 25;
            if (text.length() <= maxLength) {
                return text;
            }
            return text.substring(0, maxLength - 3) + "...";
        }
    }

    private static final class VariableTypeIcon extends JPanel {
        private final VariableType varType;

        private VariableTypeIcon(VariableType varType) {
            this.varType = varType;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            FontsUtil.applyTextRenderingHints(g2d);
            try {
                int panelHeight = getHeight();
                int circleSize = 12;
                int circleY = (panelHeight - circleSize) / 2;

                g2d.setColor(varType.getColor());
                g2d.fillOval(2, circleY, circleSize, circleSize);

                g2d.setColor(ModernColors.getTextInverse());
                g2d.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -2));
                FontMetrics symbolFm = g2d.getFontMetrics();
                String symbol = varType.getIconSymbol();
                int symbolWidth = symbolFm.stringWidth(symbol);
                int symbolAscent = symbolFm.getAscent();
                int symbolDescent = symbolFm.getDescent();
                int symbolHeight = symbolAscent + symbolDescent;

                int symbolX = 2 + (circleSize - symbolWidth) / 2;
                int symbolY = circleY + (circleSize - symbolHeight) / 2 + symbolAscent;
                g2d.drawString(symbol, symbolX, symbolY);
            } finally {
                g2d.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(16, 24);
        }
    }
}

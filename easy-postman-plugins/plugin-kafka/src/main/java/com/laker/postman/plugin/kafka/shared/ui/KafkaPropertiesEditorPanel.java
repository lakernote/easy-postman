package com.laker.postman.plugin.kafka.shared.ui;

import com.laker.postman.common.component.PlaceholderTextArea;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.util.FontsUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Supplier;

public class KafkaPropertiesEditorPanel extends JPanel {

    private final PlaceholderTextArea textArea;

    public KafkaPropertiesEditorPanel(String title, String hint, String placeholder, Supplier<Color> hintColor) {
        super(new BorderLayout(0, 0));
        setOpaque(false);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(6, 10, 4, 8));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -2));

        JLabel hintLabel = new ThemedLabel(hint, hintColor);
        hintLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -3));

        JPanel labels = new JPanel(new BorderLayout(0, 2));
        labels.setOpaque(false);
        labels.add(titleLabel, BorderLayout.NORTH);
        labels.add(hintLabel, BorderLayout.CENTER);
        header.add(labels, BorderLayout.CENTER);

        textArea = new PlaceholderTextArea(4, 0);
        textArea.setLineWrap(false);
        textArea.setPlaceholder(placeholder);
        ToolWindowSurfaceStyle.applyTextComponentInput(textArea);

        JScrollPane scrollPane = new JScrollPane(textArea);
        ToolWindowSurfaceStyle.applyScrollPaneCard(scrollPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));

        add(header, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public String getValue() {
        return textArea.getText();
    }
}

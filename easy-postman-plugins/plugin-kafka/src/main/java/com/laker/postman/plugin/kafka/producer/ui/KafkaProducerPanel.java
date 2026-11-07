package com.laker.postman.plugin.kafka.producer.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.plugin.kafka.MessageKeys;
import com.laker.postman.plugin.kafka.shared.ui.ThemedLabel;
import com.laker.postman.plugin.kafka.shared.ui.KafkaPropertiesEditorPanel;
import com.laker.postman.plugin.kafka.shared.ui.KafkaSwingSupport;
import com.laker.postman.common.component.PlaceholderTextArea;
import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ClearButton;
import com.laker.postman.common.component.button.CompactPrimaryButton;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.EditorThemeUtil;
import com.laker.postman.util.FontsUtil;
import net.miginfocom.swing.MigLayout;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static com.laker.postman.plugin.kafka.KafkaI18n.t;

public class KafkaProducerPanel extends JPanel {

    private static final String MIG_GROWX = "growx";
    private static final String ACTION_KAFKA_SEND = "kafka-send";

    public final JTextField topicField;
    public final JTextField keyField;
    public final PlaceholderTextArea headersArea;
    public final JSpinner partitionSpinner;
    public final RSyntaxTextArea payloadArea;
    public final KafkaPropertiesEditorPanel customPropsPanel;
    public final CompactPrimaryButton sendBtn;
    public final ThemedLabel statusLabel;
    public final JPanel advancedPanel;

    public KafkaProducerPanel(Runnable sendAction) {
        super(new BorderLayout(0, 0));
        setOpaque(false);

        JToggleButton advancedToggleBtn = KafkaSwingSupport.toolbarToggle(
                "icons/more.svg", t(MessageKeys.TOOLBOX_KAFKA_ADVANCED_OPTIONS));

        JPanel form = new JPanel(new MigLayout(
                "insets 4 6 4 6, fillx, novisualpadding",
                "[]8[grow,fill]8[]8[grow,fill]8[]8[90!]8[]8[]",
                "[]"
        ));
        ToolWindowSurfaceStyle.applySectionHeader(form);

        topicField = new JTextField("");
        topicField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_TOPIC_PLACEHOLDER));
        topicField.setPreferredSize(new Dimension(180, 30));

        keyField = new JTextField("");
        keyField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_KEY_PLACEHOLDER));
        keyField.setPreferredSize(new Dimension(140, 30));

        partitionSpinner = new JSpinner(new SpinnerNumberModel(-1, -1, Integer.MAX_VALUE, 1));
        partitionSpinner.setPreferredSize(new Dimension(90, 30));
        partitionSpinner.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_PARTITION_AUTO_TOOLTIP));

        sendBtn = new CompactPrimaryButton(t(MessageKeys.TOOLBOX_KAFKA_SEND), "icons/send.svg");
        sendBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_SEND));
        sendBtn.addActionListener(e -> sendAction.run());

        form.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_TOPIC)));
        form.add(topicField, MIG_GROWX);
        form.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_KEY)));
        form.add(keyField, MIG_GROWX);
        JLabel partitionLabel = new JLabel(t(MessageKeys.TOOLBOX_KAFKA_PARTITION));
        partitionLabel.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_PARTITION_AUTO_TOOLTIP));
        form.add(partitionLabel);
        form.add(partitionSpinner, "w 90!");
        form.add(sendBtn);
        form.add(advancedToggleBtn);

        customPropsPanel = new KafkaPropertiesEditorPanel(
                t(MessageKeys.TOOLBOX_KAFKA_PRODUCER_CUSTOM_PROPERTIES),
                t(MessageKeys.TOOLBOX_KAFKA_PRODUCER_CUSTOM_PROPERTIES_HINT),
                t(MessageKeys.TOOLBOX_KAFKA_PRODUCER_CUSTOM_PROPERTIES_PLACEHOLDER),
                ModernColors::getTextSecondary);
        advancedPanel = customPropsPanel;
        advancedPanel.setVisible(false);
        advancedToggleBtn.addActionListener(e -> advancedPanel.setVisible(advancedToggleBtn.isSelected()));

        JPanel headersPanel = new JPanel(new BorderLayout());
        headersPanel.setOpaque(false);
        JPanel headersHeader = new JPanel(new MigLayout("insets 4 8 4 8, fillx", "[]push", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(headersHeader);
        JLabel headersLbl = new JLabel(t(MessageKeys.TOOLBOX_KAFKA_HEADERS));
        headersLbl.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -2));
        headersHeader.add(headersLbl);

        headersArea = new PlaceholderTextArea(2, 0);
        headersArea.setLineWrap(false);
        headersArea.setPlaceholder(t(MessageKeys.TOOLBOX_KAFKA_HEADERS_PLACEHOLDER));
        ToolWindowSurfaceStyle.applyTextComponentInput(headersArea);
        JScrollPane headersScroll = new JScrollPane(headersArea);
        ToolWindowSurfaceStyle.applyScrollPaneCard(headersScroll);

        headersPanel.add(headersHeader, BorderLayout.NORTH);
        headersPanel.add(headersScroll, BorderLayout.CENTER);

        JPanel payloadPanel = new JPanel(new BorderLayout());
        payloadPanel.setOpaque(false);
        JPanel payloadHeader = new JPanel(new MigLayout("insets 4 8 4 8, fillx", "[]push[]", "[]"));
        ToolWindowSurfaceStyle.applySectionHeader(payloadHeader);
        JLabel payloadLbl = new JLabel(t(MessageKeys.TOOLBOX_KAFKA_PAYLOAD));
        payloadLbl.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -2));
        ClearButton clearButton = new ClearButton();
        clearButton.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_CLEAR_PAYLOAD));
        payloadHeader.add(payloadLbl);
        payloadHeader.add(clearButton);

        payloadArea = new FallbackAwareRSyntaxTextArea();
        payloadArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        payloadArea.setCodeFoldingEnabled(true);
        payloadArea.setAntiAliasingEnabled(true);
        payloadArea.setText("{\n  \"message\": \"hello kafka\"\n}");
        payloadArea.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"), ACTION_KAFKA_SEND);
        payloadArea.getInputMap().put(KeyStroke.getKeyStroke("meta ENTER"), ACTION_KAFKA_SEND);
        payloadArea.getActionMap().put(ACTION_KAFKA_SEND, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                sendAction.run();
            }
        });
        clearButton.addActionListener(e -> payloadArea.setText(""));
        EditorThemeUtil.loadTheme(payloadArea);
        SearchableTextArea payloadSearchableArea = new SearchableTextArea(payloadArea);

        payloadPanel.add(payloadHeader, BorderLayout.NORTH);
        payloadPanel.add(payloadSearchableArea, BorderLayout.CENTER);

        JSplitPane editorSplit = ToolWindowChrome.createVerticalInnerSplitPane(headersPanel, payloadPanel, 100);
        editorSplit.setDividerLocation(100);
        editorSplit.setResizeWeight(0.25);

        statusLabel = new ThemedLabel(t(MessageKeys.TOOLBOX_KAFKA_PRODUCER_READY), ModernColors::getTextSecondary);
        statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setOpaque(false);
        content.add(form, BorderLayout.NORTH);
        JPanel editorContainer = new JPanel(new BorderLayout(0, 0));
        editorContainer.setOpaque(false);
        editorContainer.add(advancedPanel, BorderLayout.NORTH);
        editorContainer.add(editorSplit, BorderLayout.CENTER);
        content.add(editorContainer, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void updateEditorFont() {
        payloadArea.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
    }
}

package com.laker.postman.plugin.kafka.consumer.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.ChipLabel;
import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.common.component.FallbackAwareRSyntaxTextArea;
import com.laker.postman.common.component.SearchableTextArea;
import com.laker.postman.common.component.ToolWindowActionToolbar;
import com.laker.postman.common.component.ToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ClearButton;
import com.laker.postman.common.component.button.CloseButton;
import com.laker.postman.common.component.button.CompactPrimaryButton;
import com.laker.postman.common.component.button.CopyButton;
import com.laker.postman.common.component.button.SecondaryButton;
import com.laker.postman.common.component.connection.ConnectionToolbarUi;
import com.laker.postman.common.component.table.EnhancedTablePanel;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.plugin.kafka.MessageKeys;
import com.laker.postman.plugin.kafka.shared.ui.ThemedLabel;
import com.laker.postman.plugin.kafka.consumer.KafkaConsumedMessage;
import com.laker.postman.plugin.kafka.shared.KafkaPanelSupport;
import com.laker.postman.plugin.kafka.shared.ui.KafkaPropertiesEditorPanel;
import com.laker.postman.plugin.kafka.shared.ui.KafkaSwingSupport;
import com.laker.postman.util.EditorThemeUtil;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.JsonUtil;
import com.laker.postman.common.component.notification.NotificationCenter;
import net.miginfocom.swing.MigLayout;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Supplier;

import static com.laker.postman.plugin.kafka.KafkaI18n.t;

public class KafkaConsumerPanel extends JPanel {

    private static final String CARD_CONSUME_START = "consume-start";
    private static final String CARD_CONSUME_STOP = "consume-stop";
    private static final String EMPTY_VALUE = "—";
    private static final DateTimeFormatter DETAIL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public final JTextField topicField;
    public final JTextField groupIdField;
    public final KafkaPartitionSelector partitionSelector;
    public final JComboBox<String> autoOffsetCombo;
    public final JTextField consumeStartValueField;
    public final JSpinner pollTimeoutSpinner;
    public final JSpinner batchSizeSpinner;
    public final JSpinner maxViewSpinner;
    public final KafkaPropertiesEditorPanel customPropsPanel;
    public final CardLayout consumeBtnCardLayout;
    public final JPanel consumeBtnCard;
    public final ThemedLabel statusLabel;
    public final EnhancedTablePanel messageTablePanel;
    public final RSyntaxTextArea detailArea;
    public final JSplitPane detailSplit;

    private boolean detailPanelVisible = false;
    private final JLabel detailTopicLabel;
    private final JLabel detailPartitionLabel;
    private final JLabel detailOffsetLabel;
    private final JLabel detailKeyLabel;
    private final JLabel detailMessageTimeLabel;
    private final JLabel detailConsumeTimeLabel;
    private final JLabel detailLagLabel;
    private final JLabel consumeStartValueLabel;

    public KafkaConsumerPanel(Runnable startAction, Runnable stopAction, Runnable clearAction, Runnable selectionChanged) {
        super(new BorderLayout(0, 0));
        setOpaque(false);

        JToggleButton detailToggleBtn = KafkaSwingSupport.toolbarToggle(
                "icons/detail.svg", t(MessageKeys.TOOLBOX_KAFKA_MESSAGE_DETAIL));
        JToggleButton advancedToggleBtn = KafkaSwingSupport.toolbarToggle(
                "icons/more.svg", t(MessageKeys.TOOLBOX_KAFKA_ADVANCED_OPTIONS));

        ClearButton clearConsumeBtn = new ClearButton();
        clearConsumeBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_CONSUMER_CLEAR));
        clearConsumeBtn.addActionListener(e -> clearAction.run());

        JPanel mainControls = new JPanel(new MigLayout(
                "insets 4 6 4 6, fillx, novisualpadding",
                "[]8[grow,fill]8[]8[]",
                "[]"
        ));
        ToolWindowSurfaceStyle.applySectionHeader(mainControls);

        topicField = new JTextField("");
        topicField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_TOPIC_PLACEHOLDER));
        topicField.setPreferredSize(new Dimension(0, 30));

        CompactPrimaryButton startConsumeBtn = new CompactPrimaryButton(t(MessageKeys.TOOLBOX_KAFKA_START_CONSUME_SHORT), "icons/start.svg");
        startConsumeBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_START_CONSUME));
        startConsumeBtn.addActionListener(e -> startAction.run());

        SecondaryButton stopConsumeBtn = new SecondaryButton(t(MessageKeys.TOOLBOX_KAFKA_STOP_CONSUME_SHORT), "icons/stop.svg");
        stopConsumeBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_STOP_CONSUME));
        ConnectionToolbarUi.compactButton(stopConsumeBtn, 74);
        stopConsumeBtn.addActionListener(e -> stopAction.run());

        consumeBtnCardLayout = new CardLayout();
        consumeBtnCard = new JPanel(consumeBtnCardLayout);
        consumeBtnCard.setOpaque(false);
        consumeBtnCard.add(startConsumeBtn, CARD_CONSUME_START);
        consumeBtnCard.add(stopConsumeBtn, CARD_CONSUME_STOP);
        consumeBtnCardLayout.show(consumeBtnCard, CARD_CONSUME_START);

        mainControls.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_TOPIC)));
        mainControls.add(topicField);
        mainControls.add(consumeBtnCard);
        mainControls.add(ToolWindowActionToolbar.inlineRight(
                clearConsumeBtn,
                advancedToggleBtn,
                detailToggleBtn
        ));

        JPanel advancedPanel = new JPanel(new BorderLayout(0, 0));
        advancedPanel.setOpaque(false);

        JPanel advancedRowPanel = new JPanel(new MigLayout(
                "insets 4 10 6 8, fillx",
                "[]8[grow,fill]16[]8[grow,fill]16[]8[pref!,fill]28[]8[grow,fill]",
                "[]6[]"
        ));
        ToolWindowSurfaceStyle.applySectionHeader(advancedRowPanel);

        groupIdField = new JTextField("easy-postman-consumer");
        groupIdField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_GROUP_ID_PLACEHOLDER));
        groupIdField.setPreferredSize(new Dimension(0, 28));

        partitionSelector = new KafkaPartitionSelector();
        partitionSelector.setPreferredSize(new Dimension(0, 28));

        autoOffsetCombo = new EasyComboBox<>(new String[]{
                t(MessageKeys.TOOLBOX_KAFKA_OFFSET_RESET_LATEST),
                t(MessageKeys.TOOLBOX_KAFKA_OFFSET_RESET_EARLIEST),
                t(MessageKeys.TOOLBOX_KAFKA_OFFSET_RESET_NONE),
                t(MessageKeys.TOOLBOX_KAFKA_OFFSET_RESET_TIMESTAMP),
                t(MessageKeys.TOOLBOX_KAFKA_OFFSET_RESET_OFFSET)
        }, EasyComboBox.WidthMode.FIXED_MAX);
        autoOffsetCombo.setSelectedIndex(0);
        Dimension autoOffsetSize = autoOffsetCombo.getPreferredSize();
        autoOffsetCombo.setPreferredSize(new Dimension(autoOffsetSize.width, 28));

        consumeStartValueField = new JTextField("");
        consumeStartValueField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_OFFSET_VALUE_PLACEHOLDER));
        consumeStartValueField.setPreferredSize(new Dimension(0, 28));

        pollTimeoutSpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 30000, 100));
        pollTimeoutSpinner.setPreferredSize(new Dimension(110, 28));

        batchSizeSpinner = new JSpinner(new SpinnerNumberModel(100, 1, 5000, 1));
        batchSizeSpinner.setPreferredSize(new Dimension(110, 28));

        maxViewSpinner = new JSpinner(new SpinnerNumberModel(500, 50, 10000, 50));
        maxViewSpinner.setPreferredSize(new Dimension(110, 28));

        advancedRowPanel.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_GROUP_ID)));
        advancedRowPanel.add(groupIdField);
        advancedRowPanel.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_PARTITION)));
        advancedRowPanel.add(partitionSelector);
        advancedRowPanel.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_OFFSET_RESET)));
        advancedRowPanel.add(autoOffsetCombo);
        consumeStartValueLabel = new JLabel(t(MessageKeys.TOOLBOX_KAFKA_OFFSET_VALUE));
        advancedRowPanel.add(consumeStartValueLabel);
        advancedRowPanel.add(consumeStartValueField, "growx, wrap");

        advancedRowPanel.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_POLL_TIMEOUT)));
        advancedRowPanel.add(pollTimeoutSpinner);
        advancedRowPanel.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_BATCH_SIZE)));
        advancedRowPanel.add(batchSizeSpinner);
        advancedRowPanel.add(new JLabel(t(MessageKeys.TOOLBOX_KAFKA_MAX_VIEW)));
        advancedRowPanel.add(maxViewSpinner);

        customPropsPanel = new KafkaPropertiesEditorPanel(
                t(MessageKeys.TOOLBOX_KAFKA_CONSUMER_CUSTOM_PROPERTIES),
                t(MessageKeys.TOOLBOX_KAFKA_CONSUMER_CUSTOM_PROPERTIES_HINT),
                t(MessageKeys.TOOLBOX_KAFKA_CONSUMER_CUSTOM_PROPERTIES_PLACEHOLDER),
                ModernColors::getTextSecondary);

        advancedPanel.add(advancedRowPanel, BorderLayout.NORTH);
        advancedPanel.add(customPropsPanel, BorderLayout.CENTER);
        advancedPanel.setVisible(false);
        advancedToggleBtn.addActionListener(e -> advancedPanel.setVisible(advancedToggleBtn.isSelected()));

        String[] columns = {
                t(MessageKeys.TOOLBOX_KAFKA_COL_MESSAGE_TIME),
                t(MessageKeys.TOOLBOX_KAFKA_COL_TOPIC),
                t(MessageKeys.TOOLBOX_KAFKA_COL_PARTITION),
                t(MessageKeys.TOOLBOX_KAFKA_COL_OFFSET),
                t(MessageKeys.TOOLBOX_KAFKA_COL_KEY),
                t(MessageKeys.TOOLBOX_KAFKA_COL_HEADERS),
                t(MessageKeys.TOOLBOX_KAFKA_COL_VALUE)
        };
        messageTablePanel = new EnhancedTablePanel(columns);
        messageTablePanel.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectionChanged.run();
                if (!detailPanelVisible && messageTablePanel.getTable().getSelectedRow() >= 0) {
                    detailToggleBtn.setSelected(true);
                    showDetailPanel();
                }
            }
        });

        JPanel detailPanel = new JPanel(new BorderLayout(0, 0));
        detailPanel.setOpaque(false);
        detailPanel.setMinimumSize(new Dimension(0, 0));

        JPanel detailHeader = new JPanel(new MigLayout("insets 4 10 4 8, fillx", "[]8[]8[]8[]8[]push[]4[]", "[]2[]"));
        ToolWindowSurfaceStyle.applySectionHeader(detailHeader);

        detailTopicLabel = buildChipLabel("Topic: —", ModernColors::getInfo);
        detailPartitionLabel = buildChipLabel("Partition: —", ModernColors::getSecondary);
        detailOffsetLabel = buildChipLabel("Offset: —", ModernColors::getSuccess);
        detailKeyLabel = buildChipLabel("Key: —", ModernColors::getWarningDark);
        detailMessageTimeLabel = buildMetaLabel(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_MESSAGE_TIME, EMPTY_VALUE));
        detailConsumeTimeLabel = buildMetaLabel(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_CONSUME_TIME, EMPTY_VALUE));
        detailLagLabel = buildMetaLabel(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_LAG, EMPTY_VALUE));

        JPanel timeInfoPanel = ToolWindowActionToolbar.inlineLeft(
                detailMessageTimeLabel,
                detailConsumeTimeLabel,
                detailLagLabel
        );

        CopyButton copyValueBtn = new CopyButton();
        copyValueBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_COPY_VALUE));
        copyValueBtn.addActionListener(e -> copyDetailValue());

        CloseButton closeDetailBtn = new CloseButton();
        closeDetailBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_CLOSE_DETAIL));
        closeDetailBtn.addActionListener(e -> {
            detailToggleBtn.setSelected(false);
            hideDetailPanel();
        });

        detailHeader.add(detailTopicLabel);
        detailHeader.add(detailPartitionLabel);
        detailHeader.add(detailOffsetLabel);
        detailHeader.add(detailKeyLabel);
        detailHeader.add(copyValueBtn, "pushx, align right");
        detailHeader.add(closeDetailBtn, "wrap");
        detailHeader.add(timeInfoPanel, "span, growx");

        detailArea = new FallbackAwareRSyntaxTextArea();
        detailArea.setEditable(false);
        detailArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
        detailArea.setCodeFoldingEnabled(true);
        detailArea.setAntiAliasingEnabled(true);
        detailArea.setLineWrap(false);
        detailArea.setHighlightCurrentLine(true);
        EditorThemeUtil.loadTheme(detailArea);
        detailArea.setText("");
        SearchableTextArea searchableDetail = new SearchableTextArea(detailArea, false);

        detailPanel.add(detailHeader, BorderLayout.NORTH);
        detailPanel.add(searchableDetail, BorderLayout.CENTER);

        detailSplit = ToolWindowChrome.createVerticalInnerSplitPane(messageTablePanel, detailPanel, 320);
        detailSplit.setResizeWeight(1.0);
        SwingUtilities.invokeLater(() -> detailSplit.setDividerLocation(1.0));

        detailToggleBtn.addActionListener(e -> {
            detailPanelVisible = detailToggleBtn.isSelected();
            if (detailPanelVisible) {
                showDetailPanel();
            } else {
                hideDetailPanel();
            }
        });

        statusLabel = new ThemedLabel(t(MessageKeys.TOOLBOX_KAFKA_CONSUMER_READY), ModernColors::getTextSecondary);
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        statusLabel.setBorder(new EmptyBorder(3, 10, 3, 8));

        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(mainControls, BorderLayout.NORTH);
        north.add(advancedPanel, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(detailSplit, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    public void setConsuming(boolean consuming) {
        consumeBtnCardLayout.show(consumeBtnCard, consuming ? CARD_CONSUME_STOP : CARD_CONSUME_START);
    }

    public void setConsumeStartValueVisible(boolean visible) {
        consumeStartValueLabel.setVisible(visible);
        consumeStartValueField.setVisible(visible);
        revalidate();
        repaint();
    }

    public void showDetailPanel() {
        detailPanelVisible = true;
        int total = detailSplit.getHeight();
        int loc = total > 0 ? (int) (total * 0.60) : 300;
        detailSplit.setDividerLocation(loc);
    }

    public void hideDetailPanel() {
        detailPanelVisible = false;
        detailSplit.setDividerLocation(1.0);
    }

    public void clearDetail() {
        detailArea.setText("");
        detailTopicLabel.setText("Topic: —");
        detailPartitionLabel.setText("Partition: —");
        detailOffsetLabel.setText("Offset: —");
        detailKeyLabel.setText("Key: —");
        detailMessageTimeLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_MESSAGE_TIME, EMPTY_VALUE));
        detailConsumeTimeLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_CONSUME_TIME, EMPTY_VALUE));
        detailLagLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_LAG, EMPTY_VALUE));
    }

    public void updateDetail(KafkaConsumedMessage msg) {
        if (msg == null) {
            clearDetail();
            return;
        }
        detailTopicLabel.setText("Topic: " + msg.topic());
        detailPartitionLabel.setText("Partition: " + msg.partition());
        detailOffsetLabel.setText("Offset: " + msg.offset());
        detailKeyLabel.setText("Key: " + displayValue(msg.key()));
        detailMessageTimeLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_MESSAGE_TIME, displayValue(msg.recordTime())));
        detailConsumeTimeLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_CONSUME_TIME, displayValue(msg.receiveTime())));
        detailLagLabel.setText(t(MessageKeys.TOOLBOX_KAFKA_DETAIL_LAG, formatLag(msg)));

        String value = msg.value() == null ? "" : msg.value().trim();
        if (KafkaPanelSupport.isJsonText(value)) {
            detailArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JSON);
            String pretty = JsonUtil.toJsonPrettyStr(value);
            detailArea.setText(pretty != null ? pretty : value);
        } else {
            detailArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
            detailArea.setText(value);
        }
        detailArea.setCaretPosition(0);
    }

    public void updateEditorFont() {
        detailArea.setFont(com.laker.postman.util.FontsUtil.getDefaultFont(Font.PLAIN));
    }

    private void copyDetailValue() {
        String txt = detailArea.getText().trim();
        if (!txt.isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(txt), null);
            NotificationCenter.showSuccess(t(MessageKeys.TOOLBOX_KAFKA_VALUE_COPIED));
        }
    }

    private String formatLag(KafkaConsumedMessage msg) {
        if (msg == null || msg.recordTime() == null || msg.recordTime().isBlank()
                || msg.receiveTime() == null || msg.receiveTime().isBlank()) {
            return EMPTY_VALUE;
        }
        try {
            LocalDateTime recordTime = LocalDateTime.parse(msg.recordTime(), DETAIL_TIME_FORMATTER);
            LocalDateTime consumeTime = LocalDateTime.parse(msg.receiveTime(), DETAIL_TIME_FORMATTER);
            return formatDuration(Duration.between(recordTime, consumeTime).toMillis());
        } catch (RuntimeException ignored) {
            return EMPTY_VALUE;
        }
    }

    private String formatDuration(long millis) {
        String prefix = millis < 0 ? "-" : "";
        long absMillis = Math.abs(millis);
        if (absMillis < 1000) {
            return prefix + absMillis + " ms";
        }
        if (absMillis < 60_000) {
            String seconds = String.format(Locale.ROOT, "%.1f", absMillis / 1000.0);
            if (seconds.endsWith(".0")) {
                seconds = seconds.substring(0, seconds.length() - 2);
            }
            return prefix + seconds + " s";
        }
        long totalSeconds = absMillis / 1000;
        if (totalSeconds < 3600) {
            return String.format(Locale.ROOT, "%s%dm %02ds", prefix, totalSeconds / 60, totalSeconds % 60);
        }
        return String.format(Locale.ROOT, "%s%dh %02dm", prefix, totalSeconds / 3600, (totalSeconds % 3600) / 60);
    }

    private static String displayValue(String value) {
        return value == null || value.isBlank() ? EMPTY_VALUE : value;
    }

    private JLabel buildMetaLabel(String text) {
        JLabel lbl = new ThemedLabel(text, ModernColors::getTextSecondary);
        lbl.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -3));
        lbl.setBorder(new EmptyBorder(0, 0, 0, 0));
        return lbl;
    }

    private JLabel buildChipLabel(String text, Supplier<Color> accentColor) {
        return new ThemedChipLabel(text, accentColor);
    }

    /**
     * 主题切换时重新取语义强调色，避免亮/暗模式下仍沿用旧主题的 chip 底色。
     */
    private static final class ThemedChipLabel extends ChipLabel {
        private final transient Supplier<Color> accentSupplier;

        private ThemedChipLabel(String text, Supplier<Color> accentSupplier) {
            super(text, accentSupplier.get());
            this.accentSupplier = accentSupplier;
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (accentSupplier != null) {
                setAccentColor(accentSupplier.get());
            }
        }
    }
}

package com.laker.postman.plugin.kafka.connection.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.laker.postman.common.component.EasyComboBox;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.connection.ConnectionToolbarUi;
import com.laker.postman.plugin.kafka.MessageKeys;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

import static com.laker.postman.plugin.kafka.KafkaI18n.t;

public class KafkaConnectionPanel extends JPanel {

    private static final String CARD_CONNECT = "connect";
    private static final String CARD_DISCONNECT = "disconnect";
    private static final int GROUP_GAP = 12;
    /** 四组“标签 + 输入框”列：第 1 行 配置 / Host / Client ID / 操作区，第 2 行 协议 / SASL / 用户 / 密码。 */
    private static final int PROFILE_GROUP_MIN_WIDTH = 150;
    private static final int PROFILE_GROUP_PREF_WIDTH = 240;
    private static final int HOST_GROUP_MIN_WIDTH = 110;
    private static final int HOST_GROUP_PREF_WIDTH = 200;
    private static final int CLIENT_ID_GROUP_MIN_WIDTH = 90;
    private static final int CLIENT_ID_GROUP_PREF_WIDTH = 170;
    private static final int ACTION_GROUP_MIN_WIDTH = 90;
    private static final int ACTION_GROUP_PREF_WIDTH = 170;

    public final JComboBox<String> profileCombo;
    public final JButton newProfileBtn;
    public final JButton saveProfileBtn;
    public final JButton saveAsProfileBtn;
    public final JButton deleteProfileBtn;
    public final JTextField bootstrapField;
    public final JTextField clientIdField;
    public final JComboBox<String> securityProtocolCombo;
    public final JComboBox<String> saslMechanismCombo;
    public final JTextField usernameField;
    public final JPasswordField passwordField;
    /** 两行共用同一个网格，保证各列左右边缘严格对齐。 */
    public final JPanel formGrid;
    public final JButton connectBtn;
    public final JButton disconnectBtn;
    public final JButton testConnectionBtn;
    public final CardLayout btnCardLayout;
    public final JPanel btnCard;
    private final JComponent[] saslComponents;

    public KafkaConnectionPanel(Runnable connectAction, Runnable disconnectAction) {
        this(connectAction, disconnectAction, () -> {
        });
    }

    public KafkaConnectionPanel(Runnable connectAction, Runnable disconnectAction, Runnable testConnectionAction) {
        super(new BorderLayout());
        ToolWindowSurfaceStyle.applySectionHeader(this, 3, 6, 3, 6);

        profileCombo = new JComboBox<>();
        profileCombo.setEditable(false);
        ConnectionToolbarUi.compactControl(profileCombo);
        profileCombo.setRenderer(ConnectionToolbarUi.displayRenderer(value -> value == null ? "" : value));

        newProfileBtn = ConnectionToolbarUi.iconButton(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_NEW), "icons/plus.svg");
        saveProfileBtn = ConnectionToolbarUi.iconButton(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_SAVE), "icons/save.svg");
        saveProfileBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_SAVE) + " (Ctrl+S)");
        saveAsProfileBtn = ConnectionToolbarUi.iconButton(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_SAVE_AS), "icons/duplicate.svg");
        deleteProfileBtn = ConnectionToolbarUi.iconButton(t(MessageKeys.TOOLBOX_KAFKA_PROFILE_DELETE), "icons/delete.svg");

        bootstrapField = new JTextField("localhost:9092");
        bootstrapField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_HOST_PLACEHOLDER));
        ConnectionToolbarUi.compactControl(bootstrapField);
        bootstrapField.addActionListener(e -> connectAction.run());

        clientIdField = new JTextField("easy-postman-toolbox");
        clientIdField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_CLIENT_ID_PLACEHOLDER));
        ConnectionToolbarUi.compactControl(clientIdField);

        securityProtocolCombo = new EasyComboBox<>(
                new String[]{"PLAINTEXT", "SASL_PLAINTEXT", "SASL_SSL", "SSL"},
                EasyComboBox.WidthMode.FIXED_MAX);
        ConnectionToolbarUi.compactControl(securityProtocolCombo);

        saslMechanismCombo = new EasyComboBox<>(
                new String[]{"PLAIN", "SCRAM-SHA-256", "SCRAM-SHA-512"},
                EasyComboBox.WidthMode.FIXED_MAX);
        ConnectionToolbarUi.compactControl(saslMechanismCombo);

        usernameField = new JTextField("");
        usernameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_USER_PLACEHOLDER));
        ConnectionToolbarUi.compactControl(usernameField);
        usernameField.addActionListener(e -> connectAction.run());

        passwordField = new JPasswordField("");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, t(MessageKeys.TOOLBOX_KAFKA_PASS_PLACEHOLDER));
        ConnectionToolbarUi.compactControl(passwordField);
        passwordField.addActionListener(e -> connectAction.run());

        connectBtn = ModernButtonFactory.createCompactButton(t(MessageKeys.TOOLBOX_KAFKA_CONNECT), true, "icons/connect.svg");
        connectBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_CONNECT_TOOLTIP));
        connectBtn.addActionListener(e -> connectAction.run());

        disconnectBtn = ModernButtonFactory.createCompactButton(t(MessageKeys.TOOLBOX_KAFKA_DISCONNECT), false, "icons/ws-close.svg");
        disconnectBtn.addActionListener(e -> disconnectAction.run());

        testConnectionBtn = ModernButtonFactory.createCompactButton(t(MessageKeys.TOOLBOX_KAFKA_TEST_CONNECTION), false, "icons/check.svg");
        testConnectionBtn.setToolTipText(t(MessageKeys.TOOLBOX_KAFKA_TEST_CONNECTION_TOOLTIP));
        testConnectionBtn.addActionListener(e -> testConnectionAction.run());

        btnCardLayout = new CardLayout();
        btnCard = new JPanel(btnCardLayout);
        btnCard.setOpaque(false);
        btnCard.add(connectBtn, CARD_CONNECT);
        btnCard.add(disconnectBtn, CARD_DISCONNECT);
        btnCardLayout.show(btnCard, CARD_CONNECT);

        JPanel profileGroup = new JPanel(new MigLayout(
                "insets 0, novisualpadding, gapx 0",
                "[grow,fill]2[" + ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE + "!]0["
                        + ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE + "!]0["
                        + ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE + "!]0["
                        + ConnectionToolbarUi.TOOLBAR_BUTTON_SIZE + "!]",
                "[]"
        ));
        profileGroup.setOpaque(false);
        profileGroup.add(profileCombo, "wmin 0, h " + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!");
        profileGroup.add(newProfileBtn);
        profileGroup.add(saveProfileBtn);
        profileGroup.add(saveAsProfileBtn);
        profileGroup.add(deleteProfileBtn);

        JPanel actions = new JPanel(new MigLayout("insets 0, novisualpadding, gapx 6", "[][]", "[]"));
        actions.setOpaque(false);
        String buttonHeight = "h " + ConnectionToolbarUi.CONNECTION_BUTTON_HEIGHT + "!";
        actions.add(testConnectionBtn, buttonHeight);
        actions.add(btnCard, buttonHeight);

        formGrid = new JPanel(new MigLayout(
                "insets 0, novisualpadding, gapy 4",
                growFieldColumns(PROFILE_GROUP_MIN_WIDTH, PROFILE_GROUP_PREF_WIDTH) + GROUP_GAP
                        + growFieldColumns(HOST_GROUP_MIN_WIDTH, HOST_GROUP_PREF_WIDTH) + GROUP_GAP
                        + growFieldColumns(CLIENT_ID_GROUP_MIN_WIDTH, CLIENT_ID_GROUP_PREF_WIDTH) + GROUP_GAP
                        + growFieldColumns(ACTION_GROUP_MIN_WIDTH, ACTION_GROUP_PREF_WIDTH),
                "[" + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!,center]4[" + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!,center]"
        ));
        formGrid.setOpaque(false);
        String controlHeight = "wmin 0, h " + ConnectionToolbarUi.FORM_CONTROL_HEIGHT + "!";

        // 第 1 行：基础连接
        formGrid.add(ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_KAFKA_PROFILE)));
        formGrid.add(profileGroup);
        formGrid.add(ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_KAFKA_HOST)));
        formGrid.add(bootstrapField, controlHeight);
        formGrid.add(ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_KAFKA_CLIENT_ID)));
        formGrid.add(clientIdField, controlHeight);
        formGrid.add(actions, "span 2, right, wrap");

        // 第 2 行：安全认证
        JLabel saslLabel = compactLabel(
                MessageKeys.TOOLBOX_KAFKA_SASL_MECHANISM_SHORT,
                MessageKeys.TOOLBOX_KAFKA_SASL_MECHANISM);
        JLabel userLabel = ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_KAFKA_USER));
        JLabel passLabel = ConnectionToolbarUi.label(t(MessageKeys.TOOLBOX_KAFKA_PASS));
        formGrid.add(compactLabel(
                MessageKeys.TOOLBOX_KAFKA_SECURITY_PROTOCOL_SHORT,
                MessageKeys.TOOLBOX_KAFKA_SECURITY_PROTOCOL));
        formGrid.add(securityProtocolCombo, controlHeight);
        formGrid.add(saslLabel);
        formGrid.add(saslMechanismCombo, controlHeight);
        formGrid.add(userLabel);
        formGrid.add(usernameField, controlHeight);
        formGrid.add(passLabel);
        formGrid.add(passwordField, controlHeight);
        saslComponents = new JComponent[]{saslLabel, saslMechanismCombo, userLabel, usernameField, passLabel, passwordField};

        JPanel wrapper = new JPanel(new MigLayout("insets 0, novisualpadding, fillx", "[grow,fill]", "[]"));
        wrapper.setOpaque(false);
        wrapper.add(formGrid, "growx, wmin 0");
        add(wrapper, BorderLayout.CENTER);
        ConnectionToolbarUi.lockConnectionPanelHeight(this, true);
    }

    /**
     * 标签列按本列最长标签自适应；字段列窄窗口可压缩到 min，宽窗口均分多余空间避免右侧留白，最多拉伸到 2 倍。
     */
    private static String growFieldColumns(int minWidth, int prefWidth) {
        int maxWidth = prefWidth * 2;
        return "[pref!,right]4[" + minWidth + ":" + prefWidth + ":" + maxWidth + ",fill,grow,shrink]";
    }

    private static JLabel compactLabel(String shortKey, String fullKey) {
        JLabel label = ConnectionToolbarUi.label(t(shortKey));
        label.setToolTipText(t(fullKey));
        return label;
    }

    /**
     * 非 SASL 协议下禁用（而非隐藏）SASL 机制与账号密码：布局保持稳定，用户也能看到切换协议后可填写哪些项。
     */
    public void setSaslFieldsEnabled(boolean enabled) {
        for (JComponent component : saslComponents) {
            component.setEnabled(enabled);
        }
    }

    public void showConnected(boolean connected) {
        btnCardLayout.show(btnCard, connected ? CARD_DISCONNECT : CARD_CONNECT);
    }
}

package com.laker.postman.panel.collections.editor.request.sub;

import com.laker.postman.request.model.AuthApiKeyPlacement;
import com.laker.postman.request.model.AuthType;
import com.laker.postman.request.model.RequestAuthTypes;


import com.laker.postman.common.component.EasyTextField;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证Tab面板 - 现代化版本
 * 使用专业的布局和现代化配色方案
 * 支持亮色和暗色主题自适应
 */
public class AuthTabPanel extends JPanel {
    private static final String AUTH_TYPE_INHERIT = RequestAuthTypes.AUTH_TYPE_INHERIT;
    private static final String AUTH_TYPE_NONE = RequestAuthTypes.AUTH_TYPE_NONE;
    private static final String AUTH_TYPE_API_KEY = RequestAuthTypes.AUTH_TYPE_API_KEY;
    private static final String AUTH_TYPE_BASIC = RequestAuthTypes.AUTH_TYPE_BASIC;
    private static final String AUTH_TYPE_BEARER = RequestAuthTypes.AUTH_TYPE_BEARER;
    private static final String AUTH_TYPE_DIGEST = RequestAuthTypes.AUTH_TYPE_DIGEST;

    private final JComboBox<AuthType> typeCombo;
    private final JTextField usernameField;
    private final JTextField passwordField;
    private final JTextField digestUsernameField;
    private final JTextField digestPasswordField;
    private final JTextField tokenField;
    private final JTextField apiKeyNameField;
    private final JTextField apiKeyValueField;
    private final JComboBox<AuthApiKeyPlacement> apiKeyPlacementCombo;
    private AuthType currentType = AuthType.INHERIT;
    private final List<Runnable> dirtyListeners = new ArrayList<>();

    public AuthTabPanel() {
        setLayout(new BorderLayout(5, 5));
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(new EmptyBorder(8, 10, 8, 10));

        // 初始化所有字段
        typeCombo = new JComboBox<>(AuthType.values());
        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AuthType authType) {
                    setText(authTypeDisplayText(authType));
                }
                return this;
            }
        });
        typeCombo.setPreferredSize(new Dimension(220, 32));
        typeCombo.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));

        usernameField = new EasyTextField(20);
        setFieldSize(usernameField, 200);

        passwordField = new EasyTextField(20);
        setFieldSize(passwordField, 200);

        digestUsernameField = new EasyTextField(20);
        setFieldSize(digestUsernameField, 200);

        digestPasswordField = new EasyTextField(20);
        setFieldSize(digestPasswordField, 200);

        tokenField = new EasyTextField("", 30, I18nUtil.getMessage(MessageKeys.AUTH_TOKEN_PLACEHOLDER));
        setFieldSize(tokenField, 250);

        apiKeyNameField = new EasyTextField("", 20, I18nUtil.getMessage(MessageKeys.AUTH_API_KEY_NAME_PLACEHOLDER));
        setFieldSize(apiKeyNameField, 250);

        apiKeyValueField = new EasyTextField("", 30, I18nUtil.getMessage(MessageKeys.AUTH_API_KEY_VALUE_PLACEHOLDER));
        setFieldSize(apiKeyValueField, 250);

        apiKeyPlacementCombo = new JComboBox<>(AuthApiKeyPlacement.values());
        apiKeyPlacementCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AuthApiKeyPlacement placement) {
                    setText(apiKeyPlacementDisplayText(placement));
                }
                return this;
            }
        });
        setFieldSize(apiKeyPlacementCombo, 250);
        apiKeyPlacementCombo.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));

        // 顶部：认证类型选择
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        topPanel.setOpaque(false);
        JLabel typeLabel = new JLabel(I18nUtil.getMessage(MessageKeys.AUTH_TYPE_LABEL));
        typeLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        typeLabel.setForeground(ModernColors.getTextPrimary());
        topPanel.add(typeLabel);
        topPanel.add(typeCombo);
        add(topPanel, BorderLayout.NORTH);

        // 中间：各种认证类型的配置面板（使用 CardLayout）
        JPanel cardPanel = new JPanel(new CardLayout());
        ToolWindowSurfaceStyle.applyCard(cardPanel);

        // Inherit Auth Panel
        cardPanel.add(createInheritPanel(), AUTH_TYPE_INHERIT);

        // No Auth Panel
        cardPanel.add(createNoAuthPanel(), AUTH_TYPE_NONE);

        // API Key Panel
        cardPanel.add(createApiKeyPanel(), AUTH_TYPE_API_KEY);

        // Basic Auth Panel
        cardPanel.add(createBasicAuthPanel(), AUTH_TYPE_BASIC);

        // Bearer Token Panel
        cardPanel.add(createBearerPanel(), AUTH_TYPE_BEARER);

        // Digest Auth Panel
        cardPanel.add(createDigestAuthPanel(), AUTH_TYPE_DIGEST);

        add(cardPanel, BorderLayout.CENTER);

        // 监听认证类型切换
        typeCombo.addActionListener(e -> {
            AuthType previousType = currentType;
            currentType = (AuthType) typeCombo.getSelectedItem();
            syncCredentialFields(previousType, currentType);
            CardLayout cl = (CardLayout) cardPanel.getLayout();
            cl.show(cardPanel, currentType.getConstant());
            fireDirty();
        });

        // 监听文本框内容变化
        DocumentListener docListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                fireDirty();
            }

            public void removeUpdate(DocumentEvent e) {
                fireDirty();
            }

            public void changedUpdate(DocumentEvent e) {
                fireDirty();
            }
        };
        usernameField.getDocument().addDocumentListener(docListener);
        passwordField.getDocument().addDocumentListener(docListener);
        digestUsernameField.getDocument().addDocumentListener(docListener);
        digestPasswordField.getDocument().addDocumentListener(docListener);
        tokenField.getDocument().addDocumentListener(docListener);
        apiKeyNameField.getDocument().addDocumentListener(docListener);
        apiKeyValueField.getDocument().addDocumentListener(docListener);
        apiKeyPlacementCombo.addActionListener(e -> fireDirty());

        // 默认显示继承面板
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, AUTH_TYPE_INHERIT);
    }

    private static String authTypeDisplayText(AuthType type) {
        return switch (type) {
            case INHERIT -> I18nUtil.getMessage(MessageKeys.AUTH_TYPE_INHERIT);
            case NONE -> I18nUtil.getMessage(MessageKeys.AUTH_TYPE_NONE);
            case API_KEY -> I18nUtil.getMessage(MessageKeys.AUTH_TYPE_API_KEY);
            case BASIC -> I18nUtil.getMessage(MessageKeys.AUTH_TYPE_BASIC);
            case BEARER -> I18nUtil.getMessage(MessageKeys.AUTH_TYPE_BEARER);
            case DIGEST -> I18nUtil.getMessage(MessageKeys.AUTH_TYPE_DIGEST);
        };
    }

    /**
     * GridBagLayout falls back to minimum sizes when the editor is vertically cramped; text fields would then
     * shrink to a few pixels, so the minimum width is pinned to the preferred width.
     */
    private static void setFieldSize(JComponent field, int width) {
        if (field instanceof JTextField textField) {
            // JTextField derives its preferred width from columns and ignores setPreferredSize unless columns is 0.
            textField.setColumns(0);
        }
        Dimension size = new Dimension(width, 32);
        field.setPreferredSize(size);
        field.setMinimumSize(size);
    }

    private static String apiKeyPlacementDisplayText(AuthApiKeyPlacement placement) {
        return switch (placement) {
            case HEADER -> I18nUtil.getMessage(MessageKeys.AUTH_API_KEY_ADD_TO_HEADER);
            case QUERY_PARAMS -> I18nUtil.getMessage(MessageKeys.AUTH_API_KEY_ADD_TO_QUERY_PARAMS);
        };
    }

    private JPanel createInfoPanel(String title, String description, boolean muted) {
        JPanel infoPanel = new JPanel(new BorderLayout(10, 0));
        ToolWindowSurfaceStyle.applySectionHeader(infoPanel, 8, 12, 8, 12);

        JLabel iconLabel = new JLabel("i");
        iconLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD));
        iconLabel.setForeground(muted ? ModernColors.getTextSecondary() : ModernColors.getPrimary());
        infoPanel.add(iconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, -1));
        titleLabel.setForeground(muted ? ModernColors.getTextPrimary() : ModernColors.getPrimary());
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(titleLabel);

        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
        descriptionLabel.setForeground(ModernColors.getTextSecondary());
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(descriptionLabel);

        infoPanel.add(textPanel, BorderLayout.CENTER);
        return infoPanel;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        label.setForeground(ModernColors.getTextSecondary());
        return label;
    }

    /**
     * 创建 Inherit Auth 面板
     */
    private JPanel createInheritPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(panel);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        panel.add(createInfoPanel(
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_INHERIT),
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_INHERIT_DESC),
                false
        ), BorderLayout.NORTH);
        return panel;
    }

    /**
     * 创建 No Auth 面板
     */
    private JPanel createNoAuthPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(panel);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        panel.add(createInfoPanel(
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_NONE),
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_NONE_DESC),
                true
        ), BorderLayout.NORTH);
        return panel;
    }

    /**
     * 创建 API Key 面板
     */
    private JPanel createApiKeyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(panel);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        panel.add(createInfoPanel(
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_API_KEY),
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_API_KEY_DESC),
                false
        ), BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        ToolWindowSurfaceStyle.applyCard(formPanel);
        formPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel keyLabel = createFieldLabel(I18nUtil.getMessage(MessageKeys.AUTH_API_KEY_NAME));
        formPanel.add(keyLabel, gbc);

        gbc.gridx = 1;
        formPanel.add(apiKeyNameField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel valueLabel = createFieldLabel(I18nUtil.getMessage(MessageKeys.AUTH_API_KEY_VALUE));
        formPanel.add(valueLabel, gbc);

        gbc.gridx = 1;
        formPanel.add(apiKeyValueField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel addToLabel = createFieldLabel(I18nUtil.getMessage(MessageKeys.AUTH_API_KEY_ADD_TO));
        formPanel.add(addToLabel, gbc);

        gbc.gridx = 1;
        formPanel.add(apiKeyPlacementCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createGlue(), gbc);

        panel.add(formPanel, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 创建 Basic Auth 面板
     */
    private JPanel createBasicAuthPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(panel);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        // 顶部：描述信息
        panel.add(createInfoPanel(
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_BASIC),
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_BASIC_DESC),
                false
        ), BorderLayout.NORTH);

        // 中间：输入字段
        JPanel formPanel = new JPanel(new GridBagLayout());
        ToolWindowSurfaceStyle.applyCard(formPanel);
        formPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Username
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel usernameLabel = new JLabel(I18nUtil.getMessage(MessageKeys.AUTH_USERNAME));
        usernameLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        usernameLabel.setForeground(ModernColors.getTextSecondary());
        formPanel.add(usernameLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(usernameField, gbc);

        // 添加水平填充空间
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(Box.createHorizontalGlue(), gbc);

        // Password
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel passwordLabel = new JLabel(I18nUtil.getMessage(MessageKeys.AUTH_PASSWORD));
        passwordLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        passwordLabel.setForeground(ModernColors.getTextSecondary());
        formPanel.add(passwordLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(passwordField, gbc);

        // 添加水平填充空间
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(Box.createHorizontalGlue(), gbc);

        // 填充剩余垂直空间
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createGlue(), gbc);

        panel.add(formPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建 Bearer Token 面板
     */
    private JPanel createBearerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(panel);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        // 顶部：描述信息
        panel.add(createInfoPanel(
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_BEARER),
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_BEARER_DESC),
                false
        ), BorderLayout.NORTH);

        // 中间：输入字段
        JPanel formPanel = new JPanel(new GridBagLayout());
        ToolWindowSurfaceStyle.applyCard(formPanel);
        formPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Token
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel tokenLabel = new JLabel(I18nUtil.getMessage(MessageKeys.AUTH_TOKEN));
        tokenLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        tokenLabel.setForeground(ModernColors.getTextSecondary());
        formPanel.add(tokenLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(tokenField, gbc);

        // 添加水平填充空间
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(Box.createHorizontalGlue(), gbc);

        // 填充剩余垂直空间
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createGlue(), gbc);

        panel.add(formPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 创建 Digest Auth 面板
     */
    private JPanel createDigestAuthPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(panel);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        panel.add(createInfoPanel(
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_DIGEST),
                I18nUtil.getMessage(MessageKeys.AUTH_TYPE_DIGEST_DESC),
                false
        ), BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        ToolWindowSurfaceStyle.applyCard(formPanel);
        formPanel.setBorder(new EmptyBorder(8, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel usernameLabel = new JLabel(I18nUtil.getMessage(MessageKeys.AUTH_USERNAME));
        usernameLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        usernameLabel.setForeground(ModernColors.getTextSecondary());
        formPanel.add(usernameLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(digestUsernameField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel passwordLabel = new JLabel(I18nUtil.getMessage(MessageKeys.AUTH_PASSWORD));
        passwordLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        passwordLabel.setForeground(ModernColors.getTextSecondary());
        formPanel.add(passwordLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(digestPasswordField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(Box.createHorizontalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createGlue(), gbc);

        panel.add(formPanel, BorderLayout.CENTER);

        return panel;
    }

    public String getAuthType() {
        AuthType selected = (AuthType) typeCombo.getSelectedItem();
        return selected != null ? selected.getConstant() : AUTH_TYPE_INHERIT;
    }

    public void setAuthType(String type) {
        AuthType authType = AuthType.fromConstant(type);
        typeCombo.setSelectedItem(authType);
        currentType = authType;
    }

    public String getUsername() {
        return currentType == AuthType.DIGEST ? digestUsernameField.getText() : usernameField.getText();
    }

    public void setUsername(String u) {
        String value = u == null ? "" : u;
        usernameField.setText(value);
        digestUsernameField.setText(value);
    }

    public String getPassword() {
        return currentType == AuthType.DIGEST ? digestPasswordField.getText() : passwordField.getText();
    }

    public void setPassword(String p) {
        String value = p == null ? "" : p;
        passwordField.setText(value);
        digestPasswordField.setText(value);
    }

    public String getToken() {
        return tokenField.getText();
    }

    public void setToken(String t) {
        tokenField.setText(t == null ? "" : t);
    }

    public String getApiKeyName() {
        return apiKeyNameField.getText();
    }

    public void setApiKeyName(String name) {
        apiKeyNameField.setText(name == null ? "" : name);
    }

    public String getApiKeyValue() {
        return apiKeyValueField.getText();
    }

    public void setApiKeyValue(String value) {
        apiKeyValueField.setText(value == null ? "" : value);
    }

    public String getApiKeyPlacement() {
        AuthApiKeyPlacement selected = (AuthApiKeyPlacement) apiKeyPlacementCombo.getSelectedItem();
        return selected == null ? AuthApiKeyPlacement.HEADER.getConstant() : selected.getConstant();
    }

    public void setApiKeyPlacement(String placement) {
        apiKeyPlacementCombo.setSelectedItem(AuthApiKeyPlacement.fromConstant(placement));
    }

    public void setEditable(boolean editable) {
        typeCombo.setEnabled(editable);
        usernameField.setEditable(editable);
        passwordField.setEditable(editable);
        digestUsernameField.setEditable(editable);
        digestPasswordField.setEditable(editable);
        tokenField.setEditable(editable);
        apiKeyNameField.setEditable(editable);
        apiKeyValueField.setEditable(editable);
        apiKeyPlacementCombo.setEnabled(editable);
    }

    /**
     * 注册脏数据监听器
     */
    public void addDirtyListener(Runnable listener) {
        if (listener != null) dirtyListeners.add(listener);
    }

    private void fireDirty() {
        for (Runnable l : dirtyListeners) l.run();
    }

    private void syncCredentialFields(AuthType previousType, AuthType nextType) {
        if (!usesUsernamePassword(previousType) || !usesUsernamePassword(nextType) || previousType == nextType) {
            return;
        }

        JTextField sourceUsername = previousType == AuthType.DIGEST ? digestUsernameField : usernameField;
        JTextField sourcePassword = previousType == AuthType.DIGEST ? digestPasswordField : passwordField;
        JTextField targetUsername = nextType == AuthType.DIGEST ? digestUsernameField : usernameField;
        JTextField targetPassword = nextType == AuthType.DIGEST ? digestPasswordField : passwordField;

        targetUsername.setText(sourceUsername.getText());
        targetPassword.setText(sourcePassword.getText());
    }

    private boolean usesUsernamePassword(AuthType type) {
        return type == AuthType.BASIC || type == AuthType.DIGEST;
    }

}

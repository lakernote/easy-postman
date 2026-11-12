package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.setting.SettingsFieldRow;
import com.laker.postman.common.component.setting.SettingsHintLabel;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.model.NotificationPosition;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.sidebar.SidebarTab;
import com.laker.postman.panel.sidebar.SidebarTabSettingsResolver;
import com.laker.postman.panel.sidebar.SidebarTabPanel;
import com.laker.postman.service.setting.SettingManager;
import com.laker.postman.util.EditorFontManager;
import com.laker.postman.util.FontManager;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.util.UIRefreshManager;
import com.laker.postman.util.UiFontCatalog;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.awt.*;

/**
 * 现代化界面设置面板 - 下载进度、历史记录等UI相关配置
 */
@Slf4j
public class UISettingsPanelModern extends ModernSettingsPanel {
    private static final int FIELD_SPACING = 8;
    private static final int SECTION_SPACING = 12;

    private JCheckBox showDownloadProgressCheckBox;
    private JTextField downloadProgressDialogThresholdField;
    private JTextField gitDiffLargeFileThresholdField;
    private JTextField maxHistoryCountField;
    private JTextField maxOpenedRequestsCountField;
    private JCheckBox requestEditorTabsMultiLineCheckBox;
    private JCheckBox autoFormatResponseCheckBox;
    private JCheckBox startupSplashCheckBox;
    private JCheckBox sidebarExpandedCheckBox;
    private JComboBox<String> notificationPositionComboBox;
    private SettingsFieldRow downloadProgressDialogThresholdRow;
    private JComboBox<String> fontNameComboBox;
    private JTextField fontSizeField;
    private JLabel fontPreviewLabel;
    private SettingsHintLabel fontSupportHintLabel;
    private int fieldLabelWidth;
    private JComboBox<String> editorFontNameComboBox;
    private JComboBox<String> editorFontFallbackNameComboBox;
    private JTextField editorFontSizeField;
    private JLabel editorFontPreviewLabel;
    private DefaultListModel<SidebarTabSettingItem> sidebarTabListModel;
    private JList<SidebarTabSettingItem> sidebarTabList;
    private JTextField sidebarTabsStateField;
    private List<UiFontCatalog.FontOption> availableFontOptions = List.of();
    private boolean fontOptionsLoaded;
    private boolean fontOptionsLoading;

    private enum FontComboRole {
        UI,
        EDITOR_PRIMARY,
        EDITOR_FALLBACK,
        ALL
    }

    @Override
    protected void buildContent(JPanel contentPanel) {
        // 整页共用同一标签列宽，保证各分区的输入框左边缘对齐
        fieldLabelWidth = calculateFieldLabelWidth(List.of(
                I18nUtil.getMessage(MessageKeys.SETTINGS_DOWNLOAD_THRESHOLD),
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_MAX_HISTORY),
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_MAX_OPENED_REQUESTS),
                I18nUtil.getMessage(MessageKeys.SETTINGS_GIT_DIFF_LARGE_FILE_THRESHOLD),
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_TABS),
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_NOTIFICATION_POSITION),
                I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_NAME),
                I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_SIZE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_NAME),
                I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_FALLBACK_NAME),
                I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_SIZE)
        ));

        // 下载设置区域
        JPanel downloadSection = createModernSection(
                I18nUtil.getMessage(MessageKeys.SETTINGS_DOWNLOAD_TITLE),
                ""
        );

        // 显示下载进度对话框
        showDownloadProgressCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_DOWNLOAD_SHOW_PROGRESS),
                SettingManager.isShowDownloadProgressDialog()
        );
        JPanel showProgressRow = createCheckBoxRow(
                showDownloadProgressCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_DOWNLOAD_SHOW_PROGRESS_TOOLTIP)
        );
        downloadSection.add(showProgressRow);
        downloadSection.add(createVerticalSpace(FIELD_SPACING));

        // 下载阈值
        downloadProgressDialogThresholdField = new JTextField(10);
        int thresholdMB = SettingManager.getDownloadProgressDialogThreshold() / (1024 * 1024);
        downloadProgressDialogThresholdField.setText(String.valueOf(thresholdMB));
        String downloadThresholdLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_DOWNLOAD_THRESHOLD);

        downloadProgressDialogThresholdRow = createFieldRow(
                downloadThresholdLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_DOWNLOAD_THRESHOLD_TOOLTIP),
                downloadProgressDialogThresholdField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        downloadSection.add(downloadProgressDialogThresholdRow);

        // 设置阈值字段的启用状态
        downloadProgressDialogThresholdRow.setEnabled(showDownloadProgressCheckBox.isSelected());

        showDownloadProgressCheckBox.addItemListener(e -> {
            boolean selected = e.getStateChange() == java.awt.event.ItemEvent.SELECTED;
            downloadProgressDialogThresholdRow.setEnabled(selected);
        });

        contentPanel.add(downloadSection);
        contentPanel.add(createVerticalSpace(SECTION_SPACING));

        // 通用设置区域
        JPanel generalSection = createModernSection(
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_TITLE),
                ""
        );
        String maxHistoryLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_MAX_HISTORY);
        String maxOpenedRequestsLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_MAX_OPENED_REQUESTS);
        String gitDiffThresholdLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_GIT_DIFF_LARGE_FILE_THRESHOLD);
        String notificationPositionLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_NOTIFICATION_POSITION);

        // 请求标签页
        maxOpenedRequestsCountField = new JTextField(10);
        maxOpenedRequestsCountField.setText(String.valueOf(SettingManager.getMaxOpenedRequestsCount()));
        JPanel requestsRow = createFieldRow(
                maxOpenedRequestsLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_MAX_OPENED_REQUESTS_TOOLTIP),
                maxOpenedRequestsCountField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        generalSection.add(requestsRow);
        generalSection.add(createVerticalSpace(FIELD_SPACING));

        requestEditorTabsMultiLineCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_REQUEST_TABS_MULTILINE),
                SettingManager.isRequestEditorTabsMultiLineEnabled()
        );
        JPanel requestTabsMultiLineRow = createCheckBoxRow(
                requestEditorTabsMultiLineCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_REQUEST_TABS_MULTILINE_TOOLTIP)
        );
        generalSection.add(requestTabsMultiLineRow);
        generalSection.add(createVerticalSpace(FIELD_SPACING));

        // 历史记录数量
        maxHistoryCountField = new JTextField(10);
        maxHistoryCountField.setText(String.valueOf(SettingManager.getMaxHistoryCount()));
        JPanel historyRow = createFieldRow(
                maxHistoryLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_MAX_HISTORY_TOOLTIP),
                maxHistoryCountField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        generalSection.add(historyRow);
        generalSection.add(createVerticalSpace(FIELD_SPACING));

        // 自动格式化响应体
        autoFormatResponseCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_AUTO_FORMAT_RESPONSE),
                SettingManager.isAutoFormatResponse()
        );
        JPanel formatRow = createCheckBoxRow(
                autoFormatResponseCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_AUTO_FORMAT_RESPONSE_TOOLTIP)
        );
        generalSection.add(formatRow);
        generalSection.add(createVerticalSpace(FIELD_SPACING));

        startupSplashCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_STARTUP_SPLASH),
                SettingManager.isStartupSplashEnabled()
        );
        JPanel startupSplashRow = createCheckBoxRow(
                startupSplashCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_STARTUP_SPLASH_TOOLTIP)
        );
        generalSection.add(startupSplashRow);
        generalSection.add(createVerticalSpace(FIELD_SPACING));

        // 侧边栏展开设置
        sidebarExpandedCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_EXPANDED),
                SettingManager.isSidebarExpanded()
        );
        JPanel sidebarRow = createCheckBoxRow(
                sidebarExpandedCheckBox,
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_EXPANDED_TOOLTIP)
        );
        generalSection.add(sidebarRow);
        generalSection.add(createVerticalSpace(FIELD_SPACING));

        JPanel sidebarTabsRow = createSidebarTabsRow(fieldLabelWidth);
        generalSection.add(sidebarTabsRow);
        generalSection.add(createVerticalSpace(FIELD_SPACING));

        // 通知位置设置 - 使用枚举的 i18nKey
        NotificationPosition[] positions = NotificationPosition.values();
        String[] positionLabels = new String[positions.length];
        for (int i = 0; i < positions.length; i++) {
            positionLabels[i] = I18nUtil.getMessage(positions[i].getI18nKey());
        }
        notificationPositionComboBox = new JComboBox<>(positionLabels);

        // 设置当前值 - 直接从 SettingManager 获取枚举
        NotificationPosition currentPosition = SettingManager.getNotificationPosition();
        notificationPositionComboBox.setSelectedIndex(currentPosition.getIndex());

        JPanel notificationPositionRow = createFieldRow(
                notificationPositionLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_NOTIFICATION_POSITION_TOOLTIP),
                notificationPositionComboBox,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        generalSection.add(notificationPositionRow);
        generalSection.add(createVerticalSpace(FIELD_SPACING));

        gitDiffLargeFileThresholdField = new JTextField(10);
        gitDiffLargeFileThresholdField.setText(String.valueOf(SettingManager.getGitDiffLargeFileThresholdMb()));
        JPanel gitDiffThresholdRow = createFieldRow(
                gitDiffThresholdLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_GIT_DIFF_LARGE_FILE_THRESHOLD_TOOLTIP),
                gitDiffLargeFileThresholdField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        generalSection.add(gitDiffThresholdRow);

        contentPanel.add(generalSection);
        contentPanel.add(createVerticalSpace(SECTION_SPACING));

        contentPanel.add(createUiFontSection());
        contentPanel.add(createVerticalSpace(SECTION_SPACING));
        contentPanel.add(createEditorFontSection());
        contentPanel.add(createVerticalSpace(SECTION_SPACING));

        setupValidators();

        // 跟踪所有组件的初始值
        trackComponentValue(showDownloadProgressCheckBox);
        trackComponentValue(downloadProgressDialogThresholdField);
        trackComponentValue(gitDiffLargeFileThresholdField);
        trackComponentValue(maxHistoryCountField);
        trackComponentValue(maxOpenedRequestsCountField);
        trackComponentValue(requestEditorTabsMultiLineCheckBox);
        trackComponentValue(autoFormatResponseCheckBox);
        trackComponentValue(startupSplashCheckBox);
        trackComponentValue(sidebarExpandedCheckBox);
        trackComponentValue(notificationPositionComboBox);
        trackComponentValue(fontNameComboBox);
        trackComponentValue(fontSizeField);
        trackComponentValue(editorFontNameComboBox);
        trackComponentValue(editorFontFallbackNameComboBox);
        trackComponentValue(editorFontSizeField);
        trackComponentValue(sidebarTabsStateField);
    }

    private JPanel createUiFontSection() {
        JPanel fontSection = createModernSection(
                I18nUtil.getMessage(MessageKeys.SETTINGS_UI_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_RESTART_RECOMMENDED)
        );
        String fontNameLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_NAME);
        String fontSizeLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_SIZE);

        fontNameComboBox = createFontComboBox();
        String currentFont = FontManager.resolveAllowedUiFontNameForLocale(SettingManager.getUiFontName(), I18nUtil.currentLocale());
        resetFontComboItems(
                fontNameComboBox,
                currentFont,
                I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_SYSTEM_DEFAULT),
                FontComboRole.UI
        );
        selectFontComboValue(fontNameComboBox, currentFont);
        preloadFontOptionsInBackground();
        installFontComboLazyLoader(fontNameComboBox);

        JPanel fontNameRow = createFieldRow(
                fontNameLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_NAME_TOOLTIP),
                fontNameComboBox,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        fontSection.add(fontNameRow);
        fontSection.add(createVerticalSpace(FIELD_SPACING));

        fontSizeField = new JTextField(10);
        fontSizeField.setText(String.valueOf(SettingManager.getUiFontSize()));
        JPanel fontSizeRow = createFieldRow(
                fontSizeLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_SIZE_TOOLTIP),
                fontSizeField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        fontSection.add(fontSizeRow);
        fontSection.add(createVerticalSpace(FIELD_SPACING));

        fontPreviewLabel = new JLabel(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_PREVIEW));
        fontPreviewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        fontPreviewLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        fontPreviewLabel.setOpaque(true);
        fontPreviewLabel.setBackground(getInputBackgroundColor());
        fontPreviewLabel.setForeground(getTextPrimaryColor());

        fontSupportHintLabel = new SettingsHintLabel(
                "",
                fieldLabelWidth + SettingsFieldRow.DEFAULT_GAP + SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );

        updateFontPreview();

        fontNameComboBox.addActionListener(e -> updateFontPreview());
        fontSizeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateFontPreview();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateFontPreview();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateFontPreview();
            }
        });

        fontSection.add(fontPreviewLabel);
        fontSection.add(createVerticalSpace(FIELD_SPACING));
        fontSection.add(fontSupportHintLabel);
        return fontSection;
    }

    private JPanel createEditorFontSection() {
        JPanel editorFontSection = createModernSection(
                I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_TITLE),
                I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_DESCRIPTION)
        );
        String editorFontNameLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_NAME);
        String editorFontFallbackLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_FALLBACK_NAME);
        String editorFontSizeLabel = I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_SIZE);

        editorFontNameComboBox = createFontComboBox();
        String currentEditorFont = SettingManager.getEditorFontName();
        resetFontComboItems(
                editorFontNameComboBox,
                currentEditorFont,
                createEditorPrimaryAutoFontLabel()
        );
        selectFontComboValue(editorFontNameComboBox, currentEditorFont);
        installFontComboLazyLoader(editorFontNameComboBox);

        JPanel editorFontNameRow = createFieldRow(
                editorFontNameLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_NAME_TOOLTIP),
                editorFontNameComboBox,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        editorFontSection.add(editorFontNameRow);
        editorFontSection.add(createVerticalSpace(FIELD_SPACING));

        editorFontFallbackNameComboBox = createFontComboBox();
        String currentEditorFallbackFont = SettingManager.getEditorFontFallbackName();
        resetFontComboItems(
                editorFontFallbackNameComboBox,
                currentEditorFallbackFont,
                createEditorFallbackAutoFontLabel(),
                FontComboRole.EDITOR_FALLBACK
        );
        selectFontComboValue(editorFontFallbackNameComboBox, currentEditorFallbackFont);
        installFontComboLazyLoader(editorFontFallbackNameComboBox);

        JPanel editorFontFallbackRow = createFieldRow(
                editorFontFallbackLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_FALLBACK_NAME_TOOLTIP),
                editorFontFallbackNameComboBox,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        editorFontSection.add(editorFontFallbackRow);
        editorFontSection.add(createVerticalSpace(FIELD_SPACING));

        editorFontSizeField = new JTextField(10);
        editorFontSizeField.setText(String.valueOf(SettingManager.getEditorFontSize()));
        JPanel editorFontSizeRow = createFieldRow(
                editorFontSizeLabel,
                I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_SIZE_TOOLTIP),
                editorFontSizeField,
                fieldLabelWidth,
                SettingsFieldRow.DEFAULT_FIELD_WIDTH
        );
        editorFontSection.add(editorFontSizeRow);
        editorFontSection.add(createVerticalSpace(FIELD_SPACING));

        editorFontPreviewLabel = new EditorFontPreviewLabel(I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_PREVIEW));
        editorFontPreviewLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        editorFontPreviewLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        editorFontPreviewLabel.setOpaque(true);
        editorFontPreviewLabel.setBackground(getInputBackgroundColor());
        editorFontPreviewLabel.setForeground(getTextPrimaryColor());

        updateEditorFontPreview();

        editorFontNameComboBox.addActionListener(e -> updateEditorFontPreview());
        editorFontFallbackNameComboBox.addActionListener(e -> updateEditorFontPreview());
        editorFontSizeField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                updateEditorFontPreview();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                updateEditorFontPreview();
            }

            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                updateEditorFontPreview();
            }
        });

        editorFontSection.add(editorFontPreviewLabel);
        return editorFontSection;
    }

    private JPanel createSidebarTabsRow(int labelWidth) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setBackground(getBackgroundColor());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));

        JLabel label = new JLabel(I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_TABS));
        label.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        label.setForeground(getTextPrimaryColor());
        label.setToolTipText(I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_TABS_TOOLTIP));
        label.setPreferredSize(new Dimension(labelWidth, 32));
        label.setMinimumSize(new Dimension(labelWidth, 32));
        label.setMaximumSize(new Dimension(labelWidth, 32));
        label.setAlignmentY(Component.TOP_ALIGNMENT);

        JPanel editor = createSidebarTabsEditor();
        editor.setAlignmentY(Component.TOP_ALIGNMENT);

        row.add(label);
        row.add(Box.createHorizontalStrut(SettingsFieldRow.DEFAULT_GAP));
        row.add(editor);
        row.add(Box.createHorizontalGlue());

        return row;
    }

    private JPanel createSidebarTabsEditor() {
        JPanel editor = new JPanel();
        editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));
        editor.setOpaque(false);
        editor.setBackground(getBackgroundColor());
        editor.setAlignmentX(Component.LEFT_ALIGNMENT);
        editor.setMaximumSize(new Dimension(340, 260));

        sidebarTabListModel = new DefaultListModel<>();
        Set<String> hiddenTabs = SettingManager.getHiddenSidebarTabs();
        for (SidebarTab tab : SidebarTabSettingsResolver.getOrderedSidebarTabs()) {
            sidebarTabListModel.addElement(new SidebarTabSettingItem(tab, !hiddenTabs.contains(tab.name())));
        }

        sidebarTabList = new JList<>(sidebarTabListModel);
        sidebarTabList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sidebarTabList.setCellRenderer(new SidebarTabListCellRenderer());
        sidebarTabList.setDragEnabled(true);
        sidebarTabList.setDropMode(DropMode.INSERT);
        sidebarTabList.setTransferHandler(new SidebarTabListTransferHandler());
        sidebarTabList.setFixedCellHeight(38);
        sidebarTabList.setVisibleRowCount(Math.min(sidebarTabListModel.size(), 7));
        ToolWindowSurfaceStyle.applyDialogList(sidebarTabList);
        sidebarTabList.setSelectionBackground(ModernColors.getTabSelectedBackgroundColor());
        sidebarTabList.setSelectionForeground(getTextPrimaryColor());
        sidebarTabList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = sidebarTabList.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                Rectangle bounds = sidebarTabList.getCellBounds(index, index);
                if (bounds != null && e.getX() - bounds.x <= 28) {
                    toggleSidebarTabVisibility(index);
                }
            }
        });
        sidebarTabList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleSidebarTabVisibility");
        sidebarTabList.getActionMap().put("toggleSidebarTabVisibility", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int selectedIndex = sidebarTabList.getSelectedIndex();
                if (selectedIndex >= 0) {
                    toggleSidebarTabVisibility(selectedIndex);
                }
            }
        });
        if (!sidebarTabListModel.isEmpty()) {
            sidebarTabList.setSelectedIndex(0);
        }

        JScrollPane scrollPane = new JScrollPane(sidebarTabList);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        ToolWindowSurfaceStyle.applyDialogListScrollPane(scrollPane, sidebarTabList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(320, 160));
        scrollPane.setMaximumSize(new Dimension(320, 160));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);

        JButton resetButton = createModernButton(
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_TABS_RESET),
                false
        );
        resetButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetButton.setPreferredSize(new Dimension(116, 32));
        resetButton.setMaximumSize(new Dimension(116, 32));
        resetButton.addActionListener(e -> resetSidebarTabsToDefault());

        SettingsHintLabel hintLabel = new SettingsHintLabel(
                I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_TABS_HINT),
                320
        );
        hintLabel.setBorder(new EmptyBorder(8, 0, 0, 0));

        sidebarTabsStateField = new JTextField(buildSidebarTabsStateSnapshot());

        editor.add(scrollPane);
        editor.add(Box.createVerticalStrut(8));
        editor.add(resetButton);
        editor.add(hintLabel);
        return editor;
    }

    private void resetSidebarTabsToDefault() {
        sidebarTabListModel.clear();
        for (SidebarTab tab : SidebarTab.values()) {
            sidebarTabListModel.addElement(new SidebarTabSettingItem(tab, true));
        }
        if (!sidebarTabListModel.isEmpty()) {
            sidebarTabList.setSelectedIndex(0);
        }
        sidebarTabList.repaint();
        syncSidebarTabsState();
    }

    private void toggleSidebarTabVisibility(int index) {
        SidebarTabSettingItem item = sidebarTabListModel.get(index);
        if (item.visible && countVisibleSidebarTabs() == 1) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_TABS_AT_LEAST_ONE));
            return;
        }
        item.visible = !item.visible;
        sidebarTabList.repaint();
        syncSidebarTabsState();
    }

    private int countVisibleSidebarTabs() {
        int visibleCount = 0;
        for (int i = 0; i < sidebarTabListModel.size(); i++) {
            if (sidebarTabListModel.get(i).visible) {
                visibleCount++;
            }
        }
        return visibleCount;
    }

    private void syncSidebarTabsState() {
        if (sidebarTabsStateField != null) {
            sidebarTabsStateField.setText(buildSidebarTabsStateSnapshot());
        }
    }

    private String buildSidebarTabsStateSnapshot() {
        List<String> state = new ArrayList<>();
        for (int i = 0; i < sidebarTabListModel.size(); i++) {
            SidebarTabSettingItem item = sidebarTabListModel.get(i);
            state.add(item.tab.name() + ":" + (item.visible ? "1" : "0"));
        }
        return String.join(",", state);
    }

    private List<String> getSidebarTabOrderForSave() {
        List<String> order = new ArrayList<>();
        for (int i = 0; i < sidebarTabListModel.size(); i++) {
            order.add(sidebarTabListModel.get(i).tab.name());
        }
        return order;
    }

    private Set<String> getHiddenSidebarTabsForSave() {
        Set<String> hiddenTabs = new LinkedHashSet<>();
        for (int i = 0; i < sidebarTabListModel.size(); i++) {
            SidebarTabSettingItem item = sidebarTabListModel.get(i);
            if (!item.visible) {
                hiddenTabs.add(item.tab.name());
            }
        }
        return hiddenTabs;
    }

    private String getPersistedSidebarTabsStateSnapshot() {
        List<String> persistedState = new ArrayList<>();
        Set<String> hiddenTabs = SettingManager.getHiddenSidebarTabs();
        for (SidebarTab tab : SidebarTabSettingsResolver.getOrderedSidebarTabs()) {
            persistedState.add(tab.name() + ":" + (hiddenTabs.contains(tab.name()) ? "0" : "1"));
        }
        return String.join(",", persistedState);
    }

    /**
     * 更新字体预览
     */
    private void updateFontPreview() {
        try {
            String selectedFont = (String) fontNameComboBox.getSelectedItem();
            if (selectedFont == null) return;

            int fontSize = SettingManager.getUiFontSize(); // 使用当前设置的字体大小（首次使用会根据操作系统返回默认值）
            String sizeText = fontSizeField.getText().trim();
            if (!sizeText.isEmpty()) {
                try {
                    fontSize = Integer.parseInt(sizeText);
                    fontSize = Math.max(10, Math.min(24, fontSize));
                } catch (NumberFormatException e) {
                    // 使用默认大小
                }
            }

            // 字体预览处理：
            // 1. 如果选择系统默认，使用 deriveFont 保留降级链（确保 emoji 显示）
            // 2. 如果选择自定义字体，使用 new Font 让用户看到实际字体效果
            //    （注意：自定义字体可能不支持 emoji，这是预期行为，用户需要知道选择的字体效果）

            if (fontNameComboBox.getSelectedIndex() == 0) {
                // 选择了"系统默认"，使用 deriveFont 保留降级链
                Font baseFont = UIManager.getFont("Label.font");
                if (baseFont == null) {
                    baseFont = fontPreviewLabel.getFont();
                }
                fontPreviewLabel.setFont(baseFont.deriveFont(Font.PLAIN, fontSize));
                fontSupportHintLabel.setPlainText(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_STATUS_SYSTEM_DEFAULT));
                fontSupportHintLabel.setForeground(getTextSecondaryColor());
            } else {
                // 选择了具体字体，创建该字体实例以展示真实效果
                // 这样用户可以看到选择的字体是否支持 emoji
                fontPreviewLabel.setFont(new Font(selectedFont, Font.PLAIN, fontSize));
                UiFontCatalog.FontSupport support = getFontSupport(selectedFont);
                if (support == UiFontCatalog.FontSupport.FULL) {
                    fontSupportHintLabel.setPlainText(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_STATUS_FULL));
                    fontSupportHintLabel.setForeground(getTextSecondaryColor());
                } else if (support == UiFontCatalog.FontSupport.NO_EMOJI) {
                    fontSupportHintLabel.setPlainText(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_STATUS_NO_EMOJI));
                    fontSupportHintLabel.setForeground(ModernColors.getWarning());
                } else {
                    fontSupportHintLabel.setPlainText(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_STATUS_NO_CJK));
                    fontSupportHintLabel.setForeground(ModernColors.getError());
                }
            }
        } catch (Exception e) {
            // 忽略预览更新错误
        }
    }

    private void updateEditorFontPreview() {
        try {
            int fontSize = SettingManager.getEditorFontSize();
            String sizeText = editorFontSizeField.getText().trim();
            if (!sizeText.isEmpty()) {
                try {
                    fontSize = Integer.parseInt(sizeText);
                    fontSize = Math.max(8, Math.min(32, fontSize));
                } catch (NumberFormatException e) {
                    // 使用默认大小
                }
            }

            String selectedFont = getSelectedCustomFont(editorFontNameComboBox);
            String family = selectedFont.isBlank() ? EditorFontManager.getDefaultEditorFontFamily() : selectedFont;
            Font primaryFont = new Font(family, Font.PLAIN, fontSize);
            Font fallbackFont = resolveSelectedEditorPreviewFallbackFont(fontSize);
            editorFontPreviewLabel.setFont(primaryFont);
            if (editorFontPreviewLabel instanceof EditorFontPreviewLabel editorPreviewLabel) {
                editorPreviewLabel.setPreviewFonts(primaryFont, fallbackFont);
            }

            String fallbackFontName = getSelectedCustomFont(editorFontFallbackNameComboBox);
            if (fallbackFontName.isBlank()) {
                editorFontPreviewLabel.setToolTipText(
                        I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_PREVIEW_TOOLTIP_NO_FALLBACK)
                );
            } else {
                editorFontPreviewLabel.setToolTipText(
                        I18nUtil.getMessage(MessageKeys.SETTINGS_EDITOR_FONT_PREVIEW_TOOLTIP_WITH_FALLBACK, fallbackFontName)
                );
            }
        } catch (Exception e) {
            // 忽略预览更新错误
        }
    }

    private Font resolveSelectedEditorPreviewFallbackFont(int fontSize) {
        String fallbackFamily = getSelectedCustomFont(editorFontFallbackNameComboBox);
        if (fallbackFamily.isBlank()) {
            fallbackFamily = EditorFontManager.getDefaultEditorFallbackFontFamily();
        }
        return new Font(fallbackFamily, Font.PLAIN, fontSize);
    }

    private UiFontCatalog.FontSupport getFontSupport(String fontName) {
        return availableFontOptions.stream()
                .filter(option -> option.family().equals(fontName))
                .findFirst()
                .map(UiFontCatalog.FontOption::support)
                .orElseGet(() -> UiFontCatalog.inspectFamily(fontName));
    }

    private JComboBox<String> createFontComboBox() {
        JComboBox<String> comboBox = new JComboBox<>();
        comboBox.setMaximumRowCount(20);
        comboBox.setRenderer(new FontFamilyListCellRenderer());
        return comboBox;
    }

    private void installFontComboLazyLoader(JComboBox<String> comboBox) {
        comboBox.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                loadFontOptionsIfNeeded();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }

    private void loadFontOptionsIfNeeded() {
        if (fontOptionsLoaded || fontOptionsLoading) {
            return;
        }

        List<UiFontCatalog.FontOption> cachedOptions = UiFontCatalog.getCachedAvailableFontOptions();
        if (cachedOptions != null) {
            applyLoadedFontOptions(cachedOptions);
            return;
        }

        fontOptionsLoading = true;
        UiFontCatalog.getAvailableFontOptionsAsync().whenComplete((fontOptions, throwable) ->
                SwingUtilities.invokeLater(() -> {
                    fontOptionsLoading = false;
                    if (throwable != null) {
                        log.warn("Failed to load UI font options", throwable);
                        return;
                    }
                    boolean uiPopupVisible = fontNameComboBox.isPopupVisible();
                    boolean editorPopupVisible = editorFontNameComboBox.isPopupVisible();
                    boolean fallbackPopupVisible = editorFontFallbackNameComboBox.isPopupVisible();
                    applyLoadedFontOptions(fontOptions);
                    refreshPopupIfVisible(fontNameComboBox, uiPopupVisible);
                    refreshPopupIfVisible(editorFontNameComboBox, editorPopupVisible);
                    refreshPopupIfVisible(editorFontFallbackNameComboBox, fallbackPopupVisible);
                }));
    }

    private void preloadFontOptionsInBackground() {
        if (UiFontCatalog.getCachedAvailableFontOptions() != null || fontOptionsLoading) {
            return;
        }

        fontOptionsLoading = true;
        UiFontCatalog.getAvailableFontOptionsAsync().whenComplete((fontOptions, throwable) ->
                SwingUtilities.invokeLater(() -> {
                    fontOptionsLoading = false;
                    if (throwable != null) {
                        log.warn("Failed to preload UI font options", throwable);
                    }
                }));
    }

    private void applyLoadedFontOptions(List<UiFontCatalog.FontOption> fontOptions) {
        String selectedUiFont = getSelectedCustomFont(fontNameComboBox);
        String selectedEditorFont = getSelectedCustomFont(editorFontNameComboBox);
        String selectedEditorFallbackFont = getSelectedCustomFont(editorFontFallbackNameComboBox);
        availableFontOptions = fontOptions;
        resetFontComboItems(
                fontNameComboBox,
                selectedUiFont,
                I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_SYSTEM_DEFAULT),
                FontComboRole.UI
        );
        resetFontComboItems(
                editorFontNameComboBox,
                selectedEditorFont,
                createEditorPrimaryAutoFontLabel(),
                FontComboRole.EDITOR_PRIMARY
        );
        resetFontComboItems(
                editorFontFallbackNameComboBox,
                selectedEditorFallbackFont,
                createEditorFallbackAutoFontLabel(),
                FontComboRole.EDITOR_FALLBACK
        );
        selectFontComboValue(fontNameComboBox, selectedUiFont);
        selectFontComboValue(editorFontNameComboBox, selectedEditorFont);
        selectFontComboValue(editorFontFallbackNameComboBox, selectedEditorFallbackFont);
        fontOptionsLoaded = true;
    }

    private String createEditorPrimaryAutoFontLabel() {
        return I18nUtil.getMessage(
                MessageKeys.SETTINGS_EDITOR_FONT_DEFAULT_RESOLVED,
                EditorFontManager.getDefaultEditorFontFamily()
        );
    }

    private String createEditorFallbackAutoFontLabel() {
        return I18nUtil.getMessage(
                MessageKeys.SETTINGS_EDITOR_FONT_FALLBACK_AUTO_RESOLVED,
                EditorFontManager.getDefaultEditorFallbackFontFamily()
        );
    }

    private void refreshPopupIfVisible(JComboBox<String> comboBox, boolean wasVisible) {
        if (!wasVisible) {
            return;
        }
        comboBox.hidePopup();
        comboBox.showPopup();
    }

    private void resetFontComboItems(JComboBox<String> comboBox, String currentFont, String defaultLabel) {
        resetFontComboItems(comboBox, currentFont, defaultLabel, FontComboRole.ALL);
    }

    private void resetFontComboItems(JComboBox<String> comboBox,
                                     String currentFont,
                                     String defaultLabel,
                                     FontComboRole role) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(defaultLabel);

        List<String> families = switch (role) {
            case UI -> UiFontCatalog.mergeUiFamiliesForCombo(currentFont, availableFontOptions, I18nUtil.currentLocale());
            case EDITOR_PRIMARY -> UiFontCatalog.mergeEditorPrimaryFamiliesForCombo(currentFont, availableFontOptions);
            case EDITOR_FALLBACK -> UiFontCatalog.mergeEditorFallbackFamiliesForCombo(currentFont, availableFontOptions);
            case ALL -> UiFontCatalog.mergeFamiliesForCombo(
                    currentFont,
                    availableFontOptions.stream().map(UiFontCatalog.FontOption::family).toList()
            );
        };
        for (String family : families) {
            model.addElement(family);
        }
        comboBox.setModel(model);
    }

    private String getSelectedCustomFont() {
        return getSelectedCustomFont(fontNameComboBox);
    }

    private String getSelectedCustomFont(JComboBox<String> comboBox) {
        if (comboBox.getSelectedIndex() <= 0) {
            return "";
        }
        Object selectedItem = comboBox.getSelectedItem();
        return selectedItem == null ? "" : selectedItem.toString();
    }

    private void selectFontComboValue(JComboBox<String> comboBox, String fontName) {
        if (fontName == null || fontName.isBlank()) {
            comboBox.setSelectedIndex(0);
        } else {
            comboBox.setSelectedItem(fontName);
        }
    }

    private void setupValidators() {
        setupValidator(
                downloadProgressDialogThresholdField,
                s -> isInteger(s) && Integer.parseInt(s) >= 0,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_THRESHOLD_ERROR)
        );
        setupValidator(
                maxHistoryCountField,
                this::isPositiveInteger,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_MAX_HISTORY_ERROR)
        );
        setupValidator(
                maxOpenedRequestsCountField,
                this::isPositiveInteger,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_MAX_OPENED_REQUESTS_ERROR)
        );
        setupValidator(
                gitDiffLargeFileThresholdField,
                this::isValidGitDiffLargeFileThreshold,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_GIT_DIFF_LARGE_FILE_THRESHOLD_ERROR)
        );
        setupValidator(
                fontSizeField,
                this::isValidFontSize,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_FONT_SIZE_ERROR)
        );
        setupValidator(
                editorFontSizeField,
                this::isValidEditorFontSize,
                I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_EDITOR_FONT_SIZE_ERROR)
        );
    }

    private boolean isValidFontSize(String value) {
        try {
            int size = Integer.parseInt(value.trim());
            return size >= 10 && size <= 24;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidEditorFontSize(String value) {
        try {
            int size = Integer.parseInt(value.trim());
            return size >= 8 && size <= 32;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean validateUiFontSelection() {
        String selectedFont = getSelectedCustomFont();
        if (selectedFont.isBlank()) {
            return true;
        }

        UiFontCatalog.FontSupport support = getFontSupport(selectedFont);
        if (UiFontCatalog.isUiFontAllowedForLocale(support, I18nUtil.currentLocale())) {
            return true;
        }

        NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_UNSUPPORTED_CHINESE_ERROR));
        return false;
    }

    private boolean isValidGitDiffLargeFileThreshold(String value) {
        try {
            int thresholdMb = Integer.parseInt(value.trim());
            return thresholdMb >= SettingManager.MIN_GIT_DIFF_LARGE_FILE_THRESHOLD_MB
                    && thresholdMb <= SettingManager.MAX_GIT_DIFF_LARGE_FILE_THRESHOLD_MB;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected void registerListeners() {
        saveBtn.addActionListener(e -> saveSettings(true));
        applyBtn.addActionListener(e -> saveSettings(false));
        cancelBtn.addActionListener(e -> {
            if (confirmDiscardChanges()) {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window instanceof JDialog dialog) {
                    dialog.dispose();
                }
            }
        });
        registerSaveShortcut(() -> saveSettings(false));
    }

    private void saveSettings(boolean closeAfterSave) {
        // 验证所有字段
        if (!validateAllFields()) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_VALIDATION_ERROR_MESSAGE));
            return;
        }
        if (countVisibleSidebarTabs() == 0) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_GENERAL_SIDEBAR_TABS_AT_LEAST_ONE));
            return;
        }
        if (!validateUiFontSelection()) {
            return;
        }

        try {
            String oldSidebarTabsState = getPersistedSidebarTabsStateSnapshot();

            // 保存下载设置
            SettingManager.setShowDownloadProgressDialog(showDownloadProgressCheckBox.isSelected());
            if (downloadProgressDialogThresholdField.isEnabled()) {
                int thresholdMB = Integer.parseInt(downloadProgressDialogThresholdField.getText().trim());
                SettingManager.setDownloadProgressDialogThreshold(thresholdMB * 1024 * 1024);
            }

            // 保存通用设置
            SettingManager.setMaxHistoryCount(Integer.parseInt(maxHistoryCountField.getText().trim()));
            SettingManager.setMaxOpenedRequestsCount(Integer.parseInt(maxOpenedRequestsCountField.getText().trim()));
            SettingManager.setRequestEditorTabsMultiLineEnabled(requestEditorTabsMultiLineCheckBox.isSelected());
            updateRequestEditorTabsLayoutPolicy();
            SettingManager.setGitDiffLargeFileThresholdMb(Integer.parseInt(gitDiffLargeFileThresholdField.getText().trim()));
            SettingManager.setAutoFormatResponse(autoFormatResponseCheckBox.isSelected());
            SettingManager.setStartupSplashEnabled(startupSplashCheckBox.isSelected());

            // 保存侧边栏展开设置并更新UI
            boolean oldSidebarExpanded = SettingManager.isSidebarExpanded();
            boolean newSidebarExpanded = sidebarExpandedCheckBox.isSelected();
            SettingManager.setSidebarExpanded(newSidebarExpanded);
            SettingManager.setSidebarTabOrder(getSidebarTabOrderForSave());
            SettingManager.setHiddenSidebarTabs(getHiddenSidebarTabsForSave());
            String newSidebarTabsState = buildSidebarTabsStateSnapshot();
            if (oldSidebarExpanded != newSidebarExpanded || !oldSidebarTabsState.equals(newSidebarTabsState)) {
                updateSidebarConfiguration();
            }

            // 保存通知位置设置并更新NotificationCenter - 使用枚举的 fromIndex 方法
            NotificationPosition selectedPosition = NotificationPosition.fromIndex(notificationPositionComboBox.getSelectedIndex());
            SettingManager.setNotificationPosition(selectedPosition);
            NotificationCenter.setDefaultPosition(selectedPosition);

            // 检测字体是否有变化（在保存前获取旧值）
            String oldFontName = SettingManager.getUiFontName();
            int oldFontSize = SettingManager.getUiFontSize();
            String oldEditorFontName = SettingManager.getEditorFontName();
            String oldEditorFontFallbackName = SettingManager.getEditorFontFallbackName();
            int oldEditorFontSize = SettingManager.getEditorFontSize();

            // 保存字体设置
            int fontNameIndex = fontNameComboBox.getSelectedIndex();
            String newFontName;
            if (fontNameIndex == 0) {
                // 系统默认
                newFontName = "";
                SettingManager.setUiFontName("");
            } else {
                newFontName = (String) fontNameComboBox.getSelectedItem();
                if (!getFontSupport(newFontName).isUiSafe()) {
                    NotificationCenter.showWarning(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_UNSUPPORTED_WARNING));
                }
                SettingManager.setUiFontName(newFontName);
            }
            int newFontSize = Integer.parseInt(fontSizeField.getText().trim());
            SettingManager.setUiFontSize(newFontSize);

            String newEditorFontName = getSelectedCustomFont(editorFontNameComboBox);
            String newEditorFontFallbackName = getSelectedCustomFont(editorFontFallbackNameComboBox);
            int newEditorFontSize = Integer.parseInt(editorFontSizeField.getText().trim());
            SettingManager.setEditorFontName(newEditorFontName);
            SettingManager.setEditorFontFallbackName(newEditorFontFallbackName);
            SettingManager.setEditorFontSize(newEditorFontSize);

            // 判断字体是否真的有变化（处理 null 情况）
            boolean fontChanged = !Objects.equals(newFontName, oldFontName) || newFontSize != oldFontSize;
            boolean editorFontChanged = !Objects.equals(newEditorFontName, oldEditorFontName)
                    || !Objects.equals(newEditorFontFallbackName, oldEditorFontFallbackName)
                    || newEditorFontSize != oldEditorFontSize;

            // 如果字体有变化，立即应用字体设置到整个应用
            if (fontChanged) {
                FontManager.applyFont(newFontName, newFontSize);
            } else if (editorFontChanged) {
                UIRefreshManager.refreshEditorFonts();
            }

            // 重新跟踪当前值
            originalValues.clear();
            trackComponentValue(showDownloadProgressCheckBox);
            trackComponentValue(downloadProgressDialogThresholdField);
            trackComponentValue(gitDiffLargeFileThresholdField);
            trackComponentValue(maxHistoryCountField);
            trackComponentValue(maxOpenedRequestsCountField);
            trackComponentValue(requestEditorTabsMultiLineCheckBox);
            trackComponentValue(autoFormatResponseCheckBox);
            trackComponentValue(startupSplashCheckBox);
            trackComponentValue(sidebarExpandedCheckBox);
            trackComponentValue(notificationPositionComboBox);
            trackComponentValue(fontNameComboBox);
            trackComponentValue(fontSizeField);
            trackComponentValue(editorFontNameComboBox);
            trackComponentValue(editorFontFallbackNameComboBox);
            trackComponentValue(editorFontSizeField);
            trackComponentValue(sidebarTabsStateField);
            setHasUnsavedChanges(false);

            // 根据是否修改了字体显示不同的提示信息
            if (fontChanged || editorFontChanged) {
                NotificationCenter.showInfo(I18nUtil.getMessage(MessageKeys.SETTINGS_UI_FONT_APPLIED));
            } else {
                NotificationCenter.showSuccess(I18nUtil.getMessage(MessageKeys.SETTINGS_SAVE_SUCCESS_MESSAGE));
            }

            // 根据参数决定是否关闭对话框
            if (closeAfterSave) {
                Window window = SwingUtilities.getWindowAncestor(this);
                if (window instanceof JDialog dialog) {
                    dialog.dispose();
                }
            }
        } catch (Exception ex) {
            NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.SETTINGS_SAVE_ERROR_MESSAGE) + ": " + ex.getMessage());
        }
    }

    /**
     * 更新侧边栏配置
     */
    private void updateSidebarConfiguration() {
        try {
            SidebarTabPanel sidebarPanel = UiSingletonFactory.getInstance(SidebarTabPanel.class);
            sidebarPanel.refreshSidebarConfiguration();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
        }
    }

    private void updateRequestEditorTabsLayoutPolicy() {
        try {
            UiSingletonFactory.getInstance(RequestEditorPanel.class).updateRequestEditorTabsLayoutPolicy();
        } catch (Exception ex) {
            log.debug("Failed to refresh request editor tabs layout policy", ex);
        }
    }

    private static final class EditorFontPreviewLabel extends JLabel {
        private Font primaryFont;
        private Font fallbackFont;

        private EditorFontPreviewLabel(String text) {
            super(text);
        }

        private void setPreviewFonts(Font primaryFont, Font fallbackFont) {
            this.primaryFont = primaryFont;
            this.fallbackFont = fallbackFont;
            setFont(primaryFont);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (isOpaque()) {
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                applyEditorTextHints(g2);
                paintPreviewText(g2);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            Insets insets = getInsets();
            Font primary = resolvePrimaryFont();
            Font fallback = resolveFallbackFont(primary);
            int width = calculatePreviewTextWidth(primary, fallback);
            FontMetrics primaryMetrics = getFontMetrics(primary);
            FontMetrics fallbackMetrics = getFontMetrics(fallback);
            int height = Math.max(primaryMetrics.getHeight(), fallbackMetrics.getHeight());
            return new Dimension(
                    width + insets.left + insets.right,
                    height + insets.top + insets.bottom
            );
        }

        private void paintPreviewText(Graphics2D g2) {
            String text = Objects.toString(getText(), "");
            if (text.isEmpty()) {
                return;
            }

            Font primary = resolvePrimaryFont();
            Font fallback = resolveFallbackFont(primary);
            FontMetrics primaryMetrics = g2.getFontMetrics(primary);
            FontMetrics fallbackMetrics = g2.getFontMetrics(fallback);
            int maxAscent = Math.max(primaryMetrics.getAscent(), fallbackMetrics.getAscent());
            int maxDescent = Math.max(primaryMetrics.getDescent(), fallbackMetrics.getDescent());
            int textHeight = maxAscent + maxDescent;

            Insets insets = getInsets();
            int availableHeight = getHeight() - insets.top - insets.bottom;
            int baseline = insets.top + Math.max(maxAscent, (availableHeight - textHeight) / 2 + maxAscent);
            int x = insets.left;

            g2.setColor(getForeground());
            FontRenderContext frc = g2.getFontRenderContext();
            float runX = x;
            for (FontRun run : splitFontRuns(text, primary, fallback)) {
                g2.setFont(run.font());
                g2.drawString(run.text(), runX, baseline);
                runX += (float) run.font().getStringBounds(run.text(), frc).getWidth();
            }
        }

        private int calculatePreviewTextWidth(Font primary, Font fallback) {
            String text = Objects.toString(getText(), "");
            FontRenderContext frc = new FontRenderContext(null, true, false);
            double width = 0;
            for (FontRun run : splitFontRuns(text, primary, fallback)) {
                width += run.font().getStringBounds(run.text(), frc).getWidth();
            }
            return (int) Math.ceil(width);
        }

        /**
         * 按字体切分连续文本段整体绘制，与代码编辑器一致；逐字绘制会累积取整误差，导致字距偏大。
         */
        private List<FontRun> splitFontRuns(String text, Font primary, Font fallback) {
            List<FontRun> runs = new ArrayList<>();
            int runStart = 0;
            Font runFont = null;
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                Font glyphFont = choosePreviewFont(codePoint, primary, fallback);
                if (runFont != null && glyphFont != runFont) {
                    runs.add(new FontRun(text.substring(runStart, offset), runFont));
                    runStart = offset;
                }
                runFont = glyphFont;
                offset += Character.charCount(codePoint);
            }
            if (runFont != null) {
                runs.add(new FontRun(text.substring(runStart), runFont));
            }
            return runs;
        }

        private static void applyEditorTextHints(Graphics2D g2) {
            FontsUtil.applyTextRenderingHints(g2);
            // RSyntaxTextArea 默认关闭小数字宽，保持预览与真实编辑器字距一致
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        }

        private record FontRun(String text, Font font) {
        }

        private Font choosePreviewFont(int codePoint, Font primary, Font fallback) {
            if (primary.canDisplay(codePoint)) {
                return primary;
            }
            if (fallback.canDisplay(codePoint)) {
                return fallback;
            }
            return primary;
        }

        private Font resolvePrimaryFont() {
            if (primaryFont != null) {
                return primaryFont;
            }
            Font font = getFont();
            return font == null ? FontsUtil.getDefaultFont(Font.PLAIN) : font;
        }

        private Font resolveFallbackFont(Font primary) {
            return fallbackFont == null ? primary : fallbackFont;
        }
    }

    private static final class SidebarTabSettingItem {
        private final SidebarTab tab;
        private boolean visible;

        private SidebarTabSettingItem(SidebarTab tab, boolean visible) {
            this.tab = tab;
            this.visible = visible;
        }
    }

    private final class SidebarTabListCellRenderer extends JPanel implements ListCellRenderer<SidebarTabSettingItem> {
        private final JCheckBox visibleCheckBox = new JCheckBox();
        private final JLabel titleLabel = new JLabel();
        private final JLabel dragHintLabel = new JLabel("::");

        private SidebarTabListCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(6, 8, 6, 8));
            setOpaque(true);

            visibleCheckBox.setOpaque(false);
            visibleCheckBox.setFocusable(false);
            visibleCheckBox.setEnabled(true);

            titleLabel.setOpaque(false);
            dragHintLabel.setOpaque(false);

            add(visibleCheckBox, BorderLayout.WEST);
            add(titleLabel, BorderLayout.CENTER);
            add(dragHintLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends SidebarTabSettingItem> list,
                                                      SidebarTabSettingItem value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            visibleCheckBox.setSelected(value.visible);
            titleLabel.setText(value.tab.getDisplayTitle());
            titleLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
            titleLabel.setForeground(getTextPrimaryColor());

            dragHintLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
            dragHintLabel.setForeground(getTextSecondaryColor());

            Color background = isSelected ? list.getSelectionBackground() : list.getBackground();
            setBackground(background);
            return this;
        }
    }

    private final class SidebarTabListTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            return new StringSelection(String.valueOf(sidebarTabList.getSelectedIndex()));
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            try {
                int sourceIndex = Integer.parseInt((String) support.getTransferable()
                        .getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor));
                JList.DropLocation dropLocation = (JList.DropLocation) support.getDropLocation();
                int targetIndex = dropLocation.getIndex();

                if (sourceIndex < 0 || sourceIndex >= sidebarTabListModel.size()) {
                    return false;
                }
                if (targetIndex < 0 || targetIndex > sidebarTabListModel.size()) {
                    return false;
                }

                SidebarTabSettingItem movedItem = sidebarTabListModel.get(sourceIndex);
                sidebarTabListModel.remove(sourceIndex);
                if (sourceIndex < targetIndex) {
                    targetIndex--;
                }
                sidebarTabListModel.add(targetIndex, movedItem);
                sidebarTabList.setSelectedIndex(targetIndex);
                syncSidebarTabsState();
                return true;
            } catch (Exception ex) {
                log.debug("Failed to reorder sidebar tabs", ex);
                return false;
            }
        }
    }

    private final class FontFamilyListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list,
                                                      Object value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (index <= 0 || value == null) {
                label.setFont(FontsUtil.getDefaultFont(Font.PLAIN));
                return label;
            }

            String family = value.toString();
            Font listFont = list.getFont();
            int size = listFont == null ? FontsUtil.getDefaultFont(Font.PLAIN).getSize() : listFont.getSize();
            label.setFont(new Font(family, Font.PLAIN, size));
            return label;
        }
    }
}

package com.laker.postman.panel.collections.editor.request.sub;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.CommonI18n;
import com.laker.postman.util.CommonMessageKeys;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.IconUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * 文件类型响应的概览视图：展示文件名/类型/大小，提供下载、按文本查看，以及下载后打开文件或所在文件夹。
 */
final class FileResponsePanel extends JPanel {
    private static final int FILE_ICON_SIZE = 44;
    private static final int NOTICE_ICON_SIZE = 16;
    private static final int MAX_FILE_NAME_LENGTH = 72;
    private static final int MAX_CONTENT_TYPE_LENGTH = 60;
    private static final int MAX_SAVED_PATH_LENGTH = 80;

    private final JLabel fileIconLabel = new JLabel();
    private final JLabel fileNameLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel fileSummaryLabel = new JLabel("", SwingConstants.CENTER);
    private final JPanel mismatchPanel = new JPanel(new GridBagLayout());
    private final JLabel mismatchIconLabel = new JLabel();
    private final JLabel mismatchLabel = new JLabel();
    private final JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final JButton downloadButton = ModernButtonFactory.createCompactButton(
            CommonI18n.get(CommonMessageKeys.BUTTON_DOWNLOAD), true, "icons/download.svg");
    private final JButton viewAsTextButton = ModernButtonFactory.createCompactButton(
            I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_VIEW_AS_TEXT), false, "icons/file-code.svg");
    // 保存位置与“打开”入口分两行：路径可在窄分栏中省略显示，按钮不会因 FlowLayout 换行而被裁掉
    private final JPanel savedPanel = new JPanel(new GridBagLayout());
    private final JPanel savedLinksPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    private final JLabel savedLabel = new JLabel();
    private final JButton openFileButton = createLinkButton(I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_OPEN));
    private final JButton openFolderButton = createLinkButton(CommonI18n.get(CommonMessageKeys.GENERAL_OPEN_FOLDER));
    private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel contentOmittedLabel = new JLabel("", SwingConstants.CENTER);

    private File savedFile;
    private boolean downloading;

    FileResponsePanel(Runnable downloadAction, Runnable viewAsTextAction) {
        setLayout(new GridBagLayout());
        ToolWindowSurfaceStyle.applyCard(this);
        setBorder(BorderFactory.createEmptyBorder(24, 20, 24, 20));

        fileNameLabel.putClientProperty("html.disable", Boolean.TRUE);
        savedLabel.putClientProperty("html.disable", Boolean.TRUE);
        statusLabel.putClientProperty("html.disable", Boolean.TRUE);
        contentOmittedLabel.setText(I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_CONTENT_NOT_SAVED));
        contentOmittedLabel.setToolTipText(contentOmittedLabel.getText());

        GridBagConstraints noticeConstraints = new GridBagConstraints();
        noticeConstraints.gridx = 0;
        noticeConstraints.insets = new Insets(0, 0, 0, 8);
        noticeConstraints.anchor = GridBagConstraints.CENTER;
        mismatchPanel.add(mismatchIconLabel, noticeConstraints);

        noticeConstraints.gridx = 1;
        noticeConstraints.weightx = 1;
        noticeConstraints.fill = GridBagConstraints.HORIZONTAL;
        noticeConstraints.insets = new Insets(0, 0, 0, 0);
        mismatchPanel.add(mismatchLabel, noticeConstraints);

        actionPanel.setOpaque(false);
        actionPanel.add(downloadButton);
        actionPanel.add(viewAsTextButton);

        savedPanel.setOpaque(false);
        savedLinksPanel.setOpaque(false);
        savedLinksPanel.add(openFileButton);
        savedLinksPanel.add(openFolderButton);
        GridBagConstraints savedConstraints = new GridBagConstraints();
        savedConstraints.gridx = 0;
        savedConstraints.weightx = 1;
        savedConstraints.fill = GridBagConstraints.HORIZONTAL;
        savedPanel.add(savedLabel, savedConstraints);
        savedConstraints.gridy = 1;
        savedConstraints.fill = GridBagConstraints.NONE;
        savedPanel.add(savedLinksPanel, savedConstraints);
        savedLabel.setHorizontalAlignment(SwingConstants.CENTER);

        layoutContent();

        downloadButton.addActionListener(event -> {
            if (downloadAction != null) {
                downloadAction.run();
            }
        });
        viewAsTextButton.addActionListener(event -> {
            if (viewAsTextAction != null) {
                viewAsTextAction.run();
            }
        });
        openFileButton.addActionListener(event -> openSavedFile());
        openFolderButton.addActionListener(event -> openSavedFolder());

        clear();
        applyTheme();
    }

    private void layoutContent() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;

        constraints.gridy = 0;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.SOUTH;
        constraints.insets = new Insets(0, 0, 12, 0);
        add(fileIconLabel, constraints);

        constraints.gridy = 1;
        constraints.weighty = 0;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 4, 0);
        add(fileNameLabel, constraints);

        constraints.gridy = 2;
        constraints.insets = new Insets(0, 0, 14, 0);
        add(fileSummaryLabel, constraints);

        // 提示条按内容宽度居中，避免在宽窗口中拉成整行横幅
        constraints.gridy = 3;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(0, 0, 16, 0);
        add(mismatchPanel, constraints);

        constraints.gridy = 4;
        constraints.insets = new Insets(0, 0, 12, 0);
        add(actionPanel, constraints);
        GridBagConstraints omittedConstraints = (GridBagConstraints) constraints.clone();
        omittedConstraints.fill = GridBagConstraints.HORIZONTAL;
        add(contentOmittedLabel, omittedConstraints);

        constraints.gridy = 5;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 4, 0);
        add(savedPanel, constraints);

        constraints.gridy = 6;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.NORTH;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 0, 0);
        add(statusLabel, constraints);
    }

    void setResponse(FileResponseModel model) {
        String fileName = model.fileName();
        String fileType = model.fileType();
        long bodySize = model.bodySize();
        String declaredContentType = model.declaredContentType();
        String detectedFileType = model.detectedFileType();
        boolean canViewAsText = model.canViewAsText();
        boolean contentAvailable = model.contentAvailable();
        String resolvedName = fileName == null || fileName.isBlank() ? "-" : fileName;
        String resolvedType = fileType == null || fileType.isBlank() ? "-" : fileType;

        fileNameLabel.setText(abbreviate(resolvedName, MAX_FILE_NAME_LENGTH));
        fileNameLabel.setToolTipText(resolvedName);
        fileSummaryLabel.setText(I18nUtil.getMessage(
                MessageKeys.RESPONSE_FILE_SUMMARY,
                resolvedType,
                ResponseSizeCalculator.formatBytes(Math.max(0, bodySize))
        ));

        boolean contentTypeMismatch = detectedFileType != null && !detectedFileType.isBlank();
        mismatchPanel.setVisible(contentTypeMismatch);
        if (contentTypeMismatch) {
            boolean declared = declaredContentType != null && !declaredContentType.isBlank();
            String declaredType = declared
                    ? declaredContentType.trim()
                    : I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_CONTENT_TYPE_MISSING);
            mismatchLabel.setText(declared
                    ? I18nUtil.getMessage(
                    MessageKeys.RESPONSE_FILE_MIME_MISMATCH,
                    abbreviate(stripParameters(declaredType), MAX_CONTENT_TYPE_LENGTH),
                    detectedFileType)
                    : I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_MIME_MISSING, detectedFileType));
            mismatchLabel.setToolTipText(I18nUtil.getMessage(
                    MessageKeys.RESPONSE_FILE_MIME_MISMATCH_TOOLTIP,
                    detectedFileType,
                    abbreviate(declaredType, 160)
            ));
        } else {
            mismatchLabel.setText("");
            mismatchLabel.setToolTipText(null);
        }

        actionPanel.setVisible(contentAvailable);
        viewAsTextButton.setVisible(contentAvailable && canViewAsText);
        contentOmittedLabel.setVisible(!contentAvailable);
        hideSavedFile();
        revalidate();
        repaint();
    }

    void clear() {
        fileNameLabel.setText("");
        fileNameLabel.setToolTipText(null);
        fileSummaryLabel.setText("");
        mismatchLabel.setText("");
        mismatchLabel.setToolTipText(null);
        mismatchPanel.setVisible(false);
        viewAsTextButton.setVisible(false);
        actionPanel.setVisible(true);
        contentOmittedLabel.setVisible(false);
        hideSavedFile();
    }

    void showSavedFile(File file) {
        savedFile = file;
        String path = file.getAbsolutePath();
        savedLabel.setText(I18nUtil.getMessage(
                MessageKeys.RESPONSE_FILE_SAVED_TO, abbreviate(path, MAX_SAVED_PATH_LENGTH)));
        savedLabel.setToolTipText(path);
        boolean desktopOpen = isDesktopActionSupported(Desktop.Action.OPEN);
        openFileButton.setVisible(desktopOpen);
        openFolderButton.setVisible(desktopOpen || isDesktopActionSupported(Desktop.Action.BROWSE_FILE_DIR));
        savedPanel.setVisible(true);
        setStatus(null);
        revalidate();
        repaint();
    }

    private void hideSavedFile() {
        savedFile = null;
        savedLabel.setText("");
        savedLabel.setToolTipText(null);
        savedPanel.setVisible(false);
        setStatus(null);
    }

    private void openSavedFile() {
        if (savedFile == null) {
            return;
        }
        try {
            Desktop.getDesktop().open(savedFile);
            setStatus(null);
        } catch (Exception e) {
            setStatus(errorMessage(e));
        }
    }

    private void openSavedFolder() {
        if (savedFile == null) {
            return;
        }
        try {
            if (isDesktopActionSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                // 打开所在文件夹并选中文件（macOS / 部分 Linux 桌面支持）
                Desktop.getDesktop().browseFileDirectory(savedFile);
            } else {
                File parent = savedFile.getAbsoluteFile().getParentFile();
                Desktop.getDesktop().open(parent == null ? savedFile : parent);
            }
            setStatus(null);
        } catch (Exception e) {
            setStatus(errorMessage(e));
        }
    }

    private void setStatus(String errorText) {
        statusLabel.setText(errorText == null ? " " : errorText);
        statusLabel.setVisible(errorText != null);
    }

    private static String errorMessage(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_OPEN_FAILED, message);
    }

    private static boolean isDesktopActionSupported(Desktop.Action action) {
        try {
            return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 后台保存期间禁用下载按钮，避免重复保存。
     */
    void setDownloading(boolean downloading) {
        this.downloading = downloading;
        downloadButton.setEnabled(isEnabled() && !downloading);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        downloadButton.setEnabled(enabled && !downloading);
        viewAsTextButton.setEnabled(enabled);
        openFileButton.setEnabled(enabled);
        openFolderButton.setEnabled(enabled);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (fileNameLabel != null) {
            applyTheme();
        }
    }

    private void applyTheme() {
        fileIconLabel.setIcon(IconUtil.createThemed("icons/file.svg", FILE_ICON_SIZE, FILE_ICON_SIZE));
        mismatchIconLabel.setIcon(IconUtil.createColored(
                "icons/warning.svg", NOTICE_ICON_SIZE, NOTICE_ICON_SIZE, ModernColors.getWarning()));
        savedLabel.setIcon(IconUtil.createColored(
                "icons/check.svg", NOTICE_ICON_SIZE, NOTICE_ICON_SIZE, ModernColors.getSuccess()));

        fileNameLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        fileNameLabel.setForeground(ModernColors.getTextPrimary());
        fileSummaryLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        fileSummaryLabel.setForeground(ModernColors.getTextSecondary());
        mismatchLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        mismatchLabel.setForeground(ModernColors.getTextPrimary());
        savedLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        savedLabel.setForeground(ModernColors.getTextSecondary());
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        statusLabel.setForeground(ModernColors.getError());
        contentOmittedLabel.setIcon(IconUtil.createThemed("icons/info.svg", NOTICE_ICON_SIZE, NOTICE_ICON_SIZE));
        contentOmittedLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        contentOmittedLabel.setForeground(ModernColors.getTextSecondary());
        for (JButton linkButton : new JButton[]{openFileButton, openFolderButton}) {
            linkButton.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
            linkButton.setForeground(ModernColors.getPrimary());
        }

        mismatchPanel.setBackground(ModernColors.getWarningBackgroundColor());
        // 背景与边框使用相同圆角，避免不透明背景在圆角外露出方角
        mismatchPanel.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
        mismatchPanel.setBorder(new FlatLineBorder(
                new Insets(8, 12, 8, 12), ModernColors.getWarningBorderColor(), 1f, 8));

        keepLineVisibleWhenShrunk(fileNameLabel);
        keepLineVisibleWhenShrunk(fileSummaryLabel);
        keepLineVisibleWhenShrunk(mismatchLabel);
        keepLineVisibleWhenShrunk(savedLabel);
        keepLineVisibleWhenShrunk(statusLabel);
        keepLineVisibleWhenShrunk(contentOmittedLabel);
    }

    private static JButton createLinkButton(String text) {
        JButton button = new JButton(text);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static void keepLineVisibleWhenShrunk(JLabel label) {
        int lineHeight = label.getFontMetrics(label.getFont()).getHeight();
        label.setMinimumSize(new Dimension(0, lineHeight));
    }

    private static String stripParameters(String contentType) {
        int semicolon = contentType.indexOf(';');
        return semicolon > 0 ? contentType.substring(0, semicolon).trim() : contentType;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        int suffixLength = Math.max(8, maxLength / 3);
        return value.substring(0, maxLength - suffixLength - 1)
                + "…"
                + value.substring(value.length() - suffixLength);
    }
}

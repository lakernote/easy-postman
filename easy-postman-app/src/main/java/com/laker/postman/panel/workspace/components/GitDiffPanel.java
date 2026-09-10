package com.laker.postman.panel.workspace.components;

import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.RefreshButton;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.model.GitFileChange;
import com.laker.postman.model.Workspace;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Embedded Git working tree diff viewer for a workspace.
 */
@Slf4j
public class GitDiffPanel extends JPanel {

    private static final int MAX_DIFF_LINE_CHARS = 8 * 1024;

    private final transient Workspace workspace;
    private final transient WorkspaceService workspaceService;
    private DefaultListModel<GitFileChange> changeListModel;
    private JList<GitFileChange> changeList;
    private JTextPane diffPane;
    private JLabel statusLabel;
    private JButton refreshButton;
    private int changeLoadGeneration;
    private int diffLoadGeneration;
    private SwingWorker<List<GitFileChange>, Void> changesWorker;
    private SwingWorker<DiffLoadResult, Void> diffWorker;

    public GitDiffPanel(Workspace workspace) {
        this.workspace = workspace;
        this.workspaceService = WorkspaceService.getInstance();
        initUI();
        SwingUtilities.invokeLater(this::loadChanges);
    }

    private void initUI() {
        setLayout(new BorderLayout());
        setOpaque(false);
        add(createHeader(), BorderLayout.NORTH);
        add(createContentPanel(), BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        ToolWindowSurfaceStyle.applySectionHeader(header, 10, 18, 10, 18);

        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 2));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel(I18nUtil.getMessage(MessageKeys.GIT_DIFF_TITLE));
        title.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        JLabel workspaceLabel = new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_NAME) + ": " + workspace.getName());
        workspaceLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        workspaceLabel.setForeground(ModernColors.getTextSecondary());
        workspaceLabel.setToolTipText(workspace.getName());
        titlePanel.add(title);
        titlePanel.add(workspaceLabel);
        header.add(titlePanel, BorderLayout.CENTER);

        refreshButton = new RefreshButton();
        refreshButton.addActionListener(e -> loadChanges());
        header.add(refreshButton, BorderLayout.EAST);
        return header;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 18, 12, 18));
        panel.add(createDiffSplitPane(), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        statusLabel.setForeground(ModernColors.getTextSecondary());
        panel.add(statusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JSplitPane createDiffSplitPane() {
        JSplitPane splitPane = AppToolWindowChrome.createHorizontalInnerSplitPane(
                createChangeListScrollPane(),
                createDiffScrollPane(),
                280
        );
        splitPane.setResizeWeight(0.28);
        splitPane.setContinuousLayout(true);
        return splitPane;
    }

    private JScrollPane createChangeListScrollPane() {
        changeListModel = new DefaultListModel<>();
        changeList = new JList<>(changeListModel) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int index = locationToIndex(event.getPoint());
                if (index < 0 || index >= changeListModel.size()) {
                    return null;
                }
                GitFileChange change = changeListModel.getElementAt(index);
                return change == null ? null : change.getPath();
            }
        };
        changeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        changeList.setFixedCellHeight(Math.max(34,
                FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1).getSize() + 18));
        changeList.setCellRenderer(new ChangeListCellRenderer());
        changeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                GitFileChange selected = changeList.getSelectedValue();
                if (selected != null) {
                    loadDiff(selected);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(changeList);
        ToolWindowSurfaceStyle.applyListScrollPaneCard(scrollPane, changeList);
        return scrollPane;
    }

    private JScrollPane createDiffScrollPane() {
        diffPane = new JTextPane();
        diffPane.setEditable(false);
        diffPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN,
                FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1).getSize()));
        diffPane.setBorder(new EmptyBorder(8, 10, 8, 10));
        ToolWindowSurfaceStyle.applyTextComponentCard(diffPane);
        renderPlainMessage(I18nUtil.getMessage(MessageKeys.GIT_DIFF_SELECT_FILE));

        JScrollPane scrollPane = new JScrollPane(diffPane);
        ToolWindowSurfaceStyle.applyScrollPaneCard(scrollPane);
        return scrollPane;
    }

    private void loadChanges() {
        int generation = ++changeLoadGeneration;
        diffLoadGeneration++;
        cancelWorker(changesWorker);
        cancelWorker(diffWorker);
        setBusy(true);
        statusLabel.setText(I18nUtil.getMessage(MessageKeys.GIT_DIFF_LOADING));
        changeListModel.clear();
        renderPlainMessage(I18nUtil.getMessage(MessageKeys.GIT_DIFF_LOADING));

        changesWorker = new SwingWorker<>() {
            @Override
            protected List<GitFileChange> doInBackground() throws Exception {
                return workspaceService.listWorkingTreeChanges(workspace.getId());
            }

            @Override
            protected void done() {
                if (generation != changeLoadGeneration) {
                    return;
                }
                if (isCancelled()) {
                    return;
                }
                try {
                    List<GitFileChange> changes = get();
                    displayChanges(changes);
                } catch (CancellationException ignored) {
                    // A newer refresh superseded this worker.
                } catch (Exception ex) {
                    log.warn("Failed to load Git changes for workspace: {}", workspace.getId(), ex);
                    String message = format(MessageKeys.GIT_DIFF_LOAD_FAILED, ex.getMessage());
                    statusLabel.setText(message);
                    renderPlainMessage(message);
                } finally {
                    setBusy(false);
                }
            }
        };
        changesWorker.execute();
    }

    private void displayChanges(List<GitFileChange> changes) {
        changeListModel.clear();
        if (changes == null || changes.isEmpty()) {
            statusLabel.setText(I18nUtil.getMessage(MessageKeys.GIT_DIFF_NO_CHANGES));
            renderPlainMessage(I18nUtil.getMessage(MessageKeys.GIT_DIFF_NO_CHANGES));
            return;
        }

        for (GitFileChange change : changes) {
            changeListModel.addElement(change);
        }
        statusLabel.setText(format(MessageKeys.GIT_DIFF_STATUS_COUNT, changes.size()));
        // Keep the first-file preview for a useful first-open experience. The Git
        // status used here is reused by the immediate Diff load, so opening a large
        // workspace does not perform the same repository scan twice.
        changeList.setSelectedIndex(0);
    }

    private void loadDiff(GitFileChange change) {
        int generation = ++diffLoadGeneration;
        cancelWorker(diffWorker);
        String selectedPath = change.getPath();
        statusLabel.setText(format(MessageKeys.GIT_DIFF_LOADING_FILE, change.getPath()));
        renderPlainMessage(I18nUtil.getMessage(MessageKeys.GIT_DIFF_LOADING));

        diffWorker = new SwingWorker<>() {
            @Override
            protected DiffLoadResult doInBackground() throws Exception {
                String diff = workspaceService.getWorkingTreeDiff(workspace.getId(), change.getPath());
                if (diff == null || diff.isBlank()) {
                    return DiffLoadResult.message(I18nUtil.getMessage(MessageKeys.GIT_DIFF_NO_TEXT_DIFF));
                }
                return DiffLoadResult.diff(createDiffDocument(diff));
            }

            @Override
            protected void done() {
                if (generation != diffLoadGeneration) {
                    return;
                }
                if (isCancelled()) {
                    return;
                }
                GitFileChange currentSelection = changeList.getSelectedValue();
                if (currentSelection == null || !selectedPath.equals(currentSelection.getPath())) {
                    return;
                }
                try {
                    DiffLoadResult result = get();
                    if (result.document() == null) {
                        renderPlainMessage(result.message());
                    } else {
                        renderDiff(result.document());
                    }
                    statusLabel.setText(change.getPath());
                } catch (CancellationException ignored) {
                    // A newer file selection superseded this worker.
                } catch (Exception ex) {
                    log.warn("Failed to load Git diff for workspace: {}, file: {}",
                            workspace.getId(), change.getPath(), ex);
                    String message = format(MessageKeys.GIT_DIFF_LOAD_FAILED, ex.getMessage());
                    statusLabel.setText(message);
                    renderPlainMessage(message);
                }
            }
        };
        diffWorker.execute();
    }

    private StyledDocument createDiffDocument(String diff) throws BadLocationException {
        StyledDocument document = new DefaultStyledDocument();
        for (String line : diff.split("\n", -1)) {
            String displayLine = line.length() > MAX_DIFF_LINE_CHARS
                    ? line.substring(0, MAX_DIFF_LINE_CHARS) + " …"
                    : line;
            document.insertString(document.getLength(), displayLine + "\n", styleForLine(line));
        }
        return document;
    }

    private static void cancelWorker(SwingWorker<?, ?> worker) {
        if (worker != null && !worker.isDone()) {
            worker.cancel(true);
        }
    }

    private void renderPlainMessage(String message) {
        diffPane.setText(message == null ? "" : message);
        diffPane.setCaretPosition(0);
    }

    private void renderDiff(StyledDocument document) {
        diffPane.setDocument(document);
        diffPane.setCaretPosition(0);
    }

    private SimpleAttributeSet styleForLine(String line) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attributes, Font.MONOSPACED);
        StyleConstants.setForeground(attributes, ModernColors.getTextPrimary());
        if (line.startsWith("diff --git") || line.startsWith("index ")) {
            StyleConstants.setForeground(attributes, ModernColors.getTextSecondary());
            StyleConstants.setBold(attributes, true);
        } else if (line.startsWith("@@")) {
            StyleConstants.setForeground(attributes, ModernColors.getPrimary());
            StyleConstants.setBold(attributes, true);
        } else if (line.startsWith("+") && !line.startsWith("+++")) {
            StyleConstants.setForeground(attributes, ModernColors.getSuccessDark());
        } else if (line.startsWith("-") && !line.startsWith("---")) {
            StyleConstants.setForeground(attributes, ModernColors.getErrorDark());
        } else if (line.startsWith("+++") || line.startsWith("---")) {
            StyleConstants.setForeground(attributes, ModernColors.getTextSecondary());
        }
        return attributes;
    }

    private void setBusy(boolean busy) {
        if (refreshButton != null) {
            refreshButton.setEnabled(!busy);
        }
        if (changeList != null) {
            changeList.setEnabled(!busy);
        }
    }

    private static String format(String key, Object... args) {
        return MessageFormat.format(I18nUtil.getMessage(key), args);
    }

    private static String typeLabel(GitFileChange.Type type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case ADDED -> I18nUtil.getMessage(MessageKeys.GIT_DIFF_TYPE_ADDED);
            case MODIFIED -> I18nUtil.getMessage(MessageKeys.GIT_DIFF_TYPE_MODIFIED);
            case DELETED -> I18nUtil.getMessage(MessageKeys.GIT_DIFF_TYPE_DELETED);
            case UNTRACKED -> I18nUtil.getMessage(MessageKeys.GIT_DIFF_TYPE_UNTRACKED);
            case CONFLICTING -> I18nUtil.getMessage(MessageKeys.GIT_DIFF_TYPE_CONFLICTING);
        };
    }

    private record DiffLoadResult(StyledDocument document, String message) {
        private static DiffLoadResult diff(StyledDocument document) {
            return new DiffLoadResult(document, null);
        }

        private static DiffLoadResult message(String message) {
            return new DiffLoadResult(null, message);
        }
    }

    private static Color typeColor(GitFileChange.Type type) {
        if (type == null) {
            return ModernColors.getTextSecondary();
        }
        return switch (type) {
            case ADDED, UNTRACKED -> ModernColors.getSuccessDark();
            case MODIFIED -> ModernColors.getPrimary();
            case DELETED, CONFLICTING -> ModernColors.getErrorDark();
        };
    }

    private static final class ChangeListCellRenderer extends JPanel implements ListCellRenderer<GitFileChange> {

        private final JLabel pathLabel = new JLabel();
        private final JLabel typeLabel = new JLabel();

        private ChangeListCellRenderer() {
            super(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(6, 10, 6, 10));
            pathLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
            typeLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -2));
            add(pathLabel, BorderLayout.CENTER);
            add(typeLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends GitFileChange> list,
                                                      GitFileChange value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            setOpaque(true);
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            pathLabel.setForeground(isSelected ? list.getSelectionForeground() : ModernColors.getTextPrimary());
            pathLabel.setText(value == null ? "" : value.getPath());
            if (value != null) {
                pathLabel.setToolTipText(value.getPath());
            }
            typeLabel.setForeground(isSelected ? list.getSelectionForeground() : typeColor(value == null ? null : value.getType()));
            typeLabel.setText(value == null ? "" : GitDiffPanel.typeLabel(value.getType()));
            return this;
        }
    }
}

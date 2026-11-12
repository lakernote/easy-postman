package com.laker.postman.panel.workspace.components;

import com.laker.postman.common.component.AppToolWindowChrome;
import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.common.component.button.RefreshButton;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.model.GitCommitInfo;
import com.laker.postman.model.GitOperationResult;
import com.laker.postman.model.Workspace;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Embedded Git history browser for a workspace.
 */
@Slf4j
public class GitHistoryPanel extends JPanel {

    private final transient Workspace workspace;
    private final transient WorkspaceService workspaceService;
    private final transient Runnable workspaceRefreshAction;
    private JTable historyTable;
    private DefaultTableModel tableModel;
    private JTextArea detailsArea;
    private JLabel statusLabel;
    private JButton refreshButton;
    private JButton restoreButton;
    private List<GitCommitInfo> commits;
    private int historyLoadGeneration;
    private int detailsLoadGeneration;

    public GitHistoryPanel(Workspace workspace, Runnable workspaceRefreshAction) {
        this.workspace = workspace;
        this.workspaceService = WorkspaceService.getInstance();
        this.workspaceRefreshAction = workspaceRefreshAction;
        initUI();
        SwingUtilities.invokeLater(this::loadHistory);
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
        JLabel title = new JLabel(I18nUtil.getMessage(MessageKeys.GIT_HISTORY_TITLE));
        title.setFont(FontsUtil.getDefaultFontWithOffset(Font.BOLD, 1));
        JLabel workspaceLabel = new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_NAME) + ": " + workspace.getName());
        workspaceLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        workspaceLabel.setForeground(ModernColors.getTextSecondary());
        workspaceLabel.setToolTipText(workspace.getName());
        titlePanel.add(title);
        titlePanel.add(workspaceLabel);
        header.add(titlePanel, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        restoreButton = ModernButtonFactory.createButton(
                I18nUtil.getMessage(MessageKeys.GIT_HISTORY_RESTORE),
                false,
                "icons/history.svg"
        );
        restoreButton.setEnabled(false);
        restoreButton.addActionListener(e -> restoreSelectedCommit());
        actions.add(restoreButton);

        refreshButton = new RefreshButton();
        refreshButton.addActionListener(e -> loadHistory());
        actions.add(refreshButton);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 18, 12, 18));
        panel.add(createHistorySplitPane(), BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        statusLabel.setForeground(ModernColors.getTextSecondary());
        panel.add(statusLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JSplitPane createHistorySplitPane() {
        JSplitPane splitPane = AppToolWindowChrome.createHorizontalInnerSplitPane(
                createHistoryTableScrollPane(),
                createDetailsScrollPane(),
                520
        );
        splitPane.setResizeWeight(0.45);
        splitPane.setContinuousLayout(true);
        return splitPane;
    }

    private JScrollPane createHistoryTableScrollPane() {
        String[] columnNames = {
                I18nUtil.getMessage(MessageKeys.GIT_HISTORY_COMMIT_ID),
                I18nUtil.getMessage(MessageKeys.GIT_HISTORY_MESSAGE),
                I18nUtil.getMessage(MessageKeys.GIT_HISTORY_AUTHOR),
                I18nUtil.getMessage(MessageKeys.GIT_HISTORY_DATE)
        };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        historyTable = new JTable(tableModel);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        historyTable.setRowHeight(28);
        historyTable.getTableHeader().setFont(FontsUtil.getDefaultFont(Font.BOLD));
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(310);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(160);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        historyTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        historyTable.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        historyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                GitCommitInfo commit = getSelectedCommit();
                restoreButton.setEnabled(commit != null);
                if (commit == null) {
                    renderDetails(I18nUtil.getMessage(MessageKeys.GIT_HISTORY_SELECT_COMMIT));
                } else {
                    loadCommitDetails(commit);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(historyTable);
        ToolWindowSurfaceStyle.applyTableScrollPaneCard(scrollPane, historyTable);
        return scrollPane;
    }

    private JScrollPane createDetailsScrollPane() {
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(false);
        detailsArea.setFont(FontsUtil.getMonospacedFontWithOffset(Font.PLAIN, -1));
        detailsArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        ToolWindowSurfaceStyle.applyTextComponentCard(detailsArea);
        renderDetails(I18nUtil.getMessage(MessageKeys.GIT_HISTORY_SELECT_COMMIT));

        JScrollPane scrollPane = new JScrollPane(detailsArea);
        ToolWindowSurfaceStyle.applyScrollPaneCard(scrollPane);
        return scrollPane;
    }

    private void loadHistory() {
        loadHistory(null);
    }

    private void loadHistory(String postLoadStatus) {
        int generation = ++historyLoadGeneration;
        detailsLoadGeneration++;
        setBusy(true, I18nUtil.getMessage(MessageKeys.GIT_HISTORY_LOADING));
        tableModel.setRowCount(0);
        tableModel.addRow(new Object[]{I18nUtil.getMessage(MessageKeys.GIT_HISTORY_LOADING), "", "", ""});
        renderDetails(I18nUtil.getMessage(MessageKeys.GIT_HISTORY_LOADING));

        new SwingWorker<List<GitCommitInfo>, Void>() {
            @Override
            protected List<GitCommitInfo> doInBackground() throws Exception {
                return workspaceService.getGitHistory(workspace.getId(), 100);
            }

            @Override
            protected void done() {
                if (generation != historyLoadGeneration) {
                    return;
                }
                try {
                    displayHistory(get(), postLoadStatus);
                } catch (Exception e) {
                    log.error("Failed to load Git history", e);
                    commits = List.of();
                    tableModel.setRowCount(0);
                    String message = format(MessageKeys.GIT_HISTORY_LOAD_FAILED, rootMessage(e));
                    statusLabel.setText(message);
                    renderDetails(message);
                } finally {
                    setBusy(false, statusLabel.getText());
                }
            }
        }.execute();
    }

    private void displayHistory(List<GitCommitInfo> loadedCommits, String postLoadStatus) {
        commits = loadedCommits == null ? List.of() : loadedCommits;
        tableModel.setRowCount(0);
        if (commits.isEmpty()) {
            tableModel.addRow(new Object[]{I18nUtil.getMessage(MessageKeys.GIT_HISTORY_NO_COMMITS), "", "", ""});
            renderDetails(I18nUtil.getMessage(MessageKeys.GIT_HISTORY_NO_COMMITS));
            statusLabel.setText(postLoadStatus == null || postLoadStatus.isBlank()
                    ? I18nUtil.getMessage(MessageKeys.GIT_HISTORY_NO_COMMITS)
                    : postLoadStatus);
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (GitCommitInfo commit : commits) {
            tableModel.addRow(new Object[]{
                    commit.getShortCommitId(),
                    firstLine(commit.getMessage()),
                    commit.getAuthorName(),
                    dateFormat.format(new Date(commit.getCommitTime()))
            });
        }
        historyTable.setRowSelectionInterval(0, 0);
        statusLabel.setText(postLoadStatus == null || postLoadStatus.isBlank() ? " " : postLoadStatus);
    }

    private void loadCommitDetails(GitCommitInfo commit) {
        int generation = ++detailsLoadGeneration;
        renderDetails(I18nUtil.getMessage(MessageKeys.GIT_HISTORY_DETAILS_LOADING));
        statusLabel.setText(I18nUtil.getMessage(MessageKeys.GIT_HISTORY_COMMIT_DETAILS) + ": "
                + commit.getShortCommitId());

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return workspaceService.getCommitDetails(workspace.getId(), commit.getCommitId());
            }

            @Override
            protected void done() {
                if (generation != detailsLoadGeneration) {
                    return;
                }
                GitCommitInfo selectedCommit = getSelectedCommit();
                if (selectedCommit == null || !commit.getCommitId().equals(selectedCommit.getCommitId())) {
                    return;
                }
                try {
                    renderDetails(get());
                } catch (Exception e) {
                    log.error("Failed to get commit details", e);
                    String message = format(MessageKeys.GIT_HISTORY_DETAILS_FAILED, rootMessage(e));
                    statusLabel.setText(message);
                    renderDetails(message);
                }
            }
        }.execute();
    }

    private void restoreSelectedCommit() {
        GitCommitInfo commit = getSelectedCommit();
        if (commit == null) {
            statusLabel.setText(I18nUtil.getMessage(MessageKeys.GIT_HISTORY_SELECT_COMMIT));
            return;
        }

        JCheckBox backupCheckBox = new JCheckBox(
                I18nUtil.getMessage(MessageKeys.GIT_HISTORY_RESTORE_BACKUP),
                false
        );
        Object[] message = {
                I18nUtil.getMessage(MessageKeys.GIT_HISTORY_RESTORE_CONFIRM, commit.getShortCommitId()),
                backupCheckBox
        };
        int option = JOptionPane.showConfirmDialog(
                this,
                message,
                I18nUtil.getMessage(MessageKeys.GIT_HISTORY_RESTORE),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (option != JOptionPane.YES_OPTION) {
            return;
        }

        setBusy(true, I18nUtil.getMessage(MessageKeys.GIT_HISTORY_RESTORING));
        boolean createBackup = backupCheckBox.isSelected();
        new SwingWorker<GitOperationResult, Void>() {
            @Override
            protected GitOperationResult doInBackground() throws Exception {
                return workspaceService.restoreToCommit(workspace.getId(), commit.getCommitId(), createBackup);
            }

            @Override
            protected void done() {
                boolean reloading = false;
                try {
                    GitOperationResult result = get();
                    if (result.success) {
                        notifyWorkspaceChanged();
                        reloading = true;
                        String message = I18nUtil.getMessage(
                                MessageKeys.GIT_HISTORY_RESTORE_SUCCESS,
                                commit.getShortCommitId()
                        );
                        statusLabel.setText(message);
                        loadHistory(message);
                    } else {
                        statusLabel.setText(I18nUtil.getMessage(
                                MessageKeys.GIT_HISTORY_RESTORE_FAILED,
                                result.message
                        ));
                    }
                } catch (Exception e) {
                    log.error("Failed to restore to commit", e);
                    statusLabel.setText(I18nUtil.getMessage(
                            MessageKeys.GIT_HISTORY_RESTORE_FAILED,
                            rootMessage(e)
                    ));
                } finally {
                    if (!reloading) {
                        setBusy(false, statusLabel.getText());
                    }
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String status) {
        if (historyTable != null) {
            historyTable.setEnabled(!busy);
        }
        if (refreshButton != null) {
            refreshButton.setEnabled(!busy);
        }
        if (restoreButton != null) {
            restoreButton.setEnabled(!busy && getSelectedCommit() != null);
        }
        if (statusLabel != null && status != null) {
            statusLabel.setText(status);
        }
    }

    private GitCommitInfo getSelectedCommit() {
        if (historyTable == null || commits == null || commits.isEmpty() || historyTable.getSelectedRow() < 0) {
            return null;
        }
        int modelRow = historyTable.convertRowIndexToModel(historyTable.getSelectedRow());
        if (modelRow < 0 || modelRow >= commits.size()) {
            return null;
        }
        return commits.get(modelRow);
    }

    private void renderDetails(String details) {
        detailsArea.setText(details == null ? "" : details);
        detailsArea.setCaretPosition(0);
    }

    private void notifyWorkspaceChanged() {
        if (workspaceRefreshAction != null) {
            workspaceRefreshAction.run();
        }
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String firstLine = message.split("\n", 2)[0];
        return firstLine.length() > 80 ? firstLine.substring(0, 77) + "..." : firstLine;
    }

    private static String format(String key, Object... args) {
        return MessageFormat.format(I18nUtil.getMessage(key), args);
    }

    private static String rootMessage(Exception e) {
        return e.getCause() != null && e.getCause().getMessage() != null
                ? e.getCause().getMessage()
                : e.getMessage();
    }
}

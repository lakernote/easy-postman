package com.laker.postman.panel.workspace.components;

import com.laker.postman.model.GitRepoSource;
import com.laker.postman.model.Workspace;
import com.laker.postman.model.WorkspaceType;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class WorkspaceDetailPanel extends JPanel {
    public WorkspaceDetailPanel(Workspace workspace) {
        this.setLayout(new BorderLayout());
        // 去除外边框，只保留内边距
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 基本信息
        JPanel infoSection = new JPanel(new GridBagLayout());
        infoSection.setBorder(BorderFactory.createTitledBorder(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_BASIC_INFO)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST; // 确保左对齐

        // 名称
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0; // 标签不扩展
        infoSection.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_NAME) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0; // 值扩展填充剩余空间
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel nameLabel = new JLabel(workspace.getName());
        nameLabel.setFont(FontsUtil.getDefaultFont(Font.BOLD, 12));
        infoSection.add(nameLabel, gbc);

        // 类型
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        infoSection.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        infoSection.add(new JLabel(workspace.getType() == WorkspaceType.LOCAL ?
                I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_LOCAL) :
                I18nUtil.getMessage(MessageKeys.WORKSPACE_TYPE_GIT)), gbc);

        // 路径
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        infoSection.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_PATH) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel pathLabel = new JLabel(workspace.getPath().toString());
        infoSection.add(pathLabel, gbc);

        // 描述
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        infoSection.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DESCRIPTION) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        infoSection.add(new JLabel(workspace.getDescription()), gbc);

        // 创建时间
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        infoSection.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_CREATED_TIME) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        infoSection.add(new JLabel(sdf.format(new Date(workspace.getCreatedAt()))), gbc);

        add(infoSection, BorderLayout.NORTH);

        // Git 信息（如果是 Git 工作区）
        if (workspace.getType() == WorkspaceType.GIT) {
            add(createGitInfoPanel(workspace), BorderLayout.CENTER);
        }

    }

    /**
     * 创建 Git 信息面板
     */
    private JPanel createGitInfoPanel(Workspace workspace) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_GIT_INFO)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST; // 确保左对齐

        int row = 0;

        // 仓库来源
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0; // 标签不扩展
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_REPO_SOURCE) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0; // 值扩展填充剩余空间
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel(workspace.getGitRepoSource() == GitRepoSource.CLONED ?
                I18nUtil.getMessage(MessageKeys.WORKSPACE_CLONE_FROM_REMOTE) :
                I18nUtil.getMessage(MessageKeys.WORKSPACE_INIT_LOCAL)), gbc);
        row++;

        // 远程仓库 URL
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_REMOTE_REPO) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel urlLabel = new JLabel(workspace.getGitRemoteUrl());
        urlLabel.setFont(FontsUtil.getDefaultFont(Font.PLAIN, 11));
        panel.add(urlLabel, gbc);
        row++;

        // 当前分支
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_LOCAL_BRANCH) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel(workspace.getCurrentBranch()), gbc);
        row++;

        // 远程分支
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_REMOTE_BRANCH) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel(workspace.getRemoteBranch()), gbc);
        row++;

        // 最后提交 ID
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(I18nUtil.getMessage(MessageKeys.WORKSPACE_DETAIL_LAST_COMMIT) + ":"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        String shortCommitId = "";
        if (workspace.getLastCommitId() != null) {
            shortCommitId = workspace.getLastCommitId().length() > 8
                    ? workspace.getLastCommitId().substring(0, 8)
                    : workspace.getLastCommitId();
        }
        JLabel commitLabel = new JLabel(shortCommitId);
        commitLabel.setFont(FontsUtil.getDefaultFont(Font.PLAIN, 11));
        panel.add(commitLabel, gbc);

        return panel;
    }

}

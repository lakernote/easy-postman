package com.laker.postman.panel.workspace.components;

import com.laker.postman.model.Workspace;
import com.laker.postman.plugin.git.GitWorkspaceLayout;
import com.laker.postman.service.WorkspaceService;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import javax.swing.*;
import java.awt.*;

/**
 * 工作区相关的确认提示：在已有目录中创建数据、在业务代码仓库中执行整仓库 Git 操作。
 */
@UtilityClass
public class WorkspaceConfirmations {

    /**
     * 目录中还没有 EasyPostman 数据时，确认是否在此创建；只使用后台探测结果，不在界面线程访问磁盘。
     *
     * @param confirmEmptyDirectory 不存在或为空的普通目录是否也需要确认（“打开文件夹”需要，“新建”不需要）
     */
    public static boolean confirmCreateData(Component parent, WorkspaceDirectoryProbe probe, boolean confirmEmptyDirectory) {
        if (probe.hasData()) {
            return true;
        }
        String directory = probe.directory().toString();
        String message;
        if (probe.inRepository()) {
            message = I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE_DATA_IN_REPO_CONFIRM,
                    directory, probe.layout().getRepositoryRoot().toString());
        } else if (confirmEmptyDirectory || !probe.missingOrEmpty()) {
            message = I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE_DATA_CONFIRM, directory);
        } else {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(parent, message,
                I18nUtil.getMessage(MessageKeys.WORKSPACE_CREATE_DATA_TITLE),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        return choice == JOptionPane.OK_OPTION;
    }

    /**
     * 工作区位于非 EasyPostman 创建的代码仓库中时，拉取、推送、切换分支等整仓库操作前确认；EasyPostman 自有仓库直接通过。
     */
    public static boolean confirmRepositoryWideOperation(Component parent, Workspace workspace, String operationName) {
        GitWorkspaceLayout layout = WorkspaceService.getInstance().detectGitLayout(workspace);
        if (layout == null || layout.isFullControl()) {
            return true;
        }
        int choice = JOptionPane.showConfirmDialog(parent,
                I18nUtil.getMessage(MessageKeys.GIT_REPO_WIDE_OPERATION_CONFIRM,
                        layout.getRepositoryRoot().toString(), operationName),
                I18nUtil.getMessage(MessageKeys.GIT_REPO_WIDE_OPERATION_TITLE),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.OK_OPTION;
    }

    /**
     * 工作区所在仓库是否还包含业务代码等非 EasyPostman 内容（整仓库操作需要额外确认）。
     */
    public static boolean isSharedRepository(Workspace workspace) {
        GitWorkspaceLayout layout = WorkspaceService.getInstance().detectGitLayout(workspace);
        return layout != null && !layout.isFullControl();
    }

}

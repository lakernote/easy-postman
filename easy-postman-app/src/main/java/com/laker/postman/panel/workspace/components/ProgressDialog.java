package com.laker.postman.panel.workspace.components;

import com.laker.postman.common.component.ToolWindowSurfaceStyle;
import com.laker.postman.common.component.button.ModernButtonFactory;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;

/**
 * 带进度显示的对话框基类
 * 提供通用的进度显示、按钮控制等功能
 */
public abstract class ProgressDialog extends JDialog {

    @Getter
    protected boolean confirmed = false;
    @Getter
    protected boolean operationInProgress = false;

    protected ProgressPanel progressPanel;
    protected JButton confirmButton;
    protected JButton cancelButton;
    private SwingWorker<Void, String> currentWorker;

    protected ProgressDialog(Window parent, String title) {
        super(parent, title, ModalityType.APPLICATION_MODAL);
    }

    /**
     * 初始化对话框
     */
    protected void initDialog() {
        ToolWindowSurfaceStyle.applyDialogWindowChrome(this);
        setupLayout();
        setupEventHandlers();
        pack();
        setLocationRelativeTo(getParent());
    }

    /**
     * 设置布局 - 子类需要实现
     */
    protected abstract void setupLayout();

    /**
     * 设置事件处理器 - 子类需要实现
     */
    protected abstract void setupEventHandlers();

    /**
     * 验证输入 - 子类需要实现
     */
    protected abstract void validateInput() throws IllegalArgumentException;

    /**
     * 获取操作任务 - 子类需要实现
     *
     * @return 返回一个SwingWorker来执行后台任务
     */
    protected abstract SwingWorker<Void, String> createWorkerTask();

    /**
     * 操作成功后的处理 - 子类可以重写
     */
    protected void onOperationSuccess() {
        confirmed = true;
        progressPanel.setProgressText(I18nUtil.getMessage(MessageKeys.PROGRESS_DIALOG_OPERATION_SUCCESS));
        progressPanel.getStatusLabel().setText(I18nUtil.getMessage(MessageKeys.PROGRESS_DIALOG_OPERATION_COMPLETED_CLOSING));

        // 延迟关闭对话框
        Timer timer = new Timer(1000, e -> dispose());
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * 操作失败后的处理 - 子类可以重写
     */
    protected void onOperationFailure(Exception e) {
        operationInProgress = false;
        confirmButton.setEnabled(true);
        setInputComponentsEnabled(true);
        progressPanel.setVisible(false);
        pack();

        progressPanel.reset();
        progressPanel.setProgressText(I18nUtil.getMessage(MessageKeys.PROGRESS_DIALOG_OPERATION_FAILED));
        progressPanel.getStatusLabel().setText(I18nUtil.getMessage(MessageKeys.PROGRESS_DIALOG_CHECK_INPUT_RETRY));

        showError(I18nUtil.getMessage(MessageKeys.PROGRESS_DIALOG_OPERATION_FAILED_WITH_MESSAGE, e.getMessage()));
    }

    /**
     * 设置输入组件启用状态 - 子类需要实现
     */
    protected abstract void setInputComponentsEnabled(boolean enabled);

    /**
     * 创建标准按钮面板
     */
    protected JPanel createStandardButtonPanel(String confirmText) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        ToolWindowSurfaceStyle.applyDialogFooter(panel);

        cancelButton = ModernButtonFactory.createButton(I18nUtil.getMessage(MessageKeys.BUTTON_CANCEL), false);
        cancelButton.addActionListener(this::handleCancel);

        confirmButton = ModernButtonFactory.createButton(confirmText, true);
        confirmButton.addActionListener(this::handleConfirm);

        panel.add(cancelButton);
        panel.add(confirmButton);

        // 设置默认按钮
        getRootPane().setDefaultButton(confirmButton);

        return panel;
    }

    /**
     * 处理取消操作
     */
    private void handleCancel(ActionEvent e) {
        if (operationInProgress) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    I18nUtil.getMessage(MessageKeys.PROGRESS_DIALOG_CONFIRM_CANCEL_OPERATION),
                    I18nUtil.getMessage(MessageKeys.PROGRESS_DIALOG_CONFIRM_CANCEL_TITLE),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice == JOptionPane.YES_OPTION) {
                cancelCurrentOperation();
                dispose();
            }
        } else {
            dispose();
        }
    }

    /**
     * 取消当前操作
     */
    private void cancelCurrentOperation() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
        }
    }

    /**
     * 处理确认操作
     */
    /**
     * 子类在 {@link #validateInput()} 中启动后台准备（如磁盘探测）后，准备完成时重新执行确认流程。
     */
    protected void retryConfirm() {
        if (!operationInProgress && isDisplayable()) {
            handleConfirm(null);
        }
    }

    private void handleConfirm(ActionEvent e) {
        try {
            validateInput();
            showProgressAndStartOperation();
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                showError(ex.getMessage());
            }
        }
    }

    /**
     * 显示进度并开始操作
     */
    private void showProgressAndStartOperation() {
        // 切换界面状态
        operationInProgress = true;
        progressPanel.setVisible(true);
        confirmButton.setEnabled(false);

        // 禁用所有输入组件
        setInputComponentsEnabled(false);

        // 重新调整窗口大小
        pack();

        // 创建并执行后台任务
        currentWorker = createWorkerTask();

        // 监听进度变化
        currentWorker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressPanel.getProgressBar().setValue((Integer) evt.getNewValue());
            }
        });

        // 设置进度更新处理器
        currentWorker.addPropertyChangeListener(evt -> {
            if ("state".equals(evt.getPropertyName()) &&
                    SwingWorker.StateValue.DONE == evt.getNewValue()) {
                handleWorkerCompletion();
            }
        });

        currentWorker.execute();
    }

    /**
     * 处理Worker完成事件
     */
    private void handleWorkerCompletion() {
        try {
            currentWorker.get(); // 检查是否有异常
            onOperationSuccess();
        } catch (ExecutionException ex) {
            // ExecutionException包装了实际的异常
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                onOperationFailure((RuntimeException) cause);
            } else {
                onOperationFailure(new RuntimeException(cause));
            }
        } catch (InterruptedException ex) {
            // 处理中断异常
            Thread.currentThread().interrupt(); // 重新设置中断状态
            onOperationFailure(new RuntimeException(I18nUtil.getMessage(MessageKeys.GIT_DIALOG_USER_CANCELLED), ex));
        }
    }

    /**
     * 显示错误对话框
     */
    protected void showError(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                I18nUtil.getMessage(MessageKeys.GENERAL_ERROR),
                JOptionPane.ERROR_MESSAGE
        );
    }

    @Override
    public void dispose() {
        // 确保清理资源
        cancelCurrentOperation();
        super.dispose();
    }
}

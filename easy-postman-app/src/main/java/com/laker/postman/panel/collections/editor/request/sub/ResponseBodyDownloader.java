package com.laker.postman.panel.collections.editor.request.sub;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.notification.NotificationCenter;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.http.runtime.file.ResponseFiles;
import com.laker.postman.http.runtime.model.HttpResponse;
import com.laker.postman.util.FileChooserUtil;
import com.laker.postman.util.FileExtensionUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * 响应体下载：选择保存位置、在后台写入原始内容，并在保存期间通知调用方更新按钮状态。
 */
@Slf4j
final class ResponseBodyDownloader {

    private final Consumer<Boolean> savingStateListener;
    private boolean saving;

    /**
     * @param savingStateListener 保存开始/结束时回调（EDT），用于禁用/恢复下载按钮
     */
    ResponseBodyDownloader(Consumer<Boolean> savingStateListener) {
        this.savingStateListener = savingStateListener;
    }

    boolean isSaving() {
        return saving;
    }

    /**
     * 弹出保存对话框，确认后在后台写入；写入成功后在 EDT 回调 {@code onSaved}，失败时弹出错误通知。
     *
     * @param source          调用时（EDT）截取的下载来源，写入期间切换响应也不会写错内容
     * @param defaultFileName 保存对话框默认文件名
     */
    void download(Source source, String defaultFileName, Consumer<File> onSaved) {
        if (saving) {
            return;
        }
        SystemFileChooser fileChooser = FileChooserUtil.createSaveFileChooser("response.body.save",
                I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_SAVE_DIALOG_TITLE));
        fileChooser.setSelectedFile(new File(defaultFileName));
        int userSelection = fileChooser.showSaveDialog(UiSingletonFactory.getInstance(MainFrame.class));
        if (userSelection != SystemFileChooser.APPROVE_OPTION) {
            return;
        }
        File destFile = fileChooser.getSelectedFile();
        setSaving(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                source.writeTo(destFile);
                return null;
            }

            @Override
            protected void done() {
                setSaving(false);
                try {
                    get();
                    onSaved.accept(destFile);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    log.warn("Failed to save response body to {}", destFile, cause);
                    NotificationCenter.showError(I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_SAVE_FAILED,
                            cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()));
                }
            }
        }.execute();
    }

    static void notifySaved(File savedFile) {
        NotificationCenter.showSuccess(
                I18nUtil.getMessage(MessageKeys.RESPONSE_FILE_SAVED_TO, savedFile.getAbsolutePath()));
    }

    /**
     * 默认下载文件名：优先使用响应解析出的文件名（Content-Disposition / URL / 内容识别），否则按 Content-Type 生成。
     */
    static String defaultFileName(HttpResponse response) {
        if (response != null && response.fileName != null && !response.fileName.isBlank()) {
            return response.fileName;
        }
        String extension = FileExtensionUtil.guessExtension(ResponseFiles.contentType(response));
        return FileExtensionUtil.generateSmartFileName(extension == null ? ".txt" : extension);
    }

    private void setSaving(boolean saving) {
        this.saving = saving;
        if (savingStateListener != null) {
            savingStateListener.accept(saving);
        }
    }

    /**
     * 下载来源快照。写入优先级：
     * <ol>
     *     <li>临时文件（大文件或二进制文件）原样复制</li>
     *     <li>原始响应字节（文件响应、附件或非 UTF-8/无法无损解码的文本）</li>
     *     <li>响应体文本按 Content-Type 声明的字符集（默认 UTF-8）写入，不受格式化/排序后的编辑器内容影响</li>
     *     <li>没有响应对象时回退保存编辑器文本</li>
     * </ol>
     */
    record Source(File file, byte[] bytes, String text) {

        static Source of(HttpResponse response, String editorText) {
            if (response != null && response.filePath != null && !response.filePath.isEmpty()) {
                return new Source(new File(response.filePath), null, null);
            }
            if (response != null && response.rawBodyBytes != null) {
                return new Source(null, response.rawBodyBytes, null);
            }
            if (response != null && response.body != null) {
                return new Source(null, ResponseFiles.encodeBody(response), null);
            }
            return new Source(null, null, editorText == null ? "" : editorText);
        }

        void writeTo(File destFile) throws IOException {
            if (file != null) {
                Files.copy(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else if (bytes != null) {
                Files.write(destFile.toPath(), bytes);
            } else {
                Files.writeString(destFile.toPath(), text, StandardCharsets.UTF_8);
            }
        }
    }
}

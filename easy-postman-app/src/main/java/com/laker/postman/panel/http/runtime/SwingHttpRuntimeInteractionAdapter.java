package com.laker.postman.panel.http.runtime;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.component.DownloadProgressDialog;
import com.laker.postman.frame.MainFrame;
import com.laker.postman.http.runtime.interaction.HttpCallbackDispatcher;
import com.laker.postman.http.runtime.interaction.DownloadProgressSink;
import com.laker.postman.http.runtime.interaction.DownloadProgressSinkFactory;
import com.laker.postman.http.runtime.interaction.ResponseSizeLimitWarning;
import com.laker.postman.http.runtime.interaction.ResponseSizeLimitWarningSink;
import com.laker.postman.http.runtime.observation.HttpLifecycleLogSink;
import com.laker.postman.panel.sidebar.ConsolePanel;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import lombok.experimental.UtilityClass;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;

@UtilityClass
public class SwingHttpRuntimeInteractionAdapter {
    public static DownloadProgressSinkFactory downloadProgressSinkFactory() {
        if (GraphicsEnvironment.isHeadless()) {
            return DownloadProgressSinkFactory.noop();
        }
        return SwingDownloadProgressSink::new;
    }

    public static ResponseSizeLimitWarningSink responseSizeLimitWarningSink() {
        if (GraphicsEnvironment.isHeadless()) {
            return ResponseSizeLimitWarningSink.noop();
        }
        return warning -> SwingUtilities.invokeLater(() -> showResponseSizeLimitWarning(warning));
    }

    public static HttpLifecycleLogSink lifecycleLogSink() {
        if (GraphicsEnvironment.isHeadless()) {
            return HttpLifecycleLogSink.noop();
        }
        return (message, level) -> {
            try {
                ConsolePanel.appendLog(message, toConsoleLogType(level));
            } catch (RuntimeException ignored) {
                // Logging must not break protocol callbacks.
            }
        };
    }

    public static HttpCallbackDispatcher callbackDispatcher() {
        if (GraphicsEnvironment.isHeadless()) {
            return HttpCallbackDispatcher.direct();
        }
        return new HttpCallbackDispatcher() {
            @Override
            public boolean isDispatchThread() {
                return SwingUtilities.isEventDispatchThread();
            }

            @Override
            public void dispatch(Runnable action) {
                if (action != null) {
                    SwingUtilities.invokeLater(action);
                }
            }
        };
    }

    private static ConsolePanel.LogType toConsoleLogType(HttpLifecycleLogSink.Level level) {
        return switch (level) {
            case DEBUG -> ConsolePanel.LogType.DEBUG;
            case INFO -> ConsolePanel.LogType.INFO;
            case SUCCESS -> ConsolePanel.LogType.SUCCESS;
            case WARN -> ConsolePanel.LogType.WARN;
            case ERROR -> ConsolePanel.LogType.ERROR;
        };
    }

    private static void showResponseSizeLimitWarning(ResponseSizeLimitWarning warning) {
        JOptionPane.showMessageDialog(
                null,
                warning.kind() == ResponseSizeLimitWarning.Kind.TEXT
                        ? I18nUtil.getMessage(MessageKeys.TEXT_TOO_LARGE,
                        warning.contentLengthMegabytes(), warning.maxDownloadMegabytes())
                        : I18nUtil.getMessage(MessageKeys.BINARY_TOO_LARGE,
                        warning.contentLengthMegabytes(), warning.maxDownloadMegabytes()),
                I18nUtil.getMessage(MessageKeys.DOWNLOAD_LIMIT_TITLE),
                JOptionPane.WARNING_MESSAGE
        );
    }

    private static final class SwingDownloadProgressSink implements DownloadProgressSink {
        private volatile DownloadProgressDialog progressDialog;

        @Override
        public void start(int contentLength) {
            start(contentLength, false, null, null);
        }

        @Override
        public void start(int contentLength, boolean streamingMedia, String sourceUrl, Runnable cancelAction) {
            runOnEdtAndWait(() -> {
                progressDialog = new DownloadProgressDialog(UiSingletonFactory.getInstance(MainFrame.class),
                        I18nUtil.getMessage(MessageKeys.DOWNLOAD_PROGRESS_TITLE));
                progressDialog.startDownload(contentLength, streamingMedia, sourceUrl, cancelAction);
            });
        }

        @Override
        public boolean isCancelled() {
            DownloadProgressDialog dialog = progressDialog;
            return dialog != null && dialog.isCancelled();
        }

        @Override
        public void updateProgress(int bytesRead) {
            DownloadProgressDialog dialog = progressDialog;
            if (dialog != null) {
                // Only atomic counters are touched here; the dialog timer updates Swing.
                dialog.updateProgress(bytesRead);
            }
        }

        @Override
        public void finish() {
            finish(true);
        }

        @Override
        public void finish(boolean completed) {
            finish(completed, false);
        }

        @Override
        public void finish(boolean completed, boolean cancelled) {
            Runnable finishOnEdt = () -> {
                // An interrupted invokeAndWait can leave the start event queued.
                // Resolve the dialog after that event, rather than losing finish.
                DownloadProgressDialog dialog = progressDialog;
                if (dialog != null) {
                    dialog.finishDownload(completed, cancelled);
                }
            };
            if (SwingUtilities.isEventDispatchThread()) {
                finishOnEdt.run();
            } else {
                SwingUtilities.invokeLater(finishOnEdt);
            }
        }

        private static void runOnEdtAndWait(Runnable action) {
            if (SwingUtilities.isEventDispatchThread()) {
                action.run();
                return;
            }
            try {
                SwingUtilities.invokeAndWait(action);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while creating response progress dialog", ex);
            } catch (InvocationTargetException ex) {
                throw new IllegalStateException("Unable to create response progress dialog", ex.getCause());
            }
        }
    }
}

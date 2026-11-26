package com.laker.postman.panel.update;

import com.laker.postman.test.AbstractSwingUiTest;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;
import org.testng.annotations.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ModernProgressDialogTest extends AbstractSwingUiTest {

    @Test
    public void shouldAnimateWhileConnectingAndSwitchToDeterminateOnceBytesArrive() throws Exception {
        JFrame frame = new JFrame();
        try {
            ModernProgressDialog dialog = new ModernProgressDialog(frame);
            JProgressBar progressBar = field(dialog, "progressBar");
            JLabel statusLabel = field(dialog, "statusLabel");
            JLabel percentLabel = field(dialog, "percentLabel");

            SwingUtilities.invokeAndWait(() -> invokeShowConnectingState(dialog));
            assertTrue(progressBar.isIndeterminate());
            assertEquals(statusLabel.getText(), I18nUtil.getMessage(MessageKeys.UPDATE_CONNECTING));

            dialog.updateProgress(10, 1_000, 10_000, 500);
            flushEdt();
            assertFalse(progressBar.isIndeterminate());
            assertEquals(progressBar.getValue(), 10);
            assertEquals(percentLabel.getText(), "10%");
            assertEquals(statusLabel.getText(), I18nUtil.getMessage(MessageKeys.UPDATE_DOWNLOADING));

            // 重试/切换备用源会回报 0 进度，应恢复“连接中”动画而不是静止的 0%
            dialog.updateProgress(0, 0, 0, 0);
            flushEdt();
            assertTrue(progressBar.isIndeterminate());
            assertEquals(statusLabel.getText(), I18nUtil.getMessage(MessageKeys.UPDATE_CONNECTING));

            dialog.hide();
            flushEdt();
        } finally {
            frame.dispose();
        }
    }

    private static void flushEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
        });
    }

    private static void invokeShowConnectingState(ModernProgressDialog dialog) {
        try {
            Method method = ModernProgressDialog.class.getDeclaredMethod("showConnectingState");
            method.setAccessible(true);
            method.invoke(dialog);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(ModernProgressDialog dialog, String name) throws ReflectiveOperationException {
        Field field = ModernProgressDialog.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(dialog);
    }
}

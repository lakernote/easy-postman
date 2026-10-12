package com.laker.postman.frame;

import com.laker.postman.common.UiSingletonFactory;
import com.laker.postman.common.exception.CancelException;
import com.laker.postman.ioc.ApplicationContext;
import com.laker.postman.panel.collections.editor.RequestEditorPanel;
import com.laker.postman.panel.lifecycle.AppExitCoordinator;
import com.laker.postman.panel.performance.PerformancePanel;
import com.laker.postman.test.AbstractSwingUiTest;
import org.testng.annotations.Test;

import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class MainFrameExitUiTest extends AbstractSwingUiTest {

    @Test
    public void shouldKeepPerformancePanelUsableAndAllowRetryAfterCancellingWindowExit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                verifyCancelledWindowExit();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void verifyCancelledWindowExit() throws ReflectiveOperationException {
        Map<Class<?>, Object> singletons = singletonMap();
        Map<Class<?>, Object> previousSingletons = new LinkedHashMap<>(singletons);
        ApplicationContext context = ApplicationContext.getInstance();
        context.clear();
        MainFrame frame = null;
        try {
            AppExitCoordinator coordinator = new AppExitCoordinator();
            context.registerBean("appExitCoordinator", coordinator);
            RecordingPerformancePanel performance = UiSingletonFactory.getInstance(RecordingPerformancePanel.class);
            CancellingRequestEditor editor = UiSingletonFactory.getInstance(CancellingRequestEditor.class);
            singletons.put(PerformancePanel.class, performance);
            singletons.put(RequestEditorPanel.class, editor);
            frame = UiSingletonFactory.getInstance(MainFrame.class);
            Method installCloseListener = MainFrame.class.getDeclaredMethod("initWindowCloseListener");
            installCloseListener.setAccessible(true);
            installCloseListener.invoke(frame);

            WindowEvent closing = new WindowEvent(frame, WindowEvent.WINDOW_CLOSING);
            for (var listener : frame.getWindowListeners()) {
                listener.windowClosing(closing);
            }
            // A second request must reach confirmation again after the first was cancelled.
            coordinator.exitApplication();

            assertEquals(editor.confirmationAttempts, 2);
            assertEquals(performance.cleanupCount, 0, "Cancelling exit must not dispose performance resources");
            assertTrue(frame.isEnabled(), "Cancelling exit must leave the main frame usable");
        } finally {
            if (frame != null) {
                frame.dispose();
            }
            singletons.clear();
            singletons.putAll(previousSingletons);
            context.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> singletonMap() throws ReflectiveOperationException {
        Field field = UiSingletonFactory.class.getDeclaredField("INSTANCE_MAP");
        field.setAccessible(true);
        return (Map<Class<?>, Object>) field.get(null);
    }

    public static final class RecordingPerformancePanel extends PerformancePanel {
        private int cleanupCount;

        @Override
        protected void initUI() {
        }

        @Override
        protected void registerListeners() {
        }

        @Override
        public void cleanup() {
            cleanupCount++;
        }
    }

    public static final class CancellingRequestEditor extends RequestEditorPanel {
        private int confirmationAttempts;

        @Override
        protected void initUI() {
        }

        @Override
        protected void registerListeners() {
        }

        @Override
        public JTabbedPane getTabbedPane() {
            confirmationAttempts++;
            throw new CancelException();
        }
    }
}

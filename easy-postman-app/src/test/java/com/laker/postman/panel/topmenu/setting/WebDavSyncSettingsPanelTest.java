package com.laker.postman.panel.topmenu.setting;

import com.laker.postman.test.AbstractSwingUiTest;
import okhttp3.HttpUrl;
import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class WebDavSyncSettingsPanelTest extends AbstractSwingUiTest {

    @Test
    public void shouldWarnOnlyForNonLocalHttpWebDavUrls() {
        assertFalse(WebDavSyncSettingsPanel.shouldWarnInsecureHttp(HttpUrl.parse("https://example.com/dav/")));
        assertFalse(WebDavSyncSettingsPanel.shouldWarnInsecureHttp(HttpUrl.parse("http://localhost:8088/")));
        assertFalse(WebDavSyncSettingsPanel.shouldWarnInsecureHttp(HttpUrl.parse("http://127.0.0.1:8088/")));
        assertFalse(WebDavSyncSettingsPanel.shouldWarnInsecureHttp(HttpUrl.parse("http://[::1]:8088/")));

        assertTrue(WebDavSyncSettingsPanel.shouldWarnInsecureHttp(HttpUrl.parse("http://example.com/dav/")));
        assertTrue(WebDavSyncSettingsPanel.shouldWarnInsecureHttp(HttpUrl.parse("http://192.168.1.20/dav/")));
    }

    @Test
    public void busyStateShouldNotToggleWebDavActionButtonsDisabled() throws Exception {
        WebDavSyncSettingsPanel panel = new WebDavSyncSettingsPanel();
        panel.getPreferredSize();
        ((JCheckBox) field(panel, "enabledCheckBox")).setSelected(true);
        ((JTextField) field(panel, "serverUrlField")).setText("http://127.0.0.1:8088");
        invoke(panel, "updateControlState");

        JButton testButton = (JButton) field(panel, "testConnectionButton");
        JButton uploadButton = (JButton) field(panel, "uploadButton");
        JButton restoreButton = (JButton) field(panel, "restoreButton");
        assertTrue(testButton.isEnabled());
        assertTrue(uploadButton.isEnabled());
        assertTrue(restoreButton.isEnabled());

        invoke(panel, "setBusy", true);

        assertTrue(testButton.isEnabled());
        assertTrue(uploadButton.isEnabled());
        assertTrue(restoreButton.isEnabled());
        assertEquals(testButton.getClientProperty("webdav.busy"), Boolean.TRUE);
        assertEquals(uploadButton.getClientProperty("webdav.busy"), Boolean.TRUE);
        assertEquals(restoreButton.getClientProperty("webdav.busy"), Boolean.TRUE);
    }

    @Test
    public void actionButtonsShouldStayWithinTheirRow() throws Exception {
        AtomicReference<Point> actionRowBoundsRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            try {
                WebDavSyncSettingsPanel panel = new WebDavSyncSettingsPanel();
                panel.getPreferredSize();
                panel.setSize(580, 640);
                layoutRecursively(panel);

                JButton restoreButton = (JButton) field(panel, "restoreButton");
                actionRowBoundsRef.set(new Point(
                        restoreButton.getX() + restoreButton.getWidth(),
                        restoreButton.getParent().getWidth()
                ));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });

        Point actionRowBounds = actionRowBoundsRef.get();
        assertTrue(
                actionRowBounds.x <= actionRowBounds.y,
                "The restore button must stay within its row: restore="
                        + actionRowBounds.x + ", row=" + actionRowBounds.y
        );
    }

    @Test
    public void autoUploadIntervalShouldAlignWithWebDavFields() throws Exception {
        AtomicReference<Point> rightEdges = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            try {
                WebDavSyncSettingsPanel panel = new WebDavSyncSettingsPanel();
                panel.getPreferredSize();
                panel.setSize(900, 900);
                layoutRecursively(panel);

                JComboBox<?> intervalComboBox = (JComboBox<?>) field(panel, "autoUploadIntervalComboBox");
                JTextField serverUrlField = (JTextField) field(panel, "serverUrlField");
                rightEdges.set(new Point(
                        intervalComboBox.getX() + intervalComboBox.getWidth(),
                        serverUrlField.getX() + serverUrlField.getWidth()
                ));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });

        assertEquals(rightEdges.get().x, rightEdges.get().y);
    }

    private static void layoutRecursively(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                layoutRecursively(child);
            }
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = WebDavSyncSettingsPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(Object target, String name, Object... args) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i] instanceof Boolean ? boolean.class : args[i].getClass();
        }
        Method method = WebDavSyncSettingsPanel.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }
}

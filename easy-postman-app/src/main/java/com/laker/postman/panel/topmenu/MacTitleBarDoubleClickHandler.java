package com.laker.postman.panel.topmenu;

import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * macOS fullWindowContent 模式下菜单栏覆盖了原生标题栏，空白区域的双击被 Swing 接收，
 * 原生“双击标题栏缩放”只在标题文字上生效。这里在菜单栏空白处补齐该行为，并遵循系统偏好设置。
 */
@Slf4j
class MacTitleBarDoubleClickHandler extends MouseAdapter {

    enum Action { ZOOM, MINIMIZE, NONE }

    private static volatile Action cachedAction;

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) {
            return;
        }
        Window window = SwingUtilities.getWindowAncestor(e.getComponent());
        if (!(window instanceof Frame frame)) {
            return;
        }
        switch (systemAction()) {
            case ZOOM -> toggleZoom(frame);
            case MINIMIZE -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED);
            case NONE -> {
            }
        }
    }

    private static void toggleZoom(Frame frame) {
        int state = frame.getExtendedState();
        boolean maximized = (state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        frame.setExtendedState(maximized ? state & ~Frame.MAXIMIZED_BOTH : state | Frame.MAXIMIZED_BOTH);
    }

    private static Action systemAction() {
        Action action = cachedAction;
        if (action == null) {
            action = parseAction(readGlobalDefault("AppleActionOnDoubleClick"),
                    readGlobalDefault("AppleMiniaturizeOnDoubleClick"));
            cachedAction = action;
        }
        return action;
    }

    static Action parseAction(String actionOnDoubleClick, String miniaturizeOnDoubleClick) {
        if (actionOnDoubleClick != null) {
            return switch (actionOnDoubleClick.trim()) {
                case "Minimize" -> Action.MINIMIZE;
                case "None" -> Action.NONE;
                default -> Action.ZOOM;
            };
        }
        if (miniaturizeOnDoubleClick != null && "1".equals(miniaturizeOnDoubleClick.trim())) {
            return Action.MINIMIZE;
        }
        return Action.ZOOM;
    }

    private static String readGlobalDefault(String key) {
        try {
            Process process = new ProcessBuilder("defaults", "read", "-g", key)
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? output : null;
        } catch (Exception ex) {
            log.debug("Failed to read macOS default {}", key, ex);
            return null;
        }
    }
}
